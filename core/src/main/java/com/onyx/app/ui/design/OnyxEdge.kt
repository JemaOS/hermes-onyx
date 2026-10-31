package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.theme.ReferencePalette
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * **Le trait unique de l'app — une seule source, deux valeurs, un plafond qui mord.**
 *
 * Le propriétaire a dit, capture à la main : « tous les bords gris autour des boutons sont très
 * prononcés ». Le défaut n'était pas local — c'est que l'app dessinait ses contours avec **six**
 * sources différentes : le filet fort de la référence (14 % en clair), le séparateur (8 %), les
 * **encres de texte** prises comme bordures (`theme.quaternaryText`, pleinement opaque, et
 * `theme.tertiaryText.copy(alpha = 0.35f)`), une encre mesurée à 3:1 (`OnyxInkOn`), et le liseré
 * interne du verre (noir à 16 %). Aucune ne valait ce que la maquette pose.
 *
 * Ce que la maquette pose, elle, est **écrit dans sa source** (`v4css.py`, la table du bandeau de
 * la planche) :
 *
 * | rôle de la planche | apparence claire | apparence sombre | où |
 * |---|---|---|---|
 * | le **filet** (`--line`) — carte, pastille, champ, bulle, bouton | `rgba(18,22,30,.12)` | `rgba(255,255,255,.12)` | `v4css.py:138`, `:149` |
 * | l'**arête claire du haut du verre** (`--gedge` / `--gloss`) | blanc `.98` | blanc `.62` | `v4css.py:142`, `:154` |
 * | le **filet de la pastille active** (`--pillline`) | blanc `.92` | blanc `.24` | `v4css.py:145`, `:157` |
 *
 * Autrement dit : **un seul trait** — douze pour cent, une fois sombre en clair et une fois clair
 * en sombre — et, pour une vitre, une **arête claire en haut** et rien qu'un filet très faible
 * ailleurs. Aucune bordure n'est opaque, aucune n'est un gris d'encre de texte.
 *
 * [fil] est ce trait. **Tout** contour de l'app le lit : [OnyxSurfaces.hairline] le rend, donc une
 * carte, une pastille, un champ, une feuille, un menu, la barre d'onglets et la pilule du haut le
 * prennent d'un seul coup. [MaxDarkAlpha] est le **plafond** : aucun contour *sombre* de l'app n'a
 * le droit de le dépasser — c'est ce que [OnyxEdgeVerrouTest] refuse, en nommant le fichier et la
 * ligne fautive.
 */
object OnyxEdge {

    /** Le filet en apparence **claire** : encre à 12 %. `v4css.py:138`. */
    const val FilLightAlpha: Float = 0.12f

    /** Le filet en apparence **sombre** : blanc à 12 %. `v4css.py:149`. */
    const val FilDarkAlpha: Float = 0.12f

    /** L'arête claire du haut d'une vitre, en clair : blanc à 98 %. `v4css.py:142`. */
    const val TopEdgeLightAlpha: Float = 0.98f

    /** L'arête claire du haut d'une vitre, en sombre : blanc à 62 %. `v4css.py:154`. */
    const val TopEdgeDarkAlpha: Float = 0.62f

    /**
     * **Le plafond d'un contour sombre.** C'est l'opacité du filet, et rien de plus : un trait
     * d'encadrement plus opaque que la maquette se lit comme un cadre, et c'est exactement le
     * défaut signalé. Le verrou compare chaque contour à cette valeur.
     */
    const val MaxDarkAlpha: Float = 0.12f

    /** Le filet de la maquette, dans l'apparence courante. L'encre vit dans [ReferencePalette]. */
    val LightFil: Color = ReferencePalette.LightFil

    /** Le filet de la maquette en clair, dans l'apparence courante. */
    val DarkFil: Color = ReferencePalette.DarkFil

    /** Le filet de l'apparence courante : `#0A0A1E` à 12 % en clair, blanc à 12 % en sombre. */
    fun fil(theme: ResolvedTheme): Color = if (theme.dark) DarkFil else LightFil

    /**
     * **L'arête claire** d'une surface de verre : un blanc, plus lumineux en clair (`0,98`) qu'en
     * sombre (`0,62`) — parce que la page sombre est déjà peu lumineuse. C'est la seule bordure
     * **claire** de la maison : elle marque le dessus du verre, elle n'encadre rien.
     */
    fun topEdge(theme: ResolvedTheme): Color =
        Color.White.copy(alpha = if (theme.dark) TopEdgeDarkAlpha else TopEdgeLightAlpha)

    /**
     * La même arête, **par apparence** — c'est la porte qu'emploie le verre ([drawGlassPlateEdge]),
     * qui ne reçoit que le drapeau sombre et non le thème entier.
     */
    fun topEdge(dark: Boolean): Color =
        Color.White.copy(alpha = if (dark) TopEdgeDarkAlpha else TopEdgeLightAlpha)

    /**
     * Le filet de la **pastille d'onglet active** : `inset 0 0 0 1px` de la planche — blanc `.92`
     * en clair, `.24` en sombre (`v4css.py:145`, `:157`). Il vit sur la vitre, donc il est **clair**,
     * jamais un gris.
     */
    fun activePillLine(theme: ResolvedTheme): Color =
        Color.White.copy(alpha = if (theme.dark) 0.24f else 0.92f)
}
