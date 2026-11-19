package com.urbanconnections.printer_flutter

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/** PrinterFlutterPlugin */
class PrinterFlutterPlugin: FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.RequestPermissionsResultListener, PluginRegistry.ActivityResultListener {
  private lateinit var channel : MethodChannel
  
  private var activity: Activity? = null
  private var permissionResultCallback: Result? = null
  private val PERMISSION_REQUEST_CODE = 1001

  private var bluetoothEnableResultCallback: Result? = null
  private val BLUETOOTH_ENABLE_REQUEST_CODE = 1002

  // Auto-pairing state
  private var targetMacAddress: String? = null
  private var currentPin: String = "0000"
  private var currentFallbackPin: String? = "1234"
  private var receiverRegistered = false

  // Single thread executor to run printer jobs off the main thread, one at a time.
  // Every blocking step of a job has a limit, so the queue can never stay stuck.
  private val executor = Executors.newSingleThreadExecutor()
  // Closes the connection of a step that runs past its limit.
  private val watchdog: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  private val mainHandler = Handler(Looper.getMainLooper())

  // The open connection, read by cancel() from the main thread.
  @Volatile private var connection: PrinterConnection? = null
  // true when the last job hung, was cancelled or failed; the next openPort may reset the printer.
  @Volatile private var lastJobFailed = false

  private val pairingReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      val action = intent.action
      
      val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
      }

      if (device == null || targetMacAddress == null) return
      if (!device.address.equals(targetMacAddress, ignoreCase = true)) return

      if (BluetoothDevice.ACTION_PAIRING_REQUEST == action) {
        val pairingVariant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)
        if (pairingVariant == BluetoothDevice.PAIRING_VARIANT_PIN || pairingVariant == 0) {
          try {
            val pinBytes = currentPin.toByteArray(Charsets.UTF_8)
            device.setPin(pinBytes)
            
            try {
              device.javaClass.getMethod("setPairingConfirmation", Boolean::class.javaPrimitiveType).invoke(device, true)
            } catch (e: Exception) {
              Log.w("PrinterFlutterPlugin", "setPairingConfirmation failed: ${e.message}")
            }
            
            abortBroadcast()
            Log.d("PrinterFlutterPlugin", "Auto-pairing with PIN $currentPin for ${device.address}")
          } catch (e: Exception) {
            Log.e("PrinterFlutterPlugin", "Failed to auto-pair: ${e.message}")
          }
        }
      } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == action) {
        val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
        val previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
        
        if (bondState == BluetoothDevice.BOND_NONE && previousBondState == BluetoothDevice.BOND_BONDING) {
          val reason = intent.getIntExtra("android.bluetooth.device.extra.REASON", -1)
          // 1 is UNBOND_REASON_AUTH_FAILED
          if (reason == 1 && currentFallbackPin != null && currentPin != currentFallbackPin) {
            Log.d("PrinterFlutterPlugin", "Pairing failed with $currentPin, trying fallback $currentFallbackPin")
            currentPin = currentFallbackPin!!
            currentFallbackPin = null // Only fallback once
            try {
               device.javaClass.getMethod("createBond").invoke(device)
            } catch (e: Exception) {
               Log.e("PrinterFlutterPlugin", "Failed to trigger bond for fallback: ${e.message}")
            }
          }
        }
      }
    }
  }

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
        val connectTimeoutMs = call.longArgument("connectTimeoutMs", DEFAULT_CONNECT_TIMEOUT_MS)
        val resetAfterFailure = call.argument<Boolean>("resetPrinterAfterFailure") ?: false

        targetMacAddress = macAddress.uppercase()
        currentPin = call.argument<String>("pin") ?: "0000"
        currentFallbackPin = call.argument<String>("fallbackPin")

        executeInBackground(result) {
          // Always close any existing connection before opening a new one.
          closeConnection()
          try {
            if (resetAfterFailure && lastJobFailed) {
              resetPrinter(macAddress, connectTimeoutMs)
            }
            val newConnection = PrinterConnection(BluetoothSocketTransport(macAddress), watchdog)
            connection = newConnection
            newConnection.open(connectTimeoutMs)
            "Success"
          } catch (e: Throwable) {
            lastJobFailed = true
            closeConnection()
            throw e
          }
        }
      }
      "sendTspl" -> {
        val command = call.argument<String>("command") ?: ""
        val closePort = call.argument<Boolean>("closePort") ?: false
        val limits = JobLimits.from(call)
        executeInBackground(result) {
          runJob(closePort, limits) { conn ->
            conn.write(command.toByteArray(), limits.writeTimeoutMs)
          }
        }
      }
      "sendRawBytes" -> {
        val bytes = call.argument<ByteArray>("bytes")
        val closePort = call.argument<Boolean>("closePort") ?: false
        val limits = JobLimits.from(call)
        executeInBackground(result) {
          if (bytes == null) {
            "No bytes provided"
          } else {
            runJob(closePort, limits) { conn -> conn.write(bytes, limits.writeTimeoutMs) }
          }
        }
      }
      "closePort" -> {
        executeInBackground(result) {
          val current = connection
          if (current != null && current.isOpen) {
            // Without waiting for the completion marker, give the printer time to receive
            // the last bytes before closing, as the TSC SDK did.
            Thread.sleep(CLOSE_GRACE_MS)
          }
          closeConnection()
          "Success"
        }
      }
      "cancel" -> {
        // Runs on the main thread, not on the executor: it must reach a job in progress.
        connection?.cancel()
        result.success("Success")
      }
      "printPdf" -> {
        val filePath = call.argument<String>("filePath") ?: ""
        val options = call.argument<Map<String, Any>>("options") ?: emptyMap()
        val copies = call.argument<Int>("copies") ?: 1
        val closePort = call.argument<Boolean>("closePort") ?: false
        val limits = JobLimits.from(call)
        executeInBackground(result) {
          runJob(closePort, limits) { conn ->
            PdfPrintHelper.printPdf(
              filePath = filePath,
              options = options,
              copies = copies,
              sendCommand = { bytes -> conn.write(bytes, limits.writeTimeoutMs) },
              sendString = { str -> conn.write(str.toByteArray(), limits.writeTimeoutMs) }
            )
          }
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

  /**
   * Sends a job on the open connection. With [closePort], waits for the printer to confirm it
   * processed everything (TSPL `OUT` marker) within the limit, then closes the connection.
   * Any failure closes the connection, so the next job starts on a new one.
   */
  private fun runJob(closePort: Boolean, limits: JobLimits, send: (PrinterConnection) -> Unit): String {
    val conn = connection
    if (conn == null || !conn.isOpen) {
      lastJobFailed = true
      throw PrinterException(PrinterErrorCode.NOT_CONNECTED, "No hay conexión abierta con la impresora; llama a openPort primero")
    }
    try {
      send(conn)
      if (closePort) {
        conn.write(COMPLETION_REQUEST, limits.writeTimeoutMs)
        conn.awaitMarker(COMPLETION_MARKER, limits.completionTimeoutMs)
        closeConnection()
      }
      lastJobFailed = false
      return "Success"
    } catch (e: Throwable) {
      lastJobFailed = true
      closeConnection()
      throw e
    }
  }

  /**
   * Sends `<ESC>!R` so a printer left waiting for the rest of an image starts clean.
   * The reset drops the Bluetooth link, so this waits for the printer to come back before the
   * caller reconnects. Failing to reset is not an error: the caller still tries to connect.
   */
  private fun resetPrinter(macAddress: String, connectTimeoutMs: Long) {
    val resetConnection = PrinterConnection(BluetoothSocketTransport(macAddress), watchdog)
    connection = resetConnection
    try {
      resetConnection.open(connectTimeoutMs)
      resetConnection.write(RESET_COMMAND, DEFAULT_WRITE_TIMEOUT_MS)
    } catch (e: PrinterException) {
      if (e.code == PrinterErrorCode.CANCELLED) throw e
      Log.w("PrinterFlutterPlugin", "Printer reset failed: ${e.message}")
    } finally {
      resetConnection.close()
      connection = null
    }
    Thread.sleep(RESET_SETTLE_MS)
  }

  private fun closeConnection() {
    connection?.close()
    connection = null
  }

  private fun executeInBackground(result: Result, operation: () -> Any?) {
    executor.execute {
      try {
        val res = operation()
        mainHandler.post {
          val value = if (res is Unit || res == null) "Success" else res
          result.success(value)
        }
      } catch (e: PrinterException) {
        mainHandler.post {
          result.error(e.code.name, e.message, null)
        }
      } catch (e: Throwable) {
        // Catch everything (OutOfMemoryError included) so Flutter always gets an answer.
        mainHandler.post {
          result.error("PRINTER_ERROR", e.message ?: e.toString(), null)
        }
      }
    }
  }

  private class JobLimits(val writeTimeoutMs: Long, val completionTimeoutMs: Long) {
    companion object {
      fun from(call: MethodCall) = JobLimits(
        writeTimeoutMs = call.longArgument("writeTimeoutMs", DEFAULT_WRITE_TIMEOUT_MS),
        completionTimeoutMs = call.longArgument("completionTimeoutMs", DEFAULT_COMPLETION_TIMEOUT_MS),
      )
    }
  }

  companion object {
    const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000L
    const val DEFAULT_WRITE_TIMEOUT_MS = 20_000L
    const val DEFAULT_COMPLETION_TIMEOUT_MS = 60_000L
    const val CLOSE_GRACE_MS = 1_500L
    const val RESET_SETTLE_MS = 4_000L

    /** Same bytes the TSC SDK sent: an `OUT` marker plus the `ENDLINE` its reader waited for. */
    val COMPLETION_REQUEST: ByteArray =
      ("OUT \"COMPLETED\\r\\n\"\r\n" + "\r\n" + "OUT \"ENDLINE\"\r\n").toByteArray(Charsets.US_ASCII)
    const val COMPLETION_MARKER = "ENDLINE"

    /** TSPL immediate command `<ESC>!R`: resets the printer and deletes downloaded files. */
    val RESET_COMMAND: ByteArray = byteArrayOf(0x1B, '!'.code.toByte(), 'R'.code.toByte())
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    connection?.cancel()
    executor.shutdown()
    watchdog.shutdown()
  }

  private fun registerReceiver(context: Context) {
    if (!receiverRegistered) {
      val filter = IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
        addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        priority = IntentFilter.SYSTEM_HIGH_PRIORITY
      }
      context.registerReceiver(pairingReceiver, filter)
      receiverRegistered = true
    }
  }

  private fun unregisterReceiver(context: Context) {
    if (receiverRegistered) {
      try {
        context.unregisterReceiver(pairingReceiver)
      } catch (e: Exception) {}
      receiverRegistered = false
    }
  }

  // ActivityAware implementation
  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    binding.addRequestPermissionsResultListener(this)
    binding.addActivityResultListener(this)
    registerReceiver(binding.activity)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity?.let { unregisterReceiver(it) }
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
    binding.addRequestPermissionsResultListener(this)
    binding.addActivityResultListener(this)
    registerReceiver(binding.activity)
  }

  override fun onDetachedFromActivity() {
    activity?.let { unregisterReceiver(it) }
    activity = null
  }
}

/** Reads a numeric argument sent from Dart as int or long. */
private fun MethodCall.longArgument(name: String, default: Long): Long =
  (argument<Any>(name) as? Number)?.toLong() ?: default
