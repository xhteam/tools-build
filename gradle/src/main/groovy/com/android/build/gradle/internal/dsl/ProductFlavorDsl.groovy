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

package com.android.build.gradle.internal.dsl
import com.android.annotations.NonNull
import com.android.builder.DefaultProductFlavor
import com.google.common.collect.Lists
/**
 * DSL overlay to make methods that accept String... work.
 */
class ProductFlavorDsl extends DefaultProductFlavor {
    private static final long serialVersionUID = 1L

    private List<Object> proguardFiles = Lists.newArrayList();

    ProductFlavorDsl(String name) {
        super(name)
    }

    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.

    public void buildConfig(String... lines) {
        setBuildConfig(lines)
    }

    public void buildConfig(String line) {
        setBuildConfig(line)
    }

    @NonNull
    public ProductFlavorDsl proguardFile(Object srcDir) {
        proguardFiles.add(srcDir);
        return this;
    }

    @NonNull
    public ProductFlavorDsl proguardFiles(Object... srcDirs) {
        Collections.addAll(proguardFiles, srcDirs);
        return this;
    }

    @NonNull
    public ProductFlavorDsl setProguardFiles(Iterable<?> srcDirs) {
        proguardFiles.clear();
        for (Object srcDir : srcDirs) {
            proguardFiles.add(srcDir);
        }
        return this;
    }

    @Override
    @NonNull
    public List<Object> getProguardFiles() {
        return proguardFiles;
    }
}
