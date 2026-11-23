import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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

/** Resolves `storeFile` relative to the properties file, wherever it is. */
fun keystorePath(): File {
    val raw = keystoreProperties.getProperty("storeFile") ?: return releaseKeystore
    val candidate = File(raw)
    return if (candidate.isAbsolute) candidate else File(releaseKeystore.parentFile, raw)
}

android {
    // The watch: same code package as the app, plus `.wear`; its install identifier
    // derives from the app's (a single declaration, in `gradle.properties`).
    namespace = providers.gradleProperty("onyx.namespace").get() + ".wear"
    compileSdk = 37

    defaultConfig {
        applicationId = providers.gradleProperty("onyx.applicationId").get() + ".wear"
        // The contracts shared with the app (actions) carry the **code** package, identical
        // in both manifests — never the install identifier.
        manifestPlaceholders["onyxNamespace"] = providers.gradleProperty("onyx.namespace").get()
        minSdk = 30
        targetSdk = 36
        // Merge tie-breaker: see `app/build.gradle.kts` (`ONYX_VERSION_CODE`).
        versionCode = providers.gradleProperty("onyx.versionCode").orNull?.toIntOrNull()
            ?: providers.environmentVariable("ONYX_VERSION_CODE").orNull?.trim()?.toIntOrNull()
            ?: providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
                .standardOutput.asText.get().trim().toIntOrNull() ?: 1
        // The **displayed** version: 1.3.1, then 1.3.2 on each release — derived from
        // the `versionCode`, as in `app` (see `onyx.versionBase`).
        versionName = providers.gradleProperty("onyx.versionBase").orNull?.toIntOrNull()
            ?.let { base -> "1.3." + ((versionCode ?: 1) - base) }
            ?: ("1.0.0+" + (versionCode ?: 1))
    }

    signingConfigs {
        if (keystoreProperties.getProperty("storeFile") != null) {
            create("release") {
                storeFile = keystorePath()
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.ui.graphics)

    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)

    // Wear tiles render a `ListenableFuture`; Guava is the documented dependency.
    implementation(libs.guava)

    debugImplementation(libs.androidx.ui.tooling)
}
