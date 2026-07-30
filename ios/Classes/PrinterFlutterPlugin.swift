import Flutter
import UIKit
import CoreBluetooth

public class PrinterFlutterPlugin: NSObject, FlutterPlugin, CBCentralManagerDelegate {
  private var centralManager: CBCentralManager?
  private var bluetoothStateResult: FlutterResult?
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "printer_flutter", binaryMessenger: registrar.messenger())
    let instance = PrinterFlutterPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public override init() {
    super.init()
    centralManager = CBCentralManager(delegate: self, queue: nil, options: [CBCentralManagerOptionShowPowerAlertKey: false])
  }

  public func centralManagerDidUpdateState(_ central: CBCentralManager) {
    if let result = bluetoothStateResult {
      if central.state != .unknown {
        result(central.state == .poweredOn)
        bluetoothStateResult = nil
      }
    }
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "getPlatformVersion":
      result("iOS " + UIDevice.current.systemVersion)
    case "ensureBluetoothIsOn":
      if let manager = centralManager {
        if manager.state == .unknown {
          bluetoothStateResult = result
        } else {
          result(manager.state == .poweredOn)
        }
      } else {
        result(false)
      }
    default:
      result(FlutterMethodNotImplemented)
    }
  }
}
