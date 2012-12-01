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

import com.android.resources.ResourceType;
import org.w3c.dom.Node;

/**
 * A resource.
 *
 * This includes the name, type, source file as a {@link ResourceFile} and an optional {@link Node}
 * in case of a resource coming from a value file.
 *
 */
public class Resource {

    private final String mName;
    private final ResourceType mType;

    private final Node mValue;
    private ResourceFile mSource;

    Resource(String name, ResourceType type, Node value) {
        mName = name;
        mType = type;
        mValue = value;
    }

    public String getName() {
        return mName;
    }

    public ResourceType getType() {
        return mType;
    }

    public ResourceFile getSource() {
        return mSource;
    }

    public Node getValue() {
        return mValue;
    }

    void setSource(ResourceFile sourceFile) {
        mSource = sourceFile;
    }

    /**
     * Returns a key for this resource. They key uniquely identifies this resource by combining
     * resource type, qualifiers, and name.
     * @return the key for this resource.
     */
    String getKey() {
        String qualifiers = mSource != null ? mSource.getQualifiers() : null;
        if (qualifiers != null) {
            return mType.getName() + "-" + qualifiers + "/" + mName;
        }

        return mType.getName() + "/" + mName;
    }

    @Override
    public String toString() {
        return "Resource{" +
                "mName='" + mName + '\'' +
                ", mType=" + mType +
                '}';
    }
}
