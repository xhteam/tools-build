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

package com.android.builder.internal.testing;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.utils.ILogger;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * Basic Callable to run tests on a given {@link DeviceConnector} using
 * {@link RemoteAndroidTestRunner}.
 */
public class SimpleTestCallable implements Callable<Boolean> {

    private final String projectName;
    private final DeviceConnector device;
    private final String flavorName;
    private final File resultsDir;
    private final File testApk;
    private final String testPackageName;
    private final String testInstrumentationRunner;
    private final File testedApk;
    private final String testedPackageName;
    private final int timeout;
    private final ILogger logger;

    public SimpleTestCallable(@NonNull DeviceConnector device, @NonNull String projectName,
                     @NonNull  String flavorName,
                     @NonNull  File testApk,
                     @NonNull  String testPackageName,
                     @NonNull  String testInstrumentationRunner,
                     @Nullable File testedApk,
                     @Nullable String testedPackageName,
                     @NonNull  File resultsDir,
                               int timeout,
                     @NonNull  ILogger logger) {
        this.projectName = projectName;
        this.device = device;
        this.flavorName = flavorName;
        this.resultsDir = resultsDir;
        this.testApk = testApk;
        this.testPackageName = testPackageName;
        this.testInstrumentationRunner = testInstrumentationRunner;
        this.testedApk = testedApk;
        this.testedPackageName = testedPackageName;
        this.timeout = timeout;
        this.logger = logger;
    }

    @Override
    public Boolean call() throws Exception {
        String deviceName = device.getName();
        try {
            device.connect(timeout);

            if (testedApk != null) {
                logger.info("DeviceConnector '%s': installing %s", deviceName, testedApk);
                device.installPackage(testedApk);
            }

            logger.info("DeviceConnector '%s': installing %s", deviceName, testApk);
            device.installPackage(testApk);

            RemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
                    testPackageName,
                    testInstrumentationRunner,
                    device);

            runner.setRunName(deviceName);
            runner.setMaxtimeToOutputResponse(timeout);

            CustomTestRunListener runListener = new CustomTestRunListener(
                    deviceName, projectName, flavorName, logger);
            runListener.setReportDir(resultsDir);

            runner.run(runListener);

            return runListener.getRunResult().hasFailedTests();
        } finally {
            // uninstall the apps
            uninstall(testApk, testPackageName, deviceName);

            if (testedApk != null && testedPackageName != null) {
                uninstall(testedApk, testedPackageName, deviceName);
            }

            device.disconnect(timeout);
        }
    }

    private void uninstall(@NonNull File apk,
                           @NonNull String packageName,
                           @NonNull String deviceName)
            throws DeviceException {
        if (packageName != null) {
            logger.info("DeviceConnector '%s': uninstalling %s", deviceName, packageName);
            device.uninstallPackage(packageName);
        } else {
            logger.info("DeviceConnector '%s': unable to uninstall %s: unable to get package name",
                    deviceName, apk);
        }
    }
}
