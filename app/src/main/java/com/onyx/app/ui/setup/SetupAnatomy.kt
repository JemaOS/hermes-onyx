package com.onyx.app.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.net.ConnectionProbe
import com.onyx.app.ui.design.IdentifierText
import com.onyx.app.ui.design.OnyxDisclosureSummary
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxInkOn
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.MotionFamily
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.launch

/**
 * **L'anatomie de la page de démarrage**, relevée sur la mise en route de la référence.
 *
 * La page de la référence tient en cinq pièces, dans cet ordre : l'icône de l'app **en haut à
 * gauche** (un petit carré arrondi, pas une illustration), un **très grand titre en gras**, une
 * **phrase de réassurance** en une ligne, puis **un champ** — libellé au-dessus, icône à gauche
 * (le globe), et **l'action de test dans le champ**, à droite (une pilule) — et sous le champ une
 * **ligne d'aide en gris**. Tout le reste est **replié** : les explications d'adressage, la
 * protection d'accès, et les réglages annexes.
 *
 * Ce fichier ne porte que ces pièces-là, et rien de la logique de l'écran : l'adresse testée, le
 * transport retenu et la sonde vivent dans `SetupScreen.kt`. Les couleurs viennent des rôles du
 * thème ([LocalOnyxTheme]) et les rayons de [OnyxSpace] — aucun nombre de forme ni de teinte n'est
 * écrit ici, donc la page suit le thème clair et le thème sombre sans une ligne de plus.
 */

/**
 * La ligne d'aide sous un champ : une phrase en gris, sans cadre — c'est ce qui remplace le mur
 * d'explications de l'écran d'avant (« Où tourne votre tableau de bord : LAN, Tailscale ou
 * HTTPS. »).
 */
@Composable
internal fun SetupHelpLine(text: String, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = text,
        style = theme.footnote.copy(
            // Le gris secondaire du système tombait sous 4,5:1 sur la page claire : la ligne d'aide
            // est une encre mesurée, comme les pieds de section de la maison.
            color = OnyxInkOn(theme.secondaryText, theme.formSurfaceColor, OnyxContrast.TEXT_MIN),
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSpace.CardPadding, vertical = OnyxSpace.Hair),
    )
}

/**
 * **Le champ de l'adresse, avec l'action de test dedans.**
 *
 * C'est la pièce que la référence apporte : le libellé au-dessus, le champ arrondi, le **globe à
 * gauche** (ce qu'on saisit est une adresse), et à droite, **dans** le champ, la pilule
 * « ⚡ Tester ». L'action est là où le regard est déjà — et l'écran économise une étape.
 *
 * Pendant le test, la pilule est visiblement **au repos** (estompée) et ne prend plus l'appui :
 * son libellé dit l'état (« Test en cours… ») et l'anneau tourne. Un appui qui ne ferait rien du
 * tout est exactement ce que le propriétaire signale.
 */
@Composable
internal fun SetupAddressField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    testing: Boolean,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * L'adresse porte elle-même un secret (le jeton du serveur de voix : `…/voices?token=…`) :
     * le champ se masque. Une adresse qui porte un secret **est** un secret.
     */
    secret: Boolean = false,
    /**
     * Le geste « Coller » **dans** le champ : ce qui est déjà dans le presse-papiers y est posé
     * d'un appui, puis testé. `null` cache la capsule (aucune adresse en attente de collage).
     */
    onPaste: (() -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSpace.CardPadding, vertical = OnyxSpace.Snug),
        verticalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
    ) {
        BasicText(
            text = label,
            style = theme.subheadline.copy(
                color = OnyxInkOn(theme.secondaryText, theme.formSurfaceColor, OnyxContrast.TEXT_MIN),
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SetupFieldHeight)
                .clip(RoundedCornerShape(OnyxSpace.FieldCorner))
                .background(theme.formSurfaceColor)
                // Le champ se lit par son **filet** : c'est la seule bordure de l'app
                // ([OnyxSurfaces.hairline]), celle qui porte aussi la piste du contrôle segmenté.
                .border(
                    OnyxSpace.Stroke,
                    OnyxSurfaces.hairline(theme),
                    RoundedCornerShape(OnyxSpace.FieldCorner),
                )
                .padding(horizontal = OnyxSpace.Half, vertical = OnyxSpace.Hair),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
        ) {
            OnyxIcon(Symbols.Globe, tint = theme.tertiaryText, size = 17.dp)
            OnyxTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                style = theme.body,
                // Le texte part de **gauche**, après le globe : c'est un champ, pas une valeur
                // de formulaire alignée à droite.
                alignment = TextAlign.Start,
                keyboardType = KeyboardType.Uri,
                secret = secret,
                // **Une adresse longue se relit.** Une passerelle réelle peut dépasser la largeur
                // du champ (`…an.trycloudflare.com`) : sur une seule ligne, on ne voyait que sa
                // fin et il était impossible de vérifier ce qu'on avait tapé. Le champ se replie
                // donc sur [SetupAddressLines] lignes — la valeur entière reste à l'écran.
                singleLine = false,
                maxLines = SetupAddressLines,
                // Le paramètre qui **replie pour de vrai** est `wrapLines` (voir [OnyxEditableValue]) :
                // au-delà de 1 la valeur est rendue par `OnyxWrappedEditableValue`, son début restant
                // sous les yeux. — apport du lot, gardé à côté de l'apport de la chaîne.
                wrapLines = SetupAddressLines,
                modifier = Modifier.weight(1f),
            )
            // « Coller » : la capsule de la maison, **dans le champ**, à côté de « Tester ». Un
            // appui pose le presse-papiers puis lance le test — c'est le geste que le mode
            // « Simple » doit proposer quand une adresse est déjà copiée.
            if (onPaste != null) {
                SetupFieldPill(
                    label = "Paste",
                    symbol = Symbols.DocOnClipboard,
                    enabled = !testing,
                    onClick = onPaste,
                )
            }
            // La pilule grise, dans le champ : la cible fait la hauteur voulue (44 dp) sans que le
            // champ cesse d'être un champ.
            Row(
                modifier = Modifier
                    .alpha(if (testing) 0.35f else 1f)
                    .heightIn(min = SetupTestPillHeight)
                    .clip(RoundedCornerShape(50))
                    .background(theme.fill)
                    .onyxTappable(enabled = !testing, onClick = onTest)
                    .padding(horizontal = OnyxSpace.Snug, vertical = OnyxSpace.Half),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Hair),
            ) {
                if (testing) {
                    SetupSpinner()
                } else {
                    OnyxIcon(Symbols.Bolt, tint = theme.accent, size = 13.dp)
                }
                BasicText(
                    text = if (testing) "Testing…" else "Test",
                    style = theme.footnote.copy(
                        color = theme.primaryText,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }
    }
}

/**
 * **Le champ de la clé**, la seconde moitié du mode « Simple » (« Adresse et clé »).
 *
 * Même anatomie que le champ d'adresse — libellé au-dessus, filet, icône à gauche, capsule
 * d'action **dans** le champ — mais la valeur est **entièrement masquée** : un secret ne se relit
 * jamais, il s'offre par la **copie** (la règle corrigée de la maison, sans icône œil). Le
 * presse-papiers se pose par un appui, comme au champ d'adresse.
 */
@Composable
internal fun SetupKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val clipboard = androidx.compose.ui.platform.LocalClipboard.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSpace.CardPadding, vertical = OnyxSpace.Snug),
        verticalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
    ) {
        BasicText(
            text = label,
            style = theme.subheadline.copy(
                color = OnyxInkOn(theme.secondaryText, theme.formSurfaceColor, OnyxContrast.TEXT_MIN),
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SetupFieldHeight)
                .clip(RoundedCornerShape(OnyxSpace.FieldCorner))
                .background(theme.formSurfaceColor)
                .border(
                    OnyxSpace.Stroke,
                    OnyxSurfaces.hairline(theme),
                    RoundedCornerShape(OnyxSpace.FieldCorner),
                )
                .padding(horizontal = OnyxSpace.Half, vertical = OnyxSpace.Hair),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
        ) {
            OnyxIcon(Symbols.KeyOutline, tint = theme.tertiaryText, size = 17.dp)
            OnyxTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                style = theme.body,
                alignment = TextAlign.Start,
                keyboardType = KeyboardType.Password,
                // **Entièrement masqué** : jamais la fin d'une clé, jamais un œil.
                secret = true,
                modifier = Modifier.weight(1f),
            )
            SetupFieldPill(
                label = "Paste",
                symbol = Symbols.DocOnClipboard,
                enabled = true,
                onClick = {
                    scope.launch {
                        val text = setupClipboardText(clipboard).trim()
                        if (text.isNotEmpty()) onValueChange(text)
                    }
                },
            )
            // La copie seule offre la valeur — le seul chemin, avec aucune icône œil.
            if (value.isNotEmpty()) {
                SetupFieldPill(
                    label = "Copy",
                    symbol = Symbols.DocOnDoc,
                    enabled = true,
                    onClick = {
                        clipboard.nativeClipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText(label, value),
                        )
                    },
                )
            }
        }
    }
}

/**
 * **Le bouton « Copier » de la barre de titre** : le lien de configuration de ce brouillon, prêt à
 * coller sur un autre appareil.
 *
 * Il vit **dans la barre**, pas dans le corps défilant : c'est ce qui le met à l'abri du défilement
 * (sur la capture du propriétaire, un tel bouton passait à moitié sous la barre de titre). Capsule
 * discrète, à la cible tactile de la maison, avec son libellé — jamais une icône nue.
 */
@Composable
internal fun SetupLinkCopyButton(onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    // **Le libellé seul, sans icône** : la barre porte aussi « Ignorer » et « Terminé », et à
    // police 1,3× sur un écran de 340 dp une icône de plus faisait perdre le titre de la page
    // (« Configurer On… »). Le mot reste, l'icône part.
    Box(
        modifier = Modifier
            .heightIn(min = SetupActionMinHeight)
            .clip(RoundedCornerShape(50))
            .background(theme.fill)
            .onyxTappable(onClick = onClick)
            .padding(horizontal = OnyxSpace.Half, vertical = OnyxSpace.Hair),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Copy",
            style = theme.footnote.copy(color = theme.primaryText, fontWeight = FontWeight.SemiBold),
        )
    }
}

/** La cible d'un contrôle de la barre de titre : la hauteur de la maison, jamais moins. */
internal val SetupActionMinHeight = 32.dp

/** Une capsule d'action **dans** un champ : icône + libellé, à la cible tactile de la maison. */
@Composable
private fun SetupFieldPill(
    label: String,
    symbol: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.35f)
            .heightIn(min = SetupTestPillHeight)
            .clip(RoundedCornerShape(50))
            .background(theme.fill)
            .onyxTappable(enabled = enabled, onClick = onClick)
            .padding(horizontal = OnyxSpace.Half, vertical = OnyxSpace.Half),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Hair),
    ) {
        OnyxIcon(symbol, tint = theme.accent, size = 13.dp)
        BasicText(
            text = label,
            style = theme.footnote.copy(color = theme.primaryText, fontWeight = FontWeight.SemiBold),
        )
    }
}

/**
 * **Une adresse proposée**, sous le champ : ce que l'installation connaît déjà, appuyable.
 *
 * C'est la réponse au « ça propose rien » : le mode « Simple » n'attend pas que l'utilisateur
 * devine son adresse, il la lui **offre** — celle qu'il vient de saisir, celles du réseau du
 * téléphone, celle de la passerelle qu'il utilisait, et celles de ses autres serveurs. Un appui
 * pose l'adresse dans le champ, comme un collage.
 */
@Composable
internal fun SetupSuggestionRow(
    address: String,
    source: String,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    OnyxRow(onClick = onClick) {
        OnyxIcon(Symbols.Globe, tint = theme.tertiaryText, size = 17.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(OnyxSpace.Hair),
        ) {
            IdentifierText(
                text = address,
                style = theme.body.copy(color = theme.primaryText),
            )
            BasicText(
                text = source,
                style = theme.footnote.copy(
                    color = OnyxInkOn(theme.secondaryText, theme.formSurfaceColor, OnyxContrast.TEXT_MIN),
                ),
            )
        }
    }
}

/**
 * **Le verdict du test, sous le champ** : une icône, l'état en un mot, la **latence réelle** à
 * droite, et la raison en dessous.
 *
 * « Connecté » et « En échec » ne suffisent pas : la raison est la phrase de la sonde ou du réseau
 * (« Rien n'a répondu à cette adresse »), et un succès qui ne dit pas combien de temps la passerelle
 * a mis ne prouve rien. Aucun secret ne passe par ici — la sonde ne rend qu'un message, une latence
 * et des noms de modèles.
 */
@Composable
internal fun SetupVerdict(result: ConnectionProbe, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    val ink = if (result.ok) theme.success else theme.error
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSpace.CardPadding, vertical = OnyxSpace.Hair),
        verticalArrangement = Arrangement.spacedBy(OnyxSpace.Hair),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
        ) {
            OnyxIcon(
                symbol = if (result.ok) Symbols.CheckmarkCircle else Symbols.XmarkOctagonFill,
                tint = ink,
                size = 17.dp,
            )
            BasicText(
                text = if (result.ok) "Connected" else "Failed",
                style = theme.subheadline.copy(color = ink, fontWeight = FontWeight.SemiBold),
            )
            Box(Modifier.weight(1f))
            BasicText(
                text = "${result.latencyMs} ms",
                localize = false,
                style = theme.footnote.copy(
                    color = OnyxInkOn(theme.secondaryText, theme.formSurfaceColor, OnyxContrast.TEXT_MIN),
                ),
            )
        }
        BasicText(
            text = result.message,
            // Le verdict de la sonde est un message de l'app : il se traduit comme le reste.
            localize = true,
            style = theme.footnote.copy(
                color = if (result.ok) {
                    OnyxInkOn(theme.secondaryText, theme.formSurfaceColor, OnyxContrast.TEXT_MIN)
                } else {
                    theme.error
                },
            ),
            // **Rien n'est coupé.** Quand la passerelle refuse, elle le dit — `HTTP 401
            // {"detail":"clé d'installation absente ou invalide"}` — et c'est cette phrase-là qui
            // nomme la cause. La rogner à trois lignes ne laissait voir que « Le serveur a renvoyé
            // HTTP 401 », et l'utilisateur repartait vérifier son adresse pour rien.
            // Le corps de la réponse du serveur doit tenir : un JSON d'erreur explicite est plus
            // long que trois lignes, et c'est lui qu'on veut lire. — le lot nomme la borne
            // ([SetupVerdictLines]), la chaîne en garde le nombre : douze lignes.
            maxLines = SetupVerdictLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * **Une section repliable** : le résumé — titre à gauche, chevron à droite qui se tourne d'un quart
 * de tour — et le contenu qui descend.
 *
 * C'est le rangement que la référence demande : les explications d'adressage, la protection
 * d'accès et les réglages annexes sont **tous là**, mais aucun n'est déplié d'avance. Rien n'est
 * supprimé : ce qui était un mur de texte devient une ligne qu'on ouvre.
 *
 * L'ouverture suit la famille de mouvement de la maison ([MotionFamily.Row], 180 ms — l'arrivée
 * d'une rangée) : sous « Réduire les animations », la spécification est `snap()` et le contenu est
 * là d'un coup.
 */
@Composable
internal fun SetupDisclosure(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OnyxFormSection(modifier = modifier, header = header, footer = footer) {
        OnyxDisclosureSummary(title = title, expanded = expanded, onClick = onToggle)
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = MotionFamily.Row.animated()) +
                expandVertically(animationSpec = MotionFamily.Row.animated()),
            exit = fadeOut(animationSpec = MotionFamily.Row.animated()) +
                shrinkVertically(animationSpec = MotionFamily.Row.animated()),
        ) {
            Column {
                OnyxRowDivider()
                content()
            }
        }
    }
}

/**
 * **Un sous-cas d'explication** : son titre, son exemple (une adresse, en chasse fixe — on la lit
 * pour la recopier), et la phrase qui dit quand l'utiliser.
 */
@Composable
internal fun SetupTopic(
    title: String,
    example: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSpace.CardPadding, vertical = OnyxSpace.Snug),
        verticalArrangement = Arrangement.spacedBy(OnyxSpace.Hair),
    ) {
        BasicText(
            text = title,
            style = theme.body.copy(color = theme.primaryText, fontWeight = FontWeight.SemiBold),
        )
        if (example.isNotEmpty()) {
            BasicText(
                text = example,
                // Une adresse s'affiche verbatim : elle n'est pas un libellé.
                localize = false,
                style = theme.monospacedFootnote.copy(
                    color = OnyxInkOn(theme.accentInk, theme.formSurfaceColor, OnyxContrast.TEXT_MIN),
                ),
            )
        }
        BasicText(
            text = detail,
            style = theme.footnote.copy(
                color = OnyxInkOn(theme.secondaryText, theme.formSurfaceColor, OnyxContrast.TEXT_MIN),
            ),
        )
    }
}

/**
 * **La note de bas de section** — un cadenas, une phrase. C'est elle qui dit où va un secret :
 * « Stocké chiffré sur ce téléphone, et envoyé uniquement à cette adresse. »
 */
@Composable
internal fun SetupNote(text: String, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSpace.CardPadding, vertical = OnyxSpace.Snug),
        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
        verticalAlignment = Alignment.Top,
    ) {
        OnyxIcon(Symbols.LockShield, tint = theme.tertiaryText, size = 14.dp)
        BasicText(
            text = text,
            style = theme.footnote.copy(
                color = OnyxInkOn(theme.secondaryText, theme.formSurfaceColor, OnyxContrast.TEXT_MIN),
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

/** Le côté du carré de l'icône en tête de page : petite, arrondie — jamais une illustration. */
internal val OnyxMarkSize = 30.dp

/**
 * Le coin de ce carré : un petit contrôle, donc [OnyxSpace.ControlCorner] — la même exception
 * nommée que les autres petits carrés de l'app. Sur 30 dp, le coin d'une carte (20 dp) en ferait
 * presque un disque.
 */
internal val OnyxMarkCorner = OnyxSpace.ControlCorner

/** La hauteur du champ : assez pour que la pilule de test y tienne à la cible tactile (44 dp). */
internal val SetupFieldHeight = 52.dp

/** La hauteur de la pilule « Tester » — la cible de la maison, jamais 38 dp mesurés à 48. */
internal val SetupTestPillHeight = 44.dp

/**
 * Les lignes du champ d'adresse : trois.
 *
 * Deux ne suffisaient pas — à **police 1,3×** une adresse de 49 caractères occupe trois lignes, et
 * le champ n'en montrait que deux : **son début restait hors champ**, exactement le défaut signalé.
 * Trois lignes la montrent entière, du `h` de `https` au `.com`.
 */
internal val SetupAddressLines = 3

/** Les lignes du verdict d'échec : la raison **et** le corps de la réponse du serveur (douze). */
internal val SetupVerdictLines = 12
