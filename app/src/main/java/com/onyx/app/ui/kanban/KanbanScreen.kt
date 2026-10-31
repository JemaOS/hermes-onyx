package com.onyx.app.ui.kanban

import com.onyx.app.ui.design.BadgeGeometry
import com.onyx.app.ui.design.OnyxSpace

import com.onyx.app.ui.design.onyxPage

import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.ui.common.OnyxLoadingRows
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.data.model.serve.KanbanCard
import com.onyx.app.data.model.serve.KanbanStatus
import com.onyx.app.data.model.serve.relativeLabel
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.ChipRowFit
import com.onyx.app.ui.design.OnyxAlert
import com.onyx.app.ui.design.OnyxEmptyState
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxPrompt
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.OnyxToggleRow
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.design.tapLabel
import com.onyx.app.ui.localization.resolveOnyxText

/**
 * La réserve fixe de droite d'une carte : le « … » a sa propre colonne, donc le titre et le
 * résumé ne passent jamais dessous, quelle que soit la longueur du texte. 44 dp, la zone
 * touchable du guide de la référence — la capture du Magic V3 déplié montrait le bouton collé au texte.
 */
internal val KanbanRowMenuSize: Dp = 44.dp

/** Le plancher de hauteur d'une carte : un titre court ne tasse pas la rangée. */
internal val KanbanRowMinHeight: Dp = 72.dp

/**
 * Réglages → Kanban.
 *
 * Port de `KanbanView` : une colonne à la fois, son sélecteur en haut, ses cartes, et
 * l'éditeur d'une carte. Les colonnes, les priorités et les phrases sont celles de la référence, et le
 * tableau se relit tout seul quand le serveur le change (le socket d'événements du plugin).
 *
 * @param topRow la rangée du haut qu'référence passe à `KanbanView` (le sélecteur de section de la
 * liste) : en deux panneaux, le tableau porte la sienne au-dessus de ses capsules.
 */
@Composable
fun KanbanScreen(
    viewModel: KanbanViewModel,
    onBack: (() -> Unit)?,
    onAskOnyx: (String) -> Unit,
    modifier: Modifier = Modifier,
    topRow: (@Composable () -> Unit)? = null,
    /**
     * Le contrôle du volet (`sidebar.left`) quand le tableau **est** le volet des deux panneaux :
     * la barre le porte à gauche, comme celle de la liste des conversations.
     */
    sidebarControl: (@Composable () -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<KanbanCard?>(null) }
    var pendingDelete by remember { mutableStateOf<KanbanCard?>(null) }
    var movingCard by remember { mutableStateOf<Pair<KanbanCard, KanbanStatus>?>(null) }
    var moveNote by remember { mutableStateOf("") }

    // Le direct ne vit que tant que l'écran est là : la référence arrête son socket en
    // `onDisappear` et le reprend en `onAppear`.
    DisposableEffect(viewModel) {
        viewModel.startLive()
        onDispose { viewModel.stopLive() }
    }

    /**
     * Le glissement vers l'avant : la colonne suivante de la référence, tout de suite — sauf quand cette
     * colonne veut une raison (« What is it waiting on? »), que la feuille demande alors.
     */
    fun forward(card: KanbanCard) {
        val next = nextStatus(card) ?: return
        if (next.movePrompt != null) movingCard = card to next else viewModel.move(card, next)
    }

    when {
        state.draft != null -> KanbanEditorScreen(
            state = state,
            onBack = viewModel::cancelDraft,
            onSave = viewModel::saveDraft,
            onChange = viewModel::updateDraft,
            modifier = modifier,
        )

        state.detail != null -> KanbanDetailScreen(
            card = state.detail!!,
            working = state.working,
            onBack = viewModel::closeDetail,
            onEdit = { viewModel.edit(state.detail!!) },
            onMove = { status -> movingCard = state.detail!! to status },
            onDelete = { pendingDelete = state.detail },
            onComment = { body -> viewModel.comment(state.detail!!, body) },
            onAskOnyx = { question -> onAskOnyx(question) },
            modifier = modifier,
        )

        else -> Column(
            modifier = modifier
                .fillMaxSize()
                .onyxPage(theme)
                // La zone du système est prise par la racine de l'écran : la dernière rangée
                // du corps ne peut plus être enterrée sous la barre de navigation ni sous la
                // zone du geste d'accueil. L'encart est **lu à l'exécution** (24 dp en gestes,
                // 48 dp à trois boutons) — jamais une valeur en dur qui ne vaut que pour un écran.
                .navigationBarsPadding(),
        ) {
            Column(modifier = Modifier.statusBarsPadding()) {
                OnyxPageHeader(
                    title = "Kanban",
                    onBack = onBack,
                    leading = sidebarControl,
                    action = "New card",
                    onAction = viewModel::startNew,
                    actionEnabled = !state.needsDashboard,
                    // La référence pose un symbole de liaison à droite tant que le Dashboard
                    // répond : `arrow.clockwise` (secondaire) quand le direct n'écoute pas,
                    // `dot.radiowaves.left.and.right` (vert) quand il écoute.
                    trailingSymbol = when {
                        state.needsDashboard -> null
                        state.live -> Symbols.DotRadiowavesLeftAndRight
                        else -> Symbols.ArrowClockwise
                    },
                    trailingTint = if (state.live) theme.success else theme.secondaryText,
                    trailingLabel = if (state.live) "Live updates on" else "Polling for updates",
                )
            }

            // La rangée du haut de `KanbanView(topRow:)` : en deux panneaux, le sélecteur de
            // section vit dans le panneau du tableau, au-dessus de ses capsules.
            topRow?.invoke()

            ColumnCapsules(
                selected = state.column,
                counts = { column -> state.cardsIn(column).size },
                onSelect = viewModel::selectColumn,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 40.dp),
            ) {
                state.working?.let {
                    OnyxFormSection {
                        OnyxRow { BasicText(it, style = theme.footnote.copy(color = theme.secondaryText)) }
                    }
                }

                if (state.needsDashboard) {
                    KanbanEmptyState(
                        symbol = Symbols.RectangleSplit,
                        title = "Needs the Hermes Dashboard",
                        description = "The kanban board is part of the Hermes Dashboard. Add your Hermes Dashboard address and login in Settings.",
                    )
                } else {
                    state.problem?.let { problem ->
                        OnyxFormSection {
                            OnyxRow {
                                OnyxIcon(Symbols.WifiExclamation, tint = theme.warning, size = 16.dp)
                                BasicText(
                                    problem,
                                    localize = false,
                                    style = theme.footnote.copy(color = theme.secondaryText),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            OnyxRowDivider()
                            OnyxRow(onClick = viewModel::refresh) {
                                BasicText("Try Again", style = theme.body.copy(color = theme.accent))
                            }
                        }
                    }

                    when {
                        state.loading -> OnyxLoadingRows(label = "Loading from Onyx…")

                        state.cardsIn(state.column).isEmpty() -> KanbanEmptyState(
                            symbol = kanbanStatusSymbol(state.column),
                            title = OnyxLocalization.format("Nothing in %1\$s", OnyxLocalization[state.column.label]),
                        )

                        else -> Column(modifier = Modifier.fillMaxWidth()) {
                            state.cardsIn(state.column).forEachIndexed { index, card ->
                                if (index > 0) OnyxRowDivider()
                                KanbanRow(
                                    card = card,
                                    onClick = { viewModel.openDetail(card) },
                                    onMenu = { menuFor = card },
                                    // Le glissement vers l'avant de la référence : la colonne suivante d'un
                                    // coup, avec la raison demandée quand la colonne en veut une.
                                    onForward = { forward(card) },
                                    onDelete = { pendingDelete = card },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    menuFor?.let { card ->
        OnyxMenu(
            items = buildList {
                add(
                    MenuItem("Edit", Symbols.Pencil) {
                        menuFor = null
                        viewModel.edit(card)
                    },
                )
                nextStatus(card)?.let { next ->
                    add(
                        MenuItem("Move to ${next.label}", kanbanStatusSymbol(next)) {
                            menuFor = null
                            movingCard = card to next
                        },
                    )
                }
                add(
                    MenuItem("Delete", Symbols.Trash, destructive = true) {
                        menuFor = null
                        pendingDelete = card
                    },
                )
            },
            onDismiss = { menuFor = null },
        )
    }

    pendingDelete?.let { card ->
        OnyxAlert(
            title = "Delete “${card.title}”?",
            message = "It is removed from the board. This can't be undone.",
            actions = listOf(
                AlertAction("Cancel") { pendingDelete = null },
                AlertAction("Delete", destructive = true) {
                    val target = card
                    pendingDelete = null
                    viewModel.delete(target)
                },
            ),
            onDismiss = { pendingDelete = null },
        )
    }

    movingCard?.let { (card, status) ->
        val prompt = status.movePrompt
        if (prompt != null) {
            OnyxPrompt(
                title = "Move to ${status.label}",
                message = prompt,
                placeholder = if (status == KanbanStatus.SCHEDULED || status == KanbanStatus.BLOCKED) "Reason" else "Summary",
                value = moveNote,
                onValueChange = { moveNote = it },
                confirmLabel = "Move",
                onConfirm = {
                    movingCard = null
                    val note = moveNote
                    moveNote = ""
                    viewModel.move(card, status, note)
                },
                onDismiss = { movingCard = null },
            )
        } else {
            OnyxAlert(
                title = "Move to ${status.label}?",
                actions = listOf(
                    AlertAction("Cancel") { movingCard = null },
                    AlertAction("Move") {
                        movingCard = null
                        viewModel.move(card, status)
                    },
                ),
                onDismiss = { movingCard = null },
            )
        }
    }
}

@Composable
private fun KanbanEmptyState(symbol: Int, title: String, description: String? = null) {
    OnyxEmptyState(symbol = symbol, title = title, description = description)
}

/** La rangée de capsules qui choisit la colonne (`KanbanView` la garde en haut, épinglée). */
@Composable
private fun ColumnCapsules(
    selected: KanbanStatus,
    counts: (KanbanStatus) -> Int,
    onSelect: (KanbanStatus) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints {
        // **La rangée tient, elle ne se coupe pas.** Dans le volet du pliable replié (340 dp), les
        // capsules de colonne débordaient : il n'en restait qu'un fragment de bord au droit, et
        // Compose ne dessine aucun indicateur de défilement — ce fragment se lit comme un défaut.
        // La maison a une règle pour ça ([ChipRowFit]) : on **mesure** et on **réduit** d'un
        // demi-point jusqu'à ce que la rangée tienne, jamais sous le plancher de lisibilité.
        val available = maxWidth.value
        val gapDp = 6f
        // Le dégagement d'une capsule : ses marges, son icône et l'écart avant le libellé.
        val chipInsetDp = 10f * 2f + 13f + 4f
        val measure: (String, Float) -> Float = { text, size ->
            val style = theme.footnote.copy(fontSize = size.sp, fontWeight = FontWeight.Medium)
            with(density) {
                measurer.measure(text = text, style = style, maxLines = 1).size.width.toDp().value
            }
        }
        // Le compte est mesuré **avec** le libellé : c'est la même capsule qui doit tenir.
        val widestPerColumn = { size: Float ->
            KanbanStatus.entries.map { column ->
                val count = counts(column)
                val label = if (count > 0) "${column.label}  $count" else column.label
                measure(label, size)
            }
        }
        val plan = ChipRowFit.plan(
            availableDp = available,
            chipInsetDp = chipInsetDp,
            gapDp = gapDp,
            baseSp = theme.footnote.fontSize.value,
        ) { size, _ -> widestPerColumn(size) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Qui tient ne défile pas : le défilement reste le dernier recours, avec des
                // libellés entiers — il ne coupe aucun mot.
                .then(if (plan.fits) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(gapDp.dp),
        ) {
            KanbanStatus.entries.forEach { column ->
                val active = column == selected
                Row(
                    modifier = Modifier
                        .background(
                            color = if (active) theme.accent else theme.fill,
                            shape = RoundedCornerShape(50),
                        )
                        .onyxClickable(pressedScale = 0.96f) { onSelect(column) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OnyxIcon(
                        symbol = kanbanStatusSymbol(column),
                        tint = if (active) theme.userText else theme.textColor,
                        size = 13.dp,
                    )
                    BasicText(
                        text = column.label,
                        style = theme.footnote.copy(
                            color = if (active) theme.userText else theme.textColor,
                            fontWeight = FontWeight.Medium,
                            fontSize = plan.fontSizeSp.sp,
                        ),
                        maxLines = 1,
                        softWrap = false,
                    )
                    val count = counts(column)
                    if (count > 0) {
                        BasicText(
                            text = count.toString(),
                            style = theme.caption2.copy(
                                color = if (active) theme.userText else theme.textColor,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            modifier = Modifier
                                .background(
                                    if (active) Color.White.copy(alpha = 0.25f) else theme.accent.copy(alpha = 0.15f),
                                    RoundedCornerShape(50),
                                )
                                .padding(horizontal = 5.dp, vertical = BadgeGeometry.PadVertical),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KanbanRow(
    card: KanbanCard,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val next = nextStatus(card)
    KanbanSwipeRow(
        forward = next?.let { status ->
            KanbanSwipeAction(
                label = status.label,
                symbol = kanbanStatusSymbol(status),
                tint = theme.accent,
                foreground = theme.userText,
            )
        },
        trailing = KanbanSwipeAction(
            label = "Delete",
            symbol = Symbols.Trash,
            tint = theme.error,
            foreground = Color.White,
        ),
        onForward = onForward,
        onTrailing = onDelete,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = KanbanRowMinHeight)
                .onyxTappable(onClick = onClick)
                .padding(start = 16.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
            // Le haut, pas le centre : le « … » reste sur la ligne du titre, il ne descend pas
            // jusqu'au résumé d'une carte à deux lignes.
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(
                        text = card.title,
                        localize = false,
                        style = theme.body.copy(color = theme.textColor, fontWeight = FontWeight.Medium),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (card.priority != 0) {
                        val priorityTint = if (card.priority > 0) theme.warning else theme.secondaryText
                        BasicText(
                            text = "P${card.priority}",
                            localize = false,
                            style = theme.caption2.copy(color = priorityTint, fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            modifier = Modifier
                                .background(priorityTint.copy(alpha = 0.15f), RoundedCornerShape(50))
                                .padding(horizontal = 5.dp, vertical = BadgeGeometry.PadVertical),
                        )
                    }
                }
                (card.summary ?: card.body)?.takeIf { it.isNotBlank() }?.let {
                    BasicText(
                        text = it.replace('\n', ' '),
                        localize = false,
                        style = theme.footnote.copy(color = theme.secondaryText),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    card.assignee?.takeIf { it.isNotBlank() }?.let { assignee ->
                        OnyxIcon(Symbols.Person, tint = theme.secondaryText, size = 12.dp)
                        BasicText(
                            text = assignee,
                            localize = false,
                            style = theme.caption.copy(color = theme.secondaryText),
                            maxLines = 1,
                        )
                    }
                    // `task.createdAt.relativeLabel` : l'âge (« 1 hr. ago »), pas l'estampille.
                    cardAge(card)?.let { age ->
                        BasicText(text = age, localize = false, style = theme.caption.copy(color = theme.secondaryText), maxLines = 1)
                    }
                    if (card.commentCount > 0) {
                        OnyxIcon(Symbols.TextBubble, tint = theme.secondaryText, size = 12.dp)
                        BasicText(
                            text = card.commentCount.toString(),
                            style = theme.caption.copy(color = theme.secondaryText),
                            maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (card.failed) {
                        OnyxIcon(Symbols.ExclamationTriangle, tint = theme.error, size = 12.dp)
                    }
                }
            }
            // La réserve de droite, de largeur fixe : quoi qu'écrive la carte, le texte s'arrête
            // chevauche jamais ni l'un ni l'autre. C'est aussi la zone touchable de 44 dp.
            Box(
                modifier = Modifier
                    .size(KanbanRowMenuSize)
                    .onyxClickable(onClick = onMenu)
                    .tapLabel(resolveOnyxText("Card menu")),
                contentAlignment = Alignment.TopCenter,
            ) {
                OnyxIcon(
                    symbol = Symbols.Ellipsis,
                    tint = theme.secondaryText,
                    size = 16.dp,
                )
            }
        }
    }
}

/**
 * L'âge d'une carte, comme la rangée de la référence : `created_at` arrive en secondes d'époque, et c'est
 * `relativeLabel` qui en fait « 1 hr. ago ». Une estampille déjà lisible est rendue telle quelle.
 */
internal fun cardAge(
    card: KanbanCard,
    nowMillis: Long = System.currentTimeMillis(),
    locale: java.util.Locale = OnyxLanguage.preferred,
): String? = card.createdAtMillis?.let { relativeLabel(it, nowMillis, locale) } ?: card.createdAt

/** `Created` du détail : la date de la référence, ou la valeur brute quand le serveur ne donne pas d'heure. */
internal fun cardCreatedLabel(card: KanbanCard): String? =
    card.createdAtMillis?.let {
        java.text.SimpleDateFormat("d MMM, HH:mm", OnyxLanguage.preferred).format(java.util.Date(it))
    } ?: card.createdAt

@Composable
private fun KanbanEditorScreen(
    state: KanbanUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onChange: ((KanbanDraft) -> KanbanDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val draft = state.draft ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme)
            // La zone du système est prise par la racine de l'écran : la dernière rangée du
            // corps ne peut plus être enterrée sous la barre de navigation ni sous la zone du
            // geste d'accueil. L'encart est **lu à l'exécution** (24 dp en navigation par
            // gestes, 48 dp à trois boutons, et ce qu'il faut sur un pliable déplié) — jamais
            // une valeur en dur qui ne vaut que pour un écran.
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = if (draft.id == null) "New card" else "Edit card",
                onBack = onBack,
                action = if (state.saving) "Saving…" else "Save",
                onAction = onSave,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            OnyxFormSection {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OnyxTextField(
                        value = draft.title,
                        onValueChange = { value -> onChange { it.copy(title = value) } },
                        placeholder = "Title",
                        style = theme.body,
                    )
                }
                OnyxRowDivider()
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OnyxTextField(
                        value = draft.body,
                        onValueChange = { value -> onChange { it.copy(body = value) } },
                        placeholder = "Details for the worker",
                        singleLine = false,
                        maxLines = 6,
                    )
                }
            }

            OnyxFormSection {
                PriorityRow(
                    value = draft.priority,
                    onChange = { value -> onChange { it.copy(priority = value) } },
                )
                OnyxRowDivider()
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    BasicText("Assignee", style = theme.body.copy(color = theme.textColor), modifier = Modifier.weight(0.7f))
                    OnyxTextField(
                        value = draft.assignee,
                        onValueChange = { value -> onChange { it.copy(assignee = value) } },
                        placeholder = "Assignee (profile)",
                        style = theme.body,
                        modifier = Modifier.weight(1.3f),
                    )
                }
                if (draft.id == null) {
                    OnyxRowDivider()
                    OnyxToggleRow(
                        label = "Start right away (Ready)",
                        checked = draft.startReady,
                        onCheckedChange = { value -> onChange { it.copy(startReady = value) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun PriorityRow(value: Int, onChange: (Int) -> Unit) {
    val theme = LocalOnyxTheme.current
    OnyxRow {
        BasicText("Priority $value", style = theme.body.copy(color = theme.textColor), modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .background(theme.fill, RoundedCornerShape(OnyxSpace.ControlCorner))
                .onyxClickable { onChange((value + 1) % 6) }
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            BasicText("＋", style = theme.body.copy(color = theme.accent))
        }
        Box(modifier = Modifier.padding(start = 8.dp)) {
            BasicText(
                text = "−",
                style = theme.body.copy(color = if (value > 0) theme.accent else theme.tertiaryText),
                modifier = Modifier
                    .background(theme.fill, RoundedCornerShape(OnyxSpace.ControlCorner))
                    .onyxClickable(enabled = value > 0) { onChange((value - 1).coerceAtLeast(0)) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun KanbanDetailScreen(
    card: KanbanCard,
    working: String?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMove: (KanbanStatus) -> Unit,
    onDelete: () -> Unit,
    onComment: (String) -> Unit,
    onAskOnyx: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    var comment by remember(card.id) { mutableStateOf("") }
    var showStatuses by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme)
            // La zone du système est prise par la racine de l'écran : la dernière rangée du
            // corps ne peut plus être enterrée sous la barre de navigation ni sous la zone du
            // geste d'accueil. L'encart est **lu à l'exécution** (24 dp en navigation par
            // gestes, 48 dp à trois boutons, et ce qu'il faut sur un pliable déplié) — jamais
            // une valeur en dur qui ne vaut que pour un écran.
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = card.title, onBack = onBack, action = "Edit", onAction = onEdit, localizeTitle = false)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            working?.let {
                OnyxFormSection {
                    OnyxRow { BasicText(it, style = theme.footnote.copy(color = theme.secondaryText)) }
                }
            }

            OnyxFormSection {
                OnyxRow(onClick = { showStatuses = !showStatuses }) {
                    BasicText("Status", style = theme.body.copy(color = theme.textColor), modifier = Modifier.weight(1f))
                    BasicText(
                        KanbanStatus.fromId(card.status).label,
                        style = theme.body.copy(color = theme.accent),
                    )
                    OnyxIcon(Symbols.ChevronRight, tint = theme.tertiaryText, size = 12.dp)
                }
                if (showStatuses) {
                    KanbanStatus.entries.forEach { status ->
                        OnyxRowDivider()
                        OnyxRow(onClick = {
                            showStatuses = false
                            if (status != KanbanStatus.fromId(card.status)) onMove(status)
                        }) {
                            OnyxIcon(
                                symbol = if (status == KanbanStatus.fromId(card.status)) Symbols.CheckmarkCircle else Symbols.Circle,
                                tint = if (status == KanbanStatus.fromId(card.status)) theme.accent else theme.tertiaryText,
                                size = 18.dp,
                            )
                            BasicText(
                                text = status.label,
                                style = theme.body.copy(color = theme.textColor),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                OnyxRowDivider()
                MetaRow("Priority", if (card.priority > 0) "P${card.priority}" else OnyxLocalization["None"])
                OnyxRowDivider()
                MetaRow("Assignee", card.assignee ?: OnyxLocalization["Unassigned"])
                cardCreatedLabel(card)?.let {
                    OnyxRowDivider()
                    MetaRow("Created", it)
                }
                card.completedAt?.let {
                    OnyxRowDivider()
                    MetaRow("Completed", it)
                }
            }

            card.body?.takeIf { it.isNotBlank() }?.let { body ->
                OnyxFormSection(header = "Details") {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                        BasicText(body, localize = false, style = theme.body.copy(color = theme.textColor))
                    }
                }
            }

            card.result?.takeIf { it.isNotBlank() }?.let { result ->
                OnyxFormSection(header = "Result") {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                        BasicText(result, localize = false, style = theme.body.copy(color = theme.textColor))
                    }
                }
            }

            card.lastFailure?.takeIf { it.isNotBlank() }?.let { failure ->
                OnyxFormSection(header = "Last failure") {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                        BasicText(failure, localize = false, style = theme.footnote.copy(color = theme.error))
                    }
                }
            }

            OnyxFormSection(header = "Comments") {
                if (card.comments.isEmpty()) {
                    OnyxRow { BasicText("No comments yet", style = theme.body.copy(color = theme.secondaryText)) }
                } else {
                    card.comments.forEachIndexed { index, c ->
                        if (index > 0) OnyxRowDivider()
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)) {
                            BasicText(
                                text = "${c.author}${c.at?.let { " · $it" }.orEmpty()}",
                                localize = false,
                                style = theme.caption.copy(color = theme.tertiaryText),
                            )
                            BasicText(text = c.body, localize = false, style = theme.body.copy(color = theme.textColor))
                        }
                    }
                }
                OnyxRowDivider()
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OnyxTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        placeholder = "Add a comment",
                        style = theme.body,
                        modifier = Modifier.weight(1f),
                    )
                    BasicText(
                        text = "Post",
                        style = theme.body.copy(
                            color = if (comment.isBlank()) theme.tertiaryText else theme.accent,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        modifier = Modifier.onyxClickable(enabled = comment.isNotBlank()) {
                            val body = comment
                            comment = ""
                            onComment(body)
                        },
                    )
                }
            }

            OnyxFormSection(header = "Ask Onyx about this card") {
                OnyxRow(onClick = {
                    onAskOnyx("Tell me about the kanban card “${card.title}” (${card.id}) and what to do next.")
                }) {
                    OnyxIcon(Symbols.SquareAndPencil, tint = theme.textColor, size = 17.dp)
                    BasicText(
                        text = "Ask in a new conversation",
                        style = theme.body.copy(color = theme.accent),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            OnyxFormSection {
                OnyxRow(onClick = onDelete) {
                    BasicText("Delete", style = theme.body.copy(color = theme.error))
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    val theme = LocalOnyxTheme.current
    OnyxRow {
        BasicText(label, style = theme.body.copy(color = theme.textColor), modifier = Modifier.weight(1f))
        BasicText(value, localize = false, style = theme.body.copy(color = theme.secondaryText))
    }
}

/** Les symboles de `KanbanStatus.icon`, y compris les états qui ne sont pas déplaçables à la main. */
internal fun kanbanStatusSymbol(status: KanbanStatus): Int = when (status) {
    KanbanStatus.TRIAGE -> Symbols.Tray
    KanbanStatus.TODO -> Symbols.Circle
    KanbanStatus.SCHEDULED -> Symbols.Calendar
    KanbanStatus.READY -> Symbols.PlayCircle
    KanbanStatus.RUNNING -> Symbols.Gearshape
    KanbanStatus.BLOCKED -> Symbols.HandRaised
    KanbanStatus.REVIEW -> Symbols.Eye
    KanbanStatus.DONE -> Symbols.CheckmarkCircleOutline
    KanbanStatus.ARCHIVED -> Symbols.ArchiveBox
}

/** La colonne suivante, celle que le balayage de la référence propose. */
private fun nextStatus(card: KanbanCard): KanbanStatus? = when (KanbanStatus.fromId(card.status)) {
    KanbanStatus.TRIAGE -> KanbanStatus.TODO
    KanbanStatus.TODO, KanbanStatus.BLOCKED -> KanbanStatus.READY
    KanbanStatus.REVIEW -> KanbanStatus.DONE
    KanbanStatus.DONE -> KanbanStatus.ARCHIVED
    else -> null
}
