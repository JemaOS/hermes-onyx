package com.onyx.app.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * **La confirmation de la maison — une surface, jamais une boîte du système.**
 *
 * Ce que le propriétaire a vu : sa capture de « Supprimer cette conversation ? » était un rectangle
 * gris translucide **centré** (la boîte du système que Compose dessine quand on empile des rangées
 * de texte), avec des **lignes de séparation internes** au-dessus des actions, un titre centré sur
 * deux lignes, puis **deux rangées de texte nu** — « Supprimer » en rouge, « Annuler » en noir.
 * « C'est inacceptable par rapport au design de la maquette », et il a raison : rien de tout cela
 * n'appartient à la maison (aucun arrondi de la maison, aucun verre, aucune rangée, aucune valeur).
 *
 * Ce panneau est donc la contrepartie de la maison, aux valeurs du socle, sans rien inventer :
 *
 *  - **coins arrondis 28** ([OnyxSocle.RadiusPanel] — le rayon de la famille des panneaux, celui de
 *    la plaque flottante et de la feuille, `.plaque{border-radius:28px}` de `hcss.py:227`) ;
 *  - **verre** : la vitre de la table des panneaux ([FrostSurface.SHEET]) — un fond de barreau haut,
 *    la finition commune ([frostedSheet] : lumière de tête, ombre de pied, liseré, reflet de tranche)
 *    et le flou de **fenêtre** ([DialogWindowBlur], puisque le panneau vit dans sa propre fenêtre) ;
 *  - **l'arête claire en haut** : c'est la *lumière de tête* de [drawGlassFinish], la même que sur
 *    toutes les vitres — elle n'est pas redessinée ici, elle vient du même code ;
 *  - **marges 16** : la marge d'écran du socle ([OnyxSocle.ScreenMargin]), à gauche comme à droite,
 *    une seule valeur ;
 *  - **le titre** : le grand titre du socle (30 sp, `.dsp` — la paire **30/26** de l'échelle
 *    typographique, le grand titre retenu pour ce panneau), **aligné à gauche** comme l'en-tête de
 *    la feuille de la maison — jamais un titre centré de boîte de dialogue ;
 *  - **le corps** : le corps du socle (15 sp, `.mtx2`) ;
 *  - **les actions dans nos formes** : deux pastilles **pleines largeur**, hauteur minimale de
 *    cible 48 dp ([OnyxSocle.TouchTarget]), posées à **16 dp du bord** du panneau
 *    ([OnyxReadyGeometry.Inset]) et séparées de **16 dp** ([OnyxReadyGeometry.ActionGap]) —
 *    c'est la règle que le propriétaire a nommée : « les boutons collent trop au bord, ça devrait
 *    être espacé ».
 *
 * **Le ton destructeur — un seul, et mesuré.** L'action qui ne se rattrape pas porte **l'encre de
 * danger de la maison** ([contentDangerInk]), jamais un aplat rouge : c'est la demande explicite du
 * propriétaire, et c'est ce que font déjà la pilule `Danger` d'un contenu et la rangée destructive
 * d'une feuille. Le fond de la pastille est **le gris de capsule** de l'échelle des surfaces — la
 * même marche que la pastille de l'onglet actif — de sorte que la cible **se voie** dans les deux
 * apparences (1,13:1 en clair, 1,23:1 en sombre contre ce panneau). Le libellé tient **≥ 4,5:1**
 * sur son fond, dans les deux apparences : c'est [OnyxConfirmTone], et `OnyxConfirmVerrouTest` le
 * mesure. La sortie (« Annuler ») prend la **même** pastille et l'encre du texte : deux formes
 * jumelles, une seule teinte de danger pour toute l'app.
 *
 * Ce qui **disparaît** et ne doit pas revenir : aucune ligne de séparation interne héritée du système
 * ([OnyxRowDivider] n'est pas appelé), aucun texte nu centré comme seule action.
 *
 * L'ordre des actions est celui de la maison : l'action destructrice d'abord, la sortie ensuite.
 *
 * @param onDestructive l'action dangereuse (elle referme l'appelant) ;
 * @param onCancel la sortie — le toucher à côté et le retour matériel y mènent aussi.
 */
@Composable
fun OnyxConfirm(
    title: String,
    destructiveLabel: String,
    cancelLabel: String,
    onDestructive: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    /**
     * L'action principale est-elle **destructrice** ? Vrai par défaut : elle prend l'encre de danger
     * de la maison. Faux — pour un geste qui ne défait rien mais qui **crée** (brancher une
     * conversation) — elle prend la **pilule d'accent**, comme l'action principale d'un contenu.
     * Le reste du panneau ne change pas : c'est toujours la même surface.
     */
    destructive: Boolean = true,
    /** Les textes venant du serveur passent avec `false`, sans traduction. */
    localizeMessage: Boolean = true,
) {
    val theme = LocalOnyxTheme.current

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DialogWindowBlur(FrostSurface.SHEET)
        ConfirmScrim {
            OnyxConfirmSurface(modifier = modifier) {
                OnyxConfirmHeading(title)
                OnyxConfirmBody(message, localizeMessage)
                OnyxConfirmActions {
                    val (ground, ink) = if (destructive) {
                        OnyxConfirmTone.destructiveGround(theme) to OnyxConfirmTone.destructiveInk(theme)
                    } else {
                        OnyxConfirmTone.primaryGround(theme) to OnyxConfirmTone.primaryInk(theme)
                    }
                    OnyxConfirmAction(
                        label = destructiveLabel,
                        ground = ground,
                        ink = ink,
                        onClick = onDestructive,
                    )
                    OnyxConfirmAction(
                        label = cancelLabel,
                        ground = OnyxConfirmTone.cancelGround(theme),
                        ink = OnyxConfirmTone.cancelInk(theme),
                        onClick = onCancel,
                    )
                }
            }
        }
    }
}

/**
 * **L'arrivée d'un panneau de confirmation** : la même famille de mouvement que les feuilles
 * ([MotionFamily.Open]) — un panneau qui s'ouvre est **un** mouvement. Sous « Réduire les
 * animations », l'état de départ est l'état d'arrivée : rien ne bouge.
 *
 * L'arrivée vit **dans le panneau** ([OnyxConfirmSurface]) : la confirmation de la maison et
 * l'alerte héritée arrivent donc exactement pareil, sans que le code soit écrit deux fois.
 */
@Composable
internal fun confirmArrival(): Pair<Float, Float> {
    val reduceMotion = LocalReduceMotion.current
    var arrived by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(reduceMotion) { if (!reduceMotion) arrived = true }
    val scale by animateFloatAsState(
        targetValue = if (arrived) 1f else 0.92f,
        animationSpec = MotionFamily.Open.animated(),
        label = "confirmScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (arrived) 1f else 0f,
        animationSpec = MotionFamily.Open.animated(),
        label = "confirmFade",
    )
    return scale to alpha
}

/** Le voile sous un panneau de confirmation : la page assombrie, et la marge d'écran du socle. */
@Composable
internal fun ConfirmScrim(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = OnyxSocle.ScreenMargin),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * **Le panneau de la maison — une seule surface.** C'est lui que portent la confirmation
 * ([OnyxConfirm]) et l'alerte héritée ([OnyxAlert]) : il n'y a donc **pas** deux panneaux dans l'app,
 * et une correction de rayon, de vitre ou de marge vaut pour toutes les confirmations d'un coup.
 */
@Composable
internal fun OnyxConfirmSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val (scale, alpha) = confirmArrival()
    val shape = RoundedCornerShape(OnyxSocle.RadiusPanel)
    val fill = OnyxSurfaces.surfaceHigh(theme)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = ConfirmMaxWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            // Opaque d'abord (le barreau des surfaces qui flottent), la vitre ensuite : le
            // panneau ne laisse rien traverser et garde la lumière du verre par-dessus. Le chemin
            // est **une seule fonction** ([housePanelSurface]), partagée avec les dialogues et les
            // feuilles — un panneau qui l'oublie est un panneau troué.
            .housePanelSurface(shape, fill)
            // **Le retrait : 16 dp.** Le panneau est la seule source de cette marge — les actions
            // ne portent aucune marge propre, donc aucune ne peut « coller au bord ».
            .padding(OnyxReadyGeometry.Inset),
        verticalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
        content = content,
    )
}

/** Le titre d'une confirmation : le grand titre du socle, **aligné à gauche**. */
@Composable
internal fun OnyxConfirmHeading(title: String) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = title,
        style = socleDisplayStyle(theme).copy(
            color = theme.textColor,
            textAlign = TextAlign.Start,
        ),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { heading() },
    )
}

/** Le corps d'une confirmation : le corps du socle (15 sp), jamais un texte centré de boîte système. */
@Composable
internal fun OnyxConfirmBody(message: String?, localize: Boolean) {
    val theme = LocalOnyxTheme.current
    message?.takeIf { it.isNotBlank() }?.let {
        BasicText(
            text = it,
            localize = localize,
            style = socleBodyStyle(theme).copy(color = theme.secondaryText),
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * **Le groupe des actions** : les pastilles sont empilées et séparées de
 * [OnyxReadyGeometry.ActionGap] (16 dp) — jamais collées l'une à l'autre.
 */
@Composable
internal fun OnyxConfirmActions(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OnyxReadyGeometry.ActionGap), content = content)
}

/**
 * Une action d'une confirmation : une **pastille pleine de la maison** — capsule, cible de 48 dp,
 * libellé centré. Aucune ligne de séparation ne la précède : c'est sa forme qui la détache, pas un
 * filet du système.
 */
@Composable
internal fun OnyxConfirmAction(
    label: String,
    ground: Color,
    ink: Color,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = OnyxReadyGeometry.Target)
            .clip(RoundedCornerShape(OnyxSocle.RadiusCapsule))
            .background(ground)
            .onyxTappable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = socleBodyStyle(theme).copy(color = ink, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * **La géométrie d'une confirmation, exposée** — le retrait des actions au bord, l'écart entre
 * elles, et la hauteur de cible. Nommée ici pour que la mesure et le verrou (`OnyxConfirmVerrouTest`)
 * lisent **les mêmes** valeurs que le dessin : un chiffre annoncé dans le rapport ne peut pas
 * diverger du chiffre posé.
 */
object OnyxReadyGeometry {

    /** Le retrait d'une action au bord du panneau : **16 dp**. La règle du propriétaire. */
    val Inset: Dp = OnyxSocle.CardPadding

    /** L'écart entre deux actions empilées : **16 dp** (deux blocs distincts). */
    val ActionGap: Dp = OnyxSocle.BlockGap

    /** La hauteur minimale d'une action : **48 dp** — la cible tactile de la maison. */
    val Target: Dp = OnyxSocle.TouchTarget

    /**
     * **La règle du retrait** : une action tient la maison si son retrait au bord vaut **au moins**
     * [Inset] (16 dp). C'est la fonction que le verrou éprouve sur une valeur fautive fabriquée.
     */
    fun insetIsHouse(inset: Dp): Boolean = inset.value >= Inset.value

    /** La même règle pour l'écart entre deux actions : jamais moins de [ActionGap]. */
    fun gapIsHouse(gap: Dp): Boolean = gap.value >= ActionGap.value
}

/**
 * **Le ton destructeur de la maison — un seul, mesuré.**
 *
 * Une seule teinte pour toutes les actions destructrices de l'app : l'**encre de danger**
 * ([contentDangerInk]) posée sur le **gris de capsule** ([OnyxSurfaces.capsule]) — la même marche
 * que la pastille de l'onglet actif, qui se voit sur le panneau dans les deux apparences.
 *
 * C'est la règle du propriétaire : « le danger soit porté par l'encre du libellé, pas par un
 * aplat ». Un aplat rouge ([OnyxContrast.filled] sur l'erreur) — ce que ce panneau faisait — est
 * donc écarté : il donnait un rouge générique, et une deuxième teinte de danger à côté de celle des
 * pilules de contenu.
 */
object OnyxConfirmTone {

    /** Le fond de la pastille destructive : le gris de capsule de l'échelle des surfaces. */
    fun destructiveGround(theme: ResolvedTheme): Color = OnyxSurfaces.capsule(theme)

    /** L'encre du libellé destructeur : l'encre de danger de la maison, mesurée sur son fond. */
    fun destructiveInk(theme: ResolvedTheme): Color =
        contentDangerInk(theme, destructiveGround(theme))

    /** La sortie prend **la même** pastille : une seule forme, une seule teinte de danger. */
    fun cancelGround(theme: ResolvedTheme): Color = OnyxSurfaces.capsule(theme)

    /** L'encre de la sortie : l'encre de texte de la maison. */
    fun cancelInk(theme: ResolvedTheme): Color = theme.textColor

    /** Le fond d'une action principale qui **crée** : la pilule d'accent de la maison. */
    fun primaryGround(theme: ResolvedTheme): Color = OnyxContrast.filled(theme.accent)

    /** L'encre qui se lit le mieux sur cette pilule. */
    fun primaryInk(theme: ResolvedTheme): Color = OnyxContrast.inkOn(primaryGround(theme))

    /** Le rapport du libellé destructeur sur son fond — la valeur que le verrou relit. */
    fun destructiveRatio(theme: ResolvedTheme): Float =
        OnyxContrast.ratio(destructiveInk(theme), destructiveGround(theme))

    /** Le rapport du libellé de sortie sur son fond. */
    fun cancelRatio(theme: ResolvedTheme): Float =
        OnyxContrast.ratio(cancelInk(theme), cancelGround(theme))
}

/**
 * La largeur maximale d'une confirmation : sur un écran large (tablette, pliable déplié), le panneau
 * ne s'étire pas d'un bord à l'autre — il reste une **surface centrée** de la largeur d'un panneau de
 * la maison. Sur le téléphone, les marges de 16 font déjà le travail.
 */
internal val ConfirmMaxWidth = 380.dp
