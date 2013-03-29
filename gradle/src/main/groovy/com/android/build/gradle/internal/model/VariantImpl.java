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
import com.android.build.gradle.model.Variant;
import com.android.builder.model.ProductFlavor;

import java.io.File;
import java.io.Serializable;

/**
 * Implementation of Variant that is serializable.
 */
class VariantImpl implements Variant, Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final ProductFlavor mergedFlavor;

    VariantImpl(String name, ProductFlavor mergedFlavor) {
        this.name = name;
        this.mergedFlavor = ProductFlavorImpl.cloneFlavor(mergedFlavor);
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public File getOutput() {
        return new File("");
    }

    @Override
    @NonNull
    public String getBuildType() {
        return "";
    }

    @Override
    @NonNull
    public ProductFlavor getMergedFlavor() {
        return mergedFlavor;
    }
}
