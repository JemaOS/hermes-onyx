package com.onyx.app.ui.settings.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.voice.comparableStopPhrase
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Réglages → Voix → Stop phrases (`StopPhrasesView`).
 *
 * Les façons personnelles de terminer une conversation en mains-libres, à côté de celles qui sont
 * toujours entendues.
 */
@Composable
fun StopPhrasesScreen(
    phrases: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    var draft by remember { mutableStateOf("") }

    val canAdd = addable(draft, phrases) != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Stop phrases", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            OnyxFormSection(
                header = "Your phrases",
                footer = "Say one of these on its own in hands-free mode and Onyx stops listening, without sending it to the agent. Add the words you'd use, or the ones for the language you speak to Onyx in: write them the way they are said. A phrase inside a longer sentence doesn't count.",
            ) {
                phrases.forEachIndexed { index, phrase ->
                    if (index > 0) OnyxRowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            text = phrase,
                            localize = false,
                            style = theme.body.copy(color = theme.primaryText),
                            modifier = Modifier.weight(1f),
                        )
                        OnyxIcon(
                            symbol = Symbols.MinusCircle,
                            tint = theme.error,
                            size = 17.dp,
                            modifier = Modifier.onyxClickable { onRemove(phrase) },
                        )
                    }
                }
                if (phrases.isNotEmpty()) OnyxRowDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OnyxTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = "Add a phrase",
                        style = theme.body,
                        modifier = Modifier.weight(1f),
                    )
                    BasicText(
                        text = "Add",
                        style = theme.body.copy(
                            color = if (canAdd) theme.accent else theme.tertiaryText,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        modifier = Modifier.onyxClickable(enabled = canAdd) {
                            addable(draft, phrases)?.let { phrase ->
                                onAdd(phrase)
                                draft = ""
                            }
                        },
                    )
                }
            }

            OnyxFormSection(
                header = "Always heard",
                footer = "With or without “okay”, “Onyx”, “please” or “thanks” around them.",
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                    BasicText(
                        text = BUILT_IN,
                        localize = false,
                        style = theme.callout.copy(color = theme.secondaryText),
                    )
                }
            }
        }
    }
}

/** Ce qu'une phrase devient en étant comparée, ou `null` si elle ne peut pas être ajoutée. */
fun addable(phrase: String, own: List<String>): String? {
    val cleaned = phrase.trim()
    val heard = normalizePhrase(cleaned)
    return when {
        heard.isEmpty() -> null
        cleaned.length > LONGEST_OWN -> null
        own.size >= MOST_OWN -> null
        heard in BUILT_IN_HEARD -> null
        own.any { normalizePhrase(it) == heard } -> null
        else -> cleaned
    }
}

/** La phrase telle qu'elle est entendue : minuscules, sans politesses autour. */
fun normalizePhrase(phrase: String): String {
    val clean = phrase.lowercase().replace(Regex("[\\p{Punct}’‘´`«»]"), " ")
    val tokens = clean.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
    val leading = setOf("okay", "ok", "hey", "onyx", "please")
    val trailing = listOf(listOf("thank", "you"), listOf("thanks"), listOf("please"))

    while (tokens.isNotEmpty() && tokens.first() in leading) tokens.removeAt(0)
    var trimmed = true
    while (trimmed && tokens.isNotEmpty()) {
        trimmed = false
        for (words in trailing) {
            if (tokens.size >= words.size && tokens.takeLast(words.size) == words) {
                repeat(words.size) { tokens.removeAt(tokens.lastIndex) }
                trimmed = true
            }
        }
    }
    return tokens.joinToString(" ")
}

/** Combien de phrases on peut ajouter, et leur longueur maximale, comme référence. */
const val MOST_OWN = 12
const val LONGEST_OWN = 40

/** Celles qui sont toujours entendues : le français par défaut, puis celles de la référence. */
val BUILT_IN_HEARD: Set<String> = listOf(
    "arrête", "arrête d'écouter", "c'est tout", "ça suffit", "au revoir", "bonne nuit",
    "termine la conversation", "laisse tomber", "annule", "merci",
    "stop", "stop listening", "that's all", "that's enough", "goodbye", "good night",
    "end conversation", "we're done", "never mind", "cancel", "thank you",
)
    // La même forme comparable que le moteur vocal : une phrase française avec apostrophe doit
    // compter comme déjà entendue, sans dépendre du caractère que le moteur a écrit.
    .flatMap { listOf(it, comparableStopPhrase(it)) }
    .filter { it.isNotEmpty() }
    .toSet()

/** La liste montrée : les phrases **françaises d'abord**, parce que c'est ce que l'app écoute. */
private const val BUILT_IN =
    "« arrête », « arrête d'écouter », « c'est tout », « ça suffit », « au revoir », « bonne nuit », " +
        "« termine la conversation », « laisse tomber », « annule », « merci », " +
        "« stop », « stop listening », « that's all », « that's enough », « goodbye », « good night », " +
        "« end conversation », « we're done », « never mind », « cancel », « thank you »"
