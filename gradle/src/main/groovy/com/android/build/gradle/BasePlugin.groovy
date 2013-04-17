/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.build.gradle

import com.android.SdkConstants
import com.android.annotations.NonNull
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.BadPluginException
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.dependency.ConfigurationDependencies
import com.android.build.gradle.internal.dependency.DependencyChecker
import com.android.build.gradle.internal.dependency.LibraryDependencyImpl
import com.android.build.gradle.internal.dependency.ManifestDependencyImpl
import com.android.build.gradle.internal.dependency.SymbolFileProviderImpl
import com.android.build.gradle.internal.dsl.SigningConfigDsl
import com.android.build.gradle.internal.model.ModelBuilder
import com.android.build.gradle.internal.tasks.AndroidTestTask
import com.android.build.gradle.internal.tasks.DependencyReportTask
import com.android.build.gradle.internal.tasks.InstallTask
import com.android.build.gradle.internal.tasks.PrepareDependenciesTask
import com.android.build.gradle.internal.tasks.PrepareLibraryTask
import com.android.build.gradle.internal.tasks.SigningReportTask
import com.android.build.gradle.internal.tasks.TestFlavorTask
import com.android.build.gradle.internal.tasks.TestLibraryTask
import com.android.build.gradle.internal.tasks.UninstallTask
import com.android.build.gradle.internal.tasks.ValidateSigningTask
import com.android.build.gradle.internal.variant.ApkVariantData
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.TestVariantData
import com.android.build.gradle.tasks.AidlCompile
import com.android.build.gradle.tasks.Dex
import com.android.build.gradle.tasks.GenerateBuildConfig
import com.android.build.gradle.tasks.MergeAssets
import com.android.build.gradle.tasks.MergeResources
import com.android.build.gradle.tasks.PackageApplication
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.build.gradle.tasks.ProcessAppManifest
import com.android.build.gradle.tasks.ProcessTestManifest
import com.android.build.gradle.tasks.RenderscriptCompile
import com.android.build.gradle.tasks.ZipAlign
import com.android.builder.AndroidBuilder
import com.android.builder.DefaultProductFlavor
import com.android.builder.DefaultSdkParser
import com.android.builder.PlatformSdkParser
import com.android.builder.SdkParser
import com.android.builder.VariantConfiguration
import com.android.builder.dependency.JarDependency
import com.android.builder.dependency.LibraryDependency
import com.android.builder.model.ProductFlavor
import com.android.builder.model.SourceProvider
import com.android.builder.signing.SigningConfig
import com.android.sdklib.repository.FullRevision
import com.android.utils.ILogger
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Multimap
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.tooling.BuildException
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.util.GUtil

import static com.android.builder.BuilderConstants.EXT_LIB_ARCHIVE
import static com.android.builder.BuilderConstants.FLAVORS
import static com.android.builder.BuilderConstants.INSTRUMENTATION_RESULTS
import static com.android.builder.BuilderConstants.INSTRUMENTATION_TEST
import static com.android.builder.BuilderConstants.INSTRUMENTATION_TESTS
import static com.android.builder.BuilderConstants.REPORTS

/**
 * Base class for all Android plugins
 */
public abstract class BasePlugin {
    public static final String GRADLE_MIN_VERSION = "1.6-20130404052254+0000"
    public static final String[] GRADLE_SUPPORTED_VERSIONS = [ GRADLE_MIN_VERSION ]

    public static final String INSTALL_GROUP = "Install"

    protected static File TEST_SDK_DIR;

    protected Instantiator instantiator
    private ToolingModelBuilderRegistry registry

    private final Map<Object, AndroidBuilder> builders = Maps.newIdentityHashMap()

    final List<BaseVariantData> variantDataList = []
    final Map<LibraryDependencyImpl, PrepareLibraryTask> prepareTaskMap = [:]
    final Map<SigningConfig, ValidateSigningTask> validateSigningTaskMap = [:]

    protected Project project
    protected SdkParser androidSdkParser
    private LoggerWrapper loggerWrapper

    private boolean hasCreatedTasks = false

    private ProductFlavorData<DefaultProductFlavor> defaultConfigData
    protected AndroidSourceSet mainSourceSet
    protected AndroidSourceSet testSourceSet

    protected Task uninstallAll
    protected Task assembleTest
    protected Task deviceCheck

    protected abstract BaseExtension getAndroidExtension()

    protected BasePlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
        this.instantiator = instantiator
        this.registry = registry
    }

    protected abstract BaseExtension getExtension()
    protected abstract void doCreateAndroidTasks()

    protected void apply(Project project) {
        this.project = project

        checkGradleVersion()

        project.apply plugin: JavaBasePlugin

        // Register a builder for the custom tooling model
        registry.register(new ModelBuilder());

        project.tasks.assemble.description =
            "Assembles all variants of all applications and secondary packages."

        uninstallAll = project.tasks.create("uninstallAll")
        uninstallAll.description = "Uninstall all applications."
        uninstallAll.group = INSTALL_GROUP

        deviceCheck = project.tasks.create("deviceCheck")
        deviceCheck.description = "Runs all checks that requires a connected device."
        deviceCheck.group = JavaBasePlugin.VERIFICATION_GROUP

        project.afterEvaluate {
            createAndroidTasks()
        }
    }

    private void checkGradleVersion() {
        boolean foundMatch = false
        for (String version : GRADLE_SUPPORTED_VERSIONS) {
            if (project.getGradle().gradleVersion.startsWith(version)) {
                foundMatch = true
                break
            }
        }

        if (!foundMatch) {
            throw new BuildException(
                    String.format(
                            "Gradle version %s is required. Current version is %s",
                            GRADLE_MIN_VERSION, project.getGradle().gradleVersion), null);

        }
    }

    final void createAndroidTasks() {
        // get current plugins and look for the default Java plugin.
        if (project.plugins.hasPlugin(JavaPlugin.class)) {
            throw new BadPluginException(
                    "The 'java' plugin has been applied, but it is not compatible with the Android plugins.")
        }

        findSdk(project)

        if (hasCreatedTasks) {
            return
        }
        hasCreatedTasks = true

        doCreateAndroidTasks()
        createReportTasks()
    }

    protected setDefaultConfig(DefaultProductFlavor defaultConfig,
                               NamedDomainObjectContainer<AndroidSourceSet> sourceSets) {
        mainSourceSet = sourceSets.create(defaultConfig.name)
        testSourceSet = sourceSets.create(INSTRUMENTATION_TEST)

        defaultConfigData = new ProductFlavorData<DefaultProductFlavor>(defaultConfig, mainSourceSet,
                testSourceSet, project, ConfigurationDependencies.ConfigType.DEFAULT)
    }

    ProductFlavorData getDefaultConfigData() {
        return defaultConfigData
    }

    SdkParser getSdkParser() {
        return androidSdkParser;
    }

    ILogger getLogger() {
        if (loggerWrapper == null) {
            loggerWrapper = new LoggerWrapper(project.logger)
        }

        return loggerWrapper
    }

    boolean isVerbose() {
        return project.logger.isEnabled(LogLevel.DEBUG)
    }

    AndroidBuilder getAndroidBuilder(BaseVariantData variantData) {
        AndroidBuilder androidBuilder = builders.get(variantData)

        if (androidBuilder == null) {
            String target = androidExtension.getCompileSdkVersion()
            if (target == null) {
                throw new IllegalArgumentException("android.compileSdkVersion is missing!")
            }

            FullRevision buildToolsRevision = androidExtension.buildToolsRevision
            if (buildToolsRevision == null) {
                throw new IllegalArgumentException("android.buildToolsVersion is missing!")

            }

            sdkParser.initParser(target, buildToolsRevision, logger)
            androidBuilder = new AndroidBuilder(sdkParser, logger, verbose)

            builders.put(variantData, androidBuilder)
        }

        return androidBuilder
    }

    private void findSdk(Project project) {
        // if already set through tests.
        if (TEST_SDK_DIR != null) {
            androidSdkParser = new DefaultSdkParser(TEST_SDK_DIR.absolutePath)
            return
        }

        boolean defaultParser = true
        File sdkDir = null

        def rootDir = project.rootDir
        def localProperties = new File(rootDir, SdkConstants.FN_LOCAL_PROPERTIES)
        if (localProperties.exists()) {
            Properties properties = new Properties()
            localProperties.withInputStream { instr ->
                properties.load(instr)
            }
            def sdkDirProp = properties.getProperty('sdk.dir')

            if (sdkDirProp != null) {
                sdkDir = new File(sdkDirProp)
            } else {
                sdkDirProp = properties.getProperty('android.dir')
                if (sdkDirProp != null) {
                    sdkDir = new File(rootDir, sdkDirProp)
                    defaultParser = false
                } else {
                    throw new RuntimeException(
                            "No sdk.dir property defined in local.properties file.")
                }
            }
        } else {
            def envVar = System.getenv("ANDROID_HOME")
            if (envVar != null) {
                sdkDir = new File(envVar)
            }
        }

        if (sdkDir == null) {
            throw new RuntimeException(
                    "SDK location not found. Define location with sdk.dir in the local.properties file or with an ANDROID_HOME environment variable.")
        }

        if (!sdkDir.directory) {
            throw new RuntimeException(
                    "The SDK directory '$sdkDir' specified in local.properties does not exist.")
        }

        if (defaultParser) {
            androidSdkParser = new DefaultSdkParser(sdkDir.absolutePath)
        } else {
            androidSdkParser = new PlatformSdkParser(sdkDir.absolutePath)
        }
    }

    protected String getRuntimeJars(BaseVariantData variantData) {
        return getRuntimeJarList(variantData).join(File.pathSeparator)
    }

    public List<String> getRuntimeJarList(BaseVariantData variantData) {
        AndroidBuilder androidBuilder = getAndroidBuilder(variantData)

        return androidBuilder.runtimeClasspath
    }

    protected void createProcessManifestTask(BaseVariantData variantData,
                                             String manifestOurDir) {
        def processManifestTask = project.tasks.create("process${variantData.name}Manifest",
                ProcessAppManifest)
        variantData.processManifestTask = processManifestTask
        processManifestTask.dependsOn variantData.prepareDependenciesTask

        processManifestTask.plugin = this
        processManifestTask.variant = variantData

        VariantConfiguration config = variantData.variantConfiguration
        ProductFlavor mergedFlavor = config.mergedFlavor

        processManifestTask.conventionMapping.mainManifest = {
            config.mainManifest
        }
        processManifestTask.conventionMapping.manifestOverlays = {
            config.manifestOverlays
        }
        processManifestTask.conventionMapping.packageName = {
            config.packageOverride
        }
        processManifestTask.conventionMapping.versionName = {
            config.versionName
        }
        processManifestTask.conventionMapping.libraries = {
            getManifestDependencies(config.directLibraries)
        }
        processManifestTask.conventionMapping.versionCode = {
            mergedFlavor.versionCode
        }
        processManifestTask.conventionMapping.minSdkVersion = {
            mergedFlavor.minSdkVersion
        }
        processManifestTask.conventionMapping.targetSdkVersion = {
            mergedFlavor.targetSdkVersion
        }
        processManifestTask.conventionMapping.manifestOutputFile = {
            project.file(
                    "$project.buildDir/${manifestOurDir}/$variantData.dirName/AndroidManifest.xml")
        }
    }

    protected void createProcessTestManifestTask(BaseVariantData variantData,
                                                 String manifestOurDir) {
        def processTestManifestTask = project.tasks.create("process${variantData.name}TestManifest",
                ProcessTestManifest)
        variantData.processManifestTask = processTestManifestTask
        processTestManifestTask.dependsOn variantData.prepareDependenciesTask

        processTestManifestTask.plugin = this
        processTestManifestTask.variant = variantData

        VariantConfiguration config = variantData.variantConfiguration

        processTestManifestTask.conventionMapping.testPackageName = {
            config.packageName
        }
        processTestManifestTask.conventionMapping.minSdkVersion = {
            config.minSdkVersion
        }
        processTestManifestTask.conventionMapping.targetSdkVersion = {
            config.targetSdkVersion
        }
        processTestManifestTask.conventionMapping.testedPackageName = {
            config.testedPackageName
        }
        processTestManifestTask.conventionMapping.instrumentationRunner = {
            config.instrumentationRunner
        }
        processTestManifestTask.conventionMapping.libraries = {
            getManifestDependencies(config.directLibraries)
        }
        processTestManifestTask.conventionMapping.manifestOutputFile = {
            project.file(
                    "$project.buildDir/${manifestOurDir}/$variantData.dirName/AndroidManifest.xml")
        }
    }

    protected void createRenderscriptTask(BaseVariantData variantData) {
        VariantConfiguration config = variantData.variantConfiguration

        def renderscriptTask = project.tasks.create("compile${variantData.name}Renderscript",
                RenderscriptCompile)
        variantData.renderscriptCompileTask = renderscriptTask

        renderscriptTask.dependsOn variantData.prepareDependenciesTask
        renderscriptTask.plugin = this
        renderscriptTask.variant = variantData

        renderscriptTask.targetApi = config.mergedFlavor.renderscriptTargetApi
        renderscriptTask.debugBuild = config.buildType.renderscriptDebugBuild
        renderscriptTask.optimLevel = config.buildType.renderscriptOptimLevel

        renderscriptTask.conventionMapping.sourceDirs = { config.renderscriptSourceList }
        renderscriptTask.conventionMapping.importDirs = { config.renderscriptImports }

        renderscriptTask.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/rs/$variantData.dirName")
        }
        renderscriptTask.conventionMapping.resOutputDir = {
            project.file("$project.buildDir/res/rs/$variantData.dirName")
        }
    }

    protected void createMergeResourcesTask(BaseVariantData variantData, boolean process9Patch) {
        createMergeResourcesTask(variantData, "$project.buildDir/res/all/$variantData.dirName",
                process9Patch)
    }

    protected void createMergeResourcesTask(BaseVariantData variantData, String location,
                                            boolean process9Patch) {
        def mergeResourcesTask = project.tasks.create("merge${variantData.name}Resources",
                MergeResources)
        variantData.mergeResourcesTask = mergeResourcesTask

        mergeResourcesTask.dependsOn variantData.prepareDependenciesTask, variantData.renderscriptCompileTask
        mergeResourcesTask.plugin = this
        mergeResourcesTask.variant = variantData
        mergeResourcesTask.incrementalFolder =
                project.file("$project.buildDir/incremental/mergeResources/$variantData.dirName")

        mergeResourcesTask.process9Patch = process9Patch

        mergeResourcesTask.conventionMapping.inputResourceSets = {
            variantData.variantConfiguration.getResourceSets(
                    variantData.renderscriptCompileTask.getResOutputDir())
        }

        mergeResourcesTask.conventionMapping.outputDir = { project.file(location) }
    }

    protected void createMergeAssetsTask(BaseVariantData variantData, String location) {
        if (location == null) {
            location = "$project.buildDir/assets/$variantData.dirName"
        }

        def mergeAssetsTask = project.tasks.create("merge${variantData.name}Assets", MergeAssets)
        variantData.mergeAssetsTask = mergeAssetsTask

        mergeAssetsTask.dependsOn variantData.prepareDependenciesTask
        mergeAssetsTask.plugin = this
        mergeAssetsTask.variant = variantData
        mergeAssetsTask.incrementalFolder =
            project.file("$project.buildDir/incremental/mergeAssets/$variantData.dirName")

        mergeAssetsTask.conventionMapping.inputAssetSets = {
            variantData.variantConfiguration.assetSets
        }
        mergeAssetsTask.conventionMapping.outputDir = { project.file(location) }
    }

    protected void createBuildConfigTask(BaseVariantData variantData) {
        def generateBuildConfigTask = project.tasks.create(
                "generate${variantData.name}BuildConfig", GenerateBuildConfig)
        variantData.generateBuildConfigTask = generateBuildConfigTask

        VariantConfiguration variantConfiguration = variantData.variantConfiguration

        if (variantConfiguration.type == VariantConfiguration.Type.TEST) {
            // in case of a test project, the manifest is generated so we need to depend
            // on its creation.
            generateBuildConfigTask.dependsOn variantData.processManifestTask
        }

        generateBuildConfigTask.plugin = this
        generateBuildConfigTask.variant = variantData

        generateBuildConfigTask.conventionMapping.packageName = {
            variantConfiguration.originalPackageName
        }

        generateBuildConfigTask.conventionMapping.debuggable = {
            variantConfiguration.buildType.isDebuggable()
        }

        generateBuildConfigTask.conventionMapping.javaLines = {
            variantConfiguration.buildConfigLines
        }

        generateBuildConfigTask.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/buildConfig/${variantData.dirName}")
        }
    }

    protected void createProcessResTask(BaseVariantData variantData) {
        createProcessResTask(variantData, "$project.buildDir/symbols/$variantData.dirName")
    }

    protected void createProcessResTask(BaseVariantData variantData, final String symbolLocation) {
        def processResources = project.tasks.create("process${variantData.name}Resources",
                ProcessAndroidResources)
        variantData.processResourcesTask = processResources
        processResources.dependsOn variantData.processManifestTask, variantData.mergeResourcesTask, variantData.mergeAssetsTask

        processResources.plugin = this
        processResources.variant = variantData

        VariantConfiguration variantConfiguration = variantData.variantConfiguration

        processResources.conventionMapping.manifestFile = {
            variantData.processManifestTask.manifestOutputFile
        }

        processResources.conventionMapping.resDir = {
            variantData.mergeResourcesTask.outputDir
        }

        processResources.conventionMapping.assetsDir =  {
            variantData.mergeAssetsTask.outputDir
        }

        processResources.conventionMapping.libraries = {
            getTextSymbolDependencies(variantConfiguration.allLibraries)
        }
        processResources.conventionMapping.packageForR = {
            variantConfiguration.originalPackageName
        }

        // TODO: unify with generateBuilderConfig, compileAidl, and library packaging somehow?
        processResources.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/r/$variantData.dirName")
        }
        processResources.conventionMapping.textSymbolOutputDir = {
            project.file(symbolLocation)
        }
        processResources.conventionMapping.packageOutputFile = {
            project.file(
                    "$project.buildDir/libs/${project.archivesBaseName}-${variantData.baseName}.ap_")
        }
        if (variantData.runProguard) {
            processResources.conventionMapping.proguardOutputFile = {
                project.file("$project.buildDir/proguard/${variantData.dirName}/rules.txt")
            }
        }

        processResources.conventionMapping.type = { variantConfiguration.type }
        processResources.conventionMapping.debuggable = { variantConfiguration.buildType.debuggable }
        processResources.conventionMapping.aaptOptions = { extension.aaptOptions }
    }

    protected void createProcessJavaResTask(BaseVariantData variantData) {
        VariantConfiguration variantConfiguration = variantData.variantConfiguration

        Copy processResources = project.tasks.create("process${variantData.name}JavaRes",
                ProcessResources);
        variantData.processJavaResources = processResources

        // set the input
        processResources.from(((AndroidSourceSet) variantConfiguration.defaultSourceSet).resources)

        if (variantConfiguration.type != VariantConfiguration.Type.TEST) {
            processResources.from(
                    ((AndroidSourceSet) variantConfiguration.buildTypeSourceSet).resources)
        }
        if (variantConfiguration.hasFlavors()) {
            for (SourceProvider flavorSourceSet : variantConfiguration.flavorSourceSets) {
                processResources.from(((AndroidSourceSet) flavorSourceSet).resources)
            }
        }

        processResources.conventionMapping.destinationDir = {
            project.file("$project.buildDir/javaResources/$variantData.dirName")
        }
    }

    protected void createAidlTask(BaseVariantData variantData) {
        VariantConfiguration variantConfiguration = variantData.variantConfiguration

        def compileTask = project.tasks.create("compile${variantData.name}Aidl", AidlCompile)
        variantData.aidlCompileTask = compileTask
        variantData.aidlCompileTask.dependsOn variantData.prepareDependenciesTask

        compileTask.plugin = this
        compileTask.variant = variantData
        compileTask.incrementalFolder =
            project.file("$project.buildDir/incremental/aidl/$variantData.dirName")


        compileTask.conventionMapping.sourceDirs = { variantConfiguration.aidlSourceList }
        compileTask.conventionMapping.importDirs = { variantConfiguration.aidlImports }

        compileTask.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/aidl/$variantData.dirName")
        }
    }

    protected void createCompileTask(BaseVariantData variantData,
                                     BaseVariantData testedVariantData) {
        def compileTask = project.tasks.create("compile${variantData.name}", JavaCompile)
        variantData.javaCompileTask = compileTask
        compileTask.dependsOn variantData.processResourcesTask, variantData.generateBuildConfigTask, variantData.aidlCompileTask

        VariantConfiguration config = variantData.variantConfiguration

        List<Object> sourceList = new ArrayList<Object>();
        sourceList.add(((AndroidSourceSet) config.defaultSourceSet).java)
        sourceList.add({ variantData.processResourcesTask.sourceOutputDir })
        sourceList.add({ variantData.generateBuildConfigTask.sourceOutputDir })
        sourceList.add({ variantData.aidlCompileTask.sourceOutputDir })
        sourceList.add({ variantData.renderscriptCompileTask.sourceOutputDir })

        if (config.getType() != VariantConfiguration.Type.TEST) {
            sourceList.add(((AndroidSourceSet) config.buildTypeSourceSet).java)
        }
        if (config.hasFlavors()) {
            for (SourceProvider flavorSourceSet : config.flavorSourceSets) {
                sourceList.add(((AndroidSourceSet) flavorSourceSet).java)
            }
        }
        compileTask.source = sourceList.toArray()

        if (testedVariantData != null) {
            compileTask.classpath = project.files({config.compileClasspath}) + testedVariantData.javaCompileTask.classpath + testedVariantData.javaCompileTask.outputs.files
        } else {
            compileTask.classpath = project.files({config.compileClasspath})
        }

        // TODO - dependency information for the compile classpath is being lost.
        // Add a temporary approximation
        compileTask.dependsOn project.configurations.compile.buildDependencies

        compileTask.conventionMapping.destinationDir = {
            project.file("$project.buildDir/classes/$variantData.dirName")
        }
        compileTask.conventionMapping.dependencyCacheDir = {
            project.file("$project.buildDir/dependency-cache/$variantData.dirName")
        }

        // set source/target compatibility
        compileTask.conventionMapping.sourceCompatibility = {
            extension.compileOptions.sourceCompatibility.toString()
        }
        compileTask.conventionMapping.targetCompatibility = {
            extension.compileOptions.targetCompatibility.toString()
        }

        // setup the boot classpath just before the task actually runs since this will
        // force the sdk to be parsed.
        compileTask.doFirst {
            compileTask.options.bootClasspath = getRuntimeJars(variantData)
        }
    }

    /**
     * Creates the test tasks, and return the main test[*] entry point.
     *
     * The main "test[*]" task can be created two different ways:
     * mainTask is false: this creates the task for the given variant (with its variant name).
     * mainTask is true: this creates the main "test" task, and makes check depend on it.
     *
     * @param variant the test variant
     * @param testedVariant the tested variant
     * @param configDependencies the list of config dependencies
     * @param mainTestTask whether the main task is a main test task.
     * @return the test task.
     */
    protected AndroidTestTask createTestTasks(@NonNull TestVariantData variantData,
                                              @NonNull BaseVariantData testedVariantData,
                                              List<ConfigurationDependencies> configDependencies,
                                              boolean mainTestTask) {
        // The test app is signed with the same info as the tested app so there's no need
        // to test both.
        if (!variantData.isSigned()) {
            throw new GradleException("Tested Variant '${testedVariant.name}' is not configured to create a signed APK.")
        }

        createPrepareDependenciesTask(variantData, configDependencies)

        // Add a task to process the manifest
        createProcessTestManifestTask(variantData, "manifests")

        // Add a task to compile renderscript files.
        createRenderscriptTask(variantData)

        // Add a task to merge the resource folders
        createMergeResourcesTask(variantData, true /*process9Patch*/)

        // Add a task to merge the assets folders
        createMergeAssetsTask(variantData, null /*default location*/)

        if (testedVariantData.variantConfiguration.type == VariantConfiguration.Type.LIBRARY) {
            // in this case the tested library must be fully built before test can be built!
            if (testedVariantData.assembleTask != null) {
                variantData.processManifestTask.dependsOn testedVariantData.assembleTask
                variantData.mergeResourcesTask.dependsOn testedVariantData.assembleTask
            }
        }

        // Add a task to create the BuildConfig class
        createBuildConfigTask(variantData)

        // Add a task to generate resource source files
        createProcessResTask(variantData)

        // process java resources
        createProcessJavaResTask(variantData)

        createAidlTask(variantData)

        // Add a task to compile the test application
        createCompileTask(variantData, testedVariantData)

        addPackageTasks(variantData, null)

        if (assembleTest != null) {
            assembleTest.dependsOn variantData.assembleTask
        }

        // create the check task for this test
        def testFlavorTask = project.tasks.create(
                mainTestTask ? INSTRUMENTATION_TEST : "$INSTRUMENTATION_TEST${testedVariantData.name}",
                mainTestTask ? TestLibraryTask : TestFlavorTask)
        testFlavorTask.description = "Installs and runs the tests for Build ${testedVariantData.name}."
        testFlavorTask.group = JavaBasePlugin.VERIFICATION_GROUP
        testFlavorTask.dependsOn testedVariantData.assembleTask, variantData.assembleTask

        if (mainTestTask) {
            deviceCheck.dependsOn testFlavorTask
        }

        testFlavorTask.plugin = this
        testFlavorTask.variant = variantData
        testFlavorTask.testedVariantData = testedVariantData
        testFlavorTask.flavorName = variantData.flavorName

        testFlavorTask.conventionMapping.adbExe = { androidSdkParser.adb }

        testFlavorTask.conventionMapping.testApp = { variantData.outputFile }
        if (testedVariantData.variantConfiguration.type != VariantConfiguration.Type.LIBRARY) {
            testFlavorTask.conventionMapping.testedApp = { testedVariantData.outputFile }
        }

        testFlavorTask.conventionMapping.resultsDir = {
            String rootLocation = extension.testOptions.resultsDir != null ?
                extension.testOptions.resultsDir : "$project.buildDir/$INSTRUMENTATION_RESULTS"

            String flavorFolder = variantData.flavorDirName
            if (!flavorFolder.isEmpty()) {
                flavorFolder = "$FLAVORS/" + flavorFolder
            }

            project.file("$rootLocation/$flavorFolder")
        }
        testFlavorTask.conventionMapping.reportsDir = {
            String rootLocation = extension.testOptions.reportDir != null ?
                extension.testOptions.reportDir : "$project.buildDir/$REPORTS/$INSTRUMENTATION_TESTS"

            String flavorFolder = variantData.flavorDirName
            if (!flavorFolder.isEmpty()) {
                flavorFolder = "$FLAVORS/" + flavorFolder
            }

            project.file("$rootLocation/$flavorFolder")
        }
        variantData.testFlavorTask = testFlavorTask

        return testFlavorTask
    }

    /**
     * Creates the packaging tasks for the given Variant.
     * @param variantData the variant data.
     * @param assembleTask an optional assembleTask to be used. If null a new one is created. The
     *                assembleTask is always set in the Variant.
     */
    protected void addPackageTasks(ApkVariantData variantData, Task assembleTask) {
        // Add a dex task
        def dexTaskName = "dex${variantData.name}"
        def dexTask = project.tasks.create(dexTaskName, Dex)
        variantData.dexTask = dexTask
        dexTask.dependsOn variantData.javaCompileTask

        dexTask.plugin = this
        dexTask.variant = variantData
        dexTask.incrementalFolder =
                project.file("$project.buildDir/incremental/dex/$variantData.dirName")

        dexTask.conventionMapping.libraries = { project.files({ variantData.variantConfiguration.packagedJars }) }
        dexTask.conventionMapping.sourceFiles = { variantData.javaCompileTask.outputs.files } // this creates a dependency
        dexTask.conventionMapping.outputFile = {
            project.file(
                    "${project.buildDir}/libs/${project.archivesBaseName}-${variantData.baseName}.dex")
        }
        dexTask.dexOptions = extension.dexOptions

        // Add a task to generate application package
        def packageApp = project.tasks.create("package${variantData.name}", PackageApplication)
        variantData.packageApplicationTask = packageApp
        packageApp.dependsOn variantData.processResourcesTask, dexTask, variantData.processJavaResources

        packageApp.plugin = this
        packageApp.variant = variantData

        VariantConfiguration config = variantData.variantConfiguration

        packageApp.conventionMapping.resourceFile = {
            variantData.processResourcesTask.packageOutputFile
        }
        packageApp.conventionMapping.dexFile = { dexTask.outputFile }

        packageApp.conventionMapping.packagedJars = { config.packagedJars }

        packageApp.conventionMapping.javaResourceDir = {
            getOptionalDir(variantData.processJavaResources.destinationDir)
        }

        packageApp.conventionMapping.jniDebugBuild = { config.buildType.jniDebugBuild }

        SigningConfigDsl sc = (SigningConfigDsl) config.signingConfig
        packageApp.conventionMapping.signingConfig = { sc }
        if (sc != null) {
            ValidateSigningTask validateSigningTask = validateSigningTaskMap.get(sc)
            if (validateSigningTask == null) {
                validateSigningTask = project.tasks.create("validate${sc.name.capitalize()}Signing",
                    ValidateSigningTask)
                validateSigningTask.plugin = this
                validateSigningTask.signingConfig = sc

                validateSigningTaskMap.put(sc, validateSigningTask)
            }

            packageApp.dependsOn validateSigningTask
        }

        def signedApk = variantData.isSigned()
        def apkName = signedApk ?
            "${project.archivesBaseName}-${variantData.baseName}-unaligned.apk" :
            "${project.archivesBaseName}-${variantData.baseName}-unsigned.apk"

        packageApp.conventionMapping.outputFile = {
            project.file("$project.buildDir/apk/${apkName}")
        }

        def appTask = packageApp
        variantData.outputFile = project.file("$project.buildDir/apk/${apkName}")

        if (signedApk) {
            if (variantData.zipAlign) {
                // Add a task to zip align application package
                def zipAlignTask = project.tasks.create("zipalign${variantData.name}", ZipAlign)
                variantData.zipAlignTask = zipAlignTask

                zipAlignTask.dependsOn packageApp
                zipAlignTask.conventionMapping.inputFile = { packageApp.outputFile }
                zipAlignTask.conventionMapping.outputFile = {
                    project.file(
                            "$project.buildDir/apk/${project.archivesBaseName}-${variantData.baseName}.apk")
                }
                zipAlignTask.conventionMapping.zipAlignExe = { androidSdkParser.zipAlign }

                appTask = zipAlignTask
                variantData.outputFile = project.file(
                        "$project.buildDir/apk/${project.archivesBaseName}-${variantData.baseName}.apk")
            }

            // Add a task to install the application package
            def installTask = project.tasks.create("install${variantData.name}", InstallTask)
            installTask.description = "Installs the " + variantData.description
            installTask.group = INSTALL_GROUP
            installTask.dependsOn appTask
            installTask.conventionMapping.packageFile = { appTask.outputFile }
            installTask.conventionMapping.adbExe = { androidSdkParser.adb }

            variantData.installTask = installTask
        }

        // Add an assemble task
        if (assembleTask == null) {
            assembleTask = project.tasks.create("assemble${variantData.name}")
            assembleTask.description = "Assembles the " + variantData.description
            assembleTask.group = org.gradle.api.plugins.BasePlugin.BUILD_GROUP
        }
        assembleTask.dependsOn appTask
        variantData.assembleTask = assembleTask

        // add an uninstall task
        def uninstallTask = project.tasks.create("uninstall${variantData.name}", UninstallTask)
        uninstallTask.description = "Uninstalls the " + variantData.description
        uninstallTask.group = INSTALL_GROUP
        uninstallTask.variant = variantData
        uninstallTask.conventionMapping.adbExe = { androidSdkParser.adb }

        variantData.uninstallTask = uninstallTask
        uninstallAll.dependsOn uninstallTask
    }

    private void createReportTasks() {
        def dependencyReportTask = project.tasks.create("androidDependencies", DependencyReportTask)
        dependencyReportTask.setDescription("Displays the Android dependencies of the project")
        dependencyReportTask.setVariants(variantDataList)
        dependencyReportTask.setGroup("Android")

        def signingReportTask = project.tasks.create("signingReport", SigningReportTask)
        signingReportTask.setDescription("Displays the signing info for each variant")
        signingReportTask.setVariants(variantDataList)
        signingReportTask.setGroup("Android")
    }

    protected void createPrepareDependenciesTask(
            @NonNull BaseVariantData variantData,
            @NonNull List<ConfigurationDependencies> configDependenciesList) {
        def prepareDependenciesTask = project.tasks.create("prepare${variantData.name}Dependencies",
                PrepareDependenciesTask)
        variantData.prepareDependenciesTask = prepareDependenciesTask

        prepareDependenciesTask.plugin = this
        prepareDependenciesTask.variant = variantData

        // for all libraries required by the configurations of this variant, make this task
        // depend on all the tasks preparing these libraries.
        for (ConfigurationDependencies configDependencies : configDependenciesList) {
            prepareDependenciesTask.addChecker(configDependencies.checker)

            for (LibraryDependencyImpl lib : configDependencies.libraries) {
                addDependencyToPrepareTask(prepareDependenciesTask, lib)
            }
        }
    }

    def addDependencyToPrepareTask(PrepareDependenciesTask prepareDependenciesTask,
                                   LibraryDependencyImpl lib) {
        def prepareLibTask = prepareTaskMap.get(lib)
        if (prepareLibTask != null) {
            prepareDependenciesTask.dependsOn prepareLibTask
        }

        for (LibraryDependencyImpl childLib : lib.dependencies) {
            addDependencyToPrepareTask(prepareDependenciesTask, childLib)
        }
    }

    def resolveDependencies(List<ConfigurationDependencies> configs) {
        Map<ModuleVersionIdentifier, List<LibraryDependencyImpl>> modules = [:]
        Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts = [:]
        Multimap<LibraryDependency, ConfigurationDependencies> reverseMap = ArrayListMultimap.create()

        // start with the default config and its test
        resolveDependencyForConfig(defaultConfigData, modules, artifacts, reverseMap)
        resolveDependencyForConfig(defaultConfigData.testConfigDependencies, modules, artifacts,
                reverseMap)

        // and then loop on all the other configs
        for (ConfigurationDependencies config : configs) {
            resolveDependencyForConfig(config, modules, artifacts, reverseMap)
            if (config.testConfigDependencies != null) {
                resolveDependencyForConfig(config.testConfigDependencies, modules, artifacts,
                        reverseMap)
            }
        }

        modules.values().each { List list ->
            if (!list.isEmpty()) {
                // get the first item only
                LibraryDependencyImpl androidDependency = (LibraryDependencyImpl) list.get(0)

                String bundleName = GUtil.toCamelCase(androidDependency.name.replaceAll("\\:", " "))

                def prepareLibraryTask = project.tasks.create("prepare${bundleName}Library",
                        PrepareLibraryTask)
                prepareLibraryTask.description = "Prepare ${androidDependency.name}"
                prepareLibraryTask.bundle = androidDependency.bundle
                prepareLibraryTask.explodedDir = androidDependency.bundleFolder

                // Use the reverse map to find all the configurations that included this android
                // library so that we can make sure they are built.
                List<ConfigurationDependencies> configDepList = reverseMap.get(androidDependency)
                if (configDepList != null && !configDepList.isEmpty()) {
                    for (ConfigurationDependencies configDependencies: configDepList) {
                        prepareLibraryTask.dependsOn configDependencies.configuration.buildDependencies
                    }
                }

                prepareTaskMap.put(androidDependency, prepareLibraryTask)
            }
        }
    }

    def resolveDependencyForConfig(
            ConfigurationDependencies configDependencies,
            Map<ModuleVersionIdentifier, List<LibraryDependencyImpl>> modules,
            Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts,
            Multimap<LibraryDependency, ConfigurationDependencies> reverseMap) {

        // TODO support package configuration
        def compileClasspath = configDependencies.configuration

        // TODO - shouldn't need to do this - fix this in Gradle
        ensureConfigured(compileClasspath)

        configDependencies.checker = new DependencyChecker(configDependencies, logger)

        // TODO - defer downloading until required -- This is hard to do as we need the info to build the variant config.
        List<LibraryDependencyImpl> bundles = []
        List<JarDependency> jars = []
        List<JarDependency> localJars = []
        collectArtifacts(compileClasspath, artifacts)
        compileClasspath.incoming.resolutionResult.root.dependencies.each { ResolvedDependencyResult dep ->
            addDependency(dep.selected, configDependencies, bundles, jars, modules,
                    artifacts, reverseMap)
        }

        // also need to process local jar files, as they are not processed by the
        // resolvedConfiguration result. This only includes the local jar files for this project.
        compileClasspath.allDependencies.each { dep ->
            if (dep instanceof SelfResolvingDependency &&
                    !(dep instanceof ProjectDependency)) {
                Set<File> files = ((SelfResolvingDependency) dep).resolve()
                for (File f : files) {
                    localJars << new JarDependency(f)
                }
            }
        }

        configDependencies.addLibraries(bundles)
        configDependencies.addJars(jars)
        configDependencies.addLocalJars(localJars)

        // TODO - filter bundles out of source set classpath

        configureBuild(configDependencies)
    }

    def ensureConfigured(Configuration config) {
        config.allDependencies.withType(ProjectDependency).each { dep ->
            project.evaluationDependsOn(dep.dependencyProject.path)
            ensureConfigured(dep.projectConfiguration)
        }
    }

    static def collectArtifacts(Configuration configuration, Map<ModuleVersionIdentifier,
                         List<ResolvedArtifact>> artifacts) {
        configuration.resolvedConfiguration.resolvedArtifacts.each { ResolvedArtifact artifact ->
            def id = artifact.moduleVersion.id
            List<ResolvedArtifact> moduleArtifacts = artifacts[id]
            if (moduleArtifacts == null) {
                moduleArtifacts = []
                artifacts[id] = moduleArtifacts
            }
            moduleArtifacts << artifact
        }
    }

    def addDependency(ResolvedModuleVersionResult moduleVersion,
                      ConfigurationDependencies configDependencies,
                      Collection<LibraryDependencyImpl> bundles,
                      List<JarDependency> jars,
                      Map<ModuleVersionIdentifier, List<LibraryDependencyImpl>> modules,
                      Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts,
                      Multimap<LibraryDependency, ConfigurationDependencies> reverseMap) {
        def id = moduleVersion.id
        if (configDependencies.checker.excluded(id)) {
            return
        }

        List<LibraryDependencyImpl> bundlesForThisModule = modules[id]
        if (bundlesForThisModule == null) {
            bundlesForThisModule = []
            modules[id] = bundlesForThisModule

            def nestedBundles = []
            moduleVersion.dependencies.each { ResolvedDependencyResult dep ->
                addDependency(dep.selected, configDependencies, nestedBundles,
                        jars, modules, artifacts, reverseMap)
            }

            def moduleArtifacts = artifacts[id]
            moduleArtifacts?.each { artifact ->
                if (artifact.type == EXT_LIB_ARCHIVE) {
                    def explodedDir = project.file(
                            "$project.buildDir/exploded-bundles/$artifact.file.name")
                    LibraryDependencyImpl adep = new LibraryDependencyImpl(
                            explodedDir, nestedBundles, artifact.file,
                            id.group + ":" + id.name + ":" + id.version)
                    bundlesForThisModule << adep
                    reverseMap.put(adep, configDependencies)
                } else {
                    // TODO - need the correct values for the boolean flags
                    jars << new JarDependency(artifact.file)
                }
            }

            if (bundlesForThisModule.empty && !nestedBundles.empty) {
                throw new GradleException("Module version $id depends on libraries but is not a library itself")
            }
        } else {
            for (LibraryDependency adep : bundlesForThisModule) {
                reverseMap.put(adep, configDependencies)
            }
        }

        bundles.addAll(bundlesForThisModule)
    }

    private void configureBuild(ConfigurationDependencies configurationDependencies) {
        def configuration = configurationDependencies.configuration

        addDependsOnTaskInOtherProjects(
                project.getTasks().getByName(JavaBasePlugin.BUILD_NEEDED_TASK_NAME), true,
                JavaBasePlugin.BUILD_NEEDED_TASK_NAME, "compile");
        addDependsOnTaskInOtherProjects(
                project.getTasks().getByName(JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME), false,
                JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME, "compile");
    }

    /**
     * Adds a dependency on tasks with the specified name in other projects.  The other projects
     * are determined from project lib dependencies using the specified configuration name.
     * These may be projects this project depends on or projects that depend on this project
     * based on the useDependOn argument.
     *
     * @param task Task to add dependencies to
     * @param useDependedOn if true, add tasks from projects this project depends on, otherwise
     * use projects that depend on this one.
     * @param otherProjectTaskName name of task in other projects
     * @param configurationName name of configuration to use to find the other projects
     */
    private static void addDependsOnTaskInOtherProjects(final Task task, boolean useDependedOn,
                                                 String otherProjectTaskName,
                                                 String configurationName) {
        Project project = task.getProject();
        final Configuration configuration = project.getConfigurations().getByName(
                configurationName);
        task.dependsOn(configuration.getTaskDependencyFromProjectDependency(
                useDependedOn, otherProjectTaskName));
    }

    protected static File getOptionalDir(File dir) {
        if (dir.isDirectory()) {
            return dir
        }

        return null
    }

    @NonNull
    protected List<ManifestDependencyImpl> getManifestDependencies(
            List<LibraryDependency> libraries) {

        List<ManifestDependencyImpl> list = Lists.newArrayListWithCapacity(libraries.size())

        for (LibraryDependency lib : libraries) {
            // get the dependencies
            List<ManifestDependencyImpl> children = getManifestDependencies(lib.dependencies)
            list.add(new ManifestDependencyImpl(lib.manifest, children))
        }

        return list
    }

    @NonNull
    protected static List<SymbolFileProviderImpl> getTextSymbolDependencies(
            List<LibraryDependency> libraries) {

        List<SymbolFileProviderImpl> list = Lists.newArrayListWithCapacity(libraries.size())

        for (LibraryDependency lib : libraries) {
            list.add(new SymbolFileProviderImpl(lib.manifest, lib.symbolFile))
        }

        return list
    }
}

