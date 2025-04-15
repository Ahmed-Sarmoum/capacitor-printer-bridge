package com.ahmed.plugin.printerbridge;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import io.paperdb.Paper;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;

import com.mazenrashed.printooth.utilities.PrintingCallback;

import android.bluetooth.BluetoothGatt;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.mazenrashed.printooth.Printooth;

import com.mazenrashed.printooth.data.printable.Printable;
import com.mazenrashed.printooth.utilities.Printing;
import com.mazenrashed.printooth.data.printable.RawPrintable;


import android.graphics.Bitmap;


import java.util.ArrayList;
@CapacitorPlugin(
        name = "PrinterBridge",
        permissions = {
                @Permission(
                        alias = "bluetooth",
                        strings = {
                                Manifest.permission.BLUETOOTH,
                                Manifest.permission.BLUETOOTH_ADMIN,
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN,
                        }
                )
        }
)
public class PrinterBridgePlugin extends Plugin {
    @Override
    public void load() {
        super.load();
        Paper.init(this.getActivity());
        Printooth.INSTANCE.init(this.getActivity());
    }

    private static final String TAG = "PrinterBridge";
    private BluetoothGatt bluetoothGatt;
    private PluginCall savedPrintCall;
    private Printing printing;

    @PluginMethod
    public void print(PluginCall call) {
        if (!hasRequiredPermissions()) {
            savedPrintCall = call;
            requestPermissionForPrint();
            return;
        }

        String dataString = call.getString("data");
        String[] data = dataString != null ? dataString.split("\n") : new String[0];
        String printerName = call.getString("deviceName");
        String deviceId = call.getString("deviceId");

        printText(call, data, printerName, deviceId);

    }


    @PluginMethod
    public void printQRCode(PluginCall call) {
        if (!hasRequiredPermissions()) {
            savedPrintCall = call;
            requestPermissionForPrint();
            return;
        }

        String qrData = call.getString("qrData");
        String printerName = call.getString("deviceName");
        String deviceId = call.getString("deviceId");

        if (qrData == null || qrData.isEmpty()) {
            call.reject("QR code data cannot be empty");
            return;
        }

        printQRCodeWithESCPOS(call, qrData, printerName, deviceId);
    }

    public void printQRCodeWithESCPOS(PluginCall call, String qrData, String printerName, String deviceId) {

        // Implement a retry counter
        final int[] retryCount = {0};
        final int MAX_RETRIES = 3;

        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                Log.e(TAG, "Bluetooth not supported");
                call.reject("Bluetooth not supported");
                return;
            }

            if (!adapter.isEnabled()) {
                Log.e(TAG, "Bluetooth is disabled");
                call.reject("Bluetooth is disabled");
                return;
            }

            // Check QR data length
            byte[] qrBytes;
            try {
                qrBytes = qrData.getBytes("UTF-8");
                if (qrBytes.length > 800) {
                    Log.e(TAG, "QR data is too large (" + qrBytes.length + " bytes)");
                    call.reject("QR data is too large. Please reduce data to less than 800 bytes.");
                    return;
                }

                if (qrBytes.length > 200) {
                    Log.w(TAG, "QR data is very long (" + qrBytes.length + " bytes), might cause issues with some printers");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error encoding QR data: " + e.getMessage());
                call.reject("Error encoding QR data: " + e.getMessage());
                return;
            }

            // Try to ensure any previous connections are closed
            if (printing != null) {
                try {
                    printing.wait();
                    // Wait a moment for the disconnect to complete
                    Thread.sleep(1000);
                } catch (Exception e) {
                    Log.w(TAG, "Error disconnecting previous session: " + e.getMessage());
                    // Continue anyway
                }
            }

            Printooth.INSTANCE.setPrinter(printerName, deviceId);
            printing = Printooth.INSTANCE.printer();

            printing.setPrintingCallback(new PrintingCallback() {
                @Override
                public void connectingWithPrinter() {
                    Log.d(TAG, "Connecting to printer...");
                }

                @Override
                public void connectionFailed(String s) {
                    Log.e(TAG, "Connection failed: " + s);

                    // Implement retry logic
                    if (retryCount[0] < MAX_RETRIES) {
                        retryCount[0]++;
                        Log.d(TAG, "Retrying connection, attempt " + retryCount[0]);

                        // Wait a moment before retrying
                        try {
                            Thread.sleep(2000);
                            // Then try to print again
                            preparePrintData(qrBytes, call);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Retry interrupted: " + e.getMessage());
                            call.reject("Connection retry failed: " + s);
                        }
                    } else {
                        call.reject("Connection failed after " + MAX_RETRIES + " attempts. Please check the printer and try again.");
                    }
                }

                @Override
                public void onError(String s) {
                    Log.e(TAG, "Print error: " + s);
                    call.reject("Printing error: " + s);
                }

                @Override
                public void onMessage(String s) {
                    Log.d(TAG, "Printer message: " + s);
                }

                @Override
                public void printingOrderSentSuccessfully() {
                    Log.d(TAG, "Print job sent successfully!");
                    call.resolve();
                }

                @Override
                public void disconnected() {
                    Log.d(TAG, "Disconnected from printer");
                }
            });

            // Start the initial print attempt
            preparePrintData(qrBytes, call);

        } catch (Exception err) {
            Log.e(TAG, "Exception in printQRCode: " + err.getMessage(), err);
            call.reject("Error setting up printer: " + err.getMessage());
        }
    }

    // Separate method for preparing print data that can be called for retries
    private void preparePrintData(byte[] qrBytes, PluginCall call) {
        try {
            ArrayList<Printable> printables = new ArrayList<>();

            // Initialize printer
            printables.add(new RawPrintable.Builder(new byte[]{0x1B, 0x40}).build());

            // Center alignment
            printables.add(new RawPrintable.Builder(new byte[]{0x1B, 0x61, 0x01}).build());

            // Determine best settings based on data length
            int qrSize = 4; // Default size
            int errorCorrectionLevel = 0x31; // Default to 'M' level

            // Adjust QR code parameters based on data length
            if (qrBytes.length > 300) {
                qrSize = 6;
                errorCorrectionLevel = 0x30; // Lower error correction for more data capacity ('0' = L level)
                Log.d(TAG, "Using larger QR size and lower error correction for large data");
            } else if (qrBytes.length > 100) {
                qrSize = 5; // Medium size
                Log.d(TAG, "Using medium QR size for moderate data");
            }

            // QR Code: Select model
            printables.add(new RawPrintable.Builder(new byte[]{0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00}).build());

            // QR Code: Set size
            printables.add(new RawPrintable.Builder(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, (byte)qrSize}).build());

            // QR Code: Set error correction level
            // 30h = L (7%), 31h = M (15%), 32h = Q (25%), 33h = H (30%)
            printables.add(new RawPrintable.Builder(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, (byte)errorCorrectionLevel}).build());

            // Calculate length bytes for the data command
            int len = qrBytes.length + 3;
            byte pL = (byte) (len % 256);
            byte pH = (byte) (len / 256);

            // Store QR data
            printables.add(new RawPrintable.Builder(new byte[]{0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30}).build());
            printables.add(new RawPrintable.Builder(qrBytes).build());

            // Print QR code
            printables.add(new RawPrintable.Builder(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30}).build());

            // Extra line feed to ensure QR is printed fully
            printables.add(new RawPrintable.Builder(new byte[]{0x0A, 0x0A}).build());

            // Reset to left alignment
            printables.add(new RawPrintable.Builder(new byte[]{0x1B, 0x61, 0x00}).build());

            // Add some space after QR code
            lineFeed(3, printables);

            // Send print job
            printing.print(printables);

        } catch (Exception e) {
            Log.e(TAG, "Exception when preparing print data: " + e.getMessage(), e);
            call.reject("Error preparing print data: " + e.getMessage());
        }
    }
    public void printText(PluginCall call, String[] str, String printerName, String deviceId) {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                Log.e(TAG, "Bluetooth not supported");
                call.reject("Bluetooth not supported");
                return;
            }

            if (!adapter.isEnabled()) {
                Log.e(TAG, "Bluetooth is disabled");
                call.reject("Bluetooth is disabled");
                return;
            }

            Printooth.INSTANCE.setPrinter(printerName, deviceId);

            printing = Printooth.INSTANCE.printer();

            printing.setPrintingCallback(new PrintingCallback() {
                @Override
                public void connectingWithPrinter() {
                    Log.d(TAG, "Connecting to printer...");
                }

                @Override
                public void connectionFailed(String s) {
                    Log.e(TAG, "Connection failed: " + s);
                    call.reject("Connection failed, please try reconnect to the printer!!");
                }

                @Override
                public void onError(String s) {
                    Log.e(TAG, "Print error: " + s);
                    call.reject("Printing error: " + s);
                }

                @Override
                public void onMessage(String s) {
                    Log.d(TAG, "Printer message: " + s);
                }

                @Override
                public void printingOrderSentSuccessfully() {
                    Log.d(TAG, "Print job sent successfully!");
                    call.resolve();
                }

                @Override
                public void disconnected() {
                    Log.d(TAG, "Disconnected from printer");
                }
            });

            try {
                ArrayList<Printable> printables = new ArrayList<>();
                printables.add(new RawPrintable.Builder(new byte[]{0x1B,0x74,28}).build());

                for(String line: str) {
                    Log.d(TAG, line);
                    printables.add(new RawPrintable.Builder(line.getBytes("ISO-8859-6")).setNewLinesAfter(1).build());
                }

                lineFeed(3, printables);

                printing.print(printables);
            } catch (Exception e) {
                Log.e(TAG, "Exception when printing: " + e.getMessage(), e);
                call.reject("Error during print: " + e.getMessage());
            }
        } catch (Exception err) {
            Log.e(TAG, "Exception in printText: " + err.getMessage(), err);
            call.reject("Error setting up printer: " + err.getMessage());
        }
    }

    private void lineFeed(int num, ArrayList<Printable> printables) {
        for (int i = 1; i<=num; i++) {
            printables.add(new RawPrintable.Builder(new byte[]{0x0D, 0x0A}).build());
        }
    }

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        JSObject permissionsResultJSON = new JSObject();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            boolean hasBluetoothConnect = ContextCompat.checkSelfPermission(
                    getContext(), Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED;

            boolean hasBluetoothScan = ContextCompat.checkSelfPermission(
                    getContext(), Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED;


            permissionsResultJSON.put("bluetooth",
                    (hasBluetoothConnect && hasBluetoothScan) ? "granted" : "denied");

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            boolean hasBluetoothConnect = ContextCompat.checkSelfPermission(
                    getContext(), Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED;

            boolean hasBluetoothScan = ContextCompat.checkSelfPermission(
                    getContext(), Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED;

            permissionsResultJSON.put("bluetooth",
                    (hasBluetoothConnect && hasBluetoothScan) ? "granted" : "denied");

        } else {
            permissionsResultJSON.put("bluetooth", "granted");
        }

        call.resolve(permissionsResultJSON);
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestAllPermissions(call, "permissionsCallback");
        } else {
            JSObject result = new JSObject();
            result.put("bluetooth", "granted");
            call.resolve(result);
        }
    }

    @PermissionCallback
    private void permissionsCallback(PluginCall call) {
        JSObject permissionsResultJSON = new JSObject();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean hasBluetoothConnect = ContextCompat.checkSelfPermission(
                    getContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED;

            boolean hasBluetoothScan = ContextCompat.checkSelfPermission(
                    getContext(),
                    Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED;

            permissionsResultJSON.put("bluetooth",
                    hasBluetoothConnect && hasBluetoothScan ? "granted" : "denied");
        } else {
            permissionsResultJSON.put("bluetooth", "granted");
        }

        if (savedPrintCall != null) {
            if (permissionsResultJSON.getString("bluetooth").equals("granted")) {
                String dataString = call.getString("data");
                String[] data = dataString != null ? dataString.split("\n") : new String[0];
                String printerName = call.getString("printerName");
                String deviceId = call.getString("deviceId");

                printText(call, data, printerName, deviceId);
            } else {
                savedPrintCall.reject("Bluetooth connect permission not granted");
            }
            savedPrintCall = null;
        }
    }

    private void requestPermissionForPrint() {
        requestAllPermissions(savedPrintCall, "permissionsCallback");
    }

    public boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            return ContextCompat.checkSelfPermission(
                    getContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                            getContext(),
                            Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            return ContextCompat.checkSelfPermission(
                    getContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                            getContext(),
                            Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Not needed on Android < 12
    }


    @Override
    protected void handleOnDestroy() {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this.getContext(),
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
        }
        super.handleOnDestroy();
    }
}