package guru.urchin.sdr

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoRaWanJsonParserTest {

  /** Build a minimal LoRaWAN uplink PHY payload with the given DevAddr. */
  private fun buildUplink(devAddr: ByteArray, mType: Int = 0x02): String {
    val mhdr = byteArrayOf((mType shl 5).toByte())
    val devAddrLe = devAddr.reversedArray()
    val padding = ByteArray(10)
    val payload = mhdr + devAddrLe + padding
    return Base64.encodeToString(payload, Base64.NO_WRAP)
  }

  @Test
  fun parseValidUnconfirmedUplink() {
    val devAddr = byteArrayOf(0x01, 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte())
    val b64 = buildUplink(devAddr, mType = 0x02)
    val json = """{"type":"lorawan","freq":904.3,"rssi":-65.0,"lsnr":7.2,"datr":"SF7BW125","codr":"4/5","size":15,"stat":1,"data":"$b64"}"""
    val result = LoRaWanJsonParser.parse(json)
    assertNotNull(result)
    assertEquals("01ABCDEF", result!!.devAddr)
    assertEquals("SF7BW125", result.spreadingFactor)
    assertEquals("4/5", result.codingRate)
    assertEquals(15, result.payloadSize)
    assertEquals(true, result.crcOk)
    assertEquals(-65.0, result.rssi!!, 0.01)
    assertEquals(7.2, result.snr!!, 0.01)
    assertEquals(904.3, result.frequencyMhz!!, 0.01)
  }

  @Test
  fun parseValidConfirmedUplink() {
    val devAddr = byteArrayOf(0x02, 0x34, 0x56, 0x78)
    val b64 = buildUplink(devAddr, mType = 0x04)
    val json = """{"type":"lorawan","freq":905.1,"data":"$b64"}"""
    val result = LoRaWanJsonParser.parse(json)
    assertNotNull(result)
    assertEquals("02345678", result!!.devAddr)
  }

  @Test
  fun returnsNullForDownlinkFrame() {
    val devAddr = byteArrayOf(0x01, 0x02, 0x03, 0x04)
    val b64 = buildUplink(devAddr, mType = 0x03) // Unconfirmed Data Down
    val json = """{"type":"lorawan","freq":904.3,"data":"$b64"}"""
    assertNull(LoRaWanJsonParser.parse(json))
  }

  @Test
  fun returnsNullForJoinRequest() {
    val devAddr = byteArrayOf(0x01, 0x02, 0x03, 0x04)
    val b64 = buildUplink(devAddr, mType = 0x00) // Join Request
    val json = """{"type":"lorawan","freq":904.3,"data":"$b64"}"""
    assertNull(LoRaWanJsonParser.parse(json))
  }

  @Test
  fun returnsNullForWrongType() {
    val json = """{"type":"meshtastic","freq":904.3,"data":"QAAAAAAAAAAAAA=="}"""
    assertNull(LoRaWanJsonParser.parse(json))
  }

  @Test
  fun returnsNullForMissingData() {
    val json = """{"type":"lorawan","freq":904.3}"""
    assertNull(LoRaWanJsonParser.parse(json))
  }

  @Test
  fun returnsNullForTooShortPayload() {
    // Only 3 bytes — need at least 5
    val b64 = Base64.encodeToString(byteArrayOf(0x40, 0x01, 0x02), Base64.NO_WRAP)
    val json = """{"type":"lorawan","freq":904.3,"data":"$b64"}"""
    assertNull(LoRaWanJsonParser.parse(json))
  }

  @Test
  fun returnsNullForInvalidBase64() {
    val json = """{"type":"lorawan","freq":904.3,"data":"!!!not-base64!!!"}"""
    assertNull(LoRaWanJsonParser.parse(json))
  }

  @Test
  fun returnsNullForMalformedJson() {
    assertNull(LoRaWanJsonParser.parse("not json at all"))
  }

  @Test
  fun rejectsOversizedJson() {
    val json = """{"type":"lorawan","freq":904.3,"data":"QAAAAAAAAAAAAA==","padding":"${"x".repeat(10_001)}"}"""
    assertNull(LoRaWanJsonParser.parse(json))
  }

  @Test
  fun handlesOptionalFieldsMissing() {
    val devAddr = byteArrayOf(0x01, 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte())
    val b64 = buildUplink(devAddr)
    val json = """{"type":"lorawan","data":"$b64"}"""
    val result = LoRaWanJsonParser.parse(json)
    assertNotNull(result)
    assertNull(result!!.spreadingFactor)
    assertNull(result.codingRate)
    assertNull(result.rssi)
    assertNull(result.frequencyMhz)
  }
}
