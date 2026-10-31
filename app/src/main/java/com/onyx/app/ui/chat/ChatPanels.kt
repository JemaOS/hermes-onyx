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
 * Ce que les onglets **Flux** et **Fichiers** d'une conversation montrent.
 *
 * La structure suit celle de la référence — un flux daté, des groupes par catégorie, une ligne de
 * progression, une rangée d'actions — et l'habillage est celui d'Onyx : rayons du thème, barres
 * maison, [LocalOnyxTheme] pour la typographie, la couleur du bot pour l'accent et les étiquettes,
 * les **pastilles de visage** de la maison ([BotLook], jamais un caractère à leur place), tout en
 * français par [OnyxLocalization], et des fondus **figés** sous « Réduire les animations ».
 *
 * Aucune de ces vues n'invente une donnée serveur :
 *
 * - le **Feed** est tiré des réponses de ce bot **dans cette conversation** — une carte par réponse
 *   écrite, datée, avec sa rangée d'actions ;
 * - les **Files** sont les fichiers déjà échangés ici, réunis par le même lecteur que les pastilles
 *   du fil ([DeliveredFiles]), plus ce que la personne a joint ;
 * - les **retour** (pouce haut / pouce bas) n'ont **aucune route serveur** pour les recevoir
 *   (relevé : `404` sur `/api/feed`, `/api/feedback` et leurs variantes `onyx`) : ils sont gardés sur
 *   le téléphone et l'écran le dit ;
 * Les onglets **Idées** et **Objectifs** ont été **supprimés** (demande explicite du propriétaire) :
 * leurs listes locales, leurs catégories maison et leurs panneaux ne sont plus ici. Ce fichier ne
 * garde que le Flux et les Fichiers.
 */

// ---------------------------------------------------------------------------------------------
// Le Feed — les cartes d'un bot, datées
// ---------------------------------------------------------------------------------------------

/**
 * Une carte du fil : un titre court en gras, un résumé d'une ou deux lignes, la réponse qui l'a
 * produite, et son horodatage pour le regroupement par jour.
 */
data class FeedCard(
    /** L'identifiant de la réponse : c'est lui qui porte le retour, donc il ne change pas. */
    val id: String,
    val title: String,
    val text: String,
    /** Le texte entier, pour le détail ouvert par le bouton d'information. */
    val detail: String,
    val atMillis: Long,
)

object ChatFeed {

    /** La longueur du résumé d'une carte. */
    const val Excerpt = 180

    /** La longueur maximale d'un titre. */
    const val TitleLimit = 64

    /** La longueur du texte gardé pour le détail d'une carte. */
    const val DetailLimit = 900

    /**
     * Les cartes que ce bot a produites dans cette conversation : une par réponse écrite, la plus
     * récente en tête. Une réponse sans texte (outil seul, tour interrompu) n'a rien à montrer,
     * donc elle ne fait pas de carte.
     */
    fun cards(messages: List<ChatMessage>, botName: String, limit: Int = 40): List<FeedCard> =
        messages.asSequence()
            .filter { it.role == MessageRole.ASSISTANT && !it.isSteer && it.text.isNotBlank() }
            .toList()
            .takeLast(limit)
            .map { message -> card(message, botName) }
            .reversed()

    /** Une carte, telle que [cards] la compose. */
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
        )
    }

    private fun flatten(text: String): String = text.replace(Regex("\\s+"), " ").trim()

    /**
     * Le titre d'un groupe de cartes : « Aujourd'hui », « Hier », « Cette semaine », « Plus tôt ».
     *
     * Rend une **clé** du dictionnaire, jamais un texte déjà formaté : la date ne se met donc jamais
     * en forme avec la locale de la machine.
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
// Les Files — ce qui a été échangé ici
// ---------------------------------------------------------------------------------------------

object ChatFiles {

    /**
     * Les fichiers échangés dans cette conversation, sans doublon : ce que les réponses ont livré
     * (le même repérage que les pastilles du fil) et ce que la personne a joint.
     */
    fun exchanged(messages: List<ChatMessage>): List<DeliveredFile> =
        conversationFiles(messages).map { file -> DeliveredFile(name = file.name, path = file.path) }

    /**
     * Les mêmes fichiers, avec ce que l'onglet **Fichiers** a besoin de savoir de plus : le **type**
     * annoncé par une pièce jointe, et la **date** du message qui l'a amené — la seule date dont
     * l'app dispose sans interroger le serveur.
     *
     * Un fichier garde sa première apparition : c'est là qu'il est arrivé dans la conversation, et
     * une réponse qui le redonne ne le déplace pas.
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
// Les morceaux communs
// ---------------------------------------------------------------------------------------------

/** La tête d'un onglet : la pastille de visage du bot, son nom, et l'action de composer. */
@Composable
private fun PanelHeader(
    /** Le nom **affiché** (le libellé du bot) : c'est le texte de la tête, jamais la clé du visage. */
    botName: String,
    /**
     * Le **nom de profil** du bot (`ThreadSpeaker.faceName`) : le visage s'y résout. Le libellé,
     * lui, ne le résout pas — « Onyx » (le libellé) donnait un soleil ambre là où « default » (le
     * profil) donne la bulle bleue : le défaut « le même bot a deux visages ».
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

/** Un bouton maison : la pastille du thème, l'encre mesurée sur son propre fond. */
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

/** Le titre d'une section : un libellé, un compte, et une petite étiquette à la couleur du bot. */
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

/** La petite étiquette colorée d'un groupe : le compte, à la couleur du bot. */
@Composable
private fun CountTag(count: Int, accent: Color) {
    val theme = LocalOnyxTheme.current
    // **Le compteur de la maison.** C'est un **nombre** : il prend la géométrie du compteur
    // ([CounterBadge] — rembourrage de 2 dp au-dessus et au-dessous, largeur **mesurée**, carré
    // optique pour un seul chiffre) au lieu d'un rembourrage écrit à la main. Un en-tête qui compte
    // « 1 » ou « 128 » montre donc la même pastille correctement dimensionnée dans les deux cas.
    CounterBadge(
        count = count,
        fill = accent.copy(alpha = 0.16f),
        contentColor = readableInk(accent, theme.pageColor),
    )
}

/**
 * Une source, à gauche d'une ligne : la pastille de visage du bot, ou le symbole de la personne.
 *
 * Le premier paramètre est le **nom de profil** du bot (`ThreadSpeaker.faceName`) — jamais son
 * libellé — pour que la carte du Flux montre exactement le visage que la barre du haut montre. La
 * personne, elle, n'a pas de visage de bot : elle porte le symbole neutre, jamais une créature.
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

/** Le filet d'un groupe : 0,5 dp, la teinte de séparation du thème. */
@Composable
private fun Hairline(modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    Box(modifier = modifier.fillMaxWidth().height(0.5.dp).background(theme.separator))
}

/**
 * Un état vide qui **dit** quelque chose d'utile : ce qui manque, et ce qui va le remplir. Jamais
 * un écran blanc.
 *
 * **Il suit l'alignement de son écran** : les panneaux de la conversation (en-tête, listes, cartes)
 * sont alignés à **gauche** ; un état vide centré y flottait au milieu d'un écran aligné à gauche —
 * le propriétaire l'a relevé (« jamais les deux »). Il prend donc `Alignment.Start`, comme le titre
 * et les rangées du panneau qui le porte.
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
// Le Feed
// ---------------------------------------------------------------------------------------------

/** Le flux daté des cartes d'un bot, avec sa rangée d'actions et le détail de chaque carte. */
@Composable
fun ChatFeedPane(
    cards: List<FeedCard>,
    feedback: Map<String, String>,
    botName: String,
    /** Le nom de **profil** du bot : le visage s'y résout (`ThreadSpeaker.faceName`). */
    faceName: String,
    faceCode: String?,
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
                faceCode = faceCode,
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
                faceCode = faceCode,
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
                FeedCardRow(
                    card = card,
                    choice = feedback[card.id],
                    accent = accent,
                    botName = botName,
                    faceName = faceName,
                    faceCode = faceCode,
                    onFeedback = { value -> onFeedback(card.id, value) },
                    onDiscuss = { onDiscuss(card) },
                )
            }
        }
        // Pas de note de pied : la tête du flux porte **déjà** la même phrase (« Vos pouces sont
        // gardés sur ce téléphone… »), et la répéter en bas du flux la faisait lire deux fois dans
        // le même écran (mesuré à 411 dp sur une conversation d'une carte). Une seule occurrence,
        // là où l'action se trouve : sous la tête, à côté de « Composer ».
    }
}

/** Une carte du Feed : source, titre en gras, résumé, rangée d'actions, et le détail à droite. */
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

        // Le détail : la source, l'heure, et le texte entier de la réponse.
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

/** Un pouce : le choix est dit à l'écran (inversé) et son encre est mesurée sur le fond réel. */
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
