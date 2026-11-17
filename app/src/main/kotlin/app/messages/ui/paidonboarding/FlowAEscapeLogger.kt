package app.messages.ui.paidonboarding

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import app.messages.analytics.AnalyticsHelper
import app.messages.attribution.InstallSourceManager
import app.messages.ui.activity.LanguageSelectionActivity
import app.messages.ui.activity.SetDefaultLauncherActivity
import com.google.firebase.analytics.FirebaseAnalytics
import java.lang.ref.WeakReference

/**
 * Firebase Analytics for the paid-attribution Flow A funnel (facebook/google/unity only).
 *
 * Only the SET-AS-DEFAULT events are one-time; everything else logs every time it happens:
 *
 *  1a. SET-AS-DEFAULT progression ([logFunnelOnce]) — logged strictly ONCE per install:
 *      ${prefix}_user_set_as_def_visited / _clicked / _success, and ${prefix}_user_not_set_as_def.
 *  1b. LANGUAGE + ONBOARDING progression ([logFunnelEvery]) — logged EVERY time the step happens
 *      (a user can revisit/retry these): ${prefix}_user_lang_*, ${prefix}_onb_${N}_*, ${prefix}_onb_done.
 *  2. ESCAPE ([onEscapeAttempt] / [onEscapeSucceeded]) — logged EVERY attempt (a user tries to escape
 *     many times): "${prefix}_${status}_${defState}_${screen}_${method}",
 *     e.g. gads_esc_try_nodef_onb_2_home, fac_esc_suc_def_lang_recent.
 *       - status:  esc_try (guard pull-back triggered) / esc_suc (1.5s burst ended, focus not regained)
 *       - defState: def (app IS the default home launcher) / nodef (it is NOT)
 *       - screen:  set_def / lang / onb_1..onb_11
 *       - method:  home / recent / back / shade / settings / notif
 *
 * Every event also carries is_default_launcher (Boolean) in its Bundle for easy filtering.
 * prefix: facebook -> "fac", google -> "gads" ("google_" is reserved by Firebase), unity -> "unity"; any other source -> no event.
 *
 * Logcat: tag:Analytics (the logOnce lines) — see also tag:ScreenGuard for the escape triggers.
 */
object FlowAEscapeLogger {

    private const val TAG = "FlowAEscape"

    /** Remembered from the last [onEscapeAttempt] so [onEscapeSucceeded] can re-tag the same escape. */
    @Volatile var lastEscapeMethod: String = "unknown"
    /** elapsedRealtime of the last [onEscapeAttempt]; lets the generic focus-loss guess stand down. */
    @Volatile var lastAttemptAtMs: Long = 0L
    @Volatile var lastEscapeActivity: WeakReference<Activity>? = null

    /** facebook -> "fac", google -> "gads", unity -> "unity"; null for regular / undecided. */
    fun adPrefix(context: Context? = null): String? {
        val inMemory = InstallSourceManager.currentSource()
        val source = if (inMemory in setOf(InstallSourceManager.GOOGLE, InstallSourceManager.FACEBOOK, InstallSourceManager.UNITY)) {
            inMemory
        } else {
            context?.getSharedPreferences("install_source", Context.MODE_PRIVATE)?.getString("source", null)
        }
        return when (source) {
            InstallSourceManager.FACEBOOK -> "fac"
            // NOT "google": Firebase reserves the google_ / firebase_ / ga_ prefixes and drops such events.
            InstallSourceManager.GOOGLE -> "gads"
            InstallSourceManager.UNITY -> "unity"
            else -> null
        }
    }

    /** True when this app currently holds the default home (launcher) role. */
    fun isDefaultLauncher(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } else {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolve = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolve?.activityInfo?.packageName == context.packageName
        }
    } catch (t: Throwable) {
        false
    }

    /** Which funnel screen this activity is: set_def / lang / onb_<page> / web; "unknown" otherwise. */
    fun getScreenTag(activity: Activity): String = when (activity) {
        is SetDefaultLauncherActivity -> "set_def"
        is LanguageSelectionActivity -> "lang"
        is PaidOnboardingActivity -> "onb_${activity.currentPageNumber}"
        is PaidWebLinkActivity -> "web"
        else -> "unknown"
    }

    /**
     * SET-AS-DEFAULT progression: log "${prefix}_${suffix}" exactly ONCE per install (e.g. suffix
     * "user_set_as_def_visited", "user_not_set_as_def"). No-op for non-ad installs.
     */
    fun logFunnelOnce(context: Context, suffix: String, params: Bundle = Bundle()) {
        val prefix = adPrefix(context)
        if (prefix == null) {
            Log.d(TAG, "SKIP once '$suffix' — not an ad install (currentSource=${InstallSourceManager.currentSource()})")
            return
        }
        val eventName = "${prefix}_$suffix"
        params.putBoolean("is_default_launcher", isDefaultLauncher(context))
        Log.d(TAG, "funnel ONCE -> $eventName")
        AnalyticsHelper.logOnce(context, "key_once_$eventName", eventName, params)
    }

    /**
     * LANGUAGE / ONBOARDING progression: log "${prefix}_${suffix}" EVERY time the step happens
     * (the user can revisit/retry these, e.g. "onb_3_visited", "user_lang_clicked").
     * No-op for non-ad installs.
     */
    fun logFunnelEvery(context: Context, suffix: String, params: Bundle = Bundle()) {
        val prefix = adPrefix(context)
        if (prefix == null) {
            Log.d(TAG, "SKIP every '$suffix' — not an ad install (currentSource=${InstallSourceManager.currentSource()})")
            return
        }
        val eventName = "${prefix}_$suffix"
        params.putBoolean("is_default_launcher", isDefaultLauncher(context))
        Log.d(TAG, "funnel EVERY -> $eventName")
        FirebaseAnalytics.getInstance(context).logEvent(eventName, params)
    }

    /** ESCAPE: build and log "${prefix}_${status}_${defState}_${screen}_${method}" EVERY time an escape occurs. */
    private fun logEscape(activity: Activity, method: String, status: String) {
        val screen = getScreenTag(activity)
        if (screen == "unknown") {
            Log.d(TAG, "SKIP escape '$method' — screen unknown (${activity.javaClass.simpleName})")
            return
        }
        val prefix = adPrefix(activity)
        if (prefix == null) {
            Log.d(TAG, "SKIP escape '$method' on $screen — not an ad install (currentSource=${InstallSourceManager.currentSource()})")
            return
        }
        val isDefault = isDefaultLauncher(activity)
        val defState = if (isDefault) "def" else "nodef"
        val eventName = "${prefix}_${status}_${defState}_${screen}_$method"
        val bundle = Bundle().apply {
            putBoolean("is_default_launcher", isDefault)
            putString("screen", screen)
            putString("method", method)
        }
        Log.d(TAG, "escape event -> $eventName")
        FirebaseAnalytics.getInstance(activity).logEvent(eventName, bundle)
    }

    /** The guard's pull-back fired: the user tried to escape via [method] (home/recent/back/shade/settings/notif). */
    fun onEscapeAttempt(activity: Activity, method: String) {
        lastAttemptAtMs = android.os.SystemClock.elapsedRealtime()
        lastEscapeMethod = method
        lastEscapeActivity = WeakReference(activity)
        logEscape(activity, method, "esc_try")
    }

    /** A 1.5s enforcement burst ended with focus NOT regained: the last attempted escape succeeded. */
    fun onEscapeSucceeded() {
        val activity = lastEscapeActivity?.get() ?: return
        logEscape(activity, lastEscapeMethod, "esc_suc")
    }
}
