export interface PrinterBridgePlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
