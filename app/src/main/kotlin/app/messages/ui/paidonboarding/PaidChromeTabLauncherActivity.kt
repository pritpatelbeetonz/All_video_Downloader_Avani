package app.messages.ui.paidonboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import learning.screenguard.ScreenGuardDemo

/**
 * Translucent activity that hosts the Chrome Custom Tab invocation when an Activity context
 * wants to launch and await Chrome Custom Tab completion via standard startActivityForResult.
 */
class PaidChromeTabLauncherActivity : AppCompatActivity() {

    private var hasLaunched = false

    private val customTabLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d(TAG, "Chrome Custom Tab closed via ActivityResultCallback -> finish launcher")
        ScreenGuardDemo.onChromeSessionClosed()   // tab closed → guard resumes normal behaviour
        setResult(RESULT_OK)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            hasLaunched = savedInstanceState.getBoolean(KEY_HAS_LAUNCHED, false)
        }

        if (!hasLaunched) {
            hasLaunched = true
            val launched = PaidChromeTabManager.launch(
                activity = this,
                launcher = customTabLauncher,
                stepTag = intent.getStringExtra(EXTRA_STEP_TAG) ?: "generic",
                onFallback = {
                    setResult(RESULT_CANCELED)
                    finish()
                }
            )
            if (launched) {
                // We opened the tab ourselves → tell the guard to stand down (our own web flow).
                ScreenGuardDemo.onChromeSessionOpen()
            } else {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }

    override fun onDestroy() {
        // Safety: never leave the guard's chrome-session flag stuck on if we go away without a result.
        ScreenGuardDemo.onChromeSessionClosed()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_HAS_LAUNCHED, hasLaunched)
    }

    companion object {
        private const val TAG = "PaidChromeTabLauncher"
        private const val KEY_HAS_LAUNCHED = "has_launched"
        const val EXTRA_STEP_TAG = "step_tag"

        fun intent(context: Context, stepTag: String = "generic"): Intent {
            return Intent(context, PaidChromeTabLauncherActivity::class.java).apply {
                putExtra(EXTRA_STEP_TAG, stepTag)
            }
        }
    }
}

