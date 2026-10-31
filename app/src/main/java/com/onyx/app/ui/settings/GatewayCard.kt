package com.onyx.app.ui.settings

import com.onyx.app.ui.design.OnyxSocle
import com.onyx.app.ui.design.socleShadow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxPillButton
import com.onyx.app.ui.design.OnyxInkOn
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.OnyxBrandMark
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.design.IdentifierText

/** La teinte du sous-titre d'état de la carte de passerelle. */
enum class GatewayTone { OK, BAD, IDLE }

/**
 * La **carte de la passerelle**, en tête des Réglages : la vignette d'icône teintée, l'adresse, le
 * sous-titre d'état, et le bouton « Vérifier » à droite — qui teste réellement la connexion
 * (adresse + clé) et rend le résultat sur la carte.
 *
 * La forme est celle de la référence : carte à **coins très arrondis** (20 dp) et **sans bordure** ;
 * la vignette porte la **marque du lanceur** — la plaque sombre, le cercle et la rune, aux couleurs
 * de l'icône de l'app — le logo de l'application, jamais une lettre ni une icône d'accent. Elle
 * portait l'initiale du serveur (« O » sur un carré bleu) ; une initiale ne dit pas *qui* parle,
 * elle dit seulement comment le serveur s'écrit aujourd'hui ; et « Vérifier » est une
 * **pilule à bordure** précédée de sa **petite icône d'onde**.
 *
 * Le sous-titre passe au vert quand la vérification a réussi, au rouge quand elle a échoué (avec le
 * motif exact), et reste gris tant qu'on n'a pas vérifié.
 */
@Composable
fun GatewayCard(
    name: String,
    address: String,
    status: String,
    tone: GatewayTone,
    verifying: Boolean,
    onVerify: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    // Le vert du système sur une carte blanche vaut 2,2:1 : le **texte** d'état prend donc une
    // encre qui tient 4,5:1, et la pastille de tête la même teinte écartée juste assez pour 3:1.
    val toneBase = when (tone) {
        GatewayTone.OK -> theme.success
        GatewayTone.BAD -> theme.error
        GatewayTone.IDLE -> theme.secondaryText
    }
    val statusColor = OnyxInkOn(toneBase, theme.formSurfaceColor, OnyxContrast.TEXT_MIN)
    val dotColor = OnyxInkOn(toneBase, theme.formSurfaceColor, OnyxContrast.ICON_MIN)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSocle.ScreenMargin)
            .padding(top = OnyxSocle.Loose)
            // La carte de la référence : coins très arrondis, **aucune bordure**. Et l'**ombre**
            // de la famille, **appelée pour de vrai** (trois couches dessinées) — sans cet appel la
            // carte reste plate, et rien ne ramène `SocleShadowKt` dans le binaire.
            .socleShadow(OnyxSocle.ShadowMid, OnyxSocle.RadiusCard, theme.dark)
            .background(theme.formSurfaceColor, RoundedCornerShape(20.dp))
            // Le rembourrage de la référence : le contrôle (« Vérifier ») est plus proche du bord
            // parce qu'il porte lui-même sa cible tactile.
            .padding(start = OnyxSocle.CardPadding, end = 4.dp, top = OnyxSocle.Tight, bottom = OnyxSocle.Tight),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OnyxSocle.IconLabelGap),
        ) {
            // **Le logo de l'application** : la marque du lanceur — la plaque sombre, le cercle et
            // la rune — aux couleurs de l'icône, **identiques en clair et en sombre**. Ce n'est pas
            // une icône d'accent : aucune teinte de thème n'entre dans ce dessin. La vignette
            // portait l'**initiale du serveur** (« O » sur un carré bleu) : une lettre n'est pas un
            // signe. Elle change avec la langue du serveur, elle vieillit avec son renommage, et à
            // 20 dp elle ne se distingue pas d'une autre initiale. Son côté est celui des vignettes
            // du socle (44 dp) : la cible du pouce.
            OnyxBrandMark(
                size = OnyxSocle.ThumbnailSize,
                modifier = Modifier.then(if (onOpen != null) Modifier.onyxTappable { onOpen() } else Modifier),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (onOpen != null) Modifier.onyxTappable { onOpen() } else Modifier),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BasicText(
                    text = name,
                    localize = false,
                    style = theme.subheadline.copy(color = theme.primaryText, fontWeight = FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IdentifierText(
                    text = address,
                    style = theme.footnote.copy(
                        color = OnyxInkOn(theme.secondaryText, theme.formSurfaceColor, OnyxContrast.TEXT_MIN),
                    ),
                )
            }
            VerifyButton(label = OnyxLocalization["Check"], busy = verifying, onClick = onVerify)
        }

        // Le sous-titre d'état : « Connecté », « Vérification… », ou le motif réel de l'échec. Il
        // part sous le libellé — donc après la vignette — comme la référence aligne son second
        // niveau de texte.
        Row(
            // Le retrait est **aligné sur le libellé** : la marge de la carte, la vignette, puis
            // l'écart icône-libellé — donc sur le **texte**, jamais sur la tuile.
            modifier = Modifier.padding(
                start = OnyxSocle.CardPadding + OnyxSocle.ThumbnailSize + OnyxSocle.IconLabelGap,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(dotColor, RoundedCornerShape(50)),
            )
            BasicText(
                text = status,
                localize = false,
                style = theme.footnote.copy(color = statusColor),
                maxLines = 2,
            )
        }
    }
}

/**
 * Le bouton « Vérifier » : une **pilule à bordure** avec sa petite **icône d'onde**, « Vérifier »
 * pendant le test — la forme que la référence donne au contrôle de sa carte de compte.
 */
@Composable
private fun VerifyButton(label: String, busy: Boolean, onClick: () -> Unit) {
    OnyxPillButton(
        text = label,
        symbol = Symbols.Waveform,
        onClick = onClick,
        busy = busy,
        description = OnyxLocalization["Check connection"],
    )
}
