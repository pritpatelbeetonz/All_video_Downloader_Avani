package app.messages.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import app.messages.R
import app.messages.databinding.ActivityNotificationSettingsBinding
import app.messages.enumeration.NotificationPreviewMode
import app.messages.enumeration.SwipeActions
import app.messages.enumeration.getNotificationPreviewModeText
import app.messages.enumeration.getSwipeActionText
import app.messages.preferences.PreferenceHelper
import app.messages.ui.base.BaseActivity
import app.messages.analytics.AnalyticsHelper
import app.messages.analytics.EventNames
import app.messages.analytics.EventScreenName

class NotificationSettingsActivity : BaseActivity() {
    private lateinit var binding: ActivityNotificationSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)


        initUI()
        setupClicks()
    }

    private fun initUI() {

        binding.incToolbar.tvTitle.text=getString(R.string.notifications)
        binding.incToolbar.tvDone.visibility= View.GONE

        setupNotificationActionButtonUI()
        setupNotificationPreviewModeUI()

        binding.switchWakeScreen.isChecked =
            PreferenceHelper.getInstance(this@NotificationSettingsActivity)
                .getNotificationWakeScreen()

        // Removed cbQuickReply from UI
        // binding.cbQuickReply.isChecked =
        //     PreferenceHelper.getInstance(this@NotificationSettingsActivity)
        //         .getNotificationQuickReplyEnabled()
    }

    private fun setupClicks() {
        binding.incToolbar.ivBack.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.CLICK_BACK, EventScreenName.NOTIFICATION_SETTINGS)
            finish()
        }
        binding.cvNotificationPreviews.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.SETTINGS_CLICK_NOTIFICATIONS, EventScreenName.NOTIFICATION_SETTINGS)
            showNotificationPreviewDialog()
        }

        binding.cvButton1.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.SETTINGS_CLICK_NOTIFICATIONS, EventScreenName.NOTIFICATION_SETTINGS)
            showButtonDialog(1)
        }
        binding.cvButton2.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.SETTINGS_CLICK_NOTIFICATIONS, EventScreenName.NOTIFICATION_SETTINGS)
            showButtonDialog(2)
        }
        binding.cvButton3.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.SETTINGS_CLICK_NOTIFICATIONS, EventScreenName.NOTIFICATION_SETTINGS)
            showButtonDialog(3)
        }

        binding.switchWakeScreen.setOnClickListener {
            PreferenceHelper.getInstance(this@NotificationSettingsActivity)
                .setNotificationWakeScreen(binding.switchWakeScreen.isChecked)
        }
        binding.cvMessagesReply.setOnClickListener {
            // Quick reply action
            val isChecked = !PreferenceHelper.getInstance(this@NotificationSettingsActivity).getNotificationQuickReplyEnabled()
            PreferenceHelper.getInstance(this@NotificationSettingsActivity).setNotificationQuickReplyEnabled(isChecked)
            }
        binding.cvNotifications.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.SETTINGS_CLICK_NOTIFICATIONS, EventScreenName.NOTIFICATION_SETTINGS)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    startActivity(intent)
                } else {
                    val intent = Intent("android.settings.APP_NOTIFICATION_SETTINGS").apply {
                        putExtra("app_package", packageName)
                        putExtra("app_uid", applicationInfo.uid)
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
        }
    }


    private fun showButtonDialog(buttonNo: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_swipe_action, null)

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupSwipeAction)
        val rbMarkUnread = dialogView.findViewById<RadioButton>(R.id.rbMarkUnread)
        val rbReply = dialogView.findViewById<RadioButton>(R.id.rbReply)

        rbMarkUnread.visibility = View.GONE
        rbReply.visibility = View.VISIBLE

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // ✅ REMOVE default padding (THIS FIXES FULL WIDTH ISSUE)
        dialog.window?.decorView?.setPadding(0, 0, 0, 0)

// ✅ SET WIDTH
        val width = (resources.displayMetrics.widthPixels * 0.75).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        // Load saved value
        val savedSize = PreferenceHelper.getInstance(this).getNotificationActionButton(buttonNo)

        when (savedSize) {
            SwipeActions.NONE.value -> radioGroup.check(R.id.rbNone)
            SwipeActions.ARCHIVE.value -> radioGroup.check(R.id.rbArchive)
            SwipeActions.DELETE.value -> radioGroup.check(R.id.rbDelete)
            SwipeActions.BLOCK.value -> radioGroup.check(R.id.rbBlock)
            SwipeActions.CALL.value -> radioGroup.check(R.id.rbCall)
            SwipeActions.MARK_READ.value -> radioGroup.check(R.id.rbMArkRead)
            SwipeActions.REPLY.value -> radioGroup.check(R.id.rbReply)
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val size = when (checkedId) {
                R.id.rbNone -> SwipeActions.NONE.value
                R.id.rbArchive -> SwipeActions.ARCHIVE.value
                R.id.rbDelete -> SwipeActions.DELETE.value
                R.id.rbBlock -> SwipeActions.BLOCK.value
                R.id.rbCall -> SwipeActions.CALL.value
                R.id.rbMArkRead -> SwipeActions.MARK_READ.value
                R.id.rbReply -> SwipeActions.REPLY.value
                else -> SwipeActions.NONE.value
            }

            PreferenceHelper.getInstance(this).setNotificationActionButton(buttonNo, size)
            setupNotificationActionButtonUI()

            dialog.dismiss()
        }

        dialog.show()
    }

    fun setupNotificationActionButtonUI() {
        val action1 = PreferenceHelper.getInstance(this).getNotificationActionButton(1)
        binding.tvButton1.text = getSwipeActionText(action1, this)

        val action2 = PreferenceHelper.getInstance(this).getNotificationActionButton(2)
        binding.tvButton2.text = getSwipeActionText(action2, this)

        val action3 = PreferenceHelper.getInstance(this).getNotificationActionButton(3)
        binding.tvButton3.text = getSwipeActionText(action3, this)
    }


    private fun showNotificationPreviewDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_notification_preview, null)

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroup)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.window?.decorView?.setPadding(0, 0, 0, 0)

        val width = (resources.displayMetrics.widthPixels * 0.75).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        // Load saved value
        val savedSize = PreferenceHelper.getInstance(this).getNotificationPreviewMode()

        when (savedSize) {
            NotificationPreviewMode.SHOW_NAME_AND_MESSAGES.value -> radioGroup.check(R.id.rbShowNameAndMessages)
            NotificationPreviewMode.SHOW_NAME.value -> radioGroup.check(R.id.rbShowName)
            NotificationPreviewMode.HIDE_CONTENTS.value -> radioGroup.check(R.id.rbHideContent)

        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val size = when (checkedId) {
                R.id.rbShowNameAndMessages -> NotificationPreviewMode.SHOW_NAME_AND_MESSAGES.value
                R.id.rbShowName -> NotificationPreviewMode.SHOW_NAME.value
                R.id.rbHideContent -> NotificationPreviewMode.HIDE_CONTENTS.value
                else -> NotificationPreviewMode.SHOW_NAME_AND_MESSAGES.value
            }


            PreferenceHelper.getInstance(this).setNotificationPreviewMode(size)
            setupNotificationPreviewModeUI()

            dialog.dismiss()
        }

        dialog.show()
    }

    fun setupNotificationPreviewModeUI() {
        val action = PreferenceHelper.getInstance(this).getNotificationPreviewMode()
        binding.tvNotificationPreviews.text = getNotificationPreviewModeText(action, this)
    }

}
