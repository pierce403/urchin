package guru.urchin.sdr

import android.content.Context
import java.io.File
import java.io.IOException

object Rtl433BinaryInstaller {
  private const val PREFERRED_BINARY_NAME = "librtl_433.so"
  private const val FALLBACK_BINARY_NAME = "rtl_433"

  fun expectedLocations(context: Context): List<String> {
    val nativeDir = File(context.applicationInfo.nativeLibraryDir)
    return listOf(
      File(nativeDir, PREFERRED_BINARY_NAME).absolutePath,
      File(nativeDir, FALLBACK_BINARY_NAME).absolutePath
    )
  }

  fun installedBinary(context: Context): File? =
    expectedLocations(context).map(::File).firstOrNull(File::exists)

  @Synchronized
  fun ensureInstalled(context: Context): File {
    return installedBinary(context)
      ?: throw IOException(
        "rtl_433 is not bundled in this APK. Expected executable at ${expectedLocations(context).joinToString(" or ")}."
      )
  }
}
