package com.onyx.app.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * La planification du surveillant de réponses : un seul travail périodique, ou aucun.
 *
 * Android veut bien réveiller l'app toutes les quinze minutes **au plus tôt** (c'est la plus
 * petite période que WorkManager accepte pour un travail périodique), et seulement quand le
 * téléphone a du réseau : le travail ne tourne donc jamais pour rien, et jamais hors ligne.
 *
 * **Idempotent dans les deux sens.** Planifier alors que le travail existe déjà ne le remet pas à
 * zéro ([ExistingPeriodicWorkPolicy.KEEP]) : appeler [apply] à chaque démarrage de l'app ne
 * décale donc pas la prochaine échéance, et ne fait pas repartir le compteur. Annuler un travail
 * absent ne fait rien. C'est ce qui permet de brancher la planification aussi bien sur le
 * démarrage que sur l'interrupteur du réglage, sans se demander qui a déjà fait quoi.
 */
object ReplyWatchScheduler {

    /** Le nom unique du travail : un seul surveillant par téléphone, quel que soit l'appelant. */
    const val UNIQUE_WORK = "onyx.reply.watch"

    /** L'étiquette, pour retrouver (ou annuler) le travail depuis WorkManager. */
    const val TAG = "onyx.reply.watch"

    /** La période demandée : la plus courte que WorkManager accepte pour un travail périodique. */
    const val PERIOD_MINUTES = 15L

    /** Planifie le surveillant s'il ne l'est pas déjà. */
    fun schedule(context: Context) {
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request(),
            )
        }
    }

    /** Retire le surveillant : plus aucune lecture du journal en arrière-plan. */
    fun cancel(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK) }
    }

    /** Le réglage fait foi : actif → planifié, éteint → annulé. */
    fun apply(context: Context, enabled: Boolean) {
        if (enabled) schedule(context) else cancel(context)
    }

    /** La demande elle-même : quinze minutes, réseau présent. */
    fun request(): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<ReplyWatcher>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(TAG)
            .build()
}
