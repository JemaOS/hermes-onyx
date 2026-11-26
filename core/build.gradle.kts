plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.onyx.app.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions.unitTests.isIncludeAndroidResources = true

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
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.android)

    // The reference theme and custom components are shared by the three modules.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.ui)
    api(libs.androidx.foundation)
    api(libs.androidx.ui.graphics)
    api(libs.androidx.core.ktx)

    // The system back handling: the anchored menu now lives in the **main window** (to read the
    // blur source and paint itself like glass), and is no longer a `Dialog` that would close it
    // on its own. `BackHandler` (activity-compose) closes it explicitly.
    implementation(libs.androidx.activity.compose)

    // Liquid glass (`LiquidGlass`): `dev.chrisbanes.haze`, split into three modules since
    // version 2 — the core, the blur, and the glass.
    //
    // As `api` and not `implementation`: `HazeState` is placed by the **screens** (`app` module) on
    // the scrolling parent, and read by the glass component — the type therefore crosses the module
    // boundary, and the screens must see the same Haze version as the pane they light up.
    //
    // Without `haze-materials` or `haze-blur-materials`: these two modules pull in
    // `androidx.compose.material3` (`HazeMaterials` is just Material wrapping), and the app must
    // owe no Material component.
    api(libs.haze)
    api(libs.haze.blur)
    api(libs.haze.glass)

    // **The glass lens** — `io.github.kyant0:backdrop` (Apache-2.0): capturing the
    // page into a `GraphicsLayer` and the AGSL refraction shader. Haze remains the fallback blur;
    // this module adds what Haze cannot do: bend the background at the edge (lens),
    // disperse colors over that curvature, and capture the page **only once** for
    // all surfaces (`OnyxLensGlass.kt`).

    api(libs.kyant.backdrop)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation(libs.mockwebserver)
}
