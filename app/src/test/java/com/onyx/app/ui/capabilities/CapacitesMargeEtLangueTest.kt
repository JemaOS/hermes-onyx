package com.onyx.app.ui.capabilities

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou du haut de l'écran « Capacités »** : le sélecteur de familles tient dans la marge de
 * contenu, sa moitié active est un aplat d'accent, et **aucun libellé d'interface ne repart en
 * anglais**.
 *
 * Il mord sur trois défauts relevés sur l'appareil, et sur eux seuls :
 *
 * 1. **le sélecteur sortait du système** — il allait jusqu'aux deux bords de l'écran (relevé :
 *    **4,2 dp** de chaque côté, pour une marge de contenu de **16 dp**) pendant que le titre, le
 *    sous-titre, la recherche et les cartes étaient tous à 16. C'est donc le contrôle qui était hors
 *    du système, pas le reste : il doit porter la marge du socle, **à gauche et à droite** ;
 * 2. **sa moitié active était une pilule blanche** sur une piste grise (une pastille de surface en
 *    clair, un gris clair en sombre), là où la planche validée demande **l'aplat d'accent**
 *    (`.segi.on{background:var(--accfill);color:#fff}` — la même règle que la pastille d'onglet) ;
 * 3. **des libellés restaient en anglais** dans un écran français : « What the agent can use in new
 *    chats » sous le titre, puis « Browse hub » et « New skill ». Un libellé de la maison qui n'est
 *    pas une clé du dictionnaire **ressort tel quel** ([OnyxLocalization.translate] rend l'entrée
 *    quand elle ne connaît pas la clé) : c'est exactement ce que la règle des libellés ci-dessous
 *    interdit.
 *
 * Ce qui n'est **pas** visé, et qu'il ne faut pas traduire : les **descriptions de compétences
 * fournies par le serveur** (rendues verbatim), les **identifiants** (un nom de compétence,
 * `web_search`, `SKILL.md`, une adresse de serveur MCP) et les **exemples de commande**
 * (`npx`, `-y @scope/server`, `KEY=value`) — ils n'ont ni espace ni majuscule initiale, donc la
 * règle ne les voit pas.
 */
class CapacitesMargeEtLangueTest {

    private val dir = File("src/main/java/com/onyx/app/ui/capabilities")
    private val screen = File(dir, "CapabilitiesScreen.kt").readText()
    private val socle = File("../core/src/main/java/com/onyx/app/ui/design/SocleKit.kt").readText()
    private val segmentedSource = File("../core/src/main/java/com/onyx/app/ui/design/OnyxForm.kt").readText()

    /**
     * Le bloc d'un appel : de `nom(` jusqu'à la **parenthèse qui le ferme**. Un `contains` sur le
     * fichier entier ne dirait pas **quel** appel porte la marge — deux appels du même nom se
     * confondraient, et une marge posée ailleurs dans le fichier suffirait à faire passer le test.
     */
    private fun callBlock(source: String, call: String): List<String> {
        val blocks = mutableListOf<String>()
        var start = source.indexOf(call)
        while (start >= 0) {
            var depth = 1 // la parenthèse ouvrante est dans `call`
            var end = start + call.length
            while (end < source.length) {
                when (source[end]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) {
                            blocks += source.substring(start, end + 1)
                            end = source.length
                        }
                    }
                }
                end++
            }
            start = source.indexOf(call, start + 1)
        }
        return blocks
    }

    // MARK: - 1. Le sélecteur tient dans la marge de contenu

    @Test
    fun `le selecteur de familles porte la marge de contenu des deux cotes`() {
        val blocks = callBlock(screen, "AdaptiveSegmented(")
        assertEquals("un seul sélecteur sur l'écran", 1, blocks.size)
        val block = blocks.first()
        assertTrue(
            "le sélecteur doit porter la marge d'écran du socle, pas aller jusqu'aux bords : $block",
            block.contains("OnyxSocle.ScreenMargin"),
        )
        assertTrue(
            "la marge vaut des **deux** côtés — une seule laisserait le contrôle collé à l'autre bord",
            Regex("start = OnyxSocle\\.ScreenMargin").containsMatchIn(block) &&
                Regex("end = OnyxSocle\\.ScreenMargin").containsMatchIn(block),
        )
        // Le titre du socle porte la **même** marge : le sélecteur ne peut pas être le seul élément
        // de la bande à sortir du système.
        assertTrue(
            "le titre du socle porte la marge de contenu",
            socle.contains("padding(horizontal = OnyxSocle.ScreenMargin)"),
        )
        // Et le composant **respecte** la place qu'on lui laisse : il ne se donne jamais une largeur
        // propre plus grande que celle de son parent.
        val row = File("../core/src/main/java/com/onyx/app/ui/design/OnyxForm.kt").readText()
        assertTrue(
            "le sélecteur se contraint à la largeur qu'on lui donne",
            row.contains("BoxWithConstraints(") && row.contains(".fillMaxWidth()") &&
                row.contains(".height(44.dp)"),
        )
    }

    // MARK: - 2. La moitié active est un aplat d'accent

    @Test
    fun `la moitie active du selecteur est un aplat d'accent`() {
        val block = callBlock(screen, "AdaptiveSegmented(").first()
        assertTrue(
            "le sélecteur doit demander la pilule pleine (aplat d'accent), pas la pilule de surface",
            block.contains("filled = true"),
        )
        // Et le composant doit savoir la rendre : la moitié active peinte de l'accent, son mot
        // calculé dessus — jamais un blanc posé au hasard sur une teinte pâle.
        assertTrue(
            "le sélecteur rempli est peint de l'accent, et son encre est calculée sur cet aplat",
            segmentedSource.contains("OnyxContrast.filled(theme.accent)") &&
                segmentedSource.contains("OnyxContrast.inkOn(selectedPill)"),
        )
        // Le chemin est **réellement** branché : `AdaptiveSegmented` transmet le drapeau, il ne le
        // laisse pas tomber en route (c'est par là que la pilule blanche revenait).
        val adaptive = File("src/main/java/com/onyx/app/ui/common/AdaptiveSegmented.kt").readText()
        assertTrue("le sélecteur adaptatif transmet le drapeau `filled`", adaptive.contains("filled = filled"))
        assertTrue("le sélecteur adaptatif transmet la marge", adaptive.contains("modifier = modifier"))
    }

    // MARK: - 3. Aucun libellé d'interface en anglais

    @Test
    fun `aucun libelle fixe de la famille Capacites n'echappe au dictionnaire central`() {
        // Un **libellé** : une valeur posée dans une position de texte, qui porte un espace et
        // commence par une majuscule — donc une phrase destinée à la personne, jamais un identifiant
        // (`SKILL.md`), un exemple de commande (`npx`, `-y @scope/server`) ou une adresse.
        val label = Regex(
            "(?:text|label|subtitle|title|header|placeholder)\\s*=\\s*\"([^\"\\n\\\\]{2,120})\"",
        )
        val files = listOf(
            "CapabilitiesScreen.kt", "CapabilityParts.kt", "McpPages.kt",
            "SkillPages.kt", "SkillEditorScreen.kt",
        )
        var judged = 0
        files.forEach { name ->
            val source = File(dir, name).readText()
            label.findAll(source).map { it.groupValues[1] }.toSet().forEach { word ->
                if (!word.contains(' ') || !word.first().isUpperCase()) return@forEach
                judged++
                assertTrue(
                    "« $word » ($name) doit être une clé du dictionnaire central : sans elle, il " +
                        "ressort **en anglais** au milieu d'un écran français",
                    OnyxLocalization.translate(word, Locale.FRANCE) != word,
                )
            }
        }
        assertTrue("l'écran porte bien des libellés à contrôler", judged >= 15)
        // Témoin : les trois phrases relevées sur l'appareil sont désormais traduites.
        assertEquals(
            "Ce que l’agent peut utiliser dans les nouvelles conversations",
            OnyxLocalization.translate("What the agent can use in new chats", Locale.FRANCE),
        )
        assertEquals("Parcourir le catalogue", OnyxLocalization.translate("Browse hub", Locale.FRANCE))
        assertEquals("Nouvelle compétence", OnyxLocalization.translate("New skill", Locale.FRANCE))
    }

    @Test
    fun `les pilules fixes de l'ecran passent leurs libelles au dictionnaire`() {
        // `ContentPill` ne traduit pas par défaut : son libellé est le plus souvent une **donnée**
        // (un nom de bot, une valeur). Une pilule **fixe** doit donc le demander explicitement —
        // sans quoi « Browse hub » et « New skill » restaient anglais sur un écran français.
        val pills = callBlock(screen, "ContentPill(")
        assertTrue("l'écran porte des pilules", pills.size >= 2)
        pills.forEach { block ->
            val literal = Regex("text = \"([^\"]+)\"").find(block)?.groupValues?.get(1)
            if (literal != null && !literal.contains('$')) {
                assertTrue(
                    "la pilule « $literal » doit demander la traduction (localize = true) : $block",
                    block.contains("localize = true"),
                )
                assertTrue(
                    "« $literal » doit être une clé du dictionnaire central",
                    OnyxLocalization.translate(literal, Locale.FRANCE) != literal,
                )
            }
        }
    }

    @Test
    fun `le sous-titre de l'en-tete du socle est un libelle, pas une donnee`() {
        // La fenêtre est prise **dans le corps** de [SocleLargeHeader] : le sous-titre y est composé
        // après le titre, et c'est le sien qui compte — `text = subtitle,` vit aussi dans d'autres
        // composants du socle, où le mot est une donnée.
        val start = socle.indexOf("fun SocleLargeHeader(")
        assertTrue("l'en-tête du socle existe", start >= 0)
        val from = socle.indexOf("text = subtitle,", start)
        assertTrue("l'en-tête du socle compose un sous-titre", from > start)
        val window = socle.substring(from, minOf(from + 800, socle.length))
        assertTrue(
            "le sous-titre d'un écran passe par le dictionnaire (le titre le fait déjà) : en " +
                "`localize = false`, « What the agent can use in new chats » restait anglais",
            window.contains("localize = true"),
        )
    }

    // MARK: - Les descriptions du serveur ne sont pas nos libellés

    @Test
    fun `les descriptions rendues par le serveur restent verbatim`() {
        // Ce ne sont pas nos libellés : une description de compétence vient du serveur, dans sa
        // langue. L'écran ne la traduit pas — et c'est voulu.
        assertTrue(
            "la description d'une compétence n'est pas traduite",
            screen.contains("subtitle = skill.description.takeIf { it.isNotBlank() }"),
        )
        assertFalse(
            "la description de compétence ne passe pas par le dictionnaire",
            screen.contains("OnyxLocalization[skill.description"),
        )
    }
}
