package com.onyx.app.ui.localization

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.onyx.app.localization.OnyxLocalization
import androidx.compose.foundation.text.BasicText as AndroidBasicText

/**
 * `BasicText` avec la même API que Compose Foundation 1.8, plus un repli explicite pour le contenu
 * verbatim. Seules les clés connues de [OnyxLocalization] changent: le texte inconnu est inchangé.
 *
 * [localize] doit être `false` pour les messages, noms de bots, identifiants et réponses du serveur.
 * Cela évite qu'un contenu dynamique identique à un libellé (« Settings », par exemple) soit traduit.
 */
@Composable
fun BasicText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    color: ColorProducer? = null,
    autoSize: TextAutoSize? = null,
    localize: Boolean = true,
) {
    val localized = run {
        // Une configuration Android changée force la recomposition; la valeur affichée continue
        // néanmoins de suivre Locale.getDefault, comme le font les autres API Java de l'app.
        LocalConfiguration.current
        resolveOnyxText(text, localize)
    }
    AndroidBasicText(
        text = localized,
        modifier = modifier,
        style = style,
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        color = color,
        autoSize = autoSize,
    )
}

/** Les chaînes annotées conservent leurs intervalles et leur contenu: elles restent verbatim. */
@Composable
fun BasicText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    color: ColorProducer? = null,
    autoSize: TextAutoSize? = null,
) {
    AndroidBasicText(
        text = text,
        modifier = modifier,
        style = style,
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        inlineContent = inlineContent,
        color = color,
        autoSize = autoSize,
    )
}
