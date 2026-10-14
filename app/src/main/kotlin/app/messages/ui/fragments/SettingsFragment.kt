package app.messages.ui.fragments

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.messages.config.AdsConfigManager
import app.messages.MessagesApplication
import app.messages.R
import dagger.hilt.android.EntryPointAccessors

import app.messages.analytics.AnalyticsHelper
import app.messages.analytics.EventNames
import app.messages.analytics.EventScreenName
import app.messages.data.SyncManager
import app.messages.data.db.AppDatabase
import app.messages.databinding.FragmentSettingsBinding
import app.messages.enumeration.ThemeMode
import app.messages.enumeration.getThemeModeText
import app.messages.preferences.PreferenceHelper
import app.messages.ui.activity.HomeActivity
import app.messages.ui.activity.SwipeActionActivity
import app.messages.utility.ThemeUtils
import java.lang.Exception
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {
    private lateinit var binding: FragmentSettingsBinding

    private var isSyncingMessages = false

    var prefs: PreferenceHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)

        prefs = PreferenceHelper.getInstance(requireContext())

        initUI()

        setupClicks()

        return binding.root
    }

    private fun initUI() {
        setSelectedDelayedSendingText()
        setupThemeModeUI()
        updateMmsCompressionText()
        setAutoDeleteText()
        setSelectedLanguageText()
    }

    private fun updateMmsCompressionText() {
        val limit = PreferenceHelper.getInstance(requireContext()).getMmsCompressionLimit()
        val text = when (limit) {
            -1 -> "Automatic"
            0 -> "No compression"
            100 -> "100KB"
            200 -> "200KB"
            300 -> "300KB"
            600 -> "600KB"
            1000 -> "1000KB"
            2000 -> "2000KB"
            else -> "Automatic"
        }
        binding.tvCompressMMS.text = text
    }

    private fun setupPremiumUI() {
        // cardPremium removed in new UI
    }

    private fun setSelectedDelayedSendingText() {
        val savedDelayedSending = prefs?.getDelayedSendingInSeconds()
        binding.tvDelayedSending.text = when (savedDelayedSending) {
            0 -> getString(R.string.no_delay)
            3 -> getString(R.string._3_seconds)
            5 -> getString(R.string._5_seconds)
            10 -> getString(R.string._10_seconds)
            else -> {
                getString(R.string.no_delay)
            }
        }
    }

    private fun setAutoDeleteText() {
        val period = prefs?.getAutoDeletePeriod() ?: 0
        binding.tvDeleteOld.text = when (period) {
            0 -> "Never"
            7 -> "After 7 days"
            30 -> "After 30 days"
            90 -> "After 90 days"
            365 -> "After 1 year"
            else -> "Never"
        }
    }

    private fun setSelectedFontSizeText() {
        // Font size removed
    }

    private fun setSelectedLanguageText() {
        val selectedCode = prefs?.getSelectedLanguageCode() ?: "en"
        val langModel = app.messages.data.LanguageData.getLanguageByCode(selectedCode)
        binding.tvLanguage.text = langModel?.nativeName ?: "English"
    }

    private fun setupClicks() {
        binding.incToolbar.ivBack.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.CLICK_BACK, EventScreenName.SETTINGS)
            requireActivity().finish()
        }

        binding.incToolbar.tvTitle.text = getString(R.string.settings)
        binding.incToolbar.tvDone.visibility = View.GONE

        // Language
        binding.rlLanguage.setOnClickListener {
            val intent = android.content.Intent(
                requireContext(),
                app.messages.ui.activity.LanguageSelectionActivity::class.java
            )
            intent.putExtra("from_settings", true)
            startActivity(intent)
        }

        // rlFontSize removed

        binding.rlCompressMMS.setOnClickListener {
            showCompressionDialog()
        }

        binding.rlNotifications.setOnClickListener {
            AnalyticsHelper.logEvent(
                EventNames.SETTINGS_CLICK_NOTIFICATIONS,
                EventScreenName.SETTINGS
            )
            val intent =
                android.content.Intent(
                    requireContext(),
                    app.messages.ui.activity.NotificationSettingsActivity::class.java
                )
            startActivity(intent)
        }

        binding.rlDelayedSending.setOnClickListener {
            AnalyticsHelper.logEvent(
                EventNames.SETTINGS_CLICK_DELAYED_SENDING,
                EventScreenName.SETTINGS
            )
            showDelayedSendingDialog()
        }

        binding.rlNightMode.setOnClickListener {
            AnalyticsHelper.logEvent(
                EventNames.SETTINGS_CLICK_CHOOSE_THEME,
                EventScreenName.SETTINGS
            )
            showThemeModeDialog()
        }

        binding.rlSwipeActions.setOnClickListener {
            AnalyticsHelper.logEvent(
                EventNames.SETTINGS_CLICK_SWIPE_ACTIONS,
                EventScreenName.SETTINGS
            )
            val intent = Intent(requireActivity(), SwipeActionActivity::class.java)
            startActivity(intent)
        }

        binding.switchDelivery.isChecked = prefs?.getDeliveryReportsEnabled() ?: false
        binding.switchDelivery.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed || binding.rlDelivery.isPressed) {
                prefs?.setDeliveryReportsEnabled(isChecked)
                val status = if (isChecked) "enabled" else "disabled"
                android.widget.Toast.makeText(requireContext(), "Delivery reports $status", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        binding.rlDelivery.setOnClickListener {
            binding.switchDelivery.isChecked = !binding.switchDelivery.isChecked
        }

        // cbStripeAccents removed

        binding.rlSyncMessages.setOnClickListener {
            AnalyticsHelper.logEvent(
                EventNames.SETTINGS_CLICK_SYNC_MESSAGES,
                EventScreenName.SETTINGS
            )
            syncMessagesNow()
        }

        // BackupRestore removed from main settings, RateUs, Help removed


        // rlLanguage, rlRateUs, rlInviteFriends removed

        binding.rlPrivacyPolicy.setOnClickListener {
            AnalyticsHelper.logEvent(
                EventNames.SETTINGS_CLICK_PRIVACY_POLICY,
                EventScreenName.SETTINGS
            )
            openPrivacyPolicy()
        }

        binding.rlDeleteOld.setOnClickListener {
            showAutoDeleteDialog()
        }
    }


    private fun setupThemeModeUI() {
        val themeMode = prefs?.getThemeMode() ?: ThemeMode.SYSTEM_DEFAULT.value
        binding.tvNightMode.text = getThemeModeText(themeMode, requireContext())
    }

    private fun showThemeModeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_theme_mode, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroup)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.decorView?.setPadding(0, 0, 0, 0)

        val width = (resources.displayMetrics.widthPixels * 0.75).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        val currentTheme = prefs?.getThemeMode() ?: ThemeMode.SYSTEM_DEFAULT.value
        when (currentTheme) {
            ThemeMode.SYSTEM_DEFAULT.value -> radioGroup.check(R.id.rbSystemDefault)
            ThemeMode.LIGHT.value -> radioGroup.check(R.id.rbLightTheme)
            ThemeMode.DARK.value -> radioGroup.check(R.id.rbDarkTheme)
            else -> radioGroup.check(R.id.rbSystemDefault)
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedTheme = when (checkedId) {
                R.id.rbSystemDefault -> ThemeMode.SYSTEM_DEFAULT.value
                R.id.rbLightTheme -> ThemeMode.LIGHT.value
                R.id.rbDarkTheme -> ThemeMode.DARK.value
                else -> ThemeMode.SYSTEM_DEFAULT.value
            }

            prefs?.setThemMode(selectedTheme)
            setupThemeModeUI()
            ThemeUtils.applyTheme(requireContext())

            try {
                val appPrefs = EntryPointAccessors.fromApplication(
                    requireContext().applicationContext,
                    MessagesApplication.PrefsEntryPoint::class.java
                ).appPrefs()
                val coreMode = when (selectedTheme) {
                    ThemeMode.LIGHT.value -> app.messages.core.prefs.ThemeMode.Light
                    ThemeMode.DARK.value -> app.messages.core.prefs.ThemeMode.Dark
                    else -> app.messages.core.prefs.ThemeMode.System
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    appPrefs.setThemeMode(coreMode)
                }
            } catch (_: Exception) {}

            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showFontSizeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_font_size, null)

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupFont)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // ✅ REMOVE default padding (THIS FIXES FULL WIDTH ISSUE)
        dialog.window?.decorView?.setPadding(0, 0, 0, 0)

// ✅ SET WIDTH
        val width = (resources.displayMetrics.widthPixels * 0.75).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        // Load saved value
        val savedSize = prefs?.getFontSize()

        when (savedSize) {
            0.85f -> radioGroup.check(R.id.rbSmall)
            1.0f -> radioGroup.check(R.id.rbNormal)
            1.15f -> radioGroup.check(R.id.rbLarge)
            1.3f -> radioGroup.check(R.id.rbLarger)
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val size = when (checkedId) {
                R.id.rbSmall -> 0.85f
                R.id.rbNormal -> 1.0f
                R.id.rbLarge -> 1.15f
                R.id.rbLarger -> 1.3f
                else -> 1.0f
            }

            prefs?.setFontSize(size)
            restartApp()

            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDelayedSendingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delayed_sending, null)

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroup)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // ✅ REMOVE default padding (THIS FIXES FULL WIDTH ISSUE)
        dialog.window?.decorView?.setPadding(0, 0, 0, 0)

// ✅ SET WIDTH
        val width = (resources.displayMetrics.widthPixels * 0.75).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        // Load saved value
        val savedSize = prefs?.getDelayedSendingInSeconds()

        when (savedSize) {
            0 -> radioGroup.check(R.id.rbNoDelay)
            3 -> radioGroup.check(R.id.rb3Second)
            5 -> radioGroup.check(R.id.rb5Second)
            10 -> radioGroup.check(R.id.rb10Second)
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val size = when (checkedId) {
                R.id.rbNoDelay -> 0
                R.id.rb3Second -> 3
                R.id.rb5Second -> 5
                R.id.rb10Second -> 10
                else -> 0
            }

            prefs?.setDelayedSendingInSeconds(size)

            setSelectedDelayedSendingText()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showAutoDeleteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_auto_delete, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroup)
        val btnCancel = dialogView.findViewById<android.widget.TextView>(R.id.btnCancel)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.decorView?.setPadding(0, 0, 0, 0)
        val width = (resources.displayMetrics.widthPixels * 0.75).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        val currentPeriod = prefs?.getAutoDeletePeriod() ?: 0
        when (currentPeriod) {
            0 -> radioGroup.check(R.id.rbNever)
            7 -> radioGroup.check(R.id.rb7Days)
            30 -> radioGroup.check(R.id.rb30Days)
            90 -> radioGroup.check(R.id.rb90Days)
            365 -> radioGroup.check(R.id.rb1Year)
            else -> radioGroup.check(R.id.rbNever)
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedPeriod = when (checkedId) {
                R.id.rbNever -> 0
                R.id.rb7Days -> 7
                R.id.rb30Days -> 30
                R.id.rb90Days -> 90
                R.id.rb1Year -> 365
                else -> 0
            }

            if (selectedPeriod > 0) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Warning")
                    .setMessage("Old messages will be permanently deleted and cannot be recovered. Are you sure you want to enable this?")
                    .setPositiveButton("Enable") { innerDialog, _ ->
                        prefs?.setAutoDeletePeriod(selectedPeriod)
                        setAutoDeleteText()
                        app.messages.utility.AutoDeleteManager.scheduleDailyCleanup(requireContext())
                        innerDialog.dismiss()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { innerDialog, _ ->
                        radioGroup.setOnCheckedChangeListener(null)
                        when (currentPeriod) {
                            0 -> radioGroup.check(R.id.rbNever)
                            7 -> radioGroup.check(R.id.rb7Days)
                            30 -> radioGroup.check(R.id.rb30Days)
                            90 -> radioGroup.check(R.id.rb90Days)
                            365 -> radioGroup.check(R.id.rb1Year)
                            else -> radioGroup.check(R.id.rbNever)
                        }
                        // Re-attach listener later if needed, but since it's just radio group we can just re-attach
                        innerDialog.dismiss()
                    }
                    .setOnDismissListener {
                        // Re-attach listener after cancel if they want to try again
                        // Actually it's easier to just close the whole dialog on cancel to prevent bugs
                        dialog.dismiss()
                    }
                    .show()
            } else {
                prefs?.setAutoDeletePeriod(0)
                setAutoDeleteText()
                app.messages.utility.AutoDeleteManager.cancelCleanup(requireContext())
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    fun restartApp() {
        val intent = Intent(requireActivity(), app.messages.ui.launcher.LauncherActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        requireActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        requireActivity().finish()
    }

    private fun isTaskRootLauncher(): Boolean {
        return app.messages.preferences.PreferenceHelper.getInstance(requireContext()).isLauncherModeEnabled()
    }

    private fun isDefaultHomeApp(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = requireActivity().getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
            }
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolve = requireActivity().packageManager.resolveActivity(homeIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolve?.activityInfo?.packageName == requireActivity().packageName
    }

    override fun onResume() {
        super.onResume()
        setupPremiumUI()
    }

    fun openPrivacyPolicy() {
        try {
            val url = AdsConfigManager.config.privacyPolicyUrl.ifBlank {
                app.messages.constant.Constants.PRIVACY_POLICY

            }

            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()

            customTabsIntent.launchUrl(requireContext(), Uri.parse(url))
//            val browserIntent = Intent(
//                Intent.ACTION_VIEW,
//                Uri.parse(url)
//            )
//            startActivity(browserIntent)
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
        }
    }

    private fun showCompressionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_compression, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupCompression)
        val rbAutomatic = dialogView.findViewById<RadioButton>(R.id.rbAutomatic)
        val rb100KB = dialogView.findViewById<RadioButton>(R.id.rb100KB)
        val rb200KB = dialogView.findViewById<RadioButton>(R.id.rb200KB)
        val rb300KB = dialogView.findViewById<RadioButton>(R.id.rb300KB)
        val rb600KB = dialogView.findViewById<RadioButton>(R.id.rb600KB)
        val rb1000KB = dialogView.findViewById<RadioButton>(R.id.rb1000KB)
        val rb2000KB = dialogView.findViewById<RadioButton>(R.id.rb2000KB)
        val rbNoCompression = dialogView.findViewById<RadioButton>(R.id.rbNoCompression)

        val currentLimit = PreferenceHelper.getInstance(requireContext()).getMmsCompressionLimit()
        when (currentLimit) {
            -1 -> rbAutomatic.isChecked = true
            0 -> rbNoCompression.isChecked = true
            100 -> rb100KB.isChecked = true
            200 -> rb200KB.isChecked = true
            300 -> rb300KB.isChecked = true
            600 -> rb600KB.isChecked = true
            1000 -> rb1000KB.isChecked = true
            2000 -> rb2000KB.isChecked = true
            else -> rbAutomatic.isChecked = true
        }

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val limit = when (checkedId) {
                R.id.rbAutomatic -> -1
                R.id.rbNoCompression -> 0
                R.id.rb100KB -> 100
                R.id.rb200KB -> 200
                R.id.rb300KB -> 300
                R.id.rb600KB -> 600
                R.id.rb1000KB -> 1000
                R.id.rb2000KB -> 2000
                else -> -1
            }
            PreferenceHelper.getInstance(requireContext()).setMmsCompressionLimit(limit)
            updateMmsCompressionText()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun rateUs() {
        val uri = Uri.parse("market://details?id=${requireActivity().packageName}")
        val myAppLinkToMarket = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(myAppLinkToMarket)
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
        }
    }

    private fun shareApp() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.setType("text/plain")
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            val shareMessage =
                "\n${getString(R.string.msg_share_app)}\n\n https://play.google.com/store/apps/details?id=${requireActivity().packageName}"
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage)
            startActivity(
                Intent.createChooser(
                    shareIntent,
                    getString(R.string.choose_one)
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                AdsConfigManager.configFlow.collect { config ->
                    if (!config.isNativeOn || PreferenceHelper.getInstance(requireContext()).isPremium()) {
                        binding.nativeAdContainer?.visibility = View.GONE
                    } else if (binding.nativeAdView?.childCount == 0 || binding.nativeAdContainer?.visibility == View.GONE) {
                        loadNativeAd()
                    }
                }
            }
        }
    }

    private fun loadNativeAd() {
        val config = AdsConfigManager.config

        if (PreferenceHelper.getInstance(requireContext()).isPremium() || !config.isNativeOn) {
            binding.nativeAdContainer?.visibility = View.GONE
            return
        }

        binding.nativeAdContainer?.visibility = View.VISIBLE
        binding.nativeAdShimmer?.root?.visibility = View.VISIBLE
        binding.nativeAdView?.visibility = View.GONE

        app.messages.config.AdsConfigManager.loadNativeAd(
            context = requireContext(),
            adUnitId = config.nativeAdIdSettingScreen,
            onLoaded = { nativeAd ->
                if (!isAdded) return@loadNativeAd
                binding.nativeAdShimmer?.root?.visibility = View.GONE
                binding.nativeAdView?.visibility = View.VISIBLE
                binding.nativeAdView?.removeAllViews()

                val adView = layoutInflater.inflate(R.layout.ads_native_big, null, false) as com.google.android.gms.ads.nativead.NativeAdView
                AdsConfigManager.populateNativeAdView(nativeAd, adView)

                binding.nativeAdView?.addView(adView)
            },
            onFailed = {
                if (isAdded) {
                    binding.nativeAdContainer?.visibility = View.GONE
                }
            }
        )
    }
    private fun syncMessagesNow() {
        if (isSyncingMessages) return
        if (!hasSmsPermission()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.sms_permission_required_to_sync), Toast.LENGTH_LONG
            ).show()
            return
        }

        isSyncingMessages = true
        binding.rlSyncMessages.isEnabled = false
        binding.txtSync.text = getString(R.string.syncing)
        Toast.makeText(requireContext(), getString(R.string.sync_started), Toast.LENGTH_SHORT)
            .show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val syncManager = SyncManager(requireContext())
                // Fast refresh first for quick visible updates, then full sync.
                syncManager.quickSyncRecentConversations(20)
                syncManager.syncAll()
                AppDatabase.getDatabase(requireContext()).conversationDao()
                    .deleteBlankOrPlaceholderConversations()

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.messages_synced_successfully), Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sync_failed_please_try_again), Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isSyncingMessages = false
                    binding.rlSyncMessages.isEnabled = true
                    binding.txtSync.text = getString(R.string.sync_messages)
                }
            }
        }
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /* private fun showNotificationsEnabledDialog() {
         val options = arrayOf(getString(R.string.on), getString(R.string.off))
         val checked = if (prefs?.getNotificationsEnabled() ?: false) 0 else 1

         AlertDialog.Builder(requireContext())
             .setTitle(getString(R.string.notifications))
             .setSingleChoiceItems(options, checked) { dialog, which ->
                 prefs?.setNotificationsEnabled(which == 0)
                 dialog.dismiss()
             }
             .show()
     }*/

    /*    private fun showNotificationPreviewDialog() {
            val prefs = PreferenceHelper.getInstance(requireContext())
            val options = arrayOf(
                getString(R.string.show_name_and_message),
                getString(R.string.show_name),
                getString(R.string.hide_contents)
            )
            val checked = prefs.getNotificationPreviewMode().coerceIn(0, 2)
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.notification_previews))
                .setSingleChoiceItems(options, checked) { dialog, which ->
                    prefs.setNotificationPreviewMode(which)
                    dialog.dismiss()
                }
                .show()
        }*/


}

