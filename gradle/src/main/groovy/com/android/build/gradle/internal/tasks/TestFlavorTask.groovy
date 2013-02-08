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
package com.android.build.gradle.internal.tasks

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.internal.ApplicationVariant
import com.android.build.gradle.internal.test.report.ReportType
import com.android.build.gradle.internal.test.report.TestReport
import com.android.builder.internal.util.concurrent.WaitableExecutor
import com.android.builder.testing.CustomTestRunListener
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner
import com.android.utils.ILogger
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.logging.ConsoleRenderer
import org.gradle.tooling.BuildException

import java.util.concurrent.Callable
/**
 * Run tests for a given variant
 */
public class TestFlavorTask extends BaseTask implements AndroidTestTask {

    @InputFile
    File adbExe

    @InputFile
    File testApp

    @InputFile @Optional
    File testedApp

    @OutputDirectory
    File reportsDir

    @OutputDirectory
    File resultsDir

    @Input
    String flavorName

    ApplicationVariant testedVariant

    boolean ignoreFailures
    boolean testFailed

    /**
     * Callable to run tests on a given device.
     */
    private static class DeviceTestRunner implements Callable<Boolean> {

        private final IDevice mDevice
        private final String mDeviceName
        private final String mFlavorName
        private final File mResultsDir
        private final File mTestApk
        private final ApplicationVariant mVariant
        private final File mTestedApk
        private final ApplicationVariant mTestedVariant
        private final ILogger mLogger
        private final Project mProject

        DeviceTestRunner(@NonNull IDevice device, @NonNull Project project,
                         @NonNull String flavorName,
                         @NonNull File testApk, @NonNull ApplicationVariant variant,
                         @Nullable File testedApk, @NonNull ApplicationVariant testedVariant,
                         @NonNull File resultsDir, @NonNull ILogger logger) {
            mProject = project
            mDevice = device
            mDeviceName = computeDeviceName(device)
            mFlavorName = flavorName
            mResultsDir = resultsDir
            mTestApk = testApk
            mVariant = variant
            mTestedApk = testedApk
            mTestedVariant = testedVariant
            mLogger = logger
        }

        @Override
        Boolean call() throws Exception {
            try {
                if (mTestedApk != null) {
                    mLogger.info("Device '%s': installing %s", mDeviceName, mTestedApk.absolutePath)
                    mDevice.installPackage(mTestedApk.absolutePath, true /*reinstall*/)
                }

                mLogger.info("Device '%s': installing %s", mDeviceName, mTestApk.absolutePath)
                mDevice.installPackage(mTestApk.absolutePath, true /*reinstall*/)


                RemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
                        mVariant.config.packageName, mVariant.config.instrumentationRunner,
                        mDevice)

                runner.setRunName(mDevice.serialNumber)
                CustomTestRunListener runListener = new CustomTestRunListener(
                        mDeviceName, mProject.name, mFlavorName, mLogger)
                runListener.setReportDir(mResultsDir)

                runner.run(runListener)

                return runListener.runResult.hasFailedTests()
            } finally {
                // uninstall the apps
                String packageName = mVariant.packageName
                mLogger.info("Device '%s': uninstalling %s", mDeviceName, packageName)
                mDevice.uninstallPackage(packageName)

                if (mTestedApk != null) {
                    packageName = mTestedVariant.packageName
                    mLogger.info("Device '%s': uninstalling %s", mDeviceName, packageName)
                    mDevice.uninstallPackage(packageName)
                }
            }
        }

        private String computeDeviceName(@NonNull IDevice device) {
            String version = device.getProperty(IDevice.PROP_BUILD_VERSION);
            boolean emulator = device.isEmulator()

            String name;
            if (emulator) {
                name = mDevice.avdName != null ? mDevice.avdName + "(AVD)" : mDevice.serialNumber
            } else {
                String model = device.getProperty(IDevice.PROP_DEVICE_MODEL)
                name = model != null ? model : mDevice.serialNumber
            }

            return version != null ? name + " - " + version : name
        }
    }

    @TaskAction
    protected void runTests() {
        AndroidDebugBridge.initIfNeeded(false /*clientSupport*/)

        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge(getAdbExe().absolutePath,
                false /*forceNewBridge*/)

        long timeOut = 30000 // 30 sec
        int sleepTime = 1000
        while (!bridge.hasInitialDeviceList() && timeOut > 0) {
            sleep(sleepTime)
            timeOut -= sleepTime
        }

        if (timeOut <= 0 && !bridge.hasInitialDeviceList()) {
            throw new BuildException("Timeout getting device list.", null)
        }

        IDevice[] devices = bridge.devices

        if (devices.length == 0) {
            throw new BuildException("No connected devices!", null)
        }

        File resultsOutDir = getResultsDir()

        // empty the folder.
        emptyFolder(resultsOutDir)

        WaitableExecutor<Boolean> executor = new WaitableExecutor<Boolean>();

        File testApk = getTestApp()
        File testedApk = getTestedApp()

        String flavor = getFlavorName()

        for (IDevice device : devices) {
            executor.execute(new DeviceTestRunner(device, project, flavor,
                    testApk, variant,
                    testedApk, testedVariant,
                    resultsOutDir, plugin.logger))
        }

        List<Boolean> results = executor.waitForTasks()

        // run the report from the results.
        File reportOutDir = getReportsDir()
        emptyFolder(reportOutDir)

        TestReport report = new TestReport(ReportType.SINGLE_FLAVOR, resultsOutDir, reportOutDir)
        report.generateReport()

        // check if one test failed.
        for (Boolean b : results) {
            if (b.booleanValue()) {
                testFailed = true
                String reportUrl = new ConsoleRenderer().asClickableFileUrl(
                        new File(reportOutDir, "index.html"));
                String message = "There were failing tests. See the report at: " + reportUrl;
                if (getIgnoreFailures()) {
                    getLogger().warn(message)

                    return
                } else {
                    throw new GradleException(message)
                }
            }
        }

        testFailed = false
    }
}
