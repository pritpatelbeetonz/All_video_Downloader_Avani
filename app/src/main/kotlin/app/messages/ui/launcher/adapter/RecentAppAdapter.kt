package app.messages.ui.launcher.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.messages.data.model.AppInfo
import app.messages.databinding.ItemRecentAppIconBinding

class RecentAppAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo, View) -> Unit = { _, _ -> }
) : ListAdapter<AppInfo, RecentAppAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRecentAppIconBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemRecentAppIconBinding) : RecyclerView.ViewHolder(binding.root) {
        private var iconJob: kotlinx.coroutines.Job? = null

        fun bind(item: AppInfo) {
            iconJob?.cancel()
            iconJob = AppIconCache.bind(binding.ivIcon, item)
            binding.tvLabel.text = item.label

            binding.root.setOnClickListener { onClick(item) }
            binding.root.setOnLongClickListener {
                onLongClick(item, binding.ivIcon)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean =
                oldItem == newItem
        }
    }
}
