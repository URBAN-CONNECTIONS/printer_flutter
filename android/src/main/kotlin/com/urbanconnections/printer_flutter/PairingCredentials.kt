package com.urbanconnections.printer_flutter

import java.util.concurrent.atomic.AtomicReference

/**
 * What the pairing receiver needs to answer a pairing request: the printer it expects, the PIN to
 * send and the PIN to try once if that one is refused. The three values belong to one call, so they
 * are kept and replaced together, never one by one.
 */
data class PairingTarget(
  val macAddress: String?,
  val pin: String,
  val fallbackPin: String?,
) {
  /** true when [address] is the printer of this target; the comparison ignores case. */
  fun matches(address: String?): Boolean =
    macAddress != null && address != null && address.equals(macAddress, ignoreCase = true)
}

/**
 * The [PairingTarget] of the call in progress, shared between the job thread or the main thread,
 * which replace it, and the Bluetooth receiver, which reads it.
 *
 * The target is one immutable value in one reference: a replacement is one write and a read is one
 * read, so the receiver can never see the PIN of one call with the fallback PIN of another.
 * Neither the MAC address nor the PINs are ever logged.
 */
class PairingCredentials {
  private val current = AtomicReference(PairingTarget(null, DEFAULT_PIN, DEFAULT_FALLBACK_PIN))

  /** The target as it is now. Read it once per event and use that copy for the whole event. */
  fun read(): PairingTarget = current.get()

  /** Replaces the whole target in one write; used when a call starts. */
  fun replace(target: PairingTarget) {
    current.set(target)
  }

  /**
   * Switches [seen], the target the caller read, to its fallback PIN, once. Returns the fallback
   * PIN when it switched, or null when there is nothing to switch to (no fallback, same PIN as the
   * current one, already used) or when another call replaced the target since [seen] was read: the
   * values of that newer call are never overwritten.
   */
  fun switchToFallback(seen: PairingTarget): String? {
    val fallback = seen.fallbackPin ?: return null
    if (seen.pin == fallback) return null
    return if (current.compareAndSet(seen, seen.copy(pin = fallback, fallbackPin = null))) fallback else null
  }

  companion object {
    const val DEFAULT_PIN = "0000"
    const val DEFAULT_FALLBACK_PIN = "1234"
  }
}
