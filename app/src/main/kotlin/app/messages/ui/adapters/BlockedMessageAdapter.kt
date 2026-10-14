package app.messages.ui.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.messages.R
import app.messages.data.db.BlockedMessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlockedMessageAdapter(
    private val onMessageClick: (BlockedMessageEntity) -> Unit
) : ListAdapter<BlockedMessageEntity, BlockedMessageAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumber: TextView = view.findViewById(R.id.tvNumber)
        val tvBody: TextView = view.findViewById(R.id.tvBody)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = getItem(position)
        holder.tvNumber.text = msg.address
        holder.tvBody.text = msg.body
        holder.tvDate.text = formatTimestamp(msg.date)
        
        holder.itemView.setOnClickListener {
            onMessageClick(msg)
        }
    }

    private fun formatTimestamp(timeMs: Long): String {
        return if (DateUtils.isToday(timeMs)) {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timeMs))
        } else {
            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timeMs))
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<BlockedMessageEntity>() {
            override fun areItemsTheSame(oldItem: BlockedMessageEntity, newItem: BlockedMessageEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: BlockedMessageEntity, newItem: BlockedMessageEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
}
