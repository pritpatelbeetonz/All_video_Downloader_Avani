package app.messages.ui.adapters

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import app.messages.R
import app.messages.data.db.ConversationEntity
import app.messages.databinding.ItemConversationBinding
import app.messages.data.CacheManager
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.MotionEvent
import android.view.ViewConfiguration
import app.messages.enumeration.getSwipeActionColor
import app.messages.enumeration.getSwipeActionIcon
import app.messages.preferences.PreferenceHelper
import kotlin.math.abs

class ConversationAdapter(
    private val onClick: (ConversationEntity) -> Unit,
    private val onRightSwipeActionClick: (ConversationEntity) -> Unit = {},
    private val onLeftSwipeActionClick: (ConversationEntity) -> Unit = {},
    private val isSwipeEnabled: Boolean = true
) : ListAdapter<ConversationEntity, ConversationAdapter.ConversationViewHolder>(DiffCallback()) {

    var onSelectionChanged: ((Int) -> Unit)? = null
    var onLongClick: ((ConversationEntity) -> Unit)? = null
    private val selectedIds = mutableSetOf<Long>()
    private var isSelectionMode = false

    fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode != enabled) {
            isSelectionMode = enabled
            if (!enabled) {
                selectedIds.clear()
            }
            notifyDataSetChanged()
        }
    }

    fun toggleSelection(threadId: Long) {
        if (selectedIds.contains(threadId)) {
            selectedIds.remove(threadId)
        } else {
            selectedIds.add(threadId)
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun getSelectedIds(): List<Long> = selectedIds.toList()
    fun getSelectedCount(): Int = selectedIds.size
    fun isInSelectionMode(): Boolean = isSelectionMode

    init {
        setHasStableIds(true)
    }

    private var cachedContactsMap: Map<String, app.messages.data.model.CachedContactInfo>? = null

    override fun submitList(list: List<ConversationEntity>?) {
        cachedContactsMap = null
        super.submitList(list)
    }

    override fun submitList(list: List<ConversationEntity>?, commitCallback: Runnable?) {
        cachedContactsMap = null
        super.submitList(list, commitCallback)
    }

    private fun getContactsMap(context: android.content.Context): Map<String, app.messages.data.model.CachedContactInfo> {
        var map = cachedContactsMap
        if (map == null) {
            map = CacheManager.getInstance(context).getMemoryCachedContactsMap()
            cachedContactsMap = map
        }
        return map
    }

    class DiffCallback : DiffUtil.ItemCallback<ConversationEntity>() {
        override fun areItemsTheSame(
            oldItem: ConversationEntity,
            newItem: ConversationEntity
        ): Boolean {
            return oldItem.threadId == newItem.threadId
        }

        override fun areContentsTheSame(
            oldItem: ConversationEntity,
            newItem: ConversationEntity
        ): Boolean {
            return oldItem == newItem
        }
    }

    private var cachedLeftSwipeAction: Int = -1
    private var cachedRightSwipeAction: Int = -1

    fun updateTheme(primary: Int, text: Int, grayText: Int, bgColor: Int) {
        cachedLeftSwipeAction = -1
        cachedRightSwipeAction = -1
        expandedViewHolder?.close()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val binding =
            ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ConversationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).threadId
    }

    private var expandedViewHolder: ConversationViewHolder? = null

    inner class ConversationViewHolder(private val binding: ItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var initialX = 0f
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var isDragging = false
        private var isLongClickPerformed = false
        private var currentConversation: ConversationEntity? = null
        private var currentLeftSwipeAction: Int = 0
        private var currentRightSwipeAction: Int = 0
        private val touchSlop = ViewConfiguration.get(binding.root.context).scaledTouchSlop
        private val maxSwipe =
            binding.root.context.resources.getDimension(com.intuit.sdp.R.dimen._70sdp)
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
        private var longClickRunnable: Runnable? = null

        init {
            // Register listeners once per ViewHolder to reduce per-bind allocations and scroll jank.
            binding.foregroundLayer.setOnTouchListener { view, event ->
                if (!isSwipeEnabled) return@setOnTouchListener false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (expandedViewHolder != null && expandedViewHolder != this) {
                            expandedViewHolder?.close()
                        }
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        initialX = view.translationX
                        isDragging = false
                        isLongClickPerformed = false

                        longClickRunnable = Runnable {
                            if (abs(event.rawX - initialTouchX) < touchSlop && abs(event.rawY - initialTouchY) < touchSlop) {
                                isLongClickPerformed = view.performLongClick()
                            }
                        }
                        handler.postDelayed(longClickRunnable!!, ViewConfiguration.getLongPressTimeout().toLong())

                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val diffX = event.rawX - initialTouchX
                        val diffY = event.rawY - initialTouchY

                        if (!isDragging && (abs(diffX) > touchSlop || abs(diffY) > touchSlop)) {
                            longClickRunnable?.let { handler.removeCallbacks(it) }
                            longClickRunnable = null
                            if (abs(diffX) > abs(diffY)) {
                                isDragging = true
                                view.parent.requestDisallowInterceptTouchEvent(true)
                            } else {
                                return@setOnTouchListener false
                            }
                        }

                        if (isDragging) {
                            var newX = initialX + diffX
                            
                            // Restrict swipe based on settings
                            if (currentRightSwipeAction == 0 && newX > 0) newX = 0f
                            if (currentLeftSwipeAction == 0 && newX < 0) newX = 0f
                            
                            if (newX > maxSwipe) newX = maxSwipe
                            if (newX < -maxSwipe) newX = -maxSwipe
                            view.translationX = newX
                            return@setOnTouchListener true
                        }
                        return@setOnTouchListener true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longClickRunnable?.let { handler.removeCallbacks(it) }
                        longClickRunnable = null
                        if (isDragging) {
                            val currentX = view.translationX
                            if (currentX > maxSwipe / 2) {
                                openRight()
                            } else if (currentX < -maxSwipe / 2) {
                                openLeft()
                            } else {
                                close()
                            }
                            isDragging = false
                            return@setOnTouchListener true
                        } else if (event.action == MotionEvent.ACTION_UP) {
                            if (!isLongClickPerformed && abs(event.rawX - initialTouchX) < touchSlop && abs(event.rawY - initialTouchY) < touchSlop) {
                                view.performClick()
                            }
                        }
                        false
                    }

                    else -> false
                }
            }

            binding.foregroundLayer.setOnClickListener {
                if (isSelectionMode) {
                    currentConversation?.let { toggleSelection(it.threadId) }
                } else if (binding.foregroundLayer.translationX != 0f) {
                    close()
                } else {
                    currentConversation?.let(onClick)
                }
            }

            binding.btnRightSwipeAction.setOnClickListener {
                close()
                currentConversation?.let(onRightSwipeActionClick)
            }

            binding.btnLeftSwipeAction.setOnClickListener {
                close()
                currentConversation?.let(onLeftSwipeActionClick)
            }

            binding.foregroundLayer.setOnLongClickListener {
                if (!isSelectionMode) {
                    currentConversation?.let { onLongClick?.invoke(it) }
                    true
                } else false
            }
        }

        fun bind(conn: ConversationEntity) {
            currentConversation = conn
            
            // Selection UI
            val isSelected = selectedIds.contains(conn.threadId)
            binding.selectionBackground.visibility = if (isSelected) View.VISIBLE else View.GONE
            if (isSelected) {
                binding.foregroundLayer.background = null
            } else {
                binding.foregroundLayer.setBackgroundResource(R.drawable.background_conversation)
            }
            binding.ivCheckmark.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            // Force reset state on bind
            binding.foregroundLayer.translationX = 0f
            binding.tvName.text = conn.recipientName ?: conn.address
            
            // Draft logic
            val draftText = app.messages.utility.DraftManager.getDraft(binding.root.context, conn.threadId.toString())
                ?: app.messages.utility.DraftManager.getDraft(binding.root.context, conn.address)
                
            if (!draftText.isNullOrBlank()) {
                val prefix = "Draft: "
                val spannable = android.text.SpannableStringBuilder(prefix + draftText)
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(androidx.core.content.ContextCompat.getColor(binding.root.context, R.color.colorTextRed)),
                    0, prefix.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                binding.tvSnippet.text = spannable
            } else {
                val prefix = if (conn.lastMsgType == 2) "You: " else ""
                binding.tvSnippet.text = "$prefix${conn.body}"
            }
            
            binding.tvDate.text = formatTime(conn.date)

            if (cachedLeftSwipeAction == -1) {
                val ctx = binding.ivLeftSwipeAction.context
                cachedLeftSwipeAction = PreferenceHelper.getInstance(ctx).getLeftSwipeAction()
                cachedRightSwipeAction = PreferenceHelper.getInstance(ctx).getRightSwipeAction()
            }
            currentLeftSwipeAction = cachedLeftSwipeAction
            binding.btnLeftSwipeAction.setBackgroundColor(getSwipeActionColor(currentLeftSwipeAction, binding.ivLeftSwipeAction.context))
            binding.ivLeftSwipeAction.setImageResource(getSwipeActionIcon(currentLeftSwipeAction))

            currentRightSwipeAction = cachedRightSwipeAction
            binding.btnRightSwipeAction.setBackgroundColor(getSwipeActionColor(currentRightSwipeAction, binding.ivRightSwipeAction.context))
            binding.ivRightSwipeAction.setImageResource(getSwipeActionIcon(currentRightSwipeAction))

            // Theming
            if (!conn.read) {
                binding.tvBadge.visibility = android.view.View.VISIBLE
                binding.tvName.typeface = Typeface.DEFAULT_BOLD
                binding.tvSnippet.typeface = Typeface.DEFAULT_BOLD
                binding.tvDate.typeface = Typeface.DEFAULT_BOLD
                binding.tvSnippet.setTextColor(
                    ContextCompat.getColor(
                        binding.tvSnippet.context,
                        R.color.colorTextBlack
                    )
                )
                binding.tvDate.setTextColor(
                    ContextCompat.getColor(
                        binding.tvDate.context,
                        R.color.colorPrimary
                    )
                )
            } else {
                binding.tvBadge.visibility = android.view.View.GONE
                binding.tvName.typeface = Typeface.DEFAULT_BOLD
                binding.tvSnippet.typeface = Typeface.DEFAULT
                binding.tvDate.typeface = Typeface.DEFAULT
                binding.tvSnippet.setTextColor(
                    ContextCompat.getColor(
                        binding.tvSnippet.context,
                        R.color.colorTextGrey
                    )
                )
                binding.tvDate.setTextColor(
                    ContextCompat.getColor(
                        binding.tvDate.context,
                        R.color.colorTextGrey
                    )
                )
            }

            bindAvatars(conn)

            // Swipe/click listeners are initialized once in init.
        }

        fun openRight() {
            binding.foregroundLayer.animate().translationX(maxSwipe).setDuration(200).start()
            expandedViewHolder = this
        }

        fun openLeft() {
            binding.foregroundLayer.animate().translationX(-maxSwipe).setDuration(200).start()
            expandedViewHolder = this
        }

        fun close() {
            binding.foregroundLayer.animate().translationX(0f).setDuration(200).start()
            if (expandedViewHolder == this) expandedViewHolder = null
        }

        private fun bindAvatars(conn: ConversationEntity) {
            val context = itemView.context
            val isGroup = conn.address.contains(",") || (conn.recipientName != null && conn.recipientName.contains(","))
            val contactsMap = getContactsMap(context)

            // Reset visibility
            binding.ivAvatarFull.visibility = View.GONE
            binding.tvInitialsFull.visibility = View.GONE
            binding.flAvatarBack.visibility = View.GONE
            binding.flAvatarFront.visibility = View.GONE

            if (isGroup) {
                val addresses = conn.address.split(",").filter { it.isNotBlank() }
                if (addresses.isNotEmpty()) {
                    binding.flAvatarBack.visibility = View.VISIBLE
                    bindCircleAvatar(addresses[0], binding.ivAvatarBack, binding.tvInitialsBack, contactsMap)
                    
                    if (addresses.size > 1) {
                        binding.flAvatarFront.visibility = View.VISIBLE
                        bindCircleAvatar(addresses[1], binding.ivAvatarFront, binding.tvInitialsFront, contactsMap)
                    }
                } else {
                    // Fallback for group with no addresses
                    binding.ivAvatarFull.visibility = View.VISIBLE
                    binding.ivAvatarFull.setImageResource(R.drawable.ic_group)
                    binding.ivAvatarFull.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.black))
                }
            } else {
                // Individual conversation
                val normAddr = normalizeNumber(conn.address)
                val last10 = if (normAddr.length >= 10) normAddr.takeLast(10) else normAddr
                val cachedInfo = contactsMap[conn.address] ?: contactsMap[normAddr] ?: contactsMap[last10]
                val photoUri = conn.photoUri ?: cachedInfo?.photoUri
                val name = conn.recipientName ?: cachedInfo?.name ?: conn.address

                if (!photoUri.isNullOrEmpty()) {
                    binding.ivAvatarFull.visibility = View.VISIBLE
                    binding.tvInitialsFull.visibility = View.GONE
                    binding.ivAvatarFull.imageTintList = null
                    Glide.with(context).load(photoUri).circleCrop().into(binding.ivAvatarFull)
                } else {
                    binding.ivAvatarFull.visibility = View.VISIBLE
                    binding.tvInitialsFull.visibility = View.VISIBLE
                    binding.tvInitialsFull.text = getInitials(name)
                    applyAvatarColors(binding.tvInitialsFull, binding.ivAvatarFull, name)
                }
            }
        }
        
        private fun applyAvatarColors(tv: android.widget.TextView, iv: com.google.android.material.imageview.ShapeableImageView, name: String) {
            val initialChar = getInitials(name).firstOrNull() ?: 'A'
            val (bgInt, textInt) = PREPARSED_AVATAR_COLORS[abs(initialChar.hashCode()) % PREPARSED_AVATAR_COLORS.size]
            val colorStateList = ColorStateList.valueOf(bgInt)
            
            iv.setImageResource(R.drawable.background_conversation)
            iv.imageTintList = colorStateList
            iv.backgroundTintList = colorStateList
            
            tv.backgroundTintList = colorStateList
            tv.setTextColor(textInt)
        }

        private fun bindCircleAvatar(
            address: String, 
            iv: com.google.android.material.imageview.ShapeableImageView, 
            tv: android.widget.TextView,
            contactsMap: Map<String, app.messages.data.model.CachedContactInfo>
        ) {
            val info = contactsMap[address] 
                ?: contactsMap[normalizeNumber(address)] 
                ?: contactsMap[if (address.length >= 10) address.takeLast(10) else address]
            
            val photoUri = info?.photoUri
            val name = info?.name ?: address

            // iv (ShapeableImageView) always stays visible to provide stroke & background
            iv.visibility = View.VISIBLE

            if (photoUri != null) {
                tv.visibility = View.GONE
                iv.imageTintList = null
                Glide.with(itemView.context)
                    .load(photoUri)
                    .circleCrop()
                    .into(iv)
            } else {
                tv.visibility = View.VISIBLE
                iv.setImageDrawable(null) // Clear any previous photo
                tv.text = getInitials(name).take(1)
                applyAvatarColors(tv, iv, name)
            }
        }

        private fun isHumanName(name: String): Boolean {
            if (name.contains(Regex("[a-zA-Z]"))) {
                val digitCount = name.count { it.isDigit() }
                if (digitCount > 4 && name.contains("-")) return false
                return true
            }
            return false
        }

        private fun normalizeNumber(number: String): String {
            val sb = StringBuilder(number.length)
            for (c in number) {
                if (c.isDigit()) sb.append(c)
            }
            return sb.toString()
        }

        private fun getInitials(name: String): String {
            if (name.isEmpty()) return ""
            val trimmed = name.trim()
            val spaceIdx = trimmed.indexOf(' ')
            return if (spaceIdx != -1 && spaceIdx < trimmed.length - 1) {
                "${trimmed[0]}${trimmed[spaceIdx + 1]}".uppercase()
            } else {
                trimmed[0].toString().uppercase()
            }
        }
    }

    private fun formatTime(timeMillis: Long): String {
        val date = Date(timeMillis)
        val now = System.currentTimeMillis()
        val diffDays = (now - timeMillis) / (24 * 60 * 60 * 1000L)
        return if (diffDays == 0L && isSameDay(timeMillis, now)) {
            TIME_FORMAT.format(date)
        } else {
            DATE_FORMAT.format(date)
        }
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = t1 }
        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = t2 }
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    companion object {
        private val TIME_FORMAT = SimpleDateFormat("h:mm a", Locale.getDefault())
        private val DATE_FORMAT = SimpleDateFormat("MMM d", Locale.getDefault())
        
        private val PREPARSED_AVATAR_COLORS = listOf(
            Pair(android.graphics.Color.parseColor("#E3F2FD"), android.graphics.Color.parseColor("#1976D2")), // Blue
            Pair(android.graphics.Color.parseColor("#FBE9E7"), android.graphics.Color.parseColor("#D84315")), // Deep Orange
            Pair(android.graphics.Color.parseColor("#E8F5E9"), android.graphics.Color.parseColor("#388E3C")), // Green
            Pair(android.graphics.Color.parseColor("#FCE4EC"), android.graphics.Color.parseColor("#C2185B")), // Pink
            Pair(android.graphics.Color.parseColor("#FFF3E0"), android.graphics.Color.parseColor("#F57C00")), // Orange
            Pair(android.graphics.Color.parseColor("#F3E5F5"), android.graphics.Color.parseColor("#7B1FA2"))  // Purple
        )
    }
}
