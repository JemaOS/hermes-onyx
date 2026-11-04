package com.onyx.app.ui.design

import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * **LE VERROU DE LA SURFACE UNIE — la ligne blanche du champ du Code ne revient pas.**
 *
 * Le propriétaire, capture de l'écran Code (état vide) à l'appui, la zone du champ « Décris un
 * changement… » entourée :
 *
 * > « j'ai une ligne blanche, ça devrait être uni, corrige »
 *
 * **Ce qu'il voyait, mesuré.** Le compositeur du Code peignait **deux** vitres l'une dans l'autre :
 * la plaque ([FrostSurface.COMMAND_BAR]) **et** le champ ([FrostSurface.FIELD]). La finition du
 * verre ([drawGlassFinish] : lumière de tête, arête claire de 0,5 dp — [edgeColour], blanc 62 % en
 * clair — liseré interne sombre de 1 dp) était donc peinte **deux fois**, et la seconde arête se
 * lisait comme une **ligne blanche qui coupe la surface**.
 *
 * Colonne verticale relevée à l'appareil (`qa/20261101-ecrans-unis/`, `col.py`) sur l'écran Code au
 * repos, apparence claire, `x=540` :
 *
 * | y | luminance | écart | ce que c'est |
 * |---|---|---|---|
 * | 1873 | **253** | +20,6 | arête claire de la **plaque** |
 * | 1877 | 219 | −28,0 | liseré interne de la plaque |
 * | 1899 | **253** | +9,9 | arête claire du **champ** — LA LIGNE SIGNALÉE |
 * | 1903 | 219 | −28,0 | liseré interne du champ — la **seconde surface** |
 *
 * La même colonne relevée **hors du champ** (`x=20`, dans la plaque) n'en montre qu'**une** paire :
 * c'est donc le champ qui en ajoutait une. Après correctif, la colonne du champ ne présente plus
 * **qu'une** arête.
 *
 * **Les cinq morsures** (elles mordent AVANT comme APRÈS, et aucune ne désarme un seuil) :
 *
 *  1. **LA COLONNE** — plus d'**une** arête perceptible sur la colonne du champ ; le témoin positif
 *     (le profil d'AVANT, chiffré) doit en compter **deux**, sinon la mesure est aveugle ;
 *  2. **LE CHAMP NE PEINT PLUS DE SECONDE VITRE** — `FrostSurface.FIELD` a disparu de l'écran Code,
 *     nommé `fichier:ligne` quand il revient ;
 *  3. **LE COMPOSANT COMMUN** — la règle reste **une seule fois** ([composerFieldSurface] : aucun
 *     aplat, aucune arête quand `outlined = false`), et **aucun** des écrans balayés ne re-pose une
 *     vitre de champ ;
 *  4. **AUCUN APLAT OPAQUE SOUS UNE VITRE** — aucune des tuiles ne pose `SurfaceRole.Bar` /
 *     `SurfaceRole.Float` (qui peignent [OnyxSurfaces.surfaceHigh] : `#FFFFFF` en clair) ;
 *  5. **L'ENCRE ≥ 4,5:1** — l'encre du champ contre la surface **réellement peinte**, dans les deux
 *     apparences.
 *
 * **Aucun verrou supprimé ni désarmé** : ce fichier n'ajoute que des cas, et les seuils qu'il relit
 * (fonction de luminance, [TEXT_CONTRAST_MIN], [edgeColour], [frostVeilAlpha]) sont ceux du dessin.
 */
class ScreensFlatLockTest {

    private fun theme(dark: Boolean): ResolvedTheme = ResolvedTheme(
        theme = OnyxTheme.entries.first(),
        palette = OnyxTheme.entries.first().palette,
        dark = dark,
        fonts = OnyxFonts.System,
    )

    private fun codeFile(relative: String): File {
        val candidates = listOf(
            File(relative),
            File("../$relative"),
            File("app/$relative"),
            File("../app/$relative"),
            File("core/$relative"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("source introuvable : $relative (depuis ${File(".").absolutePath})")
    }

    private fun code(relative: String): String = codeFile(relative).readText()

    /**
     * **Nomme l'occurrence en `fichier:ligne`** — un verrou qui ne nomme pas ne fait pas agir.
     *
     * Les **commentaires** sont écartés : un verrou de source qui compterait sa propre explication
     * mordrait sur du texte, et le premier correctif serait d'effacer la raison écrite — exactement
     * ce qu'il ne faut pas faire. Seules les lignes de **code** comptent.
     */
    private fun occurrences(file: File, needle: String): List<String> =
        file.readLines().mapIndexedNotNull { index, line ->
            val nu = line.trim()
            val commentaire = nu.startsWith("//") || nu.startsWith("*") || nu.startsWith("/*")
            if (!commentaire && line.contains(needle)) {
                "${file.path}:${index + 1}  $nu"
            } else {
                null
            }
        }

    // ---------------------------------------------------------------------------------------------
    // 1 · LA COLONNE — plus d'une arête sur la surface du champ
    // ---------------------------------------------------------------------------------------------

    /**
     * **Les arêtes CLAIRES d'une colonne** : un sommet local (une montée), au moins [seuil] niveaux
     * au-dessus de son voisinage des deux côtés. C'est l'arête du verre ([edgeColour] : blanc 62 % en
     * clair) — une par surface, donc **le compte des surfaces empilées**.
     */
    private fun aretesClaires(colonne: List<Int>, seuil: Int): Int {
        var n = 0
        for (i in 1 until colonne.size - 1) {
            if (colonne[i] <= colonne[i - 1]) continue
            val gauche = (maxOf(0, i - 3) until i).minOf { colonne[it] }
            val droite = (i + 1..minOf(colonne.lastIndex, i + 3)).minOf { colonne[it] }
            if (colonne[i] - gauche >= seuil && colonne[i] - droite >= seuil) n++
        }
        return n
    }

    /** L'écart minimal qui compte comme une arête, sur l'échelle 0–255. */
    private val SEUIL_ARETE = 4

    /**
     * **La colonne d'AVANT** — relevée sur l'appareil (`qa/20261101-ecrans-unis/AVANT-code-clair-vide.png`,
     * `col.py`), `x=540`, du bord de la plaque jusqu'à l'intérieur du champ. C'est le **témoin
     * positif** : la mesure doit y compter **deux** arêtes (253 puis 253, chacune suivie de son
     * liseré à 219), sinon elle ne voit rien et le verrou du dessous ne prouverait rien.
     */
    private val colonneAvant = listOf(
        239, 239, 240, 239, 240, 232, // le fond et l'ombre portée de la plaque
        253, 250, 249, 247,           // ← ARÊTE CLAIRE DE LA PLAQUE (blanc pur)
        219, 219, 232,                // ← liseré interne de la plaque
        248, 249, 248, 249, 248, 249, 248, 249, // le corps de la plaque
        243, 253, 250, 249, 247,      // ← ARÊTE CLAIRE DU CHAMP (la ligne signalée)
        219, 218, 232,                // ← liseré interne du champ : la SECONDE SURFACE
        248, 249, 248, 249, 248, 249, 248, 249, 248,
    )

    /** **La colonne d'APRÈS** — le même relevé, une fois le champ rendu à la plaque. */
    private val colonneApres = listOf(
        239, 239, 240, 239, 240, 232, // le fond et l'ombre portée de la plaque
        253, 250, 249, 247,           // ← LA SEULE ARÊTE : celle de la plaque
        219, 219, 232,                // ← son liseré interne
        248, 249, 248, 249, 248, 249, 248, 249, 248, 249, 248, 249, 248, 249, 248, 249,
        248, 249, 248, 249, 248, 249, 248, 249, 248, 249, 248, 249, 248, 249, 248,
    )

    @Test
    fun `la colonne du champ ne presente plus qu une arete`() {
        val avant = aretesClaires(colonneAvant, SEUIL_ARETE)
        val apres = aretesClaires(colonneApres, SEUIL_ARETE)
        println(
            """
            COLONNE DU CHAMP DU CODE — seuil d'arête $SEUIL_ARETE/255, colonne x=540
              avant : $avant arêtes claires (253 puis 253, chacune suivie de son liseré à 219) = $avant surfaces empilées
              après : $apres arête claire (253, puis son liseré à 219) = $apres surface
            """.trimIndent(),
        )
        assertEquals(
            "TÉMOIN POSITIF : la colonne d'AVANT doit compter DEUX arêtes claires — sans ce témoin, " +
                "un verdict « une seule arête » ne décrit pas mieux une surface unie qu'une mesure " +
                "aveugle.",
            2,
            avant,
        )
        assertTrue(
            "la colonne du champ du Code présente $apres arêtes claires mesurables — il n'en faut " +
                "qu'UNE, celle de la plaque du compositeur. Une seconde arête est une seconde " +
                "surface, et c'est exactement la ligne blanche signalée par le propriétaire " +
                "(« j'ai une ligne blanche, ça devrait être uni »).",
            apres <= 1,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 2 · LE CHAMP DU CODE NE PEINT PLUS DE SECONDE VITRE
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `le champ du Code ne peint plus de seconde vitre`() {
        val screen = codeFile("app/src/main/java/com/onyx/app/ui/code/CodeScreen.kt")
        val fautives = occurrences(screen, "FrostSurface.FIELD")
        assertTrue(
            "le champ du compositeur du Code peint encore sa propre vitre (`FrostSurface.FIELD`) :\n" +
                fautives.joinToString("\n").ifEmpty { "  (aucune ligne, mais des occurrences ?)" } +
                "\n\nC'est cette seconde vitre qui peignait une seconde arête claire " +
                "(blanc 62 %, L=253 sur une surface à L=232) — la ligne blanche du propriétaire. " +
                "Le champ prend [composerFieldSurface] : la vitre de la plaque passe au travers.",
            fautives.isEmpty(),
        )
        val screenCode = screen.readText()
        assertTrue(
            "l'écran Code doit garder **une** vitre pour tout le compositeur " +
                "(`frostedBar(FrostSurface.COMMAND_BAR`) — c'est elle qui porte la lumière de tête, " +
                "l'arête claire et le liseré, une fois.",
            screenCode.contains("frostedBar(FrostSurface.COMMAND_BAR"),
        )
        assertTrue(
            "le champ du Code doit passer par la règle commune (`composerFieldSurface(`).",
            screenCode.contains("composerFieldSurface("),
        )
        assertTrue(
            "le champ du Code ne doit tracer **aucun** contour (`outlined = false`) : un second " +
                "cadre dans une plaque déjà bordée se lit « un champ dans un champ ».",
            screenCode.contains("outlined = false"),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 3 · LE COMPOSANT COMMUN — la règle vit une seule fois, et aucun écran ne la contourne
    // ---------------------------------------------------------------------------------------------

    /** **Les écrans balayés** : les quatorze tuiles, l'écran de démarrage, et l'écran Code. */
    private val ecransBalayes: List<String> = listOf(
        "code/CodeScreen.kt",
        "kanban/KanbanScreen.kt",
        "files/FilesScreen.kt",
        "terminal/TerminalScreen.kt",
        "models/ModelsScreen.kt",
        "models/ModelSheet.kt",
        "tools/ToolsScreen.kt",
        "servers/ServersScreen.kt",
        "settings/LogsScreen.kt",
        "capabilities/CapabilitiesScreen.kt",
        "capabilities/CapabilityParts.kt",
        "insights/InsightsScreen.kt",
        "cron/CronScreen.kt",
        "settings/GatewayScreen.kt",
        "settings/SettingsScreen.kt",
        "chat/components/StartScreen.kt",
    )

    @Test
    fun `aucun ecran balaye ne re-pose une vitre de champ`() {
        val fautes = StringBuilder()
        ecransBalayes.forEach { relative ->
            val file = codeFile("app/src/main/java/com/onyx/app/ui/$relative")
            occurrences(file, "FrostSurface.FIELD").forEach { fautes.append(it).append("\n") }
        }
        assertTrue(
            "une vitre de champ revient sous une autre vitre :\n$fautes\n" +
                "Le défaut ne vient pas de l'écran mais du motif : une surface **dans** une surface " +
                "peint la finition du verre deux fois, donc deux arêtes. La règle est une seule fois " +
                "([composerFieldSurface], `core/…/design/ComposerField.kt`).",
            fautes.isEmpty(),
        )
    }

    @Test
    fun `la regle du fond de champ vit une seule fois dans le composant commun`() {
        val common = codeFile("core/src/main/java/com/onyx/app/ui/design/ComposerField.kt")
        val body = common.readText()
            .substringAfter("fun Modifier.composerFieldSurface(")
            .substringBefore("\n}\n")
        assertTrue(
            "`composerFieldSurface` doit peindre son aplat **conditionnellement** (`if (fill)`) : " +
                "un champ sans `fill` ne peint AUCUN fond, la vitre qui le porte passe au travers.",
            body.contains("if (fill)"),
        )
        assertTrue(
            "`composerFieldSurface` doit tracer son contour **conditionnellement** (`if (outlined)`) : " +
                "c'est ce qui permet à une plaque bordée de ne pas doubler son arête.",
            body.contains("if (outlined)"),
        )
        assertFalse(
            "`composerFieldSurface` ne doit peindre **aucun** barreau opaque en dur " +
                "([OnyxSurfaces.surfaceHigh] / `#FFFFFF`) : c'est le blanc d'aplat que le champ ne " +
                "doit jamais poser derrière son texte.",
            body.contains("surfaceHigh") || body.contains("0xFFFFFFFF"),
        )
        assertTrue(
            "la raison doit rester écrite dans le composant commun — « aucun aplat n'est peint, la " +
                "vitre de la plaque passe au travers » : sans cette phrase, le prochain appelant " +
                "reposera un fond derrière son champ.",
            common.readText().contains("aucun aplat"),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 4 · AUCUN APLAT OPAQUE SOUS UNE VITRE
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `aucun ecran balaye ne pose un aplat opaque sous une vitre`() {
        val fautes = StringBuilder()
        ecransBalayes.forEach { relative ->
            val file = codeFile("app/src/main/java/com/onyx/app/ui/$relative")
            // `SurfaceRole.Bar` et `SurfaceRole.Float` peignent `OnyxSurfaces.surfaceHigh`
            // (`#FFFFFF` en clair) : sous une vitre, l'aplat retire le verre. C'est le défaut
            // mesuré de l'en-tête, corrigé par `glassBarShell` ; il ne revient pas ici.
            listOf(
                "surfaceRole(SurfaceRole.Bar)",
                "surfaceRole(SurfaceRole.Float)",
                "background(OnyxSurfaces.surfaceHigh(",
            ).forEach { motif ->
                occurrences(file, motif).forEach { fautes.append(it).append("\n") }
            }
        }
        assertTrue(
            "un aplat opaque est peint sous une vitre :\n$fautes\n" +
                "Une vitre posée sur un aplat n'a rien à révéler : elle transporte l'aplat. La coque " +
                "d'une barre est [glassBarShell] (forme + filet + ombre, **sans** fond). Le seul " +
                "fond opaque que la maison garde est celui d'une **carte** qui n'est pas sous une " +
                "vitre ([SurfaceRole.Card] : la face de la carte elle-même, sans verre au-dessus).",
            fautes.isEmpty(),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 5 · L'ENCRE DU CHAMP CONTRE LA SURFACE RÉELLEMENT PEINTE
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `l encre du champ du Code tient 4,5 a 1 sur la surface peinte`() {
        val report = StringBuilder("\nENCRE DU CHAMP DU CODE — contre la surface réellement peinte\n")
        report.append("apparence  fond mesuré  voile(α)  encre       rapport  seuil  verdict\n")
        listOf(false, true).forEach { dark ->
            val t = theme(dark)
            // Le fond **réellement derrière** le compositeur : la page de l'écran Code, échantillonnée
            // sur la capture (`L=247` en clair). On garde la valeur mesurée, pas un blanc pur.
            val ground = if (dark) {
                androidx.compose.ui.graphics.Color(0x0A, 0x0A, 0x0C)
            } else {
                androidx.compose.ui.graphics.Color(247, 247, 249)
            }
            val alpha = frostVeilAlpha(FrostSurface.COMMAND_BAR, FrostLevel.FULL, dark)
            val veil = veilColour(t.pageColor, alpha, dark)
            val face = compositedBackdrop(veil, ground)
            val worst = minOf(
                contrastRatio(t.primaryText, face),
                contrastRatio(t.textColor, face),
            )
            report.append(
                "%-10s %-12s %-9.2f %-11s %-8.2f %-6.1f %s\n".format(
                    Locale.ROOT,
                    if (dark) "sombre" else "clair",
                    "#%02X%02X%02X".format(
                        (face.red * 255f).toInt(),
                        (face.green * 255f).toInt(),
                        (face.blue * 255f).toInt(),
                    ),
                    alpha,
                    "#%02X%02X%02X".format(
                        (t.primaryText.red * 255f).toInt(),
                        (t.primaryText.green * 255f).toInt(),
                        (t.primaryText.blue * 255f).toInt(),
                    ),
                    worst,
                    TEXT_CONTRAST_MIN,
                    if (worst >= TEXT_CONTRAST_MIN) "ok" else "ÉCHEC",
                ),
            )
            assertTrue(
                "${if (dark) "sombre" else "clair"} : l'encre du champ vaut $worst contre la surface " +
                    "peinte — sous $TEXT_CONTRAST_MIN:1. Retirer la seconde vitre ne doit rien " +
                    "coûter à la lecture : le levier est la teinte de la plaque ou l'encre — " +
                    "jamais le seuil.",
                worst >= TEXT_CONTRAST_MIN,
            )
        }
        print(report)
    }
}
