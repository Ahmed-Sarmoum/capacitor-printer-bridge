export interface PrinterBridgePlugin {
  /**
   * Send text data to the printer.
   * @param options.deviceName The name of the paired Bluetooth device
   * @param options.deviceId The Bluetooth MAC address
   * @param options.data The plain text string to print (newline-separated if needed)
   */
  print(options: { deviceName: string; deviceId: string; data: string }): Promise<{ success: boolean }>;

  /**
   * Prints a QR Code containing the provided data.
   * @param options.deviceName The name of the paired Bluetooth device
   * @param options.deviceId The Bluetooth MAC address
   * @param options.qrData The data to encode inside the QR code
   */
  printQRCode(options: { deviceName: string; deviceId: string; qrData: string }): Promise<{ success: boolean }>;

  /**
   * Checks if the necessary Bluetooth permissions are granted.
   */
  checkPermissions(): Promise<{ permission: string }>;

  /**
   * Requests the necessary Bluetooth permissions.
   */
  requestPermissions(): Promise<{ permission: string }>;

  /**
   * Gets the device ID (MAC address) of a paired Bluetooth device by its name.
   * @param options.printerName The name of the paired Bluetooth printer to search for
   * @returns Promise with device information if found
   */
  getDeviceIdFromPairedDevices(options: { printerName: string }): Promise<{
    deviceId: string;
    deviceName: string;
    success: boolean;
  }>;

  /**
   * Gets a list of all paired Bluetooth devices.
   * @returns Promise with array of paired devices
   */
  getPairedDevices(): Promise<{
    devices: {
      name: string;
      deviceId: string;
      type: number;
    }[];
    count: number;
  }>;
}
