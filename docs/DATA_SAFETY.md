# Play Console — Data Safety form (v1.0)

Source-of-truth answers for the Play Console **Data safety** section, derived from
what the app actually does (build doc §2, §7.3). Keep this in sync with the code:
if a future release changes data flows (e.g. ads ramp on, Firebase Crashlytics is
added, Drive auto-backup ships), update this file **and** the Console form together.

> Play's definition of **"collected"** = transmitted off the device. **"Shared"** =
> transmitted to a third party. On-device access (reading SMS/contacts to function
> as the messaging app) is **not** "collection." Backups go to the *user's own*
> storage at the *user's* initiation — not to us, not to a third party.

---

## Summary answers (v1.0 — ads OFF, no Firebase)

| Question | Answer |
|---|---|
| Does your app collect or share any required user data types? | **No** |
| Is all collected data encrypted in transit? | N/A (nothing collected); user backups are AES‑256‑GCM encrypted at rest |
| Do you provide a way for users to request data deletion? | **Yes** — uninstall removes all app data; messages remain in the system SMS store |

**Data shared with third parties: NONE.** This is the headline claim (§2.2) and it
is true at v1.0.

---

## Why each sensitive permission exists (on-device only)

| Data / permission | Why the app needs it | Leaves the device? |
|---|---|---|
| SMS (`READ_SMS`/`SEND_SMS`/`RECEIVE_SMS`/`RECEIVE_MMS`) | The app is the **default SMS handler**; it must read, send, and receive messages. | **No** — stays in the system Telephony provider + an on-device index. |
| Contacts (`READ_CONTACTS`) | Resolve names/photos for numbers in the UI. | **No.** |
| Phone (`READ_PHONE_STATE`/`READ_PHONE_NUMBERS`) | Dual‑SIM selection + the default-handler role. | **No.** |
| Notifications, alarms, biometrics | Message alerts, scheduled send, app lock. | **No.** |

No analytics event ever contains message content, phone numbers, or contact names
(§2.2). The local crash reporter writes **stack traces + build/device metadata only**
— never message content — to app-private storage.

---

## User backup & export (user-initiated, not "sharing")

- **Local backup/export** writes to a file the user picks via the system file
  picker (SAF). Backups are **AES‑256‑GCM encrypted** with the user's passphrase;
  exports (TXT/CSV/PDF) are plain by the user's choice.
- This is the user moving **their own data** to **their own** location. It is not
  developer collection and not third-party sharing. Disclose it as a user-controlled
  feature, not as data collection.

---

## Security practices to declare

- [x] Data is encrypted at rest for backups (AES‑256‑GCM, key derived from the
      user's passphrase via PBKDF2).
- [x] Users can request that data be deleted (uninstall clears app data; the app
      does not retain copies off-device).
- [x] Committed to the **Play Families** policy: rated 12+, not enrolled in
      Families, AdMob content rating capped at T.

---

## ⚠️ Update this form when these ship (currently OFF / not wired)

1. **Ads ramp on (AdMob).** When `adsEnabled` is turned on via remote config,
   Google/AdMob collects **device or other IDs** (and possibly approximate
   location) for advertising. You must then declare:
   - *Device or other IDs* — **collected & shared**, purpose: Advertising.
   - UMP consent is already gathered before any ad request.
2. **Firebase Crashlytics** (replaces the local crash reporter). Then declare:
   - *App activity / Crash logs and diagnostics* — **collected**, purpose:
     Analytics / app functionality. (Still no message content/PII.)
3. **Google Drive auto-backup (Premium).** Still the user's own Drive account, so
   not third-party sharing — but disclose the Drive integration.
4. **Tenor GIF search (WIRED — works via the public demo key by default).** When a
   user searches for a GIF, the **search term is sent to Google/Tenor** and the
   chosen GIF is fetched from Tenor's CDN. No message content, numbers, or contact
   names are sent — only what the user types into the GIF search box. If you ship
   GIF search enabled, declare under **App activity → Search history / Other
   user-generated content**: *shared with Google (Tenor)* for the GIF feature.
   (To disable: leave it out of the attach sheet, or the feature degrades to the
   local "Choose from device" picker which sends nothing off-device.)

---

## Cross-checks before submitting

- [ ] Listing claim "No data shared with third parties" matches this form.
- [ ] Privacy policy URL is live and says the same thing.
- [ ] AdMob is confirmed OFF in remote config for the v1.0 review build.
- [ ] No SDK in the build silently collects data (audit: AdMob present but gated;
      UMP; no Analytics SDK; no Crashlytics yet).
