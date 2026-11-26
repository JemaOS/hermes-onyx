package com.onyx.app.ui.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.DialogWindowBlur
import com.onyx.app.ui.design.FrostSurface
import com.onyx.app.ui.design.GlassPanelCorner
import com.onyx.app.ui.design.HouseSheetScrim
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.design.ProvideFieldTextColours
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.composerFieldSurface
import com.onyx.app.ui.design.housePanelSurface
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * **The room creation dialog** — the port of the reference dialog, in the house template.
 *
 * The reference draws it in `shared/ui/.../rooms/CreateRoomDialog.kt:66` : a panel, a title,
 * a name field with its placeholder, the paragraph that says what the room becomes, the section
 * heading that carries the constraint (« BOTS · EN CHOISIR DE 2 À 6 » — "pick 2 to 6"), the list of
 * bots with their face and a selection circle, then the confirm action as a pill. Onyx did not have
 * this dialog : its bots started from a default choice (`GroupChatViewModel.init`), with no name and
 * no shown constraint.
 *
 * **The paragraph only promises what is kept.** Three hostings exist, and each has its
 * sentence ([roomExplanation]) :
 *
 * - the **gateway** holds the room (`groups.create` with `driver: true`) : it runs with no app
 *   open, and everything is re-read on reconnection (`groups.log`) — this is the promise of the
 *   reference, and it is true here ;
 * - without Dashboard credentials, the room is **kept on this phone** (`GroupRoomStore`) : it
 *   survives a restart, but its bots only answer while the app is open — say
 *   otherwise would be a promise that the code does not keep ;
 * - with a Dashboard whose gateway does not hold rooms (`driver: false`, old
 *   gateway), the opening is refused and nothing is kept : the sentence says so before the call.
 *
 * Each bot row carries **its face** ([BotLook]) and **its circle** : filled with the accent and its
 * check when picked, empty ring otherwise — the `PickMark` of the reference.
 *
 * The confirm action is **really** constrained : it does nothing while the name is empty or
 * the count falls outside 2 to 6, and the reason is shown next to it ([roomBlockReason]) — never a greyed
 * button that says nothing.
 */
@Composable
fun RoomCreationDialog(
    bots: List<BotProfile>,
    /** The paragraph follows **the same** source as the opening : [roomHosting]. */
    hasDashboard: Boolean,
    gatewayRunsRooms: Boolean,
    /** What is running right now (« Ouverture du salon… » — "Opening the room…"), or `null`. */
    busy: String? = null,
    /** The server's refusal, shown as is below the list. */
    error: String? = null,
    onDismiss: () -> Unit,
    onCreate: (name: String, members: Set<String>) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    var name by remember { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<String>() }
    // Pressing the action while it cannot go through : the reason is then **restated**.
    var blocked by remember { mutableStateOf<String?>(null) }
    val hosting = roomHosting(hasDashboard, gatewayRunsRooms)
    val reason = roomBlockReason(name, chosen.size, busy != null)
    val fieldFocus = remember { FocusRequester() }
    // The field is **focused** on open, as in the reference : its edge takes the accent.
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    Dialog(
        onDismissRequest = { if (busy == null) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = busy == null),
    ) {
        DialogWindowBlur(FrostSurface.SHEET)
        // **The app theme, restated at the window entry.** A `Dialog` opens a
        // sub-composition : the explicit restatement guarantees that the panel, its ink and its actions
        // follow the app theme (and not the default value of `LocalOnyxTheme`, which is
        // light). This is **not** the cause of the measured defect — the dark background behind the panel
        // is the **dialog window scrim** (`setBackgroundBlurRadius` + `dimAmount`), which
        // the surface lock does not judge — but it is the guarantee the subtree was missing.
        CompositionLocalProvider(LocalOnyxTheme provides theme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // **The HOUSE scrim**, not a 35 % black written here : the same as the sheets'
                // ([HouseSheetScrim]) — the page behind is clearly darkened, and
                // the opaque panel does the rest.
                .background(HouseSheetScrim)
                .padding(horizontal = OnyxSpace.Base)
                .onyxTappable { if (busy == null) onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    // The panel does not pass the press to the scrim : pressing between two rows
                    // closes nothing.
                    .onyxTappable { }
                    // **The house surface, opaque first** : the panel no longer lets the page be read
                    // through it (owner's defect : « on lit à travers le panneau » — "you can read through the panel"). A
                    // single surface function for sheets **and** dialogs
                    // ([housePanelSurface]), so the fix holds for both families.
                    .housePanelSurface(
                        RoundedCornerShape(GlassPanelCorner),
                        // The tint is read **here**, in the restated dialog theme.
                        fill = OnyxSurfaces.surfaceHigh(theme),
                    )
                    .padding(horizontal = OnyxSpace.CardPadding, vertical = OnyxSpace.CardPadding),
                verticalArrangement = Arrangement.spacedBy(OnyxSpace.Snug),
            ) {
                BasicText(
                    text = "New room",
                    style = theme.headline.copy(color = theme.textColor, textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth(),
                )

                // The field label, then the field with its placeholder.
                Column(verticalArrangement = Arrangement.spacedBy(OnyxSpace.Hair)) {
                    BasicText(
                        text = "Room name",
                        style = theme.footnote.copy(color = theme.secondaryText),
                    )
                    ProvideFieldTextColours(theme) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                // The field is focused right away : its edge is therefore at the accent,
                                // as the reference shows.
                                .composerFieldSurface(theme, RoundedCornerShape(OnyxSpace.FieldCorner), focused = true)
                                .padding(horizontal = OnyxSpace.Snug, vertical = OnyxSpace.Half),
                        ) {
                            OnyxTextField(
                                value = name,
                                onValueChange = { name = it; blocked = null },
                                placeholder = "Release room",
                                modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
                                style = theme.body.copy(color = theme.textColor),
                                imeAction = ImeAction.Done,
                            )
                        }
                    }
                }

                // What the room becomes, told **truthfully** for this hosting.
                BasicText(
                    text = roomExplanation(hosting),
                    style = theme.footnote.copy(color = theme.secondaryText),
                )

                BasicText(
                    text = "Bots · pick $ROOM_MIN_BOTS to $ROOM_MAX_BOTS",
                    style = theme.caption.copy(color = theme.tertiaryText, fontWeight = FontWeight.SemiBold),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 264.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (bots.isEmpty()) {
                        BasicText(
                            text = "No bots on this gateway yet.",
                            style = theme.footnote.copy(color = theme.secondaryText),
                        )
                    }
                    bots.forEach { bot ->
                        val picked = bot.name in chosen
                        val full = !picked && chosen.size >= ROOM_MAX_BOTS
                        RoomBotRow(
                            bot = bot,
                            picked = picked,
                            // A seventh bot offered : the row dims before any press, and its
                            // description read by a screen reader explains why.
                            fullNotice = full,
                            enabled = busy == null,
                            onToggle = { if (picked) chosen.remove(bot.name) else if (!full) chosen.add(bot.name) },
                        )
                    }
                }

                BasicText(
                    text = roomPickedLine(chosen.size),
                    style = theme.caption.copy(color = theme.tertiaryText),
                )

                // The reason, **readable**, as long as the action cannot go through.
                (blocked ?: reason)?.let { why ->
                    BasicText(
                        text = why,
                        style = theme.footnote.copy(color = theme.warning),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (error != null && busy == null) {
                    BasicText(
                        text = error,
                        localize = false,
                        style = theme.footnote.copy(color = theme.error),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Snug, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoomActionPill(
                        text = "Cancel",
                        primary = false,
                        enabled = busy == null,
                        onClick = onDismiss,
                    )
                    RoomActionPill(
                        text = "Create",
                        primary = true,
                        enabled = reason == null,
                        // Inert **and** the reason restated : pressing never does anything in silence.
                        onClick = {
                            if (reason == null) onCreate(name.trim(), chosen.toSet()) else blocked = reason
                        },
                        description = reason,
                    )
                }
            }
        }
        }
    }
}

/** What hosts the room being opened : it is **this** that decides the sentence, never the reverse. */
enum class RoomHosting {
    /** La passerelle tient le salon : il continue sans application ouverte. */
    GATEWAY,

    /** Without Dashboard, the room is kept on this phone. */
    PHONE,

    /** The Dashboard is there, but its gateway does not hold rooms : the opening will be refused. */
    UNAVAILABLE,
}

/**
 * The real hosting : **the same** condition as the opening path (`createGroupRoom` — the
 * Dashboard credentials for the server, the phone store otherwise).
 */
internal fun roomHosting(hasDashboard: Boolean, gatewayRunsRooms: Boolean): RoomHosting = when {
    hasDashboard && gatewayRunsRooms -> RoomHosting.GATEWAY
    hasDashboard -> RoomHosting.UNAVAILABLE
    else -> RoomHosting.PHONE
}

/**
 * Le paragraphe de la boîte, selon [hosting]. Chaque variante ne dit que ce que ce chemin-là tient :
 * « il continue même application fermée » n'apparaît **que** pour un salon que la passerelle tient.
 */
internal fun roomExplanation(hosting: RoomHosting): String = when (hosting) {
    RoomHosting.GATEWAY -> OnyxLocalization[
        "The bots you pick talk things over in the room. It runs on the gateway, so it keeps going " +
            "with no app open, and the whole conversation is there when you come back."
    ]

    RoomHosting.PHONE -> OnyxLocalization[
        "The bots you pick talk things over in the room. This one is kept on this phone: it is still " +
            "there after a restart, but its bots only answer while Onyx is open."
    ]

    RoomHosting.UNAVAILABLE -> OnyxLocalization[
        "The bots you pick talk things over in the room. The gateway isn't running rooms right now: " +
            "opening one will be turned down, and nothing is kept if it is."
    ]
}

/** The constraint bounds : **the server's** (`GroupRoom.MIN_MEMBERS` / `MAX_MEMBERS`). */
internal const val ROOM_MIN_BOTS = GroupRoom.MIN_MEMBERS
internal const val ROOM_MAX_BOTS = GroupRoom.MAX_MEMBERS

/**
 * Why the action cannot go through yet, or `null` when it can.
 *
 * This is **the** rule, and it is shown : an empty name, fewer than two bots, more than six, or an
 * opening already in progress. Each case has its sentence — the action stays inert, but the reason
 * is readable.
 */
internal fun roomBlockReason(name: String, chosen: Int, busy: Boolean): String? = when {
    busy -> OnyxLocalization["Making the room…"]
    name.isBlank() -> OnyxLocalization["Name the room."]
    chosen < ROOM_MIN_BOTS -> OnyxLocalization.format(
        "%1\$d more: a room needs at least %2\$d bots.",
        ROOM_MIN_BOTS - chosen,
        ROOM_MIN_BOTS,
    )

    chosen > ROOM_MAX_BOTS -> OnyxLocalization.format(
        "Take %1\$d out: %2\$d bots at most.",
        chosen - ROOM_MAX_BOTS,
        ROOM_MAX_BOTS,
    )

    else -> null
}

/** How many bots are picked : « 3 picked », singular included. */
internal fun roomPickedLine(chosen: Int): String =
    if (chosen == 1) OnyxLocalization["1 picked"] else OnyxLocalization.format("%1\$d picked", chosen)

/** The room name as it will be sent : what was typed, without its edge whitespace. */
internal fun roomNameToSend(name: String): String = name.trim()

/** A bot row : its face, its name, and its selection circle. */
@Composable
private fun RoomBotRow(
    bot: BotProfile,
    picked: Boolean,
    fullNotice: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .background(
                color = if (picked) theme.accent.copy(alpha = 0.14f) else Color.Transparent,
                shape = RoundedCornerShape(OnyxSpace.FormCorner),
            )
            .onyxTappable(enabled = enabled && (picked || !fullNotice), onClick = onToggle)
            .semantics {
                role = Role.Checkbox
                toggleableState = if (picked) ToggleableState.On else ToggleableState.Off
                contentDescription = bot.label()
            }
            .padding(horizontal = OnyxSpace.Half, vertical = OnyxSpace.Hair),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Snug),
    ) {
        BotLook(name = bot.name, faceCode = bot.faceCode, size = 30.dp)
        BasicText(
            text = bot.label(),
            localize = false,
            style = theme.body.copy(
                color = if (fullNotice) theme.tertiaryText else theme.textColor,
                fontWeight = FontWeight.Medium,
            ),
            // A bot's name is a **row title** : two lines, ellipsis beyond.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        PickMark(picked = picked, theme = theme)
    }
}

/** The selection circle : filled with the accent and its check, or the empty ring. */
@Composable
private fun PickMark(picked: Boolean, theme: ResolvedTheme) {
    if (picked) {
        Box(
            modifier = Modifier.size(20.dp).background(theme.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // The row already says « choisi » ("picked", checkbox role) : the mark stays silent.
            OnyxIcon(symbol = Symbols.Checkmark, tint = theme.accentInk, size = 12.dp, contentDescription = null)
        }
    } else {
        Box(Modifier.size(20.dp).border(1.dp, theme.separator, CircleShape))
    }
}

/**
 * A panel action, as a **pill** : the solid accent to confirm, a simple outline to
 * cancel, and the blocked state that dims without ever hiding its reason ([description]).
 */
@Composable
private fun RoomActionPill(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    description: String? = null,
) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            // The touch target stays 44 dp even when the drawn pill is shorter.
            .defaultMinSize(minHeight = 44.dp)
            // **A disabled action does not respond.** It does not "take back" the gesture to
            // restate its reason : it is disabled in the platform sense (the screen reader
            // announces it as such), and its reason is **named** — on screen, just above, and
            // in `stateDescription`.
            .then(if (enabled) Modifier.onyxTappable(onClick = onClick) else Modifier)
            .semantics {
                if (enabled) {
                    description?.let { contentDescription = it }
                } else {
                    disabled()
                    description?.let { stateDescription = it }
                }
            }
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 34.dp)
                .background(
                    color = when {
                        // **The house's primary action : the accent, and it alone.**
                        primary && enabled -> theme.accent
                        // Disabled : the **muted accent**, not a neutral grey. It is the colour
                        // family that distinguishes the two actions — a grey, even of another
                        // step, reads as the secondary one (« deux pilules du même gris » — "two pills
                        // of the same grey", the owner's defect), and nothing says which is the action.
                        primary -> theme.accent.copy(alpha = 0.16f)
                        // The secondary : the house's throat grey, with its hairline.
                        else -> theme.fill
                    },
                    shape = CircleShape,
                )
                .then(
                    when {
                        // Disabled : the accent hairline says « c'est l'action, pas maintenant » ("it is the action, not now").
                        primary && !enabled -> Modifier.border(1.dp, theme.accent.copy(alpha = 0.35f), CircleShape)
                        primary -> Modifier
                        else -> Modifier.border(1.dp, theme.separator, CircleShape)
                    },
                )
                .padding(horizontal = OnyxSpace.Base, vertical = OnyxSpace.Half),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = text,
                style = theme.body.copy(
                    color = when {
                        primary && enabled -> theme.accentInk
                        enabled -> theme.textColor
                        // Readable, not erased : the disabled action's ink keeps the accent, at
                        // a strength that stays above the contrast floor — and the reason
                        // is **named** just above (and in `stateDescription`).
                        primary -> theme.accent.copy(alpha = 0.85f)
                        else -> theme.secondaryText
                    },
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
            )
        }
    }
}
