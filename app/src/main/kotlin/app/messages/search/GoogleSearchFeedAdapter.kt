package app.messages.search

import app.messages.ads.ng.*
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
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

class GoogleSearchFeedAdapter(
    private val onBlogClick: (BlogPost) -> Unit
) : ListAdapter<GoogleSearchFeedItem, RecyclerView.ViewHolder>(DIFF) {

    private val loadedAds = mutableMapOf<Int, NativeAd>()
    private val loadingAds = mutableSetOf<Int>()
    private val activeAdHolders = mutableSetOf<AdVH>()

    fun clearAds() {
        // Seen ads are destroyed; an ad that never got an impression goes back to the RAM cache so the
        // next search session shows it instead of firing a new request (wasted only if the cache is full).
        val unitId = AdsConfigManager.config.nativeAdIdGoogleSearch
        loadedAds.values.forEach { AdsConfigManager.releaseNativeAd(unitId, it, "view_destroyed_unseen") }
        loadedAds.clear()
        loadingAds.clear()
        activeAdHolders.clear()
        Log.d("ADS_GOOGLE_SEARCH", "♻️ [SEARCH_CLEANUP] Released all search native ads (seen -> destroyed, unseen -> recycled)")
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
                    app.messages.R.layout.ads_native_big,
                    binding.nativeAdFrameLayout, false
                ) as com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
                app.messages.config.AdsConfigManager.populateNativeAdView(cachedAd, adView, "search")
                binding.nativeAdFrameLayout.addView(adView)
                return
            }

            if (hideIfOverCap(loadedAds, loadingAds)) return

            // Every slot (including #0) waits until it is really on screen before requesting: requesting
            // #0 on open loaded ads behind the keyboard / suggestions / Chrome tab that were never seen.
            // Keep the shimmer frame laid out so its on-screen position is measurable.
            binding.nativeAdShimmerFrameLayout.visibility = android.view.View.VISIBLE
            binding.nativeAdFrameLayout.visibility = android.view.View.GONE
        }

        /** Session cap reached and this slot has no ad: collapse it instead of a shimmer that never fills. */
        private fun hideIfOverCap(loadedAds: Map<Int, NativeAd>, loadingAds: Set<Int>): Boolean {
            if (loadedAds.size + loadingAds.size < MAX_ADS_PER_SESSION) return false
            binding.nativeAdShimmerFrameLayout.visibility = android.view.View.GONE
            binding.nativeAdFrameLayout.visibility = android.view.View.GONE
            return true
        }

        private var scrollDebounceRunnable: Runnable? = null
        private val debounceHandler = android.os.Handler(android.os.Looper.getMainLooper())

        /**
         * True when at least half of this slot is on screen ABOVE the keyboard (window visible frame,
         * not raw screen height) and the screen is in the foreground (not behind a Chrome tab).
         */
        private fun isMostlyOnScreen(): Boolean {
            val root = binding.root
            if (!root.isShown || root.height <= 0) return false
            val context = root.context
            val activity = (context as? android.app.Activity)
                ?: ((context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity)
            val lifecycleOwner = activity as? androidx.lifecycle.LifecycleOwner
            if (lifecycleOwner != null &&
                !lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
            ) return false

            val rect = android.graphics.Rect()
            if (!root.getGlobalVisibleRect(rect)) return false
            val frame = android.graphics.Rect()
            root.getWindowVisibleDisplayFrame(frame) // excludes the keyboard area
            val visibleTop = maxOf(rect.top, frame.top)
            val visibleBottom = minOf(rect.bottom, frame.bottom)
            return visibleBottom - visibleTop >= root.height / 2
        }

        fun checkVisibilityAndLoad(
            scrollView: NestedScrollView,
            loadedAds: MutableMap<Int, NativeAd>,
            loadingAds: MutableSet<Int>
        ) {
            if (currentPlaceholderId < 0) return
            if (loadedAds.containsKey(currentPlaceholderId) || loadingAds.contains(currentPlaceholderId)) return
            if (hideIfOverCap(loadedAds, loadingAds)) return

            scrollDebounceRunnable?.let { debounceHandler.removeCallbacks(it) }

            if (isMostlyOnScreen()) {
                // Only request if the slot STAYS on screen for SLOT_DWELL_MS (user is reading, not
                // flicking past): a request fired on a quick pass lands after the slot is gone.
                val slotId = currentPlaceholderId
                scrollDebounceRunnable = Runnable {
                    if (currentPlaceholderId == slotId && isMostlyOnScreen()) {
                        requestAd(slotId, loadedAds, loadingAds)
                    }
                }
                debounceHandler.postDelayed(scrollDebounceRunnable!!, SLOT_DWELL_MS)
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
                        app.messages.R.layout.ads_native_big,
                        binding.nativeAdFrameLayout, false
                    ) as com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
                    app.messages.config.AdsConfigManager.populateNativeAdView(nativeAd, adView, "search")
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

        /** How long a slot must stay on screen before its ad is requested. */
        private const val SLOT_DWELL_MS = 1000L

        /**
         * Ads requested per search session. The feed has a slot after every 2nd blog; users rarely
         * read past the first few, so later slots only produced ads that were never seen.
         */
        const val MAX_ADS_PER_SESSION = 2

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
