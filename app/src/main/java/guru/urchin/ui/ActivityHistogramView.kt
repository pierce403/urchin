package guru.urchin.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import guru.urchin.R

/**
 * Custom view rendering a 24-hour activity histogram. Each bar represents
 * a one-hour bucket showing the relative observation count for that hour.
 * Used for pattern-of-life analysis — revealing commute times, shift changes,
 * and operational schedules.
 */
class ActivityHistogramView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

  private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.getColor(R.color.urchin_secondary)
    style = Paint.Style.FILL
  }

  private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.getColor(R.color.urchin_stroke_soft)
    style = Paint.Style.FILL
  }

  private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.getColor(R.color.urchin_on_background)
    textSize = 24f
    textAlign = Paint.Align.CENTER
  }

  private var hourCounts = IntArray(24)
  private val rect = RectF()

  fun setData(counts: IntArray) {
    require(counts.size == 24) { "Expected 24 hourly buckets" }
    hourCounts = counts
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val w = width.toFloat()
    val h = height.toFloat()
    val labelHeight = 30f
    val chartHeight = h - labelHeight
    val barWidth = w / 24f
    val gap = 2f
    val maxCount = hourCounts.max().coerceAtLeast(1)

    for (hour in 0 until 24) {
      val x = hour * barWidth
      // Background bar
      rect.set(x + gap, 0f, x + barWidth - gap, chartHeight)
      canvas.drawRoundRect(rect, 3f, 3f, bgPaint)

      // Data bar
      val barHeight = (hourCounts[hour].toFloat() / maxCount) * chartHeight
      rect.set(x + gap, chartHeight - barHeight, x + barWidth - gap, chartHeight)
      canvas.drawRoundRect(rect, 3f, 3f, barPaint)

      // Hour label (every 6 hours)
      if (hour % 6 == 0) {
        canvas.drawText(
          "${hour.toString().padStart(2, '0')}h",
          x + barWidth / 2,
          h - 4f,
          labelPaint
        )
      }
    }
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val desiredHeight = 160
    val width = MeasureSpec.getSize(widthMeasureSpec)
    val height = resolveSize(desiredHeight, heightMeasureSpec)
    setMeasuredDimension(width, height)
  }
}
