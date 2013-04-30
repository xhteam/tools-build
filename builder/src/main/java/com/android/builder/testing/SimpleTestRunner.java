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

package com.android.builder.testing;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.testing.SimpleTestCallable;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.TestRunner;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.ILogger;

import java.io.File;
import java.util.List;

/**
 * Basic {@link TestRunner} running tests on all devices.
 */
public class SimpleTestRunner implements TestRunner {

    @Override
    public boolean runTests(
            @NonNull String projectName,
            @NonNull String variantName,
            @NonNull File testApk,
            @NonNull String testPackageName,
            @NonNull String testInstrumentationRunner,
            @Nullable File testedApk,
            @Nullable String testedPackageName,
            @NonNull List<? extends DeviceConnector> deviceList,
            @NonNull File resultsDir,
            @NonNull ILogger logger) {

        WaitableExecutor<Boolean> executor = new WaitableExecutor<Boolean>();

        for (DeviceConnector device : deviceList) {
            executor.execute(new SimpleTestCallable(device, projectName, variantName,
                    testApk, testPackageName, testInstrumentationRunner,
                    testedApk, testedPackageName,
                    resultsDir, logger));
        }

        try {
            List<Boolean> results = executor.waitForTasks();

            // check if one test failed.
            for (Boolean b : results) {
                if (b) {
                    return false;
                }
            }
            return true;

        } catch (Exception e) {
            return false;
        }
    }
}
