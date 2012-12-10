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

package com.android.builder.internal.incremental;

import com.android.annotations.NonNull;
import com.android.builder.resources.FileStatus;
import com.google.common.collect.Maps;

import java.io.*;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class handling changes in a set of files.
 *
 * The class can store the state of the files, and later reload it and compare it to the
 * previous known state.
 *
 */
class FileManager {

    private static final Pattern READ_PATTERN = Pattern.compile("^(\\d+) (\\d+) ([0-9a-f]+) (.+)$");

    private Map<File, FileEntity> mLoadedFiles = Maps.newHashMap();
    private Map<File, FileEntity> mProcessedFiles = Maps.newHashMap();
    private Map<File, FileStatus> mResults = Maps.newHashMap();

    public FileManager() {
    }

    /**
     * Loads the known state.
     *
     * @param stateFile the file to load the state from.
     * @return false if the loading failed.
     *
     * @see #write(java.io.File)
     */
    public boolean load(File stateFile) {
        if (!stateFile.exists()) {
            return false;
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(stateFile), "UTF-8"));

            String line = null;
            while ((line = reader.readLine()) != null) {
                // skip comments
                if (line.charAt(0) == '#') {
                    continue;
                }

                // get the data with a regexp
                Matcher m = READ_PATTERN.matcher(line);
                if (m.matches()) {
                    String path = m.group(4);
                    File f = new File(path);

                    FileEntity entity = new FileEntity(
                            f,
                            Long.parseLong(m.group(1)),
                            Long.parseLong(m.group(2)),
                            m.group(3));

                    mLoadedFiles.put(f, entity);
                }
            }

            return true;
        } catch (FileNotFoundException ignored) {
            // won't happen, we check up front.
        } catch (UnsupportedEncodingException ignored) {
            // shouldn't happen, but if it does, we just won't have a cache.
        } catch (IOException ignored) {
            // shouldn't happen, but if it does, we just won't have a cache.
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }

        return false;
    }

    /**
     * Writes the state to a file
     * @param stateFile the file to write the state to.
     *
     * @see #load(java.io.File)
     */
    public void write(File stateFile) {
        OutputStreamWriter writer = null;
        try {
            // first make sure the folders exist!
            File parentFolder = stateFile.getParentFile();
            parentFolder.mkdirs();

            // then write the file.
            writer = new OutputStreamWriter(new FileOutputStream(stateFile), "UTF-8");

            writer.write("# incremental data. DO NOT EDIT.\n");
            writer.write("# format is <lastModified> <length> <SHA-1> <path>\n");
            writer.write("# Encoding is UTF-8\n");

            for (FileEntity entity : mProcessedFiles.values()) {
                String sha1 = entity.getSha1();
                if (sha1 == null) {
                    sha1 = "0123456789012345678901234567890123456789"; // TODO: find a better way to detect missing sha1
                }

                writer.write(String.format("%d %d %s %s\n",
                        entity.getLastModified(),
                        entity.getLength(),
                        sha1,
                        entity.getFile().getAbsolutePath()));
            }
        } catch (IOException ignored) {
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

   /**
     * Add an input file or folder.
     * @param file the file.
     */
    public void addFile(File file) {
        processFile(file);
    }

    /**
     * Get the list of changed inputs. Empty list means no input changes.
     *
     * @return a map of (File, FileStatus) for all changed input.
     */
    @NonNull
    public Map<File, FileStatus> getChangedFiles() {
        // at this point, all the files that needed processing have been processed,
        // but there may be removed files remaining in the loaded file map.
        for (File f : mLoadedFiles.keySet()) {
            mResults.put(f, FileStatus.REMOVED);
        }

        return mResults;
    }

    private void processFile(File file) {
        if (file.isFile()) {
            if (file.getName().startsWith(".")) {
                return;
            }

            // get the FileEntity for the new(?) version.
            FileEntity newFileEntity = new FileEntity(file);

            // see if it existed before.
            FileEntity fileEntity = mLoadedFiles.get(file);

            if (fileEntity == null) {
                // new file!
                mResults.put(file, FileStatus.NEW);

                // add it to the list of processed files
                mProcessedFiles.put(file, newFileEntity);
            } else {
                // remove it from the loaded files.
                mLoadedFiles.remove(file);

                if (newFileEntity.isDifferentThan(fileEntity)) {
                    mResults.put(file, FileStatus.CHANGED);

                    // put the newFileEntity in the processed files.
                    mProcessedFiles.put(file, newFileEntity);
                } else {
                    // just move the original entity so avoid recomputing the sha1.
                    // FileEntity.isDifferentThan doesn't necessarily compute it.
                   mProcessedFiles.put(file, fileEntity);
                }
            }
        } else if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null && files.length > 0) {
                for (File f : files) {
                    processFile(f);
                }
            }
         }
    }

    /**
     * Updates the existing files with the given files/folders.
     * @param files the new folders/files to process.
     */
    void update(Collection<File> files) {
        mLoadedFiles.clear();
        mLoadedFiles.putAll(mProcessedFiles);
        mResults.clear();
        mProcessedFiles.clear();
        for (File f : files) {
            processFile(f);
        }
    }
}
