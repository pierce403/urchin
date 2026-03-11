package guru.urchin.sdr

import org.junit.Assert.assertEquals
import org.junit.Test

class Rtl433ProcessTest {
  @Test
  fun `rtl sdr usb args use numeric device selector`() {
    val profile = SdrHardwareProfile(
      label = "RTL-SDR",
      usbVendorId = 0x0BDA,
      usbProductId = 0x2838,
      rtl433DeviceArg = "0",
      usesAndroidUsbFdRelay = true
    )

    val args = Rtl433Process.buildArgs(
      binaryPath = "/tmp/librtl_433.so",
      hardwareProfile = profile,
      frequencyHz = 433_920_000,
      gain = null
    )

    assertEquals(
      listOf(
        "/tmp/librtl_433.so",
        "-d", "0",
        "-f", "433920000",
        "-F", "json",
        "-M", "level"
      ),
      args
    )
  }

  @Test
  fun `hackrf args preserve explicit device string and gain`() {
    val profile = SdrHardwareProfile(
      label = "HackRF One",
      usbVendorId = 0x1D50,
      usbProductId = 0x6089,
      rtl433DeviceArg = "driver=hackrf"
    )

    val args = Rtl433Process.buildArgs(
      binaryPath = "/tmp/librtl_433.so",
      hardwareProfile = profile,
      frequencyHz = 433_920_000,
      gain = 17
    )

    assertEquals(
      listOf(
        "/tmp/librtl_433.so",
        "-d", "driver=hackrf",
        "-f", "433920000",
        "-F", "json",
        "-M", "level",
        "-g", "17"
      ),
      args
    )
  }
}
