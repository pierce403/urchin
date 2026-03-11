package guru.urchin.sdr

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import guru.urchin.util.DebugLog

data class SdrHardwareProfile(
  val label: String,
  val usbVendorId: Int,
  val usbProductId: Int,
  val rtl433DeviceArg: String?
)

data class SupportedSdrDevice(
  val usbDevice: UsbDevice,
  val profile: SdrHardwareProfile
)

object SdrUsbDetector {
  const val ACTION_USB_PERMISSION = "guru.urchin.USB_PERMISSION"

  private val KNOWN_DEVICES = listOf(
    SdrHardwareProfile(
      label = "RTL-SDR",
      usbVendorId = 0x0BDA,
      usbProductId = 0x2838,
      rtl433DeviceArg = "driver=rtlsdr"
    ),
    SdrHardwareProfile(
      label = "RTL-SDR",
      usbVendorId = 0x0BDA,
      usbProductId = 0x2832,
      rtl433DeviceArg = "driver=rtlsdr"
    ),
    SdrHardwareProfile(
      label = "HackRF One",
      usbVendorId = 0x1D50,
      usbProductId = 0x6089,
      rtl433DeviceArg = "driver=hackrf"
    )
  )

  fun findSdrDevice(context: Context): SupportedSdrDevice? {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
    return usbManager.deviceList.values.firstOrNull { device ->
      profileFor(device) != null
    }?.let { device ->
      SupportedSdrDevice(
        usbDevice = device,
        profile = profileFor(device) ?: error("Known SDR device lost its profile")
      )
    }
  }

  fun findAllSdrDevices(context: Context): List<SupportedSdrDevice> {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
      ?: return emptyList()
    return usbManager.deviceList.values.mapNotNull { device ->
      profileFor(device)?.let { profile ->
        SupportedSdrDevice(usbDevice = device, profile = profile)
      }
    }
  }

  fun hasPermission(context: Context, device: UsbDevice): Boolean {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
    return usbManager.hasPermission(device)
  }

  fun requestPermission(context: Context, device: UsbDevice) {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return
    val intent = Intent(ACTION_USB_PERMISSION).apply {
      setPackage(context.packageName)
    }
    val permissionIntent = PendingIntent.getBroadcast(
      context,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
    usbManager.requestPermission(device, permissionIntent)
    DebugLog.log("Requesting USB permission for SDR device: vendor=0x${"%04X".format(device.vendorId)} product=0x${"%04X".format(device.productId)}")
  }

  fun registerReceiver(context: Context, onAttached: () -> Unit, onDetached: () -> Unit, onPermissionResult: (Boolean) -> Unit): BroadcastReceiver {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
          UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
            val device = usbDeviceFromIntent(intent) ?: return
            if (profileFor(device) != null) {
              DebugLog.log("SDR USB device attached")
              onAttached()
            }
          }
          UsbManager.ACTION_USB_DEVICE_DETACHED -> {
            val device = usbDeviceFromIntent(intent) ?: return
            if (profileFor(device) != null) {
              DebugLog.log("SDR USB device detached")
              onDetached()
            }
          }
          ACTION_USB_PERMISSION -> {
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            DebugLog.log("SDR USB permission result: granted=$granted")
            onPermissionResult(granted)
          }
        }
      }
    }

    val filter = IntentFilter().apply {
      addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
      addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
      addAction(ACTION_USB_PERMISSION)
    }
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    return receiver
  }

  fun unregisterReceiver(context: Context, receiver: BroadcastReceiver) {
    try {
      context.unregisterReceiver(receiver)
    } catch (_: IllegalArgumentException) {
      // Already unregistered
    }
  }

  fun deviceDescription(device: UsbDevice): String {
    val profile = profileFor(device)
    return buildString {
      append(profile?.label ?: "USB SDR")
      append(": ")
      device.productName?.let { append(it) } ?: append("Unknown device")
      append(" (vendor=0x${"%04X".format(device.vendorId)}, product=0x${"%04X".format(device.productId)})")
    }
  }

  fun hardwareLabel(device: UsbDevice?): String? {
    if (device == null) return null
    return profileFor(device)?.label
  }

  private fun profileFor(device: UsbDevice): SdrHardwareProfile? {
    return KNOWN_DEVICES.firstOrNull {
      it.usbVendorId == device.vendorId && it.usbProductId == device.productId
    }
  }

  private fun usbDeviceFromIntent(intent: Intent): UsbDevice? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
    }
  }
}
