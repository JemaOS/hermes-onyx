package com.onyx.app.data.model

import com.onyx.app.localization.OnyxLanguage
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Les cartes qu'une réponse montre à côté de ses mots (`onyx_show`) : une carte, une liste de
 * lieux, la météo, l'heure.
 *
 * Miroir de `ReplyCard` (Echo/Models/ReplyCards:14). L'agent demande ces cartes avec l'outil
 * `onyx_show` du plugin Hermes, dont les arguments sont `{"cards": [...]}` ; l'app les lit dans
 * l'appel lui-même — en direct et quand une conversation est relue. Le format est le nôtre :
 * [ReplyCards.parse] est tolérant (un nombre en texte, un champ facultatif absent, une clé
 * inconnue) et écarte une carte ou un lieu inutilisable plutôt que l'appel entier.
 *
 * Une carte météo ou d'heure ne nomme que son lieu : le plugin (côté Hermes) cherche la prévision
 * et le fuseau et répond avec les cartes remplies, que l'app lit dans sa réponse quand elle y est ;
 * sinon la carte n'a que son lieu et n'est pas dessinée tant qu'elle n'a pas de relevé.
 */
@Serializable
sealed interface ReplyCard {

    /**
     * L'heure quelque part, dessinée en direct par le téléphone : celle d'un lieu, ou sans lieu
     * celle du téléphone lui-même.
     */
    @Serializable
    @SerialName("time")
    data class Clock(
        val place: String? = null,
        /** Un fuseau IANA (« Europe/Lisbon ») : donné, ou déduit du lieu. */
        val timeZone: String? = null,
    ) : ReplyCard {
        companion object {
            /** « Same time », « 4 hr behind », « 5½ hr ahead » : par rapport à ici. */
            fun difference(seconds: Int): String {
                if (seconds == 0) return "Same time"
                val hours = kotlin.math.abs(seconds).toDouble() / 3600.0
                val whole = hours.toInt()
                val part = hours - whole
                val fraction = mapOf(0.25 to "¼", 0.5 to "½", 0.75 to "¾")[part]
                val amount = if (part == 0.0) {
                    whole.toString()
                } else if (fraction != null) {
                    (if (whole == 0) "" else whole.toString()) + fraction
                } else {
                    String.format(Locale.US, "%.2g", hours)
                }
                return if (seconds > 0) "$amount hr ahead" else "$amount hr behind"
            }
        }
    }

    /** Des lieux sur une carte, et un chemin entre deux d'entre eux. */
    @Serializable
    @SerialName("map")
    data class Map(
        val title: String? = null,
        val places: List<Place> = emptyList(),
        val route: Route? = null,
    ) : ReplyCard

    /** Une liste de lieux : ce qui est proche, ce qui a été trouvé. */
    @Serializable
    @SerialName("places")
    data class Places(
        val title: String? = null,
        val places: List<Place> = emptyList(),
    ) : ReplyCard

    @Serializable
    @SerialName("weather")
    data class Weather(
        val place: String,
        /** « C » ou « F » : l'unité des nombres. */
        val units: String = "C",
        val now: Now? = null,
        val hours: List<Hour> = emptyList(),
        val days: List<Day> = emptyList(),
        val source: String? = null,
    ) : ReplyCard {
        /** Y a-t-il quelque chose à dessiner : une carte avec son seul lieu attend sa prévision. */
        val hasReading: Boolean get() = now != null || hours.isNotEmpty() || days.isNotEmpty()

        @Serializable
        data class Now(
            val temperature: Double,
            val condition: Condition,
            val feelsLike: Double? = null,
            val high: Double? = null,
            val low: Double? = null,
            val wind: String? = null,
            val humidity: Int? = null,
            val precipitation: Int? = null,
            val summary: String? = null,
            /** Après la nuit là-bas : un ciel clair se dessine avec la lune. */
            val night: Boolean? = null,
        )

        @Serializable
        data class Hour(
            val time: String,
            val temperature: Double,
            val condition: Condition,
            val precipitation: Int? = null,
            val night: Boolean? = null,
        )

        @Serializable
        data class Day(
            val date: String,
            val high: Double,
            val low: Double,
            val condition: Condition,
            val precipitation: Int? = null,
        )
    }

    /** Un lieu tel que l'agent le connaît. Sans coordonnées, la carte n'a que son nom. */
    @Serializable
    data class Place(
        val name: String,
        val address: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val category: String? = null,
        /** Sur cinq. */
        val rating: Double? = null,
        val note: String? = null,
        val phone: String? = null,
        val url: String? = null,
    ) {
        /** Les coordonnées sont-elles sur le globe ? Sinon elles valent `null`. */
        val hasCoordinate: Boolean
            get() {
                val latitude = latitude ?: return false
                val longitude = longitude ?: return false
                return latitude in -90.0..90.0 && longitude in -180.0..180.0
            }
    }

    @Serializable
    data class Route(
        /** `null` : depuis là où la personne est. */
        val from: Place? = null,
        val to: Place,
        val mode: Mode = Mode.DRIVING,
    ) {
        @Serializable
        enum class Mode {
            @SerialName("driving")
            DRIVING,

            @SerialName("walking")
            WALKING,

            @SerialName("transit")
            TRANSIT,

            @SerialName("cycling")
            CYCLING,
            ;

            companion object {
                /** Lu sans tenir compte de la casse ; une valeur inconnue vaut « driving ». */
                fun fromLenient(text: String?): Mode =
                    entries.firstOrNull { it.name.equals(text?.trim(), ignoreCase = true) } ?: DRIVING
            }
        }
    }

    /** Le temps qu'il fait, lu sans exiger l'orthographe exacte du serveur. */
    @Serializable
    enum class Condition {
        @SerialName("clear")
        CLEAR,

        @SerialName("partly_cloudy")
        PARTLY_CLOUDY,

        @SerialName("cloudy")
        CLOUDY,

        @SerialName("fog")
        FOG,

        @SerialName("drizzle")
        DRIZZLE,

        @SerialName("rain")
        RAIN,

        @SerialName("heavy_rain")
        HEAVY_RAIN,

        @SerialName("thunderstorm")
        THUNDERSTORM,

        @SerialName("snow")
        SNOW,

        @SerialName("sleet")
        SLEET,

        @SerialName("hail")
        HAIL,

        @SerialName("wind")
        WIND,

        @SerialName("unknown")
        UNKNOWN,
        ;

        companion object {
            /**
             * Lu avec indulgence : « Partly cloudy », « partly-cloudy », « PARTLY_CLOUDY »,
             * « sunny », « storm ». Miroir de `Condition(lenient:)`.
             */
            fun fromLenient(text: String?): Condition {
                val key = (text ?: "").lowercase().replace('-', '_').replace(' ', '_')
                entries.firstOrNull { it.name.lowercase() == key }?.let { return it }
                return when (key) {
                    "sunny", "fair", "mostly_sunny" -> CLEAR
                    "mostly_cloudy", "partly_sunny" -> PARTLY_CLOUDY
                    "overcast" -> CLOUDY
                    "mist", "haze", "smoke" -> FOG
                    "showers", "light_rain" -> RAIN
                    "storm", "storms", "thunderstorms", "thunder" -> THUNDERSTORM
                    "light_snow", "heavy_snow", "flurries" -> SNOW
                    "freezing_rain", "ice" -> SLEET
                    "windy", "breezy" -> WIND
                    else -> UNKNOWN
                }
            }
        }
    }
}

/**
 * Les cartes d'une réponse, lues de l'appel (`onyx_show`).
 *
 * Miroir de `ReplyCards` (Echo/Models/ReplyCards:135) : [tool], les bornes, [parse] tolérant,
 * [cards] depuis un appel ou depuis une étape d'outil, et [resolve] pour une conversation relue.
 */
object ReplyCards {
    /** L'outil du plugin Onyx qui les montre. */
    const val tool: String = "onyx_show"
    const val maxCards: Int = 4
    const val maxPlaces: Int = 12

    /** D'où viennent les prévisions que le plugin cherche, créditées sur la carte. */
    const val lookedUpSource: String = "Open-Meteo.com"

    /**
     * Les cartes qu'un appel demande : `null` quand ce n'est pas l'appel de l'outil, une liste vide
     * quand aucune de ses cartes n'est utilisable.
     */
    fun cards(fromCall: String, args: JsonElement?): List<ReplyCard>? {
        if (fromCall != tool) return null
        return parse(args)
    }

    /**
     * Les mêmes depuis une étape dont les arguments ont été gardés en JSON ([ToolActivity.args]).
     * La réponse du plugin, quand elle est lisible, porte les cartes remplies (le plugin 1.7.0 y
     * met la météo et l'heure cherchées) et prime sur ce que l'agent a écrit.
     */
    fun cards(from: ToolActivity): List<ReplyCard>? {
        if (from.name != tool || from.status == ToolStatus.FAILED) return null
        val answer = from.output?.let(::parseJson)
        if (answer is JsonObject && answer["cards"] is JsonArray) return parse(answer)
        val text = from.args ?: return null
        val args = parseJson(text) ?: return null
        return parse(args)
    }

    /**
     * Les cartes de chaque réponse qui n'en a pas encore, depuis ses étapes : une conversation relue
     * (l'appel d'outil s'y trouve, la carte n'y était pas forcément enregistrée).
     */
    fun resolve(messages: List<ChatMessage>): List<ChatMessage> {
        if (messages.none { it.role == Role.ASSISTANT && it.cards == null && it.tools.isNotEmpty() }) return messages
        return messages.map { message ->
            if (message.role == Role.ASSISTANT && message.cards == null) {
                val found = cards(ofSteps = message.tools)
                if (found.isNotEmpty()) message.copy(cards = found) else message
            } else {
                message
            }
        }
    }

    /** Toutes les cartes que ces étapes demandent, dans l'ordre, bornées à [maxCards]. */
    fun cards(ofSteps: List<ToolActivity>): List<ReplyCard> =
        ofSteps.mapNotNull(::cards).flatten().take(maxCards)

    /** Le texte d'un outil (`ToolActivity.args`) lu comme du JSON, ou `null`. */
    fun parseJson(text: String?): JsonElement? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return runCatching { kotlinx.serialization.json.Json.parseToJsonElement(trimmed) }.getOrNull()
    }

    /** Les unités de température que lit la personne de ce téléphone : « F » ou « C ». */
    fun preferredUnits(locale: Locale = OnyxLanguage.preferred): String =
        if (fahrenheitCountries.contains(locale.country.uppercase(Locale.US))) "F" else "C"

    /**
     * Les pays dont le système de mesure est l'impérial (le `measurementSystem == .us` de la référence, que
     * `UnitTemperature(forLocale:usage:.weather)` suit) : la température s'y écrit en Fahrenheit.
     */
    private val fahrenheitCountries = setOf(
        "US", "LR", "MM", "BS", "BZ", "KY", "PW", "FM", "MH", "GU", "PR", "VI", "AS", "MP",
    )

    fun parse(args: JsonElement?): List<ReplyCard> {
        val body = args as? JsonObject
        val list = body?.get("cards") as? JsonArray
            ?: if (body?.get("type") != null && body["type"] !is JsonNull) listOf(body) else emptyList()
        return list.mapNotNull(::card).take(maxCards)
    }

    // MARK: - Lire une carte

    /** Une carte, ou `null` si son type est inconnu ou ce qu'elle porte ne peut pas être dessiné. */
    fun card(value: JsonElement): ReplyCard? {
        val body = value as? JsonObject ?: return null
        return when (text(body["type"])?.lowercase(Locale.US)) {
            "map" -> {
                val places = placeList(body["places"] ?: body["pins"])
                val route = route(body["route"])
                if (places.isEmpty() && route == null) null
                else ReplyCard.Map(title = text(body["title"]), places = places, route = route)
            }

            "places", "place" -> {
                var places = placeList(body["places"])
                if (places.isEmpty()) place(body["place"] ?: body)?.let { places = listOf(it) }
                if (places.isEmpty()) null else ReplyCard.Places(title = text(body["title"]), places = places)
            }

            "weather" -> weather(body)

            "time", "clock" -> {
                // Un fuseau qui n'en est pas un est laissé de côté plutôt que d'être remplacé en
                // silence par GMT (java.util.TimeZone le fait) : la carte montrerait la mauvaise heure.
                val asked = text(body["timezone"] ?: body["time_zone"] ?: body["timeZone"])
                val zone = asked?.let { runCatching { java.time.ZoneId.of(it) }.getOrNull() }?.id
                val place = text(body["place"] ?: body["location"])?.take(80)
                ReplyCard.Clock(place = place, timeZone = zone)
            }

            else -> null
        }
    }

    fun placeList(value: JsonElement?): List<ReplyCard.Place> =
        (value as? JsonArray).orEmpty().mapNotNull(::place).take(maxPlaces)

    fun place(value: JsonElement?): ReplyCard.Place? {
        if (value == null || value is JsonNull) return null
        // Un lieu qui n'est que son nom : « Tram 28 Café ».
        (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return ReplyCard.Place(name = it.take(120))
        }
        val body = value as? JsonObject ?: return null
        val name = text(body["name"] ?: body["title"]) ?: return null
        val latitude = number(body["latitude"] ?: body["lat"])
        val longitude = number(body["longitude"] ?: body["lon"] ?: body["lng"])
        val place = ReplyCard.Place(
            name = name.take(120),
            address = text(body["address"]),
            latitude = latitude,
            longitude = longitude,
            category = text(body["category"]),
            rating = number(body["rating"])?.coerceIn(0.0, 5.0),
            note = text(body["note"] ?: body["description"])?.take(300),
            phone = text(body["phone"]),
            url = text(body["url"] ?: body["website"])
                ?.takeIf { it.startsWith("http", ignoreCase = true) },
        )
        // Une coordonnée hors du globe est laissée de côté : la carte n'a plus que son nom.
        return if (place.hasCoordinate) place else place.copy(latitude = null, longitude = null)
    }

    fun route(value: JsonElement?): ReplyCard.Route? {
        val body = value as? JsonObject ?: return null
        val to = place(body["to"]) ?: return null
        val mode = ReplyCard.Route.Mode.fromLenient(text(body["mode"]))
        val from = if (text(body["from"])?.lowercase(Locale.US) == "here") null else place(body["from"])
        return ReplyCard.Route(from = from, to = to, mode = mode)
    }

    fun weather(value: JsonObject): ReplyCard.Weather? {
        val place = text(value["place"] ?: value["location"]) ?: return null
        val units = text(value["units"])?.let { if (it.uppercase(Locale.US).startsWith("F")) "F" else "C" }
            ?: preferredUnits()
        var now: ReplyCard.Weather.Now? = null
        // Un relevé nomme sa condition : une température sans condition est un espace réservé (l'agent
        // a déjà envoyé 0 et « » alors que sa réponse disait 85 °F et ciel clair), jamais dessiné.
        val reading = (value["now"] ?: value["current"]) as? JsonObject
        if (reading != null) {
            val temperature = number(reading["temperature"] ?: reading["temp"])
            val condition = text(reading["condition"])
            if (temperature != null && condition != null) {
                now = ReplyCard.Weather.Now(
                    temperature = temperature,
                    condition = ReplyCard.Condition.fromLenient(condition),
                    feelsLike = number(reading["feels_like"] ?: reading["feelsLike"]),
                    high = number(reading["high"]),
                    low = number(reading["low"]),
                    wind = text(reading["wind"]),
                    humidity = number(reading["humidity"])?.let { Math.round(it).toInt() },
                    precipitation = number(reading["precipitation"] ?: reading["chance_of_rain"])
                        ?.let { Math.round(it).toInt() },
                    summary = text(reading["summary"])?.take(200),
                    night = (reading["night"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull(),
                )
            }
        }
        val hours = (value["hours"] as? JsonArray).orEmpty().mapNotNull { element ->
            val hour = element as? JsonObject ?: return@mapNotNull null
            val time = text(hour["time"]) ?: return@mapNotNull null
            val temperature = number(hour["temperature"] ?: hour["temp"]) ?: return@mapNotNull null
            val condition = text(hour["condition"]) ?: return@mapNotNull null
            ReplyCard.Weather.Hour(
                time = time,
                temperature = temperature,
                condition = ReplyCard.Condition.fromLenient(condition),
                precipitation = number(hour["precipitation"])?.let { Math.round(it).toInt() },
                night = (hour["night"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull(),
            )
        }.take(24)
        val days = (value["days"] as? JsonArray).orEmpty().mapNotNull { element ->
            val day = element as? JsonObject ?: return@mapNotNull null
            val date = text(day["date"] ?: day["day"]) ?: return@mapNotNull null
            val high = number(day["high"]) ?: return@mapNotNull null
            val low = number(day["low"]) ?: return@mapNotNull null
            val condition = text(day["condition"]) ?: return@mapNotNull null
            ReplyCard.Weather.Day(
                date = date,
                high = high,
                low = low,
                condition = ReplyCard.Condition.fromLenient(condition),
                precipitation = number(day["precipitation"])?.let { Math.round(it).toInt() },
            )
        }.take(10)
        // Exactement 0 et rien d'autre lu avec lui, sur une carte qui n'a rien de plus : un espace
        // réservé lui aussi (l'agent a envoyé 0 et « clear » sans avoir cherché la météo). Un vrai 0
        // vient avec ses autres nombres.
        if (now != null && now.temperature == 0.0 && now.high == null && now.low == null && now.feelsLike == null &&
            now.wind == null && now.humidity == null && now.precipitation == null && now.summary == null &&
            hours.isEmpty() && days.isEmpty()
        ) {
            now = null
        }
        // Ce qui n'a rien pu être lu est gardé tout de même : le lieu suffit à une carte pour être
        // cherchée. Elle n'est dessinée qu'une fois qu'elle a un relevé (`hasReading`).
        return ReplyCard.Weather(
            place = place.take(80),
            units = units,
            now = now,
            hours = hours,
            days = days,
            source = text(value["source"]),
        )
    }

    /** Une valeur lue comme du texte : une chaîne non vide, ou un nombre écrit comme tel. */
    fun text(value: JsonElement?): String? {
        if (value == null || value is JsonNull) return null
        val primitive = value as? JsonPrimitive ?: return null
        if (primitive.isString) {
            val text = primitive.contentOrNull?.trim().orEmpty()
            return text.takeIf { it.isNotEmpty() }
        }
        val number = primitive.doubleOrNull ?: return null
        return if (number == Math.floor(number) && !number.isInfinite()) {
            number.toLong().toString()
        } else {
            number.toString()
        }
    }

    /** Un nombre, lu même écrit « 61°F » ou « 55% ». */
    fun number(value: JsonElement?): Double? {
        if (value == null || value is JsonNull) return null
        val primitive = value as? JsonPrimitive ?: return null
        primitive.doubleOrNull?.let { if (it.isFinite()) return it }
        val text = primitive.contentOrNull ?: return null
        val cleaned = text.replace("°", "").replace("%", "").trim { it.isLetter() || it.isWhitespace() }
        return cleaned.toDoubleOrNull()
    }
}
