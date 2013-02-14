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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.repository.packages.FullRevision;
import com.android.sdklib.repository.PkgProps;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Closeables;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;
import java.util.Properties;

import static com.android.SdkConstants.FD_PLATFORM_TOOLS;
import static com.android.SdkConstants.FD_SUPPORT;
import static com.android.SdkConstants.FD_TOOLS;
import static com.android.SdkConstants.FN_ANNOTATIONS_JAR;
import static com.android.SdkConstants.FN_SOURCE_PROP;

/**
 * Default implementation of {@link SdkParser} for a normal Android SDK distribution.
 */
public class DefaultSdkParser implements SdkParser {

    private final String mSdkLocation;
    private SdkManager mManager;

    private File mTools;
    private File mPlatformTools;
    private final Map<String, File> mToolsMap = Maps.newHashMapWithExpectedSize(6);

    public DefaultSdkParser(@NonNull String sdkLocation) {
        if (!sdkLocation.endsWith(File.separator)) {
            mSdkLocation = sdkLocation + File.separator;
        } else {
            mSdkLocation = sdkLocation;
        }
    }

    @Override
    public IAndroidTarget resolveTarget(@NonNull String target, @NonNull ILogger logger) {
        if (mManager == null) {
            mManager = SdkManager.createManager(mSdkLocation, logger);
            if (mManager == null) {
                throw new RuntimeException("failed to parse SDK!");
            }
        }

        return mManager.getTargetFromHashString(target);
    }

    @Override
    public String getAnnotationsJar() {
        return mSdkLocation + FD_TOOLS +
                '/' + FD_SUPPORT +
                '/' + FN_ANNOTATIONS_JAR;
    }

    @Override
    public FullRevision getPlatformToolsRevision() {
        File platformTools = getPlatformToolsFolder();
        if (!platformTools.isDirectory()) {
            return null;
        }

        Reader reader = null;
        try {
            reader = new InputStreamReader(
                    new FileInputStream(new File(platformTools, FN_SOURCE_PROP)),
                    Charsets.UTF_8);
            Properties props = new Properties();
            props.load(reader);

            String value = props.getProperty(PkgProps.PKG_REVISION);

            return FullRevision.parseRevision(value);

        } catch (FileNotFoundException ignore) {
            // return null below.
        } catch (IOException ignore) {
            // return null below.
        } catch (NumberFormatException ignore) {
            // return null below.
        } finally {
            Closeables.closeQuietly(reader);
        }

        return null;
    }

    @Override
    public File getAapt() {
        return getPlatformTool(SdkConstants.FN_AAPT);
    }

    @Override
    public File getAidlCompiler() {
        return getPlatformTool(SdkConstants.FN_AIDL);
    }

    @Override
    public File getRenderscriptCompiler() {
        return getPlatformTool(SdkConstants.FN_RENDERSCRIPT);
    }

    @Override
    public File getDx() {
        return getPlatformTool(SdkConstants.FN_DX);
    }

    @Override
    public File getZipAlign() {
        return getTool(SdkConstants.FN_ZIPALIGN);
    }

    @Override
    public File getAdb() {
        return getPlatformTool(SdkConstants.FN_ADB);
    }

    private File getPlatformTool(String filename) {
        File f = mToolsMap.get(filename);
        if (f == null) {
            File platformTools = getPlatformToolsFolder();
            if (!platformTools.isDirectory()) {
                return null;
            }

            f = new File(platformTools, filename);
            mToolsMap.put(filename, f);
        }

        return f;
    }

    private File getTool(String filename) {
        File f = mToolsMap.get(filename);
        if (f == null) {
            File platformTools = getToolsFolder();
            if (!platformTools.isDirectory()) {
                return null;
            }

            f = new File(platformTools, filename);
            mToolsMap.put(filename, f);
        }

        return f;
    }

    private File getPlatformToolsFolder() {
        if (mPlatformTools == null) {
            mPlatformTools = new File(mSdkLocation, FD_PLATFORM_TOOLS);
        }

        return mPlatformTools;
    }

    private File getToolsFolder() {
        if (mTools == null) {
            mTools = new File(mSdkLocation, FD_TOOLS);
        }

        return mTools;
    }

}
