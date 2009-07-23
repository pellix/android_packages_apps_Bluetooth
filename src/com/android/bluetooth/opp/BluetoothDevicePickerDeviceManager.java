/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.opp;

import com.android.bluetooth.R;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothClass;
import com.android.bluetooth.opp.BluetoothDevicePickerManager.Callback;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * BluetoothDevicePickerDeviceManager manages the set of remote Bluetooth
 * devices.
 */
public class BluetoothDevicePickerDeviceManager {
    private static final String TAG = "BluetoothDevicePickerDeviceManager";

    final BluetoothDevicePickerManager mLocalManager;

    final List<Callback> mCallbacks;

    final List<BluetoothDevicePickerDevice> mDevices = new ArrayList<BluetoothDevicePickerDevice>();

    public BluetoothDevicePickerDeviceManager(BluetoothDevicePickerManager localManager) {
        mLocalManager = localManager;
        mCallbacks = localManager.getCallbacks();
        Log.e(TAG, "BluetoothDevicePickerDeviceManager");
        readPairedDevices();
    }

    private synchronized void readPairedDevices() {
        Log.e(TAG, "readPairedDevices");
        BluetoothDevice manager = mLocalManager.getBluetoothManager();
        String[] bondedAddresses = manager.listBonds();

        if (bondedAddresses == null) {
            Log.e(TAG, "there is no bonded device");
            return;
        }

        for (String address : bondedAddresses) {
            /* only show OPP capable devices: phone and computer */
            if (isOppCapableDevice(address) == false) {
                continue;
            }

            BluetoothDevicePickerDevice device = findDevice(address);
            if (device == null) {
                device = new BluetoothDevicePickerDevice(mLocalManager.getContext(), address);
                mDevices.add(device);
                dispatchDeviceAdded(device);
            }
        }
    }

    public synchronized List<BluetoothDevicePickerDevice> getDevicesCopy() {
        return new ArrayList<BluetoothDevicePickerDevice>(mDevices);
    }

    void onBluetoothStateChanged(boolean enabled) {
        if (enabled) {
            readPairedDevices();
        }
    }

    public synchronized void onDeviceAppeared(String address, short rssi) {
        Log.e(TAG, "onDeviceAppeared");

        /* only show OPP capable devices: phone and computer */
        if (isOppCapableDevice(address) == false) {
            return;
        }

        boolean deviceAdded = false;
        BluetoothDevicePickerDevice device = findDevice(address);
        if (device == null) {
            Log.e(TAG, "device is not found, add a new device");
            device = new BluetoothDevicePickerDevice(mLocalManager.getContext(), address);
            mDevices.add(device);
            deviceAdded = true;
        }

        device.setRssi(rssi);
        device.setVisible(true);

        if (deviceAdded) {
            Log.e(TAG, "call dispatchDeviceAdded");
            dispatchDeviceAdded(device);
        }
    }

    private boolean isOppCapableDevice(String address) {
        Log.e(TAG, "isOppCapableDevice");

        BluetoothDevice manager = mLocalManager.getBluetoothManager();
        int btClass = manager.getRemoteClass(address);
        int major = BluetoothClass.Device.Major.getDeviceMajor(btClass);
        if ((major != BluetoothClass.Device.Major.COMPUTER)
                && (major != BluetoothClass.Device.Major.PHONE)) {
            Log.e(TAG, "this is NOT an OPP capable device");
            return false;
        } else {
            Log.e(TAG, "this is an OPP capable device");
            return true;
        }
    }

    public synchronized void onDeviceDisappeared(String address) {
        Log.e(TAG, "onDeviceDisappeared");
        BluetoothDevicePickerDevice device = findDevice(address);
        if (device == null)
            return;

        device.setVisible(false);
        checkForDeviceRemoval(device);
    }

    private void checkForDeviceRemoval(BluetoothDevicePickerDevice device) {
        Log.e(TAG, "checkForDeviceRemoval");
        if (device.getPairingStatus() == BluetoothDevicePickerBtStatus.PAIRING_STATUS_UNPAIRED
                && !device.isVisible()) {
            // If device isn't paired, remove it altogether
            mDevices.remove(device);
            dispatchDeviceDeleted(device);
        }
    }

    public synchronized void onDeviceNameUpdated(String address) {
        Log.e(TAG, "onDeviceNameUpdated");
        BluetoothDevicePickerDevice device = findDevice(address);
        if (device != null) {
            device.refreshName();
        }
    }

    public synchronized BluetoothDevicePickerDevice findDevice(String address) {

        for (int i = mDevices.size() - 1; i >= 0; i--) {
            BluetoothDevicePickerDevice device = mDevices.get(i);

            if (device.getAddress().equals(address)) {
                return device;
            }
        }

        return null;
    }

    public String getName(String address) {
        BluetoothDevicePickerDevice device = findDevice(address);
        return device != null ? device.getName() : address;
    }

    private void dispatchDeviceAdded(BluetoothDevicePickerDevice device) {
        synchronized (mCallbacks) {
            for (Callback callback : mCallbacks) {
                callback.onDeviceAdded(device);
            }
        }
    }

    private void dispatchDeviceDeleted(BluetoothDevicePickerDevice device) {
        synchronized (mCallbacks) {
            for (Callback callback : mCallbacks) {
                callback.onDeviceDeleted(device);
            }
        }
    }

    public synchronized void onBondingStateChanged(String address, boolean created) {
        BluetoothDevicePickerDevice device = findDevice(address);
        if (device == null) {
            Log.e(TAG, "Got bonding state changed for " + address
                    + ", but we have no record of that device.");
            return;
        }

        device.setPairingStatus(created ? BluetoothDevicePickerBtStatus.PAIRING_STATUS_PAIRED
                : BluetoothDevicePickerBtStatus.PAIRING_STATUS_UNPAIRED);
        checkForDeviceRemoval(device);

        if (created) {
            synchronized (mCallbacks) {
                for (Callback callback : mCallbacks) {
                    callback.onBondingStateChanged(address, created);
                }
            }

        }
    }

    public synchronized void onBondingError(String address) {
        mLocalManager.showError(address, R.string.bluetooth_error_title,
                R.string.bluetooth_pairing_error_message);
    }

    public synchronized void onProfileStateChanged(String address) {
        BluetoothDevicePickerDevice device = findDevice(address);
        if (device == null)
            return;

        device.refresh();
    }

    public synchronized void onScanningStateChanged(boolean started) {
        Log.e(TAG, "onScanningStateChanged, started = " + started);
        if (!started)
            return;

        // If starting a new scan, clear old visibility
        for (int i = mDevices.size() - 1; i >= 0; i--) {
            BluetoothDevicePickerDevice device = mDevices.get(i);
            device.setVisible(false);
            checkForDeviceRemoval(device);
        }
    }
}