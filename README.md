# capacitor-printer-bridge

A Capacitor Plugin For Printing Help

## Install

```bash
npm install capacitor-printer-bridge
npx cap sync
```

## API

<docgen-index>

* [`print(...)`](#print)
* [`printQRCode(...)`](#printqrcode)
* [`checkPermissions()`](#checkpermissions)
* [`requestPermissions()`](#requestpermissions)
* [`getDeviceIdFromPairedDevices(...)`](#getdeviceidfrompaireddevices)
* [`getPairedDevices()`](#getpaireddevices)
* [`getAvailableDevices()`](#getavailabledevices)
* [`pairDevice(...)`](#pairdevice)
* [`getDeviceInfo(...)`](#getdeviceinfo)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### print(...)

```typescript
print(options: { deviceName: string; deviceId: string; data: string; }) => any
```

Send text data to the printer.

| Param         | Type                                                                 |
| ------------- | -------------------------------------------------------------------- |
| **`options`** | <code>{ deviceName: string; deviceId: string; data: string; }</code> |

**Returns:** <code>any</code>

--------------------


### printQRCode(...)

```typescript
printQRCode(options: { deviceName: string; deviceId: string; qrData: string; }) => any
```

Prints a QR Code containing the provided data.

| Param         | Type                                                                   |
| ------------- | ---------------------------------------------------------------------- |
| **`options`** | <code>{ deviceName: string; deviceId: string; qrData: string; }</code> |

**Returns:** <code>any</code>

--------------------


### checkPermissions()

```typescript
checkPermissions() => any
```

Checks if the necessary Bluetooth permissions are granted.

**Returns:** <code>any</code>

--------------------


### requestPermissions()

```typescript
requestPermissions() => any
```

Requests the necessary Bluetooth permissions.

**Returns:** <code>any</code>

--------------------


### getDeviceIdFromPairedDevices(...)

```typescript
getDeviceIdFromPairedDevices(options: { printerName: string; }) => any
```

Gets the device ID (MAC address) of a paired Bluetooth device by its name.

| Param         | Type                                  |
| ------------- | ------------------------------------- |
| **`options`** | <code>{ printerName: string; }</code> |

**Returns:** <code>any</code>

--------------------


### getPairedDevices()

```typescript
getPairedDevices() => any
```

Gets a list of all paired Bluetooth devices.

**Returns:** <code>any</code>

--------------------


### getAvailableDevices()

```typescript
getAvailableDevices() => any
```

Gets a list of all available Bluetooth devices (both paired and unpaired).
This method will start device discovery and may take up to 30 seconds.

**Returns:** <code>any</code>

--------------------


### pairDevice(...)

```typescript
pairDevice(options: { deviceAddress: string; }) => any
```

Pairs with a Bluetooth device using its MAC address.

| Param         | Type                                    |
| ------------- | --------------------------------------- |
| **`options`** | <code>{ deviceAddress: string; }</code> |

**Returns:** <code>any</code>

--------------------


### getDeviceInfo(...)

```typescript
getDeviceInfo(options: { deviceAddress: string; }) => any
```

Gets detailed information about a specific Bluetooth device.

| Param         | Type                                    |
| ------------- | --------------------------------------- |
| **`options`** | <code>{ deviceAddress: string; }</code> |

**Returns:** <code>any</code>

--------------------


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
