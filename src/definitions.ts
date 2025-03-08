export interface PrinterBridgePlugin {
  /**
   * Send text data to the printer.
   * @param options.deviceId The Bluetooth MAC address (from @capacitor-community/bluetooth-le)
   * @param options.serviceId Not used in our simple example but can be used for further customization
   * @param options.characteristicId Not used here, but available if needed
   * @param options.data The text you want to print
   */
  print(options: {
    deviceId: string;
    serviceId?: string;
    characteristicId?: string;
    data: string;
  }): Promise<{ success: boolean }>;
}
