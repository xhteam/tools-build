/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dependency.LibraryDependencyImpl;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.model.Dependencies;
import com.android.builder.dependency.JarDependency;
import com.android.builder.dependency.LibraryDependency;
import com.android.builder.model.AndroidLibrary;
import com.google.common.collect.Lists;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 */
public class DependenciesImpl implements Dependencies, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final List<AndroidLibrary> libraries;
    @NonNull
    private final List<File> jars;

    @NonNull
    static DependenciesImpl cloneDependencies(
            @Nullable VariantDependencies variantDependencies,
            @NonNull Set<Project> gradleProjects) {

        List<AndroidLibrary> libraries;
        List<File> jars;

        if (variantDependencies != null) {
            List<LibraryDependencyImpl> libs = variantDependencies.getLibraries();
            libraries = Lists.newArrayListWithCapacity(libs.size());
            for (LibraryDependencyImpl libImpl : libs) {
                AndroidLibrary clonedLib = getAndroidLibrary(libImpl, gradleProjects);
                libraries.add(clonedLib);
            }

            List<JarDependency> jarDeps = variantDependencies.getJarDependencies();
            List<JarDependency> localDeps = variantDependencies.getLocalDependencies();

            jars = Lists.newArrayListWithCapacity(jarDeps.size() + localDeps.size());
            for (JarDependency jarDep : jarDeps) {
                jars.add(jarDep.getJarFile());
            }
            for (JarDependency jarDep : localDeps) {
                jars.add(jarDep.getJarFile());
            }
        } else {
            libraries = Collections.emptyList();
            jars = Collections.emptyList();
        }

        return new DependenciesImpl(libraries, jars);
    }

    @NonNull
    private static AndroidLibrary getAndroidLibrary(@NonNull LibraryDependency libImpl,
                                                    @NonNull Set<Project> gradleProjects) {
        File bundle = libImpl.getBundle();

        // search for a project that contains this bundle in its output folder.
        Project projectMatch = null;
        for (Project project : gradleProjects) {
            File buildDir = project.getBuildDir();
            if (contains(buildDir, bundle)) {
                projectMatch = project;
                break;
            }
        }

        List<LibraryDependency> deps = libImpl.getDependencies();
        List<AndroidLibrary> clonedDeps = Lists.newArrayListWithCapacity(deps.size());
        for (LibraryDependency child : deps) {
            AndroidLibrary clonedLib = getAndroidLibrary(child, gradleProjects);
            clonedDeps.add(clonedLib);
        }

        return new AndroidLibraryImpl(libImpl, clonedDeps,
                projectMatch != null ? projectMatch.getPath() : null);
    }

    private static boolean contains(@NonNull File dir, @NonNull File file) {
        try {
            dir = dir.getCanonicalFile();
            file = file.getCanonicalFile();
        } catch (IOException e) {
            return false;
        }

        // quick fail
        return file.getAbsolutePath().startsWith(dir.getAbsolutePath()) && doContains(dir, file);

    }

    private static boolean doContains(@NonNull File dir, @NonNull File file) {
        File parent = file.getParentFile();
        return parent != null && (parent.equals(dir) || doContains(dir, parent));
    }

    private DependenciesImpl(@NonNull List<AndroidLibrary> libraries, @NonNull List<File> jars) {
        this.libraries = libraries;
        this.jars = jars;
    }

    @NonNull
    @Override
    public List<AndroidLibrary> getLibraries() {
        return libraries;
    }

    @NonNull
    @Override
    public List<File> getJars() {
        return jars;
    }

    @NonNull
    @Override
    public List<String> getProjectDependenciesPath() {
        return Collections.emptyList();
    }
}
