package com.onyx.app.data.model.serve

import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * **Un horodatage de tâche planifiée, tel qu'on le montre — jamais tel que le serveur l'écrit.**
 *
 * Le défaut relevé sur la capture du propriétaire : le détail d'une tâche affichait, en clair, les
 * **nombre de millisecondes** du serveur — `1793292701521` en sous-titre, le même en « Prochaine
 * exécution », `1793289101521` en « Dernière exécution », et `179328282.757681` comme identifiant
 * d'un passage. Un nombre de treize chiffres n'est pas une date : c'est une donnée de transport qui
 * a fui jusqu'à l'écran.
 *
 * Ici, chaque horodatage devient deux choses, et rien d'autre :
 *
 *  - [absolute] — la **date et l'heure** dans la langue du système (le format court de la locale,
 *    donc « 29/10/2026 17:12 » en français) ;
 *  - [relative] — « dans 12 minutes », « il y a 8 minutes », « maintenant », par [relativeLabel],
 *    qui connaît déjà les deux langues de la maison.
 *
 * **L'identifiant technique d'un passage.** Quand la chaîne du serveur n'est pas un instant, elle
 * reste une donnée technique et s'affiche à part, en monospace discret — jamais en titre de rangée
 * ([cronIdentity]). Quand c'est un instant, il n'y a plus rien à montrer : le nombre disparaît.
 */
data class CronStamp(
    /** L'instant, en millisecondes depuis l'époque — la forme sur laquelle on peut calculer. */
    val millis: Long,
    /** La date et l'heure, dans la langue demandée. */
    val absolute: String,
    /** La même chose, dite de près : « dans 12 minutes », « il y a 8 minutes ». */
    val relative: String,
) {
    /** Ce qu'une ligne écrit : le relatif d'abord, l'absolu ensuite — les deux, jamais un nombre. */
    val line: String get() = "$relative · $absolute"
}

/**
 * **Lit un horodatage du serveur, dans les formes qu'il renvoie vraiment**, et rend `null` quand la
 * chaîne n'est pas un instant du tout (l'appelant la traite alors comme un identifiant).
 *
 * Les formes relevées sur la capture du propriétaire :
 *  - des **millisecondes** (`1793292701521`, treize chiffres) — `next_run` et `last_run` bruts ;
 *  - des **secondes** (`1793289101`, dix chiffres) — `next_run_at` / `last_run_at` du Dashboard.
 *
 * **La règle, et pourquoi elle n'est pas « tout ce qui a des chiffres ».** Un nombre n'est retenu
 * comme instant que si, une fois mis à l'échelle, il tombe **entre 1990 et 2200** — la fenêtre où un
 * horaire de tâche a un sens. `179328282.757681`, la valeur que le serveur a mise dans le champ `at`
 * d'un passage de la capture, vaut 1975 en secondes : ce **n'est pas** un instant, et l'app le dit
 * en la gardant comme identifiant technique (affiché en monospace, jamais comme titre) plutôt que
 * d'inventer une date fausse. Une date ISO-8601 est prise telle quelle, avec son fuseau.
 *
 * Rien n'est donc deviné : ce qui n'est pas reconnu rend `null`.
 */
fun cronStampMillis(raw: String?): Long? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    // Une date ISO explicite, d'abord : elle porte son propre fuseau.
    runCatching { return java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli() }
    val parts = Regex("^([0-9]{6,19})(?:\\.([0-9]{1,9}))?$").matchEntire(text) ?: return null
    val whole = parts.groupValues[1]
    val fraction = parts.groupValues[2]
    val value = whole.toLongOrNull() ?: return null
    // Douze chiffres et plus : la valeur est déjà en millisecondes ; en dessous, en secondes.
    val millis = if (whole.length >= 12) value else value * 1000L
    // La fraction de seconde se ramène à des millisecondes en gardant ses trois premiers chiffres.
    val candidate = millis + (fraction + "000").take(3).toLong()
    return candidate.takeIf { it in INSTANT_WINDOW }
}

/**
 * La fenêtre où un horodatage de tâche a un sens : du 1ᵉʳ janvier 1990 au 1ᵉʳ janvier 2200, en
 * millisecondes. Hors de là, c'est un identifiant ou une expression — pas un instant.
 */
private val INSTANT_WINDOW = 631_152_000_000L..7_258_118_400_000L

/**
 * L'horodatage **formaté** d'une tâche ou d'une exécution.
 *
 * @param raw la chaîne du serveur (`at`, `next_run`, `last_run`), quand il n'y a pas de millisecondes.
 * @param millis l'instant déjà lu par l'API (`nextRunAt`, `lastRunAt`), prioritaire sur [raw].
 * @param nowMillis l'instant de référence du relatif — injecté, pour que le test ne dépende pas de
 *   l'horloge de la machine.
 */
fun cronStamp(
    raw: String? = null,
    millis: Long? = null,
    locale: Locale = OnyxLanguage.preferred,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): CronStamp? {
    val at = millis ?: cronStampMillis(raw) ?: return null
    val absolute = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zone)
        .format(Instant.ofEpochMilli(at))
    return CronStamp(at, absolute, relativeLabel(at, nowMillis, locale))
}

/**
 * **L'identifiant technique d'un passage** : la chaîne du serveur quand elle **n'est pas** un
 * instant. Un horodatage n'a pas d'identifiant à montrer — il a une date ; seule une valeur qui
 * n'est pas du temps (un identifiant de passage, par exemple) reste, et s'écrit alors en monospace
 * discret, **jamais** comme titre de rangée.
 */
fun cronIdentity(raw: String?): String? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    return if (cronStampMillis(text) == null) text else null
}

/**
 * **L'horaire d'une tâche, tel que l'écran le raconte.**
 *
 * Le serveur écrit la **syntaxe** d'un horaire (« every 60m ») ; la table de [OnyxLocalization]
 * porte les formes que la maison sait dire (« toutes les 60 min »), et rend les autres inchangées —
 * une syntaxe non reconnue reste donc une valeur, pas un mensonge.
 *
 * Cette fonction sert à l'**affichage** seulement : le champ de l'éditeur, lui, garde la syntaxe
 * que le serveur attend (voir `SCHEDULE_EXAMPLES`).
 */
fun CronJob.scheduleText(locale: Locale = OnyxLanguage.preferred): String =
    if (schedule.isBlank()) "" else OnyxLocalization.translate(schedule, locale)
