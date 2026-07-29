package com.urbanconnections.printer_flutter

import android.os.Handler
import android.os.Looper
import androidx.annotation.NonNull

import com.example.tscdll.TSCActivity
import com.example.tscdll.TSCUSBActivity
import com.example.tscdll.TscWifiActivity

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.util.concurrent.Executors

/** PrinterFlutterPlugin */
class PrinterFlutterPlugin: FlutterPlugin, MethodCallHandler {
  private lateinit var channel : MethodChannel
  private val tsc = TSCActivity()
  
  // Single thread executor to run printer commands off the main thread
  private val executor = Executors.newSingleThreadExecutor()
  private val mainHandler = Handler(Looper.getMainLooper())

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "printer_flutter")
    channel.setMethodCallHandler(this)
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      "getPlatformVersion" -> {
        result.success("Android ${android.os.Build.VERSION.RELEASE}")
      }
      "openPort" -> {
        val macAddress = call.argument<String>("macAddress") ?: ""
        executeInBackground(result) {
          tsc.openport(macAddress)
        }
      }
      "sendTspl" -> {
        val command = call.argument<String>("command") ?: ""
        executeInBackground(result) {
          tsc.sendcommand(command)
        }
      }
      "sendRawBytes" -> {
        val bytes = call.argument<ByteArray>("bytes")
        executeInBackground(result) {
          if (bytes != null) {
            tsc.sendcommand(bytes)
            "Success"
          } else {
            "No bytes provided"
          }
        }
      }
      "closePort" -> {
        executeInBackground(result) {
          tsc.closeport()
        }
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  private fun executeInBackground(result: Result, operation: () -> Any?) {
    executor.execute {
      try {
        val res = operation()
        mainHandler.post {
          result.success(res ?: "Success")
        }
      } catch (e: Exception) {
        mainHandler.post {
          result.error("PRINTER_ERROR", e.message, null)
        }
      }
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    executor.shutdown()
  }
}
