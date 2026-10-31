package com.onyx.app.ui.chat.components

import com.onyx.app.localization.OnyxLanguage
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.GlassCircleButton
import com.onyx.app.ui.design.IdentifierText
import com.onyx.app.ui.design.GlassPill
import com.onyx.app.ui.design.FlowState
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.LiveMark
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.design.SurfaceRole
import com.onyx.app.ui.design.surfaceRole
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.ambientLoop
import com.onyx.app.ui.design.GlassSurface
import com.onyx.app.ui.design.liquidGlass
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.localization.OnyxLocalization
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt
import com.onyx.app.ui.design.tapLabel
import com.onyx.app.ui.localization.resolveOnyxText

internal val TopBarCapsuleWidth = 86.dp
internal val TopBarActionSlotWidth = 38.dp
internal val TopBarActionSlotHeight = 30.dp
internal val TopBarPrincipalMaxWidth = 240.dp

/** Le visage du bot dans la barre : 30 dp de face, dans une boîte de 38 dp pour que l'aura respire. */
internal val TopBarAvatarSize = 30.dp

/** La boîte qui porte l'avatar et son aura : quelques points de marge pour que le halo tienne. */
internal val TopBarAvatarBoxSize = 38.dp

/**
 * La marge latérale de la barre du haut : **la même famille que la barre d'onglets** (12 dp) et que
 * la carte du compositeur (10 dp).
 *
 * Sans elle, la barre reste collée aux deux bords de l'écran : ses coins arrondis tomberaient
 * **sur** le bord, et une vitre rognée se lit comme un rectangle plutôt que comme une barre. C'est
 * le défaut « les bords ne sont pas arrondis » — il ne se corrige pas seulement en passant une
 * forme ([GlassSurface.THREAD_TOP_BAR] la porte), il se corrige aussi en **détachant** la surface,
 * comme le compositeur et les onglets.
 */
internal val TopBarSideMargin = 12.dp

/** `Conversation.refreshHeader` garde 60 caractères ; `Text.lineLimit(1)` fait le reste. */
internal const val HeaderTitleMax = 60

/**
 * Les **frontières** d'un libellé de barre : l'espace, et les séparateurs d'un nom de modèle.
 *
 * Un nom de modèle (`deepseek/deepseek-v4.1-flash`) n'a pas d'espace : couper « au mot » sur les
 * seuls blancs le laisserait entier, et c'est `TextOverflow.Ellipsis` qui le trancherait au
 * caractère — le défaut même qu'on ferme. Ses mots, à lui, sont ses segments : « / », « - », « · »
 * et « : ». Le point n'en est pas un : « v4. » coupé avant « 1-flash » se lirait comme un numéro de
 * version complet, donc faux.
 */
internal val HeaderBoundaries = Regex("[\\s/·:\\-]+")

/**
 * [text] coupé **à une frontière**, jamais au milieu d'un mot, avec son ellipse.
 *
 * La barre du haut n'a que la place qui reste après le contrôle de gauche, le visage et le groupe
 * d'icônes : un titre long y est fatalement rogné. Le rogner par `TextOverflow.Ellipsis` seul coupe
 * **au caractère** — la capture du propriétaire montrait « Vérifier la conso… » et
 * « deepseek/deepseek-v4… », deux mots tranchés en deux. Ici la coupe tombe sur une frontière, et
 * [`fits`] dit ce qui tient réellement : c'est lui qui porte la mesure (un vrai [TextMeasurer] à
 * l'écran, une longueur en caractères dans les tests).
 *
 * Un texte qui tient déjà est rendu **tel quel**. Un texte sans frontière utilisable (un seul mot
 * trop long) est rendu entier : `TextOverflow.Ellipsis` le rognera, faute de mieux — mais il ne
 * reste alors qu'un mot, jamais un demi-mot suivi d'un autre.
 */
internal fun headerLine(text: String, fits: (String) -> Boolean): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || fits(trimmed)) return trimmed
    val cuts = HeaderBoundaries.findAll(trimmed).map { it.range.last + 1 }.toList()
    var low = 0
    var high = cuts.size
    while (low < high) {
        val middle = (low + high + 1) / 2
        val candidate = trimmed.take(cuts[middle - 1]).trimEnd() + "\u2026"
        if (fits(candidate)) low = middle else high = middle - 1
    }
    return if (low <= 0) trimmed else trimmed.take(cuts[low - 1]).trimEnd() + "\u2026"
}

/**
 * [headerLine], mesuré par un vrai [TextMeasurer] dans [widthPx] pixels.
 *
 * La mesure se fait **sans contrainte** (`softWrap = false`, aucune largeur maximale) : c'est la
 * largeur du tracé qui décide, pas un repli. Contraindre la mesure à [widthPx] la rendrait
 * menteuse — la mise en page **rogne** le tracé à la contrainte, la largeur rendue vaudrait donc
 * toujours `<= widthPx` et `fits` dirait oui à tout. (Mesuré : le modèle restait entier dans la
 * barre et c'est l'ellipse de la vue qui le coupait — le défaut qu'on ferme.)
 */
internal fun TextMeasurer.headerLine(text: String, style: TextStyle, widthPx: Int): String =
    headerLine(text) { candidate ->
        measure(
            text = AnnotatedString(candidate),
            style = style,
            softWrap = false,
            maxLines = 1,
        ).size.width <= widthPx
    }

/**
 * **Le libellé de profil de la barre — écrit seulement quand il dit autre chose que le titre.**
 *
 * Le second rôle de la seconde ligne est la **clé de routage du tour** : le profil réellement en
 * force (`/p/<profil>`, `profile` de session), pour que l'app ne puisse pas annoncer un profil et
 * en servir un autre. Mais quand la conversation porte déjà ce nom — c'est le cas de toute
 * conversation de bot, `botChatToOpen` la titre du libellé du bot —, un « @writer » collé à côté ne
 * dit rien : il **répète** le titre, et il prend au modèle la place dont il a besoin pour s'écrire
 * entier (relevé du propriétaire : « deepseek/ » puis « deepsee… »). Le profil s'efface alors, et
 * **la place va au modèle**.
 *
 * [shownTitle] est le nom **écrit à l'écran** (la traduction déjà résolue pour un titre d'attente) :
 * c'est lui qui décide. Quand le profil est, au contraire, un profil qui **ne dit pas** ce nom — un
 * bot titré « Rédacteur » mais dont le profil est `writer` — la ligne reste : là, elle informe.
 */
internal fun headerProfileLabel(profile: String?, shownTitle: String): String? {
    val clean = profile?.trim().orEmpty()
    if (clean.isEmpty()) return null
    return clean.takeIf { !it.equals(shownTitle.trim(), ignoreCase = true) }
}

/**
 * **Le budget de la seconde ligne, en pixels** : la place laissée au modèle dans sa rangée.
 *
 * [rowWidthPx] la largeur maximale de la rangée, [profileWidthPx] ce que le profil y prend (zéro
 * quand il s'efface), [fixedPx] ce qui est fixe à côté — le chevron, l'anneau de contexte et les
 * écarts. C'est cette grandeur que la barre doit rendre suffisante pour le nom du modèle : elle est
 * mesurable, et c'est elle que tiennent les verrous.
 */
internal fun headerModelBudgetPx(rowWidthPx: Int, profileWidthPx: Int, fixedPx: Int): Int =
    (rowWidthPx - profileWidthPx - fixedPx).coerceAtLeast(0)

/**
 * Le titre de la barre du haut : la **première ligne** du nom, et au plus [HeaderTitleMax]
 * caractères — la règle de `Conversation.refreshHeader` — mais coupée à une frontière.
 *
 * Un mot unique plus long que la limite n'a pas de frontière : il est alors rogné au caractère, avec
 * son ellipse, parce qu'il n'y a rien de mieux à faire (et c'est alors **un** mot, pas deux).
 */
internal fun chatHeaderTitle(text: String): String {
    val first = text.lineSequence().firstOrNull()?.trim().orEmpty()
    val elided = headerLine(first) { it.length <= HeaderTitleMax }
    if (elided.length <= HeaderTitleMax) return elided
    return first.take(HeaderTitleMax - 1).trimEnd() + "\u2026"
}

/**
 * La barre de titre, refaite à la main comme celle d'la référence.
 *
 * Rien de Material : le titre est centré sur deux lignes (la conversation, puis l'agent et le
 * modèle avec un chevron), un bouton rond en verre ouvre la liste des conversations à gauche, et
 * une pastille en verre porte « … » et le crayon à droite — exactement le `toolbar` de ContentView.
 */
@Composable
fun OnyxTopBar(
    title: String,
    subtitle: String,
    /**
     * La **déclaration de repli** à écrire sous le nom du modèle, quand la passerelle a basculé sur
     * un autre modèle que celui configuré (« deepseek → repli : ornith1.5-9b (lmstudio, local) »).
     * `null` : le tour est servi par le modèle en vigueur, rien à déclarer.
     */
    fallbackLine: String? = null,
    /**
     * **Le profil réellement en force** de la conversation, écrit tel quel
     * ([com.onyx.app.ui.chat.ChatUiState.profileInForce]) : c'est lui qui décide de l'adresse de
     * passerelle, du `profile` de session, du modèle et du fournisseur du tour. `null` : rien à
     * montrer (écrans sans conversation).
     */
    profileLabel: String? = null,
    modifier: Modifier = Modifier,
    onOpenConversations: (() -> Unit)? = null,
    /**
     * Le contrôle du volet des deux panneaux, replié ou déplié (`sidebar.left`) : quand il est
     * fourni, il remplace le bouton « Conversations » à gauche — le volet est là, c'est lui qu'on
     * ouvre, et la barre ne propose pas deux fois la même destination.
     */
    sidebarToggle: (@Composable () -> Unit)? = null,
    onPickModel: (() -> Unit)? = null,
    onNewConversation: () -> Unit,
    onMenu: () -> Unit,
    menuItems: List<com.onyx.app.ui.design.MenuItem> = emptyList(),
    titleIsPlaceholder: Boolean = false,
    conversationKey: String = "",
    contextUsed: Int? = null,
    contextWindow: Int? = null,
    /**
     * Le visage du bot de la conversation ([ThreadSpeaker.faceName] / [ThreadSpeaker.faceCode]) :
     * posé à gauche du titre, il dit qui parle **sans jamais défiler** — c'est ce qui remplace
     * l'ancien en-tête du fil. `null` : pas d'avatar (écrans sans conversation).
     */
    avatarName: String? = null,
    avatarFaceCode: String? = null,
    /**
     * **Le visage de l'en-tête est la même porte que le repère de la barre du bas.**
     *
     * Quand il est fourni, un appui sur le visage ouvre l'**identité du bot de la conversation**
     * (visage, nom, voix, ce qu'il fait, personnalité, modèle, outils, compétences) — la page que
     * le repère-visage de la barre ouvre, et que « Modifier » ouvre depuis le menu d'une tuile. Le
     * **même geste donne donc le même résultat** des deux endroits où le visage est un contrôle à
     * lui. `null` : le visage reste décoratif (aucun autre écran n'est concerné).
     */
    onOpenFace: (() -> Unit)? = null,
    /**
     * Combien de sous-agents travaillent en ce moment. Zéro (ou `null`) : pas de capsule — une
     * conversation qui ne délègue rien garde sa barre telle quelle.
     */
    subagentRuns: Int? = null,
    /** Ouvre l'écran des tâches lancées. Fourni avec [subagentRuns] quand il y a des tâches. */
    onOpenSubagents: (() -> Unit)? = null,
    /**
     * LOT organisation-fil-competences : la **pastille d'état** sous le titre (« Travail · étape
     * 3 sur 5 »), comme la référence. `null` au repos : l'en-tête garde alors exactement sa
     * hauteur d'avant, et le sous-titre du modèle reste la seule seconde ligne.
     */
    status: ThreadStatus? = null,
    /**
     * LOT fil-vivant : l'**état réel du tour** ([FlowState]), tel que
     * [com.onyx.app.ui.chat.turnFlowState] le déduit. C'est lui qui donne à la pastille son
     * mouvement — respiration, pulsation, lueur figée — sans jamais changer sa couleur.
     */
    flow: FlowState = FlowState.THINKING,
    /**
     * LOT fil-vivant : vrai quand la personne **remonte lire** le fil (le suivi du bas est
     * détaché). L'état **ralentit** alors, au lieu de continuer à pleine cadence sous les yeux.
     */
    readingUp: Boolean = false,
) {
    val theme = LocalOnyxTheme.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            // La barre d'état **avant** la vitre : l'en-tête se pose donc **sous** elle, au lieu
            // de glisser dessous et de n'avoir plus d'arête haute du tout (le verre et son liseré
            // commençaient à y=0, hors de l'écran : la barre se lisait « coupée » en haut). Rien ne
            // bouge pour le contenu — le `padding` vertical de la rangée reste son propre respire.
            .statusBarsPadding()
            // **Détachée des deux bords**, comme la barre d'onglets et la carte du compositeur :
            // sans cette marge, les coins du verre tombent **sur** le bord de l'écran et la barre se
            // lit comme un rectangle rogné au lieu d'une barre flottante — le défaut « les bords ne
            // sont pas arrondis ».
            .padding(horizontal = TopBarSideMargin)
            // Le verre liquide : le fil défile **dessous** (la barre est posée par-dessus le pageur,
            // voir `ChatScreen`), donc le flou échantillonne du vrai contenu.
            //
            // **Le rôle « barre »** ([SurfaceRole.Bar]) — la même famille que la barre d'onglets :
            // le barreau [OnyxSurfaces.surfaceHigh], **le** filet de l'app, **l'**ombre unique, et
            // une **capsule**. C'était `liquidGlass(THREAD_TOP_BAR)` : le verre seul, forme 22 dp,
            // sans barreau — sur une page sombre la barre valait 1,058:1, donc rien. Les trois
            // surfaces flottantes de l'écran (en-tête, compositeur, onglets) partagent maintenant
            // la même marche et la même arête.
            .surfaceRole(SurfaceRole.Bar)
            // La forme vient de la surface, mais le rôle la fixe : une capsule, comme la barre
            // d'onglets juste en dessous.
            .liquidGlass(GlassSurface.THREAD_TOP_BAR, SurfaceRole.Bar.shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            sidebarToggle != null -> sidebarToggle()

            onOpenConversations != null -> GlassCircleButton(
                symbol = Symbols.ListBullet,
                label = "Conversations",
                size = 38.dp,
                onClick = onOpenConversations,
            )

            else -> Spacer(Modifier.size(38.dp))
        }

        // Le visage du bot de la conversation, à gauche du titre : fixe, il ne défile pas avec le
        // fil — le nom sous l'avatar ne vit plus dans le fil du tout.
        //
        // Un pas de l'échelle de **chaque côté** : mesuré, le contrôle de gauche et la boîte du
        // visage se touchaient (écart 0 dp) — les deux se lisaient comme un seul contrôle, et
        // l'écart du titre, lui, valait 6 dp, qui n'est pas une marche de la maison.
        if (avatarName != null) {
            Spacer(Modifier.width(OnyxSpace.Hair))
            // **Le visage est un contrôle quand l'écran sait où il mène.** Sans `onOpenFace`, il
            // reste exactement ce qu'il était : le dessin du bot, sans cible. Avec, il prend la
            // cible tactile de la maison (48 dp, `OnyxSpace.TouchTarget`) et le libellé de son
            // geste — le même mot que la porte de la barre du bas.
            val openFace = onOpenFace
            if (openFace != null) {
                Box(
                    modifier = Modifier
                        .size(OnyxSpace.TouchTarget)
                        .onyxClickable(onClick = openFace)
                        .tapLabel(OnyxLocalization["Open the bot's identity"]),
                    contentAlignment = Alignment.Center,
                ) {
                    TopBarAvatar(name = avatarName, faceCode = avatarFaceCode)
                }
            } else {
                TopBarAvatar(name = avatarName, faceCode = avatarFaceCode)
            }
            Spacer(Modifier.width(OnyxSpace.Hair))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = OnyxSpace.Hair),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val titleStyle = theme.headline.copy(color = theme.textColor)
            val subtitleStyle = theme.caption.copy(color = theme.secondaryText)

            // La place **réellement** restante pour le titre : la mesure se fait sur elle, et la
            // coupe tombe sur une frontière (voir [headerLine]).
            //
            // **Le titre d'attente est traduit avant d'être mesuré.** `headerLine` recevait
            // « New conversation » — qui tient — puis `TypedTitle` dessinait sa traduction,
            // « Nouvelle conversation », plus longue que la place : l'ellipse du moteur la coupait
            // au caractère (« Nouvelle convers… » à 1,3×, relevé sur 1.0.0+546). On mesure ce
            // qu'on dessine.
            val resolvedTitle = remember(title, titleIsPlaceholder) {
                if (titleIsPlaceholder) OnyxLocalization[title] else title
            }
            BoxWithConstraints(modifier = Modifier.widthIn(max = TopBarPrincipalMaxWidth)) {
                val budget = with(density) { maxWidth.roundToPx() }
                val shown = remember(resolvedTitle, budget) {
                    measurer.headerLine(resolvedTitle, titleStyle, budget)
                }
                com.onyx.app.ui.common.TypedTitle(
                    title = shown,
                    conversationKey = conversationKey,
                    isPlaceholder = titleIsPlaceholder,
                    style = titleStyle,
                    modifier = Modifier.widthIn(max = TopBarPrincipalMaxWidth),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .widthIn(max = TopBarPrincipalMaxWidth)
                    .then(if (onPickModel != null) Modifier.onyxClickable(onClick = onPickModel) else Modifier),
            ) {
                // **Le profil réellement en force** : la clé de routage du tour (adresse
                // `/p/<profil>`, `profile` de session), écrite **seulement quand elle dit autre
                // chose que le titre déjà à l'écran** ([headerProfileLabel]). Un « @writer » sous
                // un titre « writer » ne renseigne pas : il répète, et il mange la place du modèle
                // — c'est le doublon du propriétaire. Quand les deux diffèrent, la ligne reste.
                val shownProfile = remember(profileLabel, resolvedTitle) {
                    headerProfileLabel(profileLabel, resolvedTitle)
                }
                shownProfile?.let { profile ->
                    BasicText(
                        text = "@$profile",
                        localize = false,
                        style = subtitleStyle.copy(color = theme.accent),
                        // Un libellé se **replie**, jamais sur une ligne (politique des troncatures) :
                        // la barre ne coupe donc pas un nom de profil.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Le modèle : **un identifiant** ([OnyxTextPolicy.Kind.IDENTIFIER]), pas un libellé.
                // `weight(1f, fill = false)` lui donne ce qui reste après le chevron et l'anneau de
                // contexte — et **tout** ce qui reste quand le profil s'est effacé. Une adresse, un
                // chemin ou un nom de modèle **ne se coupe pas à la fin** : « deepseek/deepseek-… »
                // ne dit plus quel modèle part (le défaut relevé). [IdentifierText] garde donc le
                // début **et** la fin, sur **une seule ligne** (« deepseek/deep…-flash ») quand le
                // nom dépasse vraiment — et le dessine **entier** quand il tient, ce qui est le cas
                // dès que le profil n'a plus à répéter le titre.
                IdentifierText(
                    text = subtitle,
                    style = subtitleStyle,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = TopBarPrincipalMaxWidth),
                    maxLines = 1,
                )
                OnyxIcon(Symbols.ChevronDown, tint = theme.secondaryText, size = 8.dp)
                if (contextUsed != null && contextWindow != null && contextWindow > 0) {
                    ContextRing(used = contextUsed, window = contextWindow)
                }
            }
            // La **déclaration de repli** : le modèle en force, celui qu'il remplace et son
            // fournisseur réel — dit, jamais masqué. Une ligne, sous le nom du modèle.
            fallbackLine?.takeIf { it.isNotBlank() }?.let { line ->
                BasicText(
                    text = line,
                    localize = false,
                    style = theme.caption.copy(color = theme.accent),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = TopBarPrincipalMaxWidth),
                )
            }
            // LOT organisation-fil-competences : la pastille d'état sous le titre, comme la
            // référence (« Travail · étape 3 sur 5 »). Elle ne s'affiche que pendant un tour : au
            // repos l'en-tête garde sa hauteur, et rien n'est retiré au modèle.
            status?.let { line ->
                ThreadStatusPill(
                    status = line,
                    flow = flow,
                    readingUp = readingUp,
                    modifier = Modifier.widthIn(max = TopBarPrincipalMaxWidth),
                )
            }
        }

        // La capsule des tâches : elle n'apparaît que si la conversation a délégué quelque chose
        // (sinon la barre ne bouge pas). Un point vivant et un liseré d'accent quand l'un
        // travaille encore ; sinon le même compte, en gris — la liste reste à un tap.
        if (onOpenSubagents != null) {
            val live = (subagentRuns ?: 0) > 0
            Row(
                modifier = Modifier
                    // La capsule d'en-tête est elle aussi une vitre : elle porte un chiffre et une
                    // icône (seuil 3:1), et son flou est le plus serré de la famille (30 dp) — une
                    // petite surface n'a pas besoin du flou d'un compositeur.
                    .liquidGlass(GlassSurface.HEADER_CAPSULE, RoundedCornerShape(50))
                    .background(
                        color = if (live) theme.accent.copy(alpha = 0.14f) else theme.fill,
                        shape = RoundedCornerShape(50),
                    )
                    .onyxClickable(onClick = onOpenSubagents)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (live) {
                    LiveMark(color = theme.accent)
                } else {
                    OnyxIcon(symbol = Symbols.Person2, tint = theme.secondaryText, size = 13.dp)
                }
                BasicText(
                    text = (subagentRuns ?: 0).toString(),
                    localize = false,
                    style = theme.caption.copy(
                        color = if (live) theme.accent else theme.secondaryText,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            Spacer(Modifier.width(OnyxSpace.Hair))
        }

        GlassPill(modifier = Modifier.width(TopBarCapsuleWidth)) {
            Box(
                modifier = Modifier
                    .width(TopBarActionSlotWidth)
                    .height(TopBarActionSlotHeight)
                    .onyxClickable(onClick = onMenu)
                    .tapLabel(resolveOnyxText("More")),
                contentAlignment = Alignment.Center,
            ) {
                OnyxIcon(Symbols.Ellipsis, tint = theme.secondaryText, size = 16.dp)
            }
            Box(
                modifier = Modifier
                    .width(TopBarActionSlotWidth)
                    .height(TopBarActionSlotHeight)
                    .onyxClickable(onClick = onNewConversation)
                    .tapLabel(resolveOnyxText("New conversation")),
                contentAlignment = Alignment.Center,
            ) {
                OnyxIcon(
                    Symbols.SquareAndPencil,
                    tint = theme.accent,
                    size = 16.dp,
                )
            }
        }
    }
}

/**
 * Le visage du bot dans la barre du haut : **nu** — aucune aura, aucun cercle de couleur derrière
 * la face, aucune pulsation de fond.
 *
 * Le même dessin que partout ailleurs ([BotLook]) — la barre ne réinvente pas un avatar — mais
 * **vivant** : des pouls de repos courts et espacés (clignement, regard, respiration, micro-sourire,
 * hochement) tirés de la graine du bot, et une réaction « content » quand on le touche. La référence
 * non plus n'entoure d'aucun cercle le visage de son en-tête ; un visage qui respire sous le pouce
 * reste sobre parce qu'il **ne bouge pas en continu** : entre deux pouls il est immobile, et sa boîte
 * ne change jamais ([LiveBotFace]).
 */
@Composable
private fun TopBarAvatar(
    name: String,
    faceCode: String?,
) {
    LiveBotFace(name = name, faceCode = faceCode)
}

internal enum class ContextTint { ACCENT, WARNING, ERROR }

internal fun contextShare(used: Int, window: Int): Float =
    if (window > 0) (used.toFloat() / window.toFloat()).coerceIn(0f, 1f) else 0f

internal fun contextTint(used: Int, window: Int): ContextTint {
    val share = contextShare(used, window)
    return when {
        share >= 0.9f -> ContextTint.ERROR
        share >= 0.75f -> ContextTint.WARNING
        else -> ContextTint.ACCENT
    }
}

/** « 54,000 of 128,000 tokens (42%) », avec les séparateurs de la locale. */
internal fun contextSentence(used: Int, window: Int, locale: Locale = OnyxLanguage.preferred): String {
    val numbers = NumberFormat.getIntegerInstance(locale)
    val percent = (contextShare(used, window) * 100).roundToInt()
    val key = "%1\$s of %2\$s tokens (%3\$d%%)"
    return String.format(
        locale,
        OnyxLocalization.translate(key, locale),
        numbers.format(used),
        numbers.format(window),
        percent,
    )
}

/** L'anneau de remplissage du contexte et son popover d'explication (`ContextRing`). */
@Composable
private fun ContextRing(used: Int, window: Int) {
    val theme = LocalOnyxTheme.current
    var showDetail by remember { mutableStateOf(false) }
    val fraction = contextShare(used, window)
    val tint = when (contextTint(used, window)) {
        ContextTint.ERROR -> theme.error
        ContextTint.WARNING -> theme.warning
        ContextTint.ACCENT -> theme.accent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .onyxClickable { showDetail = true }
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Canvas(modifier = Modifier.size(11.dp)) {
            val stroke = 2.dp.toPx()
            drawArc(
                color = theme.secondaryText.copy(alpha = 0.25f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = tint,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        BasicText(
            text = "${(fraction * 100).roundToInt()}%",
            style = theme.caption.copy(color = theme.secondaryText),
        )
    }

    if (showDetail) {
        Dialog(onDismissRequest = { showDetail = false }) {
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .background(theme.surfaceColor, RoundedCornerShape(OnyxSpace.FieldCorner))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BasicText(text = "Context", style = theme.headline.copy(color = theme.textColor))
                BasicText(
                    text = contextSentence(used, window),
                    localize = false,
                    style = theme.subheadline.copy(color = theme.textColor),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(theme.secondaryText.copy(alpha = 0.20f), RoundedCornerShape(50)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(4.dp)
                            .background(tint, RoundedCornerShape(50)),
                    )
                }
                BasicText(
                    text = "Everything said in this conversation is read again with each message. When it fills up the oldest part is summarised or dropped; a new conversation starts empty.",
                    style = theme.caption.copy(color = theme.secondaryText),
                )
            }
        }
    }
}

/** La barre réduite d'un écran qui défile : la grande version en haut de page. */

@Composable
fun LargeTitleRow(title: String, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        BasicText(
            text = title,
            style = theme.largeTitle.copy(color = theme.textColor, fontWeight = FontWeight.Bold),
        )
    }
}
