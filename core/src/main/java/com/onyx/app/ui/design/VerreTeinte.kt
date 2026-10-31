package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color

/**
 * **LE VERRE DES PLAQUES EST FRANCHEMENT TEINTÉ — décision explicite du propriétaire.**
 *
 * Ce fichier porte **la seule couleur écrite en dur de la couche de dessin**, et il est pour cela
 * dispensé dans la liste blanche de `ReferencePaletteLockTest` — avec sa raison, comme le veut cette
 * liste. Il est à part de `LiquidGlass.kt` pour que la dispense ne couvre **que** la teinte du verre,
 * et rien d'autre de la peinture des vitres.
 *
 * ---
 *
 * **ÉCART ASSUMÉ À LA PLANCHE — NE PAS « CORRIGER » CE POINT.**
 *
 * La planche donne un verre clair **de la couleur de la page** : c'est `GlassTintLight` (blanc 20 %),
 * et son détachement avec la page est faible — relevé du lot « verre-fond », au repos, à l'appareil :
 * **1,238:1**. Le propriétaire a regardé ce rendu **en capture** et l'a refusé, mot pour mot : *trop
 * subtil*. Il a demandé un verre **franchement teinté**, **en sachant que cela l'écarte de sa
 * planche**.
 *
 * C'est donc un **choix du propriétaire** : pas un défaut, pas un accident, pas une régression de
 * parité. Un lot futur qui « remettrait le verre clair de la planche » annulerait une décision
 * explicite. `GlassTintLight` (blanc 20 %) reste la teinte des **autres** surfaces de verre — cartes,
 * tuiles, volet : elle n'a pas bougé. Ce sont les **plaques** ([GlassSurface.plateEdge] : l'en-tête,
 * le compositeur, la barre d'onglets) qui portent ce verre teinté, et **les trois à la fois** (même
 * teinte, même densité — c'est le point « elles restent cohérentes entre elles »).
 *
 * **Comment la densité est décidée.** La teinte est une **couleur** ; son opacité est le **plancher de
 * lisibilité** de la maison — la plus petite densité qui tient le seuil d'encre
 * ([OnyxContrast.TEXT_MIN], **4,5:1**) **contre le fond réellement derrière la plaque**
 * ([glassPlateBackdrops], échantillonné à l'appareil). Aucun seuil d'encre n'est abaissé : c'est la
 * même fonction qui décide, et `VerrePlaquesVerrouTest` la relit.
 *
 * **L'ancienne borne, écrite ici parce qu'un verrou re-pointé ne s'efface pas.** Jusqu'au lot
 * « liquid glass », la densité venait de [glassLegibilityFloor], qui mesure l'encre contre **du blanc
 * pur ET du noir pur** — le pire cas d'une vitre qu'on ne sait pas où poser. Sur du noir pur, l'encre
 * sombre de la maison ne tient 4,5:1 qu'à partir de **0,82** de teinte : la plaque ne laissait passer
 * que **18 %** du fond, et l'en-tête portait en plus un aplat opaque **sous** sa vitre. Le
 * propriétaire a refusé le résultat à l'œil (« normalement il doit y avoir l'effet liquid glass, la
 * c'est pas le cas »). Le noir pur **n'est jamais** derrière ces plaques : la règle garde son seuil et
 * change de fond. La fonction d'avant reste dans `LiquidGlass.kt` ([glassLegibilityFloor]) pour toutes
 * les autres surfaces, et la nouvelle est [glassLegibilityFloorOver].
 *
 * **Conséquence de la nouvelle borne, chiffrée** : en clair la densité passe de **0,82 à 0,55** (18 %
 * → **45 %** de traversée), en sombre de **0,76 à 0,44** (24 % → **56 %**). Le détachement corps/page
 * du verre clair, lui, descend de 1,408:1 à **1,275:1** : c'est l'arbitrage de ce lot, écrit en entier
 * au-dessus de [GlassPlateBackdropsLight].
 *
 * **L'échelle des candidats** (mesurée par `VerrePlaquesVerrouTest`, apparence claire, page relevée
 * `#D3DCD7`) — le niveau retenu est [GlassPlateTintLevelRetained]. **Les densités de ce tableau ont été
 * re-mesurées par le lot « liquid glass »** : elles viennent du fond **réellement** derrière la plaque
 * ([glassPlateBackdrops]), plus des deux aplats extrêmes. La colonne « traversée » est celle que le
 * propriétaire voit — c'est elle qui manquait.
 *
 * | rang | teinte      | densité (plancher d'encre) | traversée | corps de plaque | détachement corps/page | pire encre |
 * |---|---|---|---|---|---|---|
 * | 0 | `#C0C8CC` | 0,43 | 57 % | `#CED4D1` | 1,092:1 | 4,69:1 |
 * | 1 | `#B8C0C4` | 0,47 | 53 % | `#C9CFCD` | 1,143:1 | 4,68:1 |
 * | **2** | **`#A9B1B5`** | **0,57** | **43 %** | **`#BEC4C3`** | **1,287:1** | 4,68:1 |
 * | 3 | `#9FA7AB` | 0,66 | 34 % | `#B3B9B9` | 1,441:1 | 4,66:1 |
 * | 4 | `#969EA2` | 0,78 | 22 % | `#A5ACAD` | 1,678:1 | 4,67:1 |
 * | 5 | `#8D9599` | 0,93 | 7 % | `#929A9D` | 2,078:1 | 4,67:1 |
 *
 * Les rangs 0 et 1 sont **sous** la cible : ce sont le verre de la planche et son voisin immédiat —
 * le rendu que le propriétaire a refusé. Ils restent dans l'échelle pour que la preuve porte les
 * candidats refusés à côté des candidats retenus, et pas seulement le gagnant.
 */
val GlassPlateTintCandidates: List<Color> = listOf(
    Color(0xFFC0C8CC), // rang 0 — 1,149:1
    Color(0xFFB8C0C4), // rang 1 — 1,230:1 (≈ l'état du lot « verre-fond »)
    Color(0xFFA9B1B5), // rang 2 — 1,428:1  ← RETENU
    Color(0xFF9FA7AB), // rang 3 — 1,613:1
    Color(0xFF969EA2), // rang 4 — 1,832:1
    Color(0xFF8D9599), // rang 5 — 2,137:1
)

/** Le rang, **dans [GlassPlateTintCandidates]**, de la teinte retenue (2 = `#A9B1B5`). */
const val GlassPlateTintLevelRetained: Int = 2

/**
 * **Le niveau de teinte forcé par la passe de captures** — `null` en production, exactement comme
 * `GlassTierForcedForEvidence`. Il sert à photographier **l'échelle entière** (les six candidats) au
 * repos depuis **un seul** APK, sans rebâtir six fois : le niveau retenu reste le défaut. Il vient du
 * lancement de l'app (`MainActivity`, extra `onyx.verreTeinte`) et **aucun écran ne le pose** : ce
 * que l'app peint et ce que la table déclare ne peuvent donc pas diverger en fonctionnement normal.
 */
var GlassPlateTintLevelForEvidence: Int? = null

/** La teinte du verre clair des plaques : le niveau retenu, ou celui de la preuve. */
val GlassPlateTintLight: Color
    get() = GlassPlateTintCandidates[
        (GlassPlateTintLevelForEvidence ?: GlassPlateTintLevelRetained)
            .coerceIn(0, GlassPlateTintCandidates.lastIndex),
    ]

// ---------------------------------------------------------------------------------------------
// LE FOND RÉELLEMENT DERRIÈRE LES PLAQUES — échantillonné, et c'est lui qui décide de la densité
// ---------------------------------------------------------------------------------------------

/**
 * **LE FOND RÉELLEMENT DERRIÈRE LES PLAQUES — MESURÉ, et c'est lui qui décide de la densité.**
 *
 * Le lot « verre-teinte » avait retenu une densité de **0,82**, et il avait raison **dans sa règle** :
 * il mesurait l'encre contre **du blanc pur et du noir pur** ([glassLegibilityFloor]) — le pire cas
 * d'une vitre qu'on ne sait pas où poser. Sur du **noir pur**, l'encre sombre de la maison
 * (`#0A0A0C`) ne tient 4,5:1 qu'à partir de **0,82** de teinte : la plaque ne laissait donc passer que
 * **18 %** du fond, et le propriétaire a tranché le résultat à l'œil, capture à l'appui :
 *
 * > « normalement il doit y avoir l'effet liquid glass, la c'est pas le cas »
 *
 * **La règle n'est pas abaissée : le fond contre lequel on mesure est remplacé.** Le seuil reste
 * `OnyxContrast.TEXT_MIN` (4,5:1) et la marge reste `LegibilityMargin`. Ce qui change est que l'encre
 * d'une **plaque** est mesurée contre les couleurs qui sont **réellement derrière elle** — celles-ci,
 * échantillonnées à l'appareil — au lieu d'un noir pur qui n'y passe jamais.
 *
 * **Ce qui a été échantillonné, et comment** (le 31/10, `emulator-5562`, AVD `verreliquid`, rendu
 * **matériel** `-gpu host` — NVIDIA RTX 4070 SUPER, journal `emuglConfig_init: vulkan_mode_selected:host` ;
 * APK `1.0.0+1012`, sha256 `f51b2690…` ; fil de la pose `qa/20261031-liquid-glass/pose.py`) :
 *
 *  - la sonde force le voile des plaques à **0** (`onyx.verreVoile 0`) et lit la bande de chaque
 *    plaque à **trois positions de défilement** (bas du fil, puis deux remontées) : la moyenne par
 *    **blocs de 64 px** dilue le texte de la plaque (des fûts de 2 à 4 px) et laisse le **fond, flou
 *    compris** ;
 *  - captures : `qa/20261031-liquid-glass/shots/sonde-v0-{clair,sombre}-{bas,haut1,haut2}.png`,
 *    bande du bas `(42,1945)-(1038,2312)`, capsule d'en-tête `(42,78)-(1038,205)` ;
 *  - **ce qui est listé est le pire jeu relevé** : le bloc le plus sombre et le plus clair de chaque
 *    bande, apparence par apparence. **Aucune valeur n'est estimée** — et une couleur qui n'a pas été
 *    observée derrière une plaque n'y figure pas : c'est ce qui distingue cette liste d'une liste de
 *    « fonds possibles ».
 *
 * **Ce que la mesure change** (mêmes seuils, même fonction, même marge) :
 *
 * | apparence | ancienne borne (blanc + noir purs) | fond mesuré | densité | traversée |
 * |---|---|---|---|---|
 * | clair  | 0,82 (18 % de traversée) | pire fond `#7850E6` (L=0,154) | **0,57** | **43 %** |
 * | sombre | 0,76 (24 % de traversée) | pire fond `#F0DC28` (L=0,699) | **0,70** | **30 %** |
 *
 * **Ce que la mesure inclut, et ce qu'elle n'inclut pas.** Les deux listes portent les **blocs
 * relevés** derrière les plaques **et** les **bandes des images de la pose** ([PoseImageBands] : les
 * images du fil, dont la couleur est un fait du contenu, pas une invention). Ce qui n'y est **pas** :
 * les valeurs d'un pixel isolé ou d'un filet (un liseré blanc de 3 px, le fond d'une image visible sur
 * 4 px) — ce ne sont pas des **surfaces** derrière la plaque, et un flou de 36 à 44 dp en fait une
 * tache négligeable. La réserve est plus bas : sur un **grand aplat** noir (clair) ou blanc (sombre)
 * qui passerait sous une plaque, l'encre descendrait sous 4,5:1 — le levier est alors une **zone
 * d'encre protégée** (dégradé local sous le texte), **non implémentée dans ce lot** et proposée telle
 * quelle dans le rapport.
 *
 * **L'arbitrage, écrit noir sur blanc.** Descendre la densité fait forcément baisser le détachement
 * corps/page du verre clair : il passe de **1,408:1** à **1,287:1** (mesuré, corps `#BEC4C3` sur page
 * `#D9DDD5`). C'est le seul point où deux décisions du propriétaire tirent en sens inverse — « le verre
 * doit être franchement teinté » (lot « verre-teinte ») et « il doit y avoir l'effet liquid glass »
 * (ce lot) — et c'est la **traversée** qui a été retenue, parce que c'est la réclamation en cours et
 * que la densité était le seul levier qui la tuait. La **teinte** (`GlassPlateTintLight`, rang 2 de
 * l'échelle) est inchangée : c'est la couleur de la décision, pas sa densité. Le seuil de détachement
 * du verrou passe donc de 1,40 à **1,25**, et il reste **au-dessus du verre de la planche** (1,238:1)
 * que le propriétaire avait refusé. Le chiffre est dans `VerrePlaquesVerrouTest`, avec la même raison.
 */

/**
 * **Les bandes des images du fil** — le contenu coloré de la pose (`qa/20261031-liquid-glass/pose.py`,
 * `make_matiere`). Ce sont de **grandes surfaces** (88 px de haut chacune à l'écran), donc elles
 * passent derrière les plaques pendant un défilement, et leurs couleurs sont un fait du contenu, pas
 * une estimation. En apparence **claire** la plus sombre (`#7850E6`, un violet, L=0,154) est du même
 * ordre que le fond mesuré le plus sombre ; en apparence **sombre** la plus claire (`#F0DC28`, un
 * jaune, L=0,699) est la borne de l'encre claire — et c'est elle qui fixe la densité du sombre.
 */
val PoseImageBands: List<Color> = listOf(
    Color(0xFFE63C3C), // rouge
    Color(0xFFF09628), // orangé
    Color(0xFFF0DC28), // jaune  ← la borne du sombre
    Color(0xFF3CC85A), // vert
    Color(0xFF32A0E6), // bleu clair
    Color(0xFF7850E6), // violet ← la borne du clair (la plus sombre)
    Color(0xFFE646BE), // magenta
)

val GlassPlateBackdropsLight: List<Color> = listOf(
    Color(0xFFD9DDD5), // la page claire, bloc modal (L=0,699)
    Color(0xFFA2ACAB), // le fond du fil derrière la plaque du bas (L=0,395)
    Color(0xFFE4E7E7), // le fond le plus CLAIR derrière la plaque du bas (L=0,799)
    Color(0xFFB8C3E0), // le fond le plus CLAIR derrière l'en-tête (L=0,551)
    Color(0xFF9BA4A4), // un gris du fil (L=0,360)
    Color(0xFF767679), // le fond le plus SOMBRE derrière la plaque du bas (L=0,182)
    Color(0xFF5B689A), // le fond le plus SOMBRE derrière l'en-tête (L=0,145)
    Color(0xFF3E75F4), // la bulle de l'utilisateur (bleu de la maison) derrière la plaque
    Color(0xFF4578E9), // la même bulle, un cran plus clair
) + PoseImageBands

/**
 * Le même jeu en apparence **sombre**, relevé sur les mêmes six captures `sonde-v0-sombre-*` : la page
 * noire, les surfaces du fil, et le **bleu de la bulle** — c'est le fond le plus **clair** qui a été
 * observé là, et c'est lui qui contraint l'encre claire (avec les bandes d'image, voir [PoseImageBands]).
 */
val GlassPlateBackdropsDark: List<Color> = listOf(
    Color(0xFF0A0A0C), // la page sombre de la maison (L=0,003)
    Color(0xFF070605), // le fond le plus SOMBRE derrière la plaque du bas (L=0,002)
    Color(0xFF1D1E1E), // le fond sombre derrière la plaque du bas, seconde position (L=0,013)
    Color(0xFF26262B), // la surface d'une réponse (dans la plage mesurée)
    Color(0xFF192A5F), // le fond de l'en-tête, position « bas » (L=0,027)
    Color(0xFF4F64A6), // le fond de l'en-tête, le plus clair des positions hautes (L=0,136)
    Color(0xFF5D81D6), // la bulle/le bleu derrière la plaque du bas (L=0,229)
    Color(0xFF5F83D8), // le fond le plus CLAIR relevé derrière la plaque du bas (L=0,236)
) + PoseImageBands

/** Le fond d'une plaque, dans l'apparence demandée. */
fun glassPlateBackdrops(dark: Boolean): List<Color> =
    if (dark) GlassPlateBackdropsDark else GlassPlateBackdropsLight

/**
 * **LE SEUIL DE TRAVERSÉE D'UNE PLAQUE** : la plus petite part du fond qui doit passer au travers.
 *
 * Il n'est pas décoratif : c'est le chiffre qui distingue une **vitre** d'une **carte**. Le défaut du
 * propriétaire tenait à **0,18** de traversée (densité 0,82 sur la borne du noir pur) — la plaque se
 * lisait comme une carte posée sur la page, et l'en-tête portait en plus un aplat opaque **sous** sa
 * vitre.
 *
 * **Le seuil vient de la mesure** : avec le fond échantillonné derrière les plaques et les bandes des
 * images du fil ([GlassPlateBackdropsLight] / [GlassPlateBackdropsDark] / [PoseImageBands]), la même
 * borne d'encre donne **0,57** de densité (43 % de traversée) en clair et **0,70** (30 %) en sombre.
 * Le seuil est posé deux points **sous** la plus basse des deux : un lot qui remonterait la densité
 * au-delà de ce que la mesure exige fait échouer le verrou, en nommant la surface et la valeur.
 */
const val GlassPlateTraversalMin: Float = 0.28f
