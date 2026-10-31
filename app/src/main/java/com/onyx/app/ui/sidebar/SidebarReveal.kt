package com.onyx.app.ui.sidebar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.onyx.app.ui.design.veilColour
import com.onyx.app.ui.theme.ReferencePalette
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * **Ce qui est révélé autour du volet** — le seul endroit du volet qui pouvait être noir, et il
 * l'était.
 *
 * En surimpression, le volet ne couvre pas toute la fenêtre : le fil est **poussé** de la largeur
 * du volet (`SidebarScaffold`, l'offset du contenu), et ce qui reste dessous n'était pas la page —
 * c'était le **fond de fenêtre** (`onyx_background`, `#FF0E0E11`) que la poussée découvre. Avec un
 * voile écrit en dur, identique dans les deux apparences, cela donne, mesuré sur la capture du
 * propriétaire (téléphone 411 dp, **clair**) :
 *
 * | endroit | avant |
 * | --- | --- |
 * | coin haut-droit du volet | `(8,8,10)` — le fond de fenêtre voilé |
 * | coin bas-droit du volet | `(8,8,10)` |
 * | bord droit (la bande révélée) | `(153,153,153)` — `Color.Black` à 0,4 sur la page blanche |
 * | surface du volet | `(255,255,255)` |
 *
 * Deux choses se corrigent ici, et **une seule** des deux options de la maison est retenue :
 * *ce qui est révélé autour n'est plus sombre*. Le tracé du volet — largeur en surimpression,
 * coins arrondis du bord extérieur, hauteur pleine — ne bouge pas : c'est le langage du geste, et il
 * n'expose plus rien de sombre dès lors que le fond révélé est le bon.
 *
 * 1. [drawerGround] : le fond révélé est **la page**, jamais le fond de fenêtre.
 * 2. [drawerVeil] : le voile **suit l'apparence**. En clair, la page tirée d'un cran vers le noir
 *    ([DRAWER_VEIL_LIGHT_TINT]) posée à [DRAWER_VEIL_LIGHT_ALPHA] — un voile clair, lisible comme
 *    un voile, et qui laisse la page claire. En sombre, le voile de la maison ([veilColour]).
 */
internal object SidebarReveal {

    /**
     * Clair : de combien la page est tirée vers le noir pour faire le voile. C'est l'échelle des
     * surfaces de la maison (`InlineSurfaces` tire de 0,04 à 0,06), poussée juste assez pour que le
     * voile se lise **comme un voile** sur la page.
     */
    const val LIGHT_TINT = 0.10f

    /** Clair : l'opacité du voile. Au-delà, la surface révélée se lit comme une bande grise. */
    const val LIGHT_ALPHA = 0.55f

    /**
     * Sombre : l'opacité du voile — celle de la feuille et de l'alerte de la maison
     * (`AttachmentSheet`), qui n'a pas de raison de changer : en sombre, ce qui est révélé doit
     * s'enfoncer sous la page, et il le fait déjà.
     */
    const val DARK_ALPHA = 0.40f
}

/** Le fond révélé sous et autour du volet : **la page**, jamais le fond de fenêtre. */
internal fun drawerGround(theme: ResolvedTheme): Color = theme.pageColor

/** La teinte du voile, sans son opacité : la page, un cran plus bas. */
internal fun drawerVeilTint(theme: ResolvedTheme): Color =
    if (theme.dark) veilColour(theme.pageColor, 1f, true)
    else lerp(theme.pageColor, Color.Black, SidebarReveal.LIGHT_TINT)

/** L'opacité du voile à l'avancement donné : il suit le doigt comme le glissement du volet. */
internal fun drawerVeilAlpha(theme: ResolvedTheme, progress: Float): Float {
    val base = if (theme.dark) SidebarReveal.DARK_ALPHA else SidebarReveal.LIGHT_ALPHA
    return base * progress.coerceIn(0f, 1f)
}

/**
 * Le voile de la surimpression, prêt à peindre : sa teinte suit l'apparence, son opacité suit
 * l'avancement (0 volet fermé, 1 volet ouvert).
 */
internal fun drawerVeil(theme: ResolvedTheme, progress: Float): Color =
    drawerVeilTint(theme).copy(alpha = drawerVeilAlpha(theme, progress))

/**
 * Le fond de fenêtre de l'app (`onyx_background`, `app/src/main/res/values/colors.xml`) : ce qui
 * paraissait sous les coins du volet. Il est nommé ici pour que le test le compare — un défaut de
 * couleur ne se corrige pas « à l'œil », il se mesure.
 */
internal val WindowBackground = ReferencePalette.DarkPage
