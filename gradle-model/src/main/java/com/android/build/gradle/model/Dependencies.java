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

package com.android.build.gradle.model;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidLibrary;

import java.io.File;
import java.util.List;

/**
 */
public interface Dependencies {

    /**
     * The list of libraries. Can be AndroidLibrary or AndroidLibraryProject
     * @return the list of libraries.
     */
    @NonNull
    List<AndroidLibrary> getLibraries();

    @NonNull
    List<File> getJars();

    @NonNull
    List<String> getProjectDependenciesPath();
}
