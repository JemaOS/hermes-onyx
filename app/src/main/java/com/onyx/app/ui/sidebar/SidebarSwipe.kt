package com.onyx.app.ui.sidebar

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.FlatDetachmentMin
import com.onyx.app.ui.design.FlatSegmentPillElevation
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.flatContrastingTint
import com.onyx.app.ui.design.flatWellColor
import com.onyx.app.ui.design.sidebarPaneColor
import com.onyx.app.ui.design.sidebarRowSurface
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import kotlin.math.roundToInt

/**
 * **Le glissement d'une rangée de conversation, en deux directions** — la pilule d'action, la
 * rangée-carte, et la **zone morte du bord** qui fait cohabiter le geste avec l'ouverture du volet.
 *
 * Ce fichier ne porte que ce qui se **mesure** : les teintes de la pilule (dérivées du thème, jamais
 * choisies à l'œil), la surface de la rangée-carte, et l'arithmétique de la zone morte. Le geste
 * lui-même et son dessin sont dans le volet (`ChatSidebar.SwipeableConversationRow`), qui n'appelle
 * que ce qui est déclaré ici.
 *
 * ## La pilule est **claire**, jamais colorée
 *
 * Les pavés d'avant étaient pleins et colorés — l'accent pour épingler, un gris pour archiver, le
 * rouge du thème pour supprimer. La référence ne fait pas ça : ses actions se lisent comme **une
 * pilule claire** — un gris très doux au clair, un blanc voilé en sombre — avec son icône et son
 * libellé dedans. Aucune couleur saturée, et **pas de rouge** : la suppression n'est pas dans le
 * geste (elle vit dans l'appui long, avec sa confirmation).
 *
 * Les deux teintes sont **dérivées** : le gris des creux de la maison ([flatWellColor]) quand la
 * page est claire, du blanc composé sur la page quand elle est sombre — puis ramenées au seuil de
 * détachement des surfaces ([FlatDetachmentMin], 1,20:1) pour que la pilule se lise comme une
 * surface et pas comme un hasard. L'encre est **celle qui se lit le mieux dessus** ([OnyxContrast.inkOn]) :
 * du presque-noir au clair, du blanc en sombre. Le seuil du libellé est celui du texte de la maison,
 * 4,5:1 ([SIDEBAR_SWIPE_PILL_INK_MIN]), et il est mesuré thème par thème par `SidebarSwipeTest`.
 *
 * ## La zone morte du bord
 *
 * Le geste vers la **droite** est celui qui ouvre le volet depuis le bord de l'écran. Une rangée qui
 * le revendiquerait partout prendrait le doigt du système et **plus rien n'ouvrirait le volet** —
 * c'est la raison pour laquelle le geste était resté à une seule direction.
 *
 * La sortie n'est pas d'interdire la droite : c'est de laisser une **zone morte** au bord
 * ([sidebarSwipeDeadZonePx]) — la bande que le système réserve à son geste de retour
 * ([WindowInsets.systemGestures]) **plus** la marge de la maison ([SidebarSwipeEdgeMargin]). Un doigt
 * posé là n'est jamais revendiqué par la rangée : il reste au geste de bord, qui ouvre (ou referme)
 * le volet. Au-delà, la rangée prend le geste et épingle. La zone est **mesurée à l'appareil** et
 * journalisée ([SIDEBAR_SWIPE_LOG]) pour que le chiffre du rapport soit celui du téléphone.
 */

/**
 * Le seuil d'encre d'un libellé de pilule : 4,5:1, comme tout texte de l'app
 * ([OnyxContrast.TEXT_MIN]).
 */
internal const val SIDEBAR_SWIPE_PILL_INK_MIN: Float = OnyxContrast.TEXT_MIN

/** L'étiquette de journal de la zone morte : c'est elle qui rend la mesure lisible à l'appareil. */
internal const val SIDEBAR_SWIPE_LOG: String = "OnyxSwipe"

/**
 * En sombre, la part de blanc composée sur la page pour faire la pilule : assez pour qu'elle se
 * lise **claire** sur une page presque noire, jamais assez pour devenir un aplat blanc.
 */
internal const val SIDEBAR_SWIPE_DARK_PILL_VEIL: Float = 0.22f

/**
 * Le plancher de détachement **de la pilule du geste** : 1,20:1.
 *
 * C'est la valeur de [GlassDetachmentMin] — et elle reste celle-ci, alors que le seuil général des
 * plaques ([FlatDetachmentMin]) est passé à 1,10 avec la palette : une **vitre** qui voile un fond
 * doit s'en détacher de 1,20 pour se lire ([FlatLight] le dit), et la pilule est exactement cela —
 * une surface **flottante** posée sur le panneau, pas une tuile posée dans un creux. Le seuil est
 * donc nommé ici plutôt qu'emprunté, pour que les deux familles ne dépendent plus l'une de l'autre.
 */
internal const val SidebarSwipePillDetachmentMin: Float = 1.20f

/** Le rayon de la **rangée-carte** — celui de la rangée pendant le glissement, et rien d'autre. */
internal val SidebarSwipeCorner: Dp = 16.dp

/**
 * L'ombre de la rangée-carte, en clair : **la seule** chose qui la détache, puisque la carte et le
 * panneau valent la même teinte quasi blanche. Elle est légère — une rangée qui se soulève, pas une
 * carte qui flotte.
 */
internal val SidebarSwipeCardElevation: Dp = 4.dp

/**
 * L'air de la pilule avec le bord du panneau — **et** la marge ajoutée à la zone morte du geste.
 *
 * Les deux mesures sont la même, et c'est volontaire : la rangée ne revendique jamais un doigt posé
 * dans la bande d'air qu'elle garde elle-même avec le bord. Une seule valeur, donc les deux ne
 * peuvent pas diverger.
 */
internal val SidebarSwipeMargin: Dp = 12.dp

/**
 * Le seuil d'un geste **franc**, en dp : c'est lui qui applique l'action, et la rangée revient en
 * place. Il est **plus court que la pilule** (~110 dp) : le geste part avant d'avoir tout découvert —
 * c'est le geste franc de la référence, pas une course à parcourir en entier.
 */
internal val SidebarSwipeThreshold: Dp = 56.dp

/**
 * **La dominance d'axe exigée** : pour que la rangée revendique un geste, sa course horizontale doit
 * dépasser **1,5 fois** sa course verticale.
 *
 * C'est le correctif du défaut signalé : « quand je défile vers le bas ou haut j'ai la barre archiver
 * ou pin qui apparaît ; normalement c'est que le slide vers droite ou gauche horizontal ». La
 * classification ne regardait que `x`, si bien qu'un défilement **vertical** au doigt qui dérive
 * latéralement finissait en épinglage. Un geste oblique à 30° de la verticale a un rapport d'axes de
 * ~0,58 — il tombe donc du côté du défilement, jamais du côté de la pilule.
 */
internal const val SidebarSwipeAxisDominance: Float = 1.5f

/**
 * **La distance minimale d'un geste**, en dp, **avant que la rangée ne révèle quoi que ce soit**.
 *
 * Un geste n'est pas franc à son premier pixel : il faut que l'axe dominant ait réellement parcouru
 * cette distance pour que la rangée se mette à suivre le doigt. Plus longue que le seuil système
 * (`touchSlop`, ~8 dp), elle laisse un appui vertical commencer à défiler sans que rien ne s'ouvre ;
 * plus courte que [SidebarSwipeThreshold], elle n'empêche pas le geste franc d'aboutir.
 */
internal val SidebarSwipeAxisMin: Dp = 24.dp

/**
 * **L'axe d'un geste de rangée, tranché UNE fois.** Un geste est soit un glissement horizontal (la
 * rangée le revendique et découvre sa pilule), soit un défilement : jamais les deux.
 */
internal enum class SidebarSwipeAxis {
    /** Rien n'a encore dépassé le seuil système : l'axe n'est pas tranché, rien n'est révélé. */
    UNDECIDED,

    /** Franchement horizontal : la rangée revendique le geste. */
    HORIZONTAL,

    /** Vertical, ou oblique non franchement horizontal : le geste reste au **défilement**. */
    VERTICAL,
}

/**
 * **La classification d'un geste par son axe dominant** : pure, donc mesurée sans appareil.
 *
 * L'axe est tranché **dès que l'une des deux courses dépasse le seuil système** — c'est ce qui rend
 * l'arbitrage honnête : la rangée consomme le doigt sur l'événement **même** où le glissement du
 * volet le revendiquerait, et l'un des deux gagne franchement. Un seuil de classement plus long
 * laisserait le geste du volet démarrer d'abord, et la rangée n'arriverait jamais à le reprendre.
 *
 * Au-delà du seuil, l'axe est **horizontal** si la course horizontale dépasse la verticale de
 * [SidebarSwipeAxisDominance], **vertical** sinon. C'est ce qui rend le **geste oblique** sûr : un
 * glissement à 30° de la verticale a une course verticale 1,7 fois plus longue que l'horizontale —
 * il est classé **vertical**, donc il fait défiler la liste.
 *
 * Ce que la rangée **révèle**, en revanche, est une autre règle : voir [sidebarSwipeRevealPx].
 */
internal fun sidebarSwipeAxis(
    dx: Float,
    dy: Float,
    slop: Float,
): SidebarSwipeAxis {
    val across = kotlin.math.abs(dx)
    val along = kotlin.math.abs(dy)
    if (across < slop && along < slop) return SidebarSwipeAxis.UNDECIDED
    return if (across > along * SidebarSwipeAxisDominance) {
        SidebarSwipeAxis.HORIZONTAL
    } else {
        SidebarSwipeAxis.VERTICAL
    }
}

/**
 * **La pose de la rangée**, en pixels : **rien** avant [minTravel], puis la course au-delà.
 *
 * C'est la seconde condition de la règle : le geste franchement horizontal ne commence à **découvrir**
 * sa pilule qu'une fois la distance minimale parcourue. La rangée ne saute donc pas à la position du
 * doigt — elle part de zéro, exactement là où la distance minimale est atteinte, puis suit.
 */
internal fun sidebarSwipeRevealPx(dx: Float, minTravel: Float): Float {
    val reach = kotlin.math.abs(dx)
    if (reach <= minTravel) return 0f
    return if (dx < 0f) -(reach - minTravel) else reach - minTravel
}

/** Ce que la rangée suit le doigt **au-delà** de la pilule découverte : un geste peut aller trop loin. */
internal val SidebarSwipeSlack: Dp = 48.dp

/**
 * La course plancher, **avant** que la pilule n'ait été mesurée. La pilule suit la police, donc la
 * course aussi ; ce plancher est ce qui borne le tout premier glissement, quand la largeur n'est pas
 * encore connue.
 */
internal val SidebarSwipeFallbackReveal: Dp = 132.dp

/**
 * La forme de la pilule : **un galet**, entièrement arrondi. C'est ce qui la fait lire comme une
 * pilule et non comme un pavé, quelle que soit sa hauteur (elle suit la police).
 */
internal val SidebarSwipePillShape: Shape = RoundedCornerShape(percent = 50)

/**
 * **La teinte d'une pilule d'action** : le gris des creux au clair, du blanc voilé en sombre — dans
 * les deux cas ramenée au seuil de détachement du panneau, pour qu'elle se lise comme une surface.
 *
 * Une couleur de thème (l'accent, le rouge) ne peut pas passer par ici : il n'y a **qu'une** entrée,
 * et elle ne prend pas de couleur.
 */
internal fun sidebarSwipePillColor(theme: ResolvedTheme): Color {
    val pane = sidebarPaneColor(theme)
    val base = if (theme.dark) {
        // Le blanc du système est translucide : composer avant de mesurer, sinon la teinte annoncée
        // n'est pas celle de l'écran.
        OnyxContrast.over(Color.White.copy(alpha = SIDEBAR_SWIPE_DARK_PILL_VEIL), pane)
    } else {
        flatWellColor(theme, pane)
    }
    return flatContrastingTint(base, pane, SidebarSwipePillDetachmentMin)
}

/** L'encre d'une pilule : **celle qui se lit le mieux** sur sa teinte — presque-noir ou blanc. */
internal fun sidebarSwipePillInk(theme: ResolvedTheme): Color =
    OnyxContrast.inkOn(sidebarSwipePillColor(theme))

/** Le rapport d'encre d'une pilule, mesuré sur sa teinte réelle : il doit tenir 4,5:1. */
internal fun sidebarSwipePillInkContrast(theme: ResolvedTheme): Float =
    OnyxContrast.ratio(sidebarSwipePillInk(theme), sidebarSwipePillColor(theme))

/** Le détachement de la pilule de son panneau : une surface qui ne se détache pas n'est pas une surface. */
internal fun sidebarSwipePillDetachment(theme: ResolvedTheme): Float =
    OnyxContrast.ratio(sidebarSwipePillColor(theme), sidebarPaneColor(theme))

/**
 * **La zone morte du bord gauche**, en pixels : la bande que le système réserve à son geste de
 * retour, **plus** la marge de la maison. Pure, donc mesurée par un test.
 *
 * Les deux termes sont bornés à zéro : sur un appareil en navigation à trois boutons, la bande du
 * système est nulle, et il reste la marge.
 */
internal fun sidebarSwipeDeadZonePx(systemGesturesLeftPx: Float, marginPx: Float): Float =
    systemGesturesLeftPx.coerceAtLeast(0f) + marginPx.coerceAtLeast(0f)

/**
 * La zone morte de cet appareil, lue **une fois** par la liste des conversations, et **journalisée**.
 *
 * Le journal n'est pas un confort : c'est la seule façon de prouver à l'appareil que la bande
 * mesurée est bien celle que la rangée respecte — le chiffre du rapport est donc celui du téléphone,
 * pas celui d'un commentaire.
 */
@Composable
internal fun rememberSidebarSwipeDeadZone(): Float {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val edgePx = with(density) { WindowInsets.systemGestures.getLeft(this, layoutDirection).toFloat() }
    val marginPx = with(density) { SidebarSwipeMargin.toPx() }
    val deadZone = sidebarSwipeDeadZonePx(edgePx, marginPx)
    LaunchedEffect(deadZone) {
        Log.i(
            SIDEBAR_SWIPE_LOG,
            "zone morte du bord : ${deadZone.roundToInt()} px " +
                "(bande du système ${edgePx.roundToInt()} px + marge ${SidebarSwipeMargin.value} dp)",
        )
    }
    return deadZone
}

/**
 * **La rangée-carte** : la surface du panneau, ses coins, et l'ombre douce de la maison. C'est ce qui
 * la détache de la liste pendant le geste — au clair par l'ombre seule (la carte et le panneau valent
 * la même teinte), en sombre par la vitre de rangée, **la même** que celle des autres listes du
 * volet ([sidebarRowSurface]).
 *
 * Un seul point d'appel, donc la carte ne peut pas se mettre à diverger du panneau selon l'apparence.
 */
@Composable
internal fun Modifier.sidebarSwipeCard(): Modifier {
    val theme = LocalOnyxTheme.current
    val shape = RoundedCornerShape(SidebarSwipeCorner)
    return this
        // L'ombre est posée **avant** le rognage : un `graphicsLayer` d'ombre dessine hors de ses
        // bornes, donc c'est cet ordre qui la laisse dépasser de la carte.
        .then(if (theme.dark) Modifier else Modifier.shadow(SidebarSwipeCardElevation, shape, clip = false))
        .clip(shape)
        .background(sidebarPaneColor(theme), shape)
        .then(Modifier.sidebarRowSurface(shape))
}

/**
 * **La surface d'une pilule d'action** : sa teinte ([sidebarSwipePillColor]), ses coins de galet
 * ([SidebarSwipePillShape]) et la petite ombre qui la détache de la rangée qu'elle borde — au clair
 * seulement, comme toutes les surfaces de la maison (en sombre, la vitre porte déjà la sienne).
 */
@Composable
internal fun Modifier.sidebarSwipePill(): Modifier {
    val theme = LocalOnyxTheme.current
    return this
        .then(
            if (theme.dark) Modifier
            else Modifier.shadow(FlatSegmentPillElevation, SidebarSwipePillShape, clip = false),
        )
        .clip(SidebarSwipePillShape)
        .background(sidebarSwipePillColor(theme), SidebarSwipePillShape)
}
