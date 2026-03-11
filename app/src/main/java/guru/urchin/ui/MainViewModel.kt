package guru.urchin.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import guru.urchin.UrchinApp
import guru.urchin.sdr.SdrPreferences
import guru.urchin.sdr.SdrState
import guru.urchin.util.Formatters
import guru.urchin.util.SensorMetadataParser
import guru.urchin.util.SensorPresentationBuilder

class MainViewModel(app: Application) : AndroidViewModel(app) {
  private val urchinApp = app as UrchinApp
  private val repository = urchinApp.repository
  private val sdrController = urchinApp.sdrController

  private data class FilterState(
    val query: String = "",
    val sort: SortMode = SortMode.RECENT,
    val liveOnly: Boolean = false,
    val starredOnly: Boolean = false,
    val batteryLowOnly: Boolean = false,
    val protocol: String? = null
  )

  private val filterState = MutableStateFlow(FilterState())

  private val liveTicker = flow {
    while (true) {
      emit(System.currentTimeMillis())
      delay(LiveDeviceWindow.TICK_MS)
    }
  }
    .onStart { emit(System.currentTimeMillis()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())

  private val observedDevices = repository.observeDevices()
    .conflate()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val sdrState: StateFlow<SdrState> = sdrController.sdrState

  private val devicesFlow = observedDevices.map { devices ->
    devices.map { device ->
      val metadata = SensorMetadataParser.parse(device.lastMetadataJson)
      val presentation = SensorPresentationBuilder.build(device)
      val metaParts = buildList {
        if (presentation.listSummary.isNotBlank()) {
          add(presentation.listSummary)
        }
        add("Seen ${Formatters.formatTimestamp(device.lastSeen)}")
        add(Formatters.formatSightingsCount(device.sightingsCount))
      }
      val sensorId = metadata.tpmsSensorId
        ?: metadata.pocsagCapCode
        ?: metadata.adsbIcao
        ?: metadata.p25UnitId
      DeviceListItem(
        deviceKey = device.deviceKey,
        displayName = device.displayName,
        displayTitle = presentation.title,
        metaLine = metaParts.joinToString(" • "),
        searchText = presentation.searchText,
        sortTimestamp = device.lastSeen,
        lastSeen = device.lastSeen,
        lastRssi = device.lastRssi,
        sightingsCount = device.sightingsCount,
        starred = device.starred,
        sensorId = sensorId,
        vendorName = metadata.vendorName,
        batteryLow = metadata.tpmsBatteryOk == false,
        protocolType = presentation.protocolType
      )
    }
  }

  val devices: StateFlow<List<DeviceListItem>> = combine(
    devicesFlow, filterState, liveTicker
  ) { list, filter, now ->
    list.asSequence()
      .filter { item ->
        (filter.query.isBlank() ||
          item.searchText.contains(filter.query, ignoreCase = true) ||
          item.sensorId?.contains(filter.query, ignoreCase = true) == true ||
          item.vendorName?.contains(filter.query, ignoreCase = true) == true) &&
          (!filter.starredOnly || item.starred) &&
          (!filter.batteryLowOnly || item.batteryLow) &&
          (filter.protocol == null || item.protocolType == filter.protocol) &&
          (!filter.liveOnly || LiveDeviceWindow.isLive(item.lastSeen, now))
      }
      .sortedWith(
        when (filter.sort) {
          SortMode.RECENT -> compareByDescending { it.sortTimestamp }
          SortMode.STRONGEST -> compareByDescending { it.lastRssi }
          SortMode.NAME -> compareBy { it.displayTitle.lowercase() }
        }
      )
      .toList()
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val liveDeviceCount: StateFlow<Int> = observedDevices
    .combine(liveTicker) { devices, now ->
      devices.count { LiveDeviceWindow.isLive(it.lastSeen, now) }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

  fun updateQuery(query: String) {
    filterState.update { it.copy(query = query) }
  }

  fun updateSortMode(mode: SortMode) {
    filterState.update { it.copy(sort = mode) }
  }

  fun setLiveOnly(live: Boolean) {
    filterState.update { it.copy(liveOnly = live) }
  }

  fun setStarredOnly(starred: Boolean) {
    filterState.update { it.copy(starredOnly = starred) }
  }

  fun setBatteryLowOnly(enabled: Boolean) {
    filterState.update { it.copy(batteryLowOnly = enabled) }
  }

  fun setProtocolFilter(protocol: String?) {
    filterState.update { it.copy(protocol = protocol) }
  }

  fun setStarred(deviceKey: String, starred: Boolean) {
    viewModelScope.launch {
      repository.setStarred(deviceKey, starred)
    }
  }

  fun startScan() {
    SdrPreferences.setEnabled(getApplication(), true)
    sdrController.startSdr()
  }

  fun stopScan() {
    SdrPreferences.setEnabled(getApplication(), false)
    sdrController.stopSdr()
  }

  fun pauseScan() {
    sdrController.stopSdr()
  }

  fun refreshScan() {
    if (SdrPreferences.isEnabled(getApplication())) {
      sdrController.startSdr()
    }
  }

  override fun onCleared() {
    pauseScan()
    super.onCleared()
  }
}
