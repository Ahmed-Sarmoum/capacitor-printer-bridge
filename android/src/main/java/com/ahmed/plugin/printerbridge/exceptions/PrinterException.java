package com.ahmed.plugin.printerbridge.exceptions;

public class PrinterException extends Exception {
    public PrinterException(String message) {
        super(message);
    }

    public PrinterException(String message, Throwable cause) {
        super(message, cause);
    }
}