package com.onyx.app.ui.cron

import com.onyx.app.ui.design.OnyxSocle
import com.onyx.app.ui.design.onyxPage

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.data.model.serve.CronJob
import com.onyx.app.data.model.serve.CronRun
import com.onyx.app.data.model.serve.cronIdentity
import com.onyx.app.data.model.serve.cronStamp
import com.onyx.app.data.model.serve.scheduleText
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.common.OnyxCardRow
import com.onyx.app.ui.common.OnyxLazyFormPage
import com.onyx.app.ui.common.OnyxLoadingRows
import com.onyx.app.ui.common.OnyxTroublePanel
import com.onyx.app.ui.common.onyxLazyCardHeader
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.ContentCard
import com.onyx.app.ui.design.ContentIconButton
import com.onyx.app.ui.design.ContentPageHeader
import com.onyx.app.ui.design.ContentPill
import com.onyx.app.ui.design.ContentRow
import com.onyx.app.ui.design.ContentRowDivider
import com.onyx.app.ui.design.ContentSectionLabel
import com.onyx.app.ui.design.PillSize
import com.onyx.app.ui.design.PillVariant
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.OnyxAlert
import com.onyx.app.ui.design.OnyxEmptyState
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxInkOn
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Lucide
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.TextInkTarget
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Réglages → Cron.
 *
 * Port de `CronView` : la liste, l'éditeur et le détail d'une tâche planifiée, avec les
 * mêmes phrases et les mêmes actions (pause, exécution immédiate, suppression).
 */

/**
 * **La réserve sous le dernier bloc d'un écran de planification.**
 *
 * Elle vaut **une cible tactile plus une séparation de section** (48 + 24 = 72 dp) : c'est ce qu'il
 * faut pour que la dernière rangée d'une liste — les « exécutions » d'une tâche — reste **au-dessus**
 * de la plaque du bas (le pied du volet, la barre du fil), au lieu d'être enterrée dessous. La
 * capture du propriétaire montrait exactement l'inverse : la pastille et son rond posés sur la liste.
 *
 * La valeur est **nommée et dérivée de l'échelle** — c'était un `40.dp` écrit à la main, à quatre
 * endroits, qui ne tenait à rien.
 */
internal val CronBottomReserve: Dp = OnyxSpace.TouchTarget + OnyxSpace.Loose

@Composable
fun CronScreen(
    viewModel: CronViewModel,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /**
     * Le contrôle du volet (`sidebar.left`) : la liste des horaires **est** le volet des deux
     * panneaux ; repliée, c'est le détail qui le porte.
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
                .onyxPage(theme)
                // La zone du système est prise par la racine de l'écran : la dernière rangée
                // du corps ne peut plus être enterrée sous la barre de navigation ni sous la
                // zone du geste d'accueil. L'encart est **lu à l'exécution** (24 dp en gestes,
                // 48 dp à trois boutons) — jamais une valeur en dur qui ne vaut que pour un écran.
                .navigationBarsPadding(),
        ) {
            Column(modifier = Modifier.statusBarsPadding()) {
                // LOT anatomie-des-contenus : le grand titre et son sous-titre de la référence
                // (« Scheduled » / « 3 jobs · each run opens as a chat »), la flèche de retour seule
                // à gauche, l'action de création à droite — et **une seule couleur saturée**, la
                // pilule bleue. C'était une petite barre d'app avec un mot « New » teinté.
                ContentPageHeader(
                    title = "Scheduled",
                    // Le résumé de la table (« 3 tâches · chaque exécution s'ouvre comme une
                    // conversation ») : sa clé est traduite par `OnyxLocalization.format`.
                    subtitle = scheduledSummary(state.jobs.size, state.loading),
                    onBack = onBack,
                    // **Le rond du volet a été retiré d'ici** — ordre explicite du propriétaire
                    // (2026-10-30) : « supprime, elle ne sert à rien ». Ce rond portait
                    // `sidebarState.toggle()` (replier/déplier **la liste**) et **pas** une pause :
                    // aucune pause globale n'existe dans cet écran, et la pause d'**une** tâche
                    // appartient à la tâche (son menu long « Pause job », son balayage, sa feuille
                    // de détail — `CronScreen.kt` / `CronDetailScreen`). Le volet reste joignable
                    // par le geste du bord et par les en-têtes des autres écrans ; `panel` n'est
                    // donc plus posé ici, et le verrou `ResponsiveLayoutTest` refuse son retour.
                ) {
                    ContentPill(
                        text = "New job",
                        symbol = Lucide.Plus,
                        onClick = viewModel::startNew,
                        variant = PillVariant.Primary,
                        size = PillSize.Small,
                        // Une pilule **fixe** : son mot vient du dictionnaire (le composant ne
                        // traduisait rien, d'où « New job » au milieu d'une appli française).
                        localize = true,
                    )
                }
            }

            // **La page est une liste paresseuse** : 500 tâches ne composent que leurs rangées
            // visibles (défaut T9). La carte garde sa forme : `OnyxCardRow` en pose la surface
            // rangée par rangée, et le titre « Jobs » est un `item` de la même liste.
            OnyxLazyFormPage(modifier = Modifier.weight(1f), bottomPadding = CronBottomReserve) {
                state.working?.let { working ->
                    item(key = "working") {
                        OnyxFormSection {
                            OnyxRow { BasicText(working, style = theme.footnote.copy(color = theme.secondaryText)) }
                        }
                    }
                }

                if (state.needsDashboard) {
                    item(key = "no-gateway") {
                        OnyxEmptyState(
                            symbol = Symbols.ClockBadgeQuestionmark,
                            title = "No gateway configured",
                            description = "Cron jobs live on the Hermes gateway. Set up the Hermes Dashboard or the Hermes API in Settings.",
                        )
                    }
                } else {
                    state.problem?.let { problem ->
                        item(key = "problem") {
                            // Le panneau commun : carte du fil quand le serveur n'a pas répondu,
                            // rangée d'avertissement sinon — « Réessayer » dans les deux cas.
                            OnyxTroublePanel(problem = problem, onRetry = viewModel::refresh)
                        }
                    }

                    when {
                        state.loading -> item(key = "loading") {
                            OnyxLoadingRows(label = "Loading from Onyx…")
                        }

                        state.jobs.isEmpty() -> item(key = "empty") {
                            OnyxEmptyState(
                                symbol = Symbols.Clock,
                                title = "No jobs yet",
                                description = "Schedule Onyx to run a prompt every hour, every weekday at 9am, or once at a time you pick.",
                            )
                        }

                        else -> {
                            onyxLazyCardHeader("Jobs")
                            itemsIndexed(state.jobs, key = { _, job -> "job:" + job.id }) { index, job ->
                                OnyxCardRow(first = index == 0, last = index == state.jobs.lastIndex) {
                                    if (index > 0) ContentRowDivider()
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
    }

    menuFor?.let { job ->
        OnyxMenu(
            items = listOf(
                MenuItem("Run now", Symbols.BoltOutline) {
                    menuFor = null
                    viewModel.runNow(job)
                },
                MenuItem(if (job.paused) "Resume" else "Pause job", if (job.paused) Symbols.PlayOutline else Symbols.PauseOutline) {
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

/**
 * **Ce qu'une tâche dit d'elle-même, à droite de sa rangée** — la colonne de la référence
 * (`ScheduledPage.kt:333-344`) : l'heure sur « prochaine exécution », « échec » sur « dernière
 * exécution », « terminée », « en pause ».
 *
 * Rien n'est inventé : chaque état vient d'un champ réellement renseigné par le serveur
 * (`paused`, `lastError`, `state`, `nextRun`, `lastRun`). Un champ vide ne produit pas de ligne.
 *
 * **Ce que la capture du propriétaire a fait changer.** L'état était **le nombre de millisecondes
 * brut du serveur** (`1793292701521`) et son libellé un mot anglais (« next run »). Ici, l'heure est
 * [cronStamp] — une date dans la langue du système, et son relatif quand c'est proche —, et le
 * libellé passe par [OnyxLocalization]. **Aucun nombre brut ne peut plus sortir d'ici** : sans
 * horodatage lisible, la fonction rend `null` pour les deux, et la rangée n'écrit rien.
 */
private fun cronStatus(
    job: CronJob,
    nowMillis: Long = System.currentTimeMillis(),
): Pair<String?, String?> {
    val next = cronStamp(millis = job.nextRunAt, raw = job.nextRun, nowMillis = nowMillis)
    val last = cronStamp(millis = job.lastRunAt, raw = job.lastRun, nowMillis = nowMillis)
    val (value, label) = when {
        job.paused -> "Paused" to (if (job.lastError != null) "last run failed" else null)
        job.lastError != null || job.state.equals("error", ignoreCase = true) -> "failed" to "last run"
        next != null -> next.line to "next run"
        last != null -> "done" to "last run"
        job.state.isNotBlank() -> job.state to "state"
        else -> null to null
    }
    // Les deux mots sont des **libellés** de la maison : ils sortent traduits (la rangée de contenu
    // n'applique pas la table elle-même), et l'heure sort **déjà** formatée par `cronStamp`.
    return value?.let { OnyxLocalization[it] } to label?.let { OnyxLocalization[it] }
}

/** La teinte d'un état : l'avertissement pour la pause, l'erreur pour l'échec, rien d'autre. */
@Composable
private fun cronStatusTint(job: CronJob): Color = when {
    job.paused -> LocalOnyxTheme.current.warning
    job.lastError != null || job.state.equals("error", ignoreCase = true) -> LocalOnyxTheme.current.error
    else -> LocalOnyxTheme.current.secondaryText
}

/**
 * **Le sous-titre du grand titre** de la liste : « 3 tâches · chaque exécution s'ouvre comme une
 * conversation ». Il ne s'affiche **que sur une liste chargée non vide** — un compte inventé sur une
 * liste en cours de chargement serait un mensonge.
 */
private fun scheduledSummary(count: Int, loading: Boolean): String? {
    if (loading || count <= 0) return null
    val key = if (count == 1) "%d job · each run opens as a chat" else "%d jobs · each run opens as a chat"
    return OnyxLocalization.format(key, count)
}

@Composable
private fun CronRow(job: CronJob, onClick: () -> Unit, onMenu: () -> Unit) {
    val theme = LocalOnyxTheme.current
    val (status, statusLabel) = cronStatus(job)
    ContentRow(
        title = job.displayTitle.ifEmpty { "Untitled job" },
        localizeTitle = job.displayTitle.isEmpty(),
        // L'horaire est **raconté** (la syntaxe se traduit, la valeur saisie non) : c'était
        // « every 60m » au milieu d'une appli française.
        subtitle = listOfNotNull(job.botName, job.scheduleText().takeIf { it.isNotBlank() })
            .joinToString(" · "),
        // La vignette grise de l'anatomie, avec l'icône du rôle : l'échéancier, ou l'alerte quand la
        // dernière exécution a échoué. C'était un rond de couleur nue, sans icône.
        symbol = if (job.lastError != null || job.state.equals("error", ignoreCase = true)) {
            Symbols.ExclamationTriangle
        } else {
            Symbols.Calendar
        },
        status = status,
        statusLabel = statusLabel,
        statusTint = cronStatusTint(job),
        onClick = onClick,
        trailing = {
            ContentIconButton(
                symbol = Symbols.Ellipsis,
                description = "Job options",
                onClick = onMenu,
            )
        },
    )
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
                // La même réserve que le détail : le dernier bloc ne peut pas passer sous la plaque
                // du bas. C'était `40.dp`, à la main.
                .padding(bottom = CronBottomReserve),
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
                            // **La syntaxe reste la syntaxe.** Un exemple (« every 30m ») est la
                            // valeur que le serveur attend, pas une phrase de l'interface : le
                            // traduire à l'écran ferait saisir du français au serveur. Le pied de
                            // section dit déjà, en français, ce que ces formes veulent dire.
                            localize = false,
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
            .onyxPage(theme)
            // La zone du système est prise par la racine de l'écran : la dernière rangée du
            // corps ne peut plus être enterrée sous la barre de navigation ni sous la zone du
            // geste d'accueil. L'encart est **lu à l'exécution** (24 dp en navigation par
            // gestes, 48 dp à trois boutons, et ce qu'il faut sur un pliable déplié) — jamais
            // une valeur en dur qui ne vaut que pour un écran.
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            // LOT anatomie-des-contenus : le grand titre, le sous-titre d'état, et **les actions
            // contextuelles** (le crayon, la corbeille) à droite de la flèche de retour — la ligne
            // d'actions de la référence. C'était un mot « Edit » dans une petite barre d'app, et la
            // suppression vivait dans une section de boutons en bas de page.
            val (status, statusLabel) = cronStatus(job)
            // Le sous-titre dit **l'horaire** et **quand** — jamais un nombre de millisecondes, et
            // jamais un mot anglais (« next run ») : c'était les deux défauts de la capture. Un
            // état qui compte plus que l'heure (en pause, en échec) prend la place du relatif.
            val lead = when {
                job.paused -> statusLabel ?: status
                job.lastError != null || job.state.equals("error", ignoreCase = true) -> statusLabel ?: status
                else -> null
            }
            ContentPageHeader(
                title = job.displayTitle.ifEmpty { "Job" },
                localizeTitle = job.displayTitle.isEmpty(),
                subtitle = listOfNotNull(
                    job.scheduleText().takeIf { it.isNotBlank() },
                    lead ?: cronStamp(millis = job.nextRunAt, raw = job.nextRun)?.relative,
                ).joinToString(" · "),
                onBack = onBack,
            ) {
                ContentIconButton(
                    symbol = Lucide.Pencil,
                    description = "Edit job",
                    onClick = onEdit,
                )
                ContentIconButton(
                    symbol = Lucide.Trash2,
                    description = "Delete job",
                    onClick = onDelete,
                    tint = theme.error,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // **La réserve du bas** : la dernière rangée des exécutions ne peut pas être
                // enterrée sous la plaque qui se pose au bas de l'écran (le pied du volet, la barre
                // du fil). C'était `40.dp` écrit à la main — la capture montrait la pastille et son
                // rond posés **sur** la liste.
                .padding(bottom = CronBottomReserve),
            // **Un seul écart entre deux blocs** : le jeton des cartes empilées
            // (`OnyxSpace.BlockGap`), le même que le rembourrage interne vient de l'échelle.
            verticalArrangement = Arrangement.spacedBy(OnyxSpace.BlockGap),
        ) {
            // **La carte de résumé de la référence** (`ScheduledPage.kt:358-388`) : le texte de la
            // tâche, où elle livre, puis **les deux pilules** — l'action principale en **noir
            // rempli** (l'inverse de la source), l'action secondaire en **gris**. C'était une
            // section de boutons en bas de page, avec des mots teintés.
            //
            // La carte **grandit avec son contenu** : le rembourrage du jeton est posé après le
            // fond, donc la forme épouse la hauteur mesurée (texte + écart + commandes +
            // rembourrage). Les deux pilules sont des enfants de la carte, jamais au-delà.
            ContentCard {
                if (job.prompt.isNotBlank()) {
                    BasicText(
                        text = job.prompt.trim(),
                        localize = false,
                        style = theme.body.copy(fontSize = OnyxSocle.Body, color = theme.secondaryText),
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                job.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                    BasicText(
                        text = error,
                        localize = false,
                        // L'erreur est une **donnée technique** : elle s'écrit en monospace.
                        style = theme.monospacedFootnote.copy(fontSize = OnyxSocle.Meta, color = theme.error),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // L'écart entre le texte et les commandes est un écart entre blocs d'une
                        // même carte : c'est le jeton, pas un 8 dp choisi sur place.
                        .padding(top = OnyxSpace.BlockGap),
                    horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContentPill(
                        text = "Run now",
                        symbol = Lucide.Zap,
                        onClick = onRun,
                        variant = PillVariant.Inverse,
                        // Un mot de la maison : il sort du dictionnaire (« Exécuter maintenant »).
                        localize = true,
                    )
                    ContentPill(
                        text = if (job.paused) "Resume" else "Pause job",
                        symbol = if (job.paused) Lucide.Play else Lucide.Pause,
                        onClick = onTogglePause,
                        variant = PillVariant.Secondary,
                        localize = true,
                    )
                }
            }

            working?.let { notice ->
                ContentCard {
                    BasicText(notice, style = theme.footnote.copy(color = theme.secondaryText))
                }
            }

            // **Le carton de la planification** : ses rangées, et **un filet entre elles** —
            // jamais après la dernière (un filet de fin se lisait comme un oubli), et jamais sur
            // certaines seulement. Les deux horodatages y sont **formatés** : c'était
            // `1793292701521` en clair sous « Prochaine exécution ».
            val next = cronStamp(millis = job.nextRunAt, raw = job.nextRun)
            val last = cronStamp(millis = job.lastRunAt, raw = job.lastRun)
            val state = job.state.ifEmpty { if (job.paused) "Paused" else "Active job" }
            val rows = buildList {
                add(DetailSpec("Schedule", job.scheduleText().ifEmpty { job.schedule }, mono = true))
                add(DetailSpec("State", state, localize = job.state.isEmpty() || job.state in CRON_STATE_KEYS))
                next?.let { add(DetailSpec("Next run", it.absolute, note = it.relative)) }
                last?.let { add(DetailSpec("Last run", it.absolute, note = it.relative)) }
                add(DetailSpec("Deliver to", job.deliverTo ?: "Local (save only)", localize = job.deliverTo == null))
                job.model?.let { add(DetailSpec("Model", it, mono = true)) }
            }
            ContentCard(padding = 0.dp) {
                rows.forEachIndexed { index, spec ->
                    if (index > 0) DetailRowDivider()
                    DetailRow(
                        label = spec.label,
                        value = spec.value,
                        localizeValue = spec.localize,
                        mono = spec.mono,
                        note = spec.note,
                    )
                }
            }

            ContentSectionLabel(
                text = "Runs",
                localize = true,
                modifier = Modifier.padding(
                    start = OnyxSpace.CardPadding,
                    end = OnyxSpace.CardPadding,
                    top = OnyxSpace.BlockGap,
                    bottom = OnyxSpace.Half,
                ),
            )
            ContentCard(padding = 0.dp) {
                if (runs.isEmpty()) {
                    BasicText(
                        text = "No runs yet",
                        style = theme.body.copy(color = theme.secondaryText),
                        modifier = Modifier.padding(OnyxSpace.CardPadding),
                    )
                } else {
                    runs.take(10).forEachIndexed { index, run ->
                        // Un filet **entre** les passages, en retrait après la vignette de la
                        // rangée (`withTile` par défaut) : la liste porte une vignette par passage,
                        // le filet part donc du libellé. Le dernier n'en porte pas.
                        if (index > 0) ContentRowDivider()
                        val stamp = cronStamp(raw = run.at)
                        val identity = cronIdentity(run.at)
                        ContentRow(
                            // Le **titre** de la rangée est une date lisible, jamais l'identifiant
                            // brut du serveur (`179328282.757681` sur la capture) : quand la valeur
                            // n'est pas un instant, la rangée dit qu'elle n'est pas datée et
                            // l'identifiant descend en monospace, sous elle.
                            title = stamp?.absolute ?: "Undated run",
                            localizeTitle = stamp == null,
                            subtitle = listOfNotNull(
                                stamp?.relative,
                                run.output?.takeIf { it.isNotBlank() }?.take(200),
                                // L'identifiant technique, **s'il en reste un** (la valeur du
                                // serveur qui n'est pas un instant), s'écrit en monospace discret —
                                // et jamais comme titre.
                                identity,
                            ).joinToString(" · "),
                            subtitleMono = identity != null,
                            symbol = if (run.state.contains("error", true)) Symbols.ExclamationTriangle else Symbols.CheckmarkCircleOutline,
                            status = OnyxLocalization[run.state.lowercase()].replaceFirstChar { it.uppercase() },
                            statusLabel = OnyxLocalization["run"],
                            statusTint = if (run.state.contains("error", true)) theme.error else theme.secondaryText,
                        )
                    }
                }
            }

            // Les deux gestes restants de la page : écrire la tâche, et la supprimer. Ils étaient
            // quatre sous « Run now / Pause » ; les deux premiers sont devenus les **pilules** de la
            // carte de résumé — aucune fonction n'a disparu.
            ContentCard(padding = 0.dp) {
                ActionRow("Edit", Lucide.Pencil, theme.accent, onEdit)
                ContentRowDivider(withTile = false)
                ActionRow("Delete", Lucide.Trash2, theme.error, onDelete)
            }
        }
    }
}

/**
 * Une ligne d'information : le libellé, puis sa valeur.
 *
 * @param mono la valeur est une **donnée technique** (une expression de planification, un identifiant
 *   de modèle) : elle s'écrit en monospace, comme dans la source (`ScheduledPage.kt:325`).
 *   **Une date n'en fait plus partie** : elle se lit comme une phrase (« dans 12 minutes ·
 *   29/10/2026 17:12 »), pas comme un jeton.
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    localizeValue: Boolean = false,
    mono: Boolean = false,
    note: String? = null,
) {
    val theme = LocalOnyxTheme.current
    OnyxRow {
        // Le libellé garde sa place : **à 1,3×**, une valeur longue (« dans 23 heures ·
        // 30/10/2026 17:51 » sur une seule ligne) écrasait le libellé et le coupait au milieu d'un
        // mot (« Prochain e exécut ion »). La valeur et sa note tiennent donc **deux lignes**, à
        // droite, sous le libellé — c'est la colonne d'état de la référence.
        BasicText(
            label,
            style = theme.body.copy(color = theme.textColor),
            modifier = Modifier.weight(1f),
        )
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(OnyxSpace.GlyphGap),
        ) {
            BasicText(
                text = value,
                localize = localizeValue,
                style = if (mono) {
                    theme.monospacedFootnote.copy(color = theme.secondaryText)
                } else {
                    theme.body.copy(color = theme.secondaryText)
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            note?.let {
                BasicText(
                    text = it,
                    localize = false,
                    style = theme.caption2.copy(
                        color = OnyxInkOn(theme.tertiaryText, theme.formPageColor, TextInkTarget),
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * **Le filet entre deux rangées du carton de la planification** — et jamais après la dernière :
 * une rangée = au plus un filet, et la forme de la carte se termine sur son rembourrage, pas sur un
 * trait. Aucune rangée n'a de vignette ici, donc le filet est en retrait du rembourrage de carte,
 * des deux côtés.
 */
@Composable
private fun DetailRowDivider() {
    OnyxRowDivider()
}

/** Les valeurs d'état que le serveur écrit et que la table sait dire. */
private val CRON_STATE_KEYS = setOf("scheduled", "paused", "completed", "error")

/**
 * Une rangée du carton de la planification : son libellé (une clé du dictionnaire), sa valeur, et
 * la façon de l'écrire — [localize] pour un mot de la maison (« En pause », « Local (enregistrer
 * seulement) »), [mono] pour une donnée technique (une expression d'horaire, un nom de modèle).
 */
private data class DetailSpec(
    val label: String,
    val value: String,
    val localize: Boolean = false,
    val mono: Boolean = false,
    /** Une seconde ligne, sous la valeur : ce que la valeur veut dire de près (« dans 12 minutes »). */
    val note: String? = null,
)

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
