package com.onyx.app.ui.design

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * L'**empilement** du bas d'une conversation : le compositeur, la barre d'onglets et le clavier.
 *
 * Deux défauts mesurés, deux décisions tenues ici :
 *
 * 1. **Les deux marges ne s'additionnent jamais.** Clavier ouvert, le compositeur recouvrait à
 *    moitié la barre à cinq onglets (« Chat » et « Flux » à demi cachés dessous). Deux marges se
 *    cumulaient — la zone de navigation et celle du clavier, chacune posée de son côté
 *    ([OnyxComposer] ajoutait `imePadding()` **et** `navigationBarsPadding()`, alors que la racine
 *    de l'app a déjà réduit la fenêtre au-dessus du clavier et consommé l'encart d'IME). La pile
 *    prend donc l'encart du système **une fois**, au plus grand des deux ([stackBottomMarginDp] :
 *    `max(clavier, navigation) + l'espace de la maison`), et l'espace visuel vient de l'utilitaire
 *    existant ([SafeBarSpacing]).
 *
 * 2. **La barre d'onglets est toujours là.** Le lot précédent l'effaçait pendant la frappe
 *    (« deux rangées de contrôles plus le clavier ne tiennent pas sur une fenêtre courte »). Le
 *    propriétaire a dit non : *« elle ne doit pas disparaître si je ne l'ai pas décidée »*. Le
 *    clavier n'a donc **aucun** droit sur elle. La poignée à chevron qui la masquait — le seul
 *    autre chemin — a ensuite été retirée de l'écran sur l'ordre du propriétaire (*« supprime le
 *    bouton pour la possibilité de cacher »*), avec la préférence par conversation qui la gardait
 *    (`ChatPanelStore.tabsHidden`). **Il n'existe donc plus aucun état de masquage** : ni
 *    [tabBarVisible], ni [tabBarAlpha], ni [toggled], ni [handleLabelKey] — ces règles ont disparu
 *    avec le bouton qu'elles commandaient, plutôt que de rester comme un chemin qu'on pourrait
 *    rebrancher par distraction.
 *
 * L'ordre choisi est **compositeur puis barre** : le champ reste collé au haut de la pile — donc
 * juste sous le fil qu'on lit — et la barre vient occuper le bas, au plus près du clavier, comme
 * elle l'a toujours fait. Le champ ne bouge pas d'un onglet à l'autre, et rien n'est inséré entre
 * la conversation et la zone d'écriture. C'est aussi ce qui garde la barre **au même endroit**
 * d'une conversation à l'autre : elle est portée par la pile d'écriture, pas par le fil.
 *
 * **Elle ne s'efface plus, jamais.** Le masquage **au défilement** (fondu + glissement court,
 * déclenché par une distance parcourue) a été **retiré** sur l'ordre du propriétaire : « ça ne doit
 * JAMAIS disparaître quand je défile ». Les fonctions qui le tenaient — [stackVeiled], [accumulate],
 * [ScrollSlopDp], [typingHolds], [stackAlpha], [slideSpec], [SlideFraction] — ont disparu avec lui,
 * plutôt que de rester comme un chemin qu'on pourrait rebrancher par distraction. Il ne reste que
 * la décision de mouvement du bouton flottant ([fadeSpec]), qui ne concerne pas la plaque.
 *
 * Les fonctions restantes sont pures : un test les interroge sans appareil.
 */
object ComposerStacking {

    /**
     * La marge basse de la pile, en dp : l'encart du système **au plus grand des deux** — jamais la
     * somme — plus l'espace visuel de la maison ([SafeBarSpacing.GapDp]).
     */
    fun stackBottomMarginDp(imeDp: Int, navigationBarsDp: Int): Int =
        SafeBarSpacing.bottomMarginDp(systemBottomInsetDp = maxOf(imeDp.coerceAtLeast(0), navigationBarsDp.coerceAtLeast(0)))

    /**
     * Vrai si les deux marges ne se cumulent pas : la pile ne prend jamais plus que la plus grande
     * des deux, plus l'espace de la maison.
     */
    fun marginsDoNotCumulate(imeDp: Int, navigationBarsDp: Int): Boolean =
        stackBottomMarginDp(imeDp, navigationBarsDp) <=
            maxOf(imeDp.coerceAtLeast(0), navigationBarsDp.coerceAtLeast(0)) + SafeBarSpacing.GapDp

    /**
     * Le **vide de la maison** entre la pile d'écriture et le contenu qu'elle recouvre, en dp.
     *
     * C'est la marche `Half` de l'échelle d'espacement ([OnyxSpace.Half] = 8) : la même valeur que
     * l'espace visuel entre une barre et le bord de la zone système, exprimée par le même nom.
     */
    val StackGapDp: Int = OnyxSpace.Half.value.toInt()

    /**
     * **Le dégagement du bouton « aller au dernier message »**, en dp : le vide que le bouton laisse
     * entre lui et le haut de la pile du bas.
     *
     * C'est la marche `Snug` ([OnyxSpace.BlockGap] = 12), l'écart entre deux blocs posés l'un
     * au-dessus de l'autre. La marge n'était **pas** un dégagement : elle valait la somme de deux
     * estimations (`composerHeight + tabBarHeight + 8`) plus un second `navigationBarsPadding()`, si
     * bien que le bouton descendait dans la carte du compositeur — mesuré à 7,6 dp du bord, et
     * jusqu'à 48 dp **dedans** quand l'encart du système n'est pas consommé par la racine (barre à
     * trois boutons, barre de tâches d'un pliable). Le bouton se pose désormais au-dessus de la
     * **réserve du fil** ([threadBottomReserveDp]), qui est la pile mesurée, plus ce dégagement.
     */
    val PillClearanceDp: Int = OnyxSpace.BlockGap.value.toInt()

    /**
     * **La hauteur du bas que le fil doit réserver**, en dp — la règle qui empêche une partie de la
     * conversation de passer sous la carte du compositeur.
     *
     * Le défaut mesuré : le fil réservait `compositeur + barre d'onglets + 8`, alors que la pile
     * occupe `compositeur + barre d'onglets + encart du système + 8`. Les deux valeurs ne
     * différaient que de l'**encart du système** — 24 dp en navigation par gestes, 48 dp à trois
     * boutons, la zone du clavier quand il est ouvert — et c'est exactement de cette différence que
     * les dernières lignes (« Réflexion · 12,4 s », « … caractères mis en cache ») se retrouvaient
     * **sous** la carte. Une soustraction de vingt-quatre points ne se voit pas dans un calcul ; elle
     * se voit sur un fil laissé au bas.
     *
     * La réserve est donc **égale** à la pile :
     *  - la hauteur du compositeur, **mesurée** (elle change : pièces jointes, puce du modèle,
     *    bandeau « en attente ») ;
     *  - la hauteur de la rangée du bas, **mesurée** elle aussi — la barre entière quand elle est
     *    visible, la **poignée seule** quand la barre est masquée (elle reste, c'est la porte de
     *    retour : la réserve doit donc rester, plus petite) ;
     *  - la marge du système **une seule fois**, au plus grand du clavier et de la zone de
     *    navigation — jamais la somme (c'est la règle de [stackBottomMarginDp]) ;
     *  - le vide de la maison ([StackGapDp]).
     *
     * [maxOf] sur le clavier et la navigation, plutôt que la seule navigation : quand l'encart d'IME
     * n'est pas consommé par la racine, la réserve monte avec le clavier, et le dernier message reste
     * **au-dessus** de lui. Quand il l'est, la racine a déjà réduit la fenêtre et la valeur vaut la
     * navigation : dans les deux cas, la réserve couvre la pile.
     */
    fun threadBottomReserveDp(
        composerDp: Int,
        bottomRowDp: Int,
        imeDp: Int,
        navigationBarsDp: Int,
        gapDp: Int = StackGapDp,
    ): Int = composerDp.coerceAtLeast(0) +
        bottomRowDp.coerceAtLeast(0) +
        maxOf(imeDp.coerceAtLeast(0), navigationBarsDp.coerceAtLeast(0)) +
        gapDp.coerceAtLeast(0)

    /**
     * Le **verdict** : la réserve couvre-t-elle toute la pile ? C'est la propriété que le test
     * interroge — et il compare **les deux valeurs**, il ne se contente pas de vérifier qu'il y a un
     * rembourrage.
     */
    fun reserveCoversStack(
        reserveDp: Int,
        composerDp: Int,
        bottomRowDp: Int,
        imeDp: Int,
        navigationBarsDp: Int,
    ): Boolean = reserveDp >= composerDp.coerceAtLeast(0) +
        bottomRowDp.coerceAtLeast(0) +
        maxOf(imeDp.coerceAtLeast(0), navigationBarsDp.coerceAtLeast(0))

    /**
     * La part de la pile qui **recouvre** le contenu, en dp : ce que la réserve doit au minimum
     * égaler. Zéro serait le défaut ; c'est ce chiffre que le rapport publie pour chaque cas mesuré.
     */
    fun stackHeightDp(composerDp: Int, bottomRowDp: Int, imeDp: Int, navigationBarsDp: Int): Int =
        composerDp.coerceAtLeast(0) +
            bottomRowDp.coerceAtLeast(0) +
            maxOf(imeDp.coerceAtLeast(0), navigationBarsDp.coerceAtLeast(0))

    // -----------------------------------------------------------------------------------------
    // Les éléments FLOTTANTS au-dessus de la plaque — la règle, pure
    // -----------------------------------------------------------------------------------------

    /**
     * **La plaque du bas a-t-elle livré sa hauteur ?**
     *
     * C'est la question dont dépend **toute** position flottante, et c'est elle qui manquait. Les
     * deux hauteurs de la pile (`composerHeightPx`, `tabBarHeightPx`) sont des états **mesurés**
     * (`onSizeChanged`) : ils valent **zéro** jusqu'au premier passage de mise en page. Une position
     * calculée depuis zéro n'est pas une position approchée — c'est une position **fausse**, et elle
     * était peinte telle quelle, un instant, à **chaque** reprise de l'app.
     *
     * Zéro veut dire « pas encore mesuré », jamais « petit ». Les deux sont donc exigées : une pile
     * dont la barre d'onglets serait réduite à néant (aucune barre, aucune poignée) n'existerait pas.
     */
    fun plaqueMeasured(composerDp: Int, bottomRowDp: Int): Boolean =
        composerDp > 0 && bottomRowDp > 0

    /**
     * **L'ancre des éléments flottants, ou `null`.**
     *
     * Elle rend la marge du bas ([measuredDp] : la réserve du fil, plus le dégagement nommé) quand —
     * et **seulement** quand — la plaque a été mesurée ; `null` sinon, et `null` veut dire **on ne
     * dessine pas**. C'est la règle que le défaut de restitution d'état a fait naître : à chaque
     * réouverture (démarrage à froid, restauration après mort de processus, recréation de l'activité
     * par rotation, changement de thème, de langue ou police 1,3×, retour depuis une feuille, clavier
     * ouvert puis fermé, insets changés), la position venait d'une ancre à zéro, si bien que la
     * pastille « aller au dernier message » et la poignée de la barre se dessinaient **avant la
     * mesure**, à un endroit que rien ne soutenait, puis sautaient d'un coup.
     *
     * Une position qui vaut zéro au premier passage ne doit **jamais** être dessinée : on ne dessine
     * pas tant qu'on n'a pas mesuré, puis on apparaît **au bon endroit**.
     */
    fun floatAnchorDp(composerDp: Int, bottomRowDp: Int, measuredDp: Int): Int? =
        if (plaqueMeasured(composerDp = composerDp, bottomRowDp = bottomRowDp)) {
            measuredDp.coerceAtLeast(0)
        } else {
            null
        }

    /**
     * **Le haut d'un élément flottant**, en dp depuis le haut de la fenêtre — `null` s'il ne doit pas
     * être dessiné du tout.
     *
     * C'est la seconde moitié de la règle, et c'est celle qui se vérifie **sans appareil** : la
     * position d'un élément flottant se lit depuis le haut de la fenêtre, donc un test peut la
     * comparer au bas de l'en-tête et au bord de la barre d'état. L'ancrage est compté depuis le bas
     * de la fenêtre ([floatAnchorDp]) ; le haut vaut la fenêtre, moins l'ancre, moins la hauteur de
     * l'élément.
     */
    fun floatTopDp(
        composerDp: Int,
        bottomRowDp: Int,
        measuredDp: Int,
        windowDp: Int,
        floatHeightDp: Int,
    ): Int? = floatAnchorDp(
        composerDp = composerDp,
        bottomRowDp = bottomRowDp,
        measuredDp = measuredDp,
    )?.let { anchor -> windowDp - anchor - floatHeightDp.coerceAtLeast(0) }

    /**
     * **Le verdict d'écrasement** : un élément flottant recouvre-t-il l'en-tête (ou la barre d'état,
     * qui est au-dessus de lui) ?
     *
     * Un élément **non dessiné** (`null`) ne recouvre rien : c'est ce que doit rendre la garde. Un
     * élément dessiné dont le haut tombe dans la bande de l'en-tête recouvre : c'est le défaut, et
     * c'est ce que le test refuse.
     */
    fun overlapsHeader(floatTopDp: Int?, headerBottomDp: Int): Boolean {
        val top = floatTopDp ?: return false
        return top < headerBottomDp
    }

    /**
     * La durée d'une fondue, ou un **figé** : sous « Réduire les animations », le changement est
     * instantané (aucun mouvement), comme le reste du verre de l'app.
     *
     * Générique en `T` parce que le même réglage sert à une opacité (`Float`), à un décalage
     * (`Int`) et à la place prise par la barre (`IntSize`) : une seule décision de mouvement,
     * jamais deux.
     */
    fun <T> fadeSpec(reduceMotion: Boolean, durationMillis: Int = 180): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else tween(durationMillis = durationMillis)
}

/**
 * **L'arbre a-t-il déjà payé l'encart du clavier, en haut de la hiérarchie ?**
 *
 * `MainActivity` applique `imePadding()` **puis** `consumeWindowInsets(WindowInsets.ime)` à toute
 * l'application : la zone de contenu est **déjà** raccourcie de la hauteur du clavier, et l'encart
 * d'IME est consommé pour les rembourrages internes. Cette consommation ne se lit **pas** dans
 * `WindowInsets.ime.getBottom(...)`, qui rend toujours la hauteur **brute** : la réserve du fil
 * comptait donc le clavier une **seconde fois**.
 *
 * C'est le défaut mesuré du lot « suivi des états du fil », relevé à l'appareil (densité 2,625,
 * 1080×2400) : clavier ouvert, la réserve valait `484 dp` (dont `336 dp` de clavier) alors que la
 * fenêtre était déjà réduite de ces 336 dp. La liste posait donc `484 + 56 + 12 = 552 dp` de
 * rembourrage sous sa dernière ligne, pour un fil de `578 dp` : au bas du défilement, on ne voyait
 * plus que le rembourrage — **le fil paraissait vide**, exactement la capture du propriétaire.
 *
 * Le drapeau est **fourni par le haut** ([MainActivity]) parce que c'est là que la décision est
 * prise ; sa valeur par défaut (`false`) laisse un aperçu ou un test sans racine se comporter comme
 * avant. La fonction pure ([ComposerStacking.threadBottomReserveDp]) ne change pas : c'est
 * l'appelant qui cesse de lui passer un clavier déjà payé.
 */
val LocalOnyxImeTakenByRoot: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }

/**
 * La **réserve du bas** du fil, lue sur l'appareil : la hauteur mesurée du compositeur, celle de la
 * rangée du bas (barre entière ou poignée seule), l'encart du système (clavier ou navigation, au
 * plus grand), et le vide de la maison.
 *
 * Elle est calculée **ici** et nulle part ailleurs : un écran qui recouvre son contenu par une pile
 * n'a pas le droit d'estimer la hauteur de cette pile — c'est de cette estimation qu'est né le
 * défaut « une partie de la conversation passe sous la barre de saisie ».
 */
@Composable
fun threadBottomReserve(
    composerPx: Int,
    bottomRowPx: Int,
): Dp {
    val density = LocalDensity.current
    // Le clavier ne compte **que s'il n'a pas déjà été payé par la racine** (voir
    // [LocalOnyxImeTakenByRoot]) : sans cette garde, il était compté deux fois.
    val imeTakenByRoot = LocalOnyxImeTakenByRoot.current
    val imeRawPx = WindowInsets.ime.getBottom(density)
    val imePx = if (imeTakenByRoot) 0 else imeRawPx
    val navigationPx = WindowInsets.navigationBars.getBottom(density)
    val gapPx = with(density) { ComposerStacking.StackGapDp.dp.roundToPx() }
    val reserveDp = with(density) {
        ComposerStacking.threadBottomReserveDp(
            composerDp = composerPx.toDp().value.roundToInt(),
            bottomRowDp = bottomRowPx.toDp().value.roundToInt(),
            imeDp = imePx.toDp().value.roundToInt(),
            navigationBarsDp = navigationPx.toDp().value.roundToInt(),
            gapDp = gapPx.toDp().value.roundToInt(),
        )
    }
    // **La trace de mesure**, une ligne par valeur distincte : c'est ce qui rend le tableau du
    // rapport vérifiable sur l'appareil et pas seulement en test — les quatre hauteurs mesurées
    // (compositeur, rangée, clavier, navigation) et la réserve qui en découle. Même principe que le
    // journal du verre (`GlassLogTag`) : une décision de géométrie se lit dans `logcat` sous son
    // étiquette, sinon elle n'est vérifiable que par ses effets.
    remember(reserveDp) {
        println(
            "$ThreadReserveLogTag réserve du bas ${reserveDp}dp = compositeur " +
                "${with(density) { composerPx.toDp().value.roundToInt() }} + rangée " +
                "${with(density) { bottomRowPx.toDp().value.roundToInt() }} + système " +
                "max(clavier ${with(density) { imePx.toDp().value.roundToInt() }} " +
                "(brut ${with(density) { imeRawPx.toDp().value.roundToInt() }}, " +
                "pris par la racine=$imeTakenByRoot), navigation " +
                "${with(density) { navigationPx.toDp().value.roundToInt() }}) + écart " +
                ComposerStacking.StackGapDp,
        )
    }
    return reserveDp.dp
}

/** L'étiquette de la trace de la réserve du bas, telle qu'elle se lit dans `logcat`. */
const val ThreadReserveLogTag: String = "OnyxThreadReserve"

/**
 * **L'ancre des éléments flottants, lue sur l'appareil — ou `null`.**
 *
 * Même rôle que [threadBottomReserve] pour la pile : c'est **ici** que le placement flottant se
 * décide, à partir des hauteurs **mesurées** ([composerPx], [bottomRowPx]) — jamais d'une valeur de
 * départ. Tant que la plaque n'a pas livré ses hauteurs, elle rend `null`, et `null` veut dire
 * **on ne dessine pas** : un élément flottant ne se pose pas à une position que rien ne soutient.
 *
 * [measured] est la marge déjà calculée quand la mesure existe (la réserve du fil, plus le
 * dégagement nommé) : une seule source de vérité, la même que le rembourrage du fil.
 *
 * La trace ([FloatAnchorLogTag]) publie la décision à chaque valeur distincte : « ancre non
 * dessinée » pendant la passe où la plaque n'est pas encore mesurée, puis l'ancre en dp — c'est ce
 * qui rend les chemins de reprise (démarrage à froid, mort de processus, rotation, feuille, clavier,
 * insets) vérifiables dans `logcat`, et pas seulement dans un test.
 */
@Composable
fun threadFloatAnchor(
    composerPx: Int,
    bottomRowPx: Int,
    measured: Dp,
): Dp? {
    val density = LocalDensity.current
    val composerDp = with(density) { composerPx.toDp().value.roundToInt() }
    val bottomRowDp = with(density) { bottomRowPx.toDp().value.roundToInt() }
    val anchorDp = ComposerStacking.floatAnchorDp(
        composerDp = composerDp,
        bottomRowDp = bottomRowDp,
        measuredDp = measured.value.roundToInt(),
    )
    remember(anchorDp) {
        println(
            "$FloatAnchorLogTag ancre des éléments flottants " +
                (anchorDp?.let { "$it dp (plaque mesurée : compositeur $composerDp, rangée $bottomRowDp)" }
                    ?: "NON DESSINÉE (plaque pas encore mesurée : compositeur $composerDp, rangée $bottomRowDp)"),
        )
    }
    return anchorDp?.dp
}

/** L'étiquette de la trace de l'ancre flottante, telle qu'elle se lit dans `logcat`. */
const val FloatAnchorLogTag: String = "OnyxFloatAnchor"

/**
 * La marge basse d'un compositeur : l'encart du clavier, puis la zone du système **sans** le
 * clavier. L'exclusion est ce qui empêche les deux de s'additionner : quand l'IME est ouvert, la
 * part basse de la zone de navigation vaut zéro, et le champ reste **au-dessus** du clavier.
 */
@Composable
fun Modifier.composerBottomInsets(): Modifier = this
    .imePadding()
    .windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime))
