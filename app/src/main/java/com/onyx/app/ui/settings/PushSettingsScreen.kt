package com.onyx.app.ui.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.notifications.ApprovalNotifier
import com.onyx.app.notifications.NotificationChannels
import com.onyx.app.notifications.TurnService
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxToggleRow
import com.onyx.app.ui.design.OnyxValueRow
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Réglages → Voice → Notifications.
 *
 * Comme. Le est le relais: il appaire des
 * Hermes, chiffre les alertes entre eux et les fait livrer par un relais. Android n'a pas de
 * relais — [ApprovalNotifier] et [TurnService] publient en local, depuis la connexion de
 * Réglages — donc chaque section devient ce qui lui correspond réellement ici:
 *
 * - la liste des appairages (état suivi, retrait par balayage) devient la liste des canaux
 * d'Android ([NotificationChannels]), chacun avec son état système et le réglage du canal;
 * - l'appairage et l'installation du greffon (commande à copier, `hermes onyx-push test`)
 * deviennent l'action d'alerte de démonstration, qui prouve la permission et crée les canaux;
 * - les pieds de page sur le chiffrement et le relais deviennent « rien ne part, tout est publié
 * sur ce téléphone ».
 *
 * Aucun appairage APNs n'est présenté comme fonctionnel.
 */
@Composable
fun PushSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val notifyInBackground by viewModel.notifyInBackground.collectAsStateWithLifecycle()
    val notificationSound by viewModel.notificationSound.collectAsStateWithLifecycle()
    val showLiveActivity by viewModel.showLiveActivity.collectAsStateWithLifecycle()
    var systemEnabled by remember { mutableStateOf(context.notificationsEnabled()) }
    var channels by remember { mutableStateOf(context.onyxChannels()) }

    /** L'action à reprendre une fois la permission Android accordée (l'alerte de test). */
    var afterGrant by remember { mutableStateOf<(() -> Unit)?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Le retour des réglages d'Android change l'état système *et* les canaux.
                systemEnabled = context.notificationsEnabled()
                channels = context.onyxChannels()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        systemEnabled = context.notificationsEnabled()
        channels = context.onyxChannels()
        val pending = afterGrant
        afterGrant = null
        if (granted) {
            if (pending != null) pending() else viewModel.setNotifyInBackground(true)
        }
    }

    fun enableBackgroundNotifications() {
        when {
            systemEnabled -> viewModel.setNotifyInBackground(true)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> context.openNotificationSettings()
        }
    }

    /** Publie une alerte de démonstration, en demandant d'abord la permission si besoin. */
    fun sendTestNotification() {
        when {
            systemEnabled -> context.postTestNotification(notificationSound)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED -> {
                afterGrant = { context.postTestNotification(notificationSound) }
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> context.openNotificationSettings()
        }
        // L'alerte vient de créer les canaux qui n'existaient pas encore.
        systemEnabled = context.notificationsEnabled()
        channels = context.onyxChannels()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Notifications", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            // Le portier du système: c'est lui qui décide si Onyx publie quoi que ce soit.
            OnyxFormSection(
                header = "Android",
                footer = if (systemEnabled) {
                    "Android allows Onyx to post notifications. System and channel settings take precedence over the choices below."
                } else {
                    "Android is blocking Onyx notifications. Open its settings, allow notifications, then return here to enable background alerts."
                },
            ) {
                OnyxValueRow(
                    label = "Notification settings",
                    value = if (systemEnabled) "On" else "Off",
                    symbol = if (systemEnabled) Symbols.Bell else Symbols.BellSlash,
                    onClick = context::openNotificationSettings,
                )
            }

            // Le Comme la liste des appairages: les canaux, leur état, et
            // le réglage du canal. Android ne laisse pas l'app retirer un canal: on vient le
            // régler dans ses propres réglages, comme laisse retirer un appairage.
            OnyxFormSection(
                header = "Channels",
                footer = "Android keeps one channel for each kind of alert: when a reply is ready, when the agent waits " +
                    "for you (an approval, a question, a password) and when it couldn't reply. Tap a channel to change " +
                    "its sound or turn it off in Android's settings; Onyx can't change those settings itself. " +
                    "Nothing passes through a relay — every notification is posted on this phone, from the connection in Settings.",
            ) {
                if (channels.isEmpty()) {
                    OnyxRow {
                        BasicText(
                            "No channels yet.",
                            style = theme.body.copy(color = theme.secondaryText),
                        )
                    }
                } else {
                    channels.forEachIndexed { index, channel ->
                        if (index > 0) OnyxRowDivider()
                        OnyxValueRow(
                            label = channel.label,
                            value = channel.state.label(),
                            symbol = channel.symbol,
                            tint = if (channel.state == NotificationChannels.State.ON) theme.accent else theme.secondaryText,
                            onClick = { context.openChannelSettings(channel.id) },
                        )
                    }
                }
            }

            // Les choix, et l'action qui met le tout à l'épreuve.
            OnyxFormSection(
                header = "Onyx",
                footer = "Background alerts tell you when an agent needs an approval while you use another app. " +
                    "They work while Android lets Onyx run; force-stopping Onyx stops them. " +
                    "The ongoing answer notification shows progress and keeps the current turn alive.",
            ) {
                OnyxToggleRow(
                    label = "Notify me in the background",
                    checked = notifyInBackground,
                    onCheckedChange = { enabled ->
                        if (enabled) enableBackgroundNotifications() else viewModel.setNotifyInBackground(false)
                    },
                )
                OnyxRowDivider()
                OnyxToggleRow(
                    label = "Notification sound",
                    checked = notificationSound,
                    onCheckedChange = viewModel::setNotificationSound,
                )
                OnyxRowDivider()
                OnyxToggleRow(
                    label = "Ongoing answer notification",
                    checked = showLiveActivity,
                    onCheckedChange = viewModel::setShowLiveActivity,
                )
                OnyxRowDivider()
                OnyxRow(onClick = ::sendTestNotification) {
                    OnyxIcon(Symbols.Bell, tint = theme.accent, size = 18.dp)
                    BasicText(
                        text = "Send a test notification",
                        style = theme.body.copy(color = theme.accent),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Un canal Onyx tel qu'Android le rapporte: son libellé, son état et son symbole. */
private data class ChannelRow(
    val id: String,
    val label: String,
    val state: NotificationChannels.State,
    val symbol: Int,
)

/** Le libellé localisé de l'état d'un canal. */
private fun NotificationChannels.State.label(): String = when (this) {
    NotificationChannels.State.ON -> "On"
    NotificationChannels.State.OFF -> "Off"
    NotificationChannels.State.ABSENT -> "Not created yet"
}

/** Les canaux que Onyx peut régler ici, avec l'état réel que le système en rapporte. */
private fun Context.onyxChannels(): List<ChannelRow> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
    return NotificationChannels.all.map { channel ->
        ChannelRow(
            id = channel.id,
            label = channel.label,
            state = NotificationChannels.state(channelImportance(channel.id)),
            symbol = when (channel.id) {
                ApprovalNotifier.SILENT_CHANNEL_ID -> Symbols.BellSlash
                TurnService.CHANNEL_ID -> Symbols.Waveform
                else -> Symbols.Bell
            },
        )
    }
}

/** L'importance du canal, ou `null` quand il n'existe pas encore (Android le crée à la 1re alerte). */
private fun Context.channelImportance(id: String): Int? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    val manager = getSystemService(NotificationManager::class.java) ?: return null
    return manager.getNotificationChannel(id)?.importance
}

/** Ouvre le réglage Android du canal, avec un repli sur les réglages de l'app. */
private fun Context.openChannelSettings(channelId: String) {
    val channel = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
    runCatching { startActivity(channel) }.recoverCatching { openNotificationSettings() }
}

/** Publie l'alerte de démonstration sur le canal sonore ou silencieux, comme les vraies. */
private fun Context.postTestNotification(sound: Boolean) =
    ApprovalNotifier.notifyTest(this, sound = sound)

/** État global réel d'Android, y compris avant Android 13 et après un refus dans ses réglages. */
private fun Context.notificationsEnabled(): Boolean =
    NotificationManagerCompat.from(this).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED)

/** Ouvre le panneau de notifications de Onyx, avec un repli sur la fiche de l'app. */
private fun Context.openNotificationSettings() {
    val notifications = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    runCatching { startActivity(notifications) }.recoverCatching {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
    }
}
