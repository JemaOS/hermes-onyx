package com.onyx.app.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * L'écran d'appel entrant, dans le **thème d'Onyx** (clair ou sombre, police 1,3×, Reduce Motion) :
 * qui appelle et pourquoi, puis **Décrocher** et **Refuser**.
 *
 * Miroir de `IncomingCallScreen` / `IncomingCallView` de la référence
 * (`shared/ui/.../ui/calls/IncomingCall.kt`) : plein écran sur l'écran verrouillé, on dit **qui**
 * appelle et **pourquoi**, jamais la question elle-même (elle s'ouvre après le déverrouillage).
 * Les deux boutons sont de simples cercles pleins — aucun composant Material.
 */
@Composable
fun IncomingCallScreen(
    caller: String,
    why: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 88.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(
                text = "Onyx call",
                style = theme.footnote.copy(color = theme.secondaryText),
            )
            BasicText(
                text = caller,
                localize = false,
                style = theme.largeTitle.copy(color = theme.textColor),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = why,
                style = theme.body.copy(color = theme.secondaryText),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp, bottom = 72.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CallButton("Decline", Symbols.Xmark, theme.error, onDecline)
            CallButton("Answer", Symbols.PhoneFill, theme.success, onAnswer)
        }
    }
}

/** Un bouton d'appel : un cercle plein, son symbole, et son nom dessous. */
@Composable
private fun CallButton(name: String, symbol: Int, color: Color, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(color)
                .onyxTappable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            OnyxIcon(symbol = symbol, tint = Color.White, size = 30.dp, contentDescription = name)
        }
        Spacer(Modifier.height(10.dp))
        BasicText(text = name, style = theme.footnote.copy(color = theme.textColor))
    }
}
