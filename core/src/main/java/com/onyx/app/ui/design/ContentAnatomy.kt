package com.onyx.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ReferencePalette
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.theme.ResolvedTheme
import com.onyx.app.ui.localization.BasicText

/**
 * **L'anatomie des écrans de contenu, relevée dans la source de la référence — pas estimée sur une
 * image.**
 *
 * La source est sur la machine (`/home/john/herald`, projet Compose Multiplatform). Chaque valeur
 * ci-dessous est citée avec son `fichier:ligne`. Là où la référence et la maison divergeraient, la
 * ligne le dit — un écart se déclare, il ne se tait pas.
 *
 * Relevé (ligne → valeur) :
 *
 * | rôle | source | valeur |
 * |---|---|---|
 * | page claire / carte / creux / tuile | `ThemeColors.kt:25-31` | `#F7F7F9` / `#FFFFFF` / `#F1F1F5` / `#F3F3F7` |
 * | page sombre / carte / creux | `ThemeColors.kt:11-15` | `#0A0A0C` / `#16161A` / `#121215` |
 * | filet | `ThemeColors.kt:18-19,30-31` | 1 dp, blanc 7 % (sombre) / encre 8 % (clair) |
 * | encre principale / secondaire / tertiaire | `ThemeColors.kt:20-23,32-35` | `#F4F4F6` / `#D4D4D8` / `#8A8A94` — `#0A0A0C` / `#27272A` / `#6B6B75` |
 * | accent (remplissage) / encre d'accent | `AccentPalette.kt`, `ThemeColors.kt:69-71` | `#2F6BF5` (Onyx a la même : `ReferencePalette.Accent`) |
 * | danger / succès / avertissement | `ThemeColors.kt:44-49` | `#F85149` / `#3FB950` / `#F5A524` — clair `#CF222E` / `#1A7F37` / `#B76E00` |
 * | rayons | `HermesTheme.kt:36-43` | champ 14 / tuile 10 / carte dans une liste 14 / carte 20 / panneau 26 / pilule 999 |
 * | pilules (hauteur, rembourrage, glyphe, libellé) | `components/Button.kt:48-52` | petit 36/14/15/13 — moyen 44/18/16/14 — grand 52/22/16/15 |
 * | cible tactile | `components/TouchTarget.kt:10` | **48 dp** |
 * | titre de page (grand) | `HermesTheme.kt:47` | 32 sp **SemiBold**, interlettrage **−0,03 em**, interligne 35 |
 * | titre de page (volet étroit < 360 dp) | `HermesTheme.kt:48` | 22 sp SemiBold, −0,02 em |
 * | sous-titre de page | `SessionsSidebar.kt:1115-1121` | 13,5 sp, interligne 19,5, encre tertiaire |
 * | libellé de section (« RUNS ») | `HermesTheme.kt:55` | 11 sp **SemiBold**, interlettrage **+0,08 em**, **majuscules** |
 * | corps / corps petit | `HermesTheme.kt:50-51` | 15 sp / 13,5 sp |
 * | **monospace** | `HermesTheme.kt:54` | **GeistMono 12,5 sp** — noms de compétences, chemins, identifiants (`CapabilitiesPage.kt:482`, `ScheduledPage.kt:325`) |
 * | carte | `components/Surface.kt:30-43` | rayon 20, **filet 1 dp**, **aucune ombre** — l'ombre (16 dp) est réservée aux panneaux flottants |
 * | carte de rangées | `CapabilitiesPage.kt:426-441` | une carte, **un filet de 1 dp entre les rangées**, bords à bords |
 * | rangée à vignette | `ScheduledPage.kt:303-315` | vignette **40 dp**, rayon **10 dp**, glyphe **17 dp**, rembourrage **14 dp**, écart 12 |
 * | rangée sans vignette (compétences, outils) | `CapabilitiesPage.kt:462-491` | min 56 dp, rembourrage h14 v12, écart 12, nom **en monospace** 13,5 sp Medium, détail 13,5/12,5 sp |
 * | pastille de comptage (« 2 tools ») | `CapabilitiesPage.kt:501-511` | 10,5 sp SemiBold, rayon capsule, rembourrage h7 v1, fond `surface3` (ou teinte d'accent si « appris ») |
 * | segmenté | `components/SegmentedControl.kt:75-141` | piste `well` **+ filet 1 dp**, hauteur ≥ 48 dp, capsule active = **blanc** (`thumb`), **filet 1 dp** `strokeStrong`, libellé 13 sp (SemiBold si actif), écart 6, compte **en monospace 11 sp** |
 * | champ de saisie | `components/TextField.kt:110-166` | rayon **14**, hauteur min **52**, rembourrage h16, filet 1 dp (`strokeStrong`), loupe 16 dp |
 * | barre flottante du bas | `SessionsSidebar.kt:1234-1277` | pilule d'accent **pleine largeur** (46 dp, rayon capsule, rembourrage h16 v8, libellé 14,5 sp SemiBold) + **rond de 46 dp** à droite, écart **10 dp**, marges h14 v8 |
 * | bouton rond | `components/IconButton.kt:36-41` | carré **48 dp**, rayon **14**, glyphe 20 dp |
 * | pied de section d'une feuille | `SessionControlPanel.kt:723-736` | libellé en petites capitales au-dessus d'une carte à filet |
 *
 * **Ce que la maison ne prend pas de la source, et pourquoi :**
 *  - la vignette de la référence mesure 40 dp (liste) et 34 dp (feuille) pour un même rôle. La maison
 *    a **une** vignette de rangée de contenu ([OnyxIconTile], 32 dp, coin 10, glyphe 15) — c'est le
 *    gabarit que la source emploie elle-même à `ConfigPage.kt:388` (32 dp, `radiusSmall`) — et
 *    garde les siennes pour la feuille ([RowThumbnail], 44 dp). Deux tailles pour deux contextes,
 *    pas quatre.
 *  - la source teinte la vignette d'une action destructive (`BottomSheet.kt:186`) : le propriétaire
 *    a explicitement demandé que le **danger soit porté par l'encre du libellé**, pas par un aplat
 *    (voir `ContentDangerInk`). La vignette reste **neutre** dans toute la maison.
 *  - la source écrit 32 sp pour le titre de page ; la maison a 34 sp (`largeTitle`) et l'emploie
 *    déjà partout (`OnyxLargePageHeader`). On garde **34 sp** — un titre de page qui changerait de
 *    taille selon l'écran serait exactement le défaut que ce lot corrige.
 */

/** La cible tactile de la maison et de la source : **48 dp** (`TouchTarget.kt:10`). */
val ContentTouchTarget: Dp = OnyxSpace.TouchTarget

/** Le côté du glyphe d'un bouton rond (`IconButton.kt:44` : `iconSize = 20.dp`). */
val ContentIconGlyph: Dp = 20.dp

/**
 * Le **grand titre** d'un écran de contenu : le **corps grand de l'échelle unique** de la maison —
 * `OnyxSocle.Display` (30 sp, `.dsp`), en SemiBold comme la source (`HermesTheme.kt:47`). Ce n'est
 * pas un corps improvisé : 30 sp **est** l'un des six corps déclarés (`30/26/16/15/12/11`), et il
 * suit le réglage de taille de texte de l'app comme tout le reste (`scaled`).
 */
private fun titleStyle(theme: ResolvedTheme): TextStyle =
    theme.largeTitle.copy(color = theme.primaryText, fontWeight = FontWeight.SemiBold)

/**
 * Le **libellé de section** en petites capitales grises de la source : 11 sp SemiBold, interlettrage
 * **+0,08 em**, majuscules (`HermesTheme.kt:55`). L'encre est mesurée sur la page — un gris tertiaire
 * posé tel quel tombe sous 4,5:1 sur quelques thèmes.
 *
 * @param localize le mot est-il un **libellé de la maison** (une clé du dictionnaire) ? Faux par
 *   défaut — plusieurs appels passent déjà leur texte traduit —, vrai pour les titres de section
 *   écrits en clair dans un écran, qui restaient anglais (« RUNS »).
 */
@Composable
fun ContentSectionLabel(text: String, modifier: Modifier = Modifier, localize: Boolean = false) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = if (localize) OnyxLocalization[text].uppercase() else text.uppercase(),
        localize = false,
        style = theme.caption2.copy(
            color = OnyxInkOn(theme.tertiaryText, theme.formPageColor, TextInkTarget),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.08.em,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * L'**en-tête d'un écran de contenu** : la flèche de retour seule en haut, à gauche ; les actions
 * contextuelles à droite, et le contrôle du volet **séparé**, tout à droite ; puis le grand titre en
 * gras, aligné à gauche, et son sous-titre gris dessous.
 *
 * C'est la forme de `SubpageHeader` de la source (`SessionsSidebar.kt:1087-1125`) : ligne d'actions
 * (rembourrage 4/8/4, écart 4, retour à gauche, actions poussées à droite), titre `display` sur deux
 * lignes au plus, sous-titre de 13,5 sp en encre tertiaire. La maison ne change que le style du
 * titre (34 sp au lieu de 32) et **n'ajoute rien d'autre** : ni barre, ni pastille, ni ombre.
 *
 * @param panel le contrôle du volet : il vit à l'extrême droite, après un écart franc, pour qu'un
 *   appui manqué sur une action ne replie pas la liste.
 */
@Composable
fun ContentPageHeader(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    localizeTitle: Boolean = true,
    localizeSubtitle: Boolean = false,
    panel: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    Column(modifier = modifier.fillMaxWidth().padding(bottom = OnyxSpace.GlyphGap + OnyxSpace.Snug)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = OnyxSpace.Hair, end = OnyxSpace.Half, top = OnyxSpace.Hair),
            horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Hair),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                ContentIconButton(
                    symbol = Lucide.ArrowLeft,
                    description = "Back",
                    onClick = onBack,
                    tint = theme.secondaryText,
                )
            } else {
                Spacer(Modifier.size(ContentTouchTarget))
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Hair, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions?.invoke(this)
            }
            if (panel != null) {
                // Le contrôle du volet est **séparé** des actions contextuelles : c'est une autre
                // famille de geste (replier la liste, pas agir sur l'objet).
                Spacer(Modifier.width(OnyxSpace.Half))
                panel()
            }
        }
        BasicText(
            text = title,
            localize = localizeTitle,
            style = titleStyle(theme),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = OnyxSpace.CardPadding, end = OnyxSpace.CardPadding, top = OnyxSpace.Hair)
                .semantics { heading() },
        )
        subtitle?.takeIf { it.isNotBlank() }?.let {
            BasicText(
                text = it,
                localize = localizeSubtitle,
                style = theme.subheadline.copy(
                    fontSize = OnyxSocle.Meta,
                    color = OnyxInkOn(theme.tertiaryText, theme.formPageColor, TextInkTarget),
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    start = OnyxSpace.CardPadding,
                    end = OnyxSpace.CardPadding,
                    top = OnyxSpace.GlyphGap,
                ),
            )
        }
    }
}

/**
 * Un **bouton rond** de la famille : carré de 48 dp, rayon 14 (`IconButton.kt:36-41`), glyphe 20 dp
 * en encre secondaire. C'est le même objet pour le retour d'un en-tête, l'action contextuelle d'une
 * ligne et le rond de la barre flottante — la source n'en a qu'un.
 */
@Composable
fun ContentIconButton(
    symbol: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    glyph: Dp = ContentIconGlyph,
    rotate: Float = 0f,
) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = modifier
            .size(ContentTouchTarget)
            .clip(RoundedCornerShape(OnyxSpace.FieldCorner))
            .then(if (enabled) Modifier.onyxTappable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        OnyxIcon(
            symbol = symbol,
            tint = tint ?: theme.secondaryText,
            size = glyph,
            contentDescription = description,
            modifier = if (rotate == 0f) Modifier else Modifier.rotate(rotate),
        )
    }
}

/** La famille des pilules de la source (`Button.kt:46`) : le rôle décide du fond et de l'encre. */
enum class PillVariant {
    /**
     * **L'action principale d'un écran** : le remplissage d'accent de la source (`Button.kt:73`).
     * C'est la **seule** couleur saturée admise par écran, et elle sert la création
     * (« Nouvelle tâche », « Nouveau projet »).
     */
    Primary,

    /**
     * **L'action principale d'une carte** : le remplissage **inversé** (`Button.kt:78`) — noir en
     * clair, blanc en sombre. C'est le bouton que le propriétaire a relevé sur la référence pour
     * « ⚡ Run now ».
     */
    Inverse,

    /** **L'action secondaire** : le troisième barreau de surface (`Button.kt:74`), encre du texte. */
    Secondary,

    /** Un contour de 1 dp (`Button.kt:80-82`), sans fond — le « Test » d'une ligne. */
    Outline,

    /** Sans fond ni contour : « Annuler », une sortie. */
    Ghost,

    /** Un geste qui ne se rattrape pas. Le fond reste celui du thème : c'est l'**encre** qui dit le danger. */
    Danger,
}

/** Les trois tailles de la source (`Button.kt:48-52`) — hauteurs exactes, **corps du socle**. */
enum class PillSize(val height: Dp, val horizontalPadding: Dp, val glyph: Dp, val fontSize: TextUnit) {
    // La hauteur et le dégagement restent ceux de la source ; le corps du libellé, lui, ne sort
    // pas des six de l'app : 13 et 14 étaient deux corps qui n'existaient qu'ici.
    Small(36.dp, 14.dp, 15.dp, OnyxSocle.Meta),
    Medium(44.dp, 18.dp, 16.dp, OnyxSocle.Body),
    Large(52.dp, 22.dp, 16.dp, OnyxSocle.Body),
}

/**
 * **La pilule** : la forme unique des boutons de la maison et de la source. Entièrement arrondie,
 * un fond plat, un libellé médian ; la cible tactile fait 48 dp de haut même quand la pilule dessinée
 * en fait 36 (`TouchTarget.kt:10`), pour que la règle des 48 dp du brief soit tenue partout.
 *
 * Le libellé peut se replier sur deux lignes à l'intérieur de la pilule, comme la source
 * (`Button.kt:121-124`) : à police 1,3×, « Réessayer la connexion » ne tient pas sur une ligne, et
 * le rogner au milieu d'un mot est exactement le défaut relevé sur la feuille de la connexion.
 */
@Composable
fun ContentPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PillVariant = PillVariant.Primary,
    size: PillSize = PillSize.Medium,
    enabled: Boolean = true,
    symbol: Int? = null,
    description: String? = null,
    /**
     * Le libellé est-il un **libellé de la maison** (une clé du dictionnaire) plutôt qu'une donnée ?
     *
     * Faux par défaut : la plupart des pilules portent un mot choisi par l'appelant (un nom de bot,
     * une valeur). Les pilules **fixes** des écrans de contenu (« Run now », « Pause », « New job »)
     * le passent à vrai — sans quoi le mot restait anglais au milieu d'une appli française, ce qui est
     * exactement le défaut relevé (« Run now », « Pause » sur la carte d'une tâche planifiée).
     */
    localize: Boolean = false,
) {
    val theme = LocalOnyxTheme.current
    val accentFill = OnyxContrast.filled(theme.accent)
    val (container, content) = when (variant) {
        PillVariant.Primary -> accentFill to OnyxContrast.inkOn(accentFill)
        // L'inverse de la source : `inverse` = encre principale, `onInverse` = la page.
        PillVariant.Inverse -> theme.primaryText to theme.pageColor
        PillVariant.Secondary -> OnyxSurfaces.surface3(theme) to theme.textColor
        PillVariant.Outline -> Color.Transparent to theme.textColor
        PillVariant.Ghost -> Color.Transparent to theme.textColor
        // Le danger **en encre** : jamais un aplat rouge (demande explicite du propriétaire).
        PillVariant.Danger -> OnyxSurfaces.surface3(theme) to OnyxContrast.distinctOn(
            theme.error,
            OnyxSurfaces.surface3(theme),
            OnyxContrast.TEXT_MIN,
        )
    }
    // L'encre calculée est posée sur le fond composé : un blanc posé sur l'accent pâle d'un thème
    // clair tombe sous 4,5:1, et une pilule illisible est pire qu'une pilule sans couleur.
    val ink = if (variant == PillVariant.Primary || variant == PillVariant.Inverse) content
    else OnyxContrast.distinctOn(OnyxContrast.over(content, container), OnyxContrast.over(container, theme.pageColor), OnyxContrast.TEXT_MIN)
    val shape = CircleShape
    Box(
        modifier = modifier
            .heightIn(min = ContentTouchTarget)
            .then(if (enabled) Modifier.onyxTappable(onClick = onClick) else Modifier)
            .semantics { description?.let { contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = size.height)
                .then(
                    if (variant == PillVariant.Outline) {
                        Modifier.border(OnyxSpace.Stroke, OnyxSurfaces.lineStrong(theme), shape)
                    } else {
                        Modifier
                    },
                )
                .clip(shape)
                .background(container, shape)
                .padding(horizontal = size.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            symbol?.let {
                OnyxIcon(symbol = it, tint = ink, size = size.glyph, contentDescription = null)
            }
            BasicText(
                text = text,
                localize = localize,
                style = theme.footnote.copy(
                    fontSize = size.fontSize,
                    color = ink,
                    textAlign = TextAlign.Center,
                    fontWeight = when (variant) {
                        PillVariant.Primary, PillVariant.Inverse, PillVariant.Danger -> FontWeight.SemiBold
                        else -> FontWeight.Medium
                    },
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/**
 * **La pastille de comptage** de la source (`CapabilitiesPage.kt:501-511`) : 10,5 sp SemiBold dans
 * une capsule du troisième barreau — « 2 tools », « hub », « appris ».
 *
 * Elle **n'invente jamais un chiffre** : l'appelant ne l'affiche que s'il a la donnée (un compte
 * réel de la liste chargée). Un compteur qui ne viendrait pas de la donnée serait un mensonge, et
 * c'est la règle du brief.
 */
@Composable
fun ContentCountBadge(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val theme = LocalOnyxTheme.current
    val ground = if (accent) OnyxContrast.over(theme.accent.copy(alpha = 0.14f), theme.surfaceColor) else OnyxSurfaces.surface3(theme)
    val ink = if (accent) {
        OnyxContrast.distinctOn(theme.accent, ground, OnyxContrast.TEXT_MIN)
    } else {
        OnyxContrast.distinctOn(theme.textColor, ground, OnyxContrast.TEXT_MIN)
    }
    BasicText(
        text = text,
        localize = false,
        style = theme.caption.copy(fontSize = OnyxSocle.Micro, fontWeight = FontWeight.SemiBold, color = ink),
        maxLines = 1,
        modifier = modifier
            .background(ground, CircleShape)
            // Le rembourrage vertical de la maison : 2 dp, jamais 1 (voir [BadgeGeometry]).
            .padding(horizontal = 7.dp, vertical = BadgeGeometry.PadVertical),
    )
}

/**
 * L'**encre monospace** de tout ce qui est technique : un nom de compétence, un nom d'outil, un
 * chemin, un identifiant. C'est la signature que le propriétaire a relevée sur la source
 * (`HermesTheme.kt:54` : GeistMono 12,5 sp ; `CapabilitiesPage.kt:482` l'emploie pour le nom d'une
 * compétence, `ScheduledPage.kt:325` pour une heure de prochaine exécution).
 *
 * La maison emploie sa propre monospace embarquée (`theme.codeFont`), 13 sp — même rôle, police de
 * la maison : `theme.monospacedFootnote`.
 */
@Composable
fun contentMonoStyle(
    fontWeight: FontWeight = FontWeight.Medium,
    color: Color? = null,
): TextStyle {
    val theme = LocalOnyxTheme.current
    return theme.monospacedFootnote.copy(color = color ?: theme.textColor, fontWeight = fontWeight)
}

/**
 * **La carte à filet fin** des écrans de contenu : le barreau « surface » (blanc en clair), le coin
 * des cartes (20 dp), **le filet de 1 dp** et **aucune ombre** — c'est la source telle qu'elle est
 * écrite (`components/Surface.kt:30-43` : « flat by default (fill + one hairline) », l'ombre étant
 * réservée aux panneaux flottants).
 *
 * **Le rembourrage est le jeton**, [OnyxSpace.CardPadding] (16 dp), et non plus le 14 dp recopié de
 * la source (`ScheduledPage.kt:362`). Deux raisons, et la seconde décide :
 *
 *  - la maison a **un** rembourrage de carte depuis le lot d'espacement (`AttachmentSheet`, `OnyxMenu`,
 *    `OnyxForm`, carte de bot), et une carte de contenu qui gardait 14 en était la seule exception ;
 *  - surtout, la carte **grandit avec son contenu**. Le rembourrage posé **après** le fond, la forme
 *    épouse la hauteur mesurée (texte + commandes + rembourrage) : une carte qui porte deux pilules
 *    de 48 dp est donc haute de `CardPadding + 48 + CardPadding`, et **rien** n'en dépasse. Une
 *    valeur écrite à la main ici avait produit la carte dont les boutons sortaient par le bas —
 *    le défaut relevé par le propriétaire (« on dirait que les bords sont coupés »).
 */
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    padding: Dp = OnyxSpace.CardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .surfaceRole(SurfaceRole.Card)
            .padding(padding),
        content = content,
    )
}

/**
 * Le **filet entre deux rangées d'une même carte** : en retrait des deux bords, et **en retrait
 * après la vignette** quand la rangée en porte une (`OnyxSpace.separatorInset`). Aucune bordure
 * entre les rangées : c'est le filet qui sépare, exactement comme la source
 * (`CapabilitiesPage.kt:437`).
 */
@Composable
fun ContentRowDivider(withTile: Boolean = true) {
    OnyxRowDivider(
        inset = if (withTile) OnyxSpace.separatorInset(OnyxAnatomyTileSize) else OnyxSpace.CardPadding,
        endInset = OnyxSpace.CardPadding,
    )
}

/** Le côté de la vignette de rangée de contenu : celui de l'anatomie ([Anatomy.TileSize], 32 dp). */
val OnyxAnatomyTileSize: Dp = Anatomy.TileSize

/**
 * **Une rangée de contenu** : la vignette grise arrondie (ou rien), le titre en gras, la ligne
 * secondaire, et **à droite un état** — « done », « in 26m », « 29 msgs ».
 *
 * C'est la rangée que le propriétaire a relevée sur la référence (`ScheduledPage.kt:295-330`) :
 * la vignette d'abord, le nom en gras, la ligne secondaire en gris dessous, puis la colonne de
 * droite, alignée à droite, dont **la première ligne est en monospace** (l'heure, le compte) et la
 * seconde en petites capitales muettes (« next run », « last run »).
 *
 * @param monoTitle un nom **technique** (compétence, outil, chemin) : il s'écrit en monospace.
 * @param status l'état, en monospace — `null` quand la donnée n'existe pas : on n'invente rien.
 * @param statusLabel ce que l'état désigne (« next run »), sous lui, en gris muet.
 */
@Composable
fun ContentRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleMono: Boolean = false,
    monoTitle: Boolean = false,
    symbol: Int? = null,
    badge: (@Composable () -> Unit)? = null,
    status: String? = null,
    statusLabel: String? = null,
    statusTint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    localizeTitle: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = OnyxSpace.RowHeight)
            .then(if (onClick != null) Modifier.onyxTappable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = OnyxSpace.Snug),
        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Snug),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        symbol?.let { OnyxIconTile(it) }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OnyxSpace.GlyphGap)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = title,
                    localize = localizeTitle,
                    style = if (monoTitle) {
                        contentMonoStyle(FontWeight.Medium, theme.primaryText)
                    } else {
                        theme.body.copy(color = theme.primaryText, fontWeight = FontWeight.Medium)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                badge?.invoke()
            }
            subtitle?.takeIf { it.isNotBlank() }?.let {
                BasicText(
                    text = it,
                    localize = false,
                    style = if (subtitleMono) {
                        contentMonoStyle(FontWeight.Normal, theme.tertiaryText).copy(fontSize = OnyxSocle.Meta)
                    } else {
                        theme.footnote.copy(
                            fontSize = OnyxSocle.Meta,
                            color = OnyxInkOn(theme.tertiaryText, cardGround(theme), TextInkTarget),
                        )
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (status != null) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(OnyxSpace.GlyphGap)) {
                BasicText(
                    text = status,
                    localize = false,
                    style = contentMonoStyle(FontWeight.Normal, statusTint ?: theme.secondaryText).copy(fontSize = 12.sp),
                    maxLines = 1,
                )
                statusLabel?.takeIf { it.isNotBlank() }?.let {
                    BasicText(
                        text = it,
                        localize = false,
                        style = theme.caption2.copy(fontSize = 11.sp, color = OnyxInkOn(theme.tertiaryText, cardGround(theme), TextInkTarget)),
                        maxLines = 1,
                    )
                }
            }
        }
        trailing?.invoke()
    }
}

/** Le barreau de surface d'une carte : le fond sur lequel une ligne secondaire se mesure. */
private fun cardGround(theme: ResolvedTheme): Color = OnyxSurfaces.surface(theme)

/**
 * **La barre flottante du bas** : la pilule d'accent pleine largeur (« ✎ Nouvelle conversation ») et
 * **le rond de 46 dp à sa droite**, posés au-dessus du contenu qui défile derrière.
 *
 * Relevé de la source (`SessionsSidebar.kt:1234-1277`) : marge h14 v8, écart 10 dp entre la pilule
 * et le rond, pilule de 46 dp de haut (cible 48), libellé 14,5 sp SemiBold, rond de 46 dp. La
 * source la pose **au-dessus** d'une bande opaque qui prend le fond de la page : c'est ce qui fait
 * qu'aucune ligne ne passe derrière la barre.
 *
 * @param secondarySymbol l'action du rond (la source y met l'avatar du compte ; les écrans de
 *   contenu y mettent leur geste secondaire — un rond **sans action** ne serait pas dessiné).
 */
@Composable
fun ContentFloatingBar(
    label: String,
    symbol: Int,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondarySymbol: Int? = null,
    secondaryDescription: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    val accentFill = OnyxContrast.filled(theme.accent)
    val ink = OnyxContrast.inkOn(accentFill)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = OnyxSpace.Hair, bottom = OnyxSpace.Half),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ContentTouchTarget)
                .onyxTappable(onClick = onPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .clip(CircleShape)
                    .background(accentFill, CircleShape)
                    .padding(horizontal = OnyxSpace.CardPadding, vertical = OnyxSpace.Half),
                horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OnyxIcon(symbol = symbol, tint = ink, size = 16.dp, contentDescription = null)
                BasicText(
                    text = label,
                    localize = false,
                    style = theme.body.copy(fontSize = OnyxSocle.Body, fontWeight = FontWeight.SemiBold, color = ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (secondarySymbol != null && onSecondary != null) {
            Box(
                modifier = Modifier
                    .size(ContentTouchTarget)
                    .onyxTappable(onClick = onSecondary),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(OnyxSurfaces.surface3(theme), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    OnyxIcon(
                        symbol = secondarySymbol,
                        tint = OnyxContrast.distinctOn(theme.textColor, OnyxSurfaces.surface3(theme), OnyxContrast.ICON_MIN),
                        size = 20.dp,
                        contentDescription = secondaryDescription,
                    )
                }
            }
        }
    }
}

/**
 * **Le fond d'une barre flottante** : le contenu passe **derrière** la barre, jamais sous elle. La
 * source fait défler sa liste sous un fondu opaque (`SessionsSidebar.kt:1230`) ; ici, la barre est
 * posée dans une boîte dont le fond est celui de la page — c'est ce qui fait qu'une ligne ne se
 * devine plus derrière un bouton.
 */
@Composable
fun ContentFloatingScaffold(
    modifier: Modifier = Modifier,
    bar: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Box(modifier = modifier.fillMaxWidth()) {
        content()
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(theme.formPageColor),
        ) {
            bar()
        }
    }
}

/** La teinte du danger telle que le propriétaire la veut : sur **l'encre**, jamais sur un aplat. */
fun contentDangerInk(theme: ResolvedTheme, ground: Color): Color =
    OnyxContrast.distinctOn(theme.error, ground, OnyxContrast.TEXT_MIN)

/** La palette de la source, exposée pour les mesures et les tests (une seule source de vérité). */
object ContentAnatomy {
    /** Les valeurs de la source, telles quelles — citées par le tableau du rapport. */
    val SourceTitleSp = 32f
    val SourceTitleLetterSpacingEm = -0.03f
    val SourceEyebrowSp = 11f
    val SourceEyebrowLetterSpacingEm = 0.08f
    val SourceSubtitleSp = 13.5f
    val SourceMonoSp = 12.5f
    val SourceRadiusField = 14.dp
    val SourceRadiusCard = 20.dp
    val SourceRadiusPanel = 26.dp
    val SourceRadiusTile = 10.dp
    val SourceTileSize = 40.dp
    val SourceSheetTileSize = 34.dp
    val SourceHairline = 1.dp
    val SourceFloatingPillHeight = 46.dp
    val SourceTouchTarget = 48.dp
    val SourcePageLight = ReferencePalette.LightPage
    val SourceSurfaceLight = ReferencePalette.LightSurface
    val SourceLineLight = ReferencePalette.LightLineStrong
    val SourceAccent = ReferencePalette.Accent
}
