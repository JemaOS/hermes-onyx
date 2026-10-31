package com.onyx.app.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.localization.resolveOnyxText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Une ligne de la feuille d'ajout : sa pastille, son icône, son libellé et ce qu'elle fait. */
data class AttachmentAction(
    val label: String,
    val symbol: Int,
    /**
     * Une action qui ne se rattrape pas (« Se déconnecter ») : sa vignette et son libellé prennent
     * la couleur d'alerte du thème au lieu de l'accent. Sans ce drapeau, la ligne la plus lourde
     * de la feuille se lisait comme les autres.
     *
     * Il précède [onSelect] pour que l'écriture habituelle — `AttachmentAction("Photo Library",
     * Symbols.PhotoOnRectangle) { … }` — garde sa lambda en dernier.
     */
    val destructive: Boolean = false,
    val onSelect: () -> Unit,
)

/** La hauteur d'une ligne : la cible d'un doigt, la marche de la maison pour une rangée. */
internal val SheetRowHeight = OnyxSpace.RowHeight

/** La poignée : sa largeur, sa hauteur et le rayon de ses bouts — la mesure de la source (`BottomSheet.kt:119-121`). */
private val SheetHandleWidth = 36.dp
private val SheetHandleHeight = 4.dp

/**
 * Le côté de la **vignette** d'une ligne : celui de la famille ([OnyxSpace.ThumbnailSize]).
 *
 * C'était 40 dp, avec un coin de 11 dp et une icône de 20 — trois valeurs qui ne venaient d'aucune
 * table, et qui ne tombaient pas sur celles d'une autre rangée à vignette. La vignette est
 * maintenant celle de **toutes** les rangées ([RowThumbnail]), taille et coin compris.
 */
internal val SheetBadgeSize = OnyxSpace.ThumbnailSize

/** L'écart entre la vignette et le libellé — **le jeton commun** —, et la marge intérieure. */
private val SheetLabelGap = OnyxSpace.ThumbnailGap
private val SheetRowInset = OnyxSpace.CardPadding

/**
 * Le retrait d'un **séparateur** de ligne : il part du libellé — donc **après la vignette** — et
 * s'arrête avant le bord droit, du même retrait. Une seule source ([OnyxSpace.separatorInset]) :
 * un filet qui touche les bords au milieu de filets en retrait se lit comme un oubli.
 */
private val SheetDividerInset = OnyxSpace.separatorInset(SheetBadgeSize)

/**
 * Le rayon du panneau : la famille des panneaux, sur les coins **du haut** — le panneau descend
 * jusqu'au bord bas, comme la source (`BottomSheet.kt:107`). C'est ce qui fait qu'aucun élément
 * flottant du bas ne dépasse derrière la feuille.
 */
private val SheetShape = HouseSheetShape

/** Le voile : appuyer à côté referme la feuille, comme sur la référence. */
private val SheetScrim = HouseSheetScrim

/**
 * La distance de glissement qui vaut un « non » : au-delà, la feuille se referme ; en deçà, elle
 * remonte à sa place. Un glissement plus court hésite, il ne décide pas.
 */
internal val SheetDismissDistance = 96.dp

/** La distance dont la feuille monte : elle part sous le bord de l'écran, jamais d'un coup. */
private val SheetRise = 340.dp

/** La teinte de la vignette : assez soutenue pour se voir, sous l'icône qu'elle porte. */
internal const val SheetBadgeTint = 0.14f

/**
 * L'encre de l'icône sur sa vignette — déléguée à la vignette de la maison ([thumbnailInk]) : la
 * couleur du thème, **écartée juste assez** pour tenir 3:1 sur la teinte composée. Il n'y a plus
 * deux mesures d'encre pour le même objet ; celle-ci est celle de [RowThumbnail].
 */
internal fun sheetBadgeFill(theme: ResolvedTheme, hue: Color = theme.accent): Color {
    // **Mesuré sur le fond RÉEL de la feuille** ([houseSheetFill], celui que le panneau peint —
    // barreau des surfaces qui flottent, écarté de la page) : mesurer sur le barreau de carte
    // faisait diverger la teinte de la surface qu'elle couvre, et sur une feuille claire, blanche,
    // une pastille mesurée sur un autre blanc ne se distingue plus.
    val ground = houseSheetFill(theme)
    val pastille = OnyxContrast.over(hue.copy(alpha = SheetBadgeTint), ground)
    // Sous un accent pâle, la teinte au maximum ne se distingue presque pas du panneau (moins de
    // 1,05:1 sur deux thèmes clairs) : la pastille prend alors la teinte de l'encre du texte, qui
    // se voit toujours. L'icône, elle, reste la même famille — celle que la mesure autorise.
    return if (OnyxContrast.ratio(pastille, ground) >= TinteVisible) {
        pastille
    } else {
        OnyxContrast.over(theme.textColor.copy(alpha = SheetBadgeTint), ground)
    }
}

/** Sous ce rapport, une teinte ne se distingue plus du panneau : ce n'est plus une pastille. */
internal const val TinteVisible = 1.05f

/**
 * L'encre de l'icône sur sa pastille : l'accent du thème, **assombri (ou éclairci) juste assez**
 * pour tenir 3:1 sur la teinte de sa pastille.
 *
 * Le défaut mesuré : un bleu d'accent posé sur une pastille teintée du même bleu descend à 2,7:1
 * dans les thèmes clairs, et l'icône y passait à l'encre du texte — bleue en sombre, noire en
 * clair, alors que la référence garde ses pastilles **et** ses icônes dans la couleur du thème. Le
 * compromis inverse (éclaircir la pastille jusqu'à ce que le bleu passe) la rendait invisible
 * (1,02:1 sur le panneau en « standard ») : c'est donc l'encre qui bouge, du plus petit pas qui
 * suffit, et la pastille garde sa teinte. Si même l'accent ajusté ne suffit pas, l'encre du texte
 * prend le relais.
 */
internal fun sheetBadgeInk(theme: ResolvedTheme, hue: Color = theme.accent): Color {
    val pastille = sheetBadgeFill(theme, hue)
    val accent = hue
    if (OnyxContrast.ratio(accent, pastille) >= OnyxContrast.ICON_MIN) return accent
    val vers = if (OnyxContrast.luminance(accent) >= OnyxContrast.luminance(pastille)) Color.Black else Color.White
    var atteint = 0f
    var insuffisant = 0.8f
    repeat(20) {
        val milieu = (atteint + insuffisant) / 2f
        if (OnyxContrast.ratio(lerp(accent, vers, milieu), pastille) >= OnyxContrast.ICON_MIN) {
            insuffisant = milieu
        } else {
            atteint = milieu
        }
    }
    val encre = lerp(accent, vers, insuffisant)
    return if (OnyxContrast.ratio(encre, pastille) >= OnyxContrast.ICON_MIN) encre else theme.textColor
}

/**
 * L'encre du sous-titre : l'encre du texte, atténuée **le plus loin possible sans passer sous
 * 4,5:1** sur le panneau (`OnyxContrast.dimmed`). Le libellé secondaire du système, posé tel quel,
 * tombe sous le seuil dans plusieurs thèmes ; ici la valeur est mesurée.
 */
internal fun sheetSubtitleInk(theme: ResolvedTheme): Color =
    OnyxContrast.dimmed(theme.textColor, houseSheetFill(theme), OnyxContrast.TEXT_MIN)

/**
 * La feuille qui monte du bas, pour joindre quelque chose à un message.
 *
 * Le défaut d'origine : le « + » du compositeur ouvrait une **carte flottante au milieu de
 * l'écran** (`OnyxMenu`), quatre lignes à plat, sans titre — la référence, elle, ouvre une feuille
 * ancrée en bas. Celle-ci a donc son panneau collé au bord, ses coins arrondis, sa **poignée**, son
 * titre et son sous-titre, et une ligne par entrée avec sa **pastille carrée arrondie teintée**.
 *
 * Elle se ferme de trois façons, comme la référence : **appui à côté** (le voile), **glissement
 * vers le bas** au-delà de [SheetDismissDistance], et **retour matériel** — c'est la `Dialog` de
 * Compose qui reçoit le geste de retour du système et appelle [onDismiss], un seul chemin pour les
 * trois. Le geste de retour depuis les bords de l'écran reste celui du système, non capté ici.
 *
 * Sous « Réduire les animations » ([LocalReduceMotion]), la feuille est **figée** : elle est là
 * d'emblée, sans montée, et le retour après un glissement avorté est instantané ([reducedSpec]).
 */
@Composable
fun AttachmentSheet(
    title: String,
    subtitle: String?,
    actions: List<AttachmentAction>,
    onDismiss: () -> Unit,
    /**
     * Une ligne d'état **mesurée**, posée entre le sous-titre et les lignes — la feuille de compte
     * du volet y met le voyant de la passerelle et son compte à rebours. Vide par défaut : la
     * feuille d'ajout ne change pas d'un pixel.
     */
    status: (@Composable () -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Le flou vient de la fenêtre du système (ce qui passe derrière), le voile prend le relais
        // quand il n'est pas disponible : même recette que la feuille d'actions.
        DialogWindowBlur(FrostSurface.SHEET)
        AttachmentSheetOverlay(
            title = title,
            subtitle = subtitle,
            status = status,
            actions = actions,
            onDismiss = onDismiss,
            theme = theme,
        )
    }
}

/** Le voile, puis le panneau ancré en bas. */
@Composable
private fun AttachmentSheetOverlay(
    title: String,
    subtitle: String?,
    status: (@Composable () -> Unit)?,
    actions: List<AttachmentAction>,
    onDismiss: () -> Unit,
    theme: ResolvedTheme,
) {
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val riseDistance = with(density) { SheetRise.toPx() }
    val dismissDistance = with(density) { SheetDismissDistance.toPx() }

    // La montée : elle part de la distance de montée et rejoint 0. Sous Reduce Motion, elle part
    // déjà à 0 — la feuille est là, figée, sans une seule image animée.
    val rise = remember { Animatable(if (reduceMotion) 0f else riseDistance) }
    // Le glissement de la personne, à part de la montée : les deux ne se marchent pas dessus.
    val drag = remember { Animatable(0f) }
    LaunchedEffect(riseDistance, reduceMotion) {
        if (reduceMotion) rise.snapTo(0f) else rise.animateTo(0f, sheetRiseSpec(reduceMotion))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SheetScrim)
            // L'appui **hors du panneau** referme la feuille (le voile est derrière, pas autour).
            .onyxTappable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, (rise.value + drag.value).roundToInt()) }
                .fillMaxWidth()
                // Le panneau ne transmet pas l'appui au voile : appuyer entre deux lignes, ou sur
                // le titre, ne referme rien.
                .onyxTappable(onClick = {})
                // **Opaque d'abord, la vitre ensuite** : le texte de la page derrière ne traverse
                // plus la feuille (le défaut mesuré sur la feuille de langue), et la lumière du
                // verre reste par-dessus. Le panneau descend jusqu'au bord : rien ne dépasse.
                //
                // La teinte vient de **[houseSheetFill]** — la MÊME source que la feuille d'actions
                // (`OnyxActionSheet`) et que la feuille de compte : une seule fonction décide de la
                // surface d'une feuille, donc une correction de barreau, de marche ou d'apparence
                // vaut pour les trois. Peindre ici `OnyxSurfaces.surfaceHigh` en direct laissait
                // cette feuille en **1,070:1** sur la page claire, et la rendait blanche sur blanc.
                //
                // **Une seule surface**, et c'est [housePanelSurface] qui la pose : l'ombre du
                // socle sous la face, la teinte opaque du bord au bord, et **rien par-dessus** —
                // la vitre qui suivait peignait une seconde surface dans la première.
                .housePanelSurface(SheetShape, houseSheetFill(theme))
                .navigationBarsPadding()
                // Le glissement vers le bas : la feuille suit le doigt, puis décide.
                .pointerInput(dismissDistance, onDismiss) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, amount ->
                            scope.launch { drag.snapTo(maxOf(0f, drag.value + amount)) }
                        },
                        onDragEnd = {
                            if (drag.value > dismissDistance) {
                                onDismiss()
                            } else {
                                scope.launch { drag.animateTo(0f, sheetReturnSpec(reduceMotion)) }
                            }
                        },
                        onDragCancel = {
                            scope.launch { drag.animateTo(0f, sheetReturnSpec(reduceMotion)) }
                        },
                    )
                },
        ) {
            SheetHandle()
            SheetHeader(title = title, subtitle = subtitle, theme = theme)
            status?.invoke()
            // **La zone des rangées défile** — même défaut, même correction que [HouseSheet] : une
            // feuille plus haute que la fenêtre **coupe** ses derniers enfants au lieu de les
            // rendre atteignables. Ici, c'est la dernière rangée (et la fermeture d'une feuille de
            // compte) qui était rognée au bord bas.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                actions.forEachIndexed { index, action ->
                // Un filet entre deux lignes, **en retrait des DEUX bords** : il commence après la
                // vignette et s'arrête au rembourrage de carte du bord droit.
                //
                // Le défaut mesuré : ce filet ne passait que son **inset de départ**
                // (`OnyxRowDivider(inset = SheetDividerInset)`), et le paramètre `endInset` de la
                // maison vaut `inset` par défaut — le filet s'arrêtait donc à **68 dp du bord
                // droit**, mesuré sur la capture du propriétaire (le trait finissait 80 px avant
                // le bord du panneau, là où la rangée continue). Une liste dont chaque filet
                // s'arrête à un endroit différent de celui du voisin se lit comme un oubli.
                if (index > 0) OnyxRowDivider(inset = SheetDividerInset, endInset = OnyxSpace.CardPadding)
                // **La cascade** : chaque ligne monte d'un demi-pas, décalée de [Feedback.StaggerMs]
                // par rapport à la précédente (famille [MotionFamily.Row], plafonnée à
                // [Feedback.StaggerMaxMs]). Sept lignes qui se posaient d'un bloc — c'est ce que le
                // propriétaire appelle « pas chiadé ». Sous Reduce Motion : rien, la feuille est là.
                Box(modifier = Modifier.rowArrival(index)) {
                    SheetActionRow(action = action, theme = theme)
                }
                }
            }
            // Un dernier vide : la feuille ne colle pas ses lignes au bord du bas.
            Box(modifier = Modifier.height(OnyxSpace.BlockGap))
        }
    }
}

/** La poignée : le trait court et arrondi du haut, la marque d'une feuille qu'on peut tirer. */
@Composable
private fun SheetHandle() {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = OnyxSpace.Half, bottom = OnyxSpace.Snug),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = SheetHandleWidth, height = SheetHandleHeight)
                // Le filet **fort** de la maison : c'est celui de la source pour sa poignée
                // (`BottomSheet.kt:120` : `strokeStrong`), et le même que celui de [HouseSheet].
                .background(OnyxSurfaces.lineStrong(theme), RoundedCornerShape(SheetHandleHeight / 2)),
        )
    }
}

/** Le titre de la feuille, et ses limites en sous-titre — **le gabarit unique** ([HouseSheetHeader]). */
@Composable
private fun SheetHeader(title: String, subtitle: String?, theme: ResolvedTheme) {
    HouseSheetHeader(title = title, subtitle = subtitle, localizeSubtitle = false)
}

/** Une ligne : la pastille teintée, l'icône, le libellé — et toute la largeur pour la toucher. */
@Composable
private fun SheetActionRow(action: AttachmentAction, theme: ResolvedTheme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onyxTappable(onClick = action.onSelect)
            .defaultMinSize(minHeight = SheetRowHeight)
            .padding(horizontal = SheetRowInset, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SheetLabelGap),
    ) {
        // **La vignette neutre de la maison** : le gris de la famille des tuiles, l'encre secondaire
        // mesurée dessus. C'est le changement que le propriétaire a demandé — « chaque rangée a sa
        // tuile colorée » devient « chaque rangée a la tuile de la maison ».
        //
        // LOT anatomie-des-contenus : elle est neutre **partout**, y compris sur une ligne qui ne se
        // rattrape pas. La tuile rouge de « Se déconnecter » était « exactement la couleur décorative
        // qu'il ne veut plus » : le danger est porté par **l'encre du libellé** (et par la rangée au
        // moment de l'appui), jamais par un aplat.
        RowThumbnail(
            symbol = action.symbol,
            neutral = true,
            // **La teinte de la vignette se mesure sur le fond RÉEL de la feuille.** Sans ce
            // paramètre, la vignette prend le gris nominal de la famille — et sur une feuille
            // elle-même presque blanche en clair, ce gris vaut **#F1F1F5 sur #FFFFFF = 1,127:1** :
            // un carré blanc au glyphe noir, exactement ce que le propriétaire a montré. La mesure
            // de la capture le confirme (tuile relevée `#F1F0F5`). [houseSheetTileTint] écarte la
            // teinte du panneau de la marche d'une surface qui flotte (**1,256:1**) — c'est la même
            // fonction que la rangée de [HouseSheet], donc les deux feuilles ne peuvent plus
            // diverger.
            neutralTint = houseSheetTileTint(theme),
            contentDescription = action.label,
        )
        // Le libellé se replie **sur deux lignes** au lieu d'être coupé : « Réessayer la connexion
        // maintenant » perdait ses derniers mots à police 1,3× (défaut relevé sur la capture). La
        // coupe reste par **mots entiers** quand deux lignes ne suffisent pas.
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val resolved = resolveOnyxText(action.label)
            val available = with(density) { maxWidth.toPx() }
            val fitted = remember(resolved, available) {
                fitWords(
                    text = resolved,
                    measure = { line -> measurer.measure(AnnotatedString(line), theme.body).size.width.toFloat() },
                    available = available,
                    maxLines = 2,
                )
            }
            BasicText(
                text = fitted,
                localize = false,
                style = theme.body.copy(
                    // Le danger est porté par **l'encre du libellé**, mesurée sur le fond de la
                    // feuille ([contentDangerInk]) — la règle de la maison, et la même que la
                    // rangée de [HouseSheet]. Un rouge de thème posé tel quel tombait à 3,74:1
                    // sur les pastilles sombres.
                    color = if (action.destructive) contentDangerInk(theme, houseSheetFill(theme)) else theme.textColor,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * La montée de la feuille : **la famille [MotionFamily.Open]** — une courbe et une durée pour
 * toutes les feuilles de l'app, ici comme ailleurs. C'était 280 ms `easeOut` écrit à la main ; c'est
 * maintenant un ressort court (390 ms perçus, rebond discret 0,12) qui **pose** la feuille au lieu
 * de la laisser s'arrêter net, et la même spec vaut pour le retour après un glissement avorté : une
 * famille, une courbe, une durée. Sous « Réduire les animations », `snap()` — la feuille est là,
 * sans une image animée.
 */
private fun sheetRiseSpec(reduceMotion: Boolean): FiniteAnimationSpec<Float> =
    MotionFamily.Open.spec(reduceMotion)

/** Le retour après un glissement avorté : **la famille [MotionFamily.Dismiss]** — la sortie d'un
 * panneau a sa propre courbe et sa propre durée, distinctes de l'entrée (une sortie se sent plus
 * courte qu'une entrée, et deux temps qui ne se répondent pas se lisent comme un à-coup). Sous
 * « Réduire les animations », `snap()` : le retour ne retarde rien.
 */
private fun sheetReturnSpec(reduceMotion: Boolean): FiniteAnimationSpec<Float> =
    MotionFamily.Dismiss.spec(reduceMotion)
