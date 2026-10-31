package com.onyx.app

import com.onyx.app.data.model.serve.AgentLedger
import com.onyx.app.data.model.serve.LedgerMoneySource
import com.onyx.app.data.model.serve.LedgerScope
import com.onyx.app.ui.ledger.LedgerStandIn
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le **câblage** du relevé d'agent : l'écran est atteignable, et les quatre cas que le lot doit
 * prouver produisent bien ce qu'ils annoncent.
 *
 * Un émulateur n'a ni Dashboard ni agent : les quatre cas — conversation neuve, conversation
 * chargée, bot sans prix fourni, donnée manquante — sont posés par [LedgerStandIn], dont les
 * valeurs sont celles du serveur réel. Ce test interroge le **relevé** que ces cas produisent, avec
 * la même chaîne que l'écran (`AgentLedger.of` puis `sections`).
 */
class LedgerWiringTest {

    private val french = Locale.FRENCH

    private fun read(path: String) = File(path).readText()

    private val app = read("src/main/java/com/onyx/app/ui/OnyxApp.kt")
    private val screen = read("src/main/java/com/onyx/app/ui/ledger/AgentLedgerScreen.kt")
    private val standIn = read("src/main/java/com/onyx/app/ui/ledger/LedgerStandIn.kt")

    @Test
    fun `le releve a sa route, et son entree dans le menu du fil`() {
        assertTrue("la route manque", app.contains("LEDGER,"))
        assertTrue(
            "l'écran n'est pas branché sur sa route",
            app.contains("Route.LEDGER -> AgentLedgerScreen("),
        )
        assertTrue(
            "l'entrée du menu du fil manque",
            app.contains("""add(MenuItem("Agent ledger", Symbols.DocOnClipboard) { navTo(Route.LEDGER.name) })"""),
        )
        // Le retour ramène au fil, comme les autres écrans pleine page ouverts depuis la conversation.
        assertTrue(app.contains("onBack = { if (!navBack()) navTo(Route.CHAT.name) },\n                standIn = ledgerStandIn,"))
    }

    @Test
    fun `l ecran lit le serveur, et la doublure le remplace entierement`() {
        // La portée, la conversation ouverte et le roster : trois lectures qui basculent d'un coup.
        assertTrue(screen.contains("val thread = standIn?.conversations ?: conversations"))
        assertTrue(screen.contains("val roster = standIn?.bots ?: bots"))
        assertTrue(screen.contains("val open = standIn?.openConversation ?: openConversation"))
        // Les deux routes du serveur sont réellement appelées — le registre **profil par profil**
        // (une session de bot vit dans la base de SON profil : sans profil, sa ligne est absente et
        // le relevé annonçait « le registre n'a pas répondu pour cette session »).
        assertTrue(screen.contains("ServeSessionsApi(client).summariesIn(profiles)"))
        assertTrue(screen.contains("ModelCatalog.fetchModelOptions(server, profile)"))
    }

    @Test
    fun `cas 1 - la conversation neuve n a rien a relever, et le dit`() {
        val world = LedgerStandIn.of(LedgerStandIn.FRESH)!!
        val conversation = world.openConversation!!
        val ledger = AgentLedger.of(
            conversation = conversation,
            registryAttempted = true,
            priceSource = world.priceSource,
            priceReadAt = world.priceReadAt,
        )
        // « Peu de données » : rien n'a été exécuté, et rien n'est mesuré — mais la réponse reçue
        // compte comme une réponse, donc le relevé n'est pas vide : il **explique** chaque absence.
        assertFalse("une réponse sans durée n'est pas le vide", ledger.isEmpty)
        assertEquals(1, ledger.turnsUnmeasured)
        assertEquals(0, ledger.turnsMeasured)
        assertEquals(0, ledger.toolsTotal)
        assertEquals(0, ledger.subagentsLaunched)
        assertNull(ledger.tokensInput)
        assertNull(ledger.money.usd)
        val model = ledger.sections(french).first { it.header == "Model" }
        assertEquals("non consigné dans ce fil", model.rows.first().value)
        // Aucune session à chercher : l'écran le dit, au lieu d'accuser le registre.
        assertEquals(
            "cette conversation n’a pas de session sur le serveur",
            model.rows.first { it.label == "Origin" }.note,
        )
    }

    @Test
    fun `cas 2 - la conversation chargee releve tout, et estime au prix du serveur`() {
        val world = LedgerStandIn.of(LedgerStandIn.LOADED)!!
        val conversation = world.openConversation!!
        val registry = world.registry.first { it.id == conversation.serverSessionId }
        val ledger = AgentLedger.of(
            conversation = conversation,
            registry = registry,
            registryAttempted = true,
            price = conversation.serverSessionId?.let { registry.model?.let { model -> world.prices[model] } },
            priceSource = world.priceSource,
            priceReadAt = world.priceReadAt,
        )
        assertEquals(9, ledger.toolsTotal)
        assertEquals(6, ledger.toolsSucceeded)
        assertEquals(2, ledger.toolsFailed)
        assertEquals(1, ledger.toolsRunning)
        assertEquals(3, ledger.subagentsLaunched)
        assertEquals(2, ledger.subagentsTracked)
        assertEquals(132.4, ledger.workSeconds!!, 1e-6)
        assertEquals("minimax/minimax-m2.7", ledger.model)
        // 1 048 576 jetons d'entrée à 0,30 $ / M + 87 808 de sortie à 1,20 $ / M ≈ 0,42 $.
        assertEquals(LedgerMoneySource.SERVER_PRICE, ledger.money.source)
        assertEquals(0.42, ledger.money.usd!!, 1e-2)
        val sections = ledger.sections(french, now = world.priceReadAt)
        assertEquals("2 min 12", sections.first { it.header == "Work" }.rows.first().value)
        assertEquals("0,42 $", sections.first { it.header == "Cost" }.rows.first().value)
        assertTrue(sections.first { it.header == "Cost" }.rows.first().note!!.contains("relevé le 28 oct. 2026 à 17:31"))
    }

    @Test
    fun `cas 3 - un bot dont le fournisseur ne publie aucun prix ecrit non fourni`() {
        val world = LedgerStandIn.of(LedgerStandIn.NO_PRICE)!!
        assertEquals(LedgerScope.BOT, world.initialScope)
        val mine = world.conversations
        assertEquals(2, mine.size)
        val ledgers = mine.map { conversation ->
            val registry = world.registry.firstOrNull { it.id == conversation.serverSessionId }
            AgentLedger.of(
                conversation = conversation,
                registry = registry,
                registryAttempted = true,
                price = registry?.model?.let { model -> world.prices[model] },
                priceSource = world.priceSource,
                priceReadAt = world.priceReadAt,
            )
        }
        val total = AgentLedger.cumulative("Scout", ledgers)
        assertEquals(2, total.conversations)
        assertNull("aucun montant sans prix du serveur", total.money.usd)
        assertEquals(2, total.withoutPrice)
        val cost = total.sections(french, now = world.priceReadAt).first { it.header == "Cost" }
        assertEquals("non fourni", cost.rows.first().value)
        assertEquals("le serveur ne publie aucun prix pour ce modèle", cost.rows.first().note)
    }

    @Test
    fun `cas 4 - la donnee manquante est expliquee, jamais remplacee par un zero`() {
        val world = LedgerStandIn.of(LedgerStandIn.MISSING)!!
        val conversation = world.openConversation!!
        val ledger = AgentLedger.of(
            conversation = conversation,
            registry = null,
            registryAttempted = true,
            priceSource = world.priceSource,
            priceReadAt = world.priceReadAt,
        )
        assertNull("aucune durée mesurée", ledger.workSeconds)
        assertNull("aucun jeton remonté", ledger.tokensInput)
        val sections = ledger.sections(french, now = world.priceReadAt)
        assertEquals("non mesuré", sections.first { it.header == "Work" }.rows.first().value)
        assertEquals("non remonté", sections.first { it.header == "Tokens" }.rows.first().value)
        assertEquals("non fourni", sections.first { it.header == "Cost" }.rows.first().value)
        // Le registre a été tenté sans trouver la session : l'écran le dit.
        assertEquals(
            "le registre du serveur n’a pas répondu pour cette session",
            sections.first { it.header == "Model" }.rows.first { it.label == "Origin" }.note,
        )
        // Les appels d'outils, eux, sont comptés — et l'état de chacun est écrit.
        assertEquals(3, ledger.toolsTotal)
        assertFalse(ledger.isEmpty)
    }

    @Test
    fun `la doublure ne cite aucune autre plateforme, et ne pose rien sans drapeau`() {
        val forbidden = listOf("sw" + "ift", "sw" + "iftui", "i" + "os", "i" + "phone", "i" + "pad", "ap" + "ple", "x" + "code", "echo", "red" + "de")
        listOf(screen, standIn).forEach { source ->
            forbidden.forEach { word ->
                assertFalse(
                    "le mot « $word » ne doit pas apparaître dans le relevé",
                    Regex("(?<![a-z])$word(?![a-z])").containsMatchIn(source.lowercase()),
                )
            }
        }
        assertNull("un nom de cas inconnu ne pose rien", LedgerStandIn.of("inconnu"))
        assertNull(LedgerStandIn.of(""))
    }

    @Test
    fun `une conversation sans le moindre message est le seul cas vraiment vide`() {
        val empty = com.onyx.app.data.model.Conversation(
            id = "conv-vide",
            title = "Onyx",
            createdAt = 0L,
            updatedAt = 0L,
        )
        assertTrue(AgentLedger.of(conversation = empty).isEmpty)
    }
}
