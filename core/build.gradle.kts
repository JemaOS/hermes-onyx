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

    // Le thème et les composants maison de la référence sont partagés par les trois modules.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.ui)
    api(libs.androidx.foundation)
    api(libs.androidx.ui.graphics)
    api(libs.androidx.core.ktx)

    // Le verre liquide (`LiquidGlass`) : `dev.chrisbanes.haze`, découpé en trois modules depuis la
    // version 2 — le cœur, le flou, et le verre.
    //
    // En `api` et non `implementation` : `HazeState` est posé par les **écrans** (module `app`) sur
    // le parent qui défile, et lu par le composant en verre — le type traverse donc la frontière du
    // module, et les écrans doivent voir la même version de Haze que la vitre qu'ils éclairent.
    //
    // Sans `haze-materials` ni `haze-blur-materials` : ces deux modules tirent
    // `androidx.compose.material3` (`HazeMaterials` n'est qu'un habillage Material), et l'app ne
    // doit aucun composant Material.
    api(libs.haze)
    api(libs.haze.blur)
    api(libs.haze.glass)

    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
}
