package com.ahmed.plugin.printerbridge;

import android.util.Log;

public class PrinterBridge {

    public String echo(String value) {
        Log.i("Echo", value);
        return value;
    }
}
