package com.onyx.app.ui.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * **L'échelle d'espacement unique de l'app**, et les jetons nommés qui s'y rattachent.
 *
 * Pourquoi un fichier, alors que des `dp` traînaient déjà partout : c'est précisément ça, le défaut.
 * Le relevé fait pour ce lot a trouvé, dans les seuls écrans visibles sur la capture du
 * propriétaire, **quatorze** valeurs d'espacement différentes (2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
 * 14, 16, 18) pour six intentions. Deux rangées de la même liste ne portaient pas le même écart,
 * l'icône d'un onglet n'était pas à la même distance de son libellé que celle de sa tuile, et le
 * rembourrage d'une carte de bot (12 dp) n'était pas celui d'une rangée de conversation (11 dp).
 * C'est ça qui se voit à l'œil comme « pas propre », avant toute question de couleur.
 *
 * **L'échelle.** Cinq marches, toutes multiples de 4 : [Hair] 4, [Half] 8, [Snug] 12, [Base] 16,
 * [Loose] 24. Elles sont déclarées **dans l'ordre**, et [Scale] les rend dans cet ordre : un test
 * vérifie qu'aucune ne manque et qu'aucune n'est hors échelle, donc la liste ne peut pas dériver du
 * code qui l'utilise.
 *
 * **Les valeurs sous 4 dp ne sont pas de l'espacement.** 1 dp (trait), 2 dp (écart entre deux
 * glyphes empilés, rembourrage d'une pastille de non-lu) et 3 dp n'existent que comme
 * **correction optique** : elles ne séparent pas deux blocs, elles recalent un dessin. Elles sont
 * donc déclarées dans [Optical] — nommées, elles aussi, mais **hors** de l'échelle : un test
 * interdit qu'une d'elles serve de marge d'écran, d'écart entre blocs, de rembourrage de carte ou
 * d'écart icône-libellé. Sans cette séparation, « une seule échelle » ne veut rien dire : on
 * finirait par appeler « échelle » une collection de valeurs choisies au cas par cas.
 *
 * **Les jetons nommés.** Chaque écart a **un** nom, par usage, et il pointe sur une marche. Les
 * écrans (et les composants) s'en servent au lieu d'écrire un nombre : c'est la seule façon qu'une
 * même famille d'éléments garde le même écart partout, et qu'un changement se fasse en un endroit.
 *
 * **Le relevé.** [Audit] porte le tableau du lot : pour chaque site corrigé, la valeur trouvée, la
 * valeur retenue et le jeton qui la nomme. `OnyxSpaceScaleTest` s'en sert pour vérifier que le
 * fichier **porte** le jeton (« après ») et **ne porte plus** la valeur d'avant — le tableau du
 * rapport est donc verrouillé par le source, il ne peut pas se contenter d'être plausible.
 */
object OnyxSpace {

    // -----------------------------------------------------------------------------------------
    // L'échelle
    // -----------------------------------------------------------------------------------------

    /** L'échelle, du plus serré au plus large. C'est **la** liste : [Scale]. */
    val Scale: List<Dp> = listOf(4.dp, 8.dp, 12.dp, 16.dp, 24.dp)

    /** Le pas de l'échelle, en dp : toute marche en est un multiple. */
    const val Step: Int = 4

    /** 4 dp — le plus petit écart qui sépare encore deux choses. */
    val Hair: Dp = 4.dp

    /** 8 dp — l'écart entre deux éléments d'une **même** ligne, ou deux blocs serrés. */
    val Half: Dp = 8.dp

    /** 12 dp — l'écart entre deux blocs d'une même carte, l'interligne d'un groupe. */
    val Snug: Dp = 12.dp

    /** 16 dp — l'écart `par défaut`, et la marge d'écran. */
    val Base: Dp = 16.dp

    /** 24 dp — la séparation de deux sections distinctes. */
    val Loose: Dp = 24.dp

    // -----------------------------------------------------------------------------------------
    // Les jetons, par usage
    // -----------------------------------------------------------------------------------------

    /** La marge d'un écran : le vide entre le contenu et le bord de la fenêtre. */
    val ScreenMargin: Dp = Base

    /** L'écart entre deux blocs empilés (deux cartes, deux sections d'une carte). */
    val BlockGap: Dp = Snug

    /** L'écart entre deux sections franchement distinctes d'un même écran. */
    val SectionGap: Dp = Loose

    /** Le rembourrage intérieur d'une carte, d'un panneau, d'une feuille. */
    val CardPadding: Dp = Base

    /** L'écart entre deux cartes d'une même grille. */
    val CardGap: Dp = Half

    /**
     * La hauteur **minimale** d'une rangée de liste ou d'une ligne de feuille.
     *
     * Ce n'est **pas** un écart : c'est une hauteur de cible, déclarée dans [sizes]. La mettre dans
     * [tokens] ferait mentir l'échelle — 56 dp n'est pas une marche d'espacement, et une échelle qui
     * accueille n'importe quoi ne veut plus rien dire.
     */
    val RowHeight: Dp = 56.dp

    /**
     * Le côté d'une **vignette d'icône de rangée** : le carré arrondi posé au début d'une ligne de
     * feuille, de menu ou de liste, avec son icône dedans.
     *
     * 44 dp est la cible tactile du pouce *et* la taille à laquelle le carré reste franc à côté
     * d'une ligne de 56 dp (un carré de 40 paraît rogné, un carré de 48 mange le libellé à police
     * 1,3×). C'est une **taille**, pas un écart, donc elle a sa place ici ; elle est bien un multiple
     * du pas de 4, comme [RowHeight].
     */
    val ThumbnailSize: Dp = 44.dp

    /** L'écart entre l'icône d'une ligne et son libellé — **le même partout**. */
    val IconLabelGap: Dp = Half

    /**
     * L'écart entre une **vignette d'icône de rangée** et son libellé. C'est [IconLabelGap] : la
     * vignette et l'icône nue se suivent dans la même liste, elles ne peuvent pas s'écarter
     * différemment de leur mot.
     */
    val ThumbnailGap: Dp = IconLabelGap

    /** Le rembourrage horizontal d'un en-tête de barre ou d'un panneau. */
    val BarInset: Dp = Base

    /** L'écart vertical entre les pièces d'une barre (barre du haut, barre d'onglets). */
    val BarPadding: Dp = Hair

    /** L'écart horizontal entre deux onglets, deux tuiles ou deux pastilles. */
    val ControlGap: Dp = Hair

    /** L'écart entre les lignes d'un fil de conversation. */
    val ThreadGap: Dp = Base

    /**
     * La marge d'une feuille du bas par rapport au bord de l'écran.
     */
    val SheetMargin: Dp = Base

    // -----------------------------------------------------------------------------------------
    // Les rayons — la famille des formes
    // -----------------------------------------------------------------------------------------

    /**
     * **La famille des rayons**, en quatre marches, du plus serré au plus ouvert : [FieldCorner]
     * (un champ, une capsule de saisie), [TileCorner] (une vignette, une tuile), [CardCorner]
     * (une carte, une rangée de liste), [PanelCorner] (une feuille, un menu, une alerte).
     *
     * Pourquoi une famille plutôt qu'un rayon par composant : le relevé de ce lot a trouvé, dans les
     * seules surfaces visibles, **dix** rayons pour quatre intentions (10, 11, 12, 12, 14, 14, 16,
     * 18, 22, 50). Trois listes de réglages n'avaient pas le même coin, et une carte de réponse
     * n'avait pas celui de la carte de bots posée juste à côté. C'est ça qui se lit comme « pas
     * soigné » avant toute question de couleur : une forme qui ne répond pas à sa voisine.
     *
     * **La règle de l'emboîtement.** Un élément posé **dans** un autre prend son rayon moins son
     * rembourrage (vignette dans une feuille, carte dans une feuille) : `interior(outer, inset)`.
     * Deux courbes qui partagent le même centre se lisent comme une seule forme ; deux courbes
     * quelconques se lisent comme deux rectangles qui se chevauchent.
     *
     * **Aucune bordure dure.** La forme se lit par la **lumière** (la lumière de tête, en haut) et
     * l'**ombre** (l'ombre de pied et l'ombre portée, en bas) — jamais par un contour d'un seul
     * pixel. Un rayon plus généreux demande plus de soin, pas plus de trait.
     */
    val FieldCorner: Dp = 14.dp

    /** Le coin d'une vignette, d'une tuile, d'un carré arrondi de rangée. */
    val TileCorner: Dp = 16.dp

    /** Le coin d'une carte, d'une rangée de liste, d'un bloc de réponse. */
    val CardCorner: Dp = 20.dp

    /** Le coin de la famille des panneaux (feuille, menu, alerte) — le plus ouvert. */
    val PanelCorner: Dp = 26.dp

    /** Le coin d'une vignette d'icône de rangée : `TileCorner` diminué de son rembourrage. */
    val ThumbnailCorner: Dp = 13.dp

    /**
     * **Exception nommée (1/2) — le coin d'une rangée de formulaire natif.**
     *
     * Une liste groupée à la manière du système n'est pas une carte : son coin est plus serré que
     * [FieldCorner] et il est **le même depuis toujours** (le verrou de parité des formulaires
     * l'épingle à 12). Le nommer ici plutôt que de le laisser en dur à un seul site, c'est la
     * condition pour que « la famille » reste la règle et l'exception, l'exception.
     */
    val FormCorner: Dp = 12.dp

    /**
     * **Exception nommée (2/2) — le coin d'un petit contrôle ou d'un petit graphique.**
     *
     * Une touche de clavier, une pastille de couleur, un badge, un carré de 16 à 20 dp : sous cette
     * taille, un coin de [FieldCorner] (14 dp) avale la forme et se lit comme une capsule. Ces
     * éléments partageaient six rayons (6, 7, 8, 9, 10, 11) pour une seule intention ; ils en
     * partagent maintenant un. C'est la **seule** autre exception de l'app.
     */
    val ControlCorner: Dp = 9.dp

    // -----------------------------------------------------------------------------------------
    // L'optique — hors échelle, et nommé comme tel
    // -----------------------------------------------------------------------------------------

    /**
     * Les valeurs de **correction optique** : sous 4 dp, elles ne séparent rien, elles recalent un
     * dessin (un trait, un décalage de glyphe, le rembourrage d'une pastille). Elles vivent ici,
     * nommées, pour qu'on ne les confonde pas avec de l'espacement — un test refuse qu'une d'elles
     * soit employée comme marge, comme écart de blocs, comme rembourrage de carte ou comme écart
     * icône-libellé.
     */
    val Optical: List<Dp> = listOf(1.dp, 2.dp, 3.dp)

    /** Le trait : un filet de séparation, un contour. */
    val Stroke: Dp = 1.dp

    /** L'écart minimal entre deux glyphes empilés dans un contrôle (badge, pastille). */
    val GlyphGap: Dp = 2.dp

    // -----------------------------------------------------------------------------------------
    // La règle, pure
    // -----------------------------------------------------------------------------------------

    /** La marche la plus proche de [value], en dp. */
    fun nearest(value: Dp): Dp = Scale.minByOrNull { abs(it.value - value.value) } ?: Hair

    /** La marche la plus proche de [value] dès lors qu'elle n'est pas plus basse que l'original. */
    fun nearestAtLeast(value: Dp): Dp = Scale.firstOrNull { it.value >= value.value } ?: Loose

    /** Vrai si [value] est une marche de l'échelle (0 est admis : « aucun écart »). */
    fun isOnScale(value: Dp): Boolean =
        value.value == 0f || Scale.any { abs(it.value - value.value) < 0.001f }

    /**
     * **La règle de l'emboîtement** : le rayon d'un élément posé à [inset] du bord d'un contenant de
     * rayon [outer]. Les deux courbes partagent alors le même centre, et l'œil lit **une** forme
     * plutôt que deux rectangles qui se chevauchent. Jamais sous zéro : si le rembourrage dépasse le
     * rayon, l'élément est carré (une courbe forcée se lit comme un défaut de plus).
     */
    fun interior(outer: Dp, inset: Dp): Dp = (outer - inset).coerceAtLeast(0.dp)

    /**
     * Le retrait d'un **séparateur** de rangée : il commence là où commence le **libellé** — donc
     * après la vignette, s'il y en a une — et s'arrête avant le bord droit, du même retrait.
     *
     * Une seule fonction pour tous les séparateurs de l'app : un filet bord à bord au milieu de
     * filets en retrait se lit comme un oubli, et c'est exactement le défaut que le propriétaire
     * pointe (« le filet commence après la vignette et s'arrête avant le bord »).
     *
     * @param thumb le côté de la vignette de tête, `0.dp` pour une rangée sans vignette.
     */
    fun separatorInset(thumb: Dp = 0.dp): Dp =
        if (thumb > 0.dp) CardPadding + thumb + ThumbnailGap else CardPadding

    /** Le nom de la marche de [value], ou `null` si elle n'est pas sur l'échelle. */
    fun nameOf(value: Dp): String? = when {
        value.value == 0f -> "None"
        abs(value.value - Hair.value) < 0.001f -> "Hair"
        abs(value.value - Half.value) < 0.001f -> "Half"
        abs(value.value - Snug.value) < 0.001f -> "Snug"
        abs(value.value - Base.value) < 0.001f -> "Base"
        abs(value.value - Loose.value) < 0.001f -> "Loose"
        else -> null
    }

    /** Le pas entier, pour les endroits qui comptent en pixels (`SystemMargins`). */
    fun nearestPx(valueDp: Int): Int = (valueDp / Step.toFloat()).roundToInt() * Step

    // -----------------------------------------------------------------------------------------
    // Les jetons, tels que le test et le rapport les lisent
    // -----------------------------------------------------------------------------------------

    /** Les jetons déclarés, par nom — pour qu'aucun ne puisse être hors échelle sans échouer. */
    val tokens: List<Pair<String, Dp>> = listOf(
        "ScreenMargin" to ScreenMargin,
        "BlockGap" to BlockGap,
        "SectionGap" to SectionGap,
        "CardPadding" to CardPadding,
        "CardGap" to CardGap,
        "IconLabelGap" to IconLabelGap,
        "BarInset" to BarInset,
        "BarPadding" to BarPadding,
        "ControlGap" to ControlGap,
        "ThreadGap" to ThreadGap,
        "SheetMargin" to SheetMargin,
        "ThumbnailGap" to ThumbnailGap,
    )

    /**
     * **Les marches de l'échelle, nommées.** [tokens] porte les noms *d'usage* ; cette liste porte
     * les marches elles-mêmes, pour qu'un tableau puisse citer « Snug = 12 » sans qu'on ait à
     * deviner de quelle marche il parle.
     */
    val steps: List<Pair<String, Dp>> = listOf(
        "Hair" to Hair,
        "Half" to Half,
        "Snug" to Snug,
        "Base" to Base,
        "Loose" to Loose,
    )

    /**
     * **La cible tactile minimale : 48 dp.** C'est la valeur de la source
     * (`shared/designsystem/.../components/TouchTarget.kt:10` : `val MinTouchTarget = 48.dp`), et
     * c'est aussi le plancher du brief de ce lot. Une rangée, une pilule, un champ et un bouton rond
     * la prennent — la cible se mesure, elle ne se suppose pas.
     */
    val TouchTarget: Dp = 48.dp

    /**
     * Les **tailles** nommées : une hauteur de cible ([RowHeight]) et le côté d'une vignette
     * ([ThumbnailSize]). Elles ne sont pas sur l'échelle — une hauteur n'est pas un vide — mais
     * elles en sont un **multiple**, et elles vivent ici pour la même raison que les jetons : un
     * seul nom, un seul endroit.
     */
    val sizes: List<Pair<String, Dp>> = listOf(
        "RowHeight" to RowHeight,
        "ThumbnailSize" to ThumbnailSize,
        "TouchTarget" to TouchTarget,
    )

    /**
     * Les **rayons** nommés : la famille des formes, du plus serré ([FieldCorner]) au plus ouvert
     * ([PanelCorner]).
     *
     * Un rayon n'est ni un écart ni un multiple du pas de 4 (26 dp est le coin d'un panneau), et
     * c'est pour ça qu'il a sa propre liste : verser un rayon dans l'échelle d'espacement, c'est
     * exactement ce que la consigne interdit — appeler « échelle » une collection de valeurs
     * choisies au cas par cas.
     */
    val radii: List<Pair<String, Dp>> = listOf(
        "FieldCorner" to FieldCorner,
        "TileCorner" to TileCorner,
        "CardCorner" to CardCorner,
        "PanelCorner" to PanelCorner,
        "ThumbnailCorner" to ThumbnailCorner,
        "FormCorner" to FormCorner,
        "ControlCorner" to ControlCorner,
    )

    /**
     * **Les seuls rayons autorisés dans l'app.** Le verrou de cohérence (`ScreenDefaultsTest`)
     * refuse tout `RoundedCornerShape(<nombre>)` hors de cette liste dans un écran : c'est ce qui
     * empêche la divergence de revenir au lot suivant. `50` est la capsule (une forme, pas un
     * nombre) et reste admise comme idiome.
     */
    val allowedRadii: List<Dp> =
        radii.map { it.second } + 50.dp

    /**
     * Un site corrigé : où, la valeur d'avant, la valeur d'après, et le jeton qui la nomme.
     *
     * [file] est relatif à la racine du module (`core/` ou `app/`), [beforeSnippet] et
     * [afterSnippet] sont les extraits tels qu'ils doivent se lire dans le source — c'est le
     * verrou : le tableau annoncé est celui que le code porte.
     */
    data class SpacingFix(
        val where: String,
        val file: String,
        val before: String,
        val after: String,
        val token: String,
        val why: String,
    )

    /**
     * **Le tableau du lot** : chaque écart trouvé hors échelle dans les surfaces des écrans de la
     * capture, et la marche retenue. Toutes les lignes sont vérifiées par `OnyxSpaceScaleTest` —
     * l'ancienne valeur ne doit plus se lire dans le fichier, la nouvelle doit y être.
     *
     * Le tableau est le livrable « avant → après » : il se lit ici, et il se prouve dans le source.
     */
    val Audit: List<SpacingFix> = listOf(
        SpacingFix(
            where = "Feuille du bas (ajout) — vignette de rangée",
            file = "core/src/main/java/com/onyx/app/ui/design/AttachmentSheet.kt",
            before = "internal val SheetBadgeSize = 40.dp",
            after = "internal val SheetBadgeSize = OnyxSpace.ThumbnailSize",
            token = "ThumbnailSize = 44",
            why = "40 dp avec un coin de 11 et une icône de 20 : trois valeurs qui ne venaient d'aucune table. La vignette est celle de toutes les rangées (44 dp, coin de la famille, ombre douce).",
        ),
        SpacingFix(
            where = "Feuille du bas (ajout) — séparateur en retrait",
            file = "core/src/main/java/com/onyx/app/ui/design/AttachmentSheet.kt",
            before = "actions.forEach { action ->",
            after = "if (index > 0) OnyxRowDivider(inset = SheetDividerInset, endInset = OnyxSpace.CardPadding)",
            token = "CardPadding = 16",
            why = "Aucun filet entre les lignes : la feuille se lisait comme un bloc. Le filet part du libellé — après la vignette — et s'arrête au rembourrage de carte du bord droit : `endInset` vaut `inset` par défaut, donc sans ce paramètre le trait s'arrêtait à 68 dp du bord (défaut mesuré sur la capture du propriétaire).",
        ),
        SpacingFix(
            where = "Table du verre — coin de la famille des panneaux",
            file = "core/src/main/java/com/onyx/app/ui/design/LiquidGlass.kt",
            before = "val GlassPanelCorner: Dp = 14.dp",
            after = "val GlassPanelCorner: Dp = OnyxSpace.PanelCorner",
            token = "PanelCorner = 26",
            why = "14 dp était le coin d'une carte, pas d'un panneau presque plein écran : une feuille se lit comme une feuille si son coin est franchement plus ouvert que celui des rangées qu'elle contient.",
        ),
        SpacingFix(
            where = "Table du verre — rayon d'une rangée de liste",
            file = "core/src/main/java/com/onyx/app/ui/design/LiquidGlass.kt",
            before = "LIST_ROW(GlassBlurMinDp, GlassInk.TEXT, 2f, RoundedCornerShape(12.dp))",
            after = "LIST_ROW(GlassBlurMinDp, GlassInk.TEXT, 0f, RoundedCornerShape(OnyxSpace.CardCorner), lit = false)",
            token = "CardCorner = 20",
            why = "12 dp pour une carte, 14 pour la carte de bot posée juste à côté : deux coins pour une même famille. Toutes les cartes prennent le coin des cartes.",
        ),
        SpacingFix(
            where = "Filet d'une rangée — retrait des deux bords",
            file = "core/src/main/java/com/onyx/app/ui/design/OnyxForm.kt",
            before = "fun OnyxRowDivider(inset: androidx.compose.ui.unit.Dp = OnyxSpace.CardPadding) {",
            after = "endInset: androidx.compose.ui.unit.Dp = inset,",
            token = "CardPadding = 16",
            why = "Le filet ne s'arrêtait qu'à gauche et courait jusqu'au bord droit. Il est maintenant en retrait **des deux côtés** — c'est ce qui distingue une liste dessinée d'une liste bord à bord.",
        ),
        SpacingFix(
            where = "Fil de conversation — écart entre messages",
            file = "app/src/main/java/com/onyx/app/ui/chat/ChatScreen.kt",
            before = "verticalArrangement = Arrangement.spacedBy(14.dp),",
            after = "verticalArrangement = Arrangement.spacedBy(OnyxSpace.ThreadGap),",
            token = "ThreadGap = 16",
            why = "14 n'est sur aucune échelle (2 × 7) : il ne vient ni de 12 ni de 16. Les messages prennent la marche Base, la même que la marge d'écran du fil — c'est le même vide.",
        ),
        SpacingFix(
            where = "Fil de conversation — marge d'écran",
            file = "app/src/main/java/com/onyx/app/ui/chat/ChatScreen.kt",
            before = "contentPadding = PaddingValues(\n                        start = 16.dp,\n                        end = 16.dp,",
            after = "contentPadding = PaddingValues(\n                        start = OnyxSpace.ScreenMargin,\n                        end = OnyxSpace.ScreenMargin,",
            token = "ScreenMargin = 16",
            why = "La valeur était déjà juste, mais anonyme : elle ne tenait qu'à la vigilance. Nommée, elle ne peut plus dériver d'un écran à l'autre.",
        ),
        SpacingFix(
            where = "Fil de conversation — réserve sous la vitre du haut",
            file = "app/src/main/java/com/onyx/app/ui/chat/ChatScreen.kt",
            before = "top = if (stacked) 12.dp else glassTopInset + 12.dp,",
            after = "top = if (stacked) OnyxSpace.BlockGap else glassTopInset + OnyxSpace.BlockGap,",
            token = "BlockGap = 12",
            why = "La réserve du fil sous la barre du haut suit l'écart entre blocs : c'est le même vide, vu depuis l'autre côté du verre.",
        ),
        SpacingFix(
            where = "Barre des cinq onglets — écart icône-libellé",
            file = "core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt",
            before = "verticalArrangement = Arrangement.spacedBy(3.dp),",
            after = "verticalArrangement = Arrangement.spacedBy(OnyxSpace.IconLabelGap),",
            token = "IconLabelGap = 8",
            why = "3 dp collait l'icône à son libellé ; l'écart d'un onglet et celui d'une tuile doivent être le même — c'est le défaut qui se voit le plus sur la capture du propriétaire.",
        ),
        SpacingFix(
            where = "Barre des cinq onglets — écart entre onglets",
            file = "core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt",
            before = "horizontalArrangement = Arrangement.spacedBy(2.dp),",
            after = "horizontalArrangement = Arrangement.spacedBy(OnyxSpace.ControlGap),",
            token = "ControlGap = 4",
            why = "2 dp est une correction optique, pas un écart de contrôle : entre deux cibles tactiles, c'est 4 dp.",
        ),
        SpacingFix(
            where = "Barre des cinq onglets — rembourrage (barre asymétrique)",
            file = "core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt",
            before = "bottom = 6.dp,",
            after = "bottom = OnyxSpace.BarPadding,",
            token = "BarPadding = 4",
            why = "Le bas de la barre était à 6 dp et le haut à 4 : la barre n'était pas symétrique, et son contenu se lisait plus haut que bas.",
        ),
        SpacingFix(
            where = "Feuille du bas (ajout) — hauteur de rangée",
            file = "core/src/main/java/com/onyx/app/ui/design/AttachmentSheet.kt",
            before = "internal val SheetRowHeight = 56.dp",
            after = "internal val SheetRowHeight = OnyxSpace.RowHeight",
            token = "RowHeight = 56",
            why = "Déjà 56 (la cible tactile), mais anonyme : nommée, la même hauteur sert à la rangée de menu, à la rangée de liste et à la ligne de feuille.",
        ),
        SpacingFix(
            where = "Feuille du bas (ajout) — écart icône-libellé",
            file = "core/src/main/java/com/onyx/app/ui/design/AttachmentSheet.kt",
            before = "private val SheetLabelGap = 12.dp",
            after = "private val SheetLabelGap = OnyxSpace.ThumbnailGap",
            token = "IconLabelGap = 8",
            why = "La feuille écartait la pastille de son libellé de 12 dp là où la barre d'onglets le faisait de 2, la tuile de 9 et le menu de 12 : quatre valeurs pour un seul geste de lecture.",
        ),
        SpacingFix(
            where = "Feuille du bas (ajout) — rembourrage des lignes",
            file = "core/src/main/java/com/onyx/app/ui/design/AttachmentSheet.kt",
            before = "private val SheetRowInset = 14.dp",
            after = "private val SheetRowInset = OnyxSpace.CardPadding",
            token = "CardPadding = 16",
            why = "14 dp n'était ni la marge d'écran (16) ni l'écart entre blocs (12) : une troisième valeur pour la même intention. La feuille prend la marge de carte.",
        ),
        SpacingFix(
            where = "Menu ancré — écart icône-libellé",
            file = "core/src/main/java/com/onyx/app/ui/design/OnyxMenu.kt",
            before = "private val MenuIconGap = 12.dp",
            after = "private val MenuIconGap = OnyxSpace.IconLabelGap",
            token = "IconLabelGap = 8",
            why = "12 dp ici, 14 dans la feuille, 2 dans la barre d'onglets : trois distances pour la même lecture. C'est le défaut nommé par le propriétaire (« l'écart entre l'icône et son libellé »).",
        ),
        SpacingFix(
            where = "Menu ancré — rembourrage d'une ligne",
            file = "core/src/main/java/com/onyx/app/ui/design/OnyxMenu.kt",
            before = "private val MenuRowInset = 14.dp",
            after = "private val MenuRowInset = OnyxSpace.CardPadding",
            token = "CardPadding = 16",
            why = "14 dp n'était ni 12 ni 16 : le panneau d'un menu et celui d'une feuille n'avaient pas le même retrait, alors qu'ils se suivent à l'écran.",
        ),
        SpacingFix(
            where = "Menu ancré — hauteur d'une ligne",
            file = "core/src/main/java/com/onyx/app/ui/design/OnyxMenu.kt",
            before = "private val MenuRowHeight = 44.dp",
            after = "private val MenuRowHeight = OnyxSpace.RowHeight",
            token = "RowHeight = 56",
            why = "44 dp sous la cible tactile de 48 : alignée sur la rangée de feuille, la ligne d'un menu ne peut plus être la seule trop courte de l'app.",
        ),
        SpacingFix(
            where = "Menu ancré — rembourrage vertical d'une ligne",
            file = "core/src/main/java/com/onyx/app/ui/design/OnyxMenu.kt",
            before = "vertical = 11.dp),",
            after = "vertical = OnyxSpace.Snug),",
            token = "Snug = 12",
            why = "11 dp faisait 44 dp de haut hors texte ; avec 12, la ligne d'un menu a la même hauteur optique qu'une ligne de feuille.",
        ),
        SpacingFix(
            where = "Alerte héritée — le panneau de la maison remplace la boîte du système",
            file = "core/src/main/java/com/onyx/app/ui/design/OnyxAlert.kt",
            before = "OnyxRowDivider(inset = 0.dp)",
            after = "OnyxConfirmSurface {",
            token = "CardPadding = 16",
            why = "Le panneau de l'alerte n'est plus dessiné dans ce fichier : il vient de [OnyxConfirmSurface], " +
                "le panneau de la maison. Les lignes de séparation internes du système et le texte nu bord à bord " +
                "partent avec — c'était le reproche du propriétaire (« pas cohérente avec le reste du thème, " +
                "les boutons collent trop au bord »).",
        ),
        SpacingFix(
            where = "Feuille du bas (actions) — rembourrage de l'en-tête",
            file = "core/src/main/java/com/onyx/app/ui/design/HouseSheet.kt",
            before = "modifier = Modifier.fillMaxWidth().padding(horizontal = SheetRowInset, vertical = 2.dp),",
            after = "top = OnyxSpace.Hair, bottom = OnyxSpace.BlockGap),",
            token = "BarInset = 16 / BlockGap = 12",
            why = "L'en-tête des deux feuilles a été fusionné dans le **gabarit unique** (`HouseSheetHeader`) : le rembourrage ne peut plus diverger d'une feuille à l'autre, puisqu'il n'est plus écrit qu'une fois.",
        ),
        SpacingFix(
            where = "Liste de réglages — rangée",
            file = "core/src/main/java/com/onyx/app/ui/design/OnyxForm.kt",
            before = ".padding(horizontal = 16.dp, vertical = 11.dp),",
            after = ".padding(horizontal = OnyxSpace.CardPadding, vertical = OnyxSpace.Snug),",
            token = "Snug = 12",
            why = "La rangée de liste d'un formulaire et celle d'une feuille n'avaient pas la même hauteur (11 contre 12 dp de rembourrage vertical) pour la même cible tactile.",
        ),
        SpacingFix(
            where = "Contrôle segmenté — rembourrage de la capsule",
            file = "core/src/main/java/com/onyx/app/ui/design/OnyxForm.kt",
            before = ".padding(3.dp),",
            after = ".padding(OnyxSpace.Hair),",
            token = "Hair = 4",
            why = "3 dp pour la pastille dans sa capsule ; 4 dp est la plus petite marche qui reste une marche.",
        ),
        SpacingFix(
            where = "Titre de section — rembourrage",
            file = "core/src/main/java/com/onyx/app/ui/design/OnyxForm.kt",
            before = ".padding(horizontal = 16.dp, vertical = 10.dp),",
            after = ".padding(horizontal = OnyxSpace.CardPadding, vertical = OnyxSpace.BlockGap),",
            token = "BlockGap = 12",
            why = "10 dp n'existe sur aucune marche : le titre de page prend l'écart de bloc, en haut comme en bas.",
        ),
        SpacingFix(
            where = "Volet latéral — tête du volet",
            file = "app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt",
            before = ".padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 8.dp)",
            after = "top = OnyxSocle.ScreenMargin,",
            token = "BarInset = 16 / Half = 8",
            why = "10 dp en haut était une valeur unique dans l'app. Les barres du volet prennent l'écart de barre, en haut comme en bas.",
        ),
        SpacingFix(
            where = "Volet latéral — grille de tuiles",
            file = "app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt",
            before = "modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),",
            after = "end = OnyxSpace.Snug,",
            token = "Snug = 12",
            why = "10 dp autour de la grille de tuiles, alors que la recherche au-dessus gardait 12 : la grille n'était pas alignée sur l'élément qu'elle suit.",
        ),
        SpacingFix(
            where = "Volet latéral — tuile (icône et libellé)",
            file = "app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt",
            before = "horizontalArrangement = Arrangement.spacedBy(9.dp),",
            after = "horizontalArrangement = Arrangement.spacedBy(OnyxSpace.IconLabelGap),",
            token = "IconLabelGap = 8",
            why = "9 dp dans la tuile, 2 dans l'onglet, 12 dans le menu : le même jeton les remplace tous les trois — c'est le point qui se voit le plus sur une capture.",
        ),
        SpacingFix(
            where = "Volet latéral — capsule de recherche",
            file = "app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt",
            before = ".padding(horizontal = 14.dp),",
            after = ".padding(horizontal = OnyxSpace.CardPadding),",
            token = "CardPadding = 16",
            why = "14 dp à l'intérieur de la capsule, 12 à l'extérieur : deux valeurs pour un seul creux. La capsule garde sa marge d'écran et prend le rembourrage de carte à l'intérieur.",
        ),
        SpacingFix(
            where = "Carte de bot — rembourrage",
            file = "app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebarBots.kt",
            before = ".padding(12.dp),",
            after = ".padding(OnyxSpace.CardPadding),",
            token = "CardPadding = 16",
            why = "La carte de bot était la seule carte de l'app à 12 dp : à côté d'une rangée de conversation, son texte paraissait collé à ses bords.",
        ),
        SpacingFix(
            where = "Carte de bot — grille",
            file = "app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebarBots.kt",
            before = "contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 12.dp),",
            after = "bottom = OnyxSpace.BlockGap,",
            token = "Snug = 12",
            why = "L'onglet Bots doit avoir la même marge que l'onglet Chats, sinon la grille saute quand on change d'onglet.",
        ),
        SpacingFix(
            where = "Bloc de réponse — la carte",
            file = "app/src/main/java/com/onyx/app/ui/chat/components/ReplyCardViews.kt",
            before = ".background(theme.surfaceColor)",
            after = ".liquidGlass(GlassSurface.RESPONSE_BLOCK)",
            token = "CardPadding = 16",
            why = "La carte d'une réponse portait un aplat de surface cerné d'un filet de 0,5 dp : elle devient une vitre, avec le rembourrage de carte et l'écart de carte de la maison.",
        ),
    )
}
