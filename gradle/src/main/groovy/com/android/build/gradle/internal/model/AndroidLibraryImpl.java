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
import com.android.builder.dependency.LibraryDependency;
import com.android.builder.model.AndroidLibrary;

import java.io.File;
import java.io.Serializable;
import java.util.List;

public class AndroidLibraryImpl implements AndroidLibrary, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final File folder;
    @NonNull
    private final File jarFile;
    @NonNull
    private final List<File> localJars;
    @NonNull
    private final File resFolder;
    @NonNull
    private final File assetsFolder;
    @NonNull
    private final File jniFolder;
    @NonNull
    private final File aidlFolder;
    @NonNull
    private final File renderscriptFolder;
    @NonNull
    private final File proguardRules;
    @NonNull
    private final File lintJar;
    @NonNull
    private final List<AndroidLibraryImpl> dependencies;

    AndroidLibraryImpl(@NonNull LibraryDependency libraryDependency,
                       @NonNull List<AndroidLibraryImpl> dependencies) {
        this.dependencies = dependencies;
        folder = libraryDependency.getFolder();
        jarFile = libraryDependency.getJarFile();
        localJars = libraryDependency.getLocalJars();
        resFolder = libraryDependency.getResFolder();
        assetsFolder = libraryDependency.getAssetsFolder();
        jniFolder = libraryDependency.getJniFolder();
        aidlFolder = libraryDependency.getAidlFolder();
        renderscriptFolder = libraryDependency.getRenderscriptFolder();
        proguardRules = libraryDependency.getProguardRules();
        lintJar = libraryDependency.getLintJar();
    }

    @NonNull
    @Override
    public File getFolder() {
        return folder;
    }

    @NonNull
    @Override
    public List<? extends AndroidLibrary> getLibraryDependencies() {
        return dependencies;
    }

    @NonNull
    @Override
    public File getJarFile() {
        return jarFile;
    }

    @NonNull
    @Override
    public List<File> getLocalJars() {
        return localJars;
    }

    @NonNull
    @Override
    public File getResFolder() {
        return resFolder;
    }

    @NonNull
    @Override
    public File getAssetsFolder() {
        return assetsFolder;
    }

    @NonNull
    @Override
    public File getJniFolder() {
        return jniFolder;
    }

    @NonNull
    @Override
    public File getAidlFolder() {
        return aidlFolder;
    }

    @NonNull
    @Override
    public File getRenderscriptFolder() {
        return renderscriptFolder;
    }

    @NonNull
    @Override
    public File getProguardRules() {
        return proguardRules;
    }

    @NonNull
    @Override
    public File getLintJar() {
        return lintJar;
    }
}
