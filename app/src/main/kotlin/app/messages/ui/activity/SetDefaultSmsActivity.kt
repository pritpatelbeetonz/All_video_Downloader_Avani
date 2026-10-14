package app.messages.ui.activity

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import app.messages.R
import app.messages.databinding.ActivitySetDefaultSmsBinding
import app.messages.ui.base.BaseActivity

class SetDefaultSmsActivity : BaseActivity() {

    private lateinit var binding: ActivitySetDefaultSmsBinding

    /**
     * Robust check for default SMS app status.
     * Uses RoleManager on Android Q+ (more reliable on OEM devices like Samsung/Xiaomi/MIUI).
     * Falls back to Telephony.Sms.getDefaultSmsPackage() on older devices.
     */
    private fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(this) == packageName
        }
    }

    private val smsRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (isDefaultSmsApp()) {
            goToHome()
        } else {
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetDefaultSmsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // If already default, skip this screen immediately
        if (isDefaultSmsApp()) {
            goToHome()
            return
        }

        binding.btnNext.setOnClickListener {
            requestDefaultSmsRole()
        }

        setupGlareAnimation()
    }

    override fun onResume() {
        super.onResume()
        // Auto-navigate if user granted default SMS role and returned to this activity
        if (isDefaultSmsApp()) {
            goToHome()
        }
    }

    /** Same sweeping highlight the set-default-launcher button uses. */
    private fun setupGlareAnimation() {
        binding.ivGlare.post {
            val width = binding.btnNext.width.toFloat()
            android.animation.ObjectAnimator.ofFloat(binding.ivGlare, "translationX", -150f, width).apply {
                duration = 1500
                repeatMode = android.animation.ValueAnimator.RESTART
                repeatCount = android.animation.ValueAnimator.INFINITE
                startDelay = 1000
                start()
            }
        }
    }

    private fun requestDefaultSmsRole() {
        if (!isDefaultSmsApp()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                    if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                        smsRoleLauncher.launch(intent)
                    } else {
                        goToHome()
                    }
                } else {
                    // Fallback if RoleManager is not available or ROLE_SMS is not available
                    launchLegacyDefaultSmsRequest()
                }
            } else {
                launchLegacyDefaultSmsRequest()
            }
        } else {
            goToHome()
        }
    }

    private fun launchLegacyDefaultSmsRequest() {
        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        smsRoleLauncher.launch(intent)
    }

    private fun goToHome() {
        // Log one-time Firebase Analytics event for setting default SMS handler
        app.messages.analytics.AnalyticsHelper.logOnce(
            context = this,
            prefKey = app.messages.preferences.PreferenceHelper.KEY_EVENT_DEFAULT_SMS_LOGGED,
            eventName = app.messages.analytics.EventNames.SMS_PERMISSION_GRANTED
        )

        // Plain intent on purpose — LauncherActivity is singleTask and owns the home
        // task, so no task flags are needed and CLEAR_TASK here would be harmful.
        startActivity(
            Intent(this, app.messages.ui.launcher.LauncherActivity::class.java)
                .putExtra("open_chats_first", true)
        )
        finish()
    }


}