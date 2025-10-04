import { WebPlugin } from '@capacitor/core';

import type { PrinterBridgePlugin, BluetoothDevice } from './definitions';

export class PrinterBridgePluginWeb extends WebPlugin implements PrinterBridgePlugin {
  getPairedDevices(): Promise<{ devices: BluetoothDevice[]; count: number }> {
    console.warn('getPairedDevices is not supported on the web.');
    // Return mock data for testing
    const mockDevices: BluetoothDevice[] = [
      {
        name: 'Mock Printer 1',
        deviceId: '00:11:22:33:44:55',
        isPaired: true,
        bondState: 'BONDED',
        type: 1536,
      },
      {
        name: 'Mock Printer 2',
        deviceId: '00:11:22:33:44:56',
        isPaired: true,
        bondState: 'BONDED',
        type: 1536,
      },
    ];
    return Promise.resolve({ devices: mockDevices, count: mockDevices.length });
  }

  getAvailableDevices(): Promise<{ devices: BluetoothDevice[]; count: number }> {
    console.warn('getAvailableDevices is not supported on the web.');
    // Return mock data for testing (both paired and unpaired devices)
    const mockDevices: BluetoothDevice[] = [
      {
        name: 'Mock Printer 1',
        deviceId: '00:11:22:33:44:55',
        isPaired: true,
        bondState: 'BONDED',
        type: 1536,
      },
      {
        name: 'Mock Printer 2',
        deviceId: '00:11:22:33:44:56',
        isPaired: true,
        bondState: 'BONDED',
        type: 1536,
      },
      {
        name: 'Unpaired Device 1',
        deviceId: '00:11:22:33:44:57',
        isPaired: false,
        bondState: 'NONE',
        type: 1536,
      },
      {
        name: 'Unpaired Device 2',
        deviceId: '00:11:22:33:44:58',
        isPaired: false,
        bondState: 'NONE',
        type: 1536,
      },
    ];
    return Promise.resolve({ devices: mockDevices, count: mockDevices.length });
  }

  pairDevice(options: { deviceAddress: string }): Promise<{ success: boolean; message: string }> {
    console.warn('pairDevice is not supported on the web.');
    console.log('Attempting to pair with device:', options.deviceAddress);
    return Promise.resolve({
      success: true,
      message: `Mock pairing successful with device ${options.deviceAddress}`,
    });
  }

  getDeviceInfo(options: { deviceAddress: string }): Promise<BluetoothDevice> {
    console.warn('getDeviceInfo is not supported on the web.');
    console.log('Getting info for device:', options.deviceAddress);
    const mockDevice: BluetoothDevice = {
      name: 'Mock Device',
      deviceId: options.deviceAddress,
      isPaired: true,
      bondState: 'BONDED',
      deviceClass: 1536,
      majorDeviceClass: 1536,
      type: 1536,
    };
    return Promise.resolve(mockDevice);
  }

  getDeviceIdFromPairedDevices(options: {
    printerName: string;
  }): Promise<{ deviceId: string; deviceName: string; success: boolean }> {
    console.warn('getDeviceIdFromPairedDevices is not supported on the web.');
    console.log('Searching for device:', options.printerName);
    return Promise.resolve({
      deviceId: '00:11:22:33:44:55',
      deviceName: options.printerName,
      success: true,
    });
  }

  async print(options: { deviceName: string; deviceId: string; data: string }): Promise<{ success: boolean }> {
    console.log('PrinterPlugin: printing on web is not supported.');
    console.log('Received data:', options.data);
    return { success: false };
  }

  async checkPermissions(): Promise<{
    bluetooth: 'granted' | 'denied';
    bluetooth_connect: 'granted' | 'denied';
  }> {
    console.warn('checkPermissions is not supported on the web; returning "granted" for testing.');
    return {
      bluetooth: 'granted',
      bluetooth_connect: 'granted',
    };
  }

  async printQRCode(options: { deviceName: string; deviceId: string; qrData: string }): Promise<{ success: boolean }> {
    console.log('PrinterPlugin: printQRCode is not supported on web.');
    console.log('QR Code data:', options.qrData);
    return { success: false };
  }

  async requestPermissions(): Promise<{
    permission: {
      bluetooth: 'granted' | 'denied';
      bluetooth_connect: 'granted' | 'denied';
    };
  }> {
    console.warn('requestPermissions is not supported on the web; returning "granted" for testing.');
    return {
      permission: {
        bluetooth: 'granted',
        bluetooth_connect: 'granted',
      },
    };
  }
}

const PrinterBridge = new PrinterBridgePluginWeb();
export { PrinterBridge };
