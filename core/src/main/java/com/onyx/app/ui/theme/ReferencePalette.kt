package com.onyx.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * **La palette de la référence, relevée dans sa source — pas choisie à l'œil.**
 *
 * Le lot « uniformité » précédent n'avait remplacé que des *valeurs* (136 rayons en dur → des
 * jetons) : des substitutions **neutres par construction**, qui préservent l'apparence. D'où un
 * audit conforme et **aucune différence visible**. Ce fichier fait l'inverse : il **remplace
 * l'apparence** en recopiant, rôle par rôle, les couleurs de la référence.
 *
 * Les valeurs ne sont pas inventées : elles sont **lues** dans la source de la référence, aux
 * emplacements suivants (chemins relatifs à la racine de la référence) :
 *
 * - `shared/designsystem/src/commonMain/kotlin/dev/hermeskotlin/designsystem/ThemeColors.kt`
 *   — l'objet `Palette` : fonds, surfaces (le barreau `surface → surface2 → surface3`), creux
 *   (`well`), feuille (`sheet`), séparateurs (`line`, `lineStrong`), les quatre encres (texte,
 *   secondaire, tertiaire, muet) en clair et en sombre, et les trois signaux (danger, succès,
 *   avertissement) dans leurs deux variantes ;
 * - `shared/designsystem/src/commonMain/kotlin/dev/hermeskotlin/designsystem/AccentPalette.kt`
 *   — `AccentPalette.Blue` : l'accent, son encre, la variante « texte » et le voile (`soft`) ;
 * - `androidApp/src/main/res/values/colors.xml` et `values-night/colors.xml`
 *   — `window_background` : le fond de page confirmé hors Compose.
 *
 * **Les trois décisions qui changent l'aspect** (et qui se mesurent sur une capture) :
 *
 * 1. **L'accent est unique et c'est celui de la référence** : `#2F6BF5`. La maison portait
 *    `#2E8BF5` — même bleu à l'œil, mais **32 points de vert en trop** (139 contre 107), donc un
 *    accent qui tire au cyan. Tout le reste de l'app passe en **encre neutre**.
 * 2. **Le barreau des surfaces suit la référence, pas l'usage inverse** : en clair la **page** est
 *    `#F7F7F9` et la **carte** `#FFFFFF` (la carte est *au-dessus*, d'un cran de 1,03:1) ; en
 *    sombre la page est `#0A0A0C` et la carte `#16161A`. La maison faisait l'inverse en clair —
 *    page blanche pure, cartes `#F2F2F7` — d'où les « blocs gris clair sur un fond clair ».
 * 3. **Les séparateurs sont des cheveux translucides** (blanc 7 % en sombre, encre 8 % en clair) et
 *    non les gris opaques à 29 % / 60 % de la référence, qui dessinaient des bordures de carte.
 *
 * **Ce qui reste propre à Onyx** — et seulement ça, avec sa raison :
 * les trois signaux gardent leurs valeurs de la référence mais **rien d'autre** n'est coloré ;
 * [OnyxContrast.DarkInk] (le presque-noir maison, neutre, à 1 point de chaque canal) reste, car il
 * porte l'encre des boutons pleins de toute l'app ; les palettes de visage de bot, de syntaxe et de
 * l'orbe vocal restent telles quelles — elles portent une **information** (l'identité d'un bot, la
 * coloration d'un langage, l'état d'une orbe), pas une décoration (§4 « ne perds rien »).
 *
 * Public parce que l'app (module `app`) peint quelques constantes hors composition : le gris d'une
 * action « Archiver », le fond de fenêtre, la couleur des barres d'une orbe. Une teinte écrite deux
 * fois est une teinte qui divergera.
 */
object ReferencePalette {

    // -----------------------------------------------------------------------------------------
    // L'accent — un seul dans toute l'app
    // -----------------------------------------------------------------------------------------

    /** `AccentPalette.Blue.accent` : le bleu de la référence, remplissage et encre de bulle. */
    val Accent = Color(0xFF2F6BF5)

    /** `AccentPalette.Blue.light.text` : le bleu **en texte** sur une page claire (4,5:1 tenu). */
    val AccentTextLight = Color(0xFF1F56D6)

    /** `AccentPalette.Blue.dark.text` : le bleu **en texte** sur un fond sombre. */
    val AccentTextDark = Color(0xFF7AA5FF)

    /** L'encre posée sur l'accent : blanc dans les deux apparences (`onAccent`). */
    val OnAccent = Color.White

    // -----------------------------------------------------------------------------------------
    // Les fonds et les surfaces — le barreau « surface → surface2 → surface3 »
    // -----------------------------------------------------------------------------------------

    /** `Palette.LightBg` / `window_background_light` : la page claire est **gris très clair**. */
    val LightPage = Color(0xFFF7F7F9)

    /** `Palette.LightSurface` : la carte claire est **blanche** — un cran au-dessus de la page. */
    val LightSurface = Color(0xFFFFFFFF)

    /** `Palette.LightSurface2` : le creux, la tuile, une ligne de formulaire. */
    val LightSurface2 = Color(0xFFF1F1F5)

    /** `Palette.LightSurface3` : le troisième barreau, le plus enfoncé. */
    val LightSurface3 = Color(0xFFE9E9EF)

    /** `Palette.LightWell` : la cuvette (champ, piste, aperçu). */
    val LightWell = Color(0xFFF3F3F7)

    /** `Palette.Bg` / `window_background_dark` : la page sombre est un graphite, pas du noir pur. */
    val DarkPage = Color(0xFF0A0A0C)

    /** `Palette.Surface` : la carte sombre, un graphite un cran au-dessus de la page. */
    val DarkSurface = Color(0xFF16161A)

    /** `Palette.Surface2`. */
    val DarkSurface2 = Color(0xFF1C1C22)

    /** `Palette.Surface3`. */
    val DarkSurface3 = Color(0xFF232329)

    /** `Palette.Well`. */
    val DarkWell = Color(0xFF121215)

    /** `Palette.Sheet` : le fond d'une feuille. */
    val DarkSheet = Color(0xFF18181D)

    /** `Palette.Thumb` : un barreau au-dessus de `surface3`, « whatever the ground ». */
    val DarkThumb = Color(0xFF2C2C33)

    // -----------------------------------------------------------------------------------------
    // **Les deux barreaux AJOUTÉS par la maison** — les seuls, et ils sont mesurés
    // -----------------------------------------------------------------------------------------

    /**
     * Le barreau des **surfaces qui flottent** en sombre : compositeur, barre du haut, barre
     * d'onglets, feuille, menu, alerte.
     *
     * La référence n'a pas de rôle pour « une surface posée sur une autre qui laisse voir le contenu
     * derrière » : ses feuilles et ses menus remplacent le contenu. La maison, elle, pose une vitre
     * **sur** le fil — et la mesure a montré que le voile du verre posé sur sa page sombre
     * (`#0A0A0C`) ne donne que **1,058:1** : indiscernable, exactement le reproche du propriétaire
     * (« un bloc quasi noir sur une page quasi noire »).
     *
     * Cette valeur est donc ajoutée, et bornée par la comparaison : elle mesure **1,314:1** sur la
     * page — au-dessus du premier barreau du sombre natif de Linear (`#08090a` → `#0f1011`,
     * 1,154:1) et sous son barreau le plus clair (`#28282c`, 1,331:1). C'est la fourchette d'un
     * sombre natif, pas une valeur choisie à l'œil.
     */
    val DarkSurfaceHigh = Color(0xFF26262B)

    /**
     * La pastille de l'**onglet actif** en sombre : une **surface** claire, pas une couleur diluée.
     *
     * Elle remplace `accent.copy(alpha = 0.16f)` — un bleu à 16 % sur le voile quasi noir de la barre
     * — qui donnait la « tache bleu sombre illisible » de la capture. Mesurée contre la page :
     * **1,578:1** ; contre [DarkSurfaceHigh], la barre qui la porte : **1,201:1**.
     */
    val DarkCapsule = Color(0xFF33333A)

    // -----------------------------------------------------------------------------------------
    // Les séparateurs — des cheveux translucides, jamais des bordures
    // -----------------------------------------------------------------------------------------

    /** `Palette.LightLine` : `#0A0A1E` à 8 %. */
    val LightLine = Color(0xFF0A0A1E).copy(alpha = 0.08f)

    /** `Palette.LightLineStrong` : `#0A0A1E` à 14 %. */
    val LightLineStrong = Color(0xFF0A0A1E).copy(alpha = 0.14f)

    /** `Palette.Line` : blanc à 7 %. */
    val DarkLine = Color.White.copy(alpha = 0.07f)

    /** `Palette.LineStrong` : blanc à 12 %. */
    val DarkLineStrong = Color.White.copy(alpha = 0.12f)

    /**
     * **Le filet de la maquette — le trait unique de l'app.**
     *
     * Ce n'est pas une valeur choisie : elle est **lue** dans la planche. `v4css.py:138` donne
     * l'apparence claire (`--line:rgba(18,22,30,.12)`) et `:149` la sombre
     * (`rgba(255,255,255,.12)`) : **douze pour cent dans les deux apparences**, et rien d'autre.
     * La maison portait 14 % en clair (une encre à 14 % sur une page claire se lit comme un
     * **cadre** autour d'une carte, d'une pastille ou d'un champ) et 12 % en sombre.
     *
     * L'encre réemployée est celle du séparateur de la maison (`#0A0A1E`) : la planche et
     * [LightLine] ne diffèrent que de quelques points de canal, et une teinte écrite deux fois est
     * une teinte qui divergera — c'est donc l'**opacité** de la planche qui est reprise, jamais une
     * couleur nouvelle. Le verrou [com.onyx.app.ui.design.OnyxEdgeVerrouTest] tient le plafond.
     */
    val LightFil = LightLine.copy(alpha = 0.12f)

    /** Le filet de la maquette en apparence sombre : `rgba(255,255,255,.12)` (`v4css.py:149`). */
    val DarkFil = DarkLine.copy(alpha = 0.12f)

    // -----------------------------------------------------------------------------------------
    // Les encres — quatre rôles, clair et sombre
    // -----------------------------------------------------------------------------------------

    /** `Palette.LightText` : l'encre pleine. */
    val LightText = Color(0xFF0A0A0C)

    /** `Palette.LightText2` : l'encre secondaire — **foncée**, pas un gris pâle. */
    val LightText2 = Color(0xFF27272A)

    /** `Palette.LightText4` : l'encre tertiaire. */
    val LightText4 = Color(0xFF6B6B75)

    /** `Palette.LightText5` : l'encre muette (compteurs, notes). */
    val LightText5 = Color(0xFF8E8E98)

    /** `Palette.Text`. */
    val DarkText = Color(0xFFF4F4F6)

    /** `Palette.Text2`. */
    val DarkText2 = Color(0xFFD4D4D8)

    /** `Palette.Text4`. */
    val DarkText4 = Color(0xFF8A8A94)

    /** `Palette.Text5`. */
    val DarkText5 = Color(0xFF6B6B75)

    // -----------------------------------------------------------------------------------------
    // Les signaux — les seules couleurs saturées admises avec l'accent
    // -----------------------------------------------------------------------------------------

    /** `Palette.LightDanger` / `Palette.Danger`. */
    val Danger = DynColor(light = Color(0xFFCF222E), dark = Color(0xFFF85149))

    /** `Palette.LightSuccess` / `Palette.Success`. */
    val Success = DynColor(light = Color(0xFF1A7F37), dark = Color(0xFF3FB950))

    /** `Palette.LightWarning` / `Palette.Warning`. */
    val Warning = DynColor(light = Color(0xFFB76E00), dark = Color(0xFFF5A524))

    // -----------------------------------------------------------------------------------------
    // Les neutres nommés, pour ce qui se peint **hors** d'une composition
    // -----------------------------------------------------------------------------------------

    /**
     * Le gris neutre d'une action secondaire pleine (« Archiver » d'une rangée, le pavé de la
     * feuille d'une conversation).
     *
     * C'est le **tertiaire de la référence** (`Palette.LightText4`, `#6B6B75`) : neutre au sens de
     * la maison — 10 points d'écart entre le rouge et le bleu, et rien qui tire au violet. La maison
     * portait `#8E8E93`, le `.gray` du système, qui ne portait un libellé **blanc** qu'à **3,09:1**
     * — sous le plancher de 4,5:1 de la charte. Celui-ci le porte à **5,36:1**, dans les deux
     * apparences, et c'est le même gris que le reste de l'app.
     */
    val NeutralAction = Color(0xFF6B6B75)
}
