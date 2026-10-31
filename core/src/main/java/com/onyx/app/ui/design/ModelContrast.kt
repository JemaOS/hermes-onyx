package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * Les **fonds réellement composés** de la carte de composition et du sélecteur de modèle.
 *
 * Une vitre ne peint pas un aplat : ce qu'on lit dessus est le voile de la vitre posé sur le
 * contenu qui passe dessous. Le pire cas honnête est donc un aplat de la couleur extrême (blanc pur
 * sous un voile sombre, noir pur sous un voile clair) — on essaie les deux et on garde celui qui
 * **baisse le plus** l'encre du thème.
 *
 * Ces trois fonctions sont appelées **par les écrans et par le test** : le chiffre annoncé et le
 * chiffre mesuré sont le même. C'est ce qui permet de dire, captures à l'appui, que la carte, la
 * puce et la feuille tiennent leurs seuils (4,5:1 pour un texte, 3:1 pour une icône).
 */
object ModelContrast {

    /** Le fond composé de la **carte de composition** (la vitre de commande), pire cas. */
    fun card(theme: ResolvedTheme): Color = worst(FrostSurface.COMMAND_BAR, theme, tint = null)

    /**
     * Le fond composé de la **puce du modèle**, posée sur la carte : son voile de `fill` par-dessus
     * le fond de la carte. C'est ce fond-là que l'encre de la puce mesure.
     */
    fun chip(theme: ResolvedTheme): Color = compositedBackdrop(
        theme.fill.copy(alpha = if (theme.dark) 0.55f else 0.7f),
        card(theme),
    )

    /** Le voile noir qui précède une feuille : c'est lui qui assombrit ce qu'il y a dessous. */
    const val SheetScrimAlpha = 0.4f

    /** Le fond composé de la **feuille** (la vitre de feuille, teintée de la surface), pire cas. */
    fun sheet(theme: ResolvedTheme): Color =
        worst(FrostSurface.SHEET, theme, tint = theme.surfaceColor, scrimAlpha = SheetScrimAlpha)

    /**
     * Le carré de la **vignette d'initiale** : un gris clair posé sur la feuille — la référence le
     * dessine gris, et c'est ce gris que la vignette peint **et** que le test mesure. Son encre en
     * est dérivée ([readableInk]), sinon l'encre du thème pourrait s'y perdre.
     */
    fun monogram(theme: ResolvedTheme): Color =
        compositedBackdrop(theme.fill.copy(alpha = if (theme.dark) 0.5f else 0.65f), sheet(theme))

    /** La rangée du modèle **retenu** : la teinte d'accent posée sur la feuille. */
    fun chosenRow(theme: ResolvedTheme): Color = compositedBackdrop(theme.accent.copy(alpha = 0.10f), sheetSurface(theme))

    /**
     * **La surface de la feuille, opaque** — le barreau des surfaces qui flottent
     * ([OnyxSurfaces.surfaceHigh]), celui que la feuille de modèle peint désormais sous son contenu.
     *
     * C'est la même surface que la feuille d'actions de la maison : dès lors, tout ce qui se mesure
     * sur la feuille (tuile, rangée retenue, pastille) se mesure sur **ce qui est peint**, et non sur
     * le pire cas d'une vitre translucide.
     */
    fun sheetSurface(theme: ResolvedTheme): Color = OnyxSurfaces.surfaceHigh(theme)

    /**
     * La pastille **active** d'une rangée de choix : **l'accent de la maison**, plein — **écarté de
     * la feuille** et **de la pastille inactive** juste assez pour se voir (voir [CHIP_STEP_MIN]).
     *
     * Le motif a changé au lot « tenue de la feuille de modèle ». La pastille active était noire à
     * encre blanche (retournée en sombre) — le motif d'une référence où la sélection se lit à sa
     * masse. Sur la feuille du propriétaire, la pastille active est **l'accent** : c'est le seul
     * endroit de la feuille qui porte une couleur, et il la porte pour dire « c'est ceci ». La
     * pastille inactive reste le gris de la maison ([idleChip]).
     *
     * La teinte est **composée sur la feuille** avant d'être rendue : les accents du thème sont
     * parfois translucides, et c'est la couleur réellement peinte qui se mesure.
     *
     * **Pourquoi elle est écartée, elle aussi.** Un accent peut être **pâle** — le thème « Code »
     * peint le sien en `#CBF0FF`. Posé sur la feuille **claire** (blanche), cet accent ne mesurait
     * que **1,20:1** contre la feuille et **1,04:1** contre la pastille inactive : la pastille
     * **retenue** était indiscernable des autres, alors que c'est justement elle qui dit le choix.
     * Elle est donc écartée du fond par le pas de la maison ([CHIP_STEP_MIN]), puis de la pastille
     * inactive par le même pas. Les thèmes dont l'accent tient déjà le pas **ne bougent pas d'un
     * point** (vérifié : `#2F6BF5` du thème par défaut reste `#2F6BF5`).
     */
    fun activeChip(theme: ResolvedTheme): Color {
        val surface = sheetSurface(theme)
        val painted = OnyxContrast.over(theme.accent, surface)
        return OnyxContrast.distinctOn(
            OnyxContrast.distinctOn(painted, surface, CHIP_STEP_MIN),
            idleChip(theme),
            CHIP_STEP_MIN,
        )
    }

    /** L'encre de la pastille active : celle qui se lit sur l'accent réellement peint. */
    fun activeChipInk(theme: ResolvedTheme): Color = readableInk(theme.accentInk, activeChip(theme))

    /**
     * La pastille *inactive* (un cran qu'on peut choisir, sans l'avoir choisi) : le gris de la
     * famille des capsules ([OnyxSurfaces.capsule]), **écarté du fond de la feuille** juste assez
     * pour tenir [CHIP_STEP_MIN] — il se voit **avant** de porter une couleur, comme la pastille de
     * l'onglet actif.
     *
     * **Pourquoi il est mesuré, et non pris tel quel.** Le gris de la famille est fait pour une
     * **barre** ; posé sur la feuille **opaque** ([sheetSurface]), il tombait sous la marche d'une
     * surface qui se détache, dans les **deux** apparences :
     *
     * | apparence | pastille | feuille | rapport |
     * |---|---|---|---|
     * | sombre | `#33333A` | `#26262B` | **1,20:1** |
     * | claire | `#E9E9EF` | `#FFFFFF` | **1,21:1** |
     *
     * Sous 1,25:1, une pastille n'existe pas à l'écran : on lit ses mots, mais pas **qu'il y a une
     * pastille** — c'est le défaut relevé mot pour mot (« on ne voit pas qu'il y a une pastille »).
     * La teinte reste celle de la maison ; seule sa profondeur bouge, du plus petit pas qui atteint
     * la marche ([OnyxContrast.distinctOn]) — jamais une couleur nouvelle, et jamais à l'œil.
     */
    fun idleChip(theme: ResolvedTheme): Color =
        OnyxContrast.distinctOn(OnyxSurfaces.capsule(theme), sheetSurface(theme), CHIP_STEP_MIN)

    /**
     * L'encre **tertiaire** de la maison, tenue au seuil du **texte** (4,5:1) sur la feuille
     * **réellement peinte** ([sheetSurface]).
     *
     * Le tertiaire du système (`#8A8A94`) est dessiné pour une **carte** ; posé sur la feuille
     * opaque (`#26262B`), il mesure **4,41:1** — sous le plancher de 4,5:1 — et il porte pourtant
     * des **mots** de la feuille : l'en-tête de section (« PAR DÉFAUT »), l'état d'un profil, et
     * l'**étoile** au repos. Il est donc **écarté du fond** par la fonction de la maison
     * ([readableInk]) — la même que la rangée emploie déjà pour son nom et sa sous-ligne.
     *
     * **Le jeton partagé n'est pas touché.** `OnyxSystem.tertiaryLabel` sert partout ailleurs
     * (barres, formulaires, écrans) et n'a pas à bouger pour un fond qui n'est pas le sien : c'est
     * **l'encre de la feuille** qui est mesurée, et rien d'autre. En apparence **claire**, le
     * tertiaire mesure déjà **5,27:1** sur la feuille blanche — la fonction le rend **inchangé**.
     */
    fun sheetTertiaryInk(theme: ResolvedTheme): Color = readableInk(theme.tertiaryText, sheetSurface(theme))

    /**
     * Le seuil de la **tuile d'une rangée** : elle doit se voir sur la feuille, pas seulement
     * exister. 1,25:1 est la marche « franche » de la maison pour une surface qui se détache
     * ([OnyxSurfaces]).
     */
    const val TILE_MIN = 1.25f

    /**
     * Le seuil de la **forme d'une pastille** (active comme inactive) : elle doit se détacher de la
     * feuille, pas seulement exister.
     *
     * Une pastille qui ne se détache pas de la feuille n'existe pas pour l'œil — on lit ses mots
     * sans voir qu'il y a **une forme** à toucher. C'est le seuil de la maison pour une surface qui
     * se détache ([OnyxSurfaces.FloatStepMin]), pas un chiffre inventé ici. Il sert aussi à écarter
     * la pastille **retenue** des autres : « c'est ceci » doit se dire, pas se deviner.
     */
    const val CHIP_STEP_MIN = OnyxSurfaces.FloatStepMin

    /**
     * **Le gris de la tuile d'une rangée**, mesuré sur la feuille.
     *
     * Il part du gris de la maison ([neutralThumbnailTint] — celui de la vignette de toutes les
     * rangées de feuille). Ce gris est fait pour se poser sur une **carte** ; posé sur la feuille,
     * il tombait à 1,08:1 en clair et 1,09:1 en sombre — la tuile existait dans le code et
     * **disparaissait** à l'écran, ce qui se lit comme « une lettre nue » (le défaut relevé sur la
     * capture du propriétaire : des rangées sans tuile).
     *
     * Il est donc **écarté du fond** du plus petit pas qui atteint [TILE_MIN] : la teinte reste le
     * gris de la maison, seule sa profondeur change, et le changement est **mesuré**, jamais choisi
     * à l'œil.
     */
    fun tile(theme: ResolvedTheme): Color {
        val surface = sheetSurface(theme)
        val base = neutralThumbnailTint(theme)
        if (OnyxContrast.ratio(base, surface) >= TILE_MIN) return base
        // La direction qui offre de la place : vers le blanc sur une feuille sombre, vers le noir
        // sur une feuille claire. Dichotomie : le plus petit pas qui suffit, reproductible.
        val towards = if (theme.dark) Color.White else Color.Black
        var notYet = 0f
        var enough = 1f
        repeat(24) {
            val middle = (notYet + enough) / 2f
            if (OnyxContrast.ratio(lerp(base, towards, middle), surface) >= TILE_MIN) {
                enough = middle
            } else {
                notYet = middle
            }
        }
        return lerp(base, towards, enough)
    }

    /** L'encre du monogramme posé **dans** la tuile : mesurée contre le gris réellement peint. */
    fun tileInk(theme: ResolvedTheme): Color = readableInk(theme.textColor, tile(theme))

    /**
     * La pastille d'**information** d'une rangée de modèle : le fournisseur, et son prix quand le
     * serveur en donne un. Un gris clair posé sur la feuille — plus discret que la pastille active,
     * pour que « c'est choisi » ne se confonde jamais avec « voilà où c'est servi ».
     */
    fun offerChip(theme: ResolvedTheme): Color =
        compositedBackdrop(theme.fill.copy(alpha = if (theme.dark) 0.45f else 0.6f), sheet(theme))

    private fun worst(surface: FrostSurface, theme: ResolvedTheme, tint: Color?, scrimAlpha: Float = 0f): Color {
        val alpha = frostVeilAlpha(surface, FrostLevel.FULL, theme.dark)
        val veil = tint?.copy(alpha = alpha) ?: veilColour(theme.pageColor, alpha, theme.dark)
        // Une feuille est précédée d'un voile noir : c'est ce voile qui assombrit le contenu avant
        // que la vitre ne se pose — le pire cas honnête en tient compte (voir `SheetScrimAlpha`).
        val scrim = Color.Black.copy(alpha = scrimAlpha.coerceIn(0f, 1f))
        val candidates = listOf(
            compositedBackdrop(veil, compositedBackdrop(scrim, Color.White)),
            compositedBackdrop(veil, compositedBackdrop(scrim, Color.Black)),
        )
        return candidates.minByOrNull { contrastRatio(theme.textColor, it) } ?: candidates.first()
    }
}
