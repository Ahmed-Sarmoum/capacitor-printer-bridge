import { WebPlugin } from '@capacitor/core';

import type { PrinterBridgePlugin } from './definitions';

export class PrinterBridgePluginWeb extends WebPlugin implements PrinterBridgePlugin {
  async print(options: {
    deviceName: string;
    deviceId: string;
    serviceId?: string;
    characteristicId?: string;
    data: string;
  }): Promise<{ success: boolean }> {
    console.log('PrinterPlugin: printing on web is not supported.');
    console.log('Received data:', options.data);
    return { success: false };
  }

  async checkPermissions(): Promise<{ permission: string }> {
    console.warn('checkPermissions is not supported on the web; returning "granted" for testing.');
    // On web, we cannot really check BLUETOOTH_CONNECT; just return "granted" for testing purposes.
    return { permission: 'granted' };
  }

  async requestPermissions(): Promise<{ permission: string }> {
    console.warn('requestPermissions is not supported on the web; returning "granted" for testing.');
    return { permission: 'granted' };
  }
}

const PrinterBridge = new PrinterBridgePluginWeb();
export { PrinterBridge };
