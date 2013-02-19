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

package com.android.builder;

/**
 * Generic constants.
 */
public class BuilderConstants {

    /**
     * Extension for library packages.
     */
    public final static String EXT_LIB_ARCHIVE = "aar";

    /**
     * The name of the default config.
     */
    public static final String MAIN = "main";

    public final static String DEBUG = "debug";
    public final static String RELEASE = "release";

    public final static String LINT = "lint";

    public final static String REPORTS = "reports";

    public final static String INSTRUMENTATION_TEST = "instrumentTest";
    public final static String INSTRUMENTATION_TESTS = "instrumentTests";
    public final static String INSTRUMENTATION_RESULTS = INSTRUMENTATION_TEST + "-results";

    public final static String UI_TEST = "uiTest";
    public final static String UI_TESTS = "uiTests";
    public final static String UI_RESULTS = UI_TEST + "-results";

    public final static String FLAVORS = "flavors";
    public final static String FLAVORS_ALL = "all";
}
