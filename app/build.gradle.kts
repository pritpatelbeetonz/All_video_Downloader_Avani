plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

android {
    namespace = "app.messages"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.test.app.testfeature.apps"
        // Android 7+ floor. RoleManager.ROLE_SMS (default-handler) requires API 29+;
        // 24–28 falls back to read-only mode (banner: "Set as default to send messages").
        minSdk = 27
        targetSdk = 36
        versionCode = 12
        versionName = "12.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Tenor (Google) GIF search key. Set via `-PTENOR_API_KEY=...` or in
        // gradle.properties. When blank, TenorClient falls back to the public
        // v1 demo key so search still works out of the box for testing.
        buildConfigField(
            "String",
            "TENOR_API_KEY",
            "\"${project.findProperty("TENOR_API_KEY") ?: ""}\"",
        )
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // TODO: real signing config before first Play upload.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-Xskip-metadata-version-check",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }

    // Export Room schemas so future migrations can be diffed/tested.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            kotlin.srcDirs("src/test/kotlin")
        }
        getByName("androidTest") {
            kotlin.srcDirs("src/androidTest/kotlin")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":flutter"))
    implementation(libs.androidx.core.ktx)
    implementation("androidx.emoji2:emoji2-emojipicker:1.5.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.gson)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // MMS PDU parse/compose + WAP-push download/persist (§F5 receive path).
    implementation(libs.klinker.smsmms)

    // Monetization (§6.2) — gated OFF by default via AdsController.
    implementation(libs.ump)

    // Firebase (Remote Config and Analytics)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)
    implementation(libs.firebase.analytics)

    // For DynamicColors.applyToActivitiesIfAvailable() — also pulls Material You themes.
    implementation(libs.google.material)

    // App lock (§F16): BiometricPrompt + device-credential fallback.
    implementation(libs.androidx.biometric)

    // UI Libraries from Reference App for launcher and call-end overlays
    implementation(libs.glide)
    ksp("com.github.bumptech.glide:ksp:4.16.0")
    implementation(libs.sdp)
    implementation(libs.ssp)
    implementation(libs.lottie)
    implementation(libs.blurview)
    implementation(libs.shimmer)
    implementation(libs.play.services.ads)
    implementation(libs.billing.ktx)
    implementation(libs.onesignal)
    implementation(libs.facebook.core)
    implementation(libs.meta.adapter)
    implementation(libs.facebook.audience.network)


    implementation("androidx.navigation:navigation-fragment-ktx:2.8.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.5")


    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    // In-memory SQLite (JVM) so Room migrations can be tested without a device or
    // the instrumented room-testing artifact. Room's KSP compiler already pulls
    // sqlite-jdbc, so this resolves offline.
    testImplementation("org.xerial:sqlite-jdbc:3.41.2.2")
}
