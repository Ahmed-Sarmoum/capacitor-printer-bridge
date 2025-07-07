import { WebPlugin } from '@capacitor/core';

import type { PrinterBridgePlugin } from './definitions';

export class PrinterBridgePluginWeb extends WebPlugin implements PrinterBridgePlugin {
  getPairedDevices(): Promise<{ devices: { name: string; deviceId: string; type: number }[]; count: number }> {
    throw new Error('Method not implemented.');
  }

  getDeviceIdFromPairedDevices(): Promise<{ deviceId: string; deviceName: string; success: boolean }> {
    throw new Error('Method not implemented.');
  }

  async print(options: { deviceName: string; deviceId: string; data: string }): Promise<{ success: boolean }> {
    console.log('PrinterPlugin: printing on web is not supported.');
    console.log('Received data:', options.data);
    return { success: false };
  }

  async checkPermissions(): Promise<{ permission: string }> {
    console.warn('checkPermissions is not supported on the web; returning "granted" for testing.');
    return { permission: 'granted' };
  }

  async printQRCode(options: { deviceName: string; deviceId: string; qrData: string }): Promise<{ success: boolean }> {
    console.log('PrinterPlugin: printQRCode is not supported on web.');
    console.log('QR Code data:', options.qrData);
    return { success: false };
  }

  async requestPermissions(): Promise<{ permission: string }> {
    console.warn('requestPermissions is not supported on the web; returning "granted" for testing.');
    return { permission: 'granted' };
  }
}

const PrinterBridge = new PrinterBridgePluginWeb();
export { PrinterBridge };
