package com.onyx.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.di.AppContainer
import com.onyx.app.intake.ShareInbox
import com.onyx.app.notifications.ApprovalNotifier
import com.onyx.app.notifications.CallRinger
import com.onyx.app.shortcuts.BotShortcutInbox
import com.onyx.app.shortcuts.BotShortcutPlan
import com.onyx.app.shortcuts.BotShortcutRequest
import com.onyx.app.shortcuts.RosterShortcuts
import com.onyx.app.ui.OnyxApp
import com.onyx.app.ui.bots.BotTemplateShareCodec
import com.onyx.app.ui.design.LocalOnyxTextScale
import com.onyx.app.ui.design.ProvideReduceMotion

/**
 * The single activity. It collects what comes from outside — a share, an assistant
 * question, an App Action — and hands it to [ShareInbox] so that the UI sends it.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ApprovalNotifier.ensureChannel(this)
        // The calls channel: created at startup, like the approvals one, so that an incoming
        // call finds its channel already in place (ringtone and vibration included).
        CallRinger.ensureChannel(this)
        intake(intent)
        setContent {
            // "Reduce Motion" (French UI string « Réduire les animations »): the system flag is set at the root, as
            // `accessibilityReduceMotion` is for the whole reference app.
            ProvideReduceMotion {
                // **The Settings text scale applies to the whole app.** It is read here,
                // at the root, and provided by `LocalOnyxTextScale`; the theme applies it to the whole
                // tree. Before, it only held for the conversation thread (the three thread screens
                // re-provided it themselves), so the setting seemed to have no effect everywhere
                // else — Settings, Capabilities, sheet included.
                val textScale by AppContainer.settings.chatTextSize.collectAsStateWithLifecycle()
                CompositionLocalProvider(
                    LocalOnyxTextScale provides
                        com.onyx.app.ui.theme.ResolvedTheme.chatTextScale(textScale),
                ) {
                    // The keyboard is handled **once, here**: the app's area shrinks
                    // above the keyboard, and the inset is consumed so that inner screens do not
                    // shift by it a second time. Without that, a low field of a long form
                    // ("Identifiant", i.e. identifier, of the configuration, last field of a settings screen)
                    // stayed hidden behind the keyboard: the scrollable area kept the full screen
                    // height, so Compose had nothing to scroll up.
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .imePadding()
                            .consumeWindowInsets(WindowInsets.ime),
                    ) {
                        // **The keyboard inset was paid HERE, and only once.** The flag tells it
                        // to the whole tree: the thread's reserve (`threadBottomReserve`) must not
                        // count a second time a height the window is already shrunk by —
                        // that double count is what left the thread **empty** at the bottom of the scroll,
                        // keyboard open. Setting the flag in the same place as `imePadding` keeps the
                        // decision in a single spot.
                        CompositionLocalProvider(
                            com.onyx.app.ui.design.LocalOnyxImeTakenByRoot provides true,
                        ) {
                            OnyxApp()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intake(intent)
    }

    /** Reads a share's text, that of `ACTION_ASSIST` or of an App Action, and sets it aside. */
    private fun intake(intent: Intent?) {
        if (intent == null) return
        // **The plates' tint level, forced by the capture pass** (`onyx.verreTeinte` =
        // 0..5, the index in `GlassPlateTintCandidates`). It is the lever that makes it possible to
        // photograph **the entire** scale of tints at rest from a single APK. Absent — the
        // case of every normal launch, hence of what the person sees — the glass keeps the
        // **retained** level.
        intent.getIntExtra(EXTRA_VERRE_TEINTE, -1).let { forced ->
            if (forced in 0..5) com.onyx.app.ui.design.GlassPlateTintLevelForEvidence = forced
        }
        // **The plates' veil, forced by the capture pass** (`onyx.verreVoile`, in hundredths:
        // 0 = the plate applies no tint, the band shows the background **actually** behind it —
        // that is the probe; 100 = the opaque plate, the "no passthrough" reference). Absent — the case
        // of every normal launch — the glass keeps the density of its measurement.
        intent.getIntExtra(EXTRA_VERRE_VOILE, -1).let { forced ->
            if (forced in 0..100) {
                com.onyx.app.ui.design.GlassPlateVeilForEvidence = forced / 100f
            } else if (forced == RESET) {
                com.onyx.app.ui.design.GlassPlateVeilForEvidence = null
            }
        }
        // **The plates' edge displacement, forced by the capture pass**
        // (`onyx.verreRefraction`, in tenths of a dp: 0 = no distortion, 50 = 5 dp). It is the
        // "with / without lens" pair that **quantifies** the edge refraction by cross-correlation.
        intent.getIntExtra(EXTRA_VERRE_REFRACTION, -1).let { forced ->
            if (forced in 0..200) {
                com.onyx.app.ui.design.GlassPlateRefractionForEvidence = forced / 10f
            } else if (forced == RESET) {
                com.onyx.app.ui.design.GlassPlateRefractionForEvidence = null
            }
        }
        when (intent.action) {
            ACTION_VOICE -> ShareInbox.requestVoice(intent.getStringExtra("query"))
            ACTION_CAPTURE -> ShareInbox.requestCapture()
            // The icon's **"Secret vault"** shortcut (French UI string « Coffre de secrets »): the app opens on the vault. The
            // same deferred request as for a note — it is consumed **once**.
            ACTION_VAULT -> ShareInbox.requestVault()
            Intent.ACTION_SEND -> {
                // A share can carry text, an image, a PDF, or both: the text goes to
                // the composer, the file is attached there (it then opens in the app's viewer).
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                // A **bot template link** shared as text is not a message: it opens
                // the template preview, never the composer.
                if (isTemplateLink(text)) {
                    ShareInbox.offerTemplate(text)
                } else {
                    ShareInbox.offer(text)
                }
                sharedStream(intent)?.let { uri ->
                    ShareInbox.offerFile(uri.toString(), intent.type, sharedName(intent, uri))
                }
            }
            // A file opened from another app arrives directly in the composer; a
            // **template link** opened from a browser arrives in the template preview.
            Intent.ACTION_VIEW -> intent.data?.let { uri ->
                if (uri.scheme.equals("onyx", ignoreCase = true) && uri.host.equals("bot", ignoreCase = true)) {
                    ShareInbox.offerTemplate(uri.toString())
                } else {
                    ShareInbox.offerFile(uri.toString(), intent.type, sharedName(intent, uri))
                }
            }
            Intent.ACTION_PROCESS_TEXT ->
                ShareInbox.offer(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty())
            ACTION_ASSIST, ACTION_ASK -> {
                val query = intent.getStringExtra("query")
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                ShareInbox.offer(query.orEmpty())
            }
            // A bot shortcut, picked in the menu that opens on a long press on the icon:
            // the app opens on **that** bot's conversation, never on the list. The request is
            // filed here — the bot's profile name, its label and its named chat travel with
            // the shortcut's intent — and the UI opens it **once**.
            ACTION_BOT -> {
                val bot = intent.getStringExtra(RosterShortcuts.EXTRA_BOT)
                if (!bot.isNullOrBlank()) {
                    BotShortcutInbox.request(
                        BotShortcutRequest(
                            bot = bot,
                            label = intent.getStringExtra(RosterShortcuts.EXTRA_BOT_LABEL)
                                .orEmpty()
                                .ifBlank { bot },
                            chatId = intent.getStringExtra(RosterShortcuts.EXTRA_BOT_CHAT),
                            isDefault = intent.getBooleanExtra(RosterShortcuts.EXTRA_BOT_MAIN, false),
                        ),
                    )
                    // The launcher said a shortcut was used: the platform counts it, as
                    // for any shortcut (`reportShortcutUsed`).
                    RosterShortcuts.reportUsed(this, BotShortcutPlan.id(bot))
                }
            }
            else -> Unit
        }
    }

    /** A text that **is** a bot template link, and nothing else around it. */
    private fun isTemplateLink(text: String): Boolean {
        val clean = text.trim()
        if (clean.length > BotTemplateShareCodec.MAXIMUM_LENGTH) return false
        return clean.startsWith(BotTemplateShareCodec.PREFIX, ignoreCase = true)
    }

    /** A share's file: `ClipData` first, as Android 13 and later require. */
    private fun sharedStream(intent: Intent): Uri? {
        val clip = intent.clipData
        if (clip != null && clip.itemCount > 0) return clip.getItemAt(0).uri
        return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    }

    /** The shared file's name, when the provider gives it; otherwise the activity will resolve it. */
    private fun sharedName(intent: Intent, uri: Uri): String {
        val named = runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
        return named
            ?: intent.clipData?.getItemAt(0)?.uri?.lastPathSegment?.substringAfterLast('/')
            ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    }

    private companion object {
        /** The action of App Actions and shortcuts (`res/xml/shortcuts.xml`). */
        const val ACTION_ASK = "com.onyx.app.action.ASK"

        /** The "Secret vault" shortcut (French UI string « Coffre de secrets »): `res/xml/shortcuts.xml`. */
        const val ACTION_VAULT = "com.onyx.app.action.VAULT"

        /**
         * The plates' tint level, for the capture pass (`onyx.verreTeinte`).
         *
         * It is **not** used by the app in operation: it is the proof lever, just as
         * `GlassTierForcedForEvidence` is in the drawing layer. Without it, the glass keeps the
         * retained level.
         */
        const val EXTRA_VERRE_TEINTE = "onyx.verreTeinte"

        /**
         * **The plates' veil, for the capture pass** (`onyx.verreVoile`, in hundredths).
         *
         * Like the previous one, it is not used by the app in operation: `0` shows the background
         * **actually** behind the plate (the probe that fills `glassPlateBackdrops`), `100` gives
         * the **opaque** plate (the "no passthrough" reference). Absent, the glass keeps the density
         * its measurement gives it.
         */
        const val EXTRA_VERRE_VOILE = "onyx.verreVoile"

        /**
         * **The plates' edge displacement, for the capture pass** (`onyx.verreRefraction`,
         * in tenths of a dp). `0` = no distortion; the shipped value = 5 dp (50). The pair of
         * both is cross-correlated to **quantify** the lens in pixels.
         */
        const val EXTRA_VERRE_REFRACTION = "onyx.verreRefraction"

        /**
         * The value that **removes** a capture-pass forcing: `-2` sets the constant back to
         * `null` (the **shipped** state). The switch then happens **without restarting the app** — `singleTask`
         * calls `onNewIntent`, and both constants are Compose states.
         */
        const val RESET = -2

        /** The "talk to Onyx" shortcut (French UI string « parler à Onyx »). */
        const val ACTION_VOICE = "com.onyx.app.action.VOICE"

        /** The "take a note" shortcut (French UI string « prendre une note ») (quick capture). */
        const val ACTION_CAPTURE = "com.onyx.app.action.CAPTURE"

        /**
         * A bot's shortcut: "open this bot's conversation" (French UI string « ouvre la conversation de ce bot »).
         *
         * Published at runtime (one tile per bot, `RosterShortcuts`) and not declared in
         * `res/xml/shortcuts.xml`, it is the same action for all bots — the targeted bot travels
         * in the extras.
         */
        const val ACTION_BOT = RosterShortcuts.ACTION_BOT

        /** The phone assistant's action. */
        const val ACTION_ASSIST = "android.intent.action.ASSIST"
    }
}
