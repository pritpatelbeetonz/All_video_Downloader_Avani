package app.messages.ui.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import app.messages.R
import app.messages.data.db.LauncherScheduledMessageEntity
import app.messages.databinding.ItemScheduledMessageBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScheduleAdapter(
    private var contactsMap: Map<String, app.messages.data.model.CachedContactInfo>,
    private val onItemClick: (LauncherScheduledMessageEntity) -> Unit
) : ListAdapter<LauncherScheduledMessageEntity, ScheduleAdapter.ViewHolder>(DiffCallback()) {

    fun updateContacts(newMap: Map<String, app.messages.data.model.CachedContactInfo>) {
        this.contactsMap = newMap
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScheduledMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemScheduledMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(msg: LauncherScheduledMessageEntity) {
            binding.tvBody.text = msg.body
            binding.tvDateTime.text = formatDateTime(msg.scheduledTime)

            binding.root.setOnClickListener {
                onItemClick(msg)
            }
        }
    }

    private fun formatDateTime(timeMillis: Long): String {
        val date = Date(timeMillis)
        val cal = java.util.Calendar.getInstance()
        cal.time = date
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val suffix = when {
            day in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
        val monthSdf = SimpleDateFormat("MMM", Locale.getDefault())
        val timeSdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        
        return "Sending on ${monthSdf.format(date)} ${day}${suffix}, ${timeSdf.format(date)}"
    }

    class DiffCallback : DiffUtil.ItemCallback<LauncherScheduledMessageEntity>() {
        override fun areItemsTheSame(oldItem: LauncherScheduledMessageEntity, newItem: LauncherScheduledMessageEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: LauncherScheduledMessageEntity, newItem: LauncherScheduledMessageEntity): Boolean {
            return oldItem == newItem
        }
    }
}
