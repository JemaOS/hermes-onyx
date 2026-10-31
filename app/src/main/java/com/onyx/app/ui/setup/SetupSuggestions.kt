package com.onyx.app.ui.setup

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Ce que le mode « Simple » **propose** sous son champ d'adresse.
 *
 * Le propriétaire a écrit « quand je veux me connecter en simple ça propose rien » : le mode
 * « Simple » — donc « Adresse et clé. Onyx trouve le reste. » — n'offrait aucun champ et rien à
 * appuyer. Ce fichier porte la seule partie qui se mesure sans monter un écran : **quelles
 * adresses l'installation connaît déjà**, **laquelle est un secret**, et **quand un collage est
 * une adresse**.
 *
 * Rien n'est inventé : une adresse proposée vient toujours d'une source nommée — le brouillon en
 * cours, le réseau du téléphone, la passerelle qu'on utilisait, ou un serveur déjà enregistré.
 */
object SetupSuggestions {

    /**
     * Les paramètres de requête qui transportent un secret. Un serveur de voix annonce son adresse
     * avec son jeton dedans (`…/voices?token=…`) : **l'adresse devient alors un secret**, et le
     * champ se masque exactement comme celui de la clé — un jeton ne se relit jamais à l'écran.
     */
    private val SECRET_PARAMETERS = setOf(
        "key",
        "token",
        "access_token",
        "api_key",
        "apikey",
        "bridge-token",
        "bridgetoken",
    )

    /** Le port du tableau de bord de la maison — celui de ses propres exemples d'adresse. */
    const val LOCAL_PORT: Int = 9119

    /** Une adresse rognée : jamais d'espace autour, jamais de barre oblique finale. */
    fun normalize(raw: String): String = raw.trim().trimEnd('/')

    /**
     * Vrai pour un texte qui est **déjà** une adresse utilisable : un schéma http(s) et un hôte.
     * Un simple hôte sans schéma n'est pas proposé (l'écran l'accepterait, mais ce n'est pas ce
     * qu'on veut poser d'un appui depuis un presse-papiers quelconque).
     */
    fun isAddress(raw: String): Boolean {
        val text = normalize(raw)
        if (!text.startsWith("http://") && !text.startsWith("https://")) return false
        val host = text
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore(':')
        return host.isNotEmpty()
    }

    /**
     * Ce qu'un appui sur « Coller » écrit dans le champ.
     *
     * Un presse-papiers contient rarement l'adresse seule : on y trouve le lien de configuration
     * entier, une phrase avec une adresse dedans, ou une adresse avec un retour à la ligne. On
     * cherche donc **l'adresse** dans le texte, et on rend le texte rogné quand il est déjà une
     * adresse. Chaîne vide quand il n'y a rien à poser.
     */
    fun addressFromClipboard(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        val start = listOf(text.indexOf("http://"), text.indexOf("https://"))
            .filter { it >= 0 }
            .minOrNull() ?: return ""
        val candidate = text.substring(start)
            .substringBefore(' ')
            .substringBefore('\n')
            .substringBefore('\r')
            .substringBefore('\t')
            .substringBefore('"')
            .substringBefore('<')
            .substringBefore('>')
        val address = normalize(candidate)
        return if (isAddress(address)) address else ""
    }

    /**
     * Vrai quand l'adresse porte elle-même un secret (le jeton du serveur de voix, une clé en
     * paramètre). Le champ d'adresse se masque alors : la règle de la maison ne relit **jamais**
     * un secret, et une adresse qui en porte un devient un secret.
     */
    fun carriesSecret(raw: String): Boolean {
        val query = raw.substringAfter('?', "")
        if (query.isEmpty()) return false
        return query.split('&').any { pair ->
            val name = pair.substringBefore('=').trim().lowercase()
            name in SECRET_PARAMETERS
        }
    }

    /**
     * Les adresses déjà connues de **cette installation**, dans l'ordre de pertinence :
     *
     *  1. le brouillon en cours ([current]) — ce qu'on vient de saisir ;
     *  2. le réseau du téléphone ([local] : `192.168.…`, `10.…`, un tailnet `100.64.…`) ;
     *  3. la passerelle qu'on utilisait ([lastGateway]) ;
     *  4. les autres serveurs enregistrés ([saved]).
     *
     * Dédupliquée sur l'adresse normalisée, et sans les entrées vides : proposer deux fois la même
     * adresse sous deux libellés n'apprendrait rien à personne.
     */
    fun known(
        current: String = "",
        local: List<String> = emptyList(),
        lastGateway: String = "",
        saved: List<String> = emptyList(),
    ): List<AddressSuggestion> {
        val taken = mutableSetOf<String>()
        val out = mutableListOf<AddressSuggestion>()

        fun offer(raw: String, source: SuggestionSource) {
            val address = normalize(raw)
            if (address.isEmpty() || !isAddress(address)) return
            if (!taken.add(address.lowercase())) return
            out += AddressSuggestion(address, source)
        }

        offer(current, SuggestionSource.CURRENT)
        local.forEach { offer(it, SuggestionSource.LOCAL) }
        offer(lastGateway, SuggestionSource.LAST_GATEWAY)
        saved.forEach { offer(it, SuggestionSource.SAVED) }
        return out
    }

    /**
     * Les adresses du **réseau de ce téléphone** : les IPv4 non locales et non lien-local, en
     * `http://<ip>:9119` — le port des exemples de la maison. C'est ce qui fait qu'un « Simple »
     * sur un réseau domestique propose quelque chose d'utile sans rien demander.
     */
    fun localAddresses(): List<String> {
        val found = mutableListOf<String>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            for (nic in interfaces) {
                if (!nic.isUp || nic.isLoopback) continue
                for (address in nic.inetAddresses) {
                    if (address !is Inet4Address) continue
                    if (address.isLoopbackAddress || address.isLinkLocalAddress) continue
                    val host = address.hostAddress ?: continue
                    if (host.isEmpty()) continue
                    found += "http://$host:$LOCAL_PORT"
                }
            }
        }
        return found.distinct()
    }
}

/**
 * Le texte du presse-papiers, ou vide — jamais une exception.
 *
 * La fonction du même nom vit dans le module `core` en `internal` : elle n'est pas visible d'ici.
 * Celle-ci est la même règle, écrite là où l'écran de configuration en a besoin.
 */
internal suspend fun setupClipboardText(
    clipboard: androidx.compose.ui.platform.Clipboard,
): String {
    val clip = runCatching { clipboard.getClipEntry() }.getOrNull()?.clipData ?: return ""
    if (clip.itemCount <= 0) return ""
    return clip.getItemAt(0).text?.toString().orEmpty()
}

/** L'origine d'une adresse proposée — le libellé de sa rangée, jamais un secret. */
enum class SuggestionSource {
    CURRENT,
    LOCAL,
    LAST_GATEWAY,
    SAVED,
}

/** Une adresse offerte sous le champ : l'adresse, et d'où elle vient. */
data class AddressSuggestion(
    val address: String,
    val source: SuggestionSource,
)
