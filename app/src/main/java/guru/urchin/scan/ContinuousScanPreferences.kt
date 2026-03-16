package guru.urchin.scan

import android.content.Context

object ContinuousScanPreferences {
  private const val PREFS_NAME = "urchin_scan"
  private const val KEY = "continuous_scanning_enabled"

  fun isEnabled(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getBoolean(KEY, false)
  }

  fun setEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit().putBoolean(KEY, enabled).apply()
  }
}
