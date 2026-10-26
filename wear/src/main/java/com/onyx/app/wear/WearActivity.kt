package com.onyx.app.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.WaveformPulse
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.OnyxAppTheme

/**
 * L'écran de la montre: une question dictée, la réponse affichée et lue.
 *
 * C'est le Comme la Watch app: mêmes briques que le téléphone (`:core`), mais
 * un seul geste — on tape, on parle, on écoute.
 */
class WearActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnyxAppTheme {
                val model: WearAskViewModel = viewModel()
                val state by model.state.collectAsStateWithLifecycle()
                val speech by model.speech.state.collectAsStateWithLifecycle()
                val context = LocalContext.current


                if (!model.isConfigured) {
                    WearSetup(onSave = model::configure)
                } else {
                    WearAskScreen(
                        state = state,
                        level = speech.level,
                        onTalk = model::toggleListening,
                        onAgain = model::speakAgain,
                        onStop = model::stop,
                        
                    )
                }
            }
        }
    }
}

/** L'écran de la montre: l'anneau, la question, la réponse. */
@Composable
private fun WearAskScreen(
    state: WearState,
    level: Float,
    onTalk: () -> Unit,
    onAgain: () -> Unit,
    onStop: () -> Unit,

) {
    val theme = LocalOnyxTheme.current
    val listening = state.phase == WearPhase.LISTENING
    val text = when {
        state.reply.isNotBlank() -> state.reply
        state.heard.isNotBlank() -> state.heard
        state.problem != null -> state.problem.orEmpty()
        else -> "Tap to talk"
    }
    val showingPrompt = state.reply.isBlank() && state.heard.isBlank() && state.problem == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.pageColor)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(theme.accent, CircleShape)
                .onyxClickable(pressedScale = 0.92f, onClick = onTalk),
            contentAlignment = Alignment.Center,
        ) {
            if (listening) {
                WaveformPulse(color = theme.userText, height = 34f, spacing = 3f, barWidth = 4f)
            } else {
                OnyxIcon(Symbols.Mic, tint = theme.userText, size = 34.dp)
            }
        }

        Spacer(Modifier.height(10.dp))

        BasicText(
            text = phaseLabel(state.phase),
            style = theme.caption2.copy(color = theme.accent, textAlign = TextAlign.Center),
        )

        Spacer(Modifier.height(8.dp))

        BasicText(
            text = text,
            localize = showingPrompt,
            style = theme.footnote.copy(color = theme.textColor, textAlign = TextAlign.Center),
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        if (state.reply.isNotBlank()) {
            BasicText(
                text = "Read again",
                style = theme.caption2.copy(color = theme.accent, textAlign = TextAlign.Center),
                modifier = Modifier
                    .fillMaxWidth()
                    .onyxClickable(onClick = onAgain),
            )
        }
        if (state.phase != WearPhase.IDLE) {
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "Stop",
                style = theme.caption2.copy(color = theme.secondaryText, textAlign = TextAlign.Center),
                modifier = Modifier
                    .fillMaxWidth()
                    .onyxClickable(onClick = onStop),
            )
        }
    }
}

/** Le premier lancement: l'adresse du serveur, sa clé, le modèle. */
@Composable
private fun WearSetup(onSave: (String, String, String) -> Unit) {
    val theme = LocalOnyxTheme.current
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.pageColor)
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        ) {
        BasicText(
            text = "Your server",
            style = theme.subheadline.copy(color = theme.textColor, textAlign = TextAlign.Center),
        )
        Spacer(Modifier.height(10.dp))
        WearField(label = "Address", value = url) { url = it }
        WearField(label = "API key", value = key) { key = it }
        WearField(label = "Model", value = model) { model = it }
        Spacer(Modifier.height(12.dp))
        BasicText(
            text = "Save",
            style = theme.subheadline.copy(color = theme.accent, textAlign = TextAlign.Center),
            modifier = Modifier
                .fillMaxWidth()
                .onyxClickable { onSave(url, key, model) },
        )
    }
}

@Composable
private fun WearField(label: String, value: String, onValueChange: (String) -> Unit) {
    val theme = LocalOnyxTheme.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        BasicText(text = label, style = theme.caption2.copy(color = theme.secondaryText))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surfaceColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = theme.caption.copy(color = theme.textColor),
                singleLine = true,
                cursorBrush = SolidColor(theme.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun phaseLabel(phase: WearPhase): String = when (phase) {
    WearPhase.IDLE -> "READY"
    WearPhase.LISTENING -> "LISTENING"
    WearPhase.WAITING -> "THINKING"
    WearPhase.SPEAKING -> "SPEAKING"
    WearPhase.PROBLEM -> "PROBLEM"
}
