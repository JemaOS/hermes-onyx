package com.onyx.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.ResolvedTheme
import java.util.zip.CRC32
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * **La matière du langage « Galerie » : le fond travaillé, dessiné par le code.**
 *
 * Le défaut que ce fichier ferme est celui que le propriétaire a vu avant tout le reste : la planche
 * validée pose chaque écran sur une **matière** — un dégradé multi-arrêts, des formes floues, un
 * grain léger, un vignettage —, et l'app livrée posait ses écrans sur **un aplat de thème**. Sur un
 * aplat, tout ce qui a été construit par-dessus (verre, ombres, pastilles) se lit à plat : *le verre
 * ne se lit que s'il y a une matière derrière*. La matière n'avait jamais été implémentée ; elle
 * l'est ici, et rien d'autre ne peut être jugé avant elle.
 *
 * **Les teintes sont celles de la planche** (`maquettes-hermes-v2/sources/v4css.py`,
 * `hcss.py` : la base `#EDEBE5` / `#0A0B0E` de la direction J, les taches `#D2743F`, `#3A5AA8`,
 * `#1E8D86`, `#6E86FF`, `#8C5A2E` des palettes de couverture). Elles ne sont pas choisies : elles
 * sont recopiées.
 *
 * **Rien n'est aléatoire.** Quatre couches, toutes déterministes — aucune horloge, aucun `Random`,
 * aucun état :
 *
 * 1. le **fond** : un dégradé linéaire **multi-arrêts** (quatre arrêts, à l'angle de la recette) ;
 * 2. les **formes floues** : quatre taches radiales, chaudes et froides, qui donnent le relief ;
 * 3. le **grain** : un semis de points dont l'opacité sort d'un hachage de la position — la même
 *    texture à chaque rendu, et qui casse les bandes du dégradé ;
 * 4. le **vignettage** : un assombrissement radial des bords, qui ramène l'œil au centre.
 *
 * Un test mesure que la recette est complète (quatre couches, des opacités non nulles) : une matière
 * dont une couche serait à zéro ne serait plus une matière, et c'est exactement le défaut d'origine.
 */
object Matiere {

    /** Une **forme floue** : un centre en fraction de la boîte, un rayon, une encre, une opacité. */
    data class Blob(
        val cx: Float,
        val cy: Float,
        val radius: Float,
        val argb: Long,
        val alpha: Float,
    )

    /**
     * La recette d'une matière : sa base, son dégradé, ses formes floues, son vignettage et son
     * grain. Chaque champ est un paramètre **mesurable**, donc vérifiable sans appareil.
     */
    data class Recipe(
        val name: String,
        /** L'angle du dégradé de fond, en degrés (comme `linear-gradient(158deg, …)`). */
        val angleDeg: Float,
        /** Les arrêts du fond, du premier au dernier — au moins quatre, c'est un dégradé *travaillé*. */
        val stopsArgb: List<Long>,
        /** Les formes floues, dessinées du fond vers l'avant. */
        val blobs: List<Blob>,
        val vignetteArgb: Long,
        val vignetteAlpha: Float,
        val grainArgb: Long,
        val grainAlpha: Float,
    ) {
        /** Le pas du semis de grain, en pixels : 3 px — plus fin que ce que l'œil sépare à 1,0×. */
        val grainStepPx: Float = 3f

        init {
            require(stopsArgb.size >= 4) { "un fond travaillé demande au moins quatre arrêts" }
            require(blobs.isNotEmpty()) { "une matière sans forme floue n'est qu'un aplat" }
            require(blobs.all { it.alpha > 0f }) { "une forme floue d'opacité nulle ne rend rien" }
            require(vignetteAlpha > 0f) { "un vignettage d'opacité nulle ne rend rien" }
            require(grainAlpha > 0f) { "un grain d'opacité nulle ne rend rien" }
        }
    }

    /**
     * **La matière claire.** Fond chaud en haut à gauche, qui glisse vers un gris froid en bas à
     * droite — les teintes de la planche : une tache ambre (`#D2743F`), une tache bleue (`#3A5AA8`),
     * un souffle jade (`#1E8D86`) et une lumière crème (`#FFE2B4`).
     */
    val clair = Recipe(
        name = "Galerie claire",
        angleDeg = 158f,
        stopsArgb = listOf(0xFFF8F2E7, 0xFFEDEBE5, 0xFFEAE8E3, 0xFFE3E7F2),
        blobs = listOf(
            Blob(0.14f, 0.08f, 0.62f, 0xFFD2743F, 0.20f),
            Blob(0.94f, 0.20f, 0.56f, 0xFF3A5AA8, 0.15f),
            Blob(0.72f, 0.46f, 0.44f, 0xFFFFE2B4, 0.16f),
            Blob(0.28f, 0.88f, 0.66f, 0xFF1E8D86, 0.12f),
        ),
        vignetteArgb = 0xFF080B12,
        vignetteAlpha = 0.14f,
        grainArgb = 0xFF080B12,
        grainAlpha = 0.06f,
    )

    /**
     * **La matière sombre.** Fond noir bleuté, deux taches froides (`#6E86FF`, `#2740B0`), une
     * tache chaude (`#8C5A2E`) et un souffle jade — les mêmes teintes que le clair, retournées sur
     * le noir, comme le fait la planche.
     */
    val sombre = Recipe(
        name = "Galerie sombre",
        angleDeg = 150f,
        stopsArgb = listOf(0xFF161A2C, 0xFF0C0D14, 0xFF0A0B0E, 0xFF060709),
        blobs = listOf(
            Blob(0.15f, 0.11f, 0.58f, 0xFF6E86FF, 0.26f),
            Blob(0.90f, 0.28f, 0.56f, 0xFF2740B0, 0.22f),
            Blob(0.34f, 0.54f, 0.50f, 0xFF1E8D86, 0.12f),
            Blob(0.74f, 0.90f, 0.56f, 0xFF8C5A2E, 0.14f),
        ),
        vignetteArgb = 0xFF000000,
        vignetteAlpha = 0.34f,
        grainArgb = 0xFFFFFFFF,
        grainAlpha = 0.06f,
    )

    /** La recette d'une apparence. Fonction pure : la même apparence donne la même matière. */
    fun recipe(dark: Boolean): Recipe = if (dark) sombre else clair

    /** La graine de la matière : `crc32` de la clé — la même famille de hachage que les vignettes. */
    fun seed(key: String): Long {
        val crc = CRC32()
        crc.update(key.toByteArray(Charsets.UTF_8))
        return crc.value
    }

    /**
     * **L'opacité d'un point de grain**, pour une position `(colonne, ligne)` et une graine données.
     * Purement calculée : le même triplet rend la même valeur, toujours.
     *
     * Le mélange est un **avalanche** (xor-shift / multiplication par des constantes impaires) : la
     * première version hachait par `col * 131 + row`, dont les bits restaient corrélés d'une colonne à
     * la suivante — le « grain » se lisait alors comme une **trame de tissu diagonale**, ce qui a été
     * vu à l'appareil et corrigé ici. Un mélange propre rend un semis sans structure.
     */
    fun grainAt(col: Int, row: Int, seed: Long): Float {
        var z = seed xor (col.toLong() * 0x9E3779B97F4A7C15uL.toLong()) xor
            (row.toLong() * 0xC2B2AE3D27D4EB4FuL.toLong())
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
        z = (z xor (z ushr 27)) * 0x94D049BB133111EBuL.toLong()
        z = z xor (z ushr 31)
        val n = ((z ushr 24) and 0xFF).toInt()
        return if (n > 176) (n - 176) / 79f else 0f
    }

    /** Le côté du carreau de grain, en pixels : 128 × 128, mosaïqué par le shader. */
    const val GrainTilePx: Int = 128

    /** Le facteur d'agrandissement du grain : **un point vaut trois pixels** de l'appareil. */
    const val GrainScale: Float = 3f

    /**
     * **Le carreau de grain, construit une fois.** La première version semait un point par pas de
     * 3 px **à chaque rendu** — 40 000 `drawRect` par image sur 411 × 891, ce qui a fait **bloquer
     * l'application** (« Hermes Onyx isn't responding », relevé à l'appareil). Le grain est
     * maintenant une **image de 128 × 128**, construite **une seule fois** par apparence et mosaïquée
     * par un shader ([TileMode.Repeated]) : un seul appel de dessin, quelle que soit la surface.
     */
    private val grainTiles = HashMap<String, androidx.compose.ui.graphics.ImageBitmap>()

    fun grainTile(recipe: Recipe): androidx.compose.ui.graphics.ImageBitmap =
        grainTiles.getOrPut(recipe.name) {
            val n = GrainTilePx
            val pixels = IntArray(n * n)
            val ink = socleInk(recipe.grainArgb)
            for (row in 0 until n) {
                for (col in 0 until n) {
                    val a = grainAt(col, row, 0x9E3779B97F4A7C15uL.toLong())
                    pixels[row * n + col] =
                        if (a > 0f) ink.copy(alpha = a * recipe.grainAlpha).toArgb() else 0
                }
            }
            val bitmap = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, n, 0, 0, n, n)
            bitmap.asImageBitmap()
        }
}

/**
 * **Le dessin de la matière**, pur [DrawScope] — quatre couches, dans l'ordre.
 *
 * Séparé du composable pour être **mesurable sans appareil** : un test lui donne une taille et une
 * recette, et relit ce qui doit être non nul.
 */
fun DrawScope.drawMatiere(recipe: Matiere.Recipe, seed: Long) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    // 1 · LE FOND — un dégradé linéaire multi-arrêts, à l'angle de la recette.
    val rad = Math.toRadians(recipe.angleDeg.toDouble())
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()
    val half = (w + h) / 2f
    drawRect(
        brush = Brush.linearGradient(
            colors = recipe.stopsArgb.map { socleInk(it) },
            start = Offset(w / 2f - dx * half, h / 2f - dy * half),
            end = Offset(w / 2f + dx * half, h / 2f + dy * half),
        ),
        size = Size(w, h),
    )

    // 2 · LES FORMES FLOUES — des taches radiales, chaudes et froides.
    recipe.blobs.forEach { blob ->
        val ink = socleInk(blob.argb)
        val centre = Offset(blob.cx * w, blob.cy * h)
        val radius = blob.radius * max(w, h)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ink.copy(alpha = blob.alpha), ink.copy(alpha = 0f)),
                center = centre,
                radius = radius,
            ),
            radius = radius,
            center = centre,
        )
    }

    // 3 · LE GRAIN — un carreau déterministe de 128 × 128, mosaïqué par un shader et **posé à
    // 3 pixels pour un point** : **un seul** dessin, au lieu de quarante mille rectangles (c'est ce
    // qui bloquait l'application), et un grain **large de trois pixels** au lieu d'un grain d'un
    // pixel — à 420 dpi, un point de la taille du pixel crénelait en trame de tissu.
    val tile = Matiere.grainTile(recipe)
    scale(scaleX = Matiere.GrainScale, scaleY = Matiere.GrainScale, pivot = Offset.Zero) {
        drawRect(
            brush = androidx.compose.ui.graphics.ShaderBrush(
                androidx.compose.ui.graphics.ImageShader(
                    tile,
                    androidx.compose.ui.graphics.TileMode.Repeated,
                    androidx.compose.ui.graphics.TileMode.Repeated,
                ),
            ),
            size = Size(w / Matiere.GrainScale, h / Matiere.GrainScale),
        )
    }

    // 4 · LE VIGNETTAGE — les bords s'assombrissent, l'œil revient au centre.
    val vInk = socleInk(recipe.vignetteArgb)
    val vCentre = Offset(w * 0.5f, h * 0.42f)
    val vRadius = max(w, h) * 0.86f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                vInk.copy(alpha = 0f),
                vInk.copy(alpha = 0f),
                vInk.copy(alpha = recipe.vignetteAlpha),
            ),
            center = vCentre,
            radius = vRadius,
        ),
        size = Size(w, h),
    )
}

/**
 * **La matière, posée derrière le contenu d'une surface.** À poser **après** la couleur de fond
 * (la page, le panneau) : la matière en est la lumière, le fond en est l'aplat. Elle est dessinée
 * **avant** le contenu, donc elle ne recouvre jamais une encre.
 *
 * Elle se compose avec le halo de la maison : `.matiereBackdrop(theme).haloBackdrop(theme)` pose la
 * matière au fond, puis la lueur d'accent par-dessus — la page a alors une profondeur *et* une
 * lumière, ce que la vitre attendait pour se lire.
 */
fun Modifier.matiereBackdrop(
    theme: ResolvedTheme,
    key: String = "onyx-matiere",
): Modifier = drawWithContent {
    drawMatiere(Matiere.recipe(theme.dark), Matiere.seed(key))
    drawContent()
}

/**
 * **La page travaillée d'un écran** : la couleur de page, la matière, puis le halo de la maison.
 *
 * C'est le pendant de [onyxPage] pour les deux écrans où la matière porte la lecture — le volet
 * Discussions et le fil. Un écran qui pose `onyxPage` garde exactement ce qu'il avait ; celui qui
 * pose `matierePage` gagne les quatre couches, dans le bon ordre.
 */
@Composable
fun Modifier.matierePage(theme: ResolvedTheme, haloHeight: Dp = 260.dp): Modifier =
    this
        .background(theme.pageColor)
        .matiereBackdrop(theme)
        .haloBackdrop(theme, haloHeight)
