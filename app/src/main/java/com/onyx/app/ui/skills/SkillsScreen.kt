package com.onyx.app.ui.skills

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
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.DashboardSkill
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeSkillsApi
import com.onyx.app.data.net.TransportError
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Les compétences du gateway (`SkillsView`). */
@Composable
fun SkillsScreen(
    server: ServerConfig,
    onOpenSkill: (String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    var skills by remember(server.id) { mutableStateOf<List<DashboardSkill>>(emptyList()) }
    var loading by remember(server.id) { mutableStateOf(true) }
    var problem by remember(server.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(server.id) {
        loading = true
        runCatching { ServeSkillsApi(ServeClient(server)).skills() }
            .onSuccess { skills = it }
            .onFailure { problem = (it as? TransportError)?.userMessage() ?: it.message }
        loading = false
    }

    Column(
        modifier = modifier.fillMaxSize().background(theme.pageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Skills", onBack = onBack, action = "New", onAction = { onOpenSkill(null) })
        }

        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 40.dp),
        ) {
            problem?.let {
                OnyxFormSection {
                    OnyxRow {
                        OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 16.dp)
                        BasicText(it, localize = false, style = theme.footnote.copy(color = theme.secondaryText), modifier = Modifier.weight(1f))
                    }
                }
            }

            when {
                loading -> OnyxFormSection {
                    OnyxRow { BasicText("Loading skills…", style = theme.body.copy(color = theme.secondaryText)) }
                }

                skills.isEmpty() -> OnyxFormSection(
                    footer = "A skill is a SKILL.md the agent loads on demand when a request matches.",
                ) {
                    OnyxRow { BasicText("No skills yet", style = theme.body.copy(color = theme.secondaryText)) }
                }

                else -> OnyxFormSection(header = "Skills") {
                    skills.forEachIndexed { index, skill ->
                        if (index > 0) OnyxRowDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().onyxTappable { onOpenSkill(skill.name) }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                BasicText(skill.name, localize = false, style = theme.body.copy(color = theme.textColor))
                                skill.description.takeIf { it.isNotBlank() }?.let {
                                    BasicText(it, localize = false, style = theme.footnote.copy(color = theme.secondaryText), maxLines = 2)
                                }
                                skill.category?.takeIf { it.isNotBlank() }?.let {
                                    BasicText(it, localize = false, style = theme.caption.copy(color = theme.tertiaryText))
                                }
                            }
                            if (!skill.enabled) {
                                BasicText("Off", style = theme.caption.copy(color = theme.tertiaryText))
                            }
                            OnyxIcon(Symbols.ChevronRight, tint = theme.tertiaryText, size = 12.dp)
                        }
                    }
                }
            }
        }
    }
}

/** Créer ou modifier une compétence (`SkillEditorView`). */
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

    Column(modifier = modifier.fillMaxSize().background(theme.pageColor)) {
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
                            .background(theme.surfaceColor, RoundedCornerShape(10.dp))
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
