import { WebPlugin } from '@capacitor/core';

import type { PrinterBridgePlugin } from './definitions';

export class PrinterBridgePluginWeb extends WebPlugin implements PrinterBridgePlugin {
  async print(options: {
    deviceId: string;
    serviceId?: string;
    characteristicId?: string;
    data: string;
  }): Promise<{ success: boolean }> {
    console.log('PrinterPlugin: printing on web is not supported.');
    console.log('Received data:', options.data);
    return { success: false };
  }
}

const PrinterPlugin = new PrinterBridgePluginWeb();
export { PrinterPlugin };
