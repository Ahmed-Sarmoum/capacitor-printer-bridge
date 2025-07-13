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
  checkPermissions(): Promise<{
    permission: {
      bluetooth: 'granted' | 'denied';
      bluetooth_connect: 'granted' | 'denied';
    };
  }>;

  /**
   * Requests the necessary Bluetooth permissions.
   */
  requestPermissions(): Promise<{
    permission: {
      bluetooth: 'granted' | 'denied';
      bluetooth_connect: 'granted' | 'denied';
    };
  }>;

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
    devices: BluetoothDevice[];
    count: number;
  }>;

  /**
   * Gets a list of all available Bluetooth devices (both paired and unpaired).
   * This method will start device discovery and may take up to 30 seconds.
   * @returns Promise with array of all discovered devices
   */
  getAvailableDevices(): Promise<{
    devices: BluetoothDevice[];
    count: number;
  }>;

  /**
   * Pairs with a Bluetooth device using its MAC address.
   * @param options.deviceAddress The Bluetooth MAC address of the device to pair with
   * @returns Promise with pairing result
   */
  pairDevice(options: { deviceAddress: string }): Promise<{
    success: boolean;
    message: string;
  }>;

  /**
   * Gets detailed information about a specific Bluetooth device.
   * @param options.deviceAddress The Bluetooth MAC address of the device
   * @returns Promise with device information
   */
  getDeviceInfo(options: { deviceAddress: string }): Promise<BluetoothDevice>;
}

export interface BluetoothDevice {
  /** The display name of the device */
  name: string;
  /** The Bluetooth MAC address */
  deviceId: string;
  /** Whether the device is currently paired */
  isPaired: boolean;
  /** The bond state as a string (BONDED, BONDING, NONE, UNKNOWN) */
  bondState: string;
  /** The device class code (optional) */
  deviceClass?: number;
  /** The major device class code (optional) */
  majorDeviceClass?: number;
  /** Device type for backward compatibility */
  type?: number;
}
