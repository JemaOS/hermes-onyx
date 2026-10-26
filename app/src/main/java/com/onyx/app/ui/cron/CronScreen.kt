package com.onyx.app.ui.cron

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.data.model.serve.CronJob
import com.onyx.app.data.model.serve.CronRun
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.OnyxAlert
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
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Réglages → Cron.
 *
 * Comme: la liste, l'éditeur et le détail d'une tâche planifiée, avec les
 * mêmes phrases et les mêmes actions (pause, exécution immédiate, suppression).
 */
@Composable
fun CronScreen(
    viewModel: CronViewModel,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /**
     * Le contrôle du volet (`sidebar.left`): la liste des horaires **est** le volet des deux
     * panneaux; repliée, c'est le détail qui le porte.
     */
    sidebarControl: (@Composable () -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<CronJob?>(null) }
    var pendingDelete by remember { mutableStateOf<CronJob?>(null) }

    val detail = state.detail
    val draft = state.draft

    when {
        draft != null -> CronEditorScreen(
            state = state,
            onBack = viewModel::cancelDraft,
            onSave = viewModel::saveDraft,
            onChange = viewModel::updateDraft,
            modifier = modifier,
        )

        detail != null -> CronDetailScreen(
            job = detail,
            runs = state.runs,
            working = state.working,
            onBack = viewModel::closeDetail,
            onEdit = { viewModel.edit(detail) },
            onRun = { viewModel.runNow(detail) },
            onTogglePause = { if (detail.paused) viewModel.resume(detail) else viewModel.pause(detail) },
            onDelete = { pendingDelete = detail },
            modifier = modifier,
        )

        else -> Column(
            modifier = modifier
                .fillMaxSize()
                .background(theme.pageColor),
        ) {
            Column(modifier = Modifier.statusBarsPadding()) {
                OnyxPageHeader(
                    title = "Cron",
                    onBack = onBack,
                    leading = sidebarControl,
                    action = "New",
                    onAction = viewModel::startNew,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 40.dp),
            ) {
                state.working?.let { working ->
                    OnyxFormSection {
                        OnyxRow { BasicText(working, style = theme.footnote.copy(color = theme.secondaryText)) }
                    }
                }

                if (state.needsDashboard) {
                    OnyxEmptyState(
                        symbol = Symbols.ClockBadgeQuestionmark,
                        title = "No gateway configured",
                        description = "Cron jobs live on the Hermes gateway. Set up the Hermes Dashboard or the Hermes API in Settings.",
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
                        state.loading -> OnyxFormSection {
                            OnyxRow { BasicText("Loading from Onyx…", style = theme.body.copy(color = theme.secondaryText)) }
                        }

                        state.jobs.isEmpty() -> OnyxEmptyState(
                            symbol = Symbols.Clock,
                            title = "No jobs yet",
                            description = "Schedule Onyx to run a prompt every hour, every weekday at 9am, or once at a time you pick.",
                        )

                        else -> OnyxFormSection(header = "Jobs") {
                            state.jobs.forEachIndexed { index, job ->
                                if (index > 0) OnyxRowDivider()
                                CronRow(
                                    job = job,
                                    onClick = { viewModel.openDetail(job) },
                                    onMenu = { menuFor = job },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    menuFor?.let { job ->
        OnyxMenu(
            items = listOf(
                MenuItem("Run now", Symbols.BoltOutline) {
                    menuFor = null
                    viewModel.runNow(job)
                },
                MenuItem(if (job.paused) "Resume" else "Pause", if (job.paused) Symbols.PlayOutline else Symbols.PauseOutline) {
                    menuFor = null
                    if (job.paused) viewModel.resume(job) else viewModel.pause(job)
                },
                MenuItem("Edit", Symbols.Pencil) {
                    menuFor = null
                    viewModel.edit(job)
                },
                MenuItem("Delete", Symbols.Trash, destructive = true) {
                    menuFor = null
                    pendingDelete = job
                },
            ),
            onDismiss = { menuFor = null },
        )
    }

    pendingDelete?.let { job ->
        OnyxAlert(
            title = "Delete “${job.displayTitle}”?",
            message = "It stops running on the gateway. This can't be undone.",
            actions = listOf(
                AlertAction("Cancel") { pendingDelete = null },
                AlertAction("Delete", destructive = true) {
                    pendingDelete = null
                    viewModel.delete(job)
                },
            ),
            onDismiss = { pendingDelete = null },
        )
    }
}

internal val CronStatusDotSize = 9.dp

@Composable
private fun CronRow(job: CronJob, onClick: () -> Unit, onMenu: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onyxTappable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(CronStatusDotSize)
                .background(
                    color = when {
                        job.paused -> theme.warning
                        job.lastError != null || job.state == "error" -> theme.error
                        job.state == "completed" -> theme.tertiaryText
                        else -> theme.success
                    },
                    shape = RoundedCornerShape(50),
                ),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(
                text = job.displayTitle.ifEmpty { "Untitled job" },
                localize = job.displayTitle.isEmpty(),
                style = theme.body.copy(color = theme.textColor, fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(job.botName, job.schedule).joinToString(" · ")
            BasicText(text = subtitle, localize = false, style = theme.footnote.copy(color = theme.secondaryText))
            val status = when {
                job.paused -> "Paused"
                job.lastError != null -> "Error"
                job.state.isNotEmpty() -> job.state.replaceFirstChar { it.uppercase() }
                job.nextRun != null -> OnyxLocalization.format("Next %1\$s", job.nextRun)
                else -> "Completed"
            }
            BasicText(text = status, localize = job.paused || job.lastError != null || job.state.isEmpty(), style = theme.caption.copy(color = theme.tertiaryText))
        }
        OnyxIcon(
            symbol = Symbols.Ellipsis,
            tint = theme.secondaryText,
            size = 16.dp,
            modifier = Modifier.onyxClickable(onClick = onMenu),
        )
    }
}

@Composable
private fun CronEditorScreen(
    state: CronUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onChange: ((CronDraft) -> CronDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val draft = state.draft ?: return
    var showTargets by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = if (draft.id == null) "New job" else "Edit job",
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
            OnyxFormSection(header = "Job") {
                Field(label = "Name", value = draft.title, placeholder = "Name") { value ->
                    onChange { it.copy(title = value) }
                }
                OnyxRowDivider()
                Field(label = "Schedule", value = draft.schedule, placeholder = "Schedule") { value ->
                    onChange { it.copy(schedule = value) }
                }
                OnyxRowDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SCHEDULE_EXAMPLES.take(4).forEach { example ->
                        BasicText(
                            text = example,
                            style = theme.caption.copy(color = theme.accent),
                            maxLines = 1,
                            modifier = Modifier
                                .background(theme.accent.copy(alpha = 0.12f), RoundedCornerShape(50))
                                .onyxClickable { onChange { it.copy(schedule = example) } }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            OnyxFormSection(header = "Prompt") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                ) {
                    OnyxTextField(
                        value = draft.prompt,
                        onValueChange = { value -> onChange { it.copy(prompt = value) } },
                        placeholder = "What should Onyx do?",
                        singleLine = false,
                        maxLines = 5,
                    )
                }
            }

            OnyxFormSection(
                header = "Deliver to",
                footer = "Local keeps the output on the gateway; a platform sends it where the job runs.",
            ) {
                OnyxRow(onClick = { showTargets = !showTargets }) {
                    BasicText(
                        text = draft.deliverTo ?: "Local (save only)",
                        localize = draft.deliverTo == null,
                        style = theme.body.copy(color = theme.textColor),
                        modifier = Modifier.weight(1f),
                    )
                    OnyxIcon(Symbols.ChevronRight, tint = theme.tertiaryText, size = 12.dp)
                }
                if (showTargets) {
                    val options = listOf(null to "Local (save only)") +
                        state.targets.map { it.id to it.name }
                    options.forEach { (id, name) ->
                        OnyxRowDivider()
                        OnyxRow(onClick = {
                            onChange { it.copy(deliverTo = id) }
                            showTargets = false
                        }) {
                            OnyxIcon(
                                symbol = if (draft.deliverTo == id) Symbols.CheckmarkCircle else Symbols.Circle,
                                tint = if (draft.deliverTo == id) theme.accent else theme.tertiaryText,
                                size = 18.dp,
                            )
                            BasicText(name, localize = id == null, style = theme.body.copy(color = theme.textColor), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            OnyxFormSection(footer = "A schedule reads like “every 30m”, “weekdays at 9am”, “in 2h”, or a cron expression such as “0 */6 * * *”.") {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    BasicText(
                        text = SCHEDULE_EXAMPLES.joinToString("   ·   "),
                        style = theme.caption.copy(color = theme.secondaryText),
                    )
                }
            }
        }
    }
}

@Composable
private fun CronDetailScreen(
    job: CronJob,
    runs: List<CronRun>,
    working: String?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onTogglePause: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = job.displayTitle.ifEmpty { "Job" }, onBack = onBack, action = "Edit", onAction = onEdit, localizeTitle = job.displayTitle.isEmpty())
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
                DetailRow("Schedule", job.schedule)
                DetailRow("State", job.state.ifEmpty { if (job.paused) "Paused" else "Active" }, localizeValue = job.state.isEmpty())
                job.nextRun?.let { DetailRow("Next run", it) }
                job.lastRun?.let { DetailRow("Last run", it) }
                DetailRow("Deliver to", job.deliverTo ?: "Local (save only)", localizeValue = job.deliverTo == null)
                job.model?.let { DetailRow("Model", it) }
            }

            OnyxFormSection(header = "Prompt") {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                    BasicText(text = job.prompt, localize = false, style = theme.body.copy(color = theme.textColor))
                }
            }

            job.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                OnyxFormSection(header = "Last error") {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                        BasicText(
                            text = error,
                            localize = false,
                            style = theme.footnote.copy(color = theme.error, fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }

            OnyxFormSection(header = "Recent runs") {
                if (runs.isEmpty()) {
                    OnyxRow { BasicText("No runs yet", style = theme.body.copy(color = theme.secondaryText)) }
                } else {
                    runs.take(10).forEachIndexed { index, run ->
                        if (index > 0) OnyxRowDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        ) {
                            Column {
                                BasicText(run.at, localize = false, style = theme.footnote.copy(color = theme.secondaryText))
                                BasicText(
                                    text = run.state.replaceFirstChar { it.uppercase() },
                                    localize = false,
                                    style = theme.caption.copy(
                                        color = if (run.state.contains("error", true)) theme.error else theme.textColor,
                                    ),
                                )
                                run.output?.takeIf { it.isNotBlank() }?.let {
                                    BasicText(
                                        text = it.take(200),
                                        localize = false,
                                        style = theme.caption.copy(color = theme.tertiaryText),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OnyxFormSection {
                ActionRow("Run now", Symbols.BoltOutline, theme.accent, onRun)
                OnyxRowDivider()
                ActionRow(if (job.paused) "Resume" else "Pause", if (job.paused) Symbols.PlayOutline else Symbols.PauseOutline, theme.accent, onTogglePause)
                OnyxRowDivider()
                ActionRow("Edit", Symbols.Pencil, theme.accent, onEdit)
                OnyxRowDivider()
                ActionRow("Delete", Symbols.Trash, theme.error, onDelete)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, localizeValue: Boolean = false) {
    val theme = LocalOnyxTheme.current
    OnyxRow {
        BasicText(label, style = theme.body.copy(color = theme.textColor), modifier = Modifier.weight(1f))
        BasicText(
            text = value,
            localize = localizeValue,
            style = theme.body.copy(color = theme.secondaryText),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    OnyxRowDivider()
}

@Composable
private fun ActionRow(label: String, symbol: Int, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    OnyxRow(onClick = onClick) {
        OnyxIcon(symbol = symbol, tint = color, size = 17.dp)
        BasicText(label, style = theme.body.copy(color = color, fontWeight = FontWeight.Normal))
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(label, style = theme.body.copy(color = theme.textColor), modifier = Modifier.weight(0.7f))
        OnyxTextField(
            value = value,
            onValueChange = onChange,
            placeholder = placeholder,
            style = theme.body,
            modifier = Modifier.weight(1.3f),
        )
    }
}

private val SCHEDULE_EXAMPLES = listOf(
    "every 30m",
    "every day at 9am",
    "weekdays at 9am",
    "every monday 8am",
    "0 */6 * * *",
    "in 2h",
    "2026-12-01T09:00",
)
