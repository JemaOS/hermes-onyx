package com.onyx.app.ui.bots

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * L'écriture du visage : la révision dite, et l'échec qui se dit.
 *
 * Deux défauts, un seul symptôme — « j'ai ajouté une bouche, elle n'apparaît pas » :
 *
 *  1. **la révision fausse.** Le serveur suit `ui_meta` par clé : `hermes-bots` pour les
 *     réglages de bot, `onyx` pour le visage. L'app écrivait le visage avec `revision = 0`
 *     en dur ; le serveur, qui en attendait `1`, répondait `ui_meta_conflicts` et **refusait**
 *     l'écriture.
 *  2. **l'échec muet.** L'appel était enveloppé dans un `runCatching` : la refusal disparaissait,
 *     le geste semblait passer, et le serveur gardait l'ancien visage. Le salon et la scène
 *     vocale montraient donc le visage d'avant — les deux surfaces d'accord entre elles, et
 *     fausses l'une et l'autre.
 *
 * Ces tests lisent le source comme le fait la convention du dépôt : ils interdisent le retour
 * de la révision en dur et l'avalement silencieux.
 */
class BotFaceWriteRevisionTest {

    private val viewModel: String =
        File("src/main/java/com/onyx/app/ui/bots/BotsViewModel.kt").readText()

    @Test
    fun `l ecriture du visage dit la revision du visage`() {
        assertTrue(
            "le visage s'écrit avec la révision de la clé `onyx`, pas une révision en dur",
            viewModel.contains("setFace(bot.name, code, revision = bot.faceRevision)"),
        )
        assertFalse(
            "plus de révision en dur : le serveur la refuse",
            viewModel.contains("setFace(bot.name, code, revision = 0)"),
        )
    }

    @Test
    fun `un refus du serveur se dit`() {
        assertFalse(
            "l'échec d'écriture ne doit plus être avalé en silence",
            viewModel.contains("runCatching {\n                    val rpc = ServeRpc(config)\n                    ServeBotsRpc(rpc).setFace("),
        )
        assertTrue(
            "le refus est affiché dans l'état de la section",
            viewModel.contains("Couldn't set that up: %1\\\$s"),
        )
    }

    @Test
    fun `le visage reste aussi sur ce telephone`() {
        // Le choix local reste : sans Dashboard, c'est lui qui fait le visage.
        assertTrue(
            "le visage est gardé sur ce téléphone",
            viewModel.contains("faces.setFace(bot.name, code)"),
        )
        assertTrue(
            "et la table est republiee pour que les surfaces le voient",
            viewModel.contains("publishFaces()"),
        )
    }
}
