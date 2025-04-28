import Foundation
import Capacitor
import CoreBluetooth

// ESC/POS Commands (Helper constants)
struct ESCPOS {
    static let LF: [UInt8] = [0x0A] // Line Feed
    static let CR: [UInt8] = [0x0D] // Carriage Return
    static let ESC: [UInt8] = [0x1B] // Escape
    static let GS: [UInt8] = [0x1D] // Group Separator

    static let InitializePrinter: [UInt8] = [0x1B, 0x40] // ESC @
    static let AlignCenter: [UInt8] = [0x1B, 0x61, 0x01] // ESC a 1
    static let AlignLeft: [UInt8] = [0x1B, 0x61, 0x00] // ESC a 0

    // QR Code Commands (based on common ESC/POS standards)
    static func qrSelectModel(_ model: UInt8 = 0x32) -> [UInt8] { // Model 2 is common
        // GS ( k pL pH cn fn m
        return [0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, model, 0x00]
    }
    static func qrSetSize(_ size: UInt8) -> [UInt8] {
        // GS ( k pL pH cn fn m d1...dk
        return [0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, size]
    }
    static func qrSetErrorLevel(_ level: UInt8) -> [UInt8] { // 0x30 (L), 0x31 (M), 0x32 (Q), 0x33 (H)
        // GS ( k pL pH cn fn m d1...dk
        return [0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, level]
    }
    static func qrStoreData(_ data: Data) -> [UInt8] {
        // GS ( k pL pH cn fn m d1...dk
        let length = data.count + 3
        let pL = UInt8(length & 0xFF)
        let pH = UInt8((length >> 8) & 0xFF)
        var command: [UInt8] = [0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30]
        command.append(contentsOf: [UInt8](data))
        return command
    }
    static let qrPrint: [UInt8] = [0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30] // GS ( k pL pH cn fn m
}


@objc(PrinterBridgePlugin)
public class PrinterBridgePlugin: CAPPlugin, CBCentralManagerDelegate, CBPeripheralDelegate {
    public let identifier = "PrinterBridgePlugin"
    public let jsName = "PrinterBridge"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "print", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "printQRCode", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "checkPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestPermissions", returnType: CAPPluginReturnPromise)
    ]

    private var centralManager: CBCentralManager!
    private var targetPeripheral: CBPeripheral?
    private var writeCharacteristic: CBCharacteristic?
    private var writeType: CBCharacteristicWriteType = .withResponse // Default, might need adjustment

    // Store calls that are waiting for connection/discovery/write
    private var pendingPrintCall: CAPPluginCall?
    private var pendingConnectCall: CAPPluginCall? // For calls waiting just for connection
    private var dataToSend: [Data] = []
    private var dataSendIndex: Int = 0

    // --- Plugin Initialization ---
    override public func load() {
        centralManager = CBCentralManager(delegate: self, queue: nil)
        // Note: PaperDB is Android-specific, no direct equivalent needed here unless
        // you were storing printer info persistently across app launches.
        // Printooth init is also Android-specific. CoreBluetooth setup is done above.
    }

    // --- CoreBluetooth Delegate Methods ---

    // Central Manager Updates
    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        var state: String
        switch central.state {
        case .poweredOn:
            state = "on"
            // If there was a call waiting for Bluetooth to power on, try connecting now
            if let call = pendingConnectCall, let deviceId = call.getString("deviceId") {
                connectToDevice(call: call, deviceId: deviceId)
            }
            return // Don't reject pending calls yet
        case .poweredOff:
            state = "off"
        case .resetting:
            state = "resetting"
        case .unauthorized:
            state = "unauthorized"
        case .unsupported:
            state = "unsupported"
        case .unknown:
            state = "unknown"
        @unknown default:
            state = "unknown"
        }
        CAPLog.print("Bluetooth state changed: \(state)")
        // Reject any pending calls if Bluetooth is not available
        if let call = pendingPrintCall {
            call.reject("Bluetooth is not available. State: \(state)")
            pendingPrintCall = nil
            dataToSend.removeAll()
        }
        if let call = pendingConnectCall {
             call.reject("Bluetooth is not available. State: \(state)")
             pendingConnectCall = nil
        }
        // Clean up connection state
        targetPeripheral = nil
        writeCharacteristic = nil
    }

    // Peripheral Discovery
    public func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        guard let call = pendingConnectCall, let targetDeviceId = call.getString("deviceId") else {
            CAPLog.print("Discovered peripheral \(peripheral.name ?? "Unknown") but no pending call.")
            return
        }

        // Check if UUID matches or if name matches (UUID is preferred)
        if peripheral.identifier.uuidString == targetDeviceId || peripheral.name == targetDeviceId {
            CAPLog.print("Found target peripheral: \(peripheral.name ?? peripheral.identifier.uuidString)")
            centralManager.stopScan()
            targetPeripheral = peripheral
            targetPeripheral?.delegate = self
            centralManager.connect(peripheral, options: nil)
            // pendingConnectCall remains until connection succeeds or fails
        }
    }

    // Connection Events
    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        CAPLog.print("Connected to peripheral: \(peripheral.name ?? peripheral.identifier.uuidString)")
        guard peripheral == targetPeripheral else {
            CAPLog.print("Connected to unexpected peripheral.")
            return
        }
        // Discover services - look for common printer service UUIDs or all services
        // Common UUIDs (examples, might vary): "E7810A71-73AE-499D-8C15-FAA9AEF0C3F2", "49535343-FE7D-4AE5-8FA9-9FAFD205E455"
        // Let's discover all for now, then filter
        peripheral.discoverServices(nil)
    }

    public func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        CAPLog.print("Failed to connect to peripheral: \(peripheral.name ?? peripheral.identifier.uuidString), Error: \(error?.localizedDescription ?? "Unknown error")")
        if let call = pendingConnectCall, peripheral.identifier.uuidString == call.getString("deviceId") || peripheral.name == call.getString("deviceId") {
            call.reject("Failed to connect to printer: \(error?.localizedDescription ?? "Unknown error")")
            pendingConnectCall = nil
        }
        if targetPeripheral == peripheral {
            targetPeripheral = nil
        }
    }

    public func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
         CAPLog.print("Disconnected from peripheral: \(peripheral.name ?? peripheral.identifier.uuidString), Error: \(error?.localizedDescription ?? "Connection lost")")
         if targetPeripheral == peripheral {
             targetPeripheral = nil
             writeCharacteristic = nil
             // If a print was in progress, reject it
             if let call = pendingPrintCall {
                 call.reject("Disconnected during print operation: \(error?.localizedDescription ?? "Connection lost")")
                 pendingPrintCall = nil
                 dataToSend.removeAll()
             }
             // If a connection attempt was pending (less likely here, but possible), reject it
             if let call = pendingConnectCall {
                 call.reject("Disconnected: \(error?.localizedDescription ?? "Connection lost")")
                 pendingConnectCall = nil
             }
         }
    }

    // Service/Characteristic Discovery
    public func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard error == nil else {
            CAPLog.print("Error discovering services: \(error!.localizedDescription)")
            rejectPendingCalls("Error discovering services: \(error!.localizedDescription)")
            centralManager.cancelPeripheralConnection(peripheral)
            return
        }

        guard let services = peripheral.services else {
            rejectPendingCalls("No services found for the peripheral.")
            centralManager.cancelPeripheralConnection(peripheral)
            return
        }

        CAPLog.print("Discovered services for \(peripheral.name ?? peripheral.identifier.uuidString): \(services.map { $0.uuid.uuidString })")

        // Find a service that likely supports writing (e.g., check common printer service UUIDs or look for write properties)
        // This part might need customization based on the target printer.
        // We are looking for a characteristic with write properties (.write or .writeWithoutResponse)
        var foundService = false
        for service in services {
            // Example: Check for a common SPP-like service or a vendor-specific one
            // if service.uuid.uuidString.uppercased() == "E7810A71-73AE-499D-8C15-FAA9AEF0C3F2" {
                CAPLog.print("Discovering characteristics for service \(service.uuid.uuidString)")
                peripheral.discoverCharacteristics(nil, for: service)
                foundService = true
            // }
        }

        if !foundService {
             CAPLog.print("No suitable printing service found. Trying discovery on all services.")
             // Fallback: Discover characteristics for all services if no specific one is targeted
             for service in services {
                 peripheral.discoverCharacteristics(nil, for: service)
             }
             // If services list is not empty, assume we might find something.
             // If services is empty, the guard let services = peripheral.services check already handled it.
        }
    }

    public func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard error == nil else {
            CAPLog.print("Error discovering characteristics for service \(service.uuid.uuidString): \(error!.localizedDescription)")
            // Don't reject yet, maybe another service will work
            return
        }

        guard let characteristics = service.characteristics else {
            CAPLog.print("No characteristics found for service \(service.uuid.uuidString)")
            return
        }

        CAPLog.print("Discovered characteristics for service \(service.uuid.uuidString): \(characteristics.map { $0.uuid.uuidString })")

        // Find a writable characteristic
        for characteristic in characteristics {
            if characteristic.properties.contains(.write) || characteristic.properties.contains(.writeWithoutResponse) {
                CAPLog.print("Found writable characteristic: \(characteristic.uuid.uuidString)")
                writeCharacteristic = characteristic
                // Determine write type (prefer .withResponse if available for reliability, unless printer requires .withoutResponse)
                writeType = characteristic.properties.contains(.write) ? .withResponse : .withoutResponse
                CAPLog.print("Using write type: \(writeType == .withResponse ? "withResponse" : "withoutResponse")")

                // If we were waiting for connection/discovery, resolve the connection call
                if let call = pendingConnectCall {
                    CAPLog.print("Connection and discovery complete.")
                    // Don't resolve yet, let the print method handle sending data
                    // call.resolve() // Or resolve if the goal was just to connect
                    // pendingConnectCall = nil // Keep it if print needs it
                }

                // If data is ready to be sent, start sending
                if pendingPrintCall != nil && !dataToSend.isEmpty {
                    sendNextChunk()
                }
                return // Found characteristic, stop searching in this service
            }
        }
         CAPLog.print("No writable characteristic found in service \(service.uuid.uuidString).")
         // If this was the last service checked and still no characteristic, reject.
         // Need a way to track if all services have been checked.
         // For simplicity now, if we reach here after checking all services (or the target one)
         // and writeCharacteristic is still nil, we should probably reject.
         // This logic needs refinement for robustness.
         // Let's assume for now if we get here and writeCharacteristic is nil after some time, it failed.
         // A timer could be used, or check if this is the last discovered service.
    }

    // Data Writing
    public func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        guard error == nil else {
            CAPLog.print("Error writing value to characteristic \(characteristic.uuid.uuidString): \(error!.localizedDescription)")
            if let call = pendingPrintCall {
                call.reject("Error writing data to printer: \(error!.localizedDescription)")
                pendingPrintCall = nil
                dataToSend.removeAll()
            }
            // Consider disconnecting or retrying?
            return
        }

        CAPLog.print("Successfully wrote value for characteristic \(characteristic.uuid.uuidString).")
        // Continue sending remaining data
        sendNextChunk()
    }

    // --- Helper Methods ---

    private func rejectPendingCalls(_ message: String) {
        if let call = pendingPrintCall {
            call.reject(message)
            pendingPrintCall = nil
            dataToSend.removeAll()
        }
        if let call = pendingConnectCall {
             call.reject(message)
             pendingConnectCall = nil
        }
    }

    private func connectToDevice(call: CAPPluginCall, deviceId: String) {
        guard centralManager.state == .poweredOn else {
            call.reject("Bluetooth is not powered on. State: \(centralManager.state.rawValue)")
            return
        }

        // If already connected to the target device and characteristic found, proceed
        if let connectedPeripheral = targetPeripheral,
           connectedPeripheral.identifier.uuidString == deviceId || connectedPeripheral.name == deviceId,
           connectedPeripheral.state == .connected,
           writeCharacteristic != nil {
            CAPLog.print("Already connected to \(deviceId) and characteristic found.")
            // If it was just a connection call, resolve it. Print calls will proceed.
            if pendingConnectCall === call {
                 // Let the print method handle sending
                 // call.resolve()
                 // pendingConnectCall = nil
            }
            // If data is ready, send it (handles case where print was called while already connected)
            if pendingPrintCall === call && !dataToSend.isEmpty {
                 sendNextChunk()
            } else if pendingPrintCall === call && dataToSend.isEmpty {
                 // This case might happen if print was called with empty data after connection
                 call.resolve()
                 pendingPrintCall = nil
            }
            return
        }

        // If connected but characteristic not found yet, wait (delegate methods will handle it)
        if let connectedPeripheral = targetPeripheral,
           connectedPeripheral.identifier.uuidString == deviceId || connectedPeripheral.name == deviceId,
           connectedPeripheral.state == .connected,
           writeCharacteristic == nil {
            CAPLog.print("Already connected to \(deviceId), waiting for characteristic discovery.")
            // Store the call if it's not already stored
            if pendingPrintCall == nil && pendingConnectCall == nil {
                if !dataToSend.isEmpty { // It's likely a print call
                    pendingPrintCall = call
                } else { // Likely just a connection check or preparation
                    pendingConnectCall = call
                }
            }
            // Trigger discovery again just in case
            connectedPeripheral.discoverServices(nil)
            return
        }

        // If not connected or connected to a different device, start scan/connect
        CAPLog.print("Attempting to connect to device ID: \(deviceId)")
        pendingConnectCall = call // Store the call that initiated the connection

        // Disconnect from any current peripheral first? Optional, depends on desired behavior.
        if let currentPeripheral = targetPeripheral, currentPeripheral.state != .disconnected {
             CAPLog.print("Disconnecting from previous peripheral \(currentPeripheral.name ?? currentPeripheral.identifier.uuidString)")
             centralManager.cancelPeripheralConnection(currentPeripheral)
             // Reset state, connection will be initiated after disconnect or by scanning
             targetPeripheral = nil
             writeCharacteristic = nil
        }


        // Try retrieving known peripherals first (faster if already paired)
        if let uuid = UUID(uuidString: deviceId) {
            let knownPeripherals = centralManager.retrievePeripherals(withIdentifiers: [uuid])
            if let knownPeripheral = knownPeripherals.first {
                CAPLog.print("Found known peripheral: \(knownPeripheral.name ?? knownPeripheral.identifier.uuidString)")
                targetPeripheral = knownPeripheral
                targetPeripheral?.delegate = self
                centralManager.connect(knownPeripheral, options: nil)
                return // Wait for connection delegate methods
            }
        }

        // If not found among known, start scanning
        CAPLog.print("Scanning for peripherals...")
        // Scan for peripherals advertising services (more efficient) or all peripherals
        // centralManager.scanForPeripherals(withServices: [CBUUID(string: "SERVICE_UUID")], options: nil)
        centralManager.scanForPeripherals(withServices: nil, options: nil) // Scan for all

        // Add a timeout for scanning?
        // DispatchQueue.main.asyncAfter(deadline: .now() + 15.0) { [weak self] in
        //     guard let self = self, self.centralManager.isScanning, self.pendingConnectCall === call else { return }
        //     self.centralManager.stopScan()
        //     CAPLog.print("Scan timed out.")
        //     call.reject("Could not find printer: Scan timed out.")
        //     self.pendingConnectCall = nil
        // }
    }

    private func sendData(_ data: Data) {
        guard let peripheral = targetPeripheral, peripheral.state == .connected else {
            rejectPendingCalls("Not connected to a peripheral.")
            return
        }
        guard let characteristic = writeCharacteristic else {
            rejectPendingCalls("No writable characteristic found.")
            // Optional: Trigger discovery again?
            // peripheral.discoverServices(nil)
            return
        }

        CAPLog.print("Writing data (\(data.count) bytes) to characteristic \(characteristic.uuid.uuidString)")
        // Handle potential fragmentation if data is large
        let mtu = peripheral.maximumWriteValueLength(for: writeType)
        if mtu > 0 && data.count > mtu {
             CAPLog.print("Data size (\(data.count)) exceeds MTU (\(mtu)). Sending in chunks.")
             var offset = 0
             while offset < data.count {
                 let chunkSize = min(mtu, data.count - offset)
                 let chunk = data.subdata(in: offset..<offset + chunkSize)
                 CAPLog.print("Writing chunk: \(chunk.count) bytes")
                 peripheral.writeValue(chunk, for: characteristic, type: writeType)
                 offset += chunkSize
                 // If writing with response, wait for didWriteValueFor before sending next chunk?
                 // If writing without response, can send rapidly, but might need delays.
                 if writeType == .withoutResponse {
                     // Add a small delay if needed, printers might get overwhelmed
                     // Thread.sleep(forTimeInterval: 0.02) // 20ms example delay
                 } else {
                     // For .withResponse, the didWriteValueFor callback will trigger the next send via sendNextChunk()
                     return // Exit here, wait for callback
                 }
             }
             // If loop finished (only happens for .withoutResponse), assume all chunks sent
             if writeType == .withoutResponse {
                 CAPLog.print("Finished writing all chunks (without response).")
                 // Consider the job done here for .withoutResponse
                 if let call = pendingPrintCall {
                     call.resolve()
                     pendingPrintCall = nil
                     dataToSend.removeAll()
                 }
             }
        } else {
             // Data fits within MTU or MTU is unknown, send as one piece
             peripheral.writeValue(data, for: characteristic, type: writeType)
             // If writeType is .withoutResponse, assume success immediately?
             if writeType == .withoutResponse {
                 CAPLog.print("Wrote data (without response). Assuming success.")
                 // Continue sending next chunk immediately
                 sendNextChunk()
             }
             // If .withResponse, wait for didWriteValueFor callback
        }
    }

    // Sends the next chunk from the dataToSend array
    private func sendNextChunk() {
        guard pendingPrintCall != nil else {
            // No active print job
            dataToSend.removeAll()
            dataSendIndex = 0
            return
        }

        if dataSendIndex < dataToSend.count {
            let data = dataToSend[dataSendIndex]
            dataSendIndex += 1
            sendData(data) // sendData handles MTU chunking internally if needed for this specific Data object
        } else {
            // Finished sending all data chunks
            CAPLog.print("All data sent successfully.")
            if let call = pendingPrintCall {
                call.resolve()
                pendingPrintCall = nil
            }
            dataToSend.removeAll()
            dataSendIndex = 0
            // Optional: Disconnect after printing?
            // if let peripheral = targetPeripheral {
            //     centralManager.cancelPeripheralConnection(peripheral)
            // }
        }
    }


    // --- Plugin Methods ---

    @objc func print(_ call: CAPPluginCall) {
        guard let dataString = call.getString("data") else {
            call.reject("Missing 'data' argument.")
            return
        }
        guard let deviceId = call.getString("deviceId") else {
            // Use name as fallback? Or require ID? Let's require ID for now.
            call.reject("Missing 'deviceId' argument (peripheral UUID).")
            return
        }
        // let printerName = call.getString("deviceName") // Name is less reliable for connection

        // Prepare data array
        dataToSend.removeAll()
        dataSendIndex = 0

        // Add printer initialization
        dataToSend.append(Data(ESCPOS.InitializePrinter))

        // Add character set command (example: PC850 Multilingual) - Adjust if needed!
        // dataToSend.append(Data([0x1B, 0x74, 0x10])) // ESC t n=16 (PC850)
        // Or try Latin-1 / ISO-8859-1 which is common
        // dataToSend.append(Data([0x1B, 0x74, 0x00])) // ESC t n=0 (PC437 USA)
        // Or ISO-8859-6 for Arabic as used in Android code?
        dataToSend.append(Data([0x1B, 0x74, 28])) // ESC t n=28 (ISO-8859-6 Arabic)


        // Convert lines to data using appropriate encoding
        let lines = dataString.split(separator: "\n")
        for line in lines {
            // Use ISO-8859-6 encoding as per Android code
            if let lineData = String(line).data(using: .isoLatin6) {
                 dataToSend.append(lineData)
                 dataToSend.append(Data(ESCPOS.LF)) // Add line feed after each line
            } else {
                 CAPLog.print("Warning: Could not encode line using ISO-8859-6: \(line)")
                 // Fallback to UTF8 or skip? Let's try UTF8
                 if let fallbackData = String(line).data(using: .utf8) {
                     dataToSend.append(fallbackData)
                     dataToSend.append(Data(ESCPOS.LF))
                 }
            }
        }

        // Add line feeds at the end
        for _ in 0..<3 {
            dataToSend.append(Data(ESCPOS.LF))
        }

        // Cut paper? (Optional, command varies)
        // dataToSend.append(Data([0x1D, 0x56, 0x41, 0x00])) // GS V m=65 n=0 (Partial cut)

        pendingPrintCall = call // Store the call associated with this print job
        connectToDevice(call: call, deviceId: deviceId) // Connect and send data
    }

    @objc func printQRCode(_ call: CAPPluginCall) {
        guard let qrDataString = call.getString("qrData") else {
            call.reject("Missing 'qrData' argument.")
            return
        }
        guard let deviceId = call.getString("deviceId") else {
            call.reject("Missing 'deviceId' argument (peripheral UUID).")
            return
        }

        guard let qrData = qrDataString.data(using: .utf8) else { // Or appropriate encoding if not UTF-8
             call.reject("Could not encode QR data.")
             return
        }

        // Basic validation (similar to Android)
        if qrData.count > 800 { // Adjust limit as needed
            call.reject("QR data is too large (max ~800 bytes).")
            return
        }
        if qrData.count > 200 {
            CAPLog.print("Warning: QR data is long (\(qrData.count) bytes), might cause issues.")
        }


        dataToSend.removeAll()
        dataSendIndex = 0

        // Prepare ESC/POS commands for QR code
        dataToSend.append(Data(ESCPOS.InitializePrinter))
        dataToSend.append(Data(ESCPOS.AlignCenter))

        // --- QR Code Commands ---
        var qrSize: UInt8 = 4
        var errorLevel: UInt8 = 0x31 // 'M'

        if qrData.count > 300 {
            qrSize = 6
            errorLevel = 0x30 // 'L'
        } else if qrData.count > 100 {
            qrSize = 5
        }

        dataToSend.append(Data(ESCPOS.qrSelectModel())) // Use default Model 2
        dataToSend.append(Data(ESCPOS.qrSetSize(qrSize)))
        dataToSend.append(Data(ESCPOS.qrSetErrorLevel(errorLevel)))
        dataToSend.append(Data(ESCPOS.qrStoreData(qrData)))
        dataToSend.append(Data(ESCPOS.qrPrint))
        // --- End QR Code ---

        // Add some spacing
        dataToSend.append(Data(ESCPOS.LF))
        dataToSend.append(Data(ESCPOS.LF))
        dataToSend.append(Data(ESCPOS.AlignLeft)) // Reset alignment
        for _ in 0..<3 {
            dataToSend.append(Data(ESCPOS.LF))
        }

        // Cut paper? (Optional)
        // dataToSend.append(Data([0x1D, 0x56, 0x41, 0x00]))

        pendingPrintCall = call
        connectToDevice(call: call, deviceId: deviceId)
    }

    @objc override public func checkPermissions(_ call: CAPPluginCall) {
        var result: String
        if #available(iOS 13.1, *) {
            switch CBCentralManager.authorization {
            case .allowedAlways:
                result = "granted"
            case .denied:
                result = "denied"
            case .restricted:
                result = "restricted" // System-level restriction
            case .notDetermined:
                result = "prompt" // Permission not yet requested
            @unknown default:
                result = "unknown"
            }
        } else {
            // Fallback for older iOS versions (less specific)
            switch centralManager.state {
            case .unauthorized:
                result = "denied" // Or restricted, can't differentiate easily
            case .poweredOn:
                // If powered on, assume permissions are okay if not explicitly denied.
                // This isn't perfect, as state != .unauthorized doesn't guarantee allowedAlways.
                result = "granted" // Best guess
            case .poweredOff, .resetting, .unsupported, .unknown:
                 // Cannot determine permission status if Bluetooth isn't on/available
                 result = "unknown"
            @unknown default:
                 result = "unknown"
            }
        }
        call.resolve(["bluetooth": result])
    }

    @objc override public func requestPermissions(_ call: CAPPluginCall) {
        // On iOS, you cannot programmatically trigger the permission prompt *except*
        // by actually attempting to use Bluetooth (like scanning).
        // So, this method can either just check the status again, or initiate
        // a brief scan to force the prompt if it's '.notDetermined'.

        if #available(iOS 13.1, *) {
            if CBCentralManager.authorization == .notDetermined {
                CAPLog.print("Bluetooth permission not determined. Initiating scan to trigger prompt.")
                // Start a short scan to trigger the system prompt
                if centralManager.state == .poweredOn {
                    centralManager.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
                    // Stop scan after a short delay
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
                        self?.centralManager.stopScan()
                        CAPLog.print("Stopped scan initiated by requestPermissions.")
                        // Re-check status after attempting to trigger prompt
                        self?.checkPermissions(call)
                    }
                } else {
                    // Bluetooth not on, can't trigger prompt this way.
                    CAPLog.print("Bluetooth not powered on, cannot trigger permission prompt via scan.")
                    // Just report current status
                    checkPermissions(call)
                }
            } else {
                // Permission already determined (allowed, denied, or restricted)
                CAPLog.print("Bluetooth permission already determined.")
                checkPermissions(call)
            }
        } else {
            // Older iOS: No specific authorization property. Rely on state check.
            CAPLog.print("Requesting permissions on older iOS. Relying on state check.")
            checkPermissions(call)
        }
    }
}

// Helper extension for ISO Latin 6 encoding if needed
extension String.Encoding {
    static let isoLatin6 = String.Encoding(rawValue: CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.isoLatin6.rawValue)))}
