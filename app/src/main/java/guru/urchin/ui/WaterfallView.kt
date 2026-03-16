package guru.urchin.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Real-time waterfall/spectrogram display for RF spectrum visualization.
 * Renders FFT magnitude data as a scrolling heatmap where:
 * - X axis = frequency bins across the tuned bandwidth
 * - Y axis = time (newest at top, scrolling downward)
 * - Color = signal power (dark blue = noise floor, red/white = strong signal)
 */
class WaterfallView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

  private var bitmap: Bitmap? = null
  private var bitmapCanvas: Canvas? = null
  private val paint = Paint()
  private var currentRow = 0
  private var fftSize = 512

  private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    textSize = 24f
  }

  var centerFreqMhz: Double = 0.0
  var bandwidthMhz: Double = 2.0

  fun setFftSize(size: Int) {
    fftSize = size
    resetBitmap()
  }

  fun pushFftRow(magnitudes: FloatArray) {
    val bmp = bitmap ?: return
    val w = bmp.width
    val h = bmp.height

    // Scroll existing content down by 1 pixel
    val temp = Bitmap.createBitmap(bmp, 0, 0, w, h - 1)
    bitmapCanvas?.drawBitmap(temp, 0f, 1f, null)
    temp.recycle()

    // Draw new row at top
    val binWidth = w.toFloat() / magnitudes.size
    for (i in magnitudes.indices) {
      val x = (i * binWidth).toInt()
      val xEnd = ((i + 1) * binWidth).toInt().coerceAtMost(w)
      val color = magnitudeToColor(magnitudes[i])
      paint.color = color
      bitmapCanvas?.drawRect(x.toFloat(), 0f, xEnd.toFloat(), 1f, paint)
    }

    currentRow++
    postInvalidate()
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    resetBitmap()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

    // Frequency labels
    if (centerFreqMhz > 0) {
      val startFreq = centerFreqMhz - bandwidthMhz / 2
      val endFreq = centerFreqMhz + bandwidthMhz / 2
      canvas.drawText(
        String.format("%.1f MHz", startFreq),
        4f, height - 4f, labelPaint
      )
      canvas.drawText(
        String.format("%.1f MHz", centerFreqMhz),
        width / 2f - 40f, height - 4f, labelPaint
      )
      canvas.drawText(
        String.format("%.1f MHz", endFreq),
        width - 120f, height - 4f, labelPaint
      )
    }
  }

  private fun resetBitmap() {
    val w = width.coerceAtLeast(1)
    val h = height.coerceAtLeast(1)
    bitmap?.recycle()
    bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
      it.eraseColor(Color.BLACK)
      bitmapCanvas = Canvas(it)
    }
    currentRow = 0
  }

  private fun magnitudeToColor(mag: Float): Int {
    // Map dB value to color: -120 dB (noise floor) → dark blue, 0 dB → white
    val normalized = ((mag + 120f) / 120f).coerceIn(0f, 1f)
    return when {
      normalized < 0.25f -> {
        val t = normalized / 0.25f
        Color.rgb(0, 0, (t * 180).toInt())
      }
      normalized < 0.5f -> {
        val t = (normalized - 0.25f) / 0.25f
        Color.rgb(0, (t * 255).toInt(), 180)
      }
      normalized < 0.75f -> {
        val t = (normalized - 0.5f) / 0.25f
        Color.rgb((t * 255).toInt(), 255, (180 * (1 - t)).toInt())
      }
      else -> {
        val t = (normalized - 0.75f) / 0.25f
        Color.rgb(255, (255 * (1 - t * 0.5f)).toInt(), (t * 255).toInt())
      }
    }
  }
}
