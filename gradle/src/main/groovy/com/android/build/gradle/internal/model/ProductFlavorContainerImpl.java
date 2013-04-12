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
import com.android.build.gradle.model.Dependencies;
import com.android.build.gradle.model.ProductFlavorContainer;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SourceProvider;

import java.io.Serializable;

/**
 */
class ProductFlavorContainerImpl implements ProductFlavorContainer, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final ProductFlavor productFlavor;
    @NonNull
    private final SourceProvider sourceProvider;
    @NonNull
    private final DependenciesImpl dependencies;
    @NonNull
    private final SourceProvider testSourceProvider;
    @NonNull
    private final DependenciesImpl testDependencies;

    ProductFlavorContainerImpl(@NonNull ProductFlavorImpl productFlavor,
                               @NonNull SourceProviderImpl sourceProvider,
                               @NonNull DependenciesImpl dependencies,
                               @NonNull SourceProviderImpl testSourceProvider,
                               @NonNull DependenciesImpl testDependencies) {

        this.productFlavor = productFlavor;
        this.sourceProvider = sourceProvider;
        this.dependencies = dependencies;
        this.testSourceProvider = testSourceProvider;
        this.testDependencies = testDependencies;
    }

    @NonNull
    @Override
    public ProductFlavor getProductFlavor() {
        return productFlavor;
    }

    @NonNull
    @Override
    public SourceProvider getSourceProvider() {
        return sourceProvider;
    }

    @NonNull
    @Override
    public Dependencies getDependencies() {
        return dependencies;
    }

    @NonNull
    @Override
    public SourceProvider getTestSourceProvider() {
        return testSourceProvider;
    }

    @NonNull
    @Override
    public Dependencies getTestDependencies() {
        return testDependencies;
    }
}
