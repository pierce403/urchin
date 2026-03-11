package guru.urchin.sdr

import android.content.Context
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import guru.urchin.util.DebugLog
import java.io.Closeable
import java.io.FileDescriptor
import java.io.IOException

/**
 * Bridges Android UsbManager-granted access into a subprocess by keeping the original
 * UsbDeviceConnection open and mapping a duplicated descriptor onto child stdin.
 *
 * Android's ProcessBuilder closes non-stdio fds during spawn, so USB relays must
 * travel through stdin/stdout/stderr if they need to reach a child process.
 */
class RtlSdrUsbRelay private constructor(
  val deviceDescription: String,
  private val connection: UsbDeviceConnection,
  private val inheritedFd: ParcelFileDescriptor
) : Closeable {

  val fd: Int
    get() = inheritedFd.fd

  fun startProcess(args: List<String>): Process {
    synchronized(spawnLock) {
      val savedStdin = try {
        Os.dup(FileDescriptor.`in`)
      } catch (e: ErrnoException) {
        throw IOException("Failed to duplicate app stdin before spawning subprocess.", e)
      }
      var process: Process? = null

      try {
        try {
          Os.dup2(inheritedFd.fileDescriptor, STDIN_FILENO)
        } catch (e: ErrnoException) {
          throw IOException("Failed to map Android USB relay onto child stdin.", e)
        }

        process = ProcessBuilder(args)
          .redirectInput(ProcessBuilder.Redirect.INHERIT)
          .redirectErrorStream(false)
          .apply {
            environment()["URCHIN_RTLSDR_FD"] = STDIN_FILENO.toString()
          }
          .start()
        DebugLog.log("Mapped Android USB relay for $deviceDescription onto child fd=$STDIN_FILENO")
      } finally {
        try {
          Os.dup2(savedStdin, STDIN_FILENO)
        } catch (e: ErrnoException) {
          if (process != null) {
            DebugLog.log(
              "Failed to restore app stdin after spawning subprocess: ${e.message}",
              level = android.util.Log.WARN,
              throwable = e
            )
          } else {
            throw IOException("Failed to restore app stdin after spawning subprocess.", e)
          }
        } finally {
          try {
            Os.close(savedStdin)
          } catch (_: ErrnoException) {
            // Ignore close failures during stdin restoration.
          }
        }
      }
      return process ?: throw IOException("Failed to start subprocess with Android USB relay.")
    }
  }

  override fun close() {
    try {
      inheritedFd.close()
    } catch (_: Exception) {
      // Ignore close failures during teardown.
    }
    connection.close()
  }

  companion object {
    private const val STDIN_FILENO = 0
    private val spawnLock = Any()

    fun open(context: Context, usbDeviceId: Int): RtlSdrUsbRelay {
      val supportedDevice = SdrUsbDetector.findAllSdrDevices(context)
        .firstOrNull { it.usbDevice.deviceId == usbDeviceId }
        ?: throw IOException("Supported RTL-SDR device id=$usbDeviceId is no longer attached.")

      if (!supportedDevice.profile.usesAndroidUsbFdRelay) {
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
        DebugLog.log("Opened Android USB relay for $description parentFd=${inheritedFd.fd}")
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
