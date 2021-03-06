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

import junit.framework.TestCase;

public class ProductFlavorTest extends TestCase {

    private ProductFlavor mDefault;
    private ProductFlavor mDefault2;
    private ProductFlavor mCustom;

    @Override
    protected void setUp() throws Exception {
        mDefault = new ProductFlavor("default");
        mDefault2 = new ProductFlavor("default2");

        mCustom = new ProductFlavor("custom");
        mCustom.setMinSdkVersion(42);
        mCustom.setTargetSdkVersion(43);
        mCustom.setVersionCode(44);
        mCustom.setVersionName("42.0");
        mCustom.setPackageName("com.forty.two");
        mCustom.setTestPackageName("com.forty.two.test");
        mCustom.setTestInstrumentationRunner("com.forty.two.test.Runner");
    }

    public void testMergeOnDefault() {
        ProductFlavor flavor = mCustom.mergeOver(mDefault);

        assertEquals(42, flavor.getMinSdkVersion());
        assertEquals(43, flavor.getTargetSdkVersion());
        assertEquals(44, flavor.getVersionCode());
        assertEquals("42.0", flavor.getVersionName());
        assertEquals("com.forty.two", flavor.getPackageName());
        assertEquals("com.forty.two.test", flavor.getTestPackageName());
        assertEquals("com.forty.two.test.Runner", flavor.getTestInstrumentationRunner());
    }

    public void testMergeOnCustom() {
        ProductFlavor flavor = mDefault.mergeOver(mCustom);

        assertEquals(42, flavor.getMinSdkVersion());
        assertEquals(43, flavor.getTargetSdkVersion());
        assertEquals(44, flavor.getVersionCode());
        assertEquals("42.0", flavor.getVersionName());
        assertEquals("com.forty.two", flavor.getPackageName());
        assertEquals("com.forty.two.test", flavor.getTestPackageName());
        assertEquals("com.forty.two.test.Runner", flavor.getTestInstrumentationRunner());
    }

    public void testMergeDefaultOnDefault() {
        ProductFlavor flavor = mDefault.mergeOver(mDefault2);

        assertEquals(-1, flavor.getMinSdkVersion());
        assertEquals(-1, flavor.getTargetSdkVersion());
        assertEquals(-1, flavor.getVersionCode());
        assertNull(flavor.getVersionName());
        assertNull(flavor.getPackageName());
        assertNull(flavor.getTestPackageName());
        assertNull(flavor.getTestInstrumentationRunner());
    }
}
