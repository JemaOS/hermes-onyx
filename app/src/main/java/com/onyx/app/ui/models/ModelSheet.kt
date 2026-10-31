package com.onyx.app.ui.models

import com.onyx.app.ui.design.OnyxSocle
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.design.socleShadow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import com.onyx.app.data.net.ChatControls
import com.onyx.app.data.net.ChatControlsApi
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.chat.RunChatControlsStore
import com.onyx.app.ui.chat.chatControlsFooter
import com.onyx.app.ui.design.DialogWindowBlur
import com.onyx.app.ui.design.FrostSurface
import com.onyx.app.ui.design.IdentifierText
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxSwitch
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.ModelContrast
import com.onyx.app.ui.design.ModelEntry
import com.onyx.app.ui.design.ModelOffer
import com.onyx.app.ui.design.ModelSelection
import com.onyx.app.ui.design.ProfileState
import com.onyx.app.ui.design.ProfileView
import com.onyx.app.ui.design.ReasoningLevels
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.design.readableInk
import com.onyx.app.ui.design.socleHeadingStyle
import com.onyx.app.ui.design.socleSectionStyle
import com.onyx.app.ui.design.frostedBar
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * La ligne **« Mode rapide »** de la feuille : l'état, le pied d'explication et le geste.
 *
 * `null` — rendu par [rememberModelFastMode] quand le serveur n'expose pas de mode rapide — veut
 * dire **la ligne n'est pas dessinée** : la feuille ne peint pas un interrupteur qui ne ferait
 * rien.
 */
data class ModelFastMode(
    val on: Boolean,
    val footer: String,
    val enabled: Boolean = true,
    val onToggle: (Boolean) -> Unit,
)

/**
 * **À quoi le choix s'applique** — la mention écrite sous le titre de la feuille.
 *
 * La référence écrit « Pour cette conversation » parce que, chez elle, le modèle se choisit **par
 * conversation**. L'app, elle, écrit le modèle **quelque part de précis** selon d'où la feuille est
 * ouverte : celui du bot pour un bot, celui du serveur pour une conversation simple. La mention dit
 * donc ce qui se passe **vraiment** — [CONVERSATION] garde le sens de la référence, les deux autres
 * nomment leur portée, et **en clair** : « Pour ce bot, partout » était une phrase à deviner, elle
 * devient « Pour toutes les conversations de ce bot ». Dessiner « Pour cette conversation » alors
 * que le choix écrit le modèle du bot pour **toutes** ses conversations serait exactement le
 * mensonge que le propriétaire interdit.
 */
enum class ModelScope(val labelKey: String) {
    CONVERSATION("For this conversation"),
    BOT("For every conversation of this bot"),
    SERVER("For this server"),
}

/**
 * Le **sélecteur de modèle**, à la tenue de la **feuille d'actions de la maison**.
 *
 * Ce lot ramène cette feuille sur l'anatomie de la feuille d'actions (`ChatActionSheet`) — celle que
 * le propriétaire reconnaît comme la sienne :
 *
 * 1. la **poignée** au centre, qui se tire vers le bas et referme la feuille, et **l'arête** du
 *    haut : la feuille est une surface **opaque** ([OnyxSurfaces.surfaceHigh]) aux **coins du haut**
 *    arrondis, cerclée du filet de la maison — elle ne flotte plus au milieu de l'écran ;
 * 2. le **grand titre** « Modèle » à gauche et la **portée du choix** en sous-ligne, **sur deux
 *    lignes** : la mention ne se bat plus avec la croix de fermeture pour la largeur ;
 * 3. les **pastilles** de réflexion et de fournisseur dans **une rangée fluide qui se replie**
 *    (`FlowRow`) : ce qui ne tient pas descend d'une rangée, **rien n'est tranché par le bord** et il
 *    n'y a plus de défilement horizontal à découvrir. La pastille retenue est **à l'accent plein**
 *    ([ModelContrast.activeChip]) ;
 * 4. un **filet en retrait**, puis le **champ de recherche** de la maison (creux + filet, invite
 *    courte et **entière**) ;
 * 5. les **rangées de modèle** : la **tuile arrondie de 44 dp** du gris de la maison — mesuré pour
 *    tenir 1,25:1 sur la feuille ([ModelContrast.tile]) — portant l'initiale **dedans**, le **nom**
 *    sur une ligne, le **fournisseur en sous-ligne**, le **prix en monospace à droite**, l'**étoile**
 *    en fin de rangée et la **coche** du modèle retenu ;
 * 6. des **filets en retrait, alignés sur le nom** — jamais de bord à bord, jamais sous la tuile ;
 * 7. une liste qui **défile dans la feuille** : la dernière rangée est entièrement atteignable, la
 *    feuille ne dépasse jamais l'écran.
 *
 * Le vocabulaire visuel reste celui d'Onyx : la vitre est `FrostSurface.SHEET`, rien de Material.
 *
 * Trois garde-fous y sont tenus : la **police 1,3×** (les rangées de pastilles se replient, la liste
 * défile), l'ouverture du **clavier** (la feuille monte au-dessus, rien ne passe dessous), et
 * **« Réduire les animations »** (le retour en place est figé).
 *
 * **Aucun prix n'est inventé** : [modelRowPrice] n'écrit un montant que si le serveur l'a donné, et
 * la sous-ligne montre alors le fournisseur **seul**. Voir `ServerPrice` et
 * `ModelCatalog.parsePricing`.
 */
@Composable
fun ModelSheet(
    models: List<com.onyx.app.ui.design.ModelChoice>,
    currentModel: String?,
    reasoning: String,
    favourites: Set<String>,
    recents: List<String>,
    onPickModel: (String) -> Unit,
    onPickReasoning: (String) -> Unit,
    onToggleFavourite: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    problem: String? = null,
    /**
     * Les profils Hermes que la feuille montre, **chacun avec l'état de sa liste de modèles**.
     *
     * C'est ce qui fait apparaître les modèles de **tous** les profils — le local du profil
     * `default`, les distants du profil `kilo` — et ce qui laisse un profil injoignable dans la
     * liste, marqué, **sans effacer les autres**. Vide : la feuille retombe sur une seule section
     * « Tous les modèles » (le cas d'un serveur unique).
     */
    profiles: List<ProfileView> = emptyList(),
    /** Ce que le choix change vraiment — la mention sous le titre. */
    scope: ModelScope = ModelScope.SERVER,
    /**
     * La **ligne « Mode rapide »**, quand le serveur l'expose ([rememberModelFastMode]).
     * `null` : ce transport n'a pas de mode rapide, la ligne **n'est pas dessinée**.
     */
    fastMode: ModelFastMode? = null,
    /**
     * Le **repli déclaré** du tour, quand la passerelle a basculé sur un autre modèle. La feuille
     * s'en sert pour **dire** le repli et pour ne pas peindre un prix ni un cran de réflexion qui ne
     * s'appliquent pas au modèle réellement **en force**.
     */
    fallback: com.onyx.app.ui.design.FallbackDeclaration? = null,
) {
    val theme = LocalOnyxTheme.current
    val reduceMotion = LocalReduceMotion.current
    val coroutines = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var providerFilter by remember { mutableStateOf(ModelSelection.AllProviders) }
    // La liste s'ouvre **repliée** sur les épinglés et les récents quand il y en a : la ligne
    // « Tous les modèles · N › » déplie le reste (elle se déplie d'elle-même quand il n'y a rien à
    // épingler, sinon la feuille n'aurait qu'une ligne).
    var unfold by remember { mutableStateOf(false) }
    val offset = remember { Animatable(0f) }
    val dragThreshold = with(LocalDensity.current) { ModelSheetDismissDrag.toPx() }
    // La ponctuation d'un montant reçu du serveur suit la langue de l'interface : « 0,30 $ » en
    // français, « $0.30 » sinon. Ce n'est jamais un chiffre de l'app.
    val french = OnyxLocalization.isFrench()
    // Le retour en place de la feuille : une petite fondue, **figée** sous « Réduire les
    // animations » — la même décision de mouvement que le reste de l'app.
    val settleSpec: androidx.compose.animation.core.AnimationSpec<Float> =
        if (reduceMotion) androidx.compose.animation.core.snap() else tween(durationMillis = 180)

    // Un repli **local** ne propose pas les crans du modèle configuré : on ne peint pas un cran qui
    // ne pilote rien (le plan les déclare alors manquants, plutôt que de les montrer actifs).
    val fallbackLocal = fallback?.servedIsLocal == true
    // La rangée « Réflexion » : les crans de la référence **que le serveur accepte**, dans SON
    // ordre, puis ceux que le serveur connaît en plus — jamais un cran dessiné pour rien.
    val reasoningPlan = remember(fallbackLocal) {
        if (fallbackLocal) ReasoningLevels.plan(exposed = emptyList()) else ReasoningLevels.plan()
    }
    // Le catalogue est agrégé **une fois** : un modèle = une rangée, et toutes ses offres dessous.
    val entries = remember(models) { ModelSelection.entries(models) }
    // Les pastilles de fournisseur se comptent sur le **catalogue entier** : une recherche ne doit
    // pas faire disparaître le filtre qu'on est en train de chercher.
    val facets = remember(entries) { ModelSelection.providers(entries) }
    val filtered = remember(entries, query) { ModelSelection.filter(entries, query) }
    val shown = remember(filtered, providerFilter) { ModelSelection.byProvider(filtered, providerFilter) }
    val sections = remember(shown, favourites, recents) {
        ModelSelection.sections(shown, favourites, recents)
    }
    // Le **reste**, groupé par profil : un en-tête par profil Hermes, pour que le local du profil
    // `default` et les distants du profil `kilo` se lisent chacun chez eux.
    val groups = remember(sections.rest) { ModelSelection.groups(sections.rest) }
    // Repliée ou dépliée : les épinglés et les récents restent, le reste attend la ligne
    // « Tous les modèles · N › ». Sans rien d'épinglé ni de récent, la liste s'ouvre dépliée — une
    // feuille qui ne montrerait qu'une ligne serait un écran vide.
    val expanded = unfold || ModelSelection.opensExpanded(sections)
    // Les profils que la feuille connaît, même ceux dont on n'a **aucun** modèle : ils apparaissent
    // avec leur état (« unreachable ») au lieu de disparaître.
    val shownProfiles = remember(profiles, entries) {
        val known = profiles.map { it.name }
        val extra = ModelSelection.groups(entries).map { it.profile }.filterNot { it in known }
        profiles + extra.map { ProfileView(it, ProfileState.READY) }
    }
    val surface = OnyxSurfaces.surfaceHigh(theme)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogWindowBlur(FrostSurface.SHEET)
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Le voile de la feuille : c'est lui qui assombrit la conversation, et dont le
                // calcul de contraste tient compte ([ModelContrast.SheetScrimAlpha]).
                .background(Color.Black.copy(alpha = ModelContrast.SheetScrimAlpha))
                // Rien ne passe sous le clavier : le voile réserve la hauteur de l'IME, donc la
                // feuille entière monte au-dessus de lui.
                .imePadding()
                // Un appui hors de la feuille la referme : le voile prend le geste, la feuille non.
                .onyxTappable(onClick = onDismiss),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // **Aucune rangée coupée** : la feuille ne prend jamais toute la hauteur de l'écran
                // (le voile reste visible en tête) et sa liste défile **dedans**.
                val sheetCap = maxHeight * ModelSheetHeightFraction
                Column(
                    modifier = modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .offset { IntOffset(0, offset.value.roundToInt().coerceAtLeast(0)) }
                        .heightIn(max = sheetCap)
                        // **L'ombre de la feuille, posée pour de vrai** : la planche donne à sa
                        // feuille (`.sh`, `hcss.py:256`) l'ombre en trois profondeurs du socle.
                        // Appelée ici, elle est réellement dessinée ; sans cet appel, la feuille
                        // reste plate et rien ne ramène `SocleShadowKt` dans le binaire.
                        .socleShadow(OnyxSocle.ShadowFar, OnyxSocle.RadiusPanel, theme.dark)
                        .frostedBar(FrostSurface.SHEET, RoundedCornerShape(OnyxSpace.PanelCorner), tint = OnyxSurfaces.surfaceHigh(theme))
                        .padding(top = OnyxSocle.Tight, bottom = OnyxSocle.Tight)
                        // **L'opaque d'abord, la vitre ensuite** : le panneau ne laisse rien
                        // traverser — la même surface que la feuille d'actions de la maison, et
                        // l'**arête** du filet de la maison sur les coins du haut.
                        .clip(ModelSheetShape)
                        .background(surface, ModelSheetShape)
                        .border(OnyxSpace.Stroke, OnyxSurfaces.hairline(theme), ModelSheetShape)
                        // La feuille n'est pas une zone de fermeture : appuyer entre deux rangées
                        // ne doit rien faire.
                        .onyxTappable(onClick = {})
                        .navigationBarsPadding(),
                ) {
                    // ---- 1. La poignée et le titre : le seul endroit où la feuille se glisse ----
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (offset.value > dragThreshold) {
                                            onDismiss()
                                        } else {
                                            coroutines.launch { offset.animateTo(0f, settleSpec) }
                                        }
                                    },
                                ) { _, dragAmount ->
                                    coroutines.launch {
                                        offset.snapTo((offset.value + dragAmount).coerceAtLeast(0f))
                                    }
                                }
                            },
                    ) {
                        ModelSheetHandle()
                        ModelSheetTitleRow(
                            title = "Model",
                            mention = scope.labelKey,
                            onDismiss = onDismiss,
                        )
                    }

                    // ---- 2 bis. Le repli : **dit**, jamais masqué -------------------------------
                    // Le modèle configuré a échoué et la passerelle a basculé ; le tour est servi
                    // par un autre modèle. On le nomme, avec son fournisseur réel, et on dit que son
                    // tarif par million ne s'applique pas.
                    if (fallback != null) {
                        BasicText(
                            text = OnyxLocalization.format(
                                "This turn is served by a fallback model: %1\$s. The configured model is %2\$s.",
                                fallback.servedLine(),
                                fallback.configuredModel,
                            ),
                            style = theme.caption.copy(color = theme.accent, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(
                                start = OnyxSocle.CardPadding,
                                end = OnyxSocle.CardPadding,
                                top = OnyxSocle.Tight,
                            ),
                        )
                        BasicText(
                            text = OnyxLocalization["Served locally by the fallback — no price per million tokens."],
                            style = theme.caption.copy(color = theme.tertiaryText),
                            modifier = Modifier.padding(
                                start = OnyxSocle.CardPadding,
                                end = OnyxSocle.CardPadding,
                            ),
                        )
                    }

                    // ---- 3. « Réflexion » : les crans du serveur, en rangée fluide -----------------
                    SectionLabel("Reasoning level")
                    ChipFlow(
                        labels = reasoningPlan.row.map { (value, label) -> ChipFace(value, label, true) },
                        selected = reasoning,
                        onPick = onPickReasoning,
                    )
                    // Ce que le serveur ne propose pas, **dit utilement** : le compte des crans, pas
                    // le nom d'un cran absent (une note de développement). Un repli local ne propose
                    // aucun cran : on le dit, et aucun n'est montré comme actif.
                    if (fallbackLocal) {
                        BasicText(
                            text = OnyxLocalization.format(
                                "The fallback model (%1\$s) does not offer the reasoning levels; none is shown as active.",
                                fallback?.servedLabel.orEmpty(),
                            ),
                            style = theme.caption.copy(color = theme.tertiaryText),
                            modifier = Modifier.padding(
                                start = OnyxSocle.CardPadding,
                                end = OnyxSocle.CardPadding,
                                top = OnyxSocle.Tight,
                            ),
                        )
                    } else reasoningCountLine(reasoningPlan)?.let { line ->
                        BasicText(
                            text = line,
                            localize = false,
                            style = theme.caption.copy(color = theme.tertiaryText),
                            modifier = Modifier.padding(
                                start = OnyxSocle.CardPadding,
                                end = OnyxSocle.CardPadding,
                                top = OnyxSocle.Tight,
                            ),
                        )
                    }

                    // ---- 3 bis. « Mode rapide » — seulement si le serveur l'expose ----
                    // Rien de peint quand `fastMode` est nul : c'est le cas d'un endpoint
                    // OpenAI-compatible nu, qui n'a pas de notion de niveau accéléré.
                    fastMode?.let { FastModeRow(it) }

                    // ---- 4. Le filet en retrait, puis le champ de recherche de la maison ---------
                    Spacer(Modifier.height(OnyxSocle.Loose))
                    OnyxRowDivider(inset = SheetInsetDp, endInset = SheetInsetDp)
                    ModelSearchField(query = query, onQuery = { query = it })

                    // ---- 5. Les pastilles de fournisseur, même rangée fluide ----------------------
                    if (facets.isNotEmpty()) {
                        ChipFlow(
                            labels = listOf(ChipFace(ModelSelection.AllProviders, "All", true)) +
                                facets.map { ChipFace(it.provider, it.providerName, false) },
                            selected = providerFilter,
                            onPick = { providerFilter = it },
                            modifier = Modifier.padding(top = OnyxSocle.Tight),
                        )
                    }

                    // ---- 6. et 7. La liste : favoris, récents, puis un profil après l'autre -------
                    when {
                        loading -> SheetNote("Loading models…")
                        problem != null -> SheetNote(problem)
                        models.isEmpty() && shownProfiles.isEmpty() -> SheetNote("This server lists no model.")
                        shown.isEmpty() -> SheetNote("No model matches.")
                        else -> LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                // La liste prend la place qui reste et **défile dedans** : la
                                // dernière rangée est atteignable, rien n'est coupé par le bas.
                                .weight(1f, fill = false)
                                .padding(top = OnyxSocle.Tight),
                            contentPadding = PaddingValues(bottom = OnyxSocle.Tight),
                        ) {
                            // ---- ÉPINGLÉS ---- (le titre est mis en petites capitales par
                            // `SectionHeader` ; le mot vient de la table de langue)
                            if (sections.favourites.isNotEmpty()) {
                                item { SectionHeader(OnyxLocalization["Pinned"]) }
                                items(sections.favourites, key = { "fav-" + it.listKey }) { entry ->
                                    ModelRow(entry, currentModel, favourites, french, onPickModel, onToggleFavourite, fallbackLocal)
                                }
                            }
                            // ---- RÉCENTS ----
                            if (sections.recents.isNotEmpty()) {
                                item { SectionHeader(OnyxLocalization["Recents"]) }
                                items(sections.recents, key = { "rec-" + it.listKey }) { entry ->
                                    ModelRow(entry, currentModel, favourites, french, onPickModel, onToggleFavourite, fallbackLocal)
                                }
                            }
                            // ---- La **dernière ligne** : « Tous les modèles · N › » ----
                            // Elle déplie le reste ; son compteur est celui des rangées qu'elle
                            // découvre, jamais le total des fournisseurs.
                            if (!expanded && sections.rest.isNotEmpty()) {
                                item(key = "all-models") {
                                    AllModelsRow(count = sections.rest.size) { unfold = true }
                                }
                            } else if (shownProfiles.isEmpty()) {
                                // Aucun profil connu (serveur unique) : une seule section, celle d'avant.
                                if (sections.rest.isNotEmpty()) {
                                    if (sections.favourites.isNotEmpty() || sections.recents.isNotEmpty()) {
                                        item { SectionHeader(OnyxLocalization["All models"]) }
                                    }
                                    items(sections.rest, key = { "all-" + it.listKey }) { entry ->
                                        ModelRow(entry, currentModel, favourites, french, onPickModel, onToggleFavourite, fallbackLocal)
                                    }
                                }
                            } else {
                                // **Un profil après l'autre** : le nom du profil en tête, ses modèles
                                // dessous. Un profil dont la liste n'a pas répondu garde sa place et
                                // **dit son état** — « unreachable » — sans effacer les autres.
                                val narrowing = query.isNotBlank() || providerFilter.isNotBlank()
                                shownProfiles.forEach { view ->
                                    val rows = groups.firstOrNull { it.profile == view.name }?.choices.orEmpty()
                                    val appeared = shown.any { ModelSelection.profileOf(it) == view.name }
                                    // Rien à montrer : soit ses modèles sont déjà plus haut (favoris,
                                    // récents), soit un filtre les a écartés — pas d'en-tête vide.
                                    if (rows.isEmpty() && (appeared || narrowing)) return@forEach
                                    item(key = "profile-" + view.name) {
                                        SectionHeader(view.name, subtitle = ModelSelection.profileSubtitle(view))
                                    }
                                    if (rows.isEmpty()) {
                                        item(key = "profile-state-" + view.name) {
                                            SheetNote(
                                                when (view.state) {
                                                    ProfileState.UNREACHABLE -> "This profile is unreachable."
                                                    ProfileState.PARTIAL -> "This profile's model list is unavailable."
                                                    ProfileState.READY -> "This profile lists no model."
                                                },
                                            )
                                        }
                                    } else {
                                        items(rows, key = { "p-" + it.listKey }) { entry ->
                                            ModelRow(entry, currentModel, favourites, french, onPickModel, onToggleFavourite, fallbackLocal)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// La géométrie de la feuille — des valeurs nommées, mesurées par le verrou de la marge
// ---------------------------------------------------------------------------------------------

/** Les deux coins **du haut** de la feuille : la famille des panneaux, comme la feuille d'actions. */
internal val ModelSheetShape = RoundedCornerShape(
    topStart = OnyxSpace.PanelCorner,
    topEnd = OnyxSpace.PanelCorner,
)

/** La part de l'écran que la feuille peut occuper : le voile reste visible en tête. */
internal const val ModelSheetHeightFraction = 0.92f

/** Au-delà de ce glissement vers le bas, la feuille se referme. */
private val ModelSheetDismissDrag = 90.dp

/** La poignée : les dimensions de la feuille d'actions de la maison. */
private val ModelSheetHandleWidth = 40.dp
private val ModelSheetHandleHeight = 5.dp

/** La hauteur minimale d'une cible de la feuille : aucune cible sous 48 dp. */
internal val ModelControlTarget = 48.dp

/** La hauteur minimale d'une rangée de modèle : elle porte une tuile de 44 dp et son air. */
internal val ModelRowMinHeight = 56.dp

/** La marge latérale d'une rangée : elle laisse voir le fond arrondi du modèle retenu. */
internal val ModelRowSideMargin = OnyxSocle.Tight

/** Le dégagement intérieur de la rangée (avant la tuile). */
internal val ModelRowPadding = OnyxSocle.Tight

/** L'écart entre la tuile et le nom — l'écart icône-libellé de la maison. */
internal val ModelTileGap = OnyxSpace.ThumbnailGap

/**
 * Le retrait du filet d'une rangée : il commence **après la tuile**, donc sur le nom — marge latérale,
 * rembourrage de la rangée, côté de la tuile, écart tuile-nom. Jamais de bord à bord, et jamais sous
 * la tuile.
 */
internal val ModelRowDividerInset =
    ModelRowSideMargin + ModelRowPadding + OnyxSpace.ThumbnailSize + ModelTileGap

/** Le retrait de fin du même filet : la même marge que le côté. */
internal val ModelRowDividerEndInset = ModelRowSideMargin + ModelRowPadding

/** Le retrait des filets de section de la feuille. */
private val SheetInsetDp = OnyxSocle.CardPadding

/** L'écart entre deux pastilles — horizontal et vertical (une rangée qui se replie). */
private val ChipGapDp = OnyxSocle.Tight

/** La teinte d'accent de la rangée retenue (`ModelContrast.chosenRow` doit mesurer la même). */
private const val ChosenRowTint = 0.10f

// ---------------------------------------------------------------------------------------------
// La tête de la feuille
// ---------------------------------------------------------------------------------------------

/** La poignée, centrée : elle dit qu'on peut faire glisser la feuille vers le bas. */
@Composable
private fun ModelSheetHandle() {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = OnyxSocle.Tight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = ModelSheetHandleWidth, height = ModelSheetHandleHeight)
                .clip(RoundedCornerShape(50))
                .background(theme.tertiaryText.copy(alpha = 0.55f)),
        )
    }
}

/**
 * Le grand titre et la mention, **sur deux lignes**.
 *
 * La mention était posée à droite du titre, entre lui et la croix : à la police 1,3× elle n'avait plus
 * la place et se faisait trancher (« Pour ce bot, partout » coupé). Elle passe donc **sous le titre**,
 * sur ses propres lignes, et la croix garde sa cible entière à droite du titre.
 */
@Composable
private fun ModelSheetTitleRow(title: String, mention: String, onDismiss: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = OnyxSocle.CardPadding, end = OnyxSocle.Tight, bottom = OnyxSocle.Tight),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = socleHeadingStyle(theme).copy(color = theme.textColor),
            )
            // La mention : ce que le choix change vraiment, en gris secondaire — deux lignes au plus,
            // jamais serrée contre la croix.
            BasicText(
                text = mention,
                style = theme.footnote.copy(color = theme.secondaryText),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = OnyxSpace.Hair),
            )
        }
        OnyxIcon(
            symbol = Symbols.Xmark,
            tint = theme.secondaryText,
            size = 15.dp,
            contentDescription = "Close",
            modifier = Modifier
                .size(ModelControlTarget)
                .onyxClickable(onClick = onDismiss),
        )
    }
}

/** Le petit titre d'une section de la feuille (la « Réflexion »). */
@Composable
private fun SectionLabel(text: String) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = text,
        style = socleSectionStyle(theme).copy(color = theme.secondaryText),
        modifier = Modifier.padding(
            start = OnyxSocle.CardPadding,
            end = OnyxSocle.CardPadding,
            top = OnyxSocle.Loose,
            bottom = OnyxSocle.Tight,
        ),
    )
}

/**
 * Le **champ de recherche** de la maison : le creux ([OnyxSurfaces.well]) et **le filet de la
 * maison**, pas la vitre épaisse qui l'entourait.
 *
 * L'invite est **courte et entière** (« Rechercher un modèle ») : l'ancienne phrase — « Rechercher un
 * modèle ou un fournisseur » — ne tenait pas dans la largeur du champ et se lisait « Rechercher un
 * modèle ou un fo… ». Elle reste vraie (le champ cherche aussi les fournisseurs), elle est simplement
 * plus courte que le champ.
 */
@Composable
private fun ModelSearchField(query: String, onQuery: (String) -> Unit) {
    val theme = LocalOnyxTheme.current
    val shape = RoundedCornerShape(OnyxSpace.FormCorner)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = OnyxSocle.CardPadding, end = OnyxSocle.CardPadding, top = OnyxSocle.Tight)
            .heightIn(min = ModelControlTarget)
            .clip(shape)
            .background(OnyxSurfaces.well(theme), shape)
            .border(OnyxSpace.Stroke, OnyxSurfaces.hairline(theme), shape)
            .padding(horizontal = OnyxSpace.Snug),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
    ) {
        OnyxIcon(Symbols.MagnifyingGlass, tint = theme.tertiaryText, size = 15.dp)
        OnyxTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = "Search a model",
            modifier = Modifier.weight(1f),
            style = theme.body,
        )
        if (query.isNotEmpty()) {
            OnyxIcon(
                symbol = Symbols.XmarkCircle,
                tint = theme.tertiaryText,
                size = 16.dp,
                contentDescription = "Clear the search",
                modifier = Modifier.size(ModelControlTarget).onyxClickable { onQuery("") },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Les pastilles — une seule rangée fluide, qui se replie
// ---------------------------------------------------------------------------------------------

/**
 * Une pastille à peindre : sa **valeur**, son **libellé**, et si le libellé passe par la table de
 * traduction (les crans de la maison, oui ; le nom d'un fournisseur, non — c'est un nom propre que le
 * serveur écrit).
 */
internal data class ChipFace(val value: String, val label: String, val localize: Boolean)

/**
 * **Une rangée de pastilles qui se replie** — le seul dessin de pastille de la feuille, pour la
 * réflexion comme pour les fournisseurs.
 *
 * Le défaut corrigé est le même dans les deux rangées : elles **défilaient horizontalement**, donc la
 * dernière pastille (« Très élevé », ou un fournisseur au nom long) était **tranchée par le bord de la
 * feuille** — à moitié lisible, et intouchable puisqu'on ne voyait pas qu'elle défilait. Une `FlowRow`
 * règle le fond du problème : ce qui ne tient pas **descend d'une rangée**, chaque pastille reste
 * entière, et il n'y a plus de geste à découvrir.
 *
 * La pastille retenue est **l'accent plein** ([ModelContrast.activeChip]), les autres le gris de la
 * famille des capsules. Toute la rangée est dans **la marge de la feuille**
 * ([OnyxSocle.CardPadding]) : aucune pastille ne touche le bord.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(
    labels: List<ChipFace>,
    selected: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return
    val theme = LocalOnyxTheme.current
    val active = ModelContrast.activeChip(theme)
    val activeInk = ModelContrast.activeChipInk(theme)
    val idle = ModelContrast.idleChip(theme)
    val idleInk = readableInk(theme.textColor, idle)

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSocle.CardPadding),
        horizontalArrangement = Arrangement.spacedBy(ChipGapDp),
        verticalArrangement = Arrangement.spacedBy(ChipGapDp),
    ) {
        labels.forEach { face ->
            val on = face.value == selected
            Box(
                modifier = Modifier
                    .heightIn(min = ModelControlTarget)
                    .clip(RoundedCornerShape(OnyxSpace.ControlCorner))
                    .background(if (on) active else idle)
                    .onyxTappable { onPick(face.value) }
                    .padding(horizontal = OnyxSpace.Snug, vertical = OnyxSocle.Tight),
                contentAlignment = Alignment.Center,
            ) {
                // Le libellé : traduit s'il vient de la maison (un cran de réflexion), puis
                // **abrégé au milieu** si la place manque — une pastille ne se coupe pas net.
                IdentifierText(
                    text = if (face.localize) OnyxLocalization.translate(face.label) else face.label,
                    style = theme.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = if (on) activeInk else idleInk,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// La liste
// ---------------------------------------------------------------------------------------------

/** Une note grise au milieu de la feuille (chargement, problème, liste vide). */
@Composable
private fun SheetNote(text: String) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = text,
        localize = false,
        style = theme.footnote.copy(color = theme.secondaryText),
        modifier = Modifier.padding(start = OnyxSocle.CardPadding, end = OnyxSocle.CardPadding, top = OnyxSocle.Loose, bottom = OnyxSocle.Loose),
    )
}

/**
 * Le nom d'une section, **en français**.
 *
 * Le profil s'appelle `default` côté serveur : la feuille l'écrivait tel quel, en capitales —
 * « DEFAULT » au milieu d'une feuille française. Le nom d'un profil se **traduit** quand il désigne
 * quelque chose de la maison (le profil par défaut, les favoris, les récents), et reste tel quel quand
 * c'est un nom propre (`kilo`, `work`…).
 */
internal fun sectionTitle(name: String, locale: Locale = OnyxLanguage.preferred): String {
    val trimmed = name.trim()
    val label = when {
        trimmed.equals("default", ignoreCase = true) -> OnyxLocalization.translate("Default", locale)
        trimmed.equals("favourites", ignoreCase = true) -> OnyxLocalization.translate("Favourites", locale)
        trimmed.equals("recents", ignoreCase = true) -> OnyxLocalization.translate("Recents", locale)
        trimmed.equals("all models", ignoreCase = true) -> OnyxLocalization.translate("All models", locale)
        else -> trimmed
    }
    return label.uppercase(locale)
}

/**
 * Le petit titre d'une section de la liste — le nom d'un **profil**, ou « Favoris » / « Récents ».
 *
 * [subtitle] ne s'écrit que quand la section a quelque chose à dire : l'état d'un profil dont la
 * liste de modèles n'a pas répondu (« unreachable », « model list unavailable »).
 */
@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    val theme = LocalOnyxTheme.current
    // L'encre de la section est **mesurée sur la feuille**, pas prise du jeton partagé : le tertiaire
    // du système y tombait à 4,41:1 (sous le plancher d'un mot). Voir [ModelContrast.sheetTertiaryInk].
    val ink = ModelContrast.sheetTertiaryInk(theme)
    Column(modifier = Modifier.padding(start = OnyxSocle.CardPadding, end = OnyxSocle.CardPadding, top = OnyxSocle.Loose, bottom = OnyxSpace.Hair)) {
        BasicText(
            text = sectionTitle(title),
            localize = false,
            style = socleSectionStyle(theme).copy(color = ink),
        )
        if (!subtitle.isNullOrBlank()) {
            BasicText(
                text = subtitle,
                localize = false,
                style = theme.caption.copy(color = ink),
            )
        }
    }
}

/**
 * Le compte des crans de réflexion **proposés par le serveur**, quand il n'en propose pas tous :
 * « 5 crans de réflexion sur 6 proposés par ce serveur ».
 *
 * Ce qui se compte, ce sont les crans **de la référence que le serveur sait piloter**
 * ([ReasoningLevels.mapping] : Minimal → Max, six crans). « Ultra » n'a pas de valeur côté serveur :
 * son absence n'est pas une propriété de *ce* serveur, elle ne se dit donc pas. Quand les six sont
 * là, la ligne **disparaît**.
 *
 * L'ancienne ligne — « Ce serveur ne propose pas tous les crans : Ultra. » — nommait un cran absent et
 * se lisait comme une note de développement. Ce qui est utile à la personne qui choisit, c'est
 * **combien** elle en a sous la main, pas le nom de celui qui manque.
 */
internal fun reasoningCountLine(
    plan: ReasoningLevels.Plan,
    locale: Locale = OnyxLanguage.preferred,
): String? {
    val missing = plan.missing.filter { ReasoningLevels.mapping[it] != null }
    if (missing.isEmpty()) return null
    val total = ReasoningLevels.mapping.size
    return OnyxLocalization.format(
        "%1\$d of %2\$d reasoning levels on this server.",
        total - missing.size,
        total,
        locale = locale,
    )
}

/** Le fournisseur écrit **en sous-ligne** de la rangée : ceux du serveur, sinon le profil. */
internal fun modelRowProviders(entry: ModelEntry): String =
    entry.offers.map { it.providerName }.distinct().joinToString(ModelOffer.ChipSeparator)
        .ifBlank { entry.profileName() }

/**
 * Le prix d'une rangée, **en monospace à droite** : le premier montant que le serveur a publié.
 * `null` quand il n'en a publié aucun — jamais « 0 $ », jamais un tiret de remplissage.
 */
internal fun modelRowPrice(entry: ModelEntry, french: Boolean): String? =
    entry.offers.firstNotNullOfOrNull { it.price?.line(french) }

/**
 * La ligne **« Mode rapide »** : le libellé, l'interrupteur de la maison, et le pied qui explique
 * ce que le mode fait vraiment — repris de `ChatControls.fastFooter`, jamais réinventé.
 */
@Composable
private fun FastModeRow(mode: ModelFastMode) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = OnyxSocle.CardPadding, end = OnyxSocle.CardPadding, top = OnyxSocle.Loose),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ModelRowTargetMin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OnyxSocle.IconLabelGap),
        ) {
            BasicText(
                text = "Fast mode",
                style = theme.body.copy(color = theme.textColor),
                modifier = Modifier.weight(1f),
            )
            OnyxSwitch(checked = mode.on, enabled = mode.enabled, onCheckedChange = mode.onToggle)
        }
        BasicText(
            text = mode.footer,
            localize = false,
            style = theme.caption.copy(color = theme.secondaryText),
            modifier = Modifier.padding(top = 2.dp, bottom = OnyxSocle.Tight),
        )
    }
}

/**
 * La **dernière ligne** de la liste : « Tous les modèles · N › ».
 *
 * Le compteur s'écrit en monospace (la méta de la maison) : c'est un **compteur**, comme le
 * `monospacedFootnote` du socle. L'appui **déplie le reste** — il ne change pas d'écran, la feuille
 * s'allonge.
 */
@Composable
private fun AllModelsRow(count: Int, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RowSideMargin)
            .heightIn(min = ModelRowTargetMin)
            .clip(RoundedCornerShape(OnyxSpace.ControlCorner))
            .onyxTappable(onClick = onClick)
            .padding(start = OnyxSocle.Tight, end = OnyxSocle.Tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.IconLabelGap),
    ) {
        BasicText(
            text = OnyxLocalization["All models"],
            style = theme.body.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        BasicText(
            text = "· $count",
            localize = false,
            style = theme.monospacedFootnote.copy(color = theme.secondaryText),
        )
        OnyxIcon(Symbols.ChevronRight, tint = ModelContrast.sheetTertiaryInk(theme), size = 14.dp)
    }
}

/**
 * Une rangée de la liste : la **tuile arrondie** portant l'initiale, le **nom**, le **fournisseur en
 * sous-ligne**, le **prix en monospace à droite**, la **coche** du modèle retenu et l'**étoile** des
 * favoris — dans cet ordre, celui de la feuille d'actions de la maison.
 *
 * **La tuile** est celle de la famille ([OnyxSpace.ThumbnailSize] = 44 dp,
 * [OnyxSpace.ThumbnailCorner]), au gris de la maison **mesuré** pour se voir sur la feuille
 * ([ModelContrast.tile], ≥ 1,25:1) : la vignette précédente était un carré de 38 dp peint d'une teinte
 * qui, sur la feuille, ne se distinguait pas du fond — la capture du propriétaire y lisait « une lettre
 * nue, sans carré ».
 *
 * **Le nom tient sur une ligne** et la sous-ligne porte le fournisseur : c'est ce qui les tient
 * alignés. L'ancienne rangée laissait le nom se replier sur deux lignes, et le fournisseur — en
 * pastilles sous lui — se décalait d'autant.
 *
 * La rangée retenue porte un **fond teinté d'accent à coins arrondis** ; les autres n'ont aucun fond.
 * Aucune bordure de carte : c'est un **filet en retrait, aligné sur le nom**, qui les sépare.
 */
@Composable
private fun ModelRow(
    entry: ModelEntry,
    currentModel: String?,
    favourites: Set<String>,
    french: Boolean,
    onPick: (String) -> Unit,
    onToggleFavourite: (String, Boolean) -> Unit,
    /** Un repli **local** est à l'œuvre : aucun prix par million ne s'applique, on ne le peint pas. */
    fallbackLocal: Boolean = false,
) {
    val theme = LocalOnyxTheme.current
    val surface = OnyxSurfaces.surfaceHigh(theme)
    val current = entry.id == currentModel
    val favourite = entry.id in favourites || entry.listKey in favourites
    // L'encre de la rangée est **mesurée** contre son fond réellement composé — la teinte du modèle
    // retenu ne fait jamais perdre la lecture (même règle que la barre d'onglets).
    val rowBackground = if (current) ModelContrast.chosenRow(theme) else surface
    val nameInk = readableInk(theme.textColor, rowBackground)
    val subInk = readableInk(theme.secondaryText, rowBackground)
    val tile = ModelContrast.tile(theme)
    val tileInk = ModelContrast.tileInk(theme)
    val price = if (fallbackLocal) null else modelRowPrice(entry, french)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ModelRowSideMargin)
                .clip(RoundedCornerShape(OnyxSpace.ControlCorner))
                .background(if (current) theme.accent.copy(alpha = ChosenRowTint) else Color.Transparent)
                .onyxTappable { onPick(entry.id) }
                .heightIn(min = ModelRowMinHeight)
                // Le rembourrage vertical laisse **48 dp** à l'intérieur de la rangée : les cibles
                // de la rangée (l'étoile) gardent leur hauteur pleine, elles ne sont pas rognées par
                // la hauteur de la rangée (audit : aucune cible sous 48 dp).
                .padding(start = ModelRowPadding, end = ModelRowPadding, top = OnyxSpace.Hair, bottom = OnyxSpace.Hair),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ModelTileGap),
        ) {
            // La tuile : un carré arrondi du gris de la maison, la première lettre **dedans**.
            Box(
                modifier = Modifier
                    .size(OnyxSpace.ThumbnailSize)
                    .clip(RoundedCornerShape(OnyxSpace.ThumbnailCorner))
                    .background(tile),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = entry.monogram,
                    localize = false,
                    style = theme.headline.copy(color = tileInk, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Le nom est un **identifiant** : il s'abrège au **milieu** ([IdentifierText]) plutôt
                // que de se couper à la fin — on lit toujours par quel fournisseur il commence et par
                // quoi il finit. Une ligne, donc aligné sur sa sous-ligne de fournisseur.
                IdentifierText(
                    text = entry.label,
                    style = theme.body.copy(fontWeight = FontWeight.SemiBold),
                    color = nameInk,
                    modifier = Modifier.fillMaxWidth(),
                )
                // La sous-ligne : le fournisseur que le serveur écrit, sur une ligne, abrégé au
                // milieu quand la place manque — jamais coupé au bord.
                IdentifierText(
                    text = modelRowProviders(entry),
                    style = theme.footnote,
                    color = subInk,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Le prix : **monospace**, à droite, tel que le serveur l'a publié.
            if (price != null) {
                BasicText(
                    text = price,
                    localize = false,
                    style = theme.monospacedFootnote.copy(color = subInk),
                    maxLines = 1,
                )
            }
            if (current) {
                // Le voyant d'état de la maison : la coche **garde l'accent**, sur une ligne
                // (le verrou `IconTileHueLockTest` la relit).
                OnyxIcon(Symbols.Checkmark, tint = theme.accentInk, size = 15.dp)
            }
            // L'étoile : elle se pose et se retire, et **se garde** d'une relance à l'autre. Sa cible
            // fait la taille d'un doigt, comme les autres contrôles de la feuille. Au repos, son
            // encre est **mesurée sur la feuille** ([ModelContrast.sheetTertiaryInk]) : le tertiaire
            // du jeton partagé y tombait à 4,41:1 et l'étoile se lisait à peine comme un contrôle.
            OnyxIcon(
                symbol = if (favourite) Symbols.StarFill else Symbols.Star,
                tint = if (favourite) readableInk(theme.accent, surface, 3.0) else ModelContrast.sheetTertiaryInk(theme),
                size = 17.dp,
                contentDescription = if (favourite) "Remove from favourites" else "Add to favourites",
                modifier = Modifier
                    .size(ModelControlTarget)
                    .onyxClickable(onClick = { onToggleFavourite(entry.id, !favourite) }),
            )
        }
        OnyxRowDivider(inset = ModelRowDividerInset, endInset = ModelRowDividerEndInset)
    }
}
/**
 * La pastille d'**information** d'une rangée, écrite en deux morceaux : le **nom du fournisseur**
 * au corps de la feuille, le **montant** en **monospace** (la règle de la maison pour un prix).
 *
 * La chaîne n'est jamais recomposée : c'est exactement celle que `ModelOffer.chip()` a reçue du
 * serveur, découpée à son séparateur. Une offre sans montant (le local) se lit donc telle quelle,
 * d'un seul morceau.
 */
private fun offerChipText(chip: String, theme: ResolvedTheme, ink: Color): AnnotatedString {
    val amount = chip.substringAfter(ModelOffer.ChipSeparator, missingDelimiterValue = "")
    if (amount.isEmpty() || amount == chip) return AnnotatedString(chip)
    val name = chip.substringBefore(ModelOffer.ChipSeparator)
    return buildAnnotatedString {
        withStyle(SpanStyle(color = ink)) { append(name) }
        withStyle(SpanStyle(color = ink)) { append(ModelOffer.ChipSeparator) }
        withStyle(SpanStyle(color = ink, fontFamily = theme.codeFont.fontFamily)) { append(amount) }
    }
}

/**
 * La ligne **« Mode rapide »** de la feuille — **seulement si le serveur l'expose**.
 *
 * Le mode rapide appartient à une **session**. Les transports à registre
 * ([Transport.isStateful] : les sessions, le dashboard `serve`) le gardent par connexion et
 * par conversation ([RunChatControlsStore]), et le Dashboard le porte sur la session
 * ([ChatControlsApi] → `config.set key=fast`). Un endpoint OpenAI-compatible nu, lui, n'a **aucun**
 * mode rapide : la fonction rend `null`, la feuille **ne dessine pas la ligne** — elle n'en peint
 * pas une qui ne ferait rien.
 */
@Composable
fun rememberModelFastMode(server: ServerConfig?, stored: String?): ModelFastMode? {
    if (server == null || !server.transport.isStateful) return null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connectionKey = server.configSignature
    val key = stored?.takeIf { it.isNotBlank() } ?: ModelFastModeFallbackKey
    val local = remember(context) { RunChatControlsStore(context) }
    // Le Dashboard porte l'état sur la session distante ; les sessions le gardent ici.
    val onServe = server.transport == Transport.HERMES_SERVE && !stored.isNullOrBlank()
    val rpc = remember(onServe, server.configSignature) { if (onServe) ServeRpc(server) else null }
    val api = remember(rpc) { rpc?.let(::ChatControlsApi) }
    DisposableEffect(rpc) { onDispose { rpc?.close() } }

    var controls by remember(server.configSignature, key) {
        mutableStateOf(if (onServe) null else ChatControls(fast = local.isFast(connectionKey, key)))
    }
    var busy by remember(server.configSignature, key) { mutableStateOf(false) }
    LaunchedEffect(server.configSignature, key, onServe) {
        if (onServe) {
            controls = runCatching { api?.read(stored)?.controls }.getOrNull()
        }
    }
    return ModelFastMode(
        on = controls?.fast == true,
        enabled = !busy && (!onServe || controls != null),
        // Le pied explique ce que le mode fait **vraiment** : celui de la maison, traduit.
        footer = OnyxLocalization[chatControlsFooter(controls, offersAutoApprove = onServe)],
        onToggle = { want ->
            if (!busy) {
                busy = true
                scope.launch {
                    try {
                        controls = if (onServe && api != null) {
                            api.setFast(want, stored)
                        } else {
                            local.setFast(want, connectionKey, key)
                        }
                    } finally {
                        busy = false
                    }
                }
            }
        },
    )
}

/** La clé de repli du mode rapide quand la conversation n'a pas encore de session sur le serveur. */
private const val ModelFastModeFallbackKey = "main"

/** Le dégagement intérieur d'une pastille d'offre (le nom du fournisseur et son montant). */
private const val ProviderChipInsetDp = 22f

/** L'écart entre deux pastilles d'information. */
private val OfferChipGapDp = OnyxSocle.Tight

/** Le dégagement intérieur d'une pastille d'information. */
private const val OfferChipInsetDp = 16f

/** La marge latérale d'une rangée de modèle : c'est elle qui laisse voir le fond arrondi. */
private val RowSideMargin = 8.dp

/**
 * Le retrait du filet d'une rangée (variante d'origine) : marge latérale, rembourrage, vignette,
 * écart icône-libellé.
 */
private val RowDividerInset = RowSideMargin + OnyxSocle.Tight + 38.dp + OnyxSocle.IconLabelGap

/**
 * La cible d'une ligne de la feuille : **48 dp au moins** — la ligne « Mode rapide » et la ligne
 * « Tous les modèles » s'y tiennent.
 */
private val ModelRowTargetMin = 48.dp
