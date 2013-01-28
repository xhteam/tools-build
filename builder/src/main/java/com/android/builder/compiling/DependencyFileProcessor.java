/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.builder.compiling;

import java.io.File;

/**
 * A Class that processes a dependency file.
 */
public interface DependencyFileProcessor {

    /**
     * Processes the dependency file.
     * @param dependencyFile the dependency file.
     * @return true if the dependency file can be deleted by the caller.
     */
    boolean processFile(File dependencyFile);
}
