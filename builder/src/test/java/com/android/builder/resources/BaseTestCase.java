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

import com.google.common.collect.ListMultimap;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class BaseTestCase extends TestCase {

    protected static File getRoot(String name) {
        File root = new File("src/test/resources/testData/" + name);
        assertTrue("Test folder '" + name + "' does not exist!",
                root.isDirectory());

        return root;
    }

    protected static File getCanonicalRoot(String name) throws IOException {
        File root = getRoot(name);
        return root.getCanonicalFile();
    }

    protected void verifyResourceExists(ResourceMap resourceMap, String... resourceKeys) {
        ListMultimap<String, Resource> map = resourceMap.getResourceMap();

        for (String resKey : resourceKeys) {
            List<Resource> resources = map.get(resKey);
            assertTrue("resource '" + resKey + "' is missing!", resources.size() > 0);
        }
    }

    /**
     * Compares two resource maps.
     *
     * if <var>fullCompare</var> is <code>true</code> then it'll make sure the multimaps contains
     * the same number of items, otherwise it'll only checks that each resource key is present
     * in both maps.
     *
     * @param resourceMap1
     * @param resourceMap2
     * @param fullCompare
     */
    protected void compareResourceMaps(ResourceMap resourceMap1, ResourceMap resourceMap2,
                                       boolean fullCompare) {
        assertEquals(resourceMap1.size(), resourceMap2.size());

        // compare the resources are all the same
        ListMultimap<String, Resource> map1 = resourceMap1.getResourceMap();
        ListMultimap<String, Resource> map2 = resourceMap2.getResourceMap();
        for (String key : map1.keySet()) {
            List<Resource> items1 = map1.get(key);
            List<Resource> items2 = map2.get(key);
            if (fullCompare) {
                assertEquals("Wrong size for " + key, items1.size(), items2.size());
            } else {
                boolean map1HasItem = items1.size() > 0;
                boolean map2HasItem = items2.size() > 0;
                assertEquals("resource " + key + " missing from one map", map1HasItem, map2HasItem);
            }
        }
    }
}
