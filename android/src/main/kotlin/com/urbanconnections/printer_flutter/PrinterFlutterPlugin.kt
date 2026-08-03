package com.urbanconnections.printer_flutter

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.example.tscdll.TSCActivity
import com.example.tscdll.TSCUSBActivity
import com.example.tscdll.TscWifiActivity

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.util.concurrent.Executors

/** PrinterFlutterPlugin */
class PrinterFlutterPlugin: FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.RequestPermissionsResultListener, PluginRegistry.ActivityResultListener {
  private lateinit var channel : MethodChannel
  private val tsc = TSCActivity()
  
  private var activity: Activity? = null
  private var permissionResultCallback: Result? = null
  private val PERMISSION_REQUEST_CODE = 1001

  private var bluetoothEnableResultCallback: Result? = null
  private val BLUETOOTH_ENABLE_REQUEST_CODE = 1002

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
      "ensureBluetoothIsOn" -> {
        handleEnsureBluetoothIsOn(result)
      }
      "requestPermissions" -> {
        handleRequestPermissions(result)
      }
      "openPort" -> {
        val macAddress = call.argument<String>("macAddress") ?: ""
        executeInBackground(result) {
          try {
            // Always attempt to close any existing connection before opening a new one.
            // This prevents the native TSC SDK from crashing (SIGSEGV) if openport is called
            // while a socket is already open.
            try { tsc.closeport() } catch (e: Exception) {}
            
            val res = tsc.openport(macAddress)
            // Sometimes it returns "-1" on failure, we can try to close it just in case
            if (res == "-1") {
              try { tsc.closeport() } catch (e: Exception) {}
            }
            res
          } catch (e: Exception) {
            try { tsc.closeport() } catch (eClose: Exception) {}
            throw e
          }
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
          try { tsc.closeport() } catch (e: Exception) {}
        }
      }
      "printPdf" -> {
        val filePath = call.argument<String>("filePath") ?: ""
        val options = call.argument<Map<String, Any>>("options") ?: emptyMap()
        val copies = call.argument<Int>("copies") ?: 1
        executeInBackground(result) {
          PdfPrintHelper.printPdf(
            filePath = filePath,
            options = options,
            copies = copies,
            sendCommand = { bytes -> tsc.sendcommand(bytes) },
            sendString = { str -> tsc.sendcommand(str) }
          )
          "Success"
        }
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  private fun handleRequestPermissions(result: Result) {
    val currentActivity = activity
    if (currentActivity == null) {
      result.error("NO_ACTIVITY", "Plugin is not attached to an activity.", null)
      return
    }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
      arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val missingPermissions = permissions.filter {
      ContextCompat.checkSelfPermission(currentActivity, it) != PackageManager.PERMISSION_GRANTED
    }.toTypedArray()

    if (missingPermissions.isEmpty()) {
      result.success(true)
    } else {
      permissionResultCallback = result
      ActivityCompat.requestPermissions(currentActivity, missingPermissions, PERMISSION_REQUEST_CODE)
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
    if (requestCode == PERMISSION_REQUEST_CODE) {
      val callback = permissionResultCallback
      permissionResultCallback = null
      
      if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
        callback?.success(true)
      } else {
        callback?.success(false)
      }
      return true
    }
    return false
  }

  private fun handleEnsureBluetoothIsOn(result: Result) {
    val currentActivity = activity
    if (currentActivity == null) {
      result.error("NO_ACTIVITY", "Plugin is not attached to an activity.", null)
      return
    }

    val adapter = BluetoothAdapter.getDefaultAdapter()
    if (adapter == null) {
      result.success(false)
      return
    }

    if (adapter.isEnabled) {
      result.success(true)
    } else {
      bluetoothEnableResultCallback = result
      val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
      currentActivity.startActivityForResult(enableBtIntent, BLUETOOTH_ENABLE_REQUEST_CODE)
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
    if (requestCode == BLUETOOTH_ENABLE_REQUEST_CODE) {
      val callback = bluetoothEnableResultCallback
      bluetoothEnableResultCallback = null
      
      if (resultCode == Activity.RESULT_OK) {
        callback?.success(true)
      } else {
        callback?.success(false)
      }
      return true
    }
    return false
  }

  private fun executeInBackground(result: Result, operation: () -> Any?) {
    executor.execute {
      try {
        val res = operation()
        mainHandler.post {
          val value = if (res is Unit || res == null) "Success" else res
          result.success(value)
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

  // ActivityAware implementation
  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    binding.addRequestPermissionsResultListener(this)
    binding.addActivityResultListener(this)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
    binding.addRequestPermissionsResultListener(this)
    binding.addActivityResultListener(this)
  }

  override fun onDetachedFromActivity() {
    activity = null
  }
}
