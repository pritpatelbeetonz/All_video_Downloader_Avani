package app.messages.ui.launcher.adapter

import android.annotation.SuppressLint
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.messages.data.model.AppInfo
import app.messages.databinding.ItemAppIconBinding
import app.messages.databinding.ItemAppIconCompactBinding
import app.messages.databinding.ItemDockIconBinding
import app.messages.databinding.ItemWorkspaceIconBinding

class AppIconAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo, View) -> Unit = { _, _ -> },
    private val isDarkText: Boolean = false
) : ListAdapter<AppInfo, AppIconAdapter.VH>(DIFF) {

    var highlightedInitial: Char? = null
        private set

    var onDimmedClick: (() -> Unit)? = null

    fun setHighlightedLetter(letter: Char?) {
        val upper = letter?.uppercaseChar()
        if (highlightedInitial != upper) {
            highlightedInitial = upper
            notifyDataSetChanged()
        }
    }

    fun clearHighlightedLetter() {
        if (highlightedInitial != null) {
            highlightedInitial = null
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAppIconBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemAppIconBinding) : RecyclerView.ViewHolder(binding.root) {
        private var iconJob: kotlinx.coroutines.Job? = null
        fun bind(item: AppInfo) {
            iconJob?.cancel()
            iconJob = AppIconCache.bind(binding.ivIcon, item)
            binding.tvLabel.text = item.label
            val prefs = app.messages.preferences.PreferenceHelper.getInstance(binding.root.context)
            val hideLabels = prefs.getDrawerHideLabels()
            binding.tvLabel.visibility = if (hideLabels) View.GONE else View.VISIBLE
            binding.tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.getDrawerTextSize().toFloat())

            val iconSizeDp = prefs.getDrawerIconSize()
            val px = (iconSizeDp * binding.root.resources.displayMetrics.density).toInt()
            val params = binding.ivIcon.layoutParams
            params.width = px
            params.height = px
            binding.ivIcon.layoutParams = params

            if (isDarkText) {
                binding.tvLabel.setTextColor(android.graphics.Color.parseColor("#333333"))
            } else {
                binding.tvLabel.setTextColor(android.graphics.Color.WHITE)
            }

            binding.root.visibility = View.VISIBLE
            binding.root.alpha = 1.0f

            binding.root.setOnClickListener {
                it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(60).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    onClick(item)
                }.start()
            }
            binding.root.setOnLongClickListener {
                onLongClick(item, it)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo) =
                oldItem.packageName == newItem.packageName && oldItem.activityName == newItem.activityName

            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo) =
                oldItem.label == newItem.label &&
                    oldItem.isFavorite == newItem.isFavorite &&
                    oldItem.canUninstall == newItem.canUninstall
        }
    }
}

class CompactAppIconAdapter(
    private val onClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, CompactAppIconAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAppIconCompactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemAppIconCompactBinding) : RecyclerView.ViewHolder(binding.root) {
        private var iconJob: kotlinx.coroutines.Job? = null
        fun bind(item: AppInfo) {
            iconJob?.cancel()
            iconJob = AppIconCache.bind(binding.ivIcon, item)
            binding.tvLabel.text = item.label
            binding.root.setOnTouchListener(DisallowParentInterceptTouchListener)
            binding.root.setOnClickListener {
                it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                    onClick(item)
                }.start()
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo) =
                oldItem.packageName == newItem.packageName &&
                    oldItem.activityName == newItem.activityName

            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo) =
                oldItem.label == newItem.label &&
                    oldItem.packageName == newItem.packageName
        }
    }
}

class DockAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo, View) -> Unit = { _, _ -> }
) : ListAdapter<AppInfo, DockAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDockIconBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemDockIconBinding) : RecyclerView.ViewHolder(binding.root) {
        private var iconJob: kotlinx.coroutines.Job? = null
        fun bind(item: AppInfo) {
            iconJob?.cancel()
            iconJob = AppIconCache.bind(binding.ivIcon, item)
            val prefs = app.messages.preferences.PreferenceHelper.getInstance(binding.root.context)
            val iconSizeDp = prefs.getHomeIconSize()
            val px = (iconSizeDp * binding.root.resources.displayMetrics.density).toInt()
            val params = binding.ivIcon.layoutParams
            params.width = px
            params.height = px
            binding.ivIcon.layoutParams = params

            binding.root.setOnTouchListener(DisallowParentInterceptTouchListener)
            binding.root.setOnClickListener {
                it.animate().scaleX(0.88f).scaleY(0.88f).setDuration(50).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                    onClick(item)
                }.start()
            }
            binding.root.setOnLongClickListener {
                onLongClick(item, it)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo) =
                oldItem.label == newItem.label &&
                    oldItem.isFavorite == newItem.isFavorite &&
                    oldItem.canUninstall == newItem.canUninstall
        }
    }
}

class WorkspaceIconAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo, View) -> Unit = { _, _ -> }
) : ListAdapter<AppInfo, WorkspaceIconAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemWorkspaceIconBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemWorkspaceIconBinding) : RecyclerView.ViewHolder(binding.root) {
        private var iconJob: kotlinx.coroutines.Job? = null
        fun bind(item: AppInfo) {
            iconJob?.cancel()
            iconJob = AppIconCache.bind(binding.ivIcon, item)
            binding.tvLabel.text = item.label
            val prefs = app.messages.preferences.PreferenceHelper.getInstance(binding.root.context)
            val hideLabels = prefs.getHomeHideLabels()
            binding.tvLabel.visibility = if (hideLabels) View.GONE else View.VISIBLE
            binding.tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.getHomeTextSize().toFloat())

            val iconSizeDp = prefs.getHomeIconSize()
            val px = (iconSizeDp * binding.root.resources.displayMetrics.density).toInt()
            val params = binding.ivIcon.layoutParams
            params.width = px
            params.height = px
            binding.ivIcon.layoutParams = params

            binding.root.setOnTouchListener(DisallowParentInterceptTouchListener)
            binding.root.setOnClickListener {
                it.animate().scaleX(0.88f).scaleY(0.88f).setDuration(50).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                    onClick(item)
                }.start()
            }
            binding.root.setOnLongClickListener {
                onLongClick(item, it)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo) =
                oldItem.label == newItem.label &&
                    oldItem.isFavorite == newItem.isFavorite &&
                    oldItem.canUninstall == newItem.canUninstall
        }
    }
}

/**
 * Keeps swipe-up / ViewPager parents from stealing the gesture so long-press can fire.
 */
@SuppressLint("ClickableViewAccessibility")
private object DisallowParentInterceptTouchListener : View.OnTouchListener {
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> setDisallowIntercept(v, true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setDisallowIntercept(v, false)
        }
        return false
    }

    private fun setDisallowIntercept(v: View, disallow: Boolean) {
        var parent = v.parent
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow)
            parent = parent.parent
        }
    }
}
