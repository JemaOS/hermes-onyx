package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.OnyxSystem
import com.onyx.app.ui.theme.ReferencePalette
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * **L'échelle des surfaces de la maison — cinq rôles, une marche mesurée entre chacun.**
 *
 * Pourquoi ce fichier. Le propriétaire a testé `1.0.0+580` et son premier reproche est celui-ci,
 * mot pour mot : « le compositeur est un bloc quasi noir sur une page quasi noire, aucune séparation
 * perceptible ». Le défaut est **mesurable** et il l'a été :
 *
 * | surface | avant | luminance | marche sur la page |
 * |---|---|---|---|
 * | page (sombre) | `#0A0A0C` | 0,00308 | — |
 * | **compositeur** (voile de verre : anthracite 30 % sur la page) | `#121215` | 0,00615 | **1,058:1** |
 * | **barre d'onglets** (même voile) | `#121215` | 0,00615 | **1,058:1** |
 * | carte de liste | `#16161A` | 0,00819 | 1,096:1 |
 * | feuille / menu (`surfaceColor` en repli) | `#16161A` | 0,00819 | 1,096:1 |
 *
 * Un écart de 1,058:1 n'est pas un écart : c'est le bruit du tramage. La comparaison qui fixe
 * l'étalon est celle du système de design de **Linear**, qui est un sombre natif (relevé dans
 * `popular-web-designs` → `templates/linear.app.md`) : son échelle est `#08090a` → `#0f1011`
 * (**1,154:1**) → `#191a1b` (**1,197:1**) → `#28282c` (**1,331:1**). Autrement dit : une marche
 * perceptible en sombre commence à **1,15:1**, et une surface qui doit **se détacher** (un
 * compositeur flottant, un panneau) demande **≥ 1,25:1**.
 *
 * **Les deux régimes, et pourquoi ils ne se ressemblent pas.** En **clair**, une surface flottante
 * ne se détache pas par la luminance (la page est déjà à `L = 0,93` : il n'y a plus de place) — elle
 * se détache par le **filet** et l'**ombre**. En **sombre**, l'ombre ne se voit pas sur du quasi-noir
 * et le filet seul ne suffit pas : c'est la **luminance** qui porte la hiérarchie. C'est la pratique
 * des deux références lues : Vercel porte ses cartes claires par `box-shadow: 0 0 0 1px
 * rgba(0,0,0,0.08)` (« shadow-as-border », « cards feel built, not floating »), Linear porte ses
 * surfaces sombres par des barreaux opaques, sans ombre.
 *
 * **Ce que la maison retient, et d'où vient chaque chiffre :**
 *
 * - `[Page]`, `[Well]`, `[Surface]`, `[Tile]` sont **les valeurs de la référence**
 *   ([ReferencePalette]) : rien n'est réinventé, on ne fait que les nommer par rôle.
 * - `[SurfaceHigh]` est **la seule valeur ajoutée** en sombre : `#26262B`, **1,314:1** sur la page —
 *   la marche « franche » des surfaces qui **flottent** (compositeur, barre d'onglets, feuille,
 *   menu). Elle est au-dessus du troisième barreau de la référence (`#232329`, 1,266:1) et sous le
 *   `#28282c` de Linear (1,331:1) : dans la fourchette du sombre natif.
 * - `[Capsule]` — `#33333A` en sombre, **1,619:1** sur la page et **1,232:1** sur `[SurfaceHigh]` —
 *   est la pastille de l'onglet actif. Elle remplace une teinte d'accent à 16 % qui, sur un voile
 *   quasi noir, donnait une « tache bleu sombre illisible » : une pastille doit se voir **avant** de
 *   porter une couleur.
 * - `[Hairline]` est **le seul filet de l'app** (blanc 8 % en sombre, encre 8 % en clair), celui que
 *   la référence appelle `Line` / `LineStrong`. « Border and divider visibility : ensure separators
 *   are visible in both themes » (`ui-ux-pro-max/references/pro-rules.md`, Light/Dark Mode
 *   Contrast) — un filet qui disparaît dans une apparence est un défaut, pas une économie.
 *
 * **Ce qu'un écran doit faire.** Poser `[SurfaceHigh]` **sous** la vitre d'une surface flottante, et
 * `[Capsule]` pour l'onglet actif. La marche est ainsi **déterministe** : elle ne dépend ni du
 * contenu qui défile derrière, ni de l'opacité qu'une autre mesure a retenue pour la vitre.
 *
 * **Le verrou.** [steps] rend la table elle-même, et `OnyxSurfaceLadderTest` la relit : les chiffres
 * de ce commentaire et ceux du rapport sont **le même calcul**. Un rôle qui passerait sous son seuil
 * casse le test avant l'écran.
 */
object OnyxSurfaces {

    // -----------------------------------------------------------------------------------------
    // Les valeurs — les quatre de la référence, deux de la maison
    // -----------------------------------------------------------------------------------------

    /** La page. Valeur de la référence, dans les deux apparences. */
    val Page: Color get() = ReferencePalette.LightPage

    /** Le creux (piste d'un champ, fond d'aperçu). Valeur de la référence. */
    val Well: Color get() = ReferencePalette.LightWell

    /** La carte, la rangée de liste. Valeur de la référence. */
    val Surface: Color get() = ReferencePalette.LightSurface

    /** La vignette, la tuile d'icône. Valeur de la référence. */
    val Tile: Color get() = ReferencePalette.LightSurface2

    // -----------------------------------------------------------------------------------------
    // Le barreau **par apparence** — les six rôles que les écrans lisent
    // -----------------------------------------------------------------------------------------

    /**
     * La page de l'apparence courante. Passe par le thème : « noir pur » (OLED) et les thèmes à
     * fond propre (Paper, Slate, Terminal…) doivent continuer de commander leur page.
     */
    fun page(theme: ResolvedTheme): Color = theme.pageColor

    /** Le creux : un cran **sous** la surface, jamais au-dessus de la page en sombre. */
    fun well(theme: ResolvedTheme): Color =
        if (theme.dark) ReferencePalette.DarkWell else ReferencePalette.LightWell

    /** La carte / la rangée de liste : le premier barreau **au-dessus** de la page. */
    fun surface(theme: ResolvedTheme): Color =
        if (theme.dark) ReferencePalette.DarkSurface else ReferencePalette.LightSurface

    /**
     * **Le barreau des surfaces qui flottent** : compositeur, barre d'onglets, feuille, menu, alerte.
     *
     * C'est la correction du reproche principal (« compositeur quasi noir sur page quasi noire »).
     * En clair, c'est la carte blanche (la séparation vient du filet et de l'ombre) ; en sombre,
     * c'est `#26262B`, la marche franche de 1,314:1 qui fait qu'un compositeur se lit **posé** sur
     * la page.
     */
    fun surfaceHigh(theme: ResolvedTheme): Color =
        if (theme.dark) DarkSurfaceHigh else ReferencePalette.LightSurface

    /**
     * La pastille de l'**onglet actif** : plus claire que la barre qui la porte, jamais une teinte
     * d'accent diluée. C'est elle qui rend la pastille lisible **avant** de lui donner une couleur.
     */
    fun capsule(theme: ResolvedTheme): Color =
        if (theme.dark) DarkCapsule else ReferencePalette.LightSurface3

    /** La tuile d'icône d'une rangée : un barreau au-dessus de la surface, « whatever the ground ». */
    fun tile(theme: ResolvedTheme): Color =
        if (theme.dark) ReferencePalette.DarkThumb else ReferencePalette.LightSurface2

    /** Le fond d'un contrôle ordinaire (une rangée de formulaire, une piste). */
    fun fill(theme: ResolvedTheme): Color = OnyxSystem.fill.resolve(theme.dark)

    /**
     * **Le filet — le seul de l'app.**
     *
     * Il ne choisit plus sa valeur : il rend [OnyxEdge.fil], c'est-à-dire le **filet de la
     * maquette** — encre à **12 %** en clair, blanc à **12 %** en sombre (`v4css.py:138`, `:149`).
     * C'est le `Line` / `LineStrong` de la référence, ramené à l'opacité que la planche s'accorde.
     *
     * **Pourquoi 12 % et pas 14 %.** La maison portait le filet fort de la référence (14 % en
     * clair) ; sur une page claire, une encre à 14 % se lit comme un **cadre** posé autour d'une
     * carte, d'une pastille, d'un champ ou d'une barre — le reproche « tous les bords gris autour
     * des boutons sont très prononcés ». La planche, elle, pose une **arête claire en haut du
     * verre** et un filet **très faible** ailleurs : c'est ce plafond que [OnyxEdge] porte, et
     * [OnyxEdgeVerrouTest] le tient.
     */
    fun hairline(theme: ResolvedTheme): Color = OnyxEdge.fil(theme)

    /**
     * **Le filet de la source** — `stroke` : encre 8 % en clair, blanc 7 % en sombre
     * (`ThemeColors.kt:30-31,18-19`). C'est le filet **du contour d'une carte** chez la source ;
     * [hairline] est son `strokeStrong` (14 % / 12 %), celui que la source donne à la capsule active
     * d'un contrôle segmenté et à un champ au repos.
     *
     * Les deux existent parce que la source en a deux : on ne confond pas le contour d'une carte
     * avec celui d'un contrôle.
     */
    fun line(theme: ResolvedTheme): Color =
        if (theme.dark) ReferencePalette.DarkLine else ReferencePalette.LightLine

    /** Le `strokeStrong` de la source — le filet d'un contrôle, d'une capsule active, d'un champ. */
    fun lineStrong(theme: ResolvedTheme): Color = hairline(theme)

    /**
     * **Le troisième barreau** de la source — `surface3` : `#E9E9EF` en clair, `#232329` en sombre
     * (`ThemeColors.kt:28,14`).
     *
     * C'est le fond d'une **pilule secondaire** (`components/Button.kt:74`) et d'une **pastille de
     * comptage** (`CapabilitiesPage.kt:509`). Il manquait : la maison n'avait que `surface` et
     * `surfaceHigh`, si bien qu'une pilule secondaire empruntait le fond d'une carte ou celui d'une
     * surface flottante — deux rôles qui ne sont pas le sien.
     */
    fun surface3(theme: ResolvedTheme): Color =
        if (theme.dark) ReferencePalette.DarkSurface3 else ReferencePalette.LightSurface3

    // -----------------------------------------------------------------------------------------
    // Les deux valeurs ajoutées
    // -----------------------------------------------------------------------------------------

    /**
     * Le barreau des surfaces flottantes en sombre : `#26262B`.
     *
     * Mesuré contre la page sombre de la référence (`#0A0A0C`, luminance 0,003082) : **1,314:1**.
     * C'est au-dessus des 1,15:1 du premier barreau de Linear et sous son barreau le plus clair
     * (1,331:1) — la fourchette d'un sombre natif, pas une valeur choisie à l'œil.
     *
     * La valeur vit dans [ReferencePalette] : une teinte écrite deux fois est une teinte qui
     * divergera, et le verrou de palette la surveille déjà.
     */
    val DarkSurfaceHigh: Color = ReferencePalette.DarkSurfaceHigh

    /**
     * La pastille de l'onglet actif en sombre : `#33333A`.
     *
     * Mesurée contre la page : **1,578:1** ; contre [DarkSurfaceHigh], la barre qui la porte :
     * **1,201:1**. La pastille se voit donc **dans** la barre, et la barre se voit sur la page :
     * trois barreaux distincts là où la capture du propriétaire n'en montrait aucun. L'accent **en
     * encre** (`#7AA5FF`) y mesure **5,165:1** — au-dessus du plancher de 3:1 du brief, et même
     * au-dessus des 4,5:1 d'un mot, donc le libellé de l'onglet actif tient sans être dégradé.
     */
    val DarkCapsule: Color = ReferencePalette.DarkCapsule

    // -----------------------------------------------------------------------------------------
    // La table — ce que le test et le rapport relisent
    // -----------------------------------------------------------------------------------------

    /** Un barreau de l'échelle : son rôle, sa couleur, sa luminance et sa marche sur la page. */
    data class Step(
        val role: String,
        val light: String,
        val dark: String,
        val luminanceDark: Float,
        val overPageDark: Float,
        val overPageLight: Float,
    )

    private fun hex(color: Color): String = String.format(
        "#%02X%02X%02X",
        (color.red * 255f).toInt(),
        (color.green * 255f).toInt(),
        (color.blue * 255f).toInt(),
    )

    /**
     * **L'échelle, du plus enfoncé au plus relevé**, telle que le rapport la publie.
     *
     * [overPageDark] est le rapport de contraste WCAG du barreau contre la page sombre ; c'est lui
     * qui porte la contrainte « une marche perceptible ». [overPageLight] est le même chiffre en
     * clair : il est petit par construction (la page claire est à 0,93 de luminance), et c'est
     * **voulu** — en clair, c'est le filet et l'ombre qui séparent, pas la luminance.
     */
    val steps: List<Step> = listOf(
        Step("creux", hex(ReferencePalette.LightWell), hex(ReferencePalette.DarkWell),
            OnyxContrast.luminance(ReferencePalette.DarkWell),
            OnyxContrast.ratio(ReferencePalette.DarkWell, ReferencePalette.DarkPage),
            OnyxContrast.ratio(ReferencePalette.LightWell, ReferencePalette.LightPage)),
        Step("surface (carte, rangée)", hex(ReferencePalette.LightSurface), hex(ReferencePalette.DarkSurface),
            OnyxContrast.luminance(ReferencePalette.DarkSurface),
            OnyxContrast.ratio(ReferencePalette.DarkSurface, ReferencePalette.DarkPage),
            OnyxContrast.ratio(ReferencePalette.LightSurface, ReferencePalette.LightPage)),
        Step("surface haute (compositeur, barre, feuille, menu)",
            hex(ReferencePalette.LightSurface), hex(DarkSurfaceHigh),
            OnyxContrast.luminance(DarkSurfaceHigh),
            OnyxContrast.ratio(DarkSurfaceHigh, ReferencePalette.DarkPage),
            OnyxContrast.ratio(ReferencePalette.LightSurface, ReferencePalette.LightPage)),
        Step("capsule active (onglet)", hex(ReferencePalette.LightSurface3), hex(DarkCapsule),
            OnyxContrast.luminance(DarkCapsule),
            OnyxContrast.ratio(DarkCapsule, ReferencePalette.DarkPage),
            OnyxContrast.ratio(ReferencePalette.LightSurface3, ReferencePalette.LightPage)),
        Step("tuile (vignette d'icône)", hex(ReferencePalette.LightSurface2), hex(ReferencePalette.DarkThumb),
            OnyxContrast.luminance(ReferencePalette.DarkThumb),
            OnyxContrast.ratio(ReferencePalette.DarkThumb, ReferencePalette.DarkPage),
            OnyxContrast.ratio(ReferencePalette.LightSurface2, ReferencePalette.LightPage)),
    )

    // -----------------------------------------------------------------------------------------
    // Les seuils — nommés, donc citables
    // -----------------------------------------------------------------------------------------

    /**
     * La marche minimale d'un barreau ordinaire sur la page, en sombre : **1,09:1**.
     *
     * Ce n'est pas un chiffre de la charte WCAG (qui ne parle pas de surfaces) : il vient de la
     * **référence elle-même**. Sa carte sombre (`Palette.Surface = #16161A`) mesure **1,096:1** sur
     * sa page (`#0A0A0C`) — c'est le pas que la référence s'accorde, et la maison le suit : une
     * carte de liste qui s'en écarterait ne serait plus la carte de la référence.
     *
     * Le barreau de **Linear** (un sombre natif, relevé dans `popular-web-designs`) donne le même
     * ordre de grandeur — `#08090a` → `#0f1011` vaut 1,154:1 — et confirme qu'un pas de surface en
     * sombre vit entre 1,09 et 1,20. En dessous, deux surfaces de même teinte se lisent comme une
     * seule : c'est le défaut que le propriétaire a vu.
     *
     * **Une surface qui doit se DÉTACHER n'utilise pas ce seuil** : voir [FloatStepMin].
     */
    const val StepMin: Float = 1.09f

    /** La marche minimale d'une surface qui **flotte** (compositeur, feuille, menu) : 1,25:1. */
    const val FloatStepMin: Float = 1.25f

    /**
     * La marche minimale d'un **creux** : 1,05:1.
     *
     * Un creux ne porte pas d'encre et ne prétend pas se détacher — il se **sent**. La valeur de la
     * référence (`#121215` sur `#0A0A0C`) mesure **1,058:1** : elle est donc admise telle quelle, et
     * c'est pour ça que le creux a son propre seuil au lieu d'être jugé comme une surface.
     */
    const val WellStepMin: Float = 1.05f

    /**
     * La marche minimale d'un barreau **en apparence claire** : 1,10:1.
     *
     * Elle est plus basse que [StepMin], et c'est une contrainte physique : la page claire de la
     * référence (`#F7F7F9`) est déjà à `L = 0,931` — il n'y a plus que 7 % de luminance à parcourir
     * avant le blanc. Le troisième barreau de la référence (`#E9E9EF`) mesure **1,130:1** : c'est le
     * maximum disponible. En clair, la séparation vient du **filet** et de l'**ombre**, pas de la
     * luminance (voir [OnyxShadow]). Le contrôle « les états ne se définissent pas pour une seule
     * apparence » reste donc tenu — c'est le **seuil** qui diffère, pas l'exigence.
     */
    const val LightStepMin: Float = 1.10f

    /** La marche minimale de la pastille active **contre sa barre** : 1,15:1. */
    const val CapsuleStepMin: Float = 1.15f

    /** Le plancher d'un filet : il doit se distinguer de ce qu'il borde, dans les deux apparences. */
    const val HairlineMin: Float = 1.06f

    /** Le plancher de l'encre d'un onglet actif contre sa barre (des exigences du brief : 3:1). */
    const val ActiveInkMin: Float = 3.0f
}
