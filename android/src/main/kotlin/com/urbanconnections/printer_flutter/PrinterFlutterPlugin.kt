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
  // MAC, PIN and fallback PIN of the call in progress, kept as one value. Replaced in one write by
  // openPort (main thread) and by an atomic call (job thread); read once per event by the receiver.
  private val pairing = PairingCredentials()
  private var receiverRegistered = false

  // Single thread executor to run printer jobs off the main thread, one at a time.
  // Every blocking step of a job has a limit, so the queue can never stay stuck.
  private val executor = Executors.newSingleThreadExecutor()
  // Closes the connection of a step that runs past its limit.
  private val watchdog: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  private val mainHandler = Handler(Looper.getMainLooper())

  // The connection and the order of open, jobs, close and cancel.
  private val session = PrinterSession(
    newConnection = { mac -> PrinterConnection(BluetoothSocketTransport(mac), watchdog) },
    onWarning = { Log.w("PrinterFlutterPlugin", it) },
  )

  private val pairingReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      val action = intent.action
      
      val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
      }

      // Another thread can replace the target while the event is handled, so it is read once.
      val target = pairing.read()

      if (device == null || !target.matches(device.address)) return

      if (BluetoothDevice.ACTION_PAIRING_REQUEST == action) {
        val pairingVariant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)
        if (pairingVariant == BluetoothDevice.PAIRING_VARIANT_PIN || pairingVariant == 0) {
          try {
            val pinBytes = target.pin.toByteArray(Charsets.UTF_8)
            device.setPin(pinBytes)
            
            try {
              device.javaClass.getMethod("setPairingConfirmation", Boolean::class.javaPrimitiveType).invoke(device, true)
            } catch (e: Exception) {
              Log.w("PrinterFlutterPlugin", "setPairingConfirmation failed: ${e.message}")
            }
            
            abortBroadcast()
            Log.d("PrinterFlutterPlugin", "Auto-pairing with the configured PIN")
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
          // Only fallback once, and never over the values of a call that started in the meantime.
          if (reason == 1 && pairing.switchToFallback(target) != null) {
            Log.d("PrinterFlutterPlugin", "Pairing failed with the configured PIN, trying the fallback PIN")
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
        val connectTimeoutMs = call.longArgument("connectTimeoutMs", PrinterSession.DEFAULT_CONNECT_TIMEOUT_MS)
        val resetAfterFailure = call.argument<Boolean>("resetPrinterAfterFailure") ?: false

        pairing.replace(
          PairingTarget(
            macAddress = macAddress.uppercase(),
            pin = call.argument<String>("pin") ?: PairingCredentials.DEFAULT_PIN,
            fallbackPin = call.argument<String>("fallbackPin"),
          ),
        )

        executeInBackground(result) {
          session.open(macAddress, connectTimeoutMs, resetAfterFailure)
        }
      }
      "sendTspl" -> {
        val command = call.argument<String>("command") ?: ""
        val closePort = call.argument<Boolean>("closePort") ?: false
        val confirmCompletion = call.argument<Boolean>("confirmCompletion") ?: true
        // Opt-in in the three-step API; an atomic call resolves its own default in executeAtomic.
        val probe = call.argument<Boolean>("probe") ?: false
        val limits = jobLimitsFrom(call)
        val atomicMac = atomicMacAddress(call)
        if (atomicMac != null) {
          // The whole job in one call; closePort has no effect because the call always closes.
          executeAtomic(call, result, atomicMac) { conn, _ ->
            conn.write(command.toByteArray(), limits.writeTimeoutMs)
          }
        } else {
          executeInBackground(result) {
            session.runJob(closePort, limits, confirmCompletion, probe) { conn ->
              conn.write(command.toByteArray(), limits.writeTimeoutMs)
            }
          }
        }
      }
      "sendRawBytes" -> {
        val bytes = call.argument<ByteArray>("bytes")
        val closePort = call.argument<Boolean>("closePort") ?: false
        val confirmCompletion = call.argument<Boolean>("confirmCompletion") ?: true
        // Opt-in in the three-step API; an atomic call resolves its own default in executeAtomic.
        val probe = call.argument<Boolean>("probe") ?: false
        val limits = jobLimitsFrom(call)
        val atomicMac = atomicMacAddress(call)
        if (atomicMac != null && bytes != null) {
          executeAtomic(call, result, atomicMac) { conn, _ -> conn.write(bytes, limits.writeTimeoutMs) }
        } else {
          executeInBackground(result) {
            if (bytes == null) {
              "No bytes provided"
            } else {
              session.runJob(closePort, limits, confirmCompletion, probe) { conn -> conn.write(bytes, limits.writeTimeoutMs) }
            }
          }
        }
      }
      "closePort" -> {
        executeInBackground(result) { session.closePort() }
      }
      "cancel" -> {
        // Runs on the main thread, not on the executor: it must reach a job in progress. It only
        // sets flags; the socket is closed on the watchdog thread, so this never blocks.
        session.cancel()
        result.success("Success")
      }
      "printPdf" -> {
        val filePath = call.argument<String>("filePath") ?: ""
        val options = call.argument<Map<String, Any>>("options") ?: emptyMap()
        val copies = call.argument<Int>("copies") ?: 1
        val closePort = call.argument<Boolean>("closePort") ?: false
        val confirmCompletion = call.argument<Boolean>("confirmCompletion") ?: true
        // Opt-in in the three-step API; an atomic call resolves its own default in executeAtomic.
        val probe = call.argument<Boolean>("probe") ?: false
        val limits = jobLimitsFrom(call)
        val atomicMac = atomicMacAddress(call)
        if (atomicMac != null) {
          executeAtomic(call, result, atomicMac) { conn, phases ->
            PdfPrintHelper.printPdf(
              filePath = filePath,
              options = options,
              copies = copies,
              sendBlock = { bytes -> conn.writeBlock(bytes, limits.writeTimeoutMs) },
              sendString = { str -> conn.write(str.toByteArray(), limits.writeTimeoutMs) },
              checkCancelled = { conn.checkCancelled() },
              phases = phases
            )
          }
        } else {
          executeInBackground(result) {
            session.runJob(closePort, limits, confirmCompletion, probe) { conn ->
              PdfPrintHelper.printPdf(
                filePath = filePath,
                options = options,
                copies = copies,
                sendBlock = { bytes -> conn.writeBlock(bytes, limits.writeTimeoutMs) },
                sendString = { str -> conn.write(str.toByteArray(), limits.writeTimeoutMs) },
                checkCancelled = { conn.checkCancelled() }
              )
            }
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

  /** The `macAddress` of a print call, or null when the call uses the three-step API. */
  private fun atomicMacAddress(call: MethodCall): String? =
    call.argument<String>("macAddress")?.takeIf { it.isNotEmpty() }

  /**
   * Runs a whole job (connect, send, confirm, close) as one task of the job executor, so atomic
   * calls run first-in-first-out. The pairing PIN is set when the task starts, not when the call
   * arrives, so a call waiting in the queue does not change the PIN of the one that is running.
   * When the call ends, in success or failure, the phase times go to the log in one line that
   * holds no MAC address and no PIN.
   */
  private fun executeAtomic(
    call: MethodCall,
    result: Result,
    macAddress: String,
    send: (PrinterConnection, PhaseTimer) -> Unit,
  ) {
    val connectTimeoutMs = call.longArgument("connectTimeoutMs", PrinterSession.DEFAULT_CONNECT_TIMEOUT_MS)
    val confirmCompletion = call.argument<Boolean>("confirmCompletion") ?: true
    val probe = call.argument<Boolean>("probe") ?: true
    val pin = call.argument<String>("pin") ?: PairingCredentials.DEFAULT_PIN
    val fallbackPin = call.argument<String>("fallbackPin")
    val limits = jobLimitsFrom(call)
    val phases = PhaseTimer()

    executeInBackground(result) {
      pairing.replace(PairingTarget(macAddress.uppercase(), pin, fallbackPin))
      try {
        session.runAtomic(macAddress, connectTimeoutMs, confirmCompletion, limits, phases, probe) { conn ->
          send(conn, phases)
        }
      } finally {
        Log.i("PrinterFlutterPlugin", phases.formatLine())
      }
    }
  }

  private fun jobLimitsFrom(call: MethodCall) = JobLimits(
    writeTimeoutMs = call.longArgument("writeTimeoutMs", PrinterSession.DEFAULT_WRITE_TIMEOUT_MS),
    completionTimeoutMs = call.longArgument("completionTimeoutMs", PrinterSession.DEFAULT_COMPLETION_TIMEOUT_MS),
  )

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    session.shutdown()
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
