package app.messages.core.ads

import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive tests for the ad-gating moat. These encode product policy (§6.2);
 * a regression here ships a worse product than we promised users.
 */
class AdsPolicyTest {

    private val now = 1_000_000_000_000L

    // ── Premium kills everything ───────────────────────────────────────────────
    @Test fun `premium never sees native list ads`() {
        assertFalse(AdsPolicy.canShowNativeListAds(premium = true, adsEnabled = true, sessionCount = 99))
    }

    @Test fun `premium never sees app-open ads`() {
        val d = AdsPolicy.appOpenDecision(
            premium = true, adsEnabled = true, sessionCount = 99,
            entryPoint = AdEntryPoint.ColdStart, lastShownAtMs = null,
            shownTodayCount = 0, nowMs = now,
        )
        assertFalse(d.allowed)
        assertEquals("premium", d.reason)
    }

    @Test fun `premium never sees interstitials`() {
        assertFalse(AdsPolicy.canShowInterstitial(premium = true, adsEnabled = true, shownThisSession = 0, shownToday = 0))
    }

    // ── Remote kill switch (ships OFF) ─────────────────────────────────────────
    @Test fun `ads disabled blocks native list`() {
        assertFalse(AdsPolicy.canShowNativeListAds(premium = false, adsEnabled = false, sessionCount = 99))
    }

    @Test fun `ads disabled blocks app-open`() {
        val d = AdsPolicy.appOpenDecision(
            false, adsEnabled = false, sessionCount = 99,
            AdEntryPoint.ColdStart, null, 0, now,
        )
        assertFalse(d.allowed)
        assertEquals("ads disabled", d.reason)
    }

    // ── Native list session floor + slots ──────────────────────────────────────
    @Test fun `native list blocked before session 3`() {
        assertFalse(AdsPolicy.canShowNativeListAds(false, adsEnabled = true, sessionCount = 2))
    }

    @Test fun `native list allowed at session 3 when enabled`() {
        assertTrue(AdsPolicy.canShowNativeListAds(false, adsEnabled = true, sessionCount = 3))
    }

    @Test fun `no native slots for short lists`() {
        assertTrue(AdsPolicy.nativeAdContentSlots(4).isEmpty())
        assertTrue(AdsPolicy.nativeAdContentSlots(0).isEmpty())
    }

    @Test fun `native slots start at 4 then every 8`() {
        assertEquals(listOf(4, 12, 20, 28), AdsPolicy.nativeAdContentSlots(30))
        assertEquals(listOf(4), AdsPolicy.nativeAdContentSlots(10))
    }

    // ── App-open caps ──────────────────────────────────────────────────────────
    @Test fun `app-open blocked in early sessions`() {
        val d = AdsPolicy.appOpenDecision(false, true, sessionCount = 3, AdEntryPoint.ColdStart, null, 0, now)
        assertFalse(d.allowed)
        assertEquals("early session", d.reason)
    }

    @Test fun `app-open allowed at session 4 cold start`() {
        val d = AdsPolicy.appOpenDecision(false, true, 4, AdEntryPoint.ColdStart, null, 0, now)
        assertTrue(d.allowed)
    }

    @Test fun `app-open never on notification-tap entry`() {
        val d = AdsPolicy.appOpenDecision(false, true, 99, AdEntryPoint.NotificationTap, null, 0, now)
        assertFalse(d.allowed)
    }

    @Test fun `app-open never on share or reply or sms-to entry`() {
        listOf(AdEntryPoint.ShareIntent, AdEntryPoint.ReplyAction, AdEntryPoint.SmsToIntent).forEach { ep ->
            assertFalse(ep.name, AdsPolicy.appOpenDecision(false, true, 99, ep, null, 0, now).allowed)
        }
    }

    @Test fun `app-open respects daily cap of 3`() {
        val d = AdsPolicy.appOpenDecision(false, true, 99, AdEntryPoint.ColdStart, null, shownTodayCount = 3, nowMs = now)
        assertFalse(d.allowed)
        assertEquals("daily cap", d.reason)
    }

    @Test fun `app-open respects 4-hour interval`() {
        val threeHrsAgo = now - 3.hours.inWholeMilliseconds
        val blocked = AdsPolicy.appOpenDecision(false, true, 99, AdEntryPoint.ColdStart, threeHrsAgo, 1, now)
        assertFalse(blocked.allowed)
        assertEquals("min interval", blocked.reason)

        val fiveHrsAgo = now - 5.hours.inWholeMilliseconds
        val allowed = AdsPolicy.appOpenDecision(false, true, 99, AdEntryPoint.ColdStart, fiveHrsAgo, 1, now)
        assertTrue(allowed.allowed)
    }

    // ── Interstitial caps ───────────────────────────────────────────────────────
    @Test fun `interstitial one per session`() {
        assertTrue(AdsPolicy.canShowInterstitial(false, true, shownThisSession = 0, shownToday = 0))
        assertFalse(AdsPolicy.canShowInterstitial(false, true, shownThisSession = 1, shownToday = 0))
    }

    @Test fun `interstitial four per day`() {
        assertFalse(AdsPolicy.canShowInterstitial(false, true, shownThisSession = 0, shownToday = 4))
    }

    // ── Surface prohibitions ────────────────────────────────────────────────────
    @Test fun `thread and composer surfaces never allowed`() {
        assertFalse(AdsPolicy.isSurfaceAllowed(AdSurface.ThreadScreen))
        assertFalse(AdsPolicy.isSurfaceAllowed(AdSurface.Composer))
        assertFalse(AdsPolicy.isSurfaceAllowed(AdSurface.Onboarding))
        assertFalse(AdsPolicy.isSurfaceAllowed(AdSurface.AppLock))
    }

    @Test fun `conversation list and settings surfaces allowed`() {
        assertTrue(AdsPolicy.isSurfaceAllowed(AdSurface.ConversationList))
        assertTrue(AdsPolicy.isSurfaceAllowed(AdSurface.Settings))
    }

    // ── Global back-to-back gap ─────────────────────────────────────────────────
    @Test fun `global gap blocks back-to-back ads`() {
        assertTrue(AdsPolicy.respectsGlobalGap(null, now))
        assertFalse(AdsPolicy.respectsGlobalGap(now - 5_000, now)) // 5s < 10s gap
        assertTrue(AdsPolicy.respectsGlobalGap(now - 15.minutes.inWholeMilliseconds, now))
    }
}
