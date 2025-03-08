import { WebPlugin } from '@capacitor/core';

import type { PrinterBridgePlugin } from './definitions';

export class PrinterBridgeWeb extends WebPlugin implements PrinterBridgePlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
