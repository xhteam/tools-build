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
import com.android.build.gradle.model.Variant;
import com.android.builder.model.ProductFlavor;

import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 * Implementation of Variant that is serializable.
 */
class VariantImpl implements Variant, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final String name;
    @NonNull
    private final List<String> bootClasspath;
    @NonNull
    private final String assembleTaskName;
    @Nullable
    private final String assembleTestTaskName;
    @NonNull
    private final String buildTypeName;
    @NonNull
    private final List<String> productFlavorNames;
    @NonNull
    private final ProductFlavor mergedFlavor;
    @NonNull
    private final File outputFile;
    @Nullable
    private final File testOutputFile;
    private final boolean isSigned;
    @NonNull
    private final List<File> generatedSourceFolders;
    @Nullable
    private final List<File> generatedTestSourceFolders;
    @NonNull
    private final List<File> generatedResourceFolders;
    @Nullable
    private final List<File> generatedTestResourceFolders;

    VariantImpl(@NonNull  String name,
                @NonNull  List<String> bootClasspath,
                @NonNull  String assembleTaskName,
                @Nullable String assembleTestTaskName,
                @NonNull  String buildTypeName,
                @NonNull  List<String> productFlavorNames,
                @NonNull  ProductFlavorImpl mergedFlavor,
                @NonNull  File outputFile,
                @Nullable File testOutputFile,
                          boolean isSigned,
                @NonNull  List<File> generatedSourceFolders,
                @Nullable List<File> generatedTestSourceFolders,
                @NonNull  List<File> generatedResourceFolders,
                @Nullable List<File> generatedTestResourceFolders) {
        this.name = name;
        this.bootClasspath = bootClasspath;
        this.assembleTaskName = assembleTaskName;
        this.assembleTestTaskName = assembleTestTaskName;
        this.buildTypeName = buildTypeName;
        this.productFlavorNames = productFlavorNames;
        this.mergedFlavor = mergedFlavor;
        this.outputFile = outputFile;
        this.testOutputFile = testOutputFile;
        this.isSigned = isSigned;
        this.generatedSourceFolders = generatedSourceFolders;
        this.generatedTestSourceFolders = generatedTestSourceFolders;
        this.generatedResourceFolders = generatedResourceFolders;
        this.generatedTestResourceFolders = generatedTestResourceFolders;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public List<String> getBootClasspath() {
        return bootClasspath;
    }

    @NonNull
    @Override
    public String getAssembleTaskName() {
        return assembleTaskName;
    }

    @Nullable
    @Override
    public String getAssembleTestTaskName() {
        return assembleTestTaskName;
    }

    @Override
    @NonNull
    public File getOutputFile() {
        return outputFile;
    }

    @Nullable
    @Override
    public File getOutputTestFile() {
        return testOutputFile;
    }

    @Override
    public boolean isSigned() {
        return isSigned;
    }

    @Override
    @NonNull
    public String getBuildType() {
        return buildTypeName;
    }

    @Override
    @NonNull
    public List<String> getProductFlavors() {
        return productFlavorNames;
    }

    @Override
    @NonNull
    public ProductFlavor getMergedFlavor() {
        return mergedFlavor;
    }

    @NonNull
    @Override
    public List<File> getGeneratedSourceFolders() {
        return generatedSourceFolders;
    }

    @Nullable
    @Override
    public List<File> getGeneratedTestSourceFolders() {
        return generatedTestSourceFolders;
    }

    @NonNull
    @Override
    public List<File> getGeneratedResourceFolders() {
        return generatedResourceFolders;
    }

    @Nullable
    @Override
    public List<File> getGeneratedTestResourceFolders() {
        return generatedTestResourceFolders;
    }
}
