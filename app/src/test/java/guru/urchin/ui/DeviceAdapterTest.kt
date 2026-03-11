package guru.urchin.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DeviceAdapterTest {

  @Test
  fun `tpms protocol returns non-null icon resource`() {
    assertNotNull(DeviceAdapter.protocolIconRes("tpms"))
  }

  @Test
  fun `pocsag protocol returns non-null icon resource`() {
    assertNotNull(DeviceAdapter.protocolIconRes("pocsag"))
  }

  @Test
  fun `adsb protocol returns non-null icon resource`() {
    assertNotNull(DeviceAdapter.protocolIconRes("adsb"))
  }

  @Test
  fun `p25 protocol returns non-null icon resource`() {
    assertNotNull(DeviceAdapter.protocolIconRes("p25"))
  }

  @Test
  fun `unknown protocol returns null`() {
    assertNull(DeviceAdapter.protocolIconRes("bluetooth"))
    assertNull(DeviceAdapter.protocolIconRes(""))
    assertNull(DeviceAdapter.protocolIconRes(null))
  }

  @Test
  fun `each protocol maps to a distinct icon resource`() {
    val tpms = DeviceAdapter.protocolIconRes("tpms")!!
    val pocsag = DeviceAdapter.protocolIconRes("pocsag")!!
    val adsb = DeviceAdapter.protocolIconRes("adsb")!!
    val p25 = DeviceAdapter.protocolIconRes("p25")!!
    assertNotEquals(tpms, pocsag)
    assertNotEquals(tpms, adsb)
    assertNotEquals(tpms, p25)
    assertNotEquals(pocsag, adsb)
    assertNotEquals(pocsag, p25)
    assertNotEquals(adsb, p25)
  }
}
