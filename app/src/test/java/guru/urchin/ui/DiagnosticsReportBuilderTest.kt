package guru.urchin.ui

import guru.urchin.scan.ScanDiagnosticsSnapshot
import guru.urchin.sdr.SdrState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportBuilderTest {

  @Test
  fun `idle usb state shows usb connection guidance`() {
    val report = build(SdrState.Idle, snapshot(sourceLabel = "usb"))
    assertTrue(report.contains("USB mode is selected"))
    assertTrue(report.contains("RTL-SDR"))
    assertTrue(report.contains("HackRF"))
  }

  @Test
  fun `idle network state shows network guidance`() {
    val report = build(SdrState.Idle, snapshot(sourceLabel = "network"))
    assertTrue(report.contains("Network mode is selected"))
    assertTrue(report.contains("rtl_433"))
  }

  @Test
  fun `usb not connected shows plug-in instructions`() {
    val report = build(SdrState.UsbNotConnected, snapshot())
    assertTrue(report.contains("No supported USB SDR was detected"))
    assertTrue(report.contains("USB OTG"))
  }

  @Test
  fun `usb permission denied shows retry instructions`() {
    val report = build(SdrState.UsbPermissionDenied, snapshot())
    assertTrue(report.contains("USB access was denied"))
    assertTrue(report.contains("Retry capture"))
  }

  @Test
  fun `error state with binary hint shows setup guide reference`() {
    val report = build(SdrState.Error("binary not found"), snapshot())
    assertTrue(report.contains("rtl_433 Android setup guide"))
  }

  @Test
  fun `error state with connection refusal shows network hint`() {
    val report = build(SdrState.Error("Connection refused"), snapshot())
    assertTrue(report.contains("network host and port"))
  }

  @Test
  fun `scanning with hardware label shows device info`() {
    val report = build(SdrState.Scanning, snapshot(hardwareLabel = "RTL-SDR V3"))
    assertTrue(report.contains("RTL-SDR V3"))
    assertTrue(report.contains("gain"))
  }

  @Test
  fun `scanning with hackrf shows hackrf tip`() {
    val report = build(SdrState.Scanning, snapshot(hardwareLabel = "HackRF One"))
    assertTrue(report.contains("HackRF One"))
    assertTrue(report.contains("frequency hopping"))
  }

  @Test
  fun `scanning with no hardware label shows no setup guide section`() {
    val report = build(SdrState.Scanning, snapshot(hardwareLabel = null))
    assertFalse(report.contains("Setup guide"))
  }

  @Test
  fun `report always contains diagnostics section`() {
    val report = build(SdrState.Idle, snapshot())
    assertTrue(report.contains("Urchin diagnostics"))
    assertTrue(report.contains("State:"))
  }

  private fun build(state: SdrState, diagnostics: ScanDiagnosticsSnapshot): String {
    return DiagnosticsReportBuilder.build(
      sdrState = state,
      diagnostics = diagnostics,
      deviceCount = 0,
      logEntries = emptyList()
    )
  }

  private fun snapshot(
    sourceLabel: String? = null,
    hardwareLabel: String? = null
  ) = ScanDiagnosticsSnapshot(sourceLabel = sourceLabel, hardwareLabel = hardwareLabel)
}
