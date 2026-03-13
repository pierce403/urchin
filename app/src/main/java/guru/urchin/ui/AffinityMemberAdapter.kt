package guru.urchin.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import guru.urchin.R
import guru.urchin.data.AffinityGroupMemberEntity
import guru.urchin.databinding.ItemAffinityMemberBinding

class AffinityMemberAdapter(
  private val myMemberId: String,
  private val onRevoke: ((AffinityGroupMemberEntity) -> Unit)? = null
) : ListAdapter<AffinityGroupMemberEntity, AffinityMemberAdapter.ViewHolder>(DIFF) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val binding = ItemAffinityMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return ViewHolder(binding)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val member = getItem(position)
    val ctx = holder.itemView.context

    val suffix = when {
      member.memberId == myMemberId -> ctx.getString(R.string.member_you)
      member.revoked -> ctx.getString(R.string.member_revoked)
      else -> ""
    }

    holder.binding.memberName.text = if (suffix.isNotEmpty()) {
      ctx.getString(R.string.member_name_with_suffix, member.displayName, suffix)
    } else {
      member.displayName
    }

    holder.binding.memberStatus.text = ctx.getString(R.string.member_epoch, member.lastSeenEpoch)

    // Long-press to revoke (only for non-self, non-revoked members)
    if (member.memberId != myMemberId && !member.revoked && onRevoke != null) {
      holder.itemView.setOnLongClickListener {
        onRevoke.invoke(member)
        true
      }
    } else {
      holder.itemView.setOnLongClickListener(null)
    }
  }

  class ViewHolder(val binding: ItemAffinityMemberBinding) : RecyclerView.ViewHolder(binding.root)

  companion object {
    private val DIFF = object : DiffUtil.ItemCallback<AffinityGroupMemberEntity>() {
      override fun areItemsTheSame(a: AffinityGroupMemberEntity, b: AffinityGroupMemberEntity) =
        a.groupId == b.groupId && a.memberId == b.memberId
      override fun areContentsTheSame(a: AffinityGroupMemberEntity, b: AffinityGroupMemberEntity) = a == b
    }
  }
}
