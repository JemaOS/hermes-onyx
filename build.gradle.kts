plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// --- Locale des tests -------------------------------------------------------------------------
//
// La suite verifie l'interface **francaise**, langue de reference du projet : plusieurs tests
// comparent des chaines composees a partir de la locale du JVM qui les lance. Sans cette
// declaration, la suite etait verte sur un poste francophone et rouge sur un serveur anglophone
// (19 `ComparisonFailure` sur le CI du depot public), sans que le code soit en cause.
//
// Mesure, meme classe de test, une seule machine : `LC_ALL=en_US.UTF-8` -> 2 echecs,
// `LC_ALL=fr_FR.UTF-8` -> succes.
//
// La locale du JVM de test est donc declaree **ici, une seule fois** : la suite rend le meme
// verdict partout, et ce qui est verifie reste ce que l'application affiche en francais.
subprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("user.language", "fr")
        systemProperty("user.country", "FR")
    }
}
