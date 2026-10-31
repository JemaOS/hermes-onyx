package com.onyx.app.ui.bots

import com.onyx.app.ui.design.OnyxSocle
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
 * Un modèle de bot, comme `BotTemplate` de la référence : nom, ce qu'il fait, son prompt, et son
 * visage.
 *
 * Le visage fait partie du modèle : « Start from » promet « son nom, ce qu'il fait, sa
 * personnalité, **son visage** et ses outils remplis » — chaque vignette dessine donc la créature
 * de son modèle (forme, yeux, bouche, marque, couleur), et le bot créé la reçoit. Le modèle
 * « Blank » n'en a pas : c'est celui qui part de rien.
 */
data class BotTemplateSpec(
    val id: String,
    val title: String,
    val about: String,
    val soul: String,
    /** Le visage que ce modèle donne : ce qui se voit sur sa vignette et sur le bot créé. */
    val face: BotFace? = null,
) {
    companion object {
        val blank = BotTemplateSpec("blank", "", "", "")

        /** Les cinq modèles de la référence, mot pour mot, chacun avec sa propre créature. */
        val all = listOf(
            BotTemplateSpec(
                id = "researcher",
                title = "Researcher",
                // Curieuse : l'œil étincelant, l'antenne qui capte, le ciel.
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
                // Le rose du fard de la personne qui écrit, un sourire, la pilule de la plume.
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
                // L'hexagone de la puce, l'œil franc, la bouche fermée : il dit ce qu'il a fait.
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
                // La capsule chaude de la maison, la bouche ronde du « il fait bon », les taches.
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
                // Le carré de la case à cocher, l'indigo du calendrier, l'étincelle de l'échéance.
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
 * La poignée d'un bot neuf, telle que son nom la donne : « Scout Two » devient `scout_two`, et
 * c'est ce nom que le profil prend sur le serveur — le même que l'éditeur annonce sous le champ
 * du nom.
 */
fun botHandle(title: String): String = title.trim().lowercase().replace(' ', '_')

/**
 * L'éditeur d'un bot — **sa page d'identité**.
 *
 * Port de `BotEditor` : son visage, son nom et sa poignée, sa voix, ce qu'il fait, sa
 * personnalité, son modèle, ses outils et ses compétences, sa visibilité et sa suppression —
 * mêmes sections, mêmes pieds de section, mêmes textes.
 *
 * ## La page est passée au langage de la maison
 *
 * Elle était restée dans l'ancien : une marge d'écran qui n'était pas la marge, des cartes plates
 * sans l'ombre de la famille, des rangées à une hauteur choisie au cas par cas, et des tuiles de
 * modèle à un rayon qui n'appartenait à aucune famille. Elle prend maintenant [OnyxSocle] et rien
 * d'autre :
 *
 * - **marge 16** ([OnyxSocle.ScreenMargin]) à gauche comme à droite ;
 * - **espacements 8 / 16** ([OnyxSocle.Tight] dans un groupe, [OnyxSocle.Loose] entre les
 *   groupes) — plus une seule valeur intermédiaire ;
 * - **rayons 14 / 20 / 28 / capsule** ([OnyxSocle.RadiusField] pour une vignette,
 *   `RadiusCard` pour une carte) ;
 * - **trois profondeurs d'ombre enchaînées** ([Modifier.socleShadow] en `ShadowMid` sur chaque
 *   carte — un appelant de plus, donc l'ombre reste dans le binaire) ;
 * - **trait 1,55 dp** ([OnyxSocle.Stroke]) sur le contour des cartes ;
 * - **six corps** 30 / 26 / 16 / 15 / 12 / 11 (`socleDisplayStyle`…`socleMonoStyle`) ;
 * - **cibles ≥ 48 dp** : chaque rangée fait au moins [OnyxSocle.RowHeight] (56), chaque contrôle
 *   au moins [OnyxSocle.TouchTarget] ;
 * - **un seul accent par écran** : l'accent du thème porte l'action et les états choisis, et il
 *   n'y en a pas d'autre ;
 * - **monospace pour les identifiants et les nombres** (`socleMonoStyle`) : la poignée du bot,
 *   le nom d'un modèle, les compteurs d'outils et de compétences.
 *
 * ## Où la couleur du bot a le droit d'être
 *
 * La couleur d'un bot est **la sienne** : c'est son visage, et ç'aurait été une faute que de la
 * ramener à l'accent du thème. La règle, tenue ici et partout :
 *
 * | La couleur du bot **a** le droit | La couleur du bot **n'a pas** le droit |
 * |---|---|
 * | le **visage** du bot — l'aperçu de cette page, la vignette d'un modèle, le repère-visage de la barre du bas | les **contrôles de l'écran** : « Changer de visage », « Enregistrer », une coche, un interrupteur, un lien |
 * | l'**anneau** qui détache ce visage de la vitre | les **intitulés de section**, les filets, les ombres, les fonds de carte |
 *
 * Autrement dit : la couleur du bot **désigne le bot** ; elle n'**accentue pas** l'écran. Sur cette
 * page, seul le visage la porte — tout le reste prend l'accent du thème, qui est donc le seul
 * accent de l'écran.
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
     * La voix que ce bot lit de son cru (`BotVoicePicker.summary`) : « Automatique », « Identique à
     * l'agent principal », le nom de sa voix — et sa vitesse quand il en a une.
     */
    voiceSummary: String = "Automatic",
    models: List<String> = emptyList(),
    onPickModel: (String?) -> Unit = {},
    onSave: (title: String, about: String, model: String?, shown: Boolean) -> Unit,
    /**
     * « Create » d'un bot neuf : le nom, ce qu'il fait, la personnalité du modèle choisi, sa
     * visibilité et **son visage** — celui du modèle, ou rien pour « Blank » (son nom le lui
     * donnera).
     */
    onCreate: (title: String, about: String, soul: String, shown: Boolean, faceCode: String?) -> Unit = { _, _, _, _, _ -> },
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    // Les jeux d'outils que le serveur offre, ceux que ce bot a choisis (`null` : ceux du principal),
    // et ceux du principal — comme `offered` / `toolsets` / `mainToolsets` de la référence.
    toolsets: List<GatewayToolset> = emptyList(),
    toolsSelection: Set<String>? = null,
    mainToolsets: Set<String> = emptySet(),
    onToolsSelectionChange: (Set<String>?) -> Unit = {},
    // Les compétences du bot, chacune allumée ou non, et celles qu'il lit en entier au début de
    // chaque message — comme `skills` / `alwaysLoaded` de la référence.
    skills: List<DashboardSkill> = emptyList(),
    alwaysLoadedSkills: Set<String> = emptySet(),
    onToggleSkill: (DashboardSkill, Boolean) -> Unit = { _, _ -> },
    onPinSkill: (DashboardSkill, Boolean) -> Unit = { _, _ -> },
    // **Pas de rangée « Idées » sur cette page.** L'arbitrage de la réconciliation refuse ce que le
    // lot du visage avait ajouté : la suppression d'Idées/Objectifs (demande du propriétaire) retire
    // l'écran, sa route, sa destination, sa clé de dictionnaire **et** toute porte de secours. Le
    // quatrième repère ouvre donc l'identité du bot, sans plus mener à une liste locale disparue.
    // Voir `SuppressionIdeesObjectifsTest` (verrou qui mord) et `VisagePorteIdentiteTest` (qui nomme
    // l'arbitrage).
    /** Ce qui a échoué (une suppression refusée par le serveur, une écriture non gardée). */
    problem: String? = null,
    // Un écran dédié pour chaque liste, si le parent en a un ; sans lui, la liste se déplie ici.
    onOpenTools: (() -> Unit)? = null,
    onOpenSkills: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val isNew = bot == null
    val isMain = bot?.isDefault == true

    // Comme `BotEditor` de la référence (`Original(title: editing.title ?? editing.displayName ?? "")`) : le champ
    // part du titre du bot, sinon de son `display_name` ; vide seulement si le profil n'a ni l'un
    // ni l'autre.
    var title by remember(bot?.name) {
        mutableStateOf(bot?.title?.takeIf { it.isNotBlank() } ?: bot?.displayName.orEmpty())
    }
    var about by remember(bot?.name) { mutableStateOf(bot?.description.orEmpty()) }
    var template by remember { mutableStateOf(BotTemplateSpec.blank.id) }
    // Le visage du modèle choisi : il se voit dans l'aperçu du haut comme sur la vignette, et
    // c'est lui que le bot créé reçoit. Rien pour « Blank » (et pour un bot déjà fait, dont le
    // visage est celui de son profil).
    var pickedFace by remember { mutableStateOf<BotFace?>(null) }
    var shown by remember(bot?.name) { mutableStateOf(bot?.isHidden != true) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    // Les listes « Tools » et « Skills » dépliées sous leur rangée, quand le parent n'a pas fourni
    // d'écran dédié.
    var showTools by remember { mutableStateOf(false) }
    var showSkills by remember { mutableStateOf(false) }

    val typed = title.trim()

    // Le résumé de ses jeux d'outils, comme `BotEditor.toolsSummary` : « Same as {mainName} »
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

    // Le résumé de ses compétences, comme `BotEditor.skillsSummary` : le nombre allumées, puis
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
            .onyxPage(theme),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            // Le grand titre du socle (30 sp, −0,032 em, ExtraBold) et l'action unique de
            // l'écran, à droite. L'ancien en-tête gardait un corps intermédiaire (28) : le titre
            // d'un écran est **le** grand corps de la maison, ou il n'y en a pas.
            SocleLargeHeader(
                title = if (isNew) "New Bot" else if (isMain) mainName else typed.ifEmpty { bot?.label(mainName) ?: "Bot" },
                onBack = onCancel,
                localizeTitle = isNew,
                actions = {
                    BasicText(
                        text = if (isNew) "Create" else "Save",
                        style = socleBodyStyle(theme).copy(color = theme.accent),
                        maxLines = 1,
                        modifier = Modifier
                            .defaultMinSize(minHeight = OnyxSocle.TouchTarget)
                            .onyxClickable {
                                if (isNew) {
                                    val spec = BotTemplateSpec.all.firstOrNull { it.id == template }
                                    onCreate(title, about, spec?.soul.orEmpty(), shown, pickedFace?.code())
                                } else {
                                    onSave(title, about, bot?.model, shown)
                                }
                            }
                            .padding(horizontal = OnyxSocle.Tight, vertical = OnyxSocle.Tight),
                    )
                },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = OnyxSocle.Loose),
        ) {
            // Le visage, en grand, et « Change Face ».
            //
            // **C'est ici, et seulement ici, que la couleur du bot se pose** : le visage la porte,
            // parce qu'elle dit *qui* est ce bot. Le lien qui suit prend l'accent du thème — le
            // seul accent de l'écran (voir la règle dans le commentaire de l'écran).
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = OnyxSocle.Loose),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
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
                        .defaultMinSize(minHeight = OnyxSocle.TouchTarget)
                        .onyxClickable { onFaceChosen(faceCode) }
                        .padding(horizontal = OnyxSocle.Loose, vertical = OnyxSocle.Tight),
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
                            .padding(horizontal = OnyxSocle.Tight, vertical = OnyxSocle.Tight),
                        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Loose),
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
                    Row(modifier = Modifier.padding(horizontal = OnyxSocle.ScreenMargin, vertical = OnyxSocle.Tight)) {
                        OnyxTextField(
                            value = title,
                            onValueChange = { title = it },
                            placeholder = "Scout",
                            style = socleBodyStyle(theme),
                        )
                    }
                    // **La poignée, en monospace** : c'est un identifiant, pas une phrase — le
                    // profil prend exactement ce nom sur le serveur, et un identifiant ne se lit
                    // pas comme du texte. C'est la seule écriture de donnée de la rangée.
                    if (bot != null || typed.isNotEmpty()) {
                        IdentityDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = OnyxSocle.ScreenMargin, vertical = OnyxSocle.Tight),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
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
                Row(modifier = Modifier.padding(horizontal = OnyxSocle.ScreenMargin, vertical = OnyxSocle.Tight)) {
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
                        onClick = { showModels = !showModels },
                    )
                    if (showModels) {
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
                    IdentityDivider()
                    // « Tools » : ouvre la liste des jeux d'outils du bot, chacun avec son
                    // interrupteur. Le parent peut fournir son propre écran (`onOpenTools`) ; à
                    // défaut, la liste se déplie juste en dessous.
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
                        // « Skills » : ouvre la liste des compétences du bot, chacune avec son
                        // interrupteur et son épingle (« always loaded »).
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
                    // `BotEditor.hasSkillsTool` : sans l'outil « Skills », aucune n'est lue.
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
                IdentitySection {
                    IdentityRow(onClick = {
                        confirmingDelete = false
                        onDelete()
                    }) {
                        BasicText(
                            text = "Delete ${bot?.label(mainName) ?: "this bot"}?",
                            style = socleBodyStyle(theme).copy(color = theme.error, fontWeight = FontWeight.SemiBold),
                        )
                    }
                    IdentityDivider()
                    IdentityRow(onClick = { confirmingDelete = false }) {
                        BasicText("Cancel", style = socleBodyStyle(theme).copy(color = theme.secondaryText))
                    }
                }
            }

            // Ce qui a échoué, comme le `Label(problem, …)` orange de `BotEditor` : une
            // suppression que le serveur a refusée, un réglage qu'il n'a pas gardé. L'écran
            // reste ouvert, le bot reste là — rien n'a été fait en douce.
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
// Les pièces de la page, dans le langage du socle
// ---------------------------------------------------------------------------------------------

/** Vrai si le résumé d'outils est un **nombre** (« 17 sur 19 ») plutôt qu'un nom ou un renvoi. */
private fun toolsCounted(
    selection: Set<String>?,
    main: Set<String>,
    offered: List<GatewayToolset>,
): Boolean {
    if (selection == null || selection == main) return false
    return offered.count { selection.contains(it.name) } > 2
}

/**
 * Une section : son intitulé en petites capitales ([socleSectionStyle], 11 sp, ouvertes), sa
 * **carte** ([OnyxSocle.RadiusCard], l'ombre de la famille, le trait de 1,55), puis sa ligne de
 * contexte (12 sp). C'est la forme de toutes les cartes de la page — aucune n'échappe à la
 * famille, et le contour comme l'ombre ne sont écrits qu'ici.
 */
@Composable
private fun IdentitySection(
    header: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = OnyxSocle.Loose)) {
        header?.let {
            BasicText(
                text = it.uppercase(),
                style = socleSectionStyle(theme).copy(color = theme.secondaryText),
                localize = false,
                modifier = Modifier.padding(
                    start = OnyxSocle.ScreenMargin,
                    end = OnyxSocle.ScreenMargin,
                    bottom = OnyxSocle.Tight,
                ),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OnyxSocle.ScreenMargin)
                // **L'ombre en trois profondeurs, posée pour de vrai** : [Modifier.socleShadow]
                // dessine chaque couche séparément (un `drop-shadow` à deux ombres ne rend rien).
                .socleShadow(OnyxSocle.ShadowMid, OnyxSocle.RadiusCard, theme.dark)
                .clip(RoundedCornerShape(OnyxSocle.RadiusCard))
                .background(OnyxSurfaces.surface(theme))
                // **Le trait de la maison : 1,55 dp.** Un filet de 1 dp se lit comme un défaut
                // d'affichage à côté des surfaces de la planche.
                .border(
                    OnyxSocle.Stroke,
                    OnyxSurfaces.hairline(theme),
                    RoundedCornerShape(OnyxSocle.RadiusCard),
                ),
        ) {
            content()
        }
        footer?.let {
            BasicText(
                text = it,
                style = socleMetaStyle(theme).copy(color = theme.secondaryText),
                modifier = Modifier.padding(
                    start = OnyxSocle.ScreenMargin,
                    end = OnyxSocle.ScreenMargin,
                    top = OnyxSocle.Tight,
                ),
            )
        }
    }
}

/** Une rangée de la page : au moins 56 dp de haut, 16 dp de marge, 8 dp d'écart entre ses pièces. */
@Composable
private fun IdentityRow(
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.onyxTappable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = OnyxSocle.RowHeight)
            .padding(horizontal = OnyxSocle.ScreenMargin, vertical = OnyxSocle.Tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.IconLabelGap),
        content = content,
    )
}

/**
 * Une rangée « libellé → valeur » : la tuile grise de la maison, le libellé au titre de carte
 * (16 sp), la valeur en méta (12 sp) — **en monospace quand c'est un identifiant ou un nombre**
 * ([monoValue]) —, puis le chevron.
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
                // Un libellé **se replie** (la politique de la maison) : il ne se coupe jamais sur
                // une seule ligne, où il perdrait son mot.
                style = socleTitleStyle(theme).copy(color = theme.textColor),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (value.isNotBlank()) {
                if (monoValue) {
                    // Un identifiant ou un nombre : le **début et la fin** comptent, donc il
                    // s'abrège au milieu ([com.onyx.app.ui.design.IdentifierFit]) au lieu de se
                    // couper — la même règle que le nom d'un modèle et l'adresse d'un serveur.
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

/** Une rangée à bascule : le libellé, et l'interrupteur de la maison ([SocleSwitch]). */
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
            .defaultMinSize(minHeight = OnyxSocle.RowHeight)
            .onyxTappable { onCheckedChange(!checked) }
            .padding(horizontal = OnyxSocle.ScreenMargin, vertical = OnyxSocle.Tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.IconLabelGap),
    ) {
        BasicText(
            text = label,
            style = socleBodyStyle(theme).copy(color = theme.textColor),
            modifier = Modifier.weight(1f),
        )
        SocleSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Le filet **en retrait des deux bords** — de la marge à la marge, jamais bord à bord. */
@Composable
private fun IdentityDivider() {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSocle.ScreenMargin)
            .height(1.dp)
            .background(theme.separator),
    )
}

/**
 * Une tuile de modèle : le **visage dessiné** de son modèle (ou « + » pour Blank), puis son nom.
 *
 * Le visage passe par le même moteur que l'aperçu en haut de l'écran (`BotAvatar`) et par
 * **l'animation qui existe déjà** pour ces visages — `voicePose`, celle de la scène vocale : il
 * **respire** au repos et se tourne vers vous tant que le doigt reste appuyé (la pose attentive de
 * l'écoute), au lieu d'une seconde animation écrite ici. Sa **bouche** reste la sienne (`mouth =
 * null` : la scène vocale, elle, la tient fermée pour la piloter par la voix). Un modèle n'a donc
 * jamais l'icône générique d'un orbe : c'est sa créature, sa forme, sa couleur et son expression.
 * Sous « Réduire les animations », [rememberBotSeconds] ne rend rien et le visage est **figé**.
 *
 * Sa forme est celle de la famille : **le rayon d'un champ** ([OnyxSocle.RadiusField]), l'écart de
 * la maison (8), et le fond de la tuile retenue à l'accent. C'est le visage qui porte la couleur
 * du modèle — la tuile, elle, ne fait que l'encadrer.
 */
@Composable
private fun TemplateTile(
    label: String,
    face: BotFace?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    // L'appui de la tuile : le visage s'y abonne pour se tourner vers vous pendant l'appui long.
    val press = remember { MutableInteractionSource() }
    val held by press.collectIsPressedAsState()
    Column(
        modifier = Modifier
            .defaultMinSize(minWidth = OnyxSocle.TouchTarget)
            .background(
                color = if (selected) theme.accent.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(OnyxSocle.RadiusField),
            )
            .onyxTappable(onClick = onClick, interaction = press)
            .padding(OnyxSocle.Tight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
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
                // À la taille d'une vignette, les sourcils de l'écoute (0,35) ne se lisent pas :
                // l'appui les relève au haut de l'échelle de `BotPose.brow`, et le visage lève
                // les yeux vers vous. Sa bouche, elle, reste la sienne.
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
 * Les jeux d'outils d'un bot (`BotToolsList`).
 *
 * Une première section offre « Same as {mainName} » — le bot suit alors les jeux du principal.
 * La seconde liste chaque jeu que le serveur propose, avec son interrupteur : plus courts sont les
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
        // « Same as {mainName} » : rien de propre au bot, il hérite des jeux du principal.
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
        // Les jeux offerts, chacun sur « on » ou « off » pour ce bot.
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
                        .defaultMinSize(minHeight = OnyxSocle.RowHeight)
                        .padding(horizontal = OnyxSocle.ScreenMargin, vertical = OnyxSocle.Tight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OnyxSocle.IconLabelGap),
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
 * Les compétences d'un bot (`BotSkillsList`).
 *
 * Chacune est allumée ou éteinte pour lui seul ; les épinglées sont lues en entier au début de
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
                    // Les compétences groupées par catégorie, dans l'ordre alphabétique — comme les
                    // sections « Catégorie · n » de la référence.
                    val groups = skills
                        .groupBy { it.category?.takeIf { category -> category.isNotBlank() } ?: "Other" }
                        .toSortedMap()
                    var first = true
                    groups.forEach { (category, list) ->
                        if (!first) IdentityDivider()
                        first = false
                        IdentityRow {
                            // Le compte d'une catégorie est un **nombre** : monospace, comme le
                            // pied de l'échelle de la maison le réserve aux données chiffrées.
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

/** Une compétence : son nom, son épingle, sa phrase, son interrupteur. */
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
            .defaultMinSize(minHeight = OnyxSocle.RowHeight)
            .padding(horizontal = OnyxSocle.ScreenMargin, vertical = OnyxSocle.Tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.IconLabelGap),
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
        // L'épingle n'existe que pour une compétence allumée : éteinte, elle n'est de toute façon
        // pas lue.
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
