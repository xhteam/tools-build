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

import com.android.build.gradle.tasks.MergeResources
import com.google.common.collect.Lists
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction

class MergeResourcesTask extends MergeResources {

    // fake input to detect changes. Not actually used by the task
    @InputFiles
    Iterable<File> rawInputFolders

    // actual inputs
    List<List<File>> inputResourceSets

    @TaskAction
    void generate() {
        // this is not yet incremental. Need to clean up the previous merge result.
        File destinationDir = getOutputDir()

        deleteFolder(destinationDir)
        destinationDir.mkdir()

        getBuilder().mergeResources(destinationDir.absolutePath, getInputResourceSets())
    }

    static Iterable<File> inlineInputs(List<List<File>> inputs) {
        List<File> list = Lists.newArrayList();

        for (List<File> folders : inputs) {
            for (File folder : folders) {
                list.add(folder);
            }
        }

        return list;
    }

    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles()
        if (files != null && files.length > 0) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file)
                }
                file.delete();
            }
        }

        folder.delete()
    }
}
