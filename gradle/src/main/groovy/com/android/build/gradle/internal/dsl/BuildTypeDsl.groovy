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
import com.android.builder.BuildType
import com.android.builder.BuilderConstants
import com.android.builder.signing.SigningConfig

/**
 * DSL overlay to make methods that accept String... work.
 */
public class BuildTypeDsl extends BuildType implements Serializable {
    private static final long serialVersionUID = 1L

    BuildTypeDsl(@NonNull String name) {
        super(name)
    }

    public void init(SigningConfig debugSigningConfig) {
        if (BuilderConstants.DEBUG.equals(getName())) {
            setDebuggable(true)
            setZipAlign(false)

            assert debugSigningConfig != null
            setSigningConfig(debugSigningConfig)
        } else if (BuilderConstants.RELEASE.equals(getName())) {
            // no config needed for now.
        }
    }

    public BuildTypeDsl initWith(BuildType that) {
        setDebuggable(that.isDebuggable())
        setJniDebugBuild(that.isJniDebugBuild())
        setRenderscriptDebugBuild(that.isRenderscriptDebugBuild())
        setRenderscriptOptimLevel(that.getRenderscriptOptimLevel())
        setPackageNameSuffix(that.getPackageNameSuffix())
        setVersionNameSuffix(that.getVersionNameSuffix())
        setRunProguard(that.isRunProguard())
        setZipAlign(that.isZipAlign())
        setSigningConfig(that.getSigningConfig())

        return this;
    }


    @Override
    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false
        if (!super.equals(o)) return false

        return true
    }

    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.

    public void buildConfig(String... lines) {
        setBuildConfig(lines)
    }

    public void buildConfig(String line) {
        setBuildConfig(line)
    }
}
