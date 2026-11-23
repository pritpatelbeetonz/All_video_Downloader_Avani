# Putting another app into the launcher (page 0)

The launcher has 3 pages: **0 = your app (the "slot")**, 1 = Home, 2 = Dashboard.
Only page 0 ever changes. You never edit the launcher itself.

```
app/src/main/kotlin/app/messages/feature/
    LauncherFeature.kt   the contract an app must implement
    LauncherSlot.kt      THE ONE LINE:  val feature = VideoDownloaderFeature
```

## 1. When to run the skill

Run **`/add-launcher-feature`** in Claude Code, from the repo root.

| You want to... | Run |
|---|---|
| Put a Flutter app on page 0 (replaces the current Flutter app) | `/add-launcher-feature F:\apps\FlutterProject` |
| Put an Android project on page 0 | `/add-launcher-feature F:\apps\AndroidProject` |
| Same, but you already copied the files into the repo yourself | `/add-launcher-feature` (no path) |
| Take the current app off page 0 | `/add-launcher-feature` then "remove the integrated app" |

**Way 2 (path only)**: it detects the type, copies, converts, wires and switches the slot by itself.
**Way 1 (manual copy)**: you put the files in place first (`features/<name>/` for Android, the module folder at the repo root for Flutter), then run the skill.
It only asks a question when it is blocked (unknown path, Flutter SDK missing, sensitive permission).

When the migration is done the skill builds the project, runs `flutter analyze` / compile / lint / tests, fixes errors (max 5 rounds per stage)
and prints a short report. Builds happen **only** inside this skill; in normal work Claude does not build.

## 2. Flutter app

### Where every file goes

```
<repo root>/
  video_downloader/            <- the Flutter MODULE (name it after your app)
    pubspec.yaml               <- must be a module:  flutter: { module: { androidPackage: ... } }
    lib/main.dart              <- your Dart code (entry point `main()`)
    lib/for_ads/ng/ng_ads.dart <- ads: use THIS, never google_mobile_ads
    .android/include_flutter.groovy   <- generated; settings.gradle.kts includes it
  settings.gradle.kts          <- last line:  apply(from = File(settingsDir, "<module>/.android/include_flutter.groovy"))
  app/build.gradle.kts         <- implementation(project(":flutter"))
  app/src/main/kotlin/app/messages/ui/
    launcher/downloader/
        VideoDownloaderFeature.kt             <- implements LauncherFeature (copy + rename)
        VideoDownloaderContainerFragment.kt   <- shows the Flutter engine on page 0
    videodownloader/
        VideoDownloaderEngineHelper.kt        <- MethodChannels + ad factories (copy + rename)
```

**Only ONE Flutter module can be linked** (`include_flutter.groovy` always creates the project
`:flutter`). To swap apps, replace the module folder (or point `settings.gradle.kts` at the new
folder). Do not add a second one next to it.

### Steps

1. Create/convert the Flutter project as a module: `flutter create -t module <name>`
   (an existing app: copy its `lib/`, `assets/`, `pubspec.yaml` deps into the module).
2. Put the module folder at the **repo root**. Run `flutter pub get` inside it.
3. `settings.gradle.kts`: point the `apply(from = ...)` line at the new module's
   `.android/include_flutter.groovy`.
4. Run **`/add-launcher-feature`** (Flutter). It copies the three Kotlin files above under the
   new name, sets a new `ENGINE_ID`, and edits `LauncherSlot.kt`.
5. Ads in Dart: import `lib/for_ads/ng/ng_ads.dart`. If the old app used `google_mobile_ads`,
   replace the imports and delete that dependency from `pubspec.yaml`
   (it clashes with the Next-Gen SDK and breaks the build).
6. Native <-> Dart contract (channel `nativeChannel`). Your Dart code may call:
   `setToast`, `getAdsConfig`, `isDownloaderVisible`, `acquireWakeLock`, `releaseWakeLock`,
   `logNative`, `saveToDownloads`, `openNativeLanguageScreen`.
   Native calls into Dart: `syncAdsConfig`, `setDownloaderVisible`, `changeLanguage`.
   Keep the ones your app needs, delete the rest in the helper.
7. Native ad templates for Flutter use the factory ids `smallNativeAds`, `bigNativeAds`,
   `fullNativeAds` (layouts `small_template.xml`, `big_template.xml`, `full_template.xml`).
8. You build: `Build > Make Project`, then run. Check logcat for `[ANR Warning] onLayout`
   (should not appear; the engine boots after the flow screens, not at app start).

---

## 3. Existing Android project

### Where it goes

```
<repo root>/
  features/<name>/             <- the old project, converted to a LIBRARY module
    build.gradle.kts           <- com.android.library, namespace "app.messages.features.<name>"
    src/main/...               <- its code + res (res files prefixed  <name>_ )
  settings.gradle.kts          <- include(":feature_<name>")
                                  project(":feature_<name>").projectDir = File(settingsDir, "features/<name>")
  app/build.gradle.kts         <- implementation(project(":feature_<name>"))
```

Tiny feature (1-3 screens, no new libraries)? Put the code in
`app/src/main/kotlin/app/messages/features/<name>/` instead and prefix its resources.
**Never** put anything in `app/.../ui/launcher/` (that is the launcher itself).

### Steps

1. Copy the old app's `app` module into `features/<name>/` (only `src/`, build file, proguard file).
2. `build.gradle.kts`: `com.android.application` -> `com.android.library`; delete `applicationId`,
   versionCode/Name, signing, google-services; set `namespace`; add `resourcePrefix = "<name>_"`;
   same `compileSdk/minSdk` and library versions as `:app`; proguard -> `consumerProguardFiles`.
3. The launcher needs a **Fragment** on page 0: move the old main Activity's UI into
   `<Name>Fragment`. Other Activities can stay (start them with an Intent).
4. Manifest: remove the `MAIN/LAUNCHER` intent filter (else a second app icon) and the
   `<application android:name>`.
5. Old `Application.onCreate` code -> `warmUp(app)` in the feature class.
6. Remove `@HiltAndroidApp` (the launcher's `Controller` is already the app). Keep
   `@AndroidEntryPoint` on its fragments.
7. Ads: use the launcher's Next-Gen ads (`app.messages.ads.ng.*`). Do not add `play-services-ads`.
8. Run **`/add-launcher-feature`** (Android project). It creates `<Name>Feature` and edits `LauncherSlot.kt`.
9. You build and run.

---

## 4. How to remove the old app

### First, always: empty the slot (2 minutes, safe)

`feature/LauncherSlot.kt` -> `val feature: LauncherFeature = NoFeature`

Page 0 becomes blank, the left edge tab hides, nothing heavy starts. Build and confirm the launcher
still works. Everything below is optional clean-up you can do later.

### Then, if the old app was the Flutter downloader, delete in this order

| Step | Delete / change |
|---|---|
| 1 | Folder `video_downloader/` |
| 2 | `settings.gradle.kts`: the `include_flutter.groovy` line and the flutter maven repo |
| 3 | `app/build.gradle.kts`: `implementation(project(":flutter"))` and any Flutter-only rules |
| 4 | `app/src/main/kotlin/app/messages/ui/launcher/downloader/` and `ui/videodownloader/` |
| 5 | `AndroidManifest.xml`: `VideoDownloaderFlutterActivity` |
| 6 | `ads/ng/flutter/NgAdsFlutterBridge.kt` and the Flutter native-ad layouts `small_template/big_template/full_template.xml` |
| 7 | `MessagesApplication.kt`: the Flutter engine code (`startInitialization`, `warmFlutterEngineWhenIdle`, `initFlutterEngine`, `ensureFlutterEngineReady`) |
| 8 | `proguard-rules.pro`: section "Flutter Engine & Media Plugins" |
| 9 | Search `flutter` / `VideoDownloader` in `app/src` and fix what is left |

### If the old app was an Android library module

1. Delete the line `implementation(project(":feature_<name>"))` from `app/build.gradle.kts`.
2. Delete its `include(...)` / `projectDir` lines from `settings.gradle.kts`.
3. Delete the `features/<name>/` folder.
4. Delete its `<Name>Feature` class.

### Replace instead of remove

Do not remove first. Run the skill for the new app: it switches `LauncherSlot.feature`
to the new one; then delete the old app with the lists above.

---

## 5. Quick checklist after any change

- [ ] `LauncherSlot.feature` points to the right feature (or `NoFeature`)
- [ ] App starts, Home opens, left edge tab shows the feature icon, tapping it opens page 0
- [ ] Swipe locked on page 0 only if the app has horizontal gestures
- [ ] No second app icon, no second ads SDK
- [ ] Logcat has no `[ANR Warning] onLayout time too long`
- [ ] No leftover references: search the old app's name in `app/src`

---

## 6. Giving this to another developer

Everything they need is **inside this repo** (commit it, they clone it):

```
.claude/skills/add-launcher-feature/     the skill you run:  /add-launcher-feature <path>
.claude/skills/nextgen-ads-migration/    ads reference the first skill uses (has reference/ code)
app/src/main/kotlin/app/messages/feature/   LauncherFeature.kt + LauncherSlot.kt (the slot)
docs/LAUNCHER_FEATURE_INTEGRATION.md     this guide
```
Claude Code loads project skills from `.claude/skills/` automatically when the repo is opened.
Do **not** rely on the user-level skills folder (`~/.claude/skills`): it only exists on your own PC.
To use the skill in a different repo, copy both skill folders and the two `feature/` files into it.
