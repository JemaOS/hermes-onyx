package com.onyx.app.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.onyx.app.ui.theme.ResolvedTheme
import kotlin.math.hypot

/**
 * **La matière : ce que la vitre a à manger.**
 *
 * Le défaut relevé par le propriétaire sur ses trois captures n'était pas un défaut de verre, c'était
 * un défaut de **fond** : « je ne sais pas où est ton verre liquide ». Une vitre qui floute et
 * réfracte **ne montre rien** s'il n'y a **aucune matière à échantillonner** — et nos pages étaient
 * d'un bleu-gris **plat**. Sur un aplat, le flou d'un aplat est l'aplat, et le déplacement d'un
 * dégradé linéaire est le même dégradé : un verre parfait y est, à l'œil, **absent**.
 *
 * Ce que la référence du propriétaire (l'écran de mise à jour du système) montre, et qui manquait
 * ici : un fond **plein écran, coloré et dégradé**, avec des **zones froides** en haut et des
 * **traînées chaudes** qui remontent du bas. C'est cette matière-là qui fait vivre la vitre : elle lui
 * donne des **transitions locales** (une tache a un bord) que la réfraction peut **courber** et que le
 * flou peut **étaler** — les deux choses que ni un aplat ni un dégradé régulier ne peuvent montrer.
 *
 * **Trois règles, et elles sont mesurables.**
 *
 * 1. **La matière est un éclairage, pas un aplat de couleur.** Chaque zone est un **gradient radial**
 *    qui part de son centre et s'éteint : la page reste la page au fond, la matière en est la
 *    lumière. Aucune zone n'est opaque, et leurs opacités sont **bornées** ([ColdAlpha] /
 *    [WarmAlpha]) : la lisibilité de l'encre est mesurée dessus ([brightest] / [darkest]), et un test
 *    la refuse si elle passe sous le seuil.
 * 2. **Une seule couleur saturée reste vraie.** La zone froide **est** l'accent du thème ; la zone
 *    chaude n'est pas une seconde couleur choisie à l'œil, c'est **l'accent tourné vers l'ambre**
 *    ([WarmShift] de [WarmAnchor]) — la même famille, l'autre température. Rien n'entre dans la
 *    palette qui n'en vienne.
 * 3. **« Halo » éteint n'éteint pas le verre, il éteint la couleur.** Le réglage reste tenu (rien de
 *    coloré n'est peint), mais la matière garde une **lumière neutre** ([neutralZones]) : deux
 *    gris de la maison, un plus clair en haut, un plus creux en bas. Une vitre y garde donc de la
 *    matière — un peu de lumière, un peu de relief — sans qu'une seule couleur soit ajoutée à
 *    l'écran. C'est la différence entre « éteint » et « plat ».
 *
 * **Ce que la matière coûte.** Quatre gradients radiaux, peints **une fois** par écran sous le
 * contenu ([matterBackdrop], en fond de la page), sans animation : rien n'est recalculé par trame,
 * et le budget du verre ([GlassFluidity]) reste celui des vitres. Un écran sans vitre ne paie rien de
 * plus qu'un aplat : les mêmes quatre `drawCircle`.
 */
@Stable
object Matter {

    /** L'opacité d'une zone **froide** (l'accent) au centre de son gradient. */
    const val ColdAlpha: Float = 0.16f

    /** L'opacité d'une zone **chaude** (l'accent tourné vers l'ambre). */
    const val WarmAlpha: Float = 0.13f

    /** La part de lumière neutre quand « Halo » est éteint : l'opacité de la zone. */
    const val NeutralAlpha: Float = 0.25f

    /** Et la part de blanc (ou de noir) qu'elle porte : les deux se multiplient pour se voir. */
    const val NeutralLift: Float = 0.25f

    /** La part d'ambre dans la zone chaude : assez pour la chaleur, pas assez pour une seconde teinte. */
    const val WarmShift: Float = 0.72f

    /** La part de bleu dans la zone froide : assez pour la fraîcheur, pas assez pour une autre teinte. */
    const val CoolShift: Float = 0.45f

    /** L'ancre chaude de la maison — un ambre de lumière, jamais un aplat. */
    val WarmAnchor: Color = Color(0xFFF0A24B)

    /**
     * L'ancre froide : un bleu de lumière.
     *
     * Elle existe parce qu'un thème peut avoir un accent **chaud** (« Ambre », « Hermes », le doré de
     * `githubDark`) : prendre l'accent pour la zone froide ne donnerait alors **deux zones chaudes**,
     * et la matière perdrait ce que la référence du propriétaire montre — du froid en haut, du chaud
     * en bas. La zone froide est donc l'accent **tiré vers ce bleu** : la famille du thème pour la
     * teinte, la température de la maison pour la lumière.
     */
    val CoolAnchor: Color = Color(0xFF4E6BF0)

    /** Le rayon, en fraction de la **largeur** de la surface, d'une zone dont c'est la valeur. */
    private const val R = 0.62f

    /**
     * Une zone de matière : une couleur, un centre (en fractions de la surface) et un rayon (en
     * fraction de la largeur). Le rayon s'exprime en largeur — pas en pixels — pour que la même
     * matière tienne sur un téléphone étroit et sur un pliable déplié.
     */
    @Immutable
    data class Zone(
        val colour: Color,
        /** Le centre, en fractions : `x` de la largeur, `y` de la hauteur. */
        val x: Float,
        val y: Float,
        /** Le rayon, en fraction de la largeur. */
        val radius: Float,
        val alpha: Float,
    ) {
        /** La couleur de la zone, composée sur [ground] — c'est ce que la vitre échantillonne. */
        fun over(ground: Color): Color = compositedBackdrop(colour.copy(alpha = alpha), ground)

        /** La couleur de la zone à moitié de son rayon : le gradient y est déjà à moitié éteint. */
        fun midway(ground: Color): Color = compositedBackdrop(colour.copy(alpha = alpha / 2f), ground)
    }

    /** La zone chaude du thème : l'accent tourné vers l'ambre, jamais une couleur étrangère. */
    fun warm(theme: ResolvedTheme): Color = lerp(theme.accent, WarmAnchor, WarmShift)

    /** La zone froide du thème : l'accent tourné vers le bleu — toujours plus froide que la chaude. */
    fun cool(theme: ResolvedTheme): Color = lerp(theme.accent, CoolAnchor, CoolShift)

    /**
     * Les zones d'un écran de la maison, dans l'ordre où elles se peignent.
     *
     * La disposition est celle de la référence : la **lumière froide en haut** (deux zones, une à
     * gauche, une qui déborde à droite), les **traînées chaudes qui remontent du bas** (une à droite,
     * une à gauche plus basse). Aucune n'est centrée pile : une tache centrée se lit comme un rond,
     * une tache décalée se lit comme une lumière.
     *
     * Les opacités sont **celles que la mesure a retenues** ([scale]) : la matière ne peut pas
     * descendre l'encre du thème sous son seuil, et sur les thèmes sombres à accent clair
     * (`terminal`, `amber`) un accent plein à 16 % passait dessous. C'est la même règle que la teinte
     * d'une vitre : **c'est le fond qui bouge, pas la lisibilité**.
     */
    fun colouredZones(theme: ResolvedTheme): List<Zone> = colouredZones(theme, scale(theme))

    /** Les zones colorées à une échelle d'opacité donnée — la fonction pure du balayage. */
    fun colouredZones(theme: ResolvedTheme, scale: Float): List<Zone> = listOf(
        Zone(cool(theme), x = 0.16f, y = 0.06f, radius = 0.78f, alpha = ColdAlpha * scale),
        Zone(cool(theme), x = 0.92f, y = 0.22f, radius = 0.52f, alpha = ColdAlpha * 0.7f * scale),
        Zone(warm(theme), x = 0.86f, y = 0.86f, radius = 0.80f, alpha = WarmAlpha * scale),
        Zone(warm(theme), x = 0.08f, y = 0.70f, radius = 0.58f, alpha = WarmAlpha * 0.8f * scale),
    )

    /** La part de lumière neutre quand « Halo » est éteint — l'opacité de la zone ([NeutralAlpha]). */
    /**
     * Les zones **neutres** : « Halo » éteint, rien de coloré n'est peint — mais la page garde une
     * lumière (en haut) et un creux (en bas), donc une vitre posée dessus a encore quelque chose à
     * échantillonner.
     *
     * La lumière est une **fraction de blanc** et le creux une **fraction de noir**, sans passer par
     * le barreau du thème : mesuré, sur « Standard » clair le barreau de remplissage vaut la page au
     * niveau de gris près, et la zone ne peignait **rien** — la matière neutre redevenait exactement
     * la page. Les deux fractions sont plus généreuses que le halo coloré, et pour une raison
     * arithmétique : la zone ne déplace le fond que de `alpha × fraction × écart`, et sous 0,5 niveau
     * de gris elle disparaît dans l'arrondi du rendu. 25 % × 25 % déplace le fond d'un niveau plein,
     * ce qui se voit — et reste **un gris** : aucune couleur n'est ajoutée à l'écran.
     */
    fun neutralZones(theme: ResolvedTheme): List<Zone> = neutralZones(theme, scale(theme))

    /** Les zones neutres à une échelle d'opacité donnée. */
    fun neutralZones(theme: ResolvedTheme, scale: Float): List<Zone> = listOf(
        Zone(
            colour = lerp(theme.pageColor, Color.White, NeutralLift),
            x = 0.20f,
            y = 0.05f,
            radius = 0.80f,
            alpha = NeutralAlpha * scale,
        ),
        Zone(
            colour = lerp(theme.pageColor, Color.Black, NeutralLift),
            x = 0.80f,
            y = 0.92f,
            radius = 0.75f,
            alpha = NeutralAlpha * scale,
        ),
    )

    /** Les zones à peindre pour ce thème-là : colorées quand le halo est allumé, neutres sinon. */
    fun zones(theme: ResolvedTheme): List<Zone> = zones(theme, scale(theme))

    /** Les zones d'une apparence, à une échelle d'opacité donnée. Pure, donc éprouvable seule. */
    fun zones(theme: ResolvedTheme, scale: Float): List<Zone> =
        if (theme.halo) colouredZones(theme, scale) else neutralZones(theme, scale)

    // -------------------------------------------------------------------------------------------
    // L'échelle des opacités — c'est la mesure qui décide, pas le goût
    // -------------------------------------------------------------------------------------------

    /** Le pas du balayage de l'échelle : 2 % — plus fin que ce que l'œil distingue sur un voile. */
    const val ScaleStep: Float = 0.02f

    /** Le plancher de l'échelle : sous ce point, la matière ne serait plus une matière. */
    const val MinScale: Float = 0.25f

    /** La marge au-dessus du seuil d'encre, comme partout ailleurs ([LegibilityMargin]). */
    const val InkMargin: Float = 0.15f

    private val scaleCache = java.util.concurrent.ConcurrentHashMap<String, Float>()

    /**
     * **L'échelle d'opacité des zones, mesurée sur l'encre du thème.**
     *
     * Le défaut qu'elle répare est mesuré : sur `terminal` sombre (accent vert vif) et `amber` sombre
     * (accent doré), la lumière de la matière remontait le fond au point que l'**encre blanche du
     * thème** tombait à 4,34:1 puis 3,75:1 — sous le seuil du texte. Une matière qui mange la
     * lisibilité n'est pas une matière, c'est un défaut de plus.
     *
     * La règle est donc la même que celle de la teinte d'une vitre : on part de l'opacité nominative,
     * et on la **baisse par pas de 2 %** jusqu'à ce que l'encre du thème (texte **et** encre
     * secondaire) tienne son seuil **au pire cas** de la matière — le point le plus clair et le point
     * le plus creux, chaque zone au centre de sa propre lumière. Le résultat est **mémorisé** (une
     * fonction pure du thème) : le balayage tourne une fois par thème, pas une fois par trame.
     *
     * C'est cette échelle que le rapport imprime : sur les douze autres cas elle vaut **1,00**, donc
     * la matière est exactement celle de la spec ; elle ne bouge que là où l'encre l'exige.
     */
    fun scale(theme: ResolvedTheme): Float = scaleCache.getOrPut(scaleKey(theme)) { scan(theme) }

    /** La clé de la mémoïsation : tout ce dont l'échelle dépend. */
    private fun scaleKey(theme: ResolvedTheme): String =
        "${theme.theme.id}|${theme.dark}|${theme.halo}|${theme.accent.value}|${theme.pageColor.value}"

    /** Le balayage, pur : la plus grande échelle (par pas) dont l'encre tient au pire cas. */
    private fun scan(theme: ResolvedTheme): Float {
        val target = OnyxContrast.TEXT_MIN + InkMargin
        var scale = 1f
        while (scale >= MinScale) {
            val worst = worstInkRatio(theme, scale)
            if (worst >= target) return scale
            scale -= ScaleStep
        }
        return MinScale
    }

    /** Le pire rapport d'encre du thème sur la matière, à une échelle donnée. */
    private fun worstInkRatio(theme: ResolvedTheme, scale: Float): Float {
        val extremes = layered(theme, zones(theme, scale))
        val inks = listOf(theme.textColor, theme.secondaryText)
        return inks.minOf { ink -> extremes.minOf { OnyxContrast.ratio(ink, it) } }
    }

    /**
     * **Le pire cas clair** : la page telle que la matière la laisse au point le plus clair, après
     * **toutes** les zones qui se recouvrent là. C'est la fonction que le test interroge, et c'est
     * exactement ce que [matterBackdrop] peint — l'ordre du repli est celui du dessin.
     */
    fun brightest(theme: ResolvedTheme): Color =
        layered(theme, zones(theme)).maxByOrNull { OnyxContrast.luminance(it) } ?: theme.pageColor

    /** **Le pire cas sombre** : idem, au point le plus creux. */
    fun darkest(theme: ResolvedTheme): Color =
        layered(theme, zones(theme)).minByOrNull { OnyxContrast.luminance(it) } ?: theme.pageColor

    /**
     * Toutes les compositions d'un écran : pour chaque zone, la page recouverte **par les zones dont
     * le disque atteint son centre**, dans l'ordre où elles se peignent. C'est ce qu'un pixel porte
     * réellement là où une zone est au plus fort — quatre zones aux quatre coins ne se recouvrent
     * jamais toutes au même point, et les empiler quand même donnerait un pire cas **faux**, plus
     * sombre que l'écran : le chiffre ne serait plus une mesure.
     */
    private fun layered(theme: ResolvedTheme, zones: List<Zone>): List<Color> {
        return zones.map { target ->
            var under = theme.pageColor
            zones.forEach { zone ->
                if (zone === target || reaches(zone, target)) under = zone.over(under)
            }
            under
        }
    }

    /** Vrai si le disque de [zone] atteint le **centre** de [point]. Pure, donc éprouvable seule. */
    fun reaches(zone: Zone, point: Zone): Boolean =
        hypot(zone.x - point.x, zone.y - point.y) <= zone.radius

    /**
     * La distance, en fraction de largeur, entre le centre d'une zone et le coin le plus proche de
     * la surface : au-delà, la zone ne peint rien d'utile. Pure, donc éprouvable seule.
     */
    fun covers(zone: Zone): Boolean = hypot(zone.x - 0.5f, zone.y - 0.5f) <= zone.radius + 0.5f
}

/**
 * **La matière, peinte en fond de page.** À poser **après** la couleur de page et **avant** le
 * contenu : la page reste l'aplat du thème, la matière en est la lumière, et tout ce qui se dessine
 * au-dessus (dont le halo, et les vitres) la recouvre.
 *
 * Les zones sont des **gradients radiaux à deux arrêts** — la couleur de la zone, puis le
 * transparent —, si bien que leur bord est une transition : c'est cette transition que la réfraction
 * courbe et que le flou étale. Rien n'est animé : la matière est la même à chaque trame, donc elle
 * ne coûte rien au budget du verre.
 */
fun Modifier.matterBackdrop(theme: ResolvedTheme): Modifier = drawWithContent {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return@drawWithContent
    Matter.zones(theme).forEach { zone ->
        val centre = Offset(x = zone.x * w, y = zone.y * h)
        val radius = (zone.radius * w).coerceAtLeast(1f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(zone.colour.copy(alpha = zone.alpha), Color.Transparent),
                center = centre,
                radius = radius,
            ),
            radius = radius,
            center = centre,
        )
    }
    drawContent()
}
