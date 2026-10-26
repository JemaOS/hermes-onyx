package com.onyx.app.ui.chat.components

import android.content.ClipData
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.data.link.LinkPreview
import com.onyx.app.data.link.LinkPreviewLinks
import com.onyx.app.data.link.LinkPreviews
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * L'adresse dont la fiche se dessine sous un message, ou `null`.
 *
 * Comme les conditions de l'aperçu de lien et de `LinkPreviewSlot`:
 * une fiche seulement **une fois la réponse finie**, jamais sur une note de pilotage ni une erreur,
 * jamais sous un message sans texte, et seulement avec l'interrupteur allumé. Sans l'autorisation,
 * aucune adresse ne remonte — donc aucune requête réseau.
 */
internal fun linkPreviewUrl(message: ChatMessage, isLive: Boolean, linkPreviews: Boolean): String? {
    if (!linkPreviews) return null
    if (isLive || message.isSteer || message.error != null) return null
    if (message.text.isEmpty()) return null
    return LinkPreviewLinks.first(message.text)
}

/**
 * Le travail d'une fiche: si [message] doit en avoir une, lit la page une fois.
 *
 * Isolé pour être éprouvable hors Compose: réglage éteint ⇒ aucune requête part ([previews] n'est
 * même pas touché).
 */
internal suspend fun requestLinkPreview(
    previews: LinkPreviews,
    message: ChatMessage,
    isLive: Boolean,
    linkPreviews: Boolean,
) {
    val url = linkPreviewUrl(message, isLive, linkPreviews) ?: return
    previews.request(url)
}

/**
 * Sous un message avec un lien, l'interrupteur allumé: la page du premier lien en fiche.
 * (`LinkPreviewSlot`, — la fiche lit le réglage elle-même, donc l'allumer
 * ou l'éteindre montre ou cache toutes les fiches sans redessiner les messages.)
 */
@Composable
fun LinkPreviewSlot(
    message: ChatMessage,
    isLive: Boolean,
    linkPreviews: Boolean,
    modifier: Modifier = Modifier,
) {
    val url = linkPreviewUrl(message, isLive, linkPreviews) ?: return
    LinkPreviewCard(url = url, modifier = modifier)
}

/**
 * La fiche d'une page: son image ou son icône, son titre et son site.
 *
 * Comme `LinkPreviewCard`. Toujours la même hauteur, chargée ou non,
 * pour qu'une fiche qui se remplit ne déplace pas la conversation sous celui qui la relit. Tant que
 * la page n'a pas été lue, ou si elle ne peut pas l'être, la fiche nomme le site.
 */
@Composable
fun LinkPreviewCard(url: String, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // La fiche suit la taille du texte: `@ScaledMetric(relativeTo: .subheadline)` , que le
    // `.dynamicTypeSize` du fil (ici `theme.fontScale` = « Chat text size ») fait grandir aussi.
    val scale = LocalDensity.current.fontScale * theme.fontScale
    val height = 72.dp * scale
    val side = 52.dp * scale

    var preview by remember(url) { mutableStateOf(LinkPreviews.shared.preview(url)) }
    LaunchedEffect(url) {
        LinkPreviews.shared.request(url)
        preview = LinkPreviews.shared.preview(url)
    }
    var menuOpen by remember { mutableStateOf(false) }

    val site = preview?.site ?: LinkPreviews.site(LinkPreviewLinks.host(url) ?: url)
    val open = { openExternalUrl(context, url) }
    val shape = RoundedCornerShape(12.dp)
    val label = preview?.title?.let { "$it, $site" } ?: site

    Row(
        modifier = modifier
            .widthIn(max = 340.dp)
            .height(height)
            .clip(shape)
            .background(theme.surfaceColor.copy(alpha = 0.6f))
            .border(1.dp, theme.quaternaryText, shape)
            .onyxTappable(onLongPress = { menuOpen = true }, onClick = open)
            .semantics { contentDescription = label }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LinkPreviewPicture(preview = preview, side = side)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                text = preview?.title ?: site,
                localize = false,
                style = theme.subheadline.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (preview?.title != null) {
                BasicText(
                    text = site,
                    localize = false,
                    style = theme.caption.copy(color = theme.secondaryText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (menuOpen) {
        OnyxMenu(
            items = listOf(
                MenuItem(label = "Open", symbol = Symbols.Safari) {
                    menuOpen = false
                    open()
                },
                MenuItem(label = "Copy Link", symbol = Symbols.DocOnDoc) {
                    menuOpen = false
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(url, url))) }
                },
            ),
            onDismiss = { menuOpen = false },
        )
    }
}

/** L'image de la page (carré arrondi de 8), ou son icône de lien sur fond d'accent. */
@Composable
private fun LinkPreviewPicture(preview: LinkPreview?, side: Dp) {
    val theme = LocalOnyxTheme.current
    val bytes = preview?.imageBytes
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = bytes) {
        value = bytes?.let { image ->
            withContext(Dispatchers.Default) {
                runCatching { BitmapFactory.decodeByteArray(image, 0, image.size)?.asImageBitmap() }.getOrNull()
            }
        }
    }

    Box(
        modifier = Modifier.size(side).clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(theme.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                OnyxIcon(Symbols.Link, tint = theme.accent, size = 20.dp)
            }
        }
    }
}

/** Un tap ouvre la page dans le navigateur: `Intent.ACTION_VIEW`, comme `OpenURL`. */
private fun openExternalUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)),
        )
    }
}
