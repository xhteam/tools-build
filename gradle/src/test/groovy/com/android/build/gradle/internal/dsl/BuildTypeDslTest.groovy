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

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.internal.test.BaseTest
import com.android.builder.BuildType
import com.android.builder.BuilderConstants
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

/**
 * test that the build type are properly initialized
 */
public class BuildTypeDslTest extends BaseTest {

    public void testDebug() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build();

        project.apply plugin: 'android'

        project.android {
            target "android-15"
        }

        AppPlugin plugin = AppPlugin.pluginHolder.plugin

        BuildType type = plugin.buildTypes.get(BuilderConstants.DEBUG).buildType

        assertTrue(type.isDebuggable());
        assertTrue(type.isDebugJniBuild());
        assertNotNull(type.getSigningConfig());
        assertTrue(type.getSigningConfig().isSigningReady());
    }

    public void testRelease() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build();

        project.apply plugin: 'android'

        project.android {
            target "android-15"
        }

        AppPlugin plugin = AppPlugin.pluginHolder.plugin

        BuildType type = plugin.buildTypes.get(BuilderConstants.RELEASE).buildType

        assertFalse(type.isDebuggable());
        assertFalse(type.isDebugJniBuild());
    }

    public void testInitWith() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build();

        project.apply plugin: 'android'

        project.android {
            target "android-15"

            buildTypes {
                debug {
                    packageNameSuffix = ".debug"
                    versionNameSuffix = "-DEBUG"
                }

                foo.initWith(owner.buildTypes.debug)
                foo {
                    packageNameSuffix = ".foo"
                }
            }
        }

        AppPlugin plugin = AppPlugin.pluginHolder.plugin

        BuildType debugType = plugin.buildTypes.get(BuilderConstants.DEBUG).buildType
        BuildType fooType = plugin.buildTypes.get("foo").buildType

        assertEquals(debugType.isDebuggable(),         fooType.isDebuggable())
        assertEquals(debugType.isDebugJniBuild(),      fooType.isDebugJniBuild())
        assertEquals(debugType.isZipAlign(),           fooType.isZipAlign())
        assertEquals(debugType.getVersionNameSuffix(), fooType.getVersionNameSuffix())
        assertEquals(debugType.getSigningConfig(),     fooType.getSigningConfig())
        assertNotSame(debugType.getPackageNameSuffix(), fooType.getPackageNameSuffix())
    }
}
