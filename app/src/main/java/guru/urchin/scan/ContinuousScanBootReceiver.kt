package guru.urchin.scan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ContinuousScanBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
    if (!ContinuousScanPreferences.isEnabled(context) ||
        !StartOnBootPreferences.isEnabled(context)) {
      Log.d("BootReceiver", "Boot completed; continuous scan autostart disabled")
      return
    }
    Log.d("BootReceiver", "Boot completed; starting continuous scan service")
    ContinuousScanService.start(context)
  }
}
