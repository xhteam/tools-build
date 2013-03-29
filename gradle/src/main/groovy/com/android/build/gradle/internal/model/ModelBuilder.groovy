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

package com.android.build.gradle.internal.model

import com.android.annotations.NonNull
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.internal.BuildTypeData
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.model.AndroidProject
import com.android.build.gradle.model.BuildTypeContainer
import com.android.build.gradle.model.ProductFlavorContainer
import com.android.build.gradle.model.Variant
import com.android.builder.model.SourceProvider
import org.gradle.api.Project
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.tooling.provider.model.ToolingModelBuilder

/**
 * Builder for the custom Android model.
 */
public class ModelBuilder implements ToolingModelBuilder {
    @Override
    public boolean canBuild(String modelName) {
        // The default name for a model is the name of the Java interface
        return modelName.equals(AndroidProject.class.getName())
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        AppPlugin appPlugin = getPlugin(project, AppPlugin.class)
        LibraryPlugin libPlugin = null
        BasePlugin basePlugin = appPlugin

        if (appPlugin == null) {
            basePlugin = libPlugin = getPlugin(project, LibraryPlugin.class)
        }

        if (basePlugin == null) {
            project.logger.error("Failed to find Android plugin for project " + project.name)
            return null
        }

        DefaultAndroidProject androidProject = new DefaultAndroidProject(project.name,
                libPlugin != null).setDefaultConfig(createPFC(basePlugin.defaultConfigData))

        if (appPlugin != null) {
            for (BuildTypeData btData : appPlugin.buildTypes.values()) {
                androidProject.addBuildType(createBTC(btData))
            }
        } else if (libPlugin != null) {
            androidProject.addBuildType(createBTC(libPlugin.debugBuildTypeData))
                          .addBuildType(createBTC(libPlugin.releaseBuildTypeData))
        }

        for (BaseVariantData variantData : basePlugin.variantDataList) {
            androidProject.addVariant(createVariant(variantData))
        }

        return androidProject
    }

    @NonNull
    private static Variant createVariant(BaseVariantData variantData) {
        return new VariantImpl(
                variantData.baseName,
                variantData.variantConfiguration.mergedFlavor);
    }

    /**
     * Create a ProductFlavorContainer from a ProductFlavorData
     * @param productFlavorData the product flavor data
     * @return a non-null ProductFlavorContainer
     */
    @NonNull
    private static ProductFlavorContainer createPFC(ProductFlavorData productFlavorData) {
        return new ProductFlavorContainerImpl(
                productFlavorData.productFlavor,
                (SourceProvider) productFlavorData.sourceSet,
                (SourceProvider) productFlavorData.testSourceSet)
    }

    /**
     * Create a BuildTypeContainer from a BuildTypeData
     * @param buildTypeData the build type data
     * @return a non-null BuildTypeContainer
     */
    @NonNull
    private static BuildTypeContainer createBTC(BuildTypeData buildTypeData) {
        return new BuildTypeContainerImpl(
                buildTypeData.buildType,
                (SourceProvider) buildTypeData.sourceSet)
    }

    /**
     * Safely queries a project for a given plugin class.
     * @param project the project to query
     * @param pluginClass the plugin class.
     * @return the plugin instance or null if it is not applied.
     */
    private static <T> T getPlugin(Project project, Class<T> pluginClass) {
        try {
            return project.getPlugins().findPlugin(pluginClass)
        } catch (UnknownPluginException ignored) {
            // ignore, return null below.
        }

        return null;
    }
}
