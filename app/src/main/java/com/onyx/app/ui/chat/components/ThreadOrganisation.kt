package com.onyx.app.ui.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.data.model.SubagentStatus
import com.onyx.app.data.model.TokenUsage
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.FlowState
import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.MotionFamily
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.SelectableText
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import java.util.Locale
import com.onyx.app.ui.design.tapLabel
import com.onyx.app.ui.localization.resolveOnyxText

/**
 * L'organisation du fil et de l'écran Compétences, reprise de la référence.
 *
 * Ce fichier ne porte que la **structure et l'information** : les regroupements, l'ordre, la
 * hiérarchie, les badges, les pieds de carte et les statuts. Le **dessin** reste celui d'Onyx
 * (`OnyxSpace`, les coins de la maison, les encres mesurées du thème) — aucune couleur de la
 * référence n'est reprise ici.
 *
 * Les fonctions pures de ce fichier sont testables sans appareil : ce sont elles qui portent les
 * gabarits (compteurs, jetons, pieds de carte, libellés d'étape), et les composables ne font que
 * les dessiner.
 */

// -------------------------------------------------------------------------------------------
// Gabarits — fonctions pures, testables
// -------------------------------------------------------------------------------------------

/**
 * « Ran 3 tools » / « Ran 1 tool » : le verbe de la **rangée d'outils repliée**, comme la
 * référence (qui dit « Ran » là où Onyx disait « Used »).
 *
 * Traduit **avant** d'être joint : les motifs du dictionnaire s'accrochent en `matchEntire`, donc
 * une phrase composée ne se traduirait pas.
 */
fun ranToolsLabel(count: Int): String = if (count == 1) {
    OnyxLocalization.format("Ran %d tool", count)
} else {
    OnyxLocalization.format("Ran %d tools", count)
}

/**
 * La durée de la puce repliée : « 1 s », « < 1 s », « 1 min 4 s ».
 *
 * Le « < 1 s » de la référence est écrit tel quel dans le dictionnaire (« < 1 s ») ; au-delà de la
 * minute on passe en minutes et secondes, comme la référence.
 *
 * LOT outils-detail : le seuil est **une seconde**, pas une demi-seconde. À 0,5 s le seuil coupait
 * un appel de 0,8 s en « **0 s** » — une durée qui se lit comme « rien du tout », alors que la
 * référence écrit « < 1 s » pour tout ce qui dure moins d'une seconde. Relevé sur la capture du lot
 * (la rangée `terminal` de 0,8 s).
 */
fun ranToolsDuration(seconds: Double?): String? {
    if (seconds == null) return null
    if (seconds < 1.0) return OnyxLocalization["< 1 s"]
    if (seconds < 60) return OnyxLocalization.format("%d s", seconds.toInt())
    val minutes = (seconds / 60).toInt()
    val rest = (seconds % 60).toInt()
    return OnyxLocalization.format("%d min %d s", minutes, rest)
}

/** La puce repliée complète : « Ran 3 tools · 1 s », ou sans la durée quand elle manque. */
fun ranToolsChip(count: Int, seconds: Double?): String =
    listOfNotNull(ranToolsLabel(count), ranToolsDuration(seconds)).joinToString(" · ")

/**
 * Les secondes qu'ont pris les outils d'une réponse : du premier départ au dernier retour, comme
 * la référence (c'est le temps **des outils**, pas celui du tour).
 */
fun toolsElapsedSeconds(tools: List<com.onyx.app.data.model.ToolActivity>): Double? {
    val start = tools.mapNotNull { it.startedAt.takeIf { at -> at > 0 } }.minOrNull() ?: return null
    val end = tools.mapNotNull { it.endedAt }.maxOrNull() ?: return null
    if (end <= start) return null
    return (end - start).toDouble() / 1_000.0
}

/** La durée d'**un** outil fini, ou `null` tant qu'il tourne. */
fun toolElapsedSeconds(tool: com.onyx.app.data.model.ToolActivity): Double? {
    val start = tool.startedAt.takeIf { it > 0 } ?: return null
    val end = tool.endedAt ?: return null
    if (end <= start) return null
    return (end - start).toDouble() / 1_000.0
}

/** Le genre d'un outil, en petit glyphe — la pastille qui précède « Ran 3 tools ». */
fun toolKindSymbol(name: String): Int = when {
    name == "terminal" || name == "process" || name == "execute_code" -> Symbols.Terminal
    name == "patch" || name == "write_file" || name == "edit_file" -> Symbols.Doc
    name == "read_file" || name == "search_files" -> Symbols.MagnifyingGlass
    name.startsWith("web_") || name.startsWith("browser_") -> Symbols.Globe
    name == "delegate_task" -> Symbols.Person2
    name == "memory" || name == "session_search" -> Symbols.Brain
    else -> Symbols.WrenchAndScrewdriver
}

/**
 * Les genres d'outils d'une réponse, dédoublonnés et limités à [RanIconsMax] : la puce ne devient
 * pas un mur d'icônes, elle dit le genre, le texte dit le nombre.
 */
fun toolKindSymbols(names: List<String>): List<Int> =
    names.map(::toolKindSymbol).distinct().take(RanIconsMax)

/** Le compte d'outils d'une réponse, au singulier comme au pluriel. */
fun toolsCountLabel(count: Int): String =
    if (count == 1) OnyxLocalization["1 tool"] else OnyxLocalization.format("%d tools", count)

/**
 * Le verbe **au passé** d'un outil **connu du tableau** — « Lancé », « Lu », « Modifié »,
 * « Recherché », « Délégué » —, ou `null` quand l'outil n'y figure pas.
 *
 * LOT outils-detail : la rangée d'outil a besoin des deux, séparément. Elle écrit le verbe **et**
 * l'identifiant technique à côté (un outil connu), ou le seul mot qui porte déjà l'identifiant (un
 * outil inconnu) — sans cette distinction elle l'écrirait deux fois.
 */
fun knownToolVerb(name: String): String? = when {
    name == "terminal" || name == "shell" || name == "bash" || name == "execute_code" || name == "process" ->
        OnyxLocalization["Ran (tool)"]
    name == "read_file" || name == "file_read" || name == "web_extract" || name == "browser" || name == "fetch" ->
        OnyxLocalization["Read (tool)"]
    name == "write_file" || name == "patch" || name == "edit_file" -> OnyxLocalization["Edited (tool)"]
    name == "web_search" || name == "search" || name == "search_files" || name == "session_search" ->
        OnyxLocalization["Searched (tool)"]
    name == "delegate_task" -> OnyxLocalization["Delegated (tool)"]
    else -> null
}

/**
 * Le verbe **au passé** d'une rangée d'outil finie : « Lancé », « Lu », « Modifié », « Recherché »,
 * « Délégué » — la rangée d'outils de la référence dit ce qui a été fait (le mot de l'action), et
 * la commande suit en monospace.
 *
 * Un outil inconnu garde son nom : « Utilisé <nom> » vaut mieux qu'un verbe inventé. Les clés sont
 * propres aux outils (`Read (tool)`) parce que « Read » porte déjà « Lire » ailleurs dans le
 * dictionnaire (un bouton de la fiche d'une compétence) — deux sens, deux clés.
 */
fun toolPastVerb(name: String): String = knownToolVerb(name)
    ?: OnyxLocalization.format("Used %1\$s", name.ifBlank { OnyxLocalization["a tool"] }.replace('_', ' '))

/**
 * Les jetons d'un pied de réponse : « 18,4k ↓ · 612 ↑ » — entrée puis sortie, comme la référence.
 *
 * Rien quand le serveur n'a rien compté : un « 0 ↓ » inventé serait un chiffre faux.
 */
fun replyTokenPair(usage: TokenUsage?): String? {
    if (usage == null) return null
    val input = usage.input
    val output = usage.output
    if (input <= 0 && output <= 0) return null
    return "${compactTokenCount(input)} \u2193 · ${compactTokenCount(output)} \u2191"
}

/** « 612 », « 18.4k », « 1.2M » : un compte de jetons compact, en chiffres latins. */
fun compactTokenCount(value: Int): String {
    val number = value.coerceAtLeast(0)
    return when {
        number < 1_000 -> number.toString()
        number < 1_000_000 -> String.format(Locale.US, "%.1fk", number / 1_000.0)
        else -> String.format(Locale.US, "%.1fM", number / 1_000_000.0)
    }
}

/**
 * Le pied d'une carte de sous-agent : « modèle · durée · N outils », les seules parts connues.
 *
 * Miroir de `meta(_:)` de la référence (`ChatScreen`/`SubagentCard` : le modèle, la durée une fois
 * fini, puis le compte d'outils). La durée n'est **pas** répétée quand la ligne de détail la porte
 * déjà : une carte finie la dit à un seul endroit.
 */
fun subagentMetaLine(agent: SubagentActivity, now: Long): String {
    val parts = mutableListOf<String>()
    agent.model?.takeIf { it.isNotBlank() }?.let { parts += it }
    if (agent.status == SubagentStatus.DISPATCHED) {
        parts += OnyxLocalization["In the background"]
    } else if (agent.status.isLive) {
        parts += elapsedClock(agent.elapsedSeconds(now).toLong())
    } else {
        parts += subagentDuration(agent.elapsedSeconds(now))
    }
    if (agent.toolCount > 0) {
        parts += if (agent.toolCount == 1) OnyxLocalization["1 tool"] else OnyxLocalization.format("%d tools", agent.toolCount)
    }
    return parts.joinToString(" · ")
}

/**
 * « Working with subagents · 1 of 3 · 0:03 » : le titre de la **carte de groupe**, qui dit
 * combien travaillent sur combien, et depuis quand.
 *
 * Le compte des vivants vient des états, jamais d'un chiffre supposé ; la durée manque tant que le
 * groupe n'est pas horodaté.
 */
fun subagentGroupLabel(live: Int, total: Int, seconds: Long?): String {
    val parts = mutableListOf(OnyxLocalization["Working with subagents"])
    if (total > 0) parts += OnyxLocalization.format("%d of %d", live, total)
    seconds?.let { parts += elapsedClock(it) }
    return parts.joinToString(" · ")
}

/** Une durée en horloge : « 0:03 », « 12:05 », « 1:02:03 ». */
fun elapsedClock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val hours = s / 3_600
    val minutes = s % 3_600 / 60
    val rest = s % 60
    val mm = if (hours > 0) minutes.toString().padStart(2, '0') else minutes.toString()
    return (if (hours > 0) "$hours:" else "") + "$mm:" + rest.toString().padStart(2, '0')
}

/**
 * « Étape 3 sur 5 » : l'avancement d'un plan, à côté de sa barre.
 *
 * Le mot de la référence est « Étape » ; le dictionnaire de l'app dit « Cran » ailleurs, on garde
 * donc une clé dédiée pour ne rien changer aux écrans qui portent déjà l'autre mot.
 */
fun planStepLabel(step: Int, total: Int): String =
    OnyxLocalization.format("Étape %1\$d sur %2\$d", step, total)

/**
 * Ce qu'une ligne de statut d'en-tête dit, et de quelle couleur : l'agent travaille (étape du plan,
 * ou nombre de sous-agents), une question attend, ou la conversation est au repos.
 */
fun threadStatusLabel(working: Boolean, subagents: Int, step: Int?, total: Int?): String? = when {
    !working -> null
    step != null && total != null && total > 0 -> {
        OnyxLocalization.format("%1\$s · %2\$s", OnyxLocalization["Working"], planStepLabel(step, total))
    }
    subagents == 1 -> OnyxLocalization.format("%1\$s · %2\$s", OnyxLocalization["Working"], OnyxLocalization["1 subagent"])
    subagents > 1 -> OnyxLocalization.format("%1\$s · %2\$s", OnyxLocalization["Working"], OnyxLocalization.format("%d subagents", subagents))
    else -> OnyxLocalization["Working"]
}

/**
 * Le badge de provenance d'une compétence : « hub » (installée du catalogue), « learned » (écrite
 * par l'agent lui-même), « external » (déposée hors de l'app). `null` quand le serveur ne dit rien.
 */
fun provenanceBadge(provenance: String?): String? = when (provenance?.trim()?.lowercase()) {
    "hub" -> OnyxLocalization["hub"]
    "agent", "learned" -> OnyxLocalization["learned"]
    "external" -> OnyxLocalization["external"]
    else -> null
}

// -------------------------------------------------------------------------------------------
// La puce repliée des outils : ses deux icônes de genre
// -------------------------------------------------------------------------------------------

/** Au-delà de deux genres, la puce ne dit plus rien : le texte porte le compte. */
const val RanIconsMax: Int = 2

/**
 * Un genre d'outil, en petite pastille ronde — la référence en montre jusqu'à deux devant « Ran
 * 3 tools ». Muette : c'est un ornement du libellé qui suit, qui dit déjà tout.
 */
@Composable
fun ToolKindBadge(symbol: Int, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = modifier
            .size(20.dp)
            .background(theme.fill, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        OnyxIcon(symbol = symbol, tint = theme.secondaryText, size = 10.dp)
    }
}

/**
 * La puce repliée d'une réponse : ses icônes de genre puis « Ran 3 tools · 1 s ». L'heure de
 * travail se pose à **droite** de chaque rangée d'outil, pas ici.
 */
@Composable
fun RanToolsChipLabel(tools: List<com.onyx.app.data.model.ToolActivity>, working: Boolean, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
            toolKindSymbols(tools.map { it.name }).forEach { symbol -> ToolKindBadge(symbol) }
        }
        BasicText(
            text = if (working) {
                OnyxLocalization.format("%1\$s · %2\$s", OnyxLocalization["Working"], toolsCountLabel(tools.size))
            } else {
                ranToolsChip(tools.size, toolsElapsedSeconds(tools))
            },
            style = theme.subheadline.copy(color = theme.secondaryText),
        )
    }
}

// -------------------------------------------------------------------------------------------
// L'en-tête : la pastille d'état
// -------------------------------------------------------------------------------------------

/** De quelle couleur se lit une ligne de statut d'en-tête. */
enum class ThreadStatusTone { Busy, Waiting, Ok, Trouble }

/** Ce que la pastille de l'en-tête porte : le texte, et son ton. */
data class ThreadStatus(val text: String, val tone: ThreadStatusTone = ThreadStatusTone.Busy)

/**
 * La pastille d'état de l'en-tête de fil : un point coloré et son anneau doux, puis le texte en
 * petit gris — « Travail · étape 3 sur 5 », « Hermes · homelab ».
 *
 * L'anneau est ce qui fait lire le point comme un **état** et non comme une puce de liste : c'est
 * la même famille que le trait de statut de la référence.
 *
 * **LOT fil-vivant : le point porte la signature de l'état.** Il ne tourne plus pour lui-même : il
 * prend le mouvement de l'état réel du tour ([ThreadStatusState.flow]) — il **respire** en
 * réflexion (2,6 s `EaseInOut`), **pulse lentement** en travail (1,6 s `EaseOut`), **s'immobilise
 * avec une lueur** en attente de décision, et **s'éteint** au repos (la pastille n'existe alors
 * même pas : [threadStatus] rend `null`).
 *
 * **Aucune couleur d'état.** Le point des trois états vivants est **l'accent**, un seul dans toute
 * l'app : c'est le mouvement, et lui seul, qui dit lequel des quatre. Les teintes `Waiting` / `Ok` /
 * `Trouble` de [ThreadStatusTone] restent ce qu'elles étaient — un **avertissement** du fil
 * (serveur muet, message renvoyé), qui n'est justement pas un état du tour.
 *
 * **La lecture avant l'état.** [readingUp] est vrai quand la personne remonte lire : la période est
 * alors multipliée par [FlowGrammar.ReadingUpFactor] — l'état ralentit, il ne s'accélère sous aucun
 * doigt.
 */
@Composable
fun ThreadStatusPill(
    status: ThreadStatus,
    modifier: Modifier = Modifier,
    /** L'état réel du tour, tel que [com.onyx.app.ui.chat.turnFlowState] le déduit. */
    flow: FlowState = FlowState.THINKING,
    /** Vrai quand la personne remonte lire le fil : la signature **ralentit**. */
    readingUp: Boolean = false,
) {
    val theme = LocalOnyxTheme.current
    val reduceMotion = LocalReduceMotion.current
    val tone = when (status.tone) {
        ThreadStatusTone.Busy -> theme.accent
        ThreadStatusTone.Waiting -> theme.warning
        ThreadStatusTone.Ok -> theme.success
        ThreadStatusTone.Trouble -> theme.error
    }
    // Le passage d'un état à l'autre **arrive** au lieu de remplacer : un fondu et une échelle
    // courts ([MotionFamily.FlowShift]), figés sous « Réduire les animations » (la spec vaut alors
    // `snap()` — les états sont posés d'un coup, sans une image intermédiaire).
    val shift = MotionFamily.FlowShift.spec<Float>(reduceMotion)
    AnimatedContent(
        targetState = flow,
        transitionSpec = {
            (
                fadeIn(animationSpec = shift) +
                    scaleIn(animationSpec = shift, initialScale = FlowSignatureEntryScale)
                ) togetherWith (
                fadeOut(animationSpec = shift) +
                    scaleOut(animationSpec = shift, targetScale = FlowSignatureEntryScale)
                )
        },
        label = "flowShift",
        modifier = modifier,
    ) { state ->
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = status.text },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Hair),
        ) {
            StateSignatureDot(
                flow = state,
                tint = tone,
                readingUp = readingUp,
                description = status.text,
            )
            BasicText(
                text = status.text,
                style = theme.caption.copy(color = theme.tertiaryText),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * L'échelle d'entrée d'un état qui arrive : **0,92**. Un état posé sans échelle « saute » ; à
 * 0,92 il se resserre d'un pas (8 %), ce qui se voit sur un point et ne déplace rien.
 */
const val FlowSignatureEntryScale: Float = 0.92f

// -------------------------------------------------------------------------------------------
// L'état du tour
// -------------------------------------------------------------------------------------------

/**
 * La pastille d'état de l'en-tête pour un tour en cours, ou `null` au repos.
 *
 * **Fonction pure** (donc testable) : elle ne pose une pastille que s'il y a quelque chose à dire.
 *
 * LOT fil-vivant : l'état **réel** du tour ([flow], tel que
 * [com.onyx.app.ui.chat.turnFlowState] le déduit) décide. Au repos — ou quand aucun état n'est
 * donné et que rien ne travaille — il n'y a **pas** de pastille : l'en-tête **s'éteint**.
 */
fun threadStatus(working: Boolean, subagents: Int, flow: FlowState? = null): ThreadStatus? {
    val state = flow ?: if (working) FlowState.THINKING else FlowState.REST
    if (state == FlowState.REST) return null
    val text = threadStatusLabel(working = true, subagents = subagents, step = null, total = null)
        ?: return null
    return ThreadStatus(text, ThreadStatusTone.Busy)
}

// -------------------------------------------------------------------------------------------
// La carte d'activité au-dessus du compositeur — RETIRÉE
// -------------------------------------------------------------------------------------------
//
// Ordre du propriétaire : « supprime la barre de réflexion, encadrée en rouge, ça fait doublon ».
// Elle réaffichait au-dessus du champ ce que le fil dit déjà — la réflexion et sa durée (la rangée
// « Réflexion » dépliable, en tête de la réponse, `MessageRow.ThinkingFold`) — **et** un second
// bouton d'arrêt pour le même geste que le carré du compositeur. Le fil est la **seule** source, et
// le compositeur le **seul** arrêt du tour.
//
// `LiveActivityCard`, son titre (`liveActivityTitle`), son détail (`liveActivityDetail`), sa durée
// (`ElapsedClock`) et sa barre d'étapes (`StepBar`) sont partis avec elle : plus rien n'est dessiné
// au-dessus du champ pendant un tour, et la réserve du fil ne mesure plus que le champ.
//
// `TurnStopButton` **reste** : il sert au **groupe de sous-agents** (un arrêt par enfant vivant),
// un geste distinct de l'arrêt du tour — il n'a donc jamais été le doublon à retirer.

/**
 * L'arrêt d'**un groupe de sous-agents** : un carré dans un rond, dans une cible tactile pleine.
 * Il demande l'arrêt de **tous** les enfants vivants (un appel RPC par enfant) — ce n'est pas
 * l'arrêt du tour, qui reste le carré **du compositeur** et lui seul.
 */
@Composable
fun TurnStopButton(onClick: () -> Unit, enabled: Boolean = true) {
    val theme = LocalOnyxTheme.current
    TurnStopTarget(label = resolveOnyxText("Stop the task"), enabled = enabled, onClick = onClick) {
        Box(
            modifier = Modifier.size(30.dp).background(theme.fill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            OnyxIcon(symbol = Symbols.Stop, tint = theme.textColor, size = 12.dp)
        }
    }
}

/** La cible tactile de 44 dp d'un bouton d'arrêt, quel que soit le rond dessiné dedans. */
@Composable
private fun TurnStopTarget(
    /** Ce que le bouton arrête : la cible n'a qu'un carré, elle ne se annonce pas sans nom. */
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .onyxClickable(enabled = enabled, pressedScale = 0.9f, onClick = onClick)
            .tapLabel(label),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * L'arrêt d'**un** sous-agent : un carré dans un rond, dans une cible tactile pleine.
 *
 * Il n'est dessiné que pour un enfant vivant (`SubagentRows`) : un bouton qui ne peut rien arrêter
 * serait un bouton mort.
 */
@Composable
fun SubagentStopButton(onClick: () -> Unit, enabled: Boolean = true) {
    val theme = LocalOnyxTheme.current
    TurnStopTarget(label = resolveOnyxText("Stop this subagent"), enabled = enabled, onClick = onClick) {
        Box(
            modifier = Modifier.size(30.dp).background(theme.fill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            OnyxIcon(symbol = Symbols.Stop, tint = theme.textColor, size = 11.dp)
        }
    }
}

// -------------------------------------------------------------------------------------------
// Les cartes de sous-agents : la carte de groupe
// -------------------------------------------------------------------------------------------

/**
 * La carte de groupe : « Travail avec sous-agents · 1 sur 3 · 0:03 », les points colorés (un par
 * sous-agent), le bouton d'arrêt et le chevron.
 *
 * Le chevron ouvre les cartes individuelles : c'est là que chaque tâche se lit et s'arrête à son
 * tour. Le bouton du groupe, lui, demande l'arrêt de **tous** les enfants encore vivants — un
 * arrêt réel, un appel RPC par enfant.
 *
 * **LOT fil-vivant : c'est une carte de contexte** ([ContextCard]) — le même rayon, le même filet
 * (qui vire à l'accent tant qu'un enfant travaille), l'état à gauche (les points d'équipe), le
 * titre, la durée et l'action à droite. Son repli est **piloté de l'extérieur** : la carte ouvre
 * les cartes de ses enfants, qui vivent après elle.
 */
@Composable
fun SubagentGroupCard(
    agents: List<SubagentActivity>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onStopAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (agents.isEmpty()) return
    val live = agents.count { it.isLive }
    val started = agents.mapNotNull { it.startedAt.takeIf { at -> at > 0 } }.minOrNull()
    // L'horloge du groupe n'avance que tant qu'il a du travail : un groupe fini dit sa durée figée
    // (le maximum des durées connues), jamais un compteur qui continue seul.
    val seconds = if (live > 0 && started != null) {
        (System.currentTimeMillis() - started).coerceAtLeast(0) / 1_000
    } else {
        agents.mapNotNull { it.durationSeconds }.maxOrNull()?.toLong()
    }
    val label = subagentGroupLabel(live, agents.size, seconds)

    ContextCard(
        // Un groupe qui a encore un enfant vivant **vit** (filet d'accent, puce qui travaille) ;
        // un groupe fini est une carte d'archive, immobile.
        state = if (live > 0) ContextCardState.RUNNING else ContextCardState.DONE,
        title = label,
        modifier = modifier.padding(horizontal = OnyxSpace.Snug),
        leading = { TeamDots(agents = agents) },
        action = {
            onStopAll?.takeIf { live > 0 }?.let { stop -> TurnStopButton(onClick = stop) }
        },
        expandState = expanded,
        onExpandChange = { onToggle() },
        foldBody = { },
    )
}

/**
 * Un point par sous-agent, coloré par son état, qui se chevauchent d'un cran — la **carte de
 * groupe** dit d'un coup d'œil combien travaillent et combien ont fini.
 *
 * Au-delà de quatre, le dernier porte « +N » : le titre dit déjà le compte exact, le dessin ne le
 * répète pas mot pour mot. Muet pour les lecteurs d'écran (le titre parle).
 */
@Composable
fun TeamDots(agents: List<SubagentActivity>, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    val shown = if (agents.size > TeamDotsMax) agents.take(TeamDotsMax - 1) else agents
    Row(modifier = modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        shown.forEach { agent ->
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(2.dp, theme.surfaceColor, CircleShape)
                    .padding(2.dp)
                    .background(subagentDotColor(agent.status), CircleShape),
            )
        }
        if (agents.size > TeamDotsMax) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(2.dp, theme.surfaceColor, CircleShape)
                    .padding(2.dp)
                    .background(theme.fill, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "+${agents.size - shown.size}",
                    localize = false,
                    style = theme.caption2.copy(color = theme.secondaryText),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Au-delà de quatre sous-agents, les points se comptent — un dessin seul ne se lit plus. */
const val TeamDotsMax: Int = 4

/** La couleur du point d'un sous-agent, selon son état. */
@Composable
private fun subagentDotColor(status: SubagentStatus): Color {
    val theme = LocalOnyxTheme.current
    return when (status) {
        SubagentStatus.COMPLETED -> theme.success
        SubagentStatus.FAILED -> theme.error
        SubagentStatus.RUNNING -> theme.accent
        SubagentStatus.DISPATCHED -> theme.secondaryText
    }
}
