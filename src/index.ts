import { registerPlugin } from '@capacitor/core';

import type { PrinterBridgePlugin } from './definitions';

const PrinterBridge = registerPlugin<PrinterBridgePlugin>('PrinterBridge', {
  web: () => import('./web').then((m) => new m.PrinterBridgeWeb()),
});

export * from './definitions';
export { PrinterBridge };
