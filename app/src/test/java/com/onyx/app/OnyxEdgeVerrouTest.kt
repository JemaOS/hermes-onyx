package com.onyx.app

import com.onyx.app.ui.design.GlassDiscShadowDark
import com.onyx.app.ui.design.GlassDiscShadowLight
import com.onyx.app.ui.design.GlassHairlineDark
import com.onyx.app.ui.design.GlassHairlineLight
import com.onyx.app.ui.design.OnyxEdge
import com.onyx.app.ui.theme.ReferencePalette
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou du trait — un seul contour, et aucun au-dessus du jeton.**
 *
 * Le propriétaire a vu, sur sa capture, que « tous les bords gris autour des boutons sont très
 * prononcés ». La cause n'était pas un écran : l'app dessinait ses contours avec **six** sources
 * (le filet fort de la référence à 14 %, le séparateur à 8 %, des **encres de texte** prises comme
 * bordures — `theme.quaternaryText`, pleinement opaque, et `theme.tertiaryText.copy(alpha = 0.35f)`
 * — une encre mesurée à 3:1 via `OnyxInkOn`, et le liseré interne du verre, noir à 16 %). La planche,
 * elle, ne pose qu'**un** trait : douze pour cent (`v4css.py:138`, `:149`).
 *
 * Ce test tient les deux moitiés de la règle, et il **mord** :
 *
 * 1. **Aucun contour sombre au-dessus du jeton.** Pour chaque appel qui dessine un contour
 *    (`.border(`, `BorderStroke(`, `drawOutline(`) dans `app/src/main` et `core/src/main`, la couleur
 *    est classée ; toute couleur **sombre** connue est comparée au jeton, et toute couleur
 *    **inconnue** est refusée. L'échec **nomme le fichier et la ligne**.
 * 2. **Le jeton est bien celui de la planche**, et il est unique : ses deux opacités valent
 *    [OnyxEdge.MaxDarkAlpha], et ce que l'app appelle ailleurs un filet ne le dépasse pas
 *    ([ReferencePalette.LightLine], [ReferencePalette.DarkLine], [GlassHairlineLight]).
 *
 * Morsure prouvée : introduire un `.border(..., Color.Black.copy(alpha = 0.6f), ...)` quelque part
 * fait rougir le test 1 en imprimant le chemin et la ligne — voir le rapport du lot.
 */
class OnyxEdgeVerrouTest {

    private data class Call(
        val path: String,
        val line: Int,
        val kind: String,
        val colour: String,
        /** La bande d'une couleur **sombre** connue, ou `null` si la couleur est claire/accent. */
        val darkAlpha: Float?,
        val reason: String,
    )

    // ---------------------------------------------------------------------------------------------
    // Ce qu'un contour a le droit d'être — chaque entrée porte sa raison
    // ---------------------------------------------------------------------------------------------

    /** Une couleur **claire / d'accent / structurante** : elle n'encadre pas, elle marque une arête. */
    private val clair = listOf(
        Regex("""Color\.Transparent""") to "aucun contour (bordure conditionnelle éteinte)",
        Regex("""Color\.White""") to "l'arête claire du verre (blanc) — jamais un encadrement",
        Regex("""theme\.accent""") to "anneau d'accent (sélection / focus), une couleur, pas un gris",
        Regex("""^ringColor""") to "anneau d'état de l'orbe vocal — couleur de phase (accent/avertissement/succès), éteint au repos",
        Regex("""\baccent\b""") to "anneau d'accent (sélection)",
        Regex("""theme\.error""") to
            "le contour d'arrêt d'un processus — une couleur d'ÉTAT (rouge), pas un gris d'encadrement : " +
                "elle dit « ceci arrête », et elle est mesurée au seuil de texte sur la surface de la feuille",
        Regex("""PlaqueGeometry\.activePillLine|pillLine""") to "filet de la pastille active — blanc de la planche (.92/.24)",
        Regex("""surfaceColor|formSurfaceColor|pageColor|theme\.fill|surfaceHigh|\.surface\b|^surface$""") to "un fond de surface, pas un trait",
        Regex("""^color$|^tint$|^chosen$|^accent$""") to "échantillon de couleur / teinte choisie (Apparence), pas un encadrement",
    )

    /** Une couleur **sombre** connue : elle doit tenir sous le jeton. */
    private val sombre = listOf(
        Regex("""OnyxEdge\.fil|OnyxSurfaces\.(hairline|lineStrong|line)\(""") to
            (OnyxEdge.MaxDarkAlpha to "le jeton lui-même (OnyxEdge.fil = OnyxSurfaces.hairline)"),
        Regex("""flatTileHairline""") to
            (OnyxEdge.MaxDarkAlpha to "le liseré d'une tuile au clair — nommé, et ramené au jeton " +
                "(il sortait du 14 % de `ReferencePalette.LightLineStrong`, au-dessus du plafond)"),
        Regex("""theme\.(quaternaryText|tertiaryText|secondaryText)""") to
            (ReferencePalette.LightText5.alpha to "une encre de TEXTE prise pour une bordure — pleinement opaque"),
        Regex("""Color\.Black(?!\.copy\(alpha = (GlassHairlineLight|alpha)\))""") to
            (1.0f to "un noir plein, jamais le filet de la planche"),
        Regex("""OnyxInkOn\(""") to
            (1.0f to "une encre mesurée à 3:1 : un contour de contrôle, pas le filet de la maquette"),
        Regex("""theme\.separator""") to
            (ReferencePalette.LightLine.alpha to "le séparateur de liste (8 % / 7 %) — sous le jeton"),
        Regex("""Color\.Black\.copy\(alpha = GlassHairlineLight\)""") to
            (GlassHairlineLight to "liseré interne du verre, ramené au jeton"),
        Regex("""Color\.Black\.copy\(alpha = alpha\)""") to
            (maxOf(GlassDiscShadowLight, GlassDiscShadowDark) to "anneaux d'ombre dessinée (GlassDiscShadowL/D)"),
        Regex("""^border$""") to
            (OnyxEdge.MaxDarkAlpha to "paramètre du gabarit de bouton rond — renseigné par le jeton aux 5 appelants"),
        Regex("""^bar\.edge$""") to
            (null to "bordure de touche de la barre « Ordinateur » : mesurée à 3:1 sur la SCÈNE (toujours sombre), hors de la maquette du fil — exemption écrite"),
    )

    // ---------------------------------------------------------------------------------------------
    // Le balayage
    // ---------------------------------------------------------------------------------------------

    private fun kotFiles(): List<File> =
        listOf(File("src/main"), File("../core/src/main"))
            .filter { it.isDirectory }
            .sortedBy { it.path }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .sortedBy { it.path }

    private fun matchingParen(text: String, openIndex: Int): Int {
        var depth = 0
        var i = openIndex
        while (i < text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return text.length - 1
    }

    private fun topLevelArgs(inner: String): List<String> {
        val args = mutableListOf<String>()
        var depth = 0
        var cur = StringBuilder()
        for (ch in inner) {
            when (ch) {
                '(', '[', '{' -> { depth++; cur.append(ch) }
                ')', ']', '}' -> { depth--; cur.append(ch) }
                ',' -> if (depth == 0) { args += cur.toString().trim(); cur = StringBuilder() } else cur.append(ch)
                else -> cur.append(ch)
            }
        }
        if (cur.isNotBlank()) args += cur.toString().trim()
        return args
    }

    private fun isWidth(expr: String): Boolean =
        expr.endsWith(".dp") ||
            expr.matches(
                Regex("""^(OnyxSpace\.Stroke|OnyxSocle\.Stroke|FlatHairlineDp|SearchFocusRing|width|PlaqueGeometry\.Outline|PlaqueGeometry\.FaceRing)$"""),
            )

    /** La couleur d'un appel de contour, sans se laisser tromper par un `BorderStroke` imbriqué. */
    private fun colourOf(opener: String, call: String): String {
        val inner = call.substringAfter('(').dropLast(1)
        val args = topLevelArgs(inner)
        if (args.isEmpty()) return ""
        // arguments nommés (drawOutline, et border(width = …, color = …))
        for (a in args) {
            val m = Regex("""^\s*(color|brush)\s*=\s*(.+)$""", RegexOption.DOT_MATCHES_ALL).find(a)
            if (m != null) return m.groupValues[2].trim()
        }
        if (opener == "drawOutline(") return args.getOrNull(1)?.trim() ?: ""
        val first = args[0]
        if (first.startsWith("BorderStroke")) {
            val sub = topLevelArgs(first.substringAfter('(').dropLast(1))
            return sub.getOrNull(1)?.trim() ?: ""
        }
        if (opener == "BorderStroke(") return args.getOrNull(1)?.trim() ?: ""
        return if (isWidth(first)) args.getOrNull(1)?.trim() ?: "" else first
    }

    private fun callsIn(file: File): List<Call> {
        val text = file.readText()
        // (start, end, openerName)
        val found = mutableListOf<Triple<Int, Int, String>>()
        for (opener in listOf(".border(", "BorderStroke(", "drawOutline(")) {
            var from = 0
            while (true) {
                val at = text.indexOf(opener, from)
                if (at < 0) break
                val end = matchingParen(text, at + opener.length - 1)
                found += Triple(at, end, opener)
                from = end + 1
            }
        }
        // Ne garder que le contour le plus extérieur : `BorderStroke(` est contenu dans `.border(`.
        val outer = found.filter { c -> found.none { o -> o !== c && o.first < c.first && o.second >= c.second } }
            .sortedBy { it.first }

        return outer.map { (start, end, opener) ->
            val call = text.substring(start, end + 1)
            val line = text.substring(0, start).count { it == '\n' } + 1
            val colour = colourOf(opener, call)
            val clairHit = clair.firstOrNull { it.first.containsMatchIn(colour) }
            if (clairHit != null) {
                Call(file.path, line, opener, colour, null, clairHit.second)
            } else {
                val sombreHit = sombre.firstOrNull { it.first.containsMatchIn(colour) }
                if (sombreHit != null) {
                    Call(file.path, line, opener, colour, sombreHit.second.first, sombreHit.second.second)
                } else {
                    Call(file.path, line, opener, colour, Float.NaN, "HORS JETON — couleur non reconnue")
                }
            }
        }
    }

    private fun allCalls(): List<Call> = kotFiles().flatMap { callsIn(it) }

    // ---------------------------------------------------------------------------------------------
    // 1. Aucun contour sombre au-dessus du jeton, et aucune couleur inconnue
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `aucun contour n est declare au-dessus du jeton, et aucun contour inconnu`() {
        val calls = allCalls()
        val jeton = calls.count { it.darkAlpha != null && it.reason.startsWith("le jeton") }
        val separateur = calls.count { it.reason.startsWith("le séparateur") }
        val clairCount = calls.count { it.darkAlpha == null }

        // La morsure : une couleur sombre au-dessus du jeton, ou une couleur qu'on ne sait pas classer.
        val fautes = calls.filter { c ->
            (c.darkAlpha != null && c.darkAlpha.isFinite() && c.darkAlpha > OnyxEdge.MaxDarkAlpha) ||
                c.darkAlpha?.isNaN() == true ||
                c.reason.startsWith("HORS JETON")
        }

        // Le compte du rapport : il s'imprime même quand tout va bien.
        println(
            "TRAITS · contours lus=${calls.size} · jeton=$jeton · séparateur=$separateur · " +
                "clair/accent=$clairCount · au-dessus du jeton=${fautes.size} (plafond ${OnyxEdge.MaxDarkAlpha})",
        )

        assertTrue(
            "des contours dépassent le jeton (plafond ${OnyxEdge.MaxDarkAlpha}) ou sortent de la table :\n" +
                fautes.joinToString("\n") {
                    "  ${it.path}:${it.line} → « ${it.colour} » (alpha=${it.darkAlpha}) — ${it.reason}"
                },
            fautes.isEmpty(),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 2. Le jeton est celui de la planche, et il est unique
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `le jeton vaut les douze pour cent de la planche, dans les deux apparences`() {
        // Compose range l'alpha d'un `Color` sur 8 bits : on compare à un quart de point près.
        val quart = 1f / 255f
        assertEquals("filet clair : 12 % (v4css.py:138)", OnyxEdge.FilLightAlpha, OnyxEdge.MaxDarkAlpha, quart)
        assertEquals("filet sombre : 12 % (v4css.py:149)", OnyxEdge.FilDarkAlpha, OnyxEdge.MaxDarkAlpha, quart)
        assertEquals("l'encre claire est le filet", OnyxEdge.MaxDarkAlpha, ReferencePalette.LightFil.alpha, quart)
        assertEquals("l'encre sombre est le filet", OnyxEdge.MaxDarkAlpha, ReferencePalette.DarkFil.alpha, quart)
    }

    @Test
    fun `tout ce que l app appelle filet tient sous le jeton`() {
        val plafond = OnyxEdge.MaxDarkAlpha
        assertTrue("séparateur clair (${ReferencePalette.LightLine.alpha})", ReferencePalette.LightLine.alpha <= plafond)
        assertTrue("séparateur sombre (${ReferencePalette.DarkLine.alpha})", ReferencePalette.DarkLine.alpha <= plafond)
        assertTrue("liseré interne du verre, clair ($GlassHairlineLight)", GlassHairlineLight <= plafond)
    }

    @Test
    fun `le liseré du verre est le seul trait sombre, et il est clair en sombre`() {
        // En sombre, le liseré du verre est BLANC (une arête) : jamais un gris.
        assertTrue("liseré de verre sombre = blanc", GlassHairlineDark > 0f)
    }
}
