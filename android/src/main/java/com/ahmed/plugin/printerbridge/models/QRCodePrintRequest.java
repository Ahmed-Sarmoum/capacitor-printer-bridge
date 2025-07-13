package com.ahmed.plugin.printerbridge.models;

import com.getcapacitor.PluginCall;

public class QRCodePrintRequest {
    private final String deviceName;
    private final String deviceId;
    private final String qrData;

    public QRCodePrintRequest(String deviceName, String deviceId, String qrData) {
        this.deviceName = deviceName;
        this.deviceId = deviceId;
        this.qrData = qrData;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getQrData() {
        return qrData;
    }

    public static QRCodePrintRequest fromPluginCall(PluginCall call) {
        String deviceName = call.getString("deviceName");
        String deviceId = call.getString("deviceId");
        String qrData = call.getString("qrData");

        if (deviceName == null || deviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("deviceName is required.");
        }
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("deviceId is required.");
        }
        if (qrData == null || qrData.trim().isEmpty()) {
            throw new IllegalArgumentException("qrData is required.");
        }

        return new QRCodePrintRequest(deviceName, deviceId, qrData);
    }
}


