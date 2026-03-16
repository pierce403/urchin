package guru.urchin.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Polar compass rose displaying RSSI-vs-heading data for direction finding.
 * Each data point is a (heading, RSSI) pair; the view shows a polar plot
 * where stronger signals extend further from center, enabling visual
 * identification of the signal's bearing.
 */
class DirectionFinderView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

  data class HeadingSample(val headingDeg: Float, val rssi: Float)

  private val samples = mutableListOf<HeadingSample>()
  private var peakBearingDeg: Float? = null

  private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(80, 255, 255, 255)
    style = Paint.Style.STROKE
    strokeWidth = 1f
  }

  private val dataFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(100, 0, 200, 255)
    style = Paint.Style.FILL
  }

  private val dataStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(0, 200, 255)
    style = Paint.Style.STROKE
    strokeWidth = 2f
  }

  private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.RED
    style = Paint.Style.STROKE
    strokeWidth = 3f
  }

  private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    textSize = 28f
    textAlign = Paint.Align.CENTER
  }

  private val path = Path()

  fun addSample(headingDeg: Float, rssi: Float) {
    samples.add(HeadingSample(headingDeg, rssi))
    computePeakBearing()
    invalidate()
  }

  fun clearSamples() {
    samples.clear()
    peakBearingDeg = null
    invalidate()
  }

  private fun computePeakBearing() {
    if (samples.size < 3) {
      peakBearingDeg = null
      return
    }

    // Weighted circular mean: weight = normalized RSSI
    val minRssi = samples.minOf { it.rssi }
    val maxRssi = samples.maxOf { it.rssi }
    val range = (maxRssi - minRssi).coerceAtLeast(1f)

    var sinSum = 0.0
    var cosSum = 0.0
    for (sample in samples) {
      val weight = ((sample.rssi - minRssi) / range).toDouble()
      val rad = Math.toRadians(sample.headingDeg.toDouble())
      sinSum += weight * sin(rad)
      cosSum += weight * cos(rad)
    }

    val bearing = Math.toDegrees(kotlin.math.atan2(sinSum, cosSum)).toFloat()
    peakBearingDeg = if (bearing < 0) bearing + 360 else bearing
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val cx = width / 2f
    val cy = height / 2f
    val radius = minOf(cx, cy) - 40f

    canvas.drawColor(Color.BLACK)

    // Draw compass grid
    for (r in 1..4) {
      canvas.drawCircle(cx, cy, radius * r / 4f, gridPaint)
    }
    for (deg in 0 until 360 step 30) {
      val rad = Math.toRadians(deg.toDouble() - 90)
      canvas.drawLine(
        cx, cy,
        cx + (radius * cos(rad)).toFloat(),
        cy + (radius * sin(rad)).toFloat(),
        gridPaint
      )
    }

    // Cardinal labels
    canvas.drawText("N", cx, cy - radius - 8, labelPaint)
    canvas.drawText("S", cx, cy + radius + 28, labelPaint)
    canvas.drawText("E", cx + radius + 16, cy + 10, labelPaint)
    canvas.drawText("W", cx - radius - 16, cy + 10, labelPaint)

    // Draw data polygon
    if (samples.isNotEmpty()) {
      val minRssi = samples.minOf { it.rssi }
      val maxRssi = samples.maxOf { it.rssi }
      val range = (maxRssi - minRssi).coerceAtLeast(1f)

      // Sort by heading for continuous polygon
      val sorted = samples.sortedBy { it.headingDeg }

      path.reset()
      for ((i, sample) in sorted.withIndex()) {
        val normalized = (sample.rssi - minRssi) / range
        val r = radius * 0.2f + radius * 0.8f * normalized
        val rad = Math.toRadians(sample.headingDeg.toDouble() - 90)
        val x = cx + (r * cos(rad)).toFloat()
        val y = cy + (r * sin(rad)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
      }
      path.close()
      canvas.drawPath(path, dataFillPaint)
      canvas.drawPath(path, dataStrokePaint)
    }

    // Draw peak bearing indicator
    peakBearingDeg?.let { bearing ->
      val rad = Math.toRadians(bearing.toDouble() - 90)
      canvas.drawLine(
        cx, cy,
        cx + (radius * cos(rad)).toFloat(),
        cy + (radius * sin(rad)).toFloat(),
        peakPaint
      )
      canvas.drawText(
        "${bearing.toInt()}\u00B0",
        cx + ((radius + 20) * cos(rad)).toFloat(),
        cy + ((radius + 20) * sin(rad)).toFloat(),
        labelPaint
      )
    }
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val size = minOf(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
    setMeasuredDimension(size, size)
  }
}
