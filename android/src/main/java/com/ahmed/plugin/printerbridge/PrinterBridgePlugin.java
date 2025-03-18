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