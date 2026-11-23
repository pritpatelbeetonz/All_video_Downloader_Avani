---
name: add-launcher-feature
description: Path-driven integration of an app into this launcher's page-0 slot. Give it the PATH of a Flutter project or an Android project (e.g. "/add-launcher-feature F:\apps\CleanerApp") and it detects the type, copies it into the right folder, converts it, generates the feature class, wires settings/gradle/manifest, switches LauncherSlot, optionally removes the old app, and prints a report. Also use for "replace the video downloader with X", "remove the integrated app", "put this Flutter/Android app on page 0".
---

# add-launcher-feature (path in -> integrated app out)

Developer input is **just a path** (Flutter project or Android project) and optionally a name.
Everything below is done by you (Claude) without further questions unless a **STOP** is hit.

Architecture you must follow (never edit launcher core):
```
app/src/main/kotlin/app/messages/feature/LauncherFeature.kt   contract
app/src/main/kotlin/app/messages/feature/LauncherSlot.kt      ONE switch: val feature = ...   (+ NoFeature)
app/src/main/kotlin/app/messages/ui/launcher/downloader/VideoDownloaderFeature.kt   reference Flutter impl
docs/LAUNCHER_FEATURE_INTEGRATION.md                          human guide (keep in sync with this skill)
```
Launcher pages: 0 = slot, 1 = Home, 2 = Dashboard. Only page 0 ever changes.

## Hard rules
- **No builds while migrating** (`gradlew`, `flutter build`, `flutter run`, `assemble`). Allowed during Steps 1-3: `flutter pub get`, `flutter create --template=module`, git, file edits.
  **Builds/analysis are allowed ONLY in Step 5, after the migration is finished** (the developer explicitly asked for this). Never in other tasks.
- Never touch launcher core: `ui/launcher/{home,drawer,dashboard,settings}`, `LauncherHostFragment`, `AppDrawer*`.
  The only existing files you may edit: `feature/LauncherSlot.kt`, `settings.gradle.kts`, `app/build.gradle.kts`,
  `AndroidManifest.xml`, `proguard-rules.pro`, `strings.xml`, and (Flutter swap only) the downloader plumbing named below.
- Never add a second ads SDK. Ads = launcher Next-Gen SDK (`app.messages.ads.ng.*`). Flutter ads = `lib/for_ads/ng/ng_ads.dart`
  (channel `ng_ads`). Never `google_mobile_ads` / `play-services-ads` (the app excludes the classic SDK globally).
- Do not delete the old app unless the developer said so ("remove old", "replace and delete"). Default = **switch the slot, keep old code**.
- Work on a clean tree: run `git status --short`; if there are unrelated uncommitted changes, say so and continue only on new files + the allowed edits.

## Two ways to start (both end in the same steps)
- **Way 2 - path only:** developer runs `/add-launcher-feature <path>`; you copy everything yourself (this whole skill).
- **Way 1 - manual copy first:** developer already made `features/<name>/` (Android) or put the Flutter module folder at the repo root and
  then runs `/add-launcher-feature` (no path, or the folder as the path). Detect that the files are already inside the repo, skip the copy
  steps (A2.1 / F1.1-F1.2), and continue with the rest.

## Step 1 - Detect (from the given path `P`)
Run these (read-only) and decide the type:
```
ls P ; ls P/android P/ios P/lib P/.android 2>/dev/null
grep -n "flutter:" P/pubspec.yaml ; grep -n "module:" P/pubspec.yaml
ls P/settings.gradle* P/build.gradle* P/app/build.gradle* 2>/dev/null
grep -n "com.android.application\|com.android.library" P/app/build.gradle* P/build.gradle* 2>/dev/null
```
| Found at P | Type |
|---|---|
| `pubspec.yaml` with `flutter:` | **Flutter** (app if it has `android/` + `ios/`, module if `module:` block / `.android/`) |
| `settings.gradle(.kts)` + `app/` with `com.android.application` | **Android project** |
| only `build.gradle` with `com.android.library` | Android library (treat like Android project, skip step A3) |
| both pubspec and gradle at P | Flutter (the `android/` inside is just its host) |
Name = PascalCase of the folder name (`cleaner_app` -> `CleanerApp`), id = snake_case. Tell the developer the detected type and name in one line, then go on.
If P is neither: **STOP** and say what is missing.

Then inspect what the app needs, to report later: permissions (`AndroidManifest.xml`), plugins/deps (`pubspec.yaml` / `build.gradle`),
Firebase/ads usage (`grep -rn "google_mobile_ads\|play-services-ads\|AdView\|MobileAds" P`), Application class, launcher activity, min/compileSdk.

## Flutter pipeline (type = Flutter)

**Only one Flutter module can be linked** (`include_flutter.groovy` always creates project `:flutter`). So Flutter is always a **replace of the current Flutter module**.
Existing plumbing is reused (do NOT regenerate it):
`ui/launcher/downloader/VideoDownloaderFeature.kt`, `VideoDownloaderContainerFragment.kt`,
`ui/videodownloader/VideoDownloaderEngineHelper.kt`, engine boot in `MessagesApplication.kt`, `ads/ng/flutter/NgAdsFlutterBridge.kt`.

### F1. Place the module
1. Target folder: `<repo root>/<id>/` (e.g. `cleaner_app/`). Keep `video_downloader/` untouched until the swap works.
2. Copy: `lib/`, `assets/`, `pubspec.yaml`, `analysis_options.yaml`, `test/` (skip `build/`, `.dart_tool/`, `ios/`, `.idea/`).
3. If it is a Flutter **app** (no `module:` block): add to `pubspec.yaml` under `flutter:`
   ```yaml
   module:
     androidX: true
     androidPackage: com.<id>.module
   ```
   Move its `android/` folder to `P_android_backup/` (a module generates `.android/`). Keep a note of permissions/meta-data/`<queries>` from its
   `android/app/src/main/AndroidManifest.xml` -> they go to the host manifest in F4.
4. Fix pubspec: remove `google_mobile_ads` / `gma_mediation_*`; `name:` stays unique; align plugin versions with `video_downloader/pubspec.lock`
   when the same plugin exists (avoid duplicate native deps).
5. Run `flutter pub get` inside the new folder (generates `.android/include_flutter.groovy`). If Flutter is not installed: STOP and tell the developer to run it.

### F2. Dart side - follow the native contract
Channel `nativeChannel` (see `VideoDownloaderEngineHelper.initMethodChannel`). Dart may call: `setToast`, `getAdsConfig`, `isDownloaderVisible`,
`acquireWakeLock`, `releaseWakeLock`, `logNative`, `saveToDownloads`, `openNativeLanguageScreen`. Native calls Dart: `syncAdsConfig`,
`setDownloaderVisible`, `changeLanguage`.
- Ads: use the bundled skill **`nextgen-ads-migration`** as the reference (`.claude/skills/nextgen-ads-migration/SKILL.md`, shipped in this repo), sections 4.4-4.5.
  Copy `video_downloader/lib/for_ads/ng/ng_ads.dart` if that folder still exists, else `.claude/skills/nextgen-ads-migration/reference/flutter/ng_ads.dart`,
  to the new module's `lib/for_ads/ng/ng_ads.dart`; change `import 'package:google_mobile_ads/google_mobile_ads.dart';` to
  `import 'package:<module>/for_ads/ng/ng_ads.dart';`; add any missing API the app uses to the shim; remove `google_mobile_ads`/`gma_mediation_*` from pubspec.
  **Skip** the Android half of that skill (Gradle excludes, `NgAdsFlutterBridge`, `AdsSdkInit`, `NextGenAds`): the launcher already has them under `app.messages.ads.ng.*`.
- Ad native-factory ids available: `smallNativeAds`, `bigNativeAds`, `fullNativeAds`.
- If the Dart app has its own `MethodChannel` names, keep them and add the handler in `VideoDownloaderEngineHelper.initMethodChannel`
  (copy the `when` branch style). Remove handlers the new app does not use only if they are clearly downloader-only (`saveToDownloads`, wake locks).
- `lib/main.dart` must start with `WidgetsFlutterBinding.ensureInitialized()` and not call `runApp` twice; no `SystemChrome` orientation lock.

### F3. Wire gradle
- `settings.gradle.kts`: change the `apply(from = File(settingsDir, "video_downloader/.android/include_flutter.groovy"))` path to the new folder.
- `app/build.gradle.kts`: `implementation(project(":flutter"))` stays. Add native deps the new plugins need only if the `.android` build asks.
- `proguard-rules.pro` Flutter section stays; add `-keep` lines only for plugins that need them.

### F4. Native glue (minimal edits)
- Host manifest: add permissions/`<queries>`/meta-data collected in F1.3 (dedupe).
- If the new app's name differs, keep class names `VideoDownloader*` (internal) but change user-visible text: `edge_tab_video_downloader` string
  -> new label, and `VideoDownloaderFeature.iconRes` -> new icon if given.
- `VideoDownloaderEngineHelper.ENGINE_ID` stays (engine cache key, not user-visible).
- Optional cleanup (only if asked): rename `VideoDownloader*` -> `<Name>*` with a whole-word search/replace across `app/src` and the manifest, one commit.

### F5. Old app
Default: keep `video_downloader/` folder (unlinked). If told to remove: delete it and its generated `.iml`; leave plumbing in place (it is now the new app's).

## Android pipeline (type = Android project)

### A1. Decide folder
- Small (<= 3 screens, no new libraries, nothing but standard androidx): copy sources into
  `app/src/main/kotlin/app/messages/features/<id>/` and res into `app/src/main/res/` with prefix `<id>_`.
- Everything else (default): library module `features/<id>/` at repo root + `:feature_<id>`.
Say which one you chose and why (one line).

### A2. Create the library module (default)
1. `features/<id>/` <- copy `P/app/src/` (main only; skip androidTest). Its `proguard-rules.pro` becomes `features/<id>/consumer-rules.pro`
   (create the file even if the old app had none, so the `consumerProguardFiles("consumer-rules.pro")` line never fails).
   If the developer already copied `src/` + the proguard file into `features/<name>/` by hand ("Way 1"), skip the copy and start from step 2.
2. `features/<id>/build.gradle.kts` (write it; do not copy the old one):
   ```kotlin
   plugins {
       alias(libs.plugins.android.library)
       alias(libs.plugins.kotlin.android)
       // add ksp / hilt only if the old app used them
   }
   android {
       namespace = "app.messages.features.<id>"
       compileSdk = <same as :app>
       defaultConfig { minSdk = <same as :app>; consumerProguardFiles("consumer-rules.pro") }
       resourcePrefix = "<id>_"
       compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
       kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
       buildFeatures { viewBinding = true /* , dataBinding = true  only if the old app used it */ }
   }
   dependencies { /* copy only the old app's real dependencies, versions aligned to gradle/libs.versions.toml */ }
   ```
   Check `app/build.gradle.kts` for the real `compileSdk/minSdk/jvmTarget` and copy those values (today: compileSdk 36, minSdk 27, Java/Kotlin 17).
3. `settings.gradle.kts`: add
   ```kotlin
   include(":feature_<id>")
   project(":feature_<id>").projectDir = File(settingsDir, "features/<id>")
   ```
   `app/build.gradle.kts`: `implementation(project(":feature_<id>"))`.
4. Rename resource clashes: for every layout/drawable/string/color/style/id in the module that exists in `:app`, prefix with `<id>_`
   and update references (`R.layout.x`, `@layout/x`, binding class names change with the layout name).
5. Manifest (`features/<id>/src/main/AndroidManifest.xml`): remove `<application android:name>`, `android:icon/roundIcon/label/theme` on application,
   every `MAIN/LAUNCHER` intent-filter, `@HiltAndroidApp`. Keep its other Activities/services/receivers/permissions (set `android:exported` explicitly).
   If the old launcher Activity was the entry screen, move its UI into a Fragment (A3).

### A3. Entry fragment (page 0 needs a Fragment)
- If the old entry is a Fragment/NavHost: reuse it.
- If it is an Activity: create `<Name>Fragment` with the same layout/logic (move `onCreate` setup to `onViewCreated`, replace `this` with `requireContext()`,
  `finish()` with `requireActivity().onBackPressedDispatcher.onBackPressed()`, `startActivityForResult` with Activity Result API). Keep the old Activity
  for secondary flows only.
- Needs its own horizontal gestures (map, pager, webview, reels, drawing)? -> `lockPagerSwipeOnPage = true`.

### A4. Feature class (always in `:app`, because the `LauncherFeature` contract lives there)
File: `app/src/main/kotlin/app/messages/features/<id>/<Name>Feature.kt`
```kotlin
package app.messages.features.<id>

import app.messages.R   // the label/icon live in :app resources

object <Name>Feature : app.messages.feature.LauncherFeature {
    override val id = "<id>"
    override val labelRes = R.string.<id>_edge_tab_label
    override val iconRes = R.mipmap.ic_launcher_round          // or the app's own round icon copied as <id>_icon
    override val lockPagerSwipeOnPage = false
    override fun createFragment(): androidx.fragment.app.Fragment = <Name>Fragment()
    override fun warmUp(app: android.app.Application) { /* old Application.onCreate work, idempotent, light */ }
}
```
It references the library's `<Name>Fragment` (`:app` depends on `:feature_<id>`, never the other way round). Add `<id>_edge_tab_label` to
`app/src/main/res/values/strings.xml`. For the small (in-`:app`) option the fragment lives in the same package.

### A5. Old Application code, DI, ads
- Old `Application.onCreate` -> `warmUp(app)` (never at process start).
- Hilt: library needs the Hilt plugin + `ksp(hilt-compiler)` if it has `@AndroidEntryPoint`/`@HiltViewModel`; remove `@HiltAndroidApp`.
  Non-Hilt singletons: lazy init inside `warmUp`.
- Ads: if the feature uses classic AdMob (`com.google.android.gms.ads.*`), use the bundled skill **`nextgen-ads-migration`** (`.claude/skills/nextgen-ads-migration/SKILL.md`) as the reference:
  apply its section 3 steps 3-6 (mechanical renames to the Next-Gen classes, `NativeAdView`/`MediaView` layout tags, `bindNative`, enum `.code.value`,
  destroy natives, ad-activity class-name guards) to the feature's sources. Use the helpers already in the app (`app.messages.ads.ng.*` - `NextGenAds`,
  `AdsSdkInit`) instead of copying `reference/kotlin/*`. **Skip** its Gradle section (the host already excludes `play-services-ads`).
  Remove `play-services-ads` from the feature's `build.gradle`. Ad unit ids / Remote Config JSON go through `AdsConfigManager`.
  After the renames `grep -rn "com.google.android.gms.ads" features/<id>` must only show `APPLICATION_ID`, `AdvertisingIdClient`, comments.
- Firebase: no second `google-services.json`; the host's is used. Analytics events can stay.

### A6. Old app
Default: keep (the new feature replaces it in the slot only). If told to remove: follow `docs/LAUNCHER_FEATURE_INTEGRATION.md` section 4.

## Step 2 - Switch the slot (both types)
Edit `app/src/main/kotlin/app/messages/feature/LauncherSlot.kt`:
`val feature: LauncherFeature = <Name>Feature` (+ import). Flutter swap keeps `VideoDownloaderFeature`.

## Step 3 - Self-check (static, no builds)
1. Grep: no `google_mobile_ads`, no `play-services-ads`, no second `@HiltAndroidApp`, no `MAIN/LAUNCHER` filter in feature manifests.
2. Every `include`/`projectDir` path exists; `LauncherSlot` import resolves; each new layout name is unique (`ls app/src/main/res/layout | sort | uniq -d` equivalent).
3. Dangling check for Android: all `R.` references in the module resolve to prefixed resources.
4. Flutter: the new module's `.android/include_flutter.groovy` exists and `settings.gradle.kts` points to it; `pubspec.yaml` has the `module:` block and no `google_mobile_ads`.
5. `git status --short` to list what changed.

## Step 4 - Report (print after Step 5 finishes; short)
Print: detected type + name, folder created, files changed (bullets), what was NOT converted and why, ads/permissions merged, old app status
(kept/removed), and the exact next step: **"Run the app on a device, check logcat for `[ANR Warning] onLayout`, tap the left edge tab to open page 0."**
If Step 5 could not reach all-PASS, list the remaining errors with file:line and what was tried.

## Step 5 - Verify: build, analyze, fix (only when Steps 1-3 are complete)
Goal: leave the project compiling. Keep token cost low: **redirect all output to a file** in the scratchpad and read only the errors.
On Windows use Git Bash for `./gradlew` (PowerShell: `.\gradlew.bat`).
```
LOG=<scratchpad>/verify_N.log
./gradlew :app:compileDebugKotlin  > $LOG 2>&1 ; grep -nE "^e: |error:|FAILED|What went wrong|Could not resolve|Execution failed" $LOG | head -40
```
Run in this order, stop at the first failing stage, fix, rerun that stage (max **5 fix rounds per stage**, then STOP and report):
1. **Flutter analyze** (Flutter type only): `cd <module> && flutter analyze > $LOG 2>&1` - fix `error` lines (warnings/infos are reported, not fixed).
2. **Kotlin compile**: `./gradlew :app:compileDebugKotlin` (and `:feature_<id>:compileDebugKotlin` for a library module).
3. **Debug build**: `./gradlew :app:assembleDebug` - catches manifest merge, resource clashes, duplicate classes, missing transitive deps.
4. **Lint (new code only)**: `./gradlew :app:lintDebug` - fix only issues in files this skill created/edited.
5. **Unit tests**: `./gradlew :app:testDebugUnitTest` (fix tests broken by the migration; do not delete tests unless the code they cover was removed on purpose).
6. **R8 check (optional, ask once)**: `./gradlew :app:minifyReleaseWithR8` - this project has hit a release-only `VerifyError`; fix with `-keep` rules for the
   package named in the error (see `proguard-rules.pro`).
Typical fixes (apply the smallest one): `Unresolved reference` -> add the missing library / import; `Duplicate class` -> exclude the transitive dep
(never add `play-services-ads`); `Manifest merger failed` -> add `tools:replace` / remove the duplicate entry; `resource already defined` -> prefix with `<id>_`;
`Could not resolve` -> align version with `gradle/libs.versions.toml` or add the repo in `settings.gradle.kts`; Flutter `plugin requires` -> align `pubspec` versions.
Never "fix" an error by deleting launcher code, disabling lint globally, or lowering `minSdk`/`compileSdk`.
After every stage print one line: `stage - PASS|FAIL(n errors fixed)`. Record anything you learned that was not in this skill under
"Lessons" below (append a dated bullet) so the next run avoids it.

## Lessons (append here after each real run)
- (none yet)

## Remove only (no path given): "remove the integrated app"
1. `LauncherSlot.feature = NoFeature` (page 0 blank, left tab hidden). Tell the developer to build.
2. Only if asked to delete code, use `docs/LAUNCHER_FEATURE_INTEGRATION.md` section 4 (Flutter list or Android-module list) and finish with a search for
   `flutter` / `VideoDownloader` / the old name in `app/src`.

## STOP conditions (ask the developer, one short question)
Path missing/unknown type; Flutter SDK absent for `pub get`; two different `applicationId`/package
conflicts you cannot rename; the app needs a permission that is sensitive (SMS, call log, accessibility) - Play policy, confirm first.
