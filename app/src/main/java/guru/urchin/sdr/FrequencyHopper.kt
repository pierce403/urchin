package guru.urchin.sdr

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import guru.urchin.util.DebugLog

/**
 * Time-division frequency rotation for single-dongle multi-protocol capture.
 * Cycles through the configured frequencies, running an [Rtl433Process] at each
 * one for [dwellTimeMs] (default 5 s) before stopping and tuning to the next.
 * Falls back to a single persistent process when only one frequency is configured.
 */
class FrequencyHopper(
  private val context: Context,
  private val frequencies: List<Int>,
  private val dwellTimeMs: Long = 5000L,
  private val hardwareProfile: SdrHardwareProfile,
  private val gain: Int?,
  private val onReading: (SdrReading) -> Unit,
  private val onError: (String) -> Unit
) {
  private var hopJob: Job? = null
  private var currentProcess: Rtl433Process? = null
  private var currentFrequencyIndex = 0

  val currentFrequencyHz: Int
    get() = frequencies.getOrElse(currentFrequencyIndex) { frequencies.first() }

  val isRunning: Boolean
    get() = hopJob?.isActive == true

  fun start(scope: CoroutineScope) {
    if (frequencies.isEmpty()) {
      onError("No frequencies configured for hopping")
      return
    }
    if (frequencies.size == 1) {
      startSingleFrequency(scope, frequencies.first())
      return
    }

    DebugLog.log("FrequencyHopper starting with ${frequencies.size} frequencies, dwell=${dwellTimeMs}ms")
    hopJob = scope.launch {
      while (isActive) {
        val freq = frequencies[currentFrequencyIndex]
        DebugLog.log("FrequencyHopper tuning to $freq Hz")
        startSingleFrequency(this, freq)
        delay(dwellTimeMs)
        stopCurrentProcess()
        currentFrequencyIndex = (currentFrequencyIndex + 1) % frequencies.size
      }
    }
  }

  fun stop() {
    hopJob?.cancel()
    hopJob = null
    stopCurrentProcess()
  }

  private fun startSingleFrequency(scope: CoroutineScope, frequencyHz: Int) {
    stopCurrentProcess()
    val process = Rtl433Process(context)
    currentProcess = process
    process.start(
      scope = scope,
      hardwareProfile = hardwareProfile,
      frequencyHz = frequencyHz,
      gain = gain,
      onReading = onReading,
      onError = onError
    )
  }

  private fun stopCurrentProcess() {
    currentProcess?.stop()
    currentProcess = null
  }
}
