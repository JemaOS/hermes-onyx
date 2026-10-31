package com.onyx.app.ui.design

import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le verrou du **jeu d'icônes des menus et des pièces jointes** : une seule épaisseur de trait (2),
 * une seule grille (24), l'encre dans la zone et centrée, et les quatre dessins du menu de pièce
 * jointe nommables à 20 px.
 *
 * Le défaut d'origine, relevé dans les vectoriels : les graisses différaient d'une icône à l'autre
 * (l'éclair et la lecture à 1,8, l'histogramme à 2,6, la pause à 2,4, le clavier à 1,7), les
 * tailles optiques aussi (les « deux feuilles » montaient à 1 et descendaient à 23 là où le dossier
 * tenait dans 4 à 20), l'encre de la photothèque sortait à 1 à gauche, le trombone vivait hors
 * grille (encre de 0,25 à 18,8, décalée de 2,5) et le dossier portait un ergot — une languette de
 * 2,2 de profondeur fermée par un coin vif.
 *
 * Le trombone, lui, était **plein** : le seul aplat de la feuille d'ajout, à côté d'une image, d'un
 * appareil photo et d'un presse-papiers au trait. Il est redessiné au trait (2, bouts et jointures
 * ronds) sur la forme de la référence — un fil continu, sa grande boucle et sa petite — et il est
 * donc passé du groupe des pleines à celui des traits, avec la même encre dans 2 à 22 et le même
 * centre que les trois autres.
 */
class MenuIconFamilyGeometryTest {

    /** Les icônes au trait : la règle est un trait unique de 2. */
    private val family = listOf(
        "ic_sym_folder", "ic_sym_folder_badge_plus", "ic_sym_photo_on_rectangle", "ic_sym_photo",
        "ic_sym_camera", "ic_sym_doc_on_doc", "ic_sym_doc_on_clipboard",
        "ic_sym_arrow_clockwise", "ic_sym_arrow_counterclockwise", "ic_sym_arrow_turn_down_right",
        "ic_sym_arrow_up_circle", "ic_sym_bell", "ic_sym_bell_slash", "ic_sym_chart_bar",
        "ic_sym_checkmark", "ic_sym_checkmark_message", "ic_sym_chevron_down", "ic_sym_chevron_up",
        "ic_sym_desktopcomputer", "ic_sym_eye_slash", "ic_sym_fullscreen_enter",
        "ic_sym_fullscreen_exit", "ic_sym_gearshape_2", "ic_sym_hand_raised", "ic_sym_keyboard",
        "ic_sym_magnifyingglass", "ic_sym_message_badge", "ic_sym_pause", "ic_sym_pencil",
        "ic_sym_play", "ic_sym_safari", "ic_sym_share", "ic_sym_square_and_pencil",
        "ic_sym_square_stack_3d_up", "ic_sym_terminal", "ic_sym_trash", "ic_sym_xmark",
        "ic_sym_bolt",
        // Le contrôle du volet : il vivait hors du jeu (colonne pleine, cadre à 1,7), et se lisait
        // comme un rectangle avec une barre de couleur. Il est rentré dans la famille.
        "ic_sym_sidebar_left",
        // le haut-parleur : un cone plein et deux ondes au trait
        "ic_sym_speaker_wave_2",
        "ic_sym_phone",
        // Le trombone : redessine au trait pour la feuille d'ajout (voir la note de la classe).
        "ic_sym_paperclip",
    )

    /** Les variantes pleines : elles n'ont pas de trait, mais la même grille et le même centre. */
    private val solid = listOf(
        "ic_sym_folder_fill", "ic_sym_folder_badge_plus_fill", "ic_sym_key_fill", "ic_sym_person_2_fill",
        "ic_sym_pin_fill", "ic_sym_ellipsis", "ic_sym_stop_fill",
        // Le combiné peint, jumeau du contour « ic_sym_phone » : même silhouette, même bord
        // extérieur (la poussée d'un demi-trait est dans le vectoriel, `PhoneGlyphGeometryTest`).
        "ic_sym_phone_fill",
    )

    /**
     * Les **quatre** de la feuille d'ajout, dans l'ordre où elle les montre : la photothèque
     * (l'image), l'appareil photo, les fichiers (le trombone) et le collage (le presse-papiers).
     * La règle du bord est plus stricte (encre dans 2 à 22).
     */
    private val attach = listOf(
        "ic_sym_photo_on_rectangle", "ic_sym_camera", "ic_sym_paperclip", "ic_sym_doc_on_clipboard",
    )

    private fun path(name: String): String = listOf(
        "src/main/res/drawable/$name.xml",
        "../core/src/main/res/drawable/$name.xml",
        "core/src/main/res/drawable/$name.xml",
    ).first { File(it).isFile }

    private fun read(name: String): String = File(path(name)).readText()

    private fun drawing(name: String): Drawings.Drawing = Drawings.of(read(name))

    private fun ink(name: String, element: Int? = null): DoubleArray =
        Drawings.ink(drawing(name), element)

    private val all: List<String> get() = family + solid

    // ---- 1. la grille -------------------------------------------------------------------------

    @Test
    fun `toute la famille affiche sur une grille de 24`() {
        assertTrue("la famille est vide", all.size >= 40)
        all.forEach { name ->
            assertEquals("$name : largeur", 24.0, Drawings.viewport(read(name), "width"), 0.001)
            assertEquals("$name : hauteur", 24.0, Drawings.viewport(read(name), "height"), 0.001)
            assertEquals("$name : vue", 24.0, Drawings.viewport(read(name), "viewportWidth"), 0.001)
            assertEquals("$name : vue", 24.0, Drawings.viewport(read(name), "viewportHeight"), 0.001)
        }
    }

    // ---- 2. une seule graisse, de 2 ------------------------------------------------------------

    @Test
    fun `tout trait de la famille fait exactement deux`() {
        family.forEach { name ->
            val el = drawing(name).elements
            assertTrue("$name : aucun trait", el.any { it.stroke != null })
            val widths = el.mapNotNull { it.stroke }.distinct()
            assertEquals("$name : une seule épaisseur, $widths", 1, widths.size)
            assertEquals("$name : l'épaisseur", listOf(2.0), widths)
        }
    }

    @Test
    fun `les variantes pleines n ont pas de trait`() {
        solid.forEach { name ->
            assertTrue("$name : porte un trait", drawing(name).elements.all { it.stroke == null })
            assertTrue("$name : rien de peint", drawing(name).elements.any { it.fill != null })
        }
    }

    @Test
    fun `les bouts et les jointures sont arrondis`() {
        family.forEach { name ->
            drawing(name).elements.filter { it.stroke != null }.forEachIndexed { i, el ->
                assertEquals("$name : bouts du tracé $i", "round", el.cap)
                if (el.turns) assertEquals("$name : jointures du tracé $i", "round", el.join)
            }
        }
    }

    // ---- 3. l'encre dans la zone, et centrée ---------------------------------------------------

    @Test
    fun `l encre de chaque icone tient dans la zone et le centre`() {
        all.forEach { name ->
            val (x0, y0, x1, y1) = ink(name).toList()
            assertTrue("$name : encre à gauche ($x0)", x0 >= 1.5)
            assertTrue("$name : encre en haut ($y0)", y0 >= 1.5)
            assertTrue("$name : encre à droite ($x1)", x1 <= 22.5)
            assertTrue("$name : encre en bas ($y1)", y1 <= 22.5)
            // Le triangle de lecture est le seul dessin dont l'encre est franchement décalée :
            // un triangle qui pointe à droite est centré sur son poids, pas sur sa boîte.
            val slack = if (name == "ic_sym_play") 1.05 else 0.6
            assertEquals("$name : centre d'encre x", 12.0, (x0 + x1) / 2, slack)
            assertEquals("$name : centre d'encre y", 12.0, (y0 + y1) / 2, slack)
        }
    }

    @Test
    fun `les quatre du menu de piece jointe tiennent dans deux a vingt-deux`() {
        attach.forEach { name ->
            val (x0, y0, x1, y1) = ink(name).toList()
            // Les bornes sont tenues au centième près : l'aplatissement des arcs laisse un
            // milliardième sous 2.
            assertTrue("$name : bord gauche ($x0)", x0 >= 1.99)
            assertTrue("$name : bord haut ($y0)", y0 >= 1.99)
            assertTrue("$name : bord droit ($x1)", x1 <= 22.01)
            assertTrue("$name : bord bas ($y1)", y1 <= 22.01)
            assertEquals("$name : centre x", 12.0, (x0 + x1) / 2, 0.02)
            assertEquals("$name : centre y", 12.0, (y0 + y1) / 2, 0.02)
            // Aucun tracé ne sort de la zone, même pris seul : une corne oubliée se voit.
            drawing(name).elements.indices.forEach { i ->
                val e = ink(name, i)
                assertTrue("$name : tracé $i hors zone (${e.toList()})",
                    e[0] >= 1.99 && e[1] >= 1.99 && e[2] <= 22.01 && e[3] <= 22.01)
            }
        }
    }

    @Test
    fun `les trois dessins larges partagent la meme boite`() {
        listOf("ic_sym_folder", "ic_sym_photo_on_rectangle", "ic_sym_camera").forEach { name ->
            val (x0, y0, x1, y1) = ink(name).toList()
            assertEquals("$name : largeur", 20.0, x1 - x0, 0.02)
            assertEquals("$name : hauteur", 16.0, y1 - y0, 0.02)
        }
        // Le document a ses proportions : plus étroit, plus haut, même centre.
        val d = ink("ic_sym_doc_on_doc").toList()
        assertEquals("le document : largeur", 16.0, d[2] - d[0], 0.02)
        assertEquals("le document : hauteur", 18.0, d[3] - d[1], 0.02)
    }

    // ---- 4. le dossier n'a plus d'ergot --------------------------------------------------------

    @Test
    fun `le dossier a une languette franche et une marche arrondie`() {
        val xml = read("ic_sym_folder")
        assertTrue("le tracé part de la languette", xml.contains("M4.4,5 H9.9 A1.1,1.1"))
        assertTrue("la marche est arrondie", xml.contains("V6.7 A1.1,1.1 0 0 1 12.1,7.8"))
        assertTrue("le coin de la languette est serré", xml.contains("V6.4 A1.4,1.4 0 0 1 4.4,5"))
        // La profondeur de la languette : le dessus de la languette au-dessus du dessus du corps.
        val pts = Drawings.flatten(drawing("ic_sym_folder").elements[0].data)
        val tabTop = pts.minOf { it.second }
        val bodyTop = pts.filter { it.first > 14.0 }.minOf { it.second }
        assertTrue("profondeur de la languette : ${bodyTop - tabTop}", bodyTop - tabTop >= 2.6)
        // L'ancien tracé — languette courte fermée par un coin vif — n'est plus là.
        listOf("M5.2,5 H10.5 V7.2", "M5.2,4 H10.5 A1,1 0 0 1 11.5,5 V6.2", "M12.8,10.7 V15.5",
            "M10.4,13.1 H15.2", "A2.2,2.2 0 0 1 21,9.4", "H10.5 V7.2").forEach {
            assertFalse("le dossier porte encore « $it »", xml.contains(it))
        }
    }

    // ---- 5. l'appareil photo est symétrique ----------------------------------------------------

    @Test
    fun `la bosse du viseur de l appareil photo est au milieu`() {
        val pts = Drawings.flatten(drawing("ic_sym_camera").elements[0].data)
        assertEquals("le dessus de la bosse", 5.0, pts.minOf { it.second }, 0.02)
        // Les deux épaules, à la hauteur où la bosse rejoint le dessus du corps (6) : la bosse
        // est un trapèze aux angles arrondis, large de 7,2 et centrée.
        val shoulders = pts.filter { it.second in 5.9..6.1 }.map { it.first }
        val left = shoulders.min()
        val right = shoulders.max()
        assertEquals("bosse centrée", 12.0, (left + right) / 2, 0.05)
        assertEquals("largeur de la bosse", 7.2, right - left, 0.06)
        val lens = ink("ic_sym_camera", 1).toList()
        assertEquals("objectif centré", 12.0, (lens[0] + lens[2]) / 2, 0.02)
        assertTrue("objectif lisible : ${lens[0]} à ${lens[2]}", lens[2] - lens[0] >= 7.5)
    }

    // ---- 6. plus de trait parasite -------------------------------------------------------------

    @Test
    fun `la feuille de derriere de la phototheque s arrete au cadre`() {
        val d = drawing("ic_sym_photo_on_rectangle")
        assertEquals("le cadre, le soleil, la montagne et la feuille", 4, d.elements.size)
        // La feuille de derrière est un berceau ouvert : aucun de ses points ne tombe dans
        // l'intérieur du cadre, où il se lirait comme un trait parasite.
        Drawings.flatten(d.elements[3].data).forEach { (x, y) ->
            assertFalse("trait parasite dans l'image ($x ; $y)",
                x > 4.1 && x < 15.9 && y > 6.1 && y < 15.9)
        }
        // Et l'ancienne feuille, qui s'arrêtait au milieu de l'image, n'est plus là.
        listOf("M18,10 h2 a1,1 0 0 1 1,1 v6 a2,2 0 0 1 -2,2 H8", "M3.6,16 L8,11.6 L11,14.6")
            .forEach { assertFalse("la photothèque porte encore « $it »", read("ic_sym_photo_on_rectangle").contains(it)) }
    }

    @Test
    fun `coller garde deux feuilles entieres et ne croise rien`() {
        val d = drawing("ic_sym_doc_on_doc")
        val datas = d.elements.map { it.data }
        assertEquals("la feuille de devant, son coin, la feuille de derrière", 3, datas.size)
        assertEquals("M8,7 H15 L19,11 V20 H8 Z", datas[0])
        assertEquals("M15,7 V11 H19", datas[1])
        assertEquals("M16,8 L12,4 H5 V17 H8", datas[2])
        val ink = ink("ic_sym_doc_on_doc").toList()
        assertEquals("bord gauche", 4.0, ink[0], 0.02)
        assertEquals("bord haut", 3.0, ink[1], 0.02)
        // Aucun point de la feuille de derrière n'entre dans celle de devant.
        // Le tracé de la feuille de devant est (8 ; 7) - (19 ; 20) : son intérieur commence à un
        // trait de ses bords, et le coin coupé retranche le haut droit.
        Drawings.flatten(datas[2]).forEach { (x, y) ->
            val inside = x > 9.05 && x < 17.95 && y > 8.05 && y < 18.95 && x + y < 21.0
            assertFalse("la feuille de derrière entre dans celle de devant ($x ; $y)", inside)
        }
        listOf("M9,7 h7 l4,4 v11 H9 Z", "M9,17 H4 V2 H11 L15,6 V7", "M11,2 V6 H15").forEach {
            assertFalse("« coller » porte encore « $it »", read("ic_sym_doc_on_doc").contains(it))
        }
    }

    @Test
    fun `le presse-papiers se distingue d un rectangle`() {
        val d = drawing("ic_sym_doc_on_clipboard")
        assertEquals("la planche et ses trois lignes", 2, d.elements.size)
        val body = Drawings.flatten(d.elements[0].data)
        assertTrue("la pince dépasse le corps", body.minOf { it.second } <= 4.0)
        assertTrue("le corps descend", body.maxOf { it.second } >= 20.0)
        assertTrue("la pince est plus étroite que le corps", d.elements[0].data.contains("A1.2,1.2"))
        assertTrue("les trois lignes", d.elements[1].data.contains("M9,10.5 H15 M9,13.5 H15 M9,16.5 H12.5"))
    }

    // ---- 7. le clavier garde ses touches séparées ----------------------------------------------

    @Test
    fun `les touches du clavier restent separees au trait de deux`() {
        val d = drawing("ic_sym_keyboard")
        val keys = d.elements[1].data.split("M").filter { it.isNotBlank() }.map { "M$it" }
        assertTrue("six touches et la barre : ${keys.size}", keys.size >= 7)
        // Les trois touches de chaque rangée : l'écart d'encre entre deux voisines reste ouvert.
        listOf(0, 3).forEach { first ->
            val row = (first until first + 3).map { Drawings.inkData(keys[it], 2.0) }
            row.zipWithNext().forEach { (a, b) ->
                assertTrue("écart d'encre entre deux touches : ${b[0] - a[2]}", b[0] - a[2] >= 1.0)
            }
        }
        assertTrue("la barre d'espace", keys.last().trim() == "M8.5,15.5 H15.5")
    }

    // ---- 8. les graisses qui s'écartaient ------------------------------------------------------

    @Test
    fun `les icones qui s ecartaient du trait de deux sont rentrees dans le rang`() {
        mapOf(
            "ic_sym_chart_bar" to "2.6", "ic_sym_bolt" to "1.8", "ic_sym_play" to "1.8",
            "ic_sym_pause" to "2.4", "ic_sym_keyboard" to "1.7", "ic_sym_safari" to "1.8",
            "ic_sym_hand_raised" to "1.6",
        ).forEach { (name, ancien) ->
            assertFalse("$name porte encore un trait de $ancien", read(name).contains("strokeWidth=\"$ancien\""))
            assertEquals("$name : son trait", listOf(2.0),
                drawing(name).elements.mapNotNull { it.stroke }.distinct())
        }
    }

    @Test
    fun `le trombone est sorti du coin bas gauche et tient dans la zone`() {
        val ink = ink("ic_sym_paperclip").toList()
        assertTrue("bord gauche (${ink[0]})", ink[0] >= 2.0)
        assertTrue("bord haut (${ink[1]})", ink[1] >= 2.0)
        assertTrue("bord droit (${ink[2]})", ink[2] <= 22.0)
        assertTrue("bord bas (${ink[3]})", ink[3] <= 22.0)
        assertEquals("centré", 12.0, (ink[0] + ink[2]) / 2, 0.05)
        assertEquals("centré", 12.0, (ink[1] + ink[3]) / 2, 0.05)
    }

    /**
     * Le trombone est **au trait** comme les trois autres de la feuille d'ajout : un seul fil
     * continu, sa grande boucle et sa petite, bouts et jointures ronds. L'aplat d'avant — une
     * forme pleine de 16 x 15,5 — n'est plus là.
     */
    @Test
    fun `le trombone est un seul fil au trait de deux`() {
        val d = drawing("ic_sym_paperclip")
        assertEquals("un seul tracé", 1, d.elements.size)
        val fil = d.elements[0]
        assertEquals("son trait", 2.0, fil.stroke!!, 0.001)
        assertEquals("les bouts", "round", fil.cap)
        assertEquals("les jointures", "round", fil.join)
        assertTrue("aucun aplat", fil.fill == null || fil.fill == "@android:color/transparent")
        // Le fil tel qu'il est dessiné : quatre segments et deux boucles.
        assertEquals(
            "M4.04,12.04 L9.98,17.99 A5.94,5.94 0 0 0 18.38,9.6 L14,5.22 " +
                "A3.22,3.22 0 0 0 9.46,9.77 L13.31,13.62",
            fil.data,
        )
        // Et l'ancien trombone plein, dessiné au coin bas gauche, n'est plus dans le fichier.
        assertFalse(
            "le trombone porte encore son aplat",
            read("ic_sym_paperclip").contains("M10.771,18.577"),
        )
    }

    @Test
    fun `la famille des menus ne se limite pas aux quatre pieces jointes`() {
        // Le balayage : menu d'un message, menu d'un fichier, barre d'action d'une réponse.
        listOf(
            "ic_sym_doc_on_doc", "ic_sym_doc_on_clipboard", "ic_sym_pencil", "ic_sym_trash",
            "ic_sym_share", "ic_sym_chart_bar", "ic_sym_desktopcomputer", "ic_sym_terminal",
            "ic_sym_keyboard", "ic_sym_hand_raised", "ic_sym_bolt", "ic_sym_play", "ic_sym_pause",
            "ic_sym_safari", "ic_sym_speaker_wave_2", "ic_sym_stop_fill", "ic_sym_ellipsis",
        ).forEach { assertTrue("$it manquant", File(path(it)).isFile) }
    }
}

/** Le dessin d'un vectoriel : ses tracés, lus attribut par attribut. */
internal object Drawings {

    class El(val data: String, val stroke: Double?, val cap: String?, val join: String?, val fill: String?) {
        /**
         * Le tracé tourne-t-il ? Un sous-chemin d'un seul segment droit n'a pas de jointure à
         * arrondir : deux barres de pause dans le même tracé n'en demandent pas.
         */
        val turns: Boolean by lazy {
            val parts = if (data.contains('m')) listOf(data)
            else data.split("M").filter { it.isNotBlank() }.map { "M$it" }
            parts.any { part ->
                val p = flatten(part)
                (2 until p.size).any { i ->
                    abs((p[i - 1].first - p[i - 2].first) * (p[i].second - p[i - 1].second) -
                        (p[i - 1].second - p[i - 2].second) * (p[i].first - p[i - 1].first)) > 1e-6
                }
            }
        }
    }

    class Drawing(val elements: List<El>)

    private val ELEM = Regex("<path\\b[^>]*/>", RegexOption.DOT_MATCHES_ALL)

    fun viewport(xml: String, name: String): Double =
        Regex("android:$name=\"([^\"]*)\"").find(xml)!!.groupValues[1].removeSuffix("dp").toDouble()

    fun of(xml: String): Drawing = Drawing(
        ELEM.findAll(xml).map { m ->
            val e = m.value
            fun a(n: String) = Regex("android:$n=\"([^\"]*)\"").find(e)?.groupValues?.get(1)
            val w = a("strokeWidth")
            El(
                a("pathData") ?: error("tracé sans données"),
                if (w == null || w == "0") null else w.toDouble(),
                a("strokeLineCap"),
                a("strokeLineJoin"),
                a("fillColor"),
            )
        }.toList(),
    )

    fun ink(d: Drawing, element: Int? = null): DoubleArray {
        val boxes = d.elements.map { box(it) }
        val use = if (element == null) boxes else listOf(boxes[element])
        return doubleArrayOf(
            use.minOf { it[0] }, use.minOf { it[1] }, use.maxOf { it[2] }, use.maxOf { it[3] },
        )
    }

    fun inkData(data: String, width: Double): DoubleArray = box(El(data, width, null, null, null))

    private fun box(el: El): DoubleArray {
        val half = (el.stroke ?: 0.0) / 2.0
        val pts = flatten(el.data)
        return doubleArrayOf(
            pts.minOf { it.first } - half, pts.minOf { it.second } - half,
            pts.maxOf { it.first } + half, pts.maxOf { it.second } + half,
        )
    }

    // ---- aplatissement (M L H V A C Q S T Z, en absolu et en relatif) --------------------------

    private val CMD = Regex("([MLHVAZCSQTmlhvazcsqt])([^MLHVAZCSQTmlhvazcsqt]*)")

    private fun nums(v: String): List<Double> =
        Regex("-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?").findAll(v).map { it.value.toDouble() }
            .toList()

    fun flatten(d: String): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        var cur = 0.0 to 0.0
        var start = 0.0 to 0.0
        var ctrl: Pair<Double, Double>? = null
        CMD.findAll(d).forEach { m ->
            val c = m.groupValues[1]
            val rel = c[0].isLowerCase()
            val a = nums(m.groupValues[2])
            fun px(i: Int) = if (rel) a[i] + cur.first else a[i]
            fun py(i: Int) = if (rel) a[i] + cur.second else a[i]
            when (c.uppercase()) {
                "M" -> for (i in a.indices step 2) {
                    cur = px(i) to py(i + 1)
                    if (i == 0) start = cur
                    out += cur
                }
                "L" -> for (i in a.indices step 2) {
                    cur = px(i) to py(i + 1); out += cur
                }
                "H" -> for (i in a.indices) {
                    cur = px(i) to cur.second; out += cur
                }
                "V" -> for (i in a.indices) {
                    cur = cur.first to py(i); out += cur
                }
                "Z" -> {
                    cur = start; out += cur
                }
                "A" -> for (i in a.indices step 7) {
                    val e = px(i + 5) to py(i + 6)
                    out += arc(cur, a[i], a[i + 1], a[i + 2], a[i + 3].toInt(), a[i + 4].toInt(), e)
                    cur = e
                }
                "C" -> for (i in a.indices step 6) {
                    val p1 = px(i) to py(i + 1)
                    val p2 = px(i + 2) to py(i + 3)
                    val p3 = px(i + 4) to py(i + 5)
                    out += cubic(cur, p1, p2, p3); ctrl = p2; cur = p3
                }
                "S" -> for (i in a.indices step 4) {
                    val p2 = px(i) to py(i + 1)
                    val p3 = px(i + 2) to py(i + 3)
                    val p1 = ctrl?.let { 2 * cur.first - it.first to 2 * cur.second - it.second } ?: cur
                    out += cubic(cur, p1, p2, p3); ctrl = p2; cur = p3
                }
                "Q" -> for (i in a.indices step 4) {
                    val p1 = px(i) to py(i + 1)
                    val p2 = px(i + 2) to py(i + 3)
                    out += quad(cur, p1, p2); ctrl = p1; cur = p2
                }
                "T" -> for (i in a.indices step 2) {
                    val p2 = px(i) to py(i + 1)
                    val p1 = ctrl?.let { 2 * cur.first - it.first to 2 * cur.second - it.second } ?: cur
                    out += quad(cur, p1, p2); ctrl = p1; cur = p2
                }
            }
        }
        return out
    }

    private val STEPS = 72

    private fun arc(
        p1: Pair<Double, Double>, rx0: Double, ry0: Double, rot: Double, fa: Int, fs: Int,
        p2: Pair<Double, Double>,
    ): List<Pair<Double, Double>> {
        var rx = abs(rx0)
        var ry = abs(ry0)
        if (rx == 0.0 || ry == 0.0) return listOf(p2)
        val phi = Math.toRadians(rot)
        val dx = (p1.first - p2.first) / 2.0
        val dy = (p1.second - p2.second) / 2.0
        val cosp = cos(phi)
        val sinp = sin(phi)
        val x1 = cosp * dx + sinp * dy
        val y1 = -sinp * dx + cosp * dy
        val lam = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry)
        if (lam > 1) {
            val s = sqrt(lam)
            rx *= s
            ry *= s
        }
        val num = max(0.0, rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1)
        val den = rx * rx * y1 * y1 + ry * ry * x1 * x1
        val coef = (if (fa != fs) 1.0 else -1.0) * sqrt(if (den == 0.0) 0.0 else num / den)
        val cxp = coef * rx * y1 / ry
        val cyp = -coef * ry * x1 / rx
        val cx = cosp * cxp - sinp * cyp + (p1.first + p2.first) / 2.0
        val cy = sinp * cxp + cosp * cyp + (p1.second + p2.second) / 2.0
        val a1 = atan2((y1 - cyp) / ry, (x1 - cxp) / rx)
        val a2 = atan2((-y1 - cyp) / ry, (-x1 - cxp) / rx)
        var dd = a2 - a1
        if (fs == 1 && dd < 0) dd += 2 * Math.PI
        if (fs == 0 && dd > 0) dd -= 2 * Math.PI
        return (1..STEPS).map {
            val t = a1 + dd * it / STEPS
            val xt = rx * cos(t)
            val yt = ry * sin(t)
            Pair(cosp * xt - sinp * yt + cx, sinp * xt + cosp * yt + cy)
        }
    }

    private fun cubic(
        p0: Pair<Double, Double>, p1: Pair<Double, Double>, p2: Pair<Double, Double>,
        p3: Pair<Double, Double>,
    ): List<Pair<Double, Double>> = (1..24).map {
        val t = it / 24.0
        val m = 1 - t
        Pair(
            m * m * m * p0.first + 3 * m * m * t * p1.first + 3 * m * t * t * p2.first + t * t * t * p3.first,
            m * m * m * p0.second + 3 * m * m * t * p1.second + 3 * m * t * t * p2.second + t * t * t * p3.second,
        )
    }

    private fun quad(
        p0: Pair<Double, Double>, p1: Pair<Double, Double>, p2: Pair<Double, Double>,
    ): List<Pair<Double, Double>> = (1..20).map {
        val t = it / 20.0
        val m = 1 - t
        Pair(
            m * m * p0.first + 2 * m * t * p1.first + t * t * p2.first,
            m * m * p0.second + 2 * m * t * p1.second + t * t * p2.second,
        )
    }
}
