package com.onyx.app.ui.setup

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import com.onyx.app.data.net.ConnectionProbe

/**
 * Les sorties de l'écran « Set up Onyx », en fonctions pures.
 *
 * L'écran ne se ferme plus jamais « pour rien » : « Skip » sort toujours et « Done » dit en une
 * phrase pourquoi il ne peut pas conclure. Ces règles sont ici, hors de la composition, pour être
 * mesurées par un test.
 */

/** L'adresse que le transport retenu exige. Chaque transport a la sienne. */
internal fun transportAddress(config: ServerConfig): String = when (config.transport) {
    Transport.HERMES_SERVE -> config.serveBaseUrl
    else -> config.baseUrl
}

/**
 * Pourquoi « Done » ne peut pas conclure, en une phrase — `null` quand il peut.
 *
 * Les trois cas nommés : l'adresse manque, un test est en cours, le dernier test a échoué. Le
 * message du test est celui du serveur (ou de la couche réseau) : il n'est jamais avalé.
 */
internal fun doneRefusal(config: ServerConfig, testing: Boolean, probe: ConnectionProbe?): String? = when {
    transportAddress(config).isBlank() -> "Add the server's address first."
    testing -> "A connection test is running — one moment."
    probe != null && !probe.ok -> "The test failed: ${probe.message}"
    else -> null
}

/**
 * « Skip » ne sauvegarde le brouillon que s'il porte une adresse.
 *
 * Un brouillon vide enregistré écraserait le serveur actif : l'utilisateur perdrait la
 * configuration qu'il avait, et l'écran se réafficherait au lancement suivant. Une adresse saisie,
 * elle, se garde (c'est une configuration voulue, même sans test concluant).
 */
internal fun skipSavesDraft(draft: ServerConfig): Boolean = draft.isUsable

/**
 * « Skip » : le serveur à réactiver quand l'actif n'a pas d'adresse.
 *
 * Un serveur déjà enregistré et utilisable est « la configuration existante » : la sortie y
 * revient au lieu de laisser l'app sans adresse. `null` quand il n'y a rien à changer.
 */
internal fun serverAfterSkip(active: ServerConfig, saved: List<ServerConfig>): ServerConfig? {
    if (active.isUsable) return null
    return saved.firstOrNull { it.id != active.id && it.isUsable }
}
