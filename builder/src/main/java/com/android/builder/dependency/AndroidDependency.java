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

package com.android.builder.dependency;

import com.android.annotations.NonNull;

import java.io.File;
import java.util.List;

/**
 * Represents a dependency on a Library Project.
 */
public interface AndroidDependency extends ManifestDependency, SymbolFileProvider {

    /**
     * Returns the location of the unarchived bundle.
     */
    @NonNull
    File getFolder();

    /**
     * Returns the direct dependency of this dependency.
     */
    @NonNull
    List<AndroidDependency> getDependencies();

    /**
     * Returns the location of the jar file to use for packaging.
     *
     * @return a File for the jar file. The file may not point to an existing file.
     */
    @NonNull
    File getJarFile();

    /**
     * Returns the list of local Jar files that are included in the dependency.
     * @return a list of JarDependency. May be empty but not null.
     */
    @NonNull
    List<JarDependency> getLocalDependencies();

    /**
     * Returns the location of the res folder.
     *
     * @return a File for the res folder. The file may not point to an existing folder.
     */
    @NonNull
    File getResFolder();

    /**
     * Returns the location of the assets folder.
     *
     * @return a File for the assets folder. The file may not point to an existing folder.
     */
    @NonNull
    File getAssetsFolder();

    /**
     * Returns the location of the jni libraries folder.
     *
     * @return a File for the folder. The file may not point to an existing folder.
     */
    @NonNull
    File getJniFolder();

    /**
     * Returns the location of the aidl import folder.
     *
     * @return a File for the folder. The file may not point to an existing folder.
     */
    @NonNull
    File getAidlFolder();

    /**
     * Returns the location of the renderscript import folder.
     *
     * @return a File for the folder. The file may not point to an existing folder.
     */
    @NonNull
    File getRenderscriptFolder();

    /**
     * Returns the location of the proguard files.
     *
     * @return a File for the file. The file may not point to an existing file.
     */
    @NonNull
    File getProguardRules();

    /**
     * Returns the location of the lint jar.
     *
     * @return a File for the jar file. The file may not point to an existing file.
     */
    @NonNull
    File getLintJar();
}
