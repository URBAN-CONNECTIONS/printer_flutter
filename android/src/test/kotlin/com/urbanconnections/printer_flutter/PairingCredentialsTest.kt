package com.urbanconnections.printer_flutter

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pairing values of the call in progress, as the Bluetooth receiver reads and changes them. */
class PairingCredentialsTest {
  private val callA = PairingTarget("AA:AA", pin = "1111", fallbackPin = "2222")
  private val callB = PairingTarget("BB:BB", pin = "3333", fallbackPin = null)

  @Test
  fun beforeAnyCallTheDefaultPinsApplyAndNoPrinterIsExpected() {
    val target = PairingCredentials().read()

    assertNull(target.macAddress)
    assertEquals("0000", target.pin)
    assertEquals("1234", target.fallbackPin)
  }

  @Test
  fun replace_changesMacPinAndFallbackTogether() {
    val credentials = PairingCredentials()
    credentials.replace(callA)
    credentials.replace(callB)

    assertEquals(callB, credentials.read())
  }

  @Test
  fun aReadValueIsNotChangedByALaterReplace() {
    val credentials = PairingCredentials()
    credentials.replace(callA)
    val seen = credentials.read()

    credentials.replace(callB)

    assertEquals(callA, seen) // the event keeps the pin and the fallback of the same call
  }

  @Test
  fun matches_ignoresCaseAndRejectsMissingValues() {
    val target = PairingTarget("AA:BB:CC", pin = "0000", fallbackPin = null)

    assertTrue(target.matches("aa:bb:cc"))
    assertFalse(target.matches("AA:BB:CD"))
    assertFalse(target.matches(null))
    assertFalse(PairingTarget(null, "0000", null).matches("AA:BB:CC"))
  }

  @Test
  fun switchToFallback_usesTheFallbackOnlyOnce() {
    val credentials = PairingCredentials()
    credentials.replace(callA)

    assertEquals("2222", credentials.switchToFallback(credentials.read()))

    val after = credentials.read()
    assertEquals(PairingTarget("AA:AA", pin = "2222", fallbackPin = null), after)
    assertNull(credentials.switchToFallback(after))
  }

  @Test
  fun switchToFallback_doesNothingWithoutAFallbackOrWithTheSamePin() {
    val credentials = PairingCredentials()
    credentials.replace(callB)
    assertNull(credentials.switchToFallback(credentials.read()))

    val samePin = PairingTarget("AA:AA", pin = "1111", fallbackPin = "1111")
    credentials.replace(samePin)
    assertNull(credentials.switchToFallback(credentials.read()))
    assertEquals(samePin, credentials.read())
  }

  @Test
  fun switchToFallback_neverOverwritesTheValuesOfANewerCall() {
    val credentials = PairingCredentials()
    credentials.replace(callA)
    val seenByTheReceiver = credentials.read()

    // The next queued call starts while the receiver is still handling the failure of call A.
    credentials.replace(callB)

    assertNull(credentials.switchToFallback(seenByTheReceiver))
    assertEquals(callB, credentials.read())
  }

  @Test
  fun switchToFallback_withANewerCallThatHasTheSameValuesStillKeepsIt() {
    val credentials = PairingCredentials()
    credentials.replace(callA)
    val seen = credentials.read()
    val newer = callA.copy() // equal values, a different call
    credentials.replace(newer)

    assertNull(credentials.switchToFallback(seen))
    assertEquals(newer, credentials.read())
  }

  @Test
  fun aReaderRacingWithWritersAlwaysSeesTheValuesOfOneCall() {
    val credentials = PairingCredentials()
    val calls = listOf(
      PairingTarget("AA:AA", "pin-a", "fallback-a"),
      PairingTarget("BB:BB", "pin-b", "fallback-b"),
    )
    credentials.replace(calls[0])
    val stop = CountDownLatch(1)
    val writer = thread {
      var i = 0
      while (stop.count > 0) credentials.replace(calls[i++ % 2])
    }
    try {
      repeat(200_000) {
        val seen = credentials.read()
        val owner = calls.single { it.macAddress == seen.macAddress }
        assertEquals(owner, seen)
      }
    } finally {
      stop.countDown()
      writer.join(TimeUnit.SECONDS.toMillis(3))
    }
  }
}
