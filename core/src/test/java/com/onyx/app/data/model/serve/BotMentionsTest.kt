package com.onyx.app.data.model.serve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La liste des mentions `@` du composeur : quand elle s'ouvre, ce qu'elle propose, ce qu'un tap
 * écrit dans le brouillon, et ce qu'une mention inconnue devient.
 *
 * La référence est `BotMentions` (Echo/Services/Transport/ServerBots:302) pour le mot en
 * cours, le filtrage et l'insertion, et `ComposerView.mentionMenu`
 * (Echo/Views/ComposerView:306) pour la liste elle-même — les mêmes bots, dans le même
 * ordre, avec les mêmes bornes.
 */
class BotMentionsTest {

    private val main = BotProfile(name = "default", isDefault = true)
    private val scout = BotProfile(name = "scout", title = "Research Buddy", previousNames = listOf("finder"))
    private val quill = BotProfile(name = "writer", displayName = "Quill")
    private val fixit = BotProfile(name = "fixit", title = "Fix-it", lastActive = 1_000L)

    /** Le roster du banc d'essai, dans l'ordre où la liste le reçoit. */
    private val roster = listOf(main, scout, quill, fixit)

    /** Le bot sans nom amical : ce qu'un tap écrit est exactement sa poignée. */
    private val plainScout = BotProfile(name = "scout", lastActive = 500L)

    // MARK: Le déclencheur

    @Test
    fun `le mot en cours s ouvre au debut du brouillon ou apres une espace`() {
        assertEquals("sc", BotMentions.typing("@sc"))
        assertEquals("", BotMentions.typing("@"))
        assertEquals("", BotMentions.typing("Ask @"))
        assertEquals("sc", BotMentions.typing("Ask @sc"))
        assertEquals("sc", BotMentions.typing("Peux-tu demander à @sc"))
        // Rien qui commence par « @ » : aucun mot en cours.
        assertNull(BotMentions.typing("Ask Onyx"))
        assertNull(BotMentions.typing("Ask @scout et la suite"))
    }

    @Test
    fun `une adresse ne propose rien`() {
        // Le « @ » doit ouvrir le mot : dans une adresse, il est au milieu.
        assertNull(BotMentions.typing("mail me at sam@example.com"))
        assertNull(BotMentions.typing("sam@example"))
        assertTrue(BotMentions.menu("mail me at sam@example.com", roster, "writer").isEmpty())
        // Une espace devant le mot, en revanche, en fait bien une mention.
        assertEquals("example", BotMentions.typing("mail sam @example"))
    }

    @Test
    fun `un brouillon de plusieurs lignes n ouvre pas la liste`() {
        val drafted = "première ligne\nAsk @sc"
        assertEquals("sc", BotMentions.typing(drafted))
        assertTrue(BotMentions.menu(drafted, roster, "writer").isEmpty())
    }

    @Test
    fun `un mot termine referme la liste`() {
        assertNull(BotMentions.typing("Ask @scout et"))
        assertTrue(BotMentions.menu("Ask @scout et", roster, "writer").isEmpty())
        assertTrue(BotMentions.menu("Ask @sc,", roster, "writer").isEmpty())
    }

    // MARK: Le filtrage

    @Test
    fun `le filtrage suit ce qui suit le arobase`() {
        // Sans rien après le « @ » : tout le roster, moins celui à qui l'on parle.
        assertEquals(
            listOf("default", "scout", "fixit"),
            BotMentions.menu("Ask @", roster, "writer").map { it.name },
        )
        assertEquals(listOf("scout"), BotMentions.menu("Ask @sc", roster, "writer").map { it.name })
        assertEquals(listOf("fixit"), BotMentions.menu("Ask @fix", roster, "writer").map { it.name })
        assertEquals(listOf("default"), BotMentions.menu("Ask @her", roster, "writer").map { it.name })
    }

    @Test
    fun `le filtrage prend la poignee, le nom du profil ou le nom amical`() {
        // `BotMentions.forms` : la poignée, le nom du profil, et les formes du titre.
        assertEquals(listOf("scout"), BotMentions.menu("Ask @res", roster, "writer").map { it.name })
        assertEquals(listOf("scout"), BotMentions.menu("Ask @research", roster, "writer").map { it.name })
        assertEquals(listOf("scout"), BotMentions.menu("Ask @researchbuddy", roster, "writer").map { it.name })
        // Le libellé affiché compte aussi, quelle que soit la casse.
        assertEquals(listOf("writer"), BotMentions.menu("Ask @qu", roster, "default").map { it.name })
        assertEquals(listOf("scout"), BotMentions.menu("Ask @SC", roster, "writer").map { it.name })
    }

    @Test
    fun `le bot a qui l on parle ne se propose pas`() {
        assertEquals(listOf("default", "writer", "fixit"), BotMentions.menu("Ask @", roster, "scout").map { it.name })
        assertTrue(BotMentions.menu("Ask @hermes", roster, "default").isEmpty())
        assertTrue(BotMentions.menu("Ask @quill", roster, "writer").isEmpty())
    }

    @Test
    fun `une mention qui ne nomme personne ne propose rien`() {
        assertTrue(BotMentions.menu("Ask @nobody", roster, "writer").isEmpty())
        assertTrue(BotMentions.menu("Ask @zzz", roster, "writer").isEmpty())
    }

    @Test
    fun `au plus six bots sont proposes`() {
        val many = listOf(main) + (1..8).map { BotProfile(name = "bot$it", title = "Bot $it") }
        assertEquals(6, BotMentions.menu("Ask @", many, "writer").size)
    }

    // MARK: L'insertion

    @Test
    fun `toucher une ligne finit le mot et garde le reste du brouillon`() {
        assertEquals("Ask @scout ", BotMentions.complete("Ask @", plainScout))
        assertEquals("Ask @scout ", BotMentions.complete("Ask @sc", plainScout))
        // Le mot qui précédait, l'espace qui l'ouvrait et le reste sont gardés tels quels.
        assertEquals("Peux-tu demander à @scout ", BotMentions.complete("Peux-tu demander à @sc", plainScout))
    }

    @Test
    fun `l insertion ecrit le nom amical du bot quand il en a un`() {
        assertEquals("Ask @research-buddy ", BotMentions.complete("Ask @res", scout))
        assertEquals("Ask @fix-it ", BotMentions.complete("Ask @fi", fixit))
        assertEquals("Ask @quill ", BotMentions.complete("Ask @qu", quill))
        assertEquals("Ask @hermes ", BotMentions.complete("Ask @", main))
    }

    @Test
    fun `avec plusieurs mots, seul celui en cours est remplace et le curseur suit`() {
        // Le mot déjà écrit ne bouge pas ; le dernier est fini, et le curseur se pose après
        // l'espace finale, prêt pour la suite.
        assertEquals("Ask @wr and @fix-it ", BotMentions.complete("Ask @wr and @fi", fixit))
        assertEquals("Ask @scout and @fix-it ", BotMentions.complete("Ask @scout and @", fixit))
        // Un mot fini plus haut garde la forme qu'on lui a donnée : rien d'autre n'est réécrit.
        assertEquals(
            "Ask @research-buddy et @hermes ",
            BotMentions.complete("Ask @research-buddy et @", main),
        )
    }

    @Test
    fun `sans mot en cours, le brouillon est rendu tel quel`() {
        assertEquals("Ask @nobody about it", BotMentions.complete("Ask @nobody about it", plainScout))
        assertEquals("mail me at sam@example.com", BotMentions.complete("mail me at sam@example.com", plainScout))
        assertEquals("", BotMentions.complete("", plainScout))
    }

    // MARK: Echap

    @Test
    fun `Echap ferme la liste, et le mot suivant la rouvre`() {
        assertEquals(listOf("scout"), BotMentions.menu("Ask @sc", roster, "writer").map { it.name })
        assertTrue(BotMentions.menu("Ask @sc", roster, "writer", dismissed = "sc").isEmpty())
        // Le mot a changé : la liste revient.
        assertEquals(listOf("scout"), BotMentions.menu("Ask @sco", roster, "writer", dismissed = "sc").map { it.name })
    }

    // MARK: Les noms et le roster

    @Test
    fun `les noms amiables deviennent des poignees, jamais les mots reserves`() {
        assertEquals(listOf("research-buddy", "researchbuddy"), BotMentions.nameForms("Research Buddy"))
        assertTrue(BotMentions.nameForms("Hermes").isEmpty())
        assertTrue(BotMentions.nameForms("All").isEmpty())
        assertEquals("research-buddy", BotMentions.tag(scout))
        assertEquals("quill", BotMentions.tag(quill))
        assertEquals("hermes", BotMentions.tag(main))
        assertEquals("scout", BotMentions.tag(plainScout))
        // Tout ce à quoi un bot répond, poignée et nom du profil compris.
        assertTrue(BotMentions.forms(scout).containsAll(listOf("scout", "research-buddy", "researchbuddy")))
        assertTrue(BotMentions.forms(main).contains("hermes"))
    }

    @Test
    fun `le roster des mentions cache les bots caches et s arrete au principal seul`() {
        val hidden = BotProfile(name = "old", isHidden = true)
        val quiet = BotProfile(name = "writer")
        val busy = BotProfile(name = "scout", lastActive = 1_000L)

        // Le principal d'abord, puis le plus récemment parlé, puis le nom.
        assertEquals(
            listOf("default", "scout", "writer"),
            BotMentions.rosterForMentions(listOf(quiet, hidden, busy, main)).map { it.name },
        )
        // Seul le principal : il n'y a personne à qui parler.
        assertTrue(BotMentions.rosterForMentions(listOf(main)).isEmpty())
        assertTrue(BotMentions.rosterForMentions(listOf(main, hidden)).isEmpty())
    }
}
