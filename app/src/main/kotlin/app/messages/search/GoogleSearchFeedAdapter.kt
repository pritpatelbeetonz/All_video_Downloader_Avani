package app.messages.search

import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.messages.config.AdsConfigManager
import app.messages.databinding.ItemBlogAdPlaceholderBinding
import app.messages.databinding.ItemBlogPostBinding
import com.google.android.gms.ads.nativead.NativeAd

class GoogleSearchFeedAdapter(
    private val onBlogClick: (BlogPost) -> Unit
) : ListAdapter<GoogleSearchFeedItem, RecyclerView.ViewHolder>(DIFF) {

    private val loadedAds = mutableMapOf<Int, NativeAd>()
    private val loadingAds = mutableSetOf<Int>()
    private val activeAdHolders = mutableSetOf<AdVH>()

    fun clearAds() {
        loadedAds.values.forEach { it.destroy() }
        loadedAds.clear()
        loadingAds.clear()
        activeAdHolders.clear()
        Log.d("ADS_GOOGLE_SEARCH", "♻️ [SEARCH_CLEANUP] Destroyed all cached search native ads")
    }

    fun onScrollChanged(scrollView: NestedScrollView) {
        activeAdHolders.forEach { holder ->
            holder.checkVisibilityAndLoad(scrollView, loadedAds, loadingAds)
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is GoogleSearchFeedItem.Blog -> TYPE_BLOG
        is GoogleSearchFeedItem.AdPlaceholder -> TYPE_AD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_BLOG -> BlogVH(ItemBlogPostBinding.inflate(inflater, parent, false))
            else -> AdVH(ItemBlogAdPlaceholderBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is GoogleSearchFeedItem.Blog -> (holder as BlogVH).bind(item.post, onBlogClick)
            is GoogleSearchFeedItem.AdPlaceholder -> {
                val adHolder = holder as AdVH
                activeAdHolders.add(adHolder)
                adHolder.bind(item.id, loadedAds, loadingAds)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is AdVH) {
            activeAdHolders.remove(holder)
        }
    }

    class BlogVH(private val binding: ItemBlogPostBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(post: BlogPost, onClick: (BlogPost) -> Unit) {
            binding.tvTitle.text = post.title
            binding.tvSource.text = post.source
            binding.tvTime.text = post.timeLabel
            if (post.timeLabel.isEmpty()) {
                binding.tvTime.visibility = android.view.View.GONE
            } else {
                binding.tvTime.visibility = android.view.View.VISIBLE
            }

            val cover = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * binding.root.resources.displayMetrics.density
                setColor(post.imageColor)
            }
            
            if (!post.imgUrl.isNullOrEmpty()) {
                binding.ivCover.background = null
                com.bumptech.glide.Glide.with(binding.ivCover.context)
                    .load(post.imgUrl)
                    .placeholder(cover)
                    .into(binding.ivCover)
            } else {
                binding.ivCover.setImageDrawable(null)
                binding.ivCover.background = cover
            }

            val dot = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 3f * binding.root.resources.displayMetrics.density
                setColor(ColorUtils.blendARGB(post.imageColor, 0xFF4285F4.toInt(), 0.35f))
            }
            binding.sourceDot.background = dot

            binding.root.setOnClickListener { onClick(post) }
        }
    }

    class AdVH(private val binding: ItemBlogAdPlaceholderBinding) : RecyclerView.ViewHolder(binding.root) {
        var currentPlaceholderId: Int = -1

        fun bind(
            placeholderId: Int,
            loadedAds: MutableMap<Int, NativeAd>,
            loadingAds: MutableSet<Int>
        ) {
            currentPlaceholderId = placeholderId
            val config = app.messages.config.AdsConfigManager.config
            if (!config.canShowNative || !config.isGoogleSearchNativeOn) {
                binding.nativeAdShimmerFrameLayout.visibility = android.view.View.GONE
                binding.nativeAdFrameLayout.visibility = android.view.View.GONE
                return
            }

            // 1. If already loaded for this slot, reuse instantly!
            val cachedAd = loadedAds[placeholderId]
            if (cachedAd != null) {
                Log.d("ADS_GOOGLE_SEARCH", "💾 [SEARCH_CACHE_HIT] Reusing already loaded Native Search Ad for slot #$placeholderId")
                binding.nativeAdShimmerFrameLayout.visibility = android.view.View.GONE
                binding.nativeAdFrameLayout.visibility = android.view.View.VISIBLE
                binding.nativeAdFrameLayout.removeAllViews()
                val adView = LayoutInflater.from(binding.root.context).inflate(
                    app.messages.R.layout.ads_native_dashboard,
                    binding.nativeAdFrameLayout, false
                ) as com.google.android.gms.ads.nativead.NativeAdView
                app.messages.config.AdsConfigManager.populateNativeAdView(cachedAd, adView)
                binding.nativeAdFrameLayout.addView(adView)
                return
            }

            // Slot #0 is at top -> load immediately
            if (placeholderId == 0) {
                requestAd(placeholderId, loadedAds, loadingAds)
            } else {
                // Keep shimmer frame visible with layout bounds so its position in scrollview is measurable
                binding.nativeAdShimmerFrameLayout.visibility = android.view.View.VISIBLE
                binding.nativeAdFrameLayout.visibility = android.view.View.GONE
            }
        }

        private var scrollDebounceRunnable: Runnable? = null
        private val debounceHandler = android.os.Handler(android.os.Looper.getMainLooper())

        fun checkVisibilityAndLoad(
            scrollView: NestedScrollView,
            loadedAds: MutableMap<Int, NativeAd>,
            loadingAds: MutableSet<Int>
        ) {
            if (currentPlaceholderId < 0) return
            if (loadedAds.containsKey(currentPlaceholderId) || loadingAds.contains(currentPlaceholderId)) return

            scrollDebounceRunnable?.let { debounceHandler.removeCallbacks(it) }

            val rect = android.graphics.Rect()
            val isVisible = binding.root.getGlobalVisibleRect(rect)
            val screenHeight = binding.root.resources.displayMetrics.heightPixels

            // Check if slot has scrolled into or near viewport
            if (isVisible && rect.top < screenHeight + 250 && rect.bottom > -100) {
                // Debounce by 250ms: Only trigger if user actually stops/pauses on this item!
                scrollDebounceRunnable = Runnable {
                    val recheckRect = android.graphics.Rect()
                    val stillVisible = binding.root.getGlobalVisibleRect(recheckRect)
                    if (stillVisible && recheckRect.top < screenHeight + 250 && recheckRect.bottom > -100) {
                        requestAd(currentPlaceholderId, loadedAds, loadingAds)
                    }
                }
                debounceHandler.postDelayed(scrollDebounceRunnable!!, 250L)
            }
        }

        private fun requestAd(
            placeholderId: Int,
            loadedAds: MutableMap<Int, NativeAd>,
            loadingAds: MutableSet<Int>
        ) {
            if (loadedAds.containsKey(placeholderId) || loadingAds.contains(placeholderId)) return
            loadingAds.add(placeholderId)
            val config = app.messages.config.AdsConfigManager.config
            val unitId = config.nativeAdIdGoogleSearch
            Log.d("ADS_GOOGLE_SEARCH", "🚀 [SEARCH_AD_REQUEST] Requesting Native Search Ad for slot #$placeholderId (Visible in Viewport) | UnitId: $unitId")

            binding.nativeAdShimmerFrameLayout.visibility = android.view.View.VISIBLE
            binding.nativeAdFrameLayout.visibility = android.view.View.GONE

            app.messages.config.AdsConfigManager.loadNativeAd(
                binding.root.context,
                unitId,
                { nativeAd ->
                    loadingAds.remove(placeholderId)

                    val context = binding.root.context
                    val activity = (context as? android.app.Activity) 
                        ?: ((context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity)

                    if (activity?.isFinishing == true || activity?.isDestroyed == true) {
                        // User closed search activity -> save to RAM cache for next search!
                        app.messages.config.AdsConfigManager.cacheNativeAd(unitId, nativeAd)
                        Log.d("ADS_GOOGLE_SEARCH", "💾 [SEARCH_CACHE] Activity finishing/destroyed -> Cached Native Search Ad in RAM for next session!")
                        return@loadNativeAd
                    }

                    loadedAds[placeholderId] = nativeAd

                    binding.nativeAdShimmerFrameLayout.visibility = android.view.View.GONE
                    binding.nativeAdFrameLayout.visibility = android.view.View.VISIBLE
                    val adView = LayoutInflater.from(binding.root.context).inflate(
                        app.messages.R.layout.ads_native_dashboard,
                        binding.nativeAdFrameLayout, false
                    ) as com.google.android.gms.ads.nativead.NativeAdView
                    app.messages.config.AdsConfigManager.populateNativeAdView(nativeAd, adView)
                    binding.nativeAdFrameLayout.removeAllViews()
                    binding.nativeAdFrameLayout.addView(adView)
                    Log.d("ADS_GOOGLE_SEARCH", "✨ [SEARCH_AD_SHOWN] Native Search Ad inflated & DISPLAYED for slot #$placeholderId! 🎉")
                },
                {
                    loadingAds.remove(placeholderId)
                    Log.e("ADS_GOOGLE_SEARCH", "❌ [SEARCH_AD_FAILED] Native Search Ad failed to load for slot #$placeholderId")
                    binding.nativeAdShimmerFrameLayout.visibility = android.view.View.GONE
                    binding.nativeAdFrameLayout.visibility = android.view.View.GONE
                }
            )
        }
    }

    companion object {
        private const val TYPE_BLOG = 1
        private const val TYPE_AD = 2

        private val DIFF = object : DiffUtil.ItemCallback<GoogleSearchFeedItem>() {
            override fun areItemsTheSame(
                oldItem: GoogleSearchFeedItem,
                newItem: GoogleSearchFeedItem
            ): Boolean = when {
                oldItem is GoogleSearchFeedItem.Blog && newItem is GoogleSearchFeedItem.Blog ->
                    oldItem.post.id == newItem.post.id
                oldItem is GoogleSearchFeedItem.AdPlaceholder && newItem is GoogleSearchFeedItem.AdPlaceholder ->
                    oldItem.id == newItem.id
                else -> false
            }

            override fun areContentsTheSame(
                oldItem: GoogleSearchFeedItem,
                newItem: GoogleSearchFeedItem
            ): Boolean = oldItem == newItem
        }
    }
}
