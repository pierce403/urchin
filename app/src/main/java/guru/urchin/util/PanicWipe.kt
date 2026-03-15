package guru.urchin.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import guru.urchin.data.AppDatabase
import guru.urchin.data.DatabaseKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.security.SecureRandom

/**
 * Emergency data destruction for hostile environments. When triggered,
 * destroys the database encryption key, clears all tables, and overwrites
 * the database file with random bytes before deletion.
 *
 * Trigger methods:
 * - Broadcast intent with action [ACTION_PANIC_WIPE]
 * - Direct call to [execute]
 */
class PanicWipeReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    if (intent?.action == ACTION_PANIC_WIPE) {
      PanicWipe.execute(context)
    }
  }

  companion object {
    const val ACTION_PANIC_WIPE = "guru.urchin.action.PANIC_WIPE"
  }
}

object PanicWipe {
  fun execute(context: Context) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        DebugLog.log("PANIC WIPE: initiated")

        // 1. Destroy the database encryption key
        DatabaseKeyManager.destroyKey(context)

        // 2. Clear all database tables
        try {
          val db = AppDatabase.build(context)
          db.clearAllTables()
          db.close()
        } catch (_: Exception) {
          // Database may be inaccessible without key - that's OK
        }

        // 3. Overwrite database files with random data
        val dbFile = context.getDatabasePath("urchin.db")
        secureDelete(dbFile)
        secureDelete(File(dbFile.path + "-wal"))
        secureDelete(File(dbFile.path + "-shm"))
        secureDelete(File(dbFile.path + "-journal"))

        // 4. Clear SharedPreferences
        context.getSharedPreferences("urchin_prefs", Context.MODE_PRIVATE)
          .edit().clear().apply()
        context.getSharedPreferences("sdr_prefs", Context.MODE_PRIVATE)
          .edit().clear().apply()

        // 5. Clear internal cache
        context.cacheDir.deleteRecursively()

        DebugLog.log("PANIC WIPE: complete")
      } catch (e: Exception) {
        DebugLog.log("PANIC WIPE error: ${e.message}")
      }
    }
  }

  private fun secureDelete(file: File) {
    if (!file.exists()) return
    try {
      val length = file.length()
      val random = SecureRandom()
      val buffer = ByteArray(4096)
      file.outputStream().use { os ->
        var remaining = length
        while (remaining > 0) {
          random.nextBytes(buffer)
          val writeLen = minOf(remaining, buffer.size.toLong()).toInt()
          os.write(buffer, 0, writeLen)
          remaining -= writeLen
        }
        os.flush()
        os.fd.sync()
      }
      file.delete()
    } catch (_: Exception) {
      file.delete()
    }
  }
}
