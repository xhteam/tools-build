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

package com.android.builder.signing;

import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.google.common.base.Objects;

import java.io.File;
import java.security.KeyStore;

/**
 * SigningConfig encapsulates the information necessary to access certificates in a keystore file
 * that can be used to sign APKs.
 */
public class SigningConfig {

    public static final String DFAULT_PASSWORD = "android";
    public static final String DEFAULT_ALIAS = "AndroidDebugKey";

    private File mStoreFile = null;
    private String mStorePassword = null;
    private String mKeyAlias = null;
    private String mKeyPassword = null;
    private String mStoreType = KeyStore.getDefaultType();

    /**
     * Creates a SigningConfig.
     */
    public SigningConfig() {
    }

    /**
     * Initializes the SigningConfig with the debug keystore/key alias data.
     *
     * @throws AndroidLocationException if the debug keystore location cannot be found
     */
    public void initDebug() throws AndroidLocationException {
        mStoreFile = new File(KeystoreHelper.defaultDebugKeystoreLocation());
        mStorePassword = DFAULT_PASSWORD;
        mKeyAlias = DEFAULT_ALIAS;
        mKeyPassword = DFAULT_PASSWORD;
    }

    public File getStoreFile() {
        return mStoreFile;
    }

    public SigningConfig setStoreFile(File storeFile) {
        mStoreFile = storeFile;
        return this;
    }

    public String getStorePassword() {
        return mStorePassword;
    }

    public SigningConfig setStorePassword(String storePassword) {
        mStorePassword = storePassword;
        return this;
    }

    public String getKeyAlias() {
        return mKeyAlias;
    }

    public SigningConfig setKeyAlias(String keyAlias) {
        mKeyAlias = keyAlias;
        return this;
    }

    public String getKeyPassword() {
        return mKeyPassword;
    }

    public SigningConfig setKeyPassword(String keyPassword) {
        mKeyPassword = keyPassword;
        return this;
    }

    public String getStoreType() {
        return mStoreType;
    }

    public void SetStoreType(String storeType) {
        mStoreType = storeType;
    }

    public boolean isSigningReady() {
        return mStoreFile != null &&
                mStorePassword != null &&
                mKeyAlias != null &&
                mKeyPassword != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        SigningConfig that = (SigningConfig) o;

        if (mKeyAlias != null ?
                !mKeyAlias.equals(that.mKeyAlias) :
                that.mKeyAlias != null)
            return false;
        if (mKeyPassword != null ?
                !mKeyPassword.equals(that.mKeyPassword) :
                that.mKeyPassword != null)
            return false;
        if (mStoreFile != null ?
                !mStoreFile.equals(that.mStoreFile) :
                that.mStoreFile != null)
            return false;
        if (mStorePassword != null ?
                !mStorePassword.equals(that.mStorePassword) :
                that.mStorePassword != null)
            return false;
        if (mStoreType != null ?
                !mStoreType.equals(that.mStoreType) :
                that.mStoreType != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (mStoreFile != null ?
                mStoreFile.hashCode() : 0);
        result = 31 * result + (mStorePassword != null ?
                mStorePassword.hashCode() : 0);
        result = 31 * result + (mKeyAlias != null ? mKeyAlias.hashCode() : 0);
        result = 31 * result + (mKeyPassword != null ? mKeyPassword.hashCode() : 0);
        result = 31 * result + (mStoreType != null ? mStoreType.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("storeFile", mStoreFile.getAbsolutePath())
                .add("storePassword", mStorePassword)
                .add("keyAlias", mKeyAlias)
                .add("keyPassword", mKeyPassword)
                .add("storeType", mStoreType)
                .toString();
    }
}
