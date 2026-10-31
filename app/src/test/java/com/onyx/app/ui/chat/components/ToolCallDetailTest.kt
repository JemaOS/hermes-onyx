package com.onyx.app.ui.chat.components

import com.onyx.app.data.model.ToolActivity
import com.onyx.app.data.model.ToolStatus
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Le détail d'un appel d'outil : la rangée qui l'ouvre, la feuille qui le montre, et ce qu'elle dit
 * de ce que nos données ne portent pas.
 *
 * Quatre contrats sont vérifiés :
 *
 *  1. **la rangée dit le mot du travail et l'identifiant technique** en monospace, une seule fois ;
 *  2. **la feuille ouvre l'appel touché** — par son identifiant, jamais le premier ni le dernier
 *     (trois `terminal` de la même réponse ne se confondent pas) ;
 *  3. **un échec montre le message réel** du serveur sous le titre « Erreur », jamais le seul mot
 *     « erreur » ;
 *  4. **ce qui manque se dit** — un champ absent devient une phrase, jamais une rangée vide.
 *
 * Les gabarits sont des fonctions pures ; le câblage qui ne l'est pas (la cible tactile de la
 * rangée, l'ouverture de la feuille) est verrouillé par une lecture de source, comme les autres
 * verrous de la maison.
 */
class ToolCallDetailTest {

    private val previousLocale = Locale.getDefault()

    @Before
    fun frenchInterface() {
        Locale.setDefault(Locale.FRANCE)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    private fun tool(
        id: String,
        name: String,
        status: ToolStatus = ToolStatus.COMPLETED,
        preview: String? = null,
        args: String? = null,
        output: String? = null,
        startedAt: Long = 1_000L,
        endedAt: Long? = 2_400L,
    ) = ToolActivity(
        id = id,
        name = name,
        preview = preview,
        status = status,
        startedAt = startedAt,
        endedAt = endedAt,
        args = args,
        output = output,
    )

    private fun theme(name: OnyxTheme, dark: Boolean): ResolvedTheme =
        ResolvedTheme(theme = name, palette = name.palette, dark = dark, fonts = OnyxFonts.System)

    private fun hex(color: androidx.compose.ui.graphics.Color): String = "#%02X%02X%02X".format(
        (color.red * 255).roundToInt(),
        (color.green * 255).roundToInt(),
        (color.blue * 255).roundToInt(),
    )

    // -------------------------------------------------------------------------------------------
    // 1. La rangée
    // -------------------------------------------------------------------------------------------

    @Test
    fun `la rangee ecrit le verbe, puis l identifiant technique en monospace`() {
        val finished = toolRowLabel(tool(id = "a", name = "terminal"))
        assertEquals(OnyxLocalization["Ran (tool)"], finished.text)
        assertFalse("Un verbe n'est pas un identifiant : il n'est pas en monospace.", finished.monospace)
        assertEquals("terminal", finished.id)
    }

    @Test
    fun `un appel en cours garde son nom, en monospace`() {
        val running = toolRowLabel(tool(id = "a", name = "vision_analyze", status = ToolStatus.RUNNING, endedAt = null))
        // Tant que l'appel tourne, le passé serait faux : la rangée garde le nom — et c'est un
        // identifiant technique, donc du monospace.
        assertEquals("vision_analyze", running.text)
        assertTrue(running.monospace)
        assertNull("Le nom est déjà écrit : il ne l'est pas deux fois.", running.id)
    }

    @Test
    fun `un outil inconnu du tableau garde son identifiant, verbatim`() {
        val unknown = toolRowLabel(tool(id = "a", name = "neo4j_query"))
        // Le mot du passé reste le mot du dictionnaire (« Utilisé »), et l'identifiant technique
        // suit **tel qu'il est** — souligné compris : c'est lui qu'on cherche dans la liste.
        assertEquals(OnyxLocalization["Used (tool)"], unknown.text)
        assertEquals("neo4j_query", unknown.id)
        assertFalse(unknown.monospace)
    }

    @Test
    fun `une duree sous la seconde se dit, jamais zero`() {
        // « 0 s » se lit comme « rien du tout » : un appel de 0,8 s dure moins d'une seconde, et la
        // référence l'écrit « < 1 s ».
        assertEquals("< 1 s", ranToolsDuration(0.8))
        assertEquals("< 1 s", ranToolsDuration(0.2))
        assertEquals("1 s", ranToolsDuration(1.4))
        // Et la feuille reprend la même règle pour sa rangée « Durée ».
        val detail = toolCallDetail(tool(id = "a", name = "terminal", startedAt = 1_000, endedAt = 1_800))
        assertEquals("< 1 s", detail.rows[1].value)
    }

    @Test
    fun `la rangee d outil se touche a 48 dp`() {
        // La cible tactile de la maison : la rangée la porte, elle ne compte pas sur la hauteur de
        // son texte (à police 1,3×, deux mots tombaient sous les 48 dp).
        assertTrue("La cible de la rangée doit faire au moins 48 dp.", ToolCallRowMinTouch.value >= 48f)
        assertEquals(OnyxSpace.RowHeight, ToolCallRowMinTouch)
        val source = File("src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt").readText()
        assertTrue(
            "La rangée d'outil doit porter la hauteur de cible.",
            // LOT fil-vivant : la rangée est une **carte de contexte** ; elle lui donne la hauteur
            // de cible de la maison, et c'est le composant qui l'applique (et qui pose, sous elle,
            // la hauteur d'en-tête de 48 dp commune à toutes les cartes).
            source.contains("minHeight = ToolCallRowMinTouch,"),
        )
        val card = File("src/main/java/com/onyx/app/ui/chat/components/ContextCard.kt").readText()
        assertTrue(
            "la carte de contexte doit appliquer la hauteur reçue",
            card.contains("defaultMinSize(minHeight = minHeight)"),
        )
        assertTrue(
            "et son en-tête est la cible tactile de la maison",
            card.contains("heightIn(min = ContextCardRowHeight)"),
        )
    }

    // -------------------------------------------------------------------------------------------
    // 2. La feuille ouvre l'appel touché
    // -------------------------------------------------------------------------------------------

    /** Trois appels du **même** nom, avec des arguments différents : c'est l'ambiguïté à lever. */
    private val triples = listOf(
        tool(id = "t1", name = "terminal", args = "command: echo un", output = "un"),
        tool(id = "t2", name = "terminal", args = "command: echo deux", output = "deux"),
        tool(id = "t3", name = "terminal", args = "command: echo trois", output = "trois"),
    )

    @Test
    fun `la feuille ouvre l appel touche, jamais le premier ni le dernier`() {
        val picked = toolCallSelection(triples, "t2")
        assertEquals("t2", picked?.id)
        // Et la feuille montre **ses** arguments : c'est ce qui prouve que ce n'est pas « le
        // premier » (t1) ni « le dernier » (t3).
        val detail = toolCallDetail(picked!!)
        // Ses arguments et son résultat — et **pas** ceux de ses voisins du même nom :
        assertEquals("command: echo deux", detail.blocks.first().text)
        assertEquals("deux", detail.blocks.last().text)
        assertFalse(detail.blocks.any { it.text.contains("un") && it.text.endsWith("un") })
        assertFalse(detail.blocks.any { it.text.contains("trois") })
        assertEquals("trois appels du même nom", 3, triples.count { it.name == "terminal" })
    }

    @Test
    fun `un identifiant inconnu n ouvre rien plutot qu un autre appel`() {
        assertNull(toolCallSelection(triples, "t9"))
        assertNull(toolCallSelection(triples, null))
        assertNull(toolCallSelection(emptyList(), "t1"))
    }

    @Test
    fun `le cablage ouvre la feuille sur l identifiant de la rangee touchee`() {
        val source = File("src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt").readText()
        assertTrue(
            "La rangée doit ouvrir le détail de **son** appel.",
            // LOT fil-vivant : la rangée est une carte de contexte, et c'est **son** geste qui
            // ouvre la feuille — l'identifiant conservé est toujours celui de la rangée touchée.
            source.contains("onClick = { detailOf = tool.id },"),
        )
        assertTrue(
            "La feuille doit être choisie par l'identifiant conservé.",
            source.contains("toolCallSelection(tools, detailOf)?.let { picked ->") &&
                source.contains("ToolCallDetailSheet(tool = picked, onDismiss = { detailOf = null })"),
        )
    }

    // -------------------------------------------------------------------------------------------
    // 3. Ce que la feuille montre, et l'échec en clair
    // -------------------------------------------------------------------------------------------

    @Test
    fun `la feuille montre le nom, l etat, la duree, les arguments et le resultat`() {
        val detail = toolCallDetail(
            tool(
                id = "a",
                name = "web_search",
                args = "query: charte onyx",
                output = "3 résultats",
            ),
        )
        assertEquals("web_search", detail.name)
        assertEquals(ToolCallTone.DONE, detail.tone)
        assertEquals(listOf("État", "Durée"), detail.rows.map { it.label })
        assertEquals("Terminé", detail.rows.first().value)
        // La durée vient des horodates de l'appel (1 000 → 2 400 ms).
        assertEquals("1 s", detail.rows[1].value)
        assertTrue("La durée est une mesure : elle s'écrit en monospace.", detail.rows[1].monospace)
        assertEquals(listOf("Arguments", "Résultat"), detail.blocks.map { it.title })
        assertEquals("query: charte onyx", detail.blocks.first().text)
        assertEquals("3 résultats", detail.blocks.last().text)
        assertFalse(detail.blocks.last().failed)
    }

    @Test
    fun `un echec montre le message reel sous le titre Erreur`() {
        val error = "bash: line 3: /opt/backup.sh: Permission denied"
        val detail = toolCallDetail(
            tool(id = "a", name = "terminal", status = ToolStatus.FAILED, args = "{\"command\": \"sh backup.sh\"}", output = error),
        )
        assertEquals(ToolCallTone.FAILED, detail.tone)
        assertTrue(detail.isFailed)
        val block = detail.blocks.last()
        assertEquals("Erreur", block.title)
        assertTrue("Un échec doit se dire en rouge.", block.failed)
        assertEquals("Le message réel du serveur, pas le mot « erreur » seul.", error, block.text)
    }

    @Test
    fun `un appel en cours n a pas de duree et le dit`() {
        val detail = toolCallDetail(
            tool(id = "a", name = "terminal", status = ToolStatus.RUNNING, endedAt = null, args = "command: rsync -a /mnt/nas /backup"),
        )
        assertEquals(ToolCallTone.RUNNING, detail.tone)
        assertEquals("En cours", detail.rows.first().value)
        assertEquals("Pas de durée tant qu'il tourne.", listOf("État"), detail.rows.map { it.label })
        // Ses arguments sont là ; c'est le **résultat** qui manque encore — et la feuille le dit
        // sans faire croire à un appel sans contenu.
        assertEquals(listOf("Arguments"), detail.blocks.map { it.title })
        assertEquals(listOf("Toujours en cours."), detail.notes)
    }

    // -------------------------------------------------------------------------------------------
    // 4. Ce que nos données ne portent pas — dit, et jamais montré vide
    // -------------------------------------------------------------------------------------------

    @Test
    fun `des arguments absents ne font pas un bloc vide, mais une phrase`() {
        val detail = toolCallDetail(tool(id = "a", name = "terminal", output = "ok"))
        assertEquals("Un champ absent n'est pas un bloc vide.", listOf("Résultat"), detail.blocks.map { it.title })
        assertTrue(
            "Le transport qui n'a rien gardé doit le dire.",
            detail.notes.contains(OnyxLocalization["The arguments of this call were not kept by this transport."]),
        )
    }

    @Test
    fun `un resultat absent sur un appel fini est dit`() {
        val detail = toolCallDetail(tool(id = "a", name = "terminal", args = "{\"command\": \"ls\"}", output = null))
        assertTrue(detail.notes.contains(OnyxLocalization["No output was kept for this step."]))
    }

    @Test
    fun `un appel complet n a aucune note`() {
        val detail = toolCallDetail(tool(id = "a", name = "terminal", args = "{\"command\": \"ls\"}", output = "ok"))
        assertTrue("Rien ne manque : rien à dire.", detail.notes.isEmpty())
    }

    // -------------------------------------------------------------------------------------------
    // Le dictionnaire et les encres
    // -------------------------------------------------------------------------------------------

    @Test
    fun `les libelles de la feuille sont au dictionnaire central, en francais`() {
        // Aucun libellé en dur : chaque mot de la feuille vient du dictionnaire, et il est français.
        assertEquals("Durée", OnyxLocalization["Duration"])
        assertEquals("Arguments", OnyxLocalization["Arguments"])
        assertEquals("Résultat", OnyxLocalization["Result"])
        assertEquals("État", OnyxLocalization["State"])
        assertEquals("Erreur", OnyxLocalization["Error"])
        assertEquals("Afficher les détails", OnyxLocalization["Show details"])
        assertEquals("Copier Arguments", OnyxLocalization.format("Copy %1\$s", "Arguments"))
        assertEquals("Copié (12 caractères)", OnyxLocalization.format("Copied (%1\$d characters)", 12))
    }

    @Test
    fun `l encre d un etat se lit sur la surface de la feuille, dans les deux apparences`() {
        val lines = mutableListOf<String>()
        for (name in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                val t = theme(name, dark)
                val surface = OnyxSurfaces.surfaceHigh(t)
                for (tone in ToolCallTone.entries) {
                    val ink = toolCallStateInk(t, surface, tone)
                    val ratio = OnyxContrast.ratio(ink, surface)
                    lines += "${name.name}${if (dark) " sombre" else " clair"} · ${tone.name} · ${hex(ink)} sur ${hex(surface)} = ${"%.2f".format(ratio)}:1"
                    assertTrue(
                        "Un état doit se lire sur la surface de la feuille ($name, sombre=$dark, $tone) : $ratio",
                        ratio >= OnyxContrast.TEXT_MIN,
                    )
                }
            }
        }
        println(lines.joinToString("\n"))
    }
}
