package com.onyx.app.data.net

/**
 * **Ce qui ne doit JAMAIS atteindre le texte visible.**
 *
 * Un flux peut livrer, entre deux trames utiles, le **fragment brut d'un appel d'outil** : le JSON
 * des arguments, un chemin interne du serveur (le dossier de travail de l'agent), ou une commande.
 * Ces morceaux ne sont pas une réponse et ne se montrent pas : c'est un **fragment machine**.
 *
 * Le propriétaire l'a vu passer deux fois (dans le fil et dans le raisonnement) : les deux surfaces
 * lisent le même [com.onyx.app.data.model.ChatMessage]. La garde vit donc **au seul endroit où le
 * flux devient du texte** — ici, sur toute pièce dérivée d'un fragment.
 */
object StreamLeak {

    /**
     * Les marques d'un **chemin interne** du serveur : jamais dans le texte de l'interface. Ce sont
     * celles de la capture du propriétaire (`/home/john/.hermes/cache/documents/native-inputs/…`).
     */
    val INTERNAL_MARKERS: List<String> = listOf("/home/", ".hermes", "native-inputs", "/tmp/", "file://")

    /** Les clés d'un **objet JSON de trame** : un appel d'outil, une commande, une approbation. */
    private val FRAME_KEYS: List<String> =
        listOf("\"command\"", "execution_generation", "\"arguments\"", "\"function\"", "\"tool_calls\"")

    /**
     * La pièce ressemble-t-elle à une **fuite** — un chemin interne, ou un objet JSON de trame — donc
     * à quelque chose qui ne doit pas se lire comme une réponse ?
     */
    fun looksLikeLeak(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (INTERNAL_MARKERS.any { trimmed.contains(it) }) return true
        val looksLikeJsonObject = trimmed.startsWith("{") || trimmed.startsWith("[")
        return looksLikeJsonObject && FRAME_KEYS.any { trimmed.contains(it) }
    }

    /**
     * Le détail montrable d'une pièce : tel quel quand il est lisible, **vide** quand il vient d'un
     * fragment machine (l'appelant pose alors sa propre phrase). On ne fabrique jamais une phrase
     * d'erreur à partir du JSON brut.
     */
    fun redact(text: String): String = if (looksLikeLeak(text)) "" else text

    /** Un « mot » du libellé : une suite sans espace ni guillemet — un chemin y tient d'un bloc. */
    private val TOKEN = Regex("[^\\s\"'`]+")

    /**
     * Le **libellé d'un appel d'outil**, montré tel quel sur sa rangée — mais **débarrassé des
     * chemins internes**. Le gateway y glisse le dossier de travail de l'agent
     * (`f="/home/john/.hermes/cache/documents/native-inputs/…/api_….png" + 3 commands`) : cela n'a
     * rien à faire sous les yeux de la personne. On garde le **nom du fichier**, le libellé reste
     * lisible (`f="…/api_….png" + 3 commands`), et jamais le chemin.
     */
    fun preview(label: String?): String? {
        if (label.isNullOrEmpty()) return label
        if (INTERNAL_MARKERS.none { label.contains(it) }) return label
        return TOKEN.replace(label) { match ->
            val token = match.value
            if (INTERNAL_MARKERS.none { token.contains(it) }) {
                token
            } else {
                val name = token.trimEnd('/').substringAfterLast('/')
                if (name.isEmpty()) "…" else "…/$name"
            }
        }
    }
}
