package com.ahmed.plugin.printerbridge.services;

import com.ahmed.plugin.printerbridge.exceptions.PrinterException;
import com.ahmed.plugin.printerbridge.models.PrintRequest;
import com.ahmed.plugin.printerbridge.models.QRCodePrintRequest;
import com.ahmed.plugin.printerbridge.utils.Logger;
import com.mazenrashed.printooth.Printooth;
import com.mazenrashed.printooth.data.printable.Printable;
import com.mazenrashed.printooth.data.printable.RawPrintable;
import com.mazenrashed.printooth.utilities.Printing;
import com.mazenrashed.printooth.utilities.PrintingCallback;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PrinterService {
    private static final String TAG = "PrinterService";
    private static final int CONNECTION_TIMEOUT_SECONDS = 20;
    private static final int MAX_RETRY_ATTEMPTS = 2;
    private static final long RETRY_DELAY_MS = 1500;

    private final BluetoothService bluetoothService;
    private final ReentrantReadWriteLock connectionLock = new ReentrantReadWriteLock();

    // Connection management
    private final ConcurrentHashMap<String, PrinterConnection> printerConnections = new ConcurrentHashMap<>();
    private final AtomicBoolean isPrinting = new AtomicBoolean(false);
    private String currentPrinterKey;
    private Printing printing;

    // Helper class for printer connection state
    private static class PrinterConnection {
        final String deviceName;
        final String deviceId;
        final boolean isConnected;
        final long lastUsed;

        PrinterConnection(String deviceName, String deviceId, boolean isConnected) {
            this.deviceName = deviceName;
            this.deviceId = deviceId;
            this.isConnected = isConnected;
            this.lastUsed = System.currentTimeMillis();
        }
    }

    // Helper class for QR Code settings
    private static class QRSettings {
        final int size;
        final int errorCorrectionLevel;

        QRSettings(int size, int errorCorrectionLevel) {
            this.size = size;
            this.errorCorrectionLevel = errorCorrectionLevel;
        }
    }

    public PrinterService(BluetoothService bluetoothService) {
        this.bluetoothService = bluetoothService;
    }

    public void printText(PrintRequest request) throws PrinterException {
        if (!isPrinting.compareAndSet(false, true)) {
            throw new PrinterException("Another print operation is already in progress.");
        }
        try {
            bluetoothService.validateBluetoothState();
            setupPrinterConnection(request.getDeviceName(), request.getDeviceId());
            ArrayList<Printable> printables = createTextPrintables(request);
            executePrintJob(printables);
        } finally {
            isPrinting.set(false);
        }
    }

    public void printQRCode(QRCodePrintRequest request) throws PrinterException {
        if (!isPrinting.compareAndSet(false, true)) {
            throw new PrinterException("Another print operation is already in progress.");
        }
        try {
            bluetoothService.validateBluetoothState();
            validateQRCodeData(request.getQrData());
            setupPrinterConnection(request.getDeviceName(), request.getDeviceId());
            ArrayList<Printable> printables = createQRCodePrintables(request);
            executePrintJob(printables);
        } finally {
            isPrinting.set(false);
        }
    }

    private void setupPrinterConnection(String deviceName, String deviceId) throws PrinterException {
        if (deviceName == null || deviceName.trim().isEmpty() || deviceId == null || deviceId.trim().isEmpty()) {
            throw new PrinterException("Device name and ID are required.");
        }

        String printerKey = deviceId + ":" + deviceName;

        connectionLock.writeLock().lock();
        try {
            // Check if we're already connected to this printer
            if (printerKey.equals(currentPrinterKey) && printing != null) {
                Logger.d(TAG, "Already connected to printer: " + deviceName);
                return;
            }

            // Disconnect from current printer if different
            if (currentPrinterKey != null && !printerKey.equals(currentPrinterKey)) {
                Logger.d(TAG, "Switching from " + currentPrinterKey + " to " + printerKey);
                disconnectCurrentPrinter();
            }

            // Setup new printer connection
            try {
                Printooth.INSTANCE.setPrinter(deviceName, deviceId);
                printing = Printooth.INSTANCE.printer();

                if (printing == null) {
                    throw new PrinterException("Failed to initialize printer with Printooth.");
                }

                currentPrinterKey = printerKey;
                printerConnections.put(printerKey, new PrinterConnection(deviceName, deviceId, true));

                Logger.d(TAG, "Printer connection established: " + deviceName + " [" + deviceId + "]");

            } catch (Exception e) {
                throw new PrinterException("Failed to setup printer connection: " + e.getMessage());
            }
        } finally {
            connectionLock.writeLock().unlock();
        }
    }

    private void disconnectCurrentPrinter() {
        try {
            if (printing != null) {
                printing.setPrintingCallback(null);
                printing = null;
            }

            if (currentPrinterKey != null) {
                PrinterConnection connection = printerConnections.get(currentPrinterKey);
                if (connection != null) {
                    printerConnections.put(currentPrinterKey,
                            new PrinterConnection(connection.deviceName, connection.deviceId, false));
                }
            }

            // Clear current printer from Printooth
            Printooth.INSTANCE.removeCurrentPrinter();
            currentPrinterKey = null;

            Logger.d(TAG, "Disconnected from current printer");
        } catch (Exception e) {
            Logger.e(TAG, "Error during printer disconnection", e);
        }
    }

    private void executePrintJob(ArrayList<Printable> printables) throws PrinterException {
        if (printing == null) {
            throw new PrinterException("Printer not initialized. Call setupPrinterConnection first.");
        }

        PrinterException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<PrinterException> error = new AtomicReference<>();
            final AtomicBoolean canRetry = new AtomicBoolean(false);

            printing.setPrintingCallback(createPrintingCallback(latch, error, canRetry, attempt));

            try {
                printing.print(printables);
                if (!latch.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new PrinterException("Print operation timed out after " + CONNECTION_TIMEOUT_SECONDS + " seconds.");
                }

                if (error.get() == null) {
                    Logger.d(TAG, "Printing successful on attempt " + attempt);
                    return; // Success
                }

                lastException = error.get();
                if (canRetry.get() && attempt < MAX_RETRY_ATTEMPTS) {
                    Logger.w(TAG, "Attempt " + attempt + " failed. Retrying in " + RETRY_DELAY_MS + "ms...");
                    Thread.sleep(RETRY_DELAY_MS);
                } else {
                    throw lastException; // Non-retriable error or max retries reached
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PrinterException("Print operation was interrupted.");
            } catch (Exception e) {
                throw new PrinterException("An unexpected error occurred during printing: " + e.getMessage());
            }
        }
        throw lastException != null ? lastException : new PrinterException("Print job failed after all retries.");
    }

    private PrintingCallback createPrintingCallback(CountDownLatch latch, AtomicReference<PrinterException> error, AtomicBoolean canRetry, int attempt) {
        return new PrintingCallback() {
            @Override
            public void connectingWithPrinter() {
                Logger.d(TAG, "Connecting to printer... (Attempt " + attempt + ")");
            }

            @Override
            public void connectionFailed(String message) {
                Logger.e(TAG, "Connection failed: " + message);
                error.set(new PrinterException("Connection Failed: " + message));
                canRetry.set(true);
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                Logger.e(TAG, "Printer error: " + message);
                error.set(new PrinterException("Printer Error: " + message));
                canRetry.set(false); // Assume printer errors are not recoverable by retrying
                latch.countDown();
            }

            @Override
            public void onMessage(String message) {
                Logger.d(TAG, "Printer message: " + message);
            }

            @Override
            public void printingOrderSentSuccessfully() {
                Logger.d(TAG, "Print job sent to printer successfully.");
                latch.countDown();
            }

            @Override
            public void disconnected() {
                Logger.d(TAG, "Disconnected from printer.");
                // Update connection state
                if (currentPrinterKey != null) {
                    PrinterConnection connection = printerConnections.get(currentPrinterKey);
                    if (connection != null) {
                        printerConnections.put(currentPrinterKey,
                                new PrinterConnection(connection.deviceName, connection.deviceId, false));
                    }
                }
            }
        };
    }

    public void disconnectPrinter(String deviceId) throws PrinterException {
        connectionLock.writeLock().lock();
        try {
            if (currentPrinterKey != null && currentPrinterKey.startsWith(deviceId + ":")) {
                disconnectCurrentPrinter();
                Logger.d(TAG, "Disconnected from printer: " + deviceId);
            } else {
                Logger.w(TAG, "Printer not connected or different device: " + deviceId);
            }
        } finally {
            connectionLock.writeLock().unlock();
        }
    }

    public boolean isConnected(String deviceId) {
        connectionLock.readLock().lock();
        try {
            if (currentPrinterKey != null && currentPrinterKey.startsWith(deviceId + ":")) {
                PrinterConnection connection = printerConnections.get(currentPrinterKey);
                return connection != null && connection.isConnected;
            }
            return false;
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    private ArrayList<Printable> createTextPrintables(PrintRequest request) throws PrinterException {
        try {
            ArrayList<Printable> printables = new ArrayList<>();

            // Initialize printer (same as original)
            printables.add(new RawPrintable.Builder(new byte[] { 0x1B, 0x40 }).build());

            // Set character code table for Arabic support (same as original)
            printables.add(new RawPrintable.Builder(new byte[] { 0x1B, 0x74, 28 }).build());

            // Process each line of text - using ISO-8859-6 encoding like in the original
            for (String line : request.getData()) {
                if (line != null) { // Remove the trim().isEmpty() check to match original behavior
                    try {
                        // Using ISO-8859-6 encoding for Arabic support (same as original)
                        byte[] textBytes = line.getBytes("ISO-8859-6");
                        printables.add(new RawPrintable.Builder(textBytes).setNewLinesAfter(1).build());
                    } catch (UnsupportedEncodingException e) {
                        // Fallback to UTF-8 if ISO-8859-6 is not available
                        byte[] textBytes = line.getBytes("UTF-8");
                        printables.add(new RawPrintable.Builder(textBytes).setNewLinesAfter(1).build());
                    }
                }
            }

            // Add line feeds at the end (same as original)
            addLineFeed(3, printables);
            return printables;
        } catch (UnsupportedEncodingException e) {
            throw new PrinterException("Failed to encode text for printing: " + e.getMessage());
        }
    }

    // Updated lineFeed method to match original implementation
    private void addLineFeed(int lines, ArrayList<Printable> printables) {
        for (int i = 0; i < lines; i++) {
            // Using 0x0D, 0x0A (CR+LF) like in the original code
            printables.add(new RawPrintable.Builder(new byte[] { 0x0D, 0x0A }).build());
        }
    }

    private ArrayList<Printable> createQRCodePrintables(QRCodePrintRequest request) throws PrinterException {
        try {
            ArrayList<Printable> printables = new ArrayList<>();
            byte[] qrBytes = request.getQrData().getBytes("UTF-8");
            QRSettings settings = determineQRSettings(qrBytes.length);

            // Initialize printer
            printables.add(new RawPrintable.Builder(new byte[] { 0x1B, 0x40 }).build());

            // Set center alignment
            printables.add(new RawPrintable.Builder(new byte[] { 0x1B, 0x61, 0x01 }).build());

            // QR Code ESC/POS commands
            // Select QR Code model (Model 2)
            printables.add(new RawPrintable.Builder(new byte[] { 0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00 }).build());

            // Set module size
            printables.add(new RawPrintable.Builder(new byte[] { 0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, (byte) settings.size }).build());

            // Set error correction level
            printables.add(new RawPrintable.Builder(new byte[] { 0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, (byte) settings.errorCorrectionLevel }).build());

            // Store QR code data
            int len = qrBytes.length + 3;
            byte pL = (byte) (len % 256);
            byte pH = (byte) (len / 256);

            // Build the store data command
            byte[] storeCommand = new byte[qrBytes.length + 8];
            storeCommand[0] = 0x1D;
            storeCommand[1] = 0x28;
            storeCommand[2] = 0x6B;
            storeCommand[3] = pL;
            storeCommand[4] = pH;
            storeCommand[5] = 0x31;
            storeCommand[6] = 0x50;
            storeCommand[7] = 0x30;
            System.arraycopy(qrBytes, 0, storeCommand, 8, qrBytes.length);

            printables.add(new RawPrintable.Builder(storeCommand).build());

            // Print the QR code
            printables.add(new RawPrintable.Builder(new byte[] { 0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30 }).build());

            // Reset alignment to left
            printables.add(new RawPrintable.Builder(new byte[] { 0x1B, 0x61, 0x00 }).build());

            // Add line feeds
            addLineFeed(2, printables);

            return printables;
        } catch (UnsupportedEncodingException e) {
            throw new PrinterException("Failed to encode QR code data: " + e.getMessage());
        }
    }

    private void validateQRCodeData(String qrData) throws PrinterException {
        if (qrData == null || qrData.trim().isEmpty()) {
            throw new PrinterException("QR code data cannot be null or empty.");
        }

        // Check for maximum QR code data length (typical limit is around 2953 bytes for alphanumeric)
        if (qrData.length() > 2900) {
            throw new PrinterException("QR code data is too long. Maximum length is approximately 2900 characters.");
        }
    }

    private QRSettings determineQRSettings(int dataLength) {
        // Determine appropriate QR code size and error correction based on data length
        int size = 6; // Default module size
        int errorCorrectionLevel = 48; // Default error correction level (L = 48, M = 49, Q = 50, H = 51)

        if (dataLength > 1000) {
            size = 4; // Smaller size for longer data
            errorCorrectionLevel = 48; // Lower error correction for more data capacity
        } else if (dataLength > 500) {
            size = 5;
            errorCorrectionLevel = 49; // Medium error correction
        } else {
            size = 6;
            errorCorrectionLevel = 50; // Higher error correction for short data
        }

        return new QRSettings(size, errorCorrectionLevel);
    }

    // Additional utility methods
    public void feedPaper(int lines) throws PrinterException {
        if (!isPrinting.compareAndSet(false, true)) {
            throw new PrinterException("Another print operation is already in progress.");
        }
        try {
            if (printing == null) {
                throw new PrinterException("No printer connected. Please connect to a printer first.");
            }

            ArrayList<Printable> printables = new ArrayList<>();
            addLineFeed(lines, printables);
            executePrintJob(printables);
        } finally {
            isPrinting.set(false);
        }
    }

    public void cutPaper() throws PrinterException {
        if (!isPrinting.compareAndSet(false, true)) {
            throw new PrinterException("Another print operation is already in progress.");
        }
        try {
            if (printing == null) {
                throw new PrinterException("No printer connected. Please connect to a printer first.");
            }

            ArrayList<Printable> printables = new ArrayList<>();
            // Full cut command
            printables.add(new RawPrintable.Builder(new byte[] { 0x1D, 0x56, 0x00 }).build());
            executePrintJob(printables);
        } finally {
            isPrinting.set(false);
        }
    }

    public void partialCutPaper() throws PrinterException {
        if (!isPrinting.compareAndSet(false, true)) {
            throw new PrinterException("Another print operation is already in progress.");
        }
        try {
            if (printing == null) {
                throw new PrinterException("No printer connected. Please connect to a printer first.");
            }

            ArrayList<Printable> printables = new ArrayList<>();
            // Partial cut command
            printables.add(new RawPrintable.Builder(new byte[] { 0x1D, 0x56, 0x01 }).build());
            executePrintJob(printables);
        } finally {
            isPrinting.set(false);
        }
    }

    public void openCashDrawer() throws PrinterException {
        if (!isPrinting.compareAndSet(false, true)) {
            throw new PrinterException("Another print operation is already in progress.");
        }
        try {
            if (printing == null) {
                throw new PrinterException("No printer connected. Please connect to a printer first.");
            }

            ArrayList<Printable> printables = new ArrayList<>();
            // Cash drawer kick command (standard ESC/POS)
            printables.add(new RawPrintable.Builder(new byte[] { 0x1B, 0x70, 0x00, 0x19, (byte) 0xFA }).build());
            executePrintJob(printables);
        } finally {
            isPrinting.set(false);
        }
    }

    public void cleanup() {
        connectionLock.writeLock().lock();
        try {
            if (currentPrinterKey != null) {
                disconnectCurrentPrinter();
            }
            printerConnections.clear();
            isPrinting.set(false);
        } finally {
            connectionLock.writeLock().unlock();
        }
        Logger.d(TAG, "Printer service cleaned up.");
    }
}