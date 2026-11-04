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
 * L'unique activité. Elle recueille ce qui vient de l'extérieur — un partage, une question de
 * l'assistant, une App Action — et le remet à [ShareInbox] pour que l'UI l'envoie.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ApprovalNotifier.ensureChannel(this)
        // Le canal des appels : créé au démarrage, comme celui des approbations, pour qu'un appel
        // entrant trouve son canal déjà en place (sonnerie et vibration comprises).
        CallRinger.ensureChannel(this)
        intake(intent)
        setContent {
            // « Réduire les animations » : le drapeau système est posé à la racine, comme
            // `accessibilityReduceMotion` l'est pour toute l'app de référence.
            ProvideReduceMotion {
                // **L'échelle de texte des Réglages s'applique à toute l'app.** Elle est lue ici,
                // à la racine, et fournie par `LocalOnyxTextScale` ; le thème la pose sur tout
                // l'arbre. Elle ne tenait avant qu'au fil de conversation (les trois écrans de fil
                // la reposaient eux-mêmes), si bien que le réglage semblait sans effet partout
                // ailleurs — Réglages, Capacités, volet compris.
                val textScale by AppContainer.settings.chatTextSize.collectAsStateWithLifecycle()
                CompositionLocalProvider(
                    LocalOnyxTextScale provides
                        com.onyx.app.ui.theme.ResolvedTheme.chatTextScale(textScale),
                ) {
                    // Le clavier est traité **une seule fois, ici** : la zone de l'app se réduit
                    // au-dessus du clavier, et l'inset est consommé pour que les écrans internes ne
                    // s'en décalent pas une seconde fois. Sans ça, un champ bas d'un formulaire long
                    // (« Identifiant » de la configuration, dernier champ d'un écran de réglages)
                    // restait caché derrière le clavier : la zone défilante gardait toute la hauteur
                    // de l'écran, donc Compose n'avait rien à faire remonter.
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .imePadding()
                            .consumeWindowInsets(WindowInsets.ime),
                    ) {
                        // **L'encart du clavier a été payé ICI, et une seule fois.** Le drapeau le dit
                        // à tout l'arbre : la réserve du fil (`threadBottomReserve`) ne doit pas
                        // compter une seconde fois une hauteur dont la fenêtre est déjà réduite —
                        // c'est ce double compte qui laissait le fil **vide** au bas du défilement,
                        // clavier ouvert. Poser le drapeau au même endroit que `imePadding` tient la
                        // décision en un seul point.
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

    /** Lit le texte d'un partage, d'`ACTION_ASSIST` ou d'une App Action, et le met de côté. */
    private fun intake(intent: Intent?) {
        if (intent == null) return
        // **Le niveau de teinte des plaques, forcé par la passe de captures** (`onyx.verreTeinte` =
        // 0..5, l'indice dans `GlassPlateTintCandidates`). C'est le levier qui permet de
        // photographier **l'échelle entière** des teintes au repos depuis un seul APK. Absent — le
        // cas de tous les lancements normaux, donc de ce que la personne voit — le verre garde le
        // niveau **retenu**.
        intent.getIntExtra(EXTRA_VERRE_TEINTE, -1).let { forced ->
            if (forced in 0..5) com.onyx.app.ui.design.GlassPlateTintLevelForEvidence = forced
        }
        // **Le voile des plaques, forcé par la passe de captures** (`onyx.verreVoile`, en centièmes :
        // 0 = la plaque ne pose aucune teinte, la bande montre le fond **réellement** derrière elle —
        // c'est la sonde ; 100 = la plaque opaque, la référence « aucune traversée »). Absent — le cas
        // de tous les lancements normaux — le verre garde la densité de sa mesure.
        intent.getIntExtra(EXTRA_VERRE_VOILE, -1).let { forced ->
            if (forced in 0..100) {
                com.onyx.app.ui.design.GlassPlateVeilForEvidence = forced / 100f
            } else if (forced == RESET) {
                com.onyx.app.ui.design.GlassPlateVeilForEvidence = null
            }
        }
        // **Le déplacement d'arête des plaques, forcé par la passe de captures**
        // (`onyx.verreRefraction`, en dixièmes de dp : 0 = aucune déformation, 50 = 5 dp). C'est la
        // paire « avec / sans lentille » qui **chiffre** la réfraction de bord par corrélation croisée.
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
            // Le raccourci **« Coffre de secrets »** de l'icône : l'app s'ouvre sur le coffre. La
            // même demande différée que pour une note — elle est consommée **une fois**.
            ACTION_VAULT -> ShareInbox.requestVault()
            Intent.ACTION_SEND -> {
                // Un partage peut porter du texte, une image, un PDF, ou les deux : le texte va au
                // composeur, le fichier s'y joint (il s'ouvre ensuite dans le lecteur de l'app).
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                // Un **lien de modèle de bot** partagé comme texte n'est pas un message : il ouvre
                // l'aperçu du modèle, jamais le composeur.
                if (isTemplateLink(text)) {
                    ShareInbox.offerTemplate(text)
                } else {
                    ShareInbox.offer(text)
                }
                sharedStream(intent)?.let { uri ->
                    ShareInbox.offerFile(uri.toString(), intent.type, sharedName(intent, uri))
                }
            }
            // Un fichier ouvert depuis une autre app arrive directement dans le composer ; un
            // **lien de modèle** ouvert depuis un navigateur arrive dans l'aperçu du modèle.
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
            // Un raccourci de bot, pris dans le menu qui s'ouvre sur un appui long sur l'icône :
            // l'app s'ouvre sur la conversation de **ce** bot, jamais sur la liste. La demande est
            // déposée ici — le nom de profil du bot, son libellé et son chat nommé voyagent avec
            // l'intention du raccourci — et l'UI l'ouvre **une fois**.
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
                    // Le lanceur a dit qu'un raccourci a été pris : la plateforme le compte, comme
                    // pour n'importe quel raccourci (`reportShortcutUsed`).
                    RosterShortcuts.reportUsed(this, BotShortcutPlan.id(bot))
                }
            }
            else -> Unit
        }
    }

    /** Un texte qui **est** un lien de modèle de bot, et rien d'autre autour. */
    private fun isTemplateLink(text: String): Boolean {
        val clean = text.trim()
        if (clean.length > BotTemplateShareCodec.MAXIMUM_LENGTH) return false
        return clean.startsWith(BotTemplateShareCodec.PREFIX, ignoreCase = true)
    }

    /** Le fichier d'un partage : `ClipData` d'abord, comme le veut Android 13 et au-delà. */
    private fun sharedStream(intent: Intent): Uri? {
        val clip = intent.clipData
        if (clip != null && clip.itemCount > 0) return clip.getItemAt(0).uri
        return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    }

    /** Le nom du fichier partagé, quand le fournisseur le dit ; sinon l'activité le résoudra. */
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
        /** L'action des App Actions et des raccourcis (`res/xml/shortcuts.xml`). */
        const val ACTION_ASK = "com.onyx.app.action.ASK"

        /** Le raccourci « Coffre de secrets » : `res/xml/shortcuts.xml`. */
        const val ACTION_VAULT = "com.onyx.app.action.VAULT"

        /**
         * Le niveau de teinte des plaques, pour la passe de captures (`onyx.verreTeinte`).
         *
         * Il ne sert **pas** à l'app en fonctionnement : c'est le levier de la preuve, au même titre
         * que `GlassTierForcedForEvidence` dans la couche de dessin. Sans lui, le verre garde le
         * niveau retenu.
         */
        const val EXTRA_VERRE_TEINTE = "onyx.verreTeinte"

        /**
         * **Le voile des plaques, pour la passe de captures** (`onyx.verreVoile`, en centièmes).
         *
         * Comme le précédent, il ne sert pas à l'app en fonctionnement : `0` montre le fond
         * **réellement** derrière la plaque (la sonde qui remplit `glassPlateBackdrops`), `100` donne
         * la plaque **opaque** (la référence « aucune traversée »). Absent, le verre garde la densité
         * que sa mesure lui donne.
         */
        const val EXTRA_VERRE_VOILE = "onyx.verreVoile"

        /**
         * **Le déplacement d'arête des plaques, pour la passe de captures** (`onyx.verreRefraction`,
         * en dixièmes de dp). `0` = aucune déformation ; la valeur livrée = 5 dp (50). La paire des
         * deux est corrélée pour **chiffrer** la lentille en pixels.
         */
        const val EXTRA_VERRE_REFRACTION = "onyx.verreRefraction"

        /**
         * La valeur qui **retire** un forçage de la passe de captures : `-2` remet la constante à
         * `null` (l'état **livré**). La bascule se fait alors **sans relancer l'app** — `singleTask`
         * appelle `onNewIntent`, et les deux constantes sont des états Compose.
         */
        const val RESET = -2

        /** Le raccourci « parler à Onyx ». */
        const val ACTION_VOICE = "com.onyx.app.action.VOICE"

        /** Le raccourci « prendre une note » (capture rapide). */
        const val ACTION_CAPTURE = "com.onyx.app.action.CAPTURE"

        /**
         * Le raccourci d'un bot : « ouvre la conversation de ce bot ».
         *
         * Publié à l'exécution (une tuile par bot, `RosterShortcuts`) et non déclaré dans
         * `res/xml/shortcuts.xml`, c'est la même action pour tous les bots — le bot visé voyage
         * dans les extras.
         */
        const val ACTION_BOT = RosterShortcuts.ACTION_BOT

        /** L'action de l'assistant du téléphone. */
        const val ACTION_ASSIST = "android.intent.action.ASSIST"
    }
}
