package com.onyx.app.notifications

import android.content.Context
import com.onyx.app.data.repo.SettingsRepository
import com.onyx.app.ui.chat.TurnSession
import com.onyx.app.ui.chat.TurnStep
import com.onyx.app.ui.chat.TurnStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ce qu'il y a à faire d'un tour, décidé **sans Android** — donc vérifiable.
 *
 * Le tri est explicite parce que c'est lui qui porte les deux promesses du suivi : une seule
 * notification d'avancement (la barre d'état n'a qu'une place), et **une seule** notification de
 * fin par tour, quelle que soit la fréquence à laquelle la décision est repassée.
 */
sealed interface TurnNotice {
    /** La barre d'état suit ce tour : étape, durée, progression, bouton Arrêter. */
    data class Keep(
        val conversationId: String,
        val title: String,
        val text: String,
        val percent: Int?,
    ) : TurnNotice

    /** Le tour est fini : un mot sur le résultat, et l'extrait de la réponse. */
    data class Finish(
        val conversationId: String,
        val title: String,
        val reply: String,
        val failed: Boolean,
        val sound: Boolean,
    ) : TurnNotice

    /** Le tour est arrêté ou oublié : sa notification s'efface. */
    data class Drop(val conversationId: String) : TurnNotice

    /** Plus rien à suivre : la notification d'avancement disparaît. */
    data object Idle : TurnNotice
}

/**
 * L'état du suivi : ce qui a déjà été annoncé, et ce qu'il reste à dire.
 *
 * Séparé des appels Android pour deux raisons, toutes deux vérifiables : la décision se teste sur
 * la JVM, et « une seule notification de fin » se prouve en repassant la décision plusieurs fois —
 * c'est exactement ce qui produisait trois notifications quand la boucle d'une seconde republiat
 * chaque passage.
 */
class TurnNoticePlanner(
    private val tracking: () -> WorkTracking,
    private val sound: () -> Boolean,
    private val titleOf: (String) -> String = { NotificationCopy.ONYX },
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Les tours dont la fin a déjà été dite — ou volontairement passée sous silence. */
    private val announcedFinish = mutableSetOf<String>()

    /** Le choix en cours : c'est lui qui décide si le travail se suit pendant le tour. */
    fun trackingNow(): WorkTracking = tracking()

    /** Ce qu'il y a à faire pour ces sessions. */
    fun plan(
        sessions: Map<String, TurnSession>,
        appInForeground: Boolean,
        visibleConversationId: String?,
    ): List<TurnNotice> {
        val choice = tracking()
        val notices = mutableListOf<TurnNotice>()

        val shown = WorkTrackingPolicy.tracked(sessions.values, choice, appInForeground, visibleConversationId)
        notices += if (shown == null) {
            TurnNotice.Idle
        } else {
            TurnNotice.Keep(
                conversationId = shown.conversationId,
                title = titleOf(shown.conversationId),
                text = progressText(shown),
                percent = percentOf(shown),
            )
        }

        sessions.values.forEach { session ->
            if (session.assistantId in announcedFinish) return@forEach
            val finish = WorkTrackingPolicy.finishOf(session.live.value)
            if (finish == null) {
                // Encore en cours, ou arrêté : rien à dire. Un tour arrêté voit sa notification
                // s'effacer — l'utilisateur sait qu'il vient d'arrêter.
                if (session.live.value.phase.step == TurnStep.STOPPED) {
                    notices += TurnNotice.Drop(session.conversationId)
                }
                return@forEach
            }
            announcedFinish += session.assistantId
            // L'utilisateur avait la conversation sous les yeux : il a déjà lu la réponse.
            if (!WorkTrackingPolicy.tracks(choice, appInForeground, visibleConversationId, session.conversationId)) {
                return@forEach
            }
            notices += TurnNotice.Finish(
                conversationId = session.conversationId,
                title = titleOf(session.conversationId),
                reply = NotificationCopy.excerpt(session.message.text),
                failed = finish.failed,
                sound = sound(),
            )
        }
        return notices
    }

    /** La phrase d'avancement : l'outil qui tourne, la lecture, et la durée écoulée. */
    fun progressText(session: TurnSession): String = NotificationCopy.turnPhase(
        prefillPercent = percentOf(session),
        toolName = session.live.value.message.tools.lastOrNull { it.isRunning }?.name,
        elapsedSeconds = elapsedOf(session),
    )

    /** Le remplissage du contexte, quand le tour en a un — c'est la progression connue. */
    fun percentOf(session: TurnSession): Int? =
        session.live.value.phase.prefill?.let { (it.fraction * 100).toInt() }

    /** Secondes écoulées depuis l'envoi du message. */
    fun elapsedOf(session: TurnSession): Long =
        ((clock() - session.startedAt) / 1_000L).coerceAtLeast(0)
}

/**
 * Le suivi d'un tour dans la barre d'état, tenu par le **processus**, pas par l'écran.
 *
 * Le magasin du processus ([TurnStore]) porte déjà ce qu'un tour devient — l'étape connue, la
 * durée, la réponse qui s'écrit — même quand aucun écran ne le regarde. Le suivi s'y branche donc
 * **là**, et non sur un état d'écran : quitter la conversation, changer de route ou détruire
 * l'activité ne l'interrompt pas.
 *
 * L'app dit seulement ce que l'utilisateur a **sous les yeux** ([watching]) : sur la conversation
 * concernée, au premier plan, elle ne notifie rien.
 */
class TurnWorkNotifier(
    private val context: Context,
    private val turns: TurnStore,
    settings: SettingsRepository,
    titleOf: (String) -> String = { NotificationCopy.ONYX },
    clock: () -> Long = System::currentTimeMillis,
) {

    private val planner = TurnNoticePlanner(
        tracking = { settings.workTracking.value },
        sound = { settings.notificationSound.value },
        titleOf = titleOf,
        clock = clock,
    )

    /** L'app est-elle au premier plan ? */
    @Volatile
    var appInForeground: Boolean = true

    /** La conversation ouverte à l'écran, quand il y en a une. */
    @Volatile
    var visibleConversationId: String? = null

    /** La conversation que le service tient à l'écran, pour ne pas le démarrer deux fois. */
    private var servedConversationId: String? = null

    /** Ce que l'écran a sous les yeux : le suivi ne notifie pas ce que l'utilisateur voit. */
    fun watching(conversationId: String?, foreground: Boolean) {
        visibleConversationId = conversationId
        appInForeground = foreground
    }

    /** Branche le suivi sur la portée du processus. À appeler une fois. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            while (currentCoroutineContext().isActive) {
                val sessions = turns.sessions.value
                try {
                    apply(planner.plan(sessions, appInForeground, visibleConversationId))
                } catch (error: Exception) {
                    // Une notification ne doit jamais pouvoir tuer le suivi d'un tour.
                    android.util.Log.w("OnyxTurn", "le suivi du travail a échoué sur ce passage", error)
                    runCatching { TurnService.stop(context) }
                }
                // On égrène tant qu'un tour **tourne**, même s'il n'est pas (encore) celui qu'on
                // montre : la conversation ouverte change (retour, autre onglet, écran éteint), et
                // un tour fini doit s'annoncer dans la seconde.
                if (WorkTrackingPolicy.keepsTicking(sessions.values, planner.trackingNow())) {
                    delay(TICK_MS)
                } else {
                    // Plus rien ne tourne : on attend qu'un tour commence, sans rester bloqué.
                    withTimeoutOrNull(IDLE_WAIT_MS) { turns.sessions.first { it != sessions } }
                }
            }
        }
    }

    /** Les appels Android de la décision. */
    fun apply(notices: List<TurnNotice>) {
        notices.forEach { notice ->
            when (notice) {
                is TurnNotice.Keep -> {
                    if (notice.conversationId == servedConversationId) {
                        TurnService.phase(context, notice.text, notice.percent)
                    } else {
                        val held = TurnService.start(
                            context = context,
                            conversationId = notice.conversationId,
                            title = notice.title,
                            phase = notice.text,
                            percent = notice.percent,
                        )
                        servedConversationId = notice.conversationId
                        if (!held) {
                            // Android a refusé le service de fond : la même notification est
                            // publiée directement, le suivi tient quand même.
                            TurnNotifier.notifyProgress(
                                context = context,
                                conversationId = notice.conversationId,
                                title = notice.title,
                                text = notice.text,
                                percent = notice.percent,
                            )
                        }
                    }
                }

                is TurnNotice.Finish -> TurnNotifier.notifyFinished(
                    context = context,
                    conversationId = notice.conversationId,
                    title = notice.title,
                    reply = notice.reply,
                    failed = notice.failed,
                    sound = notice.sound,
                )

                is TurnNotice.Drop -> TurnNotifier.clear(context, notice.conversationId)

                TurnNotice.Idle -> {
                    TurnService.stop(context)
                    // Le repli a pu publier la notification sans service : on l'efface aussi.
                    TurnNotifier.clearProgress(context)
                    servedConversationId = null
                }
            }
        }
    }

    private companion object {
        /** Le battement du chronomètre affiché. */
        const val TICK_MS = 1_000L

        /** Sans tour en cours, on attend un changement du magasin — avec un filet de sécurité. */
        const val IDLE_WAIT_MS = 10_000L
    }
}
