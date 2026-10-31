package com.onyx.app.ui.design

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import com.onyx.app.ui.localization.BasicText
import kotlin.math.roundToInt

/**
 * **Ce qui a le droit d'être coupé, et ce qui ne l'a pas.** Une seule politique pour toute l'app,
 * appliquée par les sites d'affichage et **tenue par un verrou** (`TextTruncationPolicyTest`) : le
 * prochain lot ne peut pas réintroduire une coupure anonyme.
 *
 * Le partage est celui du vocabulaire de l'interface, pas d'un fichier :
 *
 * - [Kind.LABEL] — un libellé qu'on **lit pour agir** ou **pour connaître un état** : une action de
 *   feuille, un bouton, une puce, un filtre, un état (« En cours », « Compression… »). Il **se
 *   replie** ([maxLines] = 2) : à police 1,3×, la place manque d'un coup, et « Mode… » ne dit rien.
 * - [Kind.IDENTIFIER] — ce qui **doit rester exact pour être utile** : une adresse de serveur, un
 *   chemin, un nom de modèle, une cible MCP, un hôte. Il ne se coupe **jamais** à la fin
 *   ([allowsEllipsis] = false) : il s'**abrège au milieu** ([middleEllipsis]), en gardant le début
 *   **et** la fin — `https://api.exemple.com/v1` reste reconnaissable, `https://api.exe…` ne l'est pas.
 * - [Kind.TITLE] — le titre d'une rangée ou d'une en-tête : deux lignes, ellipse au-delà.
 * - [Kind.DATA] — une **donnée libre** que la personne a écrite ou reçue : un nom de fichier, un
 *   aperçu de message, un extrait de code. Elle garde une ligne et l'ellipse : la donnée est
 *   tronquée par nature, et la déplier ferait sauter la hauteur des rangées (le défaut « grille en
 *   escalier » que le volet a déjà payé). **Seule catégorie qui conserve la coupure.**
 */
object OnyxTextPolicy {

    enum class Kind { LABEL, IDENTIFIER, TITLE, DATA }

    /** Les lignes qu'un texte de ce genre accepte. */
    fun maxLines(kind: Kind): Int = when (kind) {
        Kind.LABEL -> 2
        Kind.IDENTIFIER -> 1
        Kind.TITLE -> 2
        Kind.DATA -> 1
    }

    /** L'ellipse est-elle permise pour ce genre ? Un identifiant et un nombre, non. */
    fun allowsEllipsis(kind: Kind): Boolean = kind != Kind.IDENTIFIER

    /** Une coupure **anonyme** — c'est ce que le verrou refuse. */
    fun isAnonymousCut(kind: Kind, maxLines: Int): Boolean = when (kind) {
        Kind.LABEL, Kind.TITLE -> maxLines < 2
        Kind.IDENTIFIER -> true
        Kind.DATA -> false
    }
}

/**
 * L'abrègement **au milieu**, pour ce qui doit garder son début ET sa fin.
 *
 * Une adresse coupée à la fin ne dit plus **où** elle mène (le chemin, le port, le suffixe d'hôte
 * est justement ce qui distingue deux serveurs) ; coupée au début, elle ne dit plus **quoi** (le
 * schéma, l'hôte). D'où la règle : [head] garde le début, [ELLIPSIS] marque le trou, [tail] garde la
 * fin.
 *
 * La recherche est une **fonction pure** — elle reçoit la mesure du texte en pixels — donc
 * éprouvable sans écran, comme `TileLabelFit`. Elle ne boucle jamais sur la mesure d'un texte
 * complet : deux recherches décroissantes, l'une pour la queue, l'autre pour la tête.
 */
object IdentifierFit {

    const val ELLIPSIS = "…"

    /** Ce qu'on garde au minimum de chaque côté : en dessous, l'abrègement n'informe plus. */
    const val MIN_HEAD = 4
    const val MIN_TAIL = 4

    /** La part de la place réservée à la **fin** : c'est elle qui dit où mène l'adresse. */
    const val TAIL_SHARE = 0.45f

    /**
     * Le repli sur un **séparateur** : couper au milieu d'un segment (« api.exemp…ple.com ») est
     * moins lisible que de couper entre deux segments (« api…ple.com »). On recule jusqu'à 6
     * caractères pour trouver un séparateur, jamais sous [MIN_HEAD].
     */
    private const val BOUNDARY_LOOKBACK = 6
    private const val BOUNDARIES = "/\\.:-_@?#&"

    /**
     * [text] abrégé pour tenir dans [maxWidthPx], ou [text] tel quel s'il tient déjà.
     *
     * [measure] rend la largeur du texte **avec le style et la densité courants** : l'appelant la
     * lit sur son `TextMeasurer`, ce qui garde cette fonction pure et la rend vérifiable en test.
     */
    fun middleEllipsis(text: String, maxWidthPx: Int, measure: (String) -> Int): String {
        if (text.isEmpty() || maxWidthPx <= 0) return text
        if (measure(text) <= maxWidthPx) return text
        val tail = longestSuffix(text, (maxWidthPx * TAIL_SHARE).toInt(), measure)
        var head = longestHead(text, tail, maxWidthPx, measure)
        // Si la queue mange tout l'air, on la raccourcit jusqu'à laisser un début lisible.
        var tailKept = tail
        while (head < MIN_HEAD && tailKept > MIN_TAIL) {
            tailKept--
            head = longestHead(text, tailKept, maxWidthPx, measure)
        }
        if (head <= 0) {
            // La queue mangeait toute la place : on garde alors le **début**, c'est lui qui nomme
            // la chose. Le résultat doit tenir, sinon il n'informe pas.
            tailKept = 0
            head = longestHead(text, 0, maxWidthPx, measure)
        }
        if (head <= 0 && tailKept <= 0) return ELLIPSIS
        val snapped = snapToBoundary(text, head)
        val finalHead = if (snapped >= MIN_HEAD) snapped else head
        return text.take(finalHead) + ELLIPSIS + if (tailKept > 0) text.takeLast(tailKept) else ""
    }

    private fun longestSuffix(text: String, budgetPx: Int, measure: (String) -> Int): Int {
        if (budgetPx <= 0) return 0
        var k = text.length
        while (k > 0) {
            val candidate = text.takeLast(k)
            if (measure(ELLIPSIS + candidate) <= budgetPx) return k
            k--
        }
        return 0
    }

    private fun longestHead(text: String, tail: Int, maxWidthPx: Int, measure: (String) -> Int): Int {
        var k = text.length - tail
        while (k > 0) {
            val candidate = text.take(k) + ELLIPSIS + if (tail > 0) text.takeLast(tail) else ""
            if (measure(candidate) <= maxWidthPx) return k
            k--
        }
        return 0
    }

    /** Le plus proche séparateur à gauche, dans la limite du regard arrière. */
    private fun snapToBoundary(text: String, head: Int): Int {
        for (i in head downTo (head - BOUNDARY_LOOKBACK).coerceAtLeast(MIN_HEAD)) {
            if (i - 1 in text.indices && BOUNDARIES.contains(text[i - 1])) return i
        }
        return head
    }
}

/**
 * Un texte d'**identifiant** (adresse, chemin, modèle, cible) qui ne se coupe jamais à la fin.
 *
 * Il mesure la place **réellement** offerte (`BoxWithConstraints`, la contrainte reçue) — mesurer
 * dans le parent donnait une largeur plus grande que celle du contrôle, et l'adresse débordait de
 * son champ. Puis [IdentifierFit] décide, et le texte est dessiné **verbatim** (`softWrap = false`,
 * `overflow = Clip`) : ce qui est décidé est ce qui est affiché, il n'y a pas de seconde coupure
 * cachée.
 */
@Composable
fun IdentifierText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val drawn = if (color == Color.Unspecified) style else style.copy(color = color)
    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx() }.roundToInt()
        val shown = remember(text, widthPx, drawn, maxLines) {
            IdentifierFit.middleEllipsis(text, widthPx) { candidate ->
                measurer.measure(text = candidate, style = drawn).size.width
            }
        }
        // **C'est [drawn] qui se dessine, pas [style].** Le défaut relevé était mesurable au pixel :
        // la feuille de modèle peignait **du noir** là où elle annonçait une encre mesurée — les
        // libellés de pastille (« Faible », « LM Studio »), le **nom d'un modèle** et sa **sous-ligne
        // de fournisseur** se lisaient à **1,68:1** et **1,70:1** sur la feuille sombre. La couleur
        // reçue ne servait qu'à **mesurer** la coupe au milieu : le texte était ensuite peint avec
        // le style nu, donc de la couleur par défaut du socle (noir), et le paramètre `color` de
        // cette fonction n'était **pas** appliqué. Ce qui est mesuré est désormais ce qui est peint.
        BasicText(
            text = shown,
            localize = false,
            style = drawn,
            maxLines = maxLines,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/**
 * Le libellé d'accessibilité d'une **zone cliquable**, posé sur la zone elle-même.
 *
 * Le défaut relevé : le libellé vivait sur l'**icône** (un `semanticsLabel` posé sur le dessin), si
 * bien que le nœud **cliquable** — celui qu'on touche et que le lecteur d'écran annonce — restait
 * **sans nom** : « 18 éléments cliquables sans libellé » dans le volet, les cellules de tuiles
 * comprises. Ici le nom est posé **avec la zone**.
 *
 * Mesure qui a tranché le choix de l'implémentation : l'arbre relève le nom sur le nœud cliquable
 * avec un simple `semantics { contentDescription = … }` (c'est ce que fait déjà `semanticsLabel`,
 * relevé : `clk=true desc=« Réglages »`), et **pas** avec `clearAndSetSemantics` — celui-ci fait
 * apparaître un nœud **enfant** de mêmes bornes, et le nœud cliquable reste muet. Les descendants
 * ne sont donc pas effacés.
 *
 * [label] est le libellé **déjà en français** : c'est le texte visible du contrôle, ou sa
 * description utile pour un contrôle sans texte (« Actualiser les conversations »).
 */
fun Modifier.tapLabel(label: String): Modifier =
    this.semantics { contentDescription = label }
