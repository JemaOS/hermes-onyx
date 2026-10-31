package com.onyx.app.ui.code

import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.OnyxSurfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.CodeSession
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.FrostSurface
import com.onyx.app.ui.design.OnyxAlert
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.OnyxPrompt
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.frostedBar
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Les actions d'une **session de code** : renommer, modifier, relancer, supprimer.
 *
 * Le propriétaire a signalé qu'une session listée n'était « ni modifiable, ni renommable, ni
 * supprimable » : la fiche s'affichait, et rien ne permettait d'y toucher. Les quatre gestes vivent
 * ici, dans le **menu ancré maison** — celui des bots et des conversations —, atteignable par
 * **appui long** sur la carte *et* par un bouton **⋮** visible : les deux, parce qu'un geste caché
 * n'est pas découvrable.
 *
 * Rien ici ne touche le serveur : ces actions réécrivent la **fiche locale** (`onyx_code_sessions`),
 * et la confirmation de suppression le dit noir sur blanc — la conversation Hermes reste.
 */

/** Ce qu'une fiche propose dans son menu, dans l'ordre : les gestes d'abord, la suppression à part. */
internal enum class CodeSessionAction {
    /** « Renommer » : le titre seul, écrit tout de suite. */
    Rename,

    /** « Modifier… » : dépôt, copie de travail, branche et demande. */
    Edit,

    /** « Relancer » : la demande rejouée dans une session neuve. */
    RunAgain,

    /** « Supprimer » : la fiche s'en va, la conversation reste. */
    Delete,
}

/** Le libellé d'une action, tel qu'il s'affiche (et se traduit par le dictionnaire central). */
internal fun codeSessionActionLabel(action: CodeSessionAction): String = when (action) {
    CodeSessionAction.Rename -> "Rename"
    CodeSessionAction.Edit -> "Edit…"
    CodeSessionAction.RunAgain -> "Run again"
    CodeSessionAction.Delete -> "Delete"
}

/** Le symbole maison de chaque action. */
internal fun codeSessionActionSymbol(action: CodeSessionAction): Int = when (action) {
    CodeSessionAction.Rename -> Symbols.Pencil
    CodeSessionAction.Edit -> Symbols.SquareAndPencil
    CodeSessionAction.RunAgain -> Symbols.ArrowClockwise
    CodeSessionAction.Delete -> Symbols.Trash
}

/**
 * Le menu d'une session : le panneau **ancré** à la carte (ou au bouton ⋮), jamais au centre de
 * l'écran, fermé par un appui à côté, par Échap et par le retour.
 *
 * La suppression est **destructrice**, séparée des trois autres par un filet, et elle passe par une
 * confirmation — jamais une ligne qui efface d'un coup.
 */
@Composable
internal fun CodeSessionMenu(
    session: CodeSession,
    /** Les limites de la carte (ou du bouton) dans la fenêtre : le panneau s'ouvre contre elles. */
    anchor: Rect?,
    onDismiss: () -> Unit,
    onRename: (CodeSession) -> Unit,
    onEdit: (CodeSession) -> Unit,
    onRunAgain: (CodeSession) -> Unit,
    onDelete: (CodeSession) -> Unit,
) {
    val actions = listOf(
        CodeSessionAction.Rename,
        CodeSessionAction.Edit,
        CodeSessionAction.RunAgain,
        CodeSessionAction.Delete,
    )
    OnyxMenu(
        items = actions.map { action ->
            MenuItem(
                label = codeSessionActionLabel(action),
                symbol = codeSessionActionSymbol(action),
                destructive = action == CodeSessionAction.Delete,
                // Un filet devant la suppression, et un seul : elle ne se touche pas par accident.
                groupBreak = action == CodeSessionAction.Delete,
            ) {
                onDismiss()
                when (action) {
                    CodeSessionAction.Rename -> onRename(session)
                    CodeSessionAction.Edit -> onEdit(session)
                    CodeSessionAction.RunAgain -> onRunAgain(session)
                    CodeSessionAction.Delete -> onDelete(session)
                }
            }
        },
        onDismiss = onDismiss,
        anchor = anchor,
    )
}

/**
 * « Renommer » : la même invite que le reste de l'app — un champ, Save ou Cancel.
 *
 * La sauvegarde est **immédiate** ; le titre vide ne valide pas (le bouton reste estompé et sans
 * appui), et le message dit que la **branche** ne suit pas — c'est celle d'une vraie branche du
 * dépôt.
 */
@Composable
internal fun CodeRenamePrompt(
    session: CodeSession,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OnyxPrompt(
        title = "Rename session",
        message = "The task's title. The branch keeps its name: it is the one in the repository.",
        placeholder = "Title",
        value = value,
        onValueChange = onValueChange,
        confirmLabel = "Save",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmEnabled = value.trim().isNotEmpty() && value.trim() != session.title,
    )
}

/**
 * « Supprimer » : la confirmation, avec la conséquence **dite**.
 *
 * Deux issues seulement : « Annuler » d'abord, « Supprimer » en rouge — et la phrase qui empêche la
 * mauvaise surprise : *la fiche est retirée de ce téléphone, la conversation reste dans
 * l'historique*. La suppression d'une fiche locale n'est **jamais** la suppression de la session
 * Hermes.
 */
@Composable
internal fun CodeDeleteAlert(
    session: CodeSession,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OnyxAlert(
        title = "Delete this session?",
        message = "The card is removed from this phone. The conversation stays in your history.",
        actions = listOf(
            AlertAction(label = "Cancel", onSelect = onDismiss),
            AlertAction(label = "Delete", destructive = true, onSelect = onConfirm),
        ),
        onDismiss = onDismiss,
    )
}

/**
 * La feuille « Modifier » : dépôt, copie de travail, branche — et la demande, quand on veut la
 * corriger. Elle travaille sur une **copie** : rien ne part sur le disque avant « Enregistrer ».
 *
 * C'est une feuille **dans l'écran** (pas une fenêtre à part) : ses puces ouvrent le même menu
 * ancré que le compositeur, dans le même repère, et le panneau se pose contre la puce touchée.
 */
@Composable
internal fun CodeEditSheet(
    session: CodeSession,
    title: String,
    branch: String,
    prompt: String,
    repo: String?,
    worktree: String?,
    repos: List<String>,
    worktrees: List<com.onyx.app.data.net.GitWorktree>,
    onTitle: (String) -> Unit,
    onBranch: (String) -> Unit,
    onPrompt: (String) -> Unit,
    onChooseRepo: (String) -> Unit,
    onChooseWorktree: (String?) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    // La puce ouverte et sa place dans la fenêtre : c'est ce qui ancre le panneau.
    var picker by remember { mutableStateOf<String?>(null) }
    var repoBounds by remember { mutableStateOf<Rect?>(null) }
    var worktreeBounds by remember { mutableStateOf<Rect?>(null) }
    val canSave = title.trim().isNotEmpty() && branch.trim().isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            // Un appui à côté de la feuille la referme, comme les autres panneaux de l'app.
            .onyxTappable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 340.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                // Le panneau lui-même n'est pas une zone de fermeture : appuyer entre deux champs
                // ne referme pas la feuille par accident.
                .onyxTappable(onClick = {})
                .frostedBar(FrostSurface.SHEET, RoundedCornerShape(OnyxSpace.FieldCorner), tint = OnyxSurfaces.surfaceHigh(theme))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(
                text = "Edit session",
                style = theme.headline.copy(color = theme.textColor, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
            BasicText(
                text = "The card is kept on this phone; the server keeps the repository, its worktrees and its branches.",
                style = theme.caption.copy(color = theme.secondaryText, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )

            EditField(label = "Title", value = title, placeholder = "Title", onChange = onTitle)
            EditField(label = "Branch", value = branch, placeholder = "code/<task>", onChange = onBranch)

            // La demande : plusieurs lignes, c'est celle que « Relancer » rejouera.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(
                    text = "Request",
                    style = theme.caption.copy(color = theme.secondaryText),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.fill, RoundedCornerShape(OnyxSpace.ControlCorner))
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                ) {
                    OnyxTextField(
                        value = prompt,
                        onValueChange = onPrompt,
                        placeholder = "Describe a change…",
                        style = theme.body,
                        singleLine = false,
                        maxLines = 4,
                    )
                }
            }

            // Les deux puces du serveur : elles ouvrent la liste réelle, comme au compositeur.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditChip(
                    text = repo?.substringAfterLast('/') ?: OnyxLocalization["Repository"],
                    symbol = Symbols.Folder,
                    modifier = Modifier.onGloballyPositioned { repoBounds = it.boundsInWindow() },
                    onClick = { picker = "repo" },
                )
                EditChip(
                    // « Nouveau worktree » est la traduction de la clé par le dictionnaire central ;
                    // la puce, elle, affiche la valeur telle quelle (un nom de dossier).
                    text = worktree?.substringAfterLast('/') ?: OnyxLocalization["New worktree"],
                    symbol = Symbols.FolderBadgePlus,
                    modifier = Modifier.onGloballyPositioned { worktreeBounds = it.boundsInWindow() },
                    onClick = { picker = "worktree" },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = "Cancel",
                    style = theme.body.copy(color = theme.secondaryText),
                    modifier = Modifier.onyxTappable(onClick = onDismiss),
                )
                BasicText(
                    text = "Save",
                    style = theme.body.copy(
                        color = if (canSave) theme.accent else theme.tertiaryText,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier
                        .padding(start = 20.dp)
                        .onyxTappable(enabled = canSave, onClick = onSave),
                )
            }
        }
    }

    // Les listes réelles du serveur, ancrées à la puce touchée.
    when (picker) {
        "repo" -> OnyxMenu(
            items = repos.map { path ->
                MenuItem(
                    label = path.substringAfterLast('/'),
                    checked = path == repo,
                    localize = false,
                    onSelect = { picker = null; onChooseRepo(path) },
                )
            },
            onDismiss = { picker = null },
            anchor = repoBounds,
        )
        "worktree" -> OnyxMenu(
            items = worktrees
                .filterNot { it.isMain }
                .map { tree ->
                    MenuItem(
                        label = tree.branch ?: tree.path.substringAfterLast('/'),
                        checked = tree.path == worktree,
                        localize = false,
                        onSelect = { picker = null; onChooseWorktree(tree.path) },
                    )
                } + MenuItem(
                // « Aucune pour le moment » : la session travaille dans le dépôt lui-même.
                label = "None yet",
                checked = worktree == null,
                onSelect = { picker = null; onChooseWorktree(null) },
            ),
            onDismiss = { picker = null },
            anchor = worktreeBounds,
        )
        else -> Unit
    }
}

/** Une rangée de la feuille : son libellé à gauche, son champ à droite. */
@Composable
private fun EditField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(
            text = label,
            style = theme.footnote.copy(color = theme.secondaryText),
            modifier = Modifier.widthIn(min = 56.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .background(theme.fill, RoundedCornerShape(OnyxSpace.ControlCorner))
                .padding(horizontal = 8.dp, vertical = 7.dp),
        ) {
            OnyxTextField(
                value = value,
                onValueChange = onChange,
                placeholder = placeholder,
                style = theme.body,
                modifier = Modifier.heightIn(min = 20.dp),
            )
        }
    }
}

/** Une puce de la feuille de modification : elle ouvre une liste, elle ne saisit rien. */
@Composable
private fun EditChip(
    text: String,
    symbol: Int,
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
        OnyxIcon(symbol = symbol, tint = theme.secondaryText, size = 13.dp)
        BasicText(
            text = text,
            style = theme.caption.copy(color = theme.primaryText),
            maxLines = 1,
            softWrap = false,
            localize = false,
        )
        OnyxIcon(symbol = Symbols.ChevronDown, tint = theme.tertiaryText, size = 11.dp)
    }
}
