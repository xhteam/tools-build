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
import com.google.common.collect.Lists;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 */
class ResourceFile {

    private final File mFile;
    private final List<Resource> mItems;
    private final String mQualifiers;

    ResourceFile(File file, Resource item, String qualifiers) {
        mFile = file;
        mItems = Collections.singletonList(item);
        mQualifiers = qualifiers;

        item.setSource(this);
    }

    ResourceFile(@NonNull File file, @NonNull List<Resource> items, @Nullable String qualifiers) {
        mFile = file;
        mItems = Lists.newArrayList(items);
        mQualifiers = qualifiers;

        for (Resource item : items) {
            item.setSource(this);
        }
    }

    @NonNull File getFile() {
        return mFile;
    }

    @Nullable String getQualifiers() {
        return mQualifiers;
    }
}
