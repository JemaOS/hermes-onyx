package com.onyx.app.ui.capabilities

import com.onyx.app.ui.design.OnyxSpace

import com.onyx.app.ui.design.onyxPage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeSkillsApi
import com.onyx.app.data.net.TransportError
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Créer ou modifier une compétence — **la fonction que la tuile « Compétences » portait seule**.
 *
 * Elle vit désormais dans la famille « Compétences » de « Capacités » : c'est le seul geste que
 * l'écran ne reprenait pas (le catalogue *installe* une compétence du hub, il ne l'écrit pas).
 * L'écran lui-même est inchangé — même formulaire, même route de contenu, même enregistrement.
 */
@Composable
fun SkillEditorScreen(
    server: ServerConfig,
    existing: String?,
    scope: CoroutineScope,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val isNew = existing == null

    var name by remember(existing) { mutableStateOf(existing.orEmpty()) }
    var category by remember { mutableStateOf("") }
    var content by remember(existing) { mutableStateOf("") }
    var loading by remember(existing) { mutableStateOf(!isNew) }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    val canSave = !saving && name.isNotBlank() && content.isNotBlank()

    LaunchedEffect(existing) {
        if (existing == null) return@LaunchedEffect
        loading = true
        runCatching { ServeSkillsApi(ServeClient(server)).content(existing) }
            .onSuccess { content = it }
            .onFailure { problem = (it as? TransportError)?.userMessage() ?: it.message }
        loading = false
    }

    Column(modifier = modifier.fillMaxSize().onyxPage(theme)) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = if (isNew) "New skill" else name,
                onBack = onBack,
                action = if (saving) "Saving…" else "Save",
                localizeTitle = isNew,
                onAction = {
                    if (!canSave) return@OnyxPageHeader
                    saving = true
                    scope.launch {
                        runCatching {
                            val api = ServeSkillsApi(ServeClient(server))
                            if (isNew) api.create(name.trim(), content, category.takeIf { it.isNotBlank() })
                            else api.saveContent(name.trim(), content)
                        }.onSuccess { onSaved() }
                            .onFailure { problem = (it as? TransportError)?.userMessage() ?: it.message }
                        saving = false
                    }
                },
            )
        }

        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
            OnyxFormSection(
                header = if (isNew) "New skill" else "Skill",
                footer = if (isNew) {
                    "Lowercase with hyphens, like the folder it becomes in ~/.hermes/skills."
                } else {
                    "Renaming isn't supported by the gateway; create a new skill instead."
                },
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OnyxTextField(
                        value = name,
                        onValueChange = { if (isNew) name = it },
                        placeholder = "skill-name",
                        style = theme.body,
                    )
                }
                if (isNew) {
                    OnyxRowDivider()
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OnyxTextField(
                            value = category,
                            onValueChange = { category = it },
                            placeholder = "Category (optional)",
                            style = theme.body,
                        )
                    }
                }
            }

            OnyxFormSection(
                header = "SKILL.md",
                footer = "Front matter (name, description, version, metadata) then When to Use, Procedure, Pitfalls, Verification. Onyx loads it on demand when a request matches.",
            ) {
                if (loading) {
                    OnyxRow { BasicText("Loading skill…", style = theme.body.copy(color = theme.secondaryText)) }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .height(320.dp)
                            .background(theme.surfaceColor, RoundedCornerShape(OnyxSpace.ControlCorner))
                            .padding(10.dp),
                    ) {
                        BasicTextField(
                            value = content,
                            onValueChange = { content = it },
                            textStyle = theme.footnote.copy(color = theme.textColor, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(theme.accent),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            problem?.let {
                OnyxFormSection {
                    OnyxRow { BasicText(it, localize = false, style = theme.footnote.copy(color = theme.error)) }
                }
            }
        }
    }
}
