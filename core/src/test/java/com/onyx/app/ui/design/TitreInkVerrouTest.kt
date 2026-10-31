package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de l'encre des titres et des en-têtes — et il mord.**
 *
 * Le défaut qu'il ferme : en apparence **sombre**, le titre de l'application (le nom de la passerelle
 * dans la tête du volet) restait **noir**. Un style du socle (`socleDisplayStyle`, `theme.headline`,
 * `theme.largeTitle`) ne décrit que la **forme** — corps, poids, interlettrage — **jamais** la
 * couleur. Un `BasicText` qui le reçoit **sans** `.copy(color = …)` n'hérite de rien : la Fondation
 * écrit alors du **noir par défaut**. Le titre était donc conforme en clair (noir sur blanc, par
 * accident) et **illisible en sombre** (noir sur la page de nuit) : il ne lisait pas le thème.
 *
 * Le test tombe dans **trois sens**, et c'est ce qui le fait mordre :
 *
 * 1. **La source (la provenance de l'encre).** Tout `BasicText` dont le `style` est un **style de
 *    titre** — et qui ne reçoit **aucune** couleur — fait échouer le test, en **nommant le fichier
 *    et la ligne**. Une couleur **en dur** (`Color.White`, `Color.Black`, `Color(0x…)`) sur un titre
 *    tombe au même endroit : l'encre d'un titre **vient du thème**, elle ne s'écrit pas à la main.
 * 2. **La mesure.** L'encre d'un titre est **exactement** celle du thème ([socleTitleInk] =
 *    `theme.textColor`) — pas une valeur dérivée qui se substituerait à elle — sur **tous** les
 *    thèmes.
 * 3. **Le contraste, et sa PARITÉ.** Sur chaque surface de la maison où un titre se pose (page,
 *    plan, carte, volet), le rapport de contraste de l'encre est **≥ 4,50:1 dans les deux
 *    apparences**, et les deux rapports sont **en parité** (l'un ne vaut pas moins de la moitié de
 *    l'autre) : une encre qui s'effondre d'une apparence à l'autre — le défaut même — fait tomber
 *    l'écart.
 *
 * Le tableau est **imprimé** — il est le relevé du rapport, recopié, jamais ressaisi.
 */
class TitreInkVerrouTest {

    private val root: File = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    /**
     * Les styles qui font un **titre** ou un **en-tête** de la maison : les grands corps de
     * l'échelle (`largeTitle` 30, `title`/`title2` 26, `title3`/`headline` 16) et les styles du
     * socle qui les nomment. Le **corps** (15) et la **méta** (12) n'en sont pas — ce sont des
     * libellés et des données, mesurés par d'autres verrous.
     */
    private val titleStyles = listOf(
        ".largeTitle", ".title3", ".title2", ".title.",
        ".headline",
        "socleDisplayStyle", "socleHeadingStyle", "socleTitleStyle",
    )

    private val scanRoots = listOf("app/src/main", "core/src/main")

    /**
     * **Les titres qui n'ont PAS le droit de suivre le thème, et pourquoi** — deux exceptions
     * déclarées, comme la charte l'exige. Un titre posé **sur une image** (la caméra d'un scan, la
     * couverture d'une vignette) ne se lit pas contre une surface de la maison : son encre est celle
     * de la **scène**, et le blanc y est la seule encre lisible, dans les deux apparences. Aucune
     * autre n'est admise : ajouter un fichier ici est une décision, et elle s'écrit avec sa raison.
     */
    private val mediaTitleInkAllowList: Map<String, String> = mapOf(
        "app/src/main/java/com/onyx/app/ui/setup/SetupCodeScanner.kt" to
            "les titres du scan vivent SUR l'aperçu de la caméra (le fond est l'image, pas une " +
                "surface du thème) : l'encre blanche est celle de la scène, elle ne peut pas venir " +
                "d'un thème. Même famille que la barre d'un lecteur vidéo (`BarInk`).",
        "core/src/main/java/com/onyx/app/ui/design/Galerie.kt" to
            "`.cvttl`, le titre d'une vignette de couverture : il est posé SUR l'image, avec son " +
                "ombre — la planche le donne blanc (`hcss.py:149`), et c'est l'encre de la scène, " +
                "pas celle d'une surface de la maison.",
    )

    private fun theme(theme: OnyxTheme, dark: Boolean): ResolvedTheme =
        ResolvedTheme(theme = theme, palette = theme.palette, dark = dark, fonts = OnyxFonts.System)

    private fun name(color: Color): String = "#%02X%02X%02X".format(
        (color.red * 255).roundToInt(),
        (color.green * 255).roundToInt(),
        (color.blue * 255).roundToInt(),
    )

    // =============================================================================================
    // 1) LA SOURCE — la provenance de l'encre du titre
    // =============================================================================================

    /**
     * Un titre **sans** couleur reçoit le **noir** de la Fondation ; une couleur **en dur** ne suit
     * pas le thème. Les deux se nomment ici, `fichier:ligne`.
     */
    @Test
    fun `tout titre porte une encre qui vient du theme`() {
        val offences = mutableListOf<String>()
        var titles = 0
        var dispensed = 0
        var files = 0
        scanRoots.forEach { rel ->
            val dir = File(root, rel)
            dir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".kt") }
                .forEach { file ->
                    files++
                    val src = stripComments(file.readText())
                    val relPath = file.relativeTo(root).path
                    val onMedia = mediaTitleInkAllowList.containsKey(relPath)
                    titleCalls(src).forEach { call ->
                        titles++
                        val style = call.style ?: return@forEach
                        val hasColor = style.contains("color = ") || style.contains("color=") || call.hasColorParam
                        val line = src.lineOf(call.styleOffset)
                        val here = "$relPath:$line"
                        if (onMedia) {
                            dispensed++
                            return@forEach
                        }
                        if (!hasColor) {
                            offences.add(
                                "$here — un titre/en-tête reçoit un style de TITRE SANS couleur " +
                                    "(${style.oneLine()}) : la Fondation écrit du NOIR, le titre ne " +
                                    "suit pas l'apparence sombre. Écrire `.copy(color = socleTitleInk(theme))`.",
                            )
                            return@forEach
                        }
                        val literal = listOf("Color.White", "Color.Black", "Color(0x", "Color(0f", "Color(1f")
                            .firstOrNull { style.contains(it) }
                        if (literal != null) {
                            offences.add(
                                "$here — l'encre d'un titre est une couleur EN DUR (`$literal`) : elle " +
                                    "ne suit pas le thème. `socleTitleInk(theme)` est la seule encre d'un titre.",
                            )
                        }
                    }
                }
        }
        println("== TITRES & EN-TÊTES — provenance de l'encre ==")
        println(
            "   fichiers Kotlin lus : $files · titres/en-têtes repérés : $titles · " +
                "dispensés (posés sur une image) : $dispensed · hors thème : ${offences.size}",
        )
        assertTrue(
            "Des titres/en-têtes ne lisent pas le thème :\n" + offences.joinToString("\n"),
            offences.isEmpty(),
        )
        assertTrue("aucun titre/en-tête repéré — le balayage ne mesure rien", titles > 0)
    }

    // =============================================================================================
    // 2) LA MESURE — l'encre du titre EST celle du thème
    // =============================================================================================

    /** L'encre d'un titre est **exactement** l'encre pleine du thème — pas une valeur de remplacement. */
    @Test
    fun `l'encre d'un titre est celle du theme, dans chaque theme et chaque apparence`() {
        OnyxTheme.entries.forEach { nom ->
            listOf(false, true).forEach { d ->
                val t = theme(nom, d)
                assertEquals(
                    "TITRE ($nom, ${if (d) "sombre" else "clair"}) : socleTitleInk ${name(socleTitleInk(t))} " +
                        "n'est pas l'encre pleine du thème ${name(t.textColor)} — une encre dérivée s'est " +
                        "substituée à elle. core/src/main/java/com/onyx/app/ui/design/SocleKit.kt (socleTitleInk)",
                    t.textColor, socleTitleInk(t),
                )
            }
        }
    }

    // =============================================================================================
    // 3) LE CONTRASTE, ET SA PARITÉ ENTRE LES DEUX APPARENCES
    // =============================================================================================

    /** Les surfaces de la maison où un titre se pose : la page, le plan, la carte, le volet. */
    private fun titleSurfaces(t: ResolvedTheme): List<Pair<String, Color>> = listOf(
        "page" to t.pageColor,
        "plan" to t.formPageColor,
        "carte" to t.surfaceColor,
        "volet" to sidebarPaneColor(t),
    )

    /**
     * **Le relevé complet : `thème → surface → rapport clair → rapport sombre → parité → verdict`.**
     *
     * Le rapport doit tenir **4,50:1** (un titre est un texte) dans **les deux** apparences, et les
     * deux rapports doivent être **en parité** — l'un au moins la moitié de l'autre. Une encre qui
     * vaut 21:1 en clair et 1,0:1 en sombre (le défaut d'origine : le noir par défaut) échoue les
     * deux contrôles.
     */
    @Test
    fun `le contraste d'un titre tient le seuil et la parite dans les deux apparences`() {
        println("== TITRES & EN-TÊTES — contraste de l'encre du thème (seuil 4,50:1, parité ≥ 0,50) ==")
        var worstDark = Float.MAX_VALUE
        var worstLight = Float.MAX_VALUE
        var worstParity = Float.MAX_VALUE
        OnyxTheme.entries.forEach { nom ->
            val light = theme(nom, false)
            val dark = theme(nom, true)
            val lightSurfaces = titleSurfaces(light).toMap()
            titleSurfaces(dark).forEach { (role, darkSurface) ->
                val inkDark = socleTitleInk(dark)
                val inkLight = socleTitleInk(light)
                val lightSurface = lightSurfaces.getValue(role)
                val rd = OnyxContrast.ratio(inkDark, darkSurface)
                val rl = OnyxContrast.ratio(inkLight, lightSurface)
                val parity = minOf(rd, rl) / maxOf(rd, rl)
                worstDark = minOf(worstDark, rd)
                worstLight = minOf(worstLight, rl)
                worstParity = minOf(worstParity, parity)
                println(
                    "   $nom / $role : clair ${name(inkLight)} sur ${name(lightSurface)} = " +
                        "${"%.2f".format(rl)}:1 · sombre ${name(inkDark)} sur ${name(darkSurface)} = " +
                        "${"%.2f".format(rd)}:1 · parité ${"%.2f".format(parity)}",
                )
                assertTrue(
                    "TITRE ($nom, sombre) sur $role : ${name(inkDark)} sur ${name(darkSurface)} = " +
                        "${"%.2f".format(rd)}:1, sous 4,50:1 — " +
                        "core/src/main/java/com/onyx/app/ui/design/SocleKit.kt (socleTitleInk)",
                    rd >= OnyxContrast.TEXT_MIN,
                )
                assertTrue(
                    "TITRE ($nom, clair) sur $role : ${name(inkLight)} sur ${name(lightSurface)} = " +
                        "${"%.2f".format(rl)}:1, sous 4,50:1 — " +
                        "core/src/main/java/com/onyx/app/ui/design/SocleKit.kt (socleTitleInk)",
                    rl >= OnyxContrast.TEXT_MIN,
                )
                assertTrue(
                    "TITRE ($nom) sur $role : les rapports clair ${"%.2f".format(rl)}:1 et sombre " +
                        "${"%.2f".format(rd)}:1 ne sont pas en parité (${"%.2f".format(parity)} < 0,50) — " +
                        "l'encre ne suit pas l'apparence. " +
                        "core/src/main/java/com/onyx/app/ui/design/SocleKit.kt (socleTitleInk)",
                    parity >= 0.5f,
                )
            }
        }
        println(
            "   pire encre sombre = ${"%.2f".format(worstDark)}:1 · pire encre claire = " +
                "${"%.2f".format(worstLight)}:1 · pire parité = ${"%.2f".format(worstParity)}",
        )
    }

    // =============================================================================================
    //  L'appareillage : lecture des sources Kotlin (commentaires retirés, arguments découpés)
    // =============================================================================================

    private data class TitleCall(val style: String?, val styleOffset: Int, val hasColorParam: Boolean)

    /** Les `BasicText` dont le `style =` est un style de **titre**. */
    private fun titleCalls(src: String): List<TitleCall> {
        val out = mutableListOf<TitleCall>()
        listOf("BasicText", "AndroidBasicText").forEach { fn ->
            var from = 0
            while (true) {
                val found = findCall(src, fn, from) ?: break
                from = found.end
                val args = splitArgs(found.inner)
                var style: String? = null
                var styleOffset = found.parenIndex
                var hasColor = false
                args.forEach { arg ->
                    val a = arg.trim()
                    if (a.startsWith("style =") || a.startsWith("style=")) {
                        style = a.substringAfter("=").trim()
                        styleOffset = found.parenIndex + 1 + found.inner.indexOf("style").coerceAtLeast(0)
                    }
                    if (a.startsWith("color =") || a.startsWith("color=")) hasColor = true
                }
                val st = style ?: return@forEach
                if (titleStyles.any { st.contains(it) }) out.add(TitleCall(st, styleOffset, hasColor))
            }
        }
        return out
    }

    private data class Call(val parenIndex: Int, val inner: String, val end: Int)

    private fun findCall(src: String, name: String, from: Int): Call? {
        var i = from
        while (true) {
            val j = src.indexOf(name, i)
            if (j < 0) return null
            val k = j + name.length
            if (k < src.length && src[k] == '(') {
                var depth = 0
                var p = k
                var inStr: Char? = null
                var escaped = false
                while (p < src.length) {
                    val c = src[p]
                    if (inStr != null) {
                        if (escaped) escaped = false
                        else if (c == '\\') escaped = true
                        else if (c == inStr) inStr = null
                    } else when {
                        c == '"' || c == '\'' -> inStr = c
                        c == '(' -> depth++
                        c == ')' -> {
                            depth--
                            if (depth == 0) return Call(j, src.substring(k + 1, p), p)
                        }
                    }
                    p++
                }
                return null
            }
            i = j + name.length
        }
    }

    private fun splitArgs(s: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var depth = 0
        var inStr: Char? = null
        var escaped = false
        for (c in s) {
            if (inStr != null) {
                cur.append(c)
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == inStr) inStr = null
                continue
            }
            when (c) {
                '"', '\'' -> { inStr = c; cur.append(c) }
                '(', '[', '{' -> { depth++; cur.append(c) }
                ')', ']', '}' -> { depth--; cur.append(c) }
                ',' -> if (depth == 0) { out.add(cur.toString()); cur.setLength(0) } else cur.append(c)
                else -> cur.append(c)
            }
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    /** Les commentaires deviennent des espaces **de même longueur** : les numéros de ligne ne bougent pas. */
    private fun stripComments(src: String): String {
        val out = StringBuilder(src)
        var i = 0
        var inStr: Char? = null
        var escaped = false
        var raw = false
        while (i < src.length) {
            val c = src[i]
            if (raw) {
                if (c == '"' && src.startsWith("\"\"\"", i)) { raw = false; i += 3; continue }
                i++
                continue
            }
            if (inStr != null) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == inStr) inStr = null
                i++
                continue
            }
            when {
                c == '"' && src.startsWith("\"\"\"", i) -> { raw = true; i += 3 }
                c == '"' || c == '\'' -> { inStr = c; i++ }
                c == '/' && i + 1 < src.length && src[i + 1] == '/' -> {
                    while (i < src.length && src[i] != '\n') { out[i] = ' '; i++ }
                }
                c == '/' && i + 1 < src.length && src[i + 1] == '*' -> {
                    out[i] = ' '; out[i + 1] = ' '; i += 2
                    while (i < src.length && !(src[i] == '*' && i + 1 < src.length && src[i + 1] == '/')) {
                        if (src[i] != '\n') out[i] = ' '
                        i++
                    }
                    if (i < src.length) { out[i] = ' '; if (i + 1 < src.length) out[i + 1] = ' '; i += 2 }
                }
                else -> i++
            }
        }
        return out.toString()
    }

    private fun String.lineOf(offset: Int): Int = substring(0, offset.coerceIn(0, length)).count { it == '\n' } + 1

    private fun String.oneLine(): String = replace(Regex("\\s+"), " ").trim().take(90)
}
