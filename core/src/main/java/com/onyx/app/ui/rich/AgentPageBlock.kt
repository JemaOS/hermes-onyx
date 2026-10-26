package com.onyx.app.ui.rich

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Les paliers de « A− » / « A+ »: la taille de base de la page, jamais libre entre deux. */
private val PageZoomFactors = listOf(0.85f, 1f, 1.15f)

/** L'échec d'une page, au mot (`AgentPage`). */
private const val PageLoadFailure = "This page didn't load."

/**
 * Une page HTML produite par l'agent (`AgentPage`).
 *
 * Rendu hors ligne dans la même vue que les diagrammes, à la hauteur que son contenu demande. Comme
 *: la page peut parler à l'app (`onSend`), elle a une barre de titre — le `<title>` de la page
 * et son adresse —, ses paliers de zoom (« A− » / « A+ »), le partage de son code source, et ses
 * commandes en pied de bloc (« Open in browser » et « Reload »). Elle se replie sur son code source
 * quand elle ne rend rien. Une page sans rien du tout, ou qui n'a pas chargé, le dit. Au-delà de la
 * hauteur maximale (1200 dp), c'est la page qui défile en elle-même.
 *
 * @param html le code de la page, tel que l'agent l'a écrit.
 * @param modifier la disposition du bloc.
 * @param src le chemin d'où la page a été lue (`attachment.page`): une URL http(s) fait
 * apparaître « Open in browser » en plus de « Reload », et le chemin sert d'adresse sous le titre.
 * @param onSend ce que la page envoie à l'agent: le texte posté par `{"type":"send","text":"…"}`.
 * @param onShare le partage de la page; à défaut, le code source part par `ACTION_SEND` (texte).
 */
@Composable
fun AgentPageBlock(
    html: String,
    modifier: Modifier = Modifier,
    src: String? = null,
    onSend: ((String) -> Unit)? = null,
    onShare: ((String) -> Unit)? = null,
) {
    // La page est refaite, et remesurée, quand on la relance.
    var reloadToken by remember(html) { mutableIntStateOf(0) }
    // L'état que la vue riche renvoie: au repos tant qu'elle n'a rien dit.
    var state by remember(html) { mutableStateOf(RichPageState.READY) }
    // Le titre que la page a annoncé par le pont JS (`<title>`), vide avant son premier rapport.
    var title by remember(html) { mutableStateOf("") }
    // Tant que `onPageFinished` n'a pas eu lieu, un indicateur discret le dit.
    var loading by remember(html) { mutableStateOf(false) }
    // La taille de base choisie, un des paliers de `PageZoomFactors`.
    var zoom by remember(html) { mutableFloatStateOf(1f) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // La barre de la page, au-dessus du rendu: son titre, son adresse, son zoom.
        if (html.isNotBlank()) {
            PageHeader(
                title = title,
                address = src?.takeIf { it.isNotBlank() },
                loading = loading,
                zoom = zoom,
                onZoomChange = { zoom = it },
            )
        }

        when {
            // Rien à montrer du tout.
            html.isBlank() ->
                PageNote("This page has nothing in it.", Modifier.fillMaxWidth())

            // La page n'a pas chargé: on le dit, et la commande « Reload » la relance.
            state == RichPageState.FAILED ->
                PageNote(PageLoadFailure, Modifier.fillMaxWidth())

            // La page a chargé mais ne rend rien: on retombe sur son code source.
            state == RichPageState.EMPTY ->
                PageSource(html, Modifier.fillMaxWidth())

            else -> RichWebView(
                kind = RichKind.HTML,
                source = html,
                modifier = Modifier.fillMaxWidth(),
                heightKey = WebBlockHeights.key("page", html),
                onSend = onSend,
                reloadToken = reloadToken,
                onPageState = { state = it },
                zoom = zoom,
                onPageTitle = { title = it },
                onLoading = { loading = it },
            )
        }

        // Le pied du bloc: les commandes de la page, . Une page qui n'a que son code
        // source n'a rien à recharger, mais elle se partage et se copie encore.
        if (html.isNotBlank()) {
            PageControls(
                src = src,
                canReload = state != RichPageState.EMPTY,
                html = html,
                onReload = {
                    state = RichPageState.READY
                    reloadToken += 1
                },
                onShare = onShare,
            )
        }
    }
}

/**
 * La barre de la page: son titre (`<title>`), l'adresse d'où elle vient, et les paliers de zoom.
 * Le pourcentage rappelle le palier courant; « A− » et « A+ » s'arrêtent aux extrémités.
 */
@Composable
private fun PageHeader(
    title: String,
    address: String?,
    loading: Boolean,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    // Le palier le plus proche: un facteur venu d'ailleurs retombe sur un de ceux qu'on offre.
    val index = PageZoomFactors.indices.minByOrNull { abs(PageZoomFactors[it] - zoom) }
        ?: PageZoomFactors.lastIndex

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = title.ifBlank { "Page" },
                localize = title.isBlank(),
                style = theme.subheadline.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textColor,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PageAction(text = "A−", enabled = index > 0) {
                onZoomChange(PageZoomFactors[index - 1])
            }
            BasicText(
                text = "${(zoom * 100).toInt()}%",
                style = theme.caption2.copy(color = theme.tertiaryText),
            )
            PageAction(text = "A+", enabled = index < PageZoomFactors.lastIndex) {
                onZoomChange(PageZoomFactors[index + 1])
            }
        }
        if (address != null) {
            // L'adresse, en petit, sous le titre: elle n'a de sens que si la page en a une.
            BasicText(
                text = address,
                localize = false,
                style = theme.caption2.copy(color = theme.tertiaryText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        if (loading) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(5.dp)
                        .background(theme.accent, CircleShape),
                )
                BasicText(
                    text = "Loading page…",
                    style = theme.caption2.copy(color = theme.tertiaryText),
                )
            }
        }
    }
}

/** Une remarque sur la page (vide, en échec), posée sur la surface du thème. */
@Composable
private fun PageNote(text: String, modifier: Modifier) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = modifier
            .background(theme.surfaceColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        BasicText(text = text, style = theme.footnote.copy(color = theme.secondaryText))
    }
}

/** Le code source de la page, quand elle ne rend rien: montré plutôt que perdu. */
@Composable
private fun PageSource(html: String, modifier: Modifier) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = modifier
            .background(theme.surfaceColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        BasicText(text = html, localize = false, style = theme.codeFont.copy(color = theme.textColor))
    }
}

/**
 * Le pied d'une page: « Open in browser » (si elle vient du web), « Reload », « Share page » et
 * « Copy source ». Le partage peut être pris en charge par l'appelant (`onShare`); sinon le code
 * source part comme texte par `ACTION_SEND`.
 */
@Composable
private fun PageControls(
    src: String?,
    canReload: Boolean,
    html: String,
    onReload: () -> Unit,
    onShare: ((String) -> Unit)?,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    // « Copied » revient à « Copy source » après un court instant, comme le fait.
    var copied by remember(html) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    // Une page lue depuis le web peut être rouverte dans le navigateur du téléphone.
    val link = src?.takeIf {
        it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
    }

    // Les commandes passent à la ligne sur un écran étroit, plutôt que d'être coupées.
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (link != null) {
            PageAction(text = "Open in browser") {
                // La page est ouverte hors de l'app, ouvre ses liens à l'extérieur.
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(link))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
        if (canReload) {
            PageAction(text = "Reload", onClick = onReload)
        }
        PageAction(text = "Share page") {
            if (onShare != null) {
                onShare(html)
            } else {
                // Sans callback, le partage système: le code source, tel quel, comme texte.
                runCatching {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, html)
                    }
                    context.startActivity(
                        Intent.createChooser(send, "Share page")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
        PageAction(text = if (copied) "Copied" else "Copy source") {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText("page", html)))
            }
            copied = true
        }
    }
}

/** Une commande de page: du texte touchable, sans composant Material. */
@Composable
private fun PageAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = text,
        style = theme.caption.copy(
            color = if (enabled) theme.secondaryText else theme.tertiaryText,
        ),
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp),
    )
}
