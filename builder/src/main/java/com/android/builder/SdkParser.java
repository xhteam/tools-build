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

package com.android.builder;

import com.android.annotations.NonNull;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.FullRevision;
import com.android.utils.ILogger;

import java.io.File;

/**
 * A parser able to parse the SDK and return valuable information to the build system.
 *
 */
public interface SdkParser {

    /**
     * Resolves a target hash string and returns the corresponding {@link IAndroidTarget}
     * @param target the target hash string.
     * @param logger a logger object.
     * @return the target or null if no match is found.
     *
     * @throws RuntimeException if the SDK cannot parsed.
     *
     * @see IAndroidTarget#hashString()
     */
    IAndroidTarget resolveTarget(@NonNull String target, @NonNull ILogger logger);

    /**
     * Returns the location of the annotations jar for compilation targets that are <= 15.
     */
    String getAnnotationsJar();

    /**
     * Returns the revision of the installed platform tools component.
     *
     * @return the FullRevision or null if the revision couldn't not be found
     */
    FullRevision getPlatformToolsRevision();

    /**
     * Returns the location of the aapt tool.
     */
    File getAapt();

    /**
     * Returns the location of the aidl compiler.
     */
    File getAidlCompiler();

    /**
     * Returns the location of the renderscript compiler.
     */
    File getRenderscriptCompiler();

    /**
     * Returns the location of the dx tool.
     */
    File getDx();

    /**
     * Returns the location of the zip align tool.
     */
    File getZipAlign();

    /**
     * Returns the location of the adb tool.
     */
    File getAdb();
}