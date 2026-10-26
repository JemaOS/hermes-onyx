package com.onyx.app.data.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Petites lectures sur le JSON du Dashboard.
 *
 * Les routes de `hermes serve` rendent des formes très diverses (et parfois incomplètes selon la
 * version du serveur): on les lit donc à la demande, sans exiger un schéma rigide — comme le
 * `JSONValue` , qui laisse chaque appelant prendre ce qu'il comprend.
 */
fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull

fun JsonElement?.asInt(): Int? = (this as? JsonPrimitive)?.intOrNull

fun JsonElement?.asLong(): Long? = (this as? JsonPrimitive)?.longOrNull

fun JsonElement?.asBool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

/** Une chaîne, ou `""` — pour les champs qu'un serveur peut omettre. */
fun JsonObject.str(key: String): String = this[key]?.asString().orEmpty()

fun JsonObject.strOrNull(key: String): String? = this[key]?.asString()?.takeIf { it.isNotEmpty() }

fun JsonObject.int(key: String): Int? = this[key]?.asInt()

fun JsonObject.long(key: String): Long? = this[key]?.asLong()

fun JsonObject.bool(key: String): Boolean? = this[key]?.asBool()

fun JsonObject.obj(key: String): JsonObject? = this[key].asObject()

fun JsonObject.array(key: String): List<JsonElement> = this[key].asArray().orEmpty()

/** La première clé dont la valeur est une chaîne non vide: les serveurs changent de nom. */
fun JsonObject.firstString(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { this[it]?.asString()?.takeIf { value -> value.isNotEmpty() } }

/** `true` seulement si la clé vaut explicitement `false` — utile aux drapeaux facultatifs. */
fun JsonObject.isFalse(key: String): Boolean = this[key]?.asBool() == false

internal fun jsonOf(value: String?): JsonElement = value?.let { JsonPrimitive(it) } ?: JsonNull

/** Un tableau de chaînes, en ignorant ce qui n'en est pas. */
fun JsonObject.stringList(key: String): List<String> =
    array(key).mapNotNull { it.asString() }
