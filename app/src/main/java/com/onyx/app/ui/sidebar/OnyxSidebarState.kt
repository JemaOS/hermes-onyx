package com.onyx.app.ui.sidebar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.canShowTwoPanes
import com.onyx.app.ui.sidebarContentWidthFor
import com.onyx.app.ui.sidebarWidthFor

/**
 * Le **mode** du volet, décidé par la largeur de la fenêtre — jamais par un réglage.
 *
 * Deux modes, parce que deux comportements réels : étroit, le volet glisse **par-dessus** le fil
 * (la surimpression) ; large, il est **posé** à côté de lui. Le seuil n'est pas inventé ici : c'est
 * celui des deux panneaux de l'app ([canShowTwoPanes]), celui qui garantit au fil ses 360 dp.
 */
enum class SidebarMode {
    /** Téléphone étroit, téléphone large : le volet couvre le fil, et se referme en un geste. */
    COMPACT,

    /** Pliable déplié, tablette, paysage large : le volet est posé, le fil n'est pas recouvert. */
    DOCKED,
}

/** Le mode du volet pour cette largeur. Pur, donc mesuré par un test. */
fun sidebarModeFor(availableWidth: Dp): SidebarMode =
    if (canShowTwoPanes(availableWidth)) SidebarMode.DOCKED else SidebarMode.COMPACT

/**
 * La largeur du volet **en surimpression** : **toute la fenêtre**.
 *
 * Relevé sur la vidéo du propriétaire (Honor replié, écran étroit) : le volet s'arrêtait à 86 % de
 * la fenêtre, si bien qu'il **restait une bande du fil** au bord droit — et comme le fil glissait
 * avec le volet, ce qui y apparaissait était son **pied** (le compositeur), lu comme une seconde
 * copie du volet collée au bord. Le relevé du lot 3 (téléphone 411 dp et pliable replié) nomme le
 * même défaut : une **bande résiduelle sombre** de 71 dp sous le voile, et les **liserés sombres**
 * que les coins arrondis du panneau y dessinaient en thème clair. Ce qui reste à côté du volet ne
 * se lit donc jamais comme un second panneau : c'est un morceau de fil coupé.
 *
 * Le volet en surimpression prend donc la fenêtre **entière** : rien n'est laissé à côté, il n'y a
 * plus de bande à confondre avec lui. La sortie garde ses gestes, tous mesurés : le retour matériel
 * ([OnyxSidebarState.close] par la racine du volet), le glissement vers la gauche, et l'appui sur
 * le voile tant qu'il en reste un pendant le glissement.
 *
 * La largeur **élargie** d'un écran (400…560 dp, [sidebarContentWidthFor]) ne sert **qu'aux deux
 * panneaux** : en surimpression, elle ne s'applique jamais (voir [sidebarWidthForMode]).
 */
fun sidebarOverlayWidthFor(availableWidth: Dp): Dp = availableWidth.coerceAtLeast(0.dp)

/** Le volet posé, pour cette largeur : les bornes de la géométrie des deux panneaux. */
fun sidebarDockedWidthFor(availableWidth: Dp): Dp = sidebarWidthFor(availableWidth)

/** Comment le volet est dessiné pour cette largeur : surimpression ou posé. */
fun sidebarWidthForMode(availableWidth: Dp, mode: SidebarMode, content: Boolean = false): Dp = when (mode) {
    SidebarMode.COMPACT -> sidebarOverlayWidthFor(availableWidth)
    SidebarMode.DOCKED -> if (content) {
        // Le volet porte un **écran** : la colonne de la liste (320…400 dp) l'écraserait, il prend
        // la largeur d'un écran de la maison (`sidebarContentWidthFor`).
        sidebarContentWidthFor(availableWidth)
    } else {
        sidebarDockedWidthFor(availableWidth)
    }
}

/**
 * L'état du volet, **mémorisé par largeur**.
 *
 * Deux mémoires, une par mode, comme le panneau latéral de la référence : le volet en surimpression
 * démarre **fermé** (il recouvre le fil, il n'a rien à y faire au premier écran), le volet posé
 * démarre **ouvert** (il ne recouvre rien). Surtout, elles ne se contaminent pas : replier le volet
 * posé sur une tablette ne referme pas la surimpression du téléphone, et ouvrir la surimpression ne
 * déplie pas le panneau posé. C'est l'exigence « ce qui est ouvert sur grand écran ne doit pas
 * l'être en surimpression sur petit » : chaque largeur retrouve **son** état, pas celui de l'autre.
 *
 * [isOpen] est donc **le même geste** partout (un appui sur le contrôle de la barre, le retour
 * matériel, le geste du bord) : il ne fait qu'écrire la mémoire du mode courant.
 */
@Stable
class OnyxSidebarState internal constructor() {

    /** Le mode courant, posé par la largeur de la fenêtre. */
    var mode: SidebarMode by mutableStateOf(SidebarMode.COMPACT)
        private set

    /** La surimpression : le volet du téléphone, fermé au premier écran. */
    private var drawerOpen: Boolean by mutableStateOf(false)

    /** Le volet posé : ouvert par défaut, il ne recouvre rien. */
    private var panelOpen: Boolean by mutableStateOf(true)

    /**
     * Le volet est-il en **mode recherche** ?
     *
     * La recherche n'est pas un filtre posé sur la liste : c'est un **écran du volet**. Tant qu'elle
     * est ouverte, les tuiles, les projets, le sélecteur *Chats / Bots* et les filtres s'effacent —
     * le champ et ses résultats sont le seul contenu.
     *
     * Elle vit **ici**, à côté de l'ouverture, pour une raison précise : le retour (le geste du bord
     * ou le bouton système) doit la quitter **avant** de refermer le volet. Un état qui ne
     * connaîtrait pas les deux ne pourrait pas les ordonner.
     */
    private var search: Boolean by mutableStateOf(false)

    /** Le volet montre-t-il la recherche ? */
    val searching: Boolean get() = search

    /** Le volet est-il ouvert, dans le mode courant ? */
    val isOpen: Boolean get() = if (mode == SidebarMode.DOCKED) panelOpen else drawerOpen

    /**
     * **Les écrans ouverts dans le volet**, du plus ancien au plus récent.
     *
     * La **racine** du volet n'est pas une entrée de cette pile : la racine, c'est la liste
     * elle-même (conversations, bots, tuiles). Une tuile **empile** son écran, la flèche du volet
     * **dépile** un niveau, et le pied (« Nouvelle conversation » et le rond d'initiale) reste en
     * place tout du long — le volet est un **conteneur de navigation**, pas un état qu'on referme.
     *
     * C'est l'inverse du choix précédent, qui **fermait** le volet en ouvrant une tuile : le
     * propriétaire a demandé explicitement « quand je clique sur Aperçus, on reste dans le volet ».
     */
    private val destinations: SnapshotStateList<String> = mutableStateListOf()

    /** L'écran que le volet montre, ou `null` quand il montre sa racine (la liste). */
    val destination: String? get() = destinations.lastOrNull()

    /** Le volet est-il **sur un écran** (et non sur sa liste) ? C'est ce qui décide de sa largeur. */
    val isNavigating: Boolean get() = destinations.isNotEmpty()

    /** Une tuile ouvre son écran **dans** le volet : on empile, on ne ferme rien. */
    fun pushDestination(route: String) {
        // Deux appuis sur la même tuile n'empilent pas deux fois le même écran.
        if (destinations.lastOrNull() == route) return
        destinations.add(route)
    }

    /**
     * La flèche du volet — et le retour système, qui appellent tous deux ceci — **dépile un niveau,
     * jamais plus** : l'écran rend la place au précédent, et la liste revient quand la pile est vide.
     *
     * @return `true` si un écran a été dépilé ; `false` quand le volet était déjà sur sa liste — auquel
     *   cas l'appelant ferme le volet, qui est la marche **suivante** de la même descente.
     */
    fun popDestination(): Boolean = destinations.removeLastOrNull() != null

    /** Le volet **repart de sa racine** : la pile des écrans est vidée, la liste revient. */
    fun clearDestinations() {
        destinations.clear()
    }

    /**
     * Entre en recherche : c'est ce que fait le premier appui sur le champ (ou son focus).
     *
     * Ne touche pas à l'ouverture : sur un téléphone le volet est déjà ouvert par le geste qui a
     * mené au champ, et sur un pliable il est posé.
     */
    fun enterSearch() {
        search = true
    }

    /**
     * Sort de la recherche — et **rien de plus**.
     *
     * L'ouverture du volet ne bouge pas : la sortie rend la liste des conversations, et c'est le
     * geste **suivant** qui referme le volet. Deux gestes, deux effets — c'est l'ordre que la
     * référence suit, et l'inverse renvoyait la personne à son fil d'un seul retour.
     */
    fun exitSearch() {
        search = false
    }

    /** La largeur de la fenêtre a changé : le volet change de mode, **pas** d'ouverture. */
    fun updateMode(next: SidebarMode) {
        mode = next
    }

    /** Un appui : ouvre si c'est fermé, referme si c'est ouvert. Un seul geste, jamais deux. */
    fun toggle() {
        write(!isOpen)
    }

    fun open() {
        write(true)
    }

    fun close() {
        write(false)
    }

    private fun write(open: Boolean) {
        if (mode == SidebarMode.DOCKED) panelOpen = open else drawerOpen = open
    }

    internal companion object {

        /** Le séparateur de la pile repliée en une chaîne : aucun nom de route n'en porte. */
        private const val SEPARATOR = "\u0000"

        /**
         * L'état du volet **mémorisé et restauré** : un repliage, une rotation ou une recréation
         * d'activité retrouvent le volet tel qu'il était — y compris l'écran qu'il portait. Sans
         * ça, replier un pliable ramenait la liste et perdait la navigation en cours.
         */
        val Saver: Saver<OnyxSidebarState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.mode.name,
                    state.drawerOpen,
                    state.panelOpen,
                    state.destinations.joinToString(SEPARATOR),
                )
            },
            restore = { saved ->
                OnyxSidebarState(
                    mode = SidebarMode.valueOf(saved[0] as String),
                    drawerOpen = saved[1] as Boolean,
                    panelOpen = saved[2] as Boolean,
                    stack = (saved[3] as String).split(SEPARATOR).filter { it.isNotEmpty() },
                )
            },
        )
    }

    /** Le second constructeur : celui de la restauration. Le premier reste celui de l'appelant. */
    internal constructor(
        mode: SidebarMode,
        drawerOpen: Boolean,
        panelOpen: Boolean,
        stack: List<String>,
    ) : this() {
        this.mode = mode
        this.drawerOpen = drawerOpen
        this.panelOpen = panelOpen
        this.destinations.addAll(stack)
    }
}

/**
 * L'état du volet, gardé pour la durée de l'écran — **et au-delà** : il est restauré (`Saver`), si
 * bien qu'un repliage/dépliage ou une recréation d'activité retrouvent la navigation interne.
 */
@Composable
fun rememberOnyxSidebarState(): OnyxSidebarState =
    rememberSaveable(saver = OnyxSidebarState.Saver) { OnyxSidebarState() }

/**
 * Ce qu'un **retour** fait au volet, en **un seul niveau** — le point qui restait douteux.
 *
 * Le bouton système, le geste du bord **et** l'appui sur le voile passent tous les trois par ici :
 * tant que le volet porte un écran (une tuile ouverte dedans), le retour **dépile** cet écran et le
 * volet reste ouvert sur sa liste ; c'est seulement quand il montre déjà sa liste qu'un retour le
 * **referme**. Un glissement qui fermait le volet entier d'un coup — en sautant l'écran porté —
 * n'était donc pas « le même geste » que le bouton système : c'est ce que cette règle unique tient.
 */
fun sidebarBackStep(state: OnyxSidebarState) {
    if (!state.popDestination()) state.close()
}
