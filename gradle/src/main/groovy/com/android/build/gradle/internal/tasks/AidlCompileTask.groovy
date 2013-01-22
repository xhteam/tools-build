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

package com.android.build.gradle.internal.tasks
import com.android.build.gradle.tasks.AidlCompile
import com.android.builder.compiling.DependencyFileProcessor
import com.android.builder.internal.incremental.DependencyData
import com.android.builder.internal.incremental.DependencyDataStore
import com.android.builder.internal.util.concurrent.WaitableExecutor
import com.android.builder.resources.FileStatus
import com.google.common.collect.Lists
import com.google.common.collect.Multimap
import org.gradle.api.tasks.InputFiles

import java.util.concurrent.Callable

/**
 * Task to compile aidl files. Supports incremental update.
 */
public class AidlCompileTask extends AidlCompile {

    public static class DepFileProcessor implements DependencyFileProcessor {

        List<DependencyData> dependencyDataList = Lists.newArrayList()

        List<DependencyData> getDependencyDataList() {
            return dependencyDataList
        }

        @Override
        boolean processFile(File dependencyFile) {
            DependencyData data = DependencyData.parseDependencyFile(dependencyFile)
            if (data != null) {
                dependencyDataList.add(data)
            }

            return true;
        }
    }

    @InputFiles
    List<File> sourceDirs

    @InputFiles
    List<File> importDirs

    @Override
    protected boolean isIncremental() {
        return true
    }

    @Override
    protected Collection<File> getOutputForIncrementalBuild() {
        return Collections.singletonList(getSourceOutputDir())
    }

    @Override
    protected void doFullTaskAction() {
        // this is full run, clean the previous output
        File destinationDir = getSourceOutputDir()
        emptyFolder(destinationDir)

        DepFileProcessor processor = new DepFileProcessor()

        getBuilder().compileAllAidlFiles(getSourceDirs(), destinationDir, getImportDirs(),
                processor)

        List<DependencyData> dataList = processor.getDependencyDataList()

        DependencyDataStore store = new DependencyDataStore()
        store.addData(dataList)

        File storeFile = new File(getIncrementalFolder(), "dependency.store")
        store.saveTo(storeFile)
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) {

        File incrementalData = new File(getIncrementalFolder(), "dependency.store")
        DependencyDataStore store = new DependencyDataStore()
        Multimap<String, DependencyData> inputMap
        try {
            inputMap = store.loadFrom(incrementalData)
        } catch (Exception e) {
            project.logger.info(
                    "Failed to read dependency store for aidl compilation: full task run!")
            doFullTaskAction()
            return
        }

        final File destinationDir = getSourceOutputDir()
        final List<File> fullImportDir = Lists.newArrayList()
        fullImportDir.addAll(getImportDirs())
        fullImportDir.addAll(getSourceDirs())

        final DepFileProcessor processor = new DepFileProcessor()

        // use an executor to parallelize the compilation of multiple files.
        WaitableExecutor executor = new WaitableExecutor()

        Map<String,DependencyData> mainFileMap = store.getMainFileMap()

        for (Map.Entry<File, FileStatus> entry : changedInputs.entrySet()) {
            FileStatus status = entry.getValue()

            switch (status) {
                case FileStatus.NEW:
                    executor.execute(new Callable() {
                        @Override
                        Object call() throws Exception {
                            getBuilder().compileAidlFile(entry.getKey(), destinationDir,
                                    fullImportDir, processor)
                        }
                    })
                    break;
                case FileStatus.CHANGED:
                    List<DependencyData> impactedData = inputMap.get(entry.getKey().absolutePath)
                    if (impactedData != null) {
                        for (final DependencyData data : impactedData) {
                            executor.execute(new Callable() {
                                @Override
                                Object call() throws Exception {
                                    getBuilder().compileAidlFile(new File(data.getMainFile()),
                                            destinationDir, fullImportDir, processor)
                                }
                            })
                        }
                    }
                    break;
                case FileStatus.REMOVED:
                    final DependencyData data = mainFileMap.get(entry.getKey().absolutePath)
                    if (data != null) {
                        executor.execute(new Callable() {
                            @Override
                            Object call() throws Exception {
                                cleanUpOutputFrom(data)
                            }
                        })
                        store.remove(data)
                    }
                    break;
            }
        }

        executor.waitForTasks()

        // get all the update data for the recompiled objects
        store.updateAll(processor.getDependencyDataList())

        store.saveTo(incrementalData);
    }

    private static void cleanUpOutputFrom(DependencyData dependencyData) {
        List<String> outputs = dependencyData.getOutputFiles();

        for (String output : outputs) {
            new File(output).delete()
        }
    }
}
