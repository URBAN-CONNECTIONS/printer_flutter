package com.urbanconnections.printer_flutter

import android.Manifest
import android.app.Activity
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
class PrinterFlutterPlugin: FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.RequestPermissionsResultListener {
  private lateinit var channel : MethodChannel
  private val tsc = TSCActivity()
  
  private var activity: Activity? = null
  private var permissionResultCallback: Result? = null
  private val PERMISSION_REQUEST_CODE = 1001

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
      "requestPermissions" -> {
        handleRequestPermissions(result)
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

  // ActivityAware implementation
  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    binding.addRequestPermissionsResultListener(this)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
    binding.addRequestPermissionsResultListener(this)
  }

  override fun onDetachedFromActivity() {
    activity = null
  }
}
