package guru.urchin.ui

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import guru.urchin.R
import guru.urchin.UrchinApp
import guru.urchin.util.WindowInsetsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

/**
 * Displays per-protocol 24-hour activity histograms for pattern-of-life analysis.
 * Shows which hours of the day each protocol has the most activity, revealing
 * operational schedules, commute patterns, and surveillance windows.
 */
class TimelineActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(getColor(R.color.urchin_background))
    }

    val toolbar = MaterialToolbar(this).apply {
      setBackgroundColor(getColor(R.color.urchin_surface))
      title = getString(R.string.activity_timeline)
      setTitleTextColor(getColor(R.color.urchin_on_surface))
      setNavigationIconTint(getColor(R.color.urchin_secondary))
    }
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    root.addView(toolbar, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    ))
    WindowInsetsHelper.applyToolbarInsets(toolbar)

    val scrollView = ScrollView(this)
    val content = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(32, 32, 32, 32)
    }
    scrollView.addView(content)
    root.addView(scrollView, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.MATCH_PARENT
    ))

    setContentView(root)

    lifecycleScope.launch {
      loadTimeline(content)
    }
  }

  override fun onSupportNavigateUp(): Boolean {
    finish()
    return true
  }

  private suspend fun loadTimeline(container: LinearLayout) {
    val app = application as UrchinApp
    val sightings = withContext(Dispatchers.IO) {
      app.database.sightingDao().getSightingsAfter(0)
    }

    if (sightings.isEmpty()) {
      val empty = TextView(this).apply {
        text = getString(R.string.export_no_data)
        setTextColor(getColor(R.color.urchin_on_background))
        gravity = Gravity.CENTER
        setPadding(0, 64, 0, 0)
      }
      container.addView(empty)
      return
    }

    // Group by protocol, then build 24-hour histograms
    val byProtocol = sightings.groupBy { it.protocolType ?: "unknown" }
    val calendar = Calendar.getInstance(TimeZone.getDefault())

    // Global histogram first
    addHistogramSection(container, "All Protocols", sightings.map { it.timestamp }, calendar)

    // Per-protocol histograms
    for ((protocol, protocolSightings) in byProtocol.entries.sortedByDescending { it.value.size }) {
      addHistogramSection(
        container,
        protocol.uppercase(),
        protocolSightings.map { it.timestamp },
        calendar
      )
    }
  }

  private fun addHistogramSection(
    container: LinearLayout,
    label: String,
    timestamps: List<Long>,
    calendar: Calendar
  ) {
    val header = TextView(this).apply {
      text = "$label (${timestamps.size} observations)"
      setTextColor(getColor(R.color.urchin_secondary))
      textSize = 16f
      setPadding(0, 32, 0, 8)
    }
    container.addView(header)

    val hourCounts = IntArray(24)
    for (ts in timestamps) {
      calendar.timeInMillis = ts
      hourCounts[calendar.get(Calendar.HOUR_OF_DAY)]++
    }

    val histogram = ActivityHistogramView(this).apply {
      setData(hourCounts)
    }
    container.addView(histogram, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      400
    ))
  }
}
