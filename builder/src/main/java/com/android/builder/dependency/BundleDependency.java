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

package com.android.builder.dependency;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.List;

/**
 * Default implementation of the AndroidDependency interface that handles a default bundle project
 * structure.
 */
public abstract class BundleDependency implements AndroidDependency {

    private final String mName;
    private final File mBundleFolder;
    private List<JarDependency> mLocalDependencies;

    /**
     * Creates the bundle dependency with an optional name
     * @param bundleFolder the folder containing the library
     * @param name an optional name
     */
    protected BundleDependency(@NonNull File bundleFolder, @Nullable String name) {
        mName = name;
        mBundleFolder = bundleFolder;
    }

    protected BundleDependency(@NonNull File bundleFolder) {
        this(bundleFolder, null);
    }

    public String getName() {
        return mName;
    }

    @Override
    public String toString() {
        return mName;
    }

    @Override
    @NonNull
    public File getManifest() {
        return new File(mBundleFolder, SdkConstants.FN_ANDROID_MANIFEST_XML);
    }

    @Override
    @NonNull
    public File getSymbolFile() {
        return new File(mBundleFolder, "R.txt");
    }

    @Override
    @NonNull
    public File getFolder() {
        return mBundleFolder;
    }

    @Override
    @NonNull
    public File getJarFile() {
        return new File(mBundleFolder, SdkConstants.FN_CLASSES_JAR);
    }

    @Override
    @NonNull
    public List<JarDependency> getLocalDependencies() {
        synchronized (this) {
            if (mLocalDependencies == null) {
                mLocalDependencies = Lists.newArrayList();
                File[] jarList = new File(mBundleFolder, SdkConstants.LIBS_FOLDER).listFiles();
                if (jarList != null) {
                    for (File jars : jarList) {
                        if (jars.isFile() && jars.getName().endsWith(".jar")) {
                            mLocalDependencies.add(new JarDependency(jars));
                        }
                    }
                }
            }

            return mLocalDependencies;
        }
    }


    @Override
    @NonNull
    public File getResFolder() {
        return new File(mBundleFolder, SdkConstants.FD_RES);
    }

    @Override
    @NonNull
    public File getAssetsFolder() {
        return new File(mBundleFolder, SdkConstants.FD_ASSETS);
    }

    @Override
    @NonNull
    public File getJniFolder() {
        return new File(mBundleFolder, "jni");
    }

    @Override
    @NonNull
    public File getAidlFolder() {
        return new File(mBundleFolder, SdkConstants.FD_AIDL);
    }

    @Override
    @NonNull
    public File getRenderscriptFolder() {
        return new File(mBundleFolder, SdkConstants.FD_RENDERSCRIPT);
    }

    @Override
    @NonNull
    public File getProguardRules() {
        return new File(mBundleFolder, "proguard.txt");
    }

    @Override
    @NonNull
    public File getLintJar() {
        return new File(mBundleFolder, "lint.jar");
    }

    @NonNull
    public File getBundleFolder() {
        return mBundleFolder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BundleDependency that = (BundleDependency) o;

        return Objects.equal(mName, that.mName);
    }

    @Override
    public int hashCode() {
        return mName != null ? mName.hashCode() : 0;
    }
}
