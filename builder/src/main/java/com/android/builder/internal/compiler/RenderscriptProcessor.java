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

package com.android.builder.internal.compiler;

import com.android.annotations.NonNull;
import com.android.builder.compiling.DependencyFileProcessor;
import com.android.builder.internal.CommandLineRunner;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A Source File processor for Renderscript files. This compiles each Renderscript file
 * found by the SourceSearcher.
 */
public class RenderscriptProcessor  implements SourceSearcher.SourceFileProcessor {

    @NonNull private final String mCompilerPath;
    @NonNull private final List<File> mImportPaths;
    @NonNull private final String mSourceOutputDir;
    @NonNull private final String mResOutputDir;
    private final int mTargetApi;
    private final boolean mDebugBuild;
    private final int mOptimLevel;
    @NonNull private final DependencyFileProcessor mDependencyFileProcessor;
    @NonNull private final CommandLineRunner mCmdLineRunner;

    public RenderscriptProcessor(@NonNull String compilerPath,
                                 @NonNull List<File> importPaths,
                                 @NonNull String sourceOutputDir,
                                 @NonNull String resOutputDir,
                                 int targetApi,
                                 boolean debugBuild,
                                 int optimLevel,
                                 @NonNull DependencyFileProcessor dependencyFileProcessor,
                                 @NonNull CommandLineRunner cmdLineRunner) {
        mCompilerPath = compilerPath;
        mImportPaths = importPaths;
        mSourceOutputDir = sourceOutputDir;
        mResOutputDir = resOutputDir;
        mTargetApi = targetApi < 11 ? 11 : targetApi;
        mDebugBuild = debugBuild;
        mOptimLevel = optimLevel;
        mDependencyFileProcessor = dependencyFileProcessor;
        mCmdLineRunner = cmdLineRunner;
    }

    @Override
    public void processFile(File sourceFile) throws IOException, InterruptedException {
        ArrayList<String> command = Lists.newArrayList();

        command.add(mCompilerPath);

        if (mDebugBuild) {
            command.add("-g");
        }

        command.add("-O");
        command.add(Integer.toString(mOptimLevel));

        for (File importPath : mImportPaths) {
            if (importPath.isDirectory()) {
                command.add("-I");
                command.add(importPath.getAbsolutePath());
            }
        }

        command.add("-p");
        command.add(mSourceOutputDir);

        command.add("-o");
        command.add(mResOutputDir);

        command.add("-target-api");
        command.add(Integer.toString(mTargetApi));

        // dependency file
        File tempFolder = Files.createTempDir();
        command.add("-d");
        command.add(tempFolder.getAbsolutePath());
        command.add("-MD");

        // input file
        command.add(sourceFile.getAbsolutePath());

        mCmdLineRunner.runCmdLine(command);

        // send the dependency file to the processor.
        // since it's a temp folder, there should be only one file in it, so use that.
        File[] files = tempFolder.listFiles();
        if (files != null && files.length > 0) {
            File depFile = files[0];
            if (mDependencyFileProcessor.processFile(depFile)) {
                depFile.delete();
            }
        }
    }
}
