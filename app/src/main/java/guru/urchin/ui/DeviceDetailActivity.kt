package guru.urchin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import guru.urchin.UrchinApp
import guru.urchin.data.DeviceEntity
import guru.urchin.databinding.ActivityDeviceDetailBinding
import guru.urchin.util.Formatters
import guru.urchin.util.SensorMetadataParser
import guru.urchin.util.SensorPresentationBuilder
import guru.urchin.util.WindowInsetsHelper

class DeviceDetailActivity : AppCompatActivity() {
  private lateinit var binding: ActivityDeviceDetailBinding
  private lateinit var adapter: SightingAdapter
  private var currentDevice: DeviceEntity? = null
  private var pendingExport: DeviceJsonExport? = null
  private val app by lazy { application as UrchinApp }
  private val repository by lazy { app.repository }
  private val saveDeviceJsonLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
    if (uri == null) {
      pendingExport = null
      return@registerForActivityResult
    }
    writePendingExportToUri(uri)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityDeviceDetailBinding.inflate(layoutInflater)
    setContentView(binding.root)

    setSupportActionBar(binding.toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    WindowInsetsHelper.applyToolbarInsets(binding.toolbar)
    WindowInsetsHelper.applyBottomInsets(binding.detailScroll)
    WindowInsetsHelper.requestApplyInsets(binding.root)

    val deviceKey = intent.getStringExtra(EXTRA_DEVICE_KEY) ?: run {
      finish()
      return
    }

    adapter = SightingAdapter()
    binding.sightingsList.layoutManager = LinearLayoutManager(this)
    binding.sightingsList.adapter = adapter

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
          repository.observeDevice(deviceKey).collect { device ->
            if (isFinishing) return@collect
            currentDevice = device
            invalidateOptionsMenu()
            if (device == null) {
              finish()
              return@collect
            }

            val presentation = SensorPresentationBuilder.build(device)
            val metadata = SensorMetadataParser.parse(device.lastMetadataJson)

            binding.detailName.text = presentation.title
            binding.detailIdentity.isVisible = presentation.detailLines.isNotEmpty()
            binding.detailIdentity.text = presentation.detailLines.joinToString("\n")
            binding.detailKey.text = getString(guru.urchin.R.string.sensor_key_value, device.deviceKey)
            binding.detailFirstSeen.text = getString(
              guru.urchin.R.string.first_seen_value,
              Formatters.formatTimestamp(device.firstSeen)
            )
            binding.detailLastSeen.text = getString(
              guru.urchin.R.string.last_seen_value,
              Formatters.formatTimestamp(device.lastSeen)
            )
            binding.detailStats.text = getString(
              guru.urchin.R.string.detail_stats_value,
              device.rssiMin,
              device.rssiMax,
              device.rssiAvg,
              device.sightingsCount,
              device.observationCount
            )
            binding.detailReading.text = buildLatestReading(metadata)
            binding.detailMetadata.text = metadata.rawJson ?: device.lastMetadataJson ?: getString(guru.urchin.R.string.no_metadata_recorded)
          }
        }

        launch {
          repository.observeSightings(deviceKey).collect { sightings ->
            adapter.submitList(sightings)
          }
        }
      }
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(guru.urchin.R.menu.device_detail_menu, menu)
    return true
  }

  override fun onPrepareOptionsMenu(menu: Menu): Boolean {
    val exportReady = currentDevice != null
    menu.findItem(guru.urchin.R.id.menu_rename_device)?.isEnabled = exportReady
    menu.findItem(guru.urchin.R.id.menu_copy_device_json)?.isEnabled = exportReady
    menu.findItem(guru.urchin.R.id.menu_save_device_json)?.isEnabled = exportReady
    menu.findItem(guru.urchin.R.id.menu_share_device_json)?.isEnabled = exportReady
    return super.onPrepareOptionsMenu(menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      guru.urchin.R.id.menu_rename_device -> {
        showRenameDialog()
        true
      }
      guru.urchin.R.id.menu_copy_device_json -> {
        copyCurrentDeviceJson()
        true
      }
      guru.urchin.R.id.menu_save_device_json -> {
        saveCurrentDeviceJson()
        true
      }
      guru.urchin.R.id.menu_share_device_json -> {
        shareCurrentDeviceJson()
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  override fun onSupportNavigateUp(): Boolean {
    finish()
    return true
  }

  private fun showRenameDialog() {
    val device = currentDevice ?: return
    val input = android.widget.EditText(this).apply {
      setText(device.userCustomName ?: device.displayName.orEmpty())
      hint = getString(guru.urchin.R.string.rename_device_hint)
      selectAll()
    }
    val container = android.widget.FrameLayout(this).apply {
      val margin = (16 * resources.displayMetrics.density).toInt()
      setPadding(margin, margin / 2, margin, 0)
      addView(input)
    }
    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
      .setTitle(guru.urchin.R.string.rename_device_title)
      .setView(container)
      .setMessage(guru.urchin.R.string.rename_device_clear_hint)
      .setPositiveButton(android.R.string.ok) { _, _ ->
        val newName = input.text.toString().trim().takeIf(String::isNotEmpty)
        lifecycleScope.launch {
          repository.setUserCustomName(device.deviceKey, newName)
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun buildLatestReading(metadata: guru.urchin.util.SensorMetadata): String {
    val protocol = metadata.protocolType ?: "tpms"
    return when (protocol) {
      "pocsag" -> buildList {
        metadata.pocsagCapCode?.let { add("CAP Code: $it") }
        metadata.pocsagFunctionCode?.let { add("Function: $it") }
        metadata.pocsagMessage?.let { add("Message: $it") }
        metadata.rssi?.let { add(Formatters.formatRssi(it)) }
      }
      "adsb" -> buildList {
        metadata.adsbIcao?.let { add("ICAO: $it") }
        metadata.adsbCallsign?.let { add("Callsign: $it") }
        metadata.adsbAltitude?.let { add("Altitude: ${it} ft") }
        metadata.adsbSpeed?.let { add(String.format("Speed: %.0f kts", it)) }
        metadata.adsbHeading?.let { add(String.format("Heading: %.0f°", it)) }
        if (metadata.adsbLat != null && metadata.adsbLon != null) {
          add(String.format("Position: %.4f, %.4f", metadata.adsbLat, metadata.adsbLon))
        }
        metadata.adsbSquawk?.let { add("Squawk: $it") }
        metadata.rssi?.let { add(Formatters.formatRssi(it)) }
      }
      "p25" -> buildList {
        metadata.p25UnitId?.let { add("Unit ID: $it") }
        metadata.p25TalkGroupId?.let { add("Talk Group: $it") }
        metadata.p25Nac?.let { add("NAC: $it") }
        metadata.p25Wacn?.let { add("WACN: $it") }
        metadata.p25SystemId?.let { add("System ID: $it") }
        metadata.rssi?.let { add(Formatters.formatRssi(it)) }
      }
      else -> buildList {
        metadata.tpmsPressureKpa?.let { add(Formatters.formatPressure(it)) }
        metadata.tpmsTemperatureC?.let { add(Formatters.formatTemperature(it)) }
        metadata.tpmsBatteryOk?.let { add(if (it) "Battery OK" else "Battery low") }
        metadata.tpmsFrequencyMhz?.let { add(String.format("Frequency %.2f MHz", it)) }
        metadata.tpmsSnr?.let { add(String.format("SNR %.1f dB", it)) }
        metadata.rssi?.let { add(Formatters.formatRssi(it)) }
      }
    }.joinToString("\n").ifBlank {
      getString(guru.urchin.R.string.no_latest_reading)
    }
  }

  private fun copyCurrentDeviceJson() {
    val export = buildCurrentExport() ?: return
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
      ClipData.newPlainText(getString(guru.urchin.R.string.copy_device_json), export.json)
    )
    Toast.makeText(
      this,
      guru.urchin.R.string.device_json_copied,
      Toast.LENGTH_SHORT
    ).show()
  }

  private fun saveCurrentDeviceJson() {
    val export = buildCurrentExport() ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      lifecycleScope.launch {
        val saved = withContext(Dispatchers.IO) { saveExportToDownloads(export) }
        if (saved) {
          Toast.makeText(
            this@DeviceDetailActivity,
            getString(guru.urchin.R.string.device_json_saved_downloads, export.fileName),
            Toast.LENGTH_SHORT
          ).show()
        } else {
          Toast.makeText(
            this@DeviceDetailActivity,
            guru.urchin.R.string.device_json_save_failed,
            Toast.LENGTH_SHORT
          ).show()
        }
      }
      return
    }
    pendingExport = export
    saveDeviceJsonLauncher.launch(export.fileName)
  }

  private fun shareCurrentDeviceJson() {
    val export = buildCurrentExport() ?: return
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_SUBJECT, getString(guru.urchin.R.string.device_json_share_subject, export.fileName))
      putExtra(Intent.EXTRA_TEXT, export.json)
    }
    startActivity(Intent.createChooser(shareIntent, getString(guru.urchin.R.string.share_device_json)))
  }

  private fun buildCurrentExport(): DeviceJsonExport? {
    val device = currentDevice ?: return null
    return DeviceDetailExport.build(device)
  }

  private fun writePendingExportToUri(uri: Uri) {
    val export = pendingExport ?: return
    pendingExport = null
    lifecycleScope.launch {
      val saved = withContext(Dispatchers.IO) {
        runCatching {
          contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(export.json.toByteArray())
          } ?: error("Unable to open export destination")
        }.isSuccess
      }
      Toast.makeText(
        this@DeviceDetailActivity,
        if (saved) {
          getString(guru.urchin.R.string.device_json_saved, export.fileName)
        } else {
          getString(guru.urchin.R.string.device_json_save_failed)
        },
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  private fun saveExportToDownloads(export: DeviceJsonExport): Boolean {
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val existingUri = findDownloadsExportUri(collection, export.fileName)
    if (existingUri != null) {
      return runCatching {
        contentResolver.openOutputStream(existingUri, "wt")?.use { stream ->
          stream.write(export.json.toByteArray())
        } ?: error("Unable to open existing Downloads export")
      }.isSuccess
    }
    val values = ContentValues().apply {
      put(MediaStore.MediaColumns.DISPLAY_NAME, export.fileName)
      put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
      put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
      put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = contentResolver.insert(collection, values) ?: return false
    return try {
      contentResolver.openOutputStream(uri, "wt")?.use { stream ->
        stream.write(export.json.toByteArray())
      } ?: error("Unable to open Downloads export")
      val completedValues = ContentValues().apply {
        put(MediaStore.MediaColumns.IS_PENDING, 0)
      }
      contentResolver.update(uri, completedValues, null, null)
      true
    } catch (_: Exception) {
      contentResolver.delete(uri, null, null)
      false
    }
  }

  private fun findDownloadsExportUri(collection: Uri, fileName: String): Uri? {
    val projection = arrayOf(MediaStore.MediaColumns._ID)
    val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
    val selectionArgs = arrayOf(fileName, "${Environment.DIRECTORY_DOWNLOADS}/")
    contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
      if (!cursor.moveToFirst()) {
        return null
      }
      val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
      return ContentUris.withAppendedId(collection, cursor.getLong(idIndex))
    }
    return null
  }

  companion object {
    private const val EXTRA_DEVICE_KEY = "device_key"

    fun intent(context: Context, deviceKey: String): Intent {
      return Intent(context, DeviceDetailActivity::class.java).apply {
        putExtra(EXTRA_DEVICE_KEY, deviceKey)
      }
    }
  }
}
