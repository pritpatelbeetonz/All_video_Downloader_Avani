package app.messages.ui.activity

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import app.messages.R
import app.messages.databinding.ActivitySwipeActionBinding
import app.messages.enumeration.SwipeActions
import app.messages.enumeration.getSwipeActionColor
import app.messages.enumeration.getSwipeActionIcon
import app.messages.enumeration.getSwipeActionText
import app.messages.preferences.PreferenceHelper
import app.messages.ui.base.BaseActivity
import app.messages.analytics.AnalyticsHelper
import app.messages.analytics.EventNames
import app.messages.analytics.EventScreenName

class SwipeActionActivity : BaseActivity() {

    private lateinit var binding: ActivitySwipeActionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySwipeActionBinding.inflate(layoutInflater)
        setContentView(binding.root)


        initToolbar()

        setupClicks()
        setupRightSwipeUI()
        setupLeftSwipeUI()
    }

    private fun setupRightSwipeUI() {
        val action = PreferenceHelper.getInstance(this).getRightSwipeAction()
        binding.tvSelectedRightSwipe.text = getSwipeActionText(action,this)
        binding.imgRightSwipe.setImageResource(getSwipeActionIcon(action))
        binding.flRightSwipe.setBackgroundColor(getSwipeActionColor(action,this))
    }

    private fun setupLeftSwipeUI() {
        val action = PreferenceHelper.getInstance(this).getLeftSwipeAction()
        binding.tvSelectedLeftSwipe.text = getSwipeActionText(action,this)
        binding.imgLeftSwipe.setImageResource(getSwipeActionIcon(action))
        binding.flLeftSwipe.setBackgroundColor(getSwipeActionColor(action,this))
    }

    private fun setupClicks() {
        binding.ivChangeRightSwipe.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.SETTINGS_CLICK_SWIPE_ACTIONS, EventScreenName.SWIPE_ACTIONS)
            showSwipeActionDialog(true)
        }
        binding.ivChangeLeftSwipe.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.SETTINGS_CLICK_SWIPE_ACTIONS, EventScreenName.SWIPE_ACTIONS)
            showSwipeActionDialog(false)
        }
    }

    private fun initToolbar() {
        binding.incToolbar.tvTitle.text = getString(R.string.swipe_actions)
        binding.incToolbar.tvDone.visibility = View.GONE
        binding.incToolbar.ivBack.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.CLICK_BACK, EventScreenName.SWIPE_ACTIONS)
            finish()
        }
    }


    private fun showSwipeActionDialog(isRightSwipeAction: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_swipe_action, null)

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupSwipeAction)

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
        val savedSize = if (isRightSwipeAction)
            PreferenceHelper.getInstance(this).getRightSwipeAction()
        else
            PreferenceHelper.getInstance(this).getLeftSwipeAction()

        when (savedSize) {
            SwipeActions.NONE.value -> radioGroup.check(R.id.rbNone)
            SwipeActions.ARCHIVE.value -> radioGroup.check(R.id.rbArchive)
            SwipeActions.DELETE.value -> radioGroup.check(R.id.rbDelete)
            SwipeActions.BLOCK.value -> radioGroup.check(R.id.rbBlock)
            SwipeActions.CALL.value -> radioGroup.check(R.id.rbCall)
            SwipeActions.MARK_READ.value -> radioGroup.check(R.id.rbMArkRead)
            SwipeActions.MARK_UNREAD.value -> radioGroup.check(R.id.rbMarkUnread)
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val size = when (checkedId) {
                R.id.rbNone -> SwipeActions.NONE.value
                R.id.rbArchive -> SwipeActions.ARCHIVE.value
                R.id.rbDelete -> SwipeActions.DELETE.value
                R.id.rbBlock -> SwipeActions.BLOCK.value
                R.id.rbCall -> SwipeActions.CALL.value
                R.id.rbMArkRead -> SwipeActions.MARK_READ.value
                R.id.rbMarkUnread -> SwipeActions.MARK_UNREAD.value
                else -> SwipeActions.NONE.value
            }

            if (isRightSwipeAction) {
                PreferenceHelper.getInstance(this).setRightSwipeAction(size)
                setupRightSwipeUI()
            } else {
                PreferenceHelper.getInstance(this).setLeftSwipeAction(size)
                setupLeftSwipeUI()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    /* private fun configureActionButton(buttonIndex: Int) {
         val prefs = PreferenceHelper.getInstance(this)
         val options = arrayOf(
             getString(R.string.none),
             getString(R.string.archived),
             getString(R.string.delete),
             getString(R.string.block),
             getString(R.string.call),
             getString(R.string.mark_read),
             getString(R.string.reply)
         )
         val actionMap = intArrayOf(
             app.messages.utility.NotificationHelper.ACTION_NONE,
             app.messages.utility.NotificationHelper.ACTION_ARCHIVE,
             app.messages.utility.NotificationHelper.ACTION_DELETE,
             app.messages.utility.NotificationHelper.ACTION_BLOCK,
             app.messages.utility.NotificationHelper.ACTION_CALL,
             app.messages.utility.NotificationHelper.ACTION_MARK_READ,
             app.messages.utility.NotificationHelper.ACTION_REPLY
         )

         val currentAction = prefs.getNotificationActionButton(buttonIndex)
         val checked = actionMap.indexOf(currentAction).takeIf { it >= 0 } ?: 0

         AlertDialog.Builder(this)
             .setTitle(getString(R.string.button_n, buttonIndex))
             .setSingleChoiceItems(options, checked) { dialog, which ->
                 prefs.setNotificationActionButton(buttonIndex, actionMap[which])
                 dialog.dismiss()
                 if (buttonIndex < 3) {
                     configureActionButton(buttonIndex + 1)
                 } else {
                     }
             }
             .show()
     }*/
}