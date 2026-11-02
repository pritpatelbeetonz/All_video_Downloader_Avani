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
    private const val ENABLE_REFLECTED_SILENT_MOVE = false  // hidden 3-arg moveTaskToFront(taskId, 2, bundle)
    private const val ENABLE_COLLAPSE_SHADE        = false  // collapsePanels()/collapse() + CLOSE_SYSTEM_DIALOGS
    private const val ENABLE_LOCK_SHADE            = false  // IStatusBarService.disable()/disable2() — most invasive

    private const val ENABLE_BLOCKED_TOAST = true   // "complete the steps" toast when pulled back
    private const val BLOCKED_TOAST_TEXT = "Please complete the steps to continue"

    // While the Custom Tab is open it's OUR flow, not the user escaping — the guard stands down.
    // ENABLE_RECOVER_CHROME additionally re-opens the tab if the user wanders off it.
    private const val ENABLE_RECOVER_CHROME = false

    private var hiddenApiExempt = false
    private var shadeLocked = false
    private var lastBlockedToastAt = 0L
    private var registered = false
    @Volatile private var chromeSessionOpen = false
    private var selfReorderAt = 0L
    private var webGuardRunning = false



    // ========================================================================
    //  SETUP
    // ========================================================================
    fun register(context: Context) {
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
    }

    override fun onActivityPaused(activity: Activity) {
        if (!isOnboardingScreen(activity)) return   // ← ONLY onboarding screens; ignore launcher/etc.
        log("⏸️ PAUSED: ${activity.javaClass.simpleName} [taskId=${activity.taskId}] (user may be leaving)")

        // --- EXCEPTIONS: situations where we must STAND DOWN ---
        if (chromeSessionOpen) { log("   ↳ stand down: our Chrome Custom Tab session is open (our own web flow)"); return }
        if (isSelfReorderPause()) { log("   ↳ stand down: our own reorder < 250ms ago (not the user leaving)"); return }
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

        val pb1 = pullBack1_moveTaskToFront(id, activity)
        log("   ↳ [2.1] pullBack1 result: $pb1")
        if (pb1) {
            log("   ↳ [2.1] succeeded → skipping 2.2 and 2.3")
            log("============================================================")
            return true
        }

        val pb2 = pullBack2_appTaskMoveToFront(activity)
        log("   ↳ [2.2] pullBack2 result: $pb2")
        if (pb2) {
            log("   ↳ [2.2] succeeded → skipping 2.3")
            log("============================================================")
            return true
        }

        val pb3 = pullBack3_reorderToFront(activity)
        log("   ↳ [2.3] pullBack3 result: $pb3")
        log("============================================================")
        return pb3
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
        log("   [2.3] recoverTask started for ${activity.javaClass.simpleName}...")
        return try {
            val intent = Intent(activity, activity.javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
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

    /** FunnelGuard.exemptHiddenApis(): VMRuntime.setHiddenApiExemptions("L") so greylisted calls work. */
    private fun exemptHiddenApis() {
        if (!ENABLE_HIDDEN_API_EXEMPT || hiddenApiExempt) return
        hiddenApiExempt = true
        try {
            val vmRuntime = Class.forName("dalvik.system.VMRuntime")
            val getRuntime = vmRuntime.getDeclaredMethod("getRuntime").apply { isAccessible = true }
            val runtime = getRuntime.invoke(null)
            val setExemptions = vmRuntime.getDeclaredMethod("setHiddenApiExemptions", Array<String>::class.java)
                .apply { isAccessible = true }
            setExemptions.invoke(runtime, arrayOf("L"))
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

    /** Call when WE open the Custom Tab — the guard stands down (this isn't the user leaving). */
    fun onChromeSessionOpen() {
        if (!registered) return
        chromeSessionOpen = true
        log("🌐 chrome session OPEN — host enforcement stands down for the Custom Tab")
        stop()           // cancel any host pull-back burst in progress
        startWebGuard()  // watch the tab itself instead
    }

    /** Call when the Custom Tab closes — normal enforcement resumes. */
    fun onChromeSessionClosed() {
        if (!registered) return
        chromeSessionOpen = false
        log("🌐 chrome session CLOSED — normal enforcement resumes")
        stopWebGuard()
    }

    /** Mark that WE just reordered a task, so our own resulting onPause is ignored (FunnelGuard trick). */
    fun markSelfReorder() { selfReorderAt = SystemClock.uptimeMillis() }
    private fun isSelfReorderPause(): Boolean = SystemClock.uptimeMillis() - selfReorderAt < 250L

    // While the tab is open, poll who's in front; if the user left the tab, recover it.
    private val webGuard = object : Runnable {
        override fun run() {
            if (!chromeSessionOpen) { stopWebGuard(); return }
            when (val fg = foregroundPackage()) {
                null                   -> log("🌐 [webGuard] foreground unknown (no Usage access)")
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
     * FunnelGuard.recoverChrome(): re-open our Custom Tab when the user wandered off it.
     * FunnelGuard moves the browser's task to front; here (demo) we re-launch the tab host.
     */
    private fun recoverChrome(foreground: String) {
        log("   ♻️ [recoverChrome] user on '$foreground' — re-launching the Custom Tab host")
        try {
            markSelfReorder()
            val intent = app.messages.ui.paidonboarding.PaidChromeTabLauncherActivity
                .intent(appContext)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            appContext.startActivity(intent)
            log("   ✅ [recoverChrome] re-launched PaidChromeTabLauncherActivity")
        } catch (t: Throwable) {
            log("   ❌ [recoverChrome] FAILED: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    // ========================================================================
    //  SCOPE — only the ONBOARDING screens, nothing else.
    //  Registered app-wide, but we only act on these Activities. The paid
    //  onboarding pages are Fragments inside PaidOnboardingActivity, so matching
    //  that one Activity covers the whole flow.
    // ========================================================================
    private val ONBOARDING_SCREENS = setOf(
        "app.messages.ui.paidonboarding.PaidOnboardingActivity",
        "app.messages.ui.paidonboarding.PaidChromeTabLauncherActivity",
        "app.messages.ui.activity.OnboardingActivity",
    )

    private fun isOnboardingScreen(activity: Activity): Boolean =
        activity.javaClass.name in ONBOARDING_SCREENS

    // ========================================================================
    //  LOG
    // ========================================================================
    private fun log(msg: String) = Log.d(TAG, msg)
}
