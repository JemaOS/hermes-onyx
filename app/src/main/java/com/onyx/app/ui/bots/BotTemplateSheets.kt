package com.onyx.app.ui.bots

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.ClipData
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.HouseSheet
import com.onyx.app.ui.design.HouseSheetDivider
import com.onyx.app.ui.design.HouseSheetRow
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * La feuille de « Partager comme modèle » : le lien **en clair**, le bouton qui le copie, et son QR
 * **quand le dépôt sait en produire un**.
 *
 * L'émission d'un QR est réelle, pas promise : `com.google.zxing.qrcode.QRCodeWriter` est dans les
 * dépendances (`zxing-android-embedded` → `zxing-core`, la même bibliothèque qui **lit** les codes
 * de configuration). Aucune dépendance n'est ajoutée pour cela. Si le dessin échoue (lien refusé par
 * le générateur), la feuille montre le lien et le copier, et dit que le code n'a pas pu être dessiné
 * — elle ne cache pas la moitié qui marche.
 *
 * [link] est `null` tant que le serveur n'a pas rendu le `SOUL.md`, les outils et les compétences du
 * bot : la feuille ne montre jamais un lien partiel, elle attend. [problem] porte le refus du codec
 * (un prompt qui contient une adresse, par exemple) tel qu'il se lit.
 */
@Composable
internal fun BotTemplateShareSheet(
    link: String?,
    problem: String?,
    /** Vrai quand le bot n'a pas de `SOUL.md` : le lien part sans personnalité, et c'est dit. */
    withoutPrompt: Boolean = false,
    onDismiss: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val clipboard = LocalClipboard.current
    var copied by remember(link) { mutableStateOf(false) }

    HouseSheet(
        onDismiss = onDismiss,
        title = "Share as Template…",
        subtitle = "Anyone who opens this link sees its name, role, tools, skills, model and face before adding the bot. It carries no key, no token and no server address.",
        closeLabel = "Done",
    ) {
        when {
            link != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OnyxSpace.ScreenMargin)
                        .clip(RoundedCornerShape(OnyxSpace.FieldCorner))
                        .background(OnyxSurfaces.well(theme))
                        .border(OnyxSpace.Stroke, OnyxSurfaces.hairline(theme), RoundedCornerShape(OnyxSpace.FieldCorner))
                        .padding(horizontal = OnyxSpace.Snug, vertical = OnyxSpace.Half),
                ) {
                    BasicText(
                        text = link,
                        style = theme.monospacedFootnote.copy(color = theme.textColor),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        // Le lien est déjà ce qu'il est : le dictionnaire n'a rien à y reprendre.
                        localize = false,
                    )
                }

                qrCodeOf(link)?.let { bitmap ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = OnyxSpace.Snug),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            // Le code porte le même lien : son nom d'accessibilité dit lequel.
                            contentDescription = OnyxLocalization["Bot template QR code"],
                            modifier = Modifier
                                .padding(top = OnyxSpace.Half)
                                .size(200.dp)
                                .clip(RoundedCornerShape(OnyxSpace.FieldCorner)),
                        )
                        BasicText(
                            text = "The link is ready to send. Scan the code from another phone to open it there.",
                            style = theme.footnote.copy(color = theme.tertiaryText),
                            modifier = Modifier.padding(horizontal = OnyxSpace.Base),
                        )
                    }
                } ?: BasicText(
                    text = "The code couldn't be drawn.",
                    style = theme.footnote.copy(color = theme.error),
                    modifier = Modifier.padding(horizontal = OnyxSpace.ScreenMargin, vertical = OnyxSpace.Half),
                )

                HouseSheetRow(
                    label = if (copied) "Copied" else "Copy link",
                    symbol = Symbols.DocOnDoc,
                    onClick = {
                        clipboard.nativeClipboard.setPrimaryClip(
                            ClipData.newPlainText(OnyxLocalization["Bot template link"], link),
                        )
                        copied = true
                    },
                )

                HouseSheetDivider(withTile = false)

                BasicText(
                    text = if (withoutPrompt) {
                        "This bot has no SOUL.md: the link carries no prompt."
                    } else {
                        "The prompt (SOUL.md) goes with the link."
                    },
                    style = theme.footnote.copy(color = theme.secondaryText),
                    modifier = Modifier.padding(horizontal = OnyxSpace.ScreenMargin, vertical = OnyxSpace.Half),
                )
            }

            problem != null -> BasicText(
                text = problem,
                style = theme.footnote.copy(color = theme.error),
                modifier = Modifier.padding(horizontal = OnyxSpace.ScreenMargin, vertical = OnyxSpace.Half),
            )

            else -> BasicText(
                text = "Preparing the link…",
                style = theme.footnote.copy(color = theme.secondaryText),
                modifier = Modifier.padding(horizontal = OnyxSpace.ScreenMargin, vertical = OnyxSpace.Half),
            )
        }
    }
}

/**
 * La feuille d'arrivée d'un lien, chez quelqu'un d'autre : elle **décode** le lien, montre
 * l'aperçu de [BotTemplatePreview] — nom, rôle, outils, compétences, modèle, et **les six absences**
 * — puis propose « Ajouter ». Rien ne part au serveur avant ce geste : l'aperçu est obligatoire, et
 * un lien invalide s'arrête ici avec **sa** phrase de refus.
 *
 * [busy] disable le bouton pendant l'écriture réelle du profil ; [problem] dit ce que le serveur a
 * refusé, sans effacer l'aperçu.
 */
@Composable
internal fun BotTemplateImportSheet(
    link: String,
    busy: Boolean = false,
    problem: String? = null,
    onAdd: (BotTemplateShare) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    // Le décodage est **pur** (aucun Android, aucun serveur) : il se fait une fois par lien, ici.
    val decoded = remember(link) { BotTemplateShareCodec.decodeOrNull(link) }

    HouseSheet(
        onDismiss = onDismiss,
        title = "Add this bot?",
        subtitle = "This link came from someone else. Check what it brings before adding the bot.",
        closeLabel = "Cancel",
    ) {
        val share = decoded.getOrNull()
        val refusal = decoded.exceptionOrNull()
        when {
            share != null -> {
                val preview = share.preview()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OnyxSpace.ScreenMargin),
                    verticalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
                ) {
                    BasicText(
                        text = preview.name,
                        style = theme.title3.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
                    )
                    BasicText(
                        text = preview.role,
                        style = theme.body.copy(color = theme.secondaryText),
                    )
                    previewLines(preview).drop(2).forEach { line ->
                        BasicText(
                            text = line,
                            style = theme.footnote.copy(color = theme.secondaryText),
                        )
                    }
                }

                HouseSheetDivider(withTile = false)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OnyxSpace.ScreenMargin, vertical = OnyxSpace.Half),
                    verticalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
                ) {
                    BasicText(
                        text = "What the link doesn't carry",
                        style = theme.footnote.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
                    )
                    preview.omissionLabels().forEach { line ->
                        BasicText(
                            text = line,
                            style = theme.footnote.copy(color = theme.tertiaryText),
                        )
                    }
                }

                problem?.let { said ->
                    BasicText(
                        text = said,
                        style = theme.footnote.copy(color = theme.error),
                        modifier = Modifier.padding(horizontal = OnyxSpace.ScreenMargin, vertical = OnyxSpace.Half),
                    )
                }

                HouseSheetRow(
                    label = if (busy) "Adding…" else "Add",
                    symbol = Symbols.Plus,
                    onClick = { if (!busy) onAdd(share) },
                )
            }

            else -> BasicText(
                // Le refus du codec, **sa** phrase : « ce n'est pas un lien », « ce modèle porte une
                // clé », « ce lien est abîmé » — jamais un « erreur » muet.
                text = (refusal as? BotTemplateShareException)?.let { refused -> refused.reason.message(refused.detail) }
                    ?: OnyxLocalization["That isn't a Onyx bot template link."],
                style = theme.footnote.copy(color = theme.error),
                modifier = Modifier.padding(horizontal = OnyxSpace.ScreenMargin, vertical = OnyxSpace.Half),
            )
        }
    }
}

/** Le décodage d'un lien, en `Result` : le refus garde sa raison pour la feuille. */
internal fun BotTemplateShareCodec.decodeOrNull(link: String): Result<BotTemplateShare> =
    try {
        Result.success(decode(link))
    } catch (refused: BotTemplateShareException) {
        Result.failure(refused)
    }

/**
 * Le QR d'un lien, dessiné par ZXing — `null` quand le générateur refuse (lien trop long pour un
 * code, par exemple) : la feuille montre alors le lien, et dit que le code manque.
 *
 * 512 px de côté, comme le code de connexion : la même taille à l'écran, assez dense pour être lu
 * d'un autre téléphone.
 */
private fun qrCodeOf(link: String): android.graphics.Bitmap? = runCatching {
    val size = 512
    val matrix = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, size, size)
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    bitmap
}.getOrNull()
