package com.onyx.app.shortcuts

import com.onyx.app.data.model.serve.BotProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La règle de sélection des raccourcis de bots : quels bots, dans quel ordre, et combien.
 *
 * C'est la règle que le menu d'appui long applique — les favoris d'abord, puis les plus récemment
 * actifs, puis l'ordre du serveur —, bornée par la place que la plateforme laisse.
 */
class BotShortcutPlanTest {

    private fun bot(
        name: String,
        lastActive: Long? = null,
        isDefault: Boolean = false,
        chatId: String? = null,
        previous: List<String> = emptyList(),
        title: String? = null,
    ): BotProfile = BotProfile(
        name = name,
        title = title,
        lastActive = lastActive,
        isDefault = isDefault,
        chatId = chatId,
        previousNames = previous,
    )

    @Test
    fun `les favoris passent avant les plus recents`() {
        val bots = listOf(
            bot("kilo", lastActive = 500),
            bot("hermes", lastActive = 900, isDefault = true),
            bot("coder", lastActive = 100),
        )
        val ordered = BotShortcutPlan.arrange(bots, listOf("bot:coder", "bot:kilo"))
        assertEquals(listOf("coder", "kilo", "hermes"), ordered.map { it.name })
    }

    @Test
    fun `sans ordre a la main les plus recents passent devant`() {
        val bots = listOf(
            bot("kilo", lastActive = 100),
            bot("hermes", lastActive = 900),
            bot("coder", lastActive = 500),
        )
        val ordered = BotShortcutPlan.arrange(bots, emptyList())
        assertEquals(listOf("hermes", "coder", "kilo"), ordered.map { it.name })
    }

    @Test
    fun `un bot sans activite connue passe apres ceux qui en ont`() {
        val bots = listOf(bot("kilo"), bot("hermes", lastActive = 10), bot("coder"))
        val ordered = BotShortcutPlan.arrange(bots, emptyList())
        assertEquals(listOf("hermes", "kilo", "coder"), ordered.map { it.name })
    }

    @Test
    fun `un bot renomme reste suivi par son ancien nom dans l ordre`() {
        val bots = listOf(
            bot("kilo", lastActive = 900),
            bot("coder", lastActive = 100, previous = listOf("onyx-qa")),
        )
        val ordered = BotShortcutPlan.arrange(bots, listOf("bot:onyx-qa", "bot:kilo"))
        assertEquals(listOf("coder", "kilo"), ordered.map { it.name })
    }

    @Test
    fun `la liste publiee est bornee par la place laissee`() {
        val bots = (1..9).map { bot("bot$it", lastActive = it.toLong()) }
        assertEquals(4, BotShortcutPlan.schedule(bots, emptyList(), "Hermes").size)
        assertEquals(2, BotShortcutPlan.schedule(bots, emptyList(), "Hermes", maxBots = 2).size)
        assertTrue(BotShortcutPlan.schedule(bots, emptyList(), "Hermes", maxBots = 0).isEmpty())
    }

    @Test
    fun `le raccourci porte le libelle du bot et son chat nomme`() {
        val scheduled = BotShortcutPlan.schedule(
            bots = listOf(bot("kilo", title = "Kilo", chatId = "sess-42")),
            order = emptyList(),
            mainName = "Hermes",
        )
        assertEquals(1, scheduled.size)
        assertEquals("Kilo", scheduled.first().label)
        assertEquals("sess-42", scheduled.first().chatId)
        assertTrue(scheduled.first().isDefault.not())
        assertEquals("Chat with Kilo", BotShortcutPlan.longLabel(scheduled.first()))
    }

    @Test
    fun `le profil principal prend le libelle du serveur`() {
        val scheduled = BotShortcutPlan.schedule(
            bots = listOf(bot("default", isDefault = true)),
            order = emptyList(),
            mainName = "Hermes",
        )
        assertEquals("Hermes", scheduled.first().label)
        assertTrue(scheduled.first().isDefault)
    }

    @Test
    fun `un chat nomme vide ne voyage pas dans le raccourci`() {
        val scheduled = BotShortcutPlan.schedule(
            bots = listOf(bot("kilo", chatId = "  "), bot("coder")),
            order = emptyList(),
            mainName = "Hermes",
        )
        assertEquals(listOf(null, null), scheduled.map { it.chatId })
    }

    @Test
    fun `l identifiant est stable et derive du nom de profil`() {
        assertEquals(BotShortcutPlan.id("kilo"), BotShortcutPlan.id("kilo"))
        assertNotEquals(BotShortcutPlan.id("kilo"), BotShortcutPlan.id("coder"))
        // Deux profils que seule la casse distingue restent deux profils.
        assertNotEquals(BotShortcutPlan.id("Kilo"), BotShortcutPlan.id("kilo"))
    }

    @Test
    fun `deux noms que le nettoyage confond gardent des identifiants distincts`() {
        assertNotEquals(BotShortcutPlan.id("a b"), BotShortcutPlan.id("a-b"))
        assertNotEquals(BotShortcutPlan.id("élan"), BotShortcutPlan.id("Elan"))
    }

    @Test
    fun `l identifiant ne porte que des caracteres sûrs pour le lanceur`() {
        val id = BotShortcutPlan.id("qa tester.élan/2")
        assertTrue(id, id.all { it.isLetterOrDigit() || it == '-' })
        assertTrue(id, id.startsWith("bot-"))
        assertTrue(id, id.length <= 64)
    }

    @Test
    fun `la tuile porte le visage resolu du roster, pas celui du nom`() {
        // La section repartit les visages entre les bots : la couleur du visage de `kilo` dans le
        // roster n'est pas celle que son seul nom donnerait. C'est **celle du roster** qui peinte la
        // tuile, sinon le raccourci porterait une autre couleur que le reste de l'app.
        val code = com.onyx.app.ui.design.BotFace.forName("coder").code()
        val scheduled = BotShortcutPlan.schedule(
            bots = listOf(
                BotProfile(name = "kilo", faceCode = code, title = "Kilo"),
                BotProfile(name = "hermes"),
            ),
            order = emptyList(),
            mainName = "Hermes",
        )
        assertEquals(code, scheduled.first { it.bot == "kilo" }.faceCode)
        assertEquals(null, scheduled.first { it.bot == "hermes" }.faceCode)
    }

    @Test
    fun `le nom des trois raccourcis d action ne bouge pas`() {
        assertEquals(listOf("ask", "talk", "note"), BotShortcutPlan.ACTION_IDS)
        assertTrue(BotShortcutPlan.FIRST_RANK > 0)
    }
}
