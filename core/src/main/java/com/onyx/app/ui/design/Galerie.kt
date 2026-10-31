package com.onyx.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * **Les blocs du langage « Galerie », en composables — la couche visible de la planche.**
 *
 * Ce fichier ferme les trois écarts que le propriétaire a vus avant les autres :
 *
 * 1. **La carte de conversation** n'était pas une carte mais un **petit disque de 26 px**
 *    (`ToneDisc`). La planche montre une grande carte à **couverture générative pleine largeur**
 *    (une bande d'image en haut de carte), un titre, un aperçu et une méta en monospace. Un disque
 *    n'est pas une carte illustrée : c'est [ConversationCoverCard].
 * 2. **Les ombres n'étaient pas posées.** Les cartes étaient **plates** : le modificateur
 *    [socleShadow] n'avait **aucun appelant**, donc R8 l'avait retiré du binaire. Chaque carte de
 *    ce fichier **appelle** `socleShadow` — c'est ce qui le ramène dans le binaire, et la preuve
 *    est `mapping.txt` (voir le rapport).
 * 3. **Les blocs d'en-tête de la planche n'existaient pas** : le titre à ligne d'état, l'intitulé
 *    de section en petites capitales avec compteur en monospace. Ils sont ici.
 *
 * Un seul accent par écran : le filet signature sous le titre ([signature]), et rien d'autre. Les
 * pastilles sont neutres (la gorge du thème), l'épingle est en encre discrète.
 */

/** Le filet signature de la carte : 28 × 2, à l'accent, entre le titre et l'aperçu. */
@Composable
fun signatureBar(modifier: Modifier = Modifier): Modifier {
    val theme = LocalOnyxTheme.current
    return modifier
        .size(width = 28.dp, height = 2.dp)
        .clip(RoundedCornerShape(1.dp))
        .background(theme.accent)
}

/**
 * **La carte de conversation du langage Galerie.**
 *
 * La couverture est **pleine largeur**, en haut de carte, à la manière de la planche : c'est
 * [ConversationCover], l'usine à images déterministe (même titre + même identifiant → **la même
 * image au pixel**). Un voile sombre en pied de bande sépare l'image du corps de la carte.
 *
 * Le corps porte, dans cet ordre : le **titre** (15 sp), **l'épingle et le compteur sur la ligne du
 * titre**, le **filet signature**, l'**aperçu tronqué** (12 sp, une ligne) et la **méta en
 * monospace** (13 sp) — la dernière activité et le projet.
 *
 * La carte **appelle** `socleShadow` en profondeur lointaine ([OnyxSocle.ShadowFar], celle de la
 * planche pour une carte de conversation) : c'est ce que faisait défaut — la carte était plate.
 */
@Composable
fun ConversationCoverCard(
    title: String,
    id: String,
    preview: String?,
    metaLeft: String?,
    metaRight: String?,
    pinned: Boolean = false,
    count: Int? = null,
    /**
     * La hauteur de la **couverture**, c'est-à-dire de la carte entière : le texte est **posé
     * dessus**, il n'y a plus de corps sous l'image. La planche donne 192 px à cette carte
     * (`hscene.py:41`, `cover(M, 596, CW, 192, …)`).
     */
    coverHeight: Dp = 192.dp,
    /** L'état de la conversation (en cours, en attente, non lue), posé sur la couverture. */
    state: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    // **Le coin de la couverture : 28 — celui de la plaque de la planche.** Le brief du propriétaire
    // le nomme (rayon 28) ; le générateur donne `.card` à 20 (`hcss.py:116`). C'est le brief qui est
    // suivi ici, et l'écart est écrit dans le rapport.
    val shape = RoundedCornerShape(OnyxSocle.RadiusPanel)
    Box(
        modifier = modifier
            .fillMaxWidth()
            // **L'ombre, posée pour de vrai** : trois couches dessinées, la même famille que la
            // planche (`hlib.py`, `.sh3`). Sans cet appel, la carte est plate.
            .socleShadow(OnyxSocle.ShadowFar, OnyxSocle.RadiusPanel, theme.dark)
            .clip(shape)
            .then(if (onClick != null) Modifier.onyxClickable(onClick = onClick) else Modifier)
            .tapLabel(title),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(coverHeight)) {
            // **La couverture de la carte** : le fluide animé de la variante B quand il est actif
            // ([LocalFluideEnabled], l'interrupteur d'Apparence, vrai par défaut), l'ancienne
            // couverture déterministe sinon. Le disque d'état ne bouge pas et ne grossit pas : il
            // garde l'état, le fluide porte l'identité — les deux rôles ne se mélangent jamais.
            FluideConversationCover(
                title = title,
                id = id,
                modifier = Modifier.fillMaxSize(),
            )
            // Le voile de pied de la planche (`.cov .sc-b`, `hcss.py:156`) : la couverture
            // s'assombrit vers le bas pour que le texte blanc reste lisible dessus.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.44f),
                                Color.Black.copy(alpha = 0.94f),
                            ),
                        ),
                    ),
            )
            // ---- LE TEXTE, POSÉ **SUR** LA COUVERTURE (`hlib.py:548`, `.cvtx`) ---------------
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = OnyxSocle.ScreenMargin, vertical = OnyxSocle.Tight),
            ) {
                if (!preview.isNullOrBlank()) {
                    BasicText(
                        text = preview,
                        localize = false,
                        style = theme.caption.copy(
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.86f),
                            shadow = onCoverShadow,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(OnyxSpace.Hair))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
                ) {
                    BasicText(
                        text = title,
                        localize = false,
                        // `.cvttl` : 17 px, ExtraBold, blanc, ombré (`hcss.py:149`). Le 17 sp de la
                        // couverture est une **exception déclarée** du socle (voir `OnyxSocle`).
                        style = socleTitleStyle(theme).copy(
                            fontSize = 17.sp,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            shadow = onCoverShadow,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (pinned) {
                        OnyxIcon(
                            symbol = Symbols.Pin,
                            tint = Color.White.copy(alpha = 0.9f),
                            size = 14.dp,
                            contentDescription = null,
                        )
                    }
                    if (count != null && count > 0) SocleCountPill(count)
                }
                Box(modifier = signatureBar(Modifier).padding(top = OnyxSocle.Tight))
                if (!metaLeft.isNullOrBlank() || !metaRight.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = OnyxSocle.Tight),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
                    ) {
                        BasicText(
                            text = metaLeft.orEmpty(),
                            localize = false,
                            // `.cvm` : 12 px sur la couverture, jamais une méta de page.
                            style = socleMetaStyle(theme).copy(
                                color = Color.White.copy(alpha = 0.86f),
                                shadow = onCoverShadow,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (!metaRight.isNullOrBlank()) {
                            BasicText(
                                text = metaRight,
                                localize = false,
                                // `.cva mono` : la durée, à droite, en monospace (10 px).
                                style = socleMonoStyle(theme).copy(
                                    fontSize = OnyxSocle.Micro,
                                    color = Color.White.copy(alpha = 0.86f),
                                    shadow = onCoverShadow,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            // L'état de la conversation, posé sur la couverture : rien de ce que la rangée disait
            // n'est retiré, il change de place. L'écart au bord est **la constante de la maison**
            // (`IdentityMark.Inset`), jamais un nombre écrit ici.
            if (state != null) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(IdentityMark.Inset)) { state() }
            }
        }
    }
}

/**
 * **L'ombre du texte posé sur une couverture.** La planche ombre son titre d'une couche noire
 * (`hcss.py:151`, `text-shadow:0 1px 2px rgba(0,0,0,.55)`) : un texte blanc posé sur une image
 * quelconque a besoin de cette couche pour rester lisible, quelle que soit la matière derrière.
 */
private val onCoverShadow = Shadow(
    color = Color.Black.copy(alpha = 0.55f),
    offset = Offset(0f, 1f),
    blurRadius = 2f,
)

/**
 * **La ligne d'état du grand titre** : une pastille de présence, puis un texte — l'adresse de la
 * passerelle, un identifiant. C'est le bloc `headblk` de la planche (`.mt.addr`, avec son `.pdot`).
 */
@Composable
fun GalerieStatusLine(
    text: String,
    dot: Color,
    mono: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
    ) {
        Box(Modifier.size(7.dp).background(dot, CircleShape))
        BasicText(
            text = text,
            localize = false,
            style = (if (mono) socleMonoStyle(theme) else socleMetaStyle(theme))
                .copy(color = theme.secondaryText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * **L'intitulé de section** de la planche : des **petites capitales** ouvertes (+0,16 em), un
 * **compteur en monospace** à droite. C'est le bloc `.secrow` (`.sec` + `.cnt`).
 */
@Composable
fun GalerieSectionLabel(
    text: String,
    count: Int? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 24.dp)
            .padding(end = OnyxSocle.ScreenMargin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
    ) {
        BasicText(
            text = text.uppercase(),
            localize = false,
            style = socleSectionStyle(theme).copy(color = theme.secondaryText),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            BasicText(
                text = count.toString(),
                localize = false,
                style = socleMonoStyle(theme).copy(color = theme.secondaryText),
                maxLines = 1,
            )
        }
    }
}
