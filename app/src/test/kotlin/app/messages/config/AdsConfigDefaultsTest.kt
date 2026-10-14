package app.messages.config

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in what an app tap does when the new Remote Config keys are absent.
 *
 * Gson instantiates [AdsConfig] through the synthetic no-arg constructor Kotlin
 * emits because every parameter has a default, so keys missing from the JSON keep
 * those defaults rather than falling to JVM zero-values. These tests fail if a
 * parameter ever loses its default (which would silently flip every new flag to
 * false) or if someone changes the shipped default.
 */
class AdsConfigDefaultsTest {

    private val gson = Gson()

    /** An ads_config payload predating all of the app-click flags. */
    private val legacyJson = """
        {
          "interAdId": "ca-app-pub-0000000000000000/1111111111",
          "inter_count": 2,
          "isInterOn": true,
          "isAppClickInterOn": true
        }
    """.trimIndent()

    @Test
    fun `flags absent from json keep their kotlin defaults`() {
        val config = gson.fromJson(legacyJson, AdsConfig::class.java)

        assertFalse("all-apps must stay off", config.isAdShowOnAllApps)
        assertTrue("ad-consuming-only must stay on", config.isAdShowOnlyAdConsumeApps)
        assertFalse("app-click open ad must stay off", config.isAppClickOpenAdOn)
        assertEquals("open_ad must stay unset", "", config.appClickOpenAdId)
    }

    @Test
    fun `legacy config taps show an interstitial on ad-consuming apps only`() {
        val config = gson.fromJson(legacyJson, AdsConfig::class.java)

        assertEquals(AppClickAdFormat.INTERSTITIAL, config.appClickAdFormat)
        assertEquals(AppClickAdScope.AD_CONSUMING_ONLY, config.appClickAdScope)
    }

    @Test
    fun `the shipped default config behaves the same`() {
        assertEquals(AppClickAdFormat.INTERSTITIAL, AdsConfig.DEFAULT.appClickAdFormat)
        assertEquals(AppClickAdScope.AD_CONSUMING_ONLY, AdsConfig.DEFAULT.appClickAdScope)
    }

    @Test
    fun `empty json still lands on ad-consuming apps only`() {
        val config = gson.fromJson("{}", AdsConfig::class.java)

        assertEquals(AppClickAdFormat.INTERSTITIAL, config.appClickAdFormat)
        assertEquals(AppClickAdScope.AD_CONSUMING_ONLY, config.appClickAdScope)
    }
}
