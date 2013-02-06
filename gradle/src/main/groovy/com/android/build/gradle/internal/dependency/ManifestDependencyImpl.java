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

package com.android.build.gradle.internal.dependency;

import com.android.builder.ManifestDependency;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;

import java.io.File;
import java.util.List;

/**
 * Implementation of ManifestDependency that can be used as a Task input.
 */
public class ManifestDependencyImpl implements ManifestDependency{

    private File manifest;
    private List<? extends ManifestDependency> dependencies;

    public ManifestDependencyImpl(File manifest, List<? extends ManifestDependency> dependencies) {
        this.manifest = manifest;
        this.dependencies = dependencies;
    }

    @InputFile
    @Override
    public File getManifest() {
        return manifest;
    }

    @Override
    public List<? extends ManifestDependency> getManifestDependencies() {
        return dependencies;
    }

    @Nested
    public List<ManifestDependencyImpl> getManifestDependenciesForInput() {
        return (List<ManifestDependencyImpl>) dependencies;
    }
}
