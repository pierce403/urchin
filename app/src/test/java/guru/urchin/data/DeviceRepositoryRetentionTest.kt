package guru.urchin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceRepositoryRetentionTest {
  @Test
  fun `TPMS retention is 30 days`() {
    assertEquals(30L, DeviceRepository.retentionDaysForProtocol("tpms"))
  }

  @Test
  fun `POCSAG retention is 30 days`() {
    assertEquals(30L, DeviceRepository.retentionDaysForProtocol("pocsag"))
  }

  @Test
  fun `ADS-B retention is 7 days`() {
    assertEquals(7L, DeviceRepository.retentionDaysForProtocol("adsb"))
  }

  @Test
  fun `P25 retention is 14 days`() {
    assertEquals(14L, DeviceRepository.retentionDaysForProtocol("p25"))
  }

  @Test
  fun `null protocol defaults to 30 days`() {
    assertEquals(30L, DeviceRepository.retentionDaysForProtocol(null))
  }

  @Test
  fun `unknown protocol defaults to 30 days`() {
    assertEquals(30L, DeviceRepository.retentionDaysForProtocol("unknown"))
  }
}
