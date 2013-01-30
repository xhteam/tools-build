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

package com.android.builder;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.internal.FakeAndroidTarget;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.repository.packages.FullRevision;
import com.android.utils.ILogger;
import com.google.common.collect.Maps;

import java.io.File;
import java.util.Map;

/**
 * Implementation of {@link SdkParser} for the SDK prebuilds in the Android source tree.
 */
class PlatformSdkParser implements SdkParser {
    private final String mPlatformRootFolder;

    private File mHostTools;
    private final Map<String, File> mToolsMap = Maps.newHashMapWithExpectedSize(6);
    private File mDx;
    private File mAdb;

    PlatformSdkParser(@NonNull String sdkLocation) {
        mPlatformRootFolder = sdkLocation;
    }

    @Override
    public IAndroidTarget resolveTarget(String target, ILogger logger) {
        return new FakeAndroidTarget(mPlatformRootFolder, target);
    }

    @Override
    public String getAnnotationsJar() {
        String host;
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) {
            host = "darwin-x86";
        } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_LINUX) {
            host = "linux";
        } else {
            throw new IllegalStateException("Windows is not supported for platform development");
        }

        return mPlatformRootFolder + "/out/host/" + host + "/framework/annotations.jar";
    }

    @Override
    public FullRevision getPlatformToolsRevision() {
        return new FullRevision(99);
    }

    @Override
    public File getAapt() {
        return getTool(SdkConstants.FN_AAPT);
    }

    @Override
    public File getAidlCompiler() {
        return getTool(SdkConstants.FN_AIDL);
    }

    @Override
    public File getRenderscriptCompiler() {
        return getTool(SdkConstants.FN_RENDERSCRIPT);
    }

    @Override
    public File getDx() {
        if (mDx == null) {
            mDx =  new File(mPlatformRootFolder, "prebuilts/sdk/tools/dx");
        }

        return mDx;
    }

    @Override
    public File getZipAlign() {
        return getTool(SdkConstants.FN_ZIPALIGN);
    }

    @Override
    public File getAdb() {
        if (mAdb == null) {

            if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) {
                mAdb = new File(mPlatformRootFolder, "out/host/darwin-x86/bin/adb");
            } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_LINUX) {
                mAdb = new File(mPlatformRootFolder, "out/host/linux-x86/bin/adb");
            } else {
                throw new IllegalStateException("Windows is not supported for platform development");
            }
        }

        return mAdb;
    }

    private File getTool(String filename) {
        File f = mToolsMap.get(filename);
        if (f == null) {
            File platformTools = getHostToolsFolder();
            if (!platformTools.isDirectory()) {
                return null;
            }

            f = new File(platformTools, filename);
            mToolsMap.put(filename, f);
        }

        return f;
    }

    private File getHostToolsFolder() {
        if (mHostTools == null) {
            File tools = new File(mPlatformRootFolder, "prebuilts/sdk/tools");
            if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) {
                mHostTools = new File(tools, "darwin");
            } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_LINUX) {
                mHostTools = new File(tools, "linux");
            } else {
                throw new IllegalStateException("Windows is not supported for platform development");
            }
        }
        return mHostTools;
    }
}
