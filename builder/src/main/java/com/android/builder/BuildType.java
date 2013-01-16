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
import com.android.annotations.Nullable;
import com.google.common.base.Objects;

public class BuildType extends BuildConfig {
    private static final long serialVersionUID = 1L;

    public final static String DEBUG = "debug";
    public final static String RELEASE = "release";

    private final String mName;
    private boolean mDebuggable;
    private boolean mDebugJniBuild;
    private String mPackageNameSuffix = null;
    private String mVersionNameSuffix = null;
    private boolean mRunProguard = false;
    private SigningConfig mSigningConfig = null;

    private boolean mZipAlign = true;

    public BuildType(@NonNull String name) {
        mName = name;
        if (DEBUG.equals(name)) {
            initDebug();
        } else if (RELEASE.equals(name)) {
            initRelease();
        }
    }

    private void initDebug() {
        mDebuggable = true;
        mDebugJniBuild = true;
        mZipAlign = false;
        mSigningConfig = new SigningConfig(SigningConfig.DEBUG);
    }

    private void initRelease() {
        mDebuggable = false;
        mDebugJniBuild = false;
    }

    public String getName() {
        return mName;
    }

    public BuildType setDebuggable(boolean debuggable) {
        mDebuggable = debuggable;
        return this;
    }

    public boolean isDebuggable() {
        return mDebuggable;
    }

    public BuildType setDebugJniBuild(boolean debugJniBuild) {
        mDebugJniBuild = debugJniBuild;
        return this;
    }

    public boolean isDebugJniBuild() {
        return mDebugJniBuild;
    }

    public BuildType setPackageNameSuffix(@Nullable String packageNameSuffix) {
        mPackageNameSuffix = packageNameSuffix;
        return this;
    }

    @Nullable public String getPackageNameSuffix() {
        return mPackageNameSuffix;
    }

    public BuildType setVersionNameSuffix(@Nullable String versionNameSuffix) {
        mVersionNameSuffix = versionNameSuffix;
        return this;
    }

    @Nullable public String getVersionNameSuffix() {
        return mVersionNameSuffix;
    }

    public BuildType setRunProguard(boolean runProguard) {
        mRunProguard = runProguard;
        return this;
    }

    public boolean isRunProguard() {
        return mRunProguard;
    }

    public BuildType setZipAlign(boolean zipAlign) {
        mZipAlign = zipAlign;
        return this;
    }

    public boolean isZipAlign() {
        return mZipAlign;
    }

    public BuildType setSigningConfig(@Nullable SigningConfig signingConfig) {
        mSigningConfig = signingConfig;
        return this;
    }

    @Nullable
    SigningConfig getSigningConfig() {
        return mSigningConfig;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        BuildType buildType = (BuildType) o;

        if (!mName.equals(buildType.mName)) return false;
        if (mDebugJniBuild != buildType.mDebugJniBuild) return false;
        if (mDebuggable != buildType.mDebuggable) return false;
        if (mRunProguard != buildType.mRunProguard) return false;
        if (mZipAlign != buildType.mZipAlign) return false;
        if (mPackageNameSuffix != null ?
                !mPackageNameSuffix.equals(buildType.mPackageNameSuffix) :
                buildType.mPackageNameSuffix != null)
            return false;
        if (mVersionNameSuffix != null ?
                !mVersionNameSuffix.equals(buildType.mVersionNameSuffix) :
                buildType.mVersionNameSuffix != null)
            return false;
        if (mSigningConfig != null ?
                !mSigningConfig.equals(buildType.mSigningConfig) :
                buildType.mSigningConfig != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (mName.hashCode());
        result = 31 * result + (mDebuggable ? 1 : 0);
        result = 31 * result + (mDebugJniBuild ? 1 : 0);
        result = 31 * result + (mPackageNameSuffix != null ? mPackageNameSuffix.hashCode() : 0);
        result = 31 * result + (mVersionNameSuffix != null ? mVersionNameSuffix.hashCode() : 0);
        result = 31 * result + (mRunProguard ? 1 : 0);
        result = 31 * result + (mZipAlign ? 1 : 0);
        result = 31 * result + (mSigningConfig != null ? mSigningConfig.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", mName)
                .add("debuggable", mDebuggable)
                .add("debugJniBuild", mDebugJniBuild)
                .add("packageNameSuffix", mPackageNameSuffix)
                .add("versionNameSuffix", mVersionNameSuffix)
                .add("runProguard", mRunProguard)
                .add("zipAlign", mZipAlign)
                .add("keystore", mSigningConfig)
                .toString();
    }
}
