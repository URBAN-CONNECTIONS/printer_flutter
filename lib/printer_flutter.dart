
import 'printer_flutter_platform_interface.dart';

class PrinterFlutter {
  Future<String?> getPlatformVersion() {
    return PrinterFlutterPlatform.instance.getPlatformVersion();
  }
}
