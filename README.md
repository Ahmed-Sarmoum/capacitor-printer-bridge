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

--------------------


### printQRCode(...)

```typescript
printQRCode(options: { deviceName: string; deviceId: string; qrData: string; }) => Promise<{ success: boolean; }>
```

Prints a QR Code containing the provided data.

| Param         | Type                                                                   |
| ------------- | ---------------------------------------------------------------------- |
| **`options`** | <code>{ deviceName: string; deviceId: string; qrData: string; }</code> |

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

--------------------


### checkPermissions()

```typescript
checkPermissions() => Promise<{ permission: string; }>
```

Checks if the necessary Bluetooth permissions are granted.

**Returns:** <code>Promise&lt;{ permission: string; }&gt;</code>

--------------------


### requestPermissions()

```typescript
requestPermissions() => Promise<{ permission: string; }>
```

Requests the necessary Bluetooth permissions.

**Returns:** <code>Promise&lt;{ permission: string; }&gt;</code>

--------------------

</docgen-api>
