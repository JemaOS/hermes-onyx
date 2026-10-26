package com.onyx.app.ui.cron

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.CronJob
import com.onyx.app.data.model.serve.RoutineWhen
import com.onyx.app.data.model.serve.relativeLabel
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxEmptyState
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Les planifications d'un seul bot, avec `RoutineEditor`.
 *
 * La rangée s'ouvre pour être modifiée; un appui long offre Run Now, Pause/Resume et Delete,
 * tandis qu'un glissement révèle Pause/Resume à gauche et Delete à droite. Chaque réponse est
 * livrée dans la conversation du bot, jamais dans une cible choisie par l'utilisateur.
 */
@Composable
fun BotSchedulesScreen(
    viewModel: CronViewModel,
    bot: BotProfile,
    mainName: String,
    reachable: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val name = bot.label(mainName)
    var menuFor by remember { mutableStateOf<CronJob?>(null) }

    // Le retour Android ferme d'abord l'éditeur, puis la feuille. Il reste consommé pendant un
    // enregistrement, comme `interactiveDismissDisabled(working)`.
    BackHandler {
        if (!state.saving) {
            if (state.draft != null) viewModel.cancelDraft() else onBack()
        }
    }

    val draft = state.draft
    if (draft != null) {
        RoutineEditorScreen(
            state = state,
            botName = name,
            job = state.jobs.firstOrNull { it.id == draft.id },
            onBack = { if (!state.saving) viewModel.cancelDraft() },
            onSave = viewModel::saveDraft,
            onChange = viewModel::updateDraft,
            modifier = modifier,
        )
    } else {
        BotSchedulesList(
            state = state,
            name = name,
            reachable = reachable,
            onBack = onBack,
            onNew = viewModel::startNew,
            onEdit = viewModel::edit,
            onMenu = { menuFor = it },
            onTogglePause = { job ->
                if (job.paused) viewModel.resume(job) else viewModel.pause(job)
            },
            onDelete = viewModel::delete,
            onRetry = viewModel::refresh,
            modifier = modifier,
        )
    }

    menuFor?.let { job ->
        OnyxMenu(
            items = buildList {
                add(
                    MenuItem("Run Now", Symbols.BoltOutline) {
                        menuFor = null
                        viewModel.runNow(job)
                    },
                )
                if (job.state == "scheduled" || job.state == "paused" || job.paused) {
                    add(
                        MenuItem(if (job.paused) "Resume" else "Pause", if (job.paused) Symbols.PlayOutline else Symbols.PauseOutline) {
                            menuFor = null
                            if (job.paused) viewModel.resume(job) else viewModel.pause(job)
                        },
                    )
                }
                add(
                    MenuItem("Delete", Symbols.Trash, destructive = true) {
                        menuFor = null
                        viewModel.delete(job)
                    },
                )
            },
            onDismiss = { menuFor = null },
        )
    }
}

@Composable
private fun BotSchedulesList(
    state: CronUiState,
    name: String,
    reachable: Boolean,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (CronJob) -> Unit,
    onMenu: (CronJob) -> Unit,
    onTogglePause: (CronJob) -> Unit,
    onDelete: (CronJob) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val scrollState = rememberScrollState()
    val refreshThreshold = with(LocalDensity.current) { 120.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "$name's Schedules", onBack = null, action = "Done", onAction = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .pointerInput(reachable, state.loading) {
                    if (!reachable || state.loading) return@pointerInput
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            if (scrollState.value == 0 && amount > 0f) {
                                pull = (pull + amount).coerceAtMost(refreshThreshold * 1.5f)
                                change.consume()
                            } else if (pull > 0f && amount < 0f) {
                                pull = (pull + amount).coerceAtLeast(0f)
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            if (pull >= refreshThreshold) onRetry()
                            pull = 0f
                        },
                        onDragCancel = { pull = 0f },
                    )
                }
                .navigationBarsPadding()
                .padding(bottom = 40.dp),
        ) {
            if (pull > 0f || (state.loading && state.jobs.isNotEmpty())) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ScheduleSpinner(size = 18.dp)
                }
            }
            if (!reachable) {
                ScheduleUnavailable(
                    title = "Needs the Hermes Dashboard",
                    description = "A bot's schedules are kept with its other settings on your server, through the Dashboard. Add its login in Settings → Hermes server.",
                    symbol = Symbols.ClockBadgeQuestionmark,
                )
            } else {
                state.problem?.let { problem ->
                    OnyxFormSection {
                        OnyxRow {
                            OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 17.dp)
                            BasicText(
                                text = problem,
                                localize = false,
                                style = theme.footnote.copy(color = theme.warning),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OnyxRowDivider()
                        OnyxRow(onClick = onRetry) {
                            BasicText("Try Again", style = theme.body.copy(color = theme.accent))
                        }
                    }
                }

                when {
                    state.loading && state.jobs.isEmpty() && state.problem == null -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ScheduleSpinner(size = 26.dp)
                        }
                    }

                    state.jobs.isEmpty() && state.problem == null -> {
                        ScheduleUnavailable(
                            title = "No schedules yet",
                            description = "Give $name something to do at a time: every weekday at 7, check the weather and your calendar and say what to wear. Its answer comes into its chat.",
                        )
                    }

                    state.jobs.isNotEmpty() -> OnyxFormSection {
                        state.jobs.forEachIndexed { index, job ->
                            if (index > 0) OnyxRowDivider()
                            SwipeScheduleRow(
                                job = job,
                                enabled = job.id !in state.busy,
                                onTogglePause = { onTogglePause(job) },
                                onDelete = { onDelete(job) },
                            ) {
                                BotScheduleRow(
                                    job = job,
                                    botName = name,
                                    busy = job.id in state.busy,
                                    onClick = { onEdit(job) },
                                    onMenu = { onMenu(job) },
                                )
                            }
                        }
                    }
                }

                OnyxFormSection(
                    footer = "Each run is $name's, with its own personality and tools, and its answer comes into its chat, with a notification, while your phone is off. Times are your server's. Hermes 0.21.5 and later run a bot's schedules by themselves; earlier ones only with gateway.multiplex_profiles on.",
                ) {
                    OnyxRow(onClick = onNew) {
                        OnyxIcon(Symbols.Plus, tint = theme.accent, size = 19.dp)
                        BasicText("New Schedule", style = theme.body.copy(color = theme.accent))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BotScheduleRow(
    job: CronJob,
    botName: String,
    busy: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val missed = job.isMissed()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surfaceColor)
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        OnyxIcon(
            symbol = when {
                job.paused -> Symbols.PauseCircle
                missed -> Symbols.ExclamationCircleOutline
                else -> Symbols.Clock
            },
            tint = when {
                job.paused -> theme.secondaryText
                missed -> theme.warning
                else -> theme.accent
            },
            size = 17.dp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BasicText(
                    text = job.displayTitle,
                    localize = false,
                    style = theme.body.copy(color = theme.textColor, fontWeight = FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (job.sharedWithDesktop) {
                    BasicText(
                        text = "Shared",
                        style = theme.caption2.copy(color = theme.secondaryText),
                        modifier = Modifier
                            .background(theme.fill, RoundedCornerShape(50))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            BasicText(
                text = RoutineWhen.parse(job.schedule)?.summary() ?: job.schedule,
                localize = RoutineWhen.parse(job.schedule) != null,
                style = theme.footnote.copy(color = theme.secondaryText),
            )
            val nextRunAt = job.nextRunAt
            val status = when {
                busy -> "Running…"
                job.paused -> "Paused"
                missed && nextRunAt != null -> OnyxLocalization.format(
                    "Didn't run %1\$s: your server isn't running %2\$s's schedules",
                    relativeLabel(nextRunAt),
                    botName,
                )
                nextRunAt != null -> OnyxLocalization.format("Next %1\$s", relativeLabel(nextRunAt))
                else -> null
            }
            status?.let {
                BasicText(
                    text = it,
                    style = theme.caption.copy(color = if (missed && !busy) theme.warning else theme.secondaryText),
                )
            }
        }
        if (busy) {
            Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                ScheduleSpinner(size = 16.dp)
            }
        }
    }
}

/** Les actions de balayage: pause/reprise à gauche, suppression à droite. */
@Composable
private fun SwipeScheduleRow(
    job: CronJob,
    enabled: Boolean,
    onTogglePause: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val density = LocalDensity.current
    val pauseWidth = 92.dp
    val deleteWidth = 88.dp
    val pausePx = with(density) { pauseWidth.toPx() }
    val deletePx = with(density) { deleteWidth.toPx() }
    val canPause = job.state == "scheduled" || job.state == "paused" || job.paused
    val leftLimit = if (canPause) pausePx else 0f
    var offset by remember(job.id) { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.matchParentSize()) {
            if (canPause) {
                SwipeAction(
                    label = if (job.paused) "Resume" else "Pause",
                    symbol = if (job.paused) Symbols.Play else Symbols.Pause,
                    color = if (job.paused) theme.success else theme.warning,
                    width = pauseWidth,
                    onClick = {
                        offset = 0f
                        onTogglePause()
                    },
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            SwipeAction(
                label = "Delete",
                symbol = Symbols.Trash,
                color = theme.error,
                width = deleteWidth,
                onClick = {
                    offset = 0f
                    onDelete()
                },
            )
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .pointerInput(job.id, enabled, leftLimit, deletePx) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            offset = (offset + amount).coerceIn(-deletePx, leftLimit)
                            change.consume()
                        },
                        onDragEnd = {
                            offset = when {
                                offset > leftLimit / 2f -> leftLimit
                                offset < -deletePx / 2f -> -deletePx
                                else -> 0f
                            }
                        },
                        onDragCancel = { offset = 0f },
                    )
                },
        ) {
            content()
        }
    }
}

@Composable
private fun SwipeAction(
    label: String,
    symbol: Int,
    color: androidx.compose.ui.graphics.Color,
    width: Dp,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(color)
            .onyxClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnyxIcon(symbol, tint = androidx.compose.ui.graphics.Color.White, size = 17.dp)
        BasicText(
            text = label,
            style = LocalOnyxTheme.current.caption.copy(color = androidx.compose.ui.graphics.Color.White),
        )
    }
}

@Composable
private fun RoutineEditorScreen(
    state: CronUiState,
    botName: String,
    job: CronJob?,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onChange: ((CronDraft) -> CronDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val draft = state.draft ?: return
    val initial = remember(draft.id) { RoutineWhen.parse(draft.schedule) }
    var whenState by remember(draft.id) { mutableStateOf(initial ?: RoutineWhen()) }
    var custom by remember(draft.id) { mutableStateOf(if (initial == null) draft.schedule else null) }
    var showRepeats by remember(draft.id) { mutableStateOf(false) }

    fun updateWhen(next: RoutineWhen) {
        whenState = next
        custom = null
        onChange { it.copy(schedule = next.schedule) }
    }

    val canSave = !state.saving &&
        draft.title.trim().isNotEmpty() &&
        draft.prompt.trim().isNotEmpty() &&
        draft.schedule.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor)
            .imePadding(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = if (draft.id == null) "New Schedule" else "Schedule",
                onBack = onBack,
                action = if (state.saving) "Saving…" else if (draft.id == null) "Add" else "Save",
                onAction = onSave,
                actionEnabled = canSave,
                backLabel = "Cancel",
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 40.dp),
        ) {
            OnyxFormSection(header = "Name") {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
                    OnyxTextField(
                        value = draft.title,
                        onValueChange = { value -> onChange { it.copy(title = value) } },
                        placeholder = "What to wear",
                    )
                }
            }

            OnyxFormSection(
                header = "What $botName does",
                footer = "As you would ask it in its chat. Its answer comes into the chat, with a notification.",
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
                    OnyxTextField(
                        value = draft.prompt,
                        onValueChange = { value -> onChange { it.copy(prompt = value) } },
                        placeholder = "Check the weather and my calendar, and tell me what to wear",
                        singleLine = false,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    )
                }
            }

            OnyxFormSection(header = "When") {
                val customSchedule = custom
                if (customSchedule != null) {
                    OnyxRow {
                        BasicText("Schedule", style = theme.body.copy(color = theme.textColor), modifier = Modifier.weight(1f))
                        BasicText(
                            text = customSchedule,
                            localize = false,
                            style = theme.body.copy(color = theme.secondaryText),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OnyxRowDivider()
                    OnyxRow(onClick = { updateWhen(whenState) }) {
                        BasicText("Pick a Time Instead", style = theme.body.copy(color = theme.accent))
                    }
                } else {
                    OnyxRow(onClick = { showRepeats = true }) {
                        BasicText("Repeat", style = theme.body.copy(color = theme.textColor), modifier = Modifier.weight(1f))
                        BasicText(whenState.repeats.title, style = theme.body.copy(color = theme.secondaryText))
                        OnyxIcon(Symbols.ChevronUpDown, tint = theme.tertiaryText, size = 12.dp)
                    }

                    when (whenState.repeats) {
                        RoutineWhen.Repeat.HOURS -> {
                            OnyxRowDivider()
                            OnyxRow {
                                val hours = whenState.everyHours
                                BasicText(
                                    text = "Every $hours ${if (hours == 1) "hour" else "hours"}",
                                    style = theme.body.copy(color = theme.textColor),
                                    modifier = Modifier.weight(1f),
                                )
                                OnyxIcon(
                                    Symbols.MinusCircle,
                                    tint = theme.accent,
                                    size = 22.dp,
                                    modifier = Modifier.onyxClickable(enabled = hours > 1) {
                                        updateWhen(whenState.copy(everyHours = hours - 1))
                                    },
                                )
                                OnyxIcon(
                                    Symbols.PlusCircle,
                                    tint = theme.accent,
                                    size = 22.dp,
                                    modifier = Modifier.onyxClickable(enabled = hours < 23) {
                                        updateWhen(whenState.copy(everyHours = hours + 1))
                                    },
                                )
                            }
                        }

                        RoutineWhen.Repeat.ONCE -> {
                            OnyxRowDivider()
                            OnyxRow(onClick = {
                                val zone = ZoneId.systemDefault()
                                val current = LocalDateTime.ofInstant(whenState.once, zone)
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        val date = LocalDate.of(year, month + 1, day)
                                        TimePickerDialog(
                                            context,
                                            { _, hour, minute ->
                                                val picked = LocalDateTime.of(date, LocalTime.of(hour, minute))
                                                updateWhen(whenState.copy(once = picked.atZone(zone).toInstant()))
                                            },
                                            current.hour,
                                            current.minute,
                                            android.text.format.DateFormat.is24HourFormat(context),
                                        ).show()
                                    },
                                    current.year,
                                    current.monthValue - 1,
                                    current.dayOfMonth,
                                ).apply {
                                    datePicker.minDate = System.currentTimeMillis()
                                }.show()
                            }) {
                                BasicText("At", style = theme.body.copy(color = theme.textColor), modifier = Modifier.weight(1f))
                                BasicText(
                                    text = formatOnce(whenState.once),
                                    style = theme.body.copy(color = theme.secondaryText),
                                )
                            }
                        }

                        else -> {
                            OnyxRowDivider()
                            OnyxRow(onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute -> updateWhen(whenState.copy(hour = hour, minute = minute)) },
                                    whenState.hour,
                                    whenState.minute,
                                    android.text.format.DateFormat.is24HourFormat(context),
                                ).show()
                            }) {
                                BasicText("At", style = theme.body.copy(color = theme.textColor), modifier = Modifier.weight(1f))
                                BasicText(
                                    text = shortTime(LocalTime.of(whenState.hour, whenState.minute)),
                                    style = theme.body.copy(color = theme.secondaryText),
                                )
                            }
                            if (whenState.repeats == RoutineWhen.Repeat.WEEKLY) {
                                OnyxRowDivider()
                                WeekdayPicker(
                                    days = whenState.days,
                                    onChange = { days -> updateWhen(whenState.copy(days = days)) },
                                )
                            }
                        }
                    }
                }
            }

            val lastRun = job?.lastRunAt
            val lastError = job?.lastError?.takeIf { job.lastStatus == "error" }
            if (lastRun != null || lastError != null) {
                OnyxFormSection {
                    if (lastRun != null) {
                        OnyxRow {
                            BasicText("Last run", style = theme.body.copy(color = theme.textColor), modifier = Modifier.weight(1f))
                            BasicText(
                                text = formatLastRun(Instant.ofEpochMilli(lastRun)),
                                style = theme.body.copy(color = theme.secondaryText),
                            )
                        }
                    }
                    if (lastError != null) {
                        if (lastRun != null) OnyxRowDivider()
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
                            BasicText(lastError, localize = false, style = theme.footnote.copy(color = theme.warning))
                        }
                    }
                }
            }

            state.problem?.let { problem ->
                OnyxFormSection {
                    OnyxRow {
                        OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 17.dp)
                        BasicText(
                            text = problem,
                            localize = false,
                            style = theme.footnote.copy(color = theme.warning),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    if (showRepeats) {
        OnyxMenu(
            items = RoutineWhen.Repeat.entries.map { repeat ->
                MenuItem(repeat.title, checked = repeat == whenState.repeats) {
                    showRepeats = false
                    updateWhen(whenState.copy(repeats = repeat))
                }
            },
            onDismiss = { showRepeats = false },
        )
    }
}

@Composable
private fun WeekdayPicker(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (day in 0..6) {
            val on = day in days
            val dayOfWeek = DayOfWeek.of(if (day == 0) 7 else day)
            val fullName = dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.getDefault())
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (on) theme.accent else theme.fill, CircleShape)
                    .onyxClickable {
                        if (on && days.size > 1) onChange(days - day) else if (!on) onChange(days + day)
                    }
                    .semantics {
                        contentDescription = fullName
                        selected = on
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                    style = theme.caption.copy(color = if (on) theme.userText else theme.secondaryText),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ScheduleUnavailable(title: String, description: String, symbol: Int = Symbols.Clock) {
    OnyxEmptyState(symbol = symbol, title = title, description = description)
}

/** Le petit `ProgressView` de la liste, dessiné sans composant Material. */
@Composable
private fun ScheduleSpinner(size: Dp) {
    val theme = LocalOnyxTheme.current
    val transition = rememberInfiniteTransition(label = "schedule-spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(800, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "schedule-spinner-angle",
    )
    val color = theme.secondaryText
    Canvas(modifier = Modifier.size(size)) {
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 285f,
            useCenter = false,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

private fun shortTime(time: LocalTime): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    .withLocale(Locale.getDefault())
    .format(time)
    .replace(Regex("[\\u00A0\\u202F]"), " ")

private fun formatOnce(instant: Instant): String = DateTimeFormatter.ofLocalizedDateTime(
    FormatStyle.MEDIUM,
    FormatStyle.SHORT,
)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())
    .format(instant)
    .replace(Regex("[\\u00A0\\u202F]"), " ")

private fun formatLastRun(instant: Instant): String = formatOnce(instant)
