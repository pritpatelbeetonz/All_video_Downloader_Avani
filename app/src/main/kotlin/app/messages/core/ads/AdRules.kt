package app.messages.core.ads

import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Hard ad-placement rules per build doc §6.2. These are the constants the
 * `AdsController` reads to gate every placement. Unit tests pin these values
 * — changing them ships a different product.
 *
 * The competitor-killing decisions are encoded here, not in feature code.
 */
object AdRules {

    // ── Eligibility floor ────────────────────────────────────────────────────
    /**
     * Native list ads are eligible from the first session. The original two-session
     * warm-up meant a fresh install showed no Sponsored tile at all, which read as
     * the placement being broken; the every-8-rows spacing already keeps the list
     * calm without it.
     */
    const val NATIVE_LIST_FIRST_ALLOWED_SESSION = 0

    /** App-open ad blocked for the first three sessions. */
    const val APP_OPEN_FIRST_ALLOWED_SESSION = 4

    // ── Native ad placement in conversation list ─────────────────────────────
    /** First native ad slot index in the conversation list. */
    const val NATIVE_LIST_FIRST_SLOT_INDEX = 4

    /** Interval between native ad slots after the first — first at row 4, then every 8. */
    const val NATIVE_LIST_SLOT_INTERVAL = 8

    /** Max visible native ads per screenful. */
    const val NATIVE_LIST_MAX_VISIBLE = 2

    // ── App-open ad caps ─────────────────────────────────────────────────────
    val appOpenMinInterval = 4.hours
    const val APP_OPEN_MAX_PER_DAY = 3

    /** Foreground-after-X gap before app-open ad may show again. */
    val appOpenForegroundGap = 30.minutes

    /** If app-open ad doesn't return loaded within this, skip silently. */
    val appOpenLoadTimeout = 800.milliseconds

    /** Min gap between any two ads (prevents back-to-back ad walls). */
    val anyAdMinGap = 10.seconds

    // ── Interstitial caps ────────────────────────────────────────────────────
    const val INTERSTITIAL_MAX_PER_SESSION = 1
    const val INTERSTITIAL_MAX_PER_DAY = 4

    // ── Prohibited entry points (NEVER show app-open / interstitial here) ────
    val appOpenProhibitedEntries: Set<AdEntryPoint> = setOf(
        AdEntryPoint.NotificationTap,
        AdEntryPoint.ShareIntent,
        AdEntryPoint.SmsToIntent,
        AdEntryPoint.ReplyAction,
    )

    // ── Prohibited surfaces (NEVER any ad here) ──────────────────────────────
    /**
     * Surfaces where ANY ad type is forbidden. ThreadScreen ad-free is the
     * competitor-killing decision — do not violate.
     */
    val prohibitedSurfaces: Set<AdSurface> = setOf(
        AdSurface.ThreadScreen,
        AdSurface.Composer,
        AdSurface.ContactPicker,
        AdSurface.Onboarding,
        AdSurface.AppLock,
        AdSurface.NotificationQuickReply,
    )
}

/** App entry points used to gate the app-open ad. */
enum class AdEntryPoint {
    ColdStart,
    ForegroundResume,
    NotificationTap,
    ShareIntent,
    SmsToIntent,
    ReplyAction,
}

/** Surfaces in the app — ad rules reference these to declare prohibitions. */
enum class AdSurface {
    ConversationList,
    ThreadScreen,
    Composer,
    ContactPicker,
    Onboarding,
    AppLock,
    Settings,
    Backup,
    Blocked,
    Search,
    NotificationQuickReply,
}
