package com.onyx.app.ui.design

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * La barre du bas d'une conversation : **Chat / Flux / Fichiers**, puis **le visage du bot**.
 *
 * Elle est portée par la pile d'écriture — le champ ne bouge pas quand on change d'onglet, c'est
 * lui qui porte les onglets en dessous — et c'est une **pilule flottante en verre liquide** : le
 * même verre que la barre du haut et le champ d'écriture ([liquidGlass] / [GlassSurface.TAB_BAR]),
 * avec le liseré qui suit la forme, l'ombre douce et le fond teinté de la page.
 *
 * **Ce que le lot « verre liquide » change, et pourquoi.** Sur la capture du propriétaire, la barre
 * était un **caisson plein largeur collé aux bords**, dans lequel le fil ne passait pas : ce n'est
 * pas un verre, c'est un bandeau. Trois décisions la remettent en pilule :
 *
 * 1. **Détachée des bords** — ses marges latérales viennent de l'échelle ([TabBarGeometry]), et non
 *    d'une valeur choisie ici. Elle est donc posée, pas encadrée : plus de caisson.
 * 2. **Entièrement arrondie** — sa forme est la **capsule** de la table (`RoundedCornerShape(50)`),
 *    celle de l'en-tête et du contrôle segmenté.
 * 3. **Translucide** — sa surface est déclarée à **encre dérivée** ([GlassInk.DERIVED]) : les
 *    libellés sont choisis sous [readableInk] contre le voile composé, si bien que le voile peut
 *    rester mince (mesuré : 48 % de blanc en clair, contre 67 % avec une encre fixe) et que **le fil
 *    qui défile derrière se voit à travers**.
 *
 * L'onglet actif prend la **couleur du bot** de la conversation, et son encre est **mesurée** contre
 * le fond réel de la vitre : une couleur de bot trop claire est assombrie jusqu'au seuil de
 * lisibilité — la couleur ne fait jamais perdre la lecture ([readableInk]).
 */

// ---------------------------------------------------------------------------------------------
// Les onglets
// ---------------------------------------------------------------------------------------------

/**
 * **Les repères de la barre du bas d'une conversation**, dans l'ordre où ils se suivent au doigt :
 * trois **onglets de contenu** (Chat, Flux, Fichiers) puis **le repère-visage** du bot.
 *
 * [key] est ce qui est **gardé** (par conversation) et [label] la clé du dictionnaire central : les
 * libellés ne sont jamais écrits à l'écran, ils passent par [OnyxLocalization].
 *
 * **« Idées » et « Objectifs » ont été supprimés** (demande explicite du propriétaire : « Idées et
 * Objectifs ne servent plus, donc tu peux supprimer »). Le repère-visage qui occupait la place
 * d'« Idées » **reste** — c'est le visage du bot — mais il n'ouvre plus une page du pageur : il
 * ouvre l'**identité du bot** (voir [ChatTab.BOT]).
 */
enum class ChatTab(
    val key: String,
    val label: String,
    @DrawableRes val symbol: Int,
) {
    // LOT anatomie-des-contenus : **le sens d'abord, la famille ensuite**. « Chat » portait une
    // icône de réception (une flèche qui tombe dans un bac) — elle ne disait rien de l'onglet ;
    // « Idées » portait une étoile décorative. Chaque onglet prend l'icône de **son** mot, et toutes
    // viennent du **même jeu** (Lucide, celui de la source) : un trait, des bouts ronds, une grille
    // de 24 — jamais un mélange de pleins et de traits dans la même barre.
    CHAT("Chat", "Chat", Lucide.MessagesSquare),
    FEED("Feed", "Feed", Lucide.Rss),
    FILES("Files", "Files", Lucide.Folder),
    /**
     * **Le repère-visage : le quatrième repère de la barre.** Il n'ouvre **aucune** page du pageur :
     * la barre le dessine comme le **visage du bot** (voir [ChatTabBar.faceIndex]) et l'appui ouvre
     * l'**identité du bot** de la conversation. Son symbole n'est qu'un repli — quand la barre a un
     * visage à poser, c'est lui qu'elle peint, jamais cette icône.
     */
    BOT("Bot", "Bot", Lucide.Sparkles),
    ;

    companion object {
        /** L'onglet d'une clé gardée, l'entrée par défaut si elle est inconnue ou vide. */
        fun of(key: String?): ChatTab = entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: CHAT

        /**
         * **Les repères réellement dessinés dans la plaque du bas** : les trois onglets de contenu,
         * puis le **repère-visage** ([ChatTab.BOT]). Quatre repères, comme la planche
         * (`hlib.py:493` : Chat, Flux, Fichiers, puis le visage à l'accent).
         *
         * Le cinquième repère (« Objectifs ») ne tenait pas : sur la largeur d'un téléphone, à cinq
         * colonnes égales et police 1,3×, sa part tombait à **4,2 dp** — ni icône ni libellé. Il a
         * été **supprimé** avec l'onglet « Idées » (demande du propriétaire) : ce n'est plus une
         * page cachée du pageur, c'est un écran qui n'existe plus.
         */
        val drawn: List<ChatTab> get() = entries

        /**
         * **Les pages du pageur** : les onglets **de contenu**. Le repère-visage ([ChatTab.BOT])
         * n'en est **pas** une — il ouvre l'identité du bot, hors du pageur.
         */
        val paged: List<ChatTab> get() = entries.filter { it != BOT }

        /** Le **visage** est-il porté par ce repère ? Voir [ChatTabBar.faceIndex]. */
        const val FACE_LABEL: String = "Onyx"
    }
}

/**
 * L'index d'une clé gardée, **dans le pageur** : borné aux onglets de contenu, donc jamais hors
 * page. Une clé d'avant la suppression (« Ideas », « Goals ») ou d'un autre lot retombe sur Chat —
 * une valeur gardée ne casse jamais l'écran.
 */
fun chatTabIndexOf(key: String?): Int {
    val index = ChatTab.paged.indexOf(ChatTab.of(key))
    return if (index < 0) 0 else index
}

/**
 * Le nombre de **pages** du pageur : une seule source, pour que barre, pageur et tests ne divergent
 * pas. Le repère-visage ([ChatTab.BOT]) n'est pas une page et ne compte donc pas ici.
 */
val chatTabCount: Int get() = ChatTab.paged.size

// ---------------------------------------------------------------------------------------------
// La géométrie de la pilule — deux marges, prises à l'échelle
// ---------------------------------------------------------------------------------------------

/**
 * **La géométrie de la pilule flottante.**
 *
 * Le défaut relevé sur la capture du propriétaire : la barre était **pleine largeur**, collée aux
 * bords, dans un caisson opaque — et son dernier libellé mordait le bord droit. La référence montre
 * l'inverse : une **pilule détachée des quatre côtés**, entièrement arrondie, translucide, le fil
 * qui défile derrière.
 *
 * Les deux marges viennent de **l'échelle d'espacement de la maison** ([OnyxSpace]) et de nulle part
 * ailleurs — une valeur choisie ici à l'œil se remettrait à diverger d'un écran à l'autre :
 *
 * - [SideMargin] est la **marge d'écran** (`Base`, 16 dp) : c'est exactement celle de la carte du
 *   compositeur posée juste au-dessus ([ComposerCardSideMargin]), donc la pilule et la carte
 *   partagent **une seule** colonne de départ — deux retraits concurrents se lisaient « pas propre » ;
 * - [BottomMargin] est la marche `Half` (8 dp) : la même que l'écart entre une barre et la zone
 *   système ([SafeBarSpacing.GapDp]), donc la pilule ne paraît jamais collée ni au bas de la fenêtre
 *   ni à la carte qu'elle suit.
 */
object TabBarGeometry {

    /** La marge latérale de la pilule, à gauche et à droite : la marge d'écran de la maison. */
    val SideMargin: Dp = OnyxSpace.ScreenMargin

    /** Le dégagement entre la pilule et la carte du compositeur (et la zone système, plus bas). */
    val BottomMargin: Dp = OnyxSpace.Half
}

/**
 * **Le libellé d'un onglet tient toujours entier.**
 *
 * Le second défaut de la capture : « Objectifs » coupé au bord droit. La cause est géométrique — cinq
 * onglets de largeur égale dans la largeur d'un téléphone : à **1,3×** sur 360 dp, « Objectifs »
 * demande 56 dp quand sa colonne n'en offre que 52.
 *
 * La correction suit la règle de la maison (voir [ChipRowFit]) : on **mesure** le libellé sous la
 * police réellement rendue, on **réduit** d'un demi-point tant que la colonne ne le porte pas, et on
 * s'arrête à un **plancher** de lisibilité. La règle est une **fonction pure** — elle reçoit les
 * largeurs mesurées pour une taille et rend la taille retenue — donc elle se vérifie sans appareil,
 * et le chiffre du rapport est celui du code.
 */
object TabLabelFit {

    /** La taille nominale d'un libellé d'onglet : celle de `caption2` (11 sp). */
    const val BaseSp: Float = 11f

    /**
     * **Le plancher est celui de l'app : 11 sp.** La version d'avant descendait à 9,5 sp pour faire
     * tenir « Objectifs » dans sa colonne à police 1,3× — un corps de 9,5 n'existe nulle part
     * ailleurs, et à police 1,0 c'est un libellé illisible. Une taille ne se négocie pas pour faire
     * tenir un mot : quand le mot ne tient pas, c'est le **mot** qui change.
     */
    const val FloorSp: Float = 11f

    /**
     * La largeur d'**une** colonne d'onglet, en dp.
     *
     * [innerDp] est la largeur intérieure de la pilule ; [handleDp] la place qu'une commande posée
     * **dans** la barre prendrait avant les onglets — elle vaut zéro partout depuis le retrait de la
     * poignée, et le paramètre reste pour que la règle reste éprouvable si une barre en accueille un
     * jour une ; les écarts sont ceux de la rangée (`ControlGap` entre chaque élément). Les colonnes
     * sont d'égale largeur : c'est `weight(1f)` qui les dessine, et c'est donc cette largeur-là que
     * le libellé doit tenir.
     */
    fun columnsDp(
        innerDp: Float,
        count: Int = chatTabCount,
        handleDp: Float = 0f,
        gapDp: Float = OnyxSpace.ControlGap.value,
    ): Float {
        if (count <= 0) return 0f
        val items = count + if (handleDp > 0f) 1 else 0
        val gaps = (items - 1).coerceAtLeast(0) * gapDp
        return ((innerDp - handleDp - gaps) / count).coerceAtLeast(0f)
    }

    /**
     * La taille retenue : la plus grande taille (par demi-point depuis [baseSp]) à laquelle **tous**
     * les libellés tiennent dans leur colonne. Jamais sous [floorSp].
     */
    fun fontSize(
        columnsDp: Float,
        labels: List<String>,
        baseSp: Float = BaseSp,
        floorSp: Float = FloorSp,
        width: (String, Float) -> Float,
    ): Float {
        if (columnsDp <= 0f || labels.isEmpty()) return baseSp
        // Les marches sont les **corps du socle**, jamais des demi-points.
        val rungs = (listOf(baseSp) + ChipRowFit.Rungs).filter { it <= baseSp && it >= floorSp }.distinct().sortedDescending()
        rungs.forEach { size -> if (fits(columnsDp, labels, size, width)) return size }
        return floorSp
    }

    /** Le verdict : chaque libellé tient dans sa colonne, à cette taille. */
    fun fits(
        columnsDp: Float,
        labels: List<String>,
        sizeSp: Float,
        width: (String, Float) -> Float,
    ): Boolean = labels.all { width(it, sizeSp) <= columnsDp }
}

// ---------------------------------------------------------------------------------------------
// Le geste — la règle, pure, donc vérifiable sans appareil
// ---------------------------------------------------------------------------------------------

/**
 * La règle du glissement horizontal entre onglets.
 *
 * Deux décisions y sont isolées, parce que ce sont les deux qui se discutent :
 *
 * 1. **Un glissement court revient en place.** L'onglet d'arrivée n'est décidé que si le doigt a
 *    parcouru plus de la moitié de la page, ou s'il a été relâché avec assez d'élan ; sinon on
 *    revient à l'onglet de départ. Un frôlement ne change donc jamais d'onglet.
 * 2. **Le bord de l'écran ne commence pas un changement d'onglet.** La bande réservée au geste de
 *    retour du système reste au système : le pageur ne démarre pas là, et le retour du bord n'est
 *    pas avalé par la barre.
 */
object TabSwipe {

    /** Au-delà de cette part de la page parcourue, l'onglet suivant est adopté. */
    const val CommitFraction: Float = 0.5f

    /** L'élan, en pixels par seconde, qui suffit à tourner la page sur un geste court. */
    const val FlingVelocity: Float = 1_200f

    /**
     * L'onglet sur lequel le pageur se pose après un geste.
     *
     * [dragPx] est le déplacement horizontal total (positif vers la droite), [pageWidthPx] la
     * largeur d'une page, [velocityPxPerSecond] l'élan réel du geste.
     */
    fun settledIndex(
        startIndex: Int,
        count: Int,
        dragPx: Float,
        pageWidthPx: Float,
        velocityPxPerSecond: Float = 0f,
    ): Int {
        if (count <= 1) return 0
        val from = startIndex.coerceIn(0, count - 1)
        if (pageWidthPx <= 0f) return from
        val committed = if (kotlin.math.abs(velocityPxPerSecond) >= FlingVelocity) {
            if (velocityPxPerSecond > 0f) -1 else 1
        } else {
            0
        }
        val stepped = if (committed != 0) {
            from + committed
        } else if (dragPx <= -CommitFraction * pageWidthPx) {
            from + 1
        } else if (dragPx >= CommitFraction * pageWidthPx) {
            from - 1
        } else {
            from
        }
        return stepped.coerceIn(0, count - 1)
    }

    /**
     * Vrai si un doigt **posé à [startX]** peut commencer un changement d'onglet.
     *
     * [edgeGuardPx] est la bande que le système réserve à son geste de retour (les encarts
     * `systemGestures`, nuls en navigation à trois boutons) : dedans, le geste appartient au retour,
     * pas à la barre.
     */
    fun allowsStart(startX: Float, widthPx: Float, edgeGuardPx: Float): Boolean {
        if (widthPx <= 0f) return true
        val guard = edgeGuardPx.coerceIn(0f, widthPx / 2f)
        // Une bande nulle (navigation à trois boutons) laisse tout l'écran : les deux bords
        // eux-mêmes restent au pageur.
        return startX >= guard && startX <= widthPx - guard
    }
}

/**
 * La bande du bord qui reste au geste de retour du système : deux lames invisibles, posées
 * **au-dessus** du pageur, qui absorbent le glissement horizontal du bord. Le pageur ne bouge donc
 * pas d'un doigt parti du bord — le retour du bord garde la priorité, et rien d'autre ne change :
 * une bande nulle (navigation à trois boutons) ne dessine aucune lame et ne capte rien.
 */
@Composable
fun TabSwipeEdgeGuards(
    leftPx: Float,
    rightPx: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        if (leftPx > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(with(androidx.compose.ui.platform.LocalDensity.current) { leftPx.toDp() })
                    .fillMaxHeight()
                    .pointerInput(leftPx) {
                        detectHorizontalDragGestures { change, _ -> change.consume() }
                    },
            )
        }
        if (rightPx > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(with(androidx.compose.ui.platform.LocalDensity.current) { rightPx.toDp() })
                    .fillMaxHeight()
                    .pointerInput(rightPx) {
                        detectHorizontalDragGestures { change, _ -> change.consume() }
                    },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Lisibilité — la couleur du bot ne fait jamais perdre la lecture
// ---------------------------------------------------------------------------------------------

/**
 * L'encre d'un contrôle, tenue au-dessus du seuil : la teinte préférée est conservée si elle passe
 * (le **rapport** est mesuré, pas supposé), sinon elle est **écartée du fond** par paliers — du côté
 * qui augmente le contraste, donc vers le blanc sur un fond sombre et vers le noir sur un fond clair
 * — et en dernier recours remplacée par le noir ou le blanc pur, celui des deux qui donne le
 * meilleur rapport.
 *
 * **Pourquoi le sens du rattrapage suit le fond, et pas l'ordre « assombrir d'abord ».** L'ancienne
 * version essayait toujours d'**assombrir** l'encre avant de l'éclaircir. Tant que le fond passé est
 * le fond **réellement composé**, cela ne se voit presque jamais — l'encre du thème passe déjà. Mais
 * une surface qui mesurait son encre contre un fond **hypothétique** (le pire des deux aplats,
 * blanc pur et noir pur, sous une vitre mince) obtenait l'inverse de ce qu'il fallait : en apparence
 * **sombre**, la plaque d'onglets choisissait le fond composé sur du **blanc** — le plus clair — et
 * « assombrissait » donc l'encre claire du thème jusqu'à un gris à **1,40:1** sur la plaque
 * réellement peinte (relevé : `#2F2F30`, quasi noir sur quasi noir : « on ne voit rien, les icônes
 * sont illisibles »), et l'onglet actif à **1,30:1**. Un rattrapage qui s'éloigne du fond ne peut
 * pas produire cela : il éclaircit sur un fond sombre, assombrit sur un fond clair, et l'encre garde
 * donc l'**apparence** du thème.
 */
fun readableInk(
    preferred: Color,
    background: Color,
    minRatio: Double = TEXT_CONTRAST_MIN,
): Color {
    if (contrastRatio(preferred, background) >= minRatio) return preferred
    // Le pôle qui se détache **le mieux** de ce fond : c'est lui que l'encre rejoint. Sur un fond
    // sombre c'est le blanc, sur un fond clair le noir, et sur un fond **médian** (l'accent d'une
    // pastille, un bleu de bouton) c'est celui des deux qui mesure le plus — jamais celui qu'une
    // comparaison au demi de luminance aurait désigné à l'œil.
    val away = if (
        contrastRatio(Color.White, background) >= contrastRatio(Color.Black, background)
    ) {
        Color.White
    } else {
        Color.Black
    }
    val towards = if (away == Color.White) Color.Black else Color.White
    for (step in listOf(0.30f, 0.55f, 0.78f)) {
        val moved = lerp(preferred, away, step)
        if (contrastRatio(moved, background) >= minRatio) return moved
    }
    // Le fond est médian : aucun des deux pôles ne passe depuis l'encre préférée — on garde alors
    // son atténuation la plus franche, plutôt que de renvoyer une encre au hasard.
    for (step in listOf(0.30f, 0.55f, 0.78f)) {
        val moved = lerp(preferred, towards, step)
        if (contrastRatio(moved, background) >= minRatio) return moved
    }
    return if (contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)) {
        Color.Black
    } else {
        Color.White
    }
}

/**
 * Le fond réellement composé d'un onglet, dans le pire cas : le voile de la barre, la teinte de
 * l'onglet quand il est actif, puis le contenu le plus extrême qui peut passer sous la vitre. C'est
 * cette fonction que le test interroge, donc le chiffre annoncé et le chiffre vérifié sont le même.
 */
fun tabBackground(veil: Color, tint: Color?, under: Color): Color =
    tint?.let { compositedBackdrop(it, compositedBackdrop(veil, under)) } ?: compositedBackdrop(veil, under)

// ---------------------------------------------------------------------------------------------
// **La plaque : son voile, son fond, ses encres — une seule source, donc un seul chiffre**
//
// Ces trois fonctions sont *les* règles de la plaque, et l'écran comme le verrou les appellent. Une
// encre calculée d'un côté et mesurée de l'autre diverge au premier changement de voile ; ici, le
// chiffre du rapport et le chiffre du test sont le **même appel**.
// ---------------------------------------------------------------------------------------------

/**
 * **Le voile de la vitre de la plaque** : la teinte du verre, à l'opacité **peinte**
 * ([glassPaintTintAlpha], le palier constant). C'est la matière qui porte la rangée d'onglets quand
 * la barre est posée sur la plaque (`plate = true`) — voir `ChatScreen`, qui ne peint, lui, que
 * cette vitre.
 */
fun plaqueVeil(theme: ResolvedTheme): Color = glassTintColour(theme.dark).copy(
    alpha = glassPaintTintAlpha(GlassSurface.TAB_BAR, theme.dark, glassInks(GlassSurface.TAB_BAR, theme)),
)

/**
 * **Le fond de la plaque tel qu'il est peint** : le voile du verre sur la **page** de l'apparence
 * courante, et sous **la pastille** de l'onglet actif quand il l'est.
 *
 * C'est *le* point de la correction : le fond qui décide de l'encre est celui que la capture montre.
 * L'ancienne version interrogeait un fond **hypothétique** — le pire de deux aplats, blanc pur et
 * noir pur, sous la vitre — et retenait celui qui est le plus **clair** ; en apparence sombre,
 * l'encre claire du thème y descendait à 1,40:1 sur la plaque réellement peinte.
 */
fun plaqueGround(theme: ResolvedTheme, selected: Boolean): Color = tabBackground(
    plaqueVeil(theme),
    if (selected) PlaqueGeometry.activePill(theme) else null,
    OnyxSurfaces.page(theme),
)

/**
 * **L'encre d'un repère de la plaque**, dérivée du fond qu'elle a réellement dessous.
 *
 * Les trois rôles, dans l'ordre de la barre : le **repère-visage** prend l'encre **pleine** de
 * l'apparence (le blanc de la maison en sombre — le propriétaire le demande explicitement), l'onglet
 * **actif** prend l'accent **en encre** (l'état « c'est celui-là », sa teinte de bot tenue au-dessus
 * du seuil), les autres l'encre secondaire du thème.
 */
fun plaqueInk(theme: ResolvedTheme, selected: Boolean, isFace: Boolean): Color {
    val preferred = when {
        isFace -> theme.textColor
        selected -> theme.accentInk
        else -> theme.secondaryText
    }
    return readableInk(preferred, plaqueGround(theme, selected))
}

// ---------------------------------------------------------------------------------------------
// La barre
// ---------------------------------------------------------------------------------------------

/**
 * La teinte de l'onglet actif — **retirée du dessin**.
 *
 * Elle servait à peindre `accent.copy(alpha = 0.16f)` sous l'onglet choisi. Sur le voile quasi noir
 * de la barre sombre, ce calcul donne un bleu à 16 % sur du `#121215` : la « tache bleu sombre
 * illisible » de la capture. La pastille de l'onglet actif prend maintenant le barreau
 * [OnyxSurfaces.capsule] — une **surface**, pas une couleur diluée — et l'accent ne porte plus que
 * l'**encre** de l'onglet choisi, mesurée sur le fond réellement composé.
 *
 * La constante reste déclarée parce que des écrans l'emploient pour teinter une pastille de non-lu
 * ailleurs ; elle n'est plus lue par cette barre.
 */
const val TabSelectionTint: Float = 0.16f

/**
 * La barre des cinq onglets, en verre liquide.
 *
 * [accent] est la couleur du bot de la conversation (son visage) : l'onglet actif s'y teinte,
 * l'encre étant mesurée sur le fond composé pour rester lisible dans le pire cas.
 *
 * [badges] porte, onglet par onglet, ce qu'il y a de **neuf** à voir (la pastille de non-lu du
 * Feed) : zéro n'affiche rien.
 *
 * **Elle n'accueille plus de poignée de masquage.** Le paramètre `handle` par lequel la barre
 * pouvait porter le rond à chevron a été retiré avec lui, sur l'ordre du propriétaire : la barre ne
 * se masque plus, donc elle n'a plus besoin d'un contrôle pour cela, ni de la place qu'il prenait au
 * bord d'attaque.
 */
@Composable
fun ChatTabBar(
    selectedIndex: Int,
    accent: Color,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<ChatTab> = ChatTab.entries,
    badges: List<Int> = emptyList(),
    /**
     * **La barre est posée sur la plaque unique** ([ChatScreen]) : elle ne peint alors **plus sa
     * propre capsule** (forme, fond, filet, ombre, verre) et ses marges extérieures ne sont plus
     * posées ici — c'est la plaque qui porte tout. Faux : la pilule d'origine, exactement comme
     * avant (les autres écrans qui portent la barre gardent leur comportement).
     */
    plate: Boolean = false,
    /**
     * **L'index du repère dessiné comme un visage**, et le libellé qui l'accompagne.
     *
     * La planche ne met pas une ampoule au quatrième repère : elle y met **le visage du bot, dans un
     * cercle à l'accent**, avec le libellé « Onyx » (`hlib.py:493`, `navi nav-face` / `capface`
     * 44 px). L'index reste celui de la **position** dans la barre : le repère garde donc son rôle
     * (il ouvre la quatrième page), seule sa **forme** change — c'est la même décision que pour le
     * cinquième repère retiré du dessin : on change ce qui se voit, pas ce qui se fait.
     */
    faceIndex: Int? = null,
    /** Le libellé du repère-visage, dans le dictionnaire central. `null` : celui de l'onglet. */
    faceLabel: String? = null,
    /** Le visage lui-même, dessiné par l'appelant (il seul connaît la conversation). */
    face: (@Composable () -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    // La forme vient de la **table** : la capsule de `TAB_BAR`. La recopier ici, c'est exactement
    // ainsi qu'un rayon se met à diverger d'une surface à l'autre.
    val shape = GlassSurface.TAB_BAR.shape
    // Le fond réellement composé de la barre est celui du **verre** : la teinte de la vitre, à
    // l'opacité que la mesure de lisibilité a retenue. C'est ce fond-là que les encres d'onglet
    // interrogent — l'ancien voile de `FrostSurface` ne décrit plus cette barre.
    //
    // **L'opacité interrogée est celle qui est PEINTE** ([plaqueVeil] → [glassPaintTintAlpha], le
    // palier constant), et non celle que la sonde de fluidité rapporterait : mesurer l'encre sur un
    // voile plus épais que le voile peint donnerait un chiffre juste pour une surface qui n'existe
    // pas. Le voile, le fond et les encres de la plaque vivent donc dans **les mêmes fonctions**
    // ([plaqueVeil], [plaqueGround], [plaqueInk]) que celles interrogées par le verrou.
    // **La pastille de l'onglet actif est celle de la planche.** `hcss.py:134`/`:146` donnent
    // `--pill` : un blanc à 74 % en clair, 17 % en sombre, cerné d'un filet blanc à 92 % / 24 %
    // (`inset 0 0 0 1px --pillline`). C'est le barreau que la planche peint sous « Chat »
    // (`.navi.on{background:var(--pill)}`, `fil-clair.html:110`) : l'onglet actif est **posé sur un
    // ovale plus clair**, il ne se dit pas seulement par l'encre. (Le lot « plaque finale » l'avait
    // retirée sur la foi d'un brief qui la croyait absente de la planche — la planche gagne.)
    val pill = PlaqueGeometry.activePill(theme)
    val pillLine = PlaqueGeometry.activePillLine(theme)
    val density = LocalDensity.current
    val reduceMotion = LocalReduceMotion.current
    val measurer = rememberTextMeasurer()
    val labels = tabs.map { OnyxLocalization[it.label] }

    // La mesure d'un libellé **sous la police réellement rendue** : c'est elle qui décide la taille
    // retenue, et c'est elle qui rend la coupure impossible (voir [TabLabelFit]).
    val measureLabel: (String, Float) -> Float = { text, sizeSp ->
        with(density) {
            measurer.measure(
                text = text,
                style = theme.caption2.copy(fontSize = sizeSp.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            ).size.width.toDp().value
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            // **La pilule est détachée des bords** : ces deux marges viennent de l'échelle
            // ([TabBarGeometry]) — c'est ce qui la fait flotter au lieu de l'encadrer.
            .then(
                if (plate) {
                    Modifier
                } else {
                    Modifier.padding(
                        start = TabBarGeometry.SideMargin,
                        end = TabBarGeometry.SideMargin,
                        top = OnyxSpace.BarPadding,
                        bottom = OnyxSpace.BarPadding,
                    )
                },
            ),
    ) {
        // La largeur d'une colonne d'onglet, mesurée sur la pilule : les cinq se la partagent
        // (`weight(1f)`), et la poignée — quand elle est posée dedans — prend sa cible avant eux.
        val columnsDp = TabLabelFit.columnsDp(
            innerDp = (maxWidth - OnyxSpace.ControlGap * 2).value,
            count = tabs.size,
        )
        val labelSizeSp = TabLabelFit.fontSize(
            columnsDp = columnsDp,
            labels = labels,
            width = measureLabel,
        )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // **Le barreau de la barre.** Comme le compositeur : le voile du verre posé sur la page
            // sombre ne donnait que 1,058:1 — la barre se lisait « encore plus sombre que la page »,
            // ce qui est le pire des cas (une surface doit monter, jamais descendre). Le barreau
            // [OnyxSurfaces.surfaceHigh] mesure **1,314:1** : la pilule est posée, et la capsule
            // active (1,201:1 contre elle) se lit **dans** la barre.
            .then(
                if (plate) {
                    // **Sur la plaque unique, la barre ne peint plus sa capsule** : forme, fond,
                    // filet, ombre et verre appartiennent à la plaque.
                    Modifier
                } else {
                    Modifier
                        .shadow(OnyxShadow.Float, shape, clip = false)
                        // **La famille d'ombres du socle, posée pour de vrai.** La planche donne à
                        // sa plaque (`.plaque`) l'ombre en trois profondeurs (`--gsh`,
                        // `hcss.py:226`) : elle est ajoutée ici, par-dessus la portée unique de
                        // l'app. C'est un **appelant réel** de [socleShadow] — sans lui, R8
                        // retirait `SocleShadowKt` du binaire.
                        .socleShadow(OnyxSocle.ShadowFar, OnyxSocle.RadiusPanel, theme.dark)
                        .clip(shape)
                        .background(OnyxSurfaces.surfaceHigh(theme), shape)
                        .border(OnyxSpace.Stroke, OnyxSurfaces.hairline(theme), shape)
                        .liquidGlass(GlassSurface.TAB_BAR, shape)
                },
            )
            .padding(horizontal = OnyxSpace.ControlGap, vertical = if (plate) 0.dp else OnyxSpace.BarPadding),
        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.ControlGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex.coerceIn(0, tabs.lastIndex)
            // L'encre est dérivée du fond réellement composé — voile, puis **la pastille** de
            // l'onglet actif, puis le contenu le plus extrême qui peut passer dessous.
            // **Le fond contre lequel l'encre est mesurée est celui de la PLAQUE — pas un fond
            // imaginaire.** Le défaut relevé sur la capture du propriétaire vient d'ici : l'encre
            // était choisie contre le **pire des deux aplats** que le fil pourrait laisser passer
            // (le voile composé sur du **blanc pur** et sur du **noir pur**), et l'on gardait celui
            // où le **blanc** se détache le moins — donc le plus **clair**. En apparence sombre, ce
            // fond-là est un gris à 189 quand la plaque réellement peinte est à 18 : l'encre claire
            // du thème était alors « corrigée » vers le bas (`#2F2F30`, ratio **1,40:1**), et les
            // libellés « Flux » et « Fichiers » se lisaient en quasi noir sur quasi noir.
            //
            // Une seule encre ne peut pas tenir 4,5:1 sur du blanc **et** sur du noir sous une vitre
            // mince — le choix est donc fait une fois, explicitement, du côté de **la matière de la
            // plaque** ([plaqueGround] : le voile du verre sur la **page** de l'apparence courante,
            // et sous sa pastille quand l'onglet est actif). C'est ce fond-là que la capture montre,
            // donc c'est lui que le contraste mesure. Le contenu qui défile **derrière** la plaque
            // est flouté et moyenné par la vitre ; il n'est plus une couleur, et le traiter comme un
            // aplat blanc était une hypothèse que la plaque ne respecte pas.
            //
            // **L'encre suit l'apparence** ([plaqueInk]) : l'onglet actif prend l'accent **en
            // encre** — l'état « c'est celui-là » —, les autres l'encre secondaire du thème, et le
            // **repère-visage** l'encre **pleine** (le blanc de la maison en sombre : le
            // propriétaire le demande explicitement, son contenu partait en noir sur le fond sombre).
            val isFace = faceIndex == index && face != null
            val ink = plaqueInk(theme, selected, isFace)
            val label = OnyxLocalization[
                // **Le repère-visage porte son propre libellé.** « Onyx » nomme **qui** répond — pas
                // la page qu'il ouvre. Le libellé de l'onglet reste dans le dictionnaire central et
                // continue de nommer la page pour le reste de l'app (le lecteur d'écran de la page,
                // l'état gardé par conversation).
                if (faceIndex == index && face != null) (faceLabel ?: ChatTab.FACE_LABEL) else tab.label
            ]

            // ---------------------------------------------------------------------------------
            // **La capsule se déplace, et se déforme légèrement.**
            //
            // Le reproche du propriétaire : l'onglet actif « apparaît comme une tache bleu sombre
            // illisible ». Deux causes, deux corrections :
            //
            //  1. la **couleur** — une teinte d'accent à 16 % sur un voile quasi noir ne se voit pas.
            //     Elle est remplacée par le barreau [OnyxSurfaces.capsule], qui mesure 1,201:1
            //     **contre la barre** : la pastille existe avant de porter une couleur ;
            //  2. le **mouvement** — un fondu de couleur ne déplace rien. La capsule part maintenant
            //     du côté de l'onglet **précédent** et glisse jusqu'au sien ([slideFrom]), en se
            //     **comprimant** au passage ([Feedback.TabSquash]) : elle se pose au lieu de
            //     s'allumer. Les deux mouvements lisent la **même** famille ([MotionFamily.Tab]) —
            //     une courbe, une durée — et le même ressort porte les deux, donc rien ne peut
            //     dériver de travers.
            //
            // Sous « Réduire les animations », la spec vaut `snap()` et la déformation est **nulle** :
            // la capsule est à sa place, entière, sans une image intermédiaire.
            // ---------------------------------------------------------------------------------
            // **Le glissement de la capsule a disparu avec la capsule.** Chaque onglet était décalé
            // de ± une colonne (`offset(x = slide)`) pour figurer la capsule qui glissait vers son
            // nouvel onglet ; sans capsule, ce décalage ne déplace plus qu'un libellé — il poussait
            // chaque repère non choisi d'une colonne vers la droite, si bien que le quatrième
            // sortait de la plaque (mesuré : « Onyx » large de 3,8 dp au bord droit). On n'anime
            // donc plus rien ici : c'est l'encre et le poids qui disent l'état.
            // La déformation : la capsule se comprime juste après le changement d'onglet, puis
            // reprend sa largeur en se posant. C'est l'aller-retour qui fait « objet qui se
            // déplace » plutôt que « couleur qui s'allume ».
            var squashed by remember { mutableStateOf(false) }
            LaunchedEffect(selectedIndex, reduceMotion) {
                if (reduceMotion) {
                    squashed = false
                } else {
                    squashed = true
                    delay(OnyxMotion.MS_120.toLong())
                    squashed = false
                }
            }
            val selectionSquash by animateFloatAsState(
                targetValue = if (squashed) Feedback.TabSquash else 1f,
                animationSpec = MotionFamily.Tab.animated(),
                label = "tabSelectionSquash",
            )
            val selectionScale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.94f,
                animationSpec = MotionFamily.Tab.animated(),
                label = "tabSelectionScale",
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    // **La cellule du repère est contenue dans sa rangée.** Sur la plaque, elle a
                    // un **plancher de hauteur** ([PlaqueGeometry.TabCellHeight], les 60 px de
                    // `.navi`) : la pastille, posée dessus, garde donc ses 8 dp de dégagement au
                    // filet de séparation et au bas de la plaque au lieu de monter jusqu'au bord
                    // (« la pastille déborde sur la ligne, c'est même pas centré »). Sur la carte
                    // (hors plaque), rien ne change : la cellule reste au contenu.
                    .then(
                        if (plate) Modifier.heightIn(min = PlaqueGeometry.TabCellHeight) else Modifier,
                    )
                    // **La colonne ne découpe plus ses enfants.** Elle portait un
                    // `.clip(SurfaceRole.ActiveCapsule.shape)` : c'est **lui** qui coupait le
                    // compteur de non-lu. La capsule est une forme à rayon égal à la moitié de la
                    // hauteur ; le coin haut-droit de la colonne est donc **en dehors** de la forme,
                    // et toute pastille posée à ce coin y était tranchée. Le découpage n'était pas
                    // nécessaire : la pastille active porte déjà **elle-même** la forme
                    // ([SurfaceRole.ActiveCapsule]) sur son fond et son filet, qui la dessinent
                    // ronde sans qu'aucun parent n'ait à rogner quoi que ce soit. Retiré, la
                    // pastille de compteur est entière quelle que soit sa taille.
                    // (Le plancher de hauteur de la cellule, lui, vient de la chaîne et reste.)
                    .then(
                        if (selected) {
                            Modifier.graphicsLayer {
                                scaleX = selectionSquash
                                scaleY = selectionScale
                            }
                        } else {
                            Modifier
                        },
                    )
                    // **La pastille de l'onglet actif** : le fond de la planche (`--pill`), posé
                    // **sous** l'onglet choisi et seulement sous lui, avec son filet
                    // (`inset 0 0 0 1px --pillline`). Elle **ne glisse plus** d'une colonne à
                    // l'autre : le décalage `offset` qui la suivait a disparu avec le lot
                    // précédent — il poussait chaque repère non choisi d'une colonne, et le
                    // quatrième (« Onyx ») sortait de la plaque. Ici, c'est l'onglet qui la porte
                    // donc elle ne peut plus déplacer un voisin : le dessin de la planche, sans
                    // le défaut qu'il avait introduit.
                    .then(
                        if (selected) {
                            Modifier
                                .background(pill, SurfaceRole.ActiveCapsule.shape)
                                .border(
                                    PlaqueGeometry.Outline,
                                    pillLine,
                                    SurfaceRole.ActiveCapsule.shape,
                                )
                        } else {
                            Modifier
                        },
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClickLabel = label,
                    ) { onSelect(index) }
                    // **Le nom sur la zone qu'on touche.** Relevé d'arbre : la case d'onglet
                    // était un nœud cliquable **muet**, son nom vivant dans un nœud **frère** de
                    // mêmes bornes. Le nom, l'état choisi et le rôle sont posés ici, avec la zone.
                    .semantics {
                        contentDescription = label
                        this.selected = selected
                        this.role = Role.Tab
                    }
                    .padding(vertical = if (plate) 0.dp else OnyxSpace.BarPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                // L'écart entre l'icône et son libellé : **le jeton de la maison**, le même que
                // dans une tuile, une ligne de menu et une ligne de feuille. Il valait 3 dp ici,
                // 2 dp dans le libellé d'onglet ci-dessus (entre deux onglets) et 9 dp dans une
                // tuile — trois valeurs pour un seul geste de lecture.
                verticalArrangement = Arrangement.spacedBy(OnyxSpace.IconLabelGap),
            ) {
                // **Le repère-visage.** La planche met au quatrième repère « le visage dans un
                // cercle », pas une ampoule : le visage dit **à qui** on parle, ce qu'un
                // pictogramme d'idée ne dit pas.
                //
                // **Le disque ne prend plus l'accent.** La couleur d'identité du bot (`accent`,
                // c'est-à-dire `botFaceColour`) **désigne le bot, elle ne colore pas la
                // commande** : la teinter ici faisait porter au quatrième repère un **cercle à la
                // couleur du bot** — le « cercle violet » relevé — alors que la planche n'a,
                // sous son `.capface`, **aucun disque** : le visage du bot, et lui seul, porte sa
                // couleur (c'est l'identité), et l'anneau clair de la planche
                // (`0 0 0 2.5px rgba(255,255,255,.9)`) le détache du verre sans ajouter de
                // matière. Le même accent continue de teinter ce qui **désigne** le bot ailleurs
                // (la pastille de non-lu) — il ne peint plus le repère.
                val faceContent = face
                Box(
                    modifier = Modifier.size(PlaqueGeometry.TabCell),
                    contentAlignment = Alignment.Center,
                ) {
                    if (faceContent != null && faceIndex == index) {
                        Box(
                            modifier = Modifier
                                .size(PlaqueGeometry.TabCell)
                                .clip(RoundedCornerShape(50))
                                .border(
                                    PlaqueGeometry.FaceRing,
                                    Color.White.copy(alpha = PlaqueGeometry.FaceRingAlpha),
                                    RoundedCornerShape(50),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            faceContent()
                        }
                    } else {
                        Image(
                            painter = painterResource(tab.symbol),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(ink),
                            modifier = Modifier.size(19.dp).scale(selectionScale),
                        )
                    }
                    // La pastille de non-lu : ce qu'il y a de neuf sur cet onglet.
                    //
                    // **Ce qui était faux, et ce qui le corrige.** Elle était ancrée hors de sa
                    // cellule (`offset(x = +7.dp, y = (-4).dp)`) et peinte à la main (rembourrage
                    // vertical de 1 dp, aucune largeur minimale) : le chiffre venait au contact de la
                    // boîte peinte, la pastille mordait sur le filet de la plaque, et la colonne
                    // d'onglet — découpée en capsule — la tranchait (le défaut relevé : « la partie
                    // nombre de flux est coupé »). Elle est désormais le compteur de la maison
                    // ([CounterBadge] : rembourrage 2 dp au-dessus et au-dessous, largeur et hauteur
                    // **mesurées** sur le chiffre), ancré **en dedans** du coin de la cellule
                    // ([BadgeGeometry.AnchorInset]), donc entièrement dans la rangée.
                    val count = badges.getOrNull(index) ?: 0
                    if (count > 0) {
                        CounterBadge(
                            count = count,
                            fill = accent,
                            contentColor = readableInk(accent, accent, ICON_CONTRAST_MIN),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = -BadgeGeometry.AnchorInset, y = BadgeGeometry.AnchorInset),
                        )
                    }
                }
                BasicText(
                    text = label,
                    localize = false,
                    // La taille **retenue par la mesure** : elle vient de `TabLabelFit`, donc jamais
                    // d'un libellé coupé — et `Ellipsis` en dernier recours, pour qu'une coupure au
                    // milieu d'un mot reste impossible même si le plancher ne suffisait pas.
                    style = theme.caption2.copy(
                        fontSize = labelSizeSp.sp,
                        color = ink,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    }
}
