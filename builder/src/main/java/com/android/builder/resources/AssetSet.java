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

package com.android.builder.resources;

import org.w3c.dom.Attr;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;

/**
 * Represents a set of Assets.
 */
public class AssetSet extends DataSet<AssetItem, AssetFile> {

    /**
     * Creates an asset set with a given configName. The name is used to identify the set
     * across sessions.
     *
     * @param configName the name of the config this set is associated with.
     */
    public AssetSet(String configName) {
        super(configName);
    }

    @Override
    protected DataSet<AssetItem, AssetFile> createSet(String name) {
        return new AssetSet(name);
    }

    @Override
    protected AssetFile createFileAndItems(File file) {
        // key is going to be the full filename, since you can have both
        //     icon.png
        // and
        //     icon.txt
        // in the asset folder.

        return new AssetFile(file, new AssetItem(file.getName()));
    }

    @Override
    protected AssetFile createFileAndItems(File file, Node fileNode) {
        Attr nameAttr = (Attr) fileNode.getAttributes().getNamedItem(ATTR_NAME);
        if (nameAttr == null) {
            return null;
        }

        AssetItem item = new AssetItem(nameAttr.getValue());
        return new AssetFile(file, item);
    }

    @Override
    protected boolean isValidSourceFile(File sourceFolder, File file) {
        // valid files are right under the source folder
        return file.getParentFile().equals(sourceFolder);
    }

    @Override
    protected void readSourceFolder(File sourceFolder) throws DuplicateDataException, IOException {
        // get the files
        File[] files = sourceFolder.listFiles();
        if (files != null && files.length > 0) {
            for (File file : files) {
                if (!file.isFile() || !checkFileForAndroidRes(file)) {
                    continue;
                }

                handleNewFile(sourceFolder, file);
            }
        }
    }
}
