/*
 * Copyright 2011 the original author or authors.
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

package com.android.build.gradle.internal.test.report;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 * Custom test results based on Gradle's AllTestResults
 */
class AllTestResults extends CompositeTestResults {
    private final Map<String, PackageTestResults> packages = new TreeMap<String, PackageTestResults>();

    public AllTestResults() {
        super(null, null, null, null);
    }

    @Override
    public String getTitle() {
        return "Test Summary";
    }

    public Collection<PackageTestResults> getPackages() {
        return packages.values();
    }

    @Override
    public String getName() {
        return null;
    }

    public TestResult addTest(String className, String testName, long duration,
                              String device, String project, String flavor) {
        PackageTestResults packageResults = addPackageForClass(className,
                device, project, flavor);
        return addTest(packageResults.addTest(className, testName, duration,
                device, project, flavor));
    }

    public ClassTestResults addTestClass(String className,
                                         String device, String project, String flavor) {
        return addPackageForClass(className, device, project, flavor).addClass(className,
                device, project, flavor);
    }

    private PackageTestResults addPackageForClass(String className,
                                                  String device, String project, String flavor) {
        String packageName;
        int pos = className.lastIndexOf(".");
        if (pos != -1) {
            packageName = className.substring(0, pos);
        } else {
            packageName = "";
        }
        return addPackage(packageName, device, project, flavor);
    }

    private PackageTestResults addPackage(String packageName,
                                          String device, String project, String flavor) {
        String key = device + "/" + project + "/" + flavor + "/" + packageName;

        PackageTestResults packageResults = packages.get(key);
        if (packageResults == null) {
            packageResults = new PackageTestResults(packageName, this, device, project, flavor);
            packages.put(key, packageResults);
        }
        return packageResults;
    }
}
