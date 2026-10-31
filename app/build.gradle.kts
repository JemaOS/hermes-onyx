import java.io.File
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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

/**
 * Le jeton du service vocal, pour la **version de test** : il est lu du fichier `0600` du mandataire
 * (jamais du dépôt), et devient la valeur par défaut du réglage « Voice token » (`BuildConfig`).
 * Le fichier peut être désigné par `-Ponyx.voiceTokenFile=…` ; absent, le jeton est vide et l'app
 * n'envoie aucun en-tête — le serveur local ouvert, comme avant.
 */
val voiceToken: String = runCatching {
    File(
        providers.gradleProperty("onyx.voiceTokenFile")
            .getOrElse("/home/john/kokoro-tts/auth-proxy/token"),
    ).readText().trim()
}.getOrDefault("")

/** Résout `storeFile` relativement au fichier de propriétés, où qu'il soit. */
fun keystorePath(): File {
    val raw = keystoreProperties.getProperty("storeFile") ?: return releaseKeystore
    val candidate = File(raw)
    return if (candidate.isAbsolute) candidate else File(releaseKeystore.parentFile, raw)
}

android {
    // Le paquet du code et l'identifiant installé sont déclarés une seule fois pour tout le
    // dépôt, dans `gradle.properties` (cf. le commentaire là-bas).
    namespace = providers.gradleProperty("onyx.namespace").get()
    compileSdk = 37

    defaultConfig {
        applicationId = providers.gradleProperty("onyx.applicationId").get()
        // `${applicationId}` sert aux **composants** (alias du lanceur, fournisseurs) : Android les
        // résout contre le paquet réellement installé. `${onyxNamespace}` sert aux **contrats**
        // partagés avec les compagnons Wear et Auto (permission, autorité, actions), qui ne
        // dépendent pas de l'identifiant d'installation.
        manifestPlaceholders["onyxNamespace"] = providers.gradleProperty("onyx.namespace").get()
        minSdk = 26
        targetSdk = 36
        // Version dérivée du nombre de commits : monotone (obligatoire pour qu'un APK
        // s'installe par-dessus le précédent) et traçable jusqu'au dépôt.
        //
        // **Départage de fusion.** Deux lots partis de la même base annoncent le **même** numéro
        // (`1.0.0+662` de part et d'autre) : `ONYX_VERSION_CODE` tranche alors explicitement, et
        // la valeur retenue est écrite dans le rapport de la fusion. Sans la variable, la règle
        // du compte de commits reste la seule source — les deux branches de la fusion ne peuvent
        // donc pas se marcher dessus en silence.
        versionCode = providers.environmentVariable("ONYX_VERSION_CODE").orNull?.trim()?.toIntOrNull()
            ?: ((providers.gradleProperty("onyx.versionBaseline").orNull?.toIntOrNull() ?: 0) +
                (providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
                    .standardOutput.asText.get().trim().toIntOrNull() ?: 1))
        versionName = "1.0.0+" + versionCode
        // Le jeton du service vocal pour la version de test (voir plus haut `voiceToken`).
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
        }
    }

    buildTypes {
        release {
            // Le brief demande la minification si le build reste vert ; les règles de
            // `proguard-rules.pro` couvrent kotlinx.serialization et OkHttp.
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

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    // MainActivity est une FragmentActivity pour BiometricPrompt ; l'API ActivityResult
    // demande Fragment 1.3 au moins (lint le refuse en release).
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.billing.ktx)
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
    // Un Dashboard en mémoire : le fil d'une salle éprouvé sur le vrai canal JSON-RPC.
    testImplementation(libs.mockwebserver)
}
