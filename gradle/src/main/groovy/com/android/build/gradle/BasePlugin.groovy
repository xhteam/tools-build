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
import com.android.build.gradle.internal.ApplicationVariant
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.ProductionAppVariant
import com.android.build.gradle.internal.TestAppVariant
import com.android.build.gradle.internal.dependency.AndroidDependencyImpl
import com.android.build.gradle.internal.dependency.ConfigurationDependencies
import com.android.build.gradle.internal.dependency.DependencyChecker
import com.android.build.gradle.internal.dependency.ManifestDependencyImpl
import com.android.build.gradle.internal.dependency.SymbolFileProviderImpl
import com.android.build.gradle.internal.dsl.SigningConfigDsl
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
import com.android.builder.AndroidDependency
import com.android.builder.BuilderConstants
import com.android.builder.DefaultSdkParser
import com.android.builder.JarDependency
import com.android.builder.ManifestDependency
import com.android.builder.PlatformSdkParser
import com.android.builder.ProductFlavor
import com.android.builder.SdkParser
import com.android.builder.SourceProvider
import com.android.builder.VariantConfiguration
import com.android.builder.signing.SigningConfig
import com.android.utils.ILogger
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Lists
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
import org.gradle.api.internal.plugins.ProcessResources
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.reflect.Instantiator
import org.gradle.tooling.BuildException
import org.gradle.util.GUtil
/**
 * Base class for all Android plugins
 */
public abstract class BasePlugin {
    public static final String[] GRADLE_SUPPORTED_VERSIONS = [ "1.3", "1.4" ]
    public static final String GRADLE_MIN_VERSION = "1.3"

    public static final String INSTALL_GROUP = "Install"

    protected static File TEST_SDK_DIR;

    protected Instantiator instantiator

    private final Map<Object, AndroidBuilder> builders = [:]

    final List<ApplicationVariant> variants = []
    final Map<AndroidDependencyImpl, PrepareLibraryTask> prepareTaskMap = [:]
    final Map<SigningConfig, ValidateSigningTask> validateSigningTaskMap = [:]

    protected Project project
    protected SdkParser androidSdkParser
    private LoggerWrapper loggerWrapper

    private boolean hasCreatedTasks = false

    private ProductFlavorData<ProductFlavor> defaultConfigData
    protected AndroidSourceSet mainSourceSet
    protected AndroidSourceSet testSourceSet

    protected Task uninstallAll
    protected Task assembleTest
    protected Task deviceCheck

    protected abstract String getTarget()

    protected BasePlugin(Instantiator instantiator) {
        this.instantiator = instantiator
    }

    protected abstract BaseExtension getExtension()
    protected abstract void doCreateAndroidTasks()

    protected void apply(Project project) {
        this.project = project

        checkGradleVersion()

        project.apply plugin: JavaBasePlugin

        project.tasks.assemble.description =
            "Assembles all variants of all applications and secondary packages."

        uninstallAll = project.tasks.add("uninstallAll")
        uninstallAll.description = "Uninstall all applications."
        uninstallAll.group = INSTALL_GROUP

        deviceCheck = project.tasks.add("deviceCheck")
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
        findSdk(project)

        if (hasCreatedTasks) {
            return
        }
        hasCreatedTasks = true

        doCreateAndroidTasks()
        createReportTasks()
    }

    protected setDefaultConfig(ProductFlavor defaultConfig,
                               NamedDomainObjectContainer<AndroidSourceSet> sourceSets) {
        mainSourceSet = sourceSets.create(defaultConfig.name)
        testSourceSet = sourceSets.create(BuilderConstants.TEST)

        defaultConfigData = new ProductFlavorData<ProductFlavor>(defaultConfig, mainSourceSet,
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

    AndroidBuilder getAndroidBuilder(ApplicationVariant variant) {
        AndroidBuilder androidBuilder = builders.get(variant)

        if (androidBuilder == null) {
            androidBuilder = variant.createBuilder(this)
            builders.put(variant, androidBuilder)
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

    protected String getRuntimeJars(ApplicationVariant variant) {
        AndroidBuilder androidBuilder = getAndroidBuilder(variant)

        return androidBuilder.runtimeClasspath.join(File.pathSeparator)
    }

    protected void createProcessManifestTask(ApplicationVariant variant,
                                             String manifestOurDir) {
        def processManifestTask = project.tasks.add("process${variant.name}Manifest",
                ProcessAppManifest)
        variant.processManifestTask = processManifestTask
        processManifestTask.dependsOn variant.prepareDependenciesTask

        processManifestTask.plugin = this
        processManifestTask.variant = variant

        VariantConfiguration config = variant.config
        ProductFlavor mergedFlavor = config.mergedFlavor

        processManifestTask.conventionMapping.mainManifest = {
            config.mainManifest
        }
        processManifestTask.conventionMapping.manifestOverlays = {
            config.manifestOverlays
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
        processManifestTask.conventionMapping.outManifest = {
            project.file(
                    "$project.buildDir/${manifestOurDir}/$variant.dirName/AndroidManifest.xml")
        }
    }

    protected void createProcessTestManifestTask(ApplicationVariant variant,
                                                 String manifestOurDir) {
        def processTestManifestTask = project.tasks.add("process${variant.name}TestManifest",
                ProcessTestManifest)
        variant.processManifestTask = processTestManifestTask
        processTestManifestTask.dependsOn variant.prepareDependenciesTask

        processTestManifestTask.plugin = this
        processTestManifestTask.variant = variant

        VariantConfiguration config = variant.config

        processTestManifestTask.conventionMapping.testPackageName = {
            config.packageName
        }
        processTestManifestTask.conventionMapping.minSdkVersion = {
            config.minSdkVersion
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
        processTestManifestTask.conventionMapping.outManifest = {
            project.file(
                    "$project.buildDir/${manifestOurDir}/$variant.dirName/AndroidManifest.xml")
        }
    }

    protected void createRenderscriptTask(ApplicationVariant variant) {
        VariantConfiguration config = variant.config

        def renderscriptTask = project.tasks.add("compile${variant.name}Renderscript",
                RenderscriptCompile)
        variant.renderscriptCompileTask = renderscriptTask

        renderscriptTask.dependsOn variant.prepareDependenciesTask
        renderscriptTask.plugin = this
        renderscriptTask.variant = variant

        renderscriptTask.targetApi = config.mergedFlavor.renderscriptTargetApi
        renderscriptTask.debugBuild = config.buildType.renderscriptDebugBuild
        renderscriptTask.optimLevel = config.buildType.renderscriptOptimLevel

        renderscriptTask.conventionMapping.sourceDirs = { config.renderscriptSourceList }
        renderscriptTask.conventionMapping.importDirs = { config.renderscriptImports }

        renderscriptTask.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/rs/$variant.dirName")
        }
        renderscriptTask.conventionMapping.resOutputDir = {
            project.file("$project.buildDir/res/rs/$variant.dirName")
        }
    }

    protected void createMergeResourcesTask(ApplicationVariant variant, boolean process9Patch) {
        createMergeResourcesTask(variant, "$project.buildDir/res/all/$variant.dirName",
                process9Patch)
    }

    protected void createMergeResourcesTask(ApplicationVariant variant, String location,
                                            boolean process9Patch) {
        def mergeResourcesTask = project.tasks.add("merge${variant.name}Resources", MergeResources)
        variant.mergeResourcesTask = mergeResourcesTask

        mergeResourcesTask.dependsOn variant.prepareDependenciesTask, variant.renderscriptCompileTask
        mergeResourcesTask.plugin = this
        mergeResourcesTask.variant = variant
        mergeResourcesTask.incrementalFolder =
                project.file("$project.buildDir/incremental/mergeResources/$variant.dirName")

        mergeResourcesTask.process9Patch = process9Patch

        mergeResourcesTask.conventionMapping.inputResourceSets = {
            variant.config.getResourceSets(variant.renderscriptCompileTask.getResOutputDir())
        }

        mergeResourcesTask.conventionMapping.outputDir = { project.file(location) }
    }

    protected void createMergeAssetsTask(ApplicationVariant variant, String location) {
        if (location == null) {
            location = "$project.buildDir/assets/$variant.dirName"
        }

        def mergeAssetsTask = project.tasks.add("merge${variant.name}Assets", MergeAssets)
        variant.mergeAssetsTask = mergeAssetsTask

        mergeAssetsTask.dependsOn variant.prepareDependenciesTask
        mergeAssetsTask.plugin = this
        mergeAssetsTask.variant = variant
        mergeAssetsTask.incrementalFolder =
            project.file("$project.buildDir/incremental/mergeAssets/$variant.dirName")

        mergeAssetsTask.conventionMapping.inputAssetSets = { variant.config.assetSets }
        mergeAssetsTask.conventionMapping.outputDir = { project.file(location) }
    }

    protected void createBuildConfigTask(ApplicationVariant variant) {
        def generateBuildConfigTask = project.tasks.add(
                "generate${variant.name}BuildConfig", GenerateBuildConfig)
        variant.generateBuildConfigTask = generateBuildConfigTask
        if (variant.config.type == VariantConfiguration.Type.TEST) {
            // in case of a test project, the manifest is generated so we need to depend
            // on its creation.
            generateBuildConfigTask.dependsOn variant.processManifestTask
        }

        generateBuildConfigTask.plugin = this
        generateBuildConfigTask.variant = variant

        generateBuildConfigTask.conventionMapping.packageName = {
            variant.config.originalPackageName
        }

        generateBuildConfigTask.conventionMapping.debuggable = {
            variant.config.buildType.isDebuggable()
        }

        generateBuildConfigTask.conventionMapping.javaLines = {
            variant.config.buildConfigLines
        }

        generateBuildConfigTask.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/buildConfig/${variant.dirName}")
        }
    }

    protected void createProcessResTask(ApplicationVariant variant) {
        createProcessResTask(variant, "$project.buildDir/symbols/$variant.dirName")
    }

    protected void createProcessResTask(ApplicationVariant variant, final String symbolLocation) {
        def processResources = project.tasks.add("process${variant.name}Resources",
                ProcessAndroidResources)
        variant.processResourcesTask = processResources
        processResources.dependsOn variant.processManifestTask, variant.mergeResourcesTask, variant.mergeAssetsTask

        processResources.plugin = this
        processResources.variant = variant

        VariantConfiguration config = variant.config

        processResources.conventionMapping.manifestFile = {
            variant.processManifestTask.outManifest
        }

        processResources.conventionMapping.resFolder = {
            variant.mergeResourcesTask.outputDir
        }

        processResources.conventionMapping.assetsDir =  {
            variant.mergeAssetsTask.outputDir
        }

        processResources.conventionMapping.libraries = {
            getTextSymbolDependencies(config.allLibraries)
        }
        processResources.conventionMapping.packageOverride = {
            if (config.testedConfig != null) {
                return config.testedConfig.packageOverride
            }
            config.packageOverride
        }

        // TODO: unify with generateBuilderConfig, compileAidl, and library packaging somehow?
        processResources.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/r/$variant.dirName")
        }
        processResources.conventionMapping.textSymbolDir = {
            project.file(symbolLocation)
        }
        processResources.conventionMapping.packageFile = {
            project.file(
                    "$project.buildDir/libs/${project.archivesBaseName}-${variant.baseName}.ap_")
        }
        if (variant.runProguard) {
            processResources.conventionMapping.proguardFile = {
                project.file("$project.buildDir/proguard/${variant.dirName}/rules.txt")
            }
        }

        processResources.conventionMapping.type = { config.type }
        processResources.conventionMapping.debuggable = { config.buildType.debuggable }
        processResources.conventionMapping.aaptOptions = { extension.aaptOptions }
    }

    protected void createProcessJavaResTask(ApplicationVariant variant) {
        VariantConfiguration config = variant.config

        Copy processResources = project.tasks.add("process${variant.name}JavaRes",
                ProcessResources);
        variant.processJavaResources = processResources

        // set the input
        processResources.from(((AndroidSourceSet) config.defaultSourceSet).resources)

        if (config.getType() != VariantConfiguration.Type.TEST) {
            processResources.from(((AndroidSourceSet) config.buildTypeSourceSet).resources)
        }
        if (config.hasFlavors()) {
            for (SourceProvider flavorSourceSet : config.flavorSourceSets) {
                processResources.from(((AndroidSourceSet) flavorSourceSet).resources)
            }
        }

        processResources.conventionMapping.destinationDir = {
            project.file("$project.buildDir/javaResources/$variant.dirName")
        }
    }

    protected void createAidlTask(ApplicationVariant variant) {
        VariantConfiguration config = variant.config

        def compileTask = project.tasks.add("compile${variant.name}Aidl", AidlCompile)
        variant.aidlCompileTask = compileTask
        variant.aidlCompileTask.dependsOn variant.prepareDependenciesTask

        compileTask.plugin = this
        compileTask.variant = variant
        compileTask.incrementalFolder =
            project.file("$project.buildDir/incremental/aidl/$variant.dirName")


        compileTask.conventionMapping.sourceDirs = { config.aidlSourceList }
        compileTask.conventionMapping.importDirs = { config.aidlImports }

        compileTask.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/aidl/$variant.dirName")
        }
    }

    protected void createCompileTask(ApplicationVariant variant,
                                     ApplicationVariant testedVariant) {
        def compileTask = project.tasks.add("compile${variant.name}", JavaCompile)
        variant.javaCompileTask = compileTask
        compileTask.dependsOn variant.processResourcesTask, variant.generateBuildConfigTask, variant.aidlCompileTask

        VariantConfiguration config = variant.config

        List<Object> sourceList = new ArrayList<Object>();
        sourceList.add(((AndroidSourceSet) config.defaultSourceSet).java)
        sourceList.add({ variant.processResourcesTask.sourceOutputDir })
        sourceList.add({ variant.generateBuildConfigTask.sourceOutputDir })
        sourceList.add({ variant.aidlCompileTask.sourceOutputDir })
        sourceList.add({ variant.renderscriptCompileTask.sourceOutputDir })

        if (config.getType() != VariantConfiguration.Type.TEST) {
            sourceList.add(((AndroidSourceSet) config.buildTypeSourceSet).java)
        }
        if (config.hasFlavors()) {
            for (SourceProvider flavorSourceSet : config.flavorSourceSets) {
                sourceList.add(((AndroidSourceSet) flavorSourceSet).java)
            }
        }
        compileTask.source = sourceList.toArray()

        if (testedVariant != null) {
            compileTask.classpath = project.files({config.compileClasspath}) + testedVariant.javaCompileTask.classpath + testedVariant.javaCompileTask.outputs.files
        } else {
            compileTask.classpath = project.files({config.compileClasspath})
        }

        // TODO - dependency information for the compile classpath is being lost.
        // Add a temporary approximation
        compileTask.dependsOn project.configurations.compile.buildDependencies

        compileTask.conventionMapping.destinationDir = {
            project.file("$project.buildDir/classes/$variant.dirName")
        }
        compileTask.conventionMapping.dependencyCacheDir = {
            project.file("$project.buildDir/dependency-cache/$variant.dirName")
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
            compileTask.options.bootClasspath = getRuntimeJars(variant)
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
    protected AndroidTestTask createTestTasks(TestAppVariant variant,
                                              ProductionAppVariant testedVariant,
                                              List<ConfigurationDependencies> configDependencies,
                                              boolean mainTestTask) {
        // The test app is signed with the same info as the tested app so there's no need
        // to test both.
        if (!testedVariant.isSigned()) {
            throw new GradleException("Tested Variant '${testedVariant.name}' is not configured to create a signed APK.")
        }

        createPrepareDependenciesTask(variant, configDependencies)

        // Add a task to process the manifest
        createProcessTestManifestTask(variant, "manifests")

        // Add a task to compile renderscript files.
        createRenderscriptTask(variant)

        // Add a task to merge the resource folders
        createMergeResourcesTask(variant, true /*process9Patch*/)

        // Add a task to merge the assets folders
        createMergeAssetsTask(variant, null /*default location*/)

        if (testedVariant.config.type == VariantConfiguration.Type.LIBRARY) {
            // in this case the tested library must be fully built before test can be built!
            if (testedVariant.assembleTask != null) {
                variant.processManifestTask.dependsOn testedVariant.assembleTask
                variant.mergeResourcesTask.dependsOn testedVariant.assembleTask
            }
        }

        // Add a task to create the BuildConfig class
        createBuildConfigTask(variant)

        // Add a task to generate resource source files
        createProcessResTask(variant)

        // process java resources
        createProcessJavaResTask(variant)

        createAidlTask(variant)

        // Add a task to compile the test application
        createCompileTask(variant, testedVariant)

        addPackageTasks(variant, null)

        if (assembleTest != null) {
            assembleTest.dependsOn variant.assembleTask
        }

        // create the check task for this test
        def testFlavorTask = project.tasks.add(
                mainTestTask ? "instrumentationTest" : "instrumentationTest${testedVariant.name}",
                mainTestTask ? TestLibraryTask : TestFlavorTask)
        testFlavorTask.description = "Installs and runs the tests for Build ${testedVariant.name}."
        testFlavorTask.group = JavaBasePlugin.VERIFICATION_GROUP
        testFlavorTask.dependsOn testedVariant.assembleTask, variant.assembleTask

        if (mainTestTask) {
            deviceCheck.dependsOn testFlavorTask
        }

        testFlavorTask.plugin = this
        testFlavorTask.variant = variant
        testFlavorTask.testedVariant = testedVariant
        testFlavorTask.flavorName = variant.flavorName

        testFlavorTask.conventionMapping.adbExe = { androidSdkParser.adb }

        testFlavorTask.conventionMapping.testApp = { variant.outputFile }
        if (testedVariant.config.type != VariantConfiguration.Type.LIBRARY) {
            testFlavorTask.conventionMapping.testedApp = { testedVariant.outputFile }
        }

        testFlavorTask.conventionMapping.resultsDir = {
            String rootLocation = extension.testOptions.resultsDir != null ?
                extension.testOptions.resultsDir : "$project.buildDir/test-results"

            project.file("$rootLocation/flavors/$variant.flavorDirName")
        }
        testFlavorTask.conventionMapping.reportsDir = {
            String rootLocation = extension.testOptions.reportDir != null ?
                extension.testOptions.reportDir : "$project.buildDir/reports/tests"

            project.file("$rootLocation/flavors/$variant.flavorDirName")
        }
        variant.testFlavorTask = testFlavorTask

        return testFlavorTask
    }

    /**
     * Creates the packaging tasks for the given Variant.
     * @param variant the variant.
     * @param assembleTask an optional assembleTask to be used. If null a new one is created. The
     *                assembleTask is always set in the Variant.
     */
    protected void addPackageTasks(ApplicationVariant variant, Task assembleTask) {
        // Add a dex task
        def dexTaskName = "dex${variant.name}"
        def dexTask = project.tasks.add(dexTaskName, Dex)
        variant.dexTask = dexTask
        dexTask.dependsOn variant.javaCompileTask

        dexTask.plugin = this
        dexTask.variant = variant
        dexTask.incrementalFolder =
                project.file("$project.buildDir/incremental/dex/$variant.dirName")

        dexTask.conventionMapping.libraries = { project.files({ variant.config.packagedJars }) }
        dexTask.conventionMapping.sourceFiles = { variant.javaCompileTask.outputs.files } // this creates a dependency
        dexTask.conventionMapping.outputFile = {
            project.file(
                    "${project.buildDir}/libs/${project.archivesBaseName}-${variant.baseName}.dex")
        }
        dexTask.dexOptions = extension.dexOptions

        // Add a task to generate application package
        def packageApp = project.tasks.add("package${variant.name}", PackageApplication)
        variant.packageApplicationTask = packageApp
        packageApp.dependsOn variant.processResourcesTask, dexTask, variant.processJavaResources

        packageApp.plugin = this
        packageApp.variant = variant

        VariantConfiguration config = variant.config

        packageApp.conventionMapping.resourceFile = { variant.processResourcesTask.packageFile }
        packageApp.conventionMapping.dexFile = { dexTask.outputFile }

        packageApp.conventionMapping.packagedJars = { config.packagedJars }

        packageApp.conventionMapping.javaResourceDir = {
            getOptionalDir(variant.processJavaResources.destinationDir)
        }

        packageApp.conventionMapping.jniDebugBuild = { config.buildType.jniDebugBuild }

        SigningConfigDsl sc = (SigningConfigDsl) config.signingConfig
        packageApp.conventionMapping.signingConfig = { sc }
        if (sc != null) {
            ValidateSigningTask validateSigningTask = validateSigningTaskMap.get(sc)
            if (validateSigningTask == null) {
                validateSigningTask = project.tasks.add("validate${sc.name.capitalize()}Signing",
                    ValidateSigningTask)
                validateSigningTask.plugin = this
                validateSigningTask.signingConfig = sc

                validateSigningTaskMap.put(sc, validateSigningTask)
            }

            packageApp.dependsOn validateSigningTask
        }

        def signedApk = variant.isSigned()
        def apkName = signedApk ?
            "${project.archivesBaseName}-${variant.baseName}-unaligned.apk" :
            "${project.archivesBaseName}-${variant.baseName}-unsigned.apk"

        packageApp.conventionMapping.outputFile = {
            project.file("$project.buildDir/apk/${apkName}")
        }

        def appTask = packageApp
        variant.outputFile = project.file("$project.buildDir/apk/${apkName}")

        if (signedApk) {
            if (variant.zipAlign) {
                // Add a task to zip align application package
                def zipAlignTask = project.tasks.add("zipalign${variant.name}", ZipAlign)
                variant.zipAlignTask = zipAlignTask

                zipAlignTask.dependsOn packageApp
                zipAlignTask.conventionMapping.inputFile = { packageApp.outputFile }
                zipAlignTask.conventionMapping.outputFile = {
                    project.file(
                            "$project.buildDir/apk/${project.archivesBaseName}-${variant.baseName}.apk")
                }
                zipAlignTask.conventionMapping.zipAlignExe = { androidSdkParser.zipAlign }

                appTask = zipAlignTask
                variant.outputFile = project.file(
                        "$project.buildDir/apk/${project.archivesBaseName}-${variant.baseName}.apk")
            }

            // Add a task to install the application package
            def installTask = project.tasks.add("install${variant.name}", InstallTask)
            installTask.description = "Installs the " + variant.description
            installTask.group = INSTALL_GROUP
            installTask.dependsOn appTask
            installTask.conventionMapping.packageFile = { appTask.outputFile }
            installTask.conventionMapping.adbExe = { androidSdkParser.adb }

            variant.installTask = installTask
        }

        // Add an assemble task
        if (assembleTask == null) {
            assembleTask = project.tasks.add("assemble${variant.name}")
            assembleTask.description = "Assembles the " + variant.description
            assembleTask.group = org.gradle.api.plugins.BasePlugin.BUILD_GROUP
        }
        assembleTask.dependsOn appTask
        variant.assembleTask = assembleTask

        // add an uninstall task
        def uninstallTask = project.tasks.add("uninstall${variant.name}", UninstallTask)
        uninstallTask.description = "Uninstalls the " + variant.description
        uninstallTask.group = INSTALL_GROUP
        uninstallTask.variant = variant
        uninstallTask.conventionMapping.adbExe = { androidSdkParser.adb }

        variant.uninstallTask = uninstallTask
        uninstallAll.dependsOn uninstallTask
    }

    private void createReportTasks() {
        def dependencyReportTask = project.tasks.add("androidDependencies", DependencyReportTask)
        dependencyReportTask.setDescription("Displays the Android dependencies of the project")
        dependencyReportTask.setVariants(variants)
        dependencyReportTask.setGroup("Android")

        def signingReportTask = project.tasks.add("signingReport", SigningReportTask)
        signingReportTask.setDescription("Displays the signing info for each variant")
        signingReportTask.setVariants(variants)
        signingReportTask.setGroup("Android")
    }

    protected void createPrepareDependenciesTask(ApplicationVariant variant,
            List<ConfigurationDependencies> configDependenciesList) {
        def prepareDependenciesTask = project.tasks.add("prepare${variant.name}Dependencies",
                PrepareDependenciesTask)
        variant.prepareDependenciesTask = prepareDependenciesTask

        prepareDependenciesTask.plugin = this
        prepareDependenciesTask.variant = variant

        // for all libraries required by the configurations of this variant, make this task
        // depend on all the tasks preparing these libraries.
        for (ConfigurationDependencies configDependencies : configDependenciesList) {
            prepareDependenciesTask.addChecker(configDependencies.checker)

            for (AndroidDependencyImpl lib : configDependencies.libraries) {
                addDependencyToPrepareTask(prepareDependenciesTask, lib)
            }
        }
    }

    def addDependencyToPrepareTask(PrepareDependenciesTask prepareDependenciesTask,
                                   AndroidDependencyImpl lib) {
        def prepareLibTask = prepareTaskMap.get(lib)
        if (prepareLibTask != null) {
            prepareDependenciesTask.dependsOn prepareLibTask
        }

        for (AndroidDependencyImpl childLib : lib.dependencies) {
            addDependencyToPrepareTask(prepareDependenciesTask, childLib)
        }
    }

    def resolveDependencies(List<ConfigurationDependencies> configs) {
        Map<ModuleVersionIdentifier, List<AndroidDependency>> modules = [:]
        Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts = [:]
        Multimap<AndroidDependency, ConfigurationDependencies> reverseMap = ArrayListMultimap.create()

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
                AndroidDependencyImpl androidDependency = (AndroidDependencyImpl) list.get(0)

                String bundleName = GUtil.toCamelCase(androidDependency.name.replaceAll("\\:", " "))

                def prepareLibraryTask = project.tasks.add("prepare${bundleName}Library",
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
            Map<ModuleVersionIdentifier, List<AndroidDependency>> modules,
            Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts,
            Multimap<AndroidDependency, ConfigurationDependencies> reverseMap) {

        // TODO support package configuration
        def compileClasspath = configDependencies.configuration

        // TODO - shouldn't need to do this - fix this in Gradle
        ensureConfigured(compileClasspath)

        configDependencies.checker = new DependencyChecker(configDependencies, logger)

        // TODO - defer downloading until required -- This is hard to do as we need the info to build the variant config.
        List<AndroidDependency> bundles = []
        List<JarDependency> jars = []
        collectArtifacts(compileClasspath, artifacts)
        compileClasspath.incoming.resolutionResult.root.dependencies.each { ResolvedDependencyResult dep ->
            addDependency(dep.selected, configDependencies, bundles, jars, modules,
                    artifacts, reverseMap)
        }
        // also need to process local jar files, as they are not processed by the
        // resolvedConfiguration result
        compileClasspath.allDependencies.each { dep ->
            if (dep instanceof SelfResolvingDependency &&
                    !(dep instanceof ProjectDependency)) {
                Set<File> files = ((SelfResolvingDependency) dep).resolve()
                for (File f : files) {
                    jars << new JarDependency(f.absolutePath, true, true, true)
                }
            }
        }

        configDependencies.libraries = bundles
        configDependencies.jars = jars

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
                      Collection<AndroidDependency> bundles,
                      Collection<JarDependency> jars,
                      Map<ModuleVersionIdentifier, List<AndroidDependency>> modules,
                      Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts,
                      Multimap<AndroidDependency, ConfigurationDependencies> reverseMap) {
        def id = moduleVersion.id
        if (configDependencies.checker.excluded(id)) {
            return
        }

        List<AndroidDependency> bundlesForThisModule = modules[id]
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
                if (artifact.type == BuilderConstants.EXT_LIB_ARCHIVE) {
                    def explodedDir = project.file(
                            "$project.buildDir/exploded-bundles/$artifact.file.name")
                    AndroidDependencyImpl adep = new AndroidDependencyImpl(
                            id.group + ":" + id.name + ":" + id.version,
                            explodedDir, nestedBundles, artifact.file)
                    bundlesForThisModule << adep
                    reverseMap.put(adep, configDependencies)
                } else {
                    // TODO - need the correct values for the boolean flags
                    jars << new JarDependency(artifact.file.absolutePath, true, true, true)
                }
            }

            if (bundlesForThisModule.empty && !nestedBundles.empty) {
                throw new GradleException("Module version $id depends on libraries but is not a library itself")
            }
        } else {
            for (AndroidDependency adep : bundlesForThisModule) {
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

    /**
     * TODO: remove once assets support more than one folder.
     * @param dirs
     * @return
     */
    protected static File getFirstOptionalDir(Set<File> dirs) {
        Iterator<File> iterator = dirs.iterator();

        File dir = iterator.hasNext() ? iterator.next() : null;
        if (dir != null && dir.isDirectory()) {
            return dir
        }

        return null
    }

    protected List<ManifestDependencyImpl> getManifestDependencies(
            List<AndroidDependency> libraries) {

        List<ManifestDependencyImpl> list = Lists.newArrayListWithCapacity(libraries.size())

        for (AndroidDependency lib : libraries) {
            // get the dependencies
            List<ManifestDependency> children = getManifestDependencies(lib.dependencies)
            list.add(new ManifestDependencyImpl(lib.manifest, children))
        }

        return list
    }

    protected static List<SymbolFileProviderImpl> getTextSymbolDependencies(
            List<AndroidDependency> libraries) {

        List<SymbolFileProviderImpl> list = Lists.newArrayListWithCapacity(libraries.size())

        for (AndroidDependency lib : libraries) {
            list.add(new SymbolFileProviderImpl(lib.manifest, lib.symbolFile))
        }

        return list
    }
}

