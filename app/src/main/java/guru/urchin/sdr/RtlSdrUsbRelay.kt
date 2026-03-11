package guru.urchin.sdr

import android.content.Context
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import guru.urchin.util.DebugLog
import java.io.Closeable
import java.io.IOException

/**
 * Bridges Android UsbManager-granted access into a subprocess by keeping the original
 * UsbDeviceConnection open and exposing an inheritable duplicated file descriptor.
 */
class RtlSdrUsbRelay private constructor(
  val deviceDescription: String,
  private val connection: UsbDeviceConnection,
  private val inheritedFd: ParcelFileDescriptor
) : Closeable {

  val fd: Int
    get() = inheritedFd.fd

  override fun close() {
    try {
      inheritedFd.close()
    } catch (_: Exception) {
      // Ignore close failures during teardown.
    }
    connection.close()
  }

  companion object {
    fun open(context: Context, usbDeviceId: Int): RtlSdrUsbRelay {
      val supportedDevice = SdrUsbDetector.findAllSdrDevices(context)
        .firstOrNull { it.usbDevice.deviceId == usbDeviceId }
        ?: throw IOException("Supported RTL-SDR device id=$usbDeviceId is no longer attached.")

      if (supportedDevice.profile.rtl433DeviceArg != "driver=rtlsdr") {
        throw IOException("USB fd relay is only implemented for RTL-SDR devices.")
      }

      if (!SdrUsbDetector.hasPermission(context, supportedDevice.usbDevice)) {
        throw IOException("USB permission is not granted for ${supportedDevice.profile.label}.")
      }

      val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        ?: throw IOException("USB manager unavailable.")
      val connection = usbManager.openDevice(supportedDevice.usbDevice)
        ?: throw IOException("UsbManager.openDevice() returned null for ${supportedDevice.profile.label}.")

      try {
        val inheritedFd = ParcelFileDescriptor.fromFd(connection.fileDescriptor)
        val description = SdrUsbDetector.deviceDescription(supportedDevice.usbDevice)
        DebugLog.log("Opened Android USB relay for $description fd=${inheritedFd.fd}")
        return RtlSdrUsbRelay(
          deviceDescription = description,
          connection = connection,
          inheritedFd = inheritedFd
        )
      } catch (e: Exception) {
        connection.close()
        throw IOException("Failed to duplicate Android USB fd for ${supportedDevice.profile.label}.", e)
      }
    }
  }
}
