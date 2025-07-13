package com.ahmed.plugin.printerbridge.models;

import com.getcapacitor.PluginCall;
import org.json.JSONException;

public class PrintRequest {
    private final String deviceName;
    private final String deviceId;
    private final String[] data;

    public PrintRequest(String deviceName, String deviceId, String[] data) {
        this.deviceName = deviceName;
        this.deviceId = deviceId;
        this.data = data;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String[] getData() {
        return data;
    }

    public static PrintRequest fromPluginCall(PluginCall call) throws JSONException {
        String deviceName = call.getString("deviceName");
        String deviceId = call.getString("deviceId");
        String dataString = call.getString("data"); // Changed back to string

        if (deviceName == null || deviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("deviceName is required.");
        }
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("deviceId is required.");
        }
        if (dataString == null) {
            throw new IllegalArgumentException("data string is required.");
        }

        // Split the data string by newlines, just like in the original code
        String[] data = dataString.split("\n");

        return new PrintRequest(deviceName, deviceId, data);
    }
}