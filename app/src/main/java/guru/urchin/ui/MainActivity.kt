package guru.urchin.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import android.content.Intent
import guru.urchin.R
import guru.urchin.databinding.ActivityMainBinding
import guru.urchin.scan.ContinuousScanPreferences
import guru.urchin.scan.ContinuousScanService
import guru.urchin.sdr.SdrPreferences
import guru.urchin.sdr.SdrRuntimeInspector
import guru.urchin.sdr.SdrState
import guru.urchin.sdr.SdrUsbDetector
import guru.urchin.util.AppVersion
import guru.urchin.util.AppVersionInfo
import guru.urchin.util.Formatters
import guru.urchin.util.WindowInsetsHelper

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private lateinit var viewModel: MainViewModel
  private lateinit var adapter: DeviceAdapter
  private lateinit var appVersionInfo: AppVersionInfo
  private var compactCards = false
  private var continuousScanningEnabled = false
  private var bindingPrefs = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    viewModel = ViewModelProvider(this)[MainViewModel::class.java]
    appVersionInfo = AppVersion.read(this)

    setSupportActionBar(binding.toolbar)
    supportActionBar?.title = getString(R.string.app_name_header)
    binding.toolbar.setNavigationIcon(R.drawable.ic_filter_list)
    binding.toolbar.navigationContentDescription = getString(R.string.open_filters)
    binding.toolbar.setNavigationOnClickListener { toggleFiltersDrawer() }

    WindowInsetsHelper.applyToolbarInsets(binding.toolbar)
    WindowInsetsHelper.applyBottomInsets(binding.deviceList)
    WindowInsetsHelper.applyVerticalInsets(binding.filterDrawerContent)
    WindowInsetsHelper.requestApplyInsets(binding.root)

    continuousScanningEnabled = ContinuousScanPreferences.isEnabled(this)
    compactCards = MainDisplayPreferences.isCompactDeviceCards(this)
    adapter = DeviceAdapter(
      onClick = { item ->
        startActivity(DeviceDetailActivity.intent(this, item.deviceKey))
      },
      onLongClick = { item ->
        copyDeviceToClipboard(item)
      },
      onStarToggle = { item, starred ->
        viewModel.setStarred(item.deviceKey, starred)
      }
    )
    setCompactCards(compactCards, persist = false)
    binding.deviceList.layoutManager = LinearLayoutManager(this)
    binding.deviceList.adapter = adapter
    binding.deviceList.itemAnimator = null

    val sortAdapter = ArrayAdapter(
      this,
      R.layout.spinner_item,
      SortMode.values().map { it.label }
    )
    sortAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
    binding.sortSpinner.adapter = sortAdapter
    binding.sortSpinner.setSelection(SortMode.RECENT.ordinal)
    binding.sortSpinner.onItemSelectedListener =
      object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
          parent: android.widget.AdapterView<*>?,
          view: android.view.View?,
          position: Int,
          id: Long
        ) {
          viewModel.updateSortMode(SortMode.values()[position])
        }

        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
      }

    bindPreferenceControls()

    binding.filterInput.doAfterTextChanged { text ->
      viewModel.updateQuery(text?.toString().orEmpty())
    }
    binding.liveOnly.setOnCheckedChangeListener { _, isChecked ->
      viewModel.setLiveOnly(isChecked)
    }
    binding.starredOnly.setOnCheckedChangeListener { _, isChecked ->
      viewModel.setStarredOnly(isChecked)
    }
    binding.batteryLowOnly.setOnCheckedChangeListener { _, isChecked ->
      viewModel.setBatteryLowOnly(isChecked)
    }

    bindProtocolToggles()
    bindProtocolFilterChips()

    binding.permissionActionButton.setOnClickListener { handlePrimaryAction() }

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
          viewModel.devices.collect { devices ->
            adapter.submitList(devices)
            val empty = devices.isEmpty()
            binding.emptyState.isVisible = empty
            binding.deviceList.isVisible = !empty
          }
        }

        launch {
          viewModel.sdrState.collect { state ->
            updateSdrState(state)
            invalidateOptionsMenu()
          }
        }

        launch {
          viewModel.liveDeviceCount.collect { count ->
            supportActionBar?.subtitle = getString(R.string.live_sensor_count, count)
          }
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    (application as guru.urchin.UrchinApp).sdrController.registerUsbDetection()
    viewModel.refreshScan()
    updateUsbSummary()
  }

  override fun onStop() {
    (application as guru.urchin.UrchinApp).sdrController.unregisterUsbDetection()
    viewModel.pauseScan()
    super.onStop()
  }

  override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
    menuInflater.inflate(R.menu.main_menu, menu)
    syncMenuState(menu)
    return true
  }

  override fun onPrepareOptionsMenu(menu: android.view.Menu?): Boolean {
    syncMenuState(menu)
    return super.onPrepareOptionsMenu(menu)
  }

  override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
    return when (item.itemId) {
      R.id.menu_scan_toggle -> {
        if (viewModel.sdrState.value is SdrState.Scanning) {
          viewModel.stopScan()
        } else {
          viewModel.startScan()
        }
        true
      }
      R.id.menu_alerts -> {
        startActivity(Intent(this, AlertsActivity::class.java))
        true
      }
      R.id.menu_groups -> {
        startActivity(Intent(this, AffinityGroupsActivity::class.java))
        true
      }
      R.id.menu_continuous_scanning -> {
        val enabled = !item.isChecked
        item.isChecked = enabled
        setContinuousScanningEnabled(enabled)
        true
      }
      R.id.menu_diagnostics -> {
        startActivity(Intent(this, DiagnosticsActivity::class.java))
        true
      }
      R.id.menu_compact_cards -> {
        val enabled = !item.isChecked
        item.isChecked = enabled
        setCompactCards(enabled)
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  private fun bindPreferenceControls() {
    bindingPrefs = true
    val source = SdrPreferences.source(this)
    binding.usbSource.isChecked = source == SdrPreferences.SdrSource.USB
    binding.networkSource.isChecked = source == SdrPreferences.SdrSource.NETWORK
    binding.networkHostInput.setText(SdrPreferences.networkHost(this))
    binding.networkPortInput.setText(SdrPreferences.networkPort(this).toString())
    binding.gainInput.setText(SdrPreferences.gain(this)?.toString().orEmpty())
    updateSourceVisibility(source)
    bindingPrefs = false

    binding.sourceGroup.setOnCheckedChangeListener { _, checkedId ->
      if (bindingPrefs) return@setOnCheckedChangeListener
      val nextSource = if (checkedId == R.id.networkSource) {
        SdrPreferences.SdrSource.NETWORK
      } else {
        SdrPreferences.SdrSource.USB
      }
      SdrPreferences.setSource(this, nextSource)
      updateSourceVisibility(nextSource)
      restartIfScanning()
    }

    binding.networkHostInput.doAfterTextChanged { text ->
      if (bindingPrefs) return@doAfterTextChanged
      val host = text?.toString()?.trim().orEmpty()
      if (host.isNotEmpty()) {
        SdrPreferences.setNetworkHost(this, host)
        restartIfScanning(source = SdrPreferences.SdrSource.NETWORK)
      }
    }

    binding.networkPortInput.doAfterTextChanged { text ->
      if (bindingPrefs) return@doAfterTextChanged
      val port = text?.toString()?.toIntOrNull() ?: return@doAfterTextChanged
      SdrPreferences.setNetworkPort(this, port)
      restartIfScanning(source = SdrPreferences.SdrSource.NETWORK)
    }

    binding.gainInput.doAfterTextChanged { text ->
      if (bindingPrefs) return@doAfterTextChanged
      val gain = text?.toString()?.trim()?.takeIf(String::isNotEmpty)?.toIntOrNull()
      SdrPreferences.setGain(this, gain)
      restartIfScanning()
    }
  }

  private fun bindProtocolToggles() {
    val enabled = SdrPreferences.enabledProtocols(this)
    bindingPrefs = true
    binding.protocolTpms.isChecked = "tpms" in enabled
    binding.protocolPocsag.isChecked = "pocsag" in enabled
    binding.protocolAdsb.isChecked = "adsb" in enabled
    binding.protocolUat.isChecked = "uat" in enabled
    binding.protocolP25.isChecked = "p25" in enabled
    when (SdrPreferences.frequencyMhz(this)) {
      315 -> binding.freq315.isChecked = true
      else -> binding.freq433.isChecked = true
    }
    updateUatPortVisibility()
    updateP25PortVisibility()
    updateTpmsFreqVisibility()
    updateHoppingWarning()
    binding.uatPortInput.setText(SdrPreferences.uatNetworkPort(this).toString())
    binding.p25PortInput.setText(SdrPreferences.p25NetworkPort(this).toString())
    bindingPrefs = false

    val protocolToggleListener = android.widget.CompoundButton.OnCheckedChangeListener { _, _ ->
      if (bindingPrefs) return@OnCheckedChangeListener
      val protocols = mutableSetOf<String>()
      if (binding.protocolTpms.isChecked) protocols.add("tpms")
      if (binding.protocolPocsag.isChecked) protocols.add("pocsag")
      if (binding.protocolAdsb.isChecked) protocols.add("adsb")
      if (binding.protocolUat.isChecked) protocols.add("uat")
      if (binding.protocolP25.isChecked) protocols.add("p25")
      if (protocols.isEmpty()) {
        protocols.add("tpms")
        bindingPrefs = true
        binding.protocolTpms.isChecked = true
        bindingPrefs = false
        android.widget.Toast.makeText(this, getString(R.string.protocol_required), android.widget.Toast.LENGTH_SHORT).show()
      }
      SdrPreferences.setEnabledProtocols(this, protocols)
      updateUatPortVisibility()
      updateP25PortVisibility()
      updateTpmsFreqVisibility()
      updateHoppingWarning()
      restartIfScanning()
    }
    binding.protocolTpms.setOnCheckedChangeListener(protocolToggleListener)
    binding.protocolPocsag.setOnCheckedChangeListener(protocolToggleListener)
    binding.protocolAdsb.setOnCheckedChangeListener(protocolToggleListener)
    binding.protocolUat.setOnCheckedChangeListener(protocolToggleListener)
    binding.protocolP25.setOnCheckedChangeListener(protocolToggleListener)

    binding.tpmsFreqGroup.setOnCheckedChangeListener { _, checkedId ->
      if (bindingPrefs) return@setOnCheckedChangeListener
      val mhz = if (checkedId == R.id.freq315) 315 else 433
      SdrPreferences.setFrequencyMhz(this, mhz)
      restartIfScanning()
    }

    binding.uatPortInput.doAfterTextChanged { text ->
      if (bindingPrefs) return@doAfterTextChanged
      val port = text?.toString()?.toIntOrNull() ?: return@doAfterTextChanged
      SdrPreferences.setUatNetworkPort(this, port)
      restartIfScanning(source = SdrPreferences.SdrSource.NETWORK)
    }

    binding.p25PortInput.doAfterTextChanged { text ->
      if (bindingPrefs) return@doAfterTextChanged
      val port = text?.toString()?.toIntOrNull() ?: return@doAfterTextChanged
      SdrPreferences.setP25NetworkPort(this, port)
      restartIfScanning(source = SdrPreferences.SdrSource.NETWORK)
    }
  }

  private fun updateUatPortVisibility() {
    val uatChecked = binding.protocolUat.isChecked
    val isNetwork = SdrPreferences.source(this) == SdrPreferences.SdrSource.NETWORK
    binding.uatPortLayout.isVisible = uatChecked && isNetwork
  }

  private fun updateP25PortVisibility() {
    val p25Checked = binding.protocolP25.isChecked
    val isNetwork = SdrPreferences.source(this) == SdrPreferences.SdrSource.NETWORK
    binding.p25PortLayout.isVisible = p25Checked && isNetwork
  }

  private fun updateTpmsFreqVisibility() {
    val show = binding.protocolTpms.isChecked &&
      SdrPreferences.source(this) == SdrPreferences.SdrSource.USB
    binding.tpmsFreqGroup.isVisible = show
  }

  private fun updateHoppingWarning() {
    val isUsb = SdrPreferences.source(this) == SdrPreferences.SdrSource.USB
    var frequencyCount = 0
    if (binding.protocolTpms.isChecked) frequencyCount++
    if (binding.protocolPocsag.isChecked) frequencyCount++
    if (binding.protocolAdsb.isChecked) frequencyCount++
    if (binding.protocolUat.isChecked) frequencyCount++
    // P25 uses its own dongle/binary, excluded from frequency count
    binding.hoppingWarning.isVisible = isUsb && frequencyCount > 1
  }

  private fun bindProtocolFilterChips() {
    binding.protocolChips.setOnCheckedStateChangeListener { _, checkedIds ->
      val protocol = when {
        R.id.chipTpms in checkedIds -> "tpms"
        R.id.chipPocsag in checkedIds -> "pocsag"
        R.id.chipAdsb in checkedIds -> "adsb"
        R.id.chipUat in checkedIds -> "uat"
        R.id.chipP25 in checkedIds -> "p25"
        else -> null
      }
      viewModel.setProtocolFilter(protocol)
    }
  }

  private fun updateSourceVisibility(source: SdrPreferences.SdrSource) {
    binding.networkConfigGroup.isVisible = source == SdrPreferences.SdrSource.NETWORK
    binding.usbHardwareGroup.isVisible = source == SdrPreferences.SdrSource.USB
    updateUatPortVisibility()
    updateP25PortVisibility()
    updateTpmsFreqVisibility()
    updateHoppingWarning()
    updateUsbSummary()
  }

  private fun restartIfScanning(source: SdrPreferences.SdrSource? = null) {
    if (viewModel.sdrState.value !is SdrState.Scanning) {
      updateUsbSummary()
      return
    }
    if (source != null && SdrPreferences.source(this) != source) {
      return
    }
    viewModel.stopScan()
    viewModel.startScan()
  }

  private fun updateUsbSummary() {
    val source = SdrPreferences.source(this)
    val device = SdrUsbDetector.findSdrDevice(this)
    val missingTools = SdrRuntimeInspector.missingRequiredToolLabels(
      this,
      SdrPreferences.enabledProtocols(this)
    )
    binding.hardwareSummary.text = when {
      source == SdrPreferences.SdrSource.NETWORK -> getString(R.string.network_bridge_summary)
      device != null && missingTools != null -> getString(
        R.string.usb_detected_missing_tools_summary,
        SdrUsbDetector.deviceDescription(device.usbDevice),
        missingTools
      )
      device != null -> SdrUsbDetector.deviceDescription(device.usbDevice)
      source == SdrPreferences.SdrSource.USB -> {
        val unsupported = SdrRuntimeInspector.firstUnsupportedUsbSummary(this)
        if (unsupported != null) {
          getString(R.string.usb_unsupported_summary, unsupported)
        } else {
          getString(R.string.usb_waiting_summary)
        }
      }
      else -> getString(R.string.network_bridge_summary)
    }
  }

  private fun toggleFiltersDrawer() {
    if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
      binding.drawerLayout.closeDrawer(GravityCompat.START)
    } else {
      binding.drawerLayout.openDrawer(GravityCompat.START)
    }
  }

  private fun syncMenuState(menu: android.view.Menu?) {
    menu ?: return
    menu.findItem(R.id.menu_continuous_scanning)?.isChecked = continuousScanningEnabled
    menu.findItem(R.id.menu_compact_cards)?.isChecked = compactCards
    menu.findItem(R.id.menu_version)?.title = appVersionInfo.menuLabel
    val scanToggle = menu.findItem(R.id.menu_scan_toggle)
    if (viewModel.sdrState.value is SdrState.Scanning) {
      scanToggle?.title = getString(R.string.scan_stop)
    } else {
      scanToggle?.title = getString(R.string.scan_start)
    }
  }

  private fun setCompactCards(enabled: Boolean, persist: Boolean = true) {
    compactCards = enabled
    adapter.setCompactMode(enabled)
    if (persist) {
      MainDisplayPreferences.setCompactDeviceCards(this, enabled)
    }
  }

  private fun setContinuousScanningEnabled(enabled: Boolean) {
    continuousScanningEnabled = enabled
    if (enabled) {
      ContinuousScanService.start(this)
    } else {
      ContinuousScanService.stop(this)
    }
  }

  private fun updateSdrState(state: SdrState) {
    updateUsbSummary()
    when (state) {
      SdrState.Idle -> {
        binding.scanStatus.text = getString(R.string.scan_inactive)
        binding.permissionHint.isVisible = true
        binding.permissionHint.text = getString(R.string.scan_idle_detail)
        binding.permissionActionButton.isVisible = true
        binding.permissionActionButton.text = getString(R.string.scan_start)
      }
      SdrState.Scanning -> {
        binding.scanStatus.text = getString(R.string.scan_active)
        binding.permissionHint.isVisible = true
        binding.permissionHint.text = getString(
          R.string.scan_active_detail,
          if (SdrPreferences.source(this) == SdrPreferences.SdrSource.NETWORK) {
            getString(R.string.network_source_label)
          } else {
            getString(R.string.usb_source_label)
          }
        )
        binding.permissionActionButton.isVisible = true
        binding.permissionActionButton.text = getString(R.string.scan_stop)
      }
      SdrState.UsbNotConnected -> {
        binding.scanStatus.text = getString(R.string.scan_waiting)
        binding.permissionHint.isVisible = true
        binding.permissionHint.text = getString(R.string.usb_missing_detail)
        binding.permissionActionButton.isVisible = true
        binding.permissionActionButton.text = getString(R.string.retry_scan)
        binding.drawerLayout.openDrawer(GravityCompat.START)
      }
      SdrState.UsbPermissionDenied -> {
        binding.scanStatus.text = getString(R.string.scan_error)
        binding.permissionHint.isVisible = true
        binding.permissionHint.text = getString(R.string.usb_permission_denied_detail)
        binding.permissionActionButton.isVisible = true
        binding.permissionActionButton.text = getString(R.string.retry_scan)
        binding.drawerLayout.openDrawer(GravityCompat.START)
      }
      is SdrState.Error -> {
        binding.scanStatus.text = getString(R.string.scan_error)
        binding.permissionHint.isVisible = true
        binding.permissionHint.text = state.message
        binding.permissionActionButton.isVisible = true
        binding.permissionActionButton.text = getString(R.string.retry_scan)
        binding.drawerLayout.openDrawer(GravityCompat.START)
      }
    }
  }

  private fun handlePrimaryAction() {
    when (viewModel.sdrState.value) {
      SdrState.Scanning -> viewModel.stopScan()
      else -> viewModel.startScan()
    }
  }

  private fun copyDeviceToClipboard(item: DeviceListItem) {
    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clipText = buildString {
      appendLine("Sensor: ${item.displayTitle}")
      item.sensorId?.let { appendLine("Sensor ID: $it") }
      item.vendorName?.let { appendLine("Vendor: $it") }
      appendLine(Formatters.formatRssi(item.lastRssi))
      appendLine("Last Seen: ${Formatters.formatTimestamp(item.sortTimestamp)}")
      appendLine("Sightings: ${item.sightingsCount}")
      appendLine("Battery: ${if (item.batteryLow) "Low" else "OK/Unknown"}")
    }.trimEnd()
    val clip = android.content.ClipData.newPlainText("Sensor Info", clipText)
    clipboard.setPrimaryClip(clip)
    android.widget.Toast.makeText(this, getString(R.string.copied_to_clipboard_simple), android.widget.Toast.LENGTH_SHORT).show()
  }
}
