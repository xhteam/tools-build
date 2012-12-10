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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Formatter;

/**
 * A {@link File} and its associated data needed to figure out if a file changed or not.
 */
class FileEntity {

    private static final byte[] sBuffer = new byte[4096];

    private final File file;
    private final long lastModified;
    private long length;
    private String sha1;

    /**
     * Exception to indicate a failure to check a jar file's content.
     */
    private static final class Sha1Exception extends Exception {
        private static final long serialVersionUID = 1L;
        private final File file;

        public Sha1Exception(File jarFile, Throwable cause) {
            super(cause);
            file = jarFile;
        }

        public File getJarFile() {
            return file;
        }
    }

    /**
     * Creates an entity from cached data.
     *
     * @param file the file
     * @param lastModified when it was last modified
     * @param length its length
     * @param sha1 its sha1
     */
    FileEntity(File file, long lastModified, long length, String sha1) {
        this.file = file;
        this.lastModified = lastModified;
        this.length = length;
        this.sha1 = sha1;
    }

    /**
     * Creates an entity from a {@link File}.
     *
     * The sha1 is not computed yet, it'll be done on demand when {@link #getSha1()} is called.
     *
     * @param file the file.
     */
    FileEntity(File file) {
        this.file = file;
        lastModified = file.lastModified();
        length = file.length();
    }

    /**
     * Returns the file's last modified info.
     * @return the file's last modified info.
     */
    long getLastModified() {
        return lastModified;
    }

    /**
     * Return the file length.
     * @return the file length.
     */
    long getLength() {
        return length;
    }

    /**
     * Returns the file this entity represents.
     * @return the file.
     */
    File getFile() {
        return file;
    }

    /**
     * Returns the file's sha1, computing it if necessary.
     *
     * @return the sha1 or null if it couldn't be computed.
     */
    String getSha1() {
        try {
            return computeAndReturnSha1();
        } catch (Sha1Exception e) {
            return null;
        }
    }

    /**
     * Checks whether the {@link File#lastModified()} matches the cached value. If not, length
     * is updated and the sha1 is reset (but not recomputed, this is done on demand).
     *
     * @return return whether the file was changed.
     */
    private boolean checkValidity() {
        if (lastModified != file.lastModified()) {
            length = file.length();
            sha1 = null;
            return true;
        }

        return false;
    }

    /**
     * Returns whether the two entity are different files.
     *
     * This will compute the files' sha1 if they are not yet computed.
     *
     * @param fileEntity the file to compare to.
     * @return true if the files are the same, false otherwise.
     */
    public boolean isDifferentThan(FileEntity fileEntity) {
        assert fileEntity.file.equals(file);

        // same date, same files.
        if (lastModified == fileEntity.lastModified) {
            return false;
        }

        try {
            // different date doesn't necessarily mean different file.
            // start with size, less computing intensive than sha1.
            return length != fileEntity.length ||
                    !computeAndReturnSha1().equals(fileEntity.computeAndReturnSha1());
        } catch (Sha1Exception e) {
            // if we can't compute the sha1, we consider the files different.
            return true;
        }
    }

    /**
     * Returns the file's sha1, computing it if necessary.
     *
     * @return the sha1
     * @throws Sha1Exception
     */
    private String computeAndReturnSha1() throws Sha1Exception {
        if (sha1 == null) {
            sha1 = getSha1(file);
        }
        return sha1;
    }

    /**
     * Computes the sha1 of a file and returns it.
     *
     * @param f the file to compute the sha1 for.
     * @return the sha1 value
     * @throws Sha1Exception if the sha1 value cannot be computed.
     */
    static String getSha1(File f) throws Sha1Exception {
        synchronized (sBuffer) {
            FileInputStream fis = null;
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-1");

                fis = new FileInputStream(f);
                while (true) {
                    int length = fis.read(sBuffer);
                    if (length > 0) {
                        md.update(sBuffer, 0, length);
                    } else {
                        break;
                    }
                }

                return byteArray2Hex(md.digest());

            } catch (Exception e) {
                throw new Sha1Exception(f, e);
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
    }

    /**
     * Converts a byte array to an Hex string.
     * @param hash the byte array to convert,
     * @return the converted string.
     */
    private static String byteArray2Hex(final byte[] hash) {
        Formatter formatter = new Formatter();
        try {
            for (byte b : hash) {
                formatter.format("%02x", b);
            }
            return formatter.toString();
        } finally {
            formatter.close();
        }
    }
}
