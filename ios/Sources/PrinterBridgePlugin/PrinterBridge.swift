import Foundation

@objc public class PrinterBridge: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}
