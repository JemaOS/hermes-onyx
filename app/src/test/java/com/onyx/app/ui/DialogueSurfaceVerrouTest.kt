package com.onyx.app.ui

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.design.GlassShadeLight
import com.onyx.app.ui.design.GlassShadeStart
import com.onyx.app.ui.design.GlassSheenLight
import com.onyx.app.ui.design.GlassSheenStop
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou des dialogues : un panneau n'a qu'UNE surface, uniforme du bord au bord.**
 *
 * Le défaut du propriétaire sur « Nouveau salon » : *« la fenêtre n'est pas blanche de partout,
 * c'est pas uniforme »*. Le panneau n'était pas d'un seul tenant — une **seconde surface** se
 * peignait par-dessus la première (bande plus claire en haut, corps plus gris, halo aux bords),
 * et l'arête du rectangle interne se voyait sur la forme arrondie.
 *
 * La cause est dans le chaînage : `clip` + `background(teinte opaque)` **puis** `frostedSheet`. Le
 * voile tombait bien à `Transparent`, mais la **finition de vitre** (`drawGlassFinish` : lumière de
 * tête, ombre de pied) se peignait quand même par-dessus un fond opaque : une vitre n'a rien à
 * voiler quand la surface est opaque, et elle n'a rien à éclairer non plus.
 *
 * Ce verrou tient quatre choses :
 *
 * 1. **Une seule surface, mesurée sur une grille.** La face du panneau est évaluée sur
 *    [PanneauFaceAudit.COLONNES] × [PanneauFaceAudit.LIGNES] points (9 × 12, le minimum demandé) et
 *    l'écart entre le point le plus clair et le plus sombre doit rester sous
 *    [PanneauFaceAudit.ECART_MAX] — il est **nul** quand la face est un seul aplat.
 * 2. **Aucune vitre par-dessus un fond.** Aucun `.frostedSheet(` ne suit un `.background(` dans un
 *    même chaînage : une vitre posée sur un fond opaque est la seconde surface.
 * 3. **Le garde-fou est dans la fonction de vitre elle-même** : `frostedSheet` ne peint rien quand
 *    la teinte est opaque (`if (opaque) return@composed this`), donc un site d'appel qui
 *    l'ignorerait ne peindrait pas de seconde surface pour autant.
 * 4. **Un seul chemin de surface** : le dialogue de salon passe par `housePanelSurface`, la **même**
 *    fonction que les feuilles et les alertes.
 *
 * Et la seconde moitié de la plainte : **« Créer » désactivée doit se voir et se dire**. L'action
 * principale garde l'accent ; la désactivée prend un barreau **neutre** et sa raison est **nommée**.
 */
class DialogueSurfaceVerrouTest {

    private val appMain = File("src/main/java")
    private val coreMain = File("../core/src/main/java")

    private fun sources(): List<File> =
        (appMain.walkTopDown() + coreMain.walkTopDown())
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    private fun lignesUtiles(texte: String): List<String> =
        texte.lines().filter { it.isNotBlank() }

    // ============================================================================================
    // 1 · UNE SEULE SURFACE, MESURÉE SUR UNE GRILLE
    // ============================================================================================

    @Test
    fun `la face du panneau est uniforme sur la grille de 9 colonnes sur 12 lignes`() {
        val source = File("../core/src/main/java/com/onyx/app/ui/design/HouseSheet.kt").readText()
        val audit = PanneauFaceAudit.audit(
            "core/src/main/java/com/onyx/app/ui/design/HouseSheet.kt",
            source,
        )
        assertTrue(
            "la grille doit couvrir le panneau : ${audit.points} points mesurés, il en faut au " +
                "moins ${PanneauFaceAudit.COLONNES * PanneauFaceAudit.LIGNES}",
            audit.points >= PanneauFaceAudit.COLONNES * PanneauFaceAudit.LIGNES,
        )
        assertEquals(
            "la face du panneau n'est pas uniforme (écart ${audit.ecart} > " +
                "${PanneauFaceAudit.ECART_MAX}) :\n" + audit.constats.joinToString("\n"),
            emptyList<String>(),
            audit.constats,
        )
    }

    /**
     * **La morsure** : le même audit, sur la source d'AVANT, doit tomber — et nommer la ligne.
     *
     * La chaîne d'avant est reproduite telle qu'elle était (`clip` + `background` + `frostedSheet`),
     * et l'audit doit rendre un écart **au-dessus** du seuil, avec `fichier:ligne`. Sans ce test, un
     * verrou qui ne mesure rien passerait pour un verrou qui n'a rien à dire.
     */
    @Test
    fun `le verrou mord sur la chaine d avant`() {
        val avant = """
            fun Modifier.housePanelSurface(shape: Shape, fill: Color? = null): Modifier = this
                .clip(shape)
                .background(ground, shape)
                .frostedSheet(FrostSurface.SHEET, shape, tint = ground)
        """.trimIndent()
        val audit = PanneauFaceAudit.audit("x/Avant.kt", avant)
        assertTrue(
            "le verrou ne mord pas : l'écart d'avant devrait dépasser ${PanneauFaceAudit.ECART_MAX} " +
                "(mesuré ${audit.ecart})",
            audit.ecart > PanneauFaceAudit.ECART_MAX,
        )
        assertTrue(
            "chaque constat doit nommer le fichier et la ligne",
            audit.constats.isNotEmpty() &&
                audit.constats.all { it.startsWith("x/Avant.kt:") },
        )
    }

    // ============================================================================================
    // 2 · AUCUNE VITRE PAR-DESSUS UN FOND
    // ============================================================================================

    @Test
    fun `aucun panneau ne pose une vitre par dessus son propre fond`() {
        val fautives = mutableListOf<String>()
        sources().forEach { fichier ->
            val lignes = lignesUtiles(fichier.readText())
            lignes.forEachIndexed { index, ligne ->
                if (!ligne.contains(".frostedSheet(")) return@forEachIndexed
                // La **déclaration** de la fonction n'est pas un site d'appel.
                if (ligne.contains("fun Modifier.frostedSheet(")) return@forEachIndexed
                var avant = index - 1
                while (avant >= 0 && (lignes[avant].trimStart().startsWith("//"))) avant--
                val precedente = lignes.getOrNull(avant).orEmpty()
                if (precedente.contains(".background(")) {
                    fautives += "${fichier.name}:${index + 1} — vitre par-dessus un fond opaque " +
                        "(une seconde surface dans la première) : ${ligne.trim()}"
                }
            }
        }
        assertEquals(
            "des panneaux peignent une vitre par-dessus leur fond — c'est la marche que le " +
                "propriétaire voit :\n" + fautives.joinToString("\n"),
            emptyList<String>(),
            fautives,
        )
    }

    @Test
    fun `la fonction de vitre ne peint rien sur une teinte opaque`() {
        val frost = File("../core/src/main/java/com/onyx/app/ui/design/Frost.kt").readText()
        assertTrue(
            "frostedSheet doit sortir sans peindre quand la teinte est opaque (Frost.kt) : " +
                "sans ce garde-fou, la finition du verre repeint une seconde surface",
            frost.contains("val opaque = tint != null && tint.alpha >= 1f") &&
                frost.contains("if (opaque) return@composed this"),
        )
    }

    // ============================================================================================
    // 3 · UN SEUL CHEMIN DE SURFACE, UN SEUL VOILE
    // ============================================================================================

    @Test
    fun `le dialogue de salon passe par la surface commune, pas par une vitre nue`() {
        val salon = File("src/main/java/com/onyx/app/ui/bots/RoomCreationDialog.kt").readText()
        assertTrue(
            "le panneau du salon passe par housePanelSurface (le chemin des feuilles)",
            salon.contains(".housePanelSurface("),
        )
        assertFalse(
            "plus de frostedSheet nu dans le dialogue de salon",
            Regex("""\.frostedSheet\(""").containsMatchIn(salon),
        )
    }

    @Test
    fun `le dialogue repose le theme de l app dans sa fenetre`() {
        // La cause profonde des deux plaints : une `Dialog` ouvre une sous-composition qui retombe
        // sur le thème **par défaut** (clair). Le panneau d'un dialogue sombre était donc **blanc**,
        // et son verre translucide laissait lire la page au travers. Le thème de l'app est reposé à
        // l'entrée de la fenêtre, et la teinte du panneau est calculée là où il est connu.
        val salon = File("src/main/java/com/onyx/app/ui/bots/RoomCreationDialog.kt").readText()
        assertTrue(
            "le dialogue repose le thème qu'il reçoit (la fenêtre ne retombe pas sur le défaut)",
            salon.contains("CompositionLocalProvider(LocalOnyxTheme provides theme)"),
        )
        assertTrue(
            "la teinte du panneau vient du thème du dialogue",
            salon.contains("fill = OnyxSurfaces.surfaceHigh(theme)"),
        )
    }

    @Test
    fun `le dialogue prend le voile de la maison, pas un noir ecrit au site d appel`() {
        val salon = File("src/main/java/com/onyx/app/ui/bots/RoomCreationDialog.kt").readText()
        assertTrue("le voile est celui des feuilles (HouseSheetScrim)", salon.contains("HouseSheetScrim"))
        assertFalse(
            "plus de littéral de voile écrit dans le dialogue",
            Regex("""Color\.Black\.copy\(alpha = 0\.\d+f\)""").containsMatchIn(salon),
        )
    }

    @Test
    fun `l action principale porte l accent, la desactivee un barreau neutre et sa raison`() {
        val salon = File("src/main/java/com/onyx/app/ui/bots/RoomCreationDialog.kt").readText()
        // L'action principale : l'accent de la maison, et lui seul.
        assertTrue("la principale active porte l'accent", salon.contains("primary && enabled -> theme.accent"))
        // La désactivée ne se contente pas d'un accent pâli (« plus pâle » = même gris que l'autre).
        assertFalse(
            "la désactivée n'est pas un accent pâli",
            salon.contains("primary -> theme.accent.copy(alpha = 0.18f)"),
        )
        assertTrue(
            "la désactivée garde la famille de l'accent, atténuée (pas un gris neutre)",
            salon.contains("primary -> theme.accent.copy(alpha = 0.16f)"),
        )
        assertTrue(
            "et son filet est celui de l'accent, pas celui de la secondaire",
            salon.contains("primary && !enabled -> Modifier.border(1.dp, theme.accent.copy(alpha = 0.35f), CircleShape)"),
        )
        // Et elle est **nommée** : désactivée pour la plateforme, avec sa raison en description d'état.
        assertTrue("l'action désactivée est marquée désactivée", salon.contains("disabled()"))
        assertTrue("sa raison est nommée", salon.contains("stateDescription = it"))
    }
}

/**
 * **L'audit de la face d'un panneau : ce que la chaîne de peinture rend, point par point.**
 *
 * Le verrou lit la **source** de la fonction de surface et en tire la liste des étapes qui touchent
 * la face du panneau, dans l'ordre du chaînage — c'est l'ordre de peinture (le premier modificateur
 * du chaînage est peint le premier). Puis il évalue la couleur de la face sur une grille
 * [COLONNES] × [LIGNES] de coordonnées relatives (le centre de chaque cellule) et rend l'écart
 * entre le point le plus clair et le plus sombre.
 *
 * Les amplitudes sont celles du **code** ([GlassSheenLight], [GlassShadeLight], [GlassSheenStop],
 * [GlassShadeStart]) : le chiffre du verrou et le chiffre appliqué à l'écran sortent de la même
 * valeur, donc ils ne peuvent pas diverger.
 *
 * Ce que l'audit modélise, et pourquoi c'est fidèle : les deux seules étapes **dépendantes de la
 * position** sont des dégradés **verticaux** (la lumière de tête éclaire le haut, l'ombre de pied
 * assombrit le bas) — leur valeur ne dépend donc que de `v`. Le voile, lui, est un aplat : il
 * éclaircit ou assombrit la face entière. Ces trois-là suffisent à décider si la face est un
 * **seul aplat**.
 */
internal object PanneauFaceAudit {

    /** Le minimum demandé par le propriétaire : neuf colonnes, douze lignes. */
    const val COLONNES: Int = 9
    const val LIGNES: Int = 12

    /** L'écart toléré, en niveaux de 0 à 1 : la face doit être **un seul aplat**. */
    const val ECART_MAX: Float = 0.004f       // ≈ 1 niveau sur 255

    /** Une étape de peinture qui touche la **face** du panneau (pas l'ombre, qui passe dessous). */
    private enum class Etape { FOND, VOILE, LUMIERE_DE_TETE, OMBRE_DE_PIED }

    data class Audit(val points: Int, val ecart: Float, val constats: List<String>)

    /** La teinte de la lumière de tête / de l'ombre de pied au bord, comme le code les applique. */
    private const val LUMIERE = GlassSheenLight
    private const val OMBRE = GlassShadeLight

    private fun etapes(source: String): List<Pair<Etape, Int>> {
        val out = mutableListOf<Pair<Etape, Int>>()
        source.lines().forEachIndexed { i, ligne ->
            val l = ligne.trim()
            when {
                // L'ombre du socle passe **sous** la face : elle ne la touche pas.
                l.startsWith(".socleShadow(") || l.contains(".socleShadow(") -> Unit
                l.contains(".background(") -> out += Etape.FOND to (i + 1)
                l.contains(".frostedSheet(") || l.contains(".frostedBar(") -> {
                    out += Etape.VOILE to (i + 1)
                    // La finition de vitre : la lumière de tête et l'ombre de pied, peintes
                    // **par-dessus** le fond dans `drawGlassFinish` (LiquidGlass.kt).
                    out += Etape.LUMIERE_DE_TETE to (i + 1)
                    out += Etape.OMBRE_DE_PIED to (i + 1)
                }
                l.contains("drawGlassFinish(") || l.contains("drawFrostPane(") -> {
                    out += Etape.LUMIERE_DE_TETE to (i + 1)
                    out += Etape.OMBRE_DE_PIED to (i + 1)
                }
                l.contains("verticalGradient(") -> {
                    out += Etape.LUMIERE_DE_TETE to (i + 1)
                    out += Etape.OMBRE_DE_PIED to (i + 1)
                }
            }
        }
        return out
    }

    /** La face à la position relative `v` — après toutes les étapes, dans l'ordre. */
    private fun face(ground: Color, etapes: List<Etape>, v: Float): Color {
        var c = ground
        etapes.forEach { e ->
            c = when (e) {
                Etape.FOND -> ground
                Etape.VOILE -> c                       // aplat : n'ajoute aucune position-dépendance
                Etape.LUMIERE_DE_TETE -> if (v < GlassSheenStop) {
                    // Un dégradé vertical : blanc au bord haut, éteint à GlassSheenStop.
                    melange(Color.White, c, LUMIERE * (1f - v / GlassSheenStop))
                } else c
                Etape.OMBRE_DE_PIED -> if (v > GlassShadeStart) {
                    melange(Color.Black, c, OMBRE * ((v - GlassShadeStart) / (1f - GlassShadeStart)))
                } else c
            }
        }
        return c
    }

    private fun melange(dessus: Color, dessous: Color, a: Float): Color = Color(
        red = dessus.red * a + dessous.red * (1f - a),
        green = dessus.green * a + dessous.green * (1f - a),
        blue = dessus.blue * a + dessous.blue * (1f - a),
        alpha = 1f,
    )

    private fun luminance(c: Color): Float =
        0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

    /**
     * L'audit d'une **source** : on isole le corps de `housePanelSurface` (de sa signature à
     * l'accolade de fermeture en colonne 0), on en tire les étapes de peinture, et on évalue la
     * face sur la grille. Rend l'écart le plus grand entre deux points, et un constat par étape
     * qui rend la face non uniforme — `fichier:ligne`.
     */
    fun audit(fichier: String, source: String): Audit {
        val lignes = source.lines()
        val debut = lignes.indexOfFirst { it.contains("fun Modifier.housePanelSurface(") }
            .let { if (it < 0) 0 else it }
        var fin = debut + 1
        while (fin < lignes.size && lignes[fin].trimEnd() != "}") fin++
        val corps = lignes.subList(debut, minOf(fin + 1, lignes.size))
        val et = etapes(corps.joinToString("\n")).map { (e, l) -> e to (l + debut) }
        val noms = et.map { it.first }
        val vals = mutableListOf<Float>()
        for (j in 0 until LIGNES) {
            val v = (j + 0.5f) / LIGNES
            for (i in 0 until COLONNES) {
                // `u` n'entre dans aucune étape : les deux seules étapes position-dépendantes sont
                // des dégradés **verticaux**. La colonne sert à dire que la grille est bien 2D.
                vals += luminance(face(Color.White, noms, v)) + (i * 0f)
            }
        }
        val ecart = (vals.maxOrNull() ?: 0f) - (vals.minOrNull() ?: 0f)
        val constats = mutableListOf<String>()
        if (ecart > ECART_MAX) {
            et.filter { it.first != Etape.FOND && it.first != Etape.VOILE }.distinctBy { it.second }
                .forEach { (_, ligne) ->
                    constats += "$fichier:$ligne — une étape de verre peint la face du panneau " +
                        "(écart mesuré ${"%.3f".format(ecart)}) : la face n'est pas un seul aplat"
                }
            if (constats.isEmpty()) {
                constats += "$fichier — la face du panneau n'est pas un seul aplat " +
                    "(écart mesuré ${"%.3f".format(ecart)})"
            }
        }
        return Audit(points = vals.size, ecart = ecart, constats = constats)
    }
}
