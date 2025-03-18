export interface PrinterBridgePlugin {
  /**
   * Send text data to the printer.
   * @param options.deviceId The Bluetooth MAC address (from @capacitor-community/bluetooth-le)
   * @param options.serviceId Not used in our simple example but can be used for further customization
   * @param options.characteristicId Not used here, but available if needed
   * @param options.data The text you want to print
   */
  print(options: { deviceName: string; deviceId: string; data: string }): Promise<{ success: boolean }>;

  /**
   * Checks if the necessary Bluetooth permissions (e.g. BLUETOOTH_CONNECT) are granted.
   */
  checkPermissions(): Promise<{ permission: string }>;

  /**
   * Requests the necessary Bluetooth permissions.
   */
  requestPermissions(): Promise<{ permission: string }>;
}
