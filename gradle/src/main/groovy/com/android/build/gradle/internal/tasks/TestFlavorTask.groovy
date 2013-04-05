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
import com.android.build.gradle.internal.test.report.ReportType
import com.android.build.gradle.internal.test.report.TestReport
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.TestVariantData
import com.android.builder.internal.util.concurrent.WaitableExecutor
import com.android.builder.testing.CustomTestRunListener
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner
import com.android.ide.common.internal.WaitableExecutor
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

    BaseVariantData testedVariantData

    boolean ignoreFailures
    boolean testFailed

    /**
     * Callable to run tests on a given device.
     */
    private static class DeviceTestRunner implements Callable<Boolean> {

        private final Project project
        private final IDevice device
        private final String deviceName
        private final String flavorName
        private final File resultsDir
        private final File testApk
        private final TestVariantData variantData
        private final File testedApk
        private final BaseVariantData testedVariantData
        private final ILogger logger

        DeviceTestRunner(@NonNull IDevice device, @NonNull Project project,
                         @NonNull String flavorName,
                         @NonNull File testApk, @NonNull TestVariantData variantData,
                         @Nullable File testedApk, @NonNull BaseVariantData testedVariantData,
                         @NonNull File resultsDir, @NonNull ILogger logger) {
            this.project = project
            this.device = device
            this.deviceName = computeDeviceName(device)
            this.flavorName = flavorName
            this.resultsDir = resultsDir
            this.testApk = testApk
            this.variantData = variantData
            this.testedApk = testedApk
            this.testedVariantData = testedVariantData
            this.logger = logger
        }

        @Override
        Boolean call() throws Exception {
            try {
                if (testedApk != null) {
                    logger.info("Device '%s': installing %s", deviceName, testedApk.absolutePath)
                    device.installPackage(testedApk.absolutePath, true /*reinstall*/)
                }

                logger.info("Device '%s': installing %s", deviceName, testApk.absolutePath)
                device.installPackage(testApk.absolutePath, true /*reinstall*/)

                RemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
                        variantData.variantConfiguration.packageName,
                        variantData.variantConfiguration.instrumentationRunner,
                        device)

                runner.setRunName(device.serialNumber)
                CustomTestRunListener runListener = new CustomTestRunListener(
                        deviceName, project.name, flavorName, logger)
                runListener.setReportDir(resultsDir)

                runner.run(runListener)

                return runListener.runResult.hasFailedTests()
            } finally {
                // uninstall the apps
                String packageName = variantData.packageName
                logger.info("Device '%s': uninstalling %s", deviceName, packageName)
                device.uninstallPackage(packageName)

                if (testedApk != null) {
                    packageName = testedVariantData.packageName
                    logger.info("Device '%s': uninstalling %s", deviceName, packageName)
                    device.uninstallPackage(packageName)
                }
            }
        }

        private static String computeDeviceName(@NonNull IDevice device) {
            String version = device.getProperty(IDevice.PROP_BUILD_VERSION);
            boolean emulator = device.isEmulator()

            String name;
            if (emulator) {
                name = device.avdName != null ? device.avdName + "(AVD)" : device.serialNumber
            } else {
                String model = device.getProperty(IDevice.PROP_DEVICE_MODEL)
                name = model != null ? model : device.serialNumber
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

        assert variant instanceof TestVariantData
        TestVariantData testVariantData = (TestVariantData) variant

        for (IDevice device : devices) {
            executor.execute(new DeviceTestRunner(device, project, flavor,
                    testApk, testVariantData,
                    testedApk, testedVariantData,
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
