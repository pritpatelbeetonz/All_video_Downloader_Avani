# Messages — Private SMS Messenger
## Build Specification & Project Documentation for Claude Code

> **Project codename:** `messages_app`
> **Platform:** Android only (see §1.2 — iOS is technically impossible for SMS apps)
> **Stack:** Pure native — Kotlin + Jetpack Compose + Material 3
> **Design language:** Material 3 (Material You), minimalist
> **Monetization:** AdMob (mediated) + Google Play Billing (subscription + lifetime IAP)
> **Target:** Play Store, Communication category, default SMS handler app

---

## 1. Product Overview

### 1.1 What we are building
A clean, fast, privacy-focused replacement for the stock Android SMS app. The user sets it as their **default SMS handler**, after which all SMS/MMS flows through our app. Core value proposition: *"Everything the stock app does, plus scheduling, backup, export, blocking, and privacy lock — in a calmer, cleaner interface."*

Reference competitor: `messages.sms.text.massanger` (100K+ installs, 4.4★). Their fatal flaw per user reviews: aggressive popup/interstitial ads injected after launch, slow performance, and a bloaty "home launcher" gimmick. **We win by being the version of this app that respects the user.** Do NOT build a launcher module.

### 1.2 Platform decision (read carefully)
- **Android:** Full app. SMS handling requires native Android Telephony APIs (`SmsManager`, `Telephony.Sms` content provider, `SMS_DELIVER` broadcast receiver). These are accessed from Flutter via **Kotlin platform channels**. The app MUST register as a default SMS handler via `RoleManager.ROLE_SMS`.
- **iOS:** **Do not build.** Apple provides no API for third-party SMS apps. There is no SwiftUI version of this product. (If an iOS companion is ever wanted, it must be a different product — out of scope.)

### 1.3 Why pure native Kotlin + Compose
Going native eliminates the Flutter ↔ Telephony marshalling layer entirely and gives first-class access to Material You dynamic color, the role manager, MessagingStyle notifications, and BubbleMetadata without plugin wrappers. Trade-off: the team needs Kotlin/Compose competency instead of Dart/Flutter.

Core native modules:
- `data/telephony/` — `ConversationQuery`, `SmsSender`, `DefaultSmsRole`, the SMS_DELIVER / WAP_PUSH_DELIVER receivers, `HeadlessSmsSendService`, and the SENT / DELIVERED status receivers.
- `data/telephony/TelephonyEventBus` — a `SharedFlow<TelephonyEvent>` that replaces what the Flutter version did via `EventChannel`; receivers emit, repositories collect.
- `data/role/` — wraps `RoleManager.ROLE_SMS` polling + lifecycle-aware refresh.
- `data/contacts/` — `ContactsResolver` against `ContactsContract`.
- `core/prefs/` — DataStore Preferences (replaces the Flutter `shared_preferences`).

---

## 2. Google Play Policy Constraints (NON-NEGOTIABLE)

These shape everything. Violating any of these gets the app removed.

1. **SMS permissions policy:** `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS` are ONLY permitted because the app is a default SMS handler. The app must function as a real SMS app. Permissions declaration form must be filled in Play Console.
2. **No data exfiltration:** Message content must NEVER leave the device except for user-initiated backup to the user's own Google Drive (App Data folder) or local file export. No analytics events may contain message content, phone numbers, or contact names. Declare "No data shared with third parties" in Data Safety — and make it true.
3. **Intrusive ads policy / Better Ads Standards:** No ads that appear unexpectedly during interaction, no full-screen ads that can't be dismissed after a few seconds, no ads on the lock screen, no ads outside the app. App-open ads must comply with AdMob app-open format guidance (shown on cold start/foreground, with app branding visible).
4. **Families policy:** Rated 12+; do not enroll in Families program. Set AdMob content rating to T max.
5. **Backup privacy:** Backups must be encrypted (AES-256, key derived from user passphrase or Android Keystore) before upload.

---

## 3. Feature Specification

### 3.1 P0 — MVP (v1.0, ship this first)

| # | Feature | Detail |
|---|---------|--------|
| F1 | Default SMS handler onboarding | 3-screen onboarding → role request → permission grants. App is read-only viewer until role granted. |
| F2 | Conversation list | Threaded, avatar (contact photo or initial+color), name/number, snippet, timestamp, unread badge, pinned section on top. Sorted by last activity. |
| F3 | Conversation/thread view | Bubbles (sent right/received left), day separators, delivery status ticks, long-press: copy/forward/delete/info/star. Link/phone/email detection. |
| F4 | Compose & send | New message screen with contact search/autocomplete, multi-recipient, SIM selector (dual-SIM), character/segment counter, multipart SMS. |
| F5 | MMS | Receive + display images/video/audio/vCard. Send images (compressed to carrier limit). Group MMS. |
| F6 | Contacts | Read device contacts (sync on launch + change listener). Tap avatar → contact card sheet. |
| F7 | Notifications | Per-conversation notification channels, inline reply action, mark-as-read action, custom sound per conversation, bubble support (Android 11+). |
| F8 | Search | Full-text search across messages and contacts. Debounced, indexed locally (Drift/SQLite FTS5 mirror of telephony provider). |
| F9 | Blocking & spam | Block number (uses `BlockedNumberContract` when default handler), blocked list management screen, "Blocked & spam" folder. |
| F10 | Archive & delete | Swipe actions on conversation list (configurable: archive / delete / mark read). |
| F11 | Themes | Light / Dark / System + Material You dynamic color. 8 accent colors free, 20+ premium. Bubble style options (premium). |
| F12 | Settings | Notifications, swipe actions, appearance, send settings (delivery reports, SIM), about, privacy policy. |

### 3.2 P1 — v1.1–v1.3 (retention & monetization features)

| # | Feature | Detail |
|---|---------|--------|
| F13 | Message scheduling | Compose → clock icon → date/time picker. Scheduled queue screen. Uses `AlarmManager` exact alarms (request `SCHEDULE_EXACT_ALARM`). Free: 3 active scheduled messages; Premium: unlimited. |
| F14 | Backup & restore | Local backup (encrypted .zip to user-picked folder via SAF) free + manual. Google Drive auto-backup (daily/weekly) = Premium. Restore wizard on fresh install. |
| F15 | Export | Export a conversation or all messages to TXT / CSV free; PDF (nicely formatted, using `pdf` package) = Premium. |
| F16 | App lock | PIN / biometric lock on app open; per-conversation lock = Premium. Uses `local_auth`. |
| F17 | Private folder | Hide selected conversations behind lock; suppressed notification previews ("New message"). Premium. |
| F18 | Quick reply popup | Heads-up style quick reply window. |
| F19 | Auto-delete | Auto-delete OTPs after 24h / old messages after N days (opt-in). Great "clean inbox" marketing hook. |
| F20 | Starred messages | Star any message; "Starred" screen. |

### 3.3 Explicitly OUT of scope
- ❌ Home launcher module (competitor's mistake — bloat, bad reviews, policy risk)
- ❌ RCS (no public API; Google restricts to partners)
- ❌ Any chat-over-internet features
- ❌ iOS

---

## 4. App Flow & Information Architecture

### 4.1 Onboarding flow (first launch)
```
Splash (logo, <1s, no ad EVER here)
 → Welcome screen 1: "A calmer home for your messages" (illustration)
 → Screen 2: Privacy promise ("Your messages never leave your phone")
 → Screen 3: "Set as default SMS app" → system role dialog
    ├─ Granted → contacts permission rationale → system dialog
    │     → notification permission (Android 13+)
    │     → [SOFT PAYWALL — skippable, see §6.4]
    │     → Conversation list (data import indicator if large mailbox)
    └─ Denied → limited read-only mode + persistent gentle banner
              "Set as default to send messages"
```
Rules: onboarding ≤ 60 seconds, every screen skippable except the role request, **zero ads anywhere in onboarding**.

### 4.2 Main navigation
Single-activity, no bottom nav bar (minimalism). Structure:
```
ConversationListScreen (home)
 ├─ AppBar: app name (left), search icon, avatar/menu (right)
 ├─ Menu sheet: Archived, Blocked & spam, Scheduled, Starred,
 │              Backup, Settings, ⭐ Go Premium
 ├─ FAB: "Start chat" → ComposeScreen
 ├─ List: [Pinned] + threads (+ 1 native ad slot, see §6.2)
 └─ Tap thread → ThreadScreen
       ├─ AppBar: avatar+name (tap → contact sheet), call icon, menu
       ├─ Message list (reverse, lazy, paginated 50/page)
       └─ Composer: attach (+), text field, SIM chip, schedule, send
```

### 4.3 Key user journeys (optimize these paths — they're walked 10–30×/day)
1. **Notification → reply:** notification tap → thread → reply → back. *No ad may ever appear on this path.*
2. **Open app → read new:** cold start → (app-open ad if eligible, see §6.2) → list → thread.
3. **Compose new:** FAB → pick contact → type → send.
Paths 1 and 3 must be ad-free. Path 2 carries the app-open ad only under frequency cap.

---

## 5. Design System (Material 3, Minimalist)

### 5.1 Principles
1. **Content-first:** messages are the UI. Chrome is invisible until needed.
2. **One accent color** at a time; everything else neutral.
3. **Generous whitespace:** 16dp screen margins, 12dp between list items' content, 72dp list item height.
4. **Motion is subtle:** 200–250ms standard easing, container transforms for list→thread, no gratuitous animation.
5. **No visual debt:** no gradients on bubbles by default, no drop shadows except elevation tokens, no emoji in UI labels.

### 5.2 Tokens
```dart
// Color — built on ColorScheme.fromSeed; dynamic color when available
seed (default): #2E5BFF  ("Calm Blue")
Light: surface #FCFCFF, surfaceContainer #F2F3F8, onSurface #1A1B20
Dark:  surface #121318, surfaceContainer #1E1F25, onSurface #E3E2E9
Sent bubble: primaryContainer / onPrimaryContainer
Received bubble: surfaceContainerHigh / onSurface
Unread indicator: primary dot + bold name (no loud badges)

// Typography — Google Fonts "Inter" (fallback Roboto)
displaySmall 24/32 600   → screen titles
titleMedium  16/24 600   → conversation names
bodyLarge    16/24 400   → message text
bodyMedium   14/20 400   → snippets
labelSmall   11/16 500   → timestamps, status

// Shape
Bubbles: 18dp radius, 4dp on the "tail" corner of consecutive groups
Cards/sheets: 16dp; FAB: Material 3 default; Search bar: full pill (28dp)

// Spacing scale: 4 / 8 / 12 / 16 / 24 / 32
```

### 5.3 Component specs
- **Conversation tile:** 72dp; 40dp avatar (initial on `colorScheme` tertiary palette hash of number); name `titleMedium`; snippet `bodyMedium` @ 60% opacity, 1 line ellipsis; time `labelSmall`; unread = primary dot + name/snippet at full weight/opacity. Pinned shows 14dp pin glyph.
- **Bubbles:** max width 78%; consecutive same-sender grouped (2dp gap inside group, 12dp between groups); timestamp on long-press or last-in-group only; status: ✓ sent ✓✓ delivered, red ! + "Tap to retry" on failure.
- **Composer:** 1–5 line auto-grow field in pill container; send button morphs from mic-less plain disabled → filled primary when text present; attachment sheet = modal bottom sheet with 6 large targets (Gallery, Camera, GIF, Contact, Schedule, Audio).
- **Empty states:** single line of copy + soft illustration, e.g. "No messages yet. Say hello 👋" — the only allowed emoji.
- **Dark mode** is first-class; test every screen both modes.

### 5.4 Accessibility & feel
- Min touch targets 48dp; TalkBack labels on all icons; font scaling to 200% without breakage.
- Haptics: light tick on send, selection click on long-press.
- Cold start < 1.2s to interactive list (profile this; lazy-init ads SDK AFTER first frame).

---

## 6. Monetization

### 6.1 Strategy summary
SMS apps = extreme session frequency (10–30/day), tiny session length, extreme retention once set as default. Therefore:
- **Revenue mix target:** ~55% ads / 45% IAP at maturity (IAP share grows over time).
- Ads = high-volume, low-intrusion formats (native in list, capped app-open).
- IAP = subscription with **lifetime anchor**, sold on privacy/backup/customization.
- The rating IS the funnel: every design decision that protects the 4.7★+ rating compounds organic installs in this keyword-rich category.

### 6.2 Ad placements (AdMob, mediation-ready)

| Placement | Format | Rules |
|-----------|--------|-------|
| **A1. Conversation list native ad** | Native Advanced, custom-rendered to look like a *clearly labeled* conversation tile ("Sponsored" chip, distinct surface tint) | Position: index 4, then every 10 tiles. Max 2 visible per screenful. NOT shown in first 2 sessions (let users fall in love first). Hidden during search and selection mode. |
| **A2. App-open ad** | AppOpenAd | Cold start + foreground-after-30-min only. Frequency cap: **1 per 4 hours, max 3/day**. NEVER on: first 3 sessions, notification-tap entry, share-intent entry, within 10s of a previous ad. Preload; if not loaded in 800ms, skip silently. |
| **A3. Task-completion interstitial** | Interstitial | Only after user *completes* a discrete utility task: backup finished, export saved, restore done. Cap 1/session, 4/day. Natural break = perceived as fair. |
| **A4. Rewarded — theme unlock** | Rewarded video | "Watch to unlock this theme for 7 days" on premium theme tap. Also: unlock 1 extra scheduled-message slot. This is a paywall teaser, not a revenue line. |
| **A5. Settings/secondary screens banner** | Adaptive anchored banner | Bottom of Settings, Backup, Blocked screens ONLY. |

**Hard prohibitions (encode as constants/tests):**
- ❌ No ads in ThreadScreen (the conversation itself). Ever. This is the competitor-killing decision.
- ❌ No ads in composer, contact picker, onboarding, notification flows, app lock screen.
- ❌ No interstitial on app exit, on back-press, or interrupting typing.
- ❌ Ads SDK initialized lazily post-first-frame; never block startup.

**Implementation:** central `AdsController` (Riverpod) gating every placement through: `isPremium == false && sessionCount > N && frequencyCapOk && entryPoint.allowed`. UMP SDK consent flow (GDPR/EEA) on first eligible session, before any ad request. Mediation: start AdMob-only; add Meta Audience Network + AppLovin via mediation once at 50K+ DAU.

### 6.3 IAP / Premium ("Messages Plus")

| SKU | Type | Price (USD / INR tier examples) | Notes |
|-----|------|--------------------------------|-------|
| `plus_weekly` | Sub | $1.99 / ₹99 | Trial funnel: 3-day free trial |
| `plus_yearly` | Sub | $11.99 / ₹699 | "Best value" badge, ~88% discount framing vs weekly |
| `plus_lifetime` | One-time | $24.99 / ₹1,499 | Anchor + commitment-phobe catcher |

**Premium entitlements:** remove all ads • all themes + bubble styles + app icon variants • Google Drive auto-backup • PDF export • unlimited scheduled messages • per-conversation lock + private folder • premium notification sounds • priority support.

Free tier must remain genuinely good (core messaging never gated) — that's both Play policy hygiene and review protection.

### 6.4 Paywall placement & design
1. **Onboarding soft paywall** (after permissions, before home): full-screen, benefit-led ("Private. Backed up. Yours."), yearly preselected, trial toggle visible, **X to dismiss in top corner immediately visible**. Expect 1–2% conversion here; its real job is anchoring.
2. **Contextual triggers** (highest converters): tap locked theme → mini paywall sheet; 4th scheduled message → upsell; enabling auto-backup → upsell; after user closes 3rd app-open ad in a week → "Tired of ads? Remove them forever" sheet (this one converts disproportionately).
3. **Settings → "Go Premium ⭐" row,** always present, never nagging.
4. One gentle promo notification max per month (e.g., yearly discount), opt-out respected.

Use RevenueCat if speed matters; otherwise `in_app_purchase` + server-less local verification with Play's purchase acknowledgment (acceptable at this scale), entitlement cached in encrypted prefs and re-validated via `queryPurchases` on launch.

### 6.5 Pricing localization
Use Play's price templates per country tier. India/Brazil/Indonesia: aggressive local pricing (₹699/yr ≈ impulse zone). US/UK/DE/AU: standard. Match the existing multi-locale ASO playbook (see §9).

---

## 7. Technical Architecture

### 7.1 Stack
```
Kotlin 2.x · JDK 17 · Android Gradle Plugin 8.x · Gradle 8.x
UI: Jetpack Compose + Material 3       Nav: androidx.navigation:navigation-compose
State: ViewModel + StateFlow           DI: Hilt (kapt-free via KSP)
Local DB: Room + FTS4                  Coroutines + Flow throughout
Background: WorkManager + AlarmManager  Async images: Coil
Ads: Google Mobile Ads SDK + UMP       IAP: RevenueCat (purchases-android) or BillingClient v7+
Auth/Drive: Google Sign-In + Drive AppData scope (com.google.api-client)
Crash/analytics: Firebase Crashlytics + Analytics (NO message content/PII in events)
Notifications: NotificationCompat + MessagingStyle + Bubbles (API 30+)
Other: ContactsContract reader, AndroidX Biometric, SAF document URIs, iText/PdfDocument, javax.crypto
```

### 7.2 Module layout (feature-first, single app module)
```
app/src/main/kotlin/app/messages/
  MessagesApplication.kt       Hilt @HiltAndroidApp entry
  MainActivity.kt              ComponentActivity + setContent { MessagesApp() }
  core/
    ads/AdRules.kt             Frequency caps + prohibited surfaces (the moat)
    prefs/AppPrefs.kt          DataStore Preferences
    time/RelativeTime.kt       Conversation-list timestamp formatter
    util/                      Misc
  data/
    telephony/
      ConversationQuery.kt     Telephony provider reads
      SmsSender.kt             SmsManager + delivery PendingIntents
      DefaultSmsRole.kt        RoleManager.ROLE_SMS helper
      TelephonyEventBus.kt     App-wide SharedFlow<TelephonyEvent>
      SmsDeliverReceiver.kt    SMS_DELIVER → inbox row + emit
      WapPushDeliverReceiver.kt MMS notification (M3 fills body)
      HeadlessSmsSendService.kt RESPOND_VIA_MESSAGE (required for role)
      SmsSentReceiver.kt       Internal sent callback
      SmsDeliveredReceiver.kt  Internal delivered callback
      model/Conversation.kt    Plain Kotlin data classes
      model/SmsMessage.kt
      ConversationRepository.kt + Telephony + Fake impls
    contacts/ContactsResolver.kt
    role/RoleStateRepository.kt
    backup/                    M5
  di/                          Hilt modules
  ui/
    theme/                     Color, Type, Theme, Spacing
    nav/MessagesNav.kt         NavHost + redirect logic
    conversations/             list screen + viewmodel + tile + avatar + menu
    thread/  compose/  search/ M2
    blocking/  scheduling/     M3 / M5
    backup/  export/  applock/ M5
    settings/  premium/        present in M0/M4
    onboarding/                3-page + role request
  monetization/
    ads/                       AdsController, placements/{native_list, app_open, ...}
    iap/                       EntitlementController, paywall, products
```

### 7.3 Data strategy
- **Source of truth = Android Telephony provider** (so switching default apps never loses data). Drift DB is a *mirror/index* for FTS search, plus app-only tables (scheduled, starred, pins, locks, settings).
- Initial sync: paged import with progress UI; incremental via ContentObserver → channel event stream.
- Scheduled send: row in Drift + exact alarm; on fire, native receiver sends even if app is dead, posts result notification.
- Backup format: zip{ messages.json (canonical schema), media/, manifest.json } → AES-256-GCM → Drive AppData or SAF location. Versioned schema for forward-compat restore.

### 7.4 Required AndroidManifest highlights
```xml
SEND_SMS, RECEIVE_SMS, READ_SMS, RECEIVE_MMS, RECEIVE_WAP_PUSH,
READ_CONTACTS, POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, FOREGROUND_SERVICE
<!-- Default-handler role requirements: -->
SMS_DELIVER receiver · WAP_PUSH_DELIVER receiver ·
ACTION_SENDTO activity (sms:/smsto:/mms:/mmsto:) ·
HeadlessSmsSendService (ACTION_RESPOND_VIA_MESSAGE)
```

### 7.5 Performance budgets (CI-enforced where possible)
- Cold start → interactive list: < 1.2s mid-range device (Moto G class)
- Thread open with 5k messages: < 300ms first frame (paginate!)
- Jank: 0 frames > 32ms on list scroll in profile mode
- APK/AAB: < 25MB download size
- Ads SDK init deferred ≥ first frame + 2s

---

## 8. Build Plan for Claude Code (milestones)

> Work milestone-by-milestone. Each milestone ends with `flutter analyze` clean, tests passing, and a runnable build.

**M0 — Scaffold (day 1):** Flutter project, theming/tokens (§5.2), go_router shell, Riverpod setup, CI lint config, fake data layer so all UI is buildable without a device.
**M1 — Telephony bridge:** Kotlin channel layer (§7.2 native files), role request flow, read conversations/messages, send SMS with delivery reports, ContentObserver stream. Integration-test on device/emulator with telephony.
**M2 — Core UI:** Conversation list + Thread + Compose + Search against real data. Notifications with inline reply. Dual-SIM. This is the make-or-break milestone for feel — apply §5 specs precisely.
**M3 — Utility features:** Blocking, archive, pin, star, MMS receive/send, settings.
**M4 — Monetization rails:** EntitlementController + paywalls + Play Billing products; AdsController with all caps as testable pure functions; UMP consent; native list ad component (build the "Sponsored tile" to spec). Ship v1.0 to closed testing **with ads OFF by remote config**.
**M5 — Scheduling + Backup/Export + App lock** (P1 set). Turn ads ON gradually via Remote Config (10% → 50% → 100%) while watching ratings and D1/D7.
**M6 — Polish & launch:** empty states, animations, 200ms transitions, Crashlytics zero-crash pass, Data Safety form, store listing per §9.

Testing: unit tests for AdRules/frequency caps, scheduling logic, backup encrypt/decrypt round-trip; widget tests for list/thread; one integration test for send/receive on emulator.

---

## 9. Growth & ASO (summary)

- **Title (Play):** `Messages: SMS Text App` (avoid "Launcher" — wrong intent + policy smell). Short desc: "Private SMS messenger with scheduling, backup & themes. Fast, clean, secure."
- **Keyword clusters:** sms app, text messages, messaging app, default sms, message scheduler, sms backup, block texts, private messages.
- **Localize listing** into the proven 15-locale stack (ES, PT-BR, AR, HI, ID, etc.) at launch; localize app strings for top 8 by v1.2.
- **Review prompt:** in-app review API ONLY after positive moments (10th sent message, successful backup, theme applied) and never within 24h of an ad impression. Target 4.7★.
- **Differentiation messaging vs competitor:** "No spam ads in your inbox. No launcher bloat. Just messages."
- Screenshots: 8 frames, device-framed, one benefit per frame, first three = clean inbox / privacy lock / scheduling.

---

## 10. Definition of Done (v1.0 public)
- [ ] Role-based default SMS handler works across Android 10–15
- [ ] All P0 features pass manual QA matrix (dual-SIM, RTL, dark mode, font-scale 200%)
- [ ] Zero ads on prohibited surfaces (automated check on AdRules + manual sweep)
- [ ] Crash-free sessions ≥ 99.7% in closed testing
- [ ] Cold start budget met on Moto G-class device
- [ ] Data Safety form = "no data shared", backups encrypted, privacy policy live
- [ ] Paywall + restore purchases verified with license testers
- [ ] Store listing localized, 15 locales
