package com.onyx.app.ui.bots

import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.BotFace
import com.onyx.app.ui.design.FaceEyes
import com.onyx.app.ui.design.FaceMark
import com.onyx.app.ui.design.FaceMouth
import com.onyx.app.ui.design.FaceShape
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * A bot template ready to be shared: the template's sheet ([spec]: its name, what it does, its
 * prompt, its face) **and** what a bot inherits from it at creation — its tools, its skills and
 * its default model.
 *
 * `BotTemplateSpec` carries only the sheet: it is the existing data model, and this batch does not
 * rewrite it. The three missing settings therefore live here, in the share envelope, and
 * it is this envelope that the codec serializes.
 *
 * What a link **never** carries: a key, a token, a password, a server address.
 * The codec refuses them ([BotTemplateShareError.SECRET]) rather than removing them silently.
 */
data class BotTemplateShare(
    val spec: BotTemplateSpec,
    val tools: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val model: String? = null,
) {
    /** The link to send: the only form the recipient needs to receive. */
    val link: String get() = BotTemplateShareCodec.encode(this)

    /** What the recipient will see before adding the bot. */
    fun preview(): BotTemplatePreview = BotTemplatePreview.of(this)

    companion object {
        /** The share of a catalog template, with no tool, skill or chosen model. */
        fun of(
            template: BotTemplateSpec,
            tools: List<String> = emptyList(),
            skills: List<String> = emptyList(),
            model: String? = null,
        ): BotTemplateShare = BotTemplateShare(template, tools, skills, model)
    }
}

/** Why a template link is refused — each reason has its sentence, translated by the dictionary. */
enum class BotTemplateShareError(val key: String) {
    /** This is not an Onyx template link: another scheme, another host, or arbitrary text. */
    NOT_A_TEMPLATE("That isn't a Onyx bot template link."),

    /**
     * A format version this build does not know. The refusal is explicit: reading an
     * unknown format "as best we can" would let through a field we cannot interpret.
     */
    UNKNOWN_VERSION(
        "This bot template was made by a newer version of Onyx (format %1\$s). Update the app, then try again.",
    ),

    /**
     * The link carries an API key, a token, a password or a server address. **Rejection**,
     * never a silent cleanup: what is removed without saying so is what leaks without saying so.
     */
    SECRET(
        "This bot template carries a credential or a server address (%1\$s). Onyx refuses it.",
    ),

    /** Invalid Base64, unreadable JSON, malformed UTF-8: the link is damaged. */
    CORRUPT("This bot template link is damaged and can't be read."),

    /** A field this format does not know — including a foreign key added by hand. */
    UNKNOWN_FIELD("This bot template carries a field Onyx doesn't know: %1\$s."),

    /** Beyond the size of a link Onyx emits: it is not one of its own. */
    TOO_LONG("This bot template link is too long to be one of Onyx's."),

    /** A required field is missing, or its value does not have the expected shape. */
    INVALID_FIELD("This bot template has an invalid %1\$s."),
    ;

    /** The English sentence, filled with [detail] — and translated if the interface is in French. */
    fun message(detail: String = ""): String =
        if (detail.isEmpty()) OnyxLocalization.translate(key) else OnyxLocalization.format(key, detail)
}

/** The refusal of a link, with its reason: the caller can say it, not merely swallow it. */
class BotTemplateShareException(
    val reason: BotTemplateShareError,
    val detail: String = "",
) : IllegalArgumentException(reason.message(detail))

/**
 * The codec of a bot template: `BotTemplateShare` ↔ `onyx://bot?v=1&t=…` link.
 *
 * ## The format
 *
 * A **compact** JSON (single-letter keys, so the link stays short), encoded in base64url without
 * padding, in the `t` parameter; the format version is **mandatory**, twice:
 * in the link (`v=`) and in the payload (`"v"`). Both must state [VERSION]: a link of an
 * unknown version is **refused** ([BotTemplateShareError.UNKNOWN_VERSION]), never interpreted as
 * best we can.
 *
 * | key | meaning | example |
 * |---|---|---|
 * | `v` | format version | `1` |
 * | `i` | template identifier | `researcher` |
 * | `n` | name | `Researcher` |
 * | `a` | what it does (role) | `Looks things up…` |
 * | `s` | prompt (personality) | `# Researcher…` |
 * | `f` | face | `egg:sparkle:smile:antenna:8:1:-3` |
 * | `t` | checked tools | `["web"]` |
 * | `k` | checked skills | `["arxiv"]` |
 * | `m` | default model | `hermes-3` |
 *
 * The face keeps the **local** identifiers (`line`, `wide`, `circle`…) and not the server
 * identifiers: a round trip must return the **same** face, not its normalized equivalent.
 *
 * ## What is refused
 *
 * - a **key** whose name states a secret (`apiKey`, `token`, `password`, `server`, `url`…), wherever
 *   it is in the payload;
 * - a **value** that carries a secret or an address: `http://`, `Bearer …`, `token=…`,
 *   `sk-…`, a private key block;
 * - an unknown field — no leniency: a field we ignore is a field we cannot
 *   promise not to apply.
 *
 * A template whose prompt contains an address is therefore refused: that is intended. A template
 * link serves to share a personality, not a server.
 */
object BotTemplateShareCodec {
    /** The format version this build emits, and the only one it reads. */
    const val VERSION = 1

    /** The scheme and host of the link: `onyx://bot?v=1&t=…`. */
    const val SCHEME = "onyx"
    const val HOST = "bot"

    /** The exact prefix, without a trailing slash: the link is read character by character. */
    const val PREFIX = "$SCHEME://$HOST"

    /** The same bound as the configuration links: beyond it, it is not one of ours. */
    const val MAXIMUM_LENGTH = 12_000

    /** The link parameter that carries the encoded payload (`onyx://bot?v=1&t=…`). */
    private const val LINK_PAYLOAD = "t"

    /** The field bounds, so a link cannot carry a novel or saturate a screen. */
    private const val MAX_NAME = 80
    private const val MAX_ABOUT = 600
    private const val MAX_SOUL = 8_000
    private const val MAX_MODEL = 120
    private const val MAX_ID = 60
    private const val MAX_TOOL = 120
    private const val MAX_TOOLS = 64
    private const val MAX_SKILL = 160
    private const val MAX_SKILLS = 128

    private const val KEY_VERSION = "v"
    private const val KEY_ID = "i"
    private const val KEY_NAME = "n"
    private const val KEY_ABOUT = "a"
    private const val KEY_SOUL = "s"
    private const val KEY_FACE = "f"
    private const val KEY_TOOLS = "t"
    private const val KEY_SKILLS = "k"
    private const val KEY_MODEL = "m"

    private val allowedKeys = setOf(
        KEY_VERSION, KEY_ID, KEY_NAME, KEY_ABOUT, KEY_SOUL, KEY_FACE, KEY_TOOLS, KEY_SKILLS, KEY_MODEL,
    )

    /** The readable field names, for an error that names what is wrong. */
    private val fieldNames = mapOf(
        KEY_VERSION to "version",
        KEY_ID to "id",
        KEY_NAME to "name",
        KEY_ABOUT to "description",
        KEY_SOUL to "prompt",
        KEY_FACE to "face",
        KEY_TOOLS to "tools",
        KEY_SKILLS to "skills",
        KEY_MODEL to "model",
    )

    /**
     * A key name that **states** a secret or a server. Compared without case, without hyphen or
     * underscore: `apiKey`, `api_key`, `API-KEY` and `apikey` are the same confession.
     */
    private val forbiddenKeys = setOf(
        "apikey", "key", "token", "accesstoken", "refreshtoken", "authtoken",
        "password", "passwd", "passphrase", "secret", "clientsecret", "authorization", "auth",
        "bearer", "server", "serverurl", "baseurl", "url", "uri", "endpoint", "host",
        "hostname", "address", "credentials",
        // The same confessions, in French: a hand-forged link does not go through translation.
        "cle", "clé", "jeton", "motdepasse",
    )

    /** A server address, in all its spellings. */
    private val urlScheme = Regex("(?i)\\b(?:https?|wss?|ftp|ssh|onyx)://")

    /** A secret assignment in free text: `api_key=…`, `token: …`, `mot de passe : …` ("password:"). */
    private val secretAssignment = Regex(
        "(?i)\\b(?:api[_-]?key|access[_-]?token|token|password|passwd|secret|authorization" +
            "|mot\\s+de\\s+passe|jeton|cl[eé]\\s*api)\\s*[:=]\\s*\\S",
    )

    /** An authorization header copied verbatim. */
    private val bearer = Regex("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{12,}")

    /** The shape of a provider API key (`sk-…`). */
    private val apiKeyShape = Regex("\\bsk-[A-Za-z0-9_-]{16,}")

    /** A PEM private key block. */
    private val privateKey = Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----")

    /** Serializes a share into a link. Refuses to emit a link that would carry a secret. */
    fun encode(share: BotTemplateShare): String {
        val plain = plaintext(share)
        guard(plain)
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(plain.toByteArray(StandardCharsets.UTF_8))
        val link = "$PREFIX?$KEY_VERSION=$VERSION&$LINK_PAYLOAD=$payload"
        if (link.length > MAXIMUM_LENGTH) throw BotTemplateShareException(BotTemplateShareError.TOO_LONG)
        return link
    }

    /**
     * Reads a link and returns the share it carries.
     *
     * Throws [BotTemplateShareException] — never `null`, never a half-filled object: a refused
     * link is a link whose reason is stated.
     */
    fun decode(link: String): BotTemplateShare {
        val text = link.trim()
        if (text.isEmpty()) throw BotTemplateShareException(BotTemplateShareError.NOT_A_TEMPLATE)
        if (text.length > MAXIMUM_LENGTH) throw BotTemplateShareException(BotTemplateShareError.TOO_LONG)
        if (!text.startsWith(PREFIX, ignoreCase = true)) {
            throw BotTemplateShareException(BotTemplateShareError.NOT_A_TEMPLATE)
        }

        // The rest of the link is the query: `?v=1&t=…`. A `://` left after the prefix is
        // a server address slipped into the link, even if the payload is still in clear text.
        val rest = text.substring(PREFIX.length)
        if (!rest.startsWith("?") || rest.length <= 1) {
            throw BotTemplateShareException(BotTemplateShareError.NOT_A_TEMPLATE)
        }
        val query = rest.substring(1)
        guard(query)

        var declaredVersion: String? = null
        var payload: String? = null
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val name = pair.substringBefore('=', "").lowercase()
            val value = pair.substringAfter('=', "")
            when (name) {
                KEY_VERSION -> declaredVersion = value
                LINK_PAYLOAD -> payload = value
                "" -> throw corrupt()
                else -> throw BotTemplateShareException(BotTemplateShareError.UNKNOWN_FIELD, name)
            }
        }
        val version = declaredVersion?.toIntOrNull()
            ?: throw BotTemplateShareException(BotTemplateShareError.UNKNOWN_VERSION, declaredVersion ?: "none")
        if (version != VERSION) {
            throw BotTemplateShareException(BotTemplateShareError.UNKNOWN_VERSION, version.toString())
        }
        val encoded = payload?.takeIf { it.isNotEmpty() } ?: throw corrupt()

        val plain = utf8(unb64(encoded))
        val root = parseObject(plain)
        guardJson(root)

        root.keys.firstOrNull { it !in allowedKeys }?.let { unknown ->
            throw BotTemplateShareException(BotTemplateShareError.UNKNOWN_FIELD, unknown)
        }

        val carriedVersion = int(
            root[KEY_VERSION] ?: throw BotTemplateShareException(BotTemplateShareError.UNKNOWN_VERSION, "none"),
            "version",
        )
        if (carriedVersion != VERSION) {
            throw BotTemplateShareException(BotTemplateShareError.UNKNOWN_VERSION, carriedVersion.toString())
        }

        val name = string(root[KEY_NAME], KEY_NAME, MAX_NAME, required = true)
        val about = string(root[KEY_ABOUT], KEY_ABOUT, MAX_ABOUT)
        val soul = string(root[KEY_SOUL], KEY_SOUL, MAX_SOUL)
        val id = string(root[KEY_ID], KEY_ID, MAX_ID).ifBlank { slug(name) }
        val face = (root[KEY_FACE] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.let(::parseFace)
        val tools = strings(root[KEY_TOOLS], KEY_TOOLS, MAX_TOOL, MAX_TOOLS)
        val skills = strings(root[KEY_SKILLS], KEY_SKILLS, MAX_SKILL, MAX_SKILLS)
        val model = string(root[KEY_MODEL], KEY_MODEL, MAX_MODEL).ifBlank { null }

        return BotTemplateShare(
            spec = BotTemplateSpec(id = id, title = name, about = about, soul = soul, face = face),
            tools = tools,
            skills = skills,
            model = model,
        )
    }

    /** The clear-text payload, **without** the secrets: it is what the guard inspects before encoding. */
    private fun plaintext(share: BotTemplateShare): String {
        val spec = share.spec
        val name = spec.title.trim()
        if (name.isEmpty()) throw invalid("name")
        if (name.length > MAX_NAME) throw invalid("name")
        if (spec.about.length > MAX_ABOUT) throw invalid("description")
        if (spec.soul.length > MAX_SOUL) throw invalid("prompt")
        if (spec.id.length > MAX_ID) throw invalid("id")

        val tools = cleanList(share.tools, MAX_TOOL, MAX_TOOLS, "tools")
        val skills = cleanList(share.skills, MAX_SKILL, MAX_SKILLS, "skills")
        val model = share.model?.trim()?.takeIf { it.isNotEmpty() }
        if (model != null && model.length > MAX_MODEL) throw invalid("model")

        val root = buildJsonObject {
            put(KEY_VERSION, JsonPrimitive(VERSION))
            if (spec.id.isNotBlank()) put(KEY_ID, JsonPrimitive(spec.id))
            put(KEY_NAME, JsonPrimitive(name))
            put(KEY_ABOUT, JsonPrimitive(spec.about))
            put(KEY_SOUL, JsonPrimitive(spec.soul))
            spec.face?.let { put(KEY_FACE, JsonPrimitive(faceCode(it))) }
            if (tools.isNotEmpty()) {
                put(KEY_TOOLS, buildJsonArray { tools.forEach { add(JsonPrimitive(it)) } })
            }
            if (skills.isNotEmpty()) {
                put(KEY_SKILLS, buildJsonArray { skills.forEach { add(JsonPrimitive(it)) } })
            }
            model?.let { put(KEY_MODEL, JsonPrimitive(it)) }
        }
        return root.toString()
    }

    /** The identifier of a template without one: "Scout Two" → `scout_two`. */
    private fun slug(name: String): String =
        name.trim().lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifEmpty { "template" }
            .take(MAX_ID)

    private fun faceCode(face: BotFace): String = listOf(
        face.shape.id,
        face.eyes.id,
        face.mouth.id,
        face.mark.id,
        face.colour.toString(),
        face.glance.toString(),
        face.tilt.toString(),
    ).joinToString(":")

    /**
     * A link's face: seven fields, the **local** identifiers of the enums. An
     * unknown or out-of-bounds identifier is an error, not a default face: accepting a
     * face we cannot draw would promise a creature we do not deliver.
     */
    private fun parseFace(raw: String): BotFace {
        val parts = raw.split(':')
        if (parts.size != 7) throw invalid("face")
        val shape = FaceShape.fromId(parts[0]) ?: throw invalid("face shape")
        val eyes = FaceEyes.fromId(parts[1]) ?: throw invalid("face eyes")
        val mouth = FaceMouth.fromId(parts[2]) ?: throw invalid("face mouth")
        val mark = FaceMark.fromId(parts[3]) ?: throw invalid("face mark")
        val colour = parts[4].toIntOrNull()?.takeIf { it in 0..31 } ?: throw invalid("face colour")
        val glance = parts[5].toIntOrNull()?.takeIf { it in 0..3 } ?: throw invalid("face glance")
        val tilt = parts[6].toIntOrNull()?.takeIf { it in -20..20 } ?: throw invalid("face tilt")
        return BotFace(shape = shape, eyes = eyes, mouth = mouth, colour = colour, mark = mark, glance = glance, tilt = tilt)
    }

    /** A text field: present and of the right type, or an error that names it. */
    private fun string(element: JsonElement?, key: String, maximum: Int, required: Boolean = false): String {
        if (element == null) {
            if (required) throw invalid(fieldNames[key] ?: key)
            return ""
        }
        val primitive = element as? JsonPrimitive
        if (primitive == null || !primitive.isString) throw invalid(fieldNames[key] ?: key)
        val value = primitive.content
        if (value.length > maximum) throw invalid(fieldNames[key] ?: key)
        if (required && value.isBlank()) throw invalid(fieldNames[key] ?: key)
        return value
    }

    /** A required integer (the payload version). */
    private fun int(element: JsonElement?, key: String): Int {
        val primitive = element as? JsonPrimitive ?: throw invalid(key)
        return primitive.content.toIntOrNull() ?: throw invalid(key)
    }

    /** A list of names (tools, skills): non-empty strings, no duplicates, in the order received. */
    private fun strings(element: JsonElement?, key: String, maximum: Int, count: Int): List<String> {
        if (element == null) return emptyList()
        val array = element as? JsonArray ?: throw invalid(fieldNames[key] ?: key)
        if (array.size > count) throw invalid(fieldNames[key] ?: key)
        return array.map { item ->
            val primitive = item as? JsonPrimitive
            if (primitive == null || !primitive.isString) throw invalid(fieldNames[key] ?: key)
            val value = primitive.content.trim()
            if (value.isEmpty() || value.length > maximum) throw invalid(fieldNames[key] ?: key)
            value
        }.distinct()
    }

    private fun cleanList(values: List<String>, maximum: Int, count: Int, key: String): List<String> {
        if (values.size > count) throw invalid(key)
        val cleaned = values.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.any { it.length > maximum }) throw invalid(key)
        return cleaned.distinct()
    }

    private fun parseObject(plain: String): JsonObject = try {
        Json.parseToJsonElement(plain) as? JsonObject ?: throw corrupt()
    } catch (failure: BotTemplateShareException) {
        throw failure
    } catch (_: Exception) {
        throw corrupt()
    }

    private fun unb64(encoded: String): ByteArray {
        val padded = when (encoded.length % 4) {
            2 -> "$encoded=="
            3 -> "$encoded="
            0 -> encoded
            else -> throw corrupt()
        }
        return try {
            Base64.getUrlDecoder().decode(padded)
        } catch (_: IllegalArgumentException) {
            throw corrupt()
        }
    }

    private fun utf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        throw corrupt()
    }

    /** The guard of free text: an address or a secret, and the link is refused. */
    private fun guard(text: String) {
        urlScheme.find(text)?.let { throw secret(it.value) }
        secretAssignment.find(text)?.let { throw secret(it.value.take(40)) }
        bearer.find(text)?.let { throw secret(it.value.take(40)) }
        apiKeyShape.find(text)?.let { throw secret("sk-…") }
        privateKey.find(text)?.let { throw secret("private key") }
    }

    /** The same guard, on **every** key and **every** value of the JSON payload. */
    private fun guardJson(element: JsonElement) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                if (normalizeKey(key) in forbiddenKeys) throw secret(key)
                guardJson(value)
            }
            is JsonArray -> element.forEach(::guardJson)
            is JsonPrimitive -> if (element.isString) guard(element.content)
        }
    }

    private fun normalizeKey(key: String): String =
        key.lowercase().filter { it.isLetterOrDigit() }

    private fun secret(detail: String) =
        BotTemplateShareException(BotTemplateShareError.SECRET, detail.take(60))

    private fun corrupt() = BotTemplateShareException(BotTemplateShareError.CORRUPT)

    private fun invalid(field: String) =
        BotTemplateShareException(BotTemplateShareError.INVALID_FIELD, field)
}
