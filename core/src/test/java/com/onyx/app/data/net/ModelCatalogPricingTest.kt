package com.onyx.app.data.net

import com.onyx.app.ui.design.ModelSelection
import com.onyx.app.ui.design.ServerPrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La provenance des prix** : la seule table de prix de l'app est celle que le serveur publie.
 *
 * `GET api/model/options?profile=…` rend, pour chaque fournisseur connecté, une clé `pricing` —
 * `{ "<identifiant>": { "input": "$0.30", "output": "$1.20", "cache": "$0.006", "free": false } }`.
 * Les corps de ces tests sont **recopiés du serveur réel** (relevé du 28 octobre 2026 sur
 * `:8642/api/model/options`) : `LM Studio` (le local, **aucune** clé `pricing`) et `Kilo Code` (le
 * distant, 369 modèles tarifés sur 380).
 *
 * Ce qui est tenu ici :
 *
 * 1. le montant publié est **gardé tel quel** — l'app ne le calcule pas ;
 * 2. un fournisseur **sans** table de prix laisse ses modèles **sans prix** : la pastille n'écrit
 *    alors que son nom, jamais un montant de remplissage ;
 * 3. une entrée illisible est **écartée** — un modèle se lit sans prix, jamais à zéro.
 */
class ModelCatalogPricingTest {

    /** Le corps réel de la route, réduit à ce que ces tests interrogent. */
    private val optionsBody = """
        {"providers":[
          {"slug":"lmstudio","name":"LM Studio","is_current":true,"authenticated":true,
           "models":["sage-a1.5","minimax/minimax-m2.7","qwen/qwen3.8-27b"],
           "capabilities":{"sage-a1.5":{"fast":false,"reasoning":true}}},
          {"slug":"kilocode","name":"Kilo Code","authenticated":true,
           "models":["deepseek/deepseek-v4.1-flash","minimax/minimax-m2.7","kilo-auto/free",
                     "google/gemini-3-pro-preview"],
           "pricing":{
             "deepseek/deepseek-v4.1-flash":{"input":"${'$'}0.30","output":"${'$'}1.20","cache":"${'$'}0.006","free":false},
             "minimax/minimax-m2.7":{"input":"${'$'}0.30","output":"${'$'}1.20","cache":"${'$'}0.06","free":false},
             "kilo-auto/free":{"input":"free","output":"free","cache":null,"free":true},
             "google/gemini-3-pro-preview":{"input":null,"output":null,"cache":null,"free":false}
           }}
        ]}
    """.trimIndent()

    private val profilesBody = """
        {"profiles":[{"name":"default","is_default":true,"model":"sage-a1.5","provider":"lmstudio"}]}
    """.trimIndent()

    @Test
    fun `la table de prix du serveur est lue telle quelle, et rattachee au bon fournisseur`() {
        val providers = ModelCatalog.parseModelOptions(optionsBody)
        val prices = providers.single { it.slug == "kilocode" }.pricing
        assertEquals(3, prices.size)
        assertEquals(ServerPrice("${'$'}0.30", "${'$'}1.20", "${'$'}0.006"), prices["deepseek/deepseek-v4.1-flash"])
        assertEquals("0,30 ${'$'} / 1,20 ${'$'}", prices.getValue("minimax/minimax-m2.7").line())
        // Le fournisseur **local** ne publie aucune table : rien n'est inventé pour lui.
        assertTrue(providers.single { it.slug == "lmstudio" }.pricing.isEmpty())
        // Et son nom est bien celui que le serveur écrit.
        assertEquals("LM Studio", providers.single { it.slug == "lmstudio" }.name)
        assertEquals("Kilo Code", providers.single { it.slug == "kilocode" }.name)
    }

    @Test
    fun `une entree sans montant exploitable est ecartee, pas gardee a zero`() {
        val prices = ModelCatalog.parseModelOptions(optionsBody).single { it.slug == "kilocode" }.pricing
        assertFalse(
            "un modèle publié sans montant ne doit pas devenir « 0 $ »",
            prices.containsKey("google/gemini-3-pro-preview"),
        )
        // Le modèle gratuit, lui, est bien là — et il le dit.
        assertEquals("gratuit", prices.getValue("kilo-auto/free").line())
    }

    @Test
    fun `le modele configure d un profil recoit le nom et le prix de son fournisseur`() {
        // Le profil `kilo` sert `deepseek/deepseek-v4.1-flash` chez `kilocode` : la rangée du modèle
        // configuré doit porter la **même** pastille tarifée que la ligne du fournisseur.
        val profiles = ModelCatalog.parseProfiles(
            """{"profiles":[{"name":"kilo","model":"deepseek/deepseek-v4.1-flash","provider":"kilocode"}]}""",
        )
        val catalog = ModelCatalog.buildCatalog(
            profiles = profiles,
            options = mapOf("kilo" to ModelCatalog.parseModelOptions(optionsBody)),
        )
        val configured = catalog.single().models.first()
        assertEquals("deepseek/deepseek-v4.1-flash", configured.id)
        assertEquals("kilocode", configured.provider)
        assertEquals("Kilo Code", configured.providerName)
        assertEquals("${'$'}0.30", configured.price?.input)

        // Le modèle local du profil `default` : un nom de fournisseur, et **aucun** prix.
        val localProfiles = ModelCatalog.parseProfiles(profilesBody)
        val localCatalog = ModelCatalog.buildCatalog(
            profiles = localProfiles,
            options = mapOf("default" to ModelCatalog.parseModelOptions(optionsBody)),
        )
        assertEquals("sage-a1.5", localCatalog.single().models.first().id)
        assertEquals("LM Studio", localCatalog.single().models.first().providerName)
        assertNull("le local n'a aucun prix : il ne doit pas en porter un", localCatalog.single().models.first().price)
    }

    @Test
    fun `la feuille agregee montre le local et le distant sur la meme rangee`() {
        // Le catalogue complet du profil `default`, tel que la feuille le reçoit.
        val catalog = ModelCatalog.buildCatalog(
            profiles = ModelCatalog.parseProfiles(profilesBody),
            options = mapOf("default" to ModelCatalog.parseModelOptions(optionsBody)),
        )
        val entries = ModelSelection.entries(catalog.flatMap { it.models })
        val shared = entries.single { it.id == "minimax/minimax-m2.7" }
        assertEquals(
            listOf("LM Studio", "Kilo Code · 0,30 ${'$'} / 1,20 ${'$'}"),
            shared.offerChips(),
        )
        // Le local, en tête de son profil, reste sans prix.
        assertEquals("sage-a1.5", entries.first().id)
        assertEquals(listOf("LM Studio"), entries.first().offerChips())
        // Le forfait gratuit : le serveur le dit, la pastille le répète.
        val free = entries.single { it.id == "kilo-auto/free" }
        assertEquals(listOf("Kilo Code · gratuit"), free.offerChips())
        // Aucune pastille de l'ensemble ne porte de montant pour un modèle non tarifé.
        val untarifed = entries.single { it.id == "google/gemini-3-pro-preview" }
        assertEquals(listOf("Kilo Code"), untarifed.offerChips())
    }

    @Test
    fun `un corps sans table de prix ne fait naitre aucun montant`() {
        val providers = ModelCatalog.parseModelOptions(
            """{"providers":[{"slug":"lmstudio","name":"LM Studio","models":["sage-a1.5"]}]}""",
        )
        assertTrue(providers.single().pricing.isEmpty())
        // Une table illisible ne lève rien : elle rend une table vide.
        assertTrue(ModelCatalog.parsePricing(null).isEmpty())
        assertTrue(ModelCatalog.parseModelOptions("nope").isEmpty())
    }
}
