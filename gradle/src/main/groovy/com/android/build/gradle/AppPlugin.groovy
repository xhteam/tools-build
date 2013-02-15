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

import com.android.build.gradle.internal.BuildTypeData
import com.android.build.gradle.internal.DefaultBuildVariant
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.ProductionAppVariant
import com.android.build.gradle.internal.TestAppVariant
import com.android.build.gradle.internal.dependency.ConfigurationDependencies
import com.android.build.gradle.internal.dsl.BuildTypeDsl
import com.android.build.gradle.internal.dsl.BuildTypeFactory
import com.android.build.gradle.internal.dsl.GroupableProductFlavor
import com.android.build.gradle.internal.dsl.GroupableProductFlavorFactory
import com.android.build.gradle.internal.dsl.SigningConfigDsl
import com.android.build.gradle.internal.dsl.SigningConfigFactory
import com.android.build.gradle.internal.tasks.AndroidReportTask
import com.android.build.gradle.internal.tasks.AndroidTestTask
import com.android.build.gradle.internal.test.PluginHolder
import com.android.build.gradle.internal.test.report.ReportType
import com.android.builder.AndroidDependency
import com.android.builder.BuildType
import com.android.builder.BuilderConstants
import com.android.builder.JarDependency
import com.android.builder.VariantConfiguration
import com.android.builder.signing.SigningConfig
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.internal.reflect.Instantiator

import javax.inject.Inject

/**
 * Gradle plugin class for 'application' projects.
 */
class AppPlugin extends com.android.build.gradle.BasePlugin implements org.gradle.api.Plugin<Project> {
    static PluginHolder pluginHolder;

    final Map<String, BuildTypeData> buildTypes = [:]
    final Map<String, ProductFlavorData<GroupableProductFlavor>> productFlavors = [:]
    final Map<String, SigningConfig> signingConfigs = [:]

    AppExtension extension
    AndroidReportTask testTask

    @Inject
    public AppPlugin(Instantiator instantiator) {
        super(instantiator)
    }

    @Override
    void apply(Project project) {
        super.apply(project)

        // This is for testing.
        if (pluginHolder != null) {
            pluginHolder.plugin = this;
        }

        def buildTypeContainer = project.container(BuildType, new BuildTypeFactory(instantiator))
        def productFlavorContainer = project.container(GroupableProductFlavor,
                new GroupableProductFlavorFactory(instantiator))
        def signingConfigContainer = project.container(SigningConfig,
                new SigningConfigFactory(instantiator))

        extension = project.extensions.create('android', AppExtension,
                this, (ProjectInternal) project, instantiator,
                buildTypeContainer, productFlavorContainer, signingConfigContainer)
        setDefaultConfig(extension.defaultConfig, extension.sourceSetsContainer)

        // map the whenObjectAdded callbacks on the containers.
        signingConfigContainer.whenObjectAdded { SigningConfig signingConfig ->
            SigningConfigDsl signingConfigDsl = (SigningConfigDsl) signingConfig
            signingConfigs[signingConfigDsl.name] = signingConfig
        }

        buildTypeContainer.whenObjectAdded { BuildType buildType ->
            ((BuildTypeDsl)buildType).init(signingConfigContainer.getByName(BuilderConstants.DEBUG))
            addBuildType(buildType)
        }

        productFlavorContainer.whenObjectAdded { GroupableProductFlavor productFlavor ->
            addProductFlavor(productFlavor)
        }

        // create default Objects, signingConfig first as its used by the BuildTypes.
        signingConfigContainer.create(BuilderConstants.DEBUG)
        buildTypeContainer.create(BuilderConstants.DEBUG)
        buildTypeContainer.create(BuilderConstants.RELEASE)

        // map whenObjectRemoved on the containers to throw an exception.
        signingConfigContainer.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing signingConfigs is not supported.")
        }
        buildTypeContainer.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing build types is not supported.")
        }
        productFlavorContainer.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing product flavors is not supported.")
        }
    }

    /**
     * Adds new BuildType, creating a BuildTypeData, and the associated source set,
     * and adding it to the map.
     * @param buildType the build type.
     */
    private void addBuildType(BuildType buildType) {
        String name = buildType.name
        if (name.startsWith(BuilderConstants.TEST)) {
            throw new RuntimeException("BuildType names cannot start with 'test'")
        }
        if (productFlavors.containsKey(name)) {
            throw new RuntimeException("BuildType names cannot collide with ProductFlavor names")
        }

        def sourceSet = extension.sourceSetsContainer.create(name)

        BuildTypeData buildTypeData = new BuildTypeData(buildType, sourceSet, project)
        project.tasks.assemble.dependsOn buildTypeData.assembleTask

        buildTypes[name] = buildTypeData
    }

    /**
     * Adds a new ProductFlavor, creating a ProductFlavorData and associated source sets,
     * and adding it to the map.
     *
     * @param productFlavor the product flavor
     */
    private void addProductFlavor(GroupableProductFlavor productFlavor) {
        if (productFlavor.name.startsWith(BuilderConstants.TEST)) {
            throw new RuntimeException("ProductFlavor names cannot start with 'test'")
        }
        if (buildTypes.containsKey(productFlavor.name)) {
            throw new RuntimeException("ProductFlavor names cannot collide with BuildType names")
        }

        def mainSourceSet = extension.sourceSetsContainer.create(productFlavor.name)
        String testName = "${BuilderConstants.TEST}${productFlavor.name.capitalize()}"
        def testSourceSet = extension.sourceSetsContainer.create(testName)

        ProductFlavorData<GroupableProductFlavor> productFlavorData =
                new ProductFlavorData<GroupableProductFlavor>(
                        productFlavor, mainSourceSet, testSourceSet, project)

        productFlavors[productFlavor.name] = productFlavorData
    }

    /**
     * Task creation entry point.
     */
    @Override
    protected void doCreateAndroidTasks() {
        // resolve dependencies for all config
        List<ConfigurationDependencies> dependencies = []
        dependencies.addAll(buildTypes.values())
        dependencies.addAll(productFlavors.values())
        resolveDependencies(dependencies)

        // now create the tasks.
        if (productFlavors.isEmpty()) {
            createTasksForDefaultBuild()
        } else {
            // there'll be more than one test app, so we need a top level assembleTest
            assembleTest = project.tasks.add("assembleTest")
            assembleTest.group = BasePlugin.BUILD_GROUP
            assembleTest.description = "Assembles all the Test applications"

            // same for the test task
            testTask = project.tasks.add("instrumentationTest", AndroidReportTask)
            testTask.group = JavaBasePlugin.VERIFICATION_GROUP
            testTask.description = "Installs and runs instrumentation tests for all flavors"
            testTask.reportType = ReportType.MULTI_FLAVOR
            deviceCheck.dependsOn testTask

            testTask.conventionMapping.resultsDir = {
                String rootLocation = extension.testOptions.resultsDir != null ?
                    extension.testOptions.resultsDir : "$project.buildDir/test-results"

                project.file("$rootLocation/all")
            }
            testTask.conventionMapping.reportsDir = {
                String rootLocation = extension.testOptions.reportDir != null ?
                    extension.testOptions.reportDir : "$project.buildDir/reports/tests"

                project.file("$rootLocation/all")
            }

            // check whether we have multi flavor builds
            if (extension.flavorGroupList == null || extension.flavorGroupList.size() < 2) {
                productFlavors.values().each { ProductFlavorData productFlavorData ->
                    createTasksForFlavoredBuild(productFlavorData)
                }
            } else {
                // need to group the flavor per group.
                // First a map of group -> list(ProductFlavor)
                ArrayListMultimap<String, ProductFlavorData<GroupableProductFlavor>> map = ArrayListMultimap.create();
                productFlavors.values().each { ProductFlavorData<GroupableProductFlavor> productFlavorData ->
                    def flavor = productFlavorData.productFlavor
                    if (flavor.flavorGroup == null) {
                        throw new RuntimeException(
                                "Flavor ${flavor.name} has no flavor group.")
                    }
                    if (!extension.flavorGroupList.contains(flavor.flavorGroup)) {
                        throw new RuntimeException(
                                "Flavor ${flavor.name} has unknown group ${flavor.flavorGroup}.")
                    }

                    map.put(flavor.flavorGroup, productFlavorData)
                }

                // now we use the flavor groups to generate an ordered array of flavor to use
                ProductFlavorData[] array = new ProductFlavorData[extension.flavorGroupList.size()]
                createTasksForMultiFlavoredBuilds(array, 0, map)
            }
        }

        // If gradle is launched with --continue, we want to run all tests and generate an
        // aggregate report (to help with the fact that we may have several build variants).
        // To do that, the "test" task (which does the aggregation) must always run even if
        // one of its dependent task (all the testFlavor tasks) fails, so we make them ignore their
        // error.
        // We cannot do that always: in case the test task is not going to run, we do want the
        // individual testFlavor tasks to fail.
        if (testTask != null && project.gradle.startParameter.continueOnFailure) {
            project.gradle.taskGraph.whenReady { taskGraph ->
                if (taskGraph.hasTask(testTask)) {
                    testTask.setWillRun()
                }
            }
        }
    }

    /**
     * Creates the tasks for multi-flavor builds.
     *
     * This recursively fills the array of ProductFlavorData (in the order defined
     * in extension.flavorGroupList), creating all possible combination.
     *
     * @param datas the arrays to fill
     * @param i the current index to fill
     * @param map the map of group -> list(ProductFlavor)
     * @return
     */
    private createTasksForMultiFlavoredBuilds(ProductFlavorData[] datas, int i,
                                              ListMultimap<String, ? extends ProductFlavorData> map) {
        if (i == datas.length) {
            createTasksForFlavoredBuild(datas)
            return
        }

        // fill the array at the current index.
        // get the group name that matches the index we are filling.
        def group = extension.flavorGroupList.get(i)

        // from our map, get all the possible flavors in that group.
        def flavorList = map.get(group)

        // loop on all the flavors to add them to the current index and recursively fill the next
        // indices.
        for (ProductFlavorData flavor : flavorList) {
            datas[i] = flavor
            createTasksForMultiFlavoredBuilds(datas, (int) i + 1, map)
        }
    }

    /**
     * Creates Tasks for non-flavored build. This means assembleDebug, assembleRelease, and other
     * assemble<Type> are directly building the <type> build instead of all build of the given
     * <type>.
     */
    private createTasksForDefaultBuild() {

        BuildTypeData testData = buildTypes[extension.testBuildType]
        if (testData == null) {
            throw new RuntimeException("Test Build Type '$extension.testBuildType' does not exist.")
        }

        ProductionAppVariant testedVariant = null

        ProductFlavorData defaultConfigData = getDefaultConfigData();

        for (BuildTypeData buildTypeData : buildTypes.values()) {
            List<ConfigurationDependencies> configDependencies = []
            configDependencies.add(defaultConfigData)
            configDependencies.add(buildTypeData)

            // list of dependency to set on the variantConfig
            List<JarDependency> jars = []
            jars.addAll(defaultConfigData.jars)
            jars.addAll(buildTypeData.jars)

            // the order of the libraries is important. In descending order:
            // build types, flavors, defaultConfig.
            List<AndroidDependency> libs = []
            libs.addAll(buildTypeData.libraries)
            // no flavors, just add the default config
            libs.addAll(defaultConfigData.libraries)

            def variantConfig = new VariantConfiguration(
                    defaultConfigData.productFlavor, defaultConfigData.sourceSet,
                    buildTypeData.buildType, buildTypeData.sourceSet)

            variantConfig.setJarDependencies(jars)
            variantConfig.setAndroidDependencies(libs)

            ProductionAppVariant productionAppVariant = addVariant(variantConfig,
                    buildTypeData.assembleTask, configDependencies)
            variants.add(productionAppVariant)

            if (buildTypeData == testData) {
                testedVariant = productionAppVariant
            } else {
                // add this non-tested variant to the list
                DefaultBuildVariant buildVariant = instantiator.newInstance(
                        DefaultBuildVariant.class, productionAppVariant)
                extension.buildVariants.add(buildVariant)
            }
        }

        assert testedVariant != null

        def testVariantConfig = new VariantConfiguration(
                defaultConfigData.productFlavor, defaultConfigData.testSourceSet,
                testData.buildType, null,
                VariantConfiguration.Type.TEST, testedVariant.config)

        List<ConfigurationDependencies> testConfigDependencies = []
        testConfigDependencies.add(defaultConfigData.testConfigDependencies)

        // list of dependency to set on the variantConfig
        List<JarDependency> testJars = []
        testJars.addAll(defaultConfigData.testConfigDependencies.jars)
        List<AndroidDependency> testLibs = []
        testLibs.addAll(defaultConfigData.testConfigDependencies.libraries)

        testVariantConfig.setJarDependencies(testJars)
        testVariantConfig.setAndroidDependencies(testLibs)

        def testVariant = new TestAppVariant(testVariantConfig)
        variants.add(testVariant)
        createTestTasks(testVariant, testedVariant, testConfigDependencies, true /*mainTestTask*/)

        // add the test and tested variants to the list
        DefaultBuildVariant testedBuildVariant = instantiator.newInstance(
                DefaultBuildVariant.class, testVariant)
        extension.testBuildVariants.add(testedBuildVariant)
        DefaultBuildVariant buildVariant = instantiator.newInstance(
                DefaultBuildVariant.class, testedVariant, testedBuildVariant)
        extension.buildVariants.add(buildVariant)
    }

    /**
     * Creates Task for a given flavor. This will create tasks for all build types for the given
     * flavor.
     * @param flavorDataList the flavor(s) to build.
     */
    private createTasksForFlavoredBuild(ProductFlavorData... flavorDataList) {

        BuildTypeData testData = buildTypes[extension.testBuildType]
        if (testData == null) {
            throw new RuntimeException("Test Build Type '$extension.testBuildType' does not exist.")
        }

        ProductionAppVariant testedVariant = null

        // assembleTask for this flavor(group)
        def assembleTask

        for (BuildTypeData buildTypeData : buildTypes.values()) {
            List<ConfigurationDependencies> configDependencies = []
            configDependencies.add(defaultConfigData)
            configDependencies.add(buildTypeData)

            // list of dependency to set on the variantConfig
            List<JarDependency> jars = []
            jars.addAll(defaultConfigData.jars)
            jars.addAll(buildTypeData.jars)

            // the order of the libraries is important. In descending order:
            // build types, flavors, defaultConfig.
            List<AndroidDependency> libs = []
            libs.addAll(buildTypeData.libraries)

            def variantConfig = new VariantConfiguration(
                    extension.defaultConfig, getDefaultConfigData().sourceSet,
                    buildTypeData.buildType, buildTypeData.sourceSet)

            for (ProductFlavorData data : flavorDataList) {
                variantConfig.addProductFlavor(data.productFlavor, data.sourceSet)
                jars.addAll(data.jars)
                libs.addAll(data.libraries)
                configDependencies.add(data)
            }

            // now add the defaultConfig
            libs.addAll(defaultConfigData.libraries)

            variantConfig.setJarDependencies(jars)
            variantConfig.setAndroidDependencies(libs)

            ProductionAppVariant productionAppVariant = addVariant(variantConfig, null,
                    configDependencies)
            variants.add(productionAppVariant)

            buildTypeData.assembleTask.dependsOn productionAppVariant.assembleTask

            if (assembleTask == null) {
                // create the task based on the name of the flavors.
                assembleTask = createAssembleTask(flavorDataList)
                project.tasks.assemble.dependsOn assembleTask
            }
            assembleTask.dependsOn productionAppVariant.assembleTask

            if (buildTypeData == testData) {
                testedVariant = productionAppVariant
            } else {
                // add this non-tested variant to the list
                DefaultBuildVariant buildVariant = instantiator.newInstance(
                        DefaultBuildVariant.class, productionAppVariant)
                extension.buildVariants.add(buildVariant)
            }
        }

        assert testedVariant != null

        def testVariantConfig = new VariantConfiguration(
                extension.defaultConfig, getDefaultConfigData().testSourceSet,
                testData.buildType, null,
                VariantConfiguration.Type.TEST, testedVariant.config)

        List<ConfigurationDependencies> testConfigDependencies = []
        testConfigDependencies.add(defaultConfigData.testConfigDependencies)

        // list of dependency to set on the variantConfig
        List<JarDependency> testJars = []
        testJars.addAll(defaultConfigData.testConfigDependencies.jars)

        // the order of the libraries is important. In descending order:
        // flavors, defaultConfig.
        List<AndroidDependency> testLibs = []

        for (ProductFlavorData data : flavorDataList) {
            testVariantConfig.addProductFlavor(data.productFlavor, data.testSourceSet)
            testJars.addAll(data.testConfigDependencies.jars)
            testLibs.addAll(data.testConfigDependencies.libraries)
        }

        // now add the default config
        testLibs.addAll(defaultConfigData.testConfigDependencies.libraries)

        testVariantConfig.setJarDependencies(testJars)
        testVariantConfig.setAndroidDependencies(testLibs)

        def testVariant = new TestAppVariant(testVariantConfig)
        variants.add(testVariant)
        AndroidTestTask testFlavorTask = createTestTasks(testVariant, testedVariant,
                testConfigDependencies, false /*mainTestTask*/)

        testTask.addTask(testFlavorTask)

        // add the test and tested variants to the list
        DefaultBuildVariant testedBuildVariant = instantiator.newInstance(
                DefaultBuildVariant.class, testVariant)
        extension.testBuildVariants.add(testedBuildVariant)
        DefaultBuildVariant buildVariant = instantiator.newInstance(
                DefaultBuildVariant.class, testedVariant, testedBuildVariant)
        extension.buildVariants.add(buildVariant)

    }

    private Task createAssembleTask(ProductFlavorData[] flavorDataList) {
        def name = ProductFlavorData.getFlavoredName(flavorDataList, true)

        def assembleTask = project.tasks.add("assemble${name}")
        assembleTask.description = "Assembles all builds for flavor ${name}"
        assembleTask.group = "Build"

        return assembleTask
    }

    /**
     * Creates build tasks for a given variant.
     * @param variantConfig
     * @param assembleTask an optional assembleTask to be used. If null, a new one is created.
     * @return
     */
    private ProductionAppVariant addVariant(VariantConfiguration variantConfig, Task assembleTask,
                                            List<ConfigurationDependencies> configDependencies) {

        def variant = new ProductionAppVariant(variantConfig)

        createPrepareDependenciesTask(variant, configDependencies)

        // Add a task to process the manifest(s)
        createProcessManifestTask(variant, "manifests")

        // Add a task to compile renderscript files.
        createRenderscriptTask(variant)

        // Add a task to merge the resource folders
        createMergeResourcesTask(variant, true /*process9Patch*/)

        // Add a task to merge the asset folders
        createMergeAssetsTask(variant, null /*default location*/)

        // Add a task to create the BuildConfig class
        createBuildConfigTask(variant)

        // Add a task to generate resource source files
        createProcessResTask(variant)

        // Add a task to process the java resources
        createProcessJavaResTask(variant)

        createAidlTask(variant)

        // Add a compile task
        createCompileTask(variant, null/*testedVariant*/)

        addPackageTasks(variant, assembleTask)

        return variant;
    }

    @Override
    protected String getTarget() {
        return extension.compileSdkVersion;
    }
}
