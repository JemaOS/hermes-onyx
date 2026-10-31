package com.onyx.app.ui

import com.onyx.app.localization.onyxTabsFrench
import com.onyx.app.ui.design.ChatTab
import com.onyx.app.ui.sidebar.SidebarDestinations
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de la suppression d'« Idées » et d'« Objectifs ».**
 *
 * Le propriétaire a autorisé la suppression (« Idées et Objectifs ne servent plus, donc tu peux
 * supprimer »). Ces écrans ont déjà été **redessinés par accident dans des fusions**, plus d'une
 * fois : un lot recrée une route, un autre re-branche une destination, et l'écran supprimé revient
 * sans que personne ne s'en aperçoive. Ce test **échoue** dès qu'un morceau du chemin d'accès
 * réapparaît — **route, destination, clé du dictionnaire ou symbole de code** — et il nomme le
 * **fichier et la ligne** fautifs.
 *
 * Il tient les deux bouts :
 *
 * 1. **rien de supprimé ne revient** — ni le code, ni la route, ni la destination, ni le mot ;
 * 2. **ce qui devait rester reste** — le **quatrième repère est le visage du bot** (`ChatTab.BOT`),
 *    et son routage n'est pas une page du pageur.
 *
 * Ce qu'il ne fait **pas** : interdire les mots « Idées » / « Objectifs » dans un **commentaire**.
 * Le verrou juge le **code** — un symbole, une chaîne de destination, une clé de traduction —, pas
 * la prose qui raconte la suppression.
 */
class SuppressionIdeesObjectifsTest {

    /** Les racines des **sources de production** — jamais les tests, qui parlent de ce verrou. */
    private val roots = listOf(
        File("src/main/java"),          // module `app`
        File("../core/src/main/java"),  // module `core`
    )

    /**
     * Les **symboles de code** qui ne peuvent exister que si Idées ou Objectifs revient. Un
     * commentaire qui écrit « Idées » ne les contient pas : ce sont des identifiants ou des
     * littéraux de destination.
     */
    private val forbidden = listOf(
        "ChatTab.IDEAS",
        "ChatTab.GOALS",
        "ChatIdeas",
        "ChatGoals",
        "ChatIdeasPane",
        "ChatGoalsPane",
        "NoteCategory",
        "LocalNote",
        "goalProgressLabel",
        "\"IDEAS_TAB\"",
        "\"GOALS_TAB\"",
        "\"ideas\"",
        "\"goals\"",
    )

    private fun productionFiles(): List<File> =
        roots.filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    @Test
    fun `aucun symbole d Idees ni d Objectifs ne reapparait dans le code`() {
        val hits = mutableListOf<String>()
        for (file in productionFiles()) {
            file.readLines().forEachIndexed { index, line ->
                forbidden.firstOrNull { it in line }?.let { token ->
                    hits += "${file.path}:${index + 1}: « $token » → $line"
                }
            }
        }
        assertTrue(
            "Idées/Objectifs ne doivent pas revenir — symboles trouvés :\n" + hits.joinToString("\n"),
            hits.isEmpty(),
        )
    }

    @Test
    fun `aucune route d Idees ni d Objectifs dans l enum Route`() {
        val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt")
        val text = app.readText()
        val routes = text.substringAfter("private enum class Route {").substringBefore("\n}")
        val startLine = text.substring(0, text.indexOf("private enum class Route {")).count { it == '\n' } + 1
        routes.lines().forEachIndexed { index, line ->
            listOf("IDEAS", "GOALS").forEach { token ->
                assertFalse(
                    "la route $token ne doit pas revenir : ${app.path}:${startLine + index}: « ${line.trim()} »",
                    Regex("""\b$token\b""").containsMatchIn(line),
                )
            }
        }
    }

    @Test
    fun `aucune destination du bas ne ramene Idees ni Objectifs`() {
        val destinations = SidebarDestinations.bottomTabDestinations
        val indexPath = "app/src/main/java/com/onyx/app/ui/sidebar/SidebarDestinations.kt"
        listOf("IDEAS_TAB", "GOALS_TAB").forEach { gone ->
            assertFalse(
                "$gone ne doit plus être une destination du bas : $indexPath (bottomTabDestinations)",
                gone in destinations.values,
            )
        }
        // Et aucune tuile du volet ne mène à ces écrans.
        assertTrue(
            "aucune tuile du volet ne doit ouvrir Idées ni Objectifs : $indexPath (SidebarDestinations.tiles)",
            SidebarDestinations.tiles.none { it.id == "IDEAS" || it.id == "GOALS" },
        )
    }

    @Test
    fun `les mots Idees et Objectifs ne sont plus des onglets ni des cles du dictionnaire`() {
        val indexPath = "core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt (ChatTab)"
        assertFalse("« Ideas » ne doit plus être un onglet : $indexPath",
            ChatTab.entries.any { it.key == "Ideas" })
        assertFalse("« Goals » ne doit plus être un onglet : $indexPath",
            ChatTab.entries.any { it.key == "Goals" })

        val dictPath = "core/src/main/java/com/onyx/app/localization/OnyxFrenchTabs.kt (onyxTabsFrench)"
        listOf("Ideas", "Goals", "Your ideas", "Tracking", "Ideas are kept on this phone.",
            "Goals are kept on this phone.", "Write an idea", "Create a goal").forEach { key ->
            assertFalse("la clé « $key » ne doit plus exister : $dictPath", key in onyxTabsFrench)
        }
        // Les mots français ne sont plus traduits : la table ne les nomme plus.
        assertEquals("la table française ne doit plus nommer « Idées » : $dictPath",
            "Ideas", OnyxLocalizationForTest.translate("Ideas"))
        assertEquals("la table française ne doit plus nommer « Objectifs » : $dictPath",
            "Goals", OnyxLocalizationForTest.translate("Goals"))
    }

    @Test
    fun `le quatrieme repere reste le visage du bot, et n ouvre pas une page du pageur`() {
        val barPath = "core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt"
        val screenPath = "app/src/main/java/com/onyx/app/ui/chat/ChatScreen.kt"
        // Trois pages de contenu, un quatrième repère qui est le visage.
        assertEquals("trois pages dans le pageur : $barPath (chatTabCount)", 3, ChatTab.paged.size)
        assertEquals("quatre repères dessinés : $barPath (ChatTab.drawn)", 4, ChatTab.drawn.size)
        assertTrue("le quatrième repère est le visage : $barPath (ChatTab.BOT)",
            ChatTab.drawn.last() == ChatTab.BOT)
        assertFalse("le repère-visage n'est pas une page du pageur : $barPath (ChatTab.paged)",
            ChatTab.BOT in ChatTab.paged)
        assertEquals("le libellé du repère-visage : $barPath (ChatTab.FACE_LABEL)",
            "Onyx", ChatTab.FACE_LABEL)
        // L'écran branche bien le repère-visage sur « l'identité », jamais sur une page.
        val screen = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
        assertTrue(
            "l'écran pose le repère-visage comme la dernière position : $screenPath (val faceIndex)",
            screen.contains("val faceIndex = ChatTab.drawn.lastIndex"),
        )
        assertTrue(
            "l'écran ne fait pas défiler le pageur depuis le repère-visage : $screenPath (onSelect)",
            screen.contains("index == faceIndex && onOpenBotIdentity != null -> onOpenBotIdentity()"),
        )
    }

    /**
     * **Les surfaces de la page d'identité du bot, nommées.** Le premier verrou ne connaissait que les
     * **onglets**, la **route** et la **destination** : c'est exactement pour cela que les **panneaux**
     * — « Suivi », « Idées de <nom> », « Créer un objectif » et ses catégories — ont survécu à la
     * première suppression. Ce test-ci ferme le second endroit : il interdit les **mots** de ces
     * panneaux (clés du dictionnaire **et** littéraux français), la **rangée « Idées »** que le lot du
     * visage avait posée sur la page d'identité, et il **nomme** le fichier et la ligne fautifs.
     *
     * Il ne juge que le **code** et le **dictionnaire** — un commentaire qui raconte la suppression a
     * le droit d'écrire « Idées ».
     */
    @Test
    fun `les surfaces Idees et Objectifs ne survivent nulle part, ni panneau ni page d identite`() {
        // 1. Les clés du dictionnaire des panneaux : elles ne peuvent pas revenir.
        val deadKeys = listOf(
            "Ideas", "Goals", "Your ideas", "Tracking", "Goal menu",
            "Ideas are kept on this phone.", "Goals are kept on this phone.",
            "Ideas from %1\$s", "No idea has been kept yet.", "No goal is being tracked yet.",
            "Pick a category below and write one; it stays on this phone.",
            "Pick a category below and create one; it stays on this phone.",
            "Write an idea", "Write an idea…", "Write a goal…", "Create a goal",
            "Mark as done", "Add to %1\$s", "Kept by you on this phone.",
            "Advance one step", "Add a step", "No step yet", "Done · %1\$s of %2\$s", "%1\$s of %2\$s",
            "Plan", "Make it a goal", "Schedule: %1\$s",
            "Money", "Travel", "Health", "Interests", "Home & family", "Work", "No category",
        )
        val leftoverKeys = deadKeys.filter { it in onyxTabsFrench }
        assertTrue(
            "aucune clé des listes locales ne doit revenir : $leftoverKeys " +
                "(core/src/main/java/com/onyx/app/localization/OnyxFrenchTabs.kt)",
            leftoverKeys.isEmpty(),
        )

        // 2. Les littéraux **français** des mêmes écrans — le titre du panneau, son état vide, ses
        //    catégories, son bouton. Le code de production ne les nomme plus.
        val frenchWords = listOf(
            "Les idées sont gardées sur ce téléphone",
            "Les objectifs sont gardés sur ce téléphone",
            "Aucun objectif suivi pour l’instant",
            "Aucune idée gardée pour l’instant",
            "Créer un objectif",
            "Écrire une idée",
            "Idées de ",
            "Centres d’intérêt",
        )
        val hits = mutableListOf<String>()
        for (file in productionFiles()) {
            file.readLines().forEachIndexed { index, line ->
                frenchWords.firstOrNull { it in line }?.let { token ->
                    hits += "${file.path}:${index + 1}: « $token » → $line"
                }
            }
        }
        assertTrue(
            "aucune surface ne doit nommer les listes locales :\n" + hits.joinToString("\n"),
            hits.isEmpty(),
        )

        // 3. La page d'identité du bot ne porte **ni** rangée « Idées » **ni** groupe local — le
        //    second endroit qu'avait laissé passer la première suppression.
        val editor = File("src/main/java/com/onyx/app/ui/bots/BotEditorScreen.kt")
        if (editor.isFile) {
            val text = editor.readText()
            assertFalse(
                "la page d'identité ne porte pas de rangée « Idées » : ${editor.path}",
                text.contains("onOpenIdeas"),
            )
            assertFalse(
                "ni un libellé « Ideas » : ${editor.path}",
                text.contains("label = \"Ideas\""),
            )
        }
    }
}

/** Un accès direct au dictionnaire, sans dépendre de la locale de la machine de test. */
private object OnyxLocalizationForTest {
    fun translate(key: String): String = onyxTabsFrench[key] ?: key
}
