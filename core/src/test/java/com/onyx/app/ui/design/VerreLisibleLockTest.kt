package com.onyx.app.ui.design

import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **LE VERROU DU VERRE : rien de lisible ne survit dans la vitre, et le fond ne se gauchit pas.**
 *
 * Le propriétaire a refusé le verre des tuiles du volet : « réfraction et déformation de ce qui se
 * passe derrière ». Ce n'était **pas** de la réfraction : c'était un **flou insuffisant** et un
 * **déplacement du fond démesuré**. Deux causes, nommées ici :
 *
 *  1. **La réfraction réinjecte le détail** ([GlassRefractionDetailIntensity] = 0,8) : 80 % des
 *     arêtes et du texte du fond traversaient la vitre **nets**, malgré un flou de 30 dp. L'étiquette
 *     de section **« RACCOURCIS »**, juste au-dessus de la grille, se lisait **dans** la tuile
 *     « Capacités ».
 *  2. **Le déplacement était dérivé du flou** (`blurDp × 0,9`) : une tuile de 30 dp de flou déplaçait
 *     le fond de **27 dp** (74 px à 2,75×) — une lentille, pas une vitre. C'est ce qui **gauchit** les
 *     arêtes et **duplique** ce qui passe à côté.
 *
 * Le test **modélise le pipeline** (flou de boîte au rayon réellement appliqué, profil d'arête pour
 * le champ de déplacement, réinjection de détail) et mesure, **par surface de la table** :
 *
 *  1. l'**énergie des détails** (gradient) dans le verre, rapportée à celle du fond derrière — s'il
 *     en reste, une arête est lisible et le verrou tombe ;
 *  2. le **champ de déplacement** : son maximum et son étendue (l'uniformité) ;
 *  3. la **corrélation de doublon** : le verre reproduit-il une copie **décalée** du fond ?
 *  4. le **rayon de flou** réellement appliqué, en px.
 *
 * La recette qui l'alimente vient de la **source de production** ([LiquidGlass.kt]) : le flou de la
 * fonction compilée [glassPaintBlurDp], le reste lu dans le fichier — donc ce que le test chiffre est
 * ce que l'app peint, et le verrou **mord** sur la source d'avant (il la nomme), pas seulement sur
 * un tableau écrit à côté.
 */
class VerreLisibleLockTest {

    // -- La cible de la référence, chiffrée --

    /** Au-delà, une arête du fond reste lisible dans la vitre. */
    private val energyMax = 0.05f

    /** Le déplacement du fond doit rester **sous le pixel** (quelques dixièmes). */
    private val displacementMaxPx = 0.75f

    /** Et il doit être **uniforme** : l'écart entre son maximum et son minimum reste sous le pixel. */
    private val displacementSpreadMaxPx = 0.75f

    /** Au-delà, le verre montre une **seconde copie** du fond. */
    private val duplicateCorrMax = 0.10f

    /** La densité de la capture du propriétaire (xxhdpi). */
    private val density = 2.75f

    /** La fenêtre de mesure, en px : la tuile et le fond qui la borde. */
    private val width = 240

    /**
     * Les montants du mot « RACCOURCIS », en px — des largeurs **inégales** (de vraies lettres),
     * donc le fond n'est pas périodique et un décalage ne se confond pas avec la période.
     */
    private val strokes = intArrayOf(
        3, 1, 2, 4, 1, 3, 2, 1, 4, 2, 3, 1, 2, 2, 4, 1, 3, 3, 1, 2, 4, 1, 2, 3,
    )

    // -- La recette, lue dans la source de production --

    private data class Recipe(
        val blurDp: Float,
        val displacementDp: Float,
        val detailIntensity: Float,
        val chroma: Float,
        val edgeDp: Float,
        val displacementSource: String,
    )

    private fun source(): String = read("core/src/main/java/com/onyx/app/ui/design/LiquidGlass.kt")

    private fun const(src: String, name: String): Float {
        val m = Regex("const val $name: Float = ([0-9.]+)f").find(src)
            ?: error("la constante $name a disparu de la source")
        return m.groupValues[1].toFloat()
    }

    /** Ce que `glassRefractionStyle` calcule **réellement** pour le déplacement. */
    private fun displacementExpression(src: String): String {
        val m = Regex("refractionDisplacement = ([^\\n,]+),").find(src)
            ?: error("`glassRefractionStyle` ne déclare plus son déplacement")
        return m.groupValues[1].trim()
    }

    /**
     * La recette appliquée à une surface : le flou vient de la **fonction compilée** (donc de la
     * table), le reste est lu dans le fichier — et le déplacement suit **l'expression de la source**,
     * qu'elle dérive du flou (l'ancienne faute) ou qu'elle soit absolue (la correction).
     */
    private fun recipeFor(surface: GlassSurface): Recipe {
        val src = source()
        val blurDp = glassPaintBlurDp(surface)
        val expr = displacementExpression(src)
        val ratio = const(src, "GlassRefractionDisplacementRatio")
        val absolute = Regex("const val GlassRefractionDisplacementDp: Float = ([0-9.]+)f")
            .find(src)?.groupValues?.get(1)?.toFloat()
        // La forme **dérivée du flou** est celle qui a produit la lentille : elle se reconnaît à la
        // référence explicite au flou.
        val fromBlur = expr.contains("blurDp")
        val displacementDp = when {
            fromBlur -> blurDp * ratio
            absolute != null -> absolute
            else -> ratio
        }
        return Recipe(
            blurDp = blurDp,
            displacementDp = displacementDp,
            detailIntensity = const(src, "GlassRefractionDetailIntensity"),
            chroma = const(src, "GlassChromaticAberration"),
            edgeDp = const(src, "GlassRefractionEdgeDp"),
            displacementSource = expr,
        )
    }

    // -- Le fond de la planche : le mot « RACCOURCIS » et une arête droite --

    private fun backdrop(): FloatArray {
        val f = FloatArray(width)
        var x = 0
        var i = 0
        while (x < width) {
            val ink = strokes[i % strokes.size]
            val gap = strokes[(i + 7) % strokes.size] + 1
            for (k in 0 until ink) if (x + k < width) f[x + k] = 1f
            x += ink + gap
            i++
        }
        // L'arête DROITE du panneau, qui traverse la tuile : celle qu'on voyait « gauchie ».
        for (k in width / 2 until width) f[k] += 0.8f
        return f
    }

    private fun boxBlur(v: FloatArray, radiusPx: Float): FloatArray {
        val r = max(0, radiusPx.roundToInt())
        if (r <= 0) return v.copyOf()
        val out = FloatArray(v.size)
        var sum = 0f
        for (x in v.indices) {
            val lo = max(0, x - r)
            val hi = min(v.size - 1, x + r)
            var s = 0f
            for (k in lo..hi) s += v[k]
            out[x] = s / (hi - lo + 1)
            sum += out[x]
        }
        return out
    }

    private fun meanAbsDiff(v: FloatArray): Float {
        var s = 0f
        for (i in 0 until v.size - 1) s += abs(v[i + 1] - v[i])
        return s / (v.size - 1)
    }

    private fun pearson(a: FloatArray, b: FloatArray): Double {
        val n = a.size
        var ma = 0.0
        var mb = 0.0
        for (i in 0 until n) {
            ma += a[i]
            mb += b[i]
        }
        ma /= n
        mb /= n
        var va = 0.0
        var vb = 0.0
        var cov = 0.0
        for (i in 0 until n) {
            va += (a[i] - ma) * (a[i] - ma)
            vb += (b[i] - mb) * (b[i] - mb)
            cov += (a[i] - ma) * (b[i] - mb)
        }
        if (va <= 1e-12 || vb <= 1e-12) return 0.0
        return cov / sqrt(va * vb)
    }

    private fun highPass(v: FloatArray): FloatArray {
        val b = boxBlur(v, 3f)
        return FloatArray(v.size) { v[it] - b[it] }
    }

    private fun shift(v: FloatArray, s: Int): FloatArray =
        FloatArray(v.size) { v[max(0, min(v.size - 1, it + s))] }

    private fun variance(v: FloatArray): Double {
        var m = 0.0
        for (x in v) m += x
        m /= v.size
        var s = 0.0
        for (x in v) s += (x - m) * (x - m)
        return s / v.size
    }

    private data class Measurement(
        val surface: GlassSurface,
        val blurPx: Float,
        val displacementMaxPx: Float,
        val displacementSpreadPx: Float,
        val energyRatio: Float,
        val duplicateCorr: Float,
        val chroma: Float,
    ) {
        val readableEdge: Boolean get() = energyRatio > 0.05f
        val nonUniform: Boolean get() = displacementSpreadPx > 0.75f
        val duplicated: Boolean get() = duplicateCorr > 0.10f
        val ok: Boolean get() = !readableEdge && !nonUniform && !duplicated
    }

    private fun measure(surface: GlassSurface): Measurement {
        val r = recipeFor(surface)
        val f = backdrop()
        val base = meanAbsDiff(f)
        val edgeBandPx = max(1f, r.edgeDp * density)
        val maxDispPx = r.displacementDp * density
        // Le profil d'arête : le déplacement est maximal SUR le bord et s'éteint sur la bande —
        // c'est lui qui, sans borne, tirait l'étiquette voisine dans la tuile.
        val disp = FloatArray(width) { x ->
            val dist = min(x, width - 1 - x).toFloat()
            maxDispPx * max(0f, min(1f, 1f - dist / edgeBandPx))
        }
        val blurred = boxBlur(f, r.blurDp * density)
        val g = FloatArray(width) { x ->
            val src = max(0, min(width - 1, (x + disp[x]).roundToInt()))
            r.detailIntensity * f[src] + (1f - r.detailIntensity) * blurred[x]
        }
        val energy = meanAbsDiff(g) / base
        // Le doublon : une copie DÉCALÉE du fond dans le verre. Sans hautes fréquences dans le
        // verre (flou réussi), il n'y a rien à dupliquer.
        val hpG = highPass(g)
        val duplicate = if (variance(hpG) < 0.01 * variance(f)) {
            0.0
        } else {
            val maxLag = max(2, ceil(maxDispPx.toDouble()).toInt())
            var best = 0.0
            for (s in 2..maxLag) {
                best = max(best, abs(pearson(hpG, highPass(shift(f, s)))))
                best = max(best, abs(pearson(hpG, highPass(shift(f, -s)))))
            }
            best
        }
        return Measurement(
            surface = surface,
            blurPx = r.blurDp * density,
            displacementMaxPx = maxDispPx,
            displacementSpreadPx = maxDispPx,
            energyRatio = energy,
            duplicateCorr = duplicate.toFloat(),
            chroma = r.chroma,
        )
    }

    /** Le nom que le défaut porte, tel qu'on l'écrit au propriétaire. */
    private fun label(surface: GlassSurface): String = when (surface) {
        GlassSurface.SIDEBAR_TILE -> "la tuile « Capacités » du volet RACCOURCIS"
        GlassSurface.SIDEBAR_PANEL -> "le panneau du volet"
        GlassSurface.LIST_ROW -> "une rangée de liste du volet"
        GlassSurface.BOT_CARD -> "une carte de bot"
        GlassSurface.TAB_BAR -> "la barre d'onglets"
        GlassSurface.THREAD_TOP_BAR -> "la barre du haut du fil"
        GlassSurface.COMPOSER -> "la plaque du compositeur"
        GlassSurface.HEADER_CAPSULE -> "la capsule d'en-tête"
        GlassSurface.RESPONSE_BLOCK -> "le bloc de réponse"
        GlassSurface.SHEET -> "la feuille du bas"
        GlassSurface.ALERT -> "l'alerte"
        GlassSurface.MENU -> "le menu ancré"
        GlassSurface.SEGMENTED -> "le contrôle segmenté"
        GlassSurface.MATTER_CARD -> "la grande carte de matière"
        GlassSurface.PANE -> "la plaque d'un onglet de contenu"
    }

    private fun explain(m: Measurement): String = buildString {
        append("${m.surface.name} — ${label(m.surface)} : le mot « RACCOURCIS » ")
        if (m.readableEdge) {
            append("reste LISIBLE dans le verre ")
            append("(énergie des détails ${"%.3f".format(m.energyRatio)} du fond, seuil $energyMax) ; ")
        }
        if (m.nonUniform) {
            append("le fond est GAUCHI ")
            append(
                "(déplacement ${"%.2f".format(m.displacementMaxPx)} px, " +
                    "étendue ${"%.2f".format(m.displacementSpreadPx)} px, seuil $displacementSpreadMaxPx) ; ",
            )
        }
        if (m.duplicated) {
            append("le verre en montre une SECONDE COPIE ")
            append("(corrélation de doublon ${"%.3f".format(m.duplicateCorr)}, seuil $duplicateCorrMax) ; ")
        }
        append("flou appliqué ${"%.1f".format(m.blurPx)} px, frange chromatique ${m.chroma}")
    }

    // -- Les verrous --

    @Test
    fun `aucune surface en verre ne laisse lire le fond ni ne le gauchit`() {
        val report = StringBuilder("\nTABLE DU VERRE — le mot « RACCOURCIS » derrière la vitre\n")
        report.append(
            String.format(
                "%-18s %7s %9s %9s %9s %9s %7s%n",
                "surface", "flou px", "dépl max", "étendue", "énergie", "doublon", "chroma",
            ),
        )
        var checked = 0
        val failures = mutableListOf<String>()
        GlassSurface.entries.forEach { surface ->
            val m = measure(surface)
            report.append(
                String.format(
                    "%-18s %7.1f %9.2f %9.2f %9.3f %9.3f %7.3f  %s%n",
                    surface.name, m.blurPx, m.displacementMaxPx, m.displacementSpreadPx,
                    m.energyRatio, m.duplicateCorr, m.chroma, if (m.ok) "ok" else "ÉCHEC",
                ),
            )
            checked++
            if (!m.ok) failures += explain(m)
        }
        report.append("surfaces vérifiées : $checked\n")
        print(report)
        assertTrue("la table doit couvrir toutes les surfaces en verre", checked >= 14)
        // **Le message NOMME chaque surface fautive** — la tuile « Capacités » du volet RACCOURCIS
        // en tête, puisque c'est elle que le propriétaire a encadrée.
        assertTrue(
            buildString {
                append("${failures.size} surface(s) en verre laissent transparaître ou gauchir :\n")
                failures.forEachIndexed { i, f -> append("  ${i + 1}. $f\n") }
            },
            failures.isEmpty(),
        )
    }

    @Test
    fun `le deplacement ne se derive plus du flou et la refraction ne reinjecte plus de detail`() {
        // Les deux causes, figées : le déplacement est ABSOLU (il ne suit plus le flou — c'est ce qui
        // en faisait une lentille de 27 dp), et la réfraction ne réinjecte plus aucune haute
        // fréquence (l'étiquette ne peut plus traverser la vitre en clair).
        val src = source()
        val expr = displacementExpression(src)
        assertTrue(
            "le déplacement ne doit PLUS se dériver du flou (expression lue : « $expr »)",
            !expr.contains("blurDp"),
        )
        assertTrue(
            "la réfraction ne doit plus réinjecter de détail (réglé sur ${const(src, "GlassRefractionDetailIntensity")})",
            const(src, "GlassRefractionDetailIntensity") <= 0.01f,
        )
        assertTrue(
            "aucune dispersion chromatique (réglée sur ${const(src, "GlassChromaticAberration")})",
            const(src, "GlassChromaticAberration") <= 0.01f,
        )
        assertTrue(
            "la force de réfraction reste un souffle",
            const(src, "GlassRefractionStrength") <= 0.20f,
        )
        assertTrue(
            "le verre gagne sa saturation (fonction appelée par le dessin)",
            src.contains("chromaMultiplier(GlassSaturationGain)"),
        )
        assertTrue(
            "l'arête claire reste posée par le même passage",
            src.contains("specularIntensity(GlassSpecularIntensity)") &&
                src.contains("GlassEdgeAlpha"),
        )
    }

    private fun read(path: String): String =
        listOf(File(path), File("../$path")).firstOrNull { it.isFile }?.readText()
            ?: error("fichier introuvable : $path")
}
