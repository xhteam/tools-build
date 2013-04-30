package com.android.tests.basic.buildscript;

import com.android.annotations.NonNull;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.google.common.collect.Lists;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class FakeDevice extends DeviceConnector {

    private final String name;
    private boolean connectCalled = false;
    private boolean disconnectCalled = false;
    private boolean installCalled = false;
    private boolean uninstallCalled = false;
    private boolean execShellCalled = false;

    private final List<File> installedApks = Lists.newArrayList();


    FakeDevice(String name) {
        this.name = name;
    }

    @Override
    public void connect(int timeOut) throws TimeoutException {
        System.out.println(String.format("CONNECT(%S) CALLED", name));
        connectCalled = true;
    }

    @Override
    public void disconnect(int timeOut) throws TimeoutException {
        System.out.println(String.format("DISCONNECTED(%S) CALLED", name));
        disconnectCalled = true;
    }

    @Override
    public void installPackage(@NonNull File apkFile, int timeout) throws DeviceException {
        System.out.println(String.format("INSTALL(%S) CALLED", name));

        if (apkFile == null) {
            throw new NullPointerException("Null testApk");
        }

        System.out.println(String.format("\t(%s)ApkFile: %s", name, apkFile.getAbsolutePath()));

        if (!apkFile.isFile()) {
            throw new RuntimeException("Missing file: " + apkFile.getAbsolutePath());
        }

        if (!apkFile.getAbsolutePath().endsWith(".apk")) {
            throw new RuntimeException("Wrong extension: " + apkFile.getAbsolutePath());
        }

        if (installedApks.contains(apkFile)) {
            throw new RuntimeException("Already added: " + apkFile.getAbsolutePath());
        }

        installedApks.add(apkFile);

        installCalled = true;
    }

    @Override
    public void uninstallPackage(@NonNull String packageName, int timeout) throws DeviceException {
        System.out.println(String.format("UNINSTALL(%S) CALLED", name));
        uninstallCalled = true;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void executeShellCommand(String command, IShellOutputReceiver receiver,
                                    int maxTimeToOutputResponse)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {
        System.out.println(String.format("EXECSHELL(%S) CALLED", name));
        execShellCalled = true;
    }

    public String isValid() {
        if (!connectCalled) {
            return "connect not called on " + name;
        }

        if (!disconnectCalled) {
            return "disconnect not called on " + name;
        }

        if (!installCalled) {
            return "installPackage not called on " + name;
        }

        if (!uninstallCalled) {
            return "uninstallPackage not called on " + name;
        }

        if (!execShellCalled) {
            return "executeShellCommand not called on " + name;
        }

        return null;
    }
}
