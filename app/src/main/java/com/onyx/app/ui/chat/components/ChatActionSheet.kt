package com.onyx.app.ui.chat.components

import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.design.MotionFamily
import com.onyx.app.ui.design.rowArrival

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.BackgroundProcess
import com.onyx.app.data.net.ChatContextSlice
import com.onyx.app.data.net.ChatAccount
import com.onyx.app.data.net.ChatUsage
import com.onyx.app.data.net.DiffLine
import com.onyx.app.data.net.DiffLineKind
import com.onyx.app.data.net.RestoreDiff
import com.onyx.app.data.net.RestorePoint
import com.onyx.app.data.net.RestorePointList
import com.onyx.app.data.net.ServeChatActions
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.DialogWindowBlur
import com.onyx.app.ui.design.FrostSurface
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.bodyInk
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.design.neutralThumbnailInk
import com.onyx.app.ui.design.neutralThumbnailTint
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * La feuille des actions d'une conversation — celle qui remplace la liste flottante des « … ».
 *
 * Ce qu'elle a de différent du menu flottant qu'elle remplace : elle **monte du bas**, ses coins du
 * haut sont arrondis, une **poignée** la surmonte, on la fait **glisser** vers le bas pour la
 * fermer, un appui **à côté** la ferme, et le **retour matériel** aussi. Elle porte le **nom de la
 * conversation ou du bot** en tête — ce qu'on est en train de piloter — puis une ligne par action.
 *
 * Chaque ligne : une **pastille carrée arrondie et teintée** portant l'**icône** (une couleur par
 * famille d'action, prise dans la palette d'Onyx), puis le **libellé** en clair. La cible fait au
 * moins 48 dp et les lignes sont séparées par l'air, jamais par un trait — le modèle n'en a pas.
 *
 * Trois lignes n'agissent pas : elles ouvrent une **seconde feuille** par-dessus (consommation et
 * coût, processus en arrière-plan, points de restauration). Une quatrième copie l'identifiant de
 * session et le dit **dans la feuille**, sans quoi l'appui serait muet.
 */
@Composable
fun ChatActionSheet(
    title: String,
    items: List<MenuItem>,
    server: ServerConfig,
    sessionId: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Le repli déclaré du tour : le panneau de coût en tient compte (un repli local n'a pas de prix). */
    fallback: com.onyx.app.ui.design.FallbackDeclaration? = null,
) {
    val theme = LocalOnyxTheme.current
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val actions = remember(server) { ServeChatActions(server) }

    // Le panneau ouvert (s'il y en a un), et l'état de sa lecture.
    var open by remember { mutableStateOf<ChatSheetAction?>(null) }
    var usageState by remember { mutableStateOf<LoadState<UsageRead>>(LoadState.Loading) }
    var processState by remember { mutableStateOf<LoadState<List<BackgroundProcess>>>(LoadState.Loading) }
    var restoreState by remember { mutableStateOf<LoadState<RestorePointList>>(LoadState.Loading) }
    var copied by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    // Les rangées dépliées (une seule à la fois dans chaque panneau) : déplier un point de
    // restauration charge **son diff**, déplier un processus montre **sa sortie** (déjà reçue).
    var expandedRestore by remember { mutableStateOf<String?>(null) }
    var expandedProcess by remember { mutableStateOf<String?>(null) }
    var diffState by remember { mutableStateOf<LoadState<RestoreDiff?>>(LoadState.Loading) }

    // Le compte des relectures : « Relire » sur un échec ne change pas de panneau, il **relit** —
    // sans ce compteur, `open` ne bougeait pas et la lecture ne repartait jamais.
    var reload by remember { mutableStateOf(0) }

    // La confirmation de la copie est **dans** la feuille : le voile d'un dialogue couvrirait un
    // message posé sous lui, et l'appui paraîtrait muet — le défaut à ne pas reproduire.
    fun copySession() {
        val id = chatSessionIdForCopy(sessionId)
        if (id == null) {
            notice = OnyxLocalization["this conversation has no session id yet"]
            return
        }
        scope.launch {
            runCatching {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("session", id)))
            }.onFailure { error ->
                notice = OnyxLocalization.format("Couldn't copy it: %1\$s", error.message ?: error.toString())
                return@launch
            }
            copied = true
            notice = OnyxLocalization.format("Session id copied (%1\$d characters)", id.length)
        }
    }

    // La lecture du panneau ouvert : une fois par ouverture, jamais à chaque recomposition — et de
    // nouveau quand on demande à relire.
    LaunchedEffect(open, reload) {
        val id = sessionId.orEmpty()
        // Changer de panneau (ou relire) replie les rangées : on ne garde pas le diff d'un autre
        // point sous les yeux.
        expandedRestore = null
        expandedProcess = null
        when (open) {
            ChatSheetAction.USAGE -> usageState = run {
                val usage = actions.usage(id)
                if (usage.isFailure) {
                    LoadState.Failed(readingFailure(usage.exceptionOrNull()!!))
                } else {
                    // Le compte se lit à part : `usage.bars` peut manquer sans que la consommation
                    // échoue. Son absence ne rend pas la lecture de la consommation un échec.
                    val account = actions.account().getOrNull() ?: ChatAccount(available = false)
                    LoadState.Ready(UsageRead(usage.getOrThrow(), account))
                }
            }

            ChatSheetAction.PROCESSES -> processState = actions.processes(id).fold(
                onSuccess = { LoadState.Ready(it) },
                onFailure = { LoadState.Failed(readingFailure(it)) },
            )

            ChatSheetAction.RESTORES -> restoreState = actions.restorePoints(id).fold(
                onSuccess = { LoadState.Ready(it) },
                onFailure = { LoadState.Failed(readingFailure(it)) },
            )

            else -> Unit
        }
    }

    // Le diff de la rangée dépliée : chargé au moment où on la déplie (et de nouveau à la relecture).
    LaunchedEffect(expandedRestore, reload) {
        val hash = expandedRestore ?: return@LaunchedEffect
        diffState = LoadState.Loading
        diffState = actions.diff(sessionId.orEmpty(), hash).fold(
            onSuccess = { LoadState.Ready(it) },
            onFailure = { LoadState.Failed(readingFailure(it)) },
        )
    }

    val rows = chatSheetRows(items)
    val panel = open

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogWindowBlur(FrostSurface.SHEET)
        // Le retour matériel : le panneau ouvert d'abord, la feuille ensuite. Un seul gestionnaire
        // pour les deux, puisque les deux vivent dans la même fenêtre.
        BackHandler(enabled = panel != null) { open = null }

        if (panel == null) {
            // **La feuille des actions, seule.** Ses lignes montent du bas et rien ne les recouvre :
            // c'était le défaut (« Copier l'identifiant de session » coupé en deux par le panneau),
            // et c'est aussi ce qui laisse son défilement aller jusqu'à sa dernière ligne.
            ChatSheetSurface(
                title = title,
                rows = rows,
                notice = notice,
                copied = copied,
                onDismiss = onDismiss,
                reduceMotion = reduceMotion,
                modifier = modifier,
                onRow = { row ->
                    when (row.action) {
                        ChatSheetAction.COPY_ID -> copySession()
                        ChatSheetAction.USAGE -> open = ChatSheetAction.USAGE
                        ChatSheetAction.PROCESSES -> open = ChatSheetAction.PROCESSES
                        ChatSheetAction.RESTORES -> open = ChatSheetAction.RESTORES
                        ChatSheetAction.SIMPLE -> {
                            // Une action referme la feuille puis agit, comme le menu qu'elle remplace :
                            // l'appui n'est jamais avalé par le voile.
                            onDismiss()
                            row.onSelect()
                        }
                    }
                },
            )
        } else {
            // **Le panneau remplace la feuille**, dans la même surface : aucune ligne ne reste
            // dessous, il porte son propre défilement, et sa première ligne est le **retour** — un
            // retour visible, pas seulement le geste du système.
            ChatSheetPanel(
                notice = notice,
                reduceMotion = reduceMotion,
                onBack = { open = null },
                modifier = modifier,
            ) {
                when (panel) {
                    ChatSheetAction.USAGE -> UsagePanel(usageState, onRetry = { reload += 1 }, fallback = fallback)

                    ChatSheetAction.PROCESSES -> ProcessPanel(
                        state = processState,
                        expandedId = expandedProcess,
                        onToggle = { id -> expandedProcess = if (expandedProcess == id) null else id },
                        onRetry = { reload += 1 },
                        onStop = { id ->
                            scope.launch {
                                notice = actions.stopProcess(sessionId.orEmpty(), id)
                                    ?: OnyxLocalization["Process stopped"]
                                processState = actions.processes(sessionId.orEmpty()).fold(
                                    onSuccess = { LoadState.Ready(it) },
                                    onFailure = { LoadState.Failed(readingFailure(it)) },
                                )
                            }
                        },
                    )

                    else -> RestorePanel(
                        state = restoreState,
                        expandedId = expandedRestore,
                        diff = diffState,
                        onToggle = { hash -> expandedRestore = if (expandedRestore == hash) null else hash },
                        onRetry = { reload += 1 },
                        onRestore = { hash ->
                            scope.launch {
                                notice = actions.restore(sessionId.orEmpty(), hash)
                                    ?: OnyxLocalization["Restored"]
                                restoreState = actions.restorePoints(sessionId.orEmpty()).fold(
                                    onSuccess = { LoadState.Ready(it) },
                                    onFailure = { LoadState.Failed(readingFailure(it)) },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Le motif d'un échec de lecture, prêt à afficher : la traduction du refus du serveur, jamais son
 * code brut — et jamais un motif muet.
 */
internal fun readingFailure(error: Throwable): String =
    ServeChatActions.failureReason(error.message ?: error.toString())

// ---------------------------------------------------------------------------------------------
// Les lignes de la feuille : le modèle, la famille de teinte, et l'ordre d'affichage
// ---------------------------------------------------------------------------------------------

/** Ce qu'une ligne fait : agir, ou ouvrir une seconde feuille. */
enum class ChatSheetAction { SIMPLE, USAGE, PROCESSES, RESTORES, COPY_ID }

/**
 * Une ligne de la feuille : son libellé (clé anglaise, traduite à l'affichage), son icône, sa
 * famille de teinte, et ce qu'elle fait.
 */
data class ChatSheetRow(
    val label: String,
    val symbol: Int,
    val tint: ChatActionTint,
    val action: ChatSheetAction = ChatSheetAction.SIMPLE,
    val localize: Boolean = true,
    val destructive: Boolean = false,
    val onSelect: () -> Unit = {},
)

/**
 * L'ordre de la feuille, par libellé : les actions de la conversation d'abord, puis le nouveau
 * départ, l'analyse, la restauration, l'identifiant, et enfin les autres écrans.
 *
 * Un libellé inconnu (une entrée ajoutée ailleurs, la ligne de projet) se pose **après** ce qu'on
 * connaît : aucune entrée ne disparaît faute d'être prévue ici.
 */
fun chatSheetOrder(label: String): Int = when (label) {
    "Find in conversation" -> 10
    "Rename" -> 11
    "Export as Markdown" -> 12
    "Add to Project", "Move to Project" -> 13
    "Take a note" -> 14
    "Start fresh" -> 20
    "New Conversation" -> 21
    "Usage & cost" -> 30
    "Background processes" -> 31
    "Restore points" -> 40
    "Copy session id" -> 50
    "Computer" -> 60
    "Terminal" -> 61
    "Settings" -> 62
    else -> 100
}

/**
 * La famille d'une ligne de la conversation.
 *
 * **Ce qu'elle décide a changé au lot « uniformité + sombre ».** Elle portait une **couleur par
 * famille d'action** — bleu pour Chercher/Renommer, vert pour Prendre une note, orange pour les
 * processus, rouge pour le danger — et c'est précisément le défaut que le propriétaire a dénoncé
 * sur sa capture : « trop de couleur, encore ». La règle de la maison est maintenant qu'**une
 * couleur ne sert qu'à un ÉTAT**, jamais à indexer un libellé : cinq lignes dont aucune n'est un
 * état ne peuvent pas porter quatre couleurs.
 *
 * La famille reste — elle garde la **place** de la ligne (l'ordre de la feuille) et elle porte le
 * **seul signal** qui soit un état : [DANGER]. Son dessin, lui, est neutre pour tout le reste
 * (voir [tintHue]).
 */
enum class ChatActionTint { CONVERSATION, NOTE, EXPORT, INSIGHT, BACKGROUND, RESTORE, IDENTITY, SCREEN, DANGER }

/** Le ton de la palette pour chaque famille : jamais une couleur choisie hors du thème. */
@Composable
private fun tintHue(tint: ChatActionTint): Color = tintHue(tint, LocalOnyxTheme.current)

/**
 * La teinte d'une famille **sur un thème donné** : pure, donc mesurable hors composition.
 *
 * **Neutre, sauf le danger.** La vignette d'une ligne prend l'**encre du thème** — et non plus une
 * couleur par famille : c'est exactement la vignette grise d'une rangée de l'anatomie
 * ([OnyxIconTile]), la seule que la référence dessine dans une liste. Le **rouge** reste, et lui
 * seul : une action irréversible est un **état**, pas une famille.
 *
 * Ce que cela change sur la capture : sept vignettes bleu/vert/orange/rouge deviennent sept vignettes
 * neutres, et la seule ligne colorée de la feuille est celle qui efface quelque chose.
 */
internal fun tintHue(tint: ChatActionTint, theme: ResolvedTheme): Color = when (tint) {
    // Le seul ÉTAT de la feuille : une action qui ne se rattrape pas.
    ChatActionTint.DANGER -> theme.error

    // Tout le reste : l'encre du thème, donc le gris neutre de la maison. Les sept familles gardent
    // leur nom parce qu'elles commandent encore l'ordre de la feuille ([chatSheetOrder]).
    ChatActionTint.CONVERSATION, ChatActionTint.NOTE, ChatActionTint.EXPORT, ChatActionTint.INSIGHT,
    ChatActionTint.BACKGROUND, ChatActionTint.RESTORE, ChatActionTint.IDENTITY, ChatActionTint.SCREEN ->
        theme.textColor
}

/**
 * La pastille d'icône d'une ligne : son fond (la teinte très diluée sur la surface opaque de la
 * feuille) et son encre (la teinte pleine quand elle tient 3:1 sur ce fond, sinon le blanc ou le
 * presque-noir maison — celui qui s'y lit le mieux).
 *
 * Pur et mesurable : la même règle sert au dessin et au test de contraste.
 */
object ChatActionBadge {

    /** La teinte du fond d'une pastille, avant composition sur la surface. */
    const val FILL_ALPHA = 0.16f

    /** La marge au-dessus du seuil d'icône : un trait antialiasé ne se juge pas au ras. */
    const val INK_MARGIN = 0.5f

    /** La taille de la pastille, et celle de l'icône qu'elle porte. */
    val SIZE = 30.dp
    val ICON_SIZE = 15.dp

    /** Le fond réellement lu : la teinte diluée **composée sur** la surface opaque de la feuille. */
    fun fill(hue: Color, surface: Color): Color = OnyxContrast.over(hue.copy(alpha = FILL_ALPHA), surface)

    /**
     * L'encre de l'icône : la teinte de la famille, **composée sur sa pastille**, approfondie juste
     * assez pour tenir 3:1.
     *
     * La composition vient **d'abord** : les teintes du thème sont souvent translucides (le libellé
     * secondaire du système est à 60 %). Mesurée telle quelle, la teinte « tenait » 7:1 ; **peinte**
     * ainsi, elle tombait à 2,96:1 sur sa propre pastille — la vignette délavée de la capture. Une
     * encre rendue ici est donc toujours **opaque** : ce qui est mesuré est ce qui est peint.
     *
     * La cible est **3,5:1** et non 3,0 : un trait d'icône est antialiasé — son pixel de cœur
     * atteint bien la couleur, son voisin non. Viser le ras du seuil livre une valeur
     * *théoriquement* juste et *mesurée* juste, illisible en pratique.
     */
    fun ink(hue: Color, surface: Color): Color {
        val fill = fill(hue, surface)
        val target = OnyxContrast.ICON_MIN + INK_MARGIN
        val solid = OnyxContrast.over(hue, fill)
        if (OnyxContrast.ratio(solid, fill) >= target) return solid
        // La teinte reste celle de la famille : on l'**approfondit** vers le noir, du plus petit
        // ajustement qui atteint le seuil (dichotomie, donc reproductible). Basculer sur du blanc
        // ou du presque-noir perdrait la couleur qui distingue les familles.
        var notYet = 0f
        var enough = 1f
        repeat(24) {
            val middle = (notYet + enough) / 2f
            if (OnyxContrast.ratio(lerp(solid, Color.Black, middle), fill) >= target) {
                enough = middle
            } else {
                notYet = middle
            }
        }
        val deepened = lerp(solid, Color.Black, enough)
        return if (OnyxContrast.ratio(deepened, fill) >= OnyxContrast.ICON_MIN) {
            deepened
        } else {
            OnyxContrast.inkOn(fill)
        }
    }

    /**
     * **La tuile neutre de la maison** ([neutralThumbnailTint]) : le même gris que la vignette
     * d'une rangée de feuille (`HouseSheetRow`) ou d'une pièce jointe. Le fond d'une pastille
     * d'icône de menu ou de feuille, quand la ligne n'est pas un état.
     *
     * C'est la correction du défaut relevé sur la feuille d'une conversation : quatre teintes
     * (bleu/indigo pour Renommer, Créer un fil, Marquer comme non lu, Consommation, Ouvrir ; orange
     * pour Épingler ; gris pour Copier le titre, Archiver et les mises en sourdine) deviennent **un
     * seul gris** — celui de toutes les autres rangées de l'app.
     */
    fun fillNeutral(theme: ResolvedTheme): Color = neutralThumbnailTint(theme)

    /**
     * **Le glyphe monochrome** d'une tuile neutre : l'encre secondaire de la maison, écartée du
     * fond juste assez pour tenir le seuil des icônes ([neutralThumbnailInk]). Une ligne qui n'est
     * pas un état ne porte ni couleur de tuile ni couleur de glyphe.
     */
    fun inkNeutral(theme: ResolvedTheme): Color = neutralThumbnailInk(theme)
}

/**
 * Les encres du **texte** de la feuille, dérivées de sa surface — qui est **opaque**.
 *
 * Le libellé est l'encre pleine du thème, faite pour se lire sur cette surface. Le rouge du danger
 * suit la même règle que les notes : il **reste rouge**, mais écarté du fond juste assez pour tenir
 * 4,5:1 (le rouge de signalisation du système, posé sur la surface claire la plus claire — SLATE,
 * blanc pur — n'est mesuré qu'à 3,55:1).
 *
 * [muted] est le pendant des lignes secondaires (une note, une légende, l'âge d'un point de
 * restauration) : `secondaryText` du thème **composé** sur la surface, puis écarté du fond jusqu'au
 * seuil. Le libellé secondaire du système, posé tel quel sur du blanc, est à 3,10:1 — sous le seuil
 * d'un mot, et c'est exactement ce que la capture montrait comme « délavé ». Les deux fonctions
 * rendent une couleur **opaque** : ce qui est mesuré est ce qui est peint.
 */
object SheetInk {

    /**
     * L'encre d'un libellé de ligne : la pleine encre du thème, ou le rouge du danger — **écartée du
     * fond** juste assez pour tenir 4,5:1 quand elle n'y tient pas déjà.
     *
     * Le fond, ici, n'est pas toujours celui du thème : la même feuille peut se poser sur la surface
     * d'une autre apparence (un aperçu, une vitre claire sous un thème sombre). Une pleine encre
     * posée sur un fond qui n'est pas le sien tombe alors dans le fond ; [OnyxContrast.distinctOn]
     * l'en écarte du plus petit pas qui suffit, dans la direction où il y a de la place.
     */
    fun label(theme: ResolvedTheme, surface: Color, dangerous: Boolean): Color {
        val preferred = if (dangerous) theme.error else theme.textColor
        val ink = OnyxContrast.over(preferred, surface)
        if (OnyxContrast.ratio(ink, surface) >= OnyxContrast.TEXT_MIN) return ink
        return OnyxContrast.distinctOn(ink, surface, OnyxContrast.TEXT_MIN)
    }

    /** L'encre d'une note ou d'une ligne secondaire : jamais sous 4,5:1 sur la surface. */
    fun muted(theme: ResolvedTheme, surface: Color): Color =
        bodyInk(theme, surface, theme.secondaryText)
}

/**
 * Les lignes de la feuille, à partir des actions déclarées par l'app.
 *
 * Les actions déclarées (`items`) sont converties telles quelles, puis les quatre lignes que la
 * feuille possède en propre sont ajoutées — l'ordre est celui de [chatSheetOrder]. La teinte vient
 * du libellé ([chatTintFor]) : jamais d'une position dans la liste, qui changerait d'une
 * conversation à l'autre.
 */
fun chatSheetRows(items: List<MenuItem>): List<ChatSheetRow> = buildList {
    items.forEach { item ->
        add(
            ChatSheetRow(
                label = item.label,
                symbol = item.symbol ?: Symbols.Ellipsis,
                tint = chatTintFor(item.label, item.destructive),
                localize = item.localize,
                destructive = item.destructive,
                onSelect = item.onSelect,
            ),
        )
    }
    add(ChatSheetRow("Usage & cost", Symbols.ChartBar, ChatActionTint.INSIGHT, ChatSheetAction.USAGE))
    add(ChatSheetRow("Background processes", Symbols.Clock, ChatActionTint.BACKGROUND, ChatSheetAction.PROCESSES))
    add(ChatSheetRow("Restore points", Symbols.ArrowClockwise, ChatActionTint.RESTORE, ChatSheetAction.RESTORES))
    add(ChatSheetRow("Copy session id", Symbols.DocOnDoc, ChatActionTint.IDENTITY, ChatSheetAction.COPY_ID))
}.sortedWith(compareBy { chatSheetOrder(it.label) })

/**
 * L'identifiant qu'on copie : **rendu tel quel**, jamais rogné.
 *
 * C'est la règle qui ferme le défaut déjà corrigé dans l'app (un collage qui perdait des
 * caractères : 20 attendus, 6 arrivés). Aucune coupe, aucun `trim` : un identifiant de 20
 * caractères s'écrit en 20 caractères. `null` seulement quand il n'y en a pas.
 */
fun chatSessionIdForCopy(raw: String?): String? = raw?.takeIf { it.isNotBlank() }

/** La famille de teinte d'un libellé d'action. */
fun chatTintFor(label: String, destructive: Boolean = false): ChatActionTint = when {
    destructive -> ChatActionTint.DANGER
    label == "Find in conversation" || label == "Rename" || label == "New Conversation" ->
        ChatActionTint.CONVERSATION

    label == "Take a note" -> ChatActionTint.NOTE
    label == "Export as Markdown" -> ChatActionTint.EXPORT
    label == "Start fresh" || label == "Restore points" -> ChatActionTint.RESTORE
    label == "Usage & cost" -> ChatActionTint.INSIGHT
    label == "Background processes" -> ChatActionTint.BACKGROUND
    label == "Copy session id" -> ChatActionTint.IDENTITY
    label == "Computer" || label == "Terminal" || label == "Settings" ||
        label == "Add to Project" || label == "Move to Project" -> ChatActionTint.SCREEN

    else -> ChatActionTint.CONVERSATION
}

// ---------------------------------------------------------------------------------------------
// La surface de la feuille
// ---------------------------------------------------------------------------------------------

/** Une cible tactile se prend au doigt : 52 dp de haut, l'icône respirant au milieu. */
private val SheetRowMinHeight = 52.dp

/**
 * Le coin de la feuille des actions.
 *
 * Il valait **22 dp** écrit ici : un rayon de plus, à côté des 26 dp de `GlassPanelCorner` que porte
 * la feuille des pièces jointes et de la table des panneaux. Deux feuilles du bas qui ne partageaient
 * pas le même coin, c'est le défaut de forme relevé sur la capture — il est ramené à la famille.
 */
private val SheetCorner = OnyxSpace.PanelCorner
private val SheetHandleWidth = 40.dp
private val SheetHandleHeight = 5.dp

/** L'écart de glissement au-delà duquel un glissement vers le bas referme la feuille. */
private val DismissThreshold = 72.dp

/** La forme de la feuille : ses deux coins du haut arrondis, le bas au bord de l'écran. */
private val SheetShape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner)

@Composable
private fun ChatSheetSurface(
    title: String,
    rows: List<ChatSheetRow>,
    notice: String?,
    copied: Boolean,
    onDismiss: () -> Unit,
    reduceMotion: Boolean,
    onRow: (ChatSheetRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    // Le glissement : la feuille suit le doigt, et repart au repos si on ne va pas assez loin.
    var drag by remember { mutableFloatStateOf(0f) }
    val thresholdPx = with(LocalDensity.current) { DismissThreshold.toPx() }
    val dragState = rememberDraggableState { delta -> drag = (drag + delta).coerceAtLeast(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SheetScrim)
            // Un appui hors de la feuille la referme : le voile prend le geste, la feuille non.
            .onyxTappable(onClick = onDismiss),
    ) {
        AnimatedVisibility(
            visible = shown || reduceMotion,
            enter = if (reduceMotion) {
                EnterTransition.None
            } else {
                slideInVertically(
                    animationSpec = MotionFamily.Open.spec(reduceMotion),
                    initialOffsetY = { height -> height },
                ) + fadeIn(animationSpec = MotionFamily.Open.spec(reduceMotion))
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, drag.roundToInt()) }
                    .clip(SheetShape)
                    // La surface est **opaque** : c'est elle qui porte le texte, et son contraste se
                    // mesure alors exactement. Le flou de la fenêtre reste derrière (l'écran, lui,
                    // s'assombrit) ; aucune vitre translucide ne vient trouer la lisibilité.
                    //
                    // **Le barreau est celui des surfaces flottantes** ([OnyxSurfaces.surfaceHigh]) :
                    // `surfaceColor` valait 1,096:1 sur la page sombre — la feuille se devinait plus
                    // qu'elle ne se lisait. Le barreau haut mesure 1,314:1, et le **filet** de tête le
                    // sépare du contenu par-dessus.
                    .background(OnyxSurfaces.surfaceHigh(theme))
                    .border(
                        width = OnyxSpace.Stroke,
                        color = OnyxSurfaces.hairline(theme),
                        shape = SheetShape,
                    )
                    // La feuille n'est pas une zone de fermeture : appuyer entre deux lignes ne
                    // doit rien faire.
                    .onyxTappable(onClick = {})
                    .navigationBarsPadding(),
            ) {
                SheetHeader(title = title, notice = notice, onDragState = dragState, onDragStop = {
                    if (drag > thresholdPx) onDismiss() else drag = 0f
                })

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    rows.forEach { row ->
                        Box(modifier = Modifier.rowArrival(rows.indexOf(row))) {
                            ChatSheetActionRow(
                                row = row,
                                copied = copied && row.action == ChatSheetAction.COPY_ID,
                                onClick = { onRow(row) },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

/** La poignée et le titre : le seul endroit où la feuille se prend pour glisser. */
@Composable
private fun SheetHeader(
    title: String,
    notice: String?,
    onDragState: DraggableState,
    onDragStop: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(state = onDragState, orientation = Orientation.Vertical, onDragStopped = { onDragStop() }),
    ) {
        // La poignée, centrée : elle dit qu'on peut faire glisser la feuille vers le bas.
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = SheetHandleWidth, height = SheetHandleHeight)
                    .background(theme.tertiaryText.copy(alpha = 0.55f), RoundedCornerShape(50)),
            )
        }
        // Le titre : le nom de la conversation, ou celui du bot quand elle n'a pas de nom. Coupé à
        // une **frontière** de mot (voir [headerLine]) : la feuille a la largeur de l'écran, mais un
        // nom long s'y coupait encore au milieu d'un mot.
        val density = LocalDensity.current
        val titleStyle = theme.headline.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)) {
            val budget = with(density) { maxWidth.roundToPx() }
            val measurer = rememberTextMeasurer()
            val shown = remember(title, budget) { measurer.headerLine(title, titleStyle, budget) }
            BasicText(
                text = shown,
                localize = false,
                style = titleStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        notice?.let {
            BasicText(
                text = it,
                localize = false,
                style = theme.footnote.copy(color = SheetInk.muted(theme, OnyxSurfaces.surfaceHigh(theme))),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** Une ligne : sa pastille teintée et son icône, puis son libellé en clair. */
@Composable
private fun ChatSheetActionRow(
    row: ChatSheetRow,
    copied: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val hue = tintHue(row.tint)
    val surface = OnyxSurfaces.surfaceHigh(theme)
    // La tuile et son glyphe : **neutres** — le gris et l'encre de la maison — pour toute ligne qui
    // n'est pas un état. Le **rouge du danger** garde, et lui seul, sa teinte diluée et son encre
    // rouges : une action qui ne se rattrape pas est un état, pas une famille.
    val destructive = row.destructive
    val tile = if (destructive) ChatActionBadge.fill(hue, surface) else ChatActionBadge.fillNeutral(theme)
    val glyph = if (destructive) ChatActionBadge.ink(hue, surface) else ChatActionBadge.inkNeutral(theme)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onyxTappable(onClick = onClick)
            .heightIn(min = SheetRowMinHeight)
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
                symbol = if (copied) Symbols.Checkmark else row.symbol,
                tint = glyph,
                size = ChatActionBadge.ICON_SIZE,
            )
        }
        BasicText(
            text = if (copied) OnyxLocalization["Copied"] else row.label,
            localize = !copied,
            style = theme.body.copy(color = SheetInk.label(theme, surface, dangerous = row.destructive)),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Le voile de la feuille : assez sombre pour détacher la surface, jamais au point de tout cacher. */
private val SheetScrim = Color.Black.copy(alpha = 0.42f)

// ---------------------------------------------------------------------------------------------
// Les secondes feuilles : consommation, processus, points de restauration
// ---------------------------------------------------------------------------------------------

/** Ce qu'une lecture a donné : rien encore, une réponse, ou un échec qui se dit. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Failed(val reason: String) : LoadState<Nothing>
}

/**
 * Le panneau d'une des trois listes du serveur — consommation, processus, points de restauration.
 *
 * Il **remplace** la feuille des actions dans la même fenêtre : aucune ligne de la feuille ne reste
 * dessous, donc aucune n'est recouverte ni coupée en deux. Sa première ligne est le **retour** —
 * visible, tappable, et doublé par le geste du système — puis son propre défilement, qui va
 * jusqu'à sa dernière ligne.
 */
@Composable
private fun ChatSheetPanel(
    notice: String?,
    reduceMotion: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val surface = OnyxSurfaces.surfaceHigh(theme)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SheetScrim)
            .onyxTappable(onClick = onBack),
    ) {
        AnimatedVisibility(
            visible = shown || reduceMotion,
            enter = if (reduceMotion) {
                EnterTransition.None
            } else {
                slideInVertically(
                    animationSpec = MotionFamily.Open.spec(reduceMotion),
                    initialOffsetY = { height -> height },
                ) + fadeIn(animationSpec = MotionFamily.Open.spec(reduceMotion))
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(SheetShape)
                    .background(surface)
                    // Le panneau n'est pas une zone de fermeture : appuyer entre deux lignes ne doit
                    // rien faire.
                    .onyxTappable(onClick = {})
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = SheetHandleWidth, height = SheetHandleHeight)
                            .background(theme.tertiaryText.copy(alpha = 0.55f), RoundedCornerShape(50)),
                    )
                }
                // Le retour : la première ligne du panneau. Une cible de la même famille que les
                // lignes de la feuille (52 dp, pastille teintée), pour qu'il se tape sans viser.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onyxTappable(onClick = onBack)
                        .heightIn(min = SheetRowMinHeight)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(ChatActionBadge.SIZE)
                            .background(
                                ChatActionBadge.fillNeutral(theme),
                                RoundedCornerShape(OnyxSpace.ControlCorner),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Le chevron de droite, **retourné** : la maison n'a pas de chevron gauche, et
                        // un retour se lit à son sens, pas à son nom de fichier.
                        OnyxIcon(
                            symbol = Symbols.ChevronRight,
                            tint = ChatActionBadge.inkNeutral(theme),
                            size = ChatActionBadge.ICON_SIZE,
                            modifier = Modifier.rotate(180f),
                        )
                    }
                    BasicText(
                        text = "Back to the actions",
                        style = theme.body.copy(color = SheetInk.label(theme, surface, dangerous = false)),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                notice?.let {
                    BasicText(
                        text = it,
                        localize = false,
                        style = theme.footnote.copy(color = SheetInk.muted(theme, surface)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    content()
                }
            }
        }
    }
}

/** Un titre de panneau : le nom de la feuille ouverte. */
@Composable
private fun PanelHeadline(text: String) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = text,
        style = theme.headline.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

/** Une ligne « libellé → valeur » d'un panneau. */
@Composable
private fun DetailRow(label: String, value: String, localizeLabel: Boolean = true) {
    val theme = LocalOnyxTheme.current
    val ink = SheetInk.label(theme, OnyxSurfaces.surfaceHigh(theme), dangerous = false)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            localize = localizeLabel,
            style = theme.body.copy(color = ink),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        BasicText(
            text = value,
            localize = false,
            style = theme.body.copy(color = ink),
            maxLines = 1,
        )
    }
}

/** Une phrase d'explication, plus petite, sous les chiffres — jamais sous 4,5:1. */
@Composable
private fun DetailNote(text: String) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = text,
        style = theme.caption.copy(color = SheetInk.muted(theme, OnyxSurfaces.surfaceHigh(theme))),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

/**
 * Une action de ligne de panneau (Arrêter, Restaurer, Relire).
 *
 * Sa teinte est celle du thème — le rouge du refus, l'accent de la maison — **écartée du fond**
 * juste assez pour tenir 4,5:1 : c'est un mot qui se touche, pas un simple trait.
 */
@Composable
private fun PanelAction(text: String, tint: Color, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    val surface = OnyxSurfaces.surfaceHigh(theme)
    BasicText(
        text = text,
        style = theme.body.copy(
            color = OnyxContrast.distinctOn(
                OnyxContrast.over(tint, surface),
                surface,
                OnyxContrast.TEXT_MIN,
            ),
            fontWeight = FontWeight.SemiBold,
        ),
        modifier = Modifier.onyxTappable(onClick = onClick),
    )
}

/**
 * Un échec de lecture, **dit en français et en clair** : ce que le serveur a refusé, pourquoi cela
 * arrive, et de quoi relire. Le code brut du serveur (« session not found ») ne s'affiche plus tel
 * quel — il est traduit par [ServeChatActions.failureReason].
 */
@Composable
private fun ReadingFailure(reason: String, onRetry: () -> Unit) {
    val theme = LocalOnyxTheme.current
    DetailNote(reason)
    DetailNote("Usage and its lists are only available while the conversation is running.")
    Box(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 4.dp)) {
        PanelAction("Retry the reading", theme.accent, onRetry)
    }
}

/** Ce qu'une lecture de consommation a rendu : les compteurs, et le compte du serveur. */
private data class UsageRead(val usage: ChatUsage, val account: ChatAccount)

/** « Cette conversation · 8,2 k entrée … » : un titre de section en petites capitales. */
@Composable
private fun SectionCaption(text: String) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = text,
        localize = false,
        style = theme.caption2.copy(
            color = SheetInk.muted(theme, OnyxSurfaces.surfaceHigh(theme)),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        ),
        modifier = Modifier.padding(
            start = OnyxSpace.Base, end = OnyxSpace.Base, top = OnyxSpace.Snug, bottom = OnyxSpace.Hair,
        ),
    )
}

/** Une tuile de chiffre : la valeur en monospace sur la ligne du haut, son libellé dessous. */
@Composable
private fun NumberTile(label: String, value: String, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    val surface = OnyxSurfaces.surfaceHigh(theme)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(OnyxSpace.TileCorner))
            .background(OnyxContrast.over(theme.tertiaryText.copy(alpha = 0.10f), surface))
            .padding(horizontal = OnyxSpace.Half, vertical = OnyxSpace.Half),
    ) {
        BasicText(
            text = value,
            localize = false,
            style = theme.headline.copy(
                color = SheetInk.label(theme, surface, dangerous = false),
                fontFamily = FontFamily.Monospace,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            text = label,
            style = theme.caption2.copy(color = SheetInk.muted(theme, surface)),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** La ligne « durée · pid · chemin » d'un processus, ou d'un point de restauration : en monospace. */
@Composable
private fun MonoMeta(text: String) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = text,
        localize = false,
        style = theme.monospacedFootnote.copy(color = SheetInk.muted(theme, OnyxSurfaces.surfaceHigh(theme))),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * « Consommation et coût » — le titre « Cette conversation · <modèle> », le montant et le nombre
 * d'appels au modèle, quatre tuiles de chiffres, **la fenêtre de contexte** (compteur, limite, barre
 * segmentée et sa légende), les **fichiers lus**, puis le **compte**.
 *
 * Chaque bloc n'apparaît que si le serveur le remplit ; un champ absent se dit (« — »), il ne vaut
 * jamais un zéro plausible, et un bloc que le serveur ne fournit pas se nomme (« le serveur
 * n'annonce pas … »).
 */
@Composable
private fun UsagePanel(
    state: LoadState<UsageRead>,
    onRetry: () -> Unit,
    fallback: com.onyx.app.ui.design.FallbackDeclaration? = null,
) {
    val theme = LocalOnyxTheme.current
    when (state) {
        is LoadState.Loading -> {
            PanelHeadline("Usage & cost")
            DetailNote("Reading the counters…")
        }

        is LoadState.Failed -> {
            PanelHeadline("Usage & cost")
            DetailNote("This server would not give the counters of this conversation.")
            ReadingFailure(state.reason, onRetry)
        }

        is LoadState.Ready -> {
            val usage = state.value.usage
            val account = state.value.account
            val model = usage.model?.takeIf { it.isNotBlank() }
            PanelHeadline(
                if (model != null) OnyxLocalization["This conversation"] + " · " + model
                else OnyxLocalization["This conversation"],
            )

            if (usage.isEmpty) {
                DetailNote("This server gives no token count for this conversation yet.")
            } else {
                // Le grand montant + le nombre d'appels au modèle, sur une ligne.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = OnyxSpace.Base, vertical = OnyxSpace.Half),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = ServeChatActions.money(usage.costUsd),
                        localize = false,
                        style = theme.title3.copy(
                            color = SheetInk.label(theme, OnyxSurfaces.surfaceHigh(theme), false),
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    BasicText(
                        text = "model calls: " + ServeChatActions.count(usage.apiCalls),
                        style = theme.caption.copy(color = SheetInk.muted(theme, OnyxSurfaces.surfaceHigh(theme))),
                    )
                }
                DetailNote(
                    if (usage.costUsd == null) {
                        "This server gives no price for this conversation; the counters above are all it has."
                    } else {
                        "Costs are the server's own estimate from provider pricing."
                    },
                )
                // Un tour servi par le **repli local** : le tarif par million du modèle configuré ne
                // s'applique pas — on le dit, au lieu de laisser lire un prix qui ne vaut pas.
                if (fallback?.servedIsLocal == true) {
                    DetailNote(
                        OnyxLocalization.format(
                            "This turn is served by a fallback model: %1\$s. The configured model is %2\$s.",
                            fallback.servedLine(),
                            fallback.configuredModel,
                        ),
                    )
                    DetailNote(OnyxLocalization["Served locally by the fallback — no price per million tokens."])
                }

                // Les quatre tuiles de chiffres.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = OnyxSpace.Base, vertical = OnyxSpace.Half),
                    horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
                ) {
                    NumberTile("Input", ServeChatActions.compact(usage.inputTokens), Modifier.weight(1f))
                    NumberTile("Output", ServeChatActions.compact(usage.outputTokens), Modifier.weight(1f))
                    NumberTile("Reasoning", ServeChatActions.compact(usage.reasoningTokens), Modifier.weight(1f))
                    NumberTile("From cache", ServeChatActions.compact(usage.cacheReadTokens), Modifier.weight(1f))
                }

                // La fenêtre de contexte : compteur / limite · pourcentage, la barre, puis la légende.
                SectionCaption(OnyxLocalization["What is filling the window"].uppercase())
                DetailRow(
                    "Context used",
                    if (usage.contextUsed != null || usage.contextMax != null) {
                        "${ServeChatActions.compact(usage.contextUsed)} / ${ServeChatActions.compact(usage.contextMax)} · ${ServeChatActions.percent(usage.contextPercent)}"
                    } else {
                        ServeChatActions.DASH
                    },
                )
                DetailRow("Cache share", ServeChatActions.percent(usage.cacheShare()))
                if (usage.contextSlices.isNotEmpty()) {
                    ContextBar(usage.contextSlices)
                    val total = usage.contextSlices.sumOf { it.tokens }.coerceAtLeast(1)
                    usage.contextSlices.forEach { slice -> ContextLegendRow(slice, total) }
                } else {
                    DetailNote("This server does not announce the breakdown by category.")
                }
                DetailNote(
                    when {
                        usage.contextEstimated == true ->
                            "The window is estimated from the text; the server did not give the exact count."
                        else ->
                            "Everything said here is read again with each message: this is what is filling the window."
                    },
                )

                // Les fichiers lus.
                SectionCaption(OnyxLocalization["Files read"].uppercase())
                if (usage.contextFiles.isEmpty()) {
                    DetailNote("This server does not announce which files fill the context.")
                } else {
                    usage.contextFiles.forEach { file ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = OnyxSpace.Base, vertical = OnyxSpace.Hair),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicText(
                                text = file.label,
                                localize = false,
                                style = theme.body.copy(color = SheetInk.label(theme, OnyxSurfaces.surfaceHigh(theme), false)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(OnyxSpace.Half))
                            MonoMeta(ServeChatActions.fileSize(file.chars) ?: ServeChatActions.DASH)
                        }
                    }
                }
            }

            // Le compte : la limite hebdomadaire et son échéance, ou l'aveu qu'il n'y en a pas.
            SectionCaption(OnyxLocalization["Account"].uppercase())
            if (!account.available) {
                DetailNote("This server publishes no account usage; no weekly limit or renewal date is announced.")
            } else {
                account.planName?.let { DetailRow("Plan", it, localizeLabel = false) }
                DetailRow("Weekly limit used", ServeChatActions.percent(account.planPercent))
                account.planSpentDisplay?.let { DetailRow("Spent", it, localizeLabel = false) }
                account.renewsDisplay?.let { DetailRow("Renews", it, localizeLabel = false) }
            }
        }
    }
}

/**
 * La barre segmentée de la fenêtre de contexte : un segment par poste, largeur proportionnelle à ses
 * jetons, sur l'accent du thème. Le poste le plus gros est le plus marqué — un seul accent, la
 * **taille** sépare les segments. Le trait médian respecte le rayon de la forme.
 */
@Composable
private fun ContextBar(slices: List<ChatContextSlice>) {
    val theme = LocalOnyxTheme.current
    val surface = OnyxSurfaces.surfaceHigh(theme)
    val total = slices.sumOf { it.tokens }.coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSpace.Base, vertical = OnyxSpace.Half)
            .height(10.dp)
            .clip(RoundedCornerShape(OnyxSpace.ControlCorner))
            .background(OnyxContrast.over(theme.tertiaryText.copy(alpha = 0.12f), surface)),
    ) {
        slices.forEach { slice ->
            val weight = slice.tokens.toFloat() / total
            if (weight > 0f) {
                Box(
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .background(theme.accent.copy(alpha = contextSliceAlpha(slice, total.toFloat()))),
                )
            }
        }
    }
}

/** Une ligne de la légende : sa pastille, le nom du poste (traduit), et ses jetons. */
@Composable
private fun ContextLegendRow(slice: ChatContextSlice, total: Int) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = OnyxSpace.Base, vertical = OnyxSpace.Hair),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(theme.accent.copy(alpha = contextSliceAlpha(slice, total.toFloat())), RoundedCornerShape(OnyxSpace.ControlCorner)),
        )
        BasicText(
            text = OnyxLocalization.translate(slice.label),
            localize = false,
            style = theme.body.copy(color = SheetInk.label(theme, OnyxSurfaces.surfaceHigh(theme), false)),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MonoMeta(ServeChatActions.compact(slice.tokens))
    }
}

/**
 * La teinte d'un poste de contexte : d'autant plus marquée que le poste est gros.
 *
 * Le serveur donne un jeton de sa feuille de style (`var(--context-usage-tools)`), que l'app ne
 * sait pas lire : c'est donc la **taille** du poste qui teinte sa pastille, sur l'accent du thème.
 * Un poste de part nulle garde quand même une pastille visible.
 */
internal fun contextSliceAlpha(slice: ChatContextSlice, total: Float): Float {
    val share = if (total > 0f) slice.tokens / total else 0f
    return (0.35f + share).coerceIn(0.35f, 1f)
}

/**
 * « Processus en arrière-plan » — le titre suivi d'un **point vert et du compte** (« 2 en cours »),
 * les rangées repliées (commande en monospace, `durée · pid · chemin`, pastille d'arrêt à contour
 * rouge), la rangée dépliée (la **sortie en direct**, en monospace), puis la section **TERMINÉS**
 * (coche, commande, `code de sortie · pid`, chevron).
 */
@Composable
private fun ProcessPanel(
    state: LoadState<List<BackgroundProcess>>,
    expandedId: String?,
    onToggle: (String) -> Unit,
    onRetry: () -> Unit,
    onStop: (String) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    when (state) {
        is LoadState.Loading -> {
            PanelHeadline("Background processes")
            DetailNote("Reading what is running…")
        }

        is LoadState.Failed -> {
            // La route a refusé, ou n'existe pas : la feuille le dit, au lieu d'afficher une liste
            // vide qu'on prendrait pour « rien ne tourne ».
            PanelHeadline("Background processes")
            DetailNote("This server would not give the background processes of this conversation.")
            ReadingFailure(state.reason, onRetry)
        }

        is LoadState.Ready -> {
            val running = state.value.filter { it.running }
            val finished = state.value.filterNot { it.running }

            // Le titre, puis le point vert et le compte des processus en cours.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = OnyxSpace.Base, vertical = OnyxSpace.Hair),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
            ) {
                PanelHeadline("Background processes")
                if (running.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                OnyxContrast.distinctOn(OnyxContrast.over(theme.success, OnyxSurfaces.surfaceHigh(theme)), OnyxSurfaces.surfaceHigh(theme), OnyxContrast.ICON_MIN),
                                RoundedCornerShape(50),
                            ),
                    )
                    BasicText(
                        text = OnyxLocalization.format("%1\$d running", running.size),
                        style = theme.caption.copy(color = SheetInk.muted(theme, OnyxSurfaces.surfaceHigh(theme))),
                    )
                }
            }

            if (state.value.isEmpty()) {
                DetailNote("Nothing is running in the background for this conversation.")
            } else {
                running.forEach { process ->
                    ProcessRow(process, expandedId == process.id, onToggle, onStop)
                }
                if (finished.isNotEmpty()) {
                    SectionCaption(OnyxLocalization["Done"].uppercase())
                    finished.forEach { process ->
                        FinishedProcessRow(process, expandedId == process.id, onToggle)
                    }
                }
            }
        }
    }
}

/** Une rangée de processus en cours : commande, `durée · pid · chemin`, arrêt, sortie repliable. */
@Composable
private fun ProcessRow(
    process: BackgroundProcess,
    expanded: Boolean,
    onToggle: (String) -> Unit,
    onStop: (String) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSpace.Base, vertical = OnyxSpace.Half),
        verticalArrangement = Arrangement.spacedBy(OnyxSpace.Hair),
    ) {
        BasicText(
            text = ServeChatActions.processSubject(process.command),
            localize = false,
            style = theme.monospacedFootnote.copy(color = SheetInk.label(theme, OnyxSurfaces.surfaceHigh(theme), false)),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.onyxTappable { onToggle(process.id) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoMeta(process.metaLine().ifBlank { ServeChatActions.DASH })
            Spacer(Modifier.weight(1f))
            StopPill { onStop(process.id) }
        }
        ProcessOutput(process, expanded)
    }
}

/** Une rangée de la section TERMINÉS : coche, commande, `code de sortie · pid`, chevron. */
@Composable
private fun FinishedProcessRow(
    process: BackgroundProcess,
    expanded: Boolean,
    onToggle: (String) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSpace.Base, vertical = OnyxSpace.Half),
        verticalArrangement = Arrangement.spacedBy(OnyxSpace.Hair),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
            modifier = Modifier.onyxTappable { onToggle(process.id) },
        ) {
            OnyxIcon(symbol = Symbols.CheckmarkCircle, tint = SheetInk.muted(theme, OnyxSurfaces.surfaceHigh(theme)), size = 16.dp)
            BasicText(
                text = ServeChatActions.processSubject(process.command),
                localize = false,
                style = theme.monospacedFootnote.copy(color = SheetInk.label(theme, OnyxSurfaces.surfaceHigh(theme), false)),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            OnyxIcon(
                symbol = if (expanded) Symbols.ChevronUp else Symbols.ChevronDown,
                tint = SheetInk.muted(theme, OnyxSurfaces.surfaceHigh(theme)),
                size = 14.dp,
            )
        }
        val exit = process.exitCode
        MonoMeta(
            buildString {
                append(if (exit != null) "exit code $exit" else ServeChatActions.DASH)
                process.pid?.let { append(" · pid $it") }
            },
        )
        ProcessOutput(process, expanded)
    }
}

/**
 * Ce qu'une ligne de processus dit de son état : en cours depuis N, terminé, ou en échec.
 *
 * **Arbitrage de fusion.** La refonte des trois vues a redessiné ces rangées (`metaLine()` :
 * durée · pid · chemin, puis le code de sortie) et supprimé ce libellé — dont dépend le verrou
 * `MenuLongNeufRangeesTest` du lot voisin (« relevé de l'agent »). Les deux apports sont gardés :
 * la rangée redessinée, et ce libellé, seule source de la phrase d'état d'un processus.
 */
internal fun processStatusLabel(process: BackgroundProcess): String {
    val duration = ServeChatActions.duration(process.uptimeSeconds)
    return when {
        process.running && duration != null -> OnyxLocalization.format("Running for %1\$s", duration)
        process.running -> OnyxLocalization["Running"]
        process.exitCode == 0 -> OnyxLocalization["Finished"]
        process.exitCode != null -> OnyxLocalization.format("Failed (exit code %1\$d)", process.exitCode)
        else -> OnyxLocalization["Finished"]
    }
}

/** La sortie d'un processus, dépliée : en monospace, plusieurs lignes. Jamais reconstruite. */
@Composable
private fun ProcessOutput(process: BackgroundProcess, expanded: Boolean) {
    val theme = LocalOnyxTheme.current
    if (!expanded) return
    val output = process.output
    if (output.isNullOrBlank()) {
        DetailNote("This server gave no output for this process.")
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(OnyxSpace.FieldCorner))
                .background(OnyxContrast.over(theme.tertiaryText.copy(alpha = 0.10f), OnyxSurfaces.surfaceHigh(theme)))
                .padding(OnyxSpace.Half),
        ) {
            BasicText(
                text = output,
                localize = false,
                style = theme.monospacedFootnote.copy(color = SheetInk.label(theme, OnyxSurfaces.surfaceHigh(theme), false)),
            )
        }
    }
}

/** La pastille d'arrêt : un contour rouge et un libellé, cible de 48 dp. */
@Composable
private fun StopPill(onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    val surface = OnyxSurfaces.surfaceHigh(theme)
    val ink = OnyxContrast.distinctOn(OnyxContrast.over(theme.error, surface), surface, OnyxContrast.TEXT_MIN)
    val shape = RoundedCornerShape(OnyxSpace.FieldCorner)
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(
                OnyxSpace.Stroke,
                OnyxContrast.distinctOn(OnyxContrast.over(theme.error, surface), surface, OnyxContrast.TEXT_MIN),
                shape,
            )
            .onyxTappable(onClick = onClick)
            .padding(horizontal = OnyxSpace.Snug),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Stop",
            style = theme.caption.copy(color = ink, fontWeight = FontWeight.SemiBold),
        )
    }
}

/**
 * « Points de restauration » — le titre et son sous-titre, les rangées repliées (tuile d'horloge,
 * nom, **date et heure + identifiant court en monospace**, pastille « Restaurer »), et la rangée
 * dépliée : **le diff** (ligne de compte, puis le diff unifié coloré).
 */
@Composable
private fun RestorePanel(
    state: LoadState<RestorePointList>,
    expandedId: String?,
    diff: LoadState<RestoreDiff?>,
    onToggle: (String) -> Unit,
    onRetry: () -> Unit,
    onRestore: (String) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    PanelHeadline("Restore points")
    when (state) {
        is LoadState.Loading -> DetailNote("Reading the restore points…")
        is LoadState.Failed -> {
            DetailNote("This server would not give the restore points of this conversation.")
            ReadingFailure(state.reason, onRetry)
        }

        is LoadState.Ready -> {
            val list = state.value
            when {
                !list.enabled -> DetailNote("This server does not keep restore points for this folder.")
                list.items.isEmpty() -> DetailNote("No restore point for this conversation yet.")
                else -> {
                    DetailNote("Snapshots taken before the agent changes files.")
                    list.items.forEach { point ->
                        RestoreRow(
                            point = point,
                            expanded = expandedId == point.hash,
                            diff = diff,
                            onToggle = { onToggle(point.hash) },
                            onRestore = { onRestore(point.hash) },
                        )
                    }
                }
            }
        }
    }
}

/** Une rangée de point de restauration : repliée (nom, date, identifiant, Restaurer) ou dépliée (diff). */
@Composable
private fun RestoreRow(
    point: RestorePoint,
    expanded: Boolean,
    diff: LoadState<RestoreDiff?>,
    onToggle: () -> Unit,
    onRestore: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val surface = OnyxSurfaces.surfaceHigh(theme)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = OnyxSpace.Base, vertical = OnyxSpace.Half),
        verticalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
            modifier = Modifier.onyxTappable(onClick = onToggle),
        ) {
            // La tuile d'horloge.
            Box(
                modifier = Modifier
                    .size(ChatActionBadge.SIZE)
                    .background(ChatActionBadge.fillNeutral(theme), RoundedCornerShape(OnyxSpace.ControlCorner)),
                contentAlignment = Alignment.Center,
            ) {
                OnyxIcon(symbol = Symbols.Clock, tint = ChatActionBadge.inkNeutral(theme), size = ChatActionBadge.ICON_SIZE)
            }
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = point.message.ifBlank { point.shortHash },
                    localize = false,
                    style = theme.body.copy(color = SheetInk.label(theme, surface, false)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                MonoMeta(
                    listOf(point.timestamp, point.shortHash)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { ServeChatActions.DASH },
                )
            }
            RestorePill(onRestore)
        }
        if (expanded) {
            RestoreDiffBody(diff)
        }
    }
}

/** « Restaurer » : une pastille teintée de l'accent, cible de 48 dp. */
@Composable
private fun RestorePill(onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    val surface = OnyxSurfaces.surfaceHigh(theme)
    val ink = OnyxContrast.distinctOn(OnyxContrast.over(theme.accent, surface), surface, OnyxContrast.TEXT_MIN)
    val shape = RoundedCornerShape(OnyxSpace.FieldCorner)
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(OnyxContrast.over(theme.accent.copy(alpha = 0.14f), surface))
            .onyxTappable(onClick = onClick)
            .padding(horizontal = OnyxSpace.Snug),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Restore",
            style = theme.caption.copy(color = ink, fontWeight = FontWeight.SemiBold),
        )
    }
}

/** Le corps déplié d'un point : la ligne de compte, puis le diff unifié à deux encres. */
@Composable
private fun RestoreDiffBody(diff: LoadState<RestoreDiff?>) {
    val theme = LocalOnyxTheme.current
    val surface = OnyxSurfaces.surfaceHigh(theme)
    when (diff) {
        is LoadState.Loading -> DetailNote("Reading the diff…")
        is LoadState.Failed -> DetailNote(diff.reason)
        is LoadState.Ready -> {
            val value = diff.value
            if (value == null) {
                DetailNote("This server gave no diff for this restore point.")
                return
            }
            DetailNote(value.countLine() ?: "This server gave no diff for this restore point.")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(OnyxSpace.FieldCorner))
                    .background(OnyxContrast.over(theme.tertiaryText.copy(alpha = 0.10f), surface))
                    .padding(OnyxSpace.Half),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(OnyxSpace.GlyphGap)) {
                    value.lines.forEach { line -> DiffLineRow(line) }
                }
            }
        }
    }
}

/**
 * Une ligne du diff unifié. **Deux encres distinctes** pour les lignes ajoutées et supprimées : ce
 * sont des **états** (le vert dit « ajouté », le rouge « supprimé »), pas des tuiles — elles gardent
 * donc leur couleur, et c'est le seul endroit du panneau où ce n'est pas l'accent du thème.
 */
@Composable
private fun DiffLineRow(line: DiffLine) {
    val theme = LocalOnyxTheme.current
    val surface = OnyxSurfaces.surfaceHigh(theme)
    val ink = when (line.kind) {
        DiffLineKind.ADD -> OnyxContrast.distinctOn(OnyxContrast.over(theme.success, surface), surface, OnyxContrast.TEXT_MIN)
        DiffLineKind.REMOVE -> OnyxContrast.distinctOn(OnyxContrast.over(theme.error, surface), surface, OnyxContrast.TEXT_MIN)
        DiffLineKind.HUNK -> OnyxContrast.distinctOn(OnyxContrast.over(theme.accent, surface), surface, OnyxContrast.ICON_MIN)
        DiffLineKind.FILE -> SheetInk.label(theme, surface, false)
        DiffLineKind.CONTEXT -> SheetInk.muted(theme, surface)
    }
    BasicText(
        text = line.text.ifEmpty { " " },
        localize = false,
        style = theme.monospacedFootnote.copy(color = ink),
    )
}
