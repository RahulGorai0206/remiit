// Top-level build file. Plugins are declared here with `apply false` so the
// version catalogue stays the single source of truth for versions, and applied
// in :app.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
}
