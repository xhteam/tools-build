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

package com.android.builder.resources;

import java.io.File;
import java.io.IOException;

public class ResourceSetTest extends BaseTestCase {

    private static ResourceSet sBaseResourceSet = null;

    public void testBaseResourceSetByCount() throws Exception {
        ResourceSet resourceSet = getBaseResourceSet();
        assertEquals(23, resourceSet.size());
    }

    public void testBaseResourceSetByName() throws Exception {
        ResourceSet resourceSet = getBaseResourceSet();

        verifyResourceExists(resourceSet,
                "drawable/icon",
                "drawable/patch",
                "raw/foo",
                "layout/main",
                "layout/layout_ref",
                "layout/alias_replaced_by_file",
                "layout/file_replaced_by_alias",
                "drawable/color_drawable",
                "drawable/drawable_ref",
                "color/color",
                "string/basic_string",
                "string/xliff_string",
                "string/styled_string",
                "style/style",
                "array/string_array",
                "attr/dimen_attr",
                "attr/string_attr",
                "attr/enum_attr",
                "attr/flag_attr",
                "declare-styleable/declare_styleable",
                "dimen/dimen",
                "id/item_id",
                "integer/integer"
        );
    }

    public void testDupResourceSet() throws Exception {
        File root = getRoot("dupResourceSet");

        ResourceSet set = new ResourceSet("main");
        set.addSource(new File(root, "res1"));
        set.addSource(new File(root, "res2"));
        boolean gotException = false;
        try {
            set.loadFromFiles();
        } catch (DuplicateResourceException e) {
            gotException = true;
        }

        assertTrue(gotException);
    }

    static ResourceSet getBaseResourceSet() throws DuplicateResourceException, IOException {
        if (sBaseResourceSet == null) {
            File root = getRoot("baseResourceSet");

            sBaseResourceSet = new ResourceSet("main");
            sBaseResourceSet.addSource(root);
            sBaseResourceSet.loadFromFiles();
        }

        return sBaseResourceSet;
    }
}
