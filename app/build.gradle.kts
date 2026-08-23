import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.rahulgorai.remiit"
    compileSdk = 37

    // Recorded into BuildConfig so an on-device "about" screen can name the
    // exact commit an APK came from. `rev-list -n 1 HEAD` rather than
    // `rev-parse HEAD` so a detached tag checkout (what CI does) still resolves.
    //
    // Uses providers.exec, not ProcessBuilder: Gradle 9's configuration cache
    // rejects processes spawned directly at configuration time. Guarded on .git
    // existing so source-zip builds degrade to "unknown" instead of failing.
    val gitCommitHash: String = if (File(rootDir, ".git").exists()) {
        val result = providers.exec {
            commandLine("git", "rev-list", "-n", "1", "HEAD")
            isIgnoreExitValue = true
        }
        val exitCode = result.result.get().exitValue
        if (exitCode == 0) {
            result.standardOutput.asText.get().trim().ifEmpty { "unknown" }
        } else {
            "unknown"
        }
    } else {
        "unknown"
    }

    // ------------------------------------------------------------------------
    // Version resolution
    //   CI:    APP_VERSION is set by the release workflow from the git tag.
    //   Local: falls back to libs.versions.toml, so Studio builds are unaffected.
    // ------------------------------------------------------------------------
    val resolvedVersionName: String = System.getenv("APP_VERSION")
        ?.takeIf { it.isNotBlank() }
        ?: libs.versions.appVersion.get()

    // "2.3.1" -> 20301. Keeps versionCode monotonic without manual edits.
    val resolvedVersionCode: Int = run {
        val parts = resolvedVersionName.split(".")
        if (parts.size == 3) {
            try {
                parts[0].toInt() * 10000 + parts[1].toInt() * 100 + parts[2].toInt()
            } catch (_: NumberFormatException) {
                libs.versions.appVersionCode.get().toInt()
            }
        } else {
            libs.versions.appVersionCode.get().toInt()
        }
    }

    defaultConfig {
        applicationId = "com.rahulgorai.remiit"
        minSdk = 33
        targetSdk = 37
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName

        buildConfigField("String", "GIT_COMMIT_HASH", "\"$gitCommitHash\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    val localProperties = Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

    // CI supplies KEYSTORE_PATH from the base64-decoded secret; locally it comes
    // from local.properties. Resolved once here so the release buildType can
    // decide whether a signing config exists at all.
    val releaseKeystore: File? = (
        System.getenv("KEYSTORE_PATH")
            ?: localProperties.getProperty("RELEASE_STORE_FILE")
        )?.let(::file)?.takeIf { it.exists() }

    signingConfigs {
        // Registered only when a keystore is actually present. Registering it
        // unconditionally makes `assembleRelease` fail on any machine without
        // signing material, which would mean R8 and the ABI splits could not be
        // verified locally at all.
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 full mode is the default in AGP 8+. Everything this app reaches
            // reflectively — kotlinx.serialization serializers for the whole rule
            // model, Room entities, Workers, and the manifest-declared service and
            // receiver entry points — needs an explicit keep in proguard-rules.pro.
            // Without them the APK installs fine and silently stops firing rules.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Unsigned when no keystore is available. The APK still exercises
            // R8 and the ABI splits; it just cannot be installed until signed.
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    // Per-ABI APKs. There is no native code in the app today, so the two
    // outputs currently differ only in filename — the split is configured now
    // because the planned on-device AI runtime ships .so libraries, at which
    // point it starts saving real download size.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "META-INF/*.version"
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        // Release builds should not be blocked by lint, but regressions should
        // still be visible in the report.
        abortOnError = false
        warningsAsErrors = false
    }
}

// Room schemas are committed so migrations can be diffed in review.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.workmanager)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
