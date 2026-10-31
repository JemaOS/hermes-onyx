package com.onyx.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de source de la fiche d'usage — celui qui mord sur l'arbre d'avant, tel quel.**
 *
 * Il ne référence aucune fonction du correctif : il lit les textes des sources. Sur l'arbre d'avant
 * il **échoue franchement** (la fiche nomme encore le modèle figé), sur l'arbre d'après il passe —
 * c'est la morsure rouge/verte.
 *
 * Ce qu'il tient :
 *
 * a. la fiche de consommation (la feuille des actions) nomme le modèle **en vigueur** — la même
 *    valeur que l'en-tête (`state.modelInForce`), par la source unique `ModelInForce` — et **plus**
 *    le `model` figé de `session.usage` ;
 * b. la fiche **par bot** et le **relevé d'agent** lisent la même source, et le prix s'y cherche
 *    **sous le nom affiché** (une seule valeur pour le nom et le prix) ;
 * c. les **zéros ne se contredisent pas en silence** : la phrase qui dit « aucun jeton compté »
 *    existe, et sa traduction française aussi.
 */
class FicheUsageModeleEnVigueurVerrouTest {

    private fun source(vararg candidates: String): String =
        candidates.map(::File).first { it.isFile }.readText()

    private val sheet = source("src/main/java/com/onyx/app/ui/chat/components/ChatActionSheet.kt")
    private val chat = source("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")
    private val ledgerScreen = source("src/main/java/com/onyx/app/ui/ledger/AgentLedgerScreen.kt")
    private val usageScreen = source("src/main/java/com/onyx/app/ui/conversations/SessionUsageScreen.kt")
    private val onyxApp = source("src/main/java/com/onyx/app/ui/OnyxApp.kt")
    private val ledger = source("../core/src/main/java/com/onyx/app/data/model/serve/AgentLedger.kt")
    private val french = source("../core/src/main/java/com/onyx/app/localization/OnyxFrenchMenus.kt")

    // — (a) la feuille nomme le modèle en vigueur ------------------------------------------------

    @Test
    fun `la feuille nomme le modele en vigueur, plus le nom fige de la session`() {
        assertFalse(
            "AVANT : la feuille peint le `model` figé de `session.usage` — il ne bouge pas quand on " +
                "change de modèle (ChatActionSheet.kt, la ligne `val model = usage.model?…`)",
            sheet.contains("val model = usage.model?.takeIf { it.isNotBlank() }"),
        )
        assertTrue(
            "APRÈS : le modèle vient de la source unique (`ModelInForce.preferInForce`)",
            sheet.contains("ModelInForce.preferInForce(modelInForce, usage.model)"),
        )
        assertTrue(
            "la feuille reçoit le modèle en vigueur de la conversation",
            sheet.contains("modelInForce: String? = null,"),
        )
        // Le titre « Cette conversation · modèle » est inchangé : c'est bien le modèle en vigueur
        // qui s'y écrit, puisqu'il vient de `model`.
        assertTrue(sheet.contains("OnyxLocalization[\"This conversation\"] + \" · \" + model"))
    }

    @Test
    fun `l ecran de chat passe le modele en vigueur a la feuille`() {
        assertTrue(
            "ChatScreen alimente la feuille avec `state.modelInForce` — la même valeur que l'en-tête",
            chat.contains("modelInForce = state.modelInForce,"),
        )
    }

    // — (b) la fiche par bot et le relevé lisent la même source ----------------------------------

    @Test
    fun `le releve d agent cherche le nom et le prix sous la meme valeur`() {
        assertFalse(
            "AVANT : le registre (qui fige le modèle) passait devant",
            ledger.contains("val resolvedModel = registry?.model?.takeIf { it.isNotBlank() }\n                ?: model"),
        )
        assertTrue(
            "APRÈS : la source unique résout le modèle du relevé",
            ledger.contains("ModelInForce.preferInForce(inForceModel ?: model, registry?.model)"),
        )
        assertTrue(
            "l'écran résout le modèle en vigueur par conversation",
            ledgerScreen.contains("ModelInForce.inForce("),
        )
        assertTrue(ledgerScreen.contains("botOfConversation(conversation, roster, mainName)?.model"))
        assertTrue(
            "le prix se cherche sous le nom AFFICHÉ (une seule valeur pour les deux)",
            ledgerScreen.contains("val model = ModelInForce.preferInForce(inForceModel, row?.model)"),
        )
        assertTrue(ledgerScreen.contains("price = model?.let { prices[it] },"))
    }

    @Test
    fun `la fiche par bot et la conversation passent le modele en vigueur`() {
        assertTrue(
            "la fiche d'usage nomme le modèle en vigueur sous son nom consigné",
            usageScreen.contains("ModelInForce.preferInForce(model, summary.model)"),
        )
        assertTrue(onyxApp.contains("model = usageModel,"))
        assertTrue(onyxApp.contains("openModelInForce = state.modelInForce,"))
    }

    // — (c) les zéros se disent -------------------------------------------------------------------

    @Test
    fun `les compteurs nuls sous un contexte rempli se disent, en francais aussi`() {
        assertTrue(
            "la phrase qui explique les zéros est posée par la feuille",
            sheet.contains("if (usage.reportsContextWithoutTokens)"),
        )
        assertTrue(
            "le texte de la phrase",
            sheet.contains(
                "\"This server counted no tokens for this conversation; the only thing it measured " +
                    "is the window below.\"",
            ),
        )
        assertTrue(
            "et sa traduction française existe",
            french.contains(
                "\"This server counted no tokens for this conversation; the only thing it measured " +
                    "is the window below.\" to",
            ),
        )
    }
}
