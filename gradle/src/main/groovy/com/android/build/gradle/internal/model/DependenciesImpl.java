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

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

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
            @Nullable VariantDependencies variantDependencies) {

        List<AndroidLibrary> libraries;
        List<File> jars;

        if (variantDependencies != null) {
            List<LibraryDependencyImpl> libs = variantDependencies.getLibraries();
            libraries = Lists.newArrayListWithCapacity(libs.size());
            for (LibraryDependencyImpl libImpl : libs) {
                libraries.add(new AndroidLibraryImpl(libImpl, getChildrenDependencies(libImpl)));
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
    private static List<AndroidLibraryImpl> getChildrenDependencies(LibraryDependency lib) {
        List<LibraryDependency> children = lib.getDependencies();
        List<AndroidLibraryImpl> result = Lists.newArrayListWithExpectedSize(children.size());

        for (LibraryDependency child : children) {
            List<AndroidLibraryImpl> subResults = getChildrenDependencies(child);

            result.add(new AndroidLibraryImpl(child, subResults));
        }

        return result;
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
