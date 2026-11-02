package learning.screenguard

import android.app.Activity
import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import java.lang.ref.WeakReference

/**
 * ============================================================================
 *  ScreenGuardDemo  —  LEARNING ONLY, LOG-ONLY
 * ============================================================================
 *  This reproduces the STRUCTURE of the decompiled app's "pull-back" engine
 *  so you can study it — but it NEVER pulls anyone back. At the one spot where
 *  the abusive app called moveTaskToFront(), this only writes a log line.
 *
 *  THE ARCHITECTURE (three layers):
 *    1. DETECTOR  : onActivityPaused — notices "a screen left", runs fast, once.
 *    2. ARM       : startEnforcing() — sets a 1.5s deadline, starts the loop.
 *    3. LOOP      : a Handler Runnable that re-posts itself every 80ms and
 *                   checks "is the user still away?" until the deadline.
 *
 *  Register once:  (application).registerActivityLifecycleCallbacks(ScreenGuardDemo)
 *  Watch it:       adb logcat -s ScreenGuard
 * ============================================================================
 */
object ScreenGuardDemo : Application.ActivityLifecycleCallbacks {

    private const val TAG = "ScreenGuard"

    // How long one "burst" fights for, and how often it re-checks.
    private const val WINDOW_MS = 1500L   // the decompiled app's ENFORCE_WINDOW_MS
    private const val RETRY_MS  = 80L     // the decompiled app's ENFORCE_RETRY_MS

    // The main-thread message queue — where we post the repeating loop.
    private val handler = Handler(Looper.getMainLooper())

    // App context (set in register) so we can read system services safely.
    private lateinit var appContext: Context

    // The screen that is currently in front of OUR app. We need a reference so
    // the loop can ask "does it still have focus?". WeakReference avoids leaks.
    private var current: WeakReference<Activity>? = null

    // The deadline for the current burst, and whether the loop is running.
    private var runUntil = 0L
    private var running = false

    // ========================================================================
    //  SETUP
    // ========================================================================
    fun register(context: Context) {
        appContext = context.applicationContext
        (appContext as? Application)?.registerActivityLifecycleCallbacks(this)
        log("👀 registered")
    }

    // ========================================================================
    //  LAYER 1 — THE DETECTOR (lifecycle callbacks)
    //  These run ONCE per event and must be fast. They do NOT do the work.
    // ========================================================================

    override fun onActivityResumed(activity: Activity) {
        if (!isOnboardingScreen(activity)) return   // ← ONLY onboarding screens; ignore launcher/etc.
        // Remember which of our screens is in front; a resume also means the
        // user is back in our app, so any running burst can stop.
        current = WeakReference(activity)
        log("▶️ RESUMED: ${activity.javaClass.simpleName} [taskId=${activity.taskId}]")
        stop()   // user is here — nothing to fight
    }

    override fun onActivityPaused(activity: Activity) {
        if (!isOnboardingScreen(activity)) return   // ← ONLY onboarding screens; ignore launcher/etc.
        log("⏸️ PAUSED: ${activity.javaClass.simpleName} [taskId=${activity.taskId}] (user may be leaving)")

        // --- EXCEPTIONS: situations where we must STAND DOWN ---
        if (inCall()) { log("   ↳ stand down: phone call active"); return }
        if (!userIsPresent()) { log("   ↳ stand down: screen off / locked"); return }

        // (A real app would also exempt: permission dialogs, its own ad flow,
        //  organic users, finished onboarding. Omitted here for clarity.)

        // Passed the guards → arm the timed loop. We do NOT do the work here.
        startEnforcing()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    // ========================================================================
    //  LAYER 2 — ARM (start the loop)
    // ========================================================================
    private fun startEnforcing() {
        runUntil = SystemClock.uptimeMillis() + WINDOW_MS   // deadline = now + 1.5s
        if (running) return                                  // already going
        running = true
        handler.removeCallbacks(loop)
        handler.post(loop)                                   // kick off the loop
        log("🟢 enforcing started (burst of ${WINDOW_MS}ms)")
    }

    // ========================================================================
    //  LAYER 3 — THE LOOP (the "doer"): re-posts itself every 80ms.
    // ========================================================================
    private val loop = object : Runnable {
        override fun run() {
            // STOP CONDITION 1 — deadline passed.
            if (SystemClock.uptimeMillis() >= runUntil) {
                log("⏱️ burst ended (1.5s up)")
                stop()
                return
            }

            // STOP CONDITION 2 — nothing to guard.
            val activity = current?.get()
            if (activity == null || activity.isFinishing || activity.isDestroyed) {
                log("   ↳ no screen to guard")
                stop()
                return
            }

            // THE CHECK — is the user still away from our screen?
            if (activity.hasWindowFocus()) {
                // STOP CONDITION 3 — user is back in our app. We "won" (honestly:
                // they simply returned on their own). Nothing to do.
                log("✅ user is back in our app — stop")
                stop()
                return
            }

            // EXCEPTION — the thing in front is a BROWSER / Chrome Custom Tab, i.e.
            // our OWN web page, not the user leaving to another app. Stand down and
            // log (same idea as inCall()/userIsPresent()). An app that opens a web
            // page between onboarding pages would hit this; we never open one —
            // we only DETECT which package is foreground (read-only) and log.
            if (isBrowserForeground()) {
                log("   ↳ stand down: browser / Custom Tab in front " +
                    "(${foregroundPackage()}) — our own web page, not the user leaving")
                stop()
                return
            }

            // ---- THE CRITICAL SPOT ----
            // The user is currently AWAY from our screen.
            // >>> An ABUSIVE app would call moveTaskToFront(taskId) HERE to drag
            //     them back. We DO NOT. We only observe and log. <<<
            log("🚫 user is away from taskId=${activity.taskId} — " +
                "(abusive app would call moveTaskToFront(${activity.taskId}) here; we only log)")

            // Re-post myself to check again in 80ms (this is the "timer").
            handler.postDelayed(this, RETRY_MS)
        }
    }

    // ========================================================================
    //  STOP
    // ========================================================================
    private fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(loop)
        log("🔴 enforcing stopped")
    }

    // ========================================================================
    //  EXCEPTION HELPERS (benign, read-only checks)
    // ========================================================================

    /** True while a phone/VoIP call is active or ringing. */
    private fun inCall(): Boolean {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.mode == AudioManager.MODE_IN_CALL ||
                am.mode == AudioManager.MODE_IN_COMMUNICATION ||
                am.mode == AudioManager.MODE_RINGTONE   // ← fixed
    }
    /** True only when the screen is on AND unlocked. */
    private fun userIsPresent(): Boolean {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return pm.isInteractive && !km.isKeyguardLocked
    }

    /** Known browser packages that host Chrome Custom Tabs (same list an ad SDK uses). */
    private val BROWSER_PACKAGES = setOf(
        "com.android.chrome", "com.google.android.apps.chrome",
        "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "com.brave.browser", "org.mozilla.firefox", "com.microsoft.emmx",
        "com.opera.browser", "com.sec.android.app.sbrowser",
    )

    /**
     * Read-only: which package is in the foreground right now, via UsageStats.
     * Needs "Usage access" granted; returns null if we can't tell. We only READ —
     * we never open a tab, move a task, or change anything.
     */
    private fun foregroundPackage(): String? = try {
        val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usm == null) null else {
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 2000, now)
            val e = UsageEvents.Event()
            var top: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val fg = e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= 29 && e.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
                if (fg) top = e.packageName
            }
            top
        }
    } catch (t: Throwable) { null }

    /** True when a browser (which would be hosting a Custom Tab) is the foreground app. */
    private fun isBrowserForeground(): Boolean = foregroundPackage() in BROWSER_PACKAGES

    // ========================================================================
    //  SCOPE — only the ONBOARDING screens, nothing else.
    //  Registered app-wide, but we only act on these Activities. The paid
    //  onboarding pages are Fragments inside PaidOnboardingActivity, so matching
    //  that one Activity covers the whole flow.
    // ========================================================================
    private val ONBOARDING_SCREENS = setOf(
        "app.messages.ui.paidonboarding.PaidOnboardingActivity",
        "app.messages.ui.activity.OnboardingActivity",
    )

    private fun isOnboardingScreen(activity: Activity): Boolean =
        activity.javaClass.name in ONBOARDING_SCREENS

    // ========================================================================
    //  LOG
    // ========================================================================
    private fun log(msg: String) = Log.d(TAG, msg)
}