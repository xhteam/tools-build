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

package com.android.builder.internal;

import com.android.annotations.NonNull;
import com.android.builder.model.BuildConfig;
import com.google.common.collect.Lists;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An object that contain a BuildConfig configuration
 */
public class BuildConfigImpl implements Serializable, BuildConfig {
    private static final long serialVersionUID = 1L;

    private final List<String> mBuildConfigLines = Lists.newArrayList();

    public void setBuildConfig(String... lines) {
        mBuildConfigLines.clear();
        mBuildConfigLines.addAll(Arrays.asList(lines));
    }

    public void setBuildConfig(String line) {
        mBuildConfigLines.clear();
        mBuildConfigLines.add(line);
    }

    @Override
    @NonNull
    public List<String> getBuildConfig() {
        return mBuildConfigLines;
    }

    @Override
    @NonNull
    public List<Object> getProguardFiles() {
        return Collections.emptyList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BuildConfigImpl that = (BuildConfigImpl) o;

        return mBuildConfigLines.equals(that.mBuildConfigLines);
    }

    @Override
    public int hashCode() {
        return mBuildConfigLines.hashCode();
    }
}
