package com.onyx.app.ui.identite

import com.onyx.app.ui.design.OnyxRune
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **LE LOGO DE L'APPLICATION, ET UNE SEULE RUNE.**
 *
 * Trois défauts, de la même famille — *l'application se représentait avec autre chose que sa
 * marque* :
 *
 *  1. **la rune divergente.** L'icône du lanceur porte une rune **ouverte** (deux jambes séparées) ;
 *     le glyphe que l'app dessinait pour elle-même la **refermait** (une barre en bas). Le tracé vit
 *     maintenant **une seule fois** ([OnyxRune.PATH_DATA]) et la ressource de notification s'y
 *     conforme ;
 *  2. **l'initiale mise à la place du logo.** La carte du serveur (Réglages) et le rond du pied du
 *     volet affichaient la **première lettre** du nom du serveur. Une initiale n'est pas un signe.
 *     Partout où l'app affirme son identité, elle porte **la marque** ;
 *  3. **LA TEINTE MISE À LA PLACE DE LA MARQUE — le verrou retourné.** La marque était dessinée
 *     comme un **élément d'interface** : un **glyphe monochrome à l'encre mesurée** sur un **disque
 *     teinté de l'accent** — d'où une **rune bleue sur un disque bleu pâle**. C'est **ma faute** :
 *     la consigne « glyphe monochrome à l'encre sur disque teinté » est une règle d'interface, et
 *     appliquée à l'identité elle produit un logo qui **change de couleur avec le thème**. Une
 *     marque **n'est pas** un élément d'interface : elle est **l'identité** de l'app et **ne se
 *     dérive pas du thème**. Le verrou n'exige donc plus un « disque teinté » : il exige **la
 *     marque exacte** — la plaque sombre, le cercle et la rune, aux teintes de l'icône de lanceur —
 *     et il **échoue dès qu'une teinte de thème ou d'accent entre dans le dessin du logo**.
 *
 * Chaque échec **nomme le fichier et la ligne**. Et le verrou est **mordu** : des sources fautives
 * fabriquées à partir des vraies (la rune refermée d'avant, l'initiale d'avant, et **la teinte
 * d'accent remise dans le logo**) doivent le faire crier.
 */
class RuneSourceUniqueTest {

    private fun appSource(path: String): String = File("src/main/java/com/onyx/app/$path").readText()

    private fun appRes(path: String): String = File("src/main/res/$path").readText()

    private fun coreSource(path: String): String = File("../core/src/main/$path").readText()

    /** La source du **dessin de la marque** : c'est ce fichier que le verrou de teinte surveille. */
    private val brandPath = "java/com/onyx/app/ui/design/OnyxBrand.kt"

    private fun brandSource(): String = coreSource(brandPath)

    /** La copie gelée de la rune de l'icône : le juge, avec sa provenance. */
    private val reference: Map<String, String> = File("src/test/resources/onyx-rune-reference.txt")
        .readLines()
        .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        .associate { line ->
            val key = line.substringBefore('=')
            key to line.substringAfter('=')
        }

    private val referencePath: String get() = reference.getValue("pathData")
    private val referenceStroke: String get() = reference.getValue("strokeWidth")

    /** Le numéro de ligne (1-indexé) d'un fragment dans un texte — 0 s'il n'y est pas. */
    private fun lineOf(text: String, needle: String): Int {
        val index = text.indexOf(needle)
        if (index < 0) return 0
        return text.take(index).count { it == '\n' } + 1
    }

    /** Le numéro de ligne (1-indexé) d'une position déjà connue. */
    private fun lineAt(text: String, index: Int): Int = text.take(index).count { it == '\n' } + 1

    // =============================================================================================
    // 1 · Une seule source de vérité pour la rune
    // =============================================================================================

    private fun runeViolations(relativePath: String, source: String): List<String> {
        val out = mutableListOf<String>()
        val traceLine = source.lines().withIndex().firstOrNull { it.value.contains("android:pathData=") }
        if (traceLine == null) {
            out += "$relativePath:1 : aucune ligne ne porte « android:pathData= » — " +
                "la ressource ne publie aucun tracé de rune"
            return out
        }
        val trace = traceLine.value.substringAfter("android:pathData=\"").substringBefore('"')
        if (trace != referencePath) {
            out += "$relativePath:${traceLine.index + 1} : le tracé de la rune n'est pas celui de " +
                "la référence — « $trace » au lieu de « $referencePath »"
        }
        val strokeLine = source.lines().withIndex().firstOrNull { it.value.contains("android:strokeWidth=") }
        if (strokeLine == null) {
            out += "$relativePath:1 : aucune ligne ne porte « android:strokeWidth= »"
        } else {
            val stroke = strokeLine.value.substringAfter("android:strokeWidth=\"").substringBefore('"')
            if (stroke != referenceStroke) {
                out += "$relativePath:${strokeLine.index + 1} : l'épaisseur du trait n'est pas celle " +
                    "de la référence — « $stroke » au lieu de « $referenceStroke »"
            }
        }
        if (trace.endsWith("Z", ignoreCase = true)) {
            out += "$relativePath:${traceLine.index + 1} : le tracé est REFERMÉ (« Z ») — la rune de " +
                "l'icône est ouverte, et un tracé refermé fait un second signe dans l'application"
        }
        return out
    }

    @Test
    fun `le trace de la maison est mot pour mot celui de l icone`() {
        assertEquals(
            "OnyxRune.PATH_DATA doit être le tracé de la référence (the reference fixture)",
            referencePath,
            OnyxRune.PATH_DATA,
        )
        assertEquals(
            "OnyxRune.STROKE_WIDTH doit être l'épaisseur de la référence",
            referenceStroke.toFloat(),
            OnyxRune.STROKE_WIDTH,
            0.0005f,
        )
    }

    @Test
    fun `la ressource de notification obeit a la source unique`() {
        val path = "res/drawable/ic_launcher_foreground.xml"
        val violations = runeViolations("core/src/main/$path", coreSource(path))
        assertTrue(
            "la ressource de notification ne porte pas la rune de la maison :\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `le recouvrement de silhouette entre la ressource et la reference vaut un`() {
        val source = coreSource("res/drawable/ic_launcher_foreground.xml")
        val trace = source.lines().first { it.contains("android:pathData=") }
            .substringAfter("android:pathData=\"").substringBefore('"')
        val stroke = source.lines().first { it.contains("android:strokeWidth=") }
            .substringAfter("android:strokeWidth=\"").substringBefore('"').toFloat()
        val iou = OnyxRune.iouAgainstReference(trace, stroke)
        println(
            "IoU ressource de l'app (" + trace + " @ " + stroke + ") contre référence (" +
                referencePath + " @ " + referenceStroke + ") = " + "%.4f".format(iou),
        )
        assertEquals("1,0 attendu : c'est le même signe", 1.0f, iou, 0.0005f)
    }

    // =============================================================================================
    // 2 · La marque porte EXACTEMENT les teintes de l'icône de lanceur
    // =============================================================================================

    /** Tous les littéraux `Color(0xFF……)` d'une source, avec leur ligne. */
    private fun colourLiterals(source: String): List<Pair<String, Int>> {
        val regex = Regex("""Color\(0x([0-9A-Fa-f]{8})\)""")
        return regex.findAll(source).map { m ->
            m.groupValues[1].uppercase() to lineAt(source, m.range.first)
        }.toList()
    }

    /** La couleur dont la **ligne** contient [marker] (le nom de la constante Kotlin). */
    private fun colourFor(source: String, marker: String): Pair<String, Int>? {
        val lines = source.lines()
        return colourLiterals(source).firstOrNull { (_, line) ->
            line in 1..lines.size && lines[line - 1].contains(marker)
        }
    }

    private fun hexIn(xml: String, regex: Regex): List<String> =
        regex.findAll(xml).map { it.groupValues[1].substringAfter('#').uppercase() }.toList()

    @Test
    fun `la marque porte les teintes exactes de l icone du lanceur`() {
        val brand = brandSource()
        val colors = appRes("values/colors.xml")
        val foreground = appRes("drawable/ic_launcher_adaptive_foreground.xml")

        val report = StringBuilder()
        val faults = mutableListOf<String>()

        fun compare(label: String, kotlinMarker: String, expected: String, provenance: String) {
            val found = colourFor(brand, kotlinMarker)
            if (found == null) {
                faults += "core/src/main/$brandPath:1 : la marque ne porte pas « $kotlinMarker »"
                return
            }
            val (hex, line) = found
            val ok = hex == expected.uppercase()
            report.append(
                "  " + label + " : #" + hex + " vs icône #" + expected.uppercase() +
                    (if (ok) "  ok" else "  DIVERGE") + "\n",
            )
            if (!ok) {
                faults += "core/src/main/$brandPath:$line : « $kotlinMarker » vaut #$hex au lieu de " +
                    "#${expected.uppercase()} — $provenance"
            }
        }

        val plate = Regex("""name="ic_launcher_background">#(FF[0-9A-Fa-f]{6})<""")
            .find(colors)?.groupValues?.get(1)
            ?: run {
                faults += "app/src/main/res/values/colors.xml:1 : « ic_launcher_background » introuvable"
                "00000000"
            }
        compare("plaque", "Plate", plate, "@color/ic_launcher_background")

        val gradient = hexIn(foreground, Regex("""(?:start|end)Color="#(FF[0-9A-Fa-f]{6})""""))
        if (gradient.size >= 2) {
            compare("cercle haut", "CircleTop", gradient[0], "ic_launcher_adaptive_foreground.xml (dégradé)")
            compare("cercle bas", "CircleBottom", gradient[1], "ic_launcher_adaptive_foreground.xml (dégradé)")
        } else {
            faults += "app/src/main/res/drawable/ic_launcher_adaptive_foreground.xml:1 : le dégradé du cercle est introuvable"
        }

        val strokes = hexIn(foreground, Regex("""strokeColor="#(FF[0-9A-Fa-f]{6})""""))
        if (strokes.size >= 2) {
            compare("contour", "Outline", strokes[0], "ic_launcher_adaptive_foreground.xml (contour)")
            compare("rune", "RuneInk", strokes[1], "ic_launcher_adaptive_foreground.xml (rune)")
        } else {
            faults += "app/src/main/res/drawable/ic_launcher_adaptive_foreground.xml:1 : les traits du contour/de la rune sont introuvables"
        }

        println("Teintes de la marque contre l'icône du lanceur :\n" + report)
        assertTrue(
            "la marque n'est pas aux teintes de l'icône du lanceur :\n" + faults.joinToString("\n"),
            faults.isEmpty(),
        )
    }

    // =============================================================================================
    // 3 · Le verrou de teinte : AUCUNE teinte de thème ni d'accent dans le logo
    // =============================================================================================

    /** Les formes d'écriture d'une **teinte dérivée du thème ou de l'accent**. */
    private val themeTints = listOf(
        "LocalOnyxTheme",
        "theme.accent",
        "theme.",
        "accent",
        "tint",
        "disc",
        "OnyxContrast",
        "copy(",
    )

    /**
     * Neutralise les **commentaires** en gardant les positions : on remplace chaque caractère de
     * commentaire par une espace, sans changer les longueurs ni les retours à la ligne. Le verrou
     * regarde ainsi le **code**, jamais la prose qui l'explique (un commentaire qui dit « aucune
     * teinte d'accent » ne doit pas faire échouer le contrôle).
     */
    private fun maskComments(source: String): String {
        val out = StringBuilder(source.length)
        var index = 0
        var inLine = false
        var inBlock = false
        var inString = false
        while (index < source.length) {
            val ch = source[index]
            val next = source.getOrNull(index + 1)
            when {
                inLine -> {
                    if (ch == '\n') {
                        inLine = false
                        out.append('\n')
                    } else {
                        out.append(' ')
                    }
                    index++
                }
                inBlock -> {
                    if (ch == '*' && next == '/') {
                        out.append("  ")
                        inBlock = false
                        index += 2
                    } else {
                        out.append(if (ch == '\n') '\n' else ' ')
                        index++
                    }
                }
                inString -> {
                    out.append(ch)
                    if (ch == '\\' && next != null) {
                        out.append(next)
                        index += 2
                        continue
                    }
                    if (ch == '"') inString = false
                    index++
                }
                ch == '"' -> {
                    inString = true
                    out.append(ch)
                    index++
                }
                ch == '/' && next == '/' -> {
                    inLine = true
                    out.append("  ")
                    index += 2
                }
                ch == '/' && next == '*' -> {
                    inBlock = true
                    out.append("  ")
                    index += 2
                }
                else -> {
                    out.append(ch)
                    index++
                }
            }
        }
        return out.toString()
    }

    /** Les blocs d'arguments des appels `name(` : (index absolu du « ( » de l'appel, le texte du bloc). */
    private fun argumentBlocks(source: String, name: String): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        var from = 0
        while (true) {
            val start = source.indexOf(name, from)
            if (start < 0) break
            val open = start + name.length - 1 // la « ( » de l'appel
            var depth = 0
            var index = open
            var end = source.length - 1
            var inString = false
            while (index < source.length) {
                val ch = source[index]
                if (ch == '"' && source.getOrNull(index - 1) != '\\') inString = !inString
                if (!inString) {
                    if (ch == '(') depth++
                    if (ch == ')') {
                        depth--
                        if (depth == 0) {
                            end = index
                            break
                        }
                    }
                }
                index++
            }
            out += open to source.substring(open, minOf(end + 1, source.length))
            from = end + 1
        }
        return out
    }

    /**
     * **Ce que dit le dessin du logo** : aucune teinte de thème ni d'accent ne doit s'y trouver.
     * Chaque écart est rendu avec `fichier:ligne`.
     *
     * @param wholeFile `true` : le contrôle porte sur **toute la source** (le dessin de la marque) ;
     *        `false` : sur **les arguments de chaque appel** `OnyxBrandMark( … )` (l'endroit d'identité).
     */
    private fun brandTintViolations(relativePath: String, source: String, wholeFile: Boolean): List<String> {
        val masked = maskComments(source)
        val out = mutableListOf<String>()
        val blocks: List<Pair<Int, String>> = if (wholeFile) {
            listOf(0 to masked)
        } else {
            argumentBlocks(masked, "OnyxBrandMark(")
        }
        if (blocks.isEmpty() && !wholeFile) {
            out += "$relativePath:${lineOf(source, "OnyxBrandMark(")} : aucun appel « OnyxBrandMark( » — " +
                "l'application ne porte pas le logo de la maison"
            return out
        }
        blocks.forEach { (absoluteStart, block) ->
            themeTints.forEach { tint ->
                var from = 0
                while (true) {
                    val index = block.indexOf(tint, from)
                    if (index < 0) break
                    val line = lineAt(source, absoluteStart + index)
                    out += "$relativePath:$line : une TEINTE (« $tint ») est entrée dans le dessin du " +
                        "logo — la marque ne se dérive pas du thème (voir OnyxBrand)"
                    from = index + tint.length
                }
            }
        }
        return out
    }

    /** Les fichiers qui portent la marque et que le verrou de teinte surveille. */
    private val marqueFiles = listOf(
        "ui/sidebar/ChatSidebar.kt",
        "ui/settings/GatewayCard.kt",
    )

    @Test
    fun `le logo ne porte aucune teinte de theme ni d accent`() {
        val faults = mutableListOf<String>()
        faults += brandTintViolations("core/src/main/$brandPath", brandSource(), wholeFile = true)
        marqueFiles.forEach { path ->
            faults += brandTintViolations(path, appSource(path), wholeFile = false)
        }
        println("Contrôle de teinte : " + faults.size + " manquement(s)")
        assertTrue(
            "une teinte de thème ou d'accent est entrée dans le logo :\n" + faults.joinToString("\n"),
            faults.isEmpty(),
        )
    }

    // =============================================================================================
    // 4 · Les endroits d'identité
    // =============================================================================================

    /**
     * Un endroit où l'application affirme son identité : ce qu'il est, la **marque** qu'il doit
     * porter, et la ligne où elle doit se trouver (l'ancre, quand elle manque).
     */
    private data class IdentityPlace(
        val path: String,
        val logo: String,
        val anchor: String,
        val what: String,
    )

    private val places = listOf(
        IdentityPlace(
            "ui/sidebar/ChatSidebar.kt",
            "OnyxBrandMark(",
            "private fun SidebarHeader(",
            "l'en-tête du panneau des discussions (le nom et l'adresse de la passerelle)",
        ),
        IdentityPlace(
            "ui/settings/GatewayCard.kt",
            "OnyxBrandMark(",
            "fun GatewayCard(",
            "la carte du serveur en tête des Réglages",
        ),
        IdentityPlace(
            "ui/setup/SetupScreen.kt",
            "R.mipmap.ic_launcher_graphite",
            "OnyxMarkSize",
            "l'écran d'accueil du premier lancement",
        ),
    )

    /**
     * **Les endroits dont le rond portait une INITIALE** : la première lettre du nom du serveur en
     * guise de logo. Aucun ne doit plus en porter.
     */
    private val noInitial = listOf(
        "ui/sidebar/ChatSidebar.kt",
        "ui/settings/GatewayCard.kt",
        "ui/setup/SetupScreen.kt",
        "ui/OnyxApp.kt",
    )

    /** Les formes d'écriture d'une **initiale mise à la place d'un logo**. */
    private val initialForms = listOf(
        "firstOrNull()?.uppercase",
        "firstOrNull()?.uppercaseChar()",
        "userInitial",
    )

    private fun identityViolations(relativePath: String, source: String, logo: String, anchor: String): List<String> {
        val out = mutableListOf<String>()
        if (!source.contains(logo)) {
            out += "$relativePath:${lineOf(source, anchor)} : « $anchor » n'affiche pas le logo de la " +
                "maison (« $logo » attendu) — l'application s'y représente avec autre chose que sa marque"
        }
        initialForms.forEach { form ->
            val index = source.indexOf(form)
            if (index >= 0) {
                val line = source.take(index).count { it == '\n' } + 1
                out += "$relativePath:$line : une INITIALE est affichée là où le logo doit être " +
                    "(« $form »)"
            }
        }
        return out
    }

    @Test
    fun `les endroits d identite portent la marque, jamais une initiale`() {
        val found = mutableListOf<String>()
        val report = StringBuilder()
        places.forEach { place ->
            val source = appSource(place.path)
            val violations = identityViolations(place.path, source, place.logo, place.anchor)
            report.append(
                "  " + place.what + " -> " + place.path +
                    (if (violations.isEmpty()) "  ok" else "  " + violations.size + " manquement(s)") + "\n",
            )
            found += violations
        }
        noInitial.forEach { path ->
            val source = appSource(path)
            initialForms.forEach { form ->
                val index = source.indexOf(form)
                if (index >= 0) {
                    val line = source.take(index).count { it == '\n' } + 1
                    found += "$path:$line : une INITIALE est affichée là où le logo doit être (« $form »)"
                }
            }
        }
        println("Endroits d'identité :\n" + report)
        assertTrue(
            "des endroits d'identité ne portent pas la marque de la maison :\n" + found.joinToString("\n"),
            found.isEmpty(),
        )
    }

    // =============================================================================================
    // 5 · La morsure
    // =============================================================================================

    @Test
    fun `le verrou mord sur la rune refermee de l ancien glyphe`() {
        val path = "res/drawable/ic_launcher_foreground.xml"
        val vrai = coreSource(path)
        val fautive = vrai
            .replace(referencePath, "M39.75,75.81L68.25,47.89L54.00,32.20L39.75,47.89L68.25,75.81Z")
            .replace("android:strokeWidth=\"$referenceStroke\"", "android:strokeWidth=\"8.39\"")
        val violations = runeViolations("core/src/main/$path", fautive)
        println("Morsure (rune refermée) :\n" + violations.joinToString("\n"))
        assertTrue("le verrou n'a pas mordu sur le tracé refermé", violations.size >= 2)
        assertTrue(
            "le message doit nommer la ligne du tracé fautif",
            violations.any { it.contains("ic_launcher_foreground.xml:") && it.contains("tracé") },
        )
        assertTrue(
            "le message doit dire que le tracé est refermé",
            violations.any { it.contains("REFERMÉ") },
        )
    }

    @Test
    fun `le verrou mord sur une initiale mise a la place du logo`() {
        val path = "ui/settings/GatewayCard.kt"
        val vraie = appSource(path)
        val fautive = vraie.replace(
            "    val statusColor =",
            "    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: \"?\"\n" +
                "    val statusColor =",
        )
        val violations = identityViolations(path, fautive, "OnyxBrandMark(", "fun GatewayCard(")
        println("Morsure (initiale) :\n" + violations.joinToString("\n"))
        assertTrue("le verrou n'a pas mordu sur l'initiale", violations.isNotEmpty())
        assertTrue(
            "le message doit nommer la ligne de l'initiale",
            violations.any { it.contains("GatewayCard.kt:") && it.contains("INITIALE") },
        )

        val sansLogo = vraie.replace("OnyxBrandMark(", "Box(")
        val manque = identityViolations(path, sansLogo, "OnyxBrandMark(", "fun GatewayCard(")
        println("Morsure (logo absent) :\n" + manque.joinToString("\n"))
        assertTrue(
            "le verrou n'a pas mordu sur l'absence de logo",
            manque.any { it.contains("GatewayCard.kt:") && it.contains("n'affiche pas le logo") },
        )
    }

    /**
     * **La morsure du verrou retourné — « remets le bleu → rouge ».** On remet la teinte d'accent
     * dans le dessin du logo (une rune bleue, l'ancien défaut), une fois dans la **source de la
     * marque**, une fois dans un **appel d'endroit d'identité** : les deux doivent faire crier le
     * verrou, **en nommant le fichier et la ligne**.
     */
    @Test
    fun `le verrou mord sur une teinte d accent remise dans le logo`() {
        // 1. La teinte entre dans le DESSIN de la marque : la rune reprend l'accent du thème.
        val vraie = brandSource()
        val fautive = vraie.replace(
            "        val runeHeight = 2f * radius * OnyxRune.HEIGHT_FRACTION",
            "        val runeHeight = 2f * radius * OnyxRune.HEIGHT_FRACTION\n" +
                "        val ink = LocalOnyxTheme.current.accent",
        )
        assertTrue("la source fautive doit vraiment porter la teinte", fautive != vraie)
        val dessin = brandTintViolations("core/src/main/$brandPath", fautive, wholeFile = true)
        println("Morsure (teinte dans le dessin de la marque) :\n" + dessin.joinToString("\n"))
        assertTrue("le verrou n'a pas mordu sur la teinte du dessin", dessin.isNotEmpty())
        assertTrue(
            "le message doit nommer OnyxBrand.kt et sa ligne",
            dessin.any { it.contains("OnyxBrand.kt:") && it.contains("TEINTE") },
        )

        // 2. La teinte entre par un APPEL d'endroit d'identité : `tint = theme.accent`.
        val cardPath = "ui/settings/GatewayCard.kt"
        val vraieCarte = appSource(cardPath)
        val carte = vraieCarte.replace(
            "            OnyxBrandMark(\n                size = OnyxSocle.ThumbnailSize,\n",
            "            OnyxBrandMark(\n                tint = theme.accent,\n                size = OnyxSocle.ThumbnailSize,\n",
        )
        assertTrue("la carte fautive doit vraiment porter la teinte", carte != vraieCarte)
        val appel = brandTintViolations(cardPath, carte, wholeFile = false)
        println("Morsure (teinte dans l'appel) :\n" + appel.joinToString("\n"))
        assertTrue("le verrou n'a pas mordu sur la teinte de l'appel", appel.isNotEmpty())
        assertTrue(
            "le message doit nommer GatewayCard.kt et sa ligne",
            appel.any { it.contains("GatewayCard.kt:") && it.contains("TEINTE") },
        )
    }
}
