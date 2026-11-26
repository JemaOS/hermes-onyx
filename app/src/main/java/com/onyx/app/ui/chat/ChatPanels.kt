package com.onyx.app.ui.chat

import com.onyx.app.ui.design.OnyxSpace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.ConversationFile
import com.onyx.app.data.model.Role as MessageRole
import com.onyx.app.data.model.serve.DeliveredFile
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.CounterBadge
import com.onyx.app.ui.design.ICON_CONTRAST_MIN
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.readableInk
import com.onyx.app.ui.design.reducedSpec
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import java.time.Instant
import java.time.ZoneId

/**
 * What a conversation's **Feed** and **Files** tabs show.
 *
 * The structure follows the reference — a dated feed, groups by category, a progress
 * line, an action row — and the skin is Onyx's: theme radii, house
 * bars, [LocalOnyxTheme] for typography, the bot colour for the accent and the labels,
 * the house **face badges** ([BotLook], never a character instead), all in
 * English via [OnyxLocalization], and fades **frozen** under "Reduce Motion".
 *
 * None of these views invents server data:
 *
 * - the **Feed** is drawn from this bot's replies **in this conversation** — one card per reply
 *   written, dated, with its action row;
 * - the **Files** are the files already exchanged here, gathered by the same reader as the pills
 *   in the thread ([DeliveredFiles]), plus what the person attached;
 * - the **feedback** (thumbs up / thumbs down) has **no server route** to receive them
 *   (survey: `404` on `/api/feed`, `/api/feedback` and their `onyx` variants): they are kept on
 *   the phone and the screen says so;
 * The **Ideas** and **Goals** tabs were **removed** (explicit owner request):
 * their local lists, house categories and panels are no longer here. This file
 * keeps only the Feed and the Files.
 */

// ---------------------------------------------------------------------------------------------
// The Feed — a bot's cards, dated
// ---------------------------------------------------------------------------------------------

/**
 * A feed card: a short bold title, a one- or two-line summary, the reply that produced
 * it, and its timestamp for grouping by day.
 */
data class FeedCard(
    /** The reply's identifier: it carries the feedback, so it does not change. */
    val id: String,
    val title: String,
    val text: String,
    /** The full text, for the detail opened by the information button. */
    val detail: String,
    val atMillis: Long,
    /**
     * The **profile name** of the bot that wrote this reply — the one the face resolves
     * from (`BotFaceResolver.codeOf`). It is this, not the panel's face, that the card paints: in
     * a group room, each card therefore shows **its own** bot's face, like the thread.
     *
     * Empty when the author is not named (a single-bot conversation, a card forged by a
     * test): the card then falls back to the panel's fallback face.
     */
    val author: String = "",
)

object ChatFeed {

    /** The length of a card's summary. */
    const val Excerpt = 180

    /** The maximum length of a title. */
    const val TitleLimit = 64

    /** The length of the text kept for a card's detail. */
    const val DetailLimit = 900

    /**
     * The cards this bot produced in this conversation: one per written reply, most
     * recent first. A reply with no text (tool only, interrupted turn) has nothing to show,
     * so it makes no card.
     *
     * Each card keeps **its** author (`ChatMessage.authorProfile`): it is the profile name of the
     * bot that replied, so the panel can paint each one's face rather than a single face.
     */
    fun cards(messages: List<ChatMessage>, botName: String, limit: Int = 40): List<FeedCard> =
        messages.asSequence()
            .filter { it.role == MessageRole.ASSISTANT && !it.isSteer && it.text.isNotBlank() }
            .toList()
            .takeLast(limit)
            .map { message -> card(message, botName) }
            .reversed()

    /** A card, as [cards] composes it. */
    fun card(message: ChatMessage, botName: String): FeedCard {
        val flat = flatten(message.text)
        val stop = flat.indexOfFirst { it == '.' || it == '!' || it == '?' || it == ':' }
        val head = if (stop in 0..TitleLimit) flat.take(stop) else flat.take(TitleLimit)
        val title = head.trim().ifBlank { botName }
        val rest = flat.removePrefix(head).trimStart('.', '!', '?', ':', ' ').take(Excerpt)
        return FeedCard(
            id = message.id,
            title = title,
            text = if (rest.isBlank()) flat.take(Excerpt) else rest,
            detail = flat.take(DetailLimit),
            atMillis = message.createdAt,
            // The author is **read** from the message, never guessed: it is the profile the thread
            // already uses for this reply's name and face.
            author = message.authorProfile,
        )
    }

    private fun flatten(text: String): String = text.replace(Regex("\\s+"), " ").trim()

    /**
     * The title of a group of cards: "Today", "Yesterday", "This week", "Earlier".
     *
     * Returns a dictionary **key**, never an already formatted text: the date is therefore never
     * formatted with the machine's locale.
     */
    fun dayLabel(atMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (atMillis <= 0L) return "Earlier"
        val at = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(at, now)
        return when {
            days <= 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7L -> "This week"
            else -> "Earlier"
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The Files — what was exchanged here
// ---------------------------------------------------------------------------------------------

object ChatFiles {

    /**
     * The files exchanged in this conversation, without duplicates: what the replies delivered
     * (the same detection as the thread pills) and what the person attached.
     */
    fun exchanged(messages: List<ChatMessage>): List<DeliveredFile> =
        conversationFiles(messages).map { file -> DeliveredFile(name = file.name, path = file.path) }

    /**
     * The same files, with what the **Files** tab needs to know beyond that: the **type**
     * announced by an attachment, and the **date** of the message that brought it in — the only date
     * the app has without asking the server.
     *
     * A file keeps its first appearance: that is where it arrived in the conversation, and
     * a reply that gives it again does not move it.
     */
    fun conversationFiles(messages: List<ChatMessage>): List<ConversationFile> {
        val out = LinkedHashMap<String, ConversationFile>()
        messages.forEach { message ->
            DeliveredFiles.of(message.tools.map { it.output } + listOf(message.text), limit = 40).forEach { file ->
                out.putIfAbsent(
                    file.path,
                    ConversationFile(
                        name = file.name,
                        path = file.path,
                        appearedAtMillis = message.createdAt,
                    ),
                )
            }
            message.attachments.forEach { attachment ->
                val name = attachment.name?.takeIf { it.isNotBlank() }
                    ?: attachment.uri.substringAfterLast('/').takeIf { it.isNotBlank() }
                    ?: OnyxLocalization["attachment"]
                out.putIfAbsent(
                    attachment.uri,
                    ConversationFile(
                        name = name,
                        path = attachment.uri,
                        mime = attachment.mimeType,
                        appearedAtMillis = message.createdAt,
                    ),
                )
            }
        }
        return out.values.toList()
    }
}

// ---------------------------------------------------------------------------------------------
// The shared pieces
// ---------------------------------------------------------------------------------------------

/** A tab's header: the bot's face badge, its name, and the compose action. */
@Composable
private fun PanelHeader(
    /** The **displayed** name (the bot's label): it is the header text, never the face key. */
    botName: String,
    /**
     * The bot's **profile name** (`ThreadSpeaker.faceName`): the face resolves from it. The label,
     * in contrast, does not resolve it — "Onyx" (the label) gave an amber sun where "default" (the
     * profile) gives the blue bubble: the flaw "the same bot has two faces".
     */
    faceName: String,
    faceCode: String?,
    accent: Color,
    note: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BotLook(name = faceName, faceCode = faceCode, size = 30.dp, blinks = false)
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = botName,
                localize = false,
                style = theme.headline.copy(color = theme.textColor),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = note,
                style = theme.caption.copy(color = theme.secondaryText),
                maxLines = 2,
            )
        }
        PillButton(
            label = actionLabel,
            symbol = Symbols.SquareAndPencil,
            accent = accent,
            filled = true,
            onClick = onAction,
        )
    }
}

/** A house button: the theme's pill, the ink measured against its own background. */
@Composable
private fun PillButton(
    label: String,
    symbol: Int?,
    accent: Color,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val background = if (filled) accent else theme.fill
    val ink = readableInk(if (filled) accent else theme.textColor, background, ICON_CONTRAST_MIN)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = label,
            ) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (symbol != null) {
            Image(
                painter = painterResource(symbol),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ink),
                modifier = Modifier.size(14.dp),
            )
        }
        BasicText(
            text = label,
            localize = false,
            style = theme.caption.copy(color = ink, fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A section title: a label, a count, and a small tag in the bot's colour. */
@Composable
private fun SectionHeader(label: String, count: Int, accent: Color, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(
            text = label,
            style = theme.subheadline.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        CountTag(count = count, accent = accent)
    }
}

/** A group's small coloured tag: the count, in the bot's colour. */
@Composable
private fun CountTag(count: Int, accent: Color) {
    val theme = LocalOnyxTheme.current
    // **The house counter.** It is a **number**: it takes the counter's geometry
    // ([CounterBadge] — 2 dp padding above and below, **measured** width, optical
    // square for a single digit) instead of a hand-written padding. A header that counts
    // "1" or "128" therefore shows the same correctly sized badge in both cases.
    CounterBadge(
        count = count,
        fill = accent.copy(alpha = 0.16f),
        contentColor = readableInk(accent, theme.pageColor),
    )
}

/**
 * A source, on the left of a row: the bot's face badge, or the person's symbol.
 *
 * The first parameter is the bot's **profile name** (`ThreadSpeaker.faceName`) — never its
 * label — so that the Feed card shows exactly the face the top bar shows. The
 * person, for their part, has no bot face: they wear the neutral symbol, never a creature.
 */
@Composable
private fun SourceMark(faceName: String, faceCode: String?, fromBot: Boolean, size: androidx.compose.ui.unit.Dp = 22.dp) {
    val theme = LocalOnyxTheme.current
    if (fromBot) {
        BotLook(name = faceName, faceCode = faceCode, size = size, blinks = false)
    } else {
        Image(
            painter = painterResource(Symbols.Person),
            contentDescription = null,
            colorFilter = ColorFilter.tint(theme.secondaryText),
            modifier = Modifier.size(size - 4.dp),
        )
    }
}

/** A group's hairline: 0.5 dp, the theme's separator tint. */
@Composable
private fun Hairline(modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    Box(modifier = modifier.fillMaxWidth().height(0.5.dp).background(theme.separator))
}

/**
 * An empty state that **says** something useful: what is missing, and what will fill it. Never
 * a blank screen.
 *
 * **It follows its screen's alignment**: the conversation panels (header, lists, cards)
 * are aligned to the **left**; a centred empty state floated in the middle of a left-aligned screen —
 * the owner raised it ("never both"). It therefore takes `Alignment.Start`, like the title
 * and the rows of the panel that carries it.
 */
@Composable
private fun PanelEmpty(title: String, detail: String?, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(
            text = title,
            style = theme.subheadline.copy(color = theme.textColor),
        )
        if (detail != null) {
            BasicText(
                text = detail,
                style = theme.caption.copy(color = theme.secondaryText),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The Feed
// ---------------------------------------------------------------------------------------------

/**
 * A bot's dated feed of cards, with its action row and each card's detail.
 *
 * **The face is resolved card by card.** The panel no longer receives a single face: it receives
 * a resolver ([face], the screen's `BotFaceResolver.codeOf`) and the conversation's **fallback
 * face** ([fallbackFaceCode]). Each card paints `face(card.author)` — the bot that replied,
 * the one the thread names — and falls back when the card has no named author. A group
 * room therefore showed the room's face on **all** its cards, where the thread showed
 * each bot's: that is the flaw this wiring removes.
 */
@Composable
fun ChatFeedPane(
    cards: List<FeedCard>,
    feedback: Map<String, String>,
    /** The conversation's **displayed** name (the bot's label, or the room's name). */
    botName: String,
    /**
     * The tab header's **profile name**: a card reads its own from `card.author`, but the
     * header has no card to read — it therefore keeps the conversation's profile (the room's
     * name for a room). It is the key the top bar also uses, never the label.
     */
    faceName: String,
    /**
     * The face of a **profile**, card by card: the screen's resolver
     * (`BotFaceResolver.codeOf`, always `face = { name -> face(name) }` at the caller).
     * `null` when nothing fixes this profile — the fallback then decides.
     */
    face: (String) -> String? = { null },
    /** **The fallback face**: the conversation's (the room's), when a card has no author. */
    fallbackFaceCode: String? = null,
    accent: Color,
    nowMillis: Long,
    onFeedback: (cardId: String, value: String) -> Unit,
    onDiscuss: (FeedCard) -> Unit,
    onCompose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    if (cards.isEmpty()) {
        Column(modifier = modifier.fillMaxSize()) {
            PanelHeader(
                botName = botName,
                faceName = faceName,
                faceCode = face(faceName) ?: fallbackFaceCode,
                accent = accent,
                note = OnyxLocalization["Your thumbs are kept on this phone, per bot and per card."],
                actionLabel = OnyxLocalization["Compose"],
                onAction = onCompose,
            )
            PanelEmpty(
                title = OnyxLocalization.format("No cards from %1\$s yet. Its answers appear here.", botName),
                detail = OnyxLocalization["Write the first question to fill this feed."],
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "feed-head") {
            PanelHeader(
                botName = botName,
                faceName = faceName,
                faceCode = face(faceName) ?: fallbackFaceCode,
                accent = accent,
                note = OnyxLocalization["Your thumbs are kept on this phone, per bot and per card."],
                actionLabel = OnyxLocalization["Compose"],
                onAction = onCompose,
            )
        }
        var lastLabel = ""
        cards.forEach { card ->
            val label = ChatFeed.dayLabel(card.atMillis, nowMillis)
            if (label != lastLabel) {
                val shown = label
                lastLabel = shown
                item(key = "feed-day-$shown") {
                    SectionHeader(
                        label = OnyxLocalization[shown],
                        count = cards.count { ChatFeed.dayLabel(it.atMillis, nowMillis) == shown },
                        accent = accent,
                    )
                }
            }
            item(key = card.id) {
                // **THIS card's face**, resolved from its author — the bot that replied —,
                // never a single face for the whole feed. The name comes from the same author; a
                // card without an author keeps the conversation's profile and the fallback face.
                val cardFace = face(card.author) ?: fallbackFaceCode
                val cardName = card.author.ifBlank { faceName }
                FeedCardRow(
                    card = card,
                    choice = feedback[card.id],
                    accent = accent,
                    botName = botName,
                    faceName = cardName,
                    faceCode = cardFace,
                    onFeedback = { value -> onFeedback(card.id, value) },
                    onDiscuss = { onDiscuss(card) },
                )
            }
        }
        // No footer note: the feed header **already** carries the same sentence ("Your thumbs are
        // kept on this phone…"), and repeating it at the bottom of the feed made it read twice on
        // the same screen (measured at 411 dp on a one-card conversation). A single occurrence,
        // where the action is: below the header, next to "Compose".
    }
}

/**
 * A Feed card: source, bold title, summary, action row, and the detail on the right.
 *
 * Its source badge is **its author's face** (`faceName` + `faceCode`, resolved by the
 * panel from `card.author`): in a room, two cards from two bots therefore do not show the
 * same face.
 */
@Composable
private fun FeedCardRow(
    card: FeedCard,
    choice: String?,
    accent: Color,
    botName: String,
    faceName: String,
    faceCode: String?,
    onFeedback: (String) -> Unit,
    onDiscuss: (FeedCard) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    var showDetail by remember(card.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(OnyxSpace.TileCorner))
            .background(theme.surfaceColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            SourceMark(faceName = faceName, faceCode = faceCode, fromBot = true)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                BasicText(
                    text = card.title,
                    localize = false,
                    style = theme.headline.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText(
                    text = card.text,
                    localize = false,
                    style = theme.subheadline.copy(color = theme.secondaryOnCard),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = OnyxLocalization["Source and detail"],
                    ) { showDetail = !showDetail }
                    .padding(4.dp),
            ) {
                Image(
                    painter = painterResource(Symbols.InfoCircle),
                    contentDescription = OnyxLocalization["Source and detail"],
                    colorFilter = ColorFilter.tint(readableInk(theme.secondaryText, theme.surfaceColor, ICON_CONTRAST_MIN)),
                    modifier = Modifier.size(17.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ThumbAction(
                symbol = Symbols.HandThumbsUp,
                label = OnyxLocalization["Thumb up"],
                selected = choice == "up",
                accent = accent,
                onClick = { onFeedback("up") },
            )
            ThumbAction(
                symbol = Symbols.HandThumbsDown,
                label = OnyxLocalization["Thumb down"],
                selected = choice == "down",
                accent = accent,
                onClick = { onFeedback("down") },
            )
            PillButton(
                label = OnyxLocalization["Discuss"],
                symbol = Symbols.TextBubble,
                accent = accent,
                filled = false,
                onClick = { onDiscuss(card) },
            )
        }

        // The detail: the source, the time, and the reply's full text.
        AnimatedVisibility(
            visible = showDetail,
            enter = fadeIn(animationSpec = reducedSpec(OnyxMotion.Sites.MessageFold)),
            exit = fadeOut(animationSpec = reducedSpec(OnyxMotion.Sites.MessageFold)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Hairline(modifier = Modifier.padding(vertical = 4.dp))
                BasicText(
                    text = OnyxLocalization.format("Source: %1\$s", botName),
                    localize = false,
                    style = theme.caption.copy(color = theme.secondaryOnCard),
                )
                BasicText(
                    text = card.detail,
                    localize = false,
                    style = theme.footnote.copy(color = theme.textColor),
                )
            }
        }
    }
}

/** A thumb: the choice is said on screen (inverted) and its ink is measured against the real background. */
@Composable
private fun ThumbAction(
    symbol: Int,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val background = if (selected) accent.copy(alpha = 0.18f) else theme.fill
    val ink = readableInk(if (selected) accent else theme.secondaryText, background, ICON_CONTRAST_MIN)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = label,
            ) { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Image(
            painter = painterResource(symbol),
            contentDescription = label,
            colorFilter = ColorFilter.tint(ink),
            modifier = Modifier.size(15.dp),
        )
        if (selected) {
            BasicText(
                text = label,
                localize = false,
                style = theme.caption.copy(color = readableInk(theme.textColor, background)),
                maxLines = 1,
            )
        }
    }
}
