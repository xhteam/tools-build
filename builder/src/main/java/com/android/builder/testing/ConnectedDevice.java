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

package com.android.builder.testing;

import com.android.annotations.NonNull;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;

import java.io.File;
import java.io.IOException;

/**
 * Local device connected to with ddmlib. This is a wrapper around {@link IDevice}.
 */
public class ConnectedDevice extends DeviceConnector {

    private final IDevice iDevice;

    public ConnectedDevice(@NonNull IDevice iDevice) {
        this.iDevice = iDevice;
    }

    @NonNull
    @Override
    public String getName() {
        String version = iDevice.getProperty(IDevice.PROP_BUILD_VERSION);
        boolean emulator = iDevice.isEmulator();

        String name;
        if (emulator) {
            name = iDevice.getAvdName() != null ?
                    iDevice.getAvdName() + "(AVD)" :
                    iDevice.getSerialNumber();
        } else {
            String model = iDevice.getProperty(IDevice.PROP_DEVICE_MODEL);
            name = model != null ? model : iDevice.getSerialNumber();
        }

        return version != null ? name + " - " + version : name;
    }

    @Override
    public void connect(int timeout) throws TimeoutException {
        // nothing to do here
    }

    @Override
    public void disconnect(int timeout) throws TimeoutException {
        // nothing to do here
    }

    @Override
    public void installPackage(@NonNull File apkFile) throws DeviceException {
        try {
            iDevice.installPackage(apkFile.getAbsolutePath(), true /*reinstall*/);
        } catch (Exception e) {
            throw new DeviceException(e);
        }
    }

    @Override
    public void uninstallPackage(@NonNull String packageName) throws DeviceException {
        try {
            iDevice.uninstallPackage(packageName);
        } catch (Exception e) {
            throw new DeviceException(e);
        }
    }

    @Override
    public void executeShellCommand(String command, IShellOutputReceiver receiver,
                                    int maxTimeToOutputResponse)
                                    throws TimeoutException, AdbCommandRejectedException,
                                    ShellCommandUnresponsiveException, IOException {
        iDevice.executeShellCommand(command, receiver, maxTimeToOutputResponse);
    }
}
