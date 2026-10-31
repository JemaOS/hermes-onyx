package com.onyx.app.ui.settings

import com.onyx.app.ui.design.OnyxSpace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.onyx.app.data.model.SetupCode
import com.onyx.app.security.AppLock
import com.onyx.app.security.LockDelay
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.OnyxToggleRow
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Réglages → Lock.
 *
 * Port de `LockSettings` : on demande la biométrie au retour dans l'app, après le délai choisi.
 * Le code est le secours d'Android, absent du réglage référence mais nécessaire ici quand le téléphone
 * n'a ni visage ni doigt.
 */
@Composable
fun LockSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val lock = remember { AppLock(context) }

    var enabled by remember { mutableStateOf(lock.isEnabled) }
    var delay by remember { mutableStateOf(lock.delay) }
    var pin by remember { mutableStateOf("") }
    var showDelay by remember { mutableStateOf(false) }
    // L'état du code est tenu ici autant que dans les préférences : sans cet état, supprimer le code
    // laissait la ligne et le pied de page dire encore qu'il était conservé.
    var hasPin by remember { mutableStateOf(lock.hasPin) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Lock", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            OnyxFormSection(
                header = "Lock",
                footer = "Onyx asks for your face or fingerprint when it comes back, once the delay has passed.",
            ) {
                OnyxToggleRow(
                    // Comme la référence : le libellé générique « Face or fingerprint » quand le
                    // téléphone n'annonce pas de nom de biométrie (« a passcode » ne s'affichait
                    // là que sur un appareil sans visage ni empreinte, seul cas où la référence
                    // montre ce libellé).
                    label = "Require ${lock.biometryLabel(context) ?: "Face or fingerprint"}",
                    checked = enabled,
                    onCheckedChange = { value ->
                        enabled = value
                        lock.setEnabled(value)
                    },
                )
                if (enabled) {
                    OnyxRowDivider()
                    OnyxRow(onClick = { showDelay = !showDelay }) {
                        BasicText("Lock after", style = theme.body.copy(color = theme.primaryText), modifier = Modifier.weight(1f))
                        BasicText(delay.label, style = theme.body.copy(color = theme.secondaryText))
                        OnyxIcon(Symbols.ChevronRight, tint = theme.tertiaryText, size = 12.dp)
                    }
                    if (showDelay) {
                        LockDelay.entries.forEach { option ->
                            OnyxRowDivider()
                            OnyxRow(onClick = {
                                delay = option
                                lock.setDelay(option)
                                showDelay = false
                            }) {
                                OnyxIcon(
                                    symbol = if (option == delay) Symbols.CheckmarkCircle else Symbols.Circle,
                                    tint = if (option == delay) theme.accent else theme.tertiaryText,
                                    size = 18.dp,
                                )
                                BasicText(
                                    text = option.label,
                                    style = theme.body.copy(color = theme.primaryText),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            OnyxFormSection(
                header = "Passcode",
                footer = if (hasPin) {
                    "Kept on this phone, salted and hashed; it never leaves it. Enter a new one to replace it."
                } else {
                    "A code of your own, in case the phone's biometry isn't available."
                },
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OnyxTextField(
                        value = pin,
                        onValueChange = { value -> pin = value.filter { it.isDigit() }.take(12) },
                        placeholder = if (hasPin) "New passcode" else "Passcode",
                        style = theme.body,
                        keyboardType = KeyboardType.NumberPassword,
                    )
                }
                OnyxRowDivider()
                OnyxRow(onClick = {
                    lock.setPin(pin)
                    pin = ""
                    hasPin = lock.hasPin
                }) {
                    BasicText(
                        text = if (hasPin) "Change the passcode" else "Set the passcode",
                        style = theme.body.copy(color = if (pin.length >= 4) theme.accent else theme.tertiaryText),
                    )
                }
                if (hasPin) {
                    OnyxRowDivider()
                    OnyxRow(onClick = {
                        lock.setPin("")
                        hasPin = lock.hasPin
                    }) {
                        BasicText("Remove the passcode", style = theme.body.copy(color = theme.error))
                    }
                }
            }
        }
    }
}

/**
 * Réglages → Set up another device.
 *
 * Miroir de `SetupCodeView` : le lien de connexion en QR code, à scanner sur l'autre
 * appareil, et l'inverse — scanner un code pour reprendre une connexion.
 */
@Composable
fun ConnectQrScreen(
    code: SetupCode,
    onScan: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Connect", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val link = code.link()
            val bitmap = remember(link) { qrBitmap(link) }

            BasicText(
                text = "Set up another device",
                style = theme.title3.copy(color = theme.primaryText),
                modifier = Modifier.padding(top = 18.dp),
            )
            BasicText(
                text = "Scan this on the other phone, in Settings › Servers › Set up connection.",
                style = theme.footnote.copy(color = theme.secondaryText, textAlign = TextAlign.Center),
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
            )

            if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .padding(18.dp)
                        .background(androidx.compose.ui.graphics.Color.White, androidx.compose.foundation.shape.RoundedCornerShape(OnyxSpace.CardCorner))
                        .padding(14.dp),
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = com.onyx.app.localization.OnyxLocalization["Connection QR code"],
                        modifier = Modifier.size(232.dp),
                    )
                }
            } else {
                BasicText(
                    text = "The code couldn't be drawn.",
                    style = theme.footnote.copy(color = theme.error),
                )
            }

            OnyxFormSection(
                footer = "The code carries this server's address and keys. Treat it like a password.",
            ) {
                OnyxRow(onClick = onScan) {
                    OnyxIcon(Symbols.Camera, tint = theme.accent, size = 18.dp)
                    BasicText(
                        text = "Scan a code instead",
                        style = theme.body.copy(color = theme.accent),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            BasicText(
                text = link.take(180),
                style = theme.caption.copy(color = theme.tertiaryText),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }
    }
}

/** Dessine un QR code pour un lien. */
private fun qrBitmap(link: String): android.graphics.Bitmap? = runCatching {
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
