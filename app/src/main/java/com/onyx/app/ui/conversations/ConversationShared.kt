package com.onyx.app.ui.conversations

import com.onyx.app.localization.OnyxLanguage
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.localization.resolveOnyxText
import com.onyx.app.ui.bots.BotsArrangeList
import com.onyx.app.ui.bots.CLOCK_SLACK
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

/**
 * Les **pièces partagées** de la partie Conversations — celles qui survivent à la disparition de
 * l'écran qui les portait.
 *
 * L'écran des conversations (`ConversationsScreen`) a été **retiré** : la liste vit désormais dans
 * le **volet**, avec sa recherche, ses bots, ses salons et sa multi-sélection, et les onglets de
 * section sont les tuiles du volet. Ce qui reste ici est ce que d'autres écrans utilisent encore :
 * les modèles d'un salon ([GroupChat], [GroupCreation]), les onglets de section ([ListSection], que
 * le tableau Kanban porte aussi), la barre de sélection ([SelectionBar], que l'écran d'un projet
 * porte encore), l'activité d'un bot ([BotActivity], [botActivity], [conversationUnread]) et
 * l'horodatage court d'une rangée ([rowTime]).
 */

/** Les onglets de section de la liste, comme le `Picker` d'la référence : Chat · Cron · Kanban · Code. */
enum class ListSection(val title: String) {
    CHATS("Chat"),
    CRON("Cron"),
    KANBAN("Kanban"),
    CODE("Code"),
}

/**
 * Un groupe de bots tel que le serveur le connaît (port de `GroupRoom`).
 *
 * Ses membres sont des [BotProfile] déjà chargés par l'écran : la rangée d'un groupe ne demande
 * donc rien de plus au serveur pour dessiner les visages.
 */

/**
 * Un groupe de bots tel que le serveur le connaît (port de `GroupRoom`).
 *
 * Ses membres sont des [BotProfile] déjà chargés par l'écran : la rangée d'un groupe ne demande
 * donc rien de plus au serveur pour dessiner les visages.
 */
data class GroupChat(
    val id: String,
    val name: String,
    val members: List<BotProfile> = emptyList(),
    val updatedAt: Long? = null,
    val lastMessage: String? = null,
    /**
     * Combien de membres sont en train de répondre dans la salle, en ce moment.
     *
     * C'est le `answering` de `GroupFeed` (ServerGroups) : tant qu'il reste des réponses,
     * la rangée le dit (« N answering ») à la place de la date, comme la référence y met le rond qui
     * tourne à la place de l'heure. Zéro, rien ne répond.
     */
    val answering: Int = 0,
    /**
     * Où la salle en est (`GroupRoom.activityMark`) : le numéro de son dernier événement, ou
     * l'heure de la copie d'une salle qu'Hermes Desktop tient. Sert au badge « pas encore lu ».
     */
    val activityMark: Double? = null,
    /** Une salle qu'Hermes Desktop tient lui-même : la rangée en porte la marque. */
    val inDesktop: Boolean = false,
    /**
     * Une salle que ce téléphone garde lui-même, faute de Dashboard pour la tenir : elle s'ouvre
     * sur son fil local, et non sur le journal d'une salle du serveur.
     */
    val local: Boolean = false,
)

/**
 * Ce que devient un « New Group Chat » : la salle faite — sur le serveur, ou gardée ici —, ou ce
 * qui l'a empêchée. Un échec n'est jamais un succès muet : l'écran le montre et rien n'est gardé.
 */

/**
 * Ce que devient un « New Group Chat » : la salle faite — sur le serveur, ou gardée ici —, ou ce
 * qui l'a empêchée. Un échec n'est jamais un succès muet : l'écran le montre et rien n'est gardé.
 */
sealed interface GroupCreation {
    /** La salle est là : sur le serveur, ou dans le magasin des salles gardées ici. */
    data class Made(val room: GroupChat) : GroupCreation

    /** La salle n'a pas été faite : ce qui l'a empêchée, prêt à être montré tel quel. */
    data class Failed(val problem: String) : GroupCreation
}

/**
 * L'écran des conversations.
 *
 * Port de `ConversationsView` : le grand titre, la barre à quatre boutons (réglages,
 * titre, sélection, écriture), le champ « Search conversations », les segments Chats / Cron /
 * Kanban, la section des bots avec leurs orbes, et les lignes qui font agir (nouveau groupe,
 * nouveau bot, laisser les bots se parler).
 *
 * Les paramètres de la fin (groupes, chargement, erreur, pied de liste) sont optionnels : l'écran
 * se dessine sans eux comme avant, et l'appelant les renseigne quand il a la donnée.
 */
@Composable

internal fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    /**
     * Le déplacement groupé vers un autre projet — l'écran d'un projet le propose, la liste des
     * conversations non. Présent, la barre **mesure** les trois libellés : s'ils ne tiennent pas
     * sur la ligne (téléphone fermé, police 1,3× comprise), elle n'écrit plus que les icônes,
     * chacune nommée pour un lecteur d'écran.
     */
    onMove: (() -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    val enabled = count > 0
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val spacing = 16.dp
    val countStyle = theme.subheadline.copy(color = theme.secondaryText)
    val actionStyle = theme.body.copy(color = theme.accent)
    val countFull = "$count selected"
    val countShort = "$count"
    val selectAllText = if (allSelected) "Deselect all" else "Select all"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(theme.surfaceColor),
    ) {
        // Le filet du haut : la liste défile dessous, comme sous les barres de la référence.
        OnyxRowDivider(inset = 0.dp)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // La mesure porte sur le texte **localisé** et sur la densité courante : la police
            // d'accessibilité agrandie change les largeurs, pas seulement le dessin.
            val rows = selectionBarRows(
                available = constraints.maxWidth.toFloat(),
                fullCount = measurer.measure(resolveOnyxText(countFull), countStyle).size.width.toFloat(),
                shortCount = measurer.measure(resolveOnyxText(countShort), countStyle).size.width.toFloat(),
                selectAll = measurer.measure(resolveOnyxText(selectAllText), actionStyle).size.width.toFloat(),
                cancel = measurer.measure(resolveOnyxText("Cancel"), actionStyle).size.width.toFloat(),
                spacing = with(density) { spacing.toPx() },
            )
            val countLabel = if (rows.shortCount) countShort else countFull

            // Les actions groupées : deux (archiver, supprimer) dans la liste, trois ici quand un
            // projet peut aussi recevoir les conversations. Leurs libellés se mesurent : avec trois
            // verbes sur un fil étroit, la barre se contente des icônes.
            val verbs = buildList {
                add("Archive $count")
                if (onMove != null) add("Move $count")
                add("Delete $count")
            }
            val iconGap = with(density) { 6.dp.toPx() }
            val verbsWidth = verbs.sumOf {
                measurer.measure(resolveOnyxText(it), actionStyle).size.width.toDouble()
            } + verbs.size * (with(density) { 17.dp.toPx() } + iconGap)
            val withLabels = selectionBarWithLabels(
                available = constraints.maxWidth.toFloat(),
                verbsWidth = verbsWidth.toFloat(),
                hasMove = onMove != null,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (rows.actionsBelow) {
                    // Le compte garde sa ligne entière ; ses actions descendent dessous.
                    SelectionBarCount(countLabel, countStyle, Modifier.fillMaxWidth())
                    if (rows.actionsStacked) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SelectionBarText(selectAllText, theme.accent, onSelectAll)
                            SelectionBarText("Cancel", theme.accent, onCancel)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.End),
                        ) {
                            SelectionBarText(selectAllText, theme.accent, onSelectAll)
                            SelectionBarText("Cancel", theme.accent, onCancel)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        // Le compte cède la place, jamais un mot : une seule ligne, ellipse au pire.
                        SelectionBarCount(countLabel, countStyle, Modifier.weight(1f))
                        SelectionBarText(selectAllText, theme.accent, onSelectAll)
                        SelectionBarText("Cancel", theme.accent, onCancel)
                    }
                }

                // Les actions groupées, aux deux bords : l'ordre et l'écart de la référence.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SelectionBarAction(
                        symbol = Symbols.ArchiveBox,
                        label = if (withLabels) "Archive $count" else null,
                        contentLabel = "Archive $count",
                        tint = if (enabled) theme.accent else theme.tertiaryText,
                        enabled = enabled,
                        onClick = onArchive,
                    )
                    onMove?.let { move ->
                        SelectionBarAction(
                            symbol = Symbols.Folder,
                            label = if (withLabels) "Move $count" else null,
                            contentLabel = "Move $count",
                            tint = if (enabled) theme.accent else theme.tertiaryText,
                            enabled = enabled,
                            onClick = move,
                        )
                    }
                    SelectionBarAction(
                        symbol = Symbols.Trash,
                        label = if (withLabels) "Delete $count" else null,
                        contentLabel = "Delete $count",
                        tint = if (enabled) theme.error else theme.tertiaryText,
                        enabled = enabled,
                        semibold = true,
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

/**
 * Les libellés des actions groupées de la barre tiennent-ils sur la ligne ?
 *
 * Deux actions (archiver, supprimer) les gardent toujours — c'est la barre de la liste, mesurée
 * pour ce cas. Trois (une de plus quand un projet peut **déplacer** ses conversations vers un
 * autre) seulement si la place mesurée le permet : sinon la barre n'écrit que les icônes, chacune
 * nommée pour un lecteur d'écran, plutôt que de couper un mot.
 */

/**
 * Les libellés des actions groupées de la barre tiennent-ils sur la ligne ?
 *
 * Deux actions (archiver, supprimer) les gardent toujours — c'est la barre de la liste, mesurée
 * pour ce cas. Trois (une de plus quand un projet peut **déplacer** ses conversations vers un
 * autre) seulement si la place mesurée le permet : sinon la barre n'écrit que les icônes, chacune
 * nommée pour un lecteur d'écran, plutôt que de couper un mot.
 */
internal fun selectionBarWithLabels(available: Float, verbsWidth: Float, hasMove: Boolean): Boolean =
    !hasMove || verbsWidth <= available

/**
 * Ce que la barre de sélection met sur la ligne du compte, décidé en **mesurant** la police du
 * système plutôt qu'en pariant sur une largeur en dp.
 *
 * La capture du propriétaire montrait le compte cassé mot par mot — « 4 / élément / s / sélectio /
 * nnés » — parce qu'il était le seul élément souple d'une ligne serrée entre deux actions de largeur
 * fixe. Trois replis, dans cet ordre, et le texte reste entier dans tous les cas : le compte en
 * entier s'il tient à côté des actions ; le seul nombre s'il ne reste que lui ; puis les actions
 * descendent — ensemble, ou chacune sur sa ligne s'il le faut.
 */

/**
 * Ce que la barre de sélection met sur la ligne du compte, décidé en **mesurant** la police du
 * système plutôt qu'en pariant sur une largeur en dp.
 *
 * La capture du propriétaire montrait le compte cassé mot par mot — « 4 / élément / s / sélectio /
 * nnés » — parce qu'il était le seul élément souple d'une ligne serrée entre deux actions de largeur
 * fixe. Trois replis, dans cet ordre, et le texte reste entier dans tous les cas : le compte en
 * entier s'il tient à côté des actions ; le seul nombre s'il ne reste que lui ; puis les actions
 * descendent — ensemble, ou chacune sur sa ligne s'il le faut.
 */
internal data class SelectionBarRows(
    /** Le compte s'écrit en abrégé (« 4 ») : le libellé entier ne tient pas à côté des actions. */
    val shortCount: Boolean = false,
    /** Les deux actions ne tiennent pas sur la ligne du compte : elles passent en dessous. */
    val actionsBelow: Boolean = false,
    /** Même en dessous, elles ne tiennent pas côte à côte : chacune prend sa ligne. */
    val actionsStacked: Boolean = false,
)

/**
 * La forme de la barre pour une largeur utile [available] (en pixels, police du système comprise),
 * les largeurs mesurées du compte ([fullCount], [shortCount]) et des deux actions ([selectAll],
 * [cancel]), et leur écart [spacing].
 */

/**
 * La forme de la barre pour une largeur utile [available] (en pixels, police du système comprise),
 * les largeurs mesurées du compte ([fullCount], [shortCount]) et des deux actions ([selectAll],
 * [cancel]), et leur écart [spacing].
 */
internal fun selectionBarRows(
    available: Float,
    fullCount: Float,
    shortCount: Float,
    selectAll: Float,
    cancel: Float,
    spacing: Float,
): SelectionBarRows {
    val beside = 2f * spacing + selectAll + cancel
    // Le compte en entier reste-t-il sur sa ligne, à côté des deux actions ? C'est la forme la plus
    // courte, et la seule qui garde la barre à deux lignes.
    if (fullCount + beside <= available) return SelectionBarRows()
    val stacked = selectAll + cancel + spacing > available
    // Sinon le compte prend **sa** ligne — en entier : un compte abrégé se lit mal, et la phrase
    // n'est pas un mot qu'on coupe, c'est une information qui change de place.
    if (fullCount <= available) return SelectionBarRows(actionsBelow = true, actionsStacked = stacked)
    // Même seul sur sa ligne, le libellé entier ne tient pas (police très agrandie, volet très
    // étroit) : le nombre seul, qui lui tient toujours.
    return SelectionBarRows(shortCount = true, actionsBelow = true, actionsStacked = stacked)
}

/** Le compte des rangées cochées : une seule ligne, jamais coupé au milieu d'un mot. */
@Composable

private fun SelectionBarCount(label: String, style: TextStyle, modifier: Modifier = Modifier) {
    BasicText(
        text = label,
        style = style,
        maxLines = 2,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Une action de liste de la barre (« Tout sélectionner », « Annuler »). */
@Composable

private fun SelectionBarText(label: String, color: Color, onClick: () -> Unit) {
    BasicText(
        text = label,
        style = LocalOnyxTheme.current.body.copy(color = color),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.onyxClickable(onClick = onClick),
    )
}

/** Une action groupée de la barre : son icône et son libellé, comme les boutons de la référence. */
@Composable

private fun SelectionBarAction(
    @DrawableRes symbol: Int,
    /** Nul quand la barre n'écrit que l'icône : c'est alors [contentLabel] qui la nomme. */
    label: String?,
    tint: Color,
    enabled: Boolean,
    semibold: Boolean = false,
    contentLabel: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.onyxClickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OnyxIcon(
            symbol = symbol,
            tint = tint,
            size = if (label == null) 22.dp else 17.dp,
            contentDescription = if (label == null) contentLabel else null,
        )
        if (label != null) {
            BasicText(
                text = label,
                style = LocalOnyxTheme.current.body.copy(
                    color = tint,
                    fontWeight = if (semibold) FontWeight.SemiBold else FontWeight.Normal,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * La section des bots, comme `botsSection` de la référence : des **lignes**, une par bot (`botRow`) — le
 * point du non-lu dans la marge, son visage, son nom en gras, l'heure de son dernier mot, puis le
 * dernier mot de son chat sous lui —, les **salles** du serveur (`groupRow`, leurs visages empilés
 * et qui en fait partie), puis les lignes qui font agir (New Group Chat, New Bot, laisser les bots
 * se parler), séparées par le filet rentré d'une liste la référence.
 *
 * Pendant « Reorder Bots », la section seule s'affiche, ses lignes à poignées (`BotsArrangeList`),
 * comme `arrangingBots` de la référence. « Reorder Bots » et « Sort Bots by Recent » ne sont pas des lignes
 * de la section : elles sont dans le menu contextuel d'une ligne et d'une salle (`orderingBots`),
 * comme sur la référence.
 */
@Composable

/**
 * L'activité d'un bot, déduite des conversations déjà chargées : une réponse en cours
 * (« Working »), ou un dernier mot de l'utilisateur resté sans réponse (« Needs you »).
 *
 * Le seul lien que le modèle expose entre un bot et ses conversations est l'identifiant de son
 * chat (`BotProfile.chatId`, le `canonical_session` du serveur) ; sans correspondance, le bot
 * reste au repos — aucune donnée n'est demandée au serveur pour ces pastilles.
 */
internal fun botActivity(bot: BotProfile, conversations: List<Conversation>): BotActivity {
    val chatId = bot.chatId ?: return BotActivity()
    val mine = conversations.filter { it.serverSessionId == chatId || it.id == chatId }
    val working = mine.any { it.messages.lastOrNull()?.isStreaming == true }
    val needsYou = mine.any {
        val last = it.messages.lastOrNull()
        last != null && last.role == Role.USER && !last.isStreaming
    }
    val unread = !working && mine.any { it.messages.lastOrNull()?.role == Role.ASSISTANT }
    return BotActivity(working = working, needsYou = needsYou, unread = unread)
}

/** Ce qu'un bot fait, tel que sa pastille et son visage le montrent. */

/** Ce qu'un bot fait, tel que sa pastille et son visage le montrent. */
data class BotActivity(
    val working: Boolean = false,
    val needsYou: Boolean = false,
    val unread: Boolean = false,
)

/**
 * Le non-lu d'une conversation, comme `ChatActivity.unread` (Echo/Services/ChatActivity:97) :
 * ni celle qu'on regarde, ni celle où un tour s'écrit, ni une conversation tue. Marquée non lue à
 * la main (`seen == 0`), elle l'est quoi qu'elle ait fait depuis ; vue jusqu'à [seen], une réponse
 * venue après la rallume (l'écart d'horloge d'la référence, [CLOCK_SLACK], toléré). Sans marque — le premier
 * regard sur la liste, ou un fil venu d'ailleurs —, c'est le fil qui parle ([fallback] : le dernier
 * mot est celui de l'agent).
 */

/**
 * Le non-lu d'une conversation, comme `ChatActivity.unread` (Echo/Services/ChatActivity:97) :
 * ni celle qu'on regarde, ni celle où un tour s'écrit, ni une conversation tue. Marquée non lue à
 * la main (`seen == 0`), elle l'est quoi qu'elle ait fait depuis ; vue jusqu'à [seen], une réponse
 * venue après la rallume (l'écart d'horloge d'la référence, [CLOCK_SLACK], toléré). Sans marque — le premier
 * regard sur la liste, ou un fil venu d'ailleurs —, c'est le fil qui parle ([fallback] : le dernier
 * mot est celui de l'agent).
 */
internal fun conversationUnread(
    lastSpokenAt: Long?,
    seen: Double?,
    isOpen: Boolean,
    busy: Boolean,
    muted: Boolean,
    fallback: Boolean,
    slack: Double = CLOCK_SLACK,
): Boolean {
    if (isOpen || busy || muted) return false
    if (seen == null) return fallback
    if (seen == 0.0) return true
    return lastSpokenAt != null && lastSpokenAt / 1000.0 > seen + slack
}

/**
 * « Today, 9:41 », « Yesterday », « Monday », « 12 Mar » : l'heure d'une rangée.
 *
 * Port de `DateGroup.rowTime` : l'heure du jour, « Yesterday » pour la veille, le nom du jour pour
 * la semaine qui vient de passer, puis le jour et le mois — et l'année seulement quand elle change.
 */

/**
 * « Today, 9:41 », « Yesterday », « Monday », « 12 Mar » : l'heure d'une rangée.
 *
 * Port de `DateGroup.rowTime` : l'heure du jour, « Yesterday » pour la veille, le nom du jour pour
 * la semaine qui vient de passer, puis le jour et le mois — et l'année seulement quand elle change.
 */
internal fun rowTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val yesterday = sameYear && now.get(Calendar.DAY_OF_YEAR) - 1 == then.get(Calendar.DAY_OF_YEAR)
    // Les jours 2 à 6 en arrière, hors hier déjà nommé : on dit le jour de la semaine (« Monday »).
    val withinWeek = sameYear && now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) in 2..6
    return when {
        sameDay -> java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, OnyxLanguage.preferred).format(Date(timestamp))
        yesterday -> OnyxLocalization["Yesterday"]
        withinWeek -> SimpleDateFormat("EEE", OnyxLanguage.preferred).format(Date(timestamp))
        sameYear -> SimpleDateFormat("d MMM", OnyxLanguage.preferred).format(Date(timestamp))
        else -> SimpleDateFormat("d MMM yyyy", OnyxLanguage.preferred).format(Date(timestamp))
    }
}

/** L'ordre de la liste, comme le menu de tri d'la référence. */
