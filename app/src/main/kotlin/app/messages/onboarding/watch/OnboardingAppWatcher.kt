package app.messages.onboarding.watch

import android.app.Activity
import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.messages.BuildConfig
import app.messages.data.repository.UsageStatsRepository
import app.messages.ui.paidonboarding.PaidOnboardingPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.app.ActivityManager

/**
 * ============================================================================
 *  OnboardingAppWatcher  —  a LEARNING / LOG-ONLY screen watcher
 * ============================================================================
 *
 *  WHAT THIS CLASS IS
 *  ------------------
 *  A tiny observer that watches screen changes and WRITES LOG LINES about them.
 *  That is all it does. It never blocks, forces, covers, or pulls the user back.
 *  It is the honest, Play-safe version of the "watcher" pattern.
 *
 *  It uses three ordinary, built-in Android tools — the same tools analytics
 *  SDKs and crash reporters use every day:
 *
 *    1. Application.ActivityLifecycleCallbacks
 *         → Android calls us every time ONE OF OUR screens resumes/pauses.
 *
 *    2. ProcessLifecycleOwner
 *         → tells us when the WHOLE APP moves to foreground / background
 *           (i.e. the user left to another app, then came back).
 *
 *    3. UsageStatsManager (needs "Usage access", granted by the user in Settings)
 *         → when the user comes back, we REPLAY which OTHER apps they opened
 *           while they were away. This is a look-back over history, NOT live
 *           per-second spying.
 *
 *  WHY IT IS NOT A BACKGROUND SERVICE
 *  ----------------------------------
 *  It only runs while our process is alive. Android may kill it in the
 *  background, so the UsageStats replay fills the recent gap, but a long
 *  absence can be cut off. True live background watching would need a
 *  foreground service (FOREGROUND_SERVICE + a permanent notification + a Play
 *  declaration) — we deliberately do NOT do that.
 *
 *  THE IMPORTANT LINE (read this)
 *  ------------------------------
 *  Every callback below starts with the SAME guard:
 *
 *        if (!PaidOnboardingPrefs.shouldShow(appContext)) return
 *
 *  → once onboarding is finished, this watcher goes completely silent.
 *
 *  Scattered through the file are commented-out blocks marked
 *  "ORIGINAL FunnelGuard (reference only)". Those are the DECOMPILED code from
 *  the adware app we studied. They are kept here ONLY as inert, non-compiled
 *  notes so you can SEE the difference: the real app put BLOCKING / FORCING
 *  code in the exact same callbacks where we only write a log line. Those
 *  blocks never run; they are comments.
 *
 *  Logcat filter:  tag:OnboardingWatch
 */
object OnboardingAppWatcher {

    // The tag we filter Logcat by. Run `adb logcat -s OnboardingWatch` to watch.
    private const val TAG = "OnboardingWatch"

    // A background scope for the UsageStats read (it touches the system service,
    // so we keep it off the main thread). SupervisorJob = one failing job does
    // not cancel the others.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Set once in register(). Application context so we never leak an Activity.
    private lateinit var appContext: Context

    // Helper that knows how to read UsageStats and whether access is granted.
    private lateinit var usage: UsageStatsRepository

    // Guard so register() is safe to call many times (idempotent).
    private var registered = false

    // The moment the app went to the background. 0 = we are in the foreground.
    private var leftAt = 0L

    // ========================================================================
    //  OBSERVER 1 — the WHOLE APP going foreground / background
    //  (ProcessLifecycleOwner). This is how we learn "user left" / "user back".
    // ========================================================================
    private val processObserver = object : DefaultLifecycleObserver {

        // Called when the app comes BACK to the foreground.
        override fun onStart(owner: LifecycleOwner) {
            // If leftAt is 0 we never actually left — ignore (e.g. first start).
            if (leftAt == 0L) return

            // STEP 1: how long were we away, and since when.
            val awayMs = System.currentTimeMillis() - leftAt
            val since = leftAt
            leftAt = 0L   // reset: we are back in the foreground now

            // STEP 2: the cloaking-safe guard — do nothing once setup is done.
            if (!PaidOnboardingPrefs.shouldShow(appContext)) return

            // STEP 3: just log it, then replay what happened while away.
            log("➡️ Back in onboarding after ${awayMs}ms away")
            replayOtherAppEvents(since)
        }

        // Called when the app goes to the BACKGROUND (user left to another app).
        override fun onStop(owner: LifecycleOwner) {
            if (!PaidOnboardingPrefs.shouldShow(appContext)) return

            // Remember WHEN we left, so onStart() can measure the gap and replay.
            leftAt = System.currentTimeMillis()
            log("⬅️ Left onboarding (app backgrounded)")

            // --- THIS is the fork in the road between honest and abusive. ---
            // We STOP here: we only noted the time and logged it.
            // The adware instead REACTED here to drag the user back.
        }
    }



    /** Brings THIS app's own task to the front. Call it from a USER action. */
    fun bringOwnTaskToFront(context: Context, taskId: Int) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.moveTaskToFront(taskId, 0)
    }



    // ========================================================================
    //  OBSERVER 2 — each of OUR OWN screens resuming / pausing
    //  (Application.ActivityLifecycleCallbacks). Fires per Activity.
    // ========================================================================
    private val activityObserver = object : Application.ActivityLifecycleCallbacks {

        // One of OUR screens came to the front.
        override fun onActivityResumed(activity: Activity) {
            if (!PaidOnboardingPrefs.shouldShow(appContext)) return
            if (!isOnboardingScreen(activity)) return   // ← only onboarding, not launcher/other screens

            // HONEST VERSION: write one line naming the screen. Nothing else.
            log("▶️ RESUMED (ours): ${activity.javaClass.simpleName} [taskId=${activity.taskId}]")
        }

        /* --------------------------------------------------------------------
         *  ORIGINAL FunnelGuard.onActivityResumed  (reference only — NOT run)
         *  --------------------------------------------------------------------
         *  WHAT THE DEVELOPER DID in this exact callback:
         *   - saved the current Activity into `lastResumed` (so it always knows
         *     which screen to force back to),
         *   - then called `startAdSettingsBlock()` — a fast polling loop that
         *     locks the notification shade and shoves the app back on top.
         *  i.e. the SAME event we just log, they used to ARM the trap.
         *
         *  // Kotlin
         *  // override fun onActivityResumed(activity: Activity) {
         *  //     if (activity.packageName == activity.applicationContext.packageName) {
         *  //         FunnelGuard.lastResumed = WeakReference(activity)
         *  //         if (!isFullScreenAd(activity) && FunnelGuard.adActivities == 0
         *  //             && !AdsGate.isBusy() && !holdWebAd()) {
         *  //             FunnelGuard.externalFlow = false
         *  //             FunnelGuard.adSessionAt = 0L
         *  //         }
         *  //         startAdSettingsBlock()   // ← arms the shade-lock / pull-back loop
         *  //     }
         *  // }
         * ------------------------------------------------------------------ */

        // One of OUR screens is leaving (user navigating away).
        override fun onActivityPaused(activity: Activity) {
            if (!PaidOnboardingPrefs.shouldShow(appContext)) return
            if (!isOnboardingScreen(activity)) return   // ← only onboarding, not launcher/other screens

            // HONEST VERSION: write one line. We let the user leave.
            log("⏸️ PAUSED (ours): ${activity.javaClass.simpleName} [taskId=${activity.taskId}]")
        }

        /* --------------------------------------------------------------------
         *  ORIGINAL FunnelGuard.onActivityPaused  (reference only — NOT run)
         *  --------------------------------------------------------------------
         *  WHAT THE DEVELOPER DID: the moment a screen paused (user leaving),
         *  it posted work to the FRONT of the main-thread queue to react
         *  immediately — part of the machinery that pulls the user back.
         *  Again: same callback, but "act" instead of "log".
         *
         *  // Kotlin
         *  // override fun onActivityPaused(activity: Activity) {
         *  //     if (!isFullScreenAd(activity) || activity.isFinishing()
         *  //         || activity.isChangingConfigurations()) return
         *  //     FunnelGuard.handler.postAtFrontOfQueue {
         *  //         FunnelGuard.onActivityPaused_lambda(activity)   // react / pull back
         *  //     }
         *  // }
         * ------------------------------------------------------------------ */

        // The other lifecycle callbacks exist on the interface but we don't
        // need them, so they do nothing (= Unit).
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    // ========================================================================
    //  REGISTER — the single "on switch" for the watcher.
    //  Call this once, when the app becomes the default launcher.
    // ========================================================================
    /** Idempotent; call it when the app becomes default launcher. */
    fun register(context: Context) {
        // Guard: if already registered, do nothing (safe to call repeatedly).
        if (registered) return
        registered = true

        // Use the application context (never an Activity) to avoid leaks.
        appContext = context.applicationContext
        usage = UsageStatsRepository(appContext)

        // Wire up observer 1: whole-app foreground/background.
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)

        // Wire up observer 2: per-Activity resume/pause for OUR screens.
        // This one line is the "watcher" — the exact same call the adware used.
        // The difference is entirely in what the callbacks above DO.
        (appContext as? Application)?.registerActivityLifecycleCallbacks(activityObserver)

        log("👀 Watcher registered (usageAccess=${usage.hasUsageAccess()})")
    }

    // ========================================================================
    //  REPLAY — read UsageStats and log which OTHER apps were used while away.
    //  This is a LOOK-BACK over history, not live tracking.
    // ========================================================================
    /** Replays the RESUMED / PAUSED events of other apps between [sinceMs] and now. */
    private fun replayOtherAppEvents(sinceMs: Long) {
        // If the user never granted Usage access, we can see nothing. Say so.
        if (!usage.hasUsageAccess()) {
            log("ℹ️ No Usage access granted; can't see other apps' resume/pause")
            return
        }

        // Do the read off the main thread.
        scope.launch {
            val usm = appContext.getSystemService(UsageStatsManager::class.java) ?: return@launch

            // Ask the system for every usage event in the window we were away.
            val events = usm.queryEvents(sinceMs, System.currentTimeMillis())
            val e = UsageEvents.Event()
            var count = 0

            // Walk the events one by one.
            while (events.hasNextEvent()) {
                events.getNextEvent(e)

                val pkg = e.packageName ?: continue
                if (pkg == appContext.packageName) continue   // skip our own app

                // Translate the raw event type into a readable label.
                // Older Android uses MOVE_TO_FOREGROUND/BACKGROUND; API 29+ adds
                // ACTIVITY_RESUMED/PAUSED. We handle both.
                val label = when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> "▶️ RESUMED"
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> "⏸️ PAUSED"
                    else -> if (Build.VERSION.SDK_INT >= 29) when (e.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> "▶️ RESUMED"
                        UsageEvents.Event.ACTIVITY_PAUSED -> "⏸️ PAUSED"
                        else -> null
                    } else null
                } ?: continue   // skip event types we don't care about

                count++
                log("$label (other): $pkg at ${e.timeStamp}")
            }

            // If nothing matched, say so — makes the log easy to read.
            if (count == 0) log("ℹ️ No other app resume/pause recorded while away")

            // NOTE: an abusive app would, right here, look for a "bad" package
            // (e.g. Settings) and then force itself back. We do NOT — we only
            // counted and logged. This is the honest boundary of the file.
        }
    }

    // ========================================================================
    //  SCOPE FILTER — only react on the ONBOARDING screens, nothing else.
    //  The watcher is registered app-wide (so it CAN see every screen), but we
    //  only want it active during the onboarding flow — not on the launcher,
    //  the conversation list, settings, etc. This check limits it to the
    //  onboarding Activities. The paid onboarding pages are Fragments inside
    //  PaidOnboardingActivity, so matching that one Activity covers them all.
    //  Add any other onboarding Activity's full class name to the set below.
    // ========================================================================
    private val ONBOARDING_SCREENS = setOf(
        "app.messages.ui.paidonboarding.PaidOnboardingActivity",
        "app.messages.ui.activity.OnboardingActivity",
    )

    private fun isOnboardingScreen(activity: Activity): Boolean =
        activity.javaClass.name in ONBOARDING_SCREENS

    // ========================================================================
    //  LOG — one place, DEBUG builds only. In a release build this is silent,
    //  so the watcher ships no log noise to real users.
    // ========================================================================
    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
