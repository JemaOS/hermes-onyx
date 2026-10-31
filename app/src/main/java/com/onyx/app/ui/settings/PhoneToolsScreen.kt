package com.onyx.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.onyx.app.data.local.PhoneArea
import com.onyx.app.data.local.PhonePolicy
import com.onyx.app.data.local.PhoneToolsStore
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.OnyxToggleRow
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Réglages → Outils téléphone.
 *
 * Port de `PhoneToolsSettingsView` : un interrupteur maître, une politique par famille
 * (« Off », « Ask each time », « Allow »), et la liste de ce qui a été demandé récemment.
 *
 * L'exécution côté agent passe par le plugin Hermes `phone_*` ; cette page règle ce que Onyx
 * accepterait, et le widget « From Your Agent » s'écrit ici.
 */
@Composable
fun PhoneToolsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val store = remember { PhoneToolsStore(context) }

    var enabled by remember { mutableStateOf(store.enabled) }
    var policies by remember { mutableStateOf(PhoneArea.entries.associateWith(store::policy)) }
    var openArea by remember { mutableStateOf<PhoneArea?>(null) }
    var widgetText by remember { mutableStateOf(store.agentWidgetText) }
    var log by remember { mutableStateOf(store.recent()) }

    // Les permissions que chaque famille demande, quand on l'autorise.
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    fun requestPermissions() {
        val wanted = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.READ_CALENDAR)
                add(Manifest.permission.WRITE_CALENDAR)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.READ_CONTACTS)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }
        if (wanted.isNotEmpty()) permissions.launch(wanted.toTypedArray())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Phone tools", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            OnyxFormSection {
                OnyxToggleRow(
                    label = "Phone tools",
                    checked = enabled,
                    onCheckedChange = { value ->
                        enabled = value
                        store.enabled = value
                        if (value) requestPermissions() else Unit
                    },
                )
            }

            if (enabled) {
                OnyxFormSection(
                    header = "What it may use",
                    footer = "Ask each time puts the request in front of you before anything happens; Allow lets it run.",
                ) {
                    PhoneArea.entries.forEachIndexed { index, area ->
                        if (index > 0) OnyxRowDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onyxClickable { openArea = if (openArea == area) null else area }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                        ) {
                            OnyxIcon(symbol = symbolFor(area), tint = theme.accent, size = 18.dp)
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                BasicText(area.label, style = theme.body.copy(color = theme.primaryText))
                                BasicText(area.blurb, style = theme.footnote.copy(color = theme.secondaryText))
                            }
                            BasicText(
                                text = policies[area]?.label ?: PhonePolicy.OFF.label,
                                style = theme.body.copy(color = theme.secondaryText),
                            )
                        }
                        if (openArea == area) {
                            PhonePolicy.entries.forEach { policy ->
                                OnyxRowDivider()
                                OnyxRow(onClick = {
                                    policies = policies + (area to policy)
                                    store.setPolicy(area, policy)
                                    openArea = null
                                }) {
                                    OnyxIcon(
                                        symbol = if (policies[area] == policy) Symbols.CheckmarkCircle else Symbols.Circle,
                                        tint = if (policies[area] == policy) theme.accent else theme.tertiaryText,
                                        size = 18.dp,
                                    )
                                    BasicText(
                                        text = if (policy == PhonePolicy.ALLOW) area.allowLabel else policy.label,
                                        style = theme.body.copy(color = theme.primaryText),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                OnyxFormSection(
                    header = "The widget",
                    footer = "A few lines the agent can put on Onyx's “From Your Agent” widget.",
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OnyxTextField(
                            value = widgetText,
                            onValueChange = { value ->
                                widgetText = value
                                store.agentWidgetText = value
                            },
                            placeholder = "What to show on the widget",
                            singleLine = false,
                            maxLines = 4,
                        )
                    }
                }

                OnyxFormSection(header = "Asked for lately") {
                    if (log.isEmpty()) {
                        OnyxRow {
                            BasicText(
                                "Nothing yet.",
                                style = theme.body.copy(color = theme.secondaryText),
                            )
                        }
                    } else {
                        log.forEachIndexed { index, line ->
                            if (index > 0) OnyxRowDivider()
                            OnyxRow {
                                BasicText(line, localize = false, style = theme.footnote.copy(color = theme.secondaryText))
                            }
                        }
                        OnyxRowDivider()
                        OnyxRow(onClick = {
                            store.clearLog()
                            log = emptyList()
                        }) {
                            BasicText("Clear", style = theme.body.copy(color = theme.error))
                        }
                    }
                }
            }
        }
    }
}

/** L'icône d'une famille, comme la ligne de la référence. */
private fun symbolFor(area: PhoneArea): Int = when (area) {
    PhoneArea.CALENDAR -> Symbols.Calendar
    PhoneArea.REMINDERS -> Symbols.CheckmarkCircle
    PhoneArea.CONTACTS -> Symbols.PersonCircle
    PhoneArea.PHOTOS -> Symbols.PhotoOnRectangle
    PhoneArea.FILES -> Symbols.Folder
    PhoneArea.ALARMS -> Symbols.Clock
    PhoneArea.WIDGET -> Symbols.QuoteBubble
    PhoneArea.PEOPLE -> Symbols.Bubbles
}
