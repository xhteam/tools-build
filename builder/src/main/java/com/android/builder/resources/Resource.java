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
import com.android.resources.ResourceType;
import org.w3c.dom.Node;

/**
 * A resource.
 *
 * This includes the name, type, source file as a {@link ResourceFile} and an optional {@link Node}
 * in case of a resource coming from a value file.
 *
 */
class Resource {

    private static final int MASK_TOUCHED = 0x01;
    private static final int MASK_REMOVED = 0x02;
    private static final int MASK_WRITTEN = 0x10;

    private final String mName;
    private final ResourceType mType;

    private Node mValue;
    private ResourceFile mSource;

    /**
     * The status of the Resource. It's a bit mask as opposed to an enum
     * to differentiate removed and removed+written
     */
    private int mStatus = 0;

    /**
     * Constructs the object with a name, type and optional value.
     *
     * Note that the object is not fully usable as-is. It must be added to a ResourceFile first.
     *
     * @param name the name of the resource
     * @param type the type of the resource
     * @param value an optional Node that represents the resource value.
     */
    Resource(@NonNull String name, @NonNull ResourceType type, Node value) {
        mName = name;
        mType = type;
        mValue = value;
    }

    /**
     * Returns the name of the resource.
     * @return the name.
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Returns the type of the resource.
     * @return the type.
     */
    @NonNull
    public ResourceType getType() {
        return mType;
    }

    /**
     * Returns the ResourceFile the resource is coming from. Can be null.
     * @return the resource file.
     */
    public ResourceFile getSource() {
        return mSource;
    }

    /**
     * Returns the optional value of the resource. Can be null
     * @return the value or null.
     */
    public Node getValue() {
        return mValue;
    }

    /**
     * Sets the value of the resource and set its state to TOUCHED.
     * @param from the resource to copy the value from.
     */
    void setValue(Resource from) {
        mValue = from.mValue;
        setTouched();
    }


    /**
     * Sets the ResourceFile
     * @param sourceFile the ResourceFile
     */
    void setSource(ResourceFile sourceFile) {
        mSource = sourceFile;
    }

    /**
     * Resets the state of the resource be WRITTEN. All other states are removed.
     * @return this
     *
     * @see #isWritten()
     */
    Resource resetStatusToWritten() {
        mStatus = MASK_WRITTEN;
        return this;
    }

    /**
     * Sets the resource be WRITTEN. Other states are kept.
     * @return this
     *
     * @see #isWritten()
     */
    Resource setWritten() {
        mStatus |= MASK_WRITTEN;
        return this;
    }

    /**
     * Sets the resource be REMOVED. Other states are kept.
     * @return this
     *
     * @see #isRemoved()
     */
    Resource setRemoved() {
        mStatus |= MASK_REMOVED;
        return this;
    }

    /**
     * Sets the resource be TOUCHED. Other states are kept.
     * @return this
     *
     * @see #isTouched()
     */
    Resource setTouched() {
        mStatus |= MASK_TOUCHED;
        return this;
    }

    /**
     * Returns whether the resource is REMOVED
     * @return true if removed
     */
    boolean isRemoved() {
        return (mStatus & MASK_REMOVED) != 0;
    }

    /**
     * Returns whether the resource is TOUCHED
     * @return true if touched
     */
    boolean isTouched() {
        return (mStatus & MASK_TOUCHED) != 0;
    }

    /**
     * Returns whether the resource is WRITTEN
     * @return true if written
     */
    boolean isWritten() {
        return (mStatus & MASK_WRITTEN) != 0;
    }

    /**
     * Returns a key for this resource. They key uniquely identifies this resource by combining
     * resource type, qualifiers, and name.
     *
     * If the resource has not been added to a {@link ResourceFile}, this will throw an
     * {@link IllegalStateException}.
     *
     * @return the key for this resource.
     *
     * @throws IllegalStateException if the resource is not added to a ResourceFile
     */
    String getKey() {
        if (mSource == null) {
            throw new IllegalStateException(
                    "Resource.getKey called on object with no ResourceFile: " + this);
        }
        String qualifiers = mSource.getQualifiers();
        if (qualifiers != null && qualifiers.length() > 0) {
            return mType.getName() + "-" + qualifiers + "/" + mName;
        }

        return mType.getName() + "/" + mName;
    }

    /**
     * Compares the Resource {@link #getValue()} together and returns true if they are the same.
     * @param resource The Resource object to compare to.
     * @return true if equal
     */
    public boolean compareValueWith(Resource resource) {
        if (mValue != null && resource.mValue != null) {
            return NodeUtils.compareElementNode(mValue, resource.mValue);
        }

        return mValue == resource.mValue;
    }

    @Override
    public String toString() {
        return "Resource{" +
                "mName='" + mName + '\'' +
                ", mType=" + mType +
                ", mStatus=" + mStatus +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Resource resource = (Resource) o;

        return mName.equals(resource.mName) && mType == resource.mType;
    }

    @Override
    public int hashCode() {
        int result = mName.hashCode();
        result = 31 * result + mType.hashCode();
        return result;
    }
}
