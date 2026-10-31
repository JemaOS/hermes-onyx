package com.onyx.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.calls.QuietHours
import com.onyx.app.di.AppContainer
import com.onyx.app.notifications.CallRinger
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxToggleRow
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.launch

/**
 * Réglages → Phone → **Calls**.
 *
 * Le pendant Android de `PhoneToolsSettingsView` de la référence, réduit aux appels : « Appels pour
 * une réponse » (une approbation ou une question sonne comme un appel), « Répondre en voix live »
 * (décrocher met Hermes en ligne), et les **heures de tranquillité** — rien ne sonne la nuit.
 *
 * Tout est dessiné maison (`OnyxFormSection`, `OnyxRow`, et deux flèches pour l'heure) : aucun
 * composant Material, comme le reste de l'app.
 */
@Composable
fun CallsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val settings = AppContainer.settings
    val scope = rememberCoroutineScope()

    val phoneRings by settings.phoneRings.collectAsStateWithLifecycle()
    val phoneRingsTalk by settings.phoneRingsTalk.collectAsStateWithLifecycle()
    val quietHours by settings.quietHours.collectAsStateWithLifecycle()
    val quietFrom by settings.quietFrom.collectAsStateWithLifecycle()
    val quietUntil by settings.quietUntil.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Calls", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            OnyxFormSection(
                header = "Calls for an answer",
                footer = "When a turn is waiting for your decision — an approval or a question — the " +
                    "phone rings like a call, full screen over the lock screen. Off, you get the usual " +
                    "notification instead.",
            ) {
                OnyxToggleRow(
                    label = "Ring for a decision",
                    checked = phoneRings,
                    onCheckedChange = { value -> scope.launch { settings.setPhoneRings(value) } },
                )
            }

            if (phoneRings) {
                OnyxFormSection(
                    header = "Answering",
                    footer = "On: answering puts Onyx on the line, listening — you say your decision out " +
                        "loud. Off: answering opens the conversation, where the buttons wait.",
                ) {
                    OnyxToggleRow(
                        label = "Answer with live voice",
                        checked = phoneRingsTalk,
                        onCheckedChange = { value -> scope.launch { settings.setPhoneRingsTalk(value) } },
                    )
                }

                OnyxFormSection(
                    header = "Quiet hours",
                    footer = quietHoursDetail(quietHours, quietFrom, quietUntil),
                ) {
                    OnyxToggleRow(
                        label = "Quiet hours",
                        checked = quietHours,
                        onCheckedChange = { value -> scope.launch { settings.setQuietHours(value) } },
                    )
                    if (quietHours) {
                        OnyxRowDivider()
                        QuietTimeRow("Starts", quietFrom) { delta ->
                            scope.launch { settings.setQuietFrom(wrapDay(quietFrom + delta)) }
                        }
                        OnyxRowDivider()
                        QuietTimeRow("Ends", quietUntil) { delta ->
                            scope.launch { settings.setQuietUntil(wrapDay(quietUntil + delta)) }
                        }
                    }
                }

                OnyxFormSection(
                    header = "Try it",
                    footer = "Rings this phone now, exactly as a decision would. Declining leaves the " +
                        "usual notification.",
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onyxTappable(onClick = {
                                CallRinger.ring(
                                    context,
                                    CallRinger.Ringing(
                                        id = "test-" + System.currentTimeMillis(),
                                        title = "Onyx",
                                        why = "Has a question for you",
                                        talk = phoneRingsTalk,
                                    ),
                                )
                            })
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OnyxIcon(symbol = Symbols.PhoneOutline, tint = theme.accent, size = 18.dp)
                        BasicText(
                            text = "Ring this phone",
                            style = theme.body.copy(color = theme.primaryText),
                        )
                    }
                }
            }
        }
    }
}

/** Le détail sous l'interrupteur : la plage, ou qu'elle est vide. */
internal fun quietHoursDetail(enabled: Boolean, from: Int, until: Int): String = when {
    !enabled -> "Nothing is quiet yet."
    from == until -> "They start and end at ${QuietHours.clock(from)}, so nothing is quiet: pick another time."
    else -> "Nothing rings from ${QuietHours.clock(from)} to ${QuietHours.clock(until)}."
}

/** Une heure ramenée dans la journée (une plage qui traverse minuit reste valable). */
internal fun wrapDay(minute: Int): Int = ((minute % QuietHours.MINUTES_A_DAY) + QuietHours.MINUTES_A_DAY) % QuietHours.MINUTES_A_DAY

/** « Début » / « Fin » : l'heure, encadrée de deux flèches — par pas de 30 minutes. */
@Composable
private fun QuietTimeRow(label: String, minute: Int, onStep: (Int) -> Unit) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            style = theme.body.copy(color = theme.primaryText),
            modifier = Modifier.weight(1f),
        )
        StepArrow(Symbols.MinusCircle, "Earlier") { onStep(-STEP_MINUTES) }
        BasicText(
            text = QuietHours.clock(minute),
            localize = false,
            style = theme.body.copy(color = theme.primaryText, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        StepArrow(Symbols.PlusCircle, "Later") { onStep(STEP_MINUTES) }
    }
}

@Composable
private fun StepArrow(symbol: Int, description: String, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    OnyxIcon(
        symbol = symbol,
        tint = theme.accent,
        size = 18.dp,
        contentDescription = description,
        modifier = Modifier.onyxTappable(onClick = onClick).padding(6.dp),
    )
}

/** Le pas des flèches : une demi-heure. */
private const val STEP_MINUTES = 30
