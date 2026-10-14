package app.messages.core.ads

import kotlin.time.Duration.Companion.hours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariant tests for [AdRules] — these encode product policy from build doc
 * §6.2. Changing any of these ships a different product. The competitor-
 * killing decisions live here.
 */
class AdRulesTest {

    @Test fun `ThreadScreen is permanently ad-free`() {
        assertTrue(AdSurface.ThreadScreen in AdRules.prohibitedSurfaces)
    }

    @Test fun `Composer is permanently ad-free`() {
        assertTrue(AdSurface.Composer in AdRules.prohibitedSurfaces)
    }

    @Test fun `Onboarding is ad-free`() {
        assertTrue(AdSurface.Onboarding in AdRules.prohibitedSurfaces)
    }

    @Test fun `App lock is ad-free`() {
        assertTrue(AdSurface.AppLock in AdRules.prohibitedSurfaces)
    }

    @Test fun `Notification quick reply is ad-free`() {
        assertTrue(AdSurface.NotificationQuickReply in AdRules.prohibitedSurfaces)
    }

    @Test fun `Notification-tap entry never shows app-open ad`() {
        assertTrue(AdEntryPoint.NotificationTap in AdRules.appOpenProhibitedEntries)
    }

    @Test fun `Reply-action entry never shows app-open ad`() {
        assertTrue(AdEntryPoint.ReplyAction in AdRules.appOpenProhibitedEntries)
    }

    @Test fun `Share intent entry never shows app-open ad`() {
        assertTrue(AdEntryPoint.ShareIntent in AdRules.appOpenProhibitedEntries)
    }

    @Test fun `Native list ads start at session 3 or later`() {
        assertTrue(AdRules.NATIVE_LIST_FIRST_ALLOWED_SESSION >= 3)
    }

    @Test fun `App-open frequency cap is at least 4 hours`() {
        assertTrue(AdRules.appOpenMinInterval >= 4.hours)
    }

    @Test fun `App-open daily cap does not exceed 3`() {
        assertTrue(AdRules.APP_OPEN_MAX_PER_DAY <= 3)
    }

    @Test fun `Native list first slot is index 4`() {
        assertEquals(4, AdRules.NATIVE_LIST_FIRST_SLOT_INDEX)
    }

    @Test fun `Native list interval is at least 10 tiles`() {
        assertTrue(AdRules.NATIVE_LIST_SLOT_INTERVAL >= 10)
    }
}
