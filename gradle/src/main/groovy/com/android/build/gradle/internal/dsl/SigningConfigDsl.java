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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.builder.BuilderConstants;
import com.android.builder.SigningConfig;
import com.android.prefs.AndroidLocation;
import com.google.common.base.Objects;
import org.gradle.tooling.BuildException;

import java.io.Serializable;

/**
 * DSL overlay for {@link SigningConfig}.

 */
public class SigningConfigDsl extends SigningConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;

    /**
     * Creates a SigningConfig with a given name.
     *
     * @param name the name of the signingConfig.
     *
     */
    public SigningConfigDsl(@NonNull String name) {
        super();
        this.name = name;
        if (BuilderConstants.DEBUG.equals(name)) {
            try {
                initDebug();
            } catch (AndroidLocation.AndroidLocationException e) {
                throw new BuildException("Failed to get default debug keystore location", e);
            }
        }
    }

    @NonNull
    public String getName() {
        return name;
    }

    public SigningConfigDsl initWith(SigningConfig that) {
        setStoreLocation(that.getStoreLocation());
        setStorePassword(that.getStorePassword());
        setKeyAlias(that.getKeyAlias());
        setKeyPassword(that.getKeyPassword());
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        SigningConfigDsl that = (SigningConfigDsl) o;

        if (!name.equals(that.name)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + name.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", name)
                .add("storeLocation", getStoreLocation())
                .add("storePassword", getStorePassword())
                .add("keyAlias", getKeyAlias())
                .add("keyPassword", getKeyPassword())
                .toString();
    }
}
