package com.onyx.app.ui.design

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport

/**
 * **Le profil en vigueur** — l'UNIQUE source de vérité du « profil qui va servir ».
 *
 * Le défaut fermé ici, et que le propriétaire a vu : le bot **coder** était annoncé sur le profil
 * distant `coder`, mais ses conversations tournaient dans le profil `default`, sur le modèle
 * **local** — l'en-tête nommait un modèle, la session en portait un autre, et la passerelle
 * enregistrait `profile_name = default`.
 *
 * La cause : l'app n'écrivait **nulle part** le profil du bot dans la configuration employée pour le
 * tour. La configuration du serveur actif partait telle quelle, et son `hermesProfile` (le réglage
 * global, souvent vide) décidait seul — donc la passerelle retombait sur `default`. Le profil du bot
 * n'existait que dans le **roster**, jamais dans la requête.
 *
 * Ici il n'y a plus qu'**une** valeur, rendue par [inForce], et **un seul** profil : l'affichage
 * écrit le profil en vigueur tel quel, et l'envoi porte **exactement** ce profil ([appliedTo]). Une
 * conversation simple garde le profil du serveur ; la conversation d'un bot prend **le profil de ce
 * bot**, et le profil principal (`default`, `isDefault`) reste le profil par défaut de la
 * passerelle — jamais un `/p/default/` explicite, exactement comme la référence traite `isDefault`.
 *
 * La règle, du plus précis au plus général :
 *
 * 1. **le bot** — le profil du bot dont c'est la conversation ([com.onyx.app.ui.bots.botOfConversation]) ;
 *    `null` pour une conversation simple. Un bot `isDefault` (le profil principal) vaut `default` ;
 * 2. **le serveur** — le profil configuré du serveur actif
 *    ([com.onyx.app.data.model.ServerConfig.effectiveProfile]) ;
 * 3. **le défaut** — `default`.
 *
 * `BotProfileBindingVerrouTest` échoue si le profil **affiché** ([inForce]) diffère du profil
 * **appliqué** ([appliedTo]) — les deux lisent la même décision, donc ils ne peuvent pas diverger.
 */
object ProfileInForce {

    /** Le profil principal de la passerelle : un bot `isDefault`, ou pas de profil. */
    const val Default = "default"

    /**
     * Le profil d'un bot, tel qu'il faut l'**appliquer** à la configuration : `""` pour le profil
     * principal (le réglage global et la passerelle retombent alors sur `default`).
     */
    fun applied(botProfile: String?): String {
        val clean = botProfile?.trim().orEmpty()
        return if (clean.isEmpty() || clean.equals(Default, ignoreCase = true)) "" else clean
    }

    /**
     * Le **profil en vigueur** — le nom que l'app doit montrer, et sous lequel la session tourne.
     *
     * [botProfile] : le profil du bot de la conversation, ou `null` pour une conversation simple.
     * Un bot `isDefault` (ou nommé `default`) vaut [Default].
     */
    fun inForce(botProfile: String?, serverProfile: String?): String {
        if (botProfile != null) {
            val clean = botProfile.trim()
            return if (clean.isEmpty() || clean.equals(Default, ignoreCase = true)) Default else clean
        }
        val server = serverProfile?.trim().orEmpty()
        return if (server.isEmpty() || server.equals(Default, ignoreCase = true)) Default else server
    }

    /**
     * La configuration du serveur telle que le tour doit l'employer.
     *
     * [botProfile] `null` (conversation simple) rend la configuration **telle quelle** : le profil du
     * serveur continue de décider. Sinon le profil du bot est **posé** dans `hermesProfile` **et le
     * transport devient celui qui SERT un profil nommé** — le RPC du tableau de bord
     * ([com.onyx.app.data.model.Transport.HERMES_SERVE], `profile` sur `session.create` /
     * `session.resume`) : c'est le seul canal qui porte un profil à travers le compagnon « une clé »
     * (le serveur `/v1` n'a aucun sélecteur de profil, et son préfixe `/p/<profil>` n'est ni relayé
     * par le compagnon ni servi sans la clé propre au profil — d'où le `405`).
     *
     * **Changer le transport, pas le serveur** : c'est la voie qui sert le profil sans rien casser.
     * Le modèle et le fournisseur du tour restent ceux du profil (`effectiveProfile`).
     */
    fun appliedTo(config: ServerConfig, botProfile: String?): ServerConfig {
        if (botProfile == null) return config
        val profile = applied(botProfile)
        if (profile.isEmpty()) return config.copy(hermesProfile = "")
        return config.copy(hermesProfile = profile, transport = Transport.HERMES_SERVE)
    }
}
