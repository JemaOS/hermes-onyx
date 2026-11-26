import java.io.File
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * The release key lives outside the repository: set the environment variable
 * `ONYX_RELEASE_KEYSTORE` to the path of `release.properties`. Without this file the release build
 * stays unsigned rather than failing — the key does not belong to the project.
 */
val releaseKeystore = System.getenv("ONYX_RELEASE_KEYSTORE")?.let { file(it) }
    ?: file("${rootDir}/keystore.properties")

val keystoreProperties = Properties().apply {
    if (releaseKeystore.exists()) releaseKeystore.inputStream().use { load(it) }
}

/**
 * The voice service token, for the **test version**: it is read from the agent's `0600` file
 * (never from the repository), and becomes the default value of the "Voice token" setting (`BuildConfig`).
 * The file can be specified with `-Ponyx.voiceTokenFile=…`; if absent, the token is empty and the app
 * sends no header — the open local server, as before.
 */
val voiceToken: String = runCatching {
    File(
        providers.gradleProperty("onyx.voiceTokenFile")
            .getOrElse("/home/john/kokoro-tts/auth-proxy/token"),
    ).readLines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
}.getOrDefault("")

/** Resolves `storeFile` relative to the properties file, wherever it is. */
fun keystorePath(): File {
    val raw = keystoreProperties.getProperty("storeFile") ?: return releaseKeystore
    val candidate = File(raw)
    return if (candidate.isAbsolute) candidate else File(releaseKeystore.parentFile, raw)
}

android {
    // The code package and the installed identifier are declared once for the whole
    // repository, in `gradle.properties` (cf. the comment there).
    namespace = providers.gradleProperty("onyx.namespace").get()
    compileSdk = 37

    defaultConfig {
        applicationId = providers.gradleProperty("onyx.applicationId").get()
        // `${applicationId}` is used by **components** (launcher aliases, providers): Android resolves
        // them against the actually installed package. `${onyxNamespace}` is used by **contracts**
        // shared with the Wear and Auto companions (permission, authority, actions), which do
        // not depend on the install identifier.
        manifestPlaceholders["onyxNamespace"] = providers.gradleProperty("onyx.namespace").get()
        minSdk = 26
        targetSdk = 36
        // Version derived from the commit count: monotonic (mandatory for an APK
        // to install over the previous one) and traceable back to the repository.
        //
        // **Merge tie-breaker.** Two batches started from the same base announce the **same** number
        // (`1.0.0+662` on both sides): `ONYX_VERSION_CODE` then decides explicitly, and
        // the retained value is written in the merge report. Without the variable, the
        // commit-count rule remains the only source — the two sides of the merge therefore
        // cannot silently overwrite each other.
        versionCode = providers.gradleProperty("onyx.versionCode").orNull?.toIntOrNull()
            ?: providers.environmentVariable("ONYX_VERSION_CODE").orNull?.trim()?.toIntOrNull()
            ?: providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
                .standardOutput.asText.get().trim().toIntOrNull() ?: 1
        // The **displayed** version: 1.3.1, then 1.3.2 on each release. It is **derived**
        // from the `versionCode` — a single source of truth, impossible to forget one — starting from
        // the code that carries the first 1.3.1 (`onyx.versionBase`). The release tag follows the same
        // form (`v1.3.1+<versionCode>`): it is the number, in the tag, that the update
        // compares — the readable name decides nothing.
        versionName = providers.gradleProperty("onyx.versionBase").orNull?.toIntOrNull()
            ?.let { base -> "1.3." + ((versionCode ?: 1) - base) }
            ?: ("1.0.0+" + (versionCode ?: 1))
        // The voice service token for the test version (see `voiceToken` above).
        buildConfigField("String", "VOICE_TOKEN", "\"" + voiceToken + "\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (keystoreProperties.getProperty("storeFile") != null) {
            create("release") {
                storeFile = keystorePath()
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        } else {
            // **Fallback: the debug key.** Without `keystore.properties`, the release version
            // stayed unsigned, hence uninstallable — and the only testable version was the
            // **debug** version, the slowest of all (no minification, Compose
            // checks, `assertions`). Signed with the debug key, the optimized version installs
            // **over** the test version (same signature): this is what makes smoothness
            // measurable on the device. This fallback suits tests only, never a publication.
            val debugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
            if (debugKeystore.exists()) {
                create("release") {
                    storeFile = debugKeystore
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                }
            }
        }
    }

    buildTypes {
        release {
            // The brief asks for minification if the build stays green; the
            // `proguard-rules.pro` rules cover kotlinx.serialization and OkHttp.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions.unitTests.isIncludeAndroidResources = true

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

/**
 * **The Compose compiler report** — an audit tool, not a production setting.
 *
 * The compiler says, function by function, what it can skip (`skippable`) and what it cannot
 * (`restartable but not skippable`): this is the list of recompositions the thread
 * triggers on each state publication. The report is written to
 * `build/compose_compiler/` — it changes nothing in the produced code.
 */
composeCompiler {
    reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
    metricsDestination.set(layout.buildDirectory.dir("compose_compiler/metrics"))
}

dependencies {
    // **Periodic work**: the only free way to notify of a reply while
    // the app is closed (no third-party push service, Android self-registers).
    implementation(libs.androidx.work.runtime)
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    // MainActivity is a FragmentActivity for BiometricPrompt; the ActivityResult API
    // requires Fragment 1.3 at least (lint rejects it in release).
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.sshj)
    implementation(libs.eddsa)
    implementation(libs.zxing.embedded)
    implementation(libs.androidx.glance.appwidget)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    implementation(libs.markwon.core)
    implementation(libs.markwon.strikethrough)
    implementation(libs.markwon.tables)
    implementation(libs.markwon.linkify)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // An in-memory Dashboard: a room's thread exercised over the real JSON-RPC channel.
    testImplementation(libs.mockwebserver)
}
