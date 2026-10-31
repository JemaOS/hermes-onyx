package com.onyx.app.ui.conversations

import com.onyx.app.ui.design.OnyxSpace

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onyx.app.data.model.Conversation
import com.onyx.app.ui.chat.components.ChatActionBadge
import com.onyx.app.ui.design.DialogWindowBlur
import com.onyx.app.ui.design.FrostSurface
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.OnyxSocle
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.design.sheetSurface
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.socleShadow
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.design.socleBodyStyle
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ReferencePalette
import com.onyx.app.ui.theme.ResolvedTheme
import kotlin.math.roundToInt

/**
 * La feuille des actions d'une **conversation**, ouverte par un **appui long** sur sa rangée.
 *
 * C'est **la même feuille maison** que celle des actions du fil (`ChatActionSheet`) : elle monte du
 * bas, ses coins du haut sont arrondis, une **poignée** la surmonte, on la fait **glisser** vers le
 * bas pour la fermer, un appui **à l'extérieur** la ferme, et le **retour matériel** aussi. Chaque
 * ligne est une **pastille carrée teintée** portant l'**icône** ([ChatActionBadge], le même badge que
 * la feuille du fil — même fond dilué, même encre qui tient 3,5:1), puis le **libellé** ; la cible
 * fait au moins 48 dp et les lignes sont séparées par l'air, jamais par un trait.
 *
 * Ce qu'elle ajoute à celle du fil : un **sous-titre** sous le titre (la dernière activité de la
 * conversation, « Active 7 hours ago »), et des **lignes qui se désactivent** quand l'action n'a pas
 * de sens (par exemple « Créer un fil » sur une conversation encore vide, qui n'a rien à recopier) —
 * une ligne désactivée se voit (elle s'éteint) et **n'agit pas**, elle n'est jamais muette.
 *
 * Le titre est le **nom de la conversation** ; le sous-titre dit **quand** elle a bougé pour la
 * dernière fois, comme la référence.
 */
@Composable
fun ConversationActionSheet(
    title: String,
    subtitle: String?,
    rows: List<ConversationSheetRow>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val reduceMotion = LocalReduceMotion.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    // Le glissement : la feuille suit le doigt, et repart au repos si on ne va pas assez loin.
    var drag by remember { mutableFloatStateOf(0f) }
    val thresholdPx = with(LocalDensity.current) { ConvSheetDismissThreshold.toPx() }
    val dragState = rememberDraggableState { delta -> drag = (drag + delta).coerceAtLeast(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogWindowBlur(FrostSurface.SHEET)
        // Le retour matériel referme la feuille, comme celui du fil.
        BackHandler(onBack = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ConvSheetScrim)
                // Un appui hors de la feuille la referme : le voile prend le geste, la feuille non.
                .onyxTappable(onClick = onDismiss),
        ) {
            AnimatedVisibility(
                visible = shown || reduceMotion,
                enter = if (reduceMotion) {
                    // Sous Reduce Motion, la feuille est **figée** : aucune entrée animée.
                    EnterTransition.None
                } else {
                    slideInVertically(animationSpec = tween(220), initialOffsetY = { it })
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, drag.roundToInt()) }
                        // **L'ombre de la maison**, posée pour de vrai : la feuille se détache du contenu
                        // qu'elle recouvre par la même profondeur que le gabarit ([OnyxSocle.ShadowFar]),
                        // jamais par un aplat qui se confond avec la page.
                        .socleShadow(OnyxSocle.ShadowFar, ConvSheetCorner, theme.dark)
                        .clip(ConvSheetShape)
                        // **La surface de la maison** ([sheetSurface], une seule source pour toutes
                        // les feuilles) : le barreau des surfaces qui flottent, **écarté de la page**
                        // de la marche qui se voit. `surfaceHigh` seul mesurait 1,314:1 en sombre mais
                        // **1,070:1 en clair** — une plaque blanche sur une page presque blanche,
                        // exactement le défaut relevé sur la feuille de compte.
                        .background(sheetSurface(theme))
                        // La feuille n'est pas une zone de fermeture.
                        .onyxTappable(onClick = {})
                        .navigationBarsPadding(),
                ) {
                    ConversationSheetHeader(title = title, subtitle = subtitle, onDragState = dragState, onDragStop = {
                        if (drag > thresholdPx) onDismiss() else drag = 0f
                    })

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        rows.forEachIndexed { index, row ->
                            // **Un filet entre deux rangées, en retrait des deux bords** — jamais
                            // pleine largeur. Le filet part du **libellé** (après la pastille) et
                            // s'arrête avant le bord droit, comme partout dans la maison.
                            if (index > 0) {
                                OnyxRowDivider(
                                    inset = OnyxSpace.separatorInset(ChatActionBadge.SIZE),
                                    endInset = OnyxSpace.CardPadding,
                                )
                            }
                            ConversationSheetRow(row = row)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

/** La poignée, le titre et le sous-titre : le seul endroit où la feuille se prend pour glisser. */
@Composable
private fun ConversationSheetHeader(
    title: String,
    subtitle: String?,
    onDragState: DraggableState,
    onDragStop: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(state = onDragState, orientation = Orientation.Vertical, onDragStopped = { onDragStop() }),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = ConvSheetHandleWidth, height = ConvSheetHandleHeight)
                    .background(theme.tertiaryText.copy(alpha = 0.55f), RoundedCornerShape(50)),
            )
        }
        BasicText(
            text = title,
            localize = false,
            style = theme.headline.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
        )
        subtitle?.let {
            BasicText(
                text = it,
                localize = false,
                style = theme.footnote.copy(color = theme.secondaryText),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** Une ligne : sa pastille teintée et son icône, puis son libellé. Désactivée, elle s'éteint. */
@Composable
private fun ConversationSheetRow(row: ConversationSheetRow) {
    val theme = LocalOnyxTheme.current
    val hue = conversationTintHue(row.tint)
    val surface = OnyxSurfaces.surfaceHigh(theme)
    val textColor = when {
        row.destructive -> theme.error
        else -> theme.textColor
    }
    // La tuile et son glyphe : **neutres** — le gris et l'encre de la maison — pour toute ligne qui
    // n'est pas un état. Le **rouge du danger** garde, et lui seul, sa teinte et son encre : une
    // action qui ne se rattrape pas est un état, pas une famille.
    val destructive = row.destructive
    val tile = if (destructive) ChatActionBadge.fill(hue, surface) else ChatActionBadge.fillNeutral(theme)
    val glyph = if (destructive) ChatActionBadge.ink(hue, surface) else ChatActionBadge.inkNeutral(theme)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Une ligne désactivée ne prend pas l'appui : elle ne fait rien et se voit inerte.
            .onyxTappable(enabled = row.enabled, onClick = row.onSelect)
            .heightIn(min = ConvSheetRowMinHeight)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(ChatActionBadge.SIZE)
                .background(tile, RoundedCornerShape(OnyxSpace.ControlCorner)),
            contentAlignment = Alignment.Center,
        ) {
            OnyxIcon(
                symbol = row.symbol,
                tint = glyph,
                size = ChatActionBadge.ICON_SIZE,
            )
        }
        BasicText(
            text = row.label,
            localize = row.localize,
            style = theme.body.copy(
                color = textColor,
                // Une ligne sans action reste lisible mais se dit inerte : jamais un libellé plein
                // qui laisserait croire qu'elle agit.
                fontWeight = if (row.enabled) null else FontWeight.Normal,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Le modèle de la feuille : une ligne, sa famille de teinte, et l'ordre d'affichage
// ---------------------------------------------------------------------------------------------

/** Ce qu'une ligne de la feuille d'une conversation fait — et si elle le peut. */
data class ConversationSheetRow(
    val label: String,
    val symbol: Int,
    val tint: ConversationActionTint,
    val localize: Boolean = true,
    val destructive: Boolean = false,
    /** Fausse quand l'action n'a pas de sens ici : la ligne s'éteint et n'agit pas. */
    val enabled: Boolean = true,
    val onSelect: () -> Unit = {},
)

/**
 * La famille de teinte d'une ligne de la conversation — une couleur par **famille d'action**, prise
 * dans la palette d'Onyx.
 *
 * **Ce que la famille décide a changé : elle ne commande plus la couleur.** Elle garde la **place**
 * de la ligne (l'ordre de la feuille) et son nom, mais une seule famille est un **état** et porte
 * donc une couleur : [ConversationActionTint.DANGER]. Les huit autres prennent l'**encre neutre de
 * la maison** ([conversationTintHue]) — c'est la correction du défaut relevé sur la capture : la
 * feuille d'une conversation portait **quatre** teintes (bleu/indigo, orange, gris, rouge) là où la
 * charte n'en autorise qu'**une par écran**.
 */
enum class ConversationActionTint { CONVERSATION, THREAD, PIN, ARCHIVE, INSIGHT, IDENTITY, MUTE, SCREEN, DANGER }

/**
 * La teinte d'une famille sur un thème donné : pure, donc mesurable hors composition.
 *
 * **Neutre, sauf le danger.** Le rouge du destructeur reste, et lui seul : une action irréversible
 * est un **état**, pas une famille. L'orange de l'épingle, le bleu de l'accent et le gris de
 * l'archive disparaissent — la pastille prend le gris de la maison
 * ([com.onyx.app.ui.design.ChatActionBadge.fillNeutral]) et le glyphe l'encre de la maison.
 */
@Composable
fun conversationTintHue(tint: ConversationActionTint): Color =
    conversationTintHue(tint, LocalOnyxTheme.current)

/** Le même choix de teinte, sur un thème donné — pour que le verrou le mesure sans composition. */
internal fun conversationTintHue(tint: ConversationActionTint, theme: ResolvedTheme): Color = when (tint) {
    // Le seul ÉTAT de la feuille : une action qui ne se rattrape pas.
    ConversationActionTint.DANGER -> theme.error
    // Tout le reste : l'encre du thème, donc le gris neutre de la maison. Les huit familles gardent
    // leur nom parce qu'elles commandent encore l'ordre de la feuille ([conversationSheetOrder]).
    ConversationActionTint.CONVERSATION,
    ConversationActionTint.THREAD,
    ConversationActionTint.PIN,
    ConversationActionTint.ARCHIVE,
    ConversationActionTint.INSIGHT,
    ConversationActionTint.IDENTITY,
    ConversationActionTint.MUTE,
    ConversationActionTint.SCREEN,
    -> theme.textColor
}

/** L'ordre de la feuille : les cinq actions de la conversation d'abord, puis ce qui les complète. */
fun conversationSheetOrder(label: String): Int = when (label) {
    "Rename" -> 10
    "Create thread" -> 11
    "Pin", "Unpin" -> 12
    // Ce que le menu flottant proposait et qui doit survivre : rien ne devient inatteignable.
    "Mark as Unread", "Mark as Read" -> 20
    "Usage & cost" -> 21
    "Copy title" -> 22
    "Open in New Window" -> 23
    // **« Archiver » juste avant le destructeur**, comme la référence : elle range « Archive » en
    // avant-dernière ligne, après l'usage et la copie — l'archivage est une sortie de liste, il se
    // lit au bas de la feuille, pas au milieu des gestes du fil.
    "Archive", "Unarchive" -> 24
    // Le destructeur reste **en fin de liste**, comme dans la référence.
    "Delete" -> 40
    else -> 30
}

/**
 * Les lignes de la feuille d'une conversation, **pures** : elles se construisent sans Compose, donc
 * se vérifient. L'ordre est celui de la référence — renommer, créer un fil, épingler, puis l'usage
 * et la copie, **archiver en avant-dernière ligne**, supprimer en dernier (le destructeur ne quitte
 * jamais la fin de la feuille) — puis les compléments que la rangée offrait déjà.
 *
 * Les états :
 *  - « Épingler » devient « Détacher » (et l'inverse) quand la conversation est déjà épinglée ;
 *  - « Archiver » devient « Désarchiver » quand elle est déjà archivée ;
 *  - « Créer un fil » se **désactive** sur une conversation vide : il n'y a rien à recopier.
 */
fun conversationSheetRows(
    conversation: Conversation,
    canMarkUnread: Boolean,
    muted: Boolean,
    /** Vrai quand la conversation est actuellement non lue : la ligne propose alors de la marquer lue. */
    unread: Boolean = false,
    onRename: () -> Unit,
    onCreateThread: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onToggleUnread: (() -> Unit)? = null,
    onUsage: (() -> Unit)? = null,
    onCopyTitle: (() -> Unit)? = null,
    onOpenElsewhere: (() -> Unit)? = null,
    muteRows: List<ConversationSheetRow> = emptyList(),
): List<ConversationSheetRow> = buildList {
    add(
        ConversationSheetRow(
            label = "Rename",
            symbol = Symbols.Pencil,
            tint = ConversationActionTint.CONVERSATION,
            onSelect = onRename,
        ),
    )
    add(
        ConversationSheetRow(
            label = "Create thread",
            symbol = Symbols.DocOnDoc,
            tint = ConversationActionTint.THREAD,
            // Rien à recopier dans une conversation vide : la ligne existe mais n'agit pas.
            enabled = !conversation.isEmpty,
            onSelect = onCreateThread,
        ),
    )
    add(
        ConversationSheetRow(
            label = if (conversation.pinned) "Unpin" else "Pin",
            symbol = Symbols.Pin,
            tint = ConversationActionTint.PIN,
            onSelect = onTogglePin,
        ),
    )
    add(
        ConversationSheetRow(
            label = if (conversation.archived) "Unarchive" else "Archive",
            symbol = Symbols.ArchiveBox,
            tint = ConversationActionTint.ARCHIVE,
            onSelect = onToggleArchive,
        ),
    )
    add(
        ConversationSheetRow(
            label = "Delete",
            symbol = Symbols.Trash,
            tint = ConversationActionTint.DANGER,
            destructive = true,
            onSelect = onDelete,
        ),
    )
    // Les compléments : ce que le menu flottant offrait, pour qu'aucune fonction ne disparaisse.
    onToggleUnread?.takeIf { canMarkUnread }?.let { toggle ->
        add(
            ConversationSheetRow(
                label = if (unread) "Mark as Read" else "Mark as Unread",
                symbol = if (unread) Symbols.CheckmarkMessage else Symbols.MessageBadge,
                tint = ConversationActionTint.CONVERSATION,
                onSelect = toggle,
            ),
        )
    }
    onUsage?.let { open ->
        add(
            ConversationSheetRow(
                label = "Usage & cost",
                symbol = Symbols.ChartBar,
                tint = ConversationActionTint.INSIGHT,
                onSelect = open,
            ),
        )
    }
    onCopyTitle?.let { copy ->
        add(
            ConversationSheetRow(
                label = "Copy title",
                symbol = Symbols.DocOnDoc,
                tint = ConversationActionTint.IDENTITY,
                onSelect = copy,
            ),
        )
    }
    onOpenElsewhere?.let { open ->
        add(
            ConversationSheetRow(
                label = "Open in New Window",
                symbol = Symbols.SquareAndPencil,
                tint = ConversationActionTint.SCREEN,
                onSelect = open,
            ),
        )
    }
    addAll(muteRows)
}.sortedWith(compareBy { conversationSheetOrder(it.label) })

/**
 * Le sous-titre de la feuille : la dernière activité, comme « Active 7h ago » de la référence.
 *
 * Il se compose du mot de la feuille (« Active », traduit par le catalogue) et de l'âge calculé par
 * [com.onyx.app.data.model.serve.relativeLabel] — jamais un horodatage brut.
 */
fun conversationActivitySubtitle(updatedAt: Long, now: Long = System.currentTimeMillis()): String =
    OnyxLocalization.format(
        "Active %1\$s",
        com.onyx.app.data.model.serve.relativeLabel(updatedAt, now),
    )

// ---------------------------------------------------------------------------------------------
// La surface de la feuille (les mêmes mesures que la feuille du fil)
// ---------------------------------------------------------------------------------------------

/** Une cible tactile se prend au doigt : 52 dp, l'icône respirant au milieu. */
private val ConvSheetRowMinHeight = 52.dp
private val ConvSheetCorner = 22.dp
private val ConvSheetHandleWidth = 40.dp
private val ConvSheetHandleHeight = 5.dp

/** L'écart de glissement au-delà duquel un glissement vers le bas referme la feuille. */
private val ConvSheetDismissThreshold = 72.dp

/** La forme de la feuille : coins du haut arrondis, bas au bord de l'écran. */
private val ConvSheetShape = RoundedCornerShape(topStart = ConvSheetCorner, topEnd = ConvSheetCorner)

/** Le voile de la feuille : assez sombre pour détacher la surface, jamais au point de tout cacher. */
private val ConvSheetScrim = Color.Black.copy(alpha = 0.42f)

/**
 * L'ancien gris d'« Archiver » — le neutre de la référence. Il reste exporté pour la famille
 * `ARCHIVE`, mais la pastille d'une ligne neutre prend désormais **le gris de la maison**
 * ([com.onyx.app.ui.design.ChatActionBadge.fillNeutral]) : une seule tuile neutre pour toute l'app.
 */
private val ConversationSheetGray = ReferencePalette.NeutralAction

// ---------------------------------------------------------------------------------------------
// Le bandeau d'annulation
// ---------------------------------------------------------------------------------------------

/** Une action qui vient d'être appliquée, et de quoi la défaire : le message, et le retour en arrière. */
data class ConversationUndo(val message: String, val undo: () -> Unit)

/**
 * **La pastille flottante de rattrapage** — ce qui vient d'être fait, et de quoi le défaire.
 *
 * Ce n'était pas une surface de la maison : c'était un **bandeau plat pleine largeur collé au bas**,
 * qui passait **par-dessus le bouton « Nouvelle conversation »** (défaut relevé par le propriétaire
 * sur sa capture). Rien de tout cela n'appartient à la maison — ni le bord collé, ni le plein
 * largeur du bas, ni le fait de recouvrir la plaque du pied.
 *
 * Elle est donc, comme la confirmation, une **surface de la maison** :
 *
 *  - **verre** de la famille des bandeaux ([FrostSurface.BANNER] : un flou court, une ombre douce)
 *    et la finition commune de vitre ([frostedSheet] : lumière de tête, liseré, reflet de tranche) ;
 *  - **rayon 28** ([OnyxSocle.RadiusPanel] — le rayon des panneaux de la maison) ;
 *  - **posée à 16 de la marge** ([OnyxSocle.ScreenMargin]) : jamais collée au bord ;
 *  - **au-dessus de la plaque du bas** : c'est **l'appelant** qui pose le dégagement (la hauteur
 *    mesurée de la plaque plus la marche de la maison) — la pastille ne connaît pas la plaque, mais
 *    elle n'a **jamais** le droit de la recouvrir ;
 *  - le texte (« Supprimée ») à **gauche**, « Annuler » en **accent** à **droite**, sur une cible de
 *    48 dp ([UNDO_TARGET]) en hauteur comme en largeur.
 *
 * C'est la contrepartie obligée de « l'action s'applique tout de suite » : un glissement franc ou un
 * appui applique l'action **immédiatement**, et la pastille laisse une poignée de secondes pour
 * revenir en arrière — jamais une boîte de dialogue qui retarderait le geste. Elle se referme toute
 * seule après un silence, ou sur son bouton.
 */
@Composable
fun ConversationUndoBar(
    message: String,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val reduceMotion = LocalReduceMotion.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(message) {
        shown = true
        kotlinx.coroutines.delay(UNDO_MILLIS)
        shown = false
        onDismiss()
    }

    // La forme et la matière de la pastille : le rayon des panneaux, le barreau haut, la vitre.
    val shape = RoundedCornerShape(OnyxSocle.RadiusPanel)
    val fill = OnyxSurfaces.surfaceHigh(theme)

    AnimatedVisibility(
        visible = shown || reduceMotion,
        enter = if (reduceMotion) EnterTransition.None else slideInVertically(initialOffsetY = { it }),
        exit = if (reduceMotion) {
            androidx.compose.animation.ExitTransition.None
        } else {
            androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
        },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // **La marge de la maison** : 16 de chaque côté — la pastille n'est jamais collée au
                // bord du volet.
                .padding(horizontal = OnyxSocle.ScreenMargin)
                .clip(shape)
                .background(fill, shape)
                .padding(start = OnyxSocle.CardPadding, end = OnyxSpace.Half)
                .heightIn(min = UNDO_TARGET),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = message,
                localize = false,
                style = socleBodyStyle(theme).copy(color = theme.textColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // La cible d'« Annuler » fait 48 dp de haut ; sa **largeur** suit son libellé — dans un
            // volet étroit, « Annuler » se coupait en deux sur une cible carrée de 48 dp.
            Box(
                modifier = Modifier
                    .heightIn(min = UNDO_TARGET)
                    .widthIn(min = UNDO_TARGET)
                    .padding(horizontal = OnyxSpace.Half)
                    .onyxTappable(onClick = onUndo),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "Undo",
                    style = socleBodyStyle(theme).copy(color = theme.accent, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Le silence du bandeau avant qu'il ne se referme, et la hauteur plancher de sa zone d'appui. */
private const val UNDO_MILLIS = 4200L
private val UNDO_TARGET = 48.dp

/** Réexporté pour les tests : la teinte d'un libellé de la feuille d'une conversation. */
internal fun conversationTintFor(label: String, destructive: Boolean = false): ConversationActionTint = when {
    destructive -> ConversationActionTint.DANGER
    label == "Rename" -> ConversationActionTint.CONVERSATION
    label == "Create thread" -> ConversationActionTint.THREAD
    label == "Pin" || label == "Unpin" -> ConversationActionTint.PIN
    label == "Archive" || label == "Unarchive" -> ConversationActionTint.ARCHIVE
    label == "Mark as Read" || label == "Mark as Unread" -> ConversationActionTint.CONVERSATION
    label == "Usage & cost" -> ConversationActionTint.INSIGHT
    label == "Copy title" -> ConversationActionTint.IDENTITY
    label == "Open in New Window" -> ConversationActionTint.SCREEN
    else -> ConversationActionTint.CONVERSATION
}
