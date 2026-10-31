plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// --- Locale des tests -------------------------------------------------------------------------
//
// La suite de tests verifie l'interface **francaise**, qui est la langue de reference du projet :
// plusieurs tests comparent des chaines composees a partir de la locale de la machine qui les
// lance. Sans cette declaration, la suite etait verte sur un poste francophone et rouge sur un
// serveur anglophone — un ecart d'environnement, pas un defaut du code.
//
// La locale du JVM de test est donc declaree **ici, une seule fois** : la suite rend le meme
// verdict partout, et ce qui est verifie est ce que l'application affiche en francais.
subprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("user.language", "fr")
        systemProperty("user.country", "FR")
    }
}
