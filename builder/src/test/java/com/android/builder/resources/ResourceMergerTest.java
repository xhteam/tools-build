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

import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class ResourceMergerTest extends BaseTestCase {

    private static ResourceSet sMergedSet = null;

    public void testMergeByCount() throws Exception {
        ResourceSet mergedSet = getMergedSet();

        assertEquals(25, mergedSet.getSize());
    }

    public void testMergedResourcesByName() throws Exception {
        ResourceSet mergedSet = getMergedSet();

        verifyResources(mergedSet,
                "drawable/icon",
                "drawable-ldpi/icon",
                "drawable/icon2",
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

    public void testReplacedLayout() throws Exception {
        ResourceSet mergedSet = getMergedSet();

        Resource mainLayout = mergedSet.getResourceMap().get("layout/main");
        ResourceFile sourceFile = mainLayout.getSource();
        assertTrue(sourceFile.getFile().getAbsolutePath().endsWith("overlay/layout/main.xml"));
    }

    public void testReplacedAlias() throws Exception {
        ResourceSet mergedSet = getMergedSet();

        Resource layout = mergedSet.getResourceMap().get("layout/alias_replaced_by_file");
        // since it's replaced by a file, there's no node.
        assertNull(layout.getValue());
    }

    public void testReplacedFile() throws Exception {
        ResourceSet mergedSet = getMergedSet();

        Resource layout = mergedSet.getResourceMap().get("layout/file_replaced_by_alias");
        // since it's replaced by a file, there's no node.
        assertNotNull(layout.getValue());
    }

    public void testMergeWrite() throws Exception {
        ResourceSet mergedSet = getMergedSet();

        File folder = getWrittenSet();

        ResourceSet writtenSet = new ResourceSet();
        writtenSet.addSource(folder);

        // compare the two sets.
        assertEquals(mergedSet.getSize(), writtenSet.getSize());

        // compare the resources are all the same
        Map<String, Resource> map = writtenSet.getResourceMap();
        for (Resource item : mergedSet.getResources()) {
            assertNotNull(map.get(item.getKey()));
        }
    }

    private static ResourceSet getMergedSet() throws DuplicateResourceException {
        if (sMergedSet == null) {
            File root = getRoot("baseMerge");

            ResourceSet res = ResourceSetTest.getBaseResourceSet();

            ResourceSet overlay = new ResourceSet();
            overlay.addSource(new File(root, "overlay"));

            ResourceMerger merger = new ResourceMerger();
            merger.addResourceSet(res);
            merger.addResourceSet(overlay);

            sMergedSet = merger.getMergedSet();
        }

        return sMergedSet;
    }

    private static File getWrittenSet() throws DuplicateResourceException, IOException {
        ResourceSet mergedSet = getMergedSet();

        File folder = Files.createTempDir();

        mergedSet.writeTo(folder);

        return folder;
    }
}
