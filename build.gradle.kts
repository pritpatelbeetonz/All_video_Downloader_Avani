// Top-level — apply false here, modules apply for real.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
}

subprojects {
    if (name != "flutter" && name != "app") {
        evaluationDependsOn(":flutter")
    }

    // Fix Windows cross-drive path relativization issue:
    // Project is on drive F:, but Flutter pub cache plugins are on drive C:.
    // Flutter's module_plugin_loader redirected plugins' buildDir to F:\...\plugins_build_output,
    // which causes AGP's GenerateTestConfig to fail with:
    // "IllegalArgumentException: this and base files have different roots: F:\... and C:\..."
    if (project != rootProject && name != "app" && name != "flutter") {
        layout.buildDirectory.set(file("${projectDir}/build"))
    }
}


