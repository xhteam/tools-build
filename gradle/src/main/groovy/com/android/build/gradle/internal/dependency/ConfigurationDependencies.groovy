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

package com.android.build.gradle.internal.dependency
import com.android.annotations.NonNull
import com.android.build.gradle.AndroidSourceSet
import com.android.builder.dependency.AndroidDependency
import com.android.builder.dependency.DependencyContainer
import com.android.builder.dependency.JarDependency
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
/**
 * Object that represents the dependencies of a configuration, and optionally contains the
 * dependencies for a test config for the given config.
 */
public class ConfigurationDependencies implements DependencyContainer {

    public static enum ConfigType { DEFAULT, FLAVOR, BUILDTYPE }

    final Project project
    final AndroidSourceSet sourceSet
    final ConfigType type
    ConfigurationDependencies testConfigDependencies;

    DependencyChecker checker

    protected ConfigurationDependencies(@NonNull Project project,
                                        @NonNull AndroidSourceSet sourceSet,
                                        @NonNull ConfigType type) {
        this.project = project
        this.sourceSet = sourceSet
        this.type = type
    }

    private final List<AndroidDependencyImpl> libraries = []
    private final List<JarDependency> jars = []
    private final List<JarDependency> localJars = []

    public Configuration getConfiguration() {
        return project.configurations[sourceSet.compileConfigurationName]
    }

    public String getConfigBaseName() {
        return sourceSet.name
    }

    void addLibraries(List<AndroidDependencyImpl> list) {
        libraries.addAll(list)
    }

    void addJars(List<JarDependency> list) {
        jars.addAll(list)
    }

    void addLocalJars(List<JarDependency> list) {
        localJars.addAll(list)
    }

    List<AndroidDependencyImpl> getLibraries() {
        return libraries
    }

    @NonNull
    @Override
    List<? extends AndroidDependency> getAndroidDependencies() {
        return libraries
    }

    @NonNull
    @Override
    List<JarDependency> getJarDependencies() {
        return jars
    }

    @NonNull
    @Override
    List<JarDependency> getLocalDependencies() {
        return localJars
    }
}
