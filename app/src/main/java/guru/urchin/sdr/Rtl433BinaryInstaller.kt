package guru.urchin.sdr

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.IOException

object Rtl433BinaryInstaller {
  private const val ASSET_ROOT = "sdr-bin"
  private const val BINARY_NAME = "rtl_433"
  private const val INSTALL_DIR = "sdr-bin"

  fun packagedAssetCandidates(): List<String> =
    Build.SUPPORTED_ABIS.map { "$ASSET_ROOT/$it/$BINARY_NAME" }

  fun packagedAssetPath(context: Context): String? =
    packagedAssetCandidates().firstOrNull { assetExists(context.assets, it) }

  fun installedBinary(context: Context): File? {
    val assetPath = packagedAssetPath(context) ?: return null
    val abi = assetPath.split('/')[1]
    return File(installDir(context), "rtl_433-v${appVersionCode(context)}-$abi")
      .takeIf(File::exists)
  }

  @Synchronized
  fun ensureInstalled(context: Context): File {
    val assetPath = packagedAssetPath(context)
      ?: throw IOException(
        "rtl_433 is not bundled in this APK. Expected asset ${packagedAssetCandidates().joinToString(" or ")}."
      )

    val abi = assetPath.split('/')[1]
    val installDir = installDir(context)
    if (!installDir.exists() && !installDir.mkdirs()) {
      throw IOException("Failed to create rtl_433 install dir at ${installDir.absolutePath}.")
    }

    val target = File(installDir, "rtl_433-v${appVersionCode(context)}-$abi")
    if (!target.exists() || target.length() == 0L) {
      val temp = File.createTempFile("rtl_433-", null, installDir)
      try {
        context.assets.open(assetPath).use { input ->
          temp.outputStream().use { output -> input.copyTo(output) }
        }
        ensureExecutable(temp)
        if (!temp.renameTo(target)) {
          temp.copyTo(target, overwrite = true)
          if (!temp.delete()) {
            temp.deleteOnExit()
          }
        }
      } finally {
        if (temp.exists() && temp != target) {
          temp.delete()
        }
      }
    }

    ensureExecutable(target)
    cleanupOldInstalls(installDir, keepName = target.name)
    return target
  }

  private fun installDir(context: Context): File =
    File(context.noBackupFilesDir, INSTALL_DIR)

  private fun cleanupOldInstalls(dir: File, keepName: String) {
    dir.listFiles()
      ?.filter { it.isFile && it.name.startsWith("rtl_433-") && it.name != keepName }
      ?.forEach { it.delete() }
  }

  private fun ensureExecutable(file: File) {
    file.setReadable(true, true)
    file.setWritable(true, true)
    if (!file.setExecutable(true, true) && !file.canExecute()) {
      throw IOException("Failed to mark rtl_433 executable at ${file.absolutePath}.")
    }
  }

  private fun appVersionCode(context: Context): Long {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.packageManager.getPackageInfo(
        context.packageName,
        android.content.pm.PackageManager.PackageInfoFlags.of(0)
      )
    } else {
      @Suppress("DEPRECATION")
      context.packageManager.getPackageInfo(context.packageName, 0)
    }
    return PackageInfoCompat.getLongVersionCode(packageInfo)
  }

  private fun assetExists(assetManager: AssetManager, assetPath: String): Boolean {
    val segments = assetPath.split('/')
    val directory = segments.dropLast(1).joinToString("/")
    val filename = segments.last()
    return try {
      assetManager.list(directory)?.contains(filename) == true
    } catch (_: IOException) {
      false
    }
  }
}
