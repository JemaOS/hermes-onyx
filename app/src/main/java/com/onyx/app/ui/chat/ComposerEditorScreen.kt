package com.onyx.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.semanticsLabel
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Le brouillon sur une page entière (`ComposerEditor`): Return pose toujours une nouvelle ligne,
 * « Done » ferme sans envoyer, et l'action d'envoi reste dans la barre de navigation.
 */
@Composable
fun ComposerEditorScreen(
    draft: String,
    onDraftChange: (String) -> Unit,
    onDone: () -> Unit,
    onSend: () -> Unit,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val focus = remember { FocusRequester() }
    val canSend = draft.isNotBlank()

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            ComposerHeader(
                title = "Message",
                onDone = onDone,
                action = if (isStreaming) "Send after this reply" else "Send",
                actionEnabled = canSend,
                actionLabel = if (canSend) "Send the message" else "Send: type a message first",
                onAction = onSend,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                textStyle = theme.body.copy(color = theme.textColor),
                cursorBrush = SolidColor(theme.accent),
                // Contrairement au composer, Return n'envoie jamais depuis cette page.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focus),
            )
        }
    }
}

/** La barre native de la page: annulation à gauche, titre inline, confirmation à droite. */
@Composable
private fun ComposerHeader(
    title: String,
    onDone: () -> Unit,
    action: String,
    actionEnabled: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.CenterStart) {
            BasicText(
                text = "Done",
                style = theme.body.copy(color = theme.accent),
                modifier = Modifier.onyxClickable(onClick = onDone),
            )
        }
        BasicText(
            text = title,
            style = theme.headline.copy(color = theme.textColor, textAlign = TextAlign.Center),
            modifier = Modifier.weight(1f),
        )
        Box(modifier = Modifier.widthIn(min = 72.dp), contentAlignment = Alignment.CenterEnd) {
            BasicText(
                text = action,
                style = theme.body.copy(
                    color = if (actionEnabled) theme.accent else theme.tertiaryText,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier
                    .onyxClickable(enabled = actionEnabled, onClick = onAction)
                    .semanticsLabel(actionLabel),
            )
        }
    }
}
