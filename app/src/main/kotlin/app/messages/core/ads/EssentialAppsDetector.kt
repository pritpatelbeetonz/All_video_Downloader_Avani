package app.messages.core.ads

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telecom.TelecomManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Detects Essential & Utility apps (Emergency, System, Messaging, Payments, Email,
 * Maps, Browsers, Gallery, Clock, and Ride-Hailing/Transit apps like Uber/Rapido)
 * that MUST NEVER be blocked by an interstitial or app-open ad when launched
 * from the launcher, and NEVER increment the click counter.
 */
object EssentialAppsDetector {

    private const val TAG = "EssentialApps"

    /** Known static essential & utility package names across all major brands. */
    private val KNOWN_ESSENTIAL_PACKAGES = hashSetOf(
        // ── Emergency, System & Play Store ─────────────────────────────────────
        "com.android.vending",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",

        // ── System Settings & Security Centers ─────────────────────────────────
        "com.android.settings",
        "com.google.android.settings",
        "com.samsung.android.settings",
        "com.miui.securitycenter",
        "com.coloros.safecenter",
        "com.iqoo.secure",

        // ── Phone & Dialer ─────────────────────────────────────────────────────
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.dialer",
        "com.android.phone",
        "com.android.incallui",
        "com.sh.smart.caller",
        "com.truecaller",

        // ── Contacts ───────────────────────────────────────────────────────────
        "com.google.android.contacts",
        "com.samsung.android.contacts",
        "com.android.contacts",

        // ── Clock, Alarm & Timers ──────────────────────────────────────────────
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.android.deskclock",
        "com.coloros.alarm",
        "com.oppo.alarm",
        "com.vivo.alarm",
        "com.oneplus.deskclock",
        "com.motorola.timeweatherwidget",

        // ── Ride-Hailing, Cabs & Transit ───────────────────────────────────────
        "com.ubercab",
        "com.ubercab.driver",
        "com.rapido.passenger",
        "com.rapido.rider",
        "com.olacabs.customer",
        "com.olacabs.driver",
        "in.juspay.nammayatri",
        "com.indriver.passenger",
        "com.taxify.passenger",
        "com.lyft.android",
        "com.grabtaxi.passenger",
        "com.gojek.app",
        "com.yandex.taxi",

        // ── WhatsApp & Messaging ───────────────────────────────────────────────
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.telegram.plus",

        // ── Email ──────────────────────────────────────────────────────────────
        "com.google.android.gm",
        "com.microsoft.office.outlook",
        "com.samsung.android.email.provider",
        "com.android.email",
        "com.yahoo.mobile.client.android.mail",

        // ── Google Search & Assistant ──────────────────────────────────────────
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.searchlite",

        // ── Maps & Navigation ──────────────────────────────────────────────────
        "com.google.android.apps.maps",
        "com.google.android.apps.mapslite",
        "com.waze",
        "com.here.app.maps",

        // ── Chrome & Web Browsers ──────────────────────────────────────────────
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.sec.android.app.sbrowser",

        // ── Gallery & Photos ───────────────────────────────────────────────────
        "com.google.android.apps.photos",
        "com.google.android.apps.photosgo",
        "com.sec.android.gallery3d",
        "com.miui.gallery",
        "com.coloros.gallery3d",
        "com.vivo.gallery",
        "com.oppo.gallery3d",
        "com.huawei.photos",
        "com.android.gallery3d",
        "com.android.gallery",

        // ── Camera ─────────────────────────────────────────────────────────────
        "com.google.android.GoogleCamera",
        "com.sec.android.app.camera",
        "com.android.camera",
        "com.android.camera2",
        "com.oppo.camera",
        "com.vivo.camera",
        "com.oneplus.camera",
        "com.motorola.camera2",
        "com.motorola.camera3",
        "com.huawei.camera",
        "com.nothing.camera",
        "com.asus.camera",

        // ── Payment, UPI, Banking & Finance Apps ───────────────────────────────
        "com.google.android.apps.nbu.paisa.user", // Google Pay (GPay)
        "com.phonepe.app",                       // PhonePe
        "net.one97.paytm",                       // Paytm
        "in.org.npci.upiapp",                    // BHIM UPI
        "com.samsung.android.spay",              // Samsung Wallet / Pay
        "com.samsung.android.spaymini",
        "com.cred.club",                         // CRED
        "com.freecharge.android",
        "com.mobikwik_new",
        "in.amazon.mShop.android.shopping",      // Amazon / Amazon Pay
        "com.dreamplug.androidapp",
        "com.csam.icici.bank.imobile",           // ICICI
        "com.msf.kbank.mobile",                  // Kotak
        "com.sbi.lotusintouch",                  // SBI YONO
        "com.snapwork.hdfc",                     // HDFC
        "com.axis.mobile"                        // Axis Bank
    )

    /** Memoized results for instant 0ms check on app click. */
    private val cachedEssential = ConcurrentHashMap<String, Boolean>()

    /**
     * Returns true if [packageName] is an essential / utility / payment app
     * that should launch instantly with 0 ads and without advancing the click counter.
     */
    fun isEssentialApp(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (packageName == context.packageName) return true

        cachedEssential[packageName]?.let { return it }

        val isEssential = resolveEssential(context.applicationContext, packageName)
        cachedEssential[packageName] = isEssential
        return isEssential
    }

    /** Compatibility alias for Tier-1 callers. */
    fun isTierOneEssential(context: Context, packageName: String): Boolean =
        isEssentialApp(context, packageName)

    private fun resolveEssential(appContext: Context, packageName: String): Boolean {
        // Fast static check
        if (packageName in KNOWN_ESSENTIAL_PACKAGES) return true

        val pm = appContext.packageManager

        // 1. Check Default / System Dialer
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val telecomManager = appContext.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                val defaultDialer = telecomManager?.defaultDialerPackage
                if (!defaultDialer.isNullOrBlank() && defaultDialer == packageName) {
                    return true
                }
            }

            val dialerIntent = Intent(Intent.ACTION_DIAL)
            val dialerResolved = pm.queryIntentActivities(dialerIntent, PackageManager.MATCH_DEFAULT_ONLY)
            if (dialerResolved.any { it.activityInfo?.packageName == packageName }) {
                return true
            }
        } catch (_: Exception) {
        }

        // 2. Check System Settings Intent
        try {
            val settingsIntent = Intent(Settings.ACTION_SETTINGS)
            val settingsResolved = pm.queryIntentActivities(settingsIntent, PackageManager.MATCH_DEFAULT_ONLY)
            if (settingsResolved.any { it.activityInfo?.packageName == packageName }) {
                return true
            }
        } catch (_: Exception) {
        }

        // 3. Check Contacts Intent
        try {
            val contactsIntent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
            val contactsResolved = pm.queryIntentActivities(contactsIntent, PackageManager.MATCH_DEFAULT_ONLY)
            if (contactsResolved.any { it.activityInfo?.packageName == packageName }) {
                return true
            }
        } catch (_: Exception) {
        }

        // 4. Check Clock / Alarm Intent
        try {
            val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
            val clockResolved = pm.queryIntentActivities(clockIntent, PackageManager.MATCH_DEFAULT_ONLY)
            if (clockResolved.any { it.activityInfo?.packageName == packageName }) {
                return true
            }
        } catch (_: Exception) {
        }

        // 5. Check UPI / Payment Intent Handler (e.g. upi://pay)
        try {
            val upiIntent = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay"))
            val upiResolved = pm.queryIntentActivities(upiIntent, PackageManager.MATCH_DEFAULT_ONLY)
            if (upiResolved.any { it.activityInfo?.packageName == packageName }) {
                return true
            }
        } catch (_: Exception) {
        }

        // 6. Check Email Intent Handler (mailto:)
        try {
            val mailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            val mailResolved = pm.queryIntentActivities(mailIntent, PackageManager.MATCH_DEFAULT_ONLY)
            if (mailResolved.any { it.activityInfo?.packageName == packageName }) {
                return true
            }
        } catch (_: Exception) {
        }

        // 7. Check Maps / Navigation Intent Handler (geo:0,0)
        try {
            val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
            val geoResolved = pm.queryIntentActivities(geoIntent, PackageManager.MATCH_DEFAULT_ONLY)
            if (geoResolved.any { it.activityInfo?.packageName == packageName }) {
                return true
            }
        } catch (_: Exception) {
        }

        // 8. Check Default Web Browser Intent
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            val browserResolved = pm.queryIntentActivities(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
            if (browserResolved.any { it.activityInfo?.packageName == packageName }) {
                return true
            }
        } catch (_: Exception) {
        }

        // 9. Check Gallery / Photos View Intent Handler
        try {
            val galleryIntent = Intent(Intent.ACTION_VIEW).apply { type = "image/*" }
            val galleryResolved = pm.queryIntentActivities(galleryIntent, PackageManager.MATCH_DEFAULT_ONLY)
            if (galleryResolved.any { it.activityInfo?.packageName == packageName }) {
                return true
            }
        } catch (_: Exception) {
        }

        // 10. Check Camera Intent Handlers (IMAGE_CAPTURE & STILL_IMAGE_CAMERA)
        try {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val cameraResolved = pm.queryIntentActivities(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY)
            if (cameraResolved.any { it.activityInfo?.packageName == packageName }) {
                return true
            }
            val stillCameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            val stillCameraResolved = pm.queryIntentActivities(stillCameraIntent, PackageManager.MATCH_DEFAULT_ONLY)
            if (stillCameraResolved.any { it.activityInfo?.packageName == packageName }) {
                return true
            }
        } catch (_: Exception) {
        }

        return false
    }
}
