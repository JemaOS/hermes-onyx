package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le lecteur de `GET /v1/models`.
 *
 * Le serveur ne rend pas les mêmes champs d'une version à l'autre : ce test vérifie que le lecteur
 * **garde ce qui est présent** et **n'invente rien** — un endpoint nu ne donne que l'identifiant, et
 * le modèle n'a alors **aucun prix**.
 */
class ModelCatalogTest {

    @Test
    fun `un endpoint nu ne donne que l identifiant, sans prix`() {
        val body = """{"data":[{"id":"gpt-5"},{"id":"anthropic/claude-4"}]}"""
        val models = ModelCatalog.parse(body)
        assertEquals(listOf("gpt-5", "anthropic/claude-4"), models.map { it.id })
        assertEquals("local", models[0].provider)
        assertNull("aucun prix ne doit être inventé", models[0].subtitle().let { if (it == "local") null else it })
        assertNull(models[0].priceLine())
        assertEquals(0, models[0].contextLength ?: 0)
    }

    @Test
    fun `un endpoint riche garde le fournisseur, le contexte et le tarif`() {
        val body = """
            {"data":[
              {"id":"openai/gpt-5","owned_by":"openai","context_length":400000,
               "pricing":{"prompt":"0.000003","completion":"0.000015"}}
            ]}
        """.trimIndent()
        val model = ModelCatalog.parse(body).single()
        assertEquals("openai", model.provider)
        assertEquals(400_000, model.contextLength)
        assertEquals(3.0, model.promptPricePerMTok!!, 1e-9)
        assertEquals(15.0, model.completionPricePerMTok!!, 1e-9)
        assertEquals("openai · \$3 in · \$15 out", model.subtitle())
    }

    @Test
    fun `un tableau nu est accepte aussi`() {
        val models = ModelCatalog.parse("""[{"id":"a"},{"id":"b"}]""")
        assertEquals(listOf("a", "b"), models.map { it.id })
    }

    @Test
    fun `un corps illisible rend une liste vide, jamais une exception`() {
        assertTrue(ModelCatalog.parse("").isEmpty())
        assertTrue(ModelCatalog.parse("not json").isEmpty())
        assertTrue(ModelCatalog.parse("""{"data":[]}""").isEmpty())
        // Une entrée sans identifiant ne se devine pas : elle est ignorée.
        assertTrue(ModelCatalog.parse("""{"data":[{"owned_by":"x"}]}""").isEmpty())
    }

    @Test
    fun `une fenetre de contexte alternative est lue`() {
        val model = ModelCatalog.parse("""{"data":[{"id":"m","context_window":128000}]}""").single()
        assertEquals(128_000, model.contextLength)
    }
}
