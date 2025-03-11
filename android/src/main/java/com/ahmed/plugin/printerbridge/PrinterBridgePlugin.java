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
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Handler for main thread operations
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Connection state variables
    private BluetoothGatt bluetoothGatt;
    private Printing printing;
    private int retryCount = 0;

    // Connection state management flags
    private long lastConnectionAttempt = 0;
    private long CONNECTION_COOLDOWN = 3000; // 3 seconds between connection attempts

    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private long connectionTimeout = 15000;

    // Saved calls for permission callbacks
    private PluginCall savedPrintCall;

    @Override
    public void load() {
        super.load();
        // Initialize Paper DB and Printooth
        Paper.init(getActivity().getApplicationContext());
        Printooth.INSTANCE.init(getActivity().getApplicationContext());
        Log.d(TAG, "PrinterBridge plugin loaded");
    }

    /**
     * Main printing method - handles sending text to a Bluetooth printer
     */
    @PluginMethod
    public void print(PluginCall call) {
        Log.d(TAG, "Print method called");

        new Thread(() -> {
            if (!hasRequiredPermissions()) {
                Log.d(TAG, "Required permissions not granted, requesting permissions");
                savedPrintCall = call;
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

            // First clean up any existing connections
            releaseBluetoothResources();

            ensureBluetoothConnection(deviceId, printerName, () -> {
                printText(data, printerName, deviceId);
            });
        }).start();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Method to pair with a printer
     */
    public void pairPrinter(String printerName, String deviceId) {
        if (!hasRequiredPermissions()) {
            Log.d(TAG, "Required permissions not granted, requesting permissions");
            requestBluetoothPermissions();
            return;
        }

        try {
            Log.d(TAG, "Pairing with printer: " + printerName + " (" + deviceId + ")");

            // First release any existing resources
            releaseBluetoothResources();

            if (Printooth.INSTANCE.hasPairedPrinter()) {
                Printooth.INSTANCE.removeCurrentPrinter();
            }

            // Reinitialize the Paper and Printooth to ensure clean state
            Paper.init(getActivity().getApplicationContext());
            Printooth.INSTANCE.init(getActivity().getApplicationContext());

            // Add a short delay to ensure proper initialization
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

    /**
     * Check status of all required permissions
     */
    @PluginMethod
    public void checkPermissions() {
        boolean permissionsGranted = hasRequiredPermissions();

        if (permissionsGranted) {
            Toast.makeText(getActivity(), "All required permissions granted", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getActivity(), "Bluetooth permissions not granted", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Request permissions through Plugin API
     */
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

    /**
     * Callback handler for permission requests
     */
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

                // Reset retry counter and ensure connection before printing
                retryCount = 0;

                // First release any existing connections
                releaseBluetoothResources();

                // Then pair with the printer
                pairPrinter(printerName, deviceId);

                ensureBluetoothConnection(deviceId, printerName, () -> {
                    printText(data, printerName, deviceId);
                });

                // Clear the saved call since we're handling it
                PluginCall storedCall = savedPrintCall;
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

    private final ExecutorService bluetoothExecutor = Executors.newSingleThreadExecutor();

    private void ensureBluetoothConnection(String deviceId, String printerName, Runnable onConnected) {
        // Don't try to connect if already connecting
        if (isConnecting.getAndSet(true)) {
            Log.d(TAG, "Already attempting connection, waiting...");
            // Schedule callback after a brief delay if we're already in the process of connecting
            new Handler().postDelayed(() -> {
                // If we're connected by then, proceed with callback
                if (isConnected.get()) {
                    mainHandler.post(onConnected);
                } else {
                    // Still not connected, try again
                    isConnecting.set(false);
                    ensureBluetoothConnection(deviceId, printerName, onConnected);
                }
            }, 2000);
            return;
        }

        // Use executor to move Bluetooth operations off the main thread
        bluetoothExecutor.execute(() -> {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null) {
                    Log.e(TAG, "Bluetooth not supported on this device");
                    mainHandler.post(() -> {
                        Toast.makeText(getActivity(), "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show();
                    });
                    isConnecting.set(false);
                    return;
                }

                // Check if Bluetooth is enabled
                if (!adapter.isEnabled()) {
                    Log.e(TAG, "Bluetooth is disabled");
                    mainHandler.post(() -> {
                        Toast.makeText(getActivity(), "Bluetooth is disabled. Please enable it.", Toast.LENGTH_SHORT).show();
                    });
                    isConnecting.set(false);
                    return;
                }

                // Check permissions off the main thread
                boolean hasPermissions = hasRequiredPermissions();
                if (!hasPermissions) {
                    Log.e(TAG, "Missing required Bluetooth permissions!");
                    mainHandler.post(() -> {
                        Toast.makeText(getActivity(), "Missing required Bluetooth permissions!", Toast.LENGTH_SHORT).show();
                        requestBluetoothPermissions();
                    });
                    isConnecting.set(false);
                    return;
                }

                // First clean up resources
                releaseBluetoothResources();
                Thread.sleep(1000);

//                // Cancel any existing discovery
//                if (ActivityCompat.checkSelfPermission(getContext(),
//                        Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
//                    adapter.cancelDiscovery();
//                }

                Log.d(TAG, "Attempting to connect to device: " + deviceId);
                mainHandler.post(() -> {
                    Toast.makeText(getActivity(), "Connecting to device: " + deviceId, Toast.LENGTH_SHORT).show();
                });
//
//                // Re-initialize everything from scratch
//                mainHandler.post(() -> {
//                    Paper.init(getActivity().getApplicationContext());
//                    Printooth.INSTANCE.init(getActivity().getApplicationContext());
//                });

                Thread.sleep(1000);

                // This part needs to run on the main thread
                final AtomicBoolean printerSet = new AtomicBoolean(false);
                mainHandler.post(() -> {
                    try {
                        // Remove any existing printers first
                        if (Printooth.INSTANCE.hasPairedPrinter()) {
                            Printooth.INSTANCE.removeCurrentPrinter();
                        }

                        // Add a short delay
                        new Handler().postDelayed(() -> {
                            try {
                                // Set the printer with deviceId
                                Printooth.INSTANCE.setPrinter(printerName, deviceId);
                                printerSet.set(true);
                            } catch (Exception e) {
                                Log.e(TAG, "Error setting printer", e);
                            }
                        }, 500);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in printer setup", e);
                    }
                });

                // Wait for printer to be set with timeout
                long startTime = System.currentTimeMillis();
                while (!printerSet.get()) {
                    Thread.sleep(200);
                    if (System.currentTimeMillis() - startTime > 5000) {
                        Log.e(TAG, "Timed out waiting for printer to be set");
                        isConnecting.set(false);
                        return;
                    }
                }

                // Mark as connected and run callback
                isConnected.set(true);
                isConnecting.set(false);

                // Wait a bit before calling the callback to ensure connection is stable
                Thread.sleep(1500);

                // Run callback on main thread
                mainHandler.post(onConnected);

            } catch (Exception e) {
                Log.e(TAG, "Error in ensureBluetoothConnection: " + e.getMessage(), e);
                isConnecting.set(false);
                isConnected.set(false);
                mainHandler.post(() -> {
                    Toast.makeText(getActivity(), "Error connecting to printer: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void printText(String[] str, String printerName, String deviceId) {
        // Run everything in a separate thread
        bluetoothExecutor.execute(() -> {
            try {
                if (!isConnected.get()) {
                    Log.d(TAG, "Not connected to printer, attempting connection first");
                    final CountDownLatch connectionLatch = new CountDownLatch(1);

                    mainHandler.post(() -> {
                        ensureBluetoothConnection(deviceId, printerName, () -> {
                            connectionLatch.countDown();
                        });
                    });

                    // Wait for connection with timeout
                    if (!connectionLatch.await(connectionTimeout, TimeUnit.MILLISECONDS)) {
                        Log.e(TAG, "Connection timed out");
                        mainHandler.post(() -> {
                            Toast.makeText(getActivity(), "Connection timed out", Toast.LENGTH_SHORT).show();
                            if (savedPrintCall != null) {
                                savedPrintCall.reject("Connection timed out");
                                savedPrintCall = null;
                            }
                        });
                        return;
                    }
                }

                Log.d(TAG, "Printing text to " + printerName + " (" + deviceId + ")");

                // Double-check printer pairing status on main thread
                final AtomicBoolean printerReady = new AtomicBoolean(false);
                final CountDownLatch printerLatch = new CountDownLatch(1);

                mainHandler.post(() -> {
                    try {
                        if (!Printooth.INSTANCE.hasPairedPrinter()) {
                            Printooth.INSTANCE.setPrinter(printerName, deviceId);
                        }
                        printerReady.set(true);
                    } catch (Exception e) {
                        Log.e(TAG, "Error checking printer", e);
                    } finally {
                        printerLatch.countDown();
                    }
                });

                // Wait for printer check
                printerLatch.await(5, TimeUnit.SECONDS);

                if (!printerReady.get()) {
                    Log.e(TAG, "Failed to prepare printer");
                    mainHandler.post(() -> {
                        Toast.makeText(getActivity(), "Failed to prepare printer", Toast.LENGTH_SHORT).show();
                        if (savedPrintCall != null) {
                            savedPrintCall.reject("Failed to prepare printer");
                            savedPrintCall = null;
                        }
                    });
                    return;
                }

                // Initialize printing
                final CountDownLatch printingLatch = new CountDownLatch(1);
                final AtomicReference<Printing> printingRef = new AtomicReference<>();

                mainHandler.post(() -> {
                    try {
                        printing = Printooth.INSTANCE.printer();
                        printingRef.set(printing);
                    } catch (Exception e) {
                        Log.e(TAG, "Error initializing printer", e);
                    } finally {
                        printingLatch.countDown();
                    }
                });

//                connectDevice();

                Thread.sleep(500);

                // Wait for printing to initialize
                printingLatch.await(5, TimeUnit.SECONDS);

                Printing currentPrinting = printingRef.get();
                if (currentPrinting == null) {
                    Log.e(TAG, "Failed to initialize printer");
                    mainHandler.post(() -> {
                        Toast.makeText(getActivity(), "Failed to initialize printer", Toast.LENGTH_SHORT).show();
                        if (savedPrintCall != null) {
                            savedPrintCall.reject("Failed to initialize printer");
                            savedPrintCall = null;
                        }
                    });
                    return;
                }

                // Create a print callback
                final CountDownLatch printLatch = new CountDownLatch(1);
                final AtomicBoolean printSuccess = new AtomicBoolean(false);
                final AtomicReference<String> printError = new AtomicReference<>("");

                PrintingCallback callback = new PrintingCallback() {
                    @Override
                    public void connectingWithPrinter() {
                        Log.d(TAG, "Connecting to printer...");
                        mainHandler.post(() -> {
                            Toast.makeText(getActivity(), "Connecting to printer...", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void connectionFailed(String s) {
                        Log.e(TAG, "Connection failed: " + s);
                        printError.set("Connection failed: " + s);
                        isConnected.set(false);
                        printLatch.countDown();
                    }

                    @Override
                    public void onError(String s) {
                        Log.e(TAG, "Print error: " + s);
                        printError.set("Print error: " + s);
                        printLatch.countDown();
                    }

                    @Override
                    public void onMessage(String s) {
                        Log.d(TAG, "Printer message: " + s);
                    }

                    @Override
                    public void printingOrderSentSuccessfully() {
                        Log.d(TAG, "Print job sent successfully!");
                        printSuccess.set(true);
                        printLatch.countDown();
                    }

                    @Override
                    public void disconnected() {
                        Log.d(TAG, "Disconnected from printer");
                        isConnected.set(false);
                    }
                };

                // Set callback and prepare printables on main thread
                final CountDownLatch prepareLatch = new CountDownLatch(1);
                final AtomicReference<ArrayList<Printable>> printablesRef = new AtomicReference<>();

                mainHandler.post(() -> {
                    try {
                        currentPrinting.setPrintingCallback(callback);

                        // Create printable content with Arabic/ISO encoding support
                        ArrayList<Printable> printables = new ArrayList<>();
                        // Set character encoding to support Arabic
                        printables.add(new RawPrintable.Builder(new byte[]{0x1B, 0x74, 28}).build());

                        // Add each line of text
                        for (String line : str) {
                            Log.d(TAG, "Adding line: " + line);
                            printables.add(new RawPrintable.Builder(line.getBytes("ISO-8859-6"))
                                    .setNewLinesAfter(1)
                                    .build());
                        }

                        printablesRef.set(printables);
                    } catch (Exception e) {
                        Log.e(TAG, "Error preparing print data", e);
                    } finally {
                        prepareLatch.countDown();
                    }
                });

                // Wait for preparation
                prepareLatch.await(5, TimeUnit.SECONDS);

                ArrayList<Printable> printables = printablesRef.get();
                if (printables == null) {
                    Log.e(TAG, "Failed to prepare print data");
                    mainHandler.post(() -> {
                        Toast.makeText(getActivity(), "Failed to prepare print data", Toast.LENGTH_SHORT).show();
                        if (savedPrintCall != null) {
                            savedPrintCall.reject("Failed to prepare print data");
                            savedPrintCall = null;
                        }
                    });
                    return;
                }

                // Actually send the print job on main thread
                Log.d(TAG, "Sending print job");

                final CountDownLatch sendLatch = new CountDownLatch(1);
                final AtomicBoolean sendSuccess = new AtomicBoolean(false);

                mainHandler.post(() -> {
                    try {
                        currentPrinting.print(printables);
                        sendSuccess.set(true);
                    } catch (Exception e) {
                        Log.e(TAG, "Error sending print job", e);
                    } finally {
                        sendLatch.countDown();
                    }
                });

                // Wait for send
                sendLatch.await(5, TimeUnit.SECONDS);

                if (!sendSuccess.get()) {
                    Log.e(TAG, "Failed to send print job");
                    mainHandler.post(() -> {
                        Toast.makeText(getActivity(), "Failed to send print job", Toast.LENGTH_SHORT).show();
                        if (savedPrintCall != null) {
                            savedPrintCall.reject("Failed to send print job");
                            savedPrintCall = null;
                        }
                    });
                    return;
                }

                // Wait for printing to complete with timeout
                boolean completed = printLatch.await(30, TimeUnit.SECONDS);

                if (!completed) {
                    Log.e(TAG, "Print operation timed out");
                    mainHandler.post(() -> {
                        Toast.makeText(getActivity(), "Print operation timed out", Toast.LENGTH_SHORT).show();
                        if (savedPrintCall != null) {
                            savedPrintCall.reject("Print operation timed out");
                            savedPrintCall = null;
                        }
                    });
                    return;
                }

                // Handle result
                if (printSuccess.get()) {
                    mainHandler.post(() -> {
                        Toast.makeText(getActivity(), "Print job completed successfully!", Toast.LENGTH_SHORT).show();
                        if (savedPrintCall != null) {
                            JSObject result = new JSObject();
                            result.put("success", true);
                            savedPrintCall.resolve(result);
                            savedPrintCall = null;
                        }
                    });
                } else {
                    String error = printError.get();
                    mainHandler.post(() -> {
                        Toast.makeText(getActivity(), "Printing failed: " + error, Toast.LENGTH_SHORT).show();
                        if (savedPrintCall != null) {
                            savedPrintCall.reject("Printing failed: " + error);
                            savedPrintCall = null;
                        }
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Exception in printText: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    Toast.makeText(getActivity(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    if (savedPrintCall != null) {
                        savedPrintCall.reject("Error: " + e.getMessage());
                        savedPrintCall = null;
                    }
                });
            }
        });
    }


    private static final UUID CONNECTION_UUID = UUID.fromString("0000110E-0000-1000-8000-00805F9B34FB");

    private static final int REQUEST_BLUETOOTH_PERMISSION = 1;

//    public boolean connectDevice() {
//        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
//                ActivityCompat.checkSelfPermission(this.getActivity(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//
//            ActivityCompat.requestPermissions(this.getActivity(),
//                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
//                    REQUEST_BLUETOOTH_PERMISSION);
//            return false; // Permission not granted yet
//        }
//
//        String d = ActivityCompat.checkSelfPermission(this.getActivity(), Manifest.permission.BLUETOOTH_CONNECT)+" "+ PackageManager.PERMISSION_GRANTED;
//        Log.d(TAG, d);
//
//        try {
//            BluetoothDevice mBluetoothDevice = btAdapter.getRemoteDevice("1A:2F:0A:53:38:4B");
//            BluetoothSocket mBluetoothSocket = mBluetoothDevice.createRfcommSocketToServiceRecord(CONNECTION_UUID);
//
//            mBluetoothSocket.close();
//
//            Thread.sleep(1000);
//            btAdapter.cancelDiscovery();
//            mBluetoothSocket.connect();
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return false;
//        }
//
//        return true;
//        }


    public void releaseBluetoothResources() {
        Log.d(TAG, "Releasing Bluetooth resources");

        // Mark as disconnected
        isConnected.set(false);

        bluetoothExecutor.execute(() -> {
            try {
                // Close any active connections
                if (printing != null) {
                    try {
                        printing = null;
                    } catch (Exception e) {
                        Log.e(TAG, "Error releasing printing resources", e);
                    }
                }

                if (bluetoothGatt != null) {
                    try {
                        if (ActivityCompat.checkSelfPermission(getContext(),
                                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            bluetoothGatt.close();
                        }
                        bluetoothGatt = null;
                    } catch (Exception e) {
                        Log.e(TAG, "Error closing GATT connection", e);
                    }
                }

                // Force a short delay to ensure resources are freed
                Thread.sleep(500);

                // Reset Printooth instance on main thread
                mainHandler.post(() -> {
                    try {
                        if (Printooth.INSTANCE.hasPairedPrinter()) {
                            Printooth.INSTANCE.removeCurrentPrinter();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error resetting Printooth", e);
                    }
                });

                Log.d(TAG, "Bluetooth resources released");
            } catch (Exception e) {
                Log.e(TAG, "Error in releaseBluetoothResources", e);
            }
        });
    }
    /**
     * Retry connection with exponential backoff
     */
    private void retryConnection(String deviceId, String printerName, int attempt) {
        if (attempt > MAX_RETRIES) return;

        // Exponential backoff
        int delay = 1000 * (int)Math.pow(2, attempt-1);

        Log.d(TAG, "Scheduling connection retry " + attempt + " in " + delay + "ms");

        new Handler().postDelayed(() -> {
            // Clean previous resources
            releaseBluetoothResources();

            // Try again with original saved call data
            if (savedPrintCall != null) {
                String dataString = savedPrintCall.getString("data");
                String[] data = dataString != null ? dataString.split("\n") : new String[0];

                // Try again with clean state
                ensureBluetoothConnection(deviceId, printerName, () -> {
                    printText(data, printerName, deviceId);
                });
            }
        }, delay);
    }

    /**
     * Check if all required permissions are granted based on Android version
     */
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

    /**
     * Clean up resources when plugin is destroyed
     */
    @Override
    protected void handleOnDestroy() {
        releaseBluetoothResources();

        // Shutdown executor service
        if (bluetoothExecutor != null) {
            bluetoothExecutor.shutdown();
            try {
                if (!bluetoothExecutor.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                    bluetoothExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                bluetoothExecutor.shutdownNow();
            }
        }

        super.handleOnDestroy();
    }
}