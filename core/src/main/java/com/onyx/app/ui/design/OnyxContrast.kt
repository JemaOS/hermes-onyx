package com.onyx.app.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Les rapports de contraste WCAG, et les encres qui en découlent.
 *
 * Un écran qui pose de l'encre sur un fond doit pouvoir **le prouver**. Une couleur choisie à l'œil
 * se retrouve illisible ailleurs : l'accent d'un thème sombre sur la scène d'un autre, ou une encre
 * dérivée de l'apparence (claire) posée sur un fond qui ne l'est pas (la scène du plein écran, qui
 * reste sombre dans les deux apparences) — le défaut n'apparaît alors qu'à la capture, sur un thème
 * qu'on n'avait pas essayé. Les règles sont donc ici, **pures et mesurables** : la luminance
 * relative, le rapport, la composition alpha (indispensable — la moitié des couleurs du système sont
 * translucides), le choix de l'encre sur une couleur, et l'atténuation d'une encre « le plus loin
 * possible sans passer sous un seuil ».
 *
 * Seuils retenus (WCAG 2.1, niveau AA) : **4,5** pour du texte, **3,0** pour une icône ou un trait.
 */
object OnyxContrast {
    /** Un texte se lit à partir de 4,5:1. */
    const val TEXT_MIN = 4.5f

    /** Une icône, un trait, la bordure d'un contrôle : 3:1. */
    const val ICON_MIN = 3.0f

    /** Le presque-noir maison — l'alternative au blanc sur une couleur claire. */
    val DarkInk = Color(0xFF1B1B1D)

    /** La luminance relative WCAG d'une couleur. */
    fun luminance(color: Color): Float {
        fun linear(channel: Float): Float =
            if (channel <= 0.04045f) channel / 12.92f
            else Math.pow(((channel + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()

        return 0.2126f * linear(color.red) + 0.7152f * linear(color.green) + 0.0722f * linear(color.blue)
    }

    /** Le rapport de contraste de deux couleurs (1 … 21). */
    fun ratio(a: Color, b: Color): Float {
        val first = luminance(a)
        val second = luminance(b)
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }

    /** [foreground] posé sur un fond **opaque** : la couleur que l'écran compose réellement. */
    fun over(foreground: Color, background: Color): Color {
        val alpha = foreground.alpha
        if (alpha >= 1f) return foreground
        return Color(
            red = foreground.red * alpha + background.red * (1f - alpha),
            green = foreground.green * alpha + background.green * (1f - alpha),
            blue = foreground.blue * alpha + background.blue * (1f - alpha),
            alpha = 1f,
        )
    }

    /** Le rapport réellement lu à l'écran pour une encre translucide posée sur [background]. */
    fun ratioOver(ink: Color, background: Color): Float = ratio(over(ink, background), background)

    /** Du presque-noir ou du blanc, selon ce qui se lit le mieux sur [background] (opaque). */
    fun inkOn(background: Color, light: Color = Color.White, dark: Color = DarkInk): Color =
        if (ratio(background, light) >= ratio(background, dark)) light else dark

    /**
     * Une couleur de thème qui doit **porter un mot** : écartée du bord juste assez pour que
     * l'encre qui s'y lit le mieux tienne [minRatio].
     *
     * [inkOn] choisit l'encre ; si **aucune** des deux n'atteint le seuil, c'est le fond qui bouge
     * — assombri pour du blanc, éclairci pour du noir, du plus petit ajustement qui suffit. Le
     * « Hermes gold » en clair (4,39:1) ne bouge que de quelques pour cent : invisible à l'œil,
     * mesuré ici au centième. Un fond qu'aucun ajustement ne sauverait est rendu tel quel.
     */
    fun filled(fill: Color, minRatio: Float = TEXT_MIN, maxShift: Float = 0.45f): Color {
        val ink = inkOn(fill)
        if (ratio(fill, ink) >= minRatio) return fill
        val towards = if (ink == Color.White) Color.Black else Color.White
        val reach = maxShift.coerceIn(0f, 1f)
        if (ratio(lerp(fill, towards, reach), ink) < minRatio) return fill
        var notYet = 0f
        var enough = reach
        repeat(24) {
            val middle = (notYet + enough) / 2f
            if (ratio(lerp(fill, towards, middle), ink) >= minRatio) enough = middle else notYet = middle
        }
        return lerp(fill, towards, enough)
    }

    /**
     * [colour] **écartée** de [background] juste assez pour que le rapport atteigne [minRatio].
     *
     * Le pendant de [filled], mais mesuré contre le **fond réel** au lieu de l'encre qui se poserait
     * dessus : c'est ce qu'il faut pour un contrôle qui doit se distinguer de la page où il vit — la
     * piste d'un interrupteur allumé, par exemple. Un vert de thème prévu pour un texte sur blanc
     * tombe à 2,4:1 sur la page claire d'un thème sombre ; la couleur s'éloigne alors vers le noir
     * (ou vers le blanc) du plus petit pas qui suffit, ce qui ne se voit pas à l'œil mais se mesure.
     *
     * Une couleur qui atteint déjà [minRatio] est rendue telle quelle : rien n'est « corrigé » sans
     * raison. Le déplacement est borné par [maxShift] ; au-delà, la couleur d'origine est rendue —
     * mieux vaut une couleur de thème qu'une couleur de thème méconnaissable.
     */
    fun distinctOn(colour: Color, background: Color, minRatio: Float = ICON_MIN, maxShift: Float = 0.6f): Color {
        if (ratio(colour, background) >= minRatio) return colour
        // Des deux directions, celle qui éloigne le plus du fond : c'est celle où il y a de la place.
        val towards = if (luminance(background) > 0.5f) Color.Black else Color.White
        val reach = maxShift.coerceIn(0f, 1f)
        if (ratio(lerp(colour, towards, reach), background) < minRatio) return colour
        var notYet = 0f
        var enough = reach
        repeat(24) {
            val middle = (notYet + enough) / 2f
            if (ratio(lerp(colour, towards, middle), background) >= minRatio) enough = middle else notYet = middle
        }
        return lerp(colour, towards, enough)
    }

    /**
     * [ink] atténuée vers [background], **le plus loin possible** sans descendre sous [minRatio].
     *
     * Une icône au repos doit se distinguer d'une icône active — mais pas au prix de sa lisibilité.
     * On cherche donc la plus grande atténuation qui tient le seuil (dichotomie : le résultat ne
     * dépend pas du chemin, donc il se teste), et jamais plus de [maxDim] pour qu'un état « au
     * repos » garde une encre visible.
     *
     * Si l'encre pleine est **déjà** sous le seuil, il n'y a rien à atténuer : elle est rendue
     * telle quelle, et c'est le choix de l'encre qu'il faut revoir, pas son atténuation.
     */
    fun dimmed(ink: Color, background: Color, minRatio: Float, maxDim: Float = 0.72f): Color {
        val solid = over(ink, background)
        if (ratio(solid, background) <= minRatio) return solid
        var legible = 0f
        var illegible = maxDim.coerceIn(0f, 1f)
        repeat(24) {
            val middle = (legible + illegible) / 2f
            if (ratio(lerp(solid, background, middle), background) >= minRatio) {
                legible = middle
            } else {
                illegible = middle
            }
        }
        return lerp(solid, background, legible)
    }
}

/**
 * L'encre d'une barre de commandes, dérivée du fond sur lequel elle se pose.
 *
 * Tout ce qui est dessiné dans la barre en sort : les icônes des touches, la bordure et le fond de
 * leur case, l'invite, l'encre d'un bouton plein à la couleur du thème. La règle qui compte est
 * qu'**aucune** de ces valeurs ne soit choisie à part : une barre posée sur la scène du plein écran
 * (toujours sombre) ne peut pas tirer son encre de l'apparence, sinon un thème clair écrit du noir
 * sur du noir.
 */
@Immutable
class BarInk(surface: Color, ink: Color, accent: Color) {
    /** Le fond de la barre : la page, ou la scène du plein écran. */
    val surface: Color = surface

    /** L'encre pleine, composée sur le fond (les couleurs du système sont translucides). */
    val ink: Color = OnyxContrast.over(ink, surface)

    /** Le fond d'une case : le fond de la barre, teinté de son encre. */
    val swatch: Color = OnyxContrast.over(this.ink.copy(alpha = SWATCH), surface)

    /** La bordure fine d'une case : le trait le plus faible qui tient encore 3:1 sur le fond. */
    val edge: Color = OnyxContrast.dimmed(this.ink, surface, OnyxContrast.ICON_MIN)

    /** L'icône d'une touche active, sur le fond de sa case. */
    val icon: Color = this.ink

    /** L'icône d'une touche au repos, sur le fond de sa case : jamais sous 3:1, margé au-dessus. */
    val iconIdle: Color = OnyxContrast.dimmed(this.ink, swatch, OnyxContrast.ICON_MIN + IDLE_MARGIN)

    /** La même, posée sur la barre elle-même (l'envoi de la frappe, à côté du champ). */
    val iconIdleOnSurface: Color = OnyxContrast.dimmed(this.ink, surface, OnyxContrast.ICON_MIN + IDLE_MARGIN)

    /** Un texte secondaire de la barre (l'invite) : jamais sous 4,5:1 sur le fond de la barre. */
    val muted: Color = OnyxContrast.dimmed(this.ink, surface, OnyxContrast.TEXT_MIN)

    /** Un texte posé sur une case (la note, la pastille au repos) : jamais sous 4,5:1 non plus. */
    val mutedOnSwatch: Color = OnyxContrast.dimmed(this.ink, swatch, OnyxContrast.TEXT_MIN)

    /** La couleur du thème, telle quelle, pour ce qui doit se voir de loin. */
    val accent: Color = accent

    /** Le fond du bouton plein : la couleur du thème, ajustée juste assez pour porter son mot. */
    val accentFill: Color = OnyxContrast.filled(accent)

    /** L'encre du bouton plein : celle qui s'y lit le mieux. */
    val onAccent: Color = OnyxContrast.inkOn(accentFill)

    /**
     * La couleur du thème sur le fond de la barre **quand elle s'y voit** (3:1, la frontière d'un
     * contrôle) — sinon l'encre pleine. Une pastille de couleur invisible ne dit rien : du bleu
     * clair sur une page crème est à 1,03:1.
     */
    val signal: Color =
        if (OnyxContrast.ratio(accent, surface) >= OnyxContrast.ICON_MIN) accent else this.ink

    companion object {
        /** La teinte du fond d'une case. */
        const val SWATCH = 0.18f

        /**
         * La marge de l'encre au repos au-dessus du seuil : 3,0 est le plancher de la norme, mais
         * un trait d'icône est antialiasé — le pixel de cœur atteint bien la couleur, son voisin
         * non. Un demi-point de marge évite de livrer une valeur *théoriquement* juste et
         * *mesurée* juste au ras du seuil.
         */
        const val IDLE_MARGIN = 0.5f

        /** Une barre sur un fond noir, en blanc : le défaut d'un aperçu sans thème. */
        val Default = BarInk(Color.Black, Color.White, Color.White)
    }
}
