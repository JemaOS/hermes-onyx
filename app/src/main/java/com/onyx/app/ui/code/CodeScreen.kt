package com.onyx.app.ui.code

import com.onyx.app.ui.design.OnyxSpace

import com.onyx.app.ui.design.onyxPage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.data.model.CodeFilter
import com.onyx.app.data.model.CodeSession
import com.onyx.app.data.model.CodeStatus
import com.onyx.app.data.model.codeElapsed
import com.onyx.app.data.model.codeFilterCounts
import com.onyx.app.data.model.codeFilterLabel
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.common.OnyxLoadingRows
import com.onyx.app.ui.common.OnyxTroublePanel
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.ChipRowFit
import com.onyx.app.ui.design.FrostSurface
import com.onyx.app.ui.design.OnyxAlert
import com.onyx.app.ui.design.OnyxEmptyState
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.frostedBar
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.ProvideFieldTextColours
import com.onyx.app.ui.design.fieldCursorColour
import com.onyx.app.ui.design.socleTitleInk

/** La couleur du point d'état : vert prêt, ambre en cours, rouge échec, bleu à toi. */
private fun statusTint(theme: ResolvedTheme, status: CodeStatus): Color = when (status) {
    CodeStatus.READY -> theme.success
    CodeStatus.RUNNING -> theme.warning
    CodeStatus.FAILED -> theme.error
    CodeStatus.NEEDS_YOU -> theme.accent
}

/** Le libellé d'un état, traduit par le dictionnaire central. */
private fun statusLabel(status: CodeStatus): String = when (status) {
    CodeStatus.READY -> "Ready"
    CodeStatus.RUNNING -> "Running"
    CodeStatus.FAILED -> "Failed"
    CodeStatus.NEEDS_YOU -> "Yours"
}

/** Ce qu'une puce de filtre garde autour de son libellé : ses deux marges intérieures, en dp. */
private const val FILTER_CHIP_INSET_DP = 24f

/** L'écart entre deux puces, en dp. */
private const val FILTER_CHIP_GAP_DP = 8f

/** Ce qu'une puce du compositeur garde autour de son libellé : marges, icône et chevron, en dp. */
private const val COMPOSER_CHIP_INSET_DP = 52f

/** La même puce sans ses ornements (ni icône, ni chevron) : ses deux marges intérieures seules. */
private const val COMPOSER_PLAIN_INSET_DP = 20f

/**
 * L'écran **Code** : la rangée de filtres avec leurs compteurs, la liste des sessions de code, et
 * le compositeur qui démarre une session par message.
 *
 * Même géométrie que la liste des conversations (marges de 16 dp), même verre liquide, mêmes
 * transitions fondues — figées sous « Réduire les animations ». Le dépôt, la copie de travail et la
 * branche viennent du **serveur** ; la fiche d'une session est **locale** et le dit — et c'est pour
 * ça qu'elle se **renomme**, se **modifie**, se **relance** et se **supprime** depuis cet écran.
 */
@Composable
fun CodeScreen(
    viewModel: CodeViewModel,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    sidebarControl: (@Composable () -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reduceMotion = LocalReduceMotion.current
    var menu by remember { mutableStateOf<String?>(null) }
    // La fiche dont le menu est ouvert, et l'endroit où le panneau doit se poser : la carte touchée
    // (appui long) ou son bouton ⋮ — les deux ouvrent le **même** menu, ancré à ce qu'on a touché.
    var menuSession by remember { mutableStateOf<CodeSession?>(null) }
    var menuAnchor by remember { mutableStateOf<Rect?>(null) }
    var renaming by remember { mutableStateOf<CodeSession?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<CodeSession?>(null) }
    // Les limites de chaque carte et de chaque bouton, dans la fenêtre : mesurées, jamais devinées.
    val bounds = remember { mutableStateMapOf<String, Rect>() }

    Box(modifier = modifier.fillMaxSize().onyxPage(theme)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            OnyxPageHeader(
                title = "Code",
                onBack = onBack,
                leading = sidebarControl,
                action = "Send",
                actionEnabled = state.draft.isNotBlank() && state.working == null,
                onAction = { viewModel.start() },
            )

            FilterRow(
                counts = codeFilterCounts(state.sessions),
                selected = state.filter,
                onSelect = viewModel::select,
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val visible = state.visible()
                // Une transition **fondue**, figée sous « Réduire les animations ».
                AnimatedContent(
                    targetState = state.filter to visible,
                    transitionSpec = {
                        if (reduceMotion) {
                            EnterTransition.None.togetherWith(ExitTransition.None)
                        } else {
                            fadeIn(animationSpec = OnyxMotion.easeOut(OnyxMotion.MS_200))
                                .togetherWith(fadeOut(animationSpec = OnyxMotion.easeOut(OnyxMotion.MS_150)))
                        }
                    },
                    label = "codeFilterFade",
                ) { (_, sessions) ->
                    when {
                        // Le dépôt vit sur le serveur : sans Dashboard il n'y a rien à lire, et le
                        // dire vaut mieux que de laisser croire qu'aucune session n'existe.
                        state.needsDashboard -> OnyxEmptyState(
                            symbol = Symbols.WrenchAndScrewdriver,
                            title = "No gateway configured",
                            description = "Code sessions run against a repository on the Hermes " +
                                "gateway. Set up the Hermes Dashboard in Settings, then pick a " +
                                "repository here.",
                        )

                        // **L'attente** : des rangées grises, jamais un écran vide — sans quoi un
                        // serveur lent se lirait comme « aucune session ».
                        state.loading && state.sessions.isEmpty() -> OnyxLoadingRows(
                            label = "Loading from Onyx…",
                        )

                        // **Une lecture qui a échoué ne se lit pas comme « aucune session »** : le
                        // panneau commun dit la cause et propose de réessayer. Avant ce lot, la
                        // liste affichait « Aucune session de code » **et** la bande d'échec.
                        sessions.isEmpty() && state.problem != null -> OnyxTroublePanel(
                            problem = state.problem.orEmpty(),
                            onRetry = viewModel::refresh,
                        )

                        sessions.isEmpty() -> CodeEmptyState(
                            hasAny = state.sessions.isNotEmpty(),
                        )

                        else -> {
                            // **La liste est paresseuse** : 500 sessions ne composent que leurs
                            // cartes visibles (défaut T9 de l'audit). Ces cartes ne vivent pas dans
                            // une `OnyxFormSection` : elles portent leur propre surface, donc une
                            // `LazyColumn` suffit, avec les mêmes marges qu'avant.
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(sessions, key = { session -> session.id }) { session ->
                                    CodeSessionCard(
                                        session = session,
                                        onClick = { viewModel.openDetail(session) },
                                        // L'appui long ouvre le menu de la fiche : le geste d'une
                                        // conversation, celui que le propriétaire a cherché.
                                        onLongPress = {
                                            menuAnchor = bounds[session.id]
                                            menuSession = session
                                        },
                                        // Et le bouton ⋮ fait exactement la même chose, à la vue de tous.
                                        onMore = {
                                            menuAnchor = bounds[moreKey(session.id)]
                                            menuSession = session
                                        },
                                        onBounds = { bounds[session.id] = it },
                                        onMoreBounds = { bounds[moreKey(session.id)] = it },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // La bande d'un échec **avec des sessions déjà affichées** : le panneau commun a déjà
            // pris la place de la liste quand elle était vide, on ne le redit pas deux fois.
            state.problem?.takeIf { state.sessions.isNotEmpty() }?.let { problem ->
                ProblemBar(
                    problem = problem,
                    // « Réessayer » **agit** : il relit vraiment les dépôts et les sessions.
                    onRetry = viewModel::refresh,
                    onDismiss = { viewModel.clearProblem() },
                )
            }

            CodeComposer(
                state = state,
                onDraft = viewModel::updateDraft,
                onRepo = { menu = "repo" },
                onWorktree = { menu = "worktree" },
                onBranch = { menu = "branch" },
                onSend = { viewModel.start() },
            )
        }

        // La feuille « Modifier » vit **dans l'écran** : ses puces ouvrent le même menu ancré que
        // celui du compositeur, dans le même repère, et le panneau se pose contre la puce touchée.
        state.editing?.let { session ->
            CodeEditSheet(
                session = session,
                title = state.editTitle,
                branch = state.editBranch,
                prompt = state.editPrompt,
                repo = state.editRepo,
                worktree = state.editWorktree,
                repos = state.repos,
                worktrees = state.editWorktrees,
                onTitle = viewModel::editTitle,
                onBranch = viewModel::editBranch,
                onPrompt = viewModel::editPrompt,
                onChooseRepo = viewModel::editChooseRepo,
                onChooseWorktree = viewModel::editChooseWorktree,
                onSave = viewModel::saveEdit,
                onDismiss = viewModel::cancelEdit,
            )
        }
    }

    // Les menus des puces du compositeur (dépôt, copie de travail, branche). « Nouveau worktree »
    // demande au serveur de créer la copie avant de démarrer la session.
    when (menu) {
        "repo" -> OnyxMenu(
            items = state.repos.map { path ->
                MenuItem(
                    label = path.substringAfterLast('/'),
                    checked = path == state.repo,
                    localize = false,
                    onSelect = { menu = null; viewModel.chooseRepo(path) },
                )
            },
            onDismiss = { menu = null },
        )
        "worktree" -> OnyxMenu(
            items = state.worktrees
                .filterNot { it.isMain }
                .map { tree ->
                    MenuItem(
                        label = tree.branch ?: tree.path.substringAfterLast('/'),
                        checked = tree.path == state.worktree,
                        localize = false,
                        onSelect = { menu = null; viewModel.chooseWorktree(tree.path) },
                    )
                } + MenuItem(
                label = "New worktree",
                symbol = Symbols.FolderBadgePlus,
                onSelect = { menu = null; viewModel.createWorktree() },
            ),
            onDismiss = { menu = null },
        )
        "branch" -> OnyxMenu(
            items = state.branches.take(24).map { name ->
                MenuItem(
                    label = name,
                    checked = name == state.branch,
                    localize = false,
                    onSelect = { menu = null; viewModel.chooseBranch(name) },
                )
            },
            onDismiss = { menu = null },
        )
        else -> Unit
    }

    // Le menu d'une fiche : renommer, modifier, relancer, supprimer — le panneau ancré maison.
    menuSession?.let { session ->
        CodeSessionMenu(
            session = session,
            anchor = menuAnchor,
            onDismiss = { menuSession = null },
            onRename = { pick ->
                renameText = pick.title
                renaming = pick
            },
            onEdit = viewModel::beginEdit,
            onRunAgain = viewModel::rerun,
            onDelete = { pick -> deleting = pick },
        )
    }

    // « Renommer » : un champ, Save ou Cancel — la sauvegarde est immédiate.
    renaming?.let { session ->
        CodeRenamePrompt(
            session = session,
            value = renameText,
            onValueChange = { renameText = it },
            onConfirm = {
                viewModel.rename(session, renameText)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    // « Supprimer » : la conséquence est dite avant, et rien d'autre n'est touché.
    deleting?.let { session ->
        CodeDeleteAlert(
            session = session,
            onConfirm = {
                viewModel.delete(session)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }

    // Un appui sur une carte ouvre son détail : la tâche, sa branche, ses paramètres réels, et ce
    // qui est tenu ici plutôt que sur le serveur.
    state.detail?.let { session ->
        CodeDetail(session = session, onDismiss = viewModel::closeDetail)
    }
}

/** La clé des limites du bouton ⋮ d'une fiche (celles de la carte sont la clé de la fiche). */
private fun moreKey(id: String): String = "more:$id"

/**
 * La rangée de filtres : pastille pleine pour l'actif, compteur sur chaque.
 *
 * Les **quatre** puces doivent **tenir** — à 1,0× comme à 1,3×, sur 360 dp comme sur 411 dp. La
 * rangée était défilable, et à 1,3× la quatrième puce dépassait : il n'en restait qu'un **fragment
 * de bord** au bord droit, lu comme un trait parasite (Compose ne dessine aucun indicateur de
 * défilement : tout trait vu là est un morceau d'élément coupé).
 *
 * La même méthode que la rangée d'onglets : on **mesure** chaque libellé sous la police courante et
 * on **réduit** la taille jusqu'à ce que les quatre tiennent, jamais sous le plancher de
 * lisibilité. Les compteurs se laissent tomber avant qu'une puce ne se coupe ; le défilement n'est
 * plus qu'un dernier recours, sur un écran plus étroit que ceux visés.
 */
@Composable
private fun FilterRow(
    counts: Map<CodeFilter, Int>,
    selected: CodeFilter,
    onSelect: (CodeFilter) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val filters = CodeFilter.entries
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // La place réellement offerte aux puces : la rangée, moins ses marges de côté.
        val available = maxWidth.value
        val measure: (String, Float) -> Float = { text, size ->
            val style = TextStyle(fontSize = size.sp, fontWeight = FontWeight.SemiBold)
            with(density) {
                measurer.measure(text = text, style = style, maxLines = 1).size.width.toDp().value
            }
        }
        val plan = ChipRowFit.plan(
            availableDp = available,
            chipInsetDp = FILTER_CHIP_INSET_DP,
            gapDp = FILTER_CHIP_GAP_DP,
            // La taille de départ est celle du thème (elle porte déjà l'échelle de texte de l'app) ;
            // la mesure, elle, applique en plus la police système demandée par la personne.
            baseSp = theme.subheadline.fontSize.value,
        ) { size, withCounts ->
            filters.map { filter ->
                measure(
                    codeFilterLabel(filter, counts[filter] ?: 0, showCounts = withCounts),
                    size,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    // Qui tient ne défile pas : le dernier recours garde les libellés entiers, il
                    // n'en coupe aucun — il n'existe que sur un écran plus étroit que ceux visés.
                    if (plan.fits) Modifier else Modifier.horizontalScroll(rememberScrollState()),
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FILTER_CHIP_GAP_DP.dp),
        ) {
            filters.forEach { filter ->
                val active = filter == selected
                Box(
                    modifier = Modifier
                        .heightIn(min = 32.dp)
                        .background(
                            if (active) theme.accent else theme.fill,
                            RoundedCornerShape(50),
                        )
                        .onyxTappable { onSelect(filter) }
                        .padding(horizontal = (FILTER_CHIP_INSET_DP / 2).dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = codeFilterLabel(
                            filter,
                            counts[filter] ?: 0,
                            showCounts = plan.showCounts,
                        ),
                        style = theme.subheadline.copy(
                            fontSize = plan.fontSizeSp.sp,
                            color = if (active) Color.White else theme.primaryText,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        maxLines = 1,
                        softWrap = false,
                        // Ni coupe ni ellipse : la rangée a été mesurée pour que ce libellé tienne.
                        overflow = TextOverflow.Clip,
                        // Le libellé est déjà assemblé et traduit par `filterLabel` : le repasser au
                        // dictionnaire ferait matcher la tournure « Running … » d'un autre écran.
                        localize = false,
                    )
                }
            }
        }
    }
}

/**
 * Une carte de session : point d'état, tâche, branche, pastille de statut, temps écoulé — et son
 * bouton **⋮**.
 *
 * La carte répond à **deux** gestes pour ses actions : l'**appui long** (le menu contextuel des
 * conversations) et le bouton **⋮** visible. Le premier est celui qu'on essaie par habitude, le
 * second est celui qu'on trouve sans savoir — les deux, pour que ce soit découvrable.
 */
@Composable
private fun CodeSessionCard(
    session: CodeSession,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onMore: () -> Unit,
    onBounds: (Rect) -> Unit,
    onMoreBounds: (Rect) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .onGloballyPositioned { onBounds(it.boundsInWindow()) }
            .background(theme.surfaceColor, RoundedCornerShape(OnyxSpace.FieldCorner))
            .onyxTappable(onLongPress = onLongPress, onClick = onClick)
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(10.dp)
                .background(statusTint(theme, session.status), RoundedCornerShape(50)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                text = session.title,
                // Le titre de la session : la **forme** du titre (`theme.headline`, 16 sp) et
                // l'**encre de la maison** — le style seul ne porte pas de couleur, donc la
                // Fondation écrivait du noir (invisible sur la carte sombre).
                style = theme.headline.copy(color = socleTitleInk(theme)),
                maxLines = 2,
                localize = false,
            )
            // La branche, en monospace : `code/<slug>` dérivé du titre.
            BasicText(
                text = session.branch,
                style = theme.codeFont.copy(color = theme.secondaryText),
                maxLines = 1,
                softWrap = false,
                localize = false,
            )
            // Ce qui est tenu ici, dit noir sur blanc.
            BasicText(
                text = "Held on this phone",
                style = theme.caption2.copy(color = theme.tertiaryText),
                maxLines = 1,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusPill(status = session.status)
            BasicText(
                text = OnyxLocalization.format("%1\$s ago", codeElapsed(session.statusAtMillis, System.currentTimeMillis())),
                style = theme.caption2.copy(color = theme.tertiaryText),
                maxLines = 1,
                softWrap = false,
            )
        }
        // Le bouton ⋮ des actions : le même menu que l'appui long, à la vue de tous.
        Box(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .onGloballyPositioned { onMoreBounds(it.boundsInWindow()) }
                .onyxTappable(onLongPress = onMore) { onMore() }
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            OnyxIcon(
                symbol = Symbols.Ellipsis,
                tint = theme.secondaryText,
                size = 18.dp,
                contentDescription = "Card menu",
            )
        }
    }
}

/** La pastille de statut : le mot de l'état, sur la teinte de son point, en sourdine. */
@Composable
private fun StatusPill(status: CodeStatus) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .background(statusTint(theme, status).copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        BasicText(
            text = statusLabel(status),
            style = theme.caption.copy(
                color = statusTint(theme, status),
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * L'état vide : jamais un écran blanc. Sans aucune session, il **explique comment démarrer** ;
 * avec un filtre qui ne retient rien, il le dit et nomme le filtre.
 */
@Composable
private fun CodeEmptyState(hasAny: Boolean) {
    val title = if (hasAny) "No session matches this filter." else "No code session yet"
    OnyxEmptyState(
        symbol = Symbols.WrenchAndScrewdriver,
        title = title,
        description = if (hasAny) null else
            "Pick a repository below, describe a change, then send: each message starts its own code session.",
    )
}

/**
 * Le compositeur de code : le champ, la ligne de puces (dépôt, worktree, branche), l'envoi.
 *
 * Aucun libellé n'est **tronqué** : les puces sont mesurées sous la police courante, la taille
 * descend d'un demi-point si c'est ce qu'il faut, et quand la place manque encore les libellés sont
 * abrégés **aux frontières de segment** (« …/session-neuve ») — **jamais au milieu d'un mot**, et
 * jamais par une ellipse posée au hasard. Le défilement ne reste qu'en dernier recours, et il ne
 * coupe rien : il laisse lire le libellé entier.
 */
@Composable
private fun CodeComposer(
    state: CodeUiState,
    onDraft: (String) -> Unit,
    onRepo: () -> Unit,
    onWorktree: () -> Unit,
    onBranch: () -> Unit,
    onSend: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedBar(FrostSurface.COMMAND_BAR, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Le champ : « Décris un changement… ».
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .frostedBar(FrostSurface.FIELD, RoundedCornerShape(OnyxSpace.CardCorner))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (state.draft.isEmpty()) {
                BasicText(
                    text = "Describe a change…",
                    style = theme.body.copy(color = theme.tertiaryText),
                    maxLines = 1,
                )
            }
            // Le champ de la session Code suit la **même règle** que les deux compositeurs :
            // sélection et curseur reposés localement, aucune encre héritée.
            ProvideFieldTextColours(theme) {
            BasicTextField(
                value = state.draft,
                onValueChange = onDraft,
                textStyle = theme.body.copy(color = theme.primaryText),
                cursorBrush = SolidColor(fieldCursorColour(theme)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp, max = 120.dp),
            )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Les puces : mesurées, réduites, abrégées aux frontières de segment, et — en dernier
            // recours — sans leurs ornements. Jamais un libellé coupé.
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val available = maxWidth.value
                val measure: (String, Float) -> Float = { text, size ->
                    val style = theme.caption.copy(fontSize = size.sp, fontWeight = FontWeight.SemiBold)
                    with(density) {
                        measurer.measure(text = text, style = style, maxLines = 1).size.width.toDp().value
                    }
                }
                val full = listOf(
                    state.repo?.substringAfterLast('/') ?: OnyxLocalization["Repository"],
                    state.worktree?.substringAfterLast('/') ?: OnyxLocalization["New worktree"],
                    state.branch ?: OnyxLocalization["Branch"],
                )
                val base = theme.caption.fontSize.value
                val plan = ChipRowFit.labelPlan(
                    labels = full,
                    availableDp = available,
                    // Les puces entières d'abord ; sans icône ni chevron quand c'est ce qu'il faut
                    // pour ne pas couper un mot (leur dégagement tombe de 32 dp chacune).
                    insetsDp = listOf(COMPOSER_CHIP_INSET_DP, COMPOSER_PLAIN_INSET_DP),
                    gapDp = FILTER_CHIP_GAP_DP,
                    baseSp = base,
                    // Le plancher est **11 sp déclarés**, jamais divisé par l'échelle de police :
                    // à 1,3× la rangée défile plutôt que de descendre sous le plancher de l'app.
                    floorSp = ChipRowFit.FLOOR_SP.coerceAtMost(base),
                ) { text, size -> measure(text, size) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (plan.fits) Modifier else Modifier.horizontalScroll(rememberScrollState())),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FILTER_CHIP_GAP_DP.dp),
                ) {
                    Chip(
                        text = plan.labels[0],
                        symbol = Symbols.Folder,
                        fontSizeSp = plan.fontSizeSp,
                        plain = plan.plain,
                        onClick = onRepo,
                    )
                    Chip(
                        text = plan.labels[1],
                        symbol = Symbols.FolderBadgePlus,
                        fontSizeSp = plan.fontSizeSp,
                        plain = plan.plain,
                        onClick = onWorktree,
                    )
                    Chip(
                        text = plan.labels[2],
                        symbol = Symbols.WrenchAndScrewdriver,
                        fontSizeSp = plan.fontSizeSp,
                        plain = plan.plain,
                        onClick = onBranch,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        if (state.draft.isNotBlank()) theme.accent else theme.fill,
                        RoundedCornerShape(50),
                    )
                    .onyxTappable(enabled = state.draft.isNotBlank() && state.working == null) { onSend() },
                contentAlignment = Alignment.Center,
            ) {
                OnyxIcon(
                    symbol = Symbols.ArrowUp,
                    tint = if (state.draft.isNotBlank()) Color.White else theme.tertiaryText,
                    size = 18.dp,
                    contentDescription = "Send",
                )
            }
        }
    }
}

/**
 * Une puce du compositeur : elle ouvre un menu, jamais un champ libre.
 *
 * Elle garde sa largeur **naturelle** : ni coupe ni ellipse, si bien qu'un libellé ne peut pas
 * perdre la moitié d'un mot. [plain] retire l'icône et le chevron — c'est ce qui permet aux trois
 * libellés **entiers** de tenir là où ils ne tenaient pas, et un ornement en moins vaut mieux qu'un
 * mot coupé.
 */
@Composable
private fun Chip(
    text: String,
    symbol: Int,
    fontSizeSp: Float,
    plain: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .heightIn(min = 30.dp)
            .background(theme.fill, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .onyxTappable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!plain) OnyxIcon(symbol = symbol, tint = theme.secondaryText, size = 13.dp)
        BasicText(
            text = text,
            style = theme.caption.copy(fontSize = fontSizeSp.sp, color = theme.primaryText),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            localize = false,
        )
        if (!plain) OnyxIcon(symbol = Symbols.ChevronDown, tint = theme.tertiaryText, size = 11.dp)
    }
}

/** Le détail d'une session : sa tâche, sa branche, ses paramètres réels et son état observé. */
@Composable
private fun CodeDetail(session: CodeSession, onDismiss: () -> Unit) {
    val rows = buildList {
        add("Branch" to session.branch)
        session.repoPath?.let { add("Repository" to it) }
        session.worktreePath?.let { add("Worktree" to it) }
        add(
            "Started" to OnyxLocalization.format(
                "%1\$s ago",
                codeElapsed(session.createdAtMillis, System.currentTimeMillis()),
            ),
        )
        add("Held on this phone" to statusLabel(session.status))
    }
    OnyxAlert(
        title = session.title,
        message = rows.joinToString("\n") { (label, value) -> "${OnyxLocalization[label]}: $value" },
        actions = listOf(AlertAction(label = "Done", onSelect = onDismiss)),
        onDismiss = onDismiss,
        // Les valeurs (un chemin, une branche) sont verbatim ; seul le libellé de la ligne traduit.
        localizeMessage = false,
    )
}

/**
 * La bande d'un échec : le message exact du serveur, de quoi **réessayer**, et de quoi l'effacer.
 *
 * Le « Réessayer » n'est pas un ornement : avant lui, la seule issue d'un échec de lecture était de
 * la **fermer** — l'écran restait alors vide sans dire pourquoi (audit, T6). Il relit maintenant ce
 * qui a échoué ([onRetry]).
 */
@Composable
private fun ProblemBar(problem: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(theme.error.copy(alpha = 0.15f), RoundedCornerShape(OnyxSpace.FormCorner))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(
            text = problem,
            style = theme.footnote.copy(color = theme.error),
            modifier = Modifier.weight(1f),
            localize = false,
        )
        BasicText(
            text = "Try Again",
            style = theme.footnote.copy(color = theme.accent, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.onyxTappable { onRetry() },
        )
        OnyxIcon(
            symbol = Symbols.Xmark,
            tint = theme.error,
            size = 16.dp,
            modifier = Modifier.onyxTappable { onDismiss() },
        )
    }
}
