package com.onyx.app.ui.bots

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **« Modèle », dans l'identité d'un bot, ouvre la vraie liste.**
 *
 * Relevé par le propriétaire, capture en main : sous la rangée « Modèle » (`deepseek/…-flash-1017`),
 * l'écran ne dépliait que deux lignes — « Identique à Onyx » et `hermes-agent` — et un identifiant
 * brut. Rien qui dise à la personne **quels modèles existent**, alors que la feuille du fil les
 * montre tous (recherche, profils, prix, favoris).
 *
 * Ce verrou tient le câblage : l'écran ouvre la feuille quand le parent la fournit, le parent la
 * dessine au-dessus de l'identité avec `ModelScope.BOT`, le choix s'écrit sur le profil du bot, et
 * le retour à l'agent principal garde sa rangée. C'est un verrou de **source** : la feuille est un
 * composable Compose, il n'y a pas d'assertion de comportement possible sur la JVM ici.
 */
class BotEditorModelSheetTest {

    private val editor = File("src/main/java/com/onyx/app/ui/bots/BotEditorScreen.kt").readText()
    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()

    @Test
    fun `un appui sur Modele ouvre la feuille quand le parent la fournit`() {
        assertTrue(
            "l'écran accepte la feuille du parent",
            editor.contains("onOpenModelSheet: (() -> Unit)? = null,"),
        )
        assertTrue(
            "l'appui ouvre la feuille, et ne déplie la liste locale que sans elle",
            editor.contains("if (onOpenModelSheet != null) onOpenModelSheet() else showModels = !showModels"),
        )
        assertTrue(
            "la liste brute ne se déplie plus quand la feuille existe",
            editor.contains("if (onOpenModelSheet == null && showModels)"),
        )
    }

    @Test
    fun `le parent branche la feuille sur le modele du bot`() {
        assertTrue(
            "l'identité du bot demande la feuille",
            app.contains("onOpenModelSheet = { editorModelSheet = true },"),
        )
        assertTrue(
            "la feuille est celle des bots, pas celle du serveur",
            app.contains("scope = com.onyx.app.ui.models.ModelScope.BOT,"),
        )
        assertTrue(
            "le choix s'écrit sur le profil du bot",
            app.contains("bot?.let { botsViewModel.setModel(it, model, providerForChosen(model)) }"),
        )
        // Le catalogue n'est lu qu'à l'ouverture de la feuille : l'éditeur compte comme les autres.
        assertTrue(
            "le catalogue se lit aussi pour la feuille de l'éditeur",
            app.contains("LaunchedEffect(showModelSheet, roomModelBot, editorModelSheet, state.server.id)"),
        )
    }

    @Test
    fun `le retour a l agent principal reste a portee`() {
        // « identique à X » n'est pas un modèle : c'est l'absence de choix. Il garde sa rangée,
        // sinon la feuille — qui ne choisit que des modèles — le rendrait inatteignable.
        assertTrue(
            "la rangée « identique à l'agent principal » est toujours dessinée",
            editor.contains("IdentityRow(onClick = { onPickModel(null) })"),
        )
        assertTrue(
            "et elle dit de quel agent il s'agit",
            editor.contains("text = \"Same as \$mainName\","),
        )
    }
}
