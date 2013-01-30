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

package com.android.build.gradle.tasks
import com.android.build.gradle.internal.tasks.DependencyBasedCompileTask
import com.android.builder.compiling.DependencyFileProcessor
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
/**
 * Task to compile Renderscript files. Supports incremental update.
 */
public class RenderscriptCompile extends DependencyBasedCompileTask {

    // ----- PUBLIC TASK API -----

    @OutputDirectory
    File resOutputDir

    // ----- PRIVATE TASK API -----

    @InputFiles
    List<File> sourceDirs

    @InputFiles
    List<File> importDirs

    @Input
    int optimLevel

    @Input
    int targetApi

    @Input
    boolean debugBuild

    @Override
    protected boolean isIncremental() {
        return true
    }

    @Override
    protected boolean supportsParallelization() {
        return false
    }

    @Override
    protected Collection<File> getOutputForIncrementalBuild() {
        return Collections.singletonList(getSourceOutputDir())
    }

    @Override
    protected void compileAllFiles(DependencyFileProcessor dependencyFileProcessor) {

        List<File> importFolders = getBuilder().getLeafFolders("rsh",
                getImportDirs(), getSourceDirs())

        getBuilder().compileAllRenderscriptFiles(
                getSourceDirs(),
                importFolders,
                getSourceOutputDir(),
                getResOutputDir(),
                getTargetApi(),
                getDebugBuild(),
                getOptimLevel(),
                dependencyFileProcessor)
    }

    @Override
    protected Object incrementalSetup() {
        return getBuilder().getLeafFolders("rsh", getImportDirs(), getSourceDirs())
    }

    @Override
    protected void compileSingleFile(File file,Object data,
                                     DependencyFileProcessor dependencyFileProcessor) {
        getBuilder().compileRenderscriptFile(
                file,
                (List<File>) data,
                getSourceOutputDir(),
                getResOutputDir(),
                getTargetApi(),
                getDebugBuild(),
                getOptimLevel(),
                dependencyFileProcessor)
    }
}
