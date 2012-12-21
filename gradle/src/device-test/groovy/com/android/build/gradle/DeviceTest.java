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

package com.android.build.gradle;

import junit.framework.TestCase;

/**
 * Device tests.
 *
 * This build relies on the {@link BuildTest} to have been run, so that all that there
 * is left to do is deploy the tested and test apps to the device and run the tests (and gather
 * the result).
 *
 * The dependency on the build tests is ensured by the gradle tasks definition.
 *
 */
public class DeviceTest extends TestCase {

    public void testFoo() throws Exception {
        assertTrue(true);
    }
}
