# Install Source Detection (Google / Facebook / Unity)

Decides once per install where the user came from: `google`, `facebook`, `unity` or `regular`.
Ported from the reference project (`VideoDownloader(snehil_launcher_for_Utm_flow)` →
`attribution/`), detection part only. **What the app does for each source is not built yet**:
`InstallSourceManager.onSourceDecided()` only logs.

## Files — `app/src/main/kotlin/app/messages/attribution/`

| File | Job |
|---|---|
| `InstallSourceManager.kt` | Runs the checks in order, saves the result once, sets the GA4 user property, `onSourceDecided()` hook |
| `GoogleSourceDetector.kt` | Google Ads: `gclid` / utm rules on the Play referrer |
| `FacebookSourceDetector.kt` | Meta: Facebook deferred deep link, then FB marker in the Play referrer |
| `UnitySourceDetector.kt` | Unity Ads: `unity` marker in the Play referrer |
| `FirestoreSourceStore.kt` | Reinstalls: reads / writes `install_referrers/<sha256(ANDROID_ID)>` so an ad source survives uninstall |
| `PlayReferrer.kt` | Reads the Play Install Referrer once (shared by all three) and splits it into parameters |
| `AttributionConfig.kt` | Settings from Remote Config key `install_attribution_config` (defaults built in) |
| `AttributionLog.kt` | Log tag `InstallSource` (debug builds only) |

Other changes:
- `app/build.gradle.kts`: `com.android.installreferrer:installreferrer:2.2`, `com.facebook.android:facebook-applinks:17.0.2`,
  `com.google.firebase:firebase-firestore` (BOM version)
- `MessagesApplication.onCreate()`: `InstallSourceManager.init(this)` — loads a saved source only
- `SplashActivity`: runs the check after its Remote Config fetch, waits ≤ 6 s, routes by source

## Flow

```
App start (MessagesApplication.onCreate)
 └─ InstallSourceManager.init()  → source saved before? → reuse it (later launches: instant) 💾

SplashActivity (first launch)
 ├─ no internet → regular flow right away (nothing saved, retried next launch)
 ├─ Remote Config fetch (existing splash code, ≤ 30 s)
 └─ InstallSourceManager.checkFromSplash(6 s), using the values just fetched
     ├─ ⚙️ read install_attribution_config
     ├─ isShowRegularToAll = true? → regular, nothing saved (src_regular_to_all) 🟦
     ├─ installed before this version? → regular (src_existing_user) 👴
     ├─ Firestore has this phone? → earlier ad source (src_<s>_from_firestore) 🔥
     ├─ isSimCheckAllow + no SIM (release only) → regular (src_no_sim) 📶
     ├─ country in blockedCountriesForFacebookContent → regular (src_blocked_country_XX) 🌍
     ├─ read Play referrer (≤10 s)                                   📥
     ├─ Google check   → google   (src_from_google)                  🔵
     ├─ Facebook check → facebook (src_from_facebook)   OFF by default 🟣
     ├─ Unity check    → unity    (src_from_unity)      OFF by default 🟢
     └─ nothing / timeout (30 s) → regular (src_organic)             ⚪
         ↓
     saved in prefs "install_source" · GA4 user property install_source
     · event src_* with matched_by · onSourceDecided() (TODO)        🎯
     · ad sources written to Firestore (retried next launch if it fails) 🔥
         ↓
 🚦 splash continues (decided, or 6 s passed → regular; the check still finishes and saves)
     ├─ (TODO) splash interstitial: load / show depending on the source
     └─ nextActivity()
          paid (google / fb / unity) → Set as default → Language → (11 intro screens, TODO) → Launcher
          regular                    → Onboarding → Set as default → Language → Launcher (unchanged)
```

A Google user decided only after the 6 s cap gets the regular flow on that first launch; the
saved `google` still counts for everything after.

## Where the result is saved

| Where | What | Survives uninstall |
|---|---|---|
| SharedPreferences `install_source.xml` | `source`, `matched_by`, `eligible`, `pending_sync` | No |
| Firestore `install_referrers/<sha256(ANDROID_ID)>` | `{ src, t, id }`, ad sources only | Yes |
| Firebase Analytics | user property `install_source`, event `src_*` | (reports only) |

## Firestore setup (Firebase console, project `pritpatelall`)

1. Build → Firestore Database → Create database (production mode).
2. Rules — allow reading one document and writing only a valid ad source; no listing or deleting:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /install_referrers/{id} {
         allow get: if true;
         allow create, update: if request.resource.data.keys().hasOnly(['src', 't', 'id'])
                               && request.resource.data.src in ['google', 'facebook', 'unity']
                               && request.resource.data.id == id;
         allow list, delete: if false;
       }
     }
   }
   ```
3. Data Safety form: the app now stores a hashed device identifier (ANDROID_ID SHA-256) with the
   install source → declare "Device or other IDs", purpose Analytics, not shared.

Turn it off without an update: `"isFirestoreEnabled": false` in `install_attribution_config`.

## Remote Config

New key **`install_attribution_config`** (JSON). Every field is optional; empty key = defaults.

**`isShowRegularToAll` defaults to `true` (detection OFF), as in the reference app.** Set it to
`false` in Remote Config to turn detection on. Switching it back to `true` makes everyone regular
again on the next launch (saved sources are kept and come back when it is turned off).

### Current value — Google only

Facebook and Unity checks are **off by default** (`isCheckFacebookSource` / `isCheckUnitySource`
default `false`), so their keys are left out:

```json
{
  "isShowRegularToAll": false,

  "isSimCheckAllow": false,
  "blockedCountriesForFacebookContent": [],

  "isCheckGoogleSource": true,
  "isCheckGoogleUtmList": true,
  "googleAdUTMSourceCheckStringList": [],
  "googleReferrerRules": [
    { "gclid": "*" },
    { "utm_source": ["google"], "utm_medium": ["cpc", "ppc", "paid"] }
  ],

  "isFirestoreEnabled": true,
  "firestoreReadTimeoutInSeconds": 5,

  "referrerCheckTimeoutInSeconds": 30,
  "playReferrerTimeoutInSeconds": 10
}
```

| Key | Default | Meaning |
|---|---|---|
| `isShowRegularToAll` | `true` | On = everyone regular, no checks. Set `false` to detect |
| `isSimCheckAllow` | `false` | On = no SIM card → regular (release builds only) |
| `blockedCountriesForFacebookContent` | `[]` | Users in these countries → regular. **Applies to every source, Google too**, despite the name. `["IN","US"]` or `["IN,US"]` |
| `isCheckGoogleSource` | `true` | Run the Google check |
| `isCheckGoogleUtmList` | `true` | Use `googleAdUTMSourceCheckStringList` |
| `googleAdUTMSourceCheckStringList` | `[]` | Google if the raw referrer contains any of these |
| `googleReferrerRules` | gclid / google+cpc | Google if any rule matches |
| `isFirestoreEnabled` | `true` | Reinstall memory |
| `firestoreReadTimeoutInSeconds` | `5` | |
| `referrerCheckTimeoutInSeconds` | `30` | Whole check; then regular |
| `playReferrerTimeoutInSeconds` | `10` | |

Note: SIM and country are checked **before** Google (reference order), so a real Google-ad user
in a blocked country or without a SIM is saved as regular.

### Later — turning Facebook / Unity on

Add to the same JSON:

```json
  "isCheckFacebookSource": true,
  "fbUtm": ["apps.facebook.com"],
  "fbUtmsForDeferrerLink": ["facebook", "fbclid"],
  "loopCountForFbDeepLinkCheck": 2,
  "awaitSecondCountForFbDeepLinkCheck": 2,
  "fbDeferredLinkTimeoutInSeconds": 5,

  "isCheckUnitySource": true,
  "unityUtm": ["unity"]
```

Rule syntax (`googleReferrerRules`): every key in a rule must match. `"*"` = present and not empty,
`"text"` = equals, `["a","b"]` = equals any. Case-insensitive.

Facebook deferred links need the Facebook SDK started, which `MetaAudienceNetworkManager` does
with `remoteFacebookAppId` / `remoteFacebookClientToken` from the ads config. Without those only
the Play referrer marker (`fbUtm`) works. `fbUtm` defaults to empty — set it.

## Logcat

Filter (Android Studio Logcat search box):

```
tag:InstallSource
```

or from a terminal:

```
adb logcat -s InstallSource
```

Example (Google ad install):
```
🆕 Fresh install: eligible for detection
☁️ Remote Config fetched
⚙️ install_attribution_config = (empty, defaults)
🚀 Install source check started
📥 Reading Play Install Referrer (timeout 10s)...
✅ Play referrer: "gclid=test123&utm_source=google&utm_medium=cpc" | click=... install=... installer=com.android.vending
🔵 [Google] Checking...
🔵 [Google] Params: {gclid=test123, utm_source=google, utm_medium=cpc}
🔵 [Google] ✅ Matched rule 0: {"gclid":"*"}
🎯 DECIDED: google (src_from_google, matched_by=rule:0)
🔵 Google ad user. No action yet
```

Next launch: `💾 Source from prefs: google (matched_by=rule:0). No checks`

## Testing

The referrer only exists for real Play installs (debug APKs from Android Studio log
`🚫 Play referrer not supported` and end as regular).

1. Upload to Play Console → Internal testing; add your account as tester.
2. Uninstall the app.
3. Open in Chrome **on the phone**:
   ```
   https://play.google.com/store/apps/details?id=app.john.all.video.downloader.com&referrer=gclid%3Dtest123%26utm_source%3Dgoogle%26utm_medium%3Dcpc
   ```
   Unity test: `&referrer=utm_source%3Dunity%26utm_medium%3Dcpi`
4. Install from that page, open the app, watch `tag:InstallSource`.
   (Release builds don't log; use a debuggable build on the internal track, or check the
   `src_*` events in Firebase DebugView.)

To re-test on the same phone: clear app data (the saved source lives in prefs `install_source`)
**and** delete the phone's document in Firestore `install_referrers` — otherwise the reinstall is
decided from Firestore (`🔥 [Firestore] Found src=google`).

## Not ported from the reference (on purpose)

- `isDifferentContentAllow`. (SIM and blocked-country checks are ported, off by default. Use the
  result for analytics / ad settings only — never to hide features from some users: Play cloaking risk.)
- The `install_referrer_logs` debug collection.
- `server_config.json` download: settings come straight from one Remote Config key.

## Next

Decide what `onSourceDecided()` does per source (e.g. OneSignal tag `install_source`, per-source
ad frequency, passing the source to the Flutter downloader over the method channel).
