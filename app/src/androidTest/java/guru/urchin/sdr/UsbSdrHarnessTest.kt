package guru.urchin.sdr

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import guru.urchin.data.AppDatabase
import guru.urchin.data.DeviceRepository
import guru.urchin.scan.ObservationRecorder
import guru.urchin.scan.ScanDiagnosticsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test harness that simulates a USB RTL-SDR being connected,
 * exercising the full lifecycle: attach -> permission -> scan -> data -> detach.
 *
 * No real USB hardware or native binaries are required.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class UsbSdrHarnessTest {

  private lateinit var db: AppDatabase
  private lateinit var repository: DeviceRepository
  private lateinit var controller: SdrController
  private lateinit var fakeUsb: FakeUsbDetector
  private lateinit var scope: CoroutineScope
  private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

  @Before
  fun setUp() {
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = DeviceRepository(db, db.deviceDao(), db.sightingDao())
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    fakeUsb = FakeUsbDetector()

    // Configure preferences for USB source
    SdrPreferences.setEnabled(context, true)
    SdrPreferences.setSource(context, SdrPreferences.SdrSource.USB)
    SdrPreferences.setEnabledProtocols(context, setOf("tpms"))

    ScanDiagnosticsStore.reset()

    controller = SdrController(
      context = context,
      scope = scope,
      observationRecorder = ObservationRecorder(repository, scope),
      usbDetector = fakeUsb
    )
  }

  @After
  fun tearDown() {
    db.close()
    SdrPreferences.setEnabled(context, false)
  }

  // ── State Transition Tests ──────────────────────────────────────────────

  @Test
  fun startSdr_noDevices_setsUsbNotConnected() {
    fakeUsb.devices = emptyList()
    runOnMain { controller.startSdr() }
    assertEquals(SdrState.UsbNotConnected, controller.sdrState.value)
  }

  @Test
  fun startSdr_devicePresent_permissionDenied_requestsPermission() {
    fakeUsb.devices = SdrJsonFixtures.singleRtlSdr()
    fakeUsb.permitted = false
    runOnMain { controller.startSdr() }
    assertTrue("Permission should have been requested", fakeUsb.permissionRequested)
  }

  @Test
  fun startSdr_devicePresent_permitted_setsScanning() {
    fakeUsb.devices = SdrJsonFixtures.singleRtlSdr()
    fakeUsb.permitted = true
    runOnMain { controller.startSdr() }
    // State reaches Scanning (the native process will fail, but state is set before process start)
    assertEquals(SdrState.Scanning, controller.sdrState.value)
  }

  @Test
  fun stopSdr_setsIdle() {
    fakeUsb.devices = SdrJsonFixtures.singleRtlSdr()
    fakeUsb.permitted = true
    runOnMain { controller.startSdr() }
    runOnMain { controller.stopSdr() }
    assertEquals(SdrState.Idle, controller.sdrState.value)
  }

  // ── USB Lifecycle Simulation Tests ────────────────────────────────────────

  @Test
  fun simulateAttach_whileIdle_startsScanning() {
    fakeUsb.devices = emptyList()
    runOnMain {
      controller.registerUsbDetection()
      controller.startSdr()
    }
    assertEquals(SdrState.UsbNotConnected, controller.sdrState.value)

    // Plug in a device
    fakeUsb.devices = SdrJsonFixtures.singleRtlSdr()
    fakeUsb.permitted = true
    runOnMain { fakeUsb.simulateAttach() }
    assertEquals(SdrState.Scanning, controller.sdrState.value)
  }

  @Test
  fun simulateDetach_whileScanning_setsUsbNotConnected() {
    fakeUsb.devices = SdrJsonFixtures.singleRtlSdr()
    fakeUsb.permitted = true
    runOnMain {
      controller.registerUsbDetection()
      controller.startSdr()
    }
    assertEquals(SdrState.Scanning, controller.sdrState.value)

    // Unplug the device
    runOnMain { fakeUsb.simulateDetach() }
    assertEquals(SdrState.UsbNotConnected, controller.sdrState.value)
  }

  @Test
  fun simulatePermissionDenied_setsUsbPermissionDenied() {
    runOnMain { controller.registerUsbDetection() }
    runOnMain { fakeUsb.simulatePermissionResult(false) }
    assertEquals(SdrState.UsbPermissionDenied, controller.sdrState.value)
  }

  @Test
  fun simulatePermissionGranted_retriesStart() {
    fakeUsb.devices = SdrJsonFixtures.singleRtlSdr()
    fakeUsb.permitted = true
    runOnMain { controller.registerUsbDetection() }
    runOnMain { fakeUsb.simulatePermissionResult(true) }
    assertEquals(SdrState.Scanning, controller.sdrState.value)
  }

  @Test
  fun unregisterUsbDetection_clearsReceiver() {
    runOnMain { controller.registerUsbDetection() }
    assertTrue(fakeUsb.isRegistered)
    runOnMain { controller.unregisterUsbDetection() }
    assertFalse(fakeUsb.isRegistered)
  }

  // ── Pipeline Integration Tests ────────────────────────────────────────────

  @Test
  fun handleTpmsReading_recordsToDatabase() = runBlocking {
    controller.handleSdrReading(SdrJsonFixtures.tpmsReading())
    // Allow coroutine to complete
    Thread.sleep(200)
    val devices = db.deviceDao().getDevices()
    assertEquals(1, devices.size)
    assertEquals("tpms", devices[0].protocolType)
  }

  @Test
  fun handlePocsagReading_recordsToDatabase() = runBlocking {
    controller.handleSdrReading(SdrJsonFixtures.pocsagReading())
    Thread.sleep(200)
    val devices = db.deviceDao().getDevices()
    assertEquals(1, devices.size)
    assertEquals("pocsag", devices[0].protocolType)
  }

  @Test
  fun handleAdsbReading_recordsToDatabase() = runBlocking {
    controller.handleSdrReading(SdrJsonFixtures.adsbReading())
    Thread.sleep(200)
    val devices = db.deviceDao().getDevices()
    assertEquals(1, devices.size)
    assertEquals("adsb", devices[0].protocolType)
  }

  @Test
  fun handleP25Reading_recordsToDatabase() = runBlocking {
    controller.handleSdrReading(SdrJsonFixtures.p25Reading())
    Thread.sleep(200)
    val devices = db.deviceDao().getDevices()
    assertEquals(1, devices.size)
    assertEquals("p25", devices[0].protocolType)
  }

  @Test
  fun multipleReadings_sameSensor_incrementObservationCount() = runBlocking {
    repeat(3) {
      controller.handleSdrReading(SdrJsonFixtures.tpmsReading())
    }
    Thread.sleep(300)
    val devices = db.deviceDao().getDevices()
    assertEquals(1, devices.size)
    assertEquals(3, devices[0].observationCount)
  }

  @Test
  fun multipleReadings_differentSensors_createsSeparateDevices() = runBlocking {
    controller.handleSdrReading(SdrJsonFixtures.tpmsReading(sensorId = "0x00000001"))
    controller.handleSdrReading(SdrJsonFixtures.tpmsReading(sensorId = "0x00000002"))
    controller.handleSdrReading(SdrJsonFixtures.pocsagReading(address = "9999999"))
    Thread.sleep(300)
    val devices = db.deviceDao().getDevices()
    assertEquals(3, devices.size)
  }

  @Test
  fun diagnosticsStore_reflectsReadingCount() {
    ScanDiagnosticsStore.reset()
    controller.handleSdrReading(SdrJsonFixtures.tpmsReading())
    controller.handleSdrReading(SdrJsonFixtures.adsbReading())
    val snapshot = ScanDiagnosticsStore.snapshot.value
    assertEquals(2, snapshot.sdrCallbackCount)
    assertEquals(2, snapshot.rawCallbackCount)
    assertTrue(snapshot.lastReadingAt != null && snapshot.lastReadingAt!! > 0)
  }

  // ── Full Lifecycle Scenario ───────────────────────────────────────────────

  @Test
  fun fullLifecycle_attachScanDataDetachReattach() = runBlocking {
    // 1. Start with no device
    fakeUsb.devices = emptyList()
    runOnMain {
      controller.registerUsbDetection()
      controller.startSdr()
    }
    assertEquals(SdrState.UsbNotConnected, controller.sdrState.value)

    // 2. Plug in device
    fakeUsb.devices = SdrJsonFixtures.singleRtlSdr()
    fakeUsb.permitted = true
    runOnMain { fakeUsb.simulateAttach() }
    assertEquals(SdrState.Scanning, controller.sdrState.value)

    // 3. Receive data
    controller.handleSdrReading(SdrJsonFixtures.tpmsReading(sensorId = "0xCAFEBABE"))
    controller.handleSdrReading(SdrJsonFixtures.tpmsReading(sensorId = "0xDEADBEEF"))
    Thread.sleep(200)
    assertEquals(2, db.deviceDao().getDevices().size)

    // 4. Unplug
    runOnMain { fakeUsb.simulateDetach() }
    assertEquals(SdrState.UsbNotConnected, controller.sdrState.value)

    // 5. Re-attach
    runOnMain { fakeUsb.simulateAttach() }
    assertEquals(SdrState.Scanning, controller.sdrState.value)

    // 6. More data continues to flow
    controller.handleSdrReading(SdrJsonFixtures.tpmsReading(sensorId = "0xCAFEBABE"))
    Thread.sleep(200)
    val cafebabe = db.deviceDao().getDevices().first { it.displayName?.contains("CAFEBABE") == true }
    assertEquals(2, cafebabe.observationCount)

    // Cleanup
    runOnMain { controller.unregisterUsbDetection() }
  }

  private fun runOnMain(block: () -> Unit) {
    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
      .runOnMainSync(block)
  }
}
