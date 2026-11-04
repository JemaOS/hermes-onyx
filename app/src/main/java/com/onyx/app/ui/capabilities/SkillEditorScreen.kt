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
 * Le contenu dont l'éditeur s'ouvre **pré-rempli** — le brouillon d'une démonstration
 * (`skills.draft`) que l'utilisateur a relu et gardé. Un brouillon naît d'un `POST api/skills`
 * comme n'importe quelle compétence : l'écran n'écrit jamais lui-même, il ne fait que poser le
 * texte dans ses deux champs.
 */
data class SkillEditorSeed(val name: String, val content: String)

/**
 * Créer ou modifier une compétence — **la fonction que la tuile « Compétences » portait seule**.
 *
 * Elle vit désormais dans la famille « Compétences » de « Capacités » : c'est le seul geste que
 * l'écran ne reprenait pas (le catalogue *installe* une compétence du hub, il ne l'écrit pas).
 * L'écran lui-même est inchangé — même formulaire, même route de contenu, même enregistrement.
 *
 * @param seed le brouillon d'une démonstration, quand « En faire un skill » a ouvert cet écran :
 *   il remplit le nom et le contenu, et l'enregistrement reste `POST api/skills` (l'écran ne
 *   change pas d'avis sur qui écrit une compétence).
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
     * **« En faire une routine »** — la troisième surface du lot « Teach a task ».
     *
     * Posé, l'écran propose de transformer la compétence en routine : il l'**enregistre d'abord**
     * (une routine doit nommer un skill qui existe vraiment), puis rend son nom à l'appelant, qui
     * ouvre l'écran des horaires avec le brouillon déjà rempli. `null` — le cas de tous les autres
     * appelants — l'écran reste exactement celui d'avant.
     */
    onMakeRoutine: ((String) -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    val isNew = existing == null

    // Un brouillon de démonstration arrive par [seed] : le nom proposé par le serveur et le
    // Markdown converti. Sans brouillon, l'écran est celui d'aujourd'hui, à l'identique.
    var name by remember(existing, seed) { mutableStateOf(existing ?: seed?.name.orEmpty()) }
    var category by remember { mutableStateOf("") }
    var content by remember(existing, seed) { mutableStateOf(seed?.content.orEmpty()) }
    var loading by remember(existing) { mutableStateOf(existing != null) }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    val canSave = !saving && name.isNotBlank() && content.isNotBlank()

    /**
     * Enregistre la compétence, puis rend la main à [afterwards].
     *
     * Deux sorties, un seul enregistrement : le bouton d'en-tête part vers [onSaved], la ligne
     * « En faire une routine » part vers l'écran des horaires — et les deux doivent avoir écrit le
     * skill avant, sinon la routine nommerait une compétence qui n'existe pas encore.
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
            // La marge de navigation est **portée par l'écran** : le volet ne la pose plus
            // quand une destination est ouverte, pour que le fond de la page passe sous la
            // barre de navigation au lieu de celui du volet.
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

            // **La troisième surface du lot : la démonstration devient une routine.**
            //
            // La compétence est d'abord enregistrée — une routine qui nomme un skill inexistant ne
            // ferait rien et ne le dirait pas —, puis l'écran des horaires s'ouvre avec le
            // brouillon rempli. L'horaire et la destination restent vides : c'est à la personne de
            // les choisir (voir `SkillRoutine`).
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
