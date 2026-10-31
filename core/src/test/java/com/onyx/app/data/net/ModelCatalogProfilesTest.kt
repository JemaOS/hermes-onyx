package com.onyx.app.data.net

import com.onyx.app.ui.design.ModelChoice
import com.onyx.app.ui.design.ModelSelection
import com.onyx.app.ui.design.ProfileState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'**agrégat des profils** de la feuille de modèle.
 *
 * Le propriétaire ne voyait qu'**une** ligne — l'alias de passerelle `hermes-agent` — et se
 * plaignait de ne pas voir « les modèles que j'ai en LOCAL lié à mon profil Hermes local et mon
 * profil kilo ». Ces tests tiennent les quatre règles qui corrigent cela :
 *
 * 1. **les modèles de tous les profils** apparaissent, chacun avec **son profil** ;
 * 2. le **nom affiché n'est jamais l'alias de passerelle** ;
 * 3. un profil **injoignable** n'efface pas les autres — il apparaît avec son état ;
 * 4. la **recherche** retient par nom, fournisseur **ou profil**.
 *
 * Les corps JSON sont ceux du serveur réel (relevés sur la machine) : `GET api/profiles` et
 * `GET api/model/options?profile=…`.
 */
class ModelCatalogProfilesTest {

    // — Les corps réels du serveur —

    private val profilesBody = """
        {"profiles":[
          {"name":"default","is_default":true,"model":"sage-a1.5","provider":"lmstudio","gateway_running":true},
          {"name":"coder","model":"deepseek/deepseek-v4.1-flash","provider":"kilocode","gateway_running":true},
          {"name":"kilo","model":"deepseek/deepseek-v4.1-flash","provider":"kilocode","gateway_running":true},
          {"name":"scout","model":"deepseek/deepseek-v4.1-flash","provider":"kilocode","gateway_running":true}
        ]}
    """.trimIndent()

    private val defaultOptionsBody = """
        {"model":"sage-a1.5","provider":"lmstudio","providers":[
          {"slug":"lmstudio","name":"LM Studio","is_current":true,"authenticated":true,
           "models":["sage-a1.5","sage-a1.5@q4_k_m","qwen/qwen3.8-27b"]},
          {"slug":"kilocode","name":"Kilo Code","authenticated":true,
           "models":["deepseek/deepseek-v4.1-flash","anthropic/claude-opus-4.6"]}
        ]}
    """.trimIndent()

    private val kiloOptionsBody = """
        {"model":"deepseek/deepseek-v4.1-flash","provider":"kilocode","providers":[
          {"slug":"kilocode","name":"Kilo Code","authenticated":true,
           "models":["deepseek/deepseek-v4.1-flash","anthropic/claude-opus-4.6"]}
        ]}
    """.trimIndent()

    private fun realCatalog(): List<ProfileCatalog> {
        val profiles = ModelCatalog.parseProfiles(profilesBody)
        val options = mapOf(
            "default" to ModelCatalog.parseModelOptions(defaultOptionsBody),
            "kilo" to ModelCatalog.parseModelOptions(kiloOptionsBody),
            // « coder » et « scout » : leur liste ne répond pas (délai / 401 sur le serveur réel).
        )
        return ModelCatalog.buildCatalog(profiles, options)
    }

    // — 1) Le registre des profils se lit, sans rien inventer —

    @Test
    fun `le registre des profils garde le nom, le modele et le fournisseur`() {
        val rows = ModelCatalog.parseProfiles(profilesBody)
        assertEquals(listOf("default", "coder", "kilo", "scout"), rows.map { it.name })
        assertEquals("sage-a1.5", rows.first { it.name == "default" }.model)
        assertEquals("lmstudio", rows.first { it.name == "default" }.provider)
        assertTrue(rows.first { it.name == "default" }.isDefault)
        assertEquals("deepseek/deepseek-v4.1-flash", rows.first { it.name == "kilo" }.model)
    }

    @Test
    fun `la liste des modeles se lit par fournisseur, et rien n est invente`() {
        val providers = ModelCatalog.parseModelOptions(defaultOptionsBody)
        assertEquals(listOf("lmstudio", "kilocode"), providers.map { it.slug })
        assertEquals(3, providers.first { it.slug == "lmstudio" }.models.size)
        assertTrue(providers.first { it.slug == "lmstudio" }.models.contains("sage-a1.5"))
        // Un corps illisible ou vide ne rend rien, sans exception.
        assertTrue(ModelCatalog.parseModelOptions("").isEmpty())
        assertTrue(ModelCatalog.parseModelOptions("nope").isEmpty())
        assertTrue(ModelCatalog.parseProfiles("").isEmpty())
    }

    // — 2) Tous les profils apparaissent, chacun avec son profil —

    @Test
    fun `les modeles de tous les profils apparaissent, le profil sur chaque rangee`() {
        val catalog = realCatalog()
        assertEquals(listOf("default", "coder", "kilo", "scout"), catalog.map { it.profile })

        val all = catalog.flatMap { it.models }
        // Le modèle **local** du profil `default`...
        assertTrue("le local doit apparaître", all.any { it.id == "sage-a1.5" })
        // ...et les modèles **distants** du profil `kilo`.
        assertTrue("le distant doit apparaître", all.any { it.id == "deepseek/deepseek-v4.1-flash" })
        // **Chaque** rangée porte son profil, et sa clé de liste le qualifie.
        assertTrue(all.all { !it.profile.isNullOrBlank() })
        assertEquals(all.size, all.map { it.listKey }.toSet().size)
        val local = all.single { it.id == "sage-a1.5" }
        assertEquals("default", local.profile)
        assertEquals("lmstudio", local.provider)
        assertEquals("default · lmstudio", local.rowSubtitle())
        // Le profil écrit **son** modèle configuré d'abord, puis ses fournisseurs dans l'ordre du
        // serveur : le modèle **local** du profil `default` reste en tête de son profil.
        val defaultModels = catalog.single { it.profile == "default" }.models
        assertEquals("sage-a1.5", defaultModels.first().id)
        assertEquals("lmstudio", defaultModels.first().provider)
        // Le même modèle sous deux profils ne se confond pas : deux clés de liste distinctes.
        val deepseek = all.filter { it.id == "deepseek/deepseek-v4.1-flash" }
        assertTrue(deepseek.size >= 2)
        assertEquals(deepseek.size, deepseek.map { it.listKey }.toSet().size)
    }

    // — 3) Le nom affiché n'est jamais l'alias de passerelle —

    @Test
    fun `l alias de passerelle n est jamais un nom de modele`() {
        // Le registre d'un serveur peut porter l'alias comme modèle : il ne doit pas sortir.
        val profiles = ModelCatalog.parseProfiles(
            """{"profiles":[{"name":"default","model":"hermes-agent","provider":"hermes"}]}""",
        )
        val options = mapOf("default" to ModelCatalog.parseModelOptions(
            """{"providers":[{"slug":"hermes","models":["hermes-agent"]}]}""",
        ))
        val catalog = ModelCatalog.buildCatalog(profiles, options)
        val all = catalog.flatMap { it.models }
        assertTrue("l'alias ne doit pas être un nom de modèle", all.none { it.id == "hermes-agent" })
        assertTrue(all.none { ModelCatalog.isGatewayAlias(it.id) })
        assertTrue(ModelCatalog.isGatewayAlias("hermes-agent"))
        assertTrue(ModelCatalog.isGatewayAlias(" Hermes-Agent "))
        assertFalse(ModelCatalog.isGatewayAlias("sage-a1.5"))
        // `usable` écarte l'alias, garde le reste.
        assertEquals(
            listOf("sage-a1.5"),
            ModelCatalog.usable(listOf(ModelChoice("hermes-agent"), ModelChoice("sage-a1.5"))).map { it.id },
        )
    }

    // — 4) Un profil injoignable n'efface pas les autres —

    @Test
    fun `un profil injoignable garde sa place, les autres restent visibles`() {
        val catalog = realCatalog()
        // « coder » et « scout » n'ont pas répondu : leur modèle configuré est connu → PARTIAL.
        val coder = catalog.single { it.profile == "coder" }
        assertEquals(ProfileState.PARTIAL, coder.state)
        assertEquals(listOf("deepseek/deepseek-v4.1-flash"), coder.models.map { it.id })
        // Les profils joignables restent **entiers**, à côté.
        val default = catalog.single { it.profile == "default" }
        assertEquals(ProfileState.READY, default.state)
        assertTrue(default.models.any { it.id == "sage-a1.5" })
        assertTrue(catalog.single { it.profile == "kilo" }.models.isNotEmpty())

        // Un profil dont on n'a **rien** (aucun modèle configuré, aucune liste) sort UNREACHABLE —
        // jamais absent.
        val blind = ModelCatalog.buildCatalog(
            ModelCatalog.parseProfiles("""{"profiles":[{"name":"ghost"}]}"""),
            emptyMap(),
        )
        assertEquals(ProfileState.UNREACHABLE, blind.single().state)
        assertTrue(blind.single().models.isEmpty())
    }

    // — La recherche retient aussi par profil —

    @Test
    fun `la recherche retient par nom, fournisseur ou profil`() {
        val all = realCatalog().flatMap { it.models }
        // Par **profil** : « scout » ne retient que les rangées du profil scout (son modèle est
        // distant, donc le nom ne contient pas « scout » — seul le profil le fait).
        val scout = ModelSelection.filter(all, "scout")
        assertTrue(scout.isNotEmpty())
        assertTrue(scout.all { it.profile == "scout" })
        // Par fournisseur : « lmstudio » ne retient que le local du profil `default`.
        val local = ModelSelection.filter(all, "lmstudio")
        assertTrue(local.isNotEmpty())
        assertTrue(local.all { it.provider == "lmstudio" })
        assertTrue(local.any { it.id == "sage-a1.5" })
        // Par nom de modèle (le préfixe de variante « @q4_k_m » répond aussi : c'est le même nom).
        val named = ModelSelection.filter(all, "sage-a1.5")
        assertTrue(named.isNotEmpty())
        assertTrue(named.all { it.id.contains("sage-a1.5") })
        assertTrue(named.any { it.id == "sage-a1.5" })
    }
}
