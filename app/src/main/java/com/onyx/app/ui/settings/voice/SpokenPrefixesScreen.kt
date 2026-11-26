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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.OnyxValueRow
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.design.IdentifierText

/**
 * Réglages → Voix → Spoken prefixes (`SpokenPrefixesView`).
 *
 * Un mot qui ouvre un message dicté devient un préfixe de texte, et peut envoyer vers un bot ou
 * un modèle. Les règles s'essaient dans l'ordre ; la première qui correspond l'emporte.
 */
@Composable
fun SpokenPrefixesScreen(
    rules: List<SpokenPrefix>,
    bots: List<BotProfile>,
    onAdd: (SpokenPrefix) -> Unit,
    onRemove: (Int) -> Unit,
    onOpenRule: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    var addBotMenu by remember { mutableStateOf(false) }

    val ruledBots = rules.mapNotNull { it.bot }.toSet()
    val unruled = bots.filter { !it.isHidden && it.name !in ruledBots }
        .sortedWith(compareByDescending<BotProfile> { it.isDefault }.thenBy { it.name })

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Spoken prefixes", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            OnyxFormSection(
                header = "Prefixes",
                footer = "Applies to voice mode and spoken phrases only: when a message starts with one of these words, the word is replaced with its prefix before sending, the conversation goes to the rule's bot if it names one (and stays with it), and switches to the rule's model if it names one. Typed messages are never changed. The first matching prefix wins.",
            ) {
                rules.forEachIndexed { index, rule ->
                    if (index > 0) OnyxRowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onyxTappable { onOpenRule(index) }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            text = rule.word.ifEmpty { "New prefix" },
                            localize = rule.word.isEmpty(),
                            style = theme.body.copy(color = theme.primaryText),
                            modifier = Modifier.weight(1f),
                        )
                        BasicText(
                            text = rule.summary { profile -> bots.firstOrNull { it.name == profile }?.label() },
                            localize = false,
                            style = theme.callout.copy(color = theme.secondaryText, fontFamily = FontFamily.Monospace),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        OnyxIcon(
                            symbol = Symbols.ChevronRight,
                            tint = theme.tertiaryText,
                            size = 12.dp,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        OnyxIcon(
                            symbol = Symbols.MinusCircle,
                            tint = theme.error,
                            size = 16.dp,
                            modifier = Modifier.padding(start = 8.dp).onyxClickable { onRemove(index) },
                        )
                    }
                }
                if (rules.isNotEmpty()) OnyxRowDivider()
                OnyxRow(onClick = { onAdd(SpokenPrefix()) }) {
                    OnyxIcon(Symbols.Plus, tint = theme.accent, size = 17.dp)
                    BasicText("Add prefix", style = theme.body.copy(color = theme.accent), modifier = Modifier.weight(1f))
                }
                if (unruled.isNotEmpty()) {
                    OnyxRowDivider()
                    OnyxRow(onClick = { addBotMenu = true }) {
                        OnyxIcon(Symbols.Person2, tint = theme.accent, size = 17.dp)
                        BasicText("Add a bot", style = theme.body.copy(color = theme.accent), modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (addBotMenu) {
        OnyxMenu(
            items = unruled.map { bot ->
                MenuItem(bot.label(), Symbols.Person2, localize = false) {
                    addBotMenu = false
                    onAdd(SpokenPrefix(word = bot.label(), prefix = "", bot = bot.name))
                }
            },
            onDismiss = { addBotMenu = false },
        )
    }
}

/**
 * L'éditeur d'une règle (`SpokenPrefixEditor`) : le mot et ses alias, le préfixe, le bot, le
 * modèle, et l'aperçu de ce qu'une phrase devient.
 */
@Composable
fun SpokenPrefixEditorScreen(
    rule: SpokenPrefix,
    bots: List<BotProfile>,
    models: List<String>,
    onChange: (SpokenPrefix) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    var aliases by remember(rule) { mutableStateOf(rule.aliases.joinToString(", ")) }
    var showBots by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }

    // L'exemple suit la langue : seule la phrase d'exemple est de Onyx, le mot reste celui de l'utilisateur.
    val example = (rule.word.trim().ifEmpty { "Claude" }) + OnyxLocalization[", what's using port 8880?"]
    val routed = routeVoiceText(example, listOf(rule))
    val unchanged = routed.text == example && routed.bot == null && routed.model == null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = rule.word.ifEmpty { "New prefix" },
                onBack = onBack,
                action = "Done",
                localizeTitle = rule.word.isEmpty(),
                onAction = onBack,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            OnyxFormSection(
                header = "Spoken word",
                footer = "Aliases catch the ways speech recognition tends to hear the word.",
            ) {
                Field("Word", onChange = { value -> onChange(rule.copy(word = value)) }, value = rule.word)
                OnyxRowDivider()
                Field(
                    label = "Aliases, comma-separated",
                    value = aliases,
                    onChange = { value ->
                        aliases = value
                        onChange(
                            rule.copy(
                                aliases = value.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            ),
                        )
                    },
                )
            }

            OnyxFormSection(
                header = "Text prefix",
                footer = "What replaces the word. Spaces count, including a space at the end. Leave it empty to only switch the model.",
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OnyxTextField(
                        value = rule.prefix,
                        onValueChange = { value -> onChange(rule.copy(prefix = value)) },
                        placeholder = "Prefix",
                        style = theme.body.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }

            OnyxFormSection(
                header = "Bot",
                footer = "Goes to this bot's chat before sending, and voice stays with it until another rule moves it.",
            ) {
                OnyxValueRow(
                    label = "Bot",
                    value = rule.bot?.let { profile -> bots.firstOrNull { it.name == profile }?.label() ?: profile }
                        ?: "Stay where I am",
                    localizeValue = rule.bot == null,
                    onClick = { showBots = !showBots },
                )
                if (showBots) {
                    OnyxRowDivider()
                    OnyxRow(onClick = {
                        showBots = false
                        onChange(rule.copy(bot = null))
                    }) {
                        OnyxIcon(
                            symbol = if (rule.bot == null) Symbols.CheckmarkCircle else Symbols.Circle,
                            tint = if (rule.bot == null) theme.accent else theme.tertiaryText,
                            size = 18.dp,
                        )
                        BasicText("Stay where I am", style = theme.body.copy(color = theme.primaryText), modifier = Modifier.weight(1f))
                    }
                    bots.filterNot { it.isHidden }.forEach { bot ->
                        OnyxRowDivider()
                        OnyxRow(onClick = {
                            showBots = false
                            onChange(rule.copy(bot = bot.name))
                        }) {
                            OnyxIcon(
                                symbol = if (rule.bot == bot.name) Symbols.CheckmarkCircle else Symbols.Circle,
                                tint = if (rule.bot == bot.name) theme.accent else theme.tertiaryText,
                                size = 18.dp,
                            )
                            BasicText(bot.label(), localize = false, style = theme.body.copy(color = theme.primaryText), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            OnyxFormSection(
                header = "Model",
                footer = "Switches the conversation to this model before sending, and it stays there until you pick another.",
            ) {
                OnyxValueRow(
                    label = "Model",
                    value = rule.model ?: "Keep current",
                    localizeValue = rule.model == null,
                    onClick = { showModels = !showModels },
                )
                if (showModels) {
                    OnyxRowDivider()
                    OnyxRow(onClick = {
                        showModels = false
                        onChange(rule.copy(model = null, provider = null))
                    }) {
                        OnyxIcon(
                            symbol = if (rule.model == null) Symbols.CheckmarkCircle else Symbols.Circle,
                            tint = if (rule.model == null) theme.accent else theme.tertiaryText,
                            size = 18.dp,
                        )
                        BasicText("Keep current", style = theme.body.copy(color = theme.primaryText), modifier = Modifier.weight(1f))
                    }
                    models.forEach { model ->
                        OnyxRowDivider()
                        OnyxRow(onClick = {
                            showModels = false
                            onChange(rule.copy(model = model))
                        }) {
                            OnyxIcon(
                                symbol = if (rule.model == model) Symbols.CheckmarkCircle else Symbols.Circle,
                                tint = if (rule.model == model) theme.accent else theme.tertiaryText,
                                size = 18.dp,
                            )
                            IdentifierText(
                                text = model,
                                style = theme.body.copy(color = theme.primaryText),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            OnyxFormSection(header = "Preview") {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    BasicText(
                        text = "“$example”",
                        localize = false,
                        style = theme.body.copy(color = theme.secondaryText),
                    )
                }
                OnyxRowDivider()
                if (unchanged) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        BasicText(
                            text = "Unchanged: add a word, then a bot, a prefix or a model.",
                            style = theme.footnote.copy(color = theme.secondaryText),
                        )
                    }
                } else {
                    routed.bot?.let { profile ->
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            BasicText(
                                text = "To ${bots.firstOrNull { it.name == profile }?.label() ?: profile}",
                                style = theme.callout.copy(color = theme.secondaryText),
                            )
                        }
                    }
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        BasicText(
                            text = routed.text,
                            localize = false,
                            style = theme.body.copy(color = theme.primaryText, fontFamily = FontFamily.Monospace),
                        )
                    }
                    routed.model?.let { model ->
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            BasicText(
                                text = "Model → $model",
                                style = theme.callout.copy(color = theme.secondaryText),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, onChange: (String) -> Unit, value: String = "") {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(
            text = label,
            style = theme.body.copy(color = theme.primaryText),
            modifier = Modifier.weight(0.9f),
        )
        OnyxTextField(
            value = value,
            onValueChange = onChange,
            placeholder = label,
            style = theme.body,
            modifier = Modifier.weight(1.1f),
        )
    }
}
