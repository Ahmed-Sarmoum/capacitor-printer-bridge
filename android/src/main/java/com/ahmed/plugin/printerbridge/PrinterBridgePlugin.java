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
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.mazenrashed.printooth.Printooth;
import com.mazenrashed.printooth.data.printable.Printable;
import com.mazenrashed.printooth.data.printable.RawPrintable;
import com.mazenrashed.printooth.utilities.Printing;
import com.mazenrashed.printooth.utilities.PrintingCallback;

import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@CapacitorPlugin(
        name = "PrinterBridge",
        permissions = {
                @Permission(
                        alias = "bluetooth",
                        strings = {
                                // Basic Bluetooth permissions
                                Manifest.permission.BLUETOOTH,
                                Manifest.permission.BLUETOOTH_ADMIN,
                                // Android 12+ permissions
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN,
                                // Android 14+ permission
                                Manifest.permission.NEARBY_WIFI_DEVICES
                        }
                )
        }
)
public class PrinterBridgePlugin extends Plugin {
    private static final String TAG = "PrinterBridge";
    private static final int MAX_RETRIES = 3;

    // Handler for main thread operations
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Connection state variables
    private BluetoothGatt bluetoothGatt;
    private Printing printing;
    private int retryCount = 0;


    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private long connectionTimeout = 15000;

    // Saved calls for permission callbacks
    private PluginCall savedPrintCall;

    @PluginMethod
    public void print(PluginCall call) {
        Log.d(TAG, "Print method called");

        new Thread(() -> {
            Looper.prepare();

            Paper.init(getActivity().getApplicationContext());
            Printooth.INSTANCE.init(getActivity().getApplicationContext());
            Log.d(TAG, "PrinterBridge plugin loaded");

            if (!hasRequiredPermissions()) {
                Log.d(TAG, "Required permissions not granted, requesting permissions");
                requestBluetoothPermissions();
                return;
            }

            savedPrintCall = call;

            // Get parameters from UI
            String dataString = savedPrintCall.getString("data");
            String[] data = dataString != null ? dataString.split("\n") : new String[0];
            String printerName = savedPrintCall.getString("deviceName");
            String deviceId = savedPrintCall.getString("deviceId");

            // Reset retry counter and ensure connection before printing
            retryCount = 0;

            pairPrinter(printerName, deviceId);

            printText(data, printerName, deviceId);

        }).start();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    public void pairPrinter(String printerName, String deviceId) {
        try {
            Log.d(TAG, "Pairing with printer: " + printerName + " (" + deviceId + ")");

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // Ignore
            }

            Printooth.INSTANCE.setPrinter(printerName, deviceId);
            Log.d(TAG, "Printer paired: " + printerName);
        } catch (Exception e) {
            Log.e(TAG, "Error pairing printer", e);
        }
    }


    private void requestBluetoothPermissions() {
        Log.d(TAG, "Explicitly requesting Bluetooth permissions");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            ActivityCompat.requestPermissions(getActivity(), new String[] {
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.NEARBY_WIFI_DEVICES
            }, BLUETOOTH_PERMISSION_REQUEST);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            ActivityCompat.requestPermissions(getActivity(), new String[] {
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            }, BLUETOOTH_PERMISSION_REQUEST);
        } else {
            // No runtime permissions needed for older Android versions
            Log.d(TAG, "Bluetooth permissions automatically granted for Android < 12");
        }
    }

    @PluginMethod
    public void checkPermissions() {
        boolean permissionsGranted = hasRequiredPermissions();

        if (permissionsGranted) {
            Toast.makeText(getActivity(), "All required permissions granted", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getActivity(), "Bluetooth permissions not granted", Toast.LENGTH_SHORT).show();
        }
    }

    @PluginMethod
    public void requestPermissions() {
        Log.d(TAG, "Requesting Bluetooth permissions");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean hasConnect = ContextCompat.checkSelfPermission(
                    getActivity(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            boolean hasScan = ContextCompat.checkSelfPermission(
                    getActivity(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;

            Log.d(TAG, "Current permission status - BLUETOOTH_CONNECT: " +
                    (hasConnect ? "granted" : "denied") +
                    ", BLUETOOTH_SCAN: " + (hasScan ? "granted" : "denied"));

            String[] permissions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
                permissions = new String[] {
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.NEARBY_WIFI_DEVICES
                };
            } else { // Android 12-13
                permissions = new String[] {
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                };
            }

            ActivityCompat.requestPermissions(getActivity(), permissions, BLUETOOTH_PERMISSION_REQUEST);
        } else {
            Toast.makeText(getActivity(), "No permissions needed for your Android version", Toast.LENGTH_SHORT).show();
        }
    }

    @PermissionCallback
    public void permissionsCallback(PluginCall call) {
        if (call == null) {
            Log.e(TAG, "No stored plugin call for permissions request");
            return;
        }

        JSObject permissionsResultJSON = new JSObject();
        boolean permissionsGranted = hasRequiredPermissions();

        permissionsResultJSON.put("bluetooth", permissionsGranted ? "granted" : "denied");

        Log.d(TAG, "Permissions granted: " + permissionsGranted);

        // If this was a request initiated by a print call, proceed with printing if permissions were granted
        if (savedPrintCall != null) {
            if (permissionsGranted) {
                Log.d(TAG, "Permissions granted, proceeding with print");
                String dataString = savedPrintCall.getString("data");
                String[] data = dataString != null ? dataString.split("\n") : new String[0];
                String printerName = savedPrintCall.getString("deviceName");
                String deviceId = savedPrintCall.getString("deviceId");

                retryCount = 0;

                pairPrinter(printerName, deviceId);

                printText(data, printerName, deviceId);

                savedPrintCall = null;
            } else {
                Log.e(TAG, "Permissions denied, cannot proceed with print");
                PluginCall storedCall = savedPrintCall;
                savedPrintCall = null;
                storedCall.reject("Bluetooth permissions not granted");
            }
        }

        // Resolve the call that initiated the permission request
        call.resolve(permissionsResultJSON);
    }

    private void printText(String[] str, String printerName, String deviceId) {
        try {
            Log.d(TAG, "Printing text to " + printerName + " (" + deviceId + ")");

            printing = Printooth.INSTANCE.printer();

            printing.setPrintingCallback(new PrintingCallback() {
                @Override
                public void connectingWithPrinter() {
                    Log.d(TAG, "Connecting to printer...");
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getActivity(), "Connecting to printer...", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void connectionFailed(String s) {
                    Log.e(TAG, "Connection failed: " + s);
                    // Add a retry mechanism
                    if (retryCount < MAX_RETRIES) {
                        retryCount++;
                        Log.d(TAG, "Retrying connection, attempt " + retryCount);

                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getActivity(), "Connection failed, retrying... " + retryCount + "/" + MAX_RETRIES, Toast.LENGTH_SHORT).show();
                        });

                        // Add a delay before retry
                        new Handler().postDelayed(() -> {
                            // Try reconnecting
                            printText(str, printerName, deviceId);

                        }, 2000); // 2 second delay
                    } else {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getActivity(), "Failed to connect after " + MAX_RETRIES + " attempts", Toast.LENGTH_LONG).show();
                        });
                    }
                }

                @Override
                public void onError(String s) {
                    Log.e(TAG, "Print error: " + s);
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getActivity(), "Printing error: " + s, Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onMessage(String s) {
                    Log.d(TAG, "Printer message: " + s);
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getActivity(), "Printer message: " + s, Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void printingOrderSentSuccessfully() {
                    Log.d(TAG, "Print job sent successfully!");
                    retryCount = 0;
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getActivity(), "Print job sent successfully!", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void disconnected() {
                    Log.d(TAG, "Disconnected from printer");
                }
            });

            try {
                ArrayList<Printable> printables = new ArrayList<>();
                printables.add(new RawPrintable.Builder(new byte[]{0x1B, 0x74, 28}).build());

                for (String line : str) {
                    Log.d(TAG, "Adding line: " + line);
                    printables.add(new RawPrintable.Builder(line.getBytes("ISO-8859-6"))
                            .setNewLinesAfter(1)
                            .build());
                }

                Thread.sleep(3000);

                printing.print(printables);

            } catch (Exception e) {
                Log.e(TAG, "Exception when printing: " + e.getMessage(), e);
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getActivity(), "Error during print: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        } catch (Exception err) {
            Log.e(TAG, "Exception in printText: " + err.getMessage(), err);
            getActivity().runOnUiThread(() -> {
                Toast.makeText(getActivity(), "Error setting up printer: " + err.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }


    public boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            return ContextCompat.checkSelfPermission(
                    getActivity(), Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                            getActivity(), Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                            getActivity(), Manifest.permission.NEARBY_WIFI_DEVICES
                    ) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            return ContextCompat.checkSelfPermission(
                    getActivity(), Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                            getActivity(), Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Permissions automatically granted on Android < 12
    }

    private static final int BLUETOOTH_PERMISSION_REQUEST = 1001;


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