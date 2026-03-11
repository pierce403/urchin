package guru.urchin.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import guru.urchin.data.SightingEntity
import guru.urchin.databinding.ItemSightingBinding
import guru.urchin.util.Formatters
import guru.urchin.util.SensorMetadataParser

class SightingAdapter : ListAdapter<SightingEntity, SightingAdapter.SightingViewHolder>(DiffCallback) {
  init { setHasStableIds(true) }

  override fun getItemId(position: Int): Long = getItem(position).id

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SightingViewHolder {
    val binding = ItemSightingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return SightingViewHolder(binding)
  }

  override fun onBindViewHolder(holder: SightingViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  class SightingViewHolder(
    private val binding: ItemSightingBinding
  ) : RecyclerView.ViewHolder(binding.root) {
    fun bind(item: SightingEntity) {
      val metadata = SensorMetadataParser.parse(item.metadataJson)
      binding.sightingTimestamp.text = Formatters.formatTimestamp(item.timestamp)
      binding.sightingRssi.text = Formatters.formatRssi(item.rssi)
      binding.sightingMeta.text = buildSightingMeta(metadata)
    }

    private fun buildSightingMeta(metadata: guru.urchin.util.SensorMetadata): String {
      val protocol = metadata.protocolType ?: "tpms"
      return when (protocol) {
        "pocsag" -> buildList {
          metadata.pocsagCapCode?.let { add("CAP: $it") }
          metadata.pocsagFunctionCode?.let { add("Func: $it") }
          metadata.pocsagMessage?.let { msg ->
            add(msg.take(80) + if (msg.length > 80) "…" else "")
          }
        }
        "adsb" -> buildList {
          metadata.adsbIcao?.let { add("ICAO: $it") }
          metadata.adsbCallsign?.let { add(it) }
          metadata.adsbAltitude?.let { add("${it} ft") }
          metadata.adsbSpeed?.let { add(String.format("%.0f kts", it)) }
          metadata.adsbSquawk?.let { add("Sq: $it") }
        }
        "p25" -> buildList {
          metadata.p25UnitId?.let { add("Unit: $it") }
          metadata.p25TalkGroupId?.let { add("TG: $it") }
          metadata.p25Nac?.let { add("NAC: $it") }
        }
        else -> buildList {
          metadata.tpmsPressureKpa?.let { add(Formatters.formatPressure(it)) }
          metadata.tpmsTemperatureC?.let { add(Formatters.formatTemperature(it)) }
          metadata.tpmsBatteryOk?.let { add(if (it) "Battery OK" else "Battery low") }
          metadata.tpmsSnr?.let { add(String.format("SNR %.1f dB", it)) }
        }
      }.joinToString(" • ")
    }
  }

  companion object {
    private val DiffCallback = object : DiffUtil.ItemCallback<SightingEntity>() {
      override fun areItemsTheSame(oldItem: SightingEntity, newItem: SightingEntity): Boolean {
        return oldItem.id == newItem.id
      }

      override fun areContentsTheSame(oldItem: SightingEntity, newItem: SightingEntity): Boolean {
        return oldItem == newItem
      }
    }
  }
}
