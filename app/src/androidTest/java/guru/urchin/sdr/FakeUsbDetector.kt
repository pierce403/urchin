package guru.urchin.sdr

/**
 * Test fake that simulates USB SDR device attach/detach/permission lifecycle
 * without real hardware or Android USB system services.
 */
class FakeUsbDetector : UsbDetector {

  /** Devices currently "connected". Modify before calling [simulateAttach]. */
  var devices: List<SdrDeviceHandle> = emptyList()

  /** Whether [allPermitted] returns true. */
  var permitted = true

  /** Set to true when [requestNextPermission] is called. */
  var permissionRequested = false

  private var attachCallback: (() -> Unit)? = null
  private var detachCallback: (() -> Unit)? = null
  private var permissionCallback: ((Boolean) -> Unit)? = null
  private var registered = false

  override fun findAllDevices(): List<SdrDeviceHandle> = devices

  override fun allPermitted(): Boolean = permitted

  override fun requestNextPermission() {
    permissionRequested = true
  }

  override fun deviceDescription(): String? =
    devices.firstOrNull()?.let { "${it.profile.label} (fake-${it.id})" }

  override fun registerReceiver(
    onAttached: () -> Unit,
    onDetached: () -> Unit,
    onPermissionResult: (Boolean) -> Unit
  ): Any {
    attachCallback = onAttached
    detachCallback = onDetached
    permissionCallback = onPermissionResult
    registered = true
    return "fake-receiver"
  }

  override fun unregisterReceiver(handle: Any) {
    attachCallback = null
    detachCallback = null
    permissionCallback = null
    registered = false
  }

  // ── Test control methods ──────────────────────────────────────────────────

  /** Simulates a USB SDR device being physically plugged in. */
  fun simulateAttach() {
    attachCallback?.invoke()
  }

  /** Simulates a USB SDR device being physically unplugged. */
  fun simulateDetach() {
    detachCallback?.invoke()
  }

  /** Simulates the Android USB permission dialog result. */
  fun simulatePermissionResult(granted: Boolean) {
    permissionCallback?.invoke(granted)
  }

  val isRegistered: Boolean get() = registered
}
