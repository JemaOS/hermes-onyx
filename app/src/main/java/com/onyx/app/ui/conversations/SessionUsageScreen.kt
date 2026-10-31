package com.onyx.app.ui.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeSessionsApi
import com.onyx.app.data.net.SessionSummary
import com.onyx.app.data.net.SessionUsage
import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * « Usage & cost », port de `SessionUsageView` (Echo/Views/ConversationsView:1860).
 *
 * Les jetons d'une session (entrée, sortie, raisonnement, cache lu et écrit, puis le total), son
 * coût estimé et réel quand il y en a un, et son activité — messages, appels d'outils, appels
 * d'API, modèle et origine.
 *
 * [initial] porte déjà ce que l'appelant sait — le fil local d'une conversation, ou la ligne du
 * registre. Quand [fetchId] désigne une session du gateway et que [server] peut l'atteindre, les
 * chiffres du serveur remplacent les locaux ; sinon l'écran montre ce qu'il a, sans inventer le
 * raisonnement ni le coût, que le flux ne remonte pas.
 *
 * **[profile] est le profil Hermes qui porte la session** — celui du bot de la conversation
 * (`com.onyx.app.ui.bots.conversationProfile`), sinon celui du serveur. Les sessions sont écrites
 * **par profil** (`~/.hermes/profiles/<bot>/state.db`) : une lecture qui oublie le profil interroge
 * la base `default`, n'y trouve pas la ligne d'un bot, et l'écran annonçait alors « la route du
 * gateway n'a pas répondu » sur une session parfaitement enregistrée. C'est le défaut signalé.
 */
@Composable
fun SessionUsageScreen(
    initial: SessionSummary,
    onBack: () -> Unit,
    server: ServerConfig? = null,
    fetchId: String? = null,
    profile: String? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val locale = OnyxLanguage.preferred
    var summary by remember(initial.id) { mutableStateOf(initial) }

    /**
     * D'où viennent les chiffres. **Quatre issues distinctes**, parce que les confondre était la
     * faute : « la route n'a pas répondu » et « la route a répondu, mais pas de ligne pour cette
     * session dans ce profil » ne disent pas la même chose, et accuser le téléphone pour la seconde
     * était le drapeau menteur.
     */
    var source by remember(initial.id, fetchId, server?.id, profile) {
        mutableStateOf<UsageSource>(UsageSource.Waiting)
    }

    LaunchedEffect(initial.id, fetchId, server?.id, server?.configSignature, profile) {
        val stored = fetchId ?: run { source = UsageSource.NoSession; return@LaunchedEffect }
        val reachable = server ?: run { source = UsageSource.NoSession; return@LaunchedEffect }
        if (!reachable.hasServeCredentials) { source = UsageSource.NoSession; return@LaunchedEffect }
        val outcome = runCatching {
            ServeSessionsApi(ServeClient(reachable)).summary(stored, profile)
        }
        val row = outcome.getOrNull()
        source = when {
            row != null -> {
                summary = row
                UsageSource.Registry
            }
            outcome.isSuccess -> UsageSource.NoRow(profileName(profile))
            else -> UsageSource.Silent(
                outcome.exceptionOrNull()?.message
                    ?: "the route answered nothing",
            )
        }
    }

    val cost = summary.cost ?: 0.0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor)
            // La zone du système est prise par la racine : la dernière rangée du corps ne peut
            // plus être enterrée sous la barre de navigation ni sous la zone du geste d'accueil.
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = summary.displayTitle,
                onBack = null,
                action = "Done",
                onAction = onBack,
                localizeTitle = false,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            OnyxFormSection(header = "Tokens") {
                UsageRow("Input", SessionUsage.count(summary.inputTokens))
                OnyxRowDivider()
                UsageRow("Output", SessionUsage.count(summary.outputTokens))
                OnyxRowDivider()
                UsageRow("Reasoning", SessionUsage.count(summary.reasoningTokens))
                OnyxRowDivider()
                UsageRow("Cache read", SessionUsage.count(summary.cacheReadTokens))
                OnyxRowDivider()
                UsageRow("Cache write", SessionUsage.count(summary.cacheWriteTokens))
                OnyxRowDivider()
                // Le total se replie sur ce qui est **connu** : deux entrées absentes ne font pas
                // un zéro, elles font « — ».
                UsageRow("Total", SessionUsage.count(summary.totalTokensOrNull))
            }

            if (cost > 0) {
                OnyxFormSection(header = "Cost") {
                    UsageRow("Estimated", SessionUsage.money(summary.estimatedCostUsd))
                    OnyxRowDivider()
                    UsageRow("Actual", SessionUsage.money(summary.actualCostUsd))
                }
            }

            OnyxFormSection(header = "Activity") {
                UsageRow("Messages", SessionUsage.count(summary.messageCount))
                OnyxRowDivider()
                UsageRow("Tool calls", SessionUsage.count(summary.toolCallCount))
                OnyxRowDivider()
                UsageRow("API calls", SessionUsage.count(summary.apiCallCount))
                summary.model?.takeIf { it.isNotEmpty() }?.let { model ->
                    OnyxRowDivider()
                    UsageRow("Model", model, verbatim = true)
                }
                OnyxRowDivider()
                UsageRow("Source", summary.sourceLabel, verbatim = true)
            }

            if (cost > 0) {
                OnyxFormSection {
                    OnyxRow {
                        BasicText(
                            text = "Costs are the gateway's own estimate from provider pricing.",
                            style = theme.footnote.copy(color = theme.secondaryText),
                        )
                    }
                }
            }

            // **Ce qui manque côté serveur, dit au lieu d'être tu** — et dit *juste* : une route qui
            // a répondu sans cette ligne n'est pas une route muette. Les deux phrases sont
            // composées ici, puis traduites (`localize = false`) : une phrase déjà cousue ne se
            // traduit plus.
            when (val origin = source) {
                UsageSource.Registry -> Unit

                // 1. La route a **répondu**, elle ne porte simplement pas cette session sous ce
                //    profil. Accuser le téléphone ici était le drapeau menteur : le profil est nommé.
                is UsageSource.NoRow -> OnyxFormSection {
                    OnyxRow {
                        BasicText(
                            text = OnyxLocalization.format(KEY_REGISTRY_NO_ROW, profileName(origin.profile), locale = locale),
                            localize = false,
                            style = theme.footnote.copy(color = theme.secondaryText),
                        )
                    }
                }

                // 2. La route **n'a pas répondu** : c'est le cas que la phrase d'origine décrivait.
                is UsageSource.Silent -> OnyxFormSection {
                    OnyxRow {
                        BasicText(
                            text = OnyxLocalization.translate(KEY_ROUTE_SILENT, locale),
                            localize = false,
                            style = theme.footnote.copy(color = theme.secondaryText),
                        )
                    }
                }

                // On n'a encore rien demandé, ou il n'y a pas de session à demander : rien à dire.
                UsageSource.Waiting, UsageSource.NoSession -> Unit
            }
        }
    }
}

/** La route n'a pas répondu du tout : la phrase d'origine, telle quelle (elle est traduite). */
internal const val KEY_ROUTE_SILENT =
    "The gateway's usage route didn't answer for this session, so these are the counts this phone " +
        "kept. Reasoning tokens and cost come from the gateway alone and stay blank."

/**
 * La route a répondu, mais **sans cette session sous ce profil** : le profil est nommé, et le
 * téléphone n'est pas accusé d'une panne qui n'a pas eu lieu. Un trou pour le nom du profil.
 */
internal const val KEY_REGISTRY_NO_ROW =
    "The gateway's registry answered, but it holds no row for this session under the profile " +
        "“%1\$s”, so these are the counts this phone kept. Reasoning tokens and cost come from the " +
        "gateway alone and stay blank."

/** Ce que la lecture du registre a donné, distinctement. */
internal sealed interface UsageSource {
    /** Pas encore essayé. */
    data object Waiting : UsageSource

    /** La conversation ne porte pas de session serveur (ou pas de Dashboard) : rien à interroger. */
    data object NoSession : UsageSource

    /** Le registre a rendu la ligne de cette session : les chiffres sont ceux du serveur. */
    data object Registry : UsageSource

    /** Le registre a répondu, mais sans cette session **sous ce profil** — [profile] le nomme. */
    data class NoRow(val profile: String) : UsageSource

    /** La route n'a pas répondu (panne, refus, adresse) : [reason] garde ce que le serveur a dit. */
    data class Silent(val reason: String) : UsageSource
}

/** Le nom du profil tel qu'il se lit dans la notice : jamais vide. */
internal fun profileName(profile: String?): String =
    profile?.trim()?.takeIf { it.isNotEmpty() } ?: "default"

/** Une ligne « libellé → valeur » de la feuille, les chiffres en chasse fixe comme la référence. */
@Composable
private fun UsageRow(label: String, value: String, verbatim: Boolean = false) {
    val theme = LocalOnyxTheme.current
    OnyxRow {
        BasicText(
            text = label,
            style = theme.body.copy(color = theme.primaryText),
            modifier = Modifier.weight(1f),
        )
        BasicText(
            text = value,
            localize = !verbatim,
            style = theme.body.copy(color = theme.secondaryText, fontFamily = FontFamily.Monospace),
        )
    }
}
