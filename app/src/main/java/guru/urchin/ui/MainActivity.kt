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
import guru.urchin.sdr.NetworkProbe
import guru.urchin.sdr.ProbeTarget
import guru.urchin.sdr.ProbeResult
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

    binding.testConnectionButton.setOnClickListener { runConnectionTest() }
  }

  private fun bindProtocolToggles() {
    val enabled = SdrPreferences.enabledProtocols(this)
    bindingPrefs = true
    binding.protocolTpms.isChecked = "tpms" in enabled
    binding.protocolPocsag.isChecked = "pocsag" in enabled
    binding.protocolAdsb.isChecked = "adsb" in enabled
    binding.protocolUat.isChecked = "uat" in enabled
    binding.protocolP25.isChecked = "p25" in enabled
    binding.protocolLoRaWan.isChecked = "lorawan" in enabled
    binding.protocolMeshtastic.isChecked = "meshtastic" in enabled
    binding.protocolWmbus.isChecked = "wmbus" in enabled
    binding.protocolZwave.isChecked = "zwave" in enabled
    binding.protocolSidewalk.isChecked = "sidewalk" in enabled
    when (SdrPreferences.frequencyMhz(this)) {
      315 -> binding.freq315.isChecked = true
      else -> binding.freq433.isChecked = true
    }
    updateUatPortVisibility()
    updateP25PortVisibility()
    updateLoRaWanPortVisibility()
    updateWmbusPortVisibility()
    updateZwavePortVisibility()
    updateSidewalkPortVisibility()
    updateTpmsFreqVisibility()
    updateHoppingWarning()
    binding.uatPortInput.setText(SdrPreferences.uatNetworkPort(this).toString())
    binding.p25PortInput.setText(SdrPreferences.p25NetworkPort(this).toString())
    binding.lorawanPortInput.setText(SdrPreferences.lorawanNetworkPort(this).toString())
    binding.wmbusPortInput.setText(SdrPreferences.wmbusNetworkPort(this).toString())
    binding.zwavePortInput.setText(SdrPreferences.zwaveNetworkPort(this).toString())
    binding.sidewalkPortInput.setText(SdrPreferences.sidewalkNetworkPort(this).toString())
    bindingPrefs = false

    val protocolToggleListener = android.widget.CompoundButton.OnCheckedChangeListener { _, _ ->
      if (bindingPrefs) return@OnCheckedChangeListener
      val protocols = mutableSetOf<String>()
      if (binding.protocolTpms.isChecked) protocols.add("tpms")
      if (binding.protocolPocsag.isChecked) protocols.add("pocsag")
      if (binding.protocolAdsb.isChecked) protocols.add("adsb")
      if (binding.protocolUat.isChecked) protocols.add("uat")
      if (binding.protocolP25.isChecked) protocols.add("p25")
      if (binding.protocolLoRaWan.isChecked) protocols.add("lorawan")
      if (binding.protocolMeshtastic.isChecked) protocols.add("meshtastic")
      if (binding.protocolWmbus.isChecked) protocols.add("wmbus")
      if (binding.protocolZwave.isChecked) protocols.add("zwave")
      if (binding.protocolSidewalk.isChecked) protocols.add("sidewalk")
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
      updateLoRaWanPortVisibility()
      updateWmbusPortVisibility()
      updateZwavePortVisibility()
      updateSidewalkPortVisibility()
      updateTpmsFreqVisibility()
      updateHoppingWarning()
      restartIfScanning()
    }
    binding.protocolTpms.setOnCheckedChangeListener(protocolToggleListener)
    binding.protocolPocsag.setOnCheckedChangeListener(protocolToggleListener)
    binding.protocolAdsb.setOnCheckedChangeListener(protocolToggleListener)
    binding.protocolUat.setOnCheckedChangeListener(protocolToggleListener)
    binding.protocolP25.setOnCheckedChangeListener(protocolToggleListener)
    binding.protocolLoRaWan.setOnCheckedChangeListener(protocolToggleListener)
    binding.protocolMeshtastic.setOnCheckedChangeListener(protocolToggleListener)
    binding.protocolWmbus.setOnCheckedChangeListener(protocolToggleListener)
    binding.protocolZwave.setOnCheckedChangeListener(protocolToggleListener)
    binding.protocolSidewalk.setOnCheckedChangeListener(protocolToggleListener)

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

    binding.lorawanPortInput.doAfterTextChanged { text ->
      if (bindingPrefs) return@doAfterTextChanged
      val port = text?.toString()?.toIntOrNull() ?: return@doAfterTextChanged
      SdrPreferences.setLorawanNetworkPort(this, port)
      restartIfScanning(source = SdrPreferences.SdrSource.NETWORK)
    }

    binding.wmbusPortInput.doAfterTextChanged { text ->
      if (bindingPrefs) return@doAfterTextChanged
      val port = text?.toString()?.toIntOrNull() ?: return@doAfterTextChanged
      SdrPreferences.setWmbusNetworkPort(this, port)
      restartIfScanning(source = SdrPreferences.SdrSource.NETWORK)
    }

    binding.zwavePortInput.doAfterTextChanged { text ->
      if (bindingPrefs) return@doAfterTextChanged
      val port = text?.toString()?.toIntOrNull() ?: return@doAfterTextChanged
      SdrPreferences.setZwaveNetworkPort(this, port)
      restartIfScanning(source = SdrPreferences.SdrSource.NETWORK)
    }

    binding.sidewalkPortInput.doAfterTextChanged { text ->
      if (bindingPrefs) return@doAfterTextChanged
      val port = text?.toString()?.toIntOrNull() ?: return@doAfterTextChanged
      SdrPreferences.setSidewalkNetworkPort(this, port)
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

  private fun updateLoRaWanPortVisibility() {
    val lorawanChecked = binding.protocolLoRaWan.isChecked
    val isNetwork = SdrPreferences.source(this) == SdrPreferences.SdrSource.NETWORK
    binding.lorawanPortLayout.isVisible = lorawanChecked && isNetwork
  }

  private fun updateWmbusPortVisibility() {
    val wmbusChecked = binding.protocolWmbus.isChecked
    val isNetwork = SdrPreferences.source(this) == SdrPreferences.SdrSource.NETWORK
    binding.wmbusPortLayout.isVisible = wmbusChecked && isNetwork
  }

  private fun updateZwavePortVisibility() {
    val zwaveChecked = binding.protocolZwave.isChecked
    val isNetwork = SdrPreferences.source(this) == SdrPreferences.SdrSource.NETWORK
    binding.zwavePortLayout.isVisible = zwaveChecked && isNetwork
  }

  private fun updateSidewalkPortVisibility() {
    val sidewalkChecked = binding.protocolSidewalk.isChecked
    val isNetwork = SdrPreferences.source(this) == SdrPreferences.SdrSource.NETWORK
    binding.sidewalkPortLayout.isVisible = sidewalkChecked && isNetwork
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
        R.id.chipLoRaWan in checkedIds -> "lorawan"
        R.id.chipMeshtastic in checkedIds -> "meshtastic"
        R.id.chipWmbus in checkedIds -> "wmbus"
        R.id.chipZwave in checkedIds -> "zwave"
        R.id.chipSidewalk in checkedIds -> "sidewalk"
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
    updateLoRaWanPortVisibility()
    updateWmbusPortVisibility()
    updateZwavePortVisibility()
    updateSidewalkPortVisibility()
    updateTpmsFreqVisibility()
    updateHoppingWarning()
    updateUsbSummary()
    val isNetwork = source == SdrPreferences.SdrSource.NETWORK
    binding.protocolLoRaWan.isEnabled = isNetwork
    binding.protocolMeshtastic.isEnabled = isNetwork
    binding.protocolWmbus.isEnabled = isNetwork
    binding.protocolZwave.isEnabled = isNetwork
    binding.protocolSidewalk.isEnabled = isNetwork
    binding.rakHatLabel.isEnabled = isNetwork

    // Auto-uncheck RAK HAT protocols when switching to USB (they're network-only)
    if (!isNetwork) {
      val rakHatCheckboxes = listOf(
        binding.protocolLoRaWan,
        binding.protocolMeshtastic,
        binding.protocolWmbus,
        binding.protocolZwave,
        binding.protocolSidewalk
      )
      val anyWasChecked = rakHatCheckboxes.any { it.isChecked }
      if (anyWasChecked) {
        bindingPrefs = true
        rakHatCheckboxes.forEach { it.isChecked = false }
        bindingPrefs = false
        val protocols = SdrPreferences.enabledProtocols(this).toMutableSet()
        protocols.removeAll(setOf("lorawan", "meshtastic", "wmbus", "zwave", "sidewalk"))
        if (protocols.isEmpty()) protocols.add("tpms")
        SdrPreferences.setEnabledProtocols(this, protocols)
      }
    }
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

  private fun runConnectionTest() {
    val host = binding.networkHostInput.text?.toString()?.trim().orEmpty()
    if (host.isEmpty()) {
      android.widget.Toast.makeText(this, getString(R.string.network_host), android.widget.Toast.LENGTH_SHORT).show()
      return
    }
    if (!host.matches(Regex("[a-zA-Z0-9._:-]+"))) {
      android.widget.Toast.makeText(this, "Invalid host format", android.widget.Toast.LENGTH_SHORT).show()
      return
    }

    val targets = mutableListOf<ProbeTarget>()
    if (binding.protocolTpms.isChecked || binding.protocolPocsag.isChecked) {
      val port = binding.networkPortInput.text?.toString()?.toIntOrNull()
        ?: SdrPreferences.networkPort(this)
      val label = buildString {
        if (binding.protocolTpms.isChecked) append("TPMS")
        if (binding.protocolPocsag.isChecked) {
          if (isNotEmpty()) append("/")
          append("POCSAG")
        }
      }
      targets.add(ProbeTarget(label, host, port))
    }
    if (binding.protocolAdsb.isChecked) {
      targets.add(ProbeTarget("ADS-B", host, SdrPreferences.adsbNetworkPort(this)))
    }
    if (binding.protocolUat.isChecked) {
      val port = binding.uatPortInput.text?.toString()?.toIntOrNull()
        ?: SdrPreferences.uatNetworkPort(this)
      targets.add(ProbeTarget("UAT", host, port))
    }
    if (binding.protocolP25.isChecked) {
      val port = binding.p25PortInput.text?.toString()?.toIntOrNull()
        ?: SdrPreferences.p25NetworkPort(this)
      targets.add(ProbeTarget("P25", host, port))
    }
    if (binding.protocolLoRaWan.isChecked || binding.protocolMeshtastic.isChecked) {
      val port = binding.lorawanPortInput.text?.toString()?.toIntOrNull()
        ?: SdrPreferences.lorawanNetworkPort(this)
      val label = buildString {
        if (binding.protocolLoRaWan.isChecked) append("LoRaWAN")
        if (binding.protocolMeshtastic.isChecked) {
          if (isNotEmpty()) append("/")
          append("Meshtastic")
        }
      }
      targets.add(ProbeTarget(label, host, port))
    }
    if (binding.protocolWmbus.isChecked) {
      val port = binding.wmbusPortInput.text?.toString()?.toIntOrNull()
        ?: SdrPreferences.wmbusNetworkPort(this)
      targets.add(ProbeTarget("wM-Bus", host, port))
    }
    if (binding.protocolZwave.isChecked) {
      val port = binding.zwavePortInput.text?.toString()?.toIntOrNull()
        ?: SdrPreferences.zwaveNetworkPort(this)
      targets.add(ProbeTarget("Z-Wave", host, port))
    }
    if (binding.protocolSidewalk.isChecked) {
      val port = binding.sidewalkPortInput.text?.toString()?.toIntOrNull()
        ?: SdrPreferences.sidewalkNetworkPort(this)
      targets.add(ProbeTarget("Sidewalk", host, port))
    }

    if (targets.isEmpty()) {
      android.widget.Toast.makeText(this, getString(R.string.no_protocols_enabled), android.widget.Toast.LENGTH_SHORT).show()
      return
    }

    binding.testConnectionButton.isEnabled = false
    binding.testConnectionButton.text = getString(R.string.testing_connection)

    lifecycleScope.launch {
      try {
        val results = NetworkProbe.probeAll(targets)
        results.forEach { result ->
          val status = if (result.reachable) "reachable" else "unreachable: ${result.errorMessage}"
          guru.urchin.util.DebugLog.log("Connection test: ${result.target.label} ${result.target.host}:${result.target.port} $status")
        }
        showConnectionResults(results)
      } catch (e: Exception) {
        guru.urchin.util.DebugLog.log("Connection test failed: ${e.message}")
        android.widget.Toast.makeText(this@MainActivity, "Test failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
      } finally {
        binding.testConnectionButton.isEnabled = true
        binding.testConnectionButton.text = getString(R.string.test_connection)
      }
    }
  }

  private fun showConnectionResults(results: List<ProbeResult>) {
    val message = results.joinToString("\n\n") { result ->
      val icon = if (result.reachable) "\u2705" else "\u274C"
      val status = if (result.reachable) "Reachable" else result.errorMessage ?: "Connection failed"
      "$icon ${result.target.label} (${result.target.host}:${result.target.port})\n     $status"
    }
    androidx.appcompat.app.AlertDialog.Builder(this)
      .setTitle(getString(R.string.connection_test_title))
      .setMessage(message)
      .setPositiveButton(android.R.string.ok, null)
      .show()
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
