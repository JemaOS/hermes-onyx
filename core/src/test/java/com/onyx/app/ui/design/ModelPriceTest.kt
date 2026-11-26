package com.onyx.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Les prix de la feuille de modèle** — la règle du propriétaire, chiffres à l'appui :
 * **aucun montant n'est inventé**, et une pastille n'écrit un prix que si le **serveur** l'a donné.
 *
 * La source est `GET api/model/options` → `providers[].pricing`, une table
 * `{ "<identifiant>": { "input": "$0.30", "output": "$1.20", "cache": "$0.006", "free": false } }`.
 * Les montants y sont **déjà formatés** ; l'app ne fait que les remettre dans la ponctuation de la
 * langue (« 0,30 $ »), jamais les calculer. Les valeurs des tests sont **relevées sur le serveur
 * réel** le 28 octobre 2026 (voir le rapport et le tableau de provenance).
 *
 * Ce fichier tient aussi la **forme de la rangée** : un modèle écrit **une fois**, une pastille par
 * fournisseur qui le propose — dont le `LM Studio` local *et* le `Kilo Code · 0,30 $ / 1,20 $` du
 * même modèle, exactement comme la capture du propriétaire.
 */
class ModelPriceTest {

    // — Le montant reçu du serveur, tel quel et à la française —

    @Test
    fun `un montant du serveur se lit a la francaise, et le montant brut reste disponible`() {
        val price = ServerPrice(input = "\$0.30", output = "\$1.20", cache = "\$0.006")
        assertTrue(price.hasValue)
        assertEquals("0,30 \$ / 1,20 \$", price.line())
        // En anglais, la ponctuation du serveur est gardée telle quelle.
        assertEquals("\$0.30 / \$1.20", price.lineAsServed())
    }

    @Test
    fun `sans montant, la ligne reste muette — jamais un zero de remplissage`() {
        val none = ServerPrice()
        assertFalse(none.hasValue)
        assertNull("aucun prix ne se peint", none.line())
        assertNull(none.lineAsServed())
        // Le libre-service d'un serveur local : le nom du fournisseur seul, sans montant.
        assertEquals("LM Studio", ModelOffer("lmstudio", "LM Studio", none.takeIf { it.hasValue }).chip())
    }

    @Test
    fun `un modele declare gratuit par le serveur le dit, dans la langue`() {
        val free = ServerPrice(input = "free", output = "free", free = true)
        assertTrue(free.hasValue)
        assertEquals("gratuit", free.line())
        assertEquals("free", free.lineAsServed())
        assertEquals("Kilo Code · gratuit", ModelOffer("kilocode", "Kilo Code", free).chip())
    }

    @Test
    fun `une chaine qui n est pas un montant n est pas reecrite`() {
        // Le serveur garde la main sur ce qu'il publie : l'app ne fabrique pas un montant.
        assertEquals("1200 jetons", ServerPrice.french("1200 jetons"))
        assertNull(ServerPrice.french("  "))
    }

    // — La pastille d'information : fournisseur, puis prix s'il vient du serveur —

    @Test
    fun `la pastille ecrit le fournisseur seul quand le serveur ne donne aucun prix`() {
        val local = ModelOffer("lmstudio", "LM Studio")
        assertEquals("LM Studio", local.chip())
        assertFalse("aucun montant ne s'invente", local.chip().contains("\$"))
    }

    @Test
    fun `la pastille ecrit le prix du serveur quand il en donne un`() {
        val kilo = ModelOffer("kilocode", "Kilo Code", ServerPrice("\$0.30", "\$1.20", "\$0.006"))
        assertEquals("Kilo Code · 0,30 \$ / 1,20 \$", kilo.chip())
    }

    // — La rangée agrégée : un modèle, une pastille par fournisseur —

    /** Le cas **exact** de la capture : un modèle servi localement **et** à distance. */
    private fun sharedModel(): List<ModelChoice> = listOf(
        ModelChoice("minimax/minimax-m2.7", provider = "lmstudio", providerName = "LM Studio", profile = "default"),
        ModelChoice(
            "minimax/minimax-m2.7",
            provider = "kilocode",
            providerName = "Kilo Code",
            profile = "default",
            price = ServerPrice("\$0.30", "\$1.20", "\$0.006"),
        ),
        ModelChoice("sage-a1.5", provider = "lmstudio", providerName = "LM Studio", profile = "default"),
    )

    @Test
    fun `un modele se lit une fois, avec une pastille par fournisseur`() {
        val entries = ModelSelection.entries(sharedModel())
        assertEquals("un modèle = une rangée", 2, entries.size)
        val shared = entries.first()
        assertEquals("minimax/minimax-m2.7", shared.id)
        assertEquals("M", shared.monogram)
        assertEquals("default", shared.profileName())
        // Les deux pastilles, dans l'ordre du serveur — le local d'abord, comme sur la capture.
        assertEquals(
            listOf("LM Studio", "Kilo Code · 0,30 \$ / 1,20 \$"),
            shared.offerChips(),
        )
        assertTrue(shared.offeredBy("lmstudio"))
        assertTrue(shared.offeredBy("kilocode"))
        assertTrue("une offre tarifée suffit", shared.hasPrice)

        // Le modèle purement local : une pastille, **aucun** montant.
        val local = entries.last()
        assertEquals(listOf("LM Studio"), local.offerChips())
        assertFalse("le local n'a pas de prix inventé", local.hasPrice)
    }

    @Test
    fun `l ordre de la source est garde — le modele local du profil reste en tete`() {
        val entries = ModelSelection.entries(sharedModel())
        assertEquals(listOf("minimax/minimax-m2.7", "sage-a1.5"), entries.map { it.id })
    }

    @Test
    fun `le meme modele sous deux profils reste deux rangees`() {
        val entries = ModelSelection.entries(
            listOf(
                ModelChoice("gpt-5", provider = "kilocode", profile = "kilo"),
                ModelChoice("gpt-5", provider = "kilocode", profile = "scout"),
            ),
        )
        assertEquals(2, entries.size)
        assertEquals(listOf("kilo/gpt-5", "scout/gpt-5"), entries.map { it.listKey })
        // L'agrégation ne fusionne jamais deux profils : chacun garde sa rangée et son offre.
        assertTrue(entries.all { it.offers.size == 1 })
    }

    @Test
    fun `deux offres du meme fournisseur ne font pas deux pastilles`() {
        // Un fournisseur qui se répète ne se dessine qu'une fois, et c'est **la première** offre de
        // la source qui décrit la pastille : la rangée n'a jamais deux fois « Kilo Code ».
        val entries = ModelSelection.entries(
            listOf(
                ModelChoice(
                    "gpt-5",
                    provider = "kilocode",
                    providerName = "Kilo Code",
                    profile = "kilo",
                    offers = listOf(
                        ModelOffer("kilocode", "Kilo Code", ServerPrice("\$2.00", "\$10.00")),
                        ModelOffer("kilocode", "Kilo Code", ServerPrice("\$3.00", "\$15.00")),
                    ),
                ),
            ),
        )
        assertEquals(1, entries.size)
        assertEquals(listOf("Kilo Code · 2,00 \$ / 10,00 \$"), entries.first().offerChips())

        // Deux **lignes** du même fournisseur, elles aussi, ne font qu'une pastille.
        val twice = ModelSelection.entries(
            listOf(
                ModelChoice("gpt-5", provider = "kilocode", providerName = "Kilo Code", profile = "kilo"),
                ModelChoice(
                    "gpt-5",
                    provider = "kilocode",
                    providerName = "Kilo Code",
                    profile = "kilo",
                    price = ServerPrice("\$2.00", "\$10.00"),
                ),
            ),
        )
        assertEquals(1, twice.size)
        assertEquals(listOf("Kilo Code"), twice.first().offerChips())
    }

    // — Les pastilles de fournisseur, et le filtre qu'elles pilotent —

    @Test
    fun `les fournisseurs proposes viennent des offres reelles, avec leur nom du serveur`() {
        val entries = ModelSelection.entries(sharedModel())
        val facets = ModelSelection.providers(entries)
        assertEquals(listOf("lmstudio", "kilocode"), facets.map { it.provider })
        assertEquals(listOf("LM Studio", "Kilo Code"), facets.map { it.providerName })
        // Le compte suit les rangées, pas les lignes brutes.
        assertEquals(2, facets.first { it.provider == "lmstudio" }.count)
        assertEquals(1, facets.first { it.provider == "kilocode" }.count)
    }

    @Test
    fun `le filtre d un fournisseur garde la rangee entiere, avec toutes ses pastilles`() {
        val entries = ModelSelection.entries(sharedModel())
        // « Tout » : rien n'est écarté.
        assertEquals(2, ModelSelection.byProvider(entries, ModelSelection.AllProviders).size)
        // Le distant : la rangée partagée **et** elle seule.
        val kilo = ModelSelection.byProvider(entries, "kilocode")
        assertEquals(listOf("minimax/minimax-m2.7"), kilo.map { it.id })
        assertEquals(
            "la rangée garde ses deux pastilles, elle n'est pas amputée",
            listOf("LM Studio", "Kilo Code · 0,30 \$ / 1,20 \$"),
            kilo.first().offerChips(),
        )
        // Le local : ses deux modèles.
        assertEquals(
            listOf("minimax/minimax-m2.7", "sage-a1.5"),
            ModelSelection.byProvider(entries, "lmstudio").map { it.id },
        )
    }

    @Test
    fun `la recherche retrouve un modele par n importe lequel de ses fournisseurs`() {
        val entries = ModelSelection.entries(sharedModel())
        assertEquals(
            listOf("minimax/minimax-m2.7"),
            ModelSelection.filter(entries, "kilo code").map { it.id },
        )
        assertEquals(
            listOf("minimax/minimax-m2.7", "sage-a1.5"),
            ModelSelection.filter(entries, "lm studio").map { it.id },
        )
    }

    // — « Réflexion » : les crans de la référence, dans son ordre —

    @Test
    fun `la rangee peint les crans de la reference dans son ordre, puis ceux du serveur en plus`() {
        val plan = ReasoningLevels.plan()
        // L'ordre de la référence : Minimal → Max. Les crans que la référence ne dessine pas
        // (Défaut, Aucune) sont **rejetés en fin de rangée** : rien n'est retiré, rien n'est déplacé.
        assertEquals(
            listOf("minimal", "low", "medium", "high", "xhigh", "max", "", "none"),
            plan.row.map { it.first },
        )
        // Les libellés sont ceux de la référence : « Extra high » (Très élevé), pas « X-High ».
        assertEquals(
            listOf("Minimal", "Low", "Medium", "High", "Extra high", "Max", "Default", "None"),
            plan.row.map { it.second },
        )
        // La liste brute du serveur, elle, n'a pas bougé.
        assertEquals(ReasoningLevels.server, plan.chips)
        // « Ultra » n'existe pas côté serveur : jamais dessiné.
        assertFalse(plan.row.any { (value, _) -> value == "ultra" })
        assertEquals(listOf("Ultra"), plan.missing)
    }

    @Test
    fun `un serveur sans xhigh ne voit pas « Tres eleve » dessine`() {
        val reduced = listOf("minimal" to "Minimal", "low" to "Low", "medium" to "Medium")
        val plan = ReasoningLevels.plan(reduced)
        assertEquals(listOf("minimal", "low", "medium"), plan.row.map { it.first })
        assertTrue(ReasoningLevels.accepts("high", ReasoningLevels.server))
        assertTrue(plan.missing.contains("Extra high"))
    }
}
