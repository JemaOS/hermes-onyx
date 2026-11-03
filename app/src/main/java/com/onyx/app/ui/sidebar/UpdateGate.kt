package com.onyx.app.ui.sidebar

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.onyx.app.BuildConfig
import com.onyx.app.data.net.AppUpdate
import com.onyx.app.data.net.UpdateChecker
import com.onyx.app.data.net.isUpdateAvailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * **La mise à jour de l'app** : l'état de la proposition, et le geste qui la fait disparaître.
 *
 * Le contrôle est **gratuit et local** : l'API publique de GitHub, sans compte ni clé, jointe
 * par le téléphone. Il est **ponctuel** — une fois au lancement, une fois à l'ouverture du
 * volet — et jamais en boucle : l'API publique de GitHub tolère soixante requêtes par heure et
 * par adresse, et une app qui rafraîchit son tiroir en boucle les gaspillerait.
 *
 * **Une proposition écartée ne revient pas.** La croix l'écarte, et le numéro écarté est
 * **mémorisé** : la carte ne réapparaît pas au lancement suivant. Une alerte qu'on ne peut pas
 * écarter n'est plus une alerte, c'est un reproche — et l'app qui la repose à chaque démarrage
 * apprend à l'ignorer.
 */
class UpdateGate(
    context: Context,
    private val checker: UpdateChecker = UpdateChecker(),
    /** La version **installée** : c'est elle que l'Android compare pour accepter l'installation. */
    private val installedCode: Int = BuildConfig.VERSION_CODE,
    /** La version **lisible** installée (`1.3.3`) : c'est elle qu'on compare quand la release ne porte pas son numéro. */
    private val installedName: String = BuildConfig.VERSION_NAME,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _offer = MutableStateFlow<AppUpdate?>(null)

    /** La version proposée, ou `null` quand il n'y a rien à proposer. */
    val offer: StateFlow<AppUpdate?> = _offer.asStateFlow()

    /** Le contrôle ne se fait qu'une fois par session : le volet s'ouvre souvent. */
    private var checked = false

    /**
     * Interroge GitHub et pose la proposition, si elle est justifiée.
     *
     * `null` **n'est jamais** une erreur affichée : pas de réseau, GitHub indisponible,
     * réponse inattendue — dans tous ces cas l'app ne dit rien. Le tiroir reste ce qu'il est.
     */
    suspend fun refresh(force: Boolean = false) {
        if (checked && !force) return
        checked = true
        val latest = checker.latest() ?: return
        _offer.value = when {
            // La version publiée n'est pas plus récente : rien à proposer.
            !isUpdateAvailable(installedCode, installedName, latest) -> null
            // Cette version a déjà été écartée : on ne la repropose pas.
            dismissedName() == latest.versionName -> null
            else -> latest
        }
    }

    /** **Écarte** la proposition, pour de bon : la version est mémorisée. */
    fun dismiss() {
        val current = _offer.value ?: return
        prefs.edit().putString(KEY_DISMISSED, current.versionName).apply()
        _offer.value = null
    }

    /** La version de la proposition écartée, s'il y en a une. */
    private fun dismissedName(): String? =
        prefs.getString(KEY_DISMISSED, null)?.takeIf { it.isNotBlank() }

    /**
     * **Télécharge** l'APK proposé.
     *
     * Le gestionnaire de téléchargement du système s'en charge : le fichier arrive, la
     * notification propose de l'installer, et l'installation **par-dessus** l'ancienne garde la
     * session et les réglages — les trois paquets portent le même certificat.
     */
    fun download(update: AppUpdate) {
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("Onyx ${update.versionName}")
            .setDescription("Onyx ${update.versionName}")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                // Le nom **publié** du paquet : sans numéro, comme sur la page de la release.
                "onyx-${update.versionName}-release.apk",
            )
        runCatching {
            appContext.getSystemService(DownloadManager::class.java).enqueue(request)
        }
    }

    private companion object {
        /** Les préférences de la mise à jour — à part, pour ne pas toucher aux réglages. */
        const val PREFS = "onyx-update"

        /** Le numéro de la version que la personne a écartée. */
        const val KEY_DISMISSED = "dismissed-version"
    }
}
