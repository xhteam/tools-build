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
package com.android.build.gradle

import com.android.build.gradle.internal.dsl.BuildTypeDsl
import com.android.build.gradle.internal.dsl.SigningConfigDsl
import com.android.builder.BuildType
import com.android.builder.BuilderConstants
import com.android.builder.signing.SigningConfig
import org.gradle.api.Action
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.reflect.Instantiator

/**
 * Extension for 'library' project.
 */
public class LibraryExtension extends BaseExtension {

    final BuildType debug
    final BuildType release
    final SigningConfig debugSigningConfig

    LibraryExtension(BasePlugin plugin, ProjectInternal project, Instantiator instantiator) {
        super(plugin, project, instantiator)

        debugSigningConfig = instantiator.newInstance(SigningConfigDsl.class,
                BuilderConstants.DEBUG)
        debugSigningConfig.initDebug()

        debug = instantiator.newInstance(BuildTypeDsl.class, BuilderConstants.DEBUG)
        debug.init(debugSigningConfig)
        release = instantiator.newInstance(BuildTypeDsl.class, BuilderConstants.RELEASE)
        release.init(null)
    }

    void debug(Action<BuildType> action) {
        action.execute(debug);
    }

    void release(Action<BuildType> action) {
        action.execute(release);
    }

    void debugSigningConfig(Action<SigningConfig> action) {
        action.execute(debugSigningConfig)
    }
}
