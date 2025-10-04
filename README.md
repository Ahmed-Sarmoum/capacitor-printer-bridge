# capacitor-printer-bridge

A Capacitor Plugin For Printing Help

## Install

```bash
npm install capacitor-printer-bridge
npx cap sync
```

## API

<docgen-index>

- [`print(...)`](#print)
- [`printQRCode(...)`](#printqrcode)
- [`checkPermissions()`](#checkpermissions)
- [`requestPermissions()`](#requestpermissions)
- [`getDeviceIdFromPairedDevices(...)`](#getdeviceidfrompaireddevices)
- [`getPairedDevices()`](#getpaireddevices)
- [`getAvailableDevices()`](#getavailabledevices)
- [`pairDevice(...)`](#pairdevice)
- [`getDeviceInfo(...)`](#getdeviceinfo)
- [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### print(...)

```typescript
print(options: { deviceName: string; deviceId: string; data: string; }) => Promise<{ success: boolean; }>
```

Send text data to the printer.

| Param         | Type                                                                 |
| ------------- | -------------------------------------------------------------------- |
| **`options`** | <code>{ deviceName: string; deviceId: string; data: string; }</code> |

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

---

### printQRCode(...)

```typescript
printQRCode(options: { deviceName: string; deviceId: string; qrData: string; }) => Promise<{ success: boolean; }>
```

Prints a QR Code containing the provided data.

| Param         | Type                                                                   |
| ------------- | ---------------------------------------------------------------------- |
| **`options`** | <code>{ deviceName: string; deviceId: string; qrData: string; }</code> |

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

---

### checkPermissions()

```typescript
checkPermissions() => Promise<{ bluetooth: 'granted' | 'denied'; bluetooth_connect: 'granted' | 'denied';  }>
```

Checks if the necessary Bluetooth permissions are granted.

**Returns:** <code>Promise&lt;{ permission: { bluetooth: 'granted' | 'denied'; bluetooth_connect: 'granted' | 'denied'; }; }&gt;</code>

---

### requestPermissions()

```typescript
requestPermissions() => Promise<{ permission: { bluetooth: 'granted' | 'denied'; bluetooth_connect: 'granted' | 'denied'; }; }>
```

Requests the necessary Bluetooth permissions.

**Returns:** <code>Promise&lt;{ permission: { bluetooth: 'granted' | 'denied'; bluetooth_connect: 'granted' | 'denied'; }; }&gt;</code>

---

### getDeviceIdFromPairedDevices(...)

```typescript
getDeviceIdFromPairedDevices(options: { printerName: string; }) => Promise<{ deviceId: string; deviceName: string; success: boolean; }>
```

Gets the device ID (MAC address) of a paired Bluetooth device by its name.

| Param         | Type                                  |
| ------------- | ------------------------------------- |
| **`options`** | <code>{ printerName: string; }</code> |

**Returns:** <code>Promise&lt;{ deviceId: string; deviceName: string; success: boolean; }&gt;</code>

---

### getPairedDevices()

```typescript
getPairedDevices() => Promise<{ devices: BluetoothDevice[]; count: number; }>
```

Gets a list of all paired Bluetooth devices.

**Returns:** <code>Promise&lt;{ devices: BluetoothDevice[]; count: number; }&gt;</code>

---

### getAvailableDevices()

```typescript
getAvailableDevices() => Promise<{ devices: BluetoothDevice[]; count: number; }>
```

Gets a list of all available Bluetooth devices (both paired and unpaired).
This method will start device discovery and may take up to 30 seconds.

**Returns:** <code>Promise&lt;{ devices: BluetoothDevice[]; count: number; }&gt;</code>

---

### pairDevice(...)

```typescript
pairDevice(options: { deviceAddress: string; }) => Promise<{ success: boolean; message: string; }>
```

Pairs with a Bluetooth device using its MAC address.

| Param         | Type                                    |
| ------------- | --------------------------------------- |
| **`options`** | <code>{ deviceAddress: string; }</code> |

**Returns:** <code>Promise&lt;{ success: boolean; message: string; }&gt;</code>

---

### getDeviceInfo(...)

```typescript
getDeviceInfo(options: { deviceAddress: string; }) => Promise<BluetoothDevice>
```

Gets detailed information about a specific Bluetooth device.

| Param         | Type                                    |
| ------------- | --------------------------------------- |
| **`options`** | <code>{ deviceAddress: string; }</code> |

**Returns:** <code>Promise&lt;<a href="#bluetoothdevice">BluetoothDevice</a>&gt;</code>

---

### Interfaces

#### BluetoothDevice

| Prop                   | Type                 | Description                                                 |
| ---------------------- | -------------------- | ----------------------------------------------------------- |
| **`name`**             | <code>string</code>  | The display name of the device                              |
| **`deviceId`**         | <code>string</code>  | The Bluetooth MAC address                                   |
| **`isPaired`**         | <code>boolean</code> | Whether the device is currently paired                      |
| **`bondState`**        | <code>string</code>  | The bond state as a string (BONDED, BONDING, NONE, UNKNOWN) |
| **`deviceClass`**      | <code>number</code>  | The device class code (optional)                            |
| **`majorDeviceClass`** | <code>number</code>  | The major device class code (optional)                      |
| **`type`**             | <code>number</code>  | Device type for backward compatibility                      |

</docgen-api>
