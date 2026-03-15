package guru.urchin.sdr

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeshtasticJsonParserTest {

  /** Build a Meshtastic payload: dest(4B LE) + sender(4B LE) + packetId(1B) + flags(1B) + padding. */
  private fun buildMeshtasticPayload(
    sender: ByteArray = byteArrayOf(0x1A, 0x2B, 0x3C, 0x4D),
    dest: ByteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
    packetId: Int = 42,
    hopLimit: Int = 3,
    hopStart: Int = 3
  ): String {
    val destLe = dest.reversedArray()
    val senderLe = sender.reversedArray()
    val flags = ((hopStart and 0x07) shl 3) or (hopLimit and 0x07)
    val payload = destLe + senderLe + byteArrayOf(packetId.toByte(), flags.toByte()) + ByteArray(10)
    return Base64.encodeToString(payload, Base64.NO_WRAP)
  }

  @Test
  fun parseValidMeshtasticPacket() {
    val b64 = buildMeshtasticPayload(
      sender = byteArrayOf(0x1A, 0x2B, 0x3C, 0x4D),
      dest = byteArrayOf(0x5E.toByte(), 0x6F.toByte(), 0x70.toByte(), 0x81.toByte()),
      packetId = 42,
      hopLimit = 2,
      hopStart = 3
    )
    val json = """{"type":"meshtastic","freq":906.875,"rssi":-75.0,"lsnr":8.5,"datr":"SF12BW125","codr":"4/5","chan":3,"data":"$b64"}"""
    val result = MeshtasticJsonParser.parse(json)
    assertNotNull(result)
    assertEquals("1A2B3C4D", result!!.nodeId)
    assertEquals("5E6F7081", result.destId)
    assertEquals(42, result.packetId)
    assertEquals(2, result.hopLimit)
    assertEquals(3, result.hopStart)
    assertEquals("ch3", result.channelHash)
    assertEquals(-75.0, result.rssi!!, 0.01)
    assertEquals(8.5, result.snr!!, 0.01)
    assertEquals(906.875, result.frequencyMhz!!, 0.001)
  }

  @Test
  fun returnsNullForWrongType() {
    val b64 = buildMeshtasticPayload()
    val json = """{"type":"lorawan","data":"$b64"}"""
    assertNull(MeshtasticJsonParser.parse(json))
  }

  @Test
  fun returnsNullForMissingData() {
    val json = """{"type":"meshtastic","freq":906.875}"""
    assertNull(MeshtasticJsonParser.parse(json))
  }

  @Test
  fun returnsNullForTooShortPayload() {
    // Only 8 bytes — need at least 10
    val b64 = Base64.encodeToString(ByteArray(8), Base64.NO_WRAP)
    val json = """{"type":"meshtastic","data":"$b64"}"""
    assertNull(MeshtasticJsonParser.parse(json))
  }

  @Test
  fun returnsNullForInvalidBase64() {
    val json = """{"type":"meshtastic","data":"!!!invalid!!!"}"""
    assertNull(MeshtasticJsonParser.parse(json))
  }

  @Test
  fun returnsNullForMalformedJson() {
    assertNull(MeshtasticJsonParser.parse("not json"))
  }

  @Test
  fun rejectsOversizedJson() {
    val json = """{"type":"meshtastic","data":"AAAAAAAAAAAAAAAAAAAAAA==","padding":"${"x".repeat(10_001)}"}"""
    assertNull(MeshtasticJsonParser.parse(json))
  }

  @Test
  fun handlesOptionalFieldsMissing() {
    val b64 = buildMeshtasticPayload()
    val json = """{"type":"meshtastic","data":"$b64"}"""
    val result = MeshtasticJsonParser.parse(json)
    assertNotNull(result)
    assertNull(result!!.channelHash)
    assertNull(result.rssi)
    assertNull(result.snr)
    assertNull(result.frequencyMhz)
  }

  @Test
  fun parsesExactly10BytePayload() {
    // Minimum valid size
    val b64 = Base64.encodeToString(ByteArray(10), Base64.NO_WRAP)
    val json = """{"type":"meshtastic","data":"$b64"}"""
    val result = MeshtasticJsonParser.parse(json)
    assertNotNull(result)
    assertEquals("00000000", result!!.nodeId)
    assertEquals("00000000", result.destId)
  }
}
