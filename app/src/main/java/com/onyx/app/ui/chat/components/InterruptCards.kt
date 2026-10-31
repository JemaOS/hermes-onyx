package com.onyx.app.ui.chat.components

import com.onyx.app.ui.design.OnyxSpace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ClarifyQuestion
import com.onyx.app.data.model.ClarifyRequest
import com.onyx.app.data.model.SecretRequest
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/** La réponse à une question : le texte libre nettoyé prime, sinon les choix triés. */
internal fun clarifyAnswer(question: ClarifyQuestion, picked: Set<String>, typed: String): String {
    val clean = typed.trim()
    if (clean.isNotEmpty()) return clean
    return picked.sorted().joinToString(", ")
}

/** Le bouton d'envoi ne s'active que lorsque chaque question a une réponse. */
internal fun clarifyAllAnswered(
    questions: List<ClarifyQuestion>,
    picked: Map<String, Set<String>>,
    freeText: Map<String, String>,
): Boolean = questions.all { question ->
    clarifyAnswer(question, picked[question.id].orEmpty(), freeText[question.id].orEmpty()).isNotEmpty()
}

/**
 * « Onyx a une question » (`ClarifyCard`) : choix en pastilles, réponse libre, envoi immédiat
 * d'un choix unique, et réponses groupées lorsque la demande en contient plusieurs.
 */
@Composable
fun ClarifyCard(
    request: ClarifyRequest,
    onRespond: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
    /** Qui pose la question : le nom du bot dans son fil, sinon « Onyx ». */
    speaker: String = "Onyx",
) {
    val theme = LocalOnyxTheme.current
    var picked by remember(request.id) { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var freeText by remember(request.id) { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun send(selection: Map<String, Set<String>> = picked) {
        val answers = request.questions.associate { question ->
            question.id to clarifyAnswer(
                question = question,
                picked = selection[question.id].orEmpty(),
                typed = freeText[question.id].orEmpty(),
            )
        }
        onRespond(answers)
    }

    val allAnswered = clarifyAllAnswered(request.questions, picked, freeText)
    Column(
        modifier = modifier
            .fillMaxWidth()
.background(theme.tertiarySurfaceColor, RoundedCornerShape(OnyxSpace.FieldCorner))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OnyxIcon(Symbols.QuoteBubble, tint = theme.textColor, size = 15.dp)
            BasicText(
                text = if (request.questions.size > 1) "$speaker has a few questions" else "$speaker has a question",
                style = theme.subheadline.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
            )
        }

        request.questions.forEach { question ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(question.question, localize = false, style = theme.body.copy(color = theme.textColor))
                if (question.choices.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        question.choices.forEach { choice ->
                            val selected = picked[question.id]?.contains(choice) == true
                            BasicText(
                                text = choice,
                                localize = false,
                                style = theme.footnote.copy(
                                    color = if (selected) theme.userText else theme.textColor,
                                    fontWeight = FontWeight.Medium,
                                ),
                                modifier = Modifier
                                    .background(
                                        if (selected) theme.accent else theme.textColor.copy(alpha = 0.08f),
                                        RoundedCornerShape(50),
                                    )
                                    .onyxClickable {
                                        val current = picked[question.id].orEmpty()
                                        val next = if (question.multiSelect) {
                                            if (selected) current - choice else current + choice
                                        } else {
                                            setOf(choice)
                                        }
                                        val selection = picked + (question.id to next)
                                        picked = selection
                                        if (!question.multiSelect && request.questions.size == 1) send(selection)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.surfaceColor, RoundedCornerShape(OnyxSpace.ControlCorner))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    OnyxTextField(
                        value = freeText[question.id].orEmpty(),
                        onValueChange = { value -> freeText = freeText + (question.id to value) },
                        placeholder = if (question.choices.isEmpty()) "Your answer" else "Or type something else",
                        style = theme.footnote,
                        singleLine = false,
                        imeAction = if (request.questions.size == 1) ImeAction.Send else ImeAction.Default,
                        onSend = { if (request.questions.size == 1) send() },
                    )
                }
            }
        }

        val needsButton = request.questions.size > 1 ||
            request.questions.any { it.multiSelect } ||
            request.questions.any { it.choices.isEmpty() }
        if (needsButton) {
            InterruptAction(
                title = "Send answers",
                enabled = allAnswered,
                filled = true,
                onClick = ::send,
            )
        }
    }
}

/** « sudo password needed » : le mot de passe ne quitte que vers le terminal du gateway. */
@Composable
fun SudoCard(
    onRespond: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    var password by remember { mutableStateOf("") }

    fun send() {
        if (password.isEmpty()) return
        onRespond(password)
        password = ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.error.copy(alpha = 0.10f), RoundedCornerShape(OnyxSpace.FieldCorner))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OnyxIcon(Symbols.Lock, tint = theme.textColor, size = 15.dp)
            BasicText(
                text = "sudo password needed",
                style = theme.subheadline.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
            )
        }
        BasicText(
            text = "A command on the gateway host needs elevated rights. The password goes straight to that terminal and isn't stored by Onyx.",
            style = theme.footnote.copy(color = theme.secondaryText),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(theme.surfaceColor, RoundedCornerShape(OnyxSpace.ControlCorner))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                OnyxTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Password",
                    style = theme.footnote,
                    secret = true,
                    imeAction = ImeAction.Send,
                    onSend = ::send,
                )
            }
            InterruptAction(title = "Send", enabled = password.isNotEmpty(), filled = true, onClick = ::send)
            InterruptAction(title = "Cancel", enabled = true, filled = false, onClick = { onRespond("") })
        }
    }
}

/** « Onyx needs a secret » : enregistrement sur le gateway, ou passage avec une valeur vide. */
@Composable
fun SecretCard(
    request: SecretRequest,
    onRespond: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Qui demande le secret : le nom du bot dans son fil, sinon « Onyx ». */
    speaker: String = "Onyx",
) {
    val theme = LocalOnyxTheme.current
    var value by remember(request.id) { mutableStateOf("") }

    fun send() {
        if (value.isEmpty()) return
        onRespond(value)
        value = ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
.background(theme.tertiarySurfaceColor, RoundedCornerShape(OnyxSpace.FieldCorner))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OnyxIcon(Symbols.Key, tint = theme.textColor, size = 15.dp)
            BasicText(
                text = "$speaker needs a secret",
                style = theme.subheadline.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
            )
        }
        BasicText(
            text = request.prompt.ifEmpty {
                OnyxLocalization.format("Provide a value for %1\$s.", request.envVar)
            },
            localize = false,
            style = theme.footnote.copy(color = theme.textColor),
        )
        if (request.envVar.isNotEmpty()) {
            BasicText(
                text = OnyxLocalization.format("Stored on the gateway as %1\$s", request.envVar),
                localize = false,
                style = theme.caption.copy(color = theme.secondaryText, fontFamily = FontFamily.Monospace),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(theme.surfaceColor, RoundedCornerShape(OnyxSpace.ControlCorner))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                OnyxTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = "Value",
                    style = theme.footnote,
                    secret = true,
                    imeAction = ImeAction.Send,
                    onSend = ::send,
                )
            }
            InterruptAction(title = "Save", enabled = value.isNotEmpty(), filled = true, onClick = ::send)
            InterruptAction(title = "Skip", enabled = true, filled = false, onClick = { onRespond("") })
        }
    }
}

/** Le titre rempli de la référence : texte du thème sur l'accent, texte secondaire lorsqu'il est inactif. */
@Composable
private fun InterruptAction(
    title: String,
    enabled: Boolean,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = title,
        style = theme.footnote.copy(
            color = when {
                !enabled -> theme.secondaryText
                filled -> theme.userText
                else -> theme.accent
            },
            fontWeight = FontWeight.Medium,
        ),
        modifier = Modifier
            .background(
                when {
                    !enabled -> theme.fill
                    filled -> theme.accent
                    else -> Color.Transparent
                },
                RoundedCornerShape(OnyxSpace.ControlCorner),
            )
            .onyxClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}
