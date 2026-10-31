package com.onyx.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * **LE gabarit de la feuille du bas — un seul, pour toutes les feuilles de l'app.**
 *
 * Pourquoi ce fichier. Le propriétaire a relevé deux feuilles qui « vivent dans un thème différent » :
 * la feuille de **connexion** (titre centré, tuile rouge sur « Se déconnecter ») et la feuille de
 * **langue** (titre centré, options en lignes de texte centrées, « Annuler » dans un panneau détaché
 * sous la feuille, panneau translucide où le texte de la page derrière devenait lisible). Chacune
 * avait sa forme ; le défaut ne se corrige pas feuille par feuille, il se corrige **une fois**.
 *
 * Le gabarit est celui de la source (`shared/designsystem/.../components/BottomSheet.kt:100-197`) :
 *
 *  - le panneau **descend jusqu'au bord bas** (la source : `navigationBarsPadding` seulement, aucune
 *    marge) et ne prend des coins arrondis qu'**en haut** (`BottomSheet.kt:107`) — c'est ce qui fait
 *    qu'aucun élément flottant du bas ne dépasse derrière la feuille ;
 *  - une **poignée** de 36 × 4 dp au filet fort (`BottomSheet.kt:119-121`) ;
 *  - un **grand titre aligné à gauche** (22 sp SemiBold, `BottomSheet.kt:139-144`), jamais centré,
 *    avec son sous-titre de 13 sp en encre tertiaire (`BottomSheet.kt:147-153`) — et le sous-titre
 *    **ne se coupe pas** : il porte du sens (« Onyx suit la langue du téléphone… ») ;
 *  - des **rangées du gabarit maison** : une vignette grise arrondie ([RowThumbnail]), le libellé, le
 *    contrôle à droite, un **filet en retrait** entre deux rangées (`CapabilitiesPage.kt:426-441`,
 *    `ScheduledPage.kt:295-330`) ;
 *  - l'action de fermeture **à l'intérieur du panneau**, en dernière rangée — pas dans un panneau
 *    détaché sous la feuille ;
 *  - un fond **opaque** : le panneau est posé **sur** un aplat du barreau « surface haute » et la
 *    vitre vient par-dessus. Un voile translucide à 82 % laissait le texte de la page derrière
 *    traverser la feuille et rendait les deux illisibles (défaut mesuré, voir le rapport).
 */

/** La poignée : la largeur, la hauteur et la teinte de la source (`BottomSheet.kt:119-121`). */
private val HouseSheetHandleWidth = 36.dp
private val HouseSheetHandleHeight = 4.dp

/** Le rayon du panneau : la famille des panneaux, sur les coins **du haut** seulement. */
val HouseSheetShape = RoundedCornerShape(
    topStart = OnyxSpace.PanelCorner,
    topEnd = OnyxSpace.PanelCorner,
)

/**
 * Le voile de la feuille : la valeur de la source (`BottomSheet.kt:104` :
 * `Color.Black.copy(alpha = 0.45f)`).
 *
 * Plus dense que l'ancien 0,40 : sous une feuille opaque, ce voile est ce qui empêche de **lire** ce
 * qui reste visible ailleurs à l'écran — dont la pilule flottante du bas.
 */
val HouseSheetScrim = Color.Black.copy(alpha = 0.55f)

/** La teinte du panneau : opaque, et du barreau des surfaces qui flottent. */
internal fun houseSheetFill(theme: ResolvedTheme): Color =
    // **Le barreau des surfaces qui flottent, tel quel.** En sombre il se détache de la page par la
    // luminance (`#26262B` sur `#0A0A0C` = 1,314:1). En clair il **ne peut pas** : la page est déjà
    // à L = 0,93, et la doctrine de la maison est explicite — « en clair, une surface flottante ne
    // se détache pas par la luminance, elle se détache par le filet et l'ombre ». La feuille garde
    // donc le blanc de la référence, et c'est le voile de 55 % plus l'ombre de la recette qui la
    // posent sur la page. Écarter la teinte de la page à 1,25:1 donnait `#DEDEDE` : une feuille
    // grise, plus sale que la page qu'elle recouvre. Ce qui ne doit **jamais** arriver, en revanche,
    // c'est qu'une feuille peigne **deux** surfaces : c'est le verrou `une seule surface`.
    OnyxSurfaces.surfaceHigh(theme)

/**
 * **La surface d'une feuille, rendue publique** — pour les modules qui doivent mesurer une encre
 * *contre elle* (l'état de la passerelle, dans `AccountSheet`, mesure son voyant et son motif sur le
 * panneau qu'il couvre).
 *
 * Sans cette porte, un écran du module `app` n'a d'autre choix que de recopier le barreau
 * (`OnyxSurfaces.surfaceHigh`) — et c'est exactement ainsi qu'une encre se met à être mesurée sur
 * une couleur que la feuille ne peint pas. Une seule fonction décide de la surface d'une feuille ;
 * elle est ici, et les deux noms rendent la même valeur.
 */
fun sheetSurface(theme: ResolvedTheme): Color = houseSheetFill(theme)

/**
 * **La teinte de la vignette d'une rangée de feuille.**
 *
 * Le gris neutre de la maison ([neutralThumbnailTint]) **écarté du fond de la feuille** juste assez
 * pour tenir la marche d'une surface qui **se détache** ([OnyxSurfaces.FloatStepMin], 1,25:1 — le
 * seuil nommé des surfaces qui flottent, ici appliqué à ce qui doit se **voir** sur la feuille).
 *
 * Pourquoi ce seuil et pas celui d'une surface ordinaire. Le défaut est mesuré : en clair, la
 * feuille est **blanche** (`#FFFFFF`) et la vignette était `#F1F1F5` — **1,127:1**, sous la marche
 * d'une surface qui flotte : une tuile blanche sur une feuille blanche, donc **invisible**. Ce n'est
 * pas un écart de thème, c'est un écart trop faible, et il se mesure.
 */
internal fun houseSheetTileTint(theme: ResolvedTheme): Color =
    OnyxContrast.distinctOn(neutralThumbnailTint(theme), houseSheetFill(theme), OnyxSurfaces.FloatStepMin)

/**
 * **Le fond d'une action de la maison posée dans une feuille** — la pastille de « Annuler » et de
 * toute action non destructrice.
 *
 * Le gris de gorge du thème ([ResolvedTheme.fill]) **écarté du fond de la feuille** : en clair, le
 * gris `#F1F1F5` sur la feuille blanche ne se voyait pas — la pastille était une bande du même blanc
 * que la feuille. Une action de la maison se lit **à sa forme** avant de porter son mot, donc avec
 * l'écart d'une surface qui se détache ([OnyxSurfaces.FloatStepMin]).
 */
internal fun houseSheetActionFill(theme: ResolvedTheme): Color =
    OnyxContrast.distinctOn(theme.fill, houseSheetFill(theme), OnyxSurfaces.FloatStepMin)

/**
 * **La surface d'un panneau posé sur la page — feuille *ou* dialogue : un seul chemin, et UNE
 * seule surface.**
 *
 * Le panneau est **opaque du bord au bord** : la teinte du barreau des surfaces qui flottent, sa
 * forme, et **rien d'autre**. Rien ne traverse (la page derrière ne se lit plus), et rien ne se
 * superpose non plus.
 *
 * **Pourquoi la vitre a été retirée, et non gardée par-dessus.** La version précédente appelait
 * `clip` + `background(teinte opaque)` **puis** `frostedSheet`, en gardant « la finition du verre »
 * (lumière de tête, ombre de pied). Mesure du défaut (dialogue « Nouveau salon », apparence claire,
 * 1,0×, emulator-5620) : sur 108 points d'échantillonnage (9 colonnes × 12 lignes) répartis sur le
 * panneau, l'écart de luminance allait de **201 à 255** — une bande plus claire en haut, un corps
 * plus gris, un halo aux quatre bords : la **seconde surface** que le propriétaire voit (« la
 * fenêtre n'est pas blanche de partout, c'est pas uniforme »). La même chaîne **sans** `frostedSheet`
 * rend **255 sur les 105 points de la face** (les 3 autres tombant sur les pilules d'action) :
 * l'écart tombe à **0**. Un fond opaque n'a rien à voiler, et une vitre par-dessus un fond opaque
 * n'est pas une vitre : c'est un second panneau plus petit, posé dans le premier.
 *
 * **La profondeur ne se perd pas** : elle vient de l'ombre du socle ([socleShadow]), dessinée
 * **sous** la face (comme la feuille du bas, qui la pose de la même façon) — jamais d'un aplat
 * peint par-dessus.
 *
 * @param fill la teinte **opaque** du panneau, calculée par l'appelant **avant** d'entrer dans la
 *   fenêtre du dialogue : une `Dialog` ouvre une sous-composition qui peut retomber sur le thème
 *   par défaut, donc le fond ne se lit pas ici (`LocalOnyxTheme`). Une feuille y met
 *   [houseSheetFill], un dialogue `OnyxSurfaces.surfaceHigh(theme)` — et les deux restent la même
 *   fonction de surface.
 */
@Composable
fun Modifier.housePanelSurface(shape: Shape, fill: Color): Modifier {
    val theme = LocalOnyxTheme.current
    return this
        // L'ombre du socle, **sous** la face : c'est elle qui pose le panneau sur la page.
        .socleShadow(OnyxSocle.ShadowFar, OnyxSocle.RadiusPanel, theme.dark)
        .clip(shape)
        .background(fill, shape)
}

/**
 * **Une feuille du bas au gabarit de la maison.** Elle monte du bas, s'arrête au bord, porte sa
 * poignée, son grand titre à gauche, son sous-titre, ses rangées et sa fermeture — et rien d'autre.
 *
 * @param onDismiss la fermeture, par appui à côté. Une feuille fermée autrement doit passer par la
 *   même fonction : le retour matériel de la `Dialog` et le glissement du doigt la rappellent.
 * @param closeLabel la fermeture, **dans** la feuille. `null` quand la feuille porte déjà son geste
 *   de retour (une feuille de liste, comme les pièces jointes) — auquel cas la dernière rangée ferme.
 */
@Composable
fun HouseSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    closeLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DialogWindowBlur(FrostSurface.SHEET)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HouseSheetScrim)
                .onyxTappable(onClick = onDismiss),
        ) {
            Column(
                modifier = modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onyxTappable(onClick = {})
                    // **Opaque d'abord, la vitre ensuite** : le panneau ne laisse plus rien
                    // traverser, et garde la lumière du verre par-dessus.
                    .clip(HouseSheetShape)
                    // L'ombre du socle et la face viennent de la **même** fonction que les
                    // dialogues ([housePanelSurface]) : une surface, du bord au bord, et rien
                    // par-dessus.
                    .housePanelSurface(HouseSheetShape, houseSheetFill(theme))
                    .navigationBarsPadding(),
            ) {
                HouseSheetHandle()
                HouseSheetHeader(title = title, subtitle = subtitle)
                // **La zone des rangées défile.** Le défaut mesuré sur la capture du propriétaire :
                // le bas de la feuille « Langue » est **tranché** — la pastille de fermeture est
                // rognée par le bord bas. Une `Column` qui ne tient pas dans la fenêtre ne rétrécit
                // pas : elle **coupe** ses derniers enfants. Les rangées prennent donc la place qui
                // reste, et défilent quand il n'y en a pas assez ; la poignée, le titre et la
                // fermeture gardent leur place. Le glissement de la feuille continue de fonctionner
                // depuis l'en-tête (c'est là qu'on l'attrape), la liste défile sous le doigt.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    content()
                }
                if (closeLabel != null) {
                    // **Aucun filet devant la fermeture.** Un filet ne se pose qu'**entre deux
                    // rangées** ; la fermeture, elle, se détache par **sa forme** — sa pastille,
                    // comme la confirmation de la maison. Le filet qui la précédait était un reste
                    // de la liste bord à bord.
                    HouseSheetCloseRow(label = closeLabel, onDismiss = onDismiss)
                }
                // La feuille ne colle pas sa dernière rangée au bord.
                Box(Modifier.size(OnyxSpace.BlockGap))
            }
        }
    }
}

/** La poignée : le trait court du haut, la marque d'une feuille qu'on peut tirer. */
@Composable
fun HouseSheetHandle() {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = OnyxSpace.Half, bottom = OnyxSpace.Snug),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = HouseSheetHandleWidth, height = HouseSheetHandleHeight)
                .background(OnyxSurfaces.lineStrong(theme), RoundedCornerShape(HouseSheetHandleHeight / 2)),
        )
    }
}

/**
 * **L'en-tête d'une feuille** : le grand titre **aligné à gauche**, puis le sous-titre.
 *
 * Deux gabarits pour un même objet — un titre centré ici, un titre à gauche là — c'est exactement ce
 * qui a fait dire au propriétaire que ses feuilles vivaient « dans un thème différent ». Il n'en
 * reste qu'un : celui de la source (`BottomSheet.kt:133-155`).
 *
 * Le sous-titre **se replie sur autant de lignes qu'il en faut** : c'est lui qui porte les phrases
 * utiles (« Onyx suit la langue du téléphone, sauf si vous en choisissez une »), et le rogner
 * enlevait une information réelle. Aucun mot n'est coupé : le texte se replie aux frontières de mots.
 */
@Composable
fun HouseSheetHeader(
    title: String?,
    subtitle: String? = null,
    localizeSubtitle: Boolean = true,
) {
    val theme = LocalOnyxTheme.current
    if (title == null && subtitle == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = OnyxSpace.BarInset, end = OnyxSpace.BarInset, top = OnyxSpace.Hair, bottom = OnyxSpace.BlockGap),
        // **L'écart d'un titre à son sous-titre est un écart de BLOC, pas de glyphe.**
        // `GlyphGap` vaut 2 dp : c'est l'écart entre deux glyphes empilés *dans* un dessin, et il
        // collait la ligne d'adresse au titre de 26 sp. Mesuré sur la capture du propriétaire
        // (« Vérification » / l'adresse) : 12 dp de blanc entre les deux encres à 1,0×, et **les
        // boîtes de ligne se touchent à 1,3×** — les ascendantes du titre passent dans la boîte du
        // sous-titre, ce qu'il a lu comme un chevauchement. La marche `Half` (8 dp) les sépare dans
        // les deux échelles sans changer la famille : c'est le même jeton que l'écart icône-libellé.
        verticalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
    ) {
        title?.let {
            BasicText(
                text = it,
                // La famille de la maison : `title2` (22 sp) — la taille exacte de la source
                // (`HermesTheme.kt:48` : `title` = 22 sp SemiBold).
                style = theme.title2.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        subtitle?.takeIf { it.isNotBlank() }?.let {
            BasicText(
                text = it,
                localize = localizeSubtitle,
                style = theme.footnote.copy(fontSize = OnyxSocle.Meta, color = sheetSubtitleInk(theme)),
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Le filet d'une rangée de feuille : en retrait **après la vignette**, et avant le bord droit. */
@Composable
fun HouseSheetDivider(withTile: Boolean = true) {
    OnyxRowDivider(
        inset = if (withTile) OnyxSpace.separatorInset(OnyxSpace.ThumbnailSize) else OnyxSpace.CardPadding,
        endInset = OnyxSpace.CardPadding,
    )
}

/**
 * **Une rangée de feuille** : la vignette grise arrondie de la maison, le libellé, et **le contrôle à
 * droite** (une coche, un interrupteur, un chevron).
 *
 * C'est le gabarit de la source (`BottomSheet.kt:163-197` : vignette 34 dp, écart 14, libellé
 * `body` Medium, min 52 dp) avec la vignette de la maison ([RowThumbnail], 44 dp) — une seule taille
 * de vignette pour toutes les rangées, décidée en un endroit.
 *
 * @param destructive l'état dangereux : porté par **l'encre du libellé**, jamais par un aplat de
 *   couleur (demande explicite du propriétaire). La vignette reste neutre.
 */
@Composable
fun HouseSheetRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    symbol: Int? = null,
    destructive: Boolean = false,
    description: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = OnyxSpace.RowHeight)
            .onyxTappable(onClick = onClick)
            .padding(horizontal = OnyxSpace.CardPadding, vertical = OnyxSpace.Half),
        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.ThumbnailGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        symbol?.let {
            // La vignette est **toujours neutre** : la grisaille de la famille des tuiles, l'icône
            // de la maison. La couleur ne porte plus un libellé.
            //
            // **Et elle se voit sur la feuille** : sa teinte est écartée du fond de la feuille
            // ([houseSheetTileTint]) — le gris de la famille sur la feuille blanche mesurait
            // 1,06:1, donc une tuile invisible.
            RowThumbnail(
                symbol = it,
                neutral = true,
                neutralTint = houseSheetTileTint(theme),
                contentDescription = null,
            )
        }
        BasicText(
            text = label,
            style = theme.body.copy(
                color = if (destructive) contentDangerInk(theme, houseSheetFill(theme)) else theme.textColor,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
        description?.let {
            BasicText(
                text = it,
                style = theme.caption.copy(
                    color = OnyxInkOn(theme.secondaryText, houseSheetFill(theme), TextInkTarget),
                ),
                maxLines = 1,
            )
        }
    }
}

/**
 * **La fermeture, dans la feuille — une action de la maison, pas un mot.**
 *
 * C'était un mot **centré** en encre d'accent, sans forme : le « Annuler » bleu au milieu d'une
 * bande grise, que le propriétaire a montré trois fois. Le défaut n'était pas la couleur, c'était
 * l'**absence de forme** : les deux autres actions de la maison (la confirmation de suppression) se
 * lisent à leur pastille pleine ; la fermeture d'une feuille fait de même.
 *
 * La pastille prend le gris de gorge de la maison ([houseSheetActionFill], mesuré contre le fond de
 * la feuille — jamais le blanc sur le blanc), sa capsule de la famille des formes
 * ([OnyxSocle.RadiusCapsule]) et sa cible tactile ([OnyxSpace.TouchTarget]). Elle est en retrait de
 * la marge d'écran ([OnyxSpace.ScreenMargin]) : ce n'est pas un filet, c'est une forme posée.
 *
 * Ce qui **disparaît** et ne doit pas revenir : aucune encre d'accent centrée comme seule action,
 * aucun filet devant la fermeture (un filet ne se pose qu'entre deux rangées).
 */
@Composable
fun HouseSheetCloseRow(label: String, onDismiss: () -> Unit) {
    val theme = LocalOnyxTheme.current
    val ground = houseSheetActionFill(theme)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSpace.ScreenMargin, vertical = OnyxSpace.Half)
            .heightIn(min = OnyxSpace.TouchTarget)
            .clip(RoundedCornerShape(OnyxSocle.RadiusCapsule))
            .background(ground)
            .onyxTappable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = theme.body.copy(
                color = OnyxInkOn(theme.textColor, ground, TextInkTarget),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** Le côté du contrôle de fin d'une rangée de feuille (une coche, un chevron). */
val HouseSheetControlGlyph: Dp = 18.dp
