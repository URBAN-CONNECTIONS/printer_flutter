package com.urbanconnections.printer_flutter

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Bluetooth Classic SPP link to a printer, opened with the standard Android API.
 *
 * It replaces the connection of the TSC SDK (`TSCActivity.openport`), which used the same
 * calls: `getRemoteDevice`, `createRfcommSocketToServiceRecord` with the SPP UUID,
 * `cancelDiscovery` and `connect`.
 */
class BluetoothSocketTransport(private val macAddress: String) : PrinterTransport {
  @Volatile private var socket: BluetoothSocket? = null
  @Volatile private var closed = false

  override fun connect() {
    val adapter = BluetoothAdapter.getDefaultAdapter()
      ?: throw IOException("El dispositivo no tiene Bluetooth")
    if (!adapter.isEnabled) throw IOException("El Bluetooth está apagado")

    val device = try {
      adapter.getRemoteDevice(macAddress)
    } catch (e: IllegalArgumentException) {
      throw IOException("Dirección MAC inválida: $macAddress", e)
    }

    val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
    socket = newSocket
    // A close() that arrived while the socket was being created must still apply.
    if (closed) {
      closeQuietly(newSocket)
      throw IOException("La conexión se cerró")
    }
    adapter.cancelDiscovery()
    newSocket.connect()
  }

  override val input: InputStream
    get() = socket?.inputStream ?: throw IOException("Sin conexión")

  override val output: OutputStream
    get() = socket?.outputStream ?: throw IOException("Sin conexión")

  override fun close() {
    closed = true
    socket?.let { closeQuietly(it) }
  }

  private fun closeQuietly(target: BluetoothSocket) {
    try {
      target.close()
    } catch (_: IOException) {
    }
  }

  companion object {
    /** Serial Port Profile, the service thermal printers expose. */
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
  }
}
