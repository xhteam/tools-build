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
package com.android.build.gradle.internal.dependency;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.dependency.AndroidDependency;
import com.android.builder.dependency.BundleDependency;
import com.android.builder.dependency.ManifestDependency;

import java.io.File;
import java.util.List;

public class AndroidDependencyImpl extends BundleDependency {
    final List<AndroidDependency> dependencies;
    final File bundle;

    public AndroidDependencyImpl(@NonNull File explodedBundle,
                                 @NonNull List<AndroidDependency> dependencies,
                                 @NonNull File bundle,
                                 @Nullable String name) {
        super(explodedBundle, name);
        this.dependencies = dependencies;
        this.bundle = bundle;
    }

    @Override
    @NonNull
    public List<AndroidDependency> getDependencies() {
        return dependencies;
    }

    @Override
    @NonNull
    public List<? extends ManifestDependency> getManifestDependencies() {
        return dependencies;
    }
}
