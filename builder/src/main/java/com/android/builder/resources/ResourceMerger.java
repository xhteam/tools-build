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

import com.google.common.collect.Maps;

import java.util.Map;

/**
 */
public class ResourceMerger {

    /**
     * The merged resources
     */
    private final Map<String, Resource> mItems = Maps.newHashMap();

    public ResourceMerger() {

    }

    /**
     * adds a new sourceset and overlays it on top of the existing resource items
     * @param resourceSet the ResourceSet to add.
     */
    public void addResourceSet(ResourceSet resourceSet) {
        // TODO figure out if we allow partial overlay through a per-resource flag.
        mItems.putAll(resourceSet.getResourceMap());
    }

    public ResourceSet getMergedSet() {
        return new ResourceSet(mItems);
    }
}
