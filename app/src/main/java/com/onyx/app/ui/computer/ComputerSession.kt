package com.onyx.app.ui.computer

import com.onyx.app.data.local.ComputerBridgeStore
import com.onyx.app.data.net.ComputerInput
import com.onyx.app.data.net.ComputerBridgeClient
import com.onyx.app.data.net.ComputerPoint
import com.onyx.app.data.net.ComputerSensitivity
import com.onyx.app.data.net.ComputerStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ce qui attend un « oui » de l'utilisateur avant de partir sur le PC. */
data class ComputerApproval(val action: ComputerInput, val detail: String)

/**
 * La session « Ordinateur » du processus — celle qui survit à la navigation.
 *
 * L'écran va et vient ; la session, non. Elle tient la connexion au pont, la dernière image et la
 * demande d'approbation en cours, si bien que revenir sur l'écran retrouve exactement ce qu'on
 * avait laissé : la même image, la même main, la même question posée.
 *
 * Toutes les entrées passent par ici, sans exception : c'est le seul endroit qui décide si une
 * action part tout de suite ou si elle doit d'abord être approuvée. Un écran ne peut pas envoyer
 * une commande « en douce » — il n'a pas de prise sur le client autrement.
 */
class ComputerSession(private val store: ComputerBridgeStore) {

    /**
     * La connexion, **une seule fois pour tout le processus**.
     *
     * Elle est réutilisée quand l'adresse ou le jeton changent (voir [configure]) : remplacer
     * l'objet laisserait l'écran branché sur les flux de l'ancien, et plus rien ne bougerait à
     * l'affichage — le refus du pont, notamment, ne serait jamais montré.
     */
    private val client: ComputerBridgeClient =
        ComputerBridgeClient(address = store.address, token = store.token)

    /** La connexion vivante. */
    val live: ComputerBridgeClient get() = client

    val phase: StateFlow<ComputerBridgeClient.Phase> get() = client.phase
    val hello get() = client.hello
    val state get() = client.state
    val frame get() = client.frame

    /** Le flux d'images, pour l'aperçu — lu **dans l'aperçu**, jamais au niveau de l'écran. */
    val frames get() = client.frame
    val notice get() = client.notice
    val runOutput get() = client.runOutput

    /** Les mesures du flux, et le cran d'échelle en cours — pour le panneau « Détails du flux ». */
    val stats get() = client.stats
    val ladder get() = client.ladder

    /** Vrai pendant une reprise automatique : l'écran grise l'image et le dit. */
    val reconnecting: Boolean get() = client.phase.value == ComputerBridgeClient.Phase.RECONNECTING

    /**
     * Le décodeur dit combien d'images il a réellement affichées.
     *
     * C'est ce qui donne « images jetées » sans seconde mesure : les images **reçues** sont
     * comptées par le client, les images **décodées** par l'aperçu, et l'écart est exactement ce
     * qui est arrivé trop tard.
     */
    fun noteDecoded(total: Int) = client.noteDecoded(total)

    /** Force la « liaison économe » (le cran le plus léger), ou rend la main à l'échelle. */
    fun setEconomical(economical: Boolean) = client.setEconomical(economical)

    /** L'adresse enregistrée, et de quoi montrer qu'un jeton existe sans le montrer. */
    val address: String get() = store.address
    val maskedToken: String get() = store.maskedToken
    val configured: Boolean get() = store.configured

    /** Vrai quand le jeton n'a pas pu être gardé par le trousseau : il ne vaut qu'ici. */
    val keystoreRefused: Boolean get() = store.keystoreRefused

    /**
     * La taille d'image voulue : la largeur du volet de l'aperçu, en pixels réels.
     *
     * Le pont borne (il connaît son écran) et l'app ne redemande pas deux fois la même chose.
     */
    fun requestStream(widthPx: Int) = client.requestStream(widthPx)

    /**
     * L'aperçu bouge : la cadence monte, puis retombe seule.
     *
     * C'est la seule fenêtre où l'on paie plus de réseau — le reste du temps, on lit une image.
     */
    fun setMoving(moving: Boolean) = client.setMoving(moving)

    private val _approval = MutableStateFlow<ComputerApproval?>(null)

    /** La question en attente, montrée par l'écran dès qu'il est visible. */
    val approval: StateFlow<ComputerApproval?> = _approval.asStateFlow()

    private val _refused = MutableStateFlow<String?>(null)

    /** La dernière action refusée par l'utilisateur — la preuve qu'elle n'est pas partie. */
    val refused: StateFlow<String?> = _refused.asStateFlow()

    private val _started = MutableStateFlow(false)

    /** Vrai une fois qu'une session a été ouverte, même si la connexion est en panne. */
    val started: StateFlow<Boolean> = _started.asStateFlow()

    private val _blocked = MutableStateFlow<String?>(null)

    /** La dernière commande arrêtée avant même la question (elle n'aurait pas dû être proposée). */
    val blocked: StateFlow<String?> = _blocked.asStateFlow()

    /**
     * Enregistre l'adresse et le jeton ; un changement de l'un **ou** de l'autre ferme la session
     * en cours.
     *
     * Corriger le jeton ne servait à rien tant que l'adresse, elle, ne bougeait pas : le client
     * gardait l'ancien et la session restait refusée, sans que rien ne le dise. Or c'est
     * exactement ce qu'on fait après un « jeton refusé » — on recolle le bon. Les deux champs
     * comptent donc pareil.
     */
    fun configure(address: String, token: String) {
        val before = store.address to store.token
        store.address = address
        if (token.isNotEmpty()) store.token = token
        val after = store.address to store.token
        if (before != after) {
            client.reconfigure(after.first, after.second)
            _started.value = false
        }
    }

    fun open() {
        if (!configured) return
        _started.value = true
        client.connect()
    }

    /**
     * Le geste « Réessayer » après une panne qui demandait une action (jeton recopié, adresse
     * corrigée) : on relance tout de suite, sans attendre la temporisation.
     */
    fun retry() {
        if (!configured) return
        _started.value = true
        client.retry()
    }

    /** Ferme pour de bon : la main repart à l'agent. */
    fun stop() {
        client.close()
        _started.value = false
        _approval.value = null
    }

    // ---- la main ----------------------------------------------------------------

    fun take() = client.take()

    fun release() = client.release()

    // ---- les entrées -------------------------------------------------------------

    /**
     * Un appui (ou un glisser) : direct, sans approbation.
     *
     * La main est la permission — le pont refuse toute entrée de qui ne la détient pas, donc rien
     * ne peut cliquer sur un PC que l'utilisateur n'a pas pris.
     */
    fun tap(point: ComputerPoint, button: Int = 1) = client.tap(point.x, point.y, button)

    /** Le pointeur se déplace — sans cliquer : c'est le glissement relatif du pilotage. */
    fun move(point: ComputerPoint) = client.motion(point.x, point.y)

    fun drag(from: ComputerPoint, to: ComputerPoint) = client.drag(from.x, from.y, to.x, to.y)

    fun scroll(point: ComputerPoint, amount: Int) = client.scroll(point.x, point.y, amount)

    fun typeText(text: String) = client.typeText(text)

    /**
     * Un raccourci clavier : il part, sauf s'il touche à la session — auquel cas il attend un
     * « oui », exactement comme une commande. Rend `true` quand il est parti.
     */
    fun key(keys: String): Boolean {
        if (ComputerSensitivity.requiresApproval(ComputerInput.KEY, keys)) {
            _approval.value = ComputerApproval(ComputerInput.KEY, keys)
            return false
        }
        client.key(keys, approved = false)
        return true
    }

    /**
     * Une commande : elle ne part jamais seule. Rend `true` quand elle est partie — donc jamais,
     * la première fois.
     *
     * Une commande que l'app refuse même approuvée (effacement du disque, arrêt de la machine)
     * est arrêtée **avant** la question : demander « approuvez-vous ? » pour quelque chose qu'on
     * ne laissera pas passer serait une question malhonnête.
     */
    fun run(command: String): Boolean {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return false
        if (ComputerSensitivity.blocked(trimmed)) {
            _blocked.value = trimmed
            return false
        }
        _approval.value = ComputerApproval(ComputerInput.RUN, trimmed)
        return false
    }

    /** L'utilisateur a dit oui : l'action part, marquée approuvée. */
    fun approve() {
        val pending = _approval.value ?: return
        _approval.value = null
        when (pending.action) {
            ComputerInput.RUN -> client.run(pending.detail, approved = true)
            ComputerInput.KEY -> client.key(pending.detail, approved = true)
            else -> Unit
        }
    }

    /** L'utilisateur a dit non : rien ne part, et c'est ce qui reste affiché. */
    fun refuse() {
        val pending = _approval.value ?: return
        _approval.value = null
        _refused.value = pending.detail
    }

    fun clearRefusal() {
        _refused.value = null
    }

    /**
     * Le jeton du pont, pour le bouton « copier » — c'est le seul chemin par lequel il ressort, et
     * il ne va que dans le presse-papiers. Il n'est jamais montré à l'écran, même là.
     */
    fun tokenForCopy(): String = store.token

    fun clearBlocked() {
        _blocked.value = null
    }
}
