package com.onyx.app.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les jeux d'outils d'un bot: ce que `profiles.describe` en dit, et ce qu'un choix écrit.
 *
 * Comme `BotDetails` / `BotChange.toolsets` ( et `:215`): la
 * liste vient du socket, l'écriture passe par `profiles.configure { enabled_toolsets }`, et un
 * profil sans liste propre qui n'a coché personne les a tous.
 */
class ServeBotsToolsetsTest {

    @Test
    fun `describe donne les jeux offerts et ceux du profil`() {
        val read = botToolsetsFrom(
            json(
                """
                {"toolsets":[{"name":"web","enabled":true,"description":"Search the web"},
                             {"name":"terminal","enabled":false},
                             {"name":"skills","enabled":true}],
                 "toolsets_pinned":true}
                """,
            ),
        )

        assertEquals(listOf("web", "terminal", "skills"), read.offered.map { it.name })
        assertEquals("Web search", read.offered.first().title)
        assertEquals("Search the web", read.offered.first().description)
        assertEquals(setOf("web", "skills"), read.enabled)
        assertTrue(read.pinned)
    }

    @Test
    fun `sans liste propre et sans rien de coche, le profil a tout`() {
        // 0.21.5 répond une liste vide pour un profil qui a tout: on lit « tous », .
        val read = botToolsetsFrom(json("""{"toolsets":[{"name":"web"},{"name":"terminal"}],"toolsets_pinned":false}"""))

        assertEquals(setOf("web", "terminal"), read.enabled)
        assertTrue("un jeu sans `enabled` compte comme allumé", read.offered.all { it.enabled })
    }

    @Test
    fun `une liste vide de jeux ne casse rien`() {
        val read = botToolsetsFrom(json("{}"))
        assertEquals(emptyList<GatewayToolset>(), read.offered)
        assertEquals(emptySet<String>(), read.enabled)
        assertTrue(!read.pinned)
        assertEquals(emptySet<String>(), botToolsetsFrom(null).enabled)
    }

    @Test
    fun `ecrire les jeux d'outils nomme le profil et trie les noms`() {
        val params = toolsetsConfigureParams("scout", listOf("skills", "web", "terminal"))

        assertEquals("\"scout\"", params["name"].toString())
        assertEquals("""["skills","terminal","web"]""", params["enabled_toolsets"].toString())
    }

    @Test
    fun `la ligne qui fait parler les bots ecrit le meta et la revision lue`() {
        val params = letBotsMessageParams("default", 4)

        assertEquals("\"default\"", params["name"].toString())
        assertEquals("""{"hermes-bots":{}}""", params["ui_meta"].toString())
        assertEquals("""{"hermes-bots":4}""", params["ui_meta_expected_revisions"].toString())
    }

    @Test
    fun `le titre d'un jeu suit les mots `() {
        assertEquals("To-do list", toolsetTitleFor("todo"))
        assertEquals("Home Assistant", toolsetTitleFor("homeassistant"))
        // Un jeu que ce build ne connaît pas garde son nom, mis en mots.
        assertEquals("Some New Thing", toolsetTitleFor("some_new_thing"))
    }

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
}
