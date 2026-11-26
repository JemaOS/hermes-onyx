package com.onyx.app.ui.bots

import com.onyx.app.ui.design.OnyxBase
import com.onyx.app.ui.design.socleShadow

import com.onyx.app.ui.design.onyxPage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.net.DashboardSkill
import com.onyx.app.data.net.GatewayToolset
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxConfirm
import com.onyx.app.ui.design.BotAvatar
import com.onyx.app.ui.design.BotFace
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.BotPose
import com.onyx.app.ui.design.FaceEyes
import com.onyx.app.ui.design.FaceMark
import com.onyx.app.ui.design.FaceMouth
import com.onyx.app.ui.design.FaceShape
import com.onyx.app.ui.design.IdentifierText
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.Lucide
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.design.SocleLargeHeader
import com.onyx.app.ui.design.SocleSwitch
import com.onyx.app.ui.design.SocleTile
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.design.socleBodyStyle
import com.onyx.app.ui.design.socleMetaStyle
import com.onyx.app.ui.design.socleMonoStyle
import com.onyx.app.ui.design.socleSectionStyle
import com.onyx.app.ui.design.socleTitleStyle
import com.onyx.app.ui.design.rememberBotSeconds
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.voice.voicePose
import com.onyx.app.voice.VoicePhase

/**
 * A bot template, like `BotTemplate` in the reference: name, what it does, its prompt, and its
 * face.
 *
 * The face is part of the template: "Start from" promises "its name, what it does, its
 * personality, **its face** and its tools filled in" — so each tile draws its template's creature
 * (shape, eyes, mouth, mark, colour), and the created bot receives it. The "Blank" template has
 * none: it is the one that starts from nothing.
 */
data class BotTemplateSpec(
    val id: String,
    val title: String,
    val about: String,
    val soul: String,
    /** The face this template gives: what shows on its tile and on the created bot. */
    val face: BotFace? = null,
) {
    companion object {
        val blank = BotTemplateSpec("blank", "", "", "")

        /** The five templates of the reference, word for word, each with its own creature. */
        val all = listOf(
            BotTemplateSpec(
                id = "researcher",
                title = "Researcher",
                // Curious: the sparkling eye, the receiving antenna, the sky.
                face = BotFace(
                    shape = FaceShape.EGG,
                    eyes = FaceEyes.SPARKLE,
                    mouth = FaceMouth.SMILE,
                    mark = FaceMark.ANTENNA,
                    colour = 8,
                    glance = 1,
                    tilt = -3,
                ),
                about = "Looks things up on the web and comes back with what it found, and where it found it.",
                soul = """# Researcher

**Mission:** Find things out, and say how you know.

You look things up for the person: on the web, in the pages you open, and in what was said in earlier chats. You come back with the answer first, then the few facts that carry it, each with where it came from. When sources disagree, you say so and say which you trust more and why. When you can't find something, you say that plainly rather than guess.

You are curious and careful. You keep answers short unless asked for more, and you ask one question when a request could mean two different things.""",
            ),
            BotTemplateSpec(
                id = "writer",
                title = "Writer",
                // The pink of the writer's blush, a smile, the pill of the quill.
                face = BotFace(
                    shape = FaceShape.PILL,
                    eyes = FaceEyes.HAPPY,
                    mouth = FaceMouth.SMILE,
                    mark = FaceMark.BLUSH,
                    colour = 2,
                    glance = 2,
                    tilt = 2,
                ),
                about = "Drafts, edits and tightens writing, in your voice.",
                soul = """# Writer

**Mission:** Help the person say what they mean, well.

You draft, edit and tighten writing: messages, posts, letters, notes, stories. You keep the person's own voice and say what they mean in fewer words. You give the new version first, then, only when it helps, a line on what you changed and why.

You write plainly. You cut what doesn't earn its place, prefer short words to long ones, and never pad. You ask who it is for when that would change how it reads.""",
            ),
            BotTemplateSpec(
                id = "coder",
                title = "Coder",
                // The hexagon of the chip, the frank eye, the closed mouth: it says what it did.
                face = BotFace(
                    shape = FaceShape.HEX,
                    eyes = FaceEyes.ROUND,
                    mouth = FaceMouth.NONE,
                    mark = FaceMark.NONE,
                    colour = 6,
                    glance = 0,
                    tilt = 0,
                ),
                about = "Reads and changes code, runs it, and says what it did.",
                soul = """# Coder

**Mission:** Make the change the person asked for, and show it works.

You read code before you change it, change only what the task needs, and run what you changed to see it work. You say what you did in a few lines: what changed, where, and how you checked it. When something fails, you show the error and what you tried.

You ask before anything that can't be undone: deleting files, rewriting history, touching anything outside the project.""",
            ),
            BotTemplateSpec(
                id = "home",
                title = "Home",
                // The warm capsule of the house, the round mouth of "it feels good in here" ("il fait bon"), the spots.
                face = BotFace(
                    shape = FaceShape.CAPSULE,
                    eyes = FaceEyes.HAPPY,
                    mouth = FaceMouth.OH,
                    mark = FaceMark.FRECKLES,
                    colour = 7,
                    glance = 3,
                    tilt = -2,
                ),
                about = "Looks after the house: lights, heating, sensors and routines.",
                soul = """# Home

**Mission:** Keep the house running the way the person likes it.

You look after the home: lights, heating, sensors, locks and the routines that tie them together. You check how things stand before you change them, and say what you changed in a line. You remember how the person likes things and when.

You ask before anything that affects safety or security, such as locks, alarms or the heating left on while nobody is home.""",
            ),
            BotTemplateSpec(
                id = "planner",
                title = "Planner",
                // The square of the checkbox, the indigo of the calendar, the sparkle of the deadline.
                face = BotFace(
                    shape = FaceShape.SQUARE,
                    eyes = FaceEyes.ROUND,
                    mouth = FaceMouth.SMILE,
                    mark = FaceMark.SPARK,
                    colour = 3,
                    glance = 0,
                    tilt = 3,
                ),
                about = "Keeps your plans, lists and reminders, and nudges you on time.",
                soul = """# Planner

**Mission:** Keep track of what the person means to do, and remind them in time.

You keep lists, plans and reminders. You turn "I should" into a task with a when, put repeating things on a schedule, and nudge the person before something is due, not after. When asked what is next, you give the next few things in order, nothing more.

You are brief and calm. You never add a task the person didn't ask for; you suggest it.""",
            ),
        )
    }
}


/**
 * The handle of a new bot, as its name gives it: "Scout Two" becomes `scout_two`, and
 * that is the name the profile takes on the server — the same one the editor announces under the
 * name field.
 */
fun botHandle(title: String): String = title.trim().lowercase().replace(' ', '_')

/**
 * A bot's editor — **its identity page**.
 *
 * Port of `BotEditor`: its face, its name and handle, its voice, what it does, its
 * personality, its model, its tools and skills, its visibility and its deletion —
 * same sections, same section footers, same texts.
 *
 * ## The page has been brought into the house language
 *
 * It had stayed in the old one: a screen margin that was not the margin, flat cards
 * without the family's shadow, rows with a height chosen case by case, and template tiles
 * with a radius that belonged to no family. It now takes [OnyxBase] and nothing
 * else:
 *
 * - **margin 16** ([OnyxBase.ScreenMargin]) on the left as on the right;
 * - **spacings 8 / 16** ([OnyxBase.Tight] within a group, [OnyxBase.Loose] between
 *   groups) — not a single intermediate value left;
 * - **radii 14 / 20 / 28 / capsule** ([OnyxBase.RadiusField] for a tile,
 *   `RadiusCard` for a card);
 * - **three chained shadow depths** ([Modifier.socleShadow] as `ShadowMid` on each
 *   card — one caller more, so the shadow stays in the binary);
 * - **stroke 1.55 dp** ([OnyxBase.Stroke]) on the outline of the cards;
 * - **six body sizes** 30 / 26 / 16 / 15 / 12 / 11 (`socleDisplayStyle`…`socleMonoStyle`);
 * - **targets ≥ 48 dp**: each row is at least [OnyxBase.RowHeight] (56), each control
 *   at least [OnyxBase.TouchTarget];
 * - **a single accent per screen**: the theme's accent carries the action and the chosen states, and
 *   there is no other;
 * - **monospace for identifiers and numbers** (`socleMonoStyle`): the bot's handle,
 *   a template's name, the tool and skill counters.
 *
 * ## Where the bot's colour is allowed to be
 *
 * A bot's colour is **its own**: it is its face, and it would have been a mistake to bring
 * it back to the theme's accent. The rule, kept here and everywhere:
 *
 * | The bot's colour **is** allowed | The bot's colour **is not** allowed |
 * |---|---|
 * | the bot's **face** — this page's preview, a template's tile, the face marker of the bottom bar | the **controls of the screen**: "Change Face" ("Changer de visage"), "Save" ("Enregistrer"), a checkmark, a switch, a link |
 * | the **ring** that lifts this face off the glass | the **section headings**, the rules, the shadows, the card backgrounds |
 *
 * In other words: the bot's colour **designates the bot**; it does not **accent** the screen. On this
 * page, only the face carries it — everything else takes the theme's accent, which is therefore the only
 * accent of the screen.
 */
@Composable
fun BotEditorScreen(
    bot: BotProfile?,
    faceCode: String?,
    mainName: String,
    onFaceChosen: (String?) -> Unit,
    /**
     * The name of the bot being created, so that the face screen has a true title.
     *
     * For an already-made bot, it is its profile name; for a bot being created, the one the
     * person types. The face screen opens on another screen (a route): it therefore needs
     * this name, and it cannot infer it — the bot does not exist on the server yet.
     */
    onFaceChosenNamed: (String?, String) -> Unit = { code, _ -> onFaceChosen(code) },
    /**
     * The face chosen in the dedicated face screen, for a bot **being created**.
     *
     * The face screen opens on another screen (a route): the choice therefore comes back through here.
     * Without this parameter, "Change Face" of "New Bot" could not complete — the bot does not exist
     * yet, and the editor knew nothing of the choice made elsewhere.
     */
    chosenFaceCode: String? = null,
    onOpenPrompt: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenVoice: () -> Unit,
    /**
     * The voice this bot reads with of its own (`BotVoicePicker.summary`): "Automatic", "Same as
     * the main agent", the name of its voice — and its speed when it has one.
     */
    voiceSummary: String = "Automatic",
    models: List<String> = emptyList(),
    onPickModel: (String?) -> Unit = {},
    /**
     * **The list of available models** — the thread's sheet (`ModelSheet`), with its search,
     * its profiles, its prices and its favourites.
     *
     * `null`: the screen keeps its local list unfolded (the identifiers the server returned for
     * this bot). Provided: a tap on "Model" **opens the sheet**, instead of unfolding two
     * lines that mean nothing to the person — "hermes-agent", "Same as Onyx" —
     * and a single identifier. Returning to "same as the main agent" remains possible: it has its
     * own row, under the model's.
     */
    onOpenModelSheet: (() -> Unit)? = null,
    onSave: (title: String, about: String, model: String?, shown: Boolean) -> Unit,
    /**
     * "Create" for a new bot: the name, what it does, the chosen template's personality, its
     * visibility and **its face** — the template's, or nothing for "Blank" (its name will give
     * it to it).
     */
    onCreate: (title: String, about: String, soul: String, shown: Boolean, faceCode: String?) -> Unit = { _, _, _, _, _ -> },
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    // The toolsets the server offers, those this bot has chosen (`null`: the main one's),
    // and the main one's — like `offered` / `toolsets` / `mainToolsets` in the reference.
    toolsets: List<GatewayToolset> = emptyList(),
    toolsSelection: Set<String>? = null,
    mainToolsets: Set<String> = emptySet(),
    onToolsSelectionChange: (Set<String>?) -> Unit = {},
    // The bot's skills, each on or off, and those it reads in full at the start of
    // every message — like `skills` / `alwaysLoaded` in the reference.
    skills: List<DashboardSkill> = emptyList(),
    alwaysLoadedSkills: Set<String> = emptySet(),
    onToggleSkill: (DashboardSkill, Boolean) -> Unit = { _, _ -> },
    onPinSkill: (DashboardSkill, Boolean) -> Unit = { _, _ -> },
    // **No "Ideas" row on this page.** The reconciliation arbitration refuses what the face
    // batch had added: deleting Ideas/Goals (the owner's request) removes
    // the screen, its route, its destination, its dictionary key **and** every fallback door. The
    // fourth marker therefore opens the bot's identity, no longer leading to a vanished local list.
    // See `DeletionIdeasGoalsTest` (a lock that bites) and `FaceGateIdentityTest` (which names
    // the arbitration).
    /** What failed (a deletion refused by the server, a write not kept). */
    problem: String? = null,
    // A dedicated screen for each list, if the parent has one; without it, the list unfolds here.
    onOpenTools: (() -> Unit)? = null,
    onOpenSkills: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val isNew = bot == null
    val isMain = bot?.isDefault == true

    // The field **starts from the name the page already displays**, then the title, then the `display_name`:
    // a bot without a title left the box **empty** (only the "Scout" placeholder showed) while
    // the screen was called "coder" — so you could not see what you were editing. The pre-filled name
    // is that of [BotProfile.label]; saving it without touching it writes nothing on the server,
    // since the label is identical (`saveEdit` only renames if the title differs).
    var title by remember(bot?.name) {
        mutableStateOf(bot?.label(mainName).orEmpty())
    }
    var about by remember(bot?.name) { mutableStateOf(bot?.description.orEmpty()) }
    var template by remember { mutableStateOf(BotTemplateSpec.blank.id) }
    // The chosen template's face: it shows in the top preview as on the tile, and
    // it is the one the created bot receives. Nothing for "Blank" (and for an already-made bot, whose
    // face is that of its profile).
    var pickedFace by remember { mutableStateOf<BotFace?>(null) }
    // The face chosen in the dedicated screen ("Change Face"), for a bot **being created**. It
    // arrives from another screen: it is therefore a state of the parent, and it is set here on its return.
    // Without this, the choice could not come back — and "Change Face" did nothing.
    val chosenFace = remember(chosenFaceCode) { BotFace.parse(chosenFaceCode) }
    LaunchedEffect(chosenFaceCode) {
        if (chosenFace != null) pickedFace = chosenFace
    }
    var shown by remember(bot?.name) { mutableStateOf(bot?.isHidden != true) }
    var confirmingDelete by remember { mutableStateOf(false) }
    // **The brief return of "Save" ("Enregistrer").** The action saves and **stays** on the page; a word
    // shows next to it, then fades. Without it, a silent save reads like a
    // dead button — and that is what had made people believe the action "changed page".
    var savedFlash by remember(bot?.name) { mutableStateOf(false) }
    LaunchedEffect(savedFlash) {
        if (savedFlash) {
            kotlinx.coroutines.delay(1_600)
            savedFlash = false
        }
    }
    var showModels by remember { mutableStateOf(false) }
    // The "Tools" and "Skills" lists unfolded under their row, when the parent has not provided
    // a dedicated screen.
    var showTools by remember { mutableStateOf(false) }
    var showSkills by remember { mutableStateOf(false) }

    val typed = title.trim()

    // The summary of its toolsets, like `BotEditor.toolsSummary`: "Same as {mainName}"
    // when it follows the main one, otherwise the names of the first two chosen, or "n of m".
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

    // The summary of its skills, like `BotEditor.skillsSummary`: the number on, then
    // those pinned ("always loaded").
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
            .onyxPage(theme),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            // The base's large title (30 sp, −0.032 em, ExtraBold) and the screen's single action,
            // on the right. The old header kept an intermediate body size (28): a screen's title
            // is **the** large body of the house, or there is none.
            SocleLargeHeader(
                title = if (isNew) "New Bot" else if (isMain) mainName else typed.ifEmpty { bot?.label(mainName) ?: "Bot" },
                onBack = onCancel,
                localizeTitle = isNew,
                actions = {
                    // The return word, before the action: it does not move the button (it occupies its
                    // place for an instant and fades).
                    if (savedFlash) {
                        BasicText(
                            text = "Saved",
                            style = socleBodyStyle(theme).copy(color = theme.secondaryText),
                            maxLines = 1,
                            modifier = Modifier.padding(end = OnyxBase.Tight),
                        )
                    }
                    BasicText(
                        text = if (isNew) "Create" else "Save",
                        style = socleBodyStyle(theme).copy(color = theme.accent),
                        maxLines = 1,
                        modifier = Modifier
                            .defaultMinSize(minHeight = OnyxBase.TouchTarget)
                            .onyxClickable {
                                if (isNew) {
                                    val spec = BotTemplateSpec.all.firstOrNull { it.id == template }
                                    onCreate(title, about, spec?.soul.orEmpty(), shown, pickedFace?.code())
                                } else {
                                    // **Save saves, and does not leave the page.** It was the
                                    // parent that wrote `navTo(Route.BOTS.name)`: the action
                                    // sent back to the bot list — read as "the creation
                                    // page" — whereas we had just asked to keep one's
                                    // settings, not to exit.
                                    onSave(title, about, bot?.model, shown)
                                    savedFlash = true
                                }
                            }
                            .padding(horizontal = OnyxBase.Tight, vertical = OnyxBase.Tight),
                    )
                },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = OnyxBase.Loose),
        ) {
            // The face, large, and "Change Face".
            //
            // **It is here, and only here, that the bot's colour is laid down**: the face carries it,
            // because it says *who* this bot is. The link that follows takes the theme's accent — the
            // only accent of the screen (see the rule in the screen's comment).
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = OnyxBase.Loose),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(OnyxBase.Tight),
            ) {
                BotLook(
                    name = bot?.name ?: typed.ifEmpty { "new-bot" },
                    size = 96.dp,
                    faceCode = pickedFace?.code() ?: faceCode,
                )
                BasicText(
                    text = "Change Face",
                    style = socleBodyStyle(theme).copy(color = theme.accent),
                    modifier = Modifier
                        .defaultMinSize(minHeight = OnyxBase.TouchTarget)
                        .onyxClickable {
                            onFaceChosenNamed(
                                faceCode,
                                bot?.name ?: title.ifEmpty { "new-bot" },
                            )
                        }
                        .padding(horizontal = OnyxBase.Loose, vertical = OnyxBase.Tight),
                )
            }

            if (isNew) {
                IdentitySection(
                    header = "Start from",
                    footer = "Its name, what it does, its personality, face and tools filled in. Change any of them before you make it.",
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = OnyxBase.Tight, vertical = OnyxBase.Tight),
                        horizontalArrangement = Arrangement.spacedBy(OnyxBase.Loose),
                    ) {
                        TemplateTile(
                            label = "Blank",
                            face = null,
                            selected = template == BotTemplateSpec.blank.id,
                        ) {
                            template = BotTemplateSpec.blank.id
                            pickedFace = null
                            title = ""
                            about = ""
                        }
                        BotTemplateSpec.all.forEach { spec ->
                            TemplateTile(
                                label = spec.title,
                                face = spec.face,
                                selected = template == spec.id,
                            ) {
                                template = spec.id
                                pickedFace = spec.face
                                title = spec.title
                                about = spec.about
                            }
                        }
                    }
                }
            }

            if (!isMain) {
                IdentitySection(
                    header = "Name",
                    footer = if (bot != null) {
                        OnyxLocalization.format("Others call it @%1\$s. Its handle stays @%1\$s.", bot.handle, bot.handle)
                    } else if (typed.isNotEmpty()) {
                        OnyxLocalization.format("Others call it @%1\$s.", botHandle(typed))
                    } else {
                        "Give it a name with a letter or number in it."
                    },
                ) {
                    Row(modifier = Modifier.padding(horizontal = OnyxBase.ScreenMargin, vertical = OnyxBase.Tight)) {
                        OnyxTextField(
                            value = title,
                            onValueChange = { title = it },
                            placeholder = "Scout",
                            style = socleBodyStyle(theme),
                        )
                    }
                    // **The handle, in monospace**: it is an identifier, not a sentence — the
                    // profile takes exactly this name on the server, and an identifier is not read
                    // like text. It is the only data typeface of the row.
                    if (bot != null || typed.isNotEmpty()) {
                        IdentityDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = OnyxBase.ScreenMargin, vertical = OnyxBase.Tight),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(OnyxBase.Tight),
                        ) {
                            BasicText(
                                text = "@" + (bot?.handle ?: botHandle(typed)),
                                localize = false,
                                style = socleMonoStyle(theme).copy(color = theme.secondaryText),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            if (bot != null && !isMain) {
                IdentitySection(footer = "What its replies are read in, in voice mode, group chats and the car.") {
                    IdentityRow(onClick = onOpenVoice) {
                        BasicText("Voice", style = socleTitleStyle(theme).copy(color = theme.textColor), modifier = Modifier.weight(1f))
                        BasicText(
                            text = voiceSummary,
                            style = socleMetaStyle(theme).copy(color = theme.secondaryText),
                        )
                        OnyxIcon(Symbols.ChevronRight, tint = theme.tertiaryText, size = 12.dp)
                    }
                }
            }

            IdentitySection(
                header = "What it does",
                footer = "Under its name in your chat list, and how the other bots know what to ask it.",
            ) {
                Row(modifier = Modifier.padding(horizontal = OnyxBase.ScreenMargin, vertical = OnyxBase.Tight)) {
                    OnyxTextField(
                        value = about,
                        onValueChange = { about = it },
                        placeholder = "What it's for",
                        singleLine = false,
                        maxLines = 4,
                        style = socleBodyStyle(theme),
                    )
                }
            }

            IdentitySection(
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
                IdentityRow(onClick = onOpenPrompt) {
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText("Personality", style = socleTitleStyle(theme).copy(color = theme.textColor))
                        BasicText(
                            text = "Written on the server, in its profile's SOUL.md",
                            style = socleMetaStyle(theme).copy(color = theme.secondaryText),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OnyxIcon(Symbols.ChevronRight, tint = theme.tertiaryText, size = 12.dp)
                }

                if (!isMain) {
                    IdentityDivider()
                    IdentityValueRow(
                        label = "Model",
                        value = bot?.model ?: if (isNew) "Same as $mainName" else "Not set",
                        localizeValue = bot?.model == null,
                        monoValue = bot?.model != null,
                        symbol = Symbols.Bolt,
                        // **A tap opens the model sheet**, when the parent provides it:
                        // it is the same list as in the thread — search, profiles, prices, favourites.
                        // Without it (previews, tests), the local list unfolds as before.
                        onClick = {
                            if (onOpenModelSheet != null) onOpenModelSheet() else showModels = !showModels
                        },
                    )
                    if (onOpenModelSheet == null && showModels) {
                        IdentityDivider()
                        IdentityRow(onClick = {
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
                                style = socleBodyStyle(theme).copy(color = theme.textColor),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        models.forEach { model ->
                            IdentityDivider()
                            IdentityRow(onClick = {
                                showModels = false
                                onPickModel(model)
                            }) {
                                OnyxIcon(
                                    symbol = if (bot?.model == model) Symbols.CheckmarkCircle else Symbols.Circle,
                                    tint = if (bot?.model == model) theme.accent else theme.tertiaryText,
                                    size = 18.dp,
                                )
                                IdentifierText(
                                    text = model,
                                    style = socleMonoStyle(theme).copy(color = theme.textColor),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    if (onOpenModelSheet != null) {
                        IdentityDivider()
                        // **The return to the main agent stays within reach.** The sheet picks a
                        // model; "same as X" is not one, it is the absence of a choice — it
                        // therefore keeps its row, with its checkmark, instead of disappearing with the local
                        // list.
                        IdentityRow(onClick = { onPickModel(null) }) {
                            OnyxIcon(
                                symbol = if (bot?.model == null) Symbols.CheckmarkCircle else Symbols.Circle,
                                tint = if (bot?.model == null) theme.accent else theme.tertiaryText,
                                size = 18.dp,
                            )
                            BasicText(
                                text = "Same as $mainName",
                                style = socleBodyStyle(theme).copy(color = theme.textColor),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    IdentityDivider()
                    // "Tools": opens the list of the bot's toolsets, each with its
                    // switch. The parent can provide its own screen (`onOpenTools`); by
                    // default, the list unfolds right below.
                    IdentityValueRow(
                        label = "Tools",
                        value = toolsSummary,
                        localizeValue = false,
                        monoValue = toolsCounted(toolsSelection, mainToolsets, toolsets),
                        symbol = Symbols.Bolt,
                        onClick = {
                            if (onOpenTools != null) onOpenTools() else showTools = !showTools
                        },
                    )
                    if (bot != null) {
                        IdentityDivider()
                        // "Skills": opens the list of the bot's skills, each with its
                        // switch and its pin ("always loaded").
                        IdentityValueRow(
                            label = "Skills",
                            value = skillsSummary,
                            localizeValue = false,
                            monoValue = skills.isNotEmpty(),
                            symbol = Symbols.Sparkles,
                            onClick = {
                                if (onOpenSkills != null) onOpenSkills() else showSkills = !showSkills
                            },
                        )
                    }
                }

                if (bot != null && !isMain) {
                    IdentityDivider()
                    IdentityValueRow(label = "Schedules", value = "", symbol = Symbols.Clock, onClick = onOpenSchedules)
                }
            }

            // The unfolded lists, when the parent has not provided a dedicated screen (`onOpenTools` /
            // `onOpenSkills` at `null`, the app-alone case).
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
                    // `BotEditor.hasSkillsTool`: without the "Skills" tool, none of them is read.
                    hasSkillsTool = isMain || toolsets.isEmpty() ||
                        (toolsSelection ?: mainToolsets).contains("skills"),
                )
            }

            if (bot != null && !isMain) {
                IdentitySection(footer = "A hidden bot stays on your server; find it again under Hidden Bots at the end of your bots.") {
                    IdentityToggleRow(
                        label = "Show in the chat list",
                        checked = shown,
                        onCheckedChange = { shown = it },
                    )
                }
                IdentitySection {
                    IdentityRow(onClick = { confirmingDelete = true }) {
                        BasicText("Delete Bot", style = socleBodyStyle(theme).copy(color = theme.error))
                    }
                }
            }

            if (confirmingDelete) {
                // **A confirmation does not unfold at the bottom of a scrolling page.**
                //
                // Defect found on the device: "when I click to delete the bot, it takes me
                // all the way back down automatically and I don't see the options, I have to
                // do it manually". The confirmation block was **added at the end** of the
                // scrolling `Column`: it fell below the fold, and the gesture of
                // opening it moved the view instead of showing the question. Here, the confirmation
                // is **the house's box** ([OnyxConfirm]): centred, above the screen,
                // entirely visible whatever the length of the page — and the page no longer moves
                // by a single pixel.
                OnyxConfirm(
                    title = "Delete ${bot?.label(mainName) ?: "this bot"}?",
                    destructiveLabel = OnyxLocalization["Delete"],
                    cancelLabel = OnyxLocalization["Cancel"],
                    onDestructive = {
                        confirmingDelete = false
                        onDelete()
                    },
                    onCancel = { confirmingDelete = false },
                )
            }

            // What failed, like the orange `Label(problem, …)` of `BotEditor`: a
            // deletion the server refused, a setting it did not keep. The screen
            // stays open, the bot stays there — nothing was done behind the scenes.
            problem?.let { message ->
                IdentitySection {
                    IdentityRow {
                        OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 16.dp)
                        BasicText(
                            text = message,
                            localize = false,
                            style = socleMetaStyle(theme).copy(color = theme.warning),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The pieces of the page, in the language of the base
// ---------------------------------------------------------------------------------------------

/** True if the tools summary is a **number** ("17 of 19") rather than a name or a reference. */
private fun toolsCounted(
    selection: Set<String>?,
    main: Set<String>,
    offered: List<GatewayToolset>,
): Boolean {
    if (selection == null || selection == main) return false
    return offered.count { selection.contains(it.name) } > 2
}

/**
 * A section: its heading in small caps ([socleSectionStyle], 11 sp, open), its
 * **card** ([OnyxBase.RadiusCard], the family's shadow, the 1.55 stroke), then its line of
 * context (12 sp). It is the shape of all the cards of the page — none escapes the
 * family, and both the outline and the shadow are written only here.
 */
@Composable
private fun IdentitySection(
    header: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = OnyxBase.Loose)) {
        header?.let {
            BasicText(
                // **The capital comes AFTER the translation.** Inverting it displayed the header in
                // English in a French interface: "WHAT IT DOES" instead of "SA FONCTION".
                // The catalogue contains the translation ("What it does" → "Sa fonction"); it is
                // the order of the two operations that ignored it.
                text = OnyxLocalization.translate(it).uppercase(),
                style = socleSectionStyle(theme).copy(color = theme.secondaryText),
                modifier = Modifier.padding(
                    start = OnyxBase.ScreenMargin,
                    end = OnyxBase.ScreenMargin,
                    bottom = OnyxBase.Tight,
                ),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OnyxBase.ScreenMargin)
                // **The shadow in three depths, placed for real**: [Modifier.socleShadow]
                // draws each layer separately (a `drop-shadow` with two shadows renders nothing).
                .socleShadow(OnyxBase.ShadowMid, OnyxBase.RadiusCard, theme.dark)
                .clip(RoundedCornerShape(OnyxBase.RadiusCard))
                .background(OnyxSurfaces.surface(theme))
                // **The house stroke: 1.55 dp.** A 1 dp hairline reads like a display
                // defect next to the board's surfaces.
                .border(
                    OnyxBase.Stroke,
                    OnyxSurfaces.hairline(theme),
                    RoundedCornerShape(OnyxBase.RadiusCard),
                ),
        ) {
            content()
        }
        footer?.let {
            BasicText(
                text = it,
                style = socleMetaStyle(theme).copy(color = theme.secondaryText),
                modifier = Modifier.padding(
                    start = OnyxBase.ScreenMargin,
                    end = OnyxBase.ScreenMargin,
                    top = OnyxBase.Tight,
                ),
            )
        }
    }
}

/** A row of the page: at least 56 dp high, 16 dp margin, 8 dp gap between its pieces. */
@Composable
private fun IdentityRow(
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.onyxTappable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = OnyxBase.RowHeight)
            .padding(horizontal = OnyxBase.ScreenMargin, vertical = OnyxBase.Tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxBase.IconLabelGap),
        content = content,
    )
}

/**
 * A "label → value" row: the house's grey tile, the label at card-title size
 * (16 sp), the value in meta (12 sp) — **in monospace when it is an identifier or a number**
 * ([monoValue]) —, then the chevron.
 */
@Composable
private fun IdentityValueRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    symbol: Int? = null,
    localizeValue: Boolean = true,
    monoValue: Boolean = false,
) {
    val theme = LocalOnyxTheme.current
    IdentityRow(onClick = onClick) {
        symbol?.let { SocleTile(symbol = it) }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BasicText(
                text = label,
                // A label **wraps** (the house policy): it never breaks onto
                // a single line, where it would lose its word.
                style = socleTitleStyle(theme).copy(color = theme.textColor),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (value.isNotBlank()) {
                if (monoValue) {
                    // An identifier or a number: the **start and the end** matter, so it
                    // is abbreviated in the middle ([com.onyx.app.ui.design.IdentifierFit]) instead of being
                    // cut — the same rule as a model's name and a server's address.
                    IdentifierText(
                        text = value,
                        style = socleMonoStyle(theme).copy(color = theme.secondaryText),
                    )
                } else {
                    BasicText(
                        text = value,
                        localize = localizeValue,
                        style = socleMetaStyle(theme).copy(color = theme.secondaryText),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        OnyxIcon(Symbols.ChevronRight, tint = theme.tertiaryText, size = 12.dp)
    }
}

/** A toggle row: the label, and the house's switch ([SocleSwitch]). */
@Composable
private fun IdentityToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = OnyxBase.RowHeight)
            .onyxTappable { onCheckedChange(!checked) }
            .padding(horizontal = OnyxBase.ScreenMargin, vertical = OnyxBase.Tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxBase.IconLabelGap),
    ) {
        BasicText(
            text = label,
            style = socleBodyStyle(theme).copy(color = theme.textColor),
            modifier = Modifier.weight(1f),
        )
        SocleSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** The rule **inset from both edges** — from the margin to the margin, never edge to edge. */
@Composable
private fun IdentityDivider() {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxBase.ScreenMargin)
            .height(1.dp)
            .background(theme.separator),
    )
}

/**
 * A template tile: the **drawn face** of its template (or "+" for Blank), then its name.
 *
 * The face goes through the same engine as the preview at the top of the screen (`BotAvatar`) and through
 * **the animation that already exists** for these faces — `voicePose`, the one of the voice scene: it
 * **breathes** at rest and turns towards you as long as the finger stays pressed (the attentive pose of
 * listening), instead of a second animation written here. Its **mouth** stays its own (`mouth =
 * null`: the voice scene, on the other hand, holds it closed to drive it by voice). A template therefore never
 * has the generic icon of an orb: it is its creature, its shape, its colour and its expression.
 * Under "Reduce motion" ("Réduire les animations"), [rememberBotSeconds] returns nothing and the face is **frozen**.
 *
 * Its shape is the family's: **a field's radius** ([OnyxBase.RadiusField]), the
 * house's gap (8), and the background of the selected tile at the accent. It is the face that carries the template's
 * colour — the tile itself only frames it.
 */
@Composable
private fun TemplateTile(
    label: String,
    face: BotFace?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    // The tile's press: the face subscribes to it to turn towards you during the long press.
    val press = remember { MutableInteractionSource() }
    val held by press.collectIsPressedAsState()
    Column(
        modifier = Modifier
            .defaultMinSize(minWidth = OnyxBase.TouchTarget)
            .background(
                color = if (selected) theme.accent.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(OnyxBase.RadiusField),
            )
            .onyxTappable(onClick = onClick, interaction = press)
            .padding(OnyxBase.Tight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OnyxBase.Tight),
    ) {
        if (face != null) {
            val seconds = rememberBotSeconds()
            val pose = seconds?.let { at ->
                val attentive = voicePose(
                    seconds = at,
                    phase = if (held) VoicePhase.LISTENING else VoicePhase.IDLE,
                    level = 0f,
                    tilt = face.tilt.toFloat(),
                )
                // At tile size, the listening eyebrows (0.35) cannot be read:
                // the press raises them to the top of the `BotPose.brow` scale, and the face lifts
                // its eyes towards you. Its mouth, on the other hand, stays its own.
                if (held) attentive.copy(mouth = null, brow = 0.95f) else attentive.copy(mouth = null)
            } ?: BotPose()
            BotAvatar(
                name = label,
                faceCode = face.code(),
                size = 52.dp,
                showsInitial = false,
                blinks = false,
                pose = pose,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(theme.fill, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                OnyxIcon(symbol = Symbols.Plus, tint = theme.secondaryText, size = 22.dp)
            }
        }
        BasicText(text = label, style = socleMetaStyle(theme).copy(color = theme.textColor, textAlign = TextAlign.Center))
    }
}

/**
 * A bot's toolsets (`BotToolsList`).
 *
 * A first section offers "Same as {mainName}" — the bot then follows the main one's toolsets.
 * The second lists each toolset the server offers, with its switch: the shorter the
 * tools, the faster each of the bot's turns starts on a model running on the machine.
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
        // "Same as {mainName}": nothing specific to the bot, it inherits the main one's toolsets.
        IdentitySection(footer = "The tools $mainName has.") {
            IdentityRow(onClick = { onSelectionChange(null) }) {
                OnyxIcon(
                    symbol = if (selection == null) Symbols.CheckmarkCircle else Symbols.Circle,
                    tint = if (selection == null) theme.accent else theme.tertiaryText,
                    size = 18.dp,
                )
                BasicText(
                    text = "Same as $mainName",
                    style = socleBodyStyle(theme).copy(color = theme.textColor),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // The offered toolsets, each on "on" or "off" for this bot.
        IdentitySection(
            footer = OnyxLocalization.format(
                "Fewer tools make a bot's every turn quicker to start on a model running on your own machine. Hermes 0.21.5 and later keep a bot to these; earlier ones give every bot all of %1\$s's.",
                mainName,
            ),
        ) {
            offered.forEachIndexed { index, toolset ->
                if (index > 0) IdentityDivider()
                val on = (selection ?: main).contains(toolset.name)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = OnyxBase.RowHeight)
                        .padding(horizontal = OnyxBase.ScreenMargin, vertical = OnyxBase.Tight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OnyxBase.IconLabelGap),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(toolset.title, localize = true, style = socleTitleStyle(theme).copy(color = theme.textColor))
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
                    SocleSwitch(
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
 * A bot's skills (`BotSkillsList`).
 *
 * Each is on or off for it alone; the pinned ones are read in full at the start of
 * every message. Without the bot's "Skills" tool, none is read — the screen says so.
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
        IdentitySection(
            footer = OnyxLocalization.format(
                "On, %1\$s sees a skill listed and reads it only when it needs it. Off, it doesn't see it at all. Pinned, it reads the skill in full at the start of every message: keep that to the few it needs every time, as each makes every message slower to start. A pin takes effect in new chats, and in %1\$s's ongoing chat once that starts fresh or is condensed.",
                name,
            ),
        ) {
            when {
                !hasSkillsTool -> IdentityRow {
                    OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 16.dp)
                    BasicText(
                        text = "$name's Skills tool is off (Edit Bot → Tools), so it reads none of these.",
                        style = socleMetaStyle(theme).copy(color = theme.secondaryText),
                        modifier = Modifier.weight(1f),
                    )
                }

                skills.isEmpty() -> IdentityRow {
                    BasicText("No skills yet", style = socleBodyStyle(theme).copy(color = theme.secondaryText))
                }

                else -> {
                    // The skills grouped by category, in alphabetical order — like the
                    // "Category · n" sections of the reference.
                    val groups = skills
                        .groupBy { it.category?.takeIf { category -> category.isNotBlank() } ?: "Other" }
                        .toSortedMap()
                    var first = true
                    groups.forEach { (category, list) ->
                        if (!first) IdentityDivider()
                        first = false
                        IdentityRow {
                            // A category's count is a **number**: monospace, as the
                            // house scale's footer reserves it for numeric data.
                            BasicText(
                                text = "${category.replaceFirstChar { it.uppercase() }} · ${list.size}",
                                localize = false,
                                style = socleMonoStyle(theme).copy(color = theme.secondaryText),
                            )
                        }
                        list.sortedBy { it.name }.forEach { skill ->
                            IdentityDivider()
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

/** A skill: its name, its pin, its sentence, its switch. */
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
            .defaultMinSize(minHeight = OnyxBase.RowHeight)
            .padding(horizontal = OnyxBase.ScreenMargin, vertical = OnyxBase.Tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxBase.IconLabelGap),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(skill.name, localize = false, style = socleTitleStyle(theme).copy(color = theme.textColor))
            if (pinned) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OnyxIcon(Symbols.Pin, tint = theme.accent, size = 10.dp)
                    BasicText(
                        text = "Always loaded",
                        style = socleMetaStyle(theme).copy(color = theme.accent, fontWeight = FontWeight.SemiBold),
                    )
                }
            }
            if (skill.description.isNotBlank()) {
                BasicText(
                    text = skill.description,
                    localize = false,
                    style = socleMetaStyle(theme).copy(color = theme.secondaryText),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // The pin exists only for a skill that is on: off, it is in any case
        // not read.
        if (skill.enabled) {
            OnyxIcon(
                symbol = Symbols.Pin,
                tint = if (pinned) theme.accent else theme.tertiaryText,
                size = 16.dp,
                modifier = Modifier.onyxTappable { onPin(skill, !pinned) },
            )
        }
        SocleSwitch(checked = skill.enabled, onCheckedChange = { onToggle(skill, it) })
    }
}
