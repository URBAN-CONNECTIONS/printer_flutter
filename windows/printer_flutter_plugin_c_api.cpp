#include "include/printer_flutter/printer_flutter_plugin_c_api.h"

#include <flutter/plugin_registrar_windows.h>

#include "printer_flutter_plugin.h"

void PrinterFlutterPluginCApiRegisterWithRegistrar(
    FlutterDesktopPluginRegistrarRef registrar) {
  printer_flutter::PrinterFlutterPlugin::RegisterWithRegistrar(
      flutter::PluginRegistrarManager::GetInstance()
          ->GetRegistrar<flutter::PluginRegistrarWindows>(registrar));
}
