package guru.urchin.sdr

/**
 * Abstraction over USB SDR device detection, allowing test fakes
 * to simulate the attach/detach/permission lifecycle without real hardware.
 */
interface UsbDetector {
  /** Returns all connected SDR devices with their hardware profiles. */
  fun findAllDevices(): List<SdrDeviceHandle>

  /** True when every device returned by [findAllDevices] has USB permission. */
  fun allPermitted(): Boolean

  /** Requests Android USB permission for the first unpermitted device. */
  fun requestNextPermission()

  /** Returns a human-readable label for the first connected device, or null. */
  fun deviceDescription(): String?

  /**
   * Registers a broadcast receiver for USB attach/detach/permission events.
   * Returns an opaque handle to pass to [unregisterReceiver].
   */
  fun registerReceiver(
    onAttached: () -> Unit,
    onDetached: () -> Unit,
    onPermissionResult: (Boolean) -> Unit
  ): Any

  /** Unregisters a previously registered receiver handle. */
  fun unregisterReceiver(handle: Any)
}

/** Lightweight device reference used by [SdrController] — avoids exposing [android.hardware.usb.UsbDevice]. */
data class SdrDeviceHandle(
  val id: Int,
  val profile: SdrHardwareProfile
)

/** Production implementation that delegates to [SdrUsbDetector]. */
class RealUsbDetector(private val context: android.content.Context) : UsbDetector {

  override fun findAllDevices(): List<SdrDeviceHandle> =
    SdrUsbDetector.findAllSdrDevices(context).mapIndexed { index, device ->
      SdrDeviceHandle(id = index, profile = device.profile)
    }

  override fun allPermitted(): Boolean {
    val devices = SdrUsbDetector.findAllSdrDevices(context)
    return devices.all { SdrUsbDetector.hasPermission(context, it.usbDevice) }
  }

  override fun requestNextPermission() {
    val unpermitted = SdrUsbDetector.findAllSdrDevices(context)
      .firstOrNull { !SdrUsbDetector.hasPermission(context, it.usbDevice) }
    if (unpermitted != null) {
      SdrUsbDetector.requestPermission(context, unpermitted.usbDevice)
    }
  }

  override fun deviceDescription(): String? {
    val device = SdrUsbDetector.findSdrDevice(context) ?: return null
    return SdrUsbDetector.deviceDescription(device.usbDevice)
  }

  override fun registerReceiver(
    onAttached: () -> Unit,
    onDetached: () -> Unit,
    onPermissionResult: (Boolean) -> Unit
  ): Any = SdrUsbDetector.registerReceiver(context, onAttached, onDetached, onPermissionResult)

  override fun unregisterReceiver(handle: Any) {
    if (handle is android.content.BroadcastReceiver) {
      SdrUsbDetector.unregisterReceiver(context, handle)
    }
  }
}
