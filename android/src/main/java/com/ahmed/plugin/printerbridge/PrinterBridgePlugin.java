package com.ahmed.plugin.printerbridge;

import android.Manifest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.ahmed.plugin.printerbridge.exceptions.PrinterException;
import com.ahmed.plugin.printerbridge.models.PrintRequest;
import com.ahmed.plugin.printerbridge.models.QRCodePrintRequest;
import com.ahmed.plugin.printerbridge.services.BluetoothService;
import com.ahmed.plugin.printerbridge.services.PrinterService;
import com.ahmed.plugin.printerbridge.utils.Logger;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.mazenrashed.printooth.Printooth;

import org.json.JSONException;

import java.util.concurrent.atomic.AtomicBoolean;
import io.paperdb.Paper;

@CapacitorPlugin(
        name = "PrinterBridge",
        permissions = {
                @Permission(
                        alias = PrinterBridgePlugin.BLUETOOTH,
                        strings = {
                                Manifest.permission.BLUETOOTH,
                                Manifest.permission.BLUETOOTH_ADMIN,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        }
                ),
                @Permission(
                        alias = PrinterBridgePlugin.BLUETOOTH_CONNECT,
                        strings = {
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_ADVERTISE
                        }
                )
        }
)
public class PrinterBridgePlugin extends Plugin {

    // Define constants for the permission aliases
    static final String BLUETOOTH = "bluetooth";
    static final String BLUETOOTH_CONNECT = "bluetooth_connect";
    private static final String TAG = "PrinterBridgePlugin";

    // Your implementation services
    private BluetoothService bluetoothService;
    private PrinterService printerService;

    // State management
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void load() {
        // Initialize services in a background thread
        new Thread(this::initializeServices).start();
    }

    private void initializeServices() {
        if (isInitialized.compareAndSet(false, true)) {
            try {
                Paper.init(getContext());
                Printooth.INSTANCE.init(getContext());
                this.bluetoothService = new BluetoothService(getContext());
                this.printerService = new PrinterService(this.bluetoothService);
                Logger.d(TAG, "Services initialized successfully.");
            } catch (Exception e) {
                Logger.e(TAG, "Fatal: Failed to initialize services.", e);
                isInitialized.set(false);
            }
        }
    }

    // --- Core Plugin Methods ---

    @PluginMethod
    public void getPairedDevices(PluginCall call) {
        if (!ensureInitialized(call)) return;

        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions(call, "pairedDevicesPermissionCallback");
        } else {
            doGetPairedDevices(call);
        }
    }

    @PluginMethod
    public void getAvailableDevices(PluginCall call) {
        if (!ensureInitialized(call)) return;

        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions(call, "availableDevicesPermissionCallback");
        } else {
            doGetAvailableDevices(call);
        }
    }

    @PluginMethod
    public void pairDevice(PluginCall call) {
        if (!ensureInitialized(call)) return;

        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions(call, "pairDevicePermissionCallback");
        } else {
            doPairDevice(call);
        }
    }

    @PluginMethod
    public void getDeviceInfo(PluginCall call) {
        if (!ensureInitialized(call)) return;

        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions(call, "deviceInfoPermissionCallback");
        } else {
            doGetDeviceInfo(call);
        }
    }

    @PluginMethod
    public void getDeviceIdFromPairedDevices(PluginCall call) {
        if (!ensureInitialized(call)) return;

        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions(call, "deviceIdFromPairedPermissionCallback");
        } else {
            doGetDeviceIdFromPairedDevices(call);
        }
    }


    @PluginMethod
    public void print(PluginCall call) {
        if (!ensureInitialized(call)) return;

        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions(call, "printPermissionCallback");
        } else {
            doPrint(call);
        }
    }




    @PluginMethod
    public void printQRCode(PluginCall call) {
        if (!ensureInitialized(call)) return;

        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions(call, "printQRCodePermissionCallback");
        } else {
            doPrintQRCode(call);
        }
    }

    // --- Permission Callbacks ---

    @PermissionCallback
    private void pairedDevicesPermissionCallback(PluginCall call) {
        if (hasBluetoothPermissions()) {
            doGetPairedDevices(call);
        } else {
            call.reject("Bluetooth permissions are required to get paired devices.");
        }
    }

    @PermissionCallback
    private void availableDevicesPermissionCallback(PluginCall call) {
        if (hasBluetoothPermissions()) {
            doGetAvailableDevices(call);
        } else {
            call.reject("Bluetooth permissions are required to discover available devices.");
        }
    }

    @PermissionCallback
    private void pairDevicePermissionCallback(PluginCall call) {
        if (hasBluetoothPermissions()) {
            doPairDevice(call);
        } else {
            call.reject("Bluetooth permissions are required to pair devices.");
        }
    }

    @PermissionCallback
    private void deviceInfoPermissionCallback(PluginCall call) {
        if (hasBluetoothPermissions()) {
            doGetDeviceInfo(call);
        } else {
            call.reject("Bluetooth permissions are required to get device information.");
        }
    }

    @PermissionCallback
    private void deviceIdFromPairedPermissionCallback(PluginCall call) {
        if (hasBluetoothPermissions()) {
            doGetDeviceIdFromPairedDevices(call);
        } else {
            call.reject("Bluetooth permissions are required to search paired devices.");
        }
    }

    @PermissionCallback
    private void printPermissionCallback(PluginCall call) {
        if (hasBluetoothPermissions()) {
            doPrint(call);
        } else {
            call.reject("Bluetooth permissions are required to print.");
        }
    }

    @PermissionCallback
    private void printQRCodePermissionCallback(PluginCall call) {
        if (hasBluetoothPermissions()) {
            doPrintQRCode(call);
        } else {
            call.reject("Bluetooth permissions are required to print QR codes.");
        }
    }

    // --- Private "Implementation" Methods ---

    private void doGetPairedDevices(PluginCall call) {
        try {
            JSObject result = bluetoothService.getPairedDevices();
            // Add count for consistency with TypeScript interface
            if (result.has("devices")) {
                result.put("count", result.getJSONArray("devices").length());
            }
            call.resolve(result);
        } catch (PrinterException e) {
            call.reject(e.getMessage());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void doGetAvailableDevices(PluginCall call) {
        // Execute discovery in background thread as it takes time
        getBridge().execute(() -> {
            try {
                JSObject result = bluetoothService.discoverDevices();
                // Add count for consistency with TypeScript interface
                if (result.has("devices")) {
                    result.put("count", result.getJSONArray("devices").length());
                }
                mainHandler.post(() -> call.resolve(result));
            } catch (PrinterException e) {
                Logger.e(TAG, "Device discovery failed", e);
                mainHandler.post(() -> call.reject(e.getMessage()));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void doPairDevice(PluginCall call) {
        String deviceAddress = call.getString("deviceAddress");
        if (deviceAddress == null || deviceAddress.isEmpty()) {
            call.reject("Device address is required");
            return;
        }

        // Execute pairing in background thread as it takes time
        getBridge().execute(() -> {
            try {
                JSObject result = bluetoothService.pairDevice(deviceAddress);
                mainHandler.post(() -> call.resolve(result));
            } catch (PrinterException e) {
                Logger.e(TAG, "Device pairing failed", e);
                mainHandler.post(() -> call.reject(e.getMessage()));
            }
        });
    }

    private void doGetDeviceInfo(PluginCall call) {
        String deviceAddress = call.getString("deviceAddress");
        if (deviceAddress == null || deviceAddress.isEmpty()) {
            call.reject("Device address is required");
            return;
        }

        try {
            JSObject result = bluetoothService.getDeviceInfo(deviceAddress);
            call.resolve(result);
        } catch (PrinterException e) {
            call.reject(e.getMessage());
        }
    }

    private void doGetDeviceIdFromPairedDevices(PluginCall call) {
        String printerName = call.getString("printerName");
        if (printerName == null || printerName.isEmpty()) {
            call.reject("Printer name is required");
            return;
        }

        try {
            JSObject result = bluetoothService.getDeviceIdFromPairedDevices(printerName);
            // Add success flag and deviceName for consistency with TypeScript interface
            result.put("success", true);
            result.put("deviceName", printerName);
            call.resolve(result);
        } catch (PrinterException e) {
            JSObject result = new JSObject();
            result.put("success", false);
            result.put("deviceName", printerName);
            result.put("deviceId", "");
            call.resolve(result);
        }
    }

    private void doPrint(PluginCall call) {
        try {
            PrintRequest request = PrintRequest.fromPluginCall(call);
            executePrintOperation(call, () -> printerService.printText(request));
        } catch (Exception e) {
            call.reject("Invalid print request: " + e.getMessage());
        }
    }

    private void doPrintQRCode(PluginCall call) {
        try {
            QRCodePrintRequest request = QRCodePrintRequest.fromPluginCall(call);
            executePrintOperation(call, () -> printerService.printQRCode(request));
        } catch (Exception e) {
            call.reject("Invalid QR code print request: " + e.getMessage());
        }
    }

    private void executePrintOperation(PluginCall call, PrintOperation operation) {
        getBridge().execute(() -> {
            try {
                operation.execute();
                JSObject result = new JSObject();
                result.put("success", true);
                mainHandler.post(() -> call.resolve(result));
            } catch (PrinterException e) {
                Logger.e(TAG, "Print operation failed", e);
                JSObject result = new JSObject();
                result.put("success", false);
                mainHandler.post(() -> call.resolve(result));
            }
        });
    }

    // --- Permission Helper Methods ---

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires BLUETOOTH_CONNECT and BLUETOOTH_SCAN
            return getPermissionState(BLUETOOTH_CONNECT) == PermissionState.GRANTED;
        } else {
            // Android 11 and below requires BLUETOOTH, BLUETOOTH_ADMIN, and location permissions
            return getPermissionState(BLUETOOTH) == PermissionState.GRANTED;
        }
    }

    private void requestBluetoothPermissions(PluginCall call, String callbackName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionForAlias(BLUETOOTH_CONNECT, call, callbackName);
        } else {
            requestPermissionForAlias(BLUETOOTH, call, callbackName);
        }
    }

    // --- Standard Permission Methods ---

    @Override
    @PluginMethod
    public void checkPermissions(PluginCall call) {
        super.checkPermissions(call);
    }

    @Override
    @PluginMethod
    public void requestPermissions(PluginCall call) {
        super.requestPermissions(call);
    }

    // --- Internal Helpers ---

    private boolean ensureInitialized(PluginCall call) {
        if (!isInitialized.get()) {
            call.reject("PrinterBridge plugin is not initialized.");
            return false;
        }
        return true;
    }

    @Override
    protected void handleOnDestroy() {
        if (bluetoothService != null) {
            bluetoothService.cleanup();
        }
        super.handleOnDestroy();
    }

    @FunctionalInterface
    private interface PrintOperation {
        void execute() throws PrinterException;
    }
}