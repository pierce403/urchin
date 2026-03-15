package guru.urchin.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import guru.urchin.R
import guru.urchin.data.AffinityGroupEntity
import guru.urchin.databinding.ItemAffinityGroupBinding
import java.text.DateFormat
import java.util.Date

class AffinityGroupAdapter(
  private val onClick: (AffinityGroupEntity) -> Unit
) : ListAdapter<AffinityGroupEntity, AffinityGroupAdapter.ViewHolder>(DIFF) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val binding = ItemAffinityGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return ViewHolder(binding)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val group = getItem(position)
    holder.binding.groupName.text = group.groupName
    holder.binding.groupInfo.text = holder.itemView.context.getString(
      R.string.group_list_info,
      DateFormat.getDateInstance().format(Date(group.createdAt)),
      group.keyEpoch
    )
    holder.itemView.setOnClickListener { onClick(group) }
  }

  class ViewHolder(val binding: ItemAffinityGroupBinding) : RecyclerView.ViewHolder(binding.root)

  companion object {
    private val DIFF = object : DiffUtil.ItemCallback<AffinityGroupEntity>() {
      override fun areItemsTheSame(a: AffinityGroupEntity, b: AffinityGroupEntity) = a.groupId == b.groupId
      override fun areContentsTheSame(a: AffinityGroupEntity, b: AffinityGroupEntity) = a == b
    }
  }
}
