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
 * **La boîte de création d'un salon** — le port de la boîte de la référence, au gabarit de la maison.
 *
 * La référence la dessine dans `shared/ui/.../rooms/CreateRoomDialog.kt:66` : un panneau, un titre,
 * un champ de nom avec son invite, le paragraphe qui dit ce que devient le salon, l'intitulé de
 * section qui porte la contrainte (« BOTS · EN CHOISIR DE 2 À 6 »), la liste des bots avec leur
 * visage et un cercle de sélection, puis l'action de validation en pilule. Onyx n'avait pas cette
 * boîte : ses bots partaient d'un choix par défaut (`GroupChatViewModel.init`), sans nom et sans
 * contrainte montrée.
 *
 * **Le paragraphe ne promet que ce qui est tenu.** Trois hébergements existent, et chacun a sa
 * phrase ([roomExplanation]) :
 *
 * - la **passerelle** tient le salon (`groups.create` avec `driver: true`) : il tourne sans
 *   application ouverte, et tout se relit à la reconnexion (`groups.log`) — c'est la promesse de la
 *   référence, et elle est vraie ici ;
 * - sans identifiants de Dashboard, le salon est **gardé sur ce téléphone** (`GroupRoomStore`) : il
 *   survit à une relance, mais ses bots ne répondent que pendant que l'application est ouverte — le
 *   dire autrement serait une promesse que le code ne tient pas ;
 * - avec un Dashboard dont la passerelle ne tient pas de salon (`driver: false`, passerelle
 *   ancienne), l'ouverture est refusée et rien n'est gardé : la phrase le dit avant l'appel.
 *
 * Chaque ligne de bot porte **son visage** ([BotLook]) et **son cercle** : plein à l'accent avec sa
 * coche quand il est choisi, anneau vide sinon — le `PickMark` de la référence.
 *
 * L'action de validation est **réellement** contrainte : elle n'agit pas tant que le nom est vide ou
 * que le compte sort de 2 à 6, et la raison s'affiche à côté ([roomBlockReason]) — jamais un bouton
 * grisé qui ne dit rien.
 */
@Composable
fun RoomCreationDialog(
    bots: List<BotProfile>,
    /** Le paragraphe suit **la même** source que l'ouverture : [roomHosting]. */
    hasDashboard: Boolean,
    gatewayRunsRooms: Boolean,
    /** Ce qui court à l'instant (« Ouverture du salon… »), ou `null`. */
    busy: String? = null,
    /** Le refus du serveur, montré tel quel sous la liste. */
    error: String? = null,
    onDismiss: () -> Unit,
    onCreate: (name: String, members: Set<String>) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    var name by remember { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<String>() }
    // L'appui sur l'action tant qu'elle ne peut pas aboutir : la raison est alors **reposée**.
    var blocked by remember { mutableStateOf<String?>(null) }
    val hosting = roomHosting(hasDashboard, gatewayRunsRooms)
    val reason = roomBlockReason(name, chosen.size, busy != null)
    val fieldFocus = remember { FocusRequester() }
    // Le champ est **focalisé** à l'ouverture, comme dans la référence : son arête prend l'accent.
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    Dialog(
        onDismissRequest = { if (busy == null) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = busy == null),
    ) {
        DialogWindowBlur(FrostSurface.SHEET)
        // **Le thème de l'app, reposé à l'entrée de la fenêtre.** Une `Dialog` ouvre une
        // sous-composition : le repos explicite garantit que le panneau, son encre et ses actions
        // suivent le thème de l'app (et non la valeur par défaut de `LocalOnyxTheme`, qui est
        // claire). Ce n'est **pas** la cause du défaut mesuré — le fond sombre derrière le panneau
        // est le **voile de la fenêtre de dialogue** (`setBackgroundBlurRadius` + `dimAmount`), que
        // le verrou de surface ne juge pas — mais c'est la garantie qui manquait au sous-arbre.
        CompositionLocalProvider(LocalOnyxTheme provides theme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // **Le voile de la MAISON**, pas un noir à 35 % écrit ici : le même que celui des
                // feuilles ([HouseSheetScrim]) — la page derrière est franchement assombrie, et
                // le panneau opaque fait le reste.
                .background(HouseSheetScrim)
                .padding(horizontal = OnyxSpace.Base)
                .onyxTappable { if (busy == null) onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    // Le panneau ne transmet pas l'appui au voile : appuyer entre deux lignes ne
                    // referme rien.
                    .onyxTappable { }
                    // **La surface de la maison, opaque d'abord** : le panneau ne laisse plus lire la
                    // page au travers (défaut du propriétaire : « on lit à travers le panneau »). Une
                    // seule fonction de surface pour les feuilles **et** les dialogues
                    // ([housePanelSurface]), donc la correction vaut pour les deux familles.
                    .housePanelSurface(
                        RoundedCornerShape(GlassPanelCorner),
                        // La teinte est lue **ici**, dans le thème du dialogue reposé.
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

                // Le libellé du champ, puis le champ avec son invite.
                Column(verticalArrangement = Arrangement.spacedBy(OnyxSpace.Hair)) {
                    BasicText(
                        text = "Room name",
                        style = theme.footnote.copy(color = theme.secondaryText),
                    )
                    ProvideFieldTextColours(theme) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                // Le champ est focalisé d'emblée : son arête est donc à l'accent,
                                // comme la référence le montre.
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

                // Ce que devient le salon, dit **vrai** pour cet hébergement.
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
                            // Un septième bot proposé : la ligne s'éteint avant tout appui, et sa
                            // description dite par un lecteur d'écran explique pourquoi.
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

                // La raison, **lisible**, tant que l'action ne peut pas aboutir.
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
                        // Inerte **et** la raison reposée : appuyer ne fait jamais rien en silence.
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

/** Ce qui héberge le salon qu'on ouvre : c'est **lui** qui décide la phrase, jamais l'inverse. */
enum class RoomHosting {
    /** La passerelle tient le salon : il continue sans application ouverte. */
    GATEWAY,

    /** Sans Dashboard, le salon est gardé sur ce téléphone. */
    PHONE,

    /** Le Dashboard est là, mais sa passerelle ne tient pas de salon : l'ouverture sera refusée. */
    UNAVAILABLE,
}

/**
 * L'hébergement réel : **la même** condition que le chemin d'ouverture (`createGroupRoom` — les
 * identifiants du Dashboard pour le serveur, le magasin du téléphone sinon).
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

/** Les bornes de la contrainte : **celles du serveur** (`GroupRoom.MIN_MEMBERS` / `MAX_MEMBERS`). */
internal const val ROOM_MIN_BOTS = GroupRoom.MIN_MEMBERS
internal const val ROOM_MAX_BOTS = GroupRoom.MAX_MEMBERS

/**
 * Pourquoi l'action ne peut pas encore aboutir, ou `null` quand elle le peut.
 *
 * C'est **la** règle, et elle est montrée : un nom vide, moins de deux bots, plus de six, ou une
 * ouverture déjà en cours. Chaque cas a sa phrase — l'action reste inerte, mais la raison se lit.
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

/** Combien de bots sont choisis : « 3 picked », au singulier près. */
internal fun roomPickedLine(chosen: Int): String =
    if (chosen == 1) OnyxLocalization["1 picked"] else OnyxLocalization.format("%1\$d picked", chosen)

/** Le nom du salon tel qu'il partira : ce qui est tapé, sans ses blancs de bord. */
internal fun roomNameToSend(name: String): String = name.trim()

/** Une ligne de bot : son visage, son nom, et son cercle de sélection. */
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
            // Le nom d'un bot est un **titre de rangée** : deux lignes, ellipse au-delà.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        PickMark(picked = picked, theme = theme)
    }
}

/** Le cercle de sélection : plein à l'accent avec sa coche, ou l'anneau vide. */
@Composable
private fun PickMark(picked: Boolean, theme: ResolvedTheme) {
    if (picked) {
        Box(
            modifier = Modifier.size(20.dp).background(theme.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // La ligne dit déjà « choisi » (rôle de case à cocher) : la marque reste muette.
            OnyxIcon(symbol = Symbols.Checkmark, tint = theme.accentInk, size = 12.dp, contentDescription = null)
        }
    } else {
        Box(Modifier.size(20.dp).border(1.dp, theme.separator, CircleShape))
    }
}

/**
 * Une action du panneau, en **pilule** : l'accent plein pour valider, un simple contour pour
 * annuler, et l'état bloqué qui s'assombrit sans jamais cacher sa raison ([description]).
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
            // La cible tactile reste de 44 dp même quand la pilule dessinée est plus courte.
            .defaultMinSize(minHeight = 44.dp)
            // **Une action désactivée ne répond pas.** Elle ne « reprend » pas le geste pour
            // reposer sa raison : elle est désactivée au sens de la plateforme (le lecteur d'écran
            // l'annonce comme telle), et sa raison est **nommée** — à l'écran, juste au-dessus, et
            // dans `stateDescription`.
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
                        // **L'action principale de la maison : l'accent, et lui seul.**
                        primary && enabled -> theme.accent
                        // Désactivée : l'**accent atténué**, pas un gris neutre. C'est la famille
                        // de couleur qui distingue les deux actions — un gris, même d'un autre
                        // barreau, se lit comme la secondaire (« deux pilules du même gris », le
                        // défaut du propriétaire), et rien ne dit laquelle est l'action.
                        primary -> theme.accent.copy(alpha = 0.16f)
                        // La secondaire : le gris de gorge de la maison, avec son filet.
                        else -> theme.fill
                    },
                    shape = CircleShape,
                )
                .then(
                    when {
                        // Désactivée : le filet d'accent dit « c'est l'action, pas maintenant ».
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
                        // Lisible, pas effacé : l'encre de l'action désactivée garde l'accent, à
                        // une force qui reste au-dessus du plancher de contraste — et la raison
                        // est **nommée** juste au-dessus (et dans `stateDescription`).
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
