package app.messages.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.messages.data.db.MessageEntity
import app.messages.databinding.ItemDateHeaderBinding
import app.messages.databinding.ItemMessageReceivedBinding
import app.messages.databinding.ItemMessageSentBinding
import com.bumptech.glide.Glide
import app.messages.R
import app.messages.utility.SimManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ChatItem {
    data class Message(val msg: MessageEntity) : ChatItem()
    data class DateHeader(val date: String) : ChatItem()
}

class MessageAdapter : ListAdapter<ChatItem, RecyclerView.ViewHolder>(DiffCallback()) {

    private val TYPE_RECEIVED = 1
    private val TYPE_SENT = 2
    private val TYPE_DATE_HEADER = 3

    private var simManager: SimManager? = null
    var onStopClick: ((MessageEntity) -> Unit)? = null
    var onResendClick: ((MessageEntity) -> Unit)? = null
    var onLongClick: ((MessageEntity) -> Unit)? = null
    var onSelectionChanged: ((Int) -> Unit)? = null

    private var isDeliveryReportsEnabled = false
    private val visibilityOverrides = mutableMapOf<Long, Boolean>()

    // ── Selection state ──────────────────────────────────────────────
    private val selectedIds = mutableSetOf<Long>()
    private var isSelectionMode = false

    fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode != enabled) {
            isSelectionMode = enabled
            if (!enabled) {
                val previousSelections = selectedIds.toList()
                selectedIds.clear()
                previousSelections.forEach { id ->
                    val index = currentList.indexOfFirst { it is ChatItem.Message && it.msg.id == id }
                    if (index != -1) notifyItemChanged(index)
                }
            }
        }
    }

    fun toggleSelection(id: Long) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
        val index = currentList.indexOfFirst { it is ChatItem.Message && it.msg.id == id }
        if (index != -1) {
            notifyItemChanged(index)
        }
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun getSelectedIds(): List<Long> = selectedIds.toList()
    fun getSelectedCount(): Int = selectedIds.size
    fun isInSelectionMode(): Boolean = isSelectionMode
    // ────────────────────────────────────────────────────────────────

    fun setDeliveryReportsEnabled(enabled: Boolean) {
        this.isDeliveryReportsEnabled = enabled
    }

    class DiffCallback : DiffUtil.ItemCallback<ChatItem>() {
        override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
            return if (oldItem is ChatItem.Message && newItem is ChatItem.Message) {
                oldItem.msg.id == newItem.msg.id
            } else if (oldItem is ChatItem.DateHeader && newItem is ChatItem.DateHeader) {
                oldItem.date == newItem.date
            } else {
                false
            }
        }

        override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
            return oldItem == newItem
        }
    }

    fun setSimManager(simManager: SimManager) {
        this.simManager = simManager
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ChatItem.DateHeader -> TYPE_DATE_HEADER
            is ChatItem.Message -> {
                if (item.msg.type == 1) TYPE_RECEIVED else TYPE_SENT
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SENT -> {
                val binding =
                    ItemMessageSentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SentViewHolder(binding)
            }

            TYPE_RECEIVED -> {
                val binding = ItemMessageReceivedBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ReceivedViewHolder(binding)
            }

            else -> {
                val binding = ItemDateHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                DateHeaderViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is SentViewHolder -> holder.bind((item as ChatItem.Message).msg)
            is ReceivedViewHolder -> holder.bind((item as ChatItem.Message).msg)
            is DateHeaderViewHolder -> holder.bind((item as ChatItem.DateHeader).date)
        }
    }

    inner class DateHeaderViewHolder(private val binding: ItemDateHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(date: String) {
            binding.tvDate.text = date
        }
    }

    inner class SentViewHolder(private val binding: ItemMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(msg: MessageEntity) {
            // ── Selection highlight ──────────────────────────────────
            val isSelected = selectedIds.contains(msg.id)
            binding.root.setBackgroundColor(
                if (isSelected) 0x335B9CF6 else Color.TRANSPARENT  // ~20% blue tint
            )

            // ── Click / LongClick routing ────────────────────────────
            binding.llMessage.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(msg.id)
                } else {
                    val newVisible = binding.tvTime.visibility != android.view.View.VISIBLE
                    visibilityOverrides[msg.id] = newVisible
                    binding.tvTime.visibility =
                        if (newVisible) android.view.View.VISIBLE else android.view.View.GONE
                }
            }

            binding.llMessage.setOnLongClickListener {
                if (!isSelectionMode) {
                    onLongClick?.invoke(msg)
                }
                true
            }
            // ────────────────────────────────────────────────────────

            if (msg.body.isBlank()) {
                binding.tvMessage.visibility = android.view.View.GONE
            } else {
                binding.tvMessage.visibility = android.view.View.VISIBLE
                binding.tvMessage.text = msg.body
            }

            val isDelivered = msg.status == 6
            val timeToFormat = if (isDelivered) (msg.deliveryTime ?: msg.date) else msg.date
            val timeText = formatTime(timeToFormat)
            binding.tvTime.text = if (isDelivered) "Delivered $timeText" else timeText

            // Only show by default if delivered AND reporting is enabled.
            val defaultVisible = isDelivered && isDeliveryReportsEnabled
            val isVisible = visibilityOverrides[msg.id] ?: defaultVisible
            binding.tvTime.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.GONE

            // Image handling
            if (!msg.imageUri.isNullOrEmpty()) {
                binding.ivMessage.visibility = android.view.View.VISIBLE
                Glide.with(binding.root.context)
                    .load(msg.imageUri)
                    .into(binding.ivMessage)
                
                if (msg.body.isBlank()) {
                    binding.llMessage.background = null
                    binding.llMessage.setPadding(0, 0, 0, 0)
                } else {
                    binding.llMessage.setBackgroundResource(R.drawable.bg_chat_bubble_sent)
                    val pxH = binding.root.context.resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)
                    val pxV = binding.root.context.resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)
                    binding.llMessage.setPadding(pxH, pxV, pxH, pxV)
                }
            } else {
                binding.ivMessage.visibility = android.view.View.GONE
                binding.llMessage.setBackgroundResource(R.drawable.bg_chat_bubble_sent)
                val pxH = binding.root.context.resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)
                val pxV = binding.root.context.resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)
                binding.llMessage.setPadding(pxH, pxV, pxH, pxV)
            }

            // Error handling (Type 5 is failed, Status 3 is Failed)
            if (msg.status == 3 || msg.type == 5) {
                binding.ivFail.visibility = android.view.View.VISIBLE
                binding.ivFail.setOnClickListener {
                    onResendClick?.invoke(msg)
                }
            } else {
                binding.ivFail.visibility = android.view.View.GONE
            }

            // Scheduled handling
            if (msg.status == 4 && msg.scheduledTime != null) {
                binding.llScheduled.visibility = android.view.View.VISIBLE
                binding.tvScheduled.text = "Scheduled for ${TIME_FORMAT.format(Date(msg.scheduledTime))}"
                binding.tvTime.visibility = android.view.View.GONE
            } else {
                binding.llScheduled.visibility = android.view.View.GONE
            }

            // Undo Send (Delaying) handling
            if (msg.status == 5 && msg.scheduledTime != null) {
                binding.llUndoSend.visibility = android.view.View.VISIBLE

                val totalDelay = msg.scheduledTime - msg.date
                val remaining = msg.scheduledTime - System.currentTimeMillis()

                if (remaining > 0) {
                    binding.pbUndoSend.max = 1000
                    val progress = ((totalDelay - remaining).toFloat() / totalDelay * 1000).toInt()
                    binding.pbUndoSend.progress = progress

                    val animator = android.animation.ValueAnimator.ofInt(progress, 1000)
                    animator.duration = remaining
                    animator.interpolator = android.view.animation.LinearInterpolator()
                    animator.addUpdateListener { animation ->
                        binding.pbUndoSend.progress = animation.animatedValue as Int
                    }
                    animator.start()
                    binding.pbUndoSend.tag = animator
                } else {
                    binding.pbUndoSend.progress = 1000
                }

                binding.ivStopUndoSend.setOnClickListener {
                    onStopClick?.invoke(msg)
                }
            } else {
                binding.llUndoSend.visibility = android.view.View.GONE
                (binding.pbUndoSend.tag as? android.animation.ValueAnimator)?.cancel()
            }

            // SIM information display
            val availableSims = simManager?.getAvailableSims() ?: emptyList()
            if (availableSims.size > 1 && msg.simSlot != null && simManager != null) {
                val simInfo = simManager?.getSimForSlotIndex(msg.simSlot)
                if (simInfo != null) {
                    binding.tvSimInfo.text =
                        simManager?.getSimDisplayTextWithCarrier(msg.simSlot, simInfo.carrierName)
                    binding.tvSimInfo.visibility = android.view.View.VISIBLE
                } else {
                    binding.tvSimInfo.text = simManager?.getSimDisplayText(msg.simSlot)
                    binding.tvSimInfo.visibility = android.view.View.VISIBLE
                }
            } else {
                binding.tvSimInfo.visibility = android.view.View.GONE
            }
        }
    }

    inner class ReceivedViewHolder(private val binding: ItemMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(msg: MessageEntity) {
            // ── Selection highlight ──────────────────────────────────
            val isSelected = selectedIds.contains(msg.id)
            binding.root.setBackgroundColor(
                if (isSelected) 0x335B9CF6 else Color.TRANSPARENT  // ~20% blue tint
            )

            // ── Click / LongClick routing ────────────────────────────
            binding.llMessage.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(msg.id)
                } else {
                    val newVisible = binding.tvTime.visibility != android.view.View.VISIBLE
                    visibilityOverrides[msg.id] = newVisible
                    binding.tvTime.visibility =
                        if (newVisible) android.view.View.VISIBLE else android.view.View.GONE
                }
            }

            binding.llMessage.setOnLongClickListener {
                if (!isSelectionMode) {
                    onLongClick?.invoke(msg)
                }
                true
            }
            // ────────────────────────────────────────────────────────

            if (msg.body.isBlank()) {
                binding.tvMessage.visibility = android.view.View.GONE
            } else {
                binding.tvMessage.visibility = android.view.View.VISIBLE
                binding.tvMessage.text = msg.body
            }
            val timeText = formatTime(msg.date)
            binding.tvTime.text = timeText

            // Received messages are hidden by default.
            val isVisible = visibilityOverrides[msg.id] ?: false
            binding.tvTime.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.GONE

            // Image handling
            if (!msg.imageUri.isNullOrEmpty()) {
                binding.ivMessage.visibility = android.view.View.VISIBLE
                Glide.with(binding.root.context)
                    .load(msg.imageUri)
                    .into(binding.ivMessage)
                
                if (msg.body.isBlank()) {
                    binding.llMessage.background = null
                    binding.llMessage.setPadding(0, 0, 0, 0)
                } else {
                    binding.llMessage.setBackgroundResource(R.drawable.bg_chat_bubble_received)
                    val pxH = binding.root.context.resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)
                    val pxV = binding.root.context.resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)
                    binding.llMessage.setPadding(pxH, pxV, pxH, pxV)
                }
            } else {
                binding.ivMessage.visibility = android.view.View.GONE
                binding.llMessage.setBackgroundResource(R.drawable.bg_chat_bubble_received)
                val pxH = binding.root.context.resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)
                val pxV = binding.root.context.resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)
                binding.llMessage.setPadding(pxH, pxV, pxH, pxV)
            }

            // SIM information display
            val availableSims = simManager?.getAvailableSims() ?: emptyList()
            if (availableSims.size > 1 && msg.simSlot != null && simManager != null) {
                val simInfo = simManager?.getSimForSlotIndex(msg.simSlot)
                if (simInfo != null) {
                    binding.tvSimInfo.text =
                        simManager?.getSimDisplayTextWithCarrier(msg.simSlot, simInfo.carrierName)
                    binding.tvSimInfo.visibility = android.view.View.VISIBLE
                    binding.tvSimInfo.setTextColor(
                        simManager?.getSimColor(msg.simSlot) ?: Color.WHITE
                    )
                } else {
                    binding.tvSimInfo.text = simManager?.getSimDisplayText(msg.simSlot)
                    binding.tvSimInfo.visibility = android.view.View.VISIBLE
                    binding.tvSimInfo.setTextColor(
                        simManager?.getSimColor(msg.simSlot) ?: Color.WHITE
                    )
                }
            } else {
                binding.tvSimInfo.visibility = android.view.View.GONE
            }
        }
    }

    private fun formatTime(timeMillis: Long): String {
        return TIME_FORMAT.format(Date(timeMillis))
    }

    companion object {
        private val TIME_FORMAT = SimpleDateFormat("h:mm a", Locale.getDefault())
    }
}
