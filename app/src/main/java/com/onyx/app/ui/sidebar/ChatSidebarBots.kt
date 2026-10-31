package com.onyx.app.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.BotAvatar
import com.onyx.app.ui.design.SectionAction
import com.onyx.app.ui.design.SectionActionButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.onyx.app.ui.design.OnyxSocle
import com.onyx.app.ui.design.socleShadow
import com.onyx.app.ui.design.socleTitleStyle
import com.onyx.app.ui.design.socleMetaStyle
import com.onyx.app.ui.design.socleMonoStyle
import com.onyx.app.ui.design.socleSectionStyle
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.LucideMenuIcons
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.sidebarPaneColor
import com.onyx.app.ui.design.sidebarRowSurface
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.design.tapLabel

/**
 * La position **Bots** du volet.
 *
 * C'est ici, et **nulle part dans la liste de conversations**, que vivent les bots : leurs cartes
 * sur deux colonnes (visage, nom, description courte, âge, point d'état), puis la section *Salons*
 * avec son bouton, en lignes — deux visages entrelacés, le nom, les participants, l'âge.
 *
 * La grille passe à **une colonne** quand la police est à 1,3× ou que le volet est trop étroit : à
 * deux colonnes, un nom de bot et sa description ne tiennent plus, et la carte se lit en escalier.
 * C'est mesuré ([botGridColumns]), pas deviné.
 */
@Composable
internal fun BotsTab(
    bots: List<SidebarBotCard>,
    onOpenBot: (SidebarBotCard) -> Unit,
    onNewBot: () -> Unit,
    rooms: List<SidebarRoomRow>,
    onOpenRoom: (SidebarRoomRow) -> Unit,
    onNewRoom: (() -> Unit)?,
    faceCodeOf: (String) -> String?,
    /**
     * L'appui long d'une carte : son **menu** (« Pin to top », « Hide », « Open recent session »…),
     * ouvert près d'elle. C'est le geste de la référence sur une carte de bot.
     */
    onMenuBot: ((SidebarBotCard, Rect) -> Unit)? = null,
    /** Un mot sur ce qui vient d'échouer (dupliquer, repartir à zéro, supprimer), ou `null`. */
    notice: String? = null,
    onDismissNotice: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Deux colonnes dès qu'elles tiennent, une seule à 1,3× de police ou dans un volet étroit.
        val columns = botGridColumns(maxWidth.value, fontScale)
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                // La **colonne d'alignement** du volet : 4 dp de marge de grille + 12 dp de
                // rembourrage de rangée posent le visage d'un salon à 16 dp du bord du panneau —
                // là où commence le texte de la tête et l'icône d'une tuile du côté *Chats*.
                start = OnyxSpace.Hair,
                end = OnyxSpace.Hair,
                bottom = OnyxSpace.BlockGap,
            ),
        ) {
        item(key = "bots-header") {
            SectionHeader(title = "Bots", top = OnyxSpace.Hair) {
                SectionActionButton(
                    symbol = Symbols.Plus,
                    label = "New bot",
                    onClick = onNewBot,
                )
            }
        }
        if (bots.isEmpty()) {
            item(key = "bots-empty") {
                BasicText(
                    text = "No bots on this gateway yet.",
                    style = LocalOnyxTheme.current.footnote.copy(color = mutedInk(LocalOnyxTheme.current.pageColor)),
                    modifier = Modifier.padding(horizontal = OnyxSpace.Half, vertical = OnyxSpace.Half),
                )
            }
        }
        // Ce qui vient d'échouer (dupliquer, repartir à zéro, supprimer) : une ligne, pas un silence
        // — une action qui échoue sans le dire se lit comme une action qui n'a rien fait.
        if (!notice.isNullOrBlank()) {
            item(key = "bots-notice") {
                BasicText(
                    text = notice,
                    localize = false,
                    style = LocalOnyxTheme.current.footnote.copy(color = mutedInk(LocalOnyxTheme.current.pageColor)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onyxTappable(onClick = { onDismissNotice?.invoke() })
                        // La zone **porte son nom** : c'est le texte de l'avis lui-même, que la
                        // rangée efface d'un appui.
                        .tapLabel(notice)
                        .padding(horizontal = OnyxSpace.Half, vertical = OnyxSpace.Half),
                )
            }
        }
        items(bots.chunked(columns), key = { "row:${it.first().name}" }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = BotsGrid.GapDp.dp),
                horizontalArrangement = Arrangement.spacedBy(BotsGrid.GapDp.dp),
            ) {
                row.forEach { bot ->
                    BotCard(
                        bot = bot,
                        faceCode = faceCodeOf(bot.name),
                        onClick = { onOpenBot(bot) },
                        // L'appui long ouvre le menu de la carte, comme sur une rangée : la carte
                        // passe ses limites, le panneau s'ouvre près d'elle.
                        onLongPress = onMenuBot?.let { menu -> { rect -> menu(bot, rect) } },
                        // La carte porte **sa** hauteur ([BotsGrid] la calcule) : toutes les cartes
                        // d'une même rangée mesurent donc exactement la même chose, avec ou sans
                        // description. `fillMaxHeight` ne le faisait pas — la rangée se réglait sur
                        // sa carte la plus haute, et les suivantes ne s'alignaient plus.
                        modifier = Modifier.weight(1f),
                    )
                }
                // Une rangée impaire garde la moitié de sa largeur.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        if (rooms.isNotEmpty() || onNewRoom != null) {
            item(key = "rooms-header") {
                SectionHeader(title = "Rooms", top = OnyxSpace.Snug) {
                    if (onNewRoom != null) {
                        SectionActionButton(
                            symbol = Symbols.Plus,
                            label = "New room",
                            onClick = onNewRoom,
                        )
                    }
                }
            }
            if (rooms.isEmpty()) {
                item(key = "rooms-empty") {
                    BasicText(
                        text = "No rooms yet.",
                        style = LocalOnyxTheme.current.footnote.copy(color = mutedInk(LocalOnyxTheme.current.pageColor)),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
            items(rooms, key = { "room:${it.id}" }) { room ->
                RoomRow(row = room, faceCodeOf = faceCodeOf, onClick = { onOpenRoom(room) })
            }
        }
        }
    }
}

/**
 * Combien de colonnes de cartes de bots pour cette largeur et cette police.
 *
 * Deux colonnes dès qu'elles tiennent : chaque carte garde [BOT_CARD_MIN] de large (le visage, le
 * nom et une ligne de description). En dessous — un volet étroit, ou une police à 1,3× —, une
 * seule colonne : la carte s'étire plutôt que d'écraser son texte.
 */
internal fun botGridColumns(
    availableWidthDp: Float,
    fontScale: Float = 1f,
): Int = if (availableWidthDp < BOT_CARD_MIN * 2 + 8f || fontScale >= 1.3f) 1 else 2

/** La largeur minimale d'une carte de bot : en dessous, deux colonnes ne tiennent plus. */
private const val BOT_CARD_MIN = 148f

/**
 * L'échelle d'espacement **de la grille des bots** : les pas de la maison, nommés, pour que les
 * écarts des colonnes et des rangées sortent d'une règle et non d'une valeur choisie sur place.
 *
 * L'échelle globale du projet est normalisée par un autre lot ; ces noms en reprennent les pas
 * (4 · 8 · 12) pour qu'on puisse les y brancher sans toucher à la disposition.
 */
internal object BotsGrid {
    /** Le pas de base de l'échelle, en dp. */
    const val StepDp = 4

    /** L'écart entre deux colonnes **et** entre deux rangées : deux pas. */
    const val GapDp = StepDp * 2

    /**
     * Le pas de référence de la grille pour l'intérieur d'une carte : trois pas (`OnyxSpace.Snug`).
     *
     * La carte des bots, elle, prend le **rembourrage de carte de la maison**
     * ([OnyxSpace.CardPadding], 16 dp) : c'est la valeur du lot de finition — la carte des bots
     * était alors la seule carte de l'app à 12 dp, son texte paraissait collé à ses bords — et
     * c'est ce même jeton qui entre dans [botCardHeight]. La grille garde ce pas comme la référence
     * de ses **écarts** ([GapDp]) ; l'intérieur de la carte est l'affaire de l'échelle du volet.
     */
    const val CardPadDp = StepDp * 3

    /**
     * Le rayon d'une carte — **celui de la planche**. Le générateur donne `R_CARD = 20` à la
     * carte (`hlib.py:330`, `R_CARD, R_TILE, R_PLAQUE = 20, 14, 28`) et les cartes de bot sont
     * bâties sur `.card` (`hlib.py:557`, `box(…, R_CARD, 'solid botc', …)`) : c'est le rayon
     * `RadiusCard` du socle (`OnyxSocle.RadiusCard`), pas un 14 de tuile.
     */
    const val CardRadiusDp = 20

    /**
     * Le visage : **une** taille, la même sur toutes les cartes. **48 dp**, comme la planche
     * (`.botface{width:48px;height:48px;border-radius:16px}`, `hcss.py:161`).
     */
    const val FaceDp = 48

    /**
     * Le point d'état, au coin haut-droit du visage. **8 dp**, le carré arrondi de la planche
     * (`.bot .pdot{width:8px;height:8px;border-radius:4px;background:var(--mut2)}`, `hcss.py:164`).
     */
    const val DotDp = 8

    /** La place réservée à la description : **deux** lignes, avec ou sans texte. */
    const val DescriptionLines = 2
}

/** La hauteur de texte d'un style, en dp, à la police de l'appareil. */
private fun lineHeightDp(style: TextStyle, density: Density): Dp {
    val unit = when {
        style.lineHeight.isSp -> style.lineHeight
        style.fontSize.isSp -> style.fontSize * 1.2f
        else -> return 0.dp
    }
    return with(density) { unit.toDp() }
}

/**
 * La hauteur d'une carte de bot — **la même pour toutes**, et calculée à partir des hauteurs de
 * ligne réelles des styles du thème, donc elle suit la police au lieu de la contrarier.
 *
 * C'est elle qui rend les rangées régulières : une carte sans description réserve la place de
 * [lines] lignes de légende, exactement comme une carte qui en a une. La grille ne se casse plus.
 */
internal fun botCardHeight(
    pad: Dp,
    gap: Dp,
    face: Dp,
    nameLine: Dp,
    captionLine: Dp,
    ageLine: Dp,
    lines: Int = BotsGrid.DescriptionLines,
): Dp = pad * 2 + face + gap + nameLine + gap + captionLine * lines + gap + ageLine

/**
 * Le plus long préfixe **de mots** de [text] qui, suivi d'un « … », tient encore — selon [fits].
 *
 * Pur : c'est [fits] qui mesure, ce qui rend la coupure vérifiable sans appareil. Un dernier mot
 * n'est jamais tranché au milieu ; s'il ne tient pas même seul, il est rendu tel quel et c'est le
 * texte qui l'élide, faute de mieux.
 */
internal fun ellipsizeAtWord(text: String, maxLines: Int, fits: (String) -> Boolean): String {
    if (maxLines <= 0) return ""
    val trimmed = text.trim()
    if (trimmed.isEmpty() || fits(trimmed)) return trimmed
    val words = trimmed.split(Regex("\\s+"))
    var low = 0
    var high = words.size
    while (low < high) {
        val mid = (low + high + 1) / 2
        val candidate = words.take(mid).joinToString(" ") + "…"
        if (fits(candidate)) low = mid else high = mid - 1
    }
    return if (low <= 0) words.first() else words.take(low).joinToString(" ") + "…"
}

/** [ellipsizeAtWord], mesuré par un vrai [TextMeasurer] dans [widthPx] pixels. */
internal fun TextMeasurer.ellipsizeAtWord(
    text: String,
    maxLines: Int,
    style: TextStyle,
    widthPx: Int,
): String = ellipsizeAtWord(text, maxLines) { candidate ->
    measure(
        text = AnnotatedString(candidate),
        style = style,
        softWrap = true,
        constraints = Constraints(maxWidth = widthPx.coerceAtLeast(0)),
    ).lineCount <= maxLines
}

/** Une carte de bot : visage, point d'état, nom, description courte et âge. */
@Composable
private fun BotCard(
    bot: SidebarBotCard,
    faceCode: String?,
    onClick: () -> Unit,
    /** L'appui long : le menu de la carte. La carte passe ses **limites** (le panneau s'y ancre). */
    onLongPress: ((Rect) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(BotsGrid.CardRadiusDp.dp)
    val gap = BotsGrid.GapDp.dp
    val faceSize = BotsGrid.FaceDp.dp
    // Le rembourrage **intérieur de la carte** est celui de la maison ([OnyxSpace.CardPadding]) :
    // c'est l'espacement du lot de finition, et c'est lui qui entre dans le calcul de la hauteur.
    val pad = OnyxSpace.CardPadding
    // **Le fond réel de la carte.** La planche pose la carte de bot sur `.solid`
    // (`hlib.py:557`, `box(…, R_CARD, 'solid botc', …)`) : la surface de carte de la maison, celle
    // des blocs du socle ([SocleCard]) — blanche au clair, la surface du thème en sombre. C'est la
    // même fonction sur la carte et sur les encres qu'elle porte, donc les deux ne peuvent pas
    // diverger. (Avant : la vitre au sombre et le gris des creux au clair — la planche dit `.solid`.)
    val cardGround = theme.formSurfaceColor

    val nameStyle = socleTitleStyle(theme).copy(color = theme.textColor)
    val captionStyle = socleMetaStyle(theme).copy(color = mutedInk(cardGround))
    // L'âge : la donnée chiffrée, en monospace, au plancher de la planche (`.botage`, 10 px,
    // `hcss.py:170`) — comme la durée d'une carte de conversation.
    val ageStyle = socleMonoStyle(theme).copy(fontSize = OnyxSocle.Micro, color = mutedInk(cardGround))
    val nameLine = lineHeightDp(nameStyle, density)
    val captionLine = lineHeightDp(captionStyle, density)
    val ageLine = lineHeightDp(ageStyle, density)
    val cardHeight = botCardHeight(pad, gap, faceSize, nameLine, captionLine, ageLine)
    val botCardLabel = listOfNotNull(
        // La marque d'épingle est **dans le nom** de la carte : l'icône reste alors décorative
        // (`contentDescription = null`), et le lecteur d'écran dit la même chose que l'écran.
        if (bot.pinned) OnyxLocalization["Pinned to top"] else null,
        bot.label,
        bot.description,
        bot.ageLabel,
    )
        .filter { it.isNotBlank() }
        .joinToString(", ")
    // Où la carte est : son menu s'ouvre **contre elle**, comme celui d'une rangée.
    var bounds by remember { mutableStateOf<Rect?>(null) }

    Column(
        modifier = modifier
            // **La carte de bot est une CARTE, pas une vitre.** La planche la pose sur `.solid`
            // (`hlib.py:557-563`) : surface pleine, rayon 20, ombre `.sh1` — la même famille que
            // la carte de conversation et la carte de fichier. Elle était auparavant une vitre
            // (`sidebarTileSurface`) : sur un volet où rien ne passe derrière, la vitre n'ajoutait
            // qu'un voile et un liseré, et les trois cartes de coin ne se lisaient pas comme une
            // seule famille.
            //
            // Sa hauteur, elle, est **calculée** ([botCardHeight]) : le visage, les écarts de la
            // grille ([BotsGrid]) et les hauteurs de ligne réelles des styles du thème y entrent —
            // jamais le contenu. Le rembourrage, lui, est le jeton de la maison
            // (`OnyxSpace.CardPadding`, 16 dp) : c'est l'espacement du lot de finition, et c'est ce
            // même jeton qui alimente le calcul — la carte et sa hauteur ne peuvent pas diverger.
            // La grille garde ses propres pas pour les **écarts** (colonnes, rangées), pas pour
            // l'intérieur de la carte.
            .height(cardHeight)
            // **L'ombre de la planche, POSÉE pour de vrai.** `.sh1` du jeu corrigé
            // (`hcss.py:27`) : deux couches **dessinées** par `socleShadow` — c'est ce qui
            // distingue une ombre déclarée d'une ombre rendue, et ce qui ramène la fonction dans
            // le binaire (sans appelant, R8 la retire et la carte reste plate). `ShadowNear` est
            // la profondeur d'un contrôle serré, celle de la carte de bot (`hlib.py:563`,
            // `sh='sh1'`).
            .socleShadow(OnyxSocle.ShadowNear, OnyxSocle.RadiusCard, theme.dark)
            .clip(shape)
            .background(cardGround)
            .onyxTappable(
                onClick = onClick,
                // L'appui long ouvre le menu de la carte — le geste de la référence sur une carte
                // de bot ; sans limites mesurées, il ne s'arme pas (le panneau n'aurait pas d'ancre).
                onLongPress = onLongPress?.let { press -> { bounds?.let(press) } },
            )
            // **L'effet d'appui est celui de la carte, et il n'y en a qu'un** : `onyxTappable`
            // enfonce et relâche la surface d'un geste — doubler avec l'échelle d'`onyxClickable`
            // aurait donné deux enfoncements pour un doigt.
            // La carte est **un seul contrôle** : son nom, sa description et son âge tiennent dans
            // un libellé. Relevé d'arbre : la cellule cliquable était sans nom.
            // Le nom de la zone vient **avant** le modificateur à bloc final : le verrou du volet
            // lit la chaîne de modificateurs et s'arrête à un `.onGloballyPositioned { … }` — un
            // libellé posé après lui serait invisible à ses yeux.
            .tapLabel(botCardLabel)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .padding(OnyxSpace.CardPadding),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        // Le visage et le point : le point est **au coin haut-droit du visage**, sa règle — la
        // même sur chaque carte, et non une place qui suit la hauteur du texte.
        Row(
            modifier = Modifier.fillMaxWidth().height(faceSize),
            verticalAlignment = Alignment.Top,
        ) {
            BotAvatar(name = bot.name, faceCode = faceCode, size = faceSize, blinks = false)
            Spacer(Modifier.weight(1f))
            BotStatusDot(tone = bot.tone, unread = bot.unread, size = BotsGrid.DotDp.dp)
        }
        // Le nom, avec la **marque d'épingle** devant quand le bot l'est : la même icône que
        // l'entrée « Pin to top » du menu, à la même place que dans la référence (`BotsRoster.kt:489`).
        Row(
            modifier = Modifier.fillMaxWidth().height(nameLine),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (bot.pinned) {
                OnyxIcon(
                    symbol = LucideMenuIcons.Pin,
                    tint = mutedInk(cardGround),
                    size = 12.dp,
                    // L'épingle est **décorative** : la carte porte « Pinned to top » dans son nom
                    // ([botCardLabel]), et un contrôle qui redit son libellé s'annonce deux fois.
                    contentDescription = null,
                )
            }
            BasicText(
                text = bot.label,
                localize = false,
                style = nameStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        // La description : **toujours** la place de deux lignes, coupée au mot. Une carte sans
        // description garde donc exactement la hauteur des autres.
        BotDescription(
            text = bot.description,
            style = captionStyle,
            lines = BotsGrid.DescriptionLines,
            height = captionLine * BotsGrid.DescriptionLines,
        )
        // L'âge : à droite, sur sa propre ligne — la même pour toutes les cartes.
        Row(
            modifier = Modifier.fillMaxWidth().height(ageLine),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val age = bot.ageLabel
            if (!age.isNullOrBlank()) {
                BasicText(text = age, localize = false, style = ageStyle, maxLines = 1)
            }
        }
    }
}

/**
 * La description d'un bot, **coupée à la frontière d'un mot** sur [lines] lignes.
 *
 * Ce n'est pas l'ellipse de Compose, qui tranche au caractère : ici on mesure des candidats et on
 * garde le plus long préfixe de mots qui tienne encore, suivi du « … ». La place réservée est
 * **fixe**, donc une carte sans description a la hauteur d'une carte qui en a une.
 */
@Composable
private fun BotDescription(
    text: String?,
    style: TextStyle,
    lines: Int,
    height: Dp,
) {
    val trimmed = text?.trim().orEmpty()
    val frame = Modifier.fillMaxWidth().height(height)
    if (trimmed.isEmpty()) {
        Spacer(frame)
        return
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = frame) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        val shown = measurer.ellipsizeAtWord(trimmed, lines, style, widthPx)
        BasicText(
            text = shown,
            localize = false,
            style = style,
            maxLines = lines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Le point d'état d'une carte : l'accent quand il travaille, l'ambre quand il attend. */
@Composable
private fun BotStatusDot(tone: SidebarRowTone, unread: Boolean, size: Dp) {
    val theme = LocalOnyxTheme.current
    // Le fond réel de la carte : **la même fonction que la carte elle-même**
    // ([ResolvedTheme.formSurfaceColor]) : le point est mesuré contre la surface qu'il touche
    // vraiment.
    val ground = theme.formSurfaceColor
    val (tint, ringAlpha) = when {
        tone == SidebarRowTone.WAITING -> theme.warning to 0.20f
        tone == SidebarRowTone.RUNNING -> theme.accent to 0.20f
        unread -> theme.accent to 0f
        else -> theme.tertiaryText to 0f
    }
    val ring = if (ringAlpha == 0f) {
        androidx.compose.ui.graphics.Color.Transparent
    } else {
        OnyxContrast.over(tint.copy(alpha = ringAlpha), ground)
    }
    val fill = sidebarInk(tint, if (ringAlpha == 0f) ground else ring, OnyxContrast.ICON_MIN)
    Box(
        modifier = Modifier.size(size).background(ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size * (8f / BotsGrid.DotDp)).background(fill, CircleShape))
    }
}

/**
 * Une ligne de salon : deux visages entrelacés, le nom, les participants, et l'âge à droite.
 */
@Composable
private fun RoomRow(row: SidebarRoomRow, faceCodeOf: (String) -> String?, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    val shape = RoundedCornerShape(OnyxSpace.FormCorner)
    // Le fond réel d'une rangée de salon : la vitre en sombre, le **panneau** en clair — au clair,
    // une rangée de liste n'est pas une surface, c'est du texte et des visages sur le panneau.
    val ground = if (theme.dark) theme.surfaceColor else sidebarPaneColor(theme)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = OnyxSpace.Hair)
            .defaultMinSize(minHeight = OnyxSpace.RowHeight)
            // Un salon est une **rangée de liste** : la même surface que les conversations, avec son
            // écart de rangée (`OnyxSpace.Hair`) et son écart icône-libellé (`BlockGap`).
            .sidebarRowSurface(shape)
            .onyxTappable(onClick = onClick)
            .tapLabel(listOfNotNull(row.name.takeIf { it.isNotBlank() }, row.members.joinToString(", ").takeIf { it.isNotBlank() }).joinToString(", "))
            .padding(horizontal = OnyxSpace.Snug, vertical = OnyxSpace.BlockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.BlockGap),
    ) {
        // Les visages se résolvent sur les **noms de profil** (`memberProfiles`), pas sur les
        // libellés du texte : le membre principal montrait sinon `forName("Onyx")` (un soleil ambre)
        // là où son profil donne la bulle bleue de Onyx.
        InterlacedFaces(names = row.memberProfiles, faceCodeOf = faceCodeOf)
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = row.name,
                localize = false,
                style = theme.subheadline.copy(
                    color = theme.textColor,
                    fontWeight = if (row.unread) FontWeight.SemiBold else FontWeight.Medium,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val members = row.members.joinToString(", ")
            if (members.isNotBlank()) {
                BasicText(
                    text = members,
                    localize = false,
                    style = theme.caption.copy(color = mutedInk(ground)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val age = row.ageLabel
        if (!age.isNullOrBlank()) {
            BasicText(
                text = age,
                localize = false,
                style = theme.caption.copy(color = mutedInk(ground)),
                maxLines = 1,
            )
        }
    }
}

/**
 * Deux visages **entrelacés** : les deux premiers membres, le second chevauchant le premier et
 * détaché du fond par un anneau de la couleur de la carte. Un salon d'un seul membre montre un
 * visage ; sans membre, une pastille muette — jamais un trou.
 */
@Composable
private fun InterlacedFaces(
    names: List<String>,
    faceCodeOf: (String) -> String?,
    size: Dp = 26.dp,
) {
    val theme = LocalOnyxTheme.current
    val shown = names.take(2)
    if (shown.isEmpty()) {
        Box(
            modifier = Modifier.size(size * 1.6f, size).background(theme.fill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            OnyxIcon(symbol = Symbols.Bubbles, tint = sidebarInk(theme.tertiaryText, theme.fill, OnyxContrast.ICON_MIN), size = 13.dp)
        }
        return
    }
    Box(modifier = Modifier.width(size + (size * 0.5f) * (shown.size - 1)).height(size)) {
        shown.forEachIndexed { index, name ->
            Box(
                modifier = Modifier
                    .offset(x = size * 0.5f * index)
                    .size(size)
                    .background(theme.surfaceColor, CircleShape)
                    .padding(1.5.dp),
                contentAlignment = Alignment.Center,
            ) {
                BotAvatar(name = name, faceCode = faceCodeOf(name), size = size - 3.dp, blinks = false)
            }
        }
    }
}

/** L'en-tête d'une section : ses petites capitales, et son action (un « + ») à l'autre bout. */
@Composable
private fun SectionHeader(title: String, top: Dp, action: @Composable () -> Unit) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // La ligne fait **exactement** la hauteur de l'action ([SectionAction.Size]) : le rond
            // n'ajoute aucune hauteur à l'en-tête, et l'écart sous lui est donc le rembourrage bas,
            // à la valeur près ([SectionAction.gap]).
            .defaultMinSize(minHeight = SectionAction.Size)
            // La **colonne d'alignement** : le titre d'une section commence à la **marge du
            // contenu** (16 dp), et non sur une marge à lui. Le « + » est dans la **même ligne**
            // (ce `Row`, centré verticalement) : il partage donc la ligne du libellé au lieu de
            // tomber sous lui et de toucher la carte (voir AUDIT-GEOMETRIE, 6,5 px).
            //
            // **L'écart sous l'action.** Sans lui, l'en-tête finissait sur le bas du bouton et la
            // première carte (BOTS) ou la première ligne de salon (SALONS) commençait sur le pixel
            // suivant : le « + » touchait le contenu, et l'horodatage d'un salon passait juste sous
            // lui. La maison met [SectionAction.Gap] entre un intitulé et ce qu'il annonce — le
            // même pas que le bas de l'intitulé « CONVERSATIONS ».
            .padding(start = OnyxSocle.ScreenMargin, top = top, bottom = SectionAction.Gap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(
            // Localiser **puis** mettre en capitales : « Rooms » mis en capitales d'abord (« ROOMS »)
            // n'est plus une clé connue, et la section restait en anglais.
            text = OnyxLocalization[title].uppercase(),
            localize = false,
            // **Les petites capitales de la planche** (`.sec`, `hcss.py:41`) : 11 sp, ouvertes à
            // +0,16 em, SemiBold — le même style que « CONVERSATIONS » et « BOTS » du côté
            // *Chats*. Le titre d'une section n'est pas un libellé de rangée.
            style = socleSectionStyle(theme).copy(color = mutedInk(sidebarPaneColor(theme))),
            modifier = Modifier.weight(1f),
        )
        action()
    }
}
