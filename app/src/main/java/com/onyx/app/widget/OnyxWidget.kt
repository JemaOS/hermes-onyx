package com.onyx.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.onyx.app.MainActivity
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.di.AppContainer
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.theme.OnyxSystem
import com.onyx.app.ui.theme.OnyxTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Le widget d'écran d'accueil: la dernière réponse, ou l'attente de la suivante.
 *
 * Comme la Live Activity / du widget: le même thème que l'app, la question
 * et la réponse en cours, et un tap qui ouvre la conversation. Glance ne sait pas charger nos
 * polices embarquées, donc le widget garde la police système.
 */
class OnyxWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Le travail suspendu se fait avant le rendu: la composition Glance reste synchrone.
        val snapshot = widgetSnapshot(context)
        provideContent { WidgetContent(snapshot) }
    }
}

/** Ce que le widget montre: soit la dernière réponse, soit une attente. */
private data class WidgetSnapshot(
    val question: String,
    val answer: String?,
    val theme: OnyxTheme,
    val contextPercent: Int? = null,
    val agentNote: String = "",
)

private suspend fun widgetContext(): Int? {
    if (!AppContainer.isInstalled) return null
    return runCatching {
        withTimeoutOrNull(800) {
            val prefs = AppContainer.preferences.data.first()
            val used = prefs[com.onyx.app.data.local.SettingsKeys.WIDGET_CONTEXT_USED] ?: return@withTimeoutOrNull null
            val window = prefs[com.onyx.app.data.local.SettingsKeys.WIDGET_CONTEXT_WINDOW] ?: return@withTimeoutOrNull null
            if (window > 0) (used * 100) / window else null
        }
    }.getOrNull()
}

private suspend fun widgetSnapshot(context: Context): WidgetSnapshot {
    val fallback = WidgetSnapshot(question = "", answer = null, theme = OnyxTheme.STANDARD)
    val percent = widgetContext()
    val agentNote = com.onyx.app.data.local.PhoneToolsStore(context).agentWidgetText
    if (!AppContainer.isInstalled) return fallback

    val conversation: Conversation = currentConversation() ?: return fallback

    val lastUser = conversation.messages.lastOrNull { it.role == Role.USER }
    val lastAssistant = conversation.messages.lastOrNull { it.role == Role.ASSISTANT && it.text.isNotBlank() }
    val answered = lastAssistant != null && lastUser != null && lastAssistant.createdAt > lastUser.createdAt

    val theme = runCatching { withTimeoutOrNull(1500) { AppContainer.settings.theme.first() } }.getOrNull()
        ?: OnyxTheme.STANDARD

    return WidgetSnapshot(
        question = lastUser?.text.orEmpty(),
        answer = if (answered) lastAssistant?.text else null,
        theme = theme,
        contextPercent = percent,
        agentNote = agentNote,
    )
}

/**
 * La conversation la plus récente. Le dépôt est lu au démarrage depuis le disque: on lui laisse
 * un court instant, sans quoi un widget ouvert à froid après un redémarrage ne montrerait rien.
 */
private suspend fun currentConversation(): Conversation? {
    val loaded = AppContainer.conversations.conversations.value
    val list = loaded.ifEmpty {
        withTimeoutOrNull(2000) {
            AppContainer.conversations.conversations.first { it.isNotEmpty() }
        }.orEmpty()
    }
    return list.maxByOrNull { it.updatedAt }
}

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot) {
    // Le widget est toujours sombre: il vit sur un fond d'écran d'accueil.
    val dark = true
    val palette = snapshot.theme.palette
    val background = palette.background?.resolve(dark) ?: OnyxSystem.background.resolve(dark)
    val surface = palette.surface?.resolve(dark) ?: OnyxSystem.secondaryBackground.resolve(dark)
    val textColor = palette.text?.resolve(dark) ?: OnyxSystem.label.resolve(dark)
    val secondary = OnyxSystem.secondaryLabel.resolve(dark)
    val accent = palette.accent.resolve(dark)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(background))
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = "Onyx",
                style = TextStyle(color = ColorProvider(accent), fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
            snapshot.contextPercent?.let { percent ->
                Text(
                    text = "   $percent % " + OnyxLocalization["context"],
                    style = TextStyle(color = ColorProvider(secondary), fontSize = 11.sp),
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (snapshot.agentNote.isNotBlank()) {
            Text(
                text = snapshot.agentNote,
                style = TextStyle(color = ColorProvider(accent), fontSize = 13.sp),
                maxLines = 4,
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
        }

        when {
            snapshot.question.isEmpty() -> Text(
                text = OnyxLocalization["Nothing asked yet."],
                style = TextStyle(color = ColorProvider(secondary), fontSize = 13.sp),
            )

            snapshot.answer != null -> {
                Text(
                    text = snapshot.question,
                    style = TextStyle(color = ColorProvider(secondary), fontSize = 13.sp),
                    maxLines = 2,
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    text = snapshot.answer,
                    style = TextStyle(color = ColorProvider(textColor), fontSize = 14.sp),
                    maxLines = 7,
                )
            }

            else -> {
                Text(
                    text = snapshot.question,
                    style = TextStyle(color = ColorProvider(secondary), fontSize = 13.sp),
                    maxLines = 2,
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(ColorProvider(surface))
                        .padding(8.dp),
                ) {
                    Text(
                        text = OnyxLocalization["Waiting for an answer…"],
                        style = TextStyle(color = ColorProvider(accent), fontSize = 13.sp),
                    )
                }
            }
        }
    }
}

/** Le récepteur du widget, déclaré dans le manifeste. */
class OnyxWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OnyxWidget()
}
