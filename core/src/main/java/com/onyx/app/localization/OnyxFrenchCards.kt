package com.onyx.app.localization

/**
 * Les mots des cartes d'une réponse (`ReplyCards` — météo, lieux, heure, carte) et des gabarits
 * `String.format` qu'elles emploient.
 *
 * Les gabarits gardent `%1$s` / `%1$d`: `OnyxLocalization.format` traduit la clé puis réinsère
 * les arguments. Les noms de temps sont ceux (`WeatherCard.name`), traduits ici.
 */
internal val onyxCardsFrench: Map<String, String> = linkedMapOf(
    // La carte et ses actions.
    "Open in Maps" to "Ouvrir dans Cartes",
    "Directions" to "Itinéraire",
    "Map unavailable" to "Carte indisponible",
    "From here to %1\$s" to "D'ici à %1\$s",
    "%1\$s to %2\$s" to "De %1\$s à %2\$s",
    "≈ %1\$s straight line" to "≈ %1\$s à vol d'oiseau",
    "Drive" to "Voiture",
    "Walk" to "À pied",
    "Transit" to "Transport",
    "Cycle" to "Vélo",
    "Call %1\$s" to "Appeler %1\$s",
    "Open %1\$s in Maps" to "Ouvrir %1\$s dans Cartes",
    "%1\$s's website" to "Site de %1\$s",

    // La météo.
    "Clear skies" to "Ciel dégagé",
    "Partly cloudy" to "Partiellement nuageux",
    "Cloudy" to "Nuageux",
    "Fog" to "Brouillard",
    "Drizzle" to "Bruine",
    "Rain" to "Pluie",
    "Heavy rain" to "Forte pluie",
    "Thunderstorms" to "Orages",
    "Snow" to "Neige",
    "Sleet" to "Grésil",
    "Hail" to "Grêle",
    "Windy" to "Venteux",
    "Feels like %1\$s" to "Ressenti %1\$s",
    "Wind %1\$s" to "Vent %1\$s",
    "Humidity %1\$d%%" to "Humidité %1\$d %%",
    "Rain %1\$d%%" to "Pluie %1\$d %%",
    "Weather data by Open-Meteo.com" to "Données météo par Open-Meteo.com",
    "Looking up the weather in %1\$s…" to "Recherche de la météo à %1\$s…",

    // L'heure.
    "Your time" to "Votre heure",
    "Looking up the time in %1\$s…" to "Recherche de l'heure à %1\$s…",
    "Same time" to "Même heure",
    "%1\$s hr ahead" to "%1\$s h d'avance",
    "%1\$s hr behind" to "%1\$s h de retard",

    // La durée d'un chemin.
    "%1\$d min" to "%1\$d min",
    "%1\$d hr" to "%1\$d h",
    "%1\$d hr %2\$d min" to "%1\$d h %2\$d min",
)

/**
 * Les phrases que les cartes composent elles-mêmes: la différence d'heure (« 8 hr ahead ») et
 * l'heure seule (« 2 PM ») — le motif est essayé quand le texte n'est pas une clé du dictionnaire.
 */
internal val onyxCardsFrenchPatterns: List<Pair<Regex, (MatchResult) -> String>> = listOf(
    Regex("^(.+) hr ahead$") to { match -> "${match.groupValues[1]} h d’avance" },
    Regex("^(.+) hr behind$") to { match -> "${match.groupValues[1]} h de retard" },
)
