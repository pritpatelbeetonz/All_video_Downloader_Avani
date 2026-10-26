# Default Android optimisations come from proguard-android-optimize.txt.

# ==============================================================================
# 1. Hilt, Dagger & ViewModels
# ==============================================================================
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }

# Keep all ViewModels to prevent R8 from merging or stripping classes used in Hilt's LazyClassKey multibinding.
# Merging ViewModels causes "java.lang.IllegalArgumentException: Multiple entries with same key" at runtime.
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Preserve field types for Dagger's LazyClassKeyMap to avoid duplicate key collisions in release builds (Dagger Issue #4323)
-keepclasseswithmembers,includedescriptorclasses class * {
    @dagger.internal.KeepFieldType <fields>;
}

# ==============================================================================
# 2. Compose runtime tooling
# ==============================================================================
-keep class androidx.compose.runtime.** { *; }

# ==============================================================================
# 3. Reflection over Telephony provider columns (we read by name from Cursor).
# ==============================================================================
-keepclassmembers class android.provider.Telephony$* { *; }

# ==============================================================================
# 4. Gson & JSON Models (AdsModel, ToolsConfig, RemoteFeedConfig, etc.)
# ==============================================================================
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}
-keep class com.google.gson.** { *; }
-keep class app.messages.ads.model.** { *; }
-keep class app.messages.config.** { *; }
-keep class app.messages.data.model.** { *; }

# ==============================================================================
# 5. Klinker MMS & SMS Library
# ==============================================================================
-keep class com.klinker.android.send_message.** { *; }
-dontwarn com.klinker.android.send_message.**

# ==============================================================================
# 6. Google Play Billing
# ==============================================================================
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# ==============================================================================
# 7. Warnings suppression
# ==============================================================================
# Facebook Infer nullability annotations. Compile-time only — the Meta Audience
# Network and Shimmer artifacts reference them but never ship them, so R8 reports
# them as missing. Nothing to keep; just stop the build failing on the warning.
-dontwarn com.facebook.infer.annotation.Nullsafe
-dontwarn com.facebook.infer.annotation.Nullsafe$Mode

# Google Play Core / SplitInstall / InAppReview optional classes referenced at runtime
-dontwarn com.google.android.play.core.**
-dontwarn com.google.android.play.core.splitcompat.SplitCompatApplication
-dontwarn com.google.android.play.core.splitinstall.**
-dontwarn com.google.android.play.core.tasks.**

# ==============================================================================
# 8. Flutter Embedding, Plugins & Media (VideoPlayer, ExoPlayer, VideoThumbnail)
# ==============================================================================
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.util.** { *; }
-keep class io.flutter.view.** { *; }
-keep class io.flutter.embedding.** { *; }
-keep class io.flutter.plugins.** { *; }

# Flutter video_player & ExoPlayer / Media3
-keep class io.flutter.plugins.videoplayer.** { *; }
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# Video Thumbnail Plugin
-keep class xyz.justsoft.video_thumbnail.** { *; }
-dontwarn xyz.justsoft.video_thumbnail.**

