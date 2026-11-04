package learning.screenguard

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Intent
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
import android.widget.Toast
import app.messages.onboarding.watch.OnboardingAppWatcher.bringOwnTaskToFront
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
    private const val WEB_GUARD_MS = 300L // how often we re-check the Custom Tab is still in front

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
    //  REAL OEM TECHNIQUES — ported from the decompiled FunnelGuard for STUDY.
    //  Flip these ON ONE AT A TIME to see what this device allows vs blocks.
    //  Everything is try/catch-wrapped and logged. SAFETY: the shade is always
    //  re-enabled in stop(), so a test can never leave your shade disabled.
    // ========================================================================
    private const val ENABLE_HIDDEN_API_EXEMPT     = true   // bootstrap; the shade calls need it
    private const val ENABLE_REFLECTED_FOREGROUND  = true   // read top package via hidden ActivityTaskManager when UsageStats is null (no Usage access needed)
    private const val ENABLE_REFLECTED_SILENT_MOVE = false  // hidden 3-arg moveTaskToFront(taskId, 2, bundle)
    private const val ENABLE_COLLAPSE_SHADE        = true   // collapsePanels()/collapse() + CLOSE_SYSTEM_DIALOGS — the reference runs this
    private const val ENABLE_LOCK_SHADE            = true   // IStatusBarService.disable()/disable2() — most invasive (needs STATUS_BAR perm → usually fail-logs on normal devices)

    // Shared Home/Recents trap on EVERY funnel screen (the reference's base FunnelScreenActivity):
    //  - HOME  → route forward to the Language screen (can't go out, only forward).
    //  - RECENTS → pull the SAME screen back and hold.
    // Both collapse + lock the shade and show the blocked toast. Gated on non-organic (paid) installs
    // still in onboarding at the call site. Flip to false to disable the whole shared trap.
    private const val ENABLE_FUNNEL_TRAP = true

    // The piece that actually COLLAPSES the Recents/overview surface: re-launch our own activity to
    // the front with REORDER_TO_FRONT. moveTaskToFront alone reorders the task stack but does NOT
    // dismiss an already-open overview on many OEMs — the reference's bringActivityTaskToFront does
    // BOTH (moveTask + this reorder) every time. Flip false to A/B "moveTask only" vs "moveTask+reorder".
    private const val ENABLE_REORDER_TO_FRONT = true

    private const val ENABLE_BLOCKED_TOAST = true   // "complete the steps" toast when pulled back
    private const val BLOCKED_TOAST_TEXT = "Please complete the steps to continue"

    // While the Custom Tab is open it's OUR flow, not the user escaping — the guard stands down.
    // ENABLE_RECOVER_CHROME additionally re-opens the tab if the user wanders off it.
    private const val ENABLE_RECOVER_CHROME = true   // re-open the tab if the user wanders off it (uses the reflected foreground read above)

    // FunnelGuard's PERMISSION-FREE leave detector (the real core of the reference app):
    // count OUR OWN started activities. When the count drops to 0 the whole app is backgrounded
    // (user pressed Home or switched away), so after a short delay we enforce — pulling our task
    // back via hasWindowFocus + moveTaskToFront. No Usage Access and no reflection needed, because
    // we never ask "what app is in front?"; we only watch our own activities and our own focus.
    private const val ENABLE_BACKGROUND_LEAVE_GUARD = true
    private const val LEAVE_CHECK_DELAY_MS = 90L
    private const val TAB_HIDDEN_DELAY_MS = 350L   // confirm a TAB_HIDDEN is a real escape, not a legit close

    private var hiddenApiExempt = false
    private var shadeLocked = false
    private var lastBlockedToastAt = 0L
    private var registered = false
    @Volatile private var chromeSessionOpen = false
    @Volatile private var adShowing = false   // our own full-screen App Open ad is up — guard must NOT touch it
    @Volatile private var systemPickerOpen = false   // the system role/default picker is up — don't yank over it
    private var selfReorderAt = 0L
    private var webGuardRunning = false
    private var startedCount = 0            // how many of OUR activities are currently started (visible-ish)



    // ========================================================================
    //  SETUP
    // ========================================================================
    fun register(context: Context) {
        if (registered) return   // already listening — don't stack another lifecycle callback (double events)
        appContext = context.applicationContext
        registered = true
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
        setShadeLocked(activity, false)   // back on our screen → always re-enable the shade (reference onStepResumed)
    }

    override fun onActivityPaused(activity: Activity) {
        if (!isOnboardingScreen(activity)) return   // ← ONLY onboarding screens; ignore launcher/etc.
        log("⏸️ PAUSED: ${activity.javaClass.simpleName} [taskId=${activity.taskId}] (user may be leaving)")

        // --- EXCEPTIONS: situations where we must STAND DOWN ---
        if (adShowing) { log("   ↳ stand down: our App Open ad is on screen (don't close/fight it)"); return }
        if (isOwnNavigation()) { log("   ↳ stand down: our own screen transition in progress (not the user leaving)"); return }
        if (systemPickerOpen) { log("   ↳ stand down: the system default/role picker is open (our own request)"); return }
        if (chromeSessionOpen) { log("   ↳ stand down: our Chrome Custom Tab session is open (our own web flow)"); return }
        // Only ignore the pause when it's OUR 250ms reorder AND the screen still has focus. If focus is
        // gone, the app genuinely went to background (e.g. the user tapped Settings from the shade) —
        // that's a real escape we must pull back from, not our own reorder. (Reference onStepPaused.)
        if (isSelfReorderPause() && activity.hasWindowFocus()) { log("   ↳ stand down: our own reorder < 250ms ago & still focused"); return }
        if (inCall()) { log("   ↳ stand down: phone call active"); return }
        if (!userIsPresent()) { log("   ↳ stand down: screen off / locked"); return }

        // (A real app would also exempt: permission dialogs, its own ad flow,
        //  organic users, finished onboarding. Omitted here for clarity.)

        // Passed the guards → arm the timed loop. We do NOT do the work here.
        startEnforcing()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    // --- FunnelGuard's started-activity counter: the permission-free "did the user leave?" signal ---
    override fun onActivityStarted(activity: Activity) {
        if (!ENABLE_BACKGROUND_LEAVE_GUARD) return
        startedCount++
        handler.removeCallbacks(leaveCheck)   // an activity is visible again → we're back; cancel any pending leave-enforce
    }

    override fun onActivityStopped(activity: Activity) {
        if (!ENABLE_BACKGROUND_LEAVE_GUARD) return
        startedCount = (startedCount - 1).coerceAtLeast(0)
        // count == 0 → NONE of our activities is on screen anymore = the whole app is in the
        // background. The user pressed Home or switched away. Confirm after a short delay (a real
        // leave vs. a transient Activity swap), then enforce. No foreground read involved.
        if (startedCount == 0) {
            handler.removeCallbacks(leaveCheck)
            handler.postDelayed(leaveCheck, LEAVE_CHECK_DELAY_MS)
        }
    }

    /** Fired [LEAVE_CHECK_DELAY_MS] after the app went fully background — pull our screen back. */
    private val leaveCheck = Runnable {
        val activity = current?.get() ?: return@Runnable            // nothing of ours to guard
        if (adShowing) return@Runnable                               // our own App Open ad is up — not a leave
        if (isOwnNavigation()) return@Runnable                       // our own transition in progress — not a leave
        if (systemPickerOpen) return@Runnable                        // system role picker is up — not a leave
        if (chromeSessionOpen) return@Runnable                       // our own Custom Tab is up — not a leave
        if (activity.isFinishing || activity.isDestroyed) return@Runnable
        if (!userIsPresent()) return@Runnable                        // screen off / locked
        if (inCall()) return@Runnable                                // phone call
        log("🚪 app backgrounded (startedCount=0) → leaveCheck → enforce (no foreground read needed)")
        startEnforcing()
    }

    // ========================================================================
    //  LAYER 2 — ARM (start the loop)
    // ========================================================================
    private fun startEnforcing() {
        if (adShowing) { log("   ↳ not enforcing: our App Open ad is on screen"); return }  // never fight our own ad
        if (systemPickerOpen) { log("   ↳ not enforcing: system default/role picker is open"); return }  // let the user grant it
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

            // STOP CONDITION 2b — our own Custom Tab opened mid-burst: don't fight our web flow.
            if (chromeSessionOpen) {
                log("   ↳ stand down: Custom Tab session opened — leaving our web flow alone")
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
            // The user is AWAY from our screen. Run the OEM fallback chain and stop
            // as soon as ONE pull-back succeeds, so we don't re-run the whole chain
            // every 80ms for the rest of the burst.
            if (depictFallbackChain(activity)) {
                log("   ↳ brought to front — stop (won't re-run every ${RETRY_MS}ms)")
                showBlockedToast(activity)   // tell the (confused) user why they're back
                stop()
                return
            }

            // Not brought to front yet → check again in 80ms (this is the "timer").
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
        // SAFETY: never leave the notification shade disabled after a burst.
        if (shadeLocked) {
            log("   🔓 re-enabling shade (was locked during this burst)")
            setShadeLocked(appContext, locked = false)
        }
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
    private fun foregroundPackage(): String? =
        usageForegroundPackage() ?: reflectedTopPackage()   // Usage Access first, hidden-API fallback (FunnelGuard trick)

    /** Method A — UsageStats read. Needs "Usage access"; returns null when it isn't granted. */
    private fun usageForegroundPackage(): String? = try {
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

    /**
     * Method B — FunnelGuard.reflectedTopPackage(): read the CURRENT foreground package from the
     * HIDDEN android.app.ActivityTaskManager.getService().getTasks(1), via reflection. This works
     * WITHOUT "Usage access" — it's how the reference app detects the foreground on a plain install.
     * Needs the hidden-API exemption first; returns null if the OS blocks the call (newer Android
     * increasingly returns only our own tasks for non-privileged callers).
     */
    @Volatile private var reflectionBlocked = false   // set once the OS rejects the hidden getTasks, so we stop retrying/spamming
    private var webGuardUnknownLogged = false
    private fun reflectedTopPackage(): String? {
        if (!ENABLE_REFLECTED_FOREGROUND || reflectionBlocked) return null
        exemptHiddenApis()
        return try {
            val atm = Class.forName("android.app.ActivityTaskManager")
                .getMethod("getService").invoke(null) ?: return null
            val getTasksMethods = atm.javaClass.methods.filter { it.name == "getTasks" }
            val tasks = invokeGetTasks(atm, getTasksMethods, 1) ?: return null
            val top = tasks.firstOrNull() ?: return null
            taskTopPackage(top)
        } catch (t: Throwable) {
            reflectionBlocked = true   // Android 12+ blocks this — give up so we don't flood the log every tick
            log("   ❌ [reflectedTopPackage] blocked on this OS (${t.javaClass.simpleName}) — giving up reflection; grant Usage Access to detect the foreground")
            null
        }
    }

    /** Call the hidden getTasks(max) across its version-varying signatures (1..4 args); first non-empty wins. */
    private fun invokeGetTasks(service: Any, methods: List<java.lang.reflect.Method>, max: Int): List<*>? {
        for (m in methods) {
            m.isAccessible = true
            val result = try {
                when (m.parameterTypes.size) {
                    1 -> m.invoke(service, max)
                    2 -> m.invoke(service, max, false)
                    3 -> m.invoke(service, max, false, false)
                    4 -> m.invoke(service, max, false, false, 0)
                    else -> null
                }
            } catch (t: Throwable) { null }
            if (result is List<*> && result.isNotEmpty()) return result
        }
        return null
    }

    /** Read a RunningTaskInfo's top package from its `topActivity` ComponentName field (reflection). */
    private fun taskTopPackage(taskInfo: Any): String? = try {
        val field = runCatching { taskInfo.javaClass.getField("topActivity") }
            .getOrElse { taskInfo.javaClass.getDeclaredField("topActivity").apply { isAccessible = true } }
        (field.get(taskInfo) as? android.content.ComponentName)?.packageName
    } catch (t: Throwable) { null }

    /** True when a browser (which would be hosting a Custom Tab) is the foreground app. */
    private fun isBrowserForeground(): Boolean = foregroundPackage() in BROWSER_PACKAGES

    // ========================================================================
    //  FALLBACK CHAIN — DEPICTION ONLY (LOG ONLY, NOTHING EXECUTES)
    //  Mirrors how the reference app forced itself back across different OEM
    //  phones. Every "pull-back" method below ONLY LOGS what it WOULD do and
    //  returns false — no task is moved, no shade is touched. It exists so you
    //  can SEE the chain's shape, not run it.
    // ========================================================================

    /** OEM Settings package variants (+ substring match) — read-only classification. */
    private val SETTINGS_PACKAGES = setOf(
        "com.android.settings", "com.miui.securitycenter",
        "com.samsung.android.settings", "com.coloros.safecenter",
    )
    private fun isSettingsPackage(pkg: String?) =
        pkg != null && (pkg in SETTINGS_PACKAGES || pkg.contains("settings"))
    private fun isShadePackage(pkg: String?) =
        pkg != null && pkg.contains("systemui")   // com.android.systemui, com.miui.systemui, …

    /** Runs the whole depiction: chain 1 → 2 → 3 → 4, logging each step. */
    private fun depictFallbackChain(activity: Activity): Boolean {
        val taskId = activity.taskId
        log("🚫 user is away from taskId=$taskId — running OEM fallback chain:")
        val fg = depictChain1_detect()
        val broughtToFront = depictChain2_bringToFront(activity)
        depictChain3_closeEscape(activity, fg)
        depictChain4_versionNote()
        return broughtToFront
    }

    /** CHAIN 1 — detect what's in front (read-only; first method that answers wins). */
    private fun depictChain1_detect(): String? {
        val fg = foregroundPackage()   // method A: UsageStats (real, read-only)
        // Ref app's detection: FunnelGuard.foregroundPackage(), falling back to
        // reflectedTopPackage() / invokeGetTasks() when UsageStats returns null.
        log("   [chain 1] started the foregroundPackage() [UsageStats] → " +
            (fg?.let { "$it (${classify(it)})" } ?: "null → fallback reflectedTopPackage() / invokeGetTasks()"))
        return fg
    }

    /** CHAIN 2 -- bring-to-front: 3 real fallbacks with full logging. */
    private fun depictChain2_bringToFront(activity: Activity): Boolean {
        val id = activity.taskId
        log("============================================================")
        log("⚡ [CHAIN 2] bring-to-front start for taskId=$id (${activity.javaClass.simpleName})")
        markSelfReorder()   // so our own resulting onPause isn't mistaken for the user leaving

        // Try each fallback in order; stop at the first that succeeds (skip the rest).
        if (reflectedSilentMoveToFront(activity, id)) {
            log("   ↳ [2.0] reflected silent moveToFront succeeded → skipping 2.1–2.3")
            log("============================================================")
            return true
        }

        // moveTaskToFront alone reorders the task stack but does NOT dismiss an already-open Recents
        // overview on this OEM. So, like the reference's bringActivityTaskToFront, we run BOTH the
        // moveTask AND the REORDER_TO_FRONT startActivity every time — the reorder is what collapses
        // overview. (2.2 appTask.moveToFront stays as a fallback only when moveTaskToFront throws.)
        val pb1 = pullBack1_moveTaskToFront(id, activity)
        log("   ↳ [2.1] pullBack1 (moveTaskToFront) result: $pb1")
        if (!pb1) {
            val pb2 = pullBack2_appTaskMoveToFront(activity)
            log("   ↳ [2.2] pullBack2 (appTask.moveToFront) result: $pb2")
        }

        val pb3 = pullBack3_reorderToFront(activity)   // ALWAYS — this is what closes the overview
        log("   ↳ [2.3] pullBack3 (reorderToFront) result: $pb3")
        log("============================================================")
        return pb1 || pb3
    }

    private fun pullBack1_moveTaskToFront(taskId: Int, activity: Activity): Boolean {
        log("   [2.1] Calling bringOwnTaskToFront(taskId=$taskId)...")
        return try {
            bringOwnTaskToFront(activity, taskId)
            log("   ✅ [2.1] bringOwnTaskToFront completed successfully")
            true
        } catch (t: Throwable) {
            log("   ❌ [2.1] bringOwnTaskToFront FAILED: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    fun moveAppTask(activity: Activity): Boolean {
        log("   [2.2] moveAppTask started for taskId=${activity.taskId}...")
        return try {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val targetTask = am.appTasks.firstOrNull { it.taskInfo.taskId == activity.taskId }
            if (targetTask != null) {
                log("   ↳ Found appTask -> calling moveToFront()")
                targetTask.moveToFront()
                log("   ✅ [2.2] moveAppTask: moveToFront() executed")
                true
            } else {
                log("   ⚠️ [2.2] moveAppTask: No matching appTask found (total=${am.appTasks.size})")
                false
            }
        } catch (t: Throwable) {
            log("   ❌ [2.2] moveAppTask FAILED: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    fun recoverTask(activity: Activity): Boolean {
        if (!ENABLE_REORDER_TO_FRONT) return false
        log("   [2.3] recoverTask started for ${activity.javaClass.simpleName}...")
        return try {
            // Same task (NO NEW_TASK), SINGLE_TOP so the existing instance is reused via onNewIntent
            // (no recreate), NO_ANIMATION so the snap-back is invisible — exactly the reference's
            // bringActivityTaskToFront flags (0x20030000). THIS is what collapses the overview.
            val intent = Intent(activity, activity.javaClass).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
            activity.startActivity(intent)
            @Suppress("DEPRECATION") activity.overridePendingTransition(0, 0)
            log("   ✅ [2.3] recoverTask: startActivity(REORDER_TO_FRONT) executed")
            true
        } catch (t: Throwable) {
            log("   ❌ [2.3] recoverTask FAILED: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    private fun pullBack2_appTaskMoveToFront(activity: Activity): Boolean = moveAppTask(activity)
    private fun pullBack3_reorderToFront(activity: Activity): Boolean = recoverTask(activity)

    /** CHAIN 3 — close the escape route based on WHERE the user went. DEPICTED. */
    private fun depictChain3_closeEscape(activity: Activity, fg: String?) {
        // Ref app's close-escape methods per destination. The shade branch now REALLY
        // runs collapseShade()/setShadeLocked() when their toggles are on (else logs only).
        when {
            fg == null             -> log("   [chain 3] foreground unknown (no Usage access) → retry")
            fg in BROWSER_PACKAGES -> log("   [chain 3] browser/Custom Tab → stand down (our own web page)")
            isSettingsPackage(fg)  -> log("   [chain 3] Settings → pull back + showBlockedToast() [toast omitted]")
            isShadePackage(fg)     -> {
                log("   [chain 3] shade → collapseShade() + setShadeLocked() [hidden API]")
                collapseShade(activity, closeDialogs = true)
                setShadeLocked(activity, locked = true)
            }
            else                   -> log("   [chain 3] other app ($fg) → coverOnce(host, taskId)")
        }
    }

    /** CHAIN 4 — version-aware note: which UsageStats event the detection relied on. */
    private fun depictChain4_versionNote() {
        val ev = if (Build.VERSION.SDK_INT >= 29) "ACTIVITY_RESUMED (API 29+)" else "MOVE_TO_FOREGROUND (legacy)"
        log("   [chain 4] foreground read used $ev; hidden-API calls would be wrapped in try/catch per OEM")
    }

    /** Read-only label for a foreground package. */
    private fun classify(pkg: String): String = when {
        pkg == appContext.packageName -> "our app"
        pkg in BROWSER_PACKAGES       -> "browser"
        isSettingsPackage(pkg)        -> "settings"
        isShadePackage(pkg)           -> "system shade"
        else                          -> "other app"
    }

    // ========================================================================
    //  REAL TECHNIQUES (ported from FunnelGuard) — each gated by a toggle above.
    // ========================================================================

    /**
     * FunnelGuard.exemptHiddenApis(): lift the hidden-API blocklist via VMRuntime.setHiddenApiExemptions("L").
     *
     * The catch-22 (what broke the first attempt): setHiddenApiExemptions is ITSELF a hidden method, so a
     * direct vmRuntime.getDeclaredMethod("setHiddenApiExemptions", …) is blocked → NoSuchMethodException,
     * and then ActivityTaskManager.getService() stays blocked too.
     *
     * FunnelGuard's bypass = "double / meta reflection" (aka FreeReflection): get a Method handle to
     * Class.getDeclaredMethod ITSELF, then do the hidden lookups THROUGH that handle. The reflective lookup
     * is attributed to the framework, not our app, so the blocklist doesn't hide the result.
     */
    private fun exemptHiddenApis() {
        if (!ENABLE_HIDDEN_API_EXEMPT || hiddenApiExempt) return
        hiddenApiExempt = true
        try {
            // Meta-handle to Class.getDeclaredMethod(String, Class[]) — the key to the bypass.
            val metaGetDeclaredMethod = Class::class.java.getDeclaredMethod(
                "getDeclaredMethod", String::class.java, arrayOf<Class<*>>()::class.java
            )
            val vmRuntimeClass = Class::class.java
                .getMethod("forName", String::class.java)
                .invoke(null, "dalvik.system.VMRuntime") as Class<*>
            // VMRuntime.getRuntime(), looked up THROUGH the meta-handle so it isn't blocked.
            val getRuntime = metaGetDeclaredMethod
                .invoke(vmRuntimeClass, "getRuntime", null) as java.lang.reflect.Method
            val sVmRuntime = getRuntime.invoke(null)
            // VMRuntime.setHiddenApiExemptions(String[]), also via the meta-handle.
            val setExemptions = metaGetDeclaredMethod.invoke(
                vmRuntimeClass, "setHiddenApiExemptions", arrayOf<Class<*>>(Array<String>::class.java)
            ) as java.lang.reflect.Method
            setExemptions.invoke(sVmRuntime, arrayOf("L") as Any)
            log("   🔓 [hiddenApi] setHiddenApiExemptions(\"L\") OK — greylisted APIs now callable")
        } catch (t: Throwable) {
            log("   ❌ [hiddenApi] FAILED: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /**
     * FunnelGuard.showBlockedToast(): a short "complete the steps" hint so a user who was
     * yanked back isn't confused. Throttled to once per burst window so it can't spam while
     * the 80ms loop runs.
     */
    private fun showBlockedToast(activity: Activity) {
        if (!ENABLE_BLOCKED_TOAST) return
        val now = SystemClock.uptimeMillis()
        if (now - lastBlockedToastAt < WINDOW_MS) return
        lastBlockedToastAt = now
        try {
            Toast.makeText(activity, BLOCKED_TOAST_TEXT, Toast.LENGTH_SHORT).show()
            log("   💬 [toast] shown: \"$BLOCKED_TOAST_TEXT\"")
        } catch (t: Throwable) {
            log("   ❌ [toast] FAILED: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** FunnelGuard.silentTaskOptions(): no-animation bundle + extra OEM anim keys. */
    private fun silentTaskOptions(activity: Activity) =
        ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle().apply {
            putInt("android:animType", 0)
            putInt("android:activity.animType", 0)
        }

    /** FunnelGuard's reflected HIDDEN 3-arg moveTaskToFront(taskId, 2, bundle). */
    private fun reflectedSilentMoveToFront(activity: Activity, taskId: Int): Boolean {
        if (!ENABLE_REFLECTED_SILENT_MOVE) return false
        return try {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val m = ActivityManager::class.java.getDeclaredMethod(
                "moveTaskToFront", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Bundle::class.java
            ).apply { isAccessible = true }
            log("   🧩 [reflectedMove] moveTaskToFront($taskId, 2, silentBundle) [reflection, flag 2]...")
            m.invoke(am, taskId, 2, silentTaskOptions(activity))
            log("   ✅ [reflectedMove] done")
            true
        } catch (t: Throwable) {
            log("   ❌ [reflectedMove] FAILED: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    /** FunnelGuard.collapseShade(): force-close the notification shade (reflection) + CLOSE_SYSTEM_DIALOGS. */
    private fun collapseShade(context: Context, closeDialogs: Boolean) {
        if (!ENABLE_COLLAPSE_SHADE) return
        exemptHiddenApis()
        val sb = context.getSystemService("statusbar")
        for (name in listOf("collapsePanels", "collapse")) {
            try {
                val m = sb?.javaClass?.getMethod(name) ?: continue
                m.isAccessible = true
                m.invoke(sb)
                log("   🪟 [collapseShade] $name() OK")
            } catch (t: Throwable) {
                log("   ❌ [collapseShade] $name() FAILED: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        if (closeDialogs) {
            try {
                @Suppress("DEPRECATION")
                context.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
                log("   🪟 [collapseShade] CLOSE_SYSTEM_DIALOGS sent")
            } catch (t: Throwable) {
                log("   ❌ [collapseShade] CLOSE_SYSTEM_DIALOGS FAILED: ${t.javaClass.simpleName}")
            }
        }
    }

    /**
     * FunnelGuard.setShadeLocked(): IStatusBarService.disable()/disable2() to block the pull-down.
     * SAFETY: stop() always calls this with locked=false, so the shade is re-enabled when the burst ends.
     */
    private fun setShadeLocked(context: Context, locked: Boolean) {
        if ((!ENABLE_LOCK_SHADE && locked) || shadeLocked == locked) return
        exemptHiddenApis()
        val sb = context.getSystemService("statusbar") ?: run {
            log("   ❌ [shadeLock] no statusbar service"); return
        }
        val flag = if (locked) 0x00010000 else 0   // STATUS_BAR_DISABLE_EXPAND
        var ok = false
        try {
            sb.javaClass.getMethod("disable", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(sb, flag)
            ok = true; log("   🔒 [shadeLock] disable($flag) OK")
        } catch (t: Throwable) { log("   ❌ [shadeLock] disable() FAILED: ${t.javaClass.simpleName}: ${t.message}") }
        try {
            sb.javaClass.getMethod("disable2", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(sb, if (locked) 1 else 0)
            ok = true; log("   🔒 [shadeLock] disable2(${if (locked) 1 else 0}) OK")
        } catch (t: Throwable) { log("   ❌ [shadeLock] disable2() FAILED: ${t.javaClass.simpleName}: ${t.message}") }
        if (ok) shadeLocked = locked
    }

    // ========================================================================
    //  CHROME CUSTOM TAB SESSION — FunnelGuard's "our own web flow" handling.
    //  The tab launcher calls onChromeSessionOpen()/Closed(). While open, the
    //  host-pull-back stands down; instead (if ENABLE_RECOVER_CHROME) a lighter
    //  web-guard watches the tab and re-opens it if the user wanders off.
    // ========================================================================

    // ========================================================================
    //  SHARED FUNNEL TRAP — the reference's base FunnelScreenActivity applies this
    //  to EVERY funnel screen (Home + Recents), not just set-default. Called from
    //  PaidFunnelActivity.onUserLeaveHint / dispatchKeyEvent. Gated at the call site
    //  on non-organic (paid) installs still in onboarding.
    // ========================================================================

    // Own-navigation window: while this is in the future, a Home/Recents or a background we SEE is
    // OUR doing (we just launched the next screen, opened a web step, or routed) — not the user
    // escaping. Mirrors the reference's ignoreOwnActionUntil / noteInAppAdvance. Call noteOwnNavigation
    // right before any of our own screen transitions.
    @Volatile private var ownNavUntil = 0L
    fun noteOwnNavigation(windowMs: Long = 1500L) {
        ownNavUntil = SystemClock.uptimeMillis() + windowMs
    }
    private fun isOwnNavigation(): Boolean = SystemClock.uptimeMillis() < ownNavUntil

    /** True while one of OUR OWN flows owns the screen — never treat these as the user escaping. */
    private fun ownFlowActive(): Boolean = adShowing || systemPickerOpen || chromeSessionOpen || isOwnNavigation()

    /**
     * HOME on a funnel screen → mirrors the reference's onIntroInterrupted: pull the SAME task back
     * with moveTaskToFront (BAL-safe) and hold. We deliberately do NOT startActivity-route to Language
     * here: when the system is taking the user to overview/home, a startActivity to the foreground is
     * Background-Activity-Launch-blocked, so it silently fails and the user escapes — which is exactly
     * why Recents wasn't being blocked. moveTaskToFront on OUR OWN task is BAL-exempt, so it works.
     */
    fun onFunnelHome(activity: Activity) {
        if (!ENABLE_FUNNEL_TRAP || !registered || ownFlowActive()) return
        log("🏠 funnel HOME on ${activity.javaClass.simpleName} → pull back + hold (moveTaskToFront)")
        collapseShade(activity, closeDialogs = true)
        setShadeLocked(activity, true)
        showBlockedToast(activity)
        pullBackSame(activity)
    }

    /**
     * RECENTS on a funnel screen → the reference's onRecentsPressed: pull the SAME screen back and
     * hold it (re-yanks during the burst if the user keeps trying). Shade collapsed + locked + toast.
     */
    fun onFunnelRecents(activity: Activity) {
        if (!ENABLE_FUNNEL_TRAP || !registered || ownFlowActive()) return
        log("▭ funnel RECENTS on ${activity.javaClass.simpleName} → pull back + hold")
        collapseShade(activity, closeDialogs = true)
        setShadeLocked(activity, true)
        showBlockedToast(activity)
        pullBackSame(activity)
    }

    /**
     * Pull OUR current task back to the front and hold it for the burst window. Mirrors the
     * reference's bringActivityTaskToFront: moveTaskToFront (reorder the stack) AND recoverTask
     * (REORDER_TO_FRONT startActivity) — the latter is what actually collapses the Recents overview.
     */
    private fun pullBackSame(activity: Activity) {
        markSelfReorder()   // the resulting onPause/onResume is ours, not the user leaving
        app.messages.onboarding.watch.OnboardingAppWatcher.bringOwnTaskToFront(activity, activity.taskId)
        recoverTask(activity)   // collapse the overview (no-op if ENABLE_REORDER_TO_FRONT is off)
        startEnforcing()
    }

    /** Call when WE open the Custom Tab — the guard stands down (this isn't the user leaving). */
    fun onChromeSessionOpen() {
        if (!registered) return
        chromeSessionOpen = true
        log("🌐 chrome session OPEN — host enforcement stands down for the Custom Tab")
        stop()           // cancel any host pull-back burst in progress
        startWebGuard()  // watch the tab itself instead
    }

    /**
     * Call around OUR OWN full-screen App Open ad (PaidOnboardingAppOpen). While true the guard
     * fully stands down — no leave-detection, no enforce loop, no moveTaskToFront/toast — so the
     * Engine can never close, dismiss, or steal focus from the ad. Set true BEFORE show, cleared on
     * dismiss/fail.
     */
    fun setAdShowing(on: Boolean) {
        if (!registered) return
        adShowing = on
        if (on) {
            log("📺 App Open ad OPEN — guard stands down (won't touch the ad)")
            stop()   // cancel any burst in progress so nothing races the ad
        } else {
            log("📺 App Open ad CLOSED — normal enforcement resumes")
        }
    }

    /**
     * Call around the SYSTEM default/role picker (ROLE_HOME / ROLE_SMS request, or the legacy
     * Settings home-picker). While true the guard stands down, so it never yanks our task back over
     * the system dialog the user is supposed to interact with. Mirrors the reference's
     * `settingsPickerOpen`. Set true BEFORE launching the picker, cleared when it returns.
     */
    fun setSystemPickerOpen(on: Boolean) {
        if (!registered) return
        systemPickerOpen = on
        if (on) {
            log("⚙️ system default/role picker OPEN — guard stands down")
            stop()   // cancel any burst so we don't fight the system dialog
        } else {
            log("⚙️ system default/role picker CLOSED — normal enforcement resumes")
        }
    }

    /** Call when the Custom Tab closes — normal enforcement resumes. */
    fun onChromeSessionClosed() {
        if (!registered) return
        chromeSessionOpen = false
        handler.removeCallbacks(tabHiddenCheck)
        log("🌐 chrome session CLOSED — normal enforcement resumes")
        stopWebGuard()
    }

    /**
     * FunnelGuard.onCustomTabHidden(): Chrome itself fired TAB_HIDDEN through the warmed session.
     * This is the PERMISSION-FREE way to know the user left the tab — no Usage Access, no foreground
     * read, no polling. But TAB_HIDDEN also fires on a legit close (X → our result callback advances
     * the flow and sets chromeSessionOpen=false). So we wait briefly, then recover only if the session
     * is still open and the user hasn't returned — i.e. they actually escaped.
     */
    fun onCustomTabHidden() {
        if (!registered || !ENABLE_RECOVER_CHROME) return
        if (!chromeSessionOpen) return
        log("🌐 [tabHidden] Chrome reported TAB_HIDDEN — confirming escape vs. legit close…")
        handler.removeCallbacks(tabHiddenCheck)
        handler.postDelayed(tabHiddenCheck, TAB_HIDDEN_DELAY_MS)
    }

    private val tabHiddenCheck = Runnable {
        if (!chromeSessionOpen) { log("   ↳ [tabHidden] session already closed (legit close) — no recover"); return@Runnable }
        val host = current?.get()
        if (host != null && host.hasWindowFocus()) { log("   ↳ [tabHidden] user already back in our app — no recover"); return@Runnable }
        log("   ↳ [tabHidden] user escaped the tab → recoverChrome() (no foreground read / no Usage Access)")
        recoverChrome("tab_hidden")
    }

    /** Mark that WE just reordered a task, so our own resulting onPause is ignored (FunnelGuard trick). */
    fun markSelfReorder() { selfReorderAt = SystemClock.uptimeMillis() }
    private fun isSelfReorderPause(): Boolean = SystemClock.uptimeMillis() - selfReorderAt < 250L

    // While the tab is open, poll who's in front; if the user left the tab, recover it.
    private val webGuard = object : Runnable {
        override fun run() {
            if (!chromeSessionOpen) { stopWebGuard(); return }
            when (val fg = foregroundPackage()) {
                null                   -> if (!webGuardUnknownLogged) {
                    webGuardUnknownLogged = true
                    log("🌐 [webGuard] foreground unknown (no Usage access + reflection blocked on this OS) — can't detect tab exit; grant Usage Access to enable recovery")
                }
                in BROWSER_PACKAGES    -> log("🌐 [webGuard] tab still in front ($fg) — ok")
                appContext.packageName -> log("🌐 [webGuard] our app in front — ok")
                else                   -> {
                    log("🌐 [webGuard] user left the tab → foreground=$fg → recoverChrome()")
                    recoverChrome(fg)
                }
            }
            handler.postDelayed(this, WEB_GUARD_MS)
        }
    }

    private fun startWebGuard() {
        if (!ENABLE_RECOVER_CHROME || webGuardRunning) return
        webGuardRunning = true
        webGuardUnknownLogged = false
        handler.removeCallbacks(webGuard)
        handler.post(webGuard)
        log("🌐 [webGuard] started (watching the Custom Tab)")
    }

    private fun stopWebGuard() {
        if (!webGuardRunning) return
        webGuardRunning = false
        handler.removeCallbacks(webGuard)
        log("🌐 [webGuard] stopped")
    }

    /**
     * FunnelGuard.recoverChromeOrHost(): bring our Custom Tab back when the user wandered off it.
     *
     * THE FIX (how the reference app actually does it): the Custom Tab lives inside OUR OWN task
     * (it was launched with startActivityForResult from our onboarding activity, so it shares that
     * task). So we do NOT startActivity — on Android 14 a background startActivity is blocked by the
     * Background-Activity-Launch (BAL) rules, which is why the old version failed "sometimes". We
     * instead REORDER the existing task to the front (moveToFront / moveTaskToFront). Reordering an
     * existing task is NOT a BAL-restricted launch (we hold REORDER_TASKS and it's our own task), so
     * it works reliably from the background — the same mechanism our onboarding pull-back already uses.
     */
    private fun recoverChrome(foreground: String) {
        val host = current?.get()
        if (host == null) {
            log("   ⚠️ [recoverChrome] no host activity reference — can't move our task")
            return
        }
        markSelfReorder()   // the resulting onPause/onResume is ours, not the user leaving
        // Layer 1 — AppTask.moveToFront(): reorders OUR task (with the tab on top). Permission-free,
        // BAL-safe, no reflection. This is the layer that fixes the intermittent failure.
        if (moveAppTask(host)) {
            log("   ✅ [recoverChrome] moved our task (taskId=${host.taskId}) to front via AppTask.moveToFront — tab is back")
            return
        }
        // Layer 2 — ActivityManager.moveTaskToFront(taskId, …) on our own task: also a reorder, not a launch.
        try {
            app.messages.onboarding.watch.OnboardingAppWatcher.bringOwnTaskToFront(host, host.taskId)
            log("   ✅ [recoverChrome] moved our task (taskId=${host.taskId}) to front via moveTaskToFront — tab is back")
        } catch (t: Throwable) {
            log("   ❌ [recoverChrome] both reorder layers failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    // ========================================================================
    //  SCOPE — only the ONBOARDING screens, nothing else.
    //  Registered app-wide, but we only act on these Activities. The paid
    //  onboarding pages are Fragments inside PaidOnboardingActivity, so matching
    //  that one Activity covers the whole flow.
    // ========================================================================
    private const val LANGUAGE_SCREEN = "app.messages.ui.activity.LanguageSelectionActivity"
    private const val SET_DEFAULT_SCREEN = "app.messages.ui.activity.SetDefaultLauncherActivity"

    private val ONBOARDING_SCREENS = setOf(
        SET_DEFAULT_SCREEN,                                    // Set-as-default-launcher screen (gated below)
        LANGUAGE_SCREEN,                                        // Flow A language screen (gated below)
        "app.messages.ui.paidonboarding.PaidOnboardingActivity",
        "app.messages.ui.paidonboarding.PaidChromeTabLauncherActivity",
        "app.messages.ui.paidonboarding.PaidWebLinkActivity",  // WebView fallback — our own activity, full trap applies
        "app.messages.ui.activity.OnboardingActivity",
    )

    private fun isOnboardingScreen(activity: Activity): Boolean {
        val name = activity.javaClass.name
        if (name !in ONBOARDING_SCREENS) return false
        // The language and set-default screens are also reachable outside Flow A (a later language
        // change, or an organic install setting default), so only guard them while Flow A is actually
        // in progress — a paid install that hasn't finished onboarding. The other screens here are
        // inherently Flow-A/onboarding only.
        if ((name == LANGUAGE_SCREEN || name == SET_DEFAULT_SCREEN) &&
            !app.messages.ui.paidonboarding.PaidOnboardingPrefs.shouldShow(activity)) {
            return false
        }
        return true
    }

    // ========================================================================
    //  LOG
    // ========================================================================
    private fun log(msg: String) = Log.d(TAG, msg)
}
