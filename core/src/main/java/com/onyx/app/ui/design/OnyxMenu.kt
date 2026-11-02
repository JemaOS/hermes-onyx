package com.onyx.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Une entrée du menu « … » : son libellé, son icône, son sous-menu, et s'il est destructeur. */
data class MenuItem(
    val label: String,
    val symbol: Int? = null,
    val destructive: Boolean = false,
    val checked: Boolean? = null,
    val localize: Boolean = true,
    /**
     * Les lignes de son sous-menu (« Mute › ») : la ligne porte alors le chevron et ouvre son
     * propre panneau à côté, au lieu d'agir elle-même. Vide ou nulle, la ligne agit.
     */
    val submenu: List<MenuItem>? = null,
    /**
     * Un filet au-dessus de cette ligne. Le menu du modèle ne sépare que ses groupes : un filet
     * devant « Réordonner les bots », et rien entre deux lignes d'un même groupe.
     */
    val groupBreak: Boolean = false,
    val onSelect: () -> Unit,
)

/** La largeur du panneau d'une rangée : le menu compact du modèle, entre 250 et 290 dp. */
private val MenuWidth = 280.dp

/** Ses coins — ceux de la famille des panneaux de verre —, la hauteur d'une ligne et sa marge. */
private val MenuCorner = GlassPanelCorner
private val MenuRowHeight = OnyxSpace.RowHeight
private val MenuRowInset = OnyxSpace.CardPadding

/** L'icône d'une ligne, l'écart qui la sépare du libellé, et la taille du chevron des sous-menus. */
private val MenuIconSize = 17.dp
private val MenuIconGap = OnyxSpace.IconLabelGap
private val MenuChevronSize = 13.dp

/** L'écart entre la rangée touchée et le panneau, et la marge qu'il garde au bord de l'écran. */
private val MenuGap = OnyxSpace.Hair
private val MenuMargin = OnyxSpace.Snug

/** La marge du panneau centré (les menus qui ne sont pas ancrés à une rangée). */
private val MenuCenteredInset = 32.dp

/** Le voile : léger, il laisse voir l'écran et se ferme quand on appuie à côté du panneau. */
private val MenuScrim = Color.Black.copy(alpha = 0.18f)

/** Le fond du panneau est translucide, comme le menu du modèle posé sur la liste. */
private const val MenuSurfaceAlpha = 0.94f

/**
 * Le menu du modèle (`Menu`) : un panneau arrondi, une ligne par action, l'icône à gauche et le
 * chevron des sous-menus à droite.
 *
 * Compose n'a pas de menu qui ressemble à celui-là sans Material : celui-ci est dessiné à la main,
 * avec les icônes du thème, le même ordre (le destructeur en rouge) et un filet entre les groupes.
 *
 * [anchor] est la rangée touchée (ses limites dans la fenêtre, `boundsInWindow`) : le panneau
 * s'ouvre alors **près d'elle**, aligné sur son bord, en dessous ou au-dessus selon la place. Sans
 * ancre, il s'ouvre au centre de l'écran, comme avant.
 */
@Composable
fun OnyxMenu(
    items: List<MenuItem>,
    onDismiss: () -> Unit,
    anchor: Rect? = null,
) {
    // **La fenêtre principale, pas une fenêtre à part.** Un `Dialog` est une *autre* fenêtre : il ne
    // voit ni la source de flou Haze (posée par l'écran, dans la fenêtre principale), ni la page de
    // la lentille, ni le calque de fond. Un panneau qui y vit ne peut donc rien échantillonner — il ne
    // peut que se peindre d'un voile, et le résultat se lit comme du **lavis** : un rectangle gris
    // translucide posé sur l'écran, le contenu dessous restant lisible.
    //
    // Passé par l'hôte, le panneau est dessiné **au-dessus de la route, dans la fenêtre principale** :
    // il y lit la source de flou, courbe ce qu'il recouvre, et porte la finition de verre du reste de
    // l'app. L'ancrage ne bouge pas : il est calculé en coordonnées de fenêtre (`boundsInWindow`),
    // et les deux fenêtres ont la même.
    val host = LocalOverlayHost.current
    // Le jeton du calque : il garantit que la fermeture de CE menu ne retire pas le calque d'un
    // autre panneau qui l'aurait remplacé entre-temps (un sous-menu s'ouvrant sur un menu).
    var token by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(host, items, anchor) {
        token = host?.show {
            MenuOverlay(items = items, onDismiss = onDismiss, anchor = anchor)
        }
    }
    DisposableEffect(host) {
        onDispose { token?.let { host?.dismiss(it) } }
    }
}

/** Le voile, le panneau posé dessus, et celui d'un sous-menu ouvert. */
@Composable
private fun MenuOverlay(
    items: List<MenuItem>,
    onDismiss: () -> Unit,
    anchor: Rect?,
) {
    val theme = LocalOnyxTheme.current
    val focus = remember { FocusRequester() }
    // Le sous-menu ouvert et la ligne qui l'a ouvert : son panneau se pose à côté du premier, à la
    // hauteur de cette ligne.
    var submenu by remember { mutableStateOf<Pair<List<MenuItem>, Rect>?>(null) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    var submenuSize by remember { mutableStateOf(IntSize.Zero) }
    // Où le voile lui-même tombe : la rangée touchée est dans la fenêtre, le voile peut être
    // décalé par une barre système. On mesure, on ne devine pas.
    var placed by remember { mutableStateOf<Offset?>(null) }

    // **Le retour système referme le menu.** Dans une `Dialog`, c'était la fenêtre qui s'en
    // chargeait ; hors `Dialog` — le menu vit désormais dans la fenêtre principale, pour lire la
    // source de flou et se peindre comme du verre — il faut le dire explicitement, sinon le retour
    // système déporterait l'écran au lieu de refermer le panneau.
    BackHandler(onBack = onDismiss)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { placed = it.boundsInWindow().topLeft }
            .background(MenuScrim)
            // Un appui hors du panneau referme le menu, comme celui du modèle : sans ça le voile
            // plein écran avalait le geste et le menu restait ouvert.
            .onyxTappable(onClick = onDismiss)
            .focusRequester(focus)
            .focusable()
            // Échap referme le menu, comme au clavier sur le modèle.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
    ) {
        LaunchedEffect(Unit) { focus.requestFocus() }

        val density = LocalDensity.current
        val offset = placed ?: Offset.Zero
        // La rangée, ramenée dans le repère du voile.
        val row = anchor?.let {
            Rect(it.left - offset.x, it.top - offset.y, it.right - offset.x, it.bottom - offset.y)
        }
        val marginPx = with(density) { MenuMargin.roundToPx() }
        val gapPx = with(density) { MenuGap.roundToPx() }
        val widthPx = with(density) {
            if (row == null) {
                (constraints.maxWidth - MenuCenteredInset.roundToPx() * 2).coerceAtLeast(0)
            } else {
                min(MenuWidth.roundToPx(), (constraints.maxWidth - marginPx * 2).coerceAtLeast(0))
            }
        }
        // La hauteur du panneau avant sa première mesure : ses lignes, ses filets, sa marge.
        val heightPx = if (panelSize.height > 0) panelSize.height else estimateMenuHeight(items, density)
        val x = row
            ?.let { it.left.roundToInt().coerceIn(marginPx, max(marginPx, constraints.maxWidth - widthPx - marginPx)) }
            ?: ((constraints.maxWidth - widthPx) / 2)
        val y = row
            ?.let { r ->
                val below = r.bottom.roundToInt() + gapPx
                val above = r.top.roundToInt() - gapPx - heightPx
                val chosen = if (below + heightPx + marginPx <= constraints.maxHeight) below else above
                chosen.coerceIn(marginPx, max(marginPx, constraints.maxHeight - heightPx - marginPx))
            }
            ?: ((constraints.maxHeight - heightPx) / 2)

        // Où va un sous-menu : à droite du panneau, à gauche s'il n'y a plus de place, et à sa
        // place quand deux panneaux ne tiennent pas côte à côte (un téléphone).
        val beside = row != null && x + widthPx + gapPx + widthPx + marginPx <= constraints.maxWidth
        val leftOf = row != null && !beside && x - gapPx - widthPx >= marginPx
        val replacingPanel = submenu != null && !beside && !leftOf

        MenuPanel(
            items = items,
            theme = theme,
            modifier = Modifier
                .offset { IntOffset(x, y) }
                .width(with(density) { widthPx.toDp() })
                // Le panneau attend d'être placé : il ne se montre pas une image au mauvais endroit.
                // Il s'efface aussi quand un sous-menu prend sa place, faute de largeur.
                .alpha(if (placed == null || replacingPanel) 0f else 1f)
                .onSizeChanged { panelSize = it },
            onOpenSubmenu = { sub, bounds -> submenu = sub to bounds },
        )

        submenu?.let { (subItems, bounds) ->
            val subHeightPx = if (submenuSize.height > 0) submenuSize.height else estimateMenuHeight(subItems, density)
            val right = x + widthPx + gapPx
            // À droite du panneau quand il y a la place, à gauche sinon : le sous-menu ne sort pas
            // de l'écran. Trop étroit pour deux panneaux — un téléphone —, il prend la place du
            // panneau, comme le fait le modèle.
            val subX = when {
                beside -> right
                leftOf -> x - gapPx - widthPx
                else -> x
            }
            val subY = if (beside) {
                (bounds.top - offset.y).roundToInt()
                    .coerceIn(marginPx, max(marginPx, constraints.maxHeight - subHeightPx - marginPx))
            } else {
                y
            }
            MenuPanel(
                items = subItems,
                theme = theme,
                modifier = Modifier
                    .offset { IntOffset(subX, subY) }
                    .width(with(density) { widthPx.toDp() })
                    .onSizeChanged { submenuSize = it },
            )
        }
    }
}

/** La hauteur d'un panneau avant sa mesure : ses lignes, ses filets, et sa marge intérieure. */
private fun estimateMenuHeight(items: List<MenuItem>, density: Density): Int = with(density) {
    val rows = items.size.coerceAtLeast(1) * MenuRowHeight.roundToPx()
    val dividers = items.count { it.groupBreak } * 2
    rows + OnyxSpace.Half.roundToPx() * 2 + dividers
}

/** Le panneau : une ligne par entrée, l'une sous l'autre, un filet là où le groupe change. */
@Composable
private fun MenuPanel(
    items: List<MenuItem>,
    theme: ResolvedTheme,
    modifier: Modifier = Modifier,
    /** L'appui d'une ligne à sous-menu : son panneau s'ouvre à côté, la ligne n'agit pas. */
    onOpenSubmenu: ((List<MenuItem>, Rect) -> Unit)? = null,
) {
    Column(
        modifier = modifier
            // Le panneau lui-même n'est pas une zone de fermeture : appuyer entre deux lignes ne
            // doit rien faire (l'appui est avalé ici, pas transmis au voile).
            .onyxTappable(onClick = {})
            // **Le verre du panneau**, et non plus un voile.
            //
            // Avant, le fond était peint par `frostedBar` : un **voile plat** — aucun calque flouté,
            // aucune lentille. Le contenu dessous restait lisible (mesuré : contraste 179 → 30, les
            // arêtes du texte restaient des marches nettes) et le panneau n'avait ni lumière ni
            // arête : c'était du **lavis**, pas du verre.
            //
            // Ici le panneau prend la surface de la **table du verre** ([GlassSurface.MENU]) : le fond
            // est le contenu réel, flouté et courbé par la lentille, et la finition de la maison
            // (lumière de tête, ombre de pied, liseré, reflet de tranche) se pose par-dessus.
            //
            // **Pas de `masking`**, et c'est délibéré : le panneau ancré se pose sur une **rangée**,
            // pas sur un fil qui défile sous une plaque. Il doit **laisser voir** ce qu'il recouvre —
            // c'est le verre qu'on cherche. `masking` remonterait le voile jusqu'à 0,92 et le
            // panneau redeviendrait l'**aplat** qu'on voulait supprimer (mesuré : fond uniforme, plus
            // aucune forme derrière). La lisibilité, ici, c'est le **flou** qui la porte, pas
            // l'opacité.
            // **Le chemin de Haze, pas celui de la lentille.** Mesuré : le chemin de la lentille
            // peint une **teinte** mais n'échantillonne **rien** (la texture enregistrée rend vide) —
            // le panneau laissait alors passer le texte dessous net (corrélation 0,99 avec l'image
            // non floutée). Le chemin de Haze, lui, échantillonne vraiment (le volet lit la source à
            // `count=1`) : c'est lui qui donne le fond **flouté** du verre.
            .liquidGlass(
                GlassSurface.MENU,
                RoundedCornerShape(MenuCorner),
                lens = false,
            )
            .padding(vertical = OnyxSpace.Half),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0 && item.groupBreak) OnyxRowDivider(inset = 12.dp)
            // La cascade ([MotionFamily.Row]) : chaque ligne se pose après la précédente.
            Box(modifier = Modifier.rowArrival(index)) {
                MenuRow(item = item, theme = theme, onOpenSubmenu = onOpenSubmenu)
            }
        }
    }
}

/** Une ligne : son icône, son libellé, la coche d'une ligne cochée et le chevron d'un sous-menu. */
@Composable
private fun MenuRow(
    item: MenuItem,
    theme: ResolvedTheme,
    onOpenSubmenu: ((List<MenuItem>, Rect) -> Unit)?,
) {
    // L'endroit où la ligne est : le panneau de son sous-menu s'y accroche.
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val submenu = item.submenu

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .onyxTappable {
                if (!submenu.isNullOrEmpty() && onOpenSubmenu != null) {
                    onOpenSubmenu(submenu, bounds)
                } else {
                    item.onSelect()
                }
            }
            .defaultMinSize(minHeight = MenuRowHeight)
            .padding(horizontal = MenuRowInset, vertical = OnyxSpace.Snug),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MenuIconGap),
    ) {
        item.symbol?.let {
            OnyxIcon(
                symbol = it,
                // L'**encre du thème**, monochrome : l'icône d'une ligne de menu n'est pas un état,
                // donc elle ne porte pas l'accent — c'est la règle « une seule couleur d'icône,
                // partout ». Le **rouge** reste, et lui seul, pour l'entrée destructrice : une action
                // qui ne se rattrape pas est un état. Le libellé suit la même encre.
                tint = if (item.destructive) theme.error else theme.textColor,
                size = MenuIconSize,
            )
        }
        BasicText(
            text = item.label,
            localize = item.localize,
            style = theme.body.copy(
                color = if (item.destructive) theme.error else theme.textColor,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        item.checked?.let { checked ->
            if (checked) OnyxIcon(Symbols.Checkmark, tint = theme.accentInk, size = 14.dp)
        }
        if (!submenu.isNullOrEmpty()) {
            OnyxIcon(symbol = Symbols.ChevronRight, tint = theme.secondaryText, size = MenuChevronSize)
        }
    }
}

/** Une feuille de saisie d'une ligne (nom de dossier, chemin), comme le prompt d'la référence. */
@Composable
fun OnyxPrompt(
    title: String,
    message: String? = null,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    val theme = LocalOnyxTheme.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OnyxSurfaces.surfaceHigh(theme), RoundedCornerShape(GlassPanelCorner))
                    .border(
                        OnyxSpace.Stroke,
                        OnyxSurfaces.hairline(theme),
                        RoundedCornerShape(GlassPanelCorner),
                    )
                    .padding(OnyxSpace.CardPadding),
            ) {
                BasicText(text = title, style = theme.headline.copy(color = theme.textColor))
                message?.let {
                    BasicText(
                        text = it,
                        style = theme.footnote.copy(color = theme.secondaryText),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(theme.fill, RoundedCornerShape(OnyxSpace.ControlCorner))
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                ) {
                    OnyxTextField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = placeholder,
                        style = theme.body,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = OnyxSpace.BlockGap),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = OnyxSpace.CardPadding)
                            .onyxTappable(onClick = onDismiss),
                    ) {
                        BasicText("Cancel", style = theme.body.copy(color = theme.secondaryText))
                    }
                    BasicText(
                        text = confirmLabel,
                        style = theme.body.copy(
                            color = if (confirmEnabled) theme.accent else theme.tertiaryText,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        ),
                        modifier = Modifier.onyxTappable(enabled = confirmEnabled, onClick = onConfirm),
                    )
                }
            }
        }
    }
}
