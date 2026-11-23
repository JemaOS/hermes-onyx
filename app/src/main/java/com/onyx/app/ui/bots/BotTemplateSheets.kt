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
 * The "Share as Template" sheet ("Partager comme modèle"): the link **in clear text**, the button that copies it, and its QR
 * **when the repo knows how to produce one**.
 *
 * QR emission is real, not promised: `com.google.zxing.qrcode.QRCodeWriter` is in the
 * dependencies (`zxing-android-embedded` → `zxing-core`, the same library that **reads** the configuration
 * codes). No dependency is added for this. If drawing fails (link refused by
 * the generator), the sheet shows the link and the copy action, and says the code could not be drawn
 * — it does not hide the half that works.
 *
 * [link] is `null` until the server has rendered the bot's `SOUL.md`, tools and skills:
 * the sheet never shows a partial link, it waits. [problem] carries the codec refusal
 * (a prompt that contains an address, for example) as it reads.
 */
@Composable
internal fun BotTemplateShareSheet(
    link: String?,
    problem: String?,
    /** True when the bot has no `SOUL.md`: the link goes out without a personality, and that is stated. */
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
                        // The link is already what it is: the dictionary has nothing to correct in it.
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
                            // The code carries the same link: its accessibility name says which one.
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
 * The arrival sheet of a link, at someone else's: it **decodes** the link, shows
 * the preview of [BotTemplatePreview] — name, role, tools, skills, model, and **the six omissions**
 * — then offers "Add" ("Ajouter"). Nothing goes to the server before that gesture: the preview is mandatory, and
 * an invalid link stops here with **its** refusal sentence.
 *
 * [busy] disables the button during the actual writing of the profile; [problem] says what the server
 * refused, without erasing the preview.
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
    // Decoding is **pure** (no Android, no server): it happens once per link, here.
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
                // The codec refusal, **its** sentence: "that isn't a link" ("ce n'est pas un lien"), "this template carries a"
                // "key" ("ce modèle porte une clé"), "this link is damaged" ("ce lien est abîmé") — never a mute "error".
                text = (refusal as? BotTemplateShareException)?.let { refused -> refused.reason.message(refused.detail) }
                    ?: OnyxLocalization["That isn't a Onyx bot template link."],
                style = theme.footnote.copy(color = theme.error),
                modifier = Modifier.padding(horizontal = OnyxSpace.ScreenMargin, vertical = OnyxSpace.Half),
            )
        }
    }
}

/** The decoding of a link, as a `Result`: the refusal keeps its reason for the sheet. */
internal fun BotTemplateShareCodec.decodeOrNull(link: String): Result<BotTemplateShare> =
    try {
        Result.success(decode(link))
    } catch (refused: BotTemplateShareException) {
        Result.failure(refused)
    }

/**
 * A link's QR, drawn by ZXing — `null` when the generator refuses (link too long for a
 * code, for example): the sheet then shows the link, and says the code is missing.
 *
 * 512 px per side, like the login code: the same size on screen, dense enough to be read
 * from another phone.
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
