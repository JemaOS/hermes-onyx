import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
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
    // Le compagnon voiture : même paquet de code que l'appli, plus `.auto` ; son identifiant
    // d'installation dérive de celui de l'appli (une seule déclaration, dans `gradle.properties`).
    namespace = providers.gradleProperty("onyx.namespace").get() + ".auto"
    compileSdk = 37

    defaultConfig {
        applicationId = providers.gradleProperty("onyx.applicationId").get() + ".auto"
        // Les contrats partagés avec l'appli (permission, autorité, actions) portent le paquet du
        // **code**, identique dans les deux manifestes — jamais l'identifiant d'installation.
        manifestPlaceholders["onyxNamespace"] = providers.gradleProperty("onyx.namespace").get()
        minSdk = 26
        targetSdk = 36
        // Départage de fusion : voir `app/build.gradle.kts` (`ONYX_VERSION_CODE`).
        versionCode = providers.environmentVariable("ONYX_VERSION_CODE").orNull?.trim()?.toIntOrNull()
            ?: ((providers.gradleProperty("onyx.versionBaseline").orNull?.toIntOrNull() ?: 0) +
                (providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
                    .standardOutput.asText.get().trim().toIntOrNull() ?: 1))
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
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.car.app)
}
