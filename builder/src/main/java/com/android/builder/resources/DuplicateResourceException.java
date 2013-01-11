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

/**
 * Exception when a resource is declared more than once in a {@link ResourceSet}
 */
public class DuplicateResourceException extends Exception {

    private Resource mOne;
    private Resource mTwo;

    DuplicateResourceException(Resource one, Resource two) {
        super(String.format("Duplicate resources: %1s:%2s, %3s:%4s",
                one.getSource().getFile().getAbsolutePath(), one.getKey(),
                two.getSource().getFile().getAbsolutePath(), two.getKey()));
        mOne = one;
        mTwo = two;
    }

    public Resource getOne() {
        return mOne;
    }

    public Resource getTwo() {
        return mTwo;
    }
}
