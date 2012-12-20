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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Maps;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents a file in a resource folders.
 *
 * It contains a link to the {@link File}, the qualifier string (which is the name of the folder
 * after the first '-' character), a list of {@link Resource} and a type.
 *
 * The type of the file is based on whether the file is located in a values folder (FileType.MULTI)
 * or in another folder (FileType.SINGLE).
 */
class ResourceFile {

    static enum FileType {
        SINGLE, MULTI
    }

    private final FileType mType;
    private final File mFile;
    private final Map<String, Resource> mItems;
    private final String mQualifiers;

    /**
     * Creates a resource file with a single resource item.
     *
     * The source file is set on the item with {@link Resource#setSource(ResourceFile)}
     *
     * The type of the ResourceFile will by {@link FileType#SINGLE}.
     *
     * @param file the File
     * @param item the resource item
     * @param qualifiers the qualifiers.
     */
    ResourceFile(@NonNull File file, @NonNull Resource item, @Nullable String qualifiers) {
        mType = FileType.SINGLE;
        mFile = file;
        mQualifiers = qualifiers;

        item.setSource(this);
        mItems = Collections.singletonMap(item.getKey(), item);
    }

    /**
     * Creates a resource file with a list of resource items.
     *
     * The source file is set on the items with {@link Resource#setSource(ResourceFile)}
     *
     * The type of the ResourceFile will by {@link FileType#MULTI}.
     *
     * @param file the File
     * @param items the resource items
     * @param qualifiers the qualifiers.
     */
    ResourceFile(@NonNull File file, @NonNull List<Resource> items, @Nullable String qualifiers) {
        mType = FileType.MULTI;
        mFile = file;
        mQualifiers = qualifiers;

        mItems = Maps.newHashMapWithExpectedSize(items.size());
        for (Resource item : items) {
            item.setSource(this);
            mItems.put(item.getKey(), item);
        }
    }

    @NonNull
    FileType getType() {
        return mType;
    }

    @NonNull
    File getFile() {
        return mFile;
    }

    @Nullable
    String getQualifiers() {
        return mQualifiers;
    }

    Resource getItem() {
        assert mItems.size() == 1;
        return mItems.values().iterator().next();
    }

    @NonNull
    Collection<Resource> getItems() {
        return mItems.values();
    }

    @NonNull
    Map<String, Resource> getItemMap() {
        return mItems;
    }

    void addItems(Collection<Resource> items) {
        for (Resource item : items) {
            mItems.put(item.getKey(), item);
            item.setSource(this);
        }
    }
}
