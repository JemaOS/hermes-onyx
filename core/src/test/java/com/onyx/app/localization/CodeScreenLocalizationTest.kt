package com.onyx.app.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Locale

/**
 * L'écran **Code** parle français par le dictionnaire central, et son onglet ne se confond pas avec
 * le titre de section « Discussions ».
 *
 * Le piège que ce test ferme : « Chats » reste le titre de section de la liste (« Discussions »),
 * alors que l'onglet de la rangée dit « Chat » — une seule clé pour les deux ferait basculer le
 * titre de section en même temps.
 */
class CodeScreenLocalizationTest {

    private val fr = Locale.FRANCE

    private fun translate(key: String) = OnyxLocalization.translate(key, fr)

    @Test
    fun `l onglet Chat est plus court que le titre de section Discussions`() {
        assertEquals("Chat", translate("Chat"))
        assertEquals("Discussions", translate("Chats"))
    }

    @Test
    fun `les quatre filtres sont en francais`() {
        assertEquals("Tout", translate("Everything"))
        assertEquals("En cours", translate("Running"))
        assertEquals("À toi", translate("Yours"))
        assertEquals("Prêt", translate("Ready"))
    }

    @Test
    fun `l etat vide et la carte sont en francais`() {
        val keys = listOf(
            "No code session yet",
            "No session matches this filter.",
            "Pick a repository below, describe a change, then send: each message starts its own code session.",
            "Describe a change…",
            "Repository",
            "Worktree",
            "New worktree",
            "Held on this phone",
            "Branch",
        )
        keys.forEach { key ->
            val value = translate(key)
            assertNotEquals("clé non traduite : $key", key, value)
        }
        assertEquals("Aucune session de code pour le moment", translate("No code session yet"))
        assertEquals("Décris un changement…", translate("Describe a change…"))
        assertEquals("Tenue sur ce téléphone", translate("Held on this phone"))
    }

    @Test
    fun `le temps ecoule prend la tournure francaise`() {
        assertEquals("il y a 3 s", translate("3 s ago"))
        assertEquals("il y a 12 min", translate("12 min ago"))
    }
}
