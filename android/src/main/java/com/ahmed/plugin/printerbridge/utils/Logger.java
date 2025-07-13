package com.ahmed.plugin.printerbridge.utils;

import android.util.Log;
public class Logger {
    // You can set this to false to disable all logs for a production release
    private static final boolean IS_LOGGING_ENABLED = true;

    public static void d(String tag, String message) {
        if (IS_LOGGING_ENABLED) {
            Log.d(tag, message);
        }
    }

    public static void w(String tag, String message) {
        if (IS_LOGGING_ENABLED) {
            Log.w(tag, message);
        }
    }

    public static void e(String tag, String message) {
        if (IS_LOGGING_ENABLED) {
            Log.e(tag, message);
        }
    }

    public static void e(String tag, String message, Throwable throwable) {
        if (IS_LOGGING_ENABLED) {
            Log.e(tag, message, throwable);
        }
    }
}
