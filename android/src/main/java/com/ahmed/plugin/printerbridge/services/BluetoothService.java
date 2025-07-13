package com.ahmed.plugin.printerbridge.services;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.ahmed.plugin.printerbridge.exceptions.PrinterException;
import com.ahmed.plugin.printerbridge.utils.Logger;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BluetoothService {
    private static final String TAG = "BluetoothService";
    private static final int DISCOVERY_TIMEOUT_SECONDS = 30;
    private static final int PAIRING_TIMEOUT_SECONDS = 30;

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Device discovery management
    private final ConcurrentHashMap<String, BluetoothDevice> discoveredDevices = new ConcurrentHashMap<>();
    private final AtomicBoolean isDiscovering = new AtomicBoolean(false);
    private CountDownLatch discoveryLatch;
    private BroadcastReceiver discoveryReceiver;
    private BroadcastReceiver pairingReceiver;

    // Callbacks for discovery and pairing
    public interface DiscoveryCallback {
        void onDeviceFound(BluetoothDevice device);
        void onDiscoveryComplete();
        void onError(String error);
    }

    public interface PairingCallback {
        void onPairingSuccess(BluetoothDevice device);
        void onPairingFailed(String error);
        void onPinRequired(String pin);
    }

    public BluetoothService(Context context) throws PrinterException {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            throw new PrinterException("Bluetooth is not supported on this device.");
        }
        setupReceivers();
    }

    private void setupReceivers() {
        // Discovery receiver
        discoveryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        discoveredDevices.put(device.getAddress(), device);
                        Logger.d(TAG, "Device discovered: " + device.getAddress());
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    isDiscovering.set(false);
                    if (discoveryLatch != null) {
                        discoveryLatch.countDown();
                    }
                    Logger.d(TAG, "Discovery finished");
                }
            }
        };

        // Pairing receiver
        pairingReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);

                    if (device != null) {
                        Logger.d(TAG, "Bond state changed for " + device.getAddress() + ": " + bondState);
                        switch (bondState) {
                            case BluetoothDevice.BOND_BONDED:
                                Logger.d(TAG, "Device paired successfully: " + device.getAddress());
                                break;
                            case BluetoothDevice.BOND_NONE:
                                Logger.d(TAG, "Device unpaired: " + device.getAddress());
                                break;
                        }
                    }
                }
            }
        };
    }

    public void validateBluetoothState() throws PrinterException {
        lock.readLock().lock();
        try {
            if (bluetoothAdapter == null) {
                throw new PrinterException("Bluetooth is not supported on this device.");
            }
            if (!bluetoothAdapter.isEnabled()) {
                throw new PrinterException("Bluetooth is disabled. Please enable it.");
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public JSObject getPairedDevices() throws PrinterException {
        lock.readLock().lock();
        try {
            validateBluetoothState();
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            JSArray devicesArray = new JSArray();

            if (pairedDevices != null && !pairedDevices.isEmpty()) {
                for (BluetoothDevice device : pairedDevices) {
                    JSObject deviceInfo = createDeviceInfo(device, true);
                    devicesArray.put(deviceInfo);
                }
            }

            Logger.d(TAG, "Found " + devicesArray.length() + " paired devices.");
            JSObject result = new JSObject();
            result.put("devices", devicesArray);
            return result;
        } catch (SecurityException e) {
            Logger.e(TAG, "Permission missing for getting paired devices", e);
            throw new PrinterException("Bluetooth permission denied.");
        } finally {
            lock.readLock().unlock();
        }
    }

    public JSObject discoverDevices() throws PrinterException {
        if (isDiscovering.get()) {
            throw new PrinterException("Discovery is already in progress.");
        }

        lock.writeLock().lock();
        try {
            validateBluetoothState();
            discoveredDevices.clear();

            // Register discovery receiver
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            context.registerReceiver(discoveryReceiver, filter);

            // Start discovery
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }

            if (!bluetoothAdapter.startDiscovery()) {
                throw new PrinterException("Failed to start device discovery.");
            }

            isDiscovering.set(true);
            discoveryLatch = new CountDownLatch(1);

            // Wait for discovery to complete
            if (!discoveryLatch.await(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                bluetoothAdapter.cancelDiscovery();
                throw new PrinterException("Discovery timed out.");
            }

            // Combine paired and discovered devices
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            JSArray devicesArray = new JSArray();

            // Add paired devices
            if (pairedDevices != null) {
                for (BluetoothDevice device : pairedDevices) {
                    JSObject deviceInfo = createDeviceInfo(device, true);
                    devicesArray.put(deviceInfo);
                }
            }

            // Add discovered devices that are not paired
            for (BluetoothDevice device : discoveredDevices.values()) {
                if (pairedDevices == null || !pairedDevices.contains(device)) {
                    JSObject deviceInfo = createDeviceInfo(device, false);
                    devicesArray.put(deviceInfo);
                }
            }

            JSObject result = new JSObject();
            result.put("devices", devicesArray);
            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PrinterException("Discovery was interrupted.");
        } catch (SecurityException e) {
            Logger.e(TAG, "Permission missing for device discovery", e);
            throw new PrinterException("Bluetooth permission denied.");
        } finally {
            try {
                if (discoveryReceiver != null) {
                    context.unregisterReceiver(discoveryReceiver);
                }
            } catch (Exception e) {
                Logger.e(TAG, "Error unregistering discovery receiver", e);
            }
            isDiscovering.set(false);
            lock.writeLock().unlock();
        }
    }

    public JSObject pairDevice(String deviceAddress) throws PrinterException {
        lock.writeLock().lock();
        try {
            validateBluetoothState();

            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            if (device == null) {
                throw new PrinterException("Device not found: " + deviceAddress);
            }

            // Check if already paired
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                JSObject result = new JSObject();
                result.put("success", true);
                result.put("message", "Device is already paired");
                return result;
            }

            // Register pairing receiver
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            context.registerReceiver(pairingReceiver, filter);

            // Start pairing
            if (!device.createBond()) {
                throw new PrinterException("Failed to initiate pairing with device: " + deviceAddress);
            }

            // Wait for pairing to complete
            CountDownLatch pairingLatch = new CountDownLatch(1);
            long startTime = System.currentTimeMillis();

            while (device.getBondState() == BluetoothDevice.BOND_BONDING) {
                if (System.currentTimeMillis() - startTime > PAIRING_TIMEOUT_SECONDS * 1000) {
                    throw new PrinterException("Pairing timed out for device: " + deviceAddress);
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new PrinterException("Pairing was interrupted");
                }
            }

            JSObject result = new JSObject();
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                result.put("success", true);
                result.put("message", "Device paired successfully");
            } else {
                result.put("success", false);
                result.put("message", "Pairing failed");
            }

            return result;

        } catch (SecurityException e) {
            Logger.e(TAG, "Permission missing for device pairing", e);
            throw new PrinterException("Bluetooth permission denied.");
        } finally {
            try {
                if (pairingReceiver != null) {
                    context.unregisterReceiver(pairingReceiver);
                }
            } catch (Exception e) {
                Logger.e(TAG, "Error unregistering pairing receiver", e);
            }
            lock.writeLock().unlock();
        }
    }

    public JSObject getDeviceInfo(String deviceAddress) throws PrinterException {
        lock.readLock().lock();
        try {
            validateBluetoothState();

            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            if (device == null) {
                throw new PrinterException("Device not found: " + deviceAddress);
            }

            return createDeviceInfo(device, device.getBondState() == BluetoothDevice.BOND_BONDED);

        } catch (SecurityException e) {
            Logger.e(TAG, "Permission missing for getting device info", e);
            throw new PrinterException("Bluetooth permission denied.");
        } finally {
            lock.readLock().unlock();
        }
    }

    public JSObject getDeviceIdFromPairedDevices(String printerName) throws PrinterException {
        lock.readLock().lock();
        try {
            validateBluetoothState();
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

            if (pairedDevices == null || pairedDevices.isEmpty()) {
                throw new PrinterException("No paired devices found.");
            }

            for (BluetoothDevice device : pairedDevices) {
                if (device.getName() != null && device.getName().equalsIgnoreCase(printerName)) {
                    Logger.d(TAG, "Found matching device: " + device.getName() + " [" + device.getAddress() + "]");
                    JSObject result = new JSObject();
                    result.put("deviceId", device.getAddress());
                    return result;
                }
            }
            throw new PrinterException("No paired device found with name: " + printerName);
        } catch (SecurityException e) {
            Logger.e(TAG, "Permission missing for getting device from paired list", e);
            throw new PrinterException("Bluetooth permission denied.");
        } finally {
            lock.readLock().unlock();
        }
    }

    private JSObject createDeviceInfo(BluetoothDevice device, boolean isPaired) {
        JSObject deviceInfo = new JSObject();
        try {
            deviceInfo.put("name", device.getName() != null ? device.getName() : "Unknown Device");
            deviceInfo.put("deviceId", device.getAddress());
            deviceInfo.put("isPaired", isPaired);
            deviceInfo.put("bondState", getBondStateString(device.getBondState()));

            // Add device type info if available
            if (device.getBluetoothClass() != null) {
                deviceInfo.put("deviceClass", device.getBluetoothClass().getDeviceClass());
                deviceInfo.put("majorDeviceClass", device.getBluetoothClass().getMajorDeviceClass());
            }

        } catch (SecurityException e) {
            Logger.w(TAG, "Could not get full device info due to missing permissions.");
            deviceInfo.put("name", "Permission Required");
            deviceInfo.put("deviceId", device.getAddress());
            deviceInfo.put("isPaired", isPaired);
        }
        return deviceInfo;
    }

    private String getBondStateString(int bondState) {
        switch (bondState) {
            case BluetoothDevice.BOND_BONDED:
                return "BONDED";
            case BluetoothDevice.BOND_BONDING:
                return "BONDING";
            case BluetoothDevice.BOND_NONE:
                return "NONE";
            default:
                return "UNKNOWN";
        }
    }

    public void cleanup() {
        lock.writeLock().lock();
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }

            try {
                if (discoveryReceiver != null) {
                    context.unregisterReceiver(discoveryReceiver);
                }
            } catch (Exception e) {
                Logger.e(TAG, "Error unregistering discovery receiver during cleanup", e);
            }

            try {
                if (pairingReceiver != null) {
                    context.unregisterReceiver(pairingReceiver);
                }
            } catch (Exception e) {
                Logger.e(TAG, "Error unregistering pairing receiver during cleanup", e);
            }

            discoveredDevices.clear();
            isDiscovering.set(false);

        } finally {
            lock.writeLock().unlock();
        }
        Logger.d(TAG, "Bluetooth service cleaned up.");
    }
}