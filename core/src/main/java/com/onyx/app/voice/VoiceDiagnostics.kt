package com.onyx.app.voice

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Le journal du chemin vocal **de l'appareil** : ce que la page Journaux doit montrer pour qu'une
 * écoute qui ne capte rien se lise sans deviner — le démarrage du moteur, la permission, les trames
 * reçues et l'erreur exacte.
 *
 * Le journal du serveur (Gateway/Agent/Errors) ne dit rien de ce qui se passe entre le micro et le
 * moteur local : c'est précisément là que le défaut rapporté se joue (« rien ne se passe quand je
 * parle »). Ces lignes-là sont donc tenues ici, sur l'appareil, et la page Journaux les montre.
 *
 * Deux règles, tenues par le code et par un test :
 *  - **jamais de contenu sensible** : les mots reconnus, un jeton, une adresse de serveur n'y
 *    entrent pas. On n'écrit que des faits de plomberie — états, compteurs, niveaux, codes. Toute
 *    ligne passe par [redactSensitive] avant d'être gardée, et un test qui refuse les jetons le
 *    prouve.
 *  - **borné** : au plus [MAX_LINES] lignes ; les plus anciennes tombent (un journal qu'on regarde
 *    ne doit pas remplir la mémoire du téléphone).
 */
object VoiceDiagnostics {
    /** Le plafond du journal : au-delà, la plus ancienne ligne tombe. */
    const val MAX_LINES = 200

    private val lock = Any()
    private val buffer = ArrayDeque<String>()
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    /** Ajoute une ligne (horodatée, expurgée). Vide ou blanche : ignorée. */
    fun note(line: String) {
        val clean = line.trim()
        if (clean.isEmpty()) return
        val entry = "${clock.format(Date())}  ${redactSensitive(clean)}"
        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > MAX_LINES) buffer.removeFirst()
        }
    }

    /** Les lignes dans l'ordre d'arrivée, de la plus ancienne à la plus récente. */
    fun lines(): List<String> = synchronized(lock) { buffer.toList() }

    /** Vide le journal (tests, ou le bouton « Effacer » d'une page qui l'exposerait un jour). */
    fun clear() = synchronized(lock) { buffer.clear() }
}

/**
 * Retire d'une ligne ce qui ne doit **jamais** être journalisé : un jeton, une clé, un mot de passe,
 * une autorisation. Le reste de la ligne passe tel quel — une ligne technique reste lisible, mais
 * aucune valeur secrète n'y survit.
 *
 * Le « Bearer … » est traité d'abord : sinon la règle clé/valeur n'attraperait que le mot « Bearer »
 * et laisserait le jeton derrière. Et la clé se reconnaît sans exigence de début de mot
 * (`api_key:` aussi bien que `key:`).
 */
internal fun redactSensitive(line: String): String =
    line
        .replace(Regex("(?i)\\bbearer\\s+\\S+")) { "Bearer ***" }
        .replace(Regex("(?i)(token|key|secret|password|authorization)\\s*[=:]\\s*\\S+")) { match ->
            "${match.groupValues[1]}=***"
        }
