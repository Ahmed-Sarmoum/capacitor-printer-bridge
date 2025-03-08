// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapacitorPrinterBridge",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "CapacitorPrinterBridge",
            targets: ["PrinterBridgePlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0")
    ],
    targets: [
        .target(
            name: "PrinterBridgePlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/PrinterBridgePlugin"),
        .testTarget(
            name: "PrinterBridgePluginTests",
            dependencies: ["PrinterBridgePlugin"],
            path: "ios/Tests/PrinterBridgePluginTests")
    ]
)