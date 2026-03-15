package guru.urchin.sdr

import guru.urchin.util.DebugLog
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Records raw IQ samples to files for offline analysis, forensic evidence
 * capture, or re-processing with different decoders. Supports manual and
 * alert-triggered recording with configurable duration and a pre-trigger
 * circular buffer.
 */
class IqRecorder(private val storageDir: File) {

  data class RecordingMetadata(
    val fileName: String,
    val centerFreqMhz: Double,
    val sampleRate: Int,
    val gain: Int?,
    val startTimestamp: Long,
    val durationMs: Long,
    val triggerReason: String,
    val fileSizeBytes: Long
  )

  private val recording = AtomicBoolean(false)
  private var outputStream: OutputStream? = null
  private var currentMetadata: RecordingMetadataBuilder? = null
  private val preBuffer = CircularByteBuffer(PRE_BUFFER_SIZE)

  val isRecording: Boolean get() = recording.get()

  fun startRecording(
    centerFreqMhz: Double,
    sampleRate: Int,
    gain: Int?,
    triggerReason: String,
    maxDurationMs: Long = DEFAULT_MAX_DURATION_MS
  ): Boolean {
    if (!recording.compareAndSet(false, true)) return false

    storageDir.mkdirs()
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "urchin_iq_${timestamp}_${centerFreqMhz}MHz.raw"
    val file = File(storageDir, fileName)

    try {
      val fos = FileOutputStream(file)
      outputStream = fos

      // Flush pre-buffer first
      val preData = preBuffer.drain()
      if (preData.isNotEmpty()) {
        fos.write(preData)
      }

      currentMetadata = RecordingMetadataBuilder(
        fileName = fileName,
        centerFreqMhz = centerFreqMhz,
        sampleRate = sampleRate,
        gain = gain,
        startTimestamp = System.currentTimeMillis(),
        maxDurationMs = maxDurationMs,
        triggerReason = triggerReason
      )

      DebugLog.log("IQ recording started: $fileName ($triggerReason)")
      return true
    } catch (e: Exception) {
      DebugLog.log("IQ recording failed to start: ${e.message}")
      recording.set(false)
      return false
    }
  }

  fun stopRecording(): RecordingMetadata? {
    if (!recording.compareAndSet(true, false)) return null

    val os = outputStream ?: return null
    val meta = currentMetadata ?: return null

    try {
      os.flush()
      os.close()
    } catch (e: Exception) {
      DebugLog.log("IQ recording close error: ${e.message}")
    }

    outputStream = null
    val file = File(storageDir, meta.fileName)
    val result = RecordingMetadata(
      fileName = meta.fileName,
      centerFreqMhz = meta.centerFreqMhz,
      sampleRate = meta.sampleRate,
      gain = meta.gain,
      startTimestamp = meta.startTimestamp,
      durationMs = System.currentTimeMillis() - meta.startTimestamp,
      triggerReason = meta.triggerReason,
      fileSizeBytes = file.length()
    )
    currentMetadata = null
    DebugLog.log("IQ recording stopped: ${result.fileName} (${result.fileSizeBytes} bytes, ${result.durationMs} ms)")
    return result
  }

  fun feedSamples(data: ByteArray, offset: Int = 0, length: Int = data.size) {
    if (recording.get()) {
      val meta = currentMetadata
      if (meta != null && System.currentTimeMillis() - meta.startTimestamp > meta.maxDurationMs) {
        stopRecording()
        return
      }
      try {
        outputStream?.write(data, offset, length)
      } catch (e: Exception) {
        DebugLog.log("IQ recording write error: ${e.message}")
        stopRecording()
      }
    } else {
      // Feed into pre-trigger circular buffer
      preBuffer.write(data, offset, length)
    }
  }

  private class RecordingMetadataBuilder(
    val fileName: String,
    val centerFreqMhz: Double,
    val sampleRate: Int,
    val gain: Int?,
    val startTimestamp: Long,
    val maxDurationMs: Long,
    val triggerReason: String
  )

  private class CircularByteBuffer(private val capacity: Int) {
    private val buffer = ByteArray(capacity)
    private var writePos = 0
    private var filled = 0

    @Synchronized
    fun write(data: ByteArray, offset: Int, length: Int) {
      for (i in 0 until length) {
        buffer[writePos] = data[offset + i]
        writePos = (writePos + 1) % capacity
        if (filled < capacity) filled++
      }
    }

    @Synchronized
    fun drain(): ByteArray {
      if (filled == 0) return ByteArray(0)
      val result = ByteArray(filled)
      val startPos = if (filled < capacity) 0 else writePos
      for (i in 0 until filled) {
        result[i] = buffer[(startPos + i) % capacity]
      }
      filled = 0
      writePos = 0
      return result
    }
  }

  companion object {
    private const val PRE_BUFFER_SIZE = 2 * 1024 * 1024 // 2 MB (~0.5s at 2 MS/s)
    private const val DEFAULT_MAX_DURATION_MS = 30_000L
  }
}
