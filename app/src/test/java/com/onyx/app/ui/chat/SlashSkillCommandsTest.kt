package com.onyx.app.ui.chat

import com.onyx.app.data.net.DashboardSkill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Les compétences du profil dans le menu « / ».**
 *
 * Ce que ce verrou tient :
 *
 *  1. les **six commandes fixes** restent là, dans le même ordre — le lot ajoute, il ne remplace pas ;
 *  2. une compétence devient une commande de la **même forme** qu'une commande fixe (nom, libellé,
 *     détail, action), et prend son instruction en argument ;
 *  3. un profil **sans compétence** redonne **exactement** la liste d'avant.
 */
class SlashSkillCommandsTest {

    private fun skill(
        name: String,
        description: String = "",
        enabled: Boolean = true,
    ) = DashboardSkill(name = name, description = description, enabled = enabled)

    @Test
    fun `les six commandes fixes restent et gardent leur ordre`() {
        assertEquals(
            listOf("/model", "/new", "/find", "/settings", "/voice", "/help"),
            SlashCommands.defaultCommands.map { it.name },
        )
        assertEquals(
            listOf(
                SlashAction.OPEN_MODEL_PICKER,
                SlashAction.NEW_CONVERSATION,
                SlashAction.FIND,
                SlashAction.SETTINGS,
                SlashAction.VOICE,
                SlashAction.HELP,
            ),
            SlashCommands.defaultCommands.map { it.action },
        )
    }

    @Test
    fun `un profil sans competence redonne exactement la liste d avant`() {
        assertEquals(SlashCommands.defaultCommands, SlashCommands.commands())
        assertEquals(SlashCommands.defaultCommands, SlashCommands.commands(emptyList()))
        // Les compétences passent **après** les six fixes, jamais devant.
        assertEquals(
            SlashCommands.defaultCommands.map { it.name },
            SlashCommands.commands(listOf(skill("youtube-content"))).take(6).map { it.name },
        )
    }

    @Test
    fun `une competence prend la meme forme qu une commande`() {
        val commands = SlashCommands.commands(listOf(skill("youtube-content", "Résume une vidéo")))
        val fixed = commands.first { it.name == "/model" }
        val fromSkill = commands.last()

        assertEquals("/youtube-content", fromSkill.name)
        assertEquals(fromSkill.name, fromSkill.display)
        assertEquals("Résume une vidéo", fromSkill.detail)
        // Même forme : un nom qui commence par « / », un détail non vide, une action.
        assertTrue(fromSkill.name.startsWith("/"))
        assertTrue(fromSkill.detail.isNotEmpty())
        assertTrue(fromSkill.skill)
        assertFalse(fixed.skill)
        assertEquals(SlashAction.INVOKE_SKILL, fromSkill.action)

        // Le choix de la compétence est **la même action** que sa commande, avec ou sans instruction.
        assertEquals(fromSkill.action, SlashCommands.action(fromSkill.name, commands))
        assertEquals(fromSkill.action, SlashCommands.action("/youtube-content résume ce fil", commands))
        assertEquals(fromSkill, SlashCommands.commandFor(fromSkill.name, commands))

        // Et le menu la propose, sans la confondre avec les commandes fixes.
        val menu = SlashCommands.matches("/you", commands)
        assertEquals(listOf("/youtube-content"), menu.map { it.name })

        // `/help` l'énumère comme les autres.
        assertTrue(SlashCommands.helpText(commands).contains("/youtube-content"))
    }

    @Test
    fun `le slug suit la forme du serveur`() {
        assertEquals("youtube-content", SlashCommands.slugOf("YouTube Content"))
        assertEquals("plan-de-test", SlashCommands.slugOf("Plan_de  test"))
        assertEquals("résumé", SlashCommands.slugOf("Résumé"))
        assertEquals("code-review", SlashCommands.slugOf("code review!"))
    }

    @Test
    fun `une competence eteinte ou en collision avec une commande fixe nest pas proposee`() {
        val commands = SlashCommands.commands(
            listOf(
                skill("verte"),
                skill("eteinte", enabled = false),
                // Le serveur réserve le nom d'une commande fixe : la compétence reste derrière
                // `/skill <nom>`, elle n'a pas de commande à elle.
                skill("Model"),
            ),
        )
        assertEquals(listOf("/verte"), commands.drop(6).map { it.name })
        assertEquals(SlashAction.OPEN_MODEL_PICKER, SlashCommands.action("/model", commands))
    }

    // ————————————————————————————————————————————————————————————————————————————————
    // Le MENU, et pas seulement la liste : le défaut relevé sur l'appareil était que les
    // compétences existaient sans jamais s'afficher.
    // ————————————————————————————————————————————————————————————————————————————————

    /** Un `complete.slash` qui remplit les huit lignes du menu. */
    private fun menuServeur(n: Int = SLASH_MENU_LINES): List<SlashItem> =
        (1..n).map { index -> SlashItem(text = "/cmd$index", display = "/cmd$index", detail = "") }

    @Test
    fun `les competences entrent dans le menu meme quand le serveur le remplit`() {
        // Le défaut : les commandes de l'app étaient ajoutées *après* celles du serveur, puis
        // coupées par `take(8)`. Avec huit lignes serveur, une compétence n'avait aucune place —
        // elle existait dans la liste fournie, et restait invisible dans le menu.
        val commands = SlashCommands.commands(listOf(skill("youtube-content", "Résume une vidéo")))
        val menu = withLocalCommands(menuServeur(), "/", commands)

        assertEquals(SLASH_MENU_LINES, menu.size)
        // Les six commandes de l'app d'abord, la compétence ensuite, le serveur en dernier.
        assertEquals(
            listOf("/model", "/new", "/find", "/settings", "/voice", "/help", "/youtube-content"),
            menu.take(7).map { it.text },
        )
        assertEquals("/cmd1", menu.last().text)
    }

    @Test
    fun `un prefixe precis laisse la place au serveur`() {
        // « /res » ne répond plus à aucune commande de l'app (ni aux six fixes, ni aux
        // compétences) : le menu doit être exactement celui du serveur.
        val serveur = listOf(SlashItem(text = "/resolve", display = "/resolve", detail = ""))
        val menu = withLocalCommands(serveur, "/res", SlashCommands.commands())

        assertEquals(listOf("/resolve"), menu.map { it.text })
    }

    @Test
    fun `une commande que le serveur propose deja n est pas doublee`() {
        val serveur = listOf(SlashItem(text = "/model", display = "/model", detail = ""))
        val menu = withLocalCommands(serveur, "/", SlashCommands.commands())

        assertEquals(1, menu.count { it.text == "/model" })
    }

    @Test
    fun `le menu ne depasse jamais sa taille et garde les competences en tete`() {
        // Un profil bavard : dix compétences, huit lignes serveur. Le menu reste à huit, et les
        // compétences — ce que le menu existe pour rendre atteignable — passent devant.
        val skills = (1..10).map { skill("skill-$it") }
        val commands = SlashCommands.commands(skills)
        val menu = withLocalCommands(menuServeur(), "/", commands)

        assertEquals(SLASH_MENU_LINES, menu.size)
        assertTrue(menu.drop(6).all { it.text.startsWith("/skill-") })
    }
}
