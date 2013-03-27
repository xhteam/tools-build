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

package com.android.builder.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * a Build Type.
 *
 * TODO add signing config
 */
public interface BuildType extends BuildConfig {
    @NonNull
    String getName();

    boolean isDebuggable();

    boolean isJniDebugBuild();

    boolean isRenderscriptDebugBuild();

    int getRenderscriptOptimLevel();

    @Nullable
    String getPackageNameSuffix();

    @Nullable
    String getVersionNameSuffix();

    boolean isRunProguard();

    boolean isZipAlign();
}
