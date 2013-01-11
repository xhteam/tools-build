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

package com.android.build.gradle
import com.android.build.gradle.internal.tasks.AndroidReportTask
import com.android.build.gradle.internal.tasks.TestLibraryTask
import com.android.build.gradle.internal.test.TestOptions
import com.android.build.gradle.internal.test.report.ReportType
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.TaskCollection
/**
 * Gradle plugin class for 'reporting' projects.
 *
 * This is mostly used to aggregate reports from subprojects.
 *
 */
class ReportingPlugin implements org.gradle.api.Plugin<Project> {

    private TestOptions extension

    @Override
    void apply(Project project) {
        // make sure this project depends on the evaluation of all sub projects so that
        // it's evaluated last.
        project.evaluationDependsOnChildren()

        extension = project.extensions.create('android', TestOptions)

        AndroidReportTask testTask = project.tasks.add("test", AndroidReportTask)
        testTask.group = JavaBasePlugin.VERIFICATION_GROUP
        testTask.description = "Installs and runs tests for all flavors, and aggregate the results"
        testTask.reportType = ReportType.MULTI_PROJECT

        testTask.conventionMapping.resultsDir = {
            String location = extension.resultsDir != null ?
                extension.resultsDir : "$project.buildDir/test-results"

            project.file(location)
        }
        testTask.conventionMapping.reportsDir = {
            String location = extension.reportDir != null ?
                extension.reportDir : "$project.buildDir/reports/tests"

            project.file(location)
        }

        // TODO: deal with existing/missing test/check tasks.
//        project.tasks.check.dependsOn testTask

        // gather the subprojects
        project.afterEvaluate {
            project.subprojects.each { p ->
                TaskCollection<AndroidReportTask> tasks = p.tasks.withType(AndroidReportTask)
                for (AndroidReportTask task : tasks) {
                    testTask.addTask(task)
                }
                TaskCollection<TestLibraryTask> tasks2= p.tasks.withType(TestLibraryTask)
                for (TestLibraryTask task : tasks2) {
                    testTask.addTask(task)
                }
            }
        }

        // If gradle is launched with --continue, we want to run all tests and generate an
        // aggregate report (to help with the fact that we may have several build variants).
        // To do that, the "test" task (which does the aggregation) must always run even if
        // one of its dependent task (all the testFlavor tasks) fails, so we make them ignore their
        // error.
        // We cannot do that always: in case the test task is not going to run, we do want the
        // individual testFlavor tasks to fail.
        if (testTask != null && project.gradle.startParameter.continueOnFailure) {
            project.gradle.taskGraph.whenReady { taskGraph ->
                if (taskGraph.hasTask(testTask)) {
                    testTask.setWillRun()
                }
            }
        }
    }
}
