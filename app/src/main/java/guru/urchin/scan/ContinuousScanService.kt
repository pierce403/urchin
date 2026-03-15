package guru.urchin.scan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import guru.urchin.R
import guru.urchin.UrchinApp
import guru.urchin.sdr.SdrState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ContinuousScanService : Service() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var app: UrchinApp
  private var sdrMonitorJob: Job? = null
  private var currentSdrState: SdrState = SdrState.Idle

  override fun onCreate() {
    super.onCreate()
    app = application as UrchinApp
    ensureNotificationChannel()
    observeSdrState()
    app.sdrController.registerUsbDetection()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action ?: ACTION_START) {
      ACTION_STOP -> {
        stopContinuousScanning()
        return START_NOT_STICKY
      }

      ACTION_START -> {
        ContinuousScanPreferences.setEnabled(this, true)
        startForegroundNotification()
        ensureSdrMonitor()
        app.sdrController.startSdr()
      }
    }
    return START_STICKY
  }

  override fun onDestroy() {
    app.sdrController.stopSdr()
    app.sdrController.unregisterUsbDetection()
    sdrMonitorJob?.cancel()
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun observeSdrState() {
    serviceScope.launch {
      app.sdrController.sdrState.collectLatest { state ->
        currentSdrState = state
        updateForegroundNotification()
      }
    }
  }

  private fun ensureSdrMonitor() {
    if (sdrMonitorJob?.isActive == true) return

    sdrMonitorJob = serviceScope.launch {
      var retryCount = 0
      app.sdrController.sdrState.collectLatest { state ->
        if (!ContinuousScanPreferences.isEnabled(this@ContinuousScanService)) return@collectLatest
        when (state) {
          is SdrState.Error -> {
            val backoff = (SDR_ERROR_RETRY_MS * (1 shl retryCount.coerceAtMost(4)))
              .coerceAtMost(SDR_BLOCKED_RETRY_MS)
            delay(backoff)
            retryCount++
            app.sdrController.startSdr()
          }
          is SdrState.Scanning -> retryCount = 0
          is SdrState.Idle -> {
            delay(SDR_RESTART_GRACE_MS)
            app.sdrController.startSdr()
          }
          else -> {}
        }
      }
    }
  }

  private fun stopContinuousScanning() {
    ContinuousScanPreferences.setEnabled(this, false)
    sdrMonitorJob?.cancel()
    sdrMonitorJob = null
    app.sdrController.stopSdr()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      @Suppress("DEPRECATION")
      stopForeground(true)
    }
    stopSelf()
  }

  private fun startForegroundNotification() {
    val notification = buildNotification()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
      )
    } else {
      @Suppress("DEPRECATION")
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun updateForegroundNotification() {
    val manager = getSystemService(NotificationManager::class.java) ?: return
    manager.notify(NOTIFICATION_ID, buildNotification())
  }

  private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
    .setSmallIcon(R.mipmap.ic_launcher)
    .setContentTitle(getString(R.string.continuous_scan_notification_title))
    .setContentText(notificationBody())
    .setOngoing(true)
    .setOnlyAlertOnce(true)
    .setSilent(true)
    .setCategory(NotificationCompat.CATEGORY_SERVICE)
    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    .setContentIntent(openAppPendingIntent())
    .addAction(
      0,
      getString(R.string.scan_stop),
      stopServicePendingIntent()
    )
    .build()

  private fun notificationBody(): String {
    return when (val state = currentSdrState) {
      is SdrState.Scanning -> getString(R.string.continuous_scan_active)
      is SdrState.Idle -> getString(R.string.continuous_scan_idle)
      is SdrState.UsbNotConnected -> getString(R.string.continuous_scan_usb_missing)
      is SdrState.UsbPermissionDenied -> getString(R.string.continuous_scan_usb_denied)
      is SdrState.Error -> state.message
    }
  }

  private fun openAppPendingIntent(): PendingIntent {
    val intent = Intent(this, guru.urchin.ui.MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(
      this,
      REQUEST_OPEN_APP,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun stopServicePendingIntent(): PendingIntent {
    val intent = Intent(this, ContinuousScanService::class.java).apply {
      action = ACTION_STOP
    }
    return PendingIntent.getService(
      this,
      REQUEST_STOP_SERVICE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
      CHANNEL_ID,
      getString(R.string.continuous_scan_channel_name),
      NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
      description = getString(R.string.continuous_scan_channel_description)
      setShowBadge(false)
      setSound(null, null)
    }
    manager.createNotificationChannel(channel)
  }

  companion object {
    private const val ACTION_START = "guru.urchin.action.START_CONTINUOUS_SCAN"
    private const val ACTION_STOP = "guru.urchin.action.STOP_CONTINUOUS_SCAN"
    private const val CHANNEL_ID = "continuous_scan_service_status"
    private const val NOTIFICATION_ID = 4101
    private const val REQUEST_OPEN_APP = 4102
    private const val REQUEST_STOP_SERVICE = 4103
    private const val SDR_ERROR_RETRY_MS = 4_000L
    private const val SDR_BLOCKED_RETRY_MS = 30_000L
    private const val SDR_RESTART_GRACE_MS = 1_500L

    fun start(context: Context) {
      val intent = Intent(context, ContinuousScanService::class.java).apply {
        action = ACTION_START
      }
      ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
      val intent = Intent(context, ContinuousScanService::class.java).apply {
        action = ACTION_STOP
      }
      context.startService(intent)
    }
  }
}
