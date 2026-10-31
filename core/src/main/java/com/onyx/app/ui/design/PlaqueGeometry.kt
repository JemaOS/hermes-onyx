package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * **La géométrie de la plaque du bas** — le compositeur **et** la rangée d'onglets dans **une seule**
 * surface translucide.
 *
 * Les six chiffres viennent de la planche, pas d'un choix : `hcss.py:226` (`.plaque` : rayon 28,
 * ombre en trois profondeurs), `hlib.py:491` (la plaque `slab` : 379 × 140 à 16 de la marge, avec
 * ses deux rangées), `hcss.py:98-102` (l'arête claire de 1,5 px en haut, le filet de 1 px tout
 * autour, le grain à 7,5 %).
 *
 * Pourquoi une table, et pas des valeurs posées dans l'écran. La hauteur de la plaque **est** la
 * réserve que le fil lui laisse : deux valeurs écrites à deux endroits divergent au premier
 * changement, et c'est exactement ce qui produisait les 166 dp mesurés pour 140 visés. La somme est
 * donc écrite **une fois**, ici ([Height]), et l'écran comme le test la relisent.
 *
 * La plaque a **deux étages**, pas trois : la planche met le champ, ses commandes et l'envoi sur
 * **une** ligne (`.ask`, 64 px) et la rangée d'onglets dessous (`.navrow`, 76 px). Le filet de
 * séparation tombe sur leur frontière ([Separator]) : il ne prend pas de place à lui, sans quoi la
 * somme ferait 141 et la plaque ne serait plus à 140.
 */
object PlaqueGeometry {

    /** La rangée du champ : « + », le champ, le micro, l'envoi — sur **une** ligne. `hlib.py:491`. */
    val FieldRowHeight: Dp = 64.dp

    /** La rangée des onglets, sous le filet. `hlib.py:493` (`.navrow`). */
    val TabRowHeight: Dp = 76.dp

    /**
     * **Le filet de séparation interne**, entre le champ et les onglets : 1 px dans la planche
     * (`.ask{border-bottom:1px solid var(--line)}`). Il est **peint** sur la frontière des deux
     * rangées, il ne les décale pas.
     */
    val Separator: Dp = OnyxSpace.Stroke

    /** La hauteur de la plaque au repos : **140 dp** (64 + 76), la cible de la planche. */
    val Height: Dp = FieldRowHeight + TabRowHeight

    /** Le rayon de la plaque : celui de la famille des panneaux (28). `hcss.py:226`. */
    val Radius: Dp = OnyxSocle.RadiusPanel

    /** La marge de la plaque : la marge d'écran de la maison (16), à gauche comme à droite. */
    val SideMargin: Dp = OnyxSocle.ScreenMargin

    /** Le filet extérieur, tout autour : 1 dp. `hcss.py:99` (`--gedge`). */
    val Outline: Dp = OnyxSpace.Stroke

    /**
     * **L'arête claire du haut** : 1,5 px de blanc à 98 % en clair, 62 % en sombre
     * (`hcss.py:132` / `:144`, `--gedge`). C'est elle qui fait lire la plaque **posée** plutôt que
     * découpée — un aplat n'a pas d'arête.
     */
    val TopEdge: Dp = 1.5.dp

    /** L'opacité de cette arête en clair, telle que la planche la déclare. */
    const val TopEdgeAlphaLight: Float = 0.98f

    /** …et en sombre : la même arête, moins lumineuse, parce que la page l'est déjà peu. */
    const val TopEdgeAlphaDark: Float = 0.62f

    /** Le pas du grain du verre, dans la planche : `background-size:120px 120px`. */
    const val GrainTilePx: Int = 120

    /** Son opacité : 7,5 %. `hcss.py:102`. */
    const val Grain: Float = 0.075f

    /**
     * **La hauteur de la pastille « aller au dernier message »** : la cible du pouce de la maison
     * (`ThumbnailSize`, 44 dp). C'est elle, et elle seule, que la voie flottante a désormais à loger.
     */
    val PillHeight: Dp = OnyxSpace.ThumbnailSize

    /** L'écart entre la plaque et la voie flottante : la marche `Snug` (12). */
    val FloatClearance: Dp = OnyxSpace.Snug

    /**
     * **La voie des commandes flottantes** : la bande réservée au-dessus de la plaque, en dp.
     *
     * Elle logeait **deux** commandes — la poignée à chevron de la barre et la pastille « aller au
     * dernier message » — et réservait pour cela la hauteur d'une cible (44) **plus** un écart (12) :
     * 56 dp, auxquels l'écran ajoutait son dégagement, soit 68. La poignée ayant été retirée de
     * l'écran sur l'ordre du propriétaire, la voie se ramène à ce que la pastille occupe
     * réellement : son dégagement ([FloatClearance]) plus sa hauteur ([PillHeight]) — 56 dp.
     *
     * **La règle qui compte est tenue** : le fil ne traverse jamais cette bande, donc la pastille ne
     * peut pas se poser sur du texte. Les 12 dp rendus allaient au contenu.
     */
    val FloatLane: Dp = FloatClearance + PillHeight

    /**
     * **La cellule d'un repère de la barre.** C'est la hauteur du visage de la planche
     * (`.capface`, 44 px) — et c'est aussi la cible tactile de la maison (`ThumbnailSize`), donc la
     * même valeur sert les deux. Les repères à icône ont la **même** cellule : leurs libellés
     * tombent ainsi au même
     * niveau que celui du visage, ce que la planche elle-même ne faisait pas (relevé d'audit :
     * « repères de barre à des centres différents : navi nav-face »).
     */
    val TabCell: Dp = OnyxSpace.ThumbnailSize

    /**
     * **La hauteur de la cellule d'un repère de la rangée d'onglets** : la planche donne à `.navi`
     * `height:60px` (`hcss.py:229`), dans un `.navrow` de **76 px** (`hcss.py:227`). La cellule est
     * donc **contenue** dans la rangée, avec **8 px de dégagement de chaque côté** — et c'est ce
     * dégagement qui manquait : posée sur une cellule qui montait jusqu'au filet, la pastille de
     * l'onglet actif **touchait le bord supérieur de la rangée** (« la pastille déborde sur la
     * ligne, c'est même pas centré »). La cellule est désormais un **plancher de hauteur** : elle
     * grandit avec la police (1,3×) mais jamais au point de franchir ses 76 dp.
     */
    val TabCellHeight: Dp = 60.dp

    /**
     * **Le dégagement de la pastille dans la rangée** : `(76 − 60) / 2 = 8 dp` — la moitié de
     * l'écart entre `.navrow` et `.navi`. C'est lui qui fait « contenue et centrée » : le même
     * blanc au-dessus et au-dessous de la pastille, et jamais un bord franchi.
     *
     * Il n'est pas posé à la main sur la cellule : c'est le **centrage de la rangée**
     * (`ChatScreen`, la colonne de 76 dp) qui le produit, quelle que soit la hauteur réelle de la
     * cellule. Le chiffre de la planche reste la cible, et le verrou l'exige `≥ 4 dp`.
     */
    val TabPillClearance: Dp = (TabRowHeight - TabCellHeight).value.let { (it / 2f).dp }

    /** L'anneau clair du repère-visage : 2,5 px de blanc à 90 %. `hlib.py:493` (`.capface`). */
    val FaceRing: Dp = 2.5.dp

    /** L'opacité de cet anneau, telle que la planche la déclare. */
    const val FaceRingAlpha: Float = 0.9f

    // -----------------------------------------------------------------------------------------
    // La rangée du champ : la maquette la donne au pixel, il n'y a donc rien à choisir ici.
    // -----------------------------------------------------------------------------------------

    /**
     * **La marche entre les commandes de la rangée du champ** : la planche dit `gap:10px`
     * (`.ask{gap:10px}`).
     *
     * Elle vaut ici ce que la planche écrit, et pas la marche `Snug` (12) du reste de l'app : la
     * plaque porte **quatre** commandes dans la largeur d'un téléphone, et ces 2 dp par écart sont
     * exactement ce qui permet au libellé du champ de tenir entier. La règle tactile de la maison
     * reste tenue : avec des cibles de 44 dp, le système déborde de 2 dp par côté, donc 4 dp
     * suffisent ([com.onyx.app.ui.chat.components.ComposerRowGeometry.targetsClear]).
     */
    val FieldGap: Dp = 10.dp

    /**
     * **Le rembourrage horizontal du champ sur la plaque** : la planche (`.askf{padding:0 4px}`).
     * C'est lui qui donne au libellé les 16 dp de plus que le rembourrage `12` de la carte.
     */
    val FieldInset: Dp = 4.dp

    /**
     * **Le côté du bouton d'envoi** : la planche en fait 48 px (`.csend{width:48px}`).
     *
     * Sur la plaque, l'envoi est donc **un peu plus grand** que les trois autres commandes (44 dp) :
     * c'est l'action principale de l'écran, et la planche la dessine ainsi.
     */
    val SendSlot: Dp = 48.dp

    // -----------------------------------------------------------------------------------------
    // La pastille de l'onglet actif — `--pill` de la planche
    // -----------------------------------------------------------------------------------------

    /**
     * **La pastille de l'onglet actif** : `rgba(255,255,255,.74)` en clair, `rgba(255,255,255,.17)`
     * en sombre (`hcss.py:134` / `:146`).
     *
     * La planche la peint (`fil-clair.html:110` : `.navi.on{background:var(--pill)}`) — c'est le
     * seul endroit où le brief et la planche se contredisaient, et la planche gagne : l'onglet actif
     * est **posé sur un ovale plus clair**, il ne se distingue pas seulement par l'encre.
     */
    const val ActivePillLightAlpha: Float = 0.74f

    /** …et en sombre : le même blanc, moins lumineux, parce que la page l'est déjà peu. */
    const val ActivePillDarkAlpha: Float = 0.17f

    /**
     * Le **filet de la pastille** : `inset 0 0 0 1px` — `rgba(255,255,255,.92)` en clair,
     * `.24` en sombre (`hcss.py:135` / `:147`). C'est lui qui détache l'ovale de la vitre.
     */
    const val ActivePillLineLightAlpha: Float = 0.92f

    /** …et en sombre. */
    const val ActivePillLineDarkAlpha: Float = 0.24f

    /** Le fond de la pastille, dans le thème courant : un blanc composé sur la vitre. */
    fun activePill(theme: ResolvedTheme): Color =
        Color.White.copy(alpha = if (theme.dark) ActivePillDarkAlpha else ActivePillLightAlpha)

    /** Le filet de la pastille, dans le thème courant. */
    fun activePillLine(theme: ResolvedTheme): Color =
        Color.White.copy(alpha = if (theme.dark) ActivePillLineDarkAlpha else ActivePillLineLightAlpha)
}

/**
 * **Le libellé du champ de la plaque** — « **Écrire à Onyx…** », mot pour mot celui de la planche
 * (`fil-clair.html:479` : `<span class="ph">Écrire à Onyx…</span>`).
 *
 * L'app montrait sa propre clé traduite (« Demander à Onyx »). Le libellé de la planche est donc
 * ajouté au dictionnaire central, et c'est lui que la plaque porte.
 *
 * **Pourquoi deux clés, et pas une.** La planche met **quatre** commandes dans la rangée du champ
 * (`+`, le micro, le mode vocal, l'envoi) : le champ ne garde alors que **127,4 dp** de texte à
 * 1,3× de police, quand « Écrire à Onyx… » en demande ≈ 130. Le brief a prévu ce cas : « si ça ne
 * tient réellement pas à 1,3× de police, réduis la longueur du texte d'invite plutôt que de
 * supprimer un bouton, en le disant ». C'est donc **l'ellipse** qui tombe (13,8 dp), et jamais une
 * commande : le choix est **mesuré**, pas écrit ici — [key] reçoit la largeur réellement offerte par
 * le champ et la mesure du texte sous la police rendue, et rend la clé qui tient.
 */
object PlaqueInvite {

    /** Le libellé de la planche, mot pour mot. */
    const val FULL: String = "Write to Onyx…"

    /** Le même, **sans l'ellipse** : ce que la rangée porte quand la place manque. */
    const val SHORT: String = "Write to Onyx"

    /**
     * La clé à employer : [FULL] si elle tient dans [roomDp], [SHORT] sinon.
     *
     * [width] est la largeur mesurée du libellé sous la police **réellement rendue** — la même
     * mesure que celle qui décide des libellés d'onglet ([TabLabelFit]). Une place nulle ou une
     * mesure nulle ne fait pas disparaître le libellé : on rend alors le plus court, jamais rien.
     */
    fun key(roomDp: Float, width: (String) -> Float): String =
        if (roomDp > 0f && width(FULL) <= roomDp) FULL else SHORT
}
