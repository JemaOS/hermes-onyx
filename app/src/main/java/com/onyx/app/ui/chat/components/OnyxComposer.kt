package com.onyx.app.ui.chat.components
import com.onyx.app.ui.design.GlassSurface
import com.onyx.app.ui.design.liquidGlass

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.ui.chat.AttachmentLimits
import android.provider.OpenableColumns
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.app.ui.chat.PendingAttachment
import com.onyx.app.ui.design.ChipRowFit
import com.onyx.app.ui.design.ComposerCircleButton
import com.onyx.app.ui.design.ComposerCommands
import com.onyx.app.ui.design.ComposerGrowth
import com.onyx.app.ui.design.ComposerFieldInvite
import com.onyx.app.ui.design.composerFieldInvite
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.ICON_CONTRAST_MIN
import com.onyx.app.ui.design.ModelContrast
import com.onyx.app.ui.design.ModelChip
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.MotionFamily
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.OnyxShadow
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.ProvideFieldTextColours
import com.onyx.app.ui.design.composerBottomInsets
import com.onyx.app.ui.design.composerFieldSurface
import com.onyx.app.ui.design.fieldCursorColour
import com.onyx.app.ui.design.FieldDefaultBorderWidth
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.PlaqueGeometry
import com.onyx.app.ui.design.PlaqueInvite
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.readableInk
import com.onyx.app.ui.design.semanticsLabel
import com.onyx.app.ui.theme.LocalOnyxTheme
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.onyx.app.ui.design.tapLabel
import com.onyx.app.ui.localization.resolveOnyxText

/**
 * Les limites viennent d'`AttachmentLimits` : la feuille d'ajout, l'examen d'un ajout (le
 * ViewModel) et la bande des pièces jointes les lisent au même endroit. Deux chiffres à part, et
 * la bande aurait affiché sa propre version de la limite.
 */
private const val MaxAttachmentCount = AttachmentLimits.MAX_COUNT
private const val MaxAttachmentBytes = AttachmentLimits.MAX_TOTAL_BYTES

/** Ce qui se dit quand on dépasse le nombre. */
private const val TooManyAttachmentsMessage = "You can attach up to 10 files."

/** Ce qui se dit quand on dépasse le poids. */
private const val AttachmentsTooLargeMessage = "That's more than 25 MB."

/** Le côté d'une vignette d'image dans la bande, et le repère de décodage qui va avec. */
private val StripThumbnailSize = 64.dp
private const val StripThumbnailPixels = 256

/** `ComposerView` mesure la ligne en points, puis ajoute les deux paddings de 5. */
internal fun measuredComposerButtonSize(rowHeightPx: Int, density: Float): androidx.compose.ui.unit.Dp =
    (rowHeightPx / density).dp + 10.dp

internal val ComposerPlusGlyphSize = 20.dp
internal val ComposerDictateGlyphSize = 17.dp
internal val ComposerExpandGlyphSize = 13.dp

/**
 * Le côté d'une **commande** de la rangée du compositeur : la cible tactile de la maison.
 *
 * Elle valait **38 dp** — la valeur du modèle d'origine — et c'est de là que venait le défaut le
 * plus visible de la capture du propriétaire : « le bouton rond bleu chevauche la puce du modèle ».
 * Android **élargit lui-même** toute cible tactile sous son minimum de 48 dp ; sur une commande de
 * 38 dp cela fait **5 dp de chaque côté**, et avec l'écart de 4 dp qui était posé entre elles, les
 * deux cibles se recouvraient de **6 dp**. Relevé sur l'arbre de `1.0.0+547` : le nœud du bouton rond
 * mesurait **42 × 48 dp** pour une boîte visuelle de 38.
 *
 * 44 dp est la cible de la maison ([OnyxSpace.ThumbnailSize]), et c'est **aussi** ce qui ramène le
 * débordement du système à 2 dp par côté — couvert deux fois par l'écart de la rangée
 * ([ComposerRowGeometry.Gap], 12 dp). La règle est éprouvée par `ComposerRowGeometryTest`.
 */
internal val ComposerControlSlotHeight = OnyxSpace.ThumbnailSize

/** Le rayon de la carte de composition : **le coin de la famille des panneaux** (`OnyxSpace.PanelCorner`). */
internal val ComposerCardRadius = OnyxSpace.PanelCorner

/**
 * La marge latérale de la carte : elle flotte au-dessus de la page, sans la toucher.
 *
 * C'est **la marge d'écran** de la maison ([OnyxSpace.ScreenMargin], 16 dp) — la même que la pilule
 * d'onglets posée juste en dessous. Deux retraits concurrents sur le même écran (ici 10 dp, là 12)
 * se lisent « pas propre » même quand chacun est juste pris seul : la carte et la pilule partagent
 * donc **une seule** colonne de départ.
 */
internal val ComposerCardSideMargin = OnyxSpace.ScreenMargin

/**
 * **La géométrie de la rangée de commandes du compositeur** — et le verrou qui interdit deux cibles
 * qui se chevauchent.
 *
 * Le défaut relevé sur la capture du propriétaire : « le bouton rond bleu chevauche la puce du
 * modèle ». La cause n'est pas esthétique, elle est mesurée : la valeur visuelle d'une commande est
 * 38 dp, mais Android élargit **lui-même** toute cible tactile sous son minimum (48 dp) — 5 dp de
 * chaque côté. Avec l'écart de 4 dp qui était posé là, les cibles élargies se **recouvraient** de
 * 6 dp : deux éléments sur le même pixel, exactement ce que le doigt et l'œil lisent comme une
 * superposition.
 *
 * La règle est donc arithmétique, pas décorative : l'écart entre deux commandes doit valoir au moins
 * deux fois le débordement de leur cible. Sous 38 dp de visuel, il faut **10 dp** ; la marche `Snug`
 * de l'échelle en donne **12**, et elle sert d'écart à toute la rangée. Les fonctions sont pures,
 * donc un test les interroge sans appareil.
 */
object ComposerRowGeometry {

    /** Le minimum de cible tactile du système : en dessous, la cible est élargie d'elle-même. */
    const val TouchTargetDp: Float = 48f

    /**
     * L'écart entre deux commandes de la rangée : la marche `Snug` de l'échelle (12 dp).
     *
     * Ce n'est **pas** une valeur d'esthétique : 12 ≥ 2 × (48 − 38) / 2 = 10, donc les cibles
     * tactiles élargies par le système ne se recouvrent pas. Avec les 4 dp d'avant, elles se
     * recouvraient — c'était le défaut.
     */
    val Gap: androidx.compose.ui.unit.Dp = OnyxSpace.Snug

    /** La largeur réellement occupée par la cible d'une commande : jamais moins que le minimum. */
    fun touchWidthDp(visualDp: Float, minimumDp: Float = TouchTargetDp): Float = maxOf(visualDp, minimumDp)

    /**
     * Vrai si deux commandes voisines de largeur [visualDp], séparées de [gapDp], ne se chevauchent
     * **ni à l'œil ni au doigt** : chacune déborde de sa boîte visuelle de la moitié de l'élargissement
     * du système, et l'écart doit couvrir les deux débordements.
     */
    fun targetsClear(visualDp: Float, gapDp: Float, minimumDp: Float = TouchTargetDp): Boolean {
        val overflow = (touchWidthDp(visualDp, minimumDp) - visualDp) / 2f
        return gapDp >= 2f * overflow
    }

    /**
     * La largeur laissée à la **puce du modèle**, en dp — elle prend la place qui reste, jamais
     * celle d'une commande : la rangée moins les commandes fixes et leurs écarts.
     */
    fun chipWidthDp(rowDp: Float, fixedDp: Float, count: Int, gapDp: Float): Float =
        (rowDp - fixedDp - (count - 1).coerceAtLeast(0) * gapDp).coerceAtLeast(0f)
}

/** Le dégagement horizontal de la puce du modèle, chevron compris — et sans lui, en dernier recours. */
internal const val ModelChipInsetDp = 36f
internal const val ModelChipPlainInsetDp = 20f

/**
 * Le composer d'un chat, porté de `ComposerView.composerRow`.
 *
 * Un seul champ arrondi (bouton « + », le texte, l'agrandissement, le micro) et, à côté, un
 * bouton rond dont le rôle suit ce que vous faites : mains-libres, envoi, ou arrêt.
 *
 * Au-dessus, la bande des pièces jointes : une vignette pour chaque image, une pastille pour
 * chaque fichier (son type et son poids en sous-titre), et la croix de retrait sur le coin.
 */
@Composable
fun OnyxComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    isStreaming: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    /** Donné par un écran qui doit rendre le focus au champ, sans imposer ce besoin aux autres. */
    focusRequester: FocusRequester? = null,
    canSteer: Boolean = false,
    returnSends: Boolean = true,
    queued: Int = 0,
    /**
     * **Serveur injoignable.** L'invite du champ **cède** : la file (« N messages waiting ») porte
     * l'état, et le champ ne se met pas à écrire deux textes à la fois (voir [composerFieldInvite]).
     */
    offline: Boolean = false,
    /** Erreur propre à l'écran (dictée, upload), montrée après la bande de pièces jointes. */
    problem: String? = null,
    localizeProblem: Boolean = false,
    attachments: List<com.onyx.app.ui.chat.PendingAttachment> = emptyList(),
    /** Les images collées au clavier (Ctrl+V) arrivent ici, sans les aperçus de liens. */
    onPasteImages: ((List<android.net.Uri>) -> Unit)? = null,
    onRemoveAttachment: (String) -> Unit = {},
    /** Une pièce jointe ouverte au tap depuis la bande (le PDF dans le lecteur de l'app). */
    onOpenAttachment: (com.onyx.app.ui.chat.PendingAttachment) -> Unit = {},
    onSend: () -> Unit,
    onStop: () -> Unit,
    onTalk: () -> Unit,
    /** Action spécifique au steer ; à défaut, le même envoi que la file est conservé. */
    onSteer: (() -> Unit)? = null,
    /** Le groupe envoie avec arrow.up même pendant un tour ; le chat offre steer/file. */
    queueWhileStreaming: Boolean = true,
    /** Le micro dicte dans le champ ; à défaut, il conserve l'ancienne action vocale. */
    dictating: Boolean = false,
    onDictate: (() -> Unit)? = null,
    onAddAttachment: () -> Unit = {},
    onExpand: () -> Unit = {},
    allowExpand: Boolean = true,
    maxLines: Int = ComposerGrowth.MaxLines,
    trailingButtonSize: androidx.compose.ui.unit.Dp? = null,
    verticalInsets: androidx.compose.ui.unit.Dp = 6.dp,
    auxiliaryInsets: androidx.compose.ui.unit.Dp = 18.dp,
    fieldContentPadding: androidx.compose.ui.unit.Dp = 5.dp,
    /** Les pièces jointes déposées sur le composeur ; `null` laisse le fil les recevoir. */
    onDropAttachments: ((List<android.net.Uri>) -> Unit)? = null,
    /**
     * La marge système du bas du champ. `false` quand un appelant la prend **lui-même**, une seule
     * fois, pour toute la pile du bas (compositeur **et** barre d'onglets) : c'est ce qui empêche
     * les deux marges — clavier et navigation — de s'additionner (voir [composerBottomInsets]).
     */
    applyBottomInsets: Boolean = true,
    /**
     * **Le compositeur est posé sur la plaque unique** ([ChatScreen]) : il ne peint alors **plus sa
     * propre carte** (forme, fond, filet, ombre, verre) — c'est la plaque qui les porte, et le
     * compositeur et la barre d'onglets ne font donc plus qu'**une seule** surface
     * (la plaque `.plaque` de la planche, `hcss.py:508` / `hlib.py:491`). Faux : la carte d'origine,
     * exactement comme avant.
     */
    plate: Boolean = false,
    /**
     * Vrai/faux quand le champ prend ou perd le focus : l'appelant sait qu'on est en train d'écrire.
     */
    onFocusedChange: ((Boolean) -> Unit)? = null,
    /**
     * Le modèle retenu, écrit dans la **puce** de la carte. `null` : pas de puce, la rangée garde
     * son seul « + » et ses trois boutons — c'est le cas des écrans qui n'ont pas de modèle à
     * choisir (une salle de groupe, l'écran Code).
     */
    modelLabel: String? = null,
    /** Le niveau de réflexion du modèle, second segment de la puce (« Modèle | Réflexion »). */
    reflectionLabel: String? = null,
    /** Ouvre le **sélecteur de modèle** (la feuille de la fonction 2). */
    onOpenModelPicker: (() -> Unit)? = null,
    /**
     * **Le bouton vocal est-il offert ?** Vrai par défaut, comme la planche, qui l'a toujours.
     * Faux quand l'écran appelant n'a **aucune** scène vocale à ouvrir : la commande n'est alors
     * pas dessinée, au lieu d'être dessinée puis de ne rien faire (une action muette est un bug).
     * La salle de groupe s'en sert (`GroupComposer`) quand ses voix sont déjà prises.
     */
    showTalk: Boolean = true,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val hasDraft = draft.isNotBlank() || attachments.isNotEmpty()
    val draftIsLong = draft.length > 160 || draft.count { it == '\n' } >= 3
    // **Le mesureur du brouillon.** Il compte les lignes **réelles** du texte (retours à la ligne
    // compris) à la largeur de référence, et c'est cette mesure — pure, reproductible — qui décide si
    // les commandes descendent. Rien n'est estimé au nombre de caractères.
    val draftMeasurer = rememberTextMeasurer()
    var focused by remember { mutableStateOf(false) }

    // Le poids de chaque pièce jointe, résolu une fois auprès du fournisseur de contenu.
    val sizes = remember { mutableStateMapOf<String, Long>() }
    LaunchedEffect(attachments) {
        attachments.forEach { attachment ->
            if (sizes[attachment.id] == null) {
                val bytes = withContext(Dispatchers.IO) { resolveAttachmentSize(context, attachment.uri) }
                if (bytes != null) sizes[attachment.id] = bytes
            }
        }
    }
    val knownBytes = attachments.sumOf { sizes[it.id] ?: 0L }

    // Ce qu'un dépôt a fait refuser ; le message s'efface dès que la liste change.
    var refused by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(attachments.size) { refused = null }
    val attachmentError = refused ?: when {
        attachments.size > MaxAttachmentCount -> TooManyAttachmentsMessage
        knownBytes > MaxAttachmentBytes -> AttachmentsTooLargeMessage
        else -> null
    }

    // Le dépôt du composeur : il ajoute l'image ou le texte, après avoir vérifié les limites.
    val dropTarget = rememberComposerDropTarget(
        attachmentCount = attachments.size,
        knownBytes = knownBytes,
        onAccept = onDropAttachments ?: {},
        onRefuse = { message -> refused = message },
    )
    // Sans dépôt branché, le composeur ne capte rien : c'est le fil qui reçoit, comme avant.
    val capturesDrop = onDropAttachments != null

    // ----------------------------------------------------------------------------------------
    // **Ce qui s'écrit dans le champ : UNE seule chose.** La décision est la règle pure du socle
    // (`composerFieldInvite`) — le même tableau que le rapport et le verrou. Le défaut de la
    // capture du propriétaire (« En attente du serveur » écrit **par-dessus** « Écrire à Onyx… »
    // pendant l'attente) est impossible par construction : il n'y a qu'une invite, et elle vaut
    // `null` quand elle cède.
    // ----------------------------------------------------------------------------------------
    val invite = composerFieldInvite(
        // Le brouillon est la couche dès qu'il n'est plus vide — c'est le cas de la réponse qui
        // arrive pendant la saisie.
        draftBlank = draft.isEmpty(),
        dictating = dictating,
        streaming = isStreaming,
        canSteer = canSteer,
        offline = offline,
        group = title == "the group",
    )
    /** Le mot de l'invite, ou `null` quand elle **cède** la place à un état. */
    val placeholder: String? = when (invite) {
        ComposerFieldInvite.DICTATION -> "Listening…"
        ComposerFieldInvite.STEER -> "Steer the reply…"
        ComposerFieldInvite.GROUP -> "Message the group"
        ComposerFieldInvite.ASK -> "Ask $title"
        // Cédée : l'état vit **au-dessus** du champ (la carte d'activité) ou dans la file. Rien
        // n'est écrit dans le champ, donc deux libellés ne peuvent pas s'y superposer.
        ComposerFieldInvite.YIELDED, ComposerFieldInvite.OFFLINE, ComposerFieldInvite.NONE -> null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // `ComposerView` : `.animation(.snappy(duration: 0.3), value: pendingAttachments.count)`
            // (`ComposerView:137`) — la bande de pièces jointes ouvre et referme la hauteur du
            // champ en ressort, au lieu de la sauter d'un coup. Même chose pour le brouillon en
            // cours d'édition (`:138`) et les suggestions (`:139`).
            .animateContentSize(animationSpec = OnyxMotion.Sites.ComposerRowSize)
            .then(
                if (capturesDrop) {
                    Modifier.dragAndDropTarget(
                        shouldStartDragAndDrop = { containsAttachableDrop(it) },
                        target = dropTarget,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = auxiliaryInsets, end = auxiliaryInsets, top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.forEach { attachment ->
                    AttachmentStripTile(
                        attachment = attachment,
                        bytes = sizes[attachment.id],
                        onRemove = { onRemoveAttachment(attachment.id) },
                        onOpen = { onOpenAttachment(attachment) },
                    )
                }
            }
        }
        val shownProblem = problem ?: attachmentError
        if (shownProblem != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(start = auxiliaryInsets, bottom = 2.dp),
            ) {
                OnyxIcon(
                    Symbols.ExclamationTriangle,
                    tint = if (problem != null) theme.warning else theme.error,
                    size = 13.dp,
                )
                BasicText(
                    text = shownProblem,
                    localize = if (problem != null) localizeProblem else true,
                    style = theme.caption.copy(color = if (problem != null) theme.warning else theme.error),
                )
            }
        }
        if (queued > 0) {
            BasicText(
                text = if (queued == 1) "1 message waiting" else "$queued messages waiting",
                style = theme.caption.copy(color = theme.secondaryText),
                modifier = Modifier.padding(start = 22.dp, bottom = 2.dp),
            )
        }
        // ---------------------------------------------------------------------------------
        // La **carte de composition** (fonction 1) : la même fenêtre que la référence, redessinée
        // avec le vocabulaire d'Onyx. Une carte arrondie posée en bas, le **champ de saisie en
        // haut**, et sous lui la **rangée** des commandes — « + », la **puce du modèle** (le
        // modèle **et** son niveau de réflexion, avec le chevron qui ouvre le sélecteur), puis,
        // à droite, les **trois boutons ronds** : micro, vocal, envoi.
        //
        // Rien de Material : la carte est une vitre maison (`frostedBar`), le champ ne peint
        // **aucun aplat** (`composerFieldSurface`), et les commandes **suivent le champ** : tant que
        // le texte tient sur une ligne, elles restent à sa droite (la plaque à une ligne, `.ask`) ;
        // **dès qu'il passe à plusieurs lignes, elles descendent sur leur propre rangée**, sous le
        // champ — le texte ne passe donc jamais dessous, et aucune ligne n'est coupée (voir
        // `ComposerGrowth`). La puce, elle, **mesure** son libellé, le **réduit**, puis l'**abrège
        // aux frontières de segment** (`ChipRowFit`) : elle ne déborde jamais, à 1,0× comme à 1,3×.
        //
        // La carte remplace la barre pleine largeur d'avant et **ne touche pas** à l'empilement :
        // la marge système reste prise **une seule fois**, par la pile ([composerBottomInsets]).
        // ---------------------------------------------------------------------------------
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // **La décision « une ligne ou plusieurs », prise à une largeur FIXE.**
        //
        // La largeur de référence est celle que le champ garde quand les commandes tiennent sur sa
        // rangée ([ComposerGrowth.singleLineFieldWidthDp]) — jamais la largeur courante. C'est la
        // règle qui **interdit l'aller-retour pendant la frappe** : une fois les commandes
        // descendues, le champ s'élargit ; mesurer à la largeur courante referait repasser le texte
        // à une ligne, remonterait les commandes, et ainsi de suite. La mesure est donc une fonction
        // pure de (texte, largeur fixe).
        val composerWidthDp = maxWidth.value
        val singleLineFieldWidthDp = ComposerGrowth.singleLineFieldWidthDp(composerWidthDp - 24f)
        val draftLineCount = remember(draft, singleLineFieldWidthDp, theme.body, density.density) {
            if (draft.isEmpty()) {
                1
            } else {
                val widthPx = with(density) { singleLineFieldWidthDp.dp.roundToPx() }
                draftMeasurer.measure(
                    text = AnnotatedString(draft),
                    style = theme.body,
                    constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)),
                ).lineCount
            }
        }
        val multiline = ComposerGrowth.stacksCommands(draftLineCount)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (plate) {
                        // **La rangée du champ GRANDIT avec son contenu.** Elle ne fait plus 64 dp
                        // *figés* : 64 dp est l'état **au repos** (la hauteur de `.ask` dans la
                        // planche), et c'est aussi son minimum. Au-delà, elle grandit ligne par ligne
                        // avec le texte (voir `ComposerGrowth`), jusqu'à ce que le champ défile en
                        // lui-même au maximum. C'est exactement ce qui rend impossible la coupure de
                        // la **première ligne** — le défaut de la capture du propriétaire. Au repos,
                        // la plaque vaut toujours ses 140 dp (la croissance est nulle).
                        Modifier.heightIn(min = PlaqueGeometry.FieldRowHeight)
                    } else {
                        Modifier.padding(horizontal = ComposerCardSideMargin)
                    },
                )
                // -----------------------------------------------------------------------------
                // **La marche franche du compositeur (sombre).**
                //
                // C'est la correction du reproche principal du propriétaire : sur `1.0.0+580`, le
                // compositeur était « un bloc quasi noir sur une page quasi noire ». Mesuré : le
                // voile du verre posé sur la page sombre donnait `#121215`, soit **1,058:1** —
                // indiscernable. Le barreau de [OnyxSurfaces.surfaceHigh] (`#26262B`) mesure
                // **1,314:1** : la carte se lit **posée**, pas fondue.
                //
                // Le barreau est peint **sous** la vitre, et c'est voulu : la vitre garde son flou et
                // son reflet, mais elle ne décide plus de la séparation — celle-ci est
                // **déterministe**, elle ne dépend pas de ce qui défile derrière (une image blanche
                // dans le fil, par exemple, faisait remonter l'opacité du voile et disparaître la
                // carte). Le filet de la maison et l'ombre unique suivent la même forme.
                // -----------------------------------------------------------------------------
                .then(
                    if (plate) {
                        // **Sur la plaque unique, le compositeur ne peint plus sa propre carte** :
                        // la forme, le fond, le filet et l'ombre appartiennent à la plaque de
                        // `ChatScreen`. La soudure n'est plus un pont entre deux formes, c'est
                        // **une seule** forme.
                        Modifier
                    } else {
                        Modifier
                            .shadow(OnyxShadow.Float, RoundedCornerShape(ComposerCardRadius), clip = false)
                            .clip(RoundedCornerShape(ComposerCardRadius))
                            .background(OnyxSurfaces.surfaceHigh(theme), RoundedCornerShape(ComposerCardRadius))
                            .border(
                                OnyxSpace.Stroke,
                                OnyxSurfaces.hairline(theme),
                                RoundedCornerShape(ComposerCardRadius),
                            )
                    },
                )
                // Le verre liquide du compositeur (lot « verre liquide ») : le fil défile
                // **dessous** (la pile du bas est posée par-dessus le pageur, voir `ChatScreen`).
                // C'est la plus grande vitre de l'app, donc le flou le plus large — 40 dp. La
                // forme arrondie est celle de la carte du lot « composition » : le liseré du verre
                // suit le clip de la carte et ne se lit plus comme un rectangle.
                .then(
                    if (plate) {
                        Modifier
                    } else {
                        Modifier.liquidGlass(GlassSurface.COMPOSER, RoundedCornerShape(ComposerCardRadius))
                    },
                )
                // L'encart du système **une seule fois** : le clavier, puis la zone de navigation
                // **sans** le clavier (`composerBottomInsets`). Avant, `imePadding()` et
                // `navigationBarsPadding()` se suivaient : clavier ouvert, les deux marges
                // s'additionnaient et la pile poussait la barre d'onglets sous le bord (le
                // compositeur la recouvrait à moitié).
                .then(if (applyBottomInsets) Modifier.composerBottomInsets() else Modifier)
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    // Sur la plaque, la hauteur de la rangée **est** son rembourrage : les 64 dp
                    // sont donnés par la cible `.ask`, et le contenu s'y centre (voir plus bas).
                    top = if (plate) 0.dp else verticalInsets,
                    bottom = if (plate) 0.dp else verticalInsets,
                ),
            verticalAlignment = if (plate) Alignment.CenterVertically else Alignment.Bottom,
        ) {
        // **Un étage ou deux : le MÊME arbre.** La plaque met le champ et ses commandes sur **une**
        // ligne (`.ask`, 64 px) ; la carte garde ses **deux** étages — le champ, puis ses commandes —
        // comme la référence. Ce sont les mêmes enfants, dans le même ordre, avec les mêmes gestes :
        // seule la disposition les sépare, donc les deux modes ne peuvent pas diverger. C'est ce qui
        // a ramené la plaque de 166 à 140 dp.
        // **Le « + » de la rangée**, défini une fois. Sur la plaque il est posé **en tête** — avant le
        // champ, comme dans la planche (`.ask` : « + », le champ, le micro, l'envoi) ; dans la carte
        // il ouvre la rangée des commandes, sous le champ.
        val leading: @Composable () -> Unit = {
            IconSlot(
                symbol = Symbols.Plus,
                label = "Add",
                size = ComposerControlSlotHeight,
                height = ComposerControlSlotHeight,
                glyphSize = ComposerPlusGlyphSize,
                // **L'encre vient du thème ET se lit sur ce qui est peint dessous.** Une commande
                // sans disque se mesure sur le **sol de la plaque** (`ComposerCommands`) : le même
                // chiffre pour l'écran et pour le verrou.
                tint = ComposerCommands.glyphOnPlaque(theme, theme.secondaryText),
                onClick = onAddAttachment,
            )
        }
        val body: @Composable (Modifier) -> Unit = { fieldModifier ->
            // Le fond du champ **ne peint aucun aplat** : la vitre de la carte passe au travers.
            // C'est ce qui retire pour de bon la bande claire qui se lisait derrière le texte
            // quand le clavier était ouvert (voir `composerFieldSurface`).
            ProvideFieldTextColours(theme) {
            // **Le libellé du champ, et sa mesure.** La planche écrit « Écrire à Onyx… »
            // (`fil-clair.html:479`) ; l'app montrait sa clé traduite. Le libellé de la planche est
            // donc posé ici, et **choisi par la mesure** : la plaque porte quatre commandes, donc le
            // champ ne garde que ~127 dp de texte à 1,3× de police, et `PlaqueInvite.key` rend la
            // version **courte** (sans l'ellipse) quand la pleine n'y tient pas. Le libellé n'est
            // jamais coupé — la mesure décide, pas l'ellipse de Compose.
            val inviteMeasurer = rememberTextMeasurer()
            val measureInvite: (String) -> Float = { key ->
                val label = OnyxLocalization[key]
                with(density) {
                    inviteMeasurer.measure(
                        label,
                        style = theme.body,
                        maxLines = 1,
                    ).size.width.toDp().value
                }
            }
            BoxWithConstraints(
                modifier = fieldModifier
                    .defaultMinSize(minHeight = 40.dp)
                    .composerFieldSurface(
                        theme = theme,
                        // **Le rayon du champ suit la règle de l'emboîtement** : un élément posé à
                        // `inset` du bord d'un contenant de rayon `outer` prend `interior(outer,
                        // inset)` — deux courbes de même centre se lisent comme **une** forme, deux
                        // courbes quelconques comme deux rectangles qui se chevauchent. Le champ
                        // reprenait le rayon de la **carte** (26 dp), donc ses coins ne partageaient
                        // pas le centre de ceux de la carte : le champ paraissait posé de travers.
                        shape = if (plate) {
                            // **Le champ de la plaque est une capsule.** Il n'a ni liseré ni fond
                            // propre : la plaque est la surface, et un second contour se lirait
                            // « un champ dans un champ ». Seule la sélection reste teintée.
                            RoundedCornerShape(50)
                        } else {
                            RoundedCornerShape(OnyxSpace.interior(ComposerCardRadius, 12.dp))
                        },
                        focused = focused && hasDraft,
                        // **Aucun cadre autour du champ sur la plaque** : la planche n'en dessine
                        // pas (`.askf` n'a pas de bordure), et un second contour dans une plaque
                        // déjà bordée se lit « un champ dans un champ ».
                        outlined = !plate,
                    )
                    // **Le rembourrage de la planche** : `.askf{padding:0 4px}`. Le champ de la
                    // carte garde ses 12 dp ; celui de la plaque prend les 4 de la planche, et ce
                    // sont ces 16 dp qui laissent le libellé entier à 1,3×.
                    .padding(horizontal = if (plate) PlaqueGeometry.FieldInset else 12.dp),
            ) {
                // **La couche du champ — au plus UN texte.** L'invite n'est dessinée que si elle
                // écrit quelque chose (`placeholder != null` : ni cédée, ni recouverte par le
                // brouillon). C'est ce que le verrou compte : `composerFieldTexts` rend au plus un
                // libellé, jamais deux.
                val shownInvite: String? = when {
                    placeholder == null -> null
                    // Le libellé de la plaque est **choisi par la mesure** (voir `PlaqueInvite`),
                    // dans la largeur que le champ offre réellement (`maxWidth`, rembourrage
                    // déduit) : jamais de coupure, et jamais une commande en moins. Les autres
                    // invites (dictée, pilotage) disent leur mot partout, plaque comme carte.
                    plate && invite == ComposerFieldInvite.ASK ->
                        OnyxLocalization[PlaqueInvite.key(maxWidth.value, measureInvite)]

                    else -> placeholder
                }
                if (draft.isEmpty() && shownInvite != null) {
                    BasicText(
                        text = shownInvite,
                        style = theme.body.copy(color = theme.tertiaryText),
                        // Une seule ligne : « Écrire à Onyx… » ne passe pas sur deux lignes dans
                        // la carte. Le texte s'élide, le champ garde sa hauteur.
                        // **Sur la plaque, c'est une ligne, point** : il n'y a pas de seconde ligne
                        // où un libellé pourrait se couper.
                        maxLines = if (plate) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    textStyle = theme.body.copy(color = theme.textColor),
                    cursorBrush = SolidColor(fieldCursorColour(theme)),
                    maxLines = maxLines,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = if (returnSends) ImeAction.Send else ImeAction.Default,
                    ),
                    keyboardActions = KeyboardActions(onSend = { if (hasDraft) onSend() }),
                    modifier = Modifier
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                        .onFocusChanged {
                            focused = it.isFocused
                            onFocusedChange?.invoke(it.isFocused)
                        }
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        // Ctrl+V / Cmd+V sur une image : le champ de texte ne prend que du
                        // texte, on intercepte donc avant lui.
                        .onPreviewKeyEvent { event ->
                            val chord = (event.isCtrlPressed || event.isMetaPressed) &&
                                event.key == androidx.compose.ui.input.key.Key.V
                            if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && chord) {
                                val uris = clipboardImages(context)
                                if (uris.isNotEmpty() && onPasteImages != null) {
                                    onPasteImages.invoke(uris)
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        },
                )
            }
            }

            /**
             * **Ce que porte le bouton de fin de rangée**, selon l'état.
             *
             * Calculé **une fois** pour les deux dispositions — la rangée de la carte et celle de la
             * plaque lisent la même liste, donc les trois passages (envoi ⇄ arrêt, envoi ⇄ dictée,
             * envoi ⇄ steer) ne peuvent pas diverger d'un mode à l'autre.
             */
            val trailingStates = when {
                isStreaming && hasDraft -> when {
                    !queueWhileStreaming -> listOf("send")
                    canSteer && draft.isNotBlank() -> listOf("steer", "after")
                    else -> listOf("after")
                }
                isStreaming -> listOf("stop")
                else -> listOf("send")
            }

            // La **rangée** des commandes, sous le champ : « + », la puce du modèle, puis les
            // trois boutons ronds — **sur la même ligne**, jamais repliés.
            //
            // L'écart n'est pas décoratif : [ComposerRowGeometry.Gap] (12 dp) est ce qui empêche les
            // cibles tactiles élargies par le système (48 dp pour un visuel de 38) de se recouvrir.
            // Avec les 4 dp d'avant, deux voisines se chevauchaient de 6 dp — le défaut de la capture.
            // **Une seule ligne ou sa propre rangée.** Tant que le texte tient sur une ligne, les
            // commandes restent à droite du champ (la « fin de ligne » de la planche). Dès qu'il
            // passe à plusieurs lignes, elles descendent sur **leur** rangée, pleine largeur, sous
            // le champ — exactement comme la référence du propriétaire — et le texte ne peut donc
            // jamais passer dessous. C'est la même disposition que la carte.
            val commandsOwnRow = !plate || multiline
            Row(
                modifier = if (!commandsOwnRow) {
                    // Sur la plaque à une ligne, la rangée des commandes est une **fin de ligne** :
                    // elle prend la place de ses boutons, et le champ garde le reste (`weight(1f)`
                    // posé sur la rangée). Sa marge haute disparaît — il n'y a plus d'étage au-dessus.
                    Modifier
                } else {
                    Modifier.fillMaxWidth().padding(top = 6.dp)
                },
                verticalAlignment = Alignment.CenterVertically,
                // **La marche de la planche sur la plaque** (`gap:10px`, `.ask`), la marche `Snug`
                // de la maison sur la carte : ces 2 dp par écart sont ce qui laisse les quatre
                // commandes de la plaque et le libellé du champ tenir sur une ligne. La règle
                // tactile ne bouge pas — à 44 dp de cible visuelle, 4 dp suffisent.
                horizontalArrangement = Arrangement.spacedBy(
                    if (plate) PlaqueGeometry.FieldGap else ComposerRowGeometry.Gap,
                ),
            ) {
                // **Le « + » de la rangée.** Sur la plaque il est posé **en tête** — avant le champ,
                // comme dans la planche (`.ask` : « + », le champ, le micro, l'envoi) ; dans la
                // carte il ouvre la rangée des commandes, sous le champ. Une seule définition,
                // appelée à l'endroit que la disposition demande : les deux ne peuvent pas diverger.
                if (commandsOwnRow) leading()

                // La puce du modèle : elle prend la place qui reste et ouvre le sélecteur.
                //
                // **Elle n'est PAS dessinée sur la plaque.** La planche n'a « ni troncature, ni
                // libellé de modèle dans le champ » : la puce y occupait le champ, et son libellé se
                // coupait sur deux lignes (« modèle par défa… »). Le modèle n'en devient pas
                // injoignable pour autant : il est **nommé et cliquable dans l'en-tête** de la
                // conversation (`OnyxTopBar.onPickModel`, la seconde ligne du titre), qui est sa
                // porte d'origine. Rien n'est supprimé : la puce reste entière pour la carte.
                if (!plate) {
                    if (modelLabel != null && onOpenModelPicker != null) {
                        Box(modifier = Modifier.weight(1f)) {
                            ComposerModelChip(
                                model = modelLabel,
                                reflection = reflectionLabel,
                                onClick = onOpenModelPicker,
                            )
                        }
                    } else {
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                } else if (multiline) {
                    // **La plaque multiligne n'a pas de puce de modèle** (elle vit dans l'en-tête) :
                    // c'est un simple ressort qui pousse les trois boutons à droite de leur rangée.
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }

                if (draftIsLong && allowExpand) {
                    IconSlot(
                        symbol = Symbols.Expand,
                        label = "Open the editor",
                        // La largeur d'une commande, pas 30 dp : sous 44, le système élargit la cible
                        // de 9 dp de chaque côté et elle recouvre sa voisine (relevé).
                        size = ComposerControlSlotHeight,
                        height = ComposerControlSlotHeight,
                        glyphSize = ComposerExpandGlyphSize,
                        tint = ComposerCommands.glyphOnPlaque(theme, theme.secondaryText),
                        onClick = onExpand,
                    )
                }

                // Les trois boutons ronds de la référence : micro, vocal, envoi.
                IconSlot(
                    symbol = if (dictating) Symbols.Waveform else Symbols.MicOutline,
                    label = if (dictating) "Stop dictating" else "Dictate",
                    size = ComposerControlSlotHeight,
                    height = ComposerControlSlotHeight,
                    glyphSize = ComposerDictateGlyphSize,
                    tint = ComposerCommands.glyphOnPlaque(
                        theme,
                        if (dictating) theme.error else theme.secondaryText,
                    ),
                    onClick = onDictate ?: onTalk,
                )

                // **Le bouton du mode vocal, sur la plaque.** Il l'avait quittée : la planche met
                // un micro dans la rangée du champ, entre le texte et l'envoi, et le lot précédent
                // avait retiré ce bouton parce qu'à quatre commandes le champ tombait à 131 dp de
                // large. Le brief est clair — on ne résout pas ce manque de place en supprimant une
                // commande — donc le bouton **revient**, et c'est la place qui s'ajuste :
                // la marche de la rangée et le rembourrage du champ prennent les valeurs de la
                // planche (`PlaqueGeometry.FieldGap`, `.FieldInset`), et le libellé du champ est
                // **choisi par la mesure** (`PlaqueInvite`) plutôt que coupé. Rien n'est supprimé :
                // le mode vocal est de nouveau joignable directement depuis la plaque, comme la
                // dictée, et la plaque garde ses 140 dp (64 + 76, inchangés).
                //
                // L'onde est **neutre** (fond de contrôle + filet), l'envoi seul est accentué —
                // deux cercles de même taille et de même couleur ne disaient plus laquelle est
                // l'action principale.
                val talkDisc = ComposerCommands.disc(theme, theme.fill)
                if (showTalk) {
                ComposerCircleButton(
                    symbol = null,
                    label = "Talk",
                    // **Le disque se mesure sur la plaque.** `theme.fill` posé tel quel valait
                    // **1,10:1** contre le sol sombre de la plaque : le bouton du mode vocal
                    // n'existait pas à l'œil. `ComposerCommands.disc` l'écarte du sol du plus petit
                    // pas qui suffit — la teinte du thème ne change pas quand elle tient déjà.
                    tint = talkDisc,
                    // 44 dp : la cible de la maison, la même que le « + » et le micro de la
                    // planche — c'est l'envoi, et lui seul, qui est un peu plus grand (48 dp).
                    size = trailingButtonSize ?: ComposerControlSlotHeight,
                    waveform = true,
                    // **Une commande mesurée ne s'atténue pas quand elle est inerte** : `enabled`
                    // gouverne le clic, jamais la peinture (voir `ComposerCircleButton`).
                    disabledAlpha = ComposerCommands.INERT_ALPHA,
                    // L'onde **au repos** : l'encre secondaire du thème, atténuée le plus loin
                    // possible sans descendre sous le seuil d'icône (3:1 + la marge
                    // d'antialiasing) — mesurée contre le disque **réellement peint**.
                    contentTint = OnyxContrast.dimmed(
                        OnyxContrast.over(theme.secondaryText, talkDisc),
                        talkDisc,
                        OnyxContrast.ICON_MIN + ComposerCommands.IDLE_MARGIN,
                    ),
                    border = OnyxSurfaces.hairline(theme),
                    onClick = onTalk,
                )
                }

                // **L'état du bouton d'envoi est une famille de mouvement** ([MotionFamily.Send]) :
                // une courbe et une durée, une seule fois, valables pour les trois passages (envoi
                // ⇄ arrêt, envoi ⇄ dictée). La spec est lue **hors** de `transitionSpec` — cette
                // lambda n'est pas composable, et y lire le réglage système obligerait à un
                // contournement. Sous « Réduire les animations », elle vaut `snap()` : le bouton
                // change d'état sans une image intermédiaire.
                val reduceMotion = LocalReduceMotion.current
                val sendSpec = MotionFamily.Send.spec<Float>(reduceMotion)
                AnimatedContent(
                    targetState = trailingStates,
                    transitionSpec = {
                        (
                            scaleIn(initialScale = 0.4f, animationSpec = sendSpec) +
                                fadeIn(animationSpec = sendSpec)
                            ).togetherWith(
                            scaleOut(targetScale = 0.4f, animationSpec = sendSpec) +
                                fadeOut(animationSpec = sendSpec),
                        )
                    },
                    label = "composerTrailingButton",
                ) { currentStates ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Hair),
                    ) {
                        currentStates.forEach { trailingState ->
                            TrailingComposerButton(
                                state = trailingState,
                                // **L'envoi seul est un peu plus grand sur la plaque** : la planche
                                // fait son cercle d'envoi à 48 px (`.csend{width:48px}`), quand ses
                                // trois autres commandes font 44 (`.press`). C'est l'action
                                // principale de l'écran, et c'est la planche qui le dit.
                                size = trailingButtonSize
                                    ?: if (plate) PlaqueGeometry.SendSlot else ComposerControlSlotHeight,
                                // Un envoi sans brouillon ne fait rien : le bouton le dit.
                                enabled = hasDraft || trailingState != "send",
                                onSend = onSend,
                                onSteer = onSteer ?: onSend,
                                onStop = onStop,
                                onTalk = onTalk,
                            )
                        }
                    }
                }
            }
        }
        // **Le contenant — et c'est la SEULE différence entre les deux modes.** La plaque met tout
        // sur une ligne (le champ prend la place qui reste, les quatre commandes la leur) ; la carte
        // garde le champ au-dessus et ses commandes en dessous. Les enfants sont les mêmes, dans le
        // même ordre : un seul arbre, deux dispositions.
        ComposerBodyContainer(
            plate = plate,
            multiline = multiline,
            leading = leading,
        ) { fieldModifier -> body(fieldModifier) }
        } // fin de la rangée du champ
        } // fin du BoxWithConstraints qui porte la mesure du brouillon
    }
}

/**
 * La **puce du modèle** du compositeur : le modèle retenu, son niveau de réflexion, et le chevron
 * qui ouvre le **sélecteur de modèle** (fonction 2).
 *
 * Son libellé **ne déborde jamais** : il est **mesuré** sous la police courante, **réduit** par
 * demi-point jusqu'à un plancher, puis **abregé aux frontières de segment** — la règle de
 * [ChipRowFit], celle du reste de l'app. Le plancher est une taille **rendue** : à police 1,3×,
 * 11 sp donnent plus de 14 dp de haut, largement lisibles ; c'est la place, pas l'encre, qui manque.
 */
@Composable
private fun ComposerModelChip(
    model: String,
    reflection: String?,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val label = ModelChip.label(model, reflection)
    // L'encre de la puce est **mesurée** contre son fond réellement composé (le pire cas) : la
    // couleur du thème ne fait jamais perdre la lecture — même règle que la barre d'onglets.
    val chipBackground = ModelContrast.chip(theme)
    val labelInk = readableInk(theme.textColor, chipBackground)
    val chevronInk = readableInk(theme.secondaryText, chipBackground, ICON_CONTRAST_MIN)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val available = maxWidth.value
        val measure: (String, Float) -> Float = { text, size ->
            val style = theme.caption.copy(fontSize = size.sp, fontWeight = FontWeight.SemiBold)
            with(density) {
                measurer.measure(text = text, style = style, maxLines = 1).size.width.toDp().value
            }
        }
        val base = theme.caption.fontSize.value
        val plan = ChipRowFit.labelPlan(
            labels = listOf(label),
            availableDp = available,
            // Les ornements en dernier recours : un chevron en moins vaut mieux qu'un mot coupé.
            insetsDp = listOf(ModelChipInsetDp, ModelChipPlainInsetDp),
            gapDp = 0f,
            baseSp = base,
            floorSp = ChipRowFit.FLOOR_SP.coerceAtMost(base),
            sep = ModelChip.SEPARATOR.trim().first(),
        ) { text, size -> measure(text, size) }

        Row(
            modifier = Modifier
                .height(ComposerControlSlotHeight)
                .widthIn(max = maxWidth)
                .clip(RoundedCornerShape(50))
                .background(theme.fill.copy(alpha = if (theme.dark) 0.55f else 0.7f))
                .onyxClickable(onClick = onClick)
                .semanticsLabel("Model")
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                text = plan.labels.first(),
                localize = false,
                style = theme.caption.copy(
                    fontSize = plan.fontSizeSp.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = labelInk,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!plan.plain) {
                OnyxIcon(Symbols.ChevronDown, tint = chevronInk, size = 11.dp)
            }
        }
    }
}

/**
 * **Le contenant de la carte — et la seule différence entre ses dispositions.**
 *
 * Sur la **plaque**, tant que le texte tient sur **une** ligne, le champ et ses commandes tiennent sur
 * une ligne (`.ask`, 64 px) : c'est un `Row`, le champ prend la place qui reste et les commandes la
 * leur. **Dès que le texte passe à plusieurs lignes** ([multiline]), les commandes descendent sur
 * **leur** rangée : le contenant devient un `Column`, le champ prend toute la largeur au-dessus, et
 * les commandes vivent en dessous — comme la référence du propriétaire. Dans la **carte**, c'est
 * toujours la disposition en `Column` (le champ au-dessus, les commandes en dessous). Les enfants
 * sont les mêmes, dans le même ordre, avec les mêmes gestes — seule la disposition les sépare.
 */
@Composable
private fun RowScope.ComposerBodyContainer(
    plate: Boolean,
    /**
     * **Le texte occupe-t-il plusieurs lignes ?** Alors les commandes descendent sur leur propre
     * rangée (voir [ComposerGrowth.stacksCommands]) et le champ prend toute la largeur. Faux au
     * repos : tout tient sur une ligne, et la plaque garde exactement sa géométrie de 140 dp.
     */
    multiline: Boolean = false,
    /** Ce qui se pose **avant** le champ sur la plaque — le « + » de la planche, et lui seul. */
    leading: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    if (plate && !multiline) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            // La marche de la planche sur la plaque (`gap:10px`), celle de la maison sur la carte :
            // c'est la même décision que dans la rangée des commandes, et c'est ce qui laisse le
            // champ assez large pour son libellé à 1,3× de police.
            horizontalArrangement = Arrangement.spacedBy(
                if (plate) PlaqueGeometry.FieldGap else ComposerRowGeometry.Gap,
            ),
        ) {
            leading()
            content(Modifier.weight(1f))
        }
    } else {
        Column(modifier = Modifier.weight(1f)) {
            content(Modifier.fillMaxWidth())
        }
    }
}

/**
 * Une pièce jointe de la bande, avec sa croix de retrait posée sur le coin haut-droit
 * (`AttachmentStrip` sur la référence).
 *
 * Une image se montre en vignette ; un fichier se montre en pastille, nom sur une ligne et
 * sous-titre « type · poids » sur la suivante.
 */
@Composable
private fun AttachmentStripTile(
    attachment: PendingAttachment,
    bytes: Long?,
    onRemove: () -> Unit,
    onOpen: () -> Unit = {},
) {
    Box {
        if (attachment.isImage) {
            ImageStripThumbnail(attachment = attachment, onClick = onOpen)
        } else if (com.onyx.app.ui.chat.FileViewers.isVideo(attachment.name, attachment.mimeType)) {
            VideoStripThumbnail(attachment = attachment, onClick = onOpen)
        } else {
            FileStripChip(attachment = attachment, bytes = bytes, onClick = onOpen)
        }
        // La croix est un disque sombre sous un « × » blanc, comme `xmark.circle.fill` teinté.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .size(22.dp)
                .onyxClickable(pressedScale = 0.9f, onClick = onRemove)
                // **La zone porte le nom, l'icône se tait.** Le libellé nomme la pièce jointe :
                // « Supprimer photo.jpg » dit laquelle des pièces part.
                .tapLabel(resolveOnyxText("Remove ${attachment.name}")),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
            )
            OnyxIcon(
                symbol = Symbols.Xmark,
                tint = Color.White,
                size = 10.dp,
            )
        }
    }
}

/** La vignette d'une image jointe : son image, décodée à la taille où elle se montre. */
@Composable
private fun ImageStripThumbnail(attachment: PendingAttachment, onClick: () -> Unit = {}) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = attachment.uri) {
        value = withContext(Dispatchers.IO) {
            decodeAttachmentThumbnail(context, attachment.uri, StripThumbnailPixels)
        }
    }

    Box(
        modifier = Modifier
            .size(StripThumbnailSize)
            .clip(RoundedCornerShape(OnyxSpace.FormCorner))
            .background(theme.fill)
            .onyxClickable(pressedScale = 0.97f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val shown = bitmap
        if (shown == null) {
            OnyxIcon(symbol = Symbols.Photo, tint = theme.tertiaryText, size = 22.dp)
        } else {
            Image(
                bitmap = shown,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** La pastille d'un fichier joint : son symbole, son nom, et « type · poids ». */
@Composable
private fun FileStripChip(attachment: PendingAttachment, bytes: Long?, onClick: () -> Unit = {}) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .height(45.dp)
            .background(theme.surfaceColor, RoundedCornerShape(OnyxSpace.FormCorner))
            .onyxClickable(pressedScale = 0.97f, onClick = onClick)
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OnyxIcon(symbol = fileSymbol(attachment), tint = theme.textColor, size = 16.dp)
        Column {
            BasicText(
                text = attachment.name,
                localize = false,
                style = theme.caption.copy(color = theme.textColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = attachmentSubtitle(attachment.mimeType, attachment.name, bytes),
                style = theme.caption2.copy(color = theme.secondaryText),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Le symbole d'un fichier, comme `AttachmentTile` sur la référence : l'onde pour le son, le document sinon. */
private fun fileSymbol(attachment: PendingAttachment): Int = when {
    attachment.mimeType.startsWith("audio/") -> Symbols.Waveform
    com.onyx.app.ui.chat.FileViewers.isVideo(attachment.name, attachment.mimeType) -> Symbols.Film
    else -> Symbols.Doc
}

/**
 * La vignette d'une vidéo jointe, dans la bande du composeur : sa première image sous un rond de
 * lecture. Une vidéo dont aucune image ne sort garde la pastille de fichier (jamais un carré vide).
 */
@Composable
private fun VideoStripThumbnail(attachment: PendingAttachment, onClick: () -> Unit = {}) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = attachment.uri) {
        value = com.onyx.app.ui.chat.VideoPoster.frame(context, attachment.uri, StripThumbnailPixels)
    }

    Box(
        modifier = Modifier
            .size(StripThumbnailSize)
            .clip(RoundedCornerShape(OnyxSpace.FormCorner))
            .background(theme.fill)
            .onyxClickable(pressedScale = 0.97f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val shown = bitmap
        if (shown == null) {
            OnyxIcon(symbol = Symbols.Film, tint = theme.tertiaryText, size = 22.dp)
        } else {
            Image(
                bitmap = shown,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Le rond de lecture se pose sur l'image, comme `play.fill` de la référence.
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            OnyxIcon(symbol = Symbols.Play, tint = Color.White, size = 11.dp)
        }
    }
}

/** Le rôle du bouton rond suit l'état, comme `ComposerView.trailingButtons`. */
@Composable
private fun TrailingComposerButton(
    state: String,
    size: androidx.compose.ui.unit.Dp,
    onSend: () -> Unit,
    onSteer: () -> Unit,
    onStop: () -> Unit,
    onTalk: () -> Unit,
    enabled: Boolean = true,
) {
    val theme = LocalOnyxTheme.current
    // L'encre du bouton est **mesurée** sur le disque **réellement peint** : le fond d'abord
    // ([ComposerCommands.disc] l'écarte du sol de la plaque), puis le glyphe dessus
    // ([ComposerCommands.inkOnDisc]). Quelle que soit l'apparence, le bouton existe à l'œil (3:1
    // contre la plaque) et son glyphe se lit (4,5:1 contre son disque) : le thème ne fait jamais
    // perdre la lecture, et aucune couleur n'est écrite à la main.
    val disc = ComposerCommands.disc(theme, theme.accent)

    when (state) {
        "steer" -> {
            val tint = ComposerCommands.disc(theme, theme.warning)
            ComposerCircleButton(
                symbol = Symbols.ArrowTurnDownRight,
                label = "Steer this reply",
                tint = tint,
                contentTint = ComposerCommands.inkOnDisc(theme, tint),
                size = size,
                disabledAlpha = ComposerCommands.INERT_ALPHA,
                onClick = onSteer,
            )
        }

        "after" -> ComposerCircleButton(
            symbol = Symbols.SendAfter,
            label = "Send after this reply",
            tint = disc,
            contentTint = ComposerCommands.inkOnDisc(theme, disc),
            size = size,
            disabledAlpha = ComposerCommands.INERT_ALPHA,
            onClick = onSend,
        )

        "stop" -> {
            // **La forme de l'arrêt est celle de la maison, pas un carré rouge inventé.**
            //
            // La planche **n'a pas d'état d'arrêt** : elle ne dessine que `.csend`, le cercle
            // d'envoi plein à l'accent avec une flèche. L'app a donc dû *proposer* cet état — et sa
            // première proposition (« un carré rouge qui arrive de nulle part », sur la capture du
            // propriétaire) sortait de la famille : le rouge est le signal de l'**échec** dans
            // toute l'app, pas celui d'un arrêt **demandé** ; et le carré plein jurait avec les
            // glyphes au trait de tous les autres boutons.
            //
            // L'arrêt prend donc **exactement** la forme de la maison, celle que la carte d'activité
            // dessine déjà pour le même geste ([TurnStopButton], `ThreadOrganisation.kt`) : le
            // **même cercle** que l'envoi (même créneau, même taille — c'est lui qui remplace
            // l'envoi), le **fond de contrôle** neutre de la famille de l'accent — **mesuré contre
            // la plaque** (`ComposerCommands.disc` : `theme.fill` seul valait 1,10:1 et le cercle
            // d'arrêt disparaissait en sombre) —, le glyphe d'arrêt à l'encre pleine du thème et
            // **aucun filet** — comme l'envoi. Un seul accent par écran : c'est l'envoi, et lui
            // seul, qui porte l'accent.
            val tint = ComposerCommands.disc(theme, theme.fill)
            ComposerCircleButton(
                symbol = Symbols.Stop,
                label = "Stop",
                tint = tint,
                contentTint = ComposerCommands.inkOnDisc(theme, tint, theme.textColor),
                size = size,
                disabledAlpha = ComposerCommands.INERT_ALPHA,
                onClick = onStop,
            )
        }

        "send" -> ComposerCircleButton(
            symbol = Symbols.ArrowUp,
            label = "Send",
            // **L'action principale est toujours pleine à l'accent — et elle se VOIT.** Elle se
            // peignait au fond de contrôle tant qu'aucun caractère n'était saisi : un disque gris
            // pâle à la flèche terne, que le propriétaire a lu « il a l'air désactivé ». La planche
            // montre l'inverse — `.csend{background:var(--accfill)}`, un cercle **plein à
            // l'accent**, une flèche blanche — et c'est bien elle, l'action principale de l'écran.
            // Elle se lit donc telle au repos **comme** en frappe.
            //
            // **Ce qui manquait : le disque n'était pas mesuré contre la plaque.** Sur un thème
            // dont l'accent est proche de la surface (le thème « Code » en clair : `#CBF0FF` sur
            // `#F9F9F5`), le cercle d'envoi mesurait **1,14:1** — il n'existait pas à l'œil, et la
            // zone du bouton était **vide** sur la capture. `ComposerCommands.disc` l'écarte du sol
            // du plus petit pas qui suffit (3:1) : un accent qui se voyait déjà ne bouge pas d'un
            // point, et l'accent qui se perdait devient un bouton.
            //
            // La flèche garde son encre mesurée sur l'accent peint (`ComposerCommands.inkOnDisc`),
            // et le bouton n'est plus jamais un cercle gris. Il reste **inerte** sans brouillon
            // (l'appui ne fait rien), mais il ne se lit plus « cassé ».
            tint = disc,
            contentTint = ComposerCommands.inkOnDisc(theme, disc),
            border = null,
            size = size,
            enabled = enabled,
            // **L'envoi inerte se peint comme l'envoi armé.** `enabled` ne vaut `false` que sur un
            // champ vide (`enabled = hasDraft || state != "send"`) : c'est le seul état où la
            // couche d'opacité de 35 % faisait disparaître **le disque et la flèche** ensemble
            // — mesuré **1,49:1** contre la plaque en sombre (Standard), 1,40:1 au pire des sept
            // thèmes, sous le plancher d'icône de 3:1. Le clic reste refusé ; la peinture, non.
            disabledAlpha = ComposerCommands.INERT_ALPHA,
            onClick = onSend,
        )

        else -> {
            val tint = ComposerCommands.disc(theme, theme.fill)
            ComposerCircleButton(
                symbol = null,
                label = "Talk",
                tint = tint,
                size = size,
                waveform = true,
                contentTint = OnyxContrast.dimmed(
                    OnyxContrast.over(theme.secondaryText, tint),
                    tint,
                    OnyxContrast.ICON_MIN + ComposerCommands.IDLE_MARGIN,
                ),
                border = OnyxSurfaces.hairline(theme),
                disabledAlpha = ComposerCommands.INERT_ALPHA,
                onClick = onTalk,
            )
        }
    }
}

@Composable
private fun IconSlot(
    symbol: Int,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp? = null,
    glyphSize: androidx.compose.ui.unit.Dp? = null,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .size(width = size, height = height ?: size)
            .onyxClickable(pressedScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        OnyxIcon(
            symbol = symbol,
            tint = tint ?: theme.secondaryText,
            size = glyphSize ?: size * 0.45f,
            contentDescription = label,
        )
    }
}

// MARK: - Le dépôt dans le composeur

/**
 * Le dépôt que le composeur accepte : les images et le texte, comme le fil (`ChatDropTarget`).
 *
 * Le nombre et le poids total sont vérifiés avant d'accepter ; au-delà, le dépôt est refusé et le
 * message le dit.
 */
@Composable
private fun rememberComposerDropTarget(
    attachmentCount: Int,
    knownBytes: Long,
    onAccept: (List<Uri>) -> Unit,
    onRefuse: (String) -> Unit,
): DragAndDropTarget {
    val context = LocalContext.current
    val count = rememberUpdatedState(attachmentCount)
    val bytes = rememberUpdatedState(knownBytes)
    val accept = rememberUpdatedState(onAccept)
    val refuse = rememberUpdatedState(onRefuse)
    return remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val items = event.toAndroidDragEvent().clipData ?: return false
                val uris = buildList {
                    for (index in 0 until items.itemCount) {
                        val uri = items.getItemAt(index).uri ?: continue
                        val mime = context.contentResolver.getType(uri).orEmpty()
                        if (mime.startsWith("image/") || mime.startsWith("text/")) add(uri)
                    }
                }
                if (uris.isEmpty()) return false
                if (count.value + uris.size > MaxAttachmentCount) {
                    refuse.value(TooManyAttachmentsMessage)
                    return true
                }
                val incoming = uris.sumOf { resolveAttachmentSize(context, it) ?: 0L }
                if (bytes.value + incoming > MaxAttachmentBytes) {
                    refuse.value(AttachmentsTooLargeMessage)
                    return true
                }
                accept.value(uris)
                return true
            }
        }
    }
}

/** Vrai si le dépôt porte une image ou du texte : alors le composeur peut le recevoir. */
private fun containsAttachableDrop(event: DragAndDropEvent): Boolean {
    val description = event.toAndroidDragEvent().clipDescription ?: return false
    return (0 until description.mimeTypeCount).any { index ->
        val mime = description.getMimeType(index).orEmpty()
        mime.startsWith("image/") || mime.startsWith("text/")
    }
}

/** Le poids d'une pièce jointe, quand l'appareil le connaît (`OpenableColumns.SIZE`). */
private fun resolveAttachmentSize(context: Context, uri: Uri): Long? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val column = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst() && column >= 0) cursor.getLong(column).takeIf { it >= 0 } else null
    }
}.getOrNull()

/** La vignette d'une image jointe, décodée à la taille où elle se montre. */
private fun decodeAttachmentThumbnail(context: Context, uri: Uri, maxPixels: Int): ImageBitmap? =
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (largest / sample > maxPixels * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
        }
    }.getOrNull()

/** Le type lisible d'un fichier : « PDF », sinon son extension en majuscules. */
private fun attachmentKindLabel(mimeType: String, name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when {
        mimeType == "application/pdf" || extension == "pdf" -> "PDF"
        extension.isNotEmpty() -> extension.uppercase(Locale.ROOT)
        else -> "File"
    }
}

/** « 2,4 Mo » : le poids lisible du jeu, partagé avec la feuille d'ajout (`AttachmentLimits`). */
internal fun attachmentSizeLabel(bytes: Long): String = AttachmentLimits.sizeLabel(bytes)

/** « PDF · 2,4 Mo » : le sous-titre d'un fichier joint, le poids quand il est connu. */
private fun attachmentSubtitle(mimeType: String, name: String, bytes: Long?): String {
    val kind = attachmentKindLabel(mimeType, name)
    return if (bytes != null) "$kind · ${attachmentSizeLabel(bytes)}" else kind
}

/** `ComposerPaste.isPicture` : une image n'est pas retenue si l'élément porte aussi du texte. */
internal fun isPictureClipboardItem(mimeType: String?, hasText: Boolean): Boolean =
    mimeType.orEmpty().startsWith("image/") && !hasText

/** Toutes les images du presse-papiers, jamais la miniature associée à un lien copié. */
internal fun clipboardImages(context: Context): List<Uri> {
    val manager = context.getSystemService(android.content.ClipboardManager::class.java) ?: return emptyList()
    val clip = manager.primaryClip ?: return emptyList()
    return buildList {
        for (index in 0 until clip.itemCount) {
            val item = clip.getItemAt(index)
            val uri = item.uri ?: continue
            val mime = context.contentResolver.getType(uri)
            if (isPictureClipboardItem(mime, hasText = item.text != null || item.htmlText != null)) add(uri)
        }
    }
}
