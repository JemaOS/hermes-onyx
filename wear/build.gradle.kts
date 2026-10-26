import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * La clé de release vit hors du dépôt : renseignez la variable d'environnement
 * `ONYX_RELEASE_KEYSTORE` avec le chemin de `release.properties`. Sans ce fichier le build de
 * release reste non signé plutôt que d'échouer — la clé n'appartient pas au projet.
 */
val releaseKeystore = System.getenv("ONYX_RELEASE_KEYSTORE")?.let { file(it) }
    ?: file("${rootDir}/keystore.properties")

val keystoreProperties = Properties().apply {
    if (releaseKeystore.exists()) releaseKeystore.inputStream().use { load(it) }
}

/** Résout `storeFile` relativement au fichier de propriétés, où qu'il soit. */
fun keystorePath(): File {
    val raw = keystoreProperties.getProperty("storeFile") ?: return releaseKeystore
    val candidate = File(raw)
    return if (candidate.isAbsolute) candidate else File(releaseKeystore.parentFile, raw)
}

android {
    namespace = "com.onyx.app.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.onyx.app.wear"
        minSdk = 30
        targetSdk = 36
        // Version dérivée du nombre de commits, plus la base de la ligne 1.0.0 publiée :
        // voir `onyx.versionBaseline` dans gradle.properties.
        versionCode = (providers.gradleProperty("onyx.versionBaseline").orNull?.toIntOrNull() ?: 0) +
            (providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
                .standardOutput.asText.get().trim().toIntOrNull() ?: 1)
        versionName = "1.0.0+" + versionCode
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

    // Les tuiles Wear rendent un `ListenableFuture`; Guava est la dépendance documentée.
    implementation(libs.guava)

    debugImplementation(libs.androidx.ui.tooling)
}
