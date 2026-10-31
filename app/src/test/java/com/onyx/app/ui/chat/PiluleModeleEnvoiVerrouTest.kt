package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou croisé de la pilule du haut et de l'envoi** — la source d'une même clé.
 *
 * Le défaut fermé, que rien ne voyait : la **pilule du haut** et la **puce du compositeur** nommaient
 * le modèle par `ModelName.display(state.modelInUse, state.server.effectiveModel)` pendant que la
 * **requête d'envoi** portait `server.effectiveModel`. Deux résolutions parallèles de la même chose,
 * donc une app qui pouvait **afficher un nom et en envoyer un autre** — mesure sur le double du
 * serveur, build 1.0.0+858 : la pilule disait « claude-sonnet-4.5 » et la requête partait en
 * `hermes-agent`.
 *
 * Ce verrou tient la **source** : la pilule (`ChatScreen.kt`), la puce (`ChatScreen.kt`) et l'envoi
 * (`ChatViewModel.kt`) doivent lire **la même clé**, `state.modelInForce`, et **sans habillage**
 * (aucune fonction qui pourrait rendre autre chose que ce qui part). Il tient aussi la **clé** : elle
 * est déclarée sur l'état (`ChatUiState`) et posée par l'unique résolution `ModelInForce.inForce(...)`.
 *
 * Ce fichier lit les sources **comme du texte** et ne dépend d'aucune API nouvelle : il compile donc
 * aussi bien sur la source d'**avant** — où il est **ROUGE** (la pilule lisait `state.modelInUse`,
 * l'envoi `server.effectiveModel`) — que sur celle d'**après**, où il est **VERT**. Les trois cas de
 * conversation, tapés sur l'état réel, sont dans `ModelEnVigueurCasTest`.
 *
 * **Morsure** : remettre `ModelName.display(state.modelInUse, state.server.effectiveModel)` à la
 * pilule, ou `server.effectiveModel` à l'envoi, fait rougir le premier test.
 */
class PiluleModeleEnvoiVerrouTest {

    private fun read(path: String) = File(path).readText()

    private val chatScreen = read("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")
    private val chatViewModel = read("src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt")
    private val chatUiState = read("src/main/java/com/onyx/app/ui/chat/ChatUiState.kt")

    /** Les clés d'état réellement lues par une expression (`state.xxx`, `state.xxx.yyy`). */
    private fun keys(expression: String): List<String> =
        Regex("state\\.[A-Za-z]+(?:\\.[A-Za-z]+)*").findAll(expression).map { it.value }.toList()

    private fun line(pattern: String, source: String): String =
        Regex(pattern).find(source)?.groupValues?.get(1)?.trim()
            ?: error("expression introuvable dans la source : « $pattern »")

    @Test
    fun `la pilule la puce et l envoi nomment le meme modele en vigueur`() {
        val pill = line("subtitle = ([^\\n]+)", chatScreen)
        val chip = line("modelLabel = ([^\\n]+)", chatScreen)
        val send = line("model = modelOverride \\?: ([^,\\n]+)", chatViewModel)

        assertEquals(
            "la pilule (ChatScreen.kt, `subtitle =`) doit lire la clé du modèle en vigueur",
            listOf("state.modelInForce"),
            keys(pill),
        )
        assertEquals(
            "la puce (ChatScreen.kt, `modelLabel =`) doit lire la MÊME clé que la pilule",
            keys(pill),
            keys(chip),
        )
        assertEquals(
            "l'envoi (ChatViewModel.kt, `model = modelOverride ?:`) doit lire la MÊME clé que la " +
                "pilule : sinon l'app affiche un modèle et en envoie un autre",
            keys(pill),
            keys(send),
        )
        // Et **sans habillage** : la valeur rendue est le nom lui-même, pas un libellé recalculé.
        // Un habillage (ex. `ModelInForce.display(state.modelInForce)`) ferait diverger la sortie de
        // l'envoi de celle de la pilule dès qu'un alias de passerelle entre dans la résolution.
        assertEquals("la pilule écrit la valeur telle quelle", "state.modelInForce", pill)
        assertEquals("la puce écrit la valeur telle quelle", "state.modelInForce", chip)
        assertEquals("l'envoi porte la valeur telle quelle", "state.modelInForce", send)
    }

    @Test
    fun `la cle unique du modele en vigueur est ChatUiState modelInForce`() {
        assertTrue(
            "la clé unique est déclarée sur l'état",
            chatUiState.contains("val modelInForce: String?"),
        )
        assertTrue(
            "et posée par l'unique résolution conversation → bot → serveur → défaut",
            chatUiState.contains("ModelInForce.inForce(modelInUse, botModel, server.effectiveModel)"),
        )
    }

    @Test
    fun `les anciennes expressions divergentes ne peuvent pas revenir`() {
        assertFalse(
            "la pilule ne recalcule plus un libellé à part (ChatScreen.kt)",
            chatScreen.contains("ModelName.display(state.modelInUse, state.server.effectiveModel)"),
        )
        assertFalse(
            "l'envoi ne prend plus le seul modèle du serveur (ChatViewModel.kt)",
            chatViewModel.contains("model = modelOverride ?: server.effectiveModel"),
        )
        assertFalse(
            "aucune fonction d'habillage ne peut rendre autre chose que la valeur (ChatScreen.kt)",
            chatScreen.contains("ModelInForce.display("),
        )
        assertFalse(
            "aucune seconde résolution ne réécrit le modèle de la requête (ChatViewModel.kt)",
            chatViewModel.contains("withResolvedModel(server)"),
        )
    }
}
