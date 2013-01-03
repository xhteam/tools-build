/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.builder.internal.signing;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.ILogger;
import junit.framework.TestCase;

import java.io.File;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Calendar;

public class DebugKeyProviderTest extends TestCase {

    private File mTmpFile;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // We want to allocate a new tmp file but not have it actually exist
        mTmpFile = File.createTempFile(this.getClass().getSimpleName(), ".keystore");
        assertTrue(mTmpFile.delete());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mTmpFile != null) {
            if (!mTmpFile.delete()) {
                mTmpFile.deleteOnExit();
            }
            mTmpFile = null;
        }
    }

    public void testCreateAndCheckKey() throws Exception {
        String osPath = mTmpFile.getAbsolutePath();

        FakeLogger fakeLogger = new FakeLogger();

        // "now" is just slightly before the key was created
        long now = System.currentTimeMillis();

        DebugKeyProvider provider;
        try {
            provider = new DebugKeyProvider(osPath,  null /*storeType*/, fakeLogger);
        } catch (Throwable t) {
            // In case we get any kind of exception, rewrap it to make sure we output
            // the path used.
            String msg = String.format("%1$s in %2$s\n%3$s",
                    t.getClass().getSimpleName(), osPath, t.toString());
            throw new Exception(msg, t);
        }
        assertNotNull(provider);

        assertEquals("", fakeLogger.getErr());

        PrivateKey key = provider.getDebugKey();
        assertNotNull(key);

        X509Certificate certificate = (X509Certificate) provider.getCertificate();
        assertNotNull(certificate);

        // The "not-after" (a.k.a. expiration) date should be after "now"
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        assertTrue(certificate.getNotAfter().compareTo(c.getTime()) > 0);

        // It should be valid after 1 year from now (adjust by a second since the 'now' time
        // doesn't exactly match the creation time... 1 second should be enough.)
        c.add(Calendar.DAY_OF_YEAR, 365);
        c.add(Calendar.SECOND, -1);
        assertTrue("1 year expiration failed",
                certificate.getNotAfter().compareTo(c.getTime()) > 0);

        // and 30 years from now
        c.add(Calendar.DAY_OF_YEAR, 29 * 365);
        assertTrue("30 year expiration failed",
                certificate.getNotAfter().compareTo(c.getTime()) > 0);

        // however expiration date should be passed in 30 years + 1 hour
        c.add(Calendar.HOUR, 1);
        assertFalse("30 year and 1 hour expiration failed",
                certificate.getNotAfter().compareTo(c.getTime()) > 0);
    }

    private static class FakeLogger implements ILogger {
        private String mOut = "";
        private String mErr = "";

        public String getOut() {
            return mOut;
        }

        public String getErr() {
            return mErr;
        }

        @Override
        public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
            String message = msgFormat != null ?
                    String.format(msgFormat, args) :
                    t != null ? t.getClass().getCanonicalName() : "ERROR!";
            mErr += message + "\n";
        }

        @Override
        public void warning(@NonNull String msgFormat, Object... args) {
            String message = String.format(msgFormat, args);
            mOut += message + "\n";
        }

        @Override
        public void info(@NonNull String msgFormat, Object... args) {
            String message = String.format(msgFormat, args);
            mOut += message + "\n";
        }

        @Override
        public void verbose(@NonNull String msgFormat, Object... args) {
            String message = String.format(msgFormat, args);
            mOut += message + "\n";
        }
    }
}
