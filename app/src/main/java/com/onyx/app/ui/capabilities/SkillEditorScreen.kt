package com.onyx.app.ui.capabilities

import com.onyx.app.data.net.humanFailure
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxTappable

import com.onyx.app.ui.design.onyxPage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
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
 * The content the editor opens **pre-filled** with — the draft of a demonstration
 * (`skills.draft`) that the user has reread and kept. A draft is born from a `POST api/skills`
 * like any skill: the screen never writes itself, it only places the
 * text in its two fields.
 */
data class SkillEditorSeed(val name: String, val content: String)

/**
 * Create or edit a skill — **the feature the "Skills" tile used to carry alone**.
 *
 * It now lives in the "Skills" family of "Capabilities": it is the only gesture the
 * screen did not take over (the catalog *installs* a skill from the hub, it does not write it).
 * The screen itself is unchanged — same form, same content route, same save.
 *
 * @param seed the draft of a demonstration, when "Make it a skill" opened this screen:
 *   it fills the name and the content, and the save remains `POST api/skills` (the screen does not
 *   change its mind about who writes a skill).
 */
@Composable
fun SkillEditorScreen(
    server: ServerConfig,
    existing: String?,
    scope: CoroutineScope,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    seed: SkillEditorSeed? = null,
    /**
     * **"Make it a routine"** — the third surface of the "Teach a task" batch.
     *
     * When set, the screen offers to turn the skill into a routine: it **saves it first**
     * (a routine must name a skill that actually exists), then returns its name to the caller, which
     * opens the schedules screen with the draft already filled. `null` — the case of all other
     * callers — the screen stays exactly the one from before.
     */
    onMakeRoutine: ((String) -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    val isNew = existing == null

    // A demonstration draft arrives through [seed]: the name suggested by the server and the
    // converted Markdown. Without a draft, the screen is today's, identically.
    var name by remember(existing, seed) { mutableStateOf(existing ?: seed?.name.orEmpty()) }
    var category by remember { mutableStateOf("") }
    var content by remember(existing, seed) { mutableStateOf(seed?.content.orEmpty()) }
    var loading by remember(existing) { mutableStateOf(existing != null) }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    val canSave = !saving && name.isNotBlank() && content.isNotBlank()

    /**
     * Saves the skill, then hands control back to [afterwards].
     *
     * Two exits, a single save: the header button goes to [onSaved], the
     * "Make it a routine" row goes to the schedules screen — and both must have written the
     * skill before, otherwise the routine would name a skill that does not exist yet.
     */
    fun saveThen(afterwards: () -> Unit) {
        if (!canSave) return
        saving = true
        scope.launch {
            runCatching {
                val api = ServeSkillsApi(ServeClient(server))
                if (isNew) api.create(name.trim(), content, category.takeIf { it.isNotBlank() })
                else api.saveContent(name.trim(), content)
            }.onSuccess { afterwards() }
                .onFailure { problem = humanFailure(it) }
            saving = false
        }
    }

    LaunchedEffect(existing) {
        if (existing == null) return@LaunchedEffect
        loading = true
        runCatching { ServeSkillsApi(ServeClient(server)).content(existing) }
            .onSuccess { content = it }
            .onFailure { problem = humanFailure(it) }
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme)
            // The navigation margin is **carried by the screen**: the pane no longer applies it
            // when a destination is open, so that the page background passes under the
            // navigation bar instead of the pane's.
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = if (isNew) "New skill" else name,
                onBack = onBack,
                action = if (saving) "Saving…" else "Save",
                localizeTitle = isNew,
                onAction = {
                    if (!canSave) return@OnyxPageHeader
                    saveThen(onSaved)
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

            // **The third surface of the batch: the demonstration becomes a routine.**
            //
            // The skill is saved first — a routine that names a nonexistent skill would
            // do nothing and would not say so —, then the schedules screen opens with the
            // draft filled. The schedule and the destination remain empty: it is up to the person to
            // choose them (see `SkillRoutine`).
            if (onMakeRoutine != null) {
                OnyxFormSection(
                    header = "Routine",
                    footer = "Runs this skill on a schedule you choose. The skill stays on the server: " +
                        "fixing it later fixes every routine that uses it.",
                ) {
                    OnyxRow(
                        modifier = Modifier.onyxTappable(
                            onClick = {
                                if (!canSave) return@onyxTappable
                                saveThen { onMakeRoutine(name.trim()) }
                            },
                        ),
                    ) {
                        OnyxIcon(symbol = Symbols.ArrowClockwise, tint = theme.accent, size = 18.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            BasicText(
                                text = "Make it a routine",
                                localize = false,
                                style = theme.body.copy(color = if (canSave) theme.textColor else theme.secondaryText),
                            )
                            BasicText(
                                text = if (canSave) "Saves the skill, then opens Routines." else "Name and content are needed first.",
                                localize = false,
                                style = theme.footnote.copy(color = theme.secondaryText),
                            )
                        }
                    }
                }
            }
        }
    }
}
