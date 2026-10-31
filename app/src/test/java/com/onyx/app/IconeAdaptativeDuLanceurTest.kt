package com.onyx.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * **Le verrou de l'icône adaptative du lanceur.**
 *
 * Le propriétaire a vu, sur son téléphone, le carré sombre **réduit et posé sur une plaque
 * blanche circulaire** : c'est ce que fait un lanceur récent quand l'application n'a **pas**
 * d'icône adaptative — il compose autour d'une bitmap. Un commit antérieur avait justement
 * **retiré** l'icône adaptative au profit de bitmaps par densité.
 *
 * Ce test échoue si :
 *
 * 1. **l'icône adaptative disparaît** — plus de `mipmap-anydpi-v26/ic_launcher.xml`, ou son
 *    `<background>`/`<foreground>` absent, ou la variante ronde (`ic_launcher_round.xml`) ou la
 *    ressource de l'alias de lanceur actif (`ic_launcher_graphite_block.xml`) manquante ;
 * 2. **la couche monochrome manque** — plus de `<monochrome>` déclaré, ou plus le fichier de la
 *    silhouette de la rune (`drawable/ic_launcher_adaptive_monochrome.xml`) ;
 * 3. **le premier plan sort de la zone de sécurité** — un chemin du
 *    `drawable/ic_launcher_adaptive_foreground.xml` (épaisseur de trait comprise) dépasse les
 *    deux tiers centraux du canevas de 108 unités (`[18, 90]`) ou le cercle garanti de rayon 33 ;
 * 4. **le repli des appareils anciens est sacrifié** — une densité de bitmap manquante ;
 * 5. **le fond n'est plus la couleur du carré sombre de la référence** (`#1A202A`).
 *
 * Chaque message d'échec **nomme le fichier** fautif.
 */
class IconeAdaptativeDuLanceurTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    /** Le dossier de ressources du module `:app` (répertoire de travail des tests unitaires). */
    private val res = File("src/main/res")

    /** L'icône adaptative de l'alias de lanceur actif : c'est elle que le tiroir lit. */
    private val aliasResource = "ic_launcher_graphite_block"

    /** Le canevas de l'icône adaptative, en unités du premier plan. */
    private val canvas = 108.0

    /** Les deux tiers centraux : la zone de sécurité (contenu utile garanti visible) — [18, 90]. */
    private val safeFrom = canvas / 6.0            // 18
    private val safeTo = canvas * 5.0 / 6.0        // 90

    /** Le cercle garanti de l'icône adaptative : rayon 33 (66 sur 108), jamais atteint par le masque. */
    private val guaranteedRadius = 33.0

    // --- Outils ----------------------------------------------------------------------------

    private fun elements(path: String, tag: String): List<Element> {
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(path))
        val nodes = document.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun adaptiveIconFile(name: String): File = File(res, "mipmap-anydpi-v26/$name.xml")

    // --- 1. L'icône adaptative existe, avec ses trois couches --------------------------------

    @Test
    fun `l'icone adaptative du lanceur existe et porte fond, premier plan et monochrome`() {
        listOf("ic_launcher", "ic_launcher_round", aliasResource, "${aliasResource}_round")
            .forEach { name ->
                val file = adaptiveIconFile(name)
                assertTrue(
                    "${file.path} est absent : il faut une icône adaptative (fond + premier plan " +
                        "+ monochrome) sur Android 8.0 et plus, sinon le lanceur réduit la bitmap " +
                        "sur une plaque blanche",
                    file.isFile,
                )

                val root = elements(file.path, "adaptive-icon")
                assertEquals("${file.path} : <adaptive-icon> attendu une fois", 1, root.size)

                val background = elements(file.path, "background")
                val foreground = elements(file.path, "foreground")
                val monochrome = elements(file.path, "monochrome")
                assertEquals("${file.path} : une seule couche <background> attendue", 1, background.size)
                assertEquals("${file.path} : une seule couche <foreground> attendue", 1, foreground.size)
                assertEquals(
                    "${file.path} : <monochrome> manquant — l'icône thémée d'Android 13+ est " +
                        "impossible sans la silhouette",
                    1,
                    monochrome.size,
                )

                assertEquals(
                    "${file.path} : le fond n'est plus la couleur du carré sombre de la référence",
                    "@color/ic_launcher_background",
                    background.single().getAttributeNS(androidNs, "drawable"),
                )
                assertEquals(
                    "${file.path} : le premier plan n'est plus la couche du cercle et de la rune",
                    "@drawable/ic_launcher_adaptive_foreground",
                    foreground.single().getAttributeNS(androidNs, "drawable"),
                )
                assertEquals(
                    "${file.path} : la couche monochrome n'est plus la silhouette de la rune",
                    "@drawable/ic_launcher_adaptive_monochrome",
                    monochrome.single().getAttributeNS(androidNs, "drawable"),
                )
            }
    }

    @Test
    fun `le fond est la couleur pleine du carre sombre de la reference`() {
        val path = "src/main/res/values/colors.xml"
        val colors = elements(path, "color").associate {
            it.getAttribute("name") to it.textContent.trim().uppercase()
        }
        assertEquals(
            "$path : la couleur du fond de l'icône adaptative a changé (mesure de la référence : " +
                "luminance 31)",
            "#FF1A202A",
            colors["ic_launcher_background"],
        )
    }

    @Test
    fun `les bitmaps de repli restent, aux cinq densites`() {
        listOf("ic_launcher", "ic_launcher_round", aliasResource, "${aliasResource}_round")
            .forEach { name ->
                listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi").forEach { density ->
                    val file = File(res, "mipmap-$density/$name.png")
                    assertTrue(
                        "${file.path} est absent : les bitmaps de repli des appareils anciens ne " +
                            "doivent pas être supprimées",
                        file.isFile,
                    )
                }
            }
    }

    // --- 2. Le monochrome, et 3. la zone de sécurité du premier plan -------------------------

    @Test
    fun `la couche monochrome porte la silhouette de la rune seule`() {
        val file = File(res, "drawable/ic_launcher_adaptive_monochrome.xml")
        assertTrue("${file.path} est absent : plus de couche monochrome (icône thémée)", file.isFile)
        val paths = elements(file.path, "path")
        assertTrue("${file.path} : aucun <path> — la silhouette de la rune est vide", paths.isNotEmpty())
        assertTrue(
            "${file.path} : la silhouette n'est qu'un tracé transparent — la rune n'est plus dessinée",
            paths.any { alphaOf(it.getAttributeNS(androidNs, "strokeColor")) > 0 },
        )
        // La silhouette est exactement la rune du premier plan, à la couleur près.
        val foregroundRune = runePath(preamble = File(res, "drawable/ic_launcher_adaptive_foreground.xml"))
        val monochromeRune = runePath(preamble = file)
        assertEquals(
            "${file.path} : la silhouette monochrome n'est plus la rune du premier plan",
            foregroundRune,
            monochromeRune,
        )
    }

    @Test
    fun `le premier plan tient dans la zone de securite de l'icone adaptative`() {
        val file = File(res, "drawable/ic_launcher_adaptive_foreground.xml")
        assertTrue(
            "${file.path} est absent : plus de premier plan (le cercle et la rune)",
            file.isFile,
        )

        val paths = elements(file.path, "path")
        assertTrue("${file.path} : aucun <path> — le premier plan est vide", paths.isNotEmpty())

        paths.forEachIndexed { index, path ->
            val pathData = path.getAttributeNS(androidNs, "pathData")
            val stroke = path.getAttributeNS(androidNs, "strokeWidth").toDoubleOrNull() ?: 0.0
            val half = stroke / 2.0
            val geometry = PathGeometry.parse(pathData)

            // a) aucun point (bouts, coins) hors des deux tiers centraux.
            geometry.points.forEach { p ->
                assertTrue(
                    "${file.path} (chemin ${index + 1}) : le point ($p) sort des deux tiers " +
                        "centraux [$safeFrom, $safeTo] — le contenu toucherait le masque",
                    p.x >= safeFrom - 1e-6 && p.x <= safeTo + 1e-6 &&
                        p.y >= safeFrom - 1e-6 && p.y <= safeTo + 1e-6,
                )
            }

            // b) aucun point, épaisseur de trait comprise, hors du cercle garanti de rayon 33.
            val centre = PathGeometry.CANVAS_CENTER
            geometry.points.forEach { p ->
                val reach = hypot(p.x - centre.x, p.y - centre.y) + half
                assertTrue(
                    "${file.path} (chemin ${index + 1}) : le point ($p) atteint $reach unités du " +
                        "centre, au-delà du cercle garanti de $guaranteedRadius — le contenu " +
                        "dépasserait la zone de sécurité",
                    reach <= guaranteedRadius + 1e-6,
                )
            }

            // c) les arcs : leur rayon compte aussi (un disque déborde par son rayon, pas par
            //    ses seuls bouts).
            geometry.arcs.forEach { arc ->
                val reach = hypot(arc.center.x - centre.x, arc.center.y - centre.y) + arc.radius + half
                assertTrue(
                    "${file.path} (chemin ${index + 1}) : un arc de rayon ${arc.radius} autour de " +
                        "${arc.center} atteint $reach unités du centre, au-delà du cercle garanti " +
                        "de $guaranteedRadius",
                    reach <= guaranteedRadius + 1e-6,
                )
                assertTrue(
                    "${file.path} (chemin ${index + 1}) : un arc de rayon ${arc.radius} sort de la " +
                        "zone de sécurité",
                    arc.center.x + arc.radius + half <= safeTo + 1e-6 &&
                        arc.center.x - arc.radius - half >= safeFrom - 1e-6,
                )
            }
        }
    }

    // --- Outils de géométrie ----------------------------------------------------------------

    /** La rune : le chemin qui n'est pas un cercle (le trait plein du signe). */
    private fun runePath(preamble: File): String =
        elements(preamble.path, "path")
            .map { it.getAttributeNS(androidNs, "pathData").trim() }
            .firstOrNull { !it.contains("A") }
            ?: error("${preamble.path} : aucune rune (chemin sans arc) trouvée")

    /** L'alpha d'une couleur `#AARRGGBB` ou `#RRGGBB` (0 si illisible ou transparente). */
    private fun alphaOf(color: String): Int {
        val hex = color.removePrefix("#")
        return when (hex.length) {
            8 -> hex.substring(0, 2).toIntOrNull(16) ?: 0
            6, 3 -> 255
            else -> 0
        }
    }

    private data class Arc(val center: PathPoint, val radius: Double)

    private data class PathPoint(val x: Double, val y: Double) {
        override fun toString(): String = "%.2f,%.2f".format(x, y)
    }

    /**
     * Le strict nécessaire pour lire un `pathData` **absolu** de vectoriel : `M`, `L`, `A`, `Z`.
     * Un arc est réduit à son bout, plus le centre (moyenne des bouts d'arcs du chemin) et son
     * rayon — assez pour mesurer la portée réelle d'un disque.
     */
    private class PathGeometry(
        val points: List<PathPoint>,
        val arcs: List<Arc>,
    ) {
        companion object {
            val CANVAS_CENTER = PathPoint(54.0, 54.0)

            private val token = Regex("[A-Za-z]|-?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?")

            fun parse(pathData: String): PathGeometry {
                val tokens = token.findAll(pathData).map { it.value }.toList()
                val points = mutableListOf<PathPoint>()
                val endpoints = mutableListOf<PathPoint>()
                val radii = mutableListOf<Double>()
                var i = 0
                var command = ' '
                while (i < tokens.size) {
                    if (tokens[i].length == 1 && tokens[i][0].isLetter()) {
                        command = tokens[i][0]
                        i++
                        if (command == 'Z' || command == 'z') continue
                    }
                    val arity = when (command) {
                        'M', 'L' -> 2
                        'A' -> 7
                        // Toute autre commande (relative, courbe, H/V) doit échouer franchement :
                        // un point mal interprété laisserait passer un débordement réel.
                        else -> error(
                            "commande de chemin non gérée « $command » dans « $pathData » : ce test " +
                                "ne mesure que les chemins absolus M/L/A/Z — le mettre à jour",
                        )
                    }
                    val args = (0 until arity).map { tokens[i + it].toDouble() }
                    i += arity
                    when (command) {
                        'M', 'L' -> points += PathPoint(args[0], args[1])
                        'A' -> {
                            endpoints += PathPoint(args[5], args[6])
                            radii += maxOf(abs(args[0]), abs(args[1]))
                        }
                    }
                }
                require(endpoints.isEmpty() == radii.isEmpty())
                val arcs = if (endpoints.isEmpty()) {
                    emptyList()
                } else {
                    val cx = endpoints.map { it.x }.average()
                    val cy = endpoints.map { it.y }.average()
                    radii.map { Arc(PathPoint(cx, cy), it) }
                }
                return PathGeometry(points + endpoints, arcs)
            }
        }
    }
}
