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
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.builder.resources.AssetMerger
import com.android.builder.resources.AssetSet
import com.android.builder.resources.FileStatus
import com.android.utils.Pair
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory

public class MergeAssets extends IncrementalTask {

    // ----- PUBLIC TASK API -----

    @OutputDirectory
    File outputDir

    // ----- PRIVATE TASK API -----

    // fake input to detect changes. Not actually used by the task
    @InputFiles
    Iterable<File> getRawInputFolders() {
        return IncrementalTask.flattenSourceSets(getInputAssetSets())
    }

    // actual inputs
    List<AssetSet> inputAssetSets

    @Override
    protected boolean isIncremental() {
        return true
    }

    @Override
    protected Collection<File> getOutputForIncrementalBuild() {
        return Collections.singletonList(getOutputDir())
    }

    @Override
    protected void doFullTaskAction() {
        // this is full run, clean the previous output
        File destinationDir = getOutputDir()
        emptyFolder(destinationDir)

        List<AssetSet> assetSets = getInputAssetSets()

        // create a new merger and populate it with the sets.
        AssetMerger merger = new AssetMerger()

        for (AssetSet assetSet : assetSets) {
            // set needs to be loaded.
            assetSet.loadFromFiles()
            merger.addDataSet(assetSet)
        }

        // get the merged set and write it down.
        merger.writeDataFolder(destinationDir)

        // No exception? Write the known state.
        merger.writeBlobTo(getIncrementalFolder())
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) {
        // create a merger and load the known state.
        AssetMerger merger = new AssetMerger()
        if (!merger.loadFromBlob(getIncrementalFolder())) {
            doFullTaskAction()
            return
        }

        // compare the known state to the current sets to detect incompatibility.
        // This is in case there's a change that's too hard to do incrementally. In this case
        // we'll simply revert to full build.
        List<AssetSet> assetSets = getInputAssetSets()

        if (!merger.checkValidUpdate(assetSets)) {
            project.logger.info("Changed Asset sets: full task run!")
            doFullTaskAction()
            return
        }

        // The incremental process is the following:
        // Loop on all the changed files, find which ResourceSet it belongs to, then ask
        // the resource set to update itself with the new file.
        for (Map.Entry<File, FileStatus> entry : changedInputs.entrySet()) {
            File changedFile = entry.getKey()

            Pair<AssetSet, File> matchSet = merger.getDataSetContaining(changedFile)
            assert matchSet != null
            if (matchSet == null) {
                doFullTaskAction()
                return
            }

            // do something?
            if (!matchSet.getFirst().updateWith(
                    matchSet.getSecond(), changedFile, entry.getValue())) {
                project.logger.info(
                        String.format("Failed to process %s event! Full task run",
                                entry.getValue()))
                doFullTaskAction()
                return
            }
        }

        merger.writeDataFolder(getOutputDir())

        // No exception? Write the known state.
        merger.writeBlobTo(getIncrementalFolder())
    }
}
