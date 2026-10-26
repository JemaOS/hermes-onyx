package com.onyx.app.ui.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.net.DashboardSkill
import com.onyx.app.data.net.GatewayToolset
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxSwitch
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.OnyxToggleRow
import com.onyx.app.ui.design.OnyxValueRow
import com.onyx.app.ui.design.OrbAvatar
import com.onyx.app.ui.design.OrbFaces
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme

/** Un modèle de bot, comme `BotTemplate`: nom, ce qu'il fait, et son prompt. */
data class BotTemplateSpec(
    val id: String,
    val title: String,
    val about: String,
    val soul: String,
    val orb: Int,
) {
    companion object {
        val blank = BotTemplateSpec("blank", "", "", "", 0)

        /** Les cinq modèles , mot pour mot. */
        val all = listOf(
            BotTemplateSpec(
                id = "researcher",
                title = "Researcher",
                about = "Looks things up on the web and comes back with what it found, and where it found it.",
                soul = """# Researcher

**Mission:** Find things out, and say how you know.

You look things up for the person: on the web, in the pages you open, and in what was said in earlier chats. You come back with the answer first, then the few facts that carry it, each with where it came from. When sources disagree, you say so and say which you trust more and why. When you can't find something, you say that plainly rather than guess.

You are curious and careful. You keep answers short unless asked for more, and you ask one question when a request could mean two different things.""",
                orb = 0,
            ),
            BotTemplateSpec(
                id = "writer",
                title = "Writer",
                about = "Drafts, edits and tightens writing, in your voice.",
                soul = """# Writer

**Mission:** Help the person say what they mean, well.

You draft, edit and tighten writing: messages, posts, letters, notes, stories. You keep the person's own voice and say what they mean in fewer words. You give the new version first, then, only when it helps, a line on what you changed and why.

You write plainly. You cut what doesn't earn its place, prefer short words to long ones, and never pad. You ask who it is for when that would change how it reads.""",
                orb = 6,
            ),
            BotTemplateSpec(
                id = "coder",
                title = "Coder",
                about = "Reads and changes code, runs it, and says what it did.",
                soul = """# Coder

**Mission:** Make the change the person asked for, and show it works.

You read code before you change it, change only what the task needs, and run what you changed to see it work. You say what you did in a few lines: what changed, where, and how you checked it. When something fails, you show the error and what you tried.

You ask before anything that can't be undone: deleting files, rewriting history, touching anything outside the project.""",
                orb = 12,
            ),
            BotTemplateSpec(
                id = "home",
                title = "Home",
                about = "Looks after the house: lights, heating, sensors and routines.",
                soul = """# Home

**Mission:** Keep the house running the way the person likes it.

You look after the home: lights, heating, sensors, locks and the routines that tie them together. You check how things stand before you change them, and say what you changed in a line. You remember how the person likes things and when.

You ask before anything that affects safety or security, such as locks, alarms or the heating left on while nobody is home.""",
                orb = 3,
            ),
            BotTemplateSpec(
                id = "planner",
                title = "Planner",
                about = "Keeps your plans, lists and reminders, and nudges you on time.",
                soul = """# Planner

**Mission:** Keep track of what the person means to do, and remind them in time.

You keep lists, plans and reminders. You turn "I should" into a task with a when, put repeating things on a schedule, and nudge the person before something is due, not after. When asked what is next, you give the next few things in order, nothing more.

You are brief and calm. You never add a task the person didn't ask for; you suggest it.""",
                orb = 18,
            ),
        )
    }
}

/**
 * L'éditeur d'un bot.
 *
 * Comme: son visage, son nom et sa poignée, sa voix, ce qu'il fait, sa
 * personnalité, son modèle, ses outils et ses compétences, sa visibilité et sa suppression —
 * mêmes sections, mêmes pieds de section, mêmes textes.
 */
@Composable
fun BotEditorScreen(
    bot: BotProfile?,
    faceCode: String?,
    mainName: String,
    onFaceChosen: (String?) -> Unit,
    onOpenPrompt: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenVoice: () -> Unit,
    /**
     * La voix que ce bot lit de son cru (`BotVoicePicker.summary`): « Automatique », « Identique à
     * l'agent principal », le nom de sa voix — et sa vitesse quand il en a une.
     */
    voiceSummary: String = "Automatic",
    models: List<String> = emptyList(),
    onPickModel: (String?) -> Unit = {},
    onSave: (title: String, about: String, model: String?, shown: Boolean) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    // Les jeux d'outils que le serveur offre, ceux que ce bot a choisis (`null`: ceux du principal),
    // et ceux du principal — comme `offered` / `toolsets` / `mainToolsets`.
    toolsets: List<GatewayToolset> = emptyList(),
    toolsSelection: Set<String>? = null,
    mainToolsets: Set<String> = emptySet(),
    onToolsSelectionChange: (Set<String>?) -> Unit = {},
    // Les compétences du bot, chacune allumée ou non, et celles qu'il lit en entier au début de
    // chaque message — comme `skills` / `alwaysLoaded`.
    skills: List<DashboardSkill> = emptyList(),
    alwaysLoadedSkills: Set<String> = emptySet(),
    onToggleSkill: (DashboardSkill, Boolean) -> Unit = { _, _ -> },
    onPinSkill: (DashboardSkill, Boolean) -> Unit = { _, _ -> },
    /** Ce qui a échoué (une suppression refusée par le serveur, une écriture non gardée). */
    problem: String? = null,
    // Un écran dédié pour chaque liste, si le parent en a un; sans lui, la liste se déplie ici.
    onOpenTools: (() -> Unit)? = null,
    onOpenSkills: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val isNew = bot == null
    val isMain = bot?.isDefault == true

    // Comme `BotEditor` (`Original(title: editing.title ?? editing.displayName ?? "")`): le champ
    // part du titre du bot, sinon de son `display_name`; vide seulement si le profil n'a ni l'un ni l'autre.
    var title by remember(bot?.name) {
        mutableStateOf(bot?.title?.takeIf { it.isNotBlank() } ?: bot?.displayName.orEmpty())
    }
    var about by remember(bot?.name) { mutableStateOf(bot?.description.orEmpty()) }
    var template by remember { mutableStateOf(BotTemplateSpec.blank.id) }
    var shown by remember(bot?.name) { mutableStateOf(bot?.isHidden != true) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    // Les listes « Tools » et « Skills » dépliées sous leur rangée, quand le parent n'a pas fourni
    // d'écran dédié.
    var showTools by remember { mutableStateOf(false) }
    var showSkills by remember { mutableStateOf(false) }

    val typed = title.trim()

    // Le résumé de ses jeux d'outils, comme `BotEditor.toolsSummary`: « Same as {mainName} »
    // quand il suit le principal, sinon le nom des deux premiers choisis, ou « n of m ».
    val toolsSummary: String = if (toolsSelection == null || toolsSelection == mainToolsets) {
        OnyxLocalization.format("Same as %1\$s", mainName)
    } else {
        val names = toolsets.filter { toolsSelection.contains(it.name) }.map { it.title }
        when {
            names.isEmpty() -> OnyxLocalization["None"]
            names.size <= 2 -> names.joinToString(", ")
            else -> OnyxLocalization.format("%1\$d of %2\$d", names.size, toolsets.size)
        }
    }

    // Le résumé de ses compétences, comme `BotEditor.skillsSummary`: le nombre allumées, puis
    // celles épinglées (« always loaded »).
    val skillsSummary: String = if (skills.isEmpty()) {
        OnyxLocalization["All"]
    } else {
        val on = skills.count { it.enabled }
        val counts = if (on == skills.size) {
            OnyxLocalization.format("All %1\$d", skills.size)
        } else {
            OnyxLocalization.format("%1\$d of %2\$d", on, skills.size)
        }
        if (alwaysLoadedSkills.isEmpty()) {
            counts
        } else {
            counts + " · " + OnyxLocalization.format("%1\$d always", alwaysLoadedSkills.size)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = if (isNew) "New Bot" else if (isMain) mainName else typed.ifEmpty { bot?.label(mainName) ?: "Bot" },
                onBack = onCancel,
                action = if (isNew) "Create" else "Save",
                onAction = { onSave(title, about, bot?.model, shown) },
                localizeTitle = isNew,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            // Le visage, en grand, et « Change Face ».
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BotLook(
                    name = bot?.name ?: typed.ifEmpty { "new-bot" },
                    size = 96.dp,
                    faceCode = faceCode,
                )
                BasicText(
                    text = "Change Face",
                    style = theme.subheadline.copy(color = theme.accent),
                    modifier = Modifier.onyxClickable { onFaceChosen(faceCode) },
                )
            }

            if (isNew) {
                OnyxFormSection(
                    header = "Start from",
                    footer = "Its name, what it does, its personality, face and tools filled in. Change any of them before you make it.",
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TemplateTile(
                            label = "Blank",
                            symbol = Symbols.Plus,
                            selected = template == BotTemplateSpec.blank.id,
                        ) {
                            template = BotTemplateSpec.blank.id
                            title = ""
                            about = ""
                        }
                        BotTemplateSpec.all.forEach { spec ->
                            TemplateTile(
                                label = spec.title,
                                orb = spec.orb,
                                selected = template == spec.id,
                            ) {
                                template = spec.id
                                title = spec.title
                                about = spec.about
                            }
                        }
                    }
                }
            }

            if (!isMain) {
                OnyxFormSection(
                    header = "Name",
                    footer = if (bot != null) {
                        OnyxLocalization.format("Others call it @%1\$s. Its handle stays @%1\$s.", bot.handle, bot.handle)
                    } else if (typed.isNotEmpty()) {
                        OnyxLocalization.format("Others call it @%1\$s.", typed.lowercase().replace(' ', '_'))
                    } else {
                        "Give it a name with a letter or number in it."
                    },
                ) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OnyxTextField(
                            value = title,
                            onValueChange = { title = it },
                            placeholder = "Scout",
                            style = theme.body,
                        )
                    }
                }
            }

            if (bot != null && !isMain) {
                OnyxFormSection(footer = "What its replies are read in, in voice mode, group chats and the car.") {
                    OnyxRow(onClick = onOpenVoice) {
                        BasicText("Voice", style = theme.body.copy(color = theme.textColor), modifier = Modifier.weight(1f))
                        BasicText(
                            text = voiceSummary,
                            style = theme.body.copy(color = theme.secondaryText),
                        )
                        OnyxIcon(Symbols.ChevronRight, tint = theme.tertiaryText, size = 12.dp)
                    }
                }
            }

            OnyxFormSection(
                header = "What it does",
                footer = "Under its name in your chat list, and how the other bots know what to ask it.",
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OnyxTextField(
                        value = about,
                        onValueChange = { about = it },
                        placeholder = "What it's for",
                        singleLine = false,
                        maxLines = 4,
                    )
                }
            }

            OnyxFormSection(
                footer = if (isMain) {
                    "Your agent's own: its model is picked in each conversation, its name in Settings."
                } else if (isNew) {
                    "It starts as a copy of %1\$s's setup on your server: its settings, API keys and sign-ins, so it can use the same models and tools. Its chats and memories are its own.".let { template ->
                        OnyxLocalization.format(template, mainName)
                    }
                } else {
                    null
                },
            ) {
                OnyxRow(onClick = onOpenPrompt) {
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText("Personality", style = theme.body.copy(color = theme.textColor))
                        BasicText(
                            text = "Written on the server, in its profile's SOUL.md",
                            style = theme.footnote.copy(color = theme.secondaryText),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OnyxIcon(Symbols.ChevronRight, tint = theme.tertiaryText, size = 12.dp)
                }

                if (!isMain) {
                    OnyxRowDivider()
                    OnyxValueRow(
                        label = "Model",
                        value = bot?.model ?: if (isNew) "Same as $mainName" else "Not set",
                        localizeValue = bot?.model == null,
                        symbol = Symbols.Bolt,
                        onClick = { showModels = !showModels },
                    )
                    if (showModels) {
                        OnyxRowDivider()
                        OnyxRow(onClick = {
                            showModels = false
                            onPickModel(null)
                        }) {
                            OnyxIcon(
                                symbol = if (bot?.model == null) Symbols.CheckmarkCircle else Symbols.Circle,
                                tint = if (bot?.model == null) theme.accent else theme.tertiaryText,
                                size = 18.dp,
                            )
                            BasicText(
                                text = "Same as $mainName",
                                style = theme.body.copy(color = theme.textColor),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        models.forEach { model ->
                            OnyxRowDivider()
                            OnyxRow(onClick = {
                                showModels = false
                                onPickModel(model)
                            }) {
                                OnyxIcon(
                                    symbol = if (bot?.model == model) Symbols.CheckmarkCircle else Symbols.Circle,
                                    tint = if (bot?.model == model) theme.accent else theme.tertiaryText,
                                    size = 18.dp,
                                )
                                BasicText(
                                    text = model,
                                    localize = false,
                                    style = theme.body.copy(color = theme.textColor),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    OnyxRowDivider()
                    // « Tools »: ouvre la liste des jeux d'outils du bot, chacun avec son
                    // interrupteur. Le parent peut fournir son propre écran (`onOpenTools`); à
                    // défaut, la liste se déplie juste en dessous.
                    OnyxValueRow(
                        label = "Tools",
                        value = toolsSummary,
                        localizeValue = false,
                        symbol = Symbols.Bolt,
                        onClick = {
                            if (onOpenTools != null) onOpenTools() else showTools = !showTools
                        },
                    )
                    if (bot != null) {
                        OnyxRowDivider()
                        // « Skills »: ouvre la liste des compétences du bot, chacune avec son
                        // interrupteur et son épingle (« always loaded »).
                        OnyxValueRow(
                            label = "Skills",
                            value = skillsSummary,
                            localizeValue = false,
                            symbol = Symbols.Sparkles,
                            onClick = {
                                if (onOpenSkills != null) onOpenSkills() else showSkills = !showSkills
                            },
                        )
                    }
                }

                if (bot != null && !isMain) {
                    OnyxRowDivider()
                    OnyxValueRow(label = "Schedules", value = "", symbol = Symbols.Clock, onClick = onOpenSchedules)
                }
            }

            // Les listes dépliées, quand le parent n'a pas fourni d'écran dédié (`onOpenTools` /
            // `onOpenSkills` à `null`, cas de l'app seule).
            if (!isMain && showTools && onOpenTools == null) {
                BotToolsList(
                    offered = toolsets,
                    selection = toolsSelection,
                    main = mainToolsets,
                    mainName = mainName,
                    onSelectionChange = onToolsSelectionChange,
                )
            }
            if (bot != null && showSkills && onOpenSkills == null) {
                BotSkillsList(
                    name = if (isMain) mainName else typed.ifEmpty { bot.label(mainName) },
                    skills = skills,
                    alwaysLoaded = alwaysLoadedSkills,
                    onToggle = onToggleSkill,
                    onPin = onPinSkill,
                    // `BotEditor.hasSkillsTool`: sans l'outil « Skills », aucune n'est lue.
                    hasSkillsTool = isMain || toolsets.isEmpty() ||
                        (toolsSelection ?: mainToolsets).contains("skills"),
                )
            }

            if (bot != null && !isMain) {
                OnyxFormSection(footer = "A hidden bot stays on your server; find it again under Hidden Bots at the end of your bots.") {
                    OnyxToggleRow(
                        label = "Show in the chat list",
                        checked = shown,
                        onCheckedChange = { shown = it },
                    )
                }
                OnyxFormSection {
                    OnyxRow(onClick = { confirmingDelete = true }) {
                        BasicText("Delete Bot", style = theme.body.copy(color = theme.error))
                    }
                }
            }

            if (confirmingDelete) {
                OnyxFormSection {
                    OnyxRow(onClick = {
                        confirmingDelete = false
                        onDelete()
                    }) {
                        BasicText(
                            text = "Delete ${bot?.label(mainName) ?: "this bot"}?",
                            style = theme.body.copy(color = theme.error, fontWeight = FontWeight.SemiBold),
                        )
                    }
                    OnyxRowDivider()
                    OnyxRow(onClick = { confirmingDelete = false }) {
                        BasicText("Cancel", style = theme.body.copy(color = theme.secondaryText))
                    }
                }
            }

            // Ce qui a échoué, comme le `Label(problem, …)` orange de `BotEditor`: une
            // suppression que le serveur a refusée, un réglage qu'il n'a pas gardé. L'écran
            // reste ouvert, le bot reste là — rien n'a été fait en douce.
            problem?.let { message ->
                OnyxFormSection {
                    OnyxRow {
                        OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 16.dp)
                        BasicText(
                            text = message,
                            localize = false,
                            style = theme.footnote.copy(color = theme.warning),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Une tuile de modèle: un orbe (ou « + » pour Blank), puis son nom. */
@Composable
private fun TemplateTile(
    label: String,
    orb: Int? = null,
    symbol: Int? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .background(
                color = if (selected) theme.accent.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .onyxTappable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (orb != null) {
            OrbAvatar(name = label, size = 52.dp, override = OrbFaces.all[orb % OrbFaces.all.size])
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(theme.fill, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                OnyxIcon(symbol = symbol ?: Symbols.Plus, tint = theme.secondaryText, size = 22.dp)
            }
        }
        BasicText(text = label, style = theme.caption.copy(color = theme.textColor, textAlign = TextAlign.Center))
    }
}

/**
 * Les jeux d'outils d'un bot (`BotToolsList`).
 *
 * Une première section offre « Same as {mainName} » — le bot suit alors les jeux du principal.
 * La seconde liste chaque jeu que le serveur propose, avec son interrupteur: plus courts sont les
 * outils, plus vite chaque tour du bot démarre sur un modèle qui tourne sur la machine.
 */
@Composable
fun BotToolsList(
    offered: List<GatewayToolset>,
    selection: Set<String>?,
    main: Set<String>,
    mainName: String,
    onSelectionChange: (Set<String>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Column(modifier = modifier.fillMaxWidth()) {
        // « Same as {mainName} »: rien de propre au bot, il hérite des jeux du principal.
        OnyxFormSection(footer = "The tools $mainName has.") {
            OnyxRow(onClick = { onSelectionChange(null) }) {
                OnyxIcon(
                    symbol = if (selection == null) Symbols.CheckmarkCircle else Symbols.Circle,
                    tint = if (selection == null) theme.accent else theme.tertiaryText,
                    size = 18.dp,
                )
                BasicText(
                    text = "Same as $mainName",
                    style = theme.body.copy(color = theme.textColor),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Les jeux offerts, chacun sur « on » ou « off » pour ce bot.
        OnyxFormSection(
            footer = OnyxLocalization.format(
                "Fewer tools make a bot's every turn quicker to start on a model running on your own machine. Hermes 0.21.5 and later keep a bot to these; earlier ones give every bot all of %1\$s's.",
                mainName,
            ),
        ) {
            offered.forEachIndexed { index, toolset ->
                if (index > 0) OnyxRowDivider()
                val on = (selection ?: main).contains(toolset.name)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(toolset.title, localize = true, style = theme.body.copy(color = theme.textColor))
                        toolset.description?.takeIf { it.isNotBlank() }?.let { description ->
                            BasicText(
                                text = description,
                                localize = true,
                                style = theme.caption.copy(color = theme.secondaryText),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    OnyxSwitch(
                        checked = on,
                        onCheckedChange = { checked ->
                            val next = (selection ?: main).toMutableSet()
                            if (checked) next += toolset.name else next -= toolset.name
                            onSelectionChange(next)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Les compétences d'un bot (`BotSkillsList`).
 *
 * Chacune est allumée ou éteinte pour lui seul; les épinglées sont lues en entier au début de
 * chaque message. Sans l'outil « Skills » du bot, aucune n'est lue — l'écran le dit.
 */
@Composable
fun BotSkillsList(
    name: String,
    skills: List<DashboardSkill>,
    alwaysLoaded: Set<String>,
    onToggle: (DashboardSkill, Boolean) -> Unit,
    onPin: (DashboardSkill, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hasSkillsTool: Boolean = true,
) {
    val theme = LocalOnyxTheme.current
    Column(modifier = modifier.fillMaxWidth()) {
        OnyxFormSection(
            footer = OnyxLocalization.format(
                "On, %1\$s sees a skill listed and reads it only when it needs it. Off, it doesn't see it at all. Pinned, it reads the skill in full at the start of every message: keep that to the few it needs every time, as each makes every message slower to start. A pin takes effect in new chats, and in %1\$s's ongoing chat once that starts fresh or is condensed.",
                name,
            ),
        ) {
            when {
                !hasSkillsTool -> OnyxRow {
                    OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 16.dp)
                    BasicText(
                        text = "$name's Skills tool is off (Edit Bot → Tools), so it reads none of these.",
                        style = theme.footnote.copy(color = theme.secondaryText),
                        modifier = Modifier.weight(1f),
                    )
                }

                skills.isEmpty() -> OnyxRow {
                    BasicText("No skills yet", style = theme.body.copy(color = theme.secondaryText))
                }

                else -> {
                    // Les compétences groupées par catégorie, dans l'ordre alphabétique — comme les
                    // sections « Catégorie · n ».
                    val groups = skills
                        .groupBy { it.category?.takeIf { category -> category.isNotBlank() } ?: "Other" }
                        .toSortedMap()
                    var first = true
                    groups.forEach { (category, list) ->
                        if (!first) OnyxRowDivider()
                        first = false
                        OnyxRow {
                            BasicText(
                                text = "${category.replaceFirstChar { it.uppercase() }} · ${list.size}",
                                style = theme.footnote.copy(color = theme.secondaryText),
                            )
                        }
                        list.sortedBy { it.name }.forEach { skill ->
                            OnyxRowDivider()
                            SkillRow(
                                skill = skill,
                                pinned = alwaysLoaded.contains(skill.name),
                                onToggle = onToggle,
                                onPin = onPin,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Une compétence: son nom, son épingle, sa phrase, son interrupteur. */
@Composable
private fun SkillRow(
    skill: DashboardSkill,
    pinned: Boolean,
    onToggle: (DashboardSkill, Boolean) -> Unit,
    onPin: (DashboardSkill, Boolean) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(skill.name, localize = false, style = theme.body.copy(color = theme.textColor))
            if (pinned) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OnyxIcon(Symbols.Pin, tint = theme.accent, size = 10.dp)
                    BasicText(
                        text = "Always loaded",
                        style = theme.caption2.copy(color = theme.accent, fontWeight = FontWeight.SemiBold),
                    )
                }
            }
            if (skill.description.isNotBlank()) {
                BasicText(
                    text = skill.description,
                    localize = false,
                    style = theme.caption.copy(color = theme.secondaryText),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // L'épingle n'existe que pour une compétence allumée: éteinte, elle n'est de toute façon
        // pas lue.
        if (skill.enabled) {
            OnyxIcon(
                symbol = Symbols.Pin,
                tint = if (pinned) theme.accent else theme.tertiaryText,
                size = 16.dp,
                modifier = Modifier.onyxTappable { onPin(skill, !pinned) },
            )
        }
        OnyxSwitch(checked = skill.enabled, onCheckedChange = { onToggle(skill, it) })
    }
}
