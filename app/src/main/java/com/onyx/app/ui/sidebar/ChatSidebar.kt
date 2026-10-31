package com.onyx.app.ui.sidebar

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.onyx.app.data.model.Conversation
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.conversations.ConversationActionSheet
import com.onyx.app.ui.conversations.ConversationSheetRow
import com.onyx.app.ui.conversations.ConversationUndo
import com.onyx.app.ui.conversations.ConversationUndoBar
import com.onyx.app.ui.conversations.SelectionBar
import com.onyx.app.ui.conversations.conversationActivitySubtitle
import com.onyx.app.ui.conversations.conversationSheetRows
import com.onyx.app.ui.design.OnyxConfirm
import com.onyx.app.ui.design.FrostSurface
import com.onyx.app.ui.design.MotionFamily
import com.onyx.app.ui.design.GlassSurface
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.IdentityMark
import com.onyx.app.ui.design.CounterBadge
import com.onyx.app.ui.design.BadgeGeometry
import com.onyx.app.ui.design.flatActivePillColor
import com.onyx.app.ui.design.flatActivePillInk
import com.onyx.app.ui.design.FlatChipHeight
import com.onyx.app.ui.design.ConversationCover
import com.onyx.app.ui.design.OnyxSocle
import com.onyx.app.ui.design.FlatHairlineDp
import com.onyx.app.ui.design.flatIconInk
import com.onyx.app.ui.design.flatWellColor
import com.onyx.app.ui.design.sidebarPaneColor
import com.onyx.app.ui.design.sidebarPaneSurface
import com.onyx.app.ui.design.sidebarTileSurface
import com.onyx.app.ui.design.haloBackdrop
import com.onyx.app.ui.design.matiereBackdrop
import com.onyx.app.ui.design.ConversationCoverCard
import com.onyx.app.ui.design.GalerieSectionLabel
import com.onyx.app.ui.design.socleDisplayStyle
import com.onyx.app.ui.design.socleTitleInk
import com.onyx.app.ui.design.liquidGlass
import com.onyx.app.ui.design.LocalGlassScrollFreeze
import com.onyx.app.ui.design.GlassCircleButton
import com.onyx.app.ui.design.SectionAction
import com.onyx.app.ui.design.SectionActionButton
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPrompt
import com.onyx.app.ui.design.OnyxSegmented
import com.onyx.app.ui.design.OnyxBrandMark
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.OnyxRune
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.frostedBar
import com.onyx.app.ui.design.glassSurface
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.design.semanticsLabel
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.localization.resolveOnyxText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ReferencePalette
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.onyx.app.ui.design.IdentifierText
import com.onyx.app.ui.design.tapLabel

/**
 * Le **volet latéral** de la partie Chat, dans le vocabulaire visuel de la maison : des barres et
 * des vitres, jamais de Material.
 *
 * De haut en bas, la disposition de la référence :
 * 1. la tête — le nom de la passerelle et son **adresse avec son voyant**, la roue dentée à droite,
 *    et le **contrôle de sélection** quand l'écran en fournit un ;
 * 2. la recherche (« Search chats ») ;
 * 3. le sélecteur à deux positions **Chats / Bots** ([tab]) ;
 * 4. les **tuiles** vers les écrans de l'app, deux par rangée ;
 * 5. la rangée de **projets** (« All projects », chaque projet et son compte, puis « + ») ;
 * 6. la rangée de **filtres** (Tout / En cours / Vous attend) avec leurs comptes ;
 * 7. la **liste des conversations** — pastille, titre, âge à droite, seconde ligne ;
 * 8. en bas, « Nouvelle conversation » pleine largeur et le rond de l'initiale — remplacés, pendant
 *    une sélection, par la barre de sélection ([SelectionBar]) : archiver ou supprimer le lot.
 *
 * La position *Bots* prend la place des points 5 à 7 : les bots ne sont **jamais** dans la liste de
 * conversations, ils ont leur propre onglet (voir [BotsTab]).
 *
 * **La recherche n'est pas un filtre : c'est un mode.** Tant qu'elle est ouverte, les points 3 à 6
 * s'effacent (le sélecteur, les tuiles, les projets, les filtres) et ses résultats prennent la place
 * de la liste ; le pied, lui, ne bouge pas. `searching` dit ce mode, et c'est l'état du volet — donc
 * le retour — qui l'ouvre et le ferme.
 */
@Composable
internal fun ChatSidebar(
    /** Le nom de la passerelle : celui du serveur où l'app est signée. */
    gatewayName: String,
    /** Son adresse, montrée telle quelle sous le nom. */
    gatewayAddress: String,
    /**
     * Le voyant d'état : `true` joignable, `false` non joignable, `null` inconnu (le premier
     * contact n'a pas encore répondu). Vert / rouge / ambre — jamais une icône muette.
     */
    reachable: Boolean?,
    onOpenSettings: () -> Unit,
    searchQuery: String,
    onSearchQuery: (String) -> Unit,
    /**
     * Le volet est en **mode recherche** : le sélecteur, les tuiles, les projets et les filtres
     * s'effacent, les résultats prennent la place de la liste, et le pied reste à sa place.
     */
    searching: Boolean,
    /** Le premier appui sur le champ — ou son focus — **entre** en recherche. */
    onEnterSearch: () -> Unit,
    /**
     * La croix **extérieure** : elle **sort** de la recherche. C'est une action distincte du bouton
     * qui efface le texte du champ — l'un vide la requête et reste en recherche, l'autre rend la
     * liste des conversations.
     */
    onExitSearch: () -> Unit,
    tab: SidebarTab,
    onTab: (SidebarTab) -> Unit,
    /** Combien de bots attendent quelque chose : le libellé *Bots* le porte. */
    botsWaiting: Int = 0,
    /** Les tuiles vers les écrans de l'app ; l'appelant les construit (voir `OnyxApp`). */
    tiles: List<SidebarTile>,
    onOpenTile: (SidebarTile) -> Unit,
    /**
     * **Le mode édition de la grille** — le crayon, l'appui long, le rond de retrait, la
     * bibliothèque et « Réinitialiser ». Absent, la grille n'est pas personnalisable et reste
     * exactement ce qu'elle était : aucune fonction retirée, aucun geste ajouté.
     */
    tileEditor: SidebarTileEditor? = null,
    projects: List<SidebarProjectChip>,
    selectedProjectId: String?,
    onSelectProject: (String?) -> Unit,
    onNewProject: (() -> Unit)?,
    filter: SidebarFilter,
    onFilter: (SidebarFilter) -> Unit,
    filterCounts: Map<SidebarFilter, Int>,
    rows: List<SidebarRow>,
    onOpenRow: (SidebarRow, Rect?) -> Unit,
    onNewConversation: () -> Unit,
    onOpenAccount: () -> Unit,
    // ---- La liste des conversations, quand on la sélectionne ----
    /**
     * Le **contrôle de sélection** de la tête et l'état de la multi-sélection : le geste qui
     * remplace celui de l'écran retiré (« Sélectionner », puis archiver ou supprimer le lot).
     * Absent, la tête n'y met rien et la liste reste de simples rangées.
     */
    selection: SidebarSelectionState? = null,
    /** « Archiver N » de la barre de sélection : le lot coché, tel quel. */
    onArchiveSelected: (Set<String>) -> Unit = {},
    /** « Supprimer N » : la confirmation vit chez l'appelant, qui connaît le magasin. */
    onDeleteSelected: (Set<String>) -> Unit = {},
    /**
     * Ce qu'une rangée propose à l'**appui long** — le menu d'une conversation : marquer lu ou non
     * lu, épingler, archiver, supprimer. Absent, l'appui long n'ouvre rien.
     */
    rowActions: ((SidebarRow) -> SidebarRowActions?)? = null,
    // ---- La position *Bots* ----
    bots: List<SidebarBotCard>,
    onOpenBot: (SidebarBotCard) -> Unit,
    onNewBot: () -> Unit,
    rooms: List<SidebarRoomRow>,
    onOpenRoom: (SidebarRoomRow) -> Unit,
    onNewRoom: (() -> Unit)?,
    /** Le visage de chaque bot, par nom de profil ([com.onyx.app.ui.design.BotFaceResolver]). */
    faceCodeOf: (String) -> String? = { null },
    /**
     * L'appui long d'une carte de bot : son **menu** (les neuf entrées de la référence, puis celles
     * de la maison). Absent, l'appui long n'ouvre rien.
     */
    onMenuBot: ((SidebarBotCard, Rect) -> Unit)? = null,
    /** Un mot sur ce qui vient d'échouer sur un bot (dupliquer, repartir à zéro, supprimer), ou `null`. */
    botNotice: String? = null,
    onDismissBotNotice: (() -> Unit)? = null,
    /**
     * « Réessayer » : relit la liste des conversations et le roster. La **ligne d'état** ne le montre
     * que quand la passerelle **ne répond pas** — c'est là qu'on en a besoin.
     */
    onRetry: (() -> Unit)? = null,
    /**
     * **Depuis combien de secondes la copie locale est enregistrée**, ou `null` si ce téléphone n'a
     * jamais rien écrit. C'est l'âge que porte la phrase de la ligne d'état hors ligne : « Saved copy
     * · 9 h ago. » Voir [com.onyx.app.data.local.savedCopyAtMs] — la date vient du **fichier**, donc
     * elle survit à un redémarrage de l'app.
     */
    savedCopyAgeSeconds: Double? = null,
    /**
     * **L'écran que le volet porte**, ou `null` quand il montre sa racine (sa liste).
     *
     * C'est la demande du propriétaire : « quand je clique par exemple sur Aperçus, on reste dans
     * le volet ». Le volet est un **conteneur de navigation** — il garde sa place, et c'est son
     * **contenu** qui change. L'écran apporte sa propre barre (flèche de retour, titre, sous-titre),
     * exactement l'écran d'avant ; le pied du volet (« Nouvelle conversation » et le rond d'initiale)
     * ne bouge pas.
     */
    destination: String? = null,
    /** Le contenu de [destination], fourni par l'appelant (lui seul sait construire ses écrans). */
    destinationContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    // Le volet **portant un écran** : l'écran pose lui-même l'inset du haut (`statusBarsPadding`
    // dans sa propre barre), donc le volet ne le pose pas une seconde fois. Le pied, lui, reste au
    // volet, dans les deux cas.
    val screen = destinationContent?.takeIf { destination != null }
    val showingScreen = screen != null
    // Les états des **gestes** d'une rangée : la feuille ouverte par l'appui long, la confirmation
    // de suppression, l'invite de renommage, et le bandeau d'annulation. Ce sont les gestes que
    // l'écran des conversations portait ; le volet l'a remplacé, donc ils vivent ici.
    var sheetRow by remember { mutableStateOf<SidebarRow?>(null) }
    var confirmDelete by remember { mutableStateOf<SidebarRowActions?>(null) }
    var renameTarget by remember { mutableStateOf<SidebarRowActions?>(null) }
    var renameText by remember { mutableStateOf("") }
    var undo by remember { mutableStateOf<ConversationUndo?>(null) }
    // **La hauteur mesurée de la plaque du bas** (le pied du volet). La pastille de rattrapage s'en
    // sert pour se poser **au-dessus** d'elle : « au-dessus de la plaque, jamais par-dessus » ne peut
    // pas se deviner — le pied change de hauteur (libellé replié à 1,3×, encart du système), donc on
    // la **mesure** au lieu de l'estimer. Zéro tant que le pied n'a pas été composé.
    var plateHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val plateClearance = with(density) { plateHeightPx.toDp() }

    // Une action part **tout de suite** ; le bandeau laisse ensuite de quoi la défaire. C'est ce
    // qui distingue « l'action part au geste » d'une boîte de dialogue qui le retarderait.
    fun noteUndo(message: String, back: () -> Unit) {
        undo = ConversationUndo(message, back)
    }
    fun applyPin(actions: SidebarRowActions, pinned: Boolean) {
        actions.onTogglePin(pinned)
        noteUndo(if (pinned) OnyxLocalization["Pinned"] else OnyxLocalization["Unpinned"]) {
            actions.onTogglePin(!pinned)
        }
    }
    fun applyArchive(actions: SidebarRowActions, archived: Boolean) {
        actions.onToggleArchive(archived)
        noteUndo(if (archived) OnyxLocalization["Archived"] else OnyxLocalization["Unarchived"]) {
            actions.onToggleArchive(!archived)
        }
    }
    fun applyDelete(actions: SidebarRowActions) {
        actions.onDelete()
        noteUndo(OnyxLocalization["Deleted"]) { actions.onRestore() }
    }

    // **La position de la liste est gardée ici** (n° 7), hors de la liste : passer en recherche
    // remplace le contenu du volet, mais l'état de défilement des conversations reste vivant — au
    // retour, la liste se retrouve **là où elle était**, avec ses filtres. Les résultats ont le
    // leur, neuf.
    val listState = rememberLazyListState()
    val resultsState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // **La surface du volet.** En sombre c'est la vitre de la table ; en clair c'est une
                // plaque **quasi blanche**, sans bordure, que sa seule ombre détache (posée par la
                // coquille, [com.onyx.app.ui.sidebar.SidebarScaffold], là où le panneau a sa
                // silhouette). La couleur vient d'**une** fonction ([sidebarPaneColor]), donc le
                // panneau et la coquille ne peuvent pas diverger d'un cran.
                .background(sidebarPaneColor(theme))
                // **LA MATIÈRE.** La planche pose ses écrans sur un fond **travaillé** (dégradé
                // multi-arrêts, formes floues, grain, vignettage) ; l'app posait le sien sur un
                // aplat. C'est ce qui fait lire tout le reste — le verre ne se lit que s'il y a une
                // matière derrière (voir [Matiere]).
                .matiereBackdrop(theme)
                // **Le halo de la maison** : le volet porte la matière comme les autres écrans,
                // pour que sa vitre ait quelque chose à voir derrière elle (voir `onyxPage`).
                .haloBackdrop(theme)
                // **Le volet est une vitre** — en apparence **sombre**. En clair, la vitre opaque
                // est retirée : sur un panneau quasi blanc elle n'apportait que son voile, son
                // filet interne et son liseré, c'est-à-dire du bruit. Le choix est dans
                // [sidebarPaneSurface], un seul point de décision, donc le sombre ne peut pas être
                // dégradé en ré-habillant le clair.
                .sidebarPaneSurface()
                // Le volet est une **surface à lui** : il porte ses propres marges système, sinon sa
                // tête glisse sous la barre d'état et son pied sous la barre de navigation (mesuré :
                // le nom de la passerelle passait sous l'heure, « Nouvelle conversation » sous le
                // geste Android).
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            if (showingScreen) {
                // L'écran prend la place de la tête, de la recherche, du sélecteur et de la liste —
                // et **rien d'autre** ne change : le pied reste au volet, sous l'écran.
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) { screen() }
            } else {
                SidebarHeader(
                    name = gatewayName,
                    address = gatewayAddress,
                    reachable = reachable,
                    onOpenSettings = onOpenSettings,
                    // Le rond de l'initiale (le pied) et le nom dans la tête ouvrent la même feuille.
                    onOpenAccount = onOpenAccount,
                    // Le contrôle de sélection n'a de sens que sur la liste des conversations.
                    onSelect = selection
                        ?.takeIf { tab == SidebarTab.CHATS && !it.isSelecting }
                        ?.let { state -> { state.start() } },
                )
                // **La ligne d'état hors ligne**, sous la tête : une phrase grise et un « Try again »
                // discret. Elle **remplace** le rond de réessai qui vivait dans la tête — un second
                // chemin pour le même geste aurait été un doublon —, et elle n'apparaît que quand
                // la passerelle ne répond pas.
                if (reachable == false) {
                    SidebarStatusLine(
                        ageSeconds = savedCopyAgeSeconds,
                        onRetry = onRetry,
                    )
                }
                SidebarSearch(
                    query = searchQuery,
                    searching = searching,
                    onQuery = onSearchQuery,
                    onEnterSearch = onEnterSearch,
                    onExitSearch = onExitSearch,
                )
                // **En recherche, le sélecteur s'efface** : la recherche est le seul contenu
                // du volet (n° 7).
                if (!searching) {
                    SidebarSegment(tab = tab, botsWaiting = botsWaiting, onTab = onTab)
                }

                // **Le mode recherche remplace la liste** : ses tuiles, ses projets et ses
                // filtres appartiennent à la liste des conversations, qui n'est plus composée.
                // Sinon, la liste normale — jamais filtrée (`query = ""`), la requête ne valant
                // que pour la recherche.
                if (searching) {
                    SearchResults(
                        rows = rows,
                        query = searchQuery,
                        onOpenRow = { row -> onOpenRow(row, null) },
                        state = resultsState,
                        modifier = Modifier.weight(1f),
                    )
                } else when (tab) {
                    SidebarTab.CHATS -> ConversationList(
                        rows = rows,
                        // La liste n'est **jamais** filtrée : la requête ne vaut que pour la
                        // recherche, et sortir de la recherche la rend entière, telle qu'elle était.
                        query = "",
                        state = listState,
                        selecting = selection?.isSelecting == true,
                        selectedIds = selection?.selectedIds.orEmpty(),
                        onToggleRow = { row -> selection?.toggle(row.id) },
                        onOpenRow = onOpenRow,
                        // L'appui long ouvre la **feuille du bas** : le même composant partout.
                        onMenuRow = if (rowActions != null) { row, _ -> sheetRow = row } else null,
                        // Les deux pilules du glissement : l'action part tout de suite, le bandeau la
                        // défait. La suppression n'est **pas** dans le geste : elle vit dans l'appui
                        // long, avec sa confirmation ([confirmDelete]).
                        rowActions = rowActions,
                        onSwipePin = { actions -> applyPin(actions, !actions.conversation.pinned) },
                        onSwipeArchive = { actions -> applyArchive(actions, !actions.conversation.archived) },
                        // Les tuiles, les projets et les filtres **appartiennent à la liste** : ils
                        // défilent avec elle. Posés au-dessus, ils mangeaient toute la hauteur d'un
                        // téléphone en paysage (411 dp) et le pied — « Nouvelle conversation » — sortait
                        // de l'écran, la liste ne gardant plus rien. C'est la structure de la référence :
                        // la grille de tuiles est un élément de la liste.
                        header = {
                            item(key = "tiles") {
                                SidebarTileGrid(
                                    tiles = tiles,
                                    onOpenTile = onOpenTile,
                                    editor = tileEditor,
                                )
                            }
                            item(key = "projects") {
                                SidebarProjects(
                                    projects = projects,
                                    selectedId = selectedProjectId,
                                    onSelect = onSelectProject,
                                    onNewProject = onNewProject,
                                )
                            }
                            item(key = "filters") {
                                SidebarFilters(filter = filter, counts = filterCounts, onFilter = onFilter)
                            }
                            // **L'intitulé de section de la planche** : des petites capitales, un
                            // compteur en monospace. C'est le bloc `.secrow`/`.sec`/`.cnt` — la
                            // liste dit combien de conversations elle porte, et sous quel mot.
                            item(key = "section") {
                                GalerieSectionLabel(
                                    text = OnyxLocalization["Conversations"],
                                    count = rows.size,
                                    modifier = Modifier.padding(
                                        start = OnyxSocle.ScreenMargin,
                                        top = OnyxSocle.Loose,
                                        bottom = OnyxSocle.Tight,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )

                    SidebarTab.BOTS -> BotsTab(
                        bots = bots,
                        onOpenBot = onOpenBot,
                        onNewBot = onNewBot,
                        rooms = rooms,
                        onOpenRoom = onOpenRoom,
                        onNewRoom = onNewRoom,
                        faceCodeOf = faceCodeOf,
                        // L'appui long d'une carte : son menu, ancré à la carte.
                        onMenuBot = onMenuBot,
                        notice = botNotice,
                        onDismissNotice = onDismissBotNotice,
                        modifier = Modifier.weight(1f),
                    )
                }

            }
            if (selection != null && selection.isSelecting && tab == SidebarTab.CHATS) {
                // La barre de sélection **prend la place du pied** : ce sont les deux mêmes gestes,
                // sur la même ligne du bas, et « Nouvelle conversation » n'a pas à s'inviter pendant
                // qu'on choisit quoi archiver.
                val every = rows.map { it.id }.toSet()
                SelectionBar(
                    count = selection.selectedIds.size,
                    allSelected = every.isNotEmpty() && selection.selectedIds.containsAll(every),
                    onSelectAll = { selection.selectAll(every) },
                    onCancel = { selection.cancel() },
                    onArchive = { onArchiveSelected(selection.selectedIds) },
                    onDelete = { onDeleteSelected(selection.selectedIds) },
                )
            } else {
                SidebarFooter(
                    onNewConversation = onNewConversation,
                    onOpenAccount = onOpenAccount,
                    // Le **point d'état** posé sur le rond de l'initiale : la même joignabilité que le
                    // voyant de la tête, pour qu'on sache d'un coup d'œil, depuis le bas du volet,
                    // si la passerelle répond — sans remonter à la tête.
                    reachable = reachable,
                    // La hauteur du pied est **relevée** ici : c'est la plaque au-dessus de laquelle
                    // la pastille de rattrapage se pose (jamais dessus).
                    modifier = Modifier.onSizeChanged { plateHeightPx = it.height },
                )
            }
        }

        // **La pastille de rattrapage, posée AU-DESSUS de la plaque du bas.** Le dégagement vaut la
        // hauteur **mesurée** du pied plus la marche de la maison ([OnyxSpace.BlockGap] = 12) : la
        // pastille ne touche ni le bord, ni le bouton « Nouvelle conversation ». L'encart du système
        // est pris une fois ([navigationBarsPadding]) comme le fait le volet lui-même, donc la
        // pastille et le pied restent solidaires du bord système.
        undo?.let { state ->
            key(state) {
                ConversationUndoBar(
                    message = state.message,
                    onUndo = {
                        undo = null
                        state.undo()
                    },
                    onDismiss = { undo = null },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = plateClearance + OnyxSpace.BlockGap),
                )
            }
        }
    }

    // L'appui long d'une rangée ouvre **la feuille du bas** de la conversation — le même composant
    // que les autres listes du volet : son nom en tête, sa dernière activité dessous, puis une ligne
    // par action. Les entrées du menu flottant d'avant (marquer non lu, usage, copier le titre, se
    // taire, ouvrir ailleurs) y passent aussi : aucune fonction ne devient inatteignable.
    if (rowActions != null) {
        val row = sheetRow
        val actions = row?.let { rowActions(it) }
        if (row != null && actions != null) {
            ConversationRowMenu(
                actions = actions,
                onDismiss = { sheetRow = null },
                onRename = {
                    sheetRow = null
                    renameText = actions.conversation.title
                    renameTarget = actions
                },
                onTogglePin = {
                    sheetRow = null
                    applyPin(actions, !actions.conversation.pinned)
                },
                onToggleArchive = {
                    sheetRow = null
                    applyArchive(actions, !actions.conversation.archived)
                },
                onDelete = {
                    sheetRow = null
                    confirmDelete = actions
                },
                onToggleUnread = {
                    sheetRow = null
                    if (actions.unread) actions.onMarkRead() else actions.onMarkUnread()
                },
                onCopyTitle = {
                    sheetRow = null
                    actions.onCopyTitle()
                },
                onOpenElsewhere = {
                    sheetRow = null
                    actions.onOpenElsewhere()
                },
            )
        }
    }

    // La suppression d'une conversation demande une confirmation explicite, comme la suppression
    // groupée : et sa phrase dit la vérité — elle quitte ce téléphone, elle reste sur le serveur.
    // **La confirmation est une surface de la maison** ([OnyxConfirm]) : coins arrondis 28, verre,
    // arête claire, titre du socle, corps du socle, et deux actions dans nos formes — la boîte du
    // système (lignes internes, texte nu centré) n'a plus sa place ici.
    confirmDelete?.let { actions ->
        OnyxConfirm(
            title = OnyxLocalization["Delete this conversation?"],
            message = OnyxLocalization["It leaves this phone and your server. This can't be undone."],
            destructiveLabel = "Delete",
            cancelLabel = "Cancel",
            onDestructive = {
                confirmDelete = null
                applyDelete(actions)
            },
            onCancel = { confirmDelete = null },
        )
    }

    // « Renommer » : l'invite maison, un champ, Save ou Cancel — comme l'invite du fil.
    renameTarget?.let { actions ->
        val target = actions
        OnyxPrompt(
            title = "Rename conversation",
            placeholder = "Name",
            value = renameText,
            onValueChange = { renameText = it },
            confirmLabel = "Save",
            onConfirm = {
                renameTarget = null
                target.onRename(renameText.trim())
            },
            onDismiss = { renameTarget = null },
        )
    }
}

/**
 * La **multi-sélection** de la liste du volet : ce qui est coché, et l'ouverture du mode. L'état
 * vit dans le branchement ([ChatSidebarHost]) ; la mise en page ne fait que le lire et le passer.
 */
@androidx.compose.runtime.Stable
internal class SidebarSelectionState {

    /** Le mode est-il ouvert (le contrôle de la tête a été touché) ? */
    var isSelecting by mutableStateOf(false)
        private set

    /** Les identifiants cochés. */
    var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    fun start() {
        isSelecting = true
    }

    fun cancel() {
        isSelecting = false
        selectedIds = emptySet()
    }

    fun toggle(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    /** « Tout sélectionner » / « Tout désélectionner », selon ce qui est déjà coché. */
    fun selectAll(ids: Set<String>) {
        selectedIds = if (ids.isNotEmpty() && selectedIds.containsAll(ids)) emptySet() else ids
    }
}

/**
 * Ce qu'une rangée de conversation propose par ses **gestes** : l'appui long ouvre la feuille du
 * bas, le glissement vers la gauche découvre ses pavés. C'est le menu que l'écran des conversations
 * portait — les mêmes actions, dans le volet, pour qu'aucune ne disparaisse. La feuille et le
 * bandeau d'annulation se construisent à partir d'ici.
 */
internal data class SidebarRowActions(
    /** La conversation elle-même : la feuille en tire son nom, sa dernière activité et ses états. */
    val conversation: Conversation,
    /** La rangée est non lue : la feuille propose « Marquer comme lu », l'inverse sinon. */
    val unread: Boolean,
    /** La conversation est en sourdine : la ligne de silence le dit. */
    val muted: Boolean,
    val onMarkRead: () -> Unit,
    val onMarkUnread: () -> Unit,
    /** Épingle ou détache : l'action reçoit l'état visé, si bien qu'un retour puisse le défaire. */
    val onTogglePin: (Boolean) -> Unit,
    /** Archive ou désarchive, même règle. */
    val onToggleArchive: (Boolean) -> Unit,
    /** Renomme la conversation : le libellé définitif, déjà rogné. */
    val onRename: (String) -> Unit,
    /** « Créer un fil » : une branche locale (copie du transcript), ouverte à part. */
    val onCreateThread: () -> Unit,
    /** « Usage & cost » : la fiche d'usage de cette conversation. */
    val onOpenUsage: () -> Unit,
    /** « Copier le titre » : le titre dans le presse-papiers. */
    val onCopyTitle: () -> Unit,
    /** « Ouvrir ailleurs » : la conversation dans la fenêtre principale, sans la quitter d'ici. */
    val onOpenElsewhere: () -> Unit,
    val onDelete: () -> Unit,
    /** Remet la conversation supprimée : c'est le retour en arrière du bandeau d'annulation. */
    val onRestore: () -> Unit,
    /** Les lignes de silence (« Mute », « Mute for 1 hour »…) de cette conversation. */
    val muteRows: List<ConversationSheetRow>,
)

/**
 * Le menu d'une rangée : l'appui long ouvre **la feuille du bas** — le même composant que celui du
 * fil et des autres listes du volet, une seule feuille maison. Elle porte le nom de la conversation,
 * sa dernière activité, puis une ligne par action ; les lignes du silence et les compléments la
 * rejoignent, jamais une seconde feuille.
 */
@Composable
private fun ConversationRowMenu(
    actions: SidebarRowActions,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onToggleUnread: () -> Unit,
    onCopyTitle: () -> Unit,
    onOpenElsewhere: () -> Unit,
) {
    ConversationActionSheet(
        title = actions.conversation.title,
        subtitle = conversationActivitySubtitle(actions.conversation.updatedAt),
        rows = conversationSheetRows(
            conversation = actions.conversation,
            // La conversation ouverte se marque elle aussi : la quitter la marque lue de nouveau,
            // comme le faisait l'écran retiré.
            canMarkUnread = true,
            muted = actions.muted,
            unread = actions.unread,
            onRename = onRename,
            onCreateThread = {
                onDismiss()
                actions.onCreateThread()
            },
            onTogglePin = onTogglePin,
            onToggleArchive = onToggleArchive,
            onDelete = onDelete,
            onToggleUnread = onToggleUnread,
            onUsage = {
                onDismiss()
                actions.onOpenUsage()
            },
            onCopyTitle = onCopyTitle,
            onOpenElsewhere = onOpenElsewhere,
            // Les lignes du silence ferment la feuille en agissant, comme les autres.
            muteRows = actions.muteRows.map { row ->
                row.copy(onSelect = {
                    onDismiss()
                    row.onSelect()
                })
            },
        ),
        onDismiss = onDismiss,
    )
}

// ---------------------------------------------------------------------------------------------
// 1. La tête
// ---------------------------------------------------------------------------------------------

/**
 * La tête du volet : le nom de la passerelle, son adresse et le **voyant** qui dit si elle répond,
 * la roue dentée à droite.
 *
 * Le voyant est la seule couleur de la tête : vert quand la passerelle répond, rouge quand elle ne
 * répond pas, ambre quand on ne sait pas encore. Il est double — la pastille **et** un état lu par
 * les lecteurs d'écran —, sinon la couleur porterait seule une information.
 *
 * Le bloc du nom **et** de l'adresse ouvre la feuille de compte : c'est l'état de la connexion qui
 * est écrit là, et c'est le même geste que le rond de l'initiale du pied. La roue dentée, elle,
 * garde son chemin — les Réglages en un appui.
 */
@Composable
private fun SidebarHeader(
    name: String,
    address: String,
    reachable: Boolean?,
    onOpenSettings: () -> Unit,
    /**
     * Le contrôle de sélection de la liste (`checkmark.circle`), quand l'écran en fournit un : le
     * pendant du bouton de la barre de l'écran retiré. Absent, la tête n'a que la roue.
     */
    onSelect: (() -> Unit)? = null,
    /**
     * Le rond de l'initiale — et le nom dans la tête — ouvrent cette feuille de compte (n° 8).
     */
    onOpenAccount: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    // Le fond réel de la tête : le **panneau**, pas la page. En sombre les deux valent la même
    // couleur ; en clair le panneau est quasi blanc, et une encre mesurée contre la page blanche y
    // tomberait du mauvais côté du seuil (ou, ici, resterait juste — mais par accident).
    val pane = sidebarPaneColor(theme)
    val dot = signalInk(
        when (reachable) {
            true -> theme.success
            false -> theme.error
            null -> theme.warning
        },
        pane,
    )
    val word = when (reachable) {
        true -> "Reachable"
        false -> "Unreachable"
        null -> "Checking"
    }
    // Le libellé annoncé du bloc cliquable : le nom, puis l'adresse — ce que la tête écrit.
    val accountLabel = listOf(name, address)
        .filter { it.isNotBlank() }
        .joinToString(", ")
        .ifBlank { name }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = OnyxSocle.ScreenMargin,
                end = OnyxSocle.ScreenMargin,
                top = OnyxSocle.ScreenMargin,
                bottom = OnyxSocle.Tight,
            )
            .semantics { stateDescription = word },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.BlockGap),
    ) {
        // **Le logo de l'application, dans la tête du volet.** La tête ne portait que le **mot**
        // (« Onyx ») comme marque : un mot est un titre, pas un logo — il se lit, il ne se
        // reconnaît pas. Le signe est posé à gauche du bloc, et le bloc garde exactement ce qu'il
        // portait : le nom, l'adresse, et **son point d'état**.
        //
        // Ce n'est plus une icône d'accent : c'est la **marque du lanceur** — la plaque sombre,
        // le cercle et la rune — aux couleurs de l'icône, **identiques en clair et en sombre**.
        // Aucune teinte de thème n'entre dans ce dessin (voir `OnyxBrand`).
        OnyxBrandMark(
            size = OnyxRune.Mark,
            description = resolveOnyxText("Onyx"),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                // Le nom et l'adresse **sont** l'état de la connexion : les toucher ouvre la feuille
                // de compte, comme le rond de l'initiale. La cible fait la marche de la maison —
                // le bloc est petit, le doigt non.
                .defaultMinSize(minHeight = MinTarget)
                .onyxTappable(onClick = onOpenAccount)
                // **Le nom du contrôle, c'est ce qu'il montre** : le nom de la passerelle et son
                // adresse. Sans lui, le relevé d'arbre rend un nœud cliquable au texte vide.
                .tapLabel(accountLabel),
        ) {
            BasicText(
                text = name,
                // **Le grand titre de la planche** : 30 sp, −0,032 em, ExtraBold ([OnyxSocle.Display]).
                // La tête du volet portait 17 sp : ce n'est pas un grand titre.
                //
                // **L'ENCRE DU TITRE.** `socleDisplayStyle` ne porte que la **forme** ; sans couleur,
                // la Fondation écrit du **noir** — le nom de la passerelle restait donc noir en
                // apparence sombre (noir sur la page de nuit, illisible : « le titre de l'app ne
                // suit pas le thème »). Un titre prend l'encre de la maison ([socleTitleInk]).
                style = socleDisplayStyle(theme).copy(color = socleTitleInk(theme)),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
                modifier = Modifier.padding(top = 1.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(dot, CircleShape),
                )
                IdentifierText(
                    text = address,
                    style = theme.caption.copy(color = mutedInk(pane)),
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        // **Un SEUL rond, à l'encre neutre, sur blanc** — le bloc `.press` de la planche
        // (`hcss.py:68` : 48 × 48, filet de 1 px, fond plein, encre d'icône **neutre**).
        // La tête du volet portait **deux** ronds teintés d'accent (la roue **et** le contrôle de
        // sélection, tous deux à l'accent) : la planche n'en montre qu'un, et c'est celui-ci.
        // Le contrôle de sélection n'est **pas** retiré du code — il n'est plus dessiné ici ; il
        // reste atteignable par l'appui long sur une rangée, et la liste est remise au propriétaire.
        GlassCircleButton(
            symbol = Symbols.GearshapeSingle,
            label = "Settings",
            size = OnyxSocle.TouchTarget,
            iconSize = 20.dp,
            tint = mutedIconInk(pane),
            onClick = onOpenSettings,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 1 bis. La ligne d'état hors ligne
// ---------------------------------------------------------------------------------------------

/**
 * **Ce que le volet dit quand la passerelle ne répond plus** : une phrase grise, et un « Try again »
 * discret — rien d'autre.
 *
 * C'est l'infléchissement demandé : un bandeau rouge, un code d'erreur ou une exception recopiée
 * **n'ont rien à faire dans un volet**, dont la liste reste lisible parce qu'elle est enregistrée
 * sur ce téléphone. La phrase dit donc les deux choses utiles, dans cet ordre — *ce qu'on a* (« une
 * copie enregistrée il y a 9 h ») et *ce qui manque* (« le serveur est injoignable ») —, et le seul
 * geste offert est le réessai, dans une **pilule à bordure fine** : c'est la seule trait de tout le
 * volet clair, et il marque le seul endroit où l'on peut agir.
 *
 * L'âge vient du **fichier de la copie locale** ([com.onyx.app.data.local.savedCopyAtMs]), jamais
 * d'une horloge de session : une copie vieille de trois jours ne se dit pas « il y a 2 secondes »
 * parce que l'app vient de démarrer.
 */
@Composable
private fun SidebarStatusLine(
    ageSeconds: Double?,
    onRetry: (() -> Unit)?,
) {
    val theme = LocalOnyxTheme.current
    val pane = sidebarPaneColor(theme)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = OnyxSpace.BarInset,
                end = OnyxSpace.Snug,
                top = OnyxSpace.Hair,
                bottom = OnyxSpace.Half,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.BlockGap),
    ) {
        BasicText(
            text = savedCopyNotice(ageSeconds),
            // La phrase est **déjà** dans la langue du lecteur (elle est assemblée par le modèle,
            // parce que son âge est variable) : la repasser dans le traducteur ne ferait que risquer
            // de la relire comme une clé.
            localize = false,
            style = theme.footnote.copy(color = mutedInk(pane)),
            // **Quatre lignes** : la phrase du propriétaire est longue, et à police 1,3× dans un
            // volet de 340 dp elle en demande quatre à côté du bouton. À trois, la dernière ligne
            // était **coupée** (« … injoignable pour le » — mesuré sur la capture) : une phrase
            // tronquée serait exactement le défaut que cette ligne répare.
            maxLines = 4,
            // Une ellipse avant une coupe sèche : même en quatre lignes, un mot ne doit pas être
            // brisé (« … injoignable pour le » était la coupe mesurée).
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            // La pilule discrète : bordure fine, pas de fond, l'accent en encre. Elle ne crie pas —
            // c'est ce que « discret » veut dire, et c'est la seule bordure du volet au clair.
            BasicText(
                text = SAVED_COPY_RETRY_LABEL,
                style = theme.footnote.copy(
                    // L'accent **mesuré** : le bleu de la maison sur un panneau quasi blanc vaut
                    // 3,4:1 — sous le seuil du texte. Il est écarté du fond du plus petit pas qui
                    // suffit, donc il reste bleu et devient lisible.
                    color = sidebarInk(theme.accent, pane, OnyxContrast.TEXT_MIN),
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .defaultMinSize(minHeight = 32.dp)
                    .border(FlatHairlineDp, theme.separator, CircleShape)
                    .onyxTappable(onClick = onRetry)
                    .tapLabel(resolveOnyxText(SAVED_COPY_RETRY_LABEL))
                    .padding(horizontal = OnyxSpace.Snug),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 2. La recherche
// ---------------------------------------------------------------------------------------------

/**
 * La capsule « Search chats », en verre, dans une cible de 44 dp — et **le mode recherche**.
 *
 * Le champ porte trois choses, dans cet ordre : la loupe, le texte, puis **deux actions
 * distinctes**.
 * - **Le premier appui entre en recherche** (le champ comme son focus, les deux chemins mènent au
 *   même mode). En recherche, la capsule s'entoure de l'**anneau de focus** — le liseré d'accent de
 *   la maison —, le seul état qui dise « c'est ici que la frappe va ».
 * - **La croix intérieure efface le texte** : elle vide la requête et **reste** en recherche.
 * - **La croix extérieure sort de la recherche** : elle rend la liste des conversations. Les deux ne
 *   font pas la même chose, et ne sont donc pas au même endroit.
 */
@Composable
private fun SidebarSearch(
    query: String,
    searching: Boolean,
    onQuery: (String) -> Unit,
    onEnterSearch: () -> Unit,
    onExitSearch: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val shape = RoundedCornerShape(OnyxSpace.PanelCorner)
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // **Sortir de la recherche relâche le champ.** Sans ça, le champ restait focalisé (curseur et
    // clavier compris) après la sortie, et un nouvel appui sur lui **ne changeait plus l'état du
    // focus** — donc n'entrait plus en recherche : le champ devenait inerte au second essai. La
    // sortie le rend au repos, le prochain appui le reprend.
    LaunchedEffect(searching) {
        if (!searching) focusManager.clearFocus()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSpace.Snug)
            .defaultMinSize(minHeight = MinTarget)
            // L'anneau **avant** la vitre : le liseré se lit sur la page, pas sous le voile.
            .then(if (searching) Modifier.border(SearchFocusRing, theme.accent, shape) else Modifier)
            .frostedBar(FrostSurface.SEARCH_FIELD, shape)
            // Le premier appui **entre** en recherche : c'est le geste de la référence. Il ne prend
            // pas la frappe au champ — Compose donne l'appui au plus profond, donc au `BasicTextField`
            // dès qu'il est touché ; celui-ci ne prend que le reste de la capsule (la loupe, les
            // marges), qui doit mener au même endroit.
            .onyxTappable(onClick = {
                focus.requestFocus()
                onEnterSearch()
            })
            // La capsule **fusionne son champ** : le nœud cliquable n'a pas de nom, mais le champ
            // qu'il contient en a un (le texte d'invite est un nœud voisin, d'où la fusion).
            // `mergeDescendants` fait exactement cela — un nœud, le nom du champ, et le champ
            // reste saisissable (la fusion ne touche que l'arbre d'accessibilité).
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = OnyxSpace.CardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.IconLabelGap),
    ) {
        OnyxIcon(symbol = Symbols.MagnifyingGlass, tint = mutedIconInk(theme.pageColor), size = 17.dp)
        Box(modifier = Modifier.weight(1f).padding(vertical = OnyxSpace.Snug)) {
            if (query.isEmpty()) {
                BasicText(text = "Search chats", style = theme.body.copy(color = sidebarFieldInk(theme)))
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                textStyle = TextStyle(color = theme.textColor, fontSize = OnyxSocle.Body),
                singleLine = true,
                cursorBrush = SolidColor(theme.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    // Le clavier ou un focus programmé entrent par le même chemin que l'appui.
                    .onFocusChanged { if (it.isFocused) onEnterSearch() }
                    // Le champ **lui-même** : le texte d'invite est un nœud voisin, donc le champ
                    // arrivait à l'arbre sans nom.
                    .tapLabel(resolveOnyxText("Search chats")),
            )
        }
        if (query.isNotEmpty()) {
            // **Effacer** — la requête, et rien d'autre : on reste en recherche, prêt à retaper.
            Box(
                modifier = Modifier
                    .size(MinTarget)
                    .onyxTappable(onClick = { onQuery("") })
                    .semanticsLabel("Clear search"),
                contentAlignment = Alignment.Center,
            ) {
                OnyxIcon(symbol = Symbols.XmarkCircle, tint = mutedIconInk(theme.pageColor), size = 18.dp)
            }
        }
        if (searching) {
            // **Sortir** — l'autre action, à l'extérieur du champ : elle referme la recherche et
            // rétablit la liste des conversations.
            Box(
                modifier = Modifier
                    .size(MinTarget)
                    .onyxTappable(onClick = onExitSearch)
                    .semanticsLabel("End search"),
                contentAlignment = Alignment.Center,
            ) {
                OnyxIcon(symbol = Symbols.Xmark, tint = mutedIconInk(theme.pageColor), size = 17.dp)
            }
        }
    }
}

/** Le trait de l'anneau de focus : assez épais pour se lire, jamais un cadre. */
private val SearchFocusRing = 1.5.dp

// ---------------------------------------------------------------------------------------------
// 2 bis. Les résultats de la recherche
// ---------------------------------------------------------------------------------------------

/**
 * **Les résultats de la recherche** prennent la place de la liste des conversations.
 *
 * C'est ce qui fait du mode recherche un **écran** et non un filtre : les tuiles, les projets, le
 * sélecteur et les filtres ne sont plus là — ils appartiennent à la liste des conversations, qui
 * n'est plus composée. Rien ne correspond : l'état vide, centré.
 */
@Composable
private fun SearchResults(
    rows: List<SidebarRow>,
    query: String,
    onOpenRow: (SidebarRow) -> Unit,
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = state,
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        if (rows.isEmpty()) {
            item(key = "empty") {
                SearchEmptyState(query = query, modifier = Modifier.fillParentMaxSize())
            }
        }
        items(rows, key = { it.id }) { row ->
            ConversationRow(row = row, selecting = false, selected = false, onClick = { onOpenRow(row) })
        }
    }
}

/**
 * L'état vide de la recherche, **centré** : la vignette, le titre, et la phrase qui dit ce que la
 * recherche interroge.
 *
 * La phrase n'est pas un ornement : c'est le seul endroit où la personne apprend que sa requête a
 * été comparée aux titres **et** au texte des messages. Recopier celle de la référence aurait promis
 * les identifiants de session, que ce code ne lit pas — [SearchScopeSentence] est la vérité de
 * `sidebarMatches`, et un test la mesure.
 */
@Composable
private fun SearchEmptyState(query: String, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    val asked = query.isNotBlank()
    Column(
        modifier = modifier.padding(horizontal = OnyxSpace.ScreenMargin),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SearchEmptyGlyph()
        BasicText(
            // Sans requête, il n'y a pas « rien trouvé » : il n'y a rien à chercher.
            text = if (asked) SearchEmptyTitle else "No conversations yet.",
            // `textAlign` voyage dans le style : c'est la seule mise en page dont l'état vide centré
            // a besoin, et elle ne demande pas d'ajouter un paramètre au `BasicText` de la maison.
            style = theme.subheadline.copy(
                color = theme.textColor,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.padding(top = OnyxSpace.BlockGap, bottom = OnyxSpace.Hair),
        )
        if (asked) {
            BasicText(
                text = SearchScopeSentence,
                style = theme.caption.copy(
                    color = mutedInk(theme.pageColor),
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.widthIn(max = 240.dp),
            )
        }
    }
}

/** La vignette de l'état vide : la **loupe barrée**, à l'encre mesurée sur son disque. */
@Composable
private fun SearchEmptyGlyph() {
    val theme = LocalOnyxTheme.current
    val ground = OnyxContrast.over(theme.fill, theme.pageColor)
    val ink = mutedIconInk(ground)
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(ground)
            // Le trait **barre** la loupe : sans lui, rien ne distingue « rien trouvé » d'une
            // recherche en cours. Il passe **par-dessus** l'icône — dessiné dessous, la loupe le
            // cacherait et la vignette redeviendrait une simple loupe.
            .drawWithContent {
                drawContent()
                drawLine(
                    color = ink,
                    start = Offset(size.width * 0.30f, size.height * 0.30f),
                    end = Offset(size.width * 0.70f, size.height * 0.70f),
                    strokeWidth = OnyxSocle.Stroke.toPx(),
                    cap = StrokeCap.Round,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        OnyxIcon(symbol = Symbols.MagnifyingGlass, tint = ink, size = 28.dp)
    }
}

// ---------------------------------------------------------------------------------------------
// 3. Le sélecteur Chats / Bots
// ---------------------------------------------------------------------------------------------

/** La capsule à deux positions : *Chats* / *Bots*, la position active dans une pastille claire. */
@Composable
private fun SidebarSegment(tab: SidebarTab, botsWaiting: Int, onTab: (SidebarTab) -> Unit) {
    val options = SidebarTab.entries
    OnyxSegmented(
        options = options.map { sidebarTabLabel(it, botsWaiting) },
        selectedIndex = options.indexOf(tab),
        onSelect = { index -> onTab(options[index]) },
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
        // **La piste du volet, au clair** : piste gris clair, pastille blanche sur l'option
        // retenue, son libellé en noir, l'autre en gris. En sombre le drapeau ne fait rien — la
        // piste garde son verre, comme avant.
        flat = true,
        // **La pilule pleine de la planche** : l'onglet retenu est un aplat d'accent, dans les deux
        // apparences. C'est le seul accent du volet Discussions.
        filled = true,
    )
}

// ---------------------------------------------------------------------------------------------
// 4. Les tuiles
// ---------------------------------------------------------------------------------------------

/**
 * **Le mode édition de la grille de raccourcis**, prêt à dessiner.
 *
 * Il est **fourni par l'appelant** (`ChatSidebarHost`) et vaut `null` quand la grille n'est pas
 * personnalisable : la grille reste alors exactement ce qu'elle était. Tout ce qui est **décidé**
 * vit dans [SidebarTileLayout] (pur) ; ici, on dessine, et on rend les gestes.
 *
 * @property active l'édition est-elle ouverte ?
 * @property library les destinations **disponibles mais pas affichées** ([SidebarTileLayout.library]).
 * @property onSetOrder la grille vient de changer : on rend **toute** la liste des identifiants, dans
 *   l'ordre — c'est ce que l'appelant enregistre (ajout, retrait, réordonnancement, réinitialisation).
 * @property onEnter entre en édition (le crayon, et l'appui long sur une tuile).
 * @property onExit sort de l'édition — l'écran retrouve son état normal, sans reste.
 */
@Immutable
internal data class SidebarTileEditor(
    val active: Boolean,
    val library: List<SidebarTile>,
    val onSetOrder: (List<String>) -> Unit,
    val onEnter: () -> Unit,
    val onExit: () -> Unit,
)

/**
 * **Ce qu'une tuile est en train de faire** : ouvrir son écran, se laisser retirer, ou se laisser
 * ajouter depuis la bibliothèque. Trois dessins d'une **même** tuile — jamais trois tuiles.
 */
private enum class TileCellMode { OPEN, EDIT, LIBRARY }

/** Combien de temps la grille garde « Raccourci retiré · Annuler », et une raison de refus. */
internal const val SidebarTileUndoMillis: Long = 8_000

/**
 * **La bande du rond de retrait** : sur une tuile en édition, le rond occupe une **bande à lui**,
 * au-dessus de l'icône et du libellé ([OnyxSpace.Loose], 24 dp).
 *
 * Pourquoi une bande, et pas un rond posé sur le coin : une cible de 48 dp posée sur une tuile de
 * 44 dp recouvrirait le **libellé** (« Configuration » va jusqu'au bord de sa tuile sur un téléphone
 * de 411 dp), et une place retirée au libellé le ferait passer **sous le plancher de taille** —
 * c'est-à-dire le couper. La bande donne au rond sa cible pleine sans toucher au mot.
 */
internal val TileBadgeStrip: Dp = OnyxSpace.Loose

/** Le glyphe du rond : 11 dp, la marche des identifiants de la maison (`OnyxSocle.Micro`). */
internal val TileRemoveGlyph: Dp = 11.dp

/**
 * **La grille de raccourcis du volet** — les écrans de l'app en tuiles, deux ou trois par rangée,
 * l'icône dans son carré et l'intitulé à côté.
 *
 * **Le mot le plus large de la grille doit tenir** : la taille des libellés est mesurée une fois pour
 * tout le volet ([TileLabelFit]) — une grille dont les cases se coupent ne se lit pas, et deux tuiles
 * voisines dont les libellés changent de taille se lisent comme un défaut. Le **nombre de colonnes**
 * ([tileColumnsFor]) est décidé par la même règle : une troisième colonne ne s'obtient pas si elle
 * appauvrit le libellé.
 *
 * **Personnalisable** ([editor] non nul) : la ligne de titre porte un **crayon** (cible 48 dp), un
 * **appui long sur une tuile entre en édition**, chaque tuile reçoit alors un **rond de retrait** et
 * se **réordonne par glissement**, et **LA BIBLIOTHÈQUE** apparaît sous la grille — les destinations
 * disponibles mais pas affichées, sous la même forme de tuiles, à ajouter d'un appui. La grille **ne
 * se tronque jamais en silence** : ses bornes refusent, et elle dit pourquoi.
 *
 * **L'appui long d'une tuile : tranché, et dit.** Une tuile n'avait **aucun** appui long ; il entre
 * donc en édition. L'appui long qui ouvre une feuille est celui d'une **rangée de conversation** et
 * d'une **carte de bot** — d'autres objets, dans d'autres listes : les deux ne se disputent rien.
 */
@Composable
private fun SidebarTileGrid(
    tiles: List<SidebarTile>,
    onOpenTile: (SidebarTile) -> Unit,
    editor: SidebarTileEditor? = null,
) {
    val theme = LocalOnyxTheme.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = theme.footnote.copy(color = theme.textColor, fontWeight = FontWeight.Medium)
    // **n° 2 — une seule taille pour toute la grille.** La taille nominale tant que le mot le plus
    // large de la grille tient dans la largeur offerte au libellé, sinon on descend (plancher
    // [TileLabelFit]) et **jamais** sous lui. La taille nominale du thème porte déjà l'échelle de la
    // maison (police 1,3×) : la mesure part donc de la taille réellement demandée, pas d'une constante.
    val baseSp = labelStyle.fontSize.takeIf { it.isSp }?.value ?: 16f
    val gridEditor = editor
    val editing = gridEditor?.active == true
    // L'ordre courant **tel que les tuiles le disent** : c'est lui que les gestes réécrivent.
    val ids = remember(tiles) { tiles.map { it.id } }
    val setOrder: (List<String>) -> Unit = gridEditor?.onSetOrder ?: {}
    // La borne du bas, telle qu'elle s'écrit, et la raison quand elle refuse.
    val removalRefusal = SidebarTileLayout.removalRefusal(ids)
    val removalReason = removalRefusal?.let { OnyxLocalization.format(it, SidebarTileLayout.MIN_TILES) }
    // **Ce qui vient d'être retiré** — la grille en garde la trace quelques secondes, avec un
    // « Annuler » : on retire vite, on rattrape vite, et aucune confirmation ne s'interpose.
    var undo by remember { mutableStateOf<List<String>?>(null) }
    // **La raison d'un refus** (rond inactif au plancher, plafond atteint) : elle **remplace** le
    // geste au lieu de ne rien faire.
    var refusal by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(undo, refusal) {
        if (undo == null && refusal == null) return@LaunchedEffect
        delay(SidebarTileUndoMillis)
        undo = null
        refusal = null
    }
    BoxWithConstraints(
        modifier = Modifier.padding(
            start = OnyxSpace.Snug,
            end = OnyxSpace.Snug,
            bottom = OnyxSpace.Snug,
        ),
    ) {
        // **n° 1 — la largeur se lit ici, une fois** : c'est la grille qui sait combien de place une
        // tuile a vraiment, et c'est cette largeur-là que le libellé reçoit (`tileLabelWidthIn`). La
        // mesurer dans la tuile dépendait de la contrainte qu'elle recevait, et une contrainte trop
        // large y faisait tenir « Fichiers du serveur » sur une ligne — coupée par le bord. La
        // variante avec compteur donne la largeur de la tuile qui en porte un (le cas le plus étroit).
        val gridWidth = this.maxWidth
        // n° 2 : le mot le plus large de la grille, mesuré à la taille nominale, une seule fois.
        // La mesure est sans contrainte : elle ne dépend donc pas du nombre de colonnes, et c'est
        // elle qui le **décide** — puis la largeur du libellé en découle.
        val widestWordDp = remember(tiles, labelStyle) {
            val widest = tiles.maxOfOrNull { tile ->
                tile.label.split(' ').filter { it.isNotBlank() }.maxOfOrNull { word ->
                    measurer.measure(
                        text = AnnotatedString(word),
                        style = labelStyle,
                        constraints = Constraints(),
                    ).size.width / density.density
                } ?: 0f
            } ?: 0f
            widest.dp
        }
        // **Le nombre de colonnes** : la règle maison, pure ([tileColumnsFor]) — deux, trois si la
        // largeur le permet **sans** appauvrir le libellé. La mise en page ne se casse donc ni à
        // quatre tuiles ni à douze, ni à police 1,3× : elle change de colonnes avant de changer de mot.
        // **Deux règles, un seul nombre.** La grille personnalisable choisit ses colonnes à la
        // largeur ([tileColumnsFor], deux ou trois) — mais la police du système garde son mot : à
        // police 1,3×, deux colonnes ne laissent plus la place au libellé, donc la **grille cède
        // avant le corps** et retombe à une seule colonne ([tileColumns], apport de la fusion
        // horaire). On retient la plus contraignante des deux : aucune des deux intentions ne se
        // perd, et il n'y a qu'un seul nombre de colonnes pour toute la grille.
        val columns = minOf(tileColumnsFor(gridWidth, widestWordDp), tileColumns(density.fontScale))
        val labelWidth = tileLabelWidthIn(gridWidth, badgeDigits = 0, columns = columns)
        val labelWidthBadge = tileLabelWidthIn(gridWidth, badgeDigits = TILE_BADGE_DIGITS, columns = columns)
        // Le cas le plus étroit (tuile à compteur) décide la taille : elle vaut pour toutes.
        val sizeSp = TileLabelFit.fontSize(baseSp, labelWidthBadge.value, widestWordDp.value)

        // ---- Le réordonnancement -------------------------------------------------------------
        // **L'ancre ne bouge pas.** Pendant le glissement, la grille garde **exactement** sa
        // disposition : seule la tuile prise porte une translation, dessinée au-dessus des autres.
        // Aucune voisine ne bouge, aucun trou ne s'ouvre, la tuile n'est **jamais** à cheval sur deux
        // places — et il n'y a **qu'une seule cible** à la fois : la plus proche.
        val centers = remember { mutableMapOf<Int, Offset>() }
        var dragIndex by remember { mutableStateOf<Int?>(null) }
        var dragDy by remember { mutableFloatStateOf(0f) }

        /** La place visée : la tuile dont le centre est le **plus proche** du centre déplacé. */
        fun targetFor(index: Int, dy: Float): Int {
            val base = centers[index] ?: return index
            return centers.minByOrNull { (_, center) ->
                val dx = center.x - base.x
                val dz = center.y - (base.y + dy)
                dx * dx + dz * dz
            }?.key ?: index
        }

        /** Le décalage, **borné** aux rangées de la grille : la tuile n'en sort jamais. */
        fun clampDy(index: Int, dy: Float): Float {
            val base = centers[index] ?: return 0f
            val lowest = centers.values.minOfOrNull { it.y } ?: base.y
            val highest = centers.values.maxOfOrNull { it.y } ?: base.y
            return dy.coerceIn(lowest - base.y, highest - base.y)
        }

        Column(verticalArrangement = Arrangement.spacedBy(OnyxSpace.Hair)) {
            // **La ligne de titre de la section** : son intitulé en petites capitales, son compteur
            // en monospace, et — au bout de la ligne, à la marge du contenu — le contrôle du mode.
            SidebarTileTitleRow(
                count = tiles.size,
                editable = gridEditor != null,
                editing = editing,
                onEnter = { gridEditor?.onEnter?.invoke() },
                onExit = { gridEditor?.onExit?.invoke() },
                onReset = {
                    setOrder(SidebarTileLayout.defaultIds)
                    undo = null
                    refusal = null
                },
                modifier = Modifier.padding(bottom = OnyxSpace.Half),
            )
            // **La grille de la planche, à la lettre** (`hscene.py:45`,
            // `tiles_row(M, 268, CW, 56, [...])`) : ses tuiles dans son ordre, **sans intitulé de
            // groupe**. Les quatre tuiles propres à Onyx (`SidebarDestinations.onyxTiles`) ne sont
            // plus **dessinées** d'office ; elles restent **dans le code** (mêmes identifiants, mêmes
            // écrans, mêmes verrous), au catalogue et dans la bibliothèque — rien n'est supprimé.
            tiles.chunked(columns).forEach { row ->
                // **La rangée prend la hauteur de sa plus haute tuile** (`IntrinsicSize.Min`) : quand
                // un libellé se replie sur deux lignes, les tuiles de la rangée grandissent
                // **ensemble** — l'une ne dépasse pas l'autre, et la grille garde ses rangées alignées.
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Hair),
                ) {
                    row.forEach { tile ->
                        val index = tiles.indexOf(tile)
                        val dragged = dragIndex == index
                        SidebarTileCell(
                            tile = tile,
                            onClick = { onOpenTile(tile) },
                            labelWidth = if (tile.badge > 0) labelWidthBadge else labelWidth,
                            // n° 2 : une seule taille, la même pour toutes les tuiles de la grille.
                            // n° 2 : une seule taille, ramenée au corps du socle, la même partout.
                            labelStyle = labelStyle.copy(fontSize = OnyxSocle.snap(sizeSp.sp)),
                            mode = if (editing) TileCellMode.EDIT else TileCellMode.OPEN,
                            removable = removalRefusal == null,
                            removeLabel = OnyxLocalization.format(
                                "Remove %1\$s",
                                resolveOnyxText(tile.label),
                            ),
                            removeReason = removalReason,
                            onRemove = {
                                undo = ids
                                refusal = null
                                setOrder(SidebarTileLayout.remove(ids, tile.id))
                            },
                            onRefused = {
                                // Le rond inactif **dit pourquoi** : la raison prend la place du
                                // rattrapage précédent, elle ne se cache pas derrière lui.
                                undo = null
                                refusal = removalReason
                            },
                            // **En édition seulement** : hors édition, la tuile ouvre son écran ;
                            // l'appui long est le **seul** autre chemin (avec le crayon).
                            reorder = if (gridEditor == null) {
                                Modifier
                            } else {
                                Modifier.reorderGesture(
                                    index = index,
                                    editing = editing,
                                    onLongPress = { gridEditor.onEnter() },
                                    onStart = { dragIndex = index; dragDy = 0f },
                                    onMove = { dy -> dragDy = clampDy(index, dy) },
                                    onDrop = { dy ->
                                        val from = dragIndex ?: index
                                        dragIndex = null
                                        dragDy = 0f
                                        val to = targetFor(from, dy)
                                        // **Un lâcher qui ne déplace rien n'écrit rien.** Un appui
                                        // long qui ne bouge pas (et un appui **posé** sur un rond de
                                        // retrait, que la même détection termine en « lâcher sans
                                        // déplacement ») rendrait sinon la liste **d'avant** le geste :
                                        // c'est exactement ainsi qu'un retrait se faisait défaire. On
                                        // n'écrit donc que si la place change vraiment.
                                        if (to != from) {
                                            setOrder(SidebarTileLayout.move(ids, from, to))
                                        }
                                    },
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .offset { IntOffset(0, if (dragged) dragDy.toInt() else 0) }
                                .zIndex(if (dragged) 1f else 0f)
                                .onGloballyPositioned { coords ->
                                    centers[index] = coords.boundsInRoot().center
                                },
                        )
                    }
                    // Une rangée incomplète garde ses cases vides : la tuile seule ne s'étire pas.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            // **Le rattrapage, et les raisons.** « Raccourci retiré · Annuler », ou la raison d'un
            // refus — au bas de la grille, discret, jamais une alerte.
            // **La raison passe avant le rattrapage** : quand le rond vient d'être touché alors que
            // la grille est au plancher, c'est la **réponse** à ce geste-là que la personne attend —
            // pas le souvenir du retrait précédent. Les deux ne s'écrivent donc jamais ensemble.
            if (refusal != null || undo != null) {
                SidebarTileNotice(
                    text = refusal ?: OnyxLocalization["Shortcut removed"],
                    action = if (refusal == null) OnyxLocalization["Undo"] else null,
                    onAction = if (refusal != null) {
                        null
                    } else {
                        {
                            undo?.let(setOrder)
                            undo = null
                        }
                    },
                    modifier = Modifier.padding(top = OnyxSpace.Half),
                )
            }
            // **LA BIBLIOTHÈQUE** — sous la grille, seulement en édition : les destinations que l'app
            // porte et que la grille ne montre pas. Mêmes tuiles, mêmes carrés, mêmes mots.
            if (editing) {
                SidebarTileLibrary(
                    library = gridEditor?.library.orEmpty(),
                    columns = columns,
                    labelWidth = labelWidth,
                    labelWidthBadge = labelWidthBadge,
                    labelStyle = labelStyle,
                    sizeSp = sizeSp,
                    onAdd = { tile ->
                        val additionRefusal = SidebarTileLayout.additionRefusal(ids, tile.id)
                        if (additionRefusal != null) {
                            // **La borne haute refuse et le dit** — jamais une troncature silencieuse.
                            refusal = OnyxLocalization.format(
                                additionRefusal,
                                SidebarTileLayout.MAX_TILES,
                            )
                        } else {
                            undo = null
                            refusal = null
                            setOrder(ids + tile.id)
                        }
                    },
                    modifier = Modifier.padding(top = OnyxSpace.Loose),
                )
            }
        }
    }
}

/**
 * **Le geste de réordonnancement d'une tuile** : appui long, puis déplacement **vertical**.
 *
 * Les deux règles de la maison sont reprises telles quelles :
 * - **l'axe est tranché une fois** ([sidebarSwipeAxis]) : un geste oblique ou horizontal n'est pas un
 *   réordonnancement, la tuile ne bouge pas ;
 * - **rien ne bouge avant décision** : le décalage ne compte qu'à partir du moment où l'axe est
 *   vertical, si bien qu'un appui long immobile ne fait pas sauter la tuile de quelques pixels.
 *
 * Le geste est **consommé** dès l'appui long : la liste sous la grille ne défile pas pendant qu'on
 * réordonne. Hors édition, l'appui long **entre** en édition — il ne réordonne pas encore, ce qui
 * laisse le temps de voir la grille se poser avant de la déplacer.
 */
private fun Modifier.reorderGesture(
    index: Int,
    editing: Boolean,
    onLongPress: () -> Unit,
    onStart: () -> Unit,
    onMove: (Float) -> Unit,
    onDrop: (Float) -> Unit,
): Modifier = this.pointerInput(index, editing) {
    val slop = viewConfiguration.touchSlop
    var accX = 0f
    var accY = 0f
    var originY = 0f
    var live = false
    detectDragGesturesAfterLongPress(
        onDragStart = {
            accX = 0f
            accY = 0f
            originY = 0f
            live = false
            onLongPress()
            if (editing) onStart()
        },
        onDragEnd = { onDrop(if (live) accY - originY else 0f) },
        onDragCancel = { onDrop(0f) },
        onDrag = onDrag@{ change, amount ->
            // Le geste est à nous : la liste ne défilera pas tant que le doigt est posé.
            change.consume()
            accX += amount.x
            accY += amount.y
            if (!editing) return@onDrag
            if (!live) {
                if (sidebarSwipeAxis(accX, accY, slop) == SidebarSwipeAxis.VERTICAL) {
                    live = true
                    // Le décalage part de **là où l'axe a été tranché** : aucun saut.
                    originY = accY
                }
                return@onDrag
            }
            onMove(accY - originY)
        },
    )
}

/**
 * **La ligne de titre de la section des tuiles** : son intitulé (petites capitales, compteur en
 * monospace, comme les autres sections du volet) et, **au bout de la ligne**, le contrôle du mode.
 *
 * - au repos : un **crayon** de 44 dp ([SectionActionButton] — le rond de la maison, à la
 *   taille et au glyphe des autres actions de section) ;
 * - en édition : **« Réinitialiser »** puis **« Terminé »**, à la **même place** que le crayon et
 *   **dans l'accent** — sortir de l'édition est aussi évident que d'y entrer, et au même endroit.
 */
@Composable
private fun SidebarTileTitleRow(
    count: Int,
    editable: Boolean,
    editing: Boolean,
    onEnter: () -> Unit,
    onExit: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // La ligne de titre fait la hauteur de l'action, plus l'écart de la maison sous elle :
            // l'intitulé « RACCOURCIS » annonce la grille de tuiles qui suit, et son crayon ne peut
            // pas finir sur la première tuile ([SectionAction]).
            .defaultMinSize(minHeight = SectionAction.Size)
            .padding(bottom = SectionAction.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GalerieSectionLabel(
            text = OnyxLocalization["Shortcuts"],
            count = count,
            modifier = Modifier.weight(1f),
        )
        if (editing) {
            SidebarTileSmallButton(
                label = OnyxLocalization["Reset"],
                filled = false,
                onClick = onReset,
            )
            Spacer(Modifier.width(OnyxSpace.Half))
            SidebarTileSmallButton(
                label = OnyxLocalization["Done"],
                filled = true,
                onClick = onExit,
            )
        } else if (editable) {
            SectionActionButton(
                symbol = Symbols.Pencil,
                label = "Edit shortcuts",
                onClick = onEnter,
            )
        }
    }
}

/** Un petit bouton de la ligne de titre : **plein d'accent** (« Terminé ») ou en retrait (« Réinitialiser »). */
@Composable
private fun SidebarTileSmallButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    val shape = RoundedCornerShape(50)
    val ground = if (filled) OnyxContrast.filled(theme.accent) else flatWellColor(theme)
    val ink = if (filled) OnyxContrast.inkOn(ground) else mutedInk(sidebarPaneColor(theme))
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = SectionAction.Size)
            .clip(shape)
            .background(ground)
            .then(if (filled) Modifier else Modifier.border(FlatHairlineDp, theme.separator, shape))
            .onyxClickable(onClick = onClick)
            .semanticsLabel(label)
            .padding(horizontal = OnyxSpace.Base),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            localize = false,
            style = theme.footnote.copy(color = ink, fontWeight = FontWeight.SemiBold),
            // Un libellé se **replie**, il ne se tranche pas : la police 1,3× sur un volet étroit
            // fait passer « Réinitialiser » sur deux lignes, et le bouton grandit avec lui.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * **LA BIBLIOTHÈQUE** — les destinations que l'app porte et que la grille ne montre pas.
 *
 * Ce sont les **mêmes tuiles** (carré blanc arrondi, icône, libellé), sous un intitulé en **petites
 * capitales** ; un appui en ajoute une à la grille. Son **état vide a une phrase** — quand tout est
 * déjà sur la grille, la bibliothèque le dit au lieu de laisser un blanc.
 */
@Composable
private fun SidebarTileLibrary(
    library: List<SidebarTile>,
    columns: Int,
    labelWidth: Dp,
    labelWidthBadge: Dp,
    labelStyle: TextStyle,
    sizeSp: Float,
    onAdd: (SidebarTile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Column(modifier = modifier.fillMaxWidth()) {
        GalerieSectionLabel(
            text = OnyxLocalization["Library"],
            count = library.size,
            modifier = Modifier.padding(bottom = OnyxSpace.Half),
        )
        if (library.isEmpty()) {
            BasicText(
                text = OnyxLocalization["Everything is already on the grid."],
                localize = false,
                style = theme.footnote.copy(color = mutedInk(sidebarPaneColor(theme))),
                modifier = Modifier.padding(
                    start = OnyxSpace.ScreenMargin,
                    end = OnyxSpace.ScreenMargin,
                    top = OnyxSpace.Half,
                ),
            )
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(OnyxSpace.Hair)) {
            library.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Hair),
                ) {
                    row.forEach { tile ->
                        SidebarTileCell(
                            tile = tile,
                            onClick = { onAdd(tile) },
                            labelWidth = if (tile.badge > 0) labelWidthBadge else labelWidth,
                            // n° 2 : une seule taille, ramenée au corps du socle, la même partout.
                            labelStyle = labelStyle.copy(fontSize = OnyxSocle.snap(sizeSp.sp)),
                            mode = TileCellMode.LIBRARY,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Le bandeau discret du bas de la grille : une phrase, et parfois un geste de rattrapage. */
@Composable
private fun SidebarTileNotice(
    text: String,
    action: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = OnyxSocle.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
    ) {
        BasicText(
            text = text,
            localize = false,
            style = theme.footnote.copy(color = mutedInk(sidebarPaneColor(theme))),
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            SidebarTileSmallButton(label = action, filled = false, onClick = onAction)
        }
    }
}

@Composable
private fun SidebarTileCell(
    tile: SidebarTile,
    onClick: () -> Unit,
    /** La largeur que le libellé a **vraiment**, donnée par la grille ([tileLabelWidthIn]). */
    labelWidth: Dp,
    /** La taille retenue pour **toute** la grille ([TileLabelFit]) — une seule, pour toutes. */
    labelStyle: TextStyle,
    modifier: Modifier = Modifier,
    mode: TileCellMode = TileCellMode.OPEN,
    /** Le rond de retrait est-il actif ? **Faux au plancher** : il dit alors pourquoi. */
    removable: Boolean = true,
    /** Le nom accessible du rond de retrait. */
    removeLabel: String? = null,
    /** La **raison** de l'inactivité du rond, telle qu'elle s'écrit. */
    removeReason: String? = null,
    /** Le retrait, une fois l'appui **franc** obtenu. */
    onRemove: (() -> Unit)? = null,
    /** Le rond inactif vient d'être touché : la raison prend la place du geste. */
    onRefused: (() -> Unit)? = null,
    /** Le geste de réordonnancement, posé par la grille (édition seulement). */
    reorder: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val dark = theme.dark
    // **n° 4 — le rayon et la surface suivent l'apparence.** En sombre, la tuile garde *exactement*
    // sa vitre et son coin d'avant (12 dp) ; en clair, elle prend le coin de la famille des
    // vignettes ([OnyxSpace.TileCorner]) sur le **gris des creux** ([flatWellColor]) — un aplat,
    // pas une carte en verre : au clair, la référence est plate et nette.
    val shape = if (dark) RoundedCornerShape(OnyxSpace.FormCorner) else RoundedCornerShape(OnyxSpace.TileCorner)
    val ground = if (dark) theme.surfaceColor else flatWellColor(theme)
    // Le libellé de la tuile, traduit **une fois** : il sert au dessin **et** à l'accessibilité. Le
    // relevé d'arbre rendait la cellule cliquable sans nom (le libellé vivait dans un nœud enfant) ;
    // il est désormais posé **avec** la zone, compteur compris.
    val libelle = resolveOnyxText(tile.label)
    val tileLabel = if (tile.badge > 0) "$libelle, ${tile.badge}" else libelle
    // **Ce que la tuile fait**, selon le mode : ouvrir son écran, se réordonner (édition), être
    // ajoutée (bibliothèque). En édition, l'appui **simple** ne navigue pas — on ne quitte pas la
    // composition en visant un rond.
    val gesture = when (mode) {
        TileCellMode.OPEN -> Modifier.onyxClickable(onClick = onClick).tapLabel(tileLabel)
        TileCellMode.LIBRARY -> Modifier.onyxClickable(onClick = onClick)
            .tapLabel(OnyxLocalization.format("Add %1\$s", libelle))
        TileCellMode.EDIT -> reorder.tapLabel(tileLabel)
    }
    Column(
        modifier = modifier
            // **La hauteur plancher est celle de la cible tactile** ([SidebarTileHeight], 44 dp), mais
            // elle **cède** au contenu : un libellé qui se replie sur deux lignes (à police 1,3×,
            // « Fichiers du serveur ») doit pouvoir grandir — sinon la seconde ligne est **coupée**
            // par le bas de la tuile. En édition, la bande du rond s'ajoute **au-dessus** du contenu.
            .heightIn(min = SidebarTileHeight)
            .sidebarTileSurface(GlassSurface.SIDEBAR_TILE, shape)
            .then(gesture)
            // Le rembourrage **interne** de 4 dp place l'icône à 16 dp du bord du panneau (12 de
            // marge de grille + 4) : c'est la **colonne d'alignement** du volet.
            .padding(horizontal = OnyxSpace.Hair, vertical = OnyxSpace.Half),
        verticalArrangement = Arrangement.spacedBy(OnyxSpace.Hair),
    ) {
        // **La bande du rond** : au-dessus du contenu, pour ne jamais recouvrir le libellé.
        when (mode) {
            TileCellMode.EDIT -> TileCellStrip(
                symbol = Symbols.Xmark,
                label = if (removable) {
                    removeLabel.orEmpty()
                } else {
                    "${removeLabel.orEmpty()} · ${removeReason.orEmpty()}"
                },
                active = removable,
                onPress = onRemove,
                onRefused = onRefused,
            )
            TileCellMode.LIBRARY -> TileCellStrip(
                symbol = Symbols.Plus,
                label = OnyxLocalization.format("Add %1\$s", libelle),
                active = true,
                onPress = null,
                onRefused = null,
            )
            TileCellMode.OPEN -> Unit
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OnyxSpace.IconLabelGap),
        ) {
            // **L'ICÔNE DANS UN CARRÉ BLANC ARRONDI** — le bloc `.tileic` de la planche
            // (`hcss.py:90` : 32 × 32, rayon 14, fond plein, filet de 1 px). L'icône ne porte plus
            // directement sur la tuile : c'est ce carré qui la pose et qui l'aligne sur la colonne.
            Box(
                modifier = Modifier
                    .size(TileIconSquare)
                    .clip(RoundedCornerShape(OnyxSocle.RadiusField))
                    .background(theme.formSurfaceColor)
                    .border(
                        FlatHairlineDp,
                        theme.separator,
                        RoundedCornerShape(OnyxSocle.RadiusField),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                OnyxIcon(
                    symbol = tile.symbol,
                    tint = mutedIconInk(theme.formSurfaceColor),
                    size = TileLabelIcon,
                )
            }
            // **Un mot ne se coupe jamais (n° 1 + n° 2).** Le libellé est mesuré sous la taille retenue
            // pour **toute** la grille ([TileLabelFit], n° 2) sur la largeur **réellement** laissée au
            // texte (n° 1, `tileLabelWidthIn`), puis rangé en lignes **aux espaces** par `tileLabelPlan`
            // (n° 1) : la taille cède avant le mot. La grille a déjà choisi une taille qui fait tenir le
            // mot le plus large, donc le plan ne réduit le plus souvent rien — il garde le libellé sur
            // sa ligne, jamais coupé au milieu.
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val base = labelStyle
            // Le libellé est traduit **avant** d'être découpé : chaque ligne est alors dessinée verbatim
            // (`localize = false`), sinon la seconde ligne d'un libellé français serait cherchée dans le
            // dictionnaire et retomberait en anglais.
            val textWidth = with(density) { labelWidth.toPx() }
            val plan = remember(libelle, textWidth, base) {
                tileLabelPlan(
                    label = libelle,
                    measure = { text -> measurer.measure(text, base).size.width.toFloat() },
                    available = textWidth,
                )
            }
            Column(modifier = Modifier.weight(1f, fill = false)) {
                // Le rapport de la tuile donne une taille **calculée** : elle est ramenée au corps le
                // plus proche du socle, jamais sous 11 sp (apport de la fusion horaire).
                val style = base.copy(fontSize = OnyxSocle.snap(base.fontSize * plan.fontRatio))
                plan.lines.forEach { line ->
                    BasicText(
                        text = line,
                        localize = false,
                        style = style,
                        maxLines = 1,
                        // Chaque ligne est un groupe de mots entiers, déjà ajusté pour tenir : l'ellipse
                        // ne se déclenche donc pas, mais un mot ne se coupe jamais.
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                }
            }
            if (tile.badge > 0) BadgeCount(tile.badge)
        }
    }
}

/**
 * **Le rond d'une tuile en édition (retirer) ou dans la bibliothèque (ajouter)** — la bande du haut.
 *
 * **Aucun retrait par glissement, et pas de retrait sur un frôlement.** Le rond ne réagit qu'à un
 * appui qui **commence et finit sur lui** : `tryAwaitRelease` rend faux dès que le geste part
 * ailleurs (un défilement le traverse, un glissement de réordonnancement l'effleure) — c'est
 * exactement « un appui franc ». Et un rond **inactif** (au plancher de la grille) ne fait pas
 * semblant : il est éteint, et il **dit pourquoi**.
 */
@Composable
private fun TileCellStrip(
    symbol: Int,
    label: String,
    active: Boolean,
    onPress: (() -> Unit)?,
    onRefused: (() -> Unit)?,
) {
    val theme = LocalOnyxTheme.current
    val shape = CircleShape
    var pressed by remember { mutableStateOf(false) }
    val interactive = onPress != null || onRefused != null
    Box(
        modifier = Modifier.fillMaxWidth().height(TileBadgeStrip),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .size(OnyxSocle.TouchTarget)
                .then(
                    if (!interactive) {
                        Modifier
                    } else {
                        Modifier.pointerInput(active) {
                            detectTapGestures(
                                onPress = {
                                    pressed = true
                                    // **L'appui franc** : un geste que le défilement ou le
                                    // glissement emporte ne va pas au bout — `tryAwaitRelease` fait
                                    // alors faux, et rien n'est retiré.
                                    val wentThrough = tryAwaitRelease()
                                    pressed = false
                                    if (wentThrough) {
                                        if (active) onPress?.invoke() else onRefused?.invoke()
                                    }
                                },
                            )
                        }.tapLabel(label)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val fill = if (active) OnyxContrast.filled(theme.accent) else flatWellColor(theme)
            Box(
                modifier = Modifier
                    .size(TileBadge + if (pressed) OnyxSpace.Hair else 0.dp)
                    .clip(shape)
                    .background(fill)
                    .border(FlatHairlineDp, if (active) fill else theme.separator, shape),
                contentAlignment = Alignment.Center,
            ) {
                OnyxIcon(
                    symbol = symbol,
                    tint = if (active) OnyxContrast.inkOn(fill) else mutedIconInk(fill),
                    size = TileRemoveGlyph,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 5. Les projets
// ---------------------------------------------------------------------------------------------

/** « All projects », chaque projet et son compte, puis le bouton **+**. */
@Composable
private fun SidebarProjects(
    projects: List<SidebarProjectChip>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onNewProject: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        // **Le filet d'air entre les puces et le rond du « + ».** Sans lui, les puces défilaient
        // jusqu'au bord même du bouton : une puce à moitié coupée contre le rond se lit comme si le
        // bouton la chevauchait (« Home 4 » tronqué, puis « Onyx » à demi masqué). La
        // géométrie est celle de la maison : `projectChipsWidth` la mesure, le test la compare.
        horizontalArrangement = Arrangement.spacedBy(ProjectChipsGutter),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                // Les puces **défilent sous** le bouton si rien ne les coupe : « Onyx 2 » se
                // retrouvait à moitié sous le rond du « + ». Le défilement est donc borné.
                .clipToBounds()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = OnyxSpace.Hair),
            horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            projects.forEach { chip ->
                SidebarChip(
                    label = chip.label,
                    count = chip.count,
                    selected = chip.id == selectedId,
                    onClick = { onSelect(chip.id) },
                )
            }
        }
        if (onNewProject != null) {
            SectionActionButton(
                symbol = Symbols.FolderBadgePlus,
                label = "New project",
                onClick = onNewProject,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 6. Les filtres
// ---------------------------------------------------------------------------------------------

/**
 * Tout / En cours / Vous attend, chacun avec le nombre de lignes qu'il retient.
 *
 * **Les puces passent à la ligne** (`FlowRow`) au lieu de sortir par le bord : sur un téléphone
 * étroit, la dernière (« Vous attend ») ne tenait pas dans la rangée et se retrouvait coupée par le
 * bord de l'écran — une puce à moitié lisible, et une pastille de filtre qu'on ne peut pas toucher.
 * Ici, ce qui ne tient pas descend d'une rangée : chaque puce reste **entière**, sans geste de
 * défilement à découvrir, et la rangée garde l'écart de la maison entre ses puces.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SidebarFilters(
    filter: SidebarFilter,
    counts: Map<SidebarFilter, Int>,
    onFilter: (SidebarFilter) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            // n° 4 : la marge de la rangée suit la **colonne d'alignement** du volet (les puces,
            // comme les tuiles, s'alignent à 16 dp du bord). n° 1 : la rangée est un `FlowRow` — ce
            // qui ne tient pas descend d'une rangée, donc aucun défilement horizontal.
            .padding(start = OnyxSpace.Hair, end = OnyxSpace.Hair, top = 2.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SidebarFilter.entries.forEach { option ->
            SidebarChip(
                label = option.label,
                count = counts[option] ?: 0,
                selected = option == filter,
                dot = when (option) {
                    SidebarFilter.ALL -> null
                    SidebarFilter.RUNNING -> theme.accent
                    SidebarFilter.WAITING -> theme.warning
                },
                onClick = { onFilter(option) },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 7. La liste des conversations
// ---------------------------------------------------------------------------------------------

@Composable
private fun ConversationList(
    rows: List<SidebarRow>,
    query: String,
    onOpenRow: (SidebarRow, Rect?) -> Unit,
    /** La position de défilement, **tenue par le volet** (n° 7) : la recherche la lui rend intacte. */
    state: LazyListState,
    /** Le mode multi-sélection : chaque rangée porte alors une case, et le tap coche. */
    selecting: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onToggleRow: (SidebarRow) -> Unit = {},
    /** L'appui long d'une rangée : la feuille du bas de la conversation. */
    onMenuRow: ((SidebarRow, Rect) -> Unit)? = null,
    /**
     * Ce qu'une rangée propose par ses gestes. Absent, la liste reste de simples lignes : ni
     * pilule au glissement, ni feuille à l'appui long.
     */
    rowActions: ((SidebarRow) -> SidebarRowActions?)? = null,
    /**
     * « Épingler » du glissement vers la **droite** : l'action s'applique tout de suite, le même
     * épinglage que la feuille.
     */
    onSwipePin: (SidebarRowActions) -> Unit = {},
    /** « Archiver » du glissement vers la **gauche**. */
    onSwipeArchive: (SidebarRowActions) -> Unit = {},
    header: LazyListScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    // **La zone morte du bord** (voir [SidebarSwipe.kt]) : la bande du système plus la marge de la
    // maison, lue une fois pour toute la liste. C'est elle qui laisse le geste vers la droite
    // ouvrir le volet depuis le bord, au lieu de le prendre à la rangée.
    val edgeDeadZone = rememberSidebarSwipeDeadZone()
    // **La lumière se fige pendant le défilement.** Tant que la liste bouge, aucune de ses vitres ne
    // s'éclaire : le doigt qui fait défiler n'est pas un appui, et une lumière qui suivrait le
    // contenu se lirait comme un halo sale (voir [LocalGlassScrollFreeze]). Au repos, la lumière
    // revient toute seule — c'est le même état que celui qui porte la position de défilement.
    CompositionLocalProvider(LocalGlassScrollFreeze provides state.isScrollInProgress) {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            // **n° 7 — la position de défilement vient du volet** (`state`) : passer en recherche puis
            // revenir rend la liste **là où elle était**.
            state = state,
            // **La marge horizontale est portée par chaque bloc**, pas par la liste : c'est ce qui met
            // l'icône d'une tuile, le disque d'une rangée et le libellé d'une puce dans la **même
            // colonne** (16 dp), malgré leurs rembourrages internes différents. Une marge posée sur la
            // liste se serait ajoutée à tous, et les tuiles — qui ont la leur — auraient glissé de 8 dp
            // (mesuré sur la capture : bloc à 20 dp et icône à 24 quand le disque était à 16).
            contentPadding = PaddingValues(bottom = OnyxSpace.Snug),
            // **Aucun séparateur entre les rangées**, et un **rythme régulier** : l'écart de rangée est
            // une marche de l'échelle (4 dp), la même entre toutes. La liste ne se dessine plus par des
            // filets — elle se lit par son rythme.
            verticalArrangement = Arrangement.spacedBy(OnyxSpace.Hair),
        ) {
            header()
            if (rows.isEmpty()) {
                item(key = "empty") {
                    BasicText(
                        text = if (query.isBlank()) "No conversations yet." else "No matches.",
                        style = theme.footnote.copy(color = mutedInk(sidebarPaneColor(theme))),
                        modifier = Modifier.padding(horizontal = OnyxSpace.Base, vertical = OnyxSpace.Base),
                    )
                }
            }
            items(rows, key = { it.id }) { row ->
                val content: @Composable (Modifier) -> Unit = { gesture ->
                    ConversationRow(
                        row = row,
                        selecting = selecting,
                        selected = row.id in selectedIds,
                        onClick = { rect -> if (selecting) onToggleRow(row) else onOpenRow(row, rect) },
                        onLongPress = onMenuRow?.let { menu -> { rect -> menu(row, rect) } },
                        gesture = gesture,
                    )
                }
                // En multi-sélection, la rangée n'a pas de pilule : le geste y coche, et rien d'autre.
                val actions = if (selecting) null else rowActions?.invoke(row)
                if (actions != null) {
                    SwipeableConversationRow(
                        conversation = actions.conversation,
                        edgeDeadZonePx = edgeDeadZone,
                        onPin = { onSwipePin(actions) },
                        onArchive = { onSwipeArchive(actions) },
                        content = content,
                    )
                } else {
                    content(Modifier)
                }
            }
        }
    }
}

/**
 * Une ligne : pastille d'état à gauche, titre, **âge à droite**, seconde ligne dessous. En mode
 * multi-sélection, la pastille cède la place à la **case** cochée, et le tap coche au lieu
 * d'ouvrir. L'appui long ouvre la feuille de la conversation ([SidebarRowActions]).
 *
 * [gesture] est le geste du **glissement**, posé par [SwipeableConversationRow]. Il l'est **après**
 * l'appui : dans la chaîne des modificateurs, le plus interne reçoit les événements en premier, et
 * c'est ce qui permet au glissement de consommer le doigt avant que le clic ne s'en saisisse.
 */
@Composable
private fun ConversationRow(
    row: SidebarRow,
    selecting: Boolean,
    selected: Boolean,
    /** Le tap : la rangée passe ses **limites** (la couverture d'ouverture en part), ou l'état. */
    onClick: (Rect?) -> Unit,
    onLongPress: ((Rect) -> Unit)? = null,
    gesture: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    // Le coin de la rangée : celui du contrôle en multi-sélection, celui de la **carte** sinon —
    // la carte de conversation porte le coin de la famille des cartes ([OnyxSocle.RadiusCard]).
    val shape = if (selecting) {
        RoundedCornerShape(OnyxSpace.ControlCorner)
    } else {
        RoundedCornerShape(OnyxSocle.RadiusCard)
    }
    // Le fond réel de la liste : le **panneau**. En sombre il vaut la page, donc rien ne bouge ; en
    // clair il est quasi blanc, et c'est contre lui que l'âge et la seconde ligne doivent tenir
    // 4,5:1.
    val pane = sidebarPaneColor(theme)
    // **Une seule colonne à gauche** : la marge de la liste (8 dp) plus le rembourrage de la rangée
    // (8 dp) posent le cercle à 16 dp du bord du panneau — exactement là où commence le texte de la
    // tête et où commence l'icône d'une tuile.
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val rowLabel = listOfNotNull(
        row.title.takeIf { it.isNotBlank() },
        sidebarAgeLabel(row.age).takeIf { it.isNotEmpty() },
    ).joinToString(", ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // **Le rythme est constant** : une rangée fait 56 dp, qu'elle porte une seconde ligne ou
            // non. C'est ce qui donne à la liste son pas régulier, maintenant qu'aucun séparateur ne
            // la dessine.
            .defaultMinSize(minHeight = OnyxSpace.RowHeight)
            .clip(shape)
            .onyxTappable(
                onClick = { onClick(bounds) },
                onLongPress = onLongPress?.let { press -> { bounds?.let(press) } },
            )
            // **La rangée se annonce d'une voix** : son titre, son état et son âge — ce qu'elle
            // montre. Relevé d'arbre : sans ce libellé, la cellule cliquable était muette.
            .tapLabel(rowLabel)
            .then(gesture)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            // **Le rembourrage de la colonne** (16 dp) : c'est lui qui pose le disque dans la
            // colonne d'alignement du volet — la même que le texte de la tête et l'icône d'une
            // tuile. La rangée, elle, reste pleine largeur : son geste et son appui vont jusqu'aux
            // bords, comme dans la référence.
            .padding(horizontal = OnyxSpace.Base, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
    ) {
        if (selecting) {
            SelectionMark(selected = selected)
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = row.title,
                    localize = false,
                    style = theme.subheadline.copy(color = theme.textColor),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val second = row.subtitle
                if (!second.isNullOrBlank()) {
                    BasicText(
                        text = second,
                        localize = true,
                        style = theme.caption.copy(color = mutedInk(pane)),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            val ageSelecting = sidebarAgeLabel(row.age)
            if (ageSelecting.isNotEmpty()) {
                BasicText(
                    text = ageSelecting,
                    localize = false,
                    style = theme.caption.copy(color = mutedInk(pane), fontWeight = FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            // **La carte de conversation**, et non plus un disque de 26 px : une couverture
            // générative **pleine largeur** en tête de carte, le titre à 15 sp, l'aperçu tronqué,
            // l'épingle et la méta en monospace. L'état (en cours, en attente, non lue) n'est pas
            // retiré : il passe **sur** la couverture. Voir [ConversationCoverCard].
            ConversationCoverCard(
                title = row.title,
                id = row.id,
                preview = row.subtitle,
                metaLeft = row.projectLabel,
                metaRight = sidebarAgeLabel(row.age).takeIf { it.isNotEmpty() },
                pinned = row.pinned,
                // **La marque d'identité du bot de cette conversation**, dans le coin haut-droit :
                // le disque de sa couleur, son initiale (ou la rune de la maison) à l'encre mesurée,
                // et l'état posé sur la marque. La géométrie et l'écart viennent des constantes de
                // la maison (`IdentityMark`) — aucun nombre n'est écrit ici.
                state = { ToneDisc(row.tone, row.identity, row.identityFace) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Une rangée **à deux gestes** : vers la **droite** elle découvre à sa gauche la pilule
 * « Épingler », vers la **gauche** la pilule « Archiver » à sa droite. Les deux pilules sont
 * **claires** ([sidebarSwipePillColor]) et portent leur icône et leur libellé — jamais un pavé de
 * couleur, jamais de rouge. Pendant le geste, la rangée **devient une carte à coins arrondis**
 * ([sidebarSwipeCard]) ; au relâchement elle **revient en place**, que l'action soit partie ou non.
 *
 * Le geste **franc** — au-delà de [SidebarSwipeThreshold] — applique l'action **tout de suite** : le
 * bandeau d'annulation du volet laisse de quoi la défaire, et il n'y a **aucune** confirmation à
 * confirmer. La suppression n'est pas ici : elle vit dans l'**appui long**, avec sa confirmation —
 * un geste large vers elle serait un piège.
 *
 * La **zone morte du bord** ([edgeDeadZonePx]) est le prix du geste vers la droite : un doigt posé
 * dans la bande que le système réserve au retour de bord n'est **jamais** revendiqué par la rangée,
 * il reste au geste qui ouvre le volet.
 */
@Composable
private fun SwipeableConversationRow(
    conversation: Conversation,
    /** La zone morte du bord gauche, en pixels : en deçà, la rangée ne revendique rien. */
    edgeDeadZonePx: Float,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    /** Le contenu de la rangée ; il reçoit le **geste** à poser chez lui, après son appui. */
    content: @Composable (Modifier) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val scope = rememberCoroutineScope()
    // La largeur de la pilule découverte, mesurée par la pilule elle-même : la rangée ne s'écarte que
    // de ce qu'il y a à découvrir — et comme cette largeur suit la police, la pilule reste entière
    // à 1,3×.
    var pillWidth by remember { mutableIntStateOf(0) }
    // La pose de la rangée : 0 au repos, positive vers la droite (pilule « Épingler »), négative
    // vers la gauche (pilule « Archiver »). Elle suit le doigt, puis rejoint sa place.
    val slide = remember { Animatable(0f) }
    // Vrai du premier doigt franc jusqu'à ce que la rangée soit **revenue** : c'est ce qui porte la
    // carte et son ombre, sans quoi la carte retomberait avant d'être rentrée.
    var dragging by remember { mutableStateOf(false) }
    // La course du doigt, le seuil et la zone morte sont lus par l'**état mis à jour**, jamais
    // capturés : la largeur de la pilule n'arrive qu'à la mesure, et une clé de geste qui changerait
    // à ce moment-là **couperait le glissement en cours**.
    val travel = rememberUpdatedState(
        with(LocalDensity.current) {
            (if (pillWidth > 0) pillWidth.toFloat() else SidebarSwipeFallbackReveal.toPx()) +
                SidebarSwipeSlack.toPx()
        },
    )
    val threshold = rememberUpdatedState(with(LocalDensity.current) { SidebarSwipeThreshold.toPx() })
    val deadZone = rememberUpdatedState(edgeDeadZonePx)
    // **La distance minimale d'un geste**, en pixels : elle retient la **révélation** (voir
    // [sidebarSwipeRevealPx]) — jamais la classification, qui doit tomber au seuil système pour que
    // la rangée et le geste du volet s'arbitrent franchement. Lue par l'état mis à jour, comme le
    // seuil et la zone morte : une clé de geste qui changerait en cours de route couperait le geste.
    val axisMin = rememberUpdatedState(with(LocalDensity.current) { SidebarSwipeAxisMin.toPx() })
    // Le retour en place est un **geste de la main** : la famille « micro » de la maison — une seule
    // courbe pour tous les chemins, sans rebond (une rangée qui dépasserait sa place découvrirait la
    // pilule de l'autre sens) — et sous « Réduire les animations », aucune image intermédiaire.
    val settle = MotionFamily.Tab.animated<Float>()

    // Le geste lit la **passe Initial** : le doigt lui arrive avant le clic de la rangée, et c'est ce
    // qui rend le glissement possible — par la passe ordinaire, le clic de la rangée recevait le doigt
    // d'abord et le glissement n'obtenait qu'un doigt déjà levé.
    //
    // La clé est la conversation **et elle seule** : dès qu'une épingle ou une archive change, le
    // geste est reposé avec les rappels de l'état courant, jamais ceux d'avant l'action.
    val gesture = Modifier.pointerInput(conversation) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            // **L'axe est tranché une seule fois, au début du geste** (voir [sidebarSwipeAxis]) : un
            // glissement vertical ne doit **jamais** engager une action de rangée. Tant que l'axe
            // n'est pas tranché, la rangée ne révèle rien et ne consomme rien ; une fois qu'il est
            // **vertical**, la révélation ne peut plus s'engager dans ce geste, même si le doigt
            // dévie ensuite — le défilement de la liste garde le doigt.
            var axis = SidebarSwipeAxis.UNDECIDED
            var total = 0f
            var across = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val delta = change.positionChange()
                total += delta.x
                across += delta.y
                if (axis == SidebarSwipeAxis.UNDECIDED) {
                    // L'axe est tranché **dès le seuil système** : la rangée consomme le doigt sur
                    // l'événement même où le glissement du volet le revendiquerait, et l'un des deux
                    // gagne franchement — c'est ce qui garde le geste du bord intact.
                    axis = sidebarSwipeAxis(total, across, slop)
                    if (axis == SidebarSwipeAxis.UNDECIDED) continue
                    // Un geste vertical (ou oblique non franchement horizontal) **reste au
                    // défilement** : rien n'est révélé, rien n'est consommé, et cette rangée n'y
                    // reviendra pas de tout le geste.
                    if (axis == SidebarSwipeAxis.VERTICAL) return@awaitEachGesture
                    // Vers la **droite**, un doigt posé dans la **zone morte du bord** reste au geste
                    // qui ouvre le volet : la rangée ne le revendique pas et ne consomme rien.
                    if (total > 0f && down.position.x <= deadZone.value) return@awaitEachGesture
                    dragging = true
                }
                change.consume()
                // **Rien n'est révélé avant la distance minimale** : la rangée part de zéro à cet
                // instant, puis suit le doigt — elle ne saute pas sous lui.
                val next = sidebarSwipeRevealPx(total, axisMin.value)
                    .coerceIn(-travel.value, travel.value)
                scope.launch { slide.snapTo(next) }
            }
            if (axis != SidebarSwipeAxis.HORIZONTAL) return@awaitEachGesture
            // La décision se prend sur ce que le doigt a **parcouru**, jamais sur le seul dernier
            // événement reçu : un geste interrompu a déjà parcouru sa course, et l'ignorer ferait
            // sauter la rangée sous le doigt.
            val pin = total >= threshold.value
            val archive = total <= -threshold.value
            // Le retour en place est lancé **avant** l'action, et depuis la portée de la rangée :
            // l'action change la conversation, donc la clé du geste se repose — une animation portée
            // par le geste serait emportée avec lui, et la rangée resterait à mi-course.
            scope.launch {
                slide.animateTo(0f, settle)
                dragging = false
            }
            when {
                pin -> onPin()
                archive -> onArchive()
            }
        }
    }

    // **Rien n'est mémorisé ici** : la carte et la pilule se lisent sur la **pose** elle-même. Une
    // pose à zéro est une rangée au repos — donc aucune carte fantôme, et aucun nœud invisible dans
    // l'arbre d'accessibilité, même après un doigt interrompu.
    val lifted = slide.value > 0.5f || slide.value < -0.5f

    Box(modifier = Modifier.fillMaxWidth()) {
        // Les pilules vivent **derrière** la rangée : elles sont **découvertes** par le glissement,
        // jamais posées devant lui. Celle du côté que le doigt découvre est la seule composée — donc
        // aucune cible invisible ne reste au bord d'une rangée.
        if (slide.value > 0.5f) {
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.CenterStart) {
                SidebarSwipeAction(
                    label = if (conversation.pinned) "Unpin" else "Pin",
                    symbol = Symbols.Pin,
                    onWidth = { pillWidth = it },
                    onClick = onPin,
                )
            }
        }
        if (slide.value < -0.5f) {
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                SidebarSwipeAction(
                    label = if (conversation.archived) "Unarchive" else "Archive",
                    symbol = Symbols.ArchiveBox,
                    onWidth = { pillWidth = it },
                    onClick = onArchive,
                )
            }
        }
        Box(
            modifier = Modifier
                // La rangée suit le doigt exactement ; c'est la pilule qui est fixe, derrière elle.
                .offset { IntOffset(slide.value.roundToInt(), 0) }
                // **La rangée-carte** : pendant le geste, elle se détache de la liste — l'ombre de la
                // maison au clair, sa vitre en sombre ([sidebarSwipeCard]).
                .then(if (lifted) Modifier.sidebarSwipeCard() else Modifier),
        ) {
            content(gesture)
        }
    }
}

/**
 * Une action du glissement : **une pilule claire** — la teinte des creux au clair, un blanc voilé en
 * sombre ([sidebarSwipePillColor]) — avec son **icône et son libellé**, dans l'encre qui s'y lit le
 * mieux ([sidebarSwipePillInk]).
 *
 * Aucune couleur saturée, aucun aplat : la pilule se lit par sa **forme** (un galet,
 * [SidebarSwipePillShape]) et par son air avec les bords, comme tout le reste du volet. Sa hauteur
 * est un **plancher** — la cible tactile de la maison ([MinTarget], 44 dp) — et sa largeur celle de
 * son libellé, jamais un nombre écrit : à police 1,3×, la pilule grandit au lieu de rogner son mot.
 */
@Composable
private fun SidebarSwipeAction(
    label: String,
    symbol: Int,
    onClick: () -> Unit,
    /** La largeur de la pilule : la rangée n'a besoin que de ce qu'il y a à découvrir. */
    onWidth: (Int) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val ink = sidebarSwipePillInk(theme)
    Row(
        modifier = Modifier
            // L'air de la pilule avec le bord du panneau : c'est aussi la marge de la zone morte.
            .padding(horizontal = SidebarSwipeMargin)
            .defaultMinSize(minHeight = MinTarget)
            .onSizeChanged { onWidth(it.width) }
            .sidebarSwipePill()
            // La **cible** et l'**étiquette** couvrent la pilule entière : le rembourrage interne est
            // posé **après** elles, sinon le contrôle se réduit à son texte (mesuré : 19,8 dp de haut
            // au lieu des 44 dp de la capsule) et la pilule n'est touchable qu'au milieu.
            .onyxClickable(onClick = onClick)
            .semanticsLabel(label)
            // Le rembourrage est **à l'intérieur** de la pilule : sans lui, le libellé touchait le
            // bord du galet et se faisait rogner par les coins (mesuré au zoom ×3 : le « r » de
            // « Épingler » à 1 px du bord). Le plancher de hauteur reste la cible de la maison, et le
            // rembourrage vertical laisse la pilule grandir à 1,3× au lieu de serrer son mot.
            .padding(horizontal = OnyxSpace.Base, vertical = OnyxSpace.Snug),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
    ) {
        // L'icône est **décorative** : le libellé est l'étiquette du contrôle entier
        // ([semanticsLabel]), et le redire ici ferait deux annonces pour une seule pilule.
        OnyxIcon(symbol = symbol, tint = ink, size = 18.dp, contentDescription = null)
        BasicText(
            // Le libellé **se traduit** (« Pin » / « Épingler »), comme les lignes de la feuille.
            text = label,
            localize = true,
            style = theme.caption.copy(color = ink, fontWeight = FontWeight.Medium),
            maxLines = 2,
            // Une seule ligne, **avec ellipse** : jamais un mot coupé au milieu (« Épingl… »), la
            // règle du volet que `ResponsiveLayoutTest` tient.
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * La **case** d'une rangée en multi-sélection : un rond plein à la couleur d'accent quand elle est
 * cochée, un anneau vide sinon. Mesurée comme la pastille qu'elle remplace (26 dp), pour que la
 * liste ne se décale pas d'un mode à l'autre.
 */
@Composable
private fun SelectionMark(selected: Boolean) {
    val theme = LocalOnyxTheme.current
    val fill = if (selected) OnyxContrast.over(theme.accent, theme.pageColor) else theme.pageColor
    Box(
        modifier = Modifier.size(26.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(fill, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                OnyxIcon(
                    symbol = Symbols.Checkmark,
                    tint = OnyxContrast.inkOn(fill),
                    size = 14.dp,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(1.dp, theme.separator, CircleShape),
            )
        }
    }
}

/**
 * **La marque d'identité d'une conversation — la pastille du coin haut-droit de sa carte.**
 *
 * Le propriétaire a vu, sur sa capture, deux ronds de teintes différentes sans règle apparente : un
 * rond ambre sur une carte, un rond bleu-violet sur une autre. Ce n'étaient pas des identités — la
 * pastille portait **l'état** (l'ambre d'une question qui attend, l'accent d'un tour en cours), et le
 * **même bot** montrait donc autre chose ici que dans la liste des bots, la fiche, l'en-tête ou le
 * volet. Deux marques pour une identité : la divergence que la maison interdit.
 *
 * Le disque est maintenant **celui du bot** (`IdentityMark`), son glyphe est son initiale — la rune
 * de la maison pour le bot principal —, et l'état ne se perd pas : il passe **sur** la marque, dans
 * le point de présence, à la teinte et à la géométrie de la maison. La vignette de couverture et le
 * voile noir qui vivaient sous l'encre sont retirés : c'est eux qui faisaient que l'encre *annoncée*
 * n'était pas l'encre *peinte* (le fond réel était un mélange, écrit à la main ici même).
 */
@Composable
private fun ToneDisc(tone: SidebarRowTone, identity: String, faceCode: String?) {
    IdentityMark(
        profile = identity,
        faceCode = faceCode,
        presence = sidebarPresenceOf(tone),
    )
}

// ---------------------------------------------------------------------------------------------
// 8. Le pied
// ---------------------------------------------------------------------------------------------

/**
 * « New conversation » pleine largeur, et le rond de l'initiale — **sur la même ligne**.
 *
 * C'est le pied de la référence : une **grande pilule pleine**, la couleur du thème ajustée pour
 * porter son mot ([OnyxContrast.filled] puis [OnyxContrast.inkOn]) — jamais un aplat de `fill` qui
 * se lirait comme une rangée désactivée —, et le rond de l'initiale à droite, qui porte un **point
 * d'état** : vert quand la passerelle répond, ambre quand elle ne répond pas, gris quand on ne sait
 * pas encore. Le point est **mesuré** sur le rond (3:1) ; l'état ne dépend donc jamais de la seule
 * couleur pour se lire — la tête du volet le dit aussi en toutes lettres.
 *
 * La pilule ne change plus de couleur avec l'onglet affiché (`highlightNew`) : dans la référence,
 * « Nouvelle conversation » est **toujours** le bouton plein du pied, et un bouton qui s'éteint selon
 * l'onglet se lit comme un bouton désactivé.
 */
@Composable
private fun SidebarFooter(
    onNewConversation: () -> Unit,
    onOpenAccount: () -> Unit,
    reachable: Boolean?,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
    ) {
        val fill = OnyxContrast.filled(theme.accent)
        val ink = OnyxContrast.inkOn(fill)
        val avatarFill = OnyxContrast.over(theme.accent.copy(alpha = 0.16f), sidebarPaneColor(theme))
        Row(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = MinTarget)
                .clip(CircleShape)
                .background(fill)
                .onyxClickable(onClick = onNewConversation)
                .tapLabel(resolveOnyxText("New conversation"))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            OnyxIcon(symbol = Symbols.SquareAndPencil, tint = ink, size = 16.dp)
            BasicText(
                text = "New conversation",
                style = theme.subheadline.copy(
                    color = ink,
                    fontWeight = FontWeight.SemiBold,
                ),
                // Le libellé du pied **tient sur une ligne** : il ne se replie pas et ne se coupe pas
                // au milieu d'un mot quand le volet est étroit. Il ne glisse pas non plus (`softWrap`
                // à faux, n° 2) et il se termine par une **ellipse** plutôt qu'une coupure sèche
                // (n° 3) : à 1,3× le libellé ne doit jamais se briser au milieu d'un mot
                // (« Nouvelle conver… »), et jamais sortir de sa pastille.
                maxLines = 2,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // **Le logo de l'application**, et **son point d'état** au coin haut-droit : la même place
        // que sur le visage d'une carte de bot, donc la même lecture.
        //
        // Ce rond portait l'**initiale de la passerelle** (« O ») — « faute de mieux », disait le
        // code. Une initiale n'est pas un signe : c'est un morceau de mot. Le rond porte donc la
        // **marque du lanceur** — la plaque sombre, le cercle et la rune, aux couleurs de l'icône —
        // et il ouvre toujours la feuille de compte. Le point d'état n'est pas le logo : il reste
        // une couleur d'information, mesurée sur le rond.
        Box(modifier = Modifier.size(OnyxSocle.ThumbnailSize)) {
            OnyxBrandMark(
                size = OnyxSocle.ThumbnailSize,
                modifier = Modifier
                    .onyxClickable(onClick = onOpenAccount)
                    .tapLabel(resolveOnyxText("Account")),
            )
            // Le point : vert / ambre / gris, mesuré sur le rond de l'initiale.
            val signal = when (reachable) {
                true -> theme.success
                false -> theme.warning
                null -> theme.tertiaryText
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .background(sidebarPaneColor(theme), CircleShape)
                    .padding(2.dp)
                    .background(signalInk(signal, avatarFill), CircleShape),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Les pièces partagées
// ---------------------------------------------------------------------------------------------

/**
 * Une puce : son libellé, son compte quand elle en porte un, et l'état choisi.
 *
 * **Au clair, une puce est plate et pleine** : la puce au repos prend le **gris des creux**
 * ([flatWellColor]) avec une encre foncée mesurée dessus, et la puce choisie est une **pastille
 * pleine et foncée** — l'encre du texte pour fond, le blanc pour mot, ce que [OnyxContrast.inkOn]
 * décide. C'est le couple que la référence montre, et il remplace la teinte d'accent à 16 % : sur un
 * panneau clair, un fond teinté se lit comme un état « survolé », pas comme un choix.
 *
 * [dot] est la couleur d'information de la puce (le filtre « En cours » est bleu, « Vous attend »
 * ambre) : elle est portée par un **point** mesuré à 3:1 sur le fond réel de la puce — pleine,
 * il est mesuré contre le foncé, au repos contre le gris —, jamais par le libellé. Une couleur
 * seule ne doit pas être ce qui distingue deux filtres.
 *
 * Sa **hauteur est constante** ([FlatChipHeight], 32 dp) dans les deux états : une puce choisie ne
 * grandit pas de deux pixels sous le doigt, ce qui faisait sauter toute la rangée.
 */
@Composable
private fun SidebarChip(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    dot: Color? = null,
) {
    val theme = LocalOnyxTheme.current
    val dark = theme.dark
    val pane = sidebarPaneColor(theme)
    val shape = CircleShape
    // Les deux fonds : la pastille pleine et foncée au clair quand c'est choisi, le gris des creux
    // sinon. En sombre, on garde exactement les valeurs d'avant (l'accent à 16 % sur la page).
    // **La pastille choisie est PLEINE À L'ACCENT** — `.chip2.on{background:var(--accfill);
    // border-color:transparent;color:#fff}` (`hcss.py:107`), dans les **deux** apparences. Elle
    // portait au clair un aplat d'**encre** (le texte pour fond) : la planche montre l'accent, et
    // c'est le seul aplat d'accent des pastilles.
    val chosenOnGround = OnyxContrast.filled(theme.accent)
    // **Les autres pastilles sont BLANCHES à filet** — `.chip2{background:var(--solid);
    // border:1px solid var(--line)}` (`hcss.py:96`). En sombre, on garde la marche du volet (le
    // panneau) : une seconde surface claire y ajouterait une marche qui n'existe pas dans la planche.
    val restingGround = if (dark) pane else theme.formSurfaceColor
    val ground = if (selected) chosenOnGround else restingGround
    val ink = when {
        selected -> OnyxContrast.inkOn(chosenOnGround)
        dark -> mutedInk(ground)
        // Le repos : une encre foncée mesurée sur le blanc de la pastille, donc jamais un gris pâle
        // posé au hasard sur un blanc.
        else -> sidebarInk(theme.textColor, ground, OnyxContrast.TEXT_MIN)
    }
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = MinTarget)
            .clip(shape)
            .onyxTappable(onClick = onClick)
            .tapLabel(listOfNotNull(resolveOnyxText(label), count?.toString()).joinToString(", ")),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .height(FlatChipHeight)
                .clip(shape)
                .then(
                    when {
                        selected -> Modifier.background(chosenOnGround)
                        dark -> Modifier
                        // **La pastille blanche porte son filet** — `.chip2` de la planche
                        // (`hcss.py:96`), et c'est ce filet qui la détache du panneau clair.
                        else -> Modifier
                            .background(restingGround)
                            .border(FlatHairlineDp, theme.separator, shape)
                    },
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
        ) {
            if (dot != null) {
                // Sur la pastille choisie, le point passe au **blanc** (`.chip2.on .dd{background:
                // #fff}`, hcss.py:109) ; au repos il garde sa couleur d'information (« En cours »
                // bleu, « Vous attend » ambre), mesurée sur le fond réel de la pastille.
                Box(
                    Modifier
                        .size(7.dp)
                        .background(
                            if (selected) Color.White else signalInk(dot, restingGround),
                            CircleShape,
                        ),
                )
            }
            BasicText(
                text = label,
                style = theme.footnote.copy(color = ink, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (count != null && count > 0) {
                BasicText(
                    text = count.toString(),
                    localize = false,
                    style = theme.caption.copy(
                        color = if (selected) ink else mutedInk(ground),
                    ),
                    maxLines = 1,
                    // Le compteur garde l'ellipse de la rangée : un nombre ne se coupe pas.
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Le compteur d'une tuile : un petit rond plein, lisible sur les deux apparences.
 *
 * **Délègue au compteur de la maison** ([CounterBadge]) depuis la correction du rognage : la
 * pastille ici portait une hauteur **fixe** (18 dp), un rembourrage vertical de **1 dp** et aucune
 * largeur calculée — le même défaut que la barre d'onglets, en plus petit. La géométrie vient
 * maintenant de [BadgeGeometry], comme partout ailleurs : un chiffre donne un carré, deux et trois
 * chiffres élargissent la pastille, et le corps du texte décide de sa hauteur.
 */
@Composable
internal fun BadgeCount(value: Int) {
    val theme = LocalOnyxTheme.current
    val fill = OnyxContrast.filled(theme.accent)
    CounterBadge(
        count = value,
        fill = fill,
        contentColor = OnyxContrast.inkOn(fill),
    )
}

/**
 * Une encre « seconde ligne », **mesurée** pour tenir 4,5:1 sur [ground] : l'adresse de la
 * passerelle, l'âge d'une conversation, la description d'un bot. `tertiaryLabel` du système ne
 * tient pas ce seuil (1,7:1 mesuré en clair) — d'où cette encre, plus claire que le texte mais
 * jamais sous le seuil.
 */
@Composable
internal fun mutedInk(ground: Color): Color =
    sidebarInk(LocalOnyxTheme.current.textColor, ground, OnyxContrast.TEXT_MIN)

/** Une encre d'**icône**, mesurée pour tenir 3:1 sur [ground] (la marge du trait antialiasé). */
@Composable
internal fun mutedIconInk(ground: Color): Color =
    sidebarInk(LocalOnyxTheme.current.textColor, ground, OnyxContrast.ICON_MIN + 0.5f)

/**
 * **L'invite d'un champ du volet** — « Search chats », et la même règle pour tout champ à venir.
 *
 * Elle valait `mutedInk(ground)`, c'est-à-dire l'encre pleine du thème **atténuée jusqu'au
 * plancher** : le rapport tombait donc **exactement sur 4,50:1** de la page, sans aucune marge. Ce
 * n'est pas un doute théorique : à l'écran, un gris calculé au ras du seuil se lit « gris sombre sur
 * fond sombre », et le propriétaire l'a relevé sur la capture du volet en apparence sombre.
 *
 * La marge est celle que la maison s'accorde déjà pour l'encre d'une icône au repos
 * (`BarInk.IDLE_MARGIN`, un demi-point) : une encre est **antialiasée**, donc le pixel de cœur
 * atteint la couleur et son voisin non. L'invite est donc tenue à **5,00:1** de la page, et ce
 * chiffre est celui que le verrou de contraste relit.
 */
@Composable
internal fun sidebarFieldInk(theme: com.onyx.app.ui.theme.ResolvedTheme): Color =
    sidebarFieldInkOn(theme)

/**
 * La même règle, **sans composition** : c'est elle que le verrou de contraste interroge, donc le
 * chiffre annoncé dans le rapport et le chiffre vérifié par le test sont **le même calcul**.
 */
internal fun sidebarFieldInkOn(theme: com.onyx.app.ui.theme.ResolvedTheme): Color =
    sidebarInk(theme.textColor, theme.pageColor, OnyxContrast.TEXT_MIN + 0.5f)

/** Une couleur d'**information** (voyant, pastille), mesurée pour tenir 3:1 sur [ground]. */
@Composable
internal fun signalInk(colour: Color, ground: Color): Color =
    sidebarInk(colour, ground, OnyxContrast.ICON_MIN)

/** La cible tactile de la maison : rien de ce qui se touche ne descend sous 44 dp. */
internal val MinTarget: Dp = 44.dp

/**
 * La hauteur d'une **tuile** du volet : [OnyxSpace.ThumbnailSize], c'est-à-dire 44 dp.
 *
 * Elle est **constante** — c'est le point du lot : deux rangées de tuiles se lisent comme une grille
 * si toutes les tuiles ont la même hauteur, et une seule tuile à deux lignes faisait sauter la
 * rangée entière. La valeur est reprise de la taille de vignette de la maison plutôt qu'écrite ici :
 * une tuile et une vignette de rangée sont la même cible tactile.
 */
internal val SidebarTileHeight: Dp = OnyxSpace.ThumbnailSize

/**
 * **Le carré blanc de l'icône d'une tuile** — le bloc `.tileic` de la planche
 * (`hcss.py:90`) : 32 × 32, rayon 14 (`OnyxSocle.RadiusField`), fond plein et filet de 1 px.
 *
 * C'est lui qui a été ajouté pour coller à la planche : l'icône d'une tuile n'y porte jamais
 * **directement** sur la tuile, elle est posée dans ce carré — et c'est ce carré qui donne aux six
 * tuiles le même axe optique.
 */
internal val TileIconSquare: Dp = 32.dp
