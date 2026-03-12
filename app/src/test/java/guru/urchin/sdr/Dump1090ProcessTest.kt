package guru.urchin.sdr

import org.junit.Assert.assertEquals
import org.junit.Test

class Dump1090ProcessTest {
  @Test
  fun `usb adsb args use rtlsdr loopback sbs stream`() {
    val args = Dump1090Process.buildArgs(
      binaryPath = "/tmp/libdump1090.so",
      frequencyHz = 1_090_000_000,
      gain = 17,
      sbsPort = 30003
    )

    assertEquals(
      listOf(
        "/tmp/libdump1090.so",
        "--device-type", "rtlsdr",
        "--device", "0",
        "--freq", "1090000000",
        "--net",
        "--net-bind-address", "127.0.0.1",
        "--net-sbs-port", "30003",
        "--quiet",
        "--gain", "17"
      ),
      args
    )
  }
}
