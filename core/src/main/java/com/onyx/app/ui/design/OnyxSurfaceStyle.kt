package com.onyx.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * **Une seule famille de formes et une seule ombre — par RÔLE, pas par écran.**
 *
 * Le troisième reproche du propriétaire, sur ses captures de `1.0.0+580` : « traitements de formes
 * incohérents d'un écran à l'autre ». Relevé, rôle par rôle, dans l'arbre au moment du lot :
 *
 * | objet | avant (rayon / bord / ombre) | écart |
 * |---|---|---|
 * | champ de recherche (Capacités, Projets) | coin `ControlCorner` 9 dp, fond `surfaceColor`, **aucun filet** | coin de *contrôle*, pas de *champ* |
 * | champ du compositeur | coin 22 dp (`ComposerCardRadius`), filet 1 dp à 50 % d'un séparateur à 7 % | deux coins et deux filets pour un champ |
 * | contrôle segmenté | capsule 50 %, **ombre d'élévation** (`FlatSegmentPillElevation`) | la seule ombre de sa famille |
 * | tuile du volet | coin 16 dp, fond `fill`, plat | aucun filet, aucune ombre |
 * | bouton rond du fil | capsule, `shadow(4.dp, CircleShape)` | une ombre à part |
 * | feuille des actions | coin **22 dp** (`SheetCorner`) | le panneau de la table dit **26** |
 * | feuille des pièces jointes | coin `GlassPanelCorner` = 26 dp | deux feuilles, deux coins |
 *
 * Autrement dit : **quatre familles de formes pour un même rôle visuel** — c'est ce que l'œil lit
 * comme « pas chiadé » avant toute question de couleur.
 *
 * **Ce que ce fichier pose.** Un **rôle** ([SurfaceRole]) décide à lui seul :
 *  - son **rayon**, pris dans la famille de [OnyxSpace] (jamais un nombre écrit sur place) ;
 *  - son **fond**, pris dans l'échelle de [OnyxSurfaces] ;
 *  - s'il porte **le filet** — il n'y en a qu'un ([OnyxSurfaces.hairline], 1 dp) ;
 *  - s'il porte **l'ombre** — il n'y en a qu'une ([OnyxShadow.Float]).
 *
 * Un écran n'écrit donc plus ni rayon, ni filet, ni ombre : il écrit `surfaceRole(SurfaceRole.Float)`
 * et la forme est celle de **toutes** les surfaces de ce rôle. C'est la seule façon qu'une famille
 * ne dérive pas — la laisser à la discipline de chaque écran, c'est ce qui a produit le tableau
 * ci-dessus.
 *
 * **La règle du rayon suit la référence sur un point.** `ControlCorner` (9 dp) n'existe que pour les
 * **petits contrôles** (une touche, un badge) — un champ de recherche de 44 dp de haut n'en fait pas
 * partie, et lui donner ce coin le faisait ressembler à un bouton. Le champ prend [OnyxSpace.FieldCorner],
 * comme le champ du compositeur.
 *
 * **La capsule.** Un rayon ne peut pas exprimer « entièrement arrondi » : la capsule d'une barre
 * d'onglets ou d'un contrôle segmenté épouse la hauteur. Elle est donc un rôle à part
 * ([SurfaceRole.Capsule], [SurfaceRole.Segment]), et jamais un [RoundedCornerShape] de 50 recopié
 * sur place.
 */
enum class SurfaceRole {

    /** La page : ni fond propre, ni filet, ni ombre. */
    Page,

    /** Un **champ de saisie** — recherche, adresse, nom de projet. Creux + filet, jamais d'ombre. */
    Field,

    /** Une **capsule** segmentée ou un bouton en pilule : entièrement arrondi, fond de contrôle. */
    Capsule,

    /** Un **segment** à l'intérieur d'une capsule : le rayon suit l'emboîtement (`interior`). */
    Segment,

    /** Une **tuile** d'icône (volet, feuille, menu) : le coin de la vignette, un pas au-dessus. */
    Tile,

    /** Une **carte** — rangée de liste, cartouche de reprise, bloc de réponse. */
    Card,

    /**
     * Une surface qui **flotte** au-dessus du contenu : compositeur, barre d'onglets, feuille,
     * menu, alerte. C'est le seul rôle qui prend **le filet ET l'ombre**, et c'est ce qui le
     * distingue d'une carte.
     */
    Float,

    /** La pastille de l'**onglet actif** : capsule pleine, fond [OnyxSurfaces.capsule], sans filet. */
    ActiveCapsule,

    /**
     * Une **barre** flottante — barre du haut du fil, barre d'onglets.
     *
     * C'est le rôle qui manquait : les deux barres de l'app doivent être **la même forme** (une
     * capsule) et prendre **le même barreau** ([OnyxSurfaces.surfaceHigh]) que le compositeur. Un
     * en-tête écrit à part, c'est l'en-tête qui diverge — et c'est bien ce qui s'était passé (barre
     * du haut à 22 dp, barre d'onglets en capsule, compositeur à 22 dp puis 26).
     */
    Bar,
    ;

    /** Le rayon du rôle, **toujours** pris dans la famille de [OnyxSpace]. */
    val radius: Dp
        get() = when (this) {
            // Un champ et une capsule segmentée sont les deux formes « de saisie » de l'app.
            Field -> OnyxSpace.FieldCorner
            // Un segment vit DANS une capsule : la règle de l'emboîtement, pas un rayon de plus.
            Segment -> OnyxSpace.interior(OnyxSpace.PanelCorner, OnyxSpace.Hair)
            Tile -> OnyxSpace.ThumbnailCorner
            Card -> OnyxSpace.CardCorner
            Float -> OnyxSpace.PanelCorner
            // Une barre est **entièrement** arrondie : sa forme ignore le rayon, mais elle doit
            // rendre une marche de la famille si on l'interroge.
            Bar, Page, Capsule, ActiveCapsule -> OnyxSpace.FieldCorner
        }

    /** La forme du rôle. Les **barres** et les **capsules** sont entièrement arrondies (50 %). */
    val shape: Shape
        get() = when (this) {
            Capsule, ActiveCapsule, Bar -> RoundedCornerShape(50)
            else -> RoundedCornerShape(radius)
        }

    /** Vrai si le rôle porte **le** filet de l'app. */
    val hasHairline: Boolean
        get() = this == Field || this == Float || this == Card || this == Bar

    /** Vrai si le rôle porte **l'**ombre de l'app. */
    val hasShadow: Boolean
        get() = this == Float || this == Tile || this == Bar
}

/**
 * **L'ombre — une seule, pour toute l'app.**
 *
 * Le relevé a trouvé, dans les seuls écrans de la capture, **quatre** portées d'ombre (2, 4, 5, 12
 * dp) et deux ombres colorées, pour trois intentions. Une app qui a quatre ombres n'a pas de
 * profondeur : elle a quatre hasards. La maison n'en garde qu'**une** — [Float] — et elle sert les
 * deux seuls rôles qui se détachent du contenu ([SurfaceRole.Float], [SurfaceRole.Tile]).
 *
 * **Pourquoi 6 dp, et pas 12 ni 2.** En **clair**, c'est l'ombre qui fait exister une surface
 * flottante sur une page déjà presque blanche (Vercel : « cards feel built, not floating ») — il lui
 * faut donc de la portée. En **sombre**, une ombre ne se voit pas sur du quasi-noir : c'est la
 * luminance qui porte ([OnyxSurfaces.DarkSurfaceHigh], 1,314:1). 6 dp est la plus grande portée qui
 * reste une **ombre** sur un fond noir (au-delà, elle devient un halo gris) et la plus petite qui
 * détache encore une carte sur `#F7F7F9`.
 *
 * **Aucune ombre colorée.** L'ombre d'une surface est toujours noire, jamais teintée par l'accent :
 * une ombre prend la lumière de la scène, pas la couleur de l'objet.
 */
object OnyxShadow {
    /** La portée, en dp. La seule valeur d'ombre de l'app. */
    val Float: Dp = 6.dp

    /** L'alpha de l'ombre : noire en clair, plus dense en sombre (elle n'a presque rien à ombrer). */
    fun alpha(theme: ResolvedTheme): Float = if (theme.dark) 0.30f else 0.12f
}

/**
 * **La seule façon d'habiller une surface.** Elle applique, dans l'ordre : le fond du rôle (pris à
 * [OnyxSurfaces]), puis le filet s'il y en a un, puis l'ombre s'il y en a une — et **rien d'autre**.
 *
 * L'ordre compte : le filet est peint **au-dessus** du fond et **sous** l'ombre portée, sans quoi
 * l'ombre mangera les 1 dp du filet sur un bord.
 *
 * Un appelant qui a besoin d'un fond hors rôle (un aplat d'accent, une couleur de bot) garde
 * `.background(...)` : ce modificateur est pour les **surfaces**, pas pour les aplats de signal.
 */
@Composable
fun Modifier.surfaceRole(role: SurfaceRole): Modifier {
    val theme = LocalOnyxTheme.current
    return surfaceRole(role, theme)
}

/** Le même, avec le thème fourni : les appelants hors composition (aperçus, mesures) l'emploient. */
fun Modifier.surfaceRole(role: SurfaceRole, theme: ResolvedTheme): Modifier {
    if (role == SurfaceRole.Page) return this
    var modifier = this
    val fill = when (role) {
        SurfaceRole.Field -> OnyxSurfaces.well(theme)
        SurfaceRole.Capsule, SurfaceRole.Segment -> OnyxSurfaces.fill(theme)
        SurfaceRole.Tile -> OnyxSurfaces.tile(theme)
        SurfaceRole.Card -> OnyxSurfaces.surface(theme)
        SurfaceRole.Float, SurfaceRole.Bar -> OnyxSurfaces.surfaceHigh(theme)
        SurfaceRole.ActiveCapsule -> OnyxSurfaces.capsule(theme)
        SurfaceRole.Page -> OnyxSurfaces.page(theme)
    }
    if (role.hasShadow) {
        modifier = modifier.shadow(OnyxShadow.Float, role.shape, clip = false)
    }
    modifier = modifier.clip(role.shape).background(fill, role.shape)
    if (role.hasHairline) {
        modifier = modifier.border(OnyxSpace.Stroke, OnyxSurfaces.hairline(theme), role.shape)
    }
    return modifier
}
