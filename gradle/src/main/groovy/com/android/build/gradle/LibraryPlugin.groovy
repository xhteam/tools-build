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
import com.android.build.gradle.internal.BuildTypeData
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.api.LibraryVariantImpl
import com.android.build.gradle.internal.api.TestVariantImpl
import com.android.build.gradle.internal.dependency.ConfigurationDependencies
import com.android.build.gradle.internal.tasks.MergeFileTask
import com.android.build.gradle.internal.variant.LibraryVariantData
import com.android.build.gradle.internal.variant.TestVariantData
import com.android.builder.BuilderConstants
import com.android.builder.VariantConfiguration
import com.android.builder.dependency.DependencyContainer
import com.android.builder.dependency.JarDependency
import com.android.builder.dependency.LibraryBundle
import com.android.builder.dependency.LibraryDependency
import com.android.builder.dependency.ManifestDependency
import com.android.builder.model.AndroidLibrary
import com.google.common.collect.Sets
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.reflect.Instantiator
import org.gradle.tooling.BuildException
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import javax.inject.Inject

/**
 * Gradle plugin class for 'library' projects.
 */
public class LibraryPlugin extends BasePlugin implements Plugin<Project> {

    private final static String DIR_BUNDLES = "bundles";

    LibraryExtension extension
    BuildTypeData debugBuildTypeData
    BuildTypeData releaseBuildTypeData

    @Inject
    public LibraryPlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
        super(instantiator, registry)
    }

    @Override
    protected BaseExtension getAndroidExtension() {
        return extension;
    }

    @Override
    void apply(Project project) {
        super.apply(project)

        extension = project.extensions.create('android', LibraryExtension,
                this, (ProjectInternal) project, instantiator)
        setDefaultConfig(extension.defaultConfig, extension.sourceSetsContainer)

        // create the source sets for the build type.
        // the ones for the main product flavors are handled by the base plugin.
        def debugSourceSet = extension.sourceSetsContainer.create(BuilderConstants.DEBUG)
        def releaseSourceSet = extension.sourceSetsContainer.create(BuilderConstants.RELEASE)

        debugBuildTypeData = new BuildTypeData(extension.debug, debugSourceSet, project)
        releaseBuildTypeData = new BuildTypeData(extension.release, releaseSourceSet, project)
        project.tasks.assemble.dependsOn debugBuildTypeData.assembleTask
        project.tasks.assemble.dependsOn releaseBuildTypeData.assembleTask

        createConfigurations(releaseSourceSet)
    }

    void createConfigurations(AndroidSourceSet releaseSourceSet) {
        // The library artifact is published for the "default" configuration so we make
        // sure "default" extends from the actual configuration used for building.
        project.configurations["default"].extendsFrom(
                project.configurations[mainSourceSet.getPackageConfigurationName()])
        project.configurations["default"].extendsFrom(
                project.configurations[releaseSourceSet.getPackageConfigurationName()])

        project.plugins.withType(MavenPlugin) {
            project.conf2ScopeMappings.addMapping(300,
                    project.configurations[mainSourceSet.getCompileConfigurationName()],
                    "compile")
            project.conf2ScopeMappings.addMapping(300,
                    project.configurations[releaseSourceSet.getCompileConfigurationName()],
                    "compile")
            // TODO -- figure out the package configuration
//            project.conf2ScopeMappings.addMapping(300,
//                    project.configurations[mainSourceSet.getPackageConfigurationName()],
//                    "runtime")
//            project.conf2ScopeMappings.addMapping(300,
//                    project.configurations[releaseSourceSet.getPackageConfigurationName()],
//                    "runtime")
        }
    }

    @Override
    protected void doCreateAndroidTasks() {
        // resolve dependencies for all config
        List<ConfigurationDependencies> dependencies = []
        dependencies.add(debugBuildTypeData)
        dependencies.add(releaseBuildTypeData)
        resolveDependencies(dependencies)

        // create the variants and get their internal storage objects.
        LibraryVariantData testedVariantData = createLibraryVariant(debugBuildTypeData, false)
        LibraryVariantData notTestedVariantData = createLibraryVariant(releaseBuildTypeData, true)
        TestVariantData testVariantData = createTestVariant(testedVariantData)

        // and now create the API objects for the variants

        // add the not-tested build variant.
        extension.addLibraryVariant(
                instantiator.newInstance(LibraryVariantImpl.class, notTestedVariantData))

        // add the tested build variant
        LibraryVariantImpl libraryVariant = instantiator.newInstance(LibraryVariantImpl.class,
                testedVariantData);
        extension.addLibraryVariant(libraryVariant);

        // add the test variant
        TestVariantImpl testVariant = instantiator.newInstance(TestVariantImpl.class,
                testVariantData, libraryVariant)
        extension.addTestVariant(testVariant)

        // finally, wire the test Variant inside the tested library variant.
        libraryVariant.setTestVariant(testVariant)
    }

    private LibraryVariantData createLibraryVariant(
            @NonNull BuildTypeData buildTypeData, boolean publishArtifact) {
        ProductFlavorData defaultConfigData = getDefaultConfigData();

        // the order of the libraries is important. In descending order:
        // build type, defaultConfig.
        List<ConfigurationDependencies> configDependencies = []
        configDependencies.add(buildTypeData)
        configDependencies.add(defaultConfigData)

        def variantConfig = new VariantConfiguration(
                defaultConfigData.productFlavor, defaultConfigData.sourceSet,
                buildTypeData.buildType, buildTypeData.sourceSet,
                VariantConfiguration.Type.LIBRARY, project.name)

        variantConfig.setDependencies(configDependencies)

        String packageName = variantConfig.getPackageFromManifest()
        if (packageName == null) {
            throw new BuildException("Failed to read manifest", null)
        }

        LibraryVariantData variantData = new LibraryVariantData(variantConfig)
        variantDataList.add(variantData)

        createPrepareDependenciesTask(variantData, configDependencies)

        // Add a task to process the manifest(s)
        createProcessManifestTask(variantData, DIR_BUNDLES)

        // Add a task to compile renderscript files.
        createRenderscriptTask(variantData)

        // Add a task to merge the resource folders
        createMergeResourcesTask(variantData,
                "$project.buildDir/$DIR_BUNDLES/${variantData.dirName}/res",
                false /*process9Patch*/)

        // Add a task to merge the assets folders
        createMergeAssetsTask(variantData,
                "$project.buildDir/$DIR_BUNDLES/${variantData.dirName}/assets")

        // Add a task to create the BuildConfig class
        createBuildConfigTask(variantData)

        // Add a task to generate resource source files, directing the location
        // of the r.txt file to be directly in the bundle.
        createProcessResTask(variantData, "$project.buildDir/$DIR_BUNDLES/${variantData.dirName}")

        // process java resources
        createProcessJavaResTask(variantData)

        createAidlTask(variantData)

        // Add a compile task
        createCompileTask(variantData, null/*testedVariant*/)

        // jar the classes.
        Jar jar = project.tasks.create("package${buildTypeData.buildType.name.capitalize()}Jar", Jar);
        jar.dependsOn variantData.javaCompileTask, variantData.processJavaResources
        jar.from(variantData.javaCompileTask.outputs);
        jar.from(variantData.processJavaResources.destinationDir)

        jar.destinationDir = project.file("$project.buildDir/$DIR_BUNDLES/${variantData.dirName}")
        jar.archiveName = "classes.jar"
        packageName = packageName.replace('.', '/');
        jar.exclude(packageName + "/R.class")
        jar.exclude(packageName + "/R\$*.class")
        jar.exclude(packageName + "/BuildConfig.class")

        // package the aidl files into the bundle folder
        Sync packageAidl = project.tasks.create("package${variantData.name}Aidl", Sync)
        // packageAidl from 3 sources. the order is important to make sure the override works well.
        packageAidl.from(defaultConfigData.sourceSet.aidl.srcDirs,
                buildTypeData.sourceSet.aidl.srcDirs).include("**/*.aidl")
        packageAidl.into(project.file(
                "$project.buildDir/$DIR_BUNDLES/${variantData.dirName}/$SdkConstants.FD_AIDL"))

        // package the renderscript header files files into the bundle folder
        Sync packageRenderscript = project.tasks.create("package${variantData.name}Renderscript",
                Sync)
        // package from 3 sources. the order is important to make sure the override works well.
        packageRenderscript.from(defaultConfigData.sourceSet.renderscript.srcDirs,
                buildTypeData.sourceSet.renderscript.srcDirs).include("**/*.rsh")
        packageRenderscript.into(project.file(
                "$project.buildDir/$DIR_BUNDLES/${variantData.dirName}/$SdkConstants.FD_RENDERSCRIPT"))

        // package the renderscript header files files into the bundle folder
        Sync packageLocalJar = project.tasks.create("package${variantData.name}LocalJar", Sync)
        packageLocalJar.from(getLocalJarFileList(configDependencies))
        packageLocalJar.into(project.file(
                "$project.buildDir/$DIR_BUNDLES/${variantData.dirName}/$SdkConstants.LIBS_FOLDER"))

        // merge the proguard files together
        MergeFileTask mergeFileTask = project.tasks.create("merge${variantData.name}ProguardFiles",
                MergeFileTask)
        mergeFileTask.conventionMapping.inputFiles = { project.files(variantConfig.getProguardFiles(false)).files }
        mergeFileTask.conventionMapping.outputFile = {
            project.file(
                    "$project.buildDir/$DIR_BUNDLES/${variantData.dirName}/$LibraryBundle.FN_PROGUARD_TXT")
        }

        Zip bundle = project.tasks.create("bundle${variantData.name}", Zip)
        bundle.dependsOn jar, packageAidl, packageRenderscript, packageLocalJar, mergeFileTask
        bundle.setDescription("Assembles a bundle containing the library in ${variantData.name}.");
        bundle.destinationDir = project.file("$project.buildDir/libs")
        bundle.extension = BuilderConstants.EXT_LIB_ARCHIVE
        if (variantData.baseName != BuilderConstants.RELEASE) {
            bundle.classifier = variantData.baseName
        }
        bundle.from(project.file("$project.buildDir/$DIR_BUNDLES/${variantData.dirName}"))

        variantData.packageLibTask = bundle
        variantData.outputFile = bundle.archivePath

        if (publishArtifact) {
            // add the artifact that will be published
            project.artifacts.add("default", bundle)
        }

        buildTypeData.assembleTask.dependsOn bundle
        variantData.assembleTask = bundle

        // configure the variant to be testable.
        variantConfig.output = new LibraryBundle(
                project.file("$project.buildDir/$DIR_BUNDLES/${variantData.dirName}"),
                variantData.getName()) {

            @NonNull
            @Override
            List<LibraryDependency> getDependencies() {
                return variantConfig.directLibraries
            }

            @NonNull
            @Override
            List<? extends AndroidLibrary> getLibraryDependencies() {
                return variantConfig.directLibraries
            }

            @NonNull
            @Override
            List<ManifestDependency> getManifestDependencies() {
                return variantConfig.directLibraries
            }
        };

        return variantData
    }

    static Object[] getLocalJarFileList(List<? extends DependencyContainer> containerList) {
        Set<File> files = Sets.newHashSet()
        for (DependencyContainer dependencyContainer : containerList) {
            for (JarDependency jarDependency : dependencyContainer.localDependencies) {
                files.add(jarDependency.jarFile)
            }
        }

        return files.toArray()
    }

    private TestVariantData createTestVariant(LibraryVariantData testedVariantData) {
        ProductFlavorData defaultConfigData = getDefaultConfigData();

        // the order of the libraries is important. In descending order:
        // build types, defaultConfig.
        List<ConfigurationDependencies> configDependencies = []
        configDependencies.add(defaultConfigData.testConfigDependencies)

        def testVariantConfig = new VariantConfiguration(
                defaultConfigData.productFlavor, defaultConfigData.testSourceSet,
                debugBuildTypeData.buildType, null,
                VariantConfiguration.Type.TEST,
                testedVariantData.variantConfiguration, project.name)

        testVariantConfig.setDependencies(configDependencies)

        TestVariantData testVariantData = new TestVariantData(testVariantConfig,)
        variantDataList.add(testVariantData)
        testedVariantData.setTestVariantData(testVariantData);
        createTestTasks(testVariantData, testedVariantData, configDependencies,
                true /*mainTestTask*/, true /*isLibraryTest*/)

        return testVariantData
    }
}
