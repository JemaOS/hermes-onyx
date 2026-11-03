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
 * Un modèle de bot prêt à être partagé : la fiche du modèle ([spec] : son nom, ce qu'il fait, son
 * prompt, son visage) **et** ce qu'un bot en hérite à la création — ses outils, ses compétences et
 * son modèle par défaut.
 *
 * `BotTemplateSpec` ne porte que la fiche : c'est le modèle de données existant, et ce lot ne le
 * réécrit pas. Les trois réglages qui manquent vivent donc ici, dans l'enveloppe du partage, et
 * c'est elle que le codec sérialise.
 *
 * Ce qu'un lien ne porte **jamais** : une clé, un jeton, un mot de passe, une adresse de serveur.
 * Le codec les refuse ([BotTemplateShareError.SECRET]) plutôt que de les retirer en silence.
 */
data class BotTemplateShare(
    val spec: BotTemplateSpec,
    val tools: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val model: String? = null,
) {
    /** Le lien à envoyer : la seule forme que le destinataire a besoin de recevoir. */
    val link: String get() = BotTemplateShareCodec.encode(this)

    /** Ce que le destinataire verra avant d'ajouter le bot. */
    fun preview(): BotTemplatePreview = BotTemplatePreview.of(this)

    companion object {
        /** Le partage d'un modèle du catalogue, sans outil, compétence ni modèle choisi. */
        fun of(
            template: BotTemplateSpec,
            tools: List<String> = emptyList(),
            skills: List<String> = emptyList(),
            model: String? = null,
        ): BotTemplateShare = BotTemplateShare(template, tools, skills, model)
    }
}

/** Pourquoi un lien de modèle est refusé — chaque raison a sa phrase, traduite par le dictionnaire. */
enum class BotTemplateShareError(val key: String) {
    /** Ce n'est pas un lien de modèle Onyx : autre schéma, autre hôte, ou texte quelconque. */
    NOT_A_TEMPLATE("That isn't a Onyx bot template link."),

    /**
     * Une version de format que cette build ne connaît pas. Le refus est explicite : lire un
     * format inconnu « au mieux » laisserait passer un champ qu'on ne sait pas interpréter.
     */
    UNKNOWN_VERSION(
        "This bot template was made by a newer version of Onyx (format %1\$s). Update the app, then try again.",
    ),

    /**
     * Le lien porte une clé d'API, un jeton, un mot de passe ou une adresse de serveur. **Rejet**,
     * jamais un nettoyage silencieux : ce qui est retiré sans le dire est ce qui fuit sans le dire.
     */
    SECRET(
        "This bot template carries a credential or a server address (%1\$s). Onyx refuses it.",
    ),

    /** Base64 invalide, JSON illisible, UTF-8 malformé : le lien est abîmé. */
    CORRUPT("This bot template link is damaged and can't be read."),

    /** Un champ que ce format ne connaît pas — y compris une clé étrangère ajoutée à la main. */
    UNKNOWN_FIELD("This bot template carries a field Onyx doesn't know: %1\$s."),

    /** Au-delà de la taille d'un lien qu'Onyx émet : ce n'est pas un des siens. */
    TOO_LONG("This bot template link is too long to be one of Onyx's."),

    /** Un champ obligatoire manque, ou sa valeur n'a pas la forme attendue. */
    INVALID_FIELD("This bot template has an invalid %1\$s."),
    ;

    /** La phrase anglaise, remplie avec [detail] — et traduite si l'interface est en français. */
    fun message(detail: String = ""): String =
        if (detail.isEmpty()) OnyxLocalization.translate(key) else OnyxLocalization.format(key, detail)
}

/** Le refus d'un lien, avec sa raison : l'appelant peut la dire, pas seulement l'avaler. */
class BotTemplateShareException(
    val reason: BotTemplateShareError,
    val detail: String = "",
) : IllegalArgumentException(reason.message(detail))

/**
 * Le codec d'un modèle de bot : `BotTemplateShare` ↔ lien `onyx://bot?v=1&t=…`.
 *
 * ## Le format
 *
 * Un JSON **compact** (clés d'une lettre, pour que le lien reste court), encodé en base64url sans
 * remplissage, dans le paramètre `t` ; la version du format est **obligatoire**, deux fois :
 * dans le lien (`v=`) et dans la charge (`"v"`). Les deux doivent dire [VERSION] : un lien d'une
 * version inconnue est **refusé** ([BotTemplateShareError.UNKNOWN_VERSION]), jamais interprété au
 * mieux.
 *
 * | clé | sens | exemple |
 * |---|---|---|
 * | `v` | version du format | `1` |
 * | `i` | identifiant du modèle | `researcher` |
 * | `n` | nom | `Researcher` |
 * | `a` | ce qu'il fait (rôle) | `Looks things up…` |
 * | `s` | prompt (personnalité) | `# Researcher…` |
 * | `f` | visage | `egg:sparkle:smile:antenna:8:1:-3` |
 * | `t` | outils cochés | `["web"]` |
 * | `k` | compétences cochées | `["arxiv"]` |
 * | `m` | modèle par défaut | `hermes-3` |
 *
 * Le visage garde les identifiants **locaux** (`line`, `wide`, `circle`…) et non les identifiants
 * serveur : un aller-retour doit rendre le **même** visage, pas son équivalent normalisé.
 *
 * ## Ce qui est refusé
 *
 * - une **clé** dont le nom dit un secret (`apiKey`, `token`, `password`, `server`, `url`…), où
 *   qu'elle soit dans la charge ;
 * - une **valeur** qui porte un secret ou une adresse : `http://`, `Bearer …`, `token=…`,
 *   `sk-…`, un bloc de clé privée ;
 * - un champ inconnu — pas de tolérance : un champ qu'on ignore est un champ qu'on ne peut pas
 *   promettre de ne pas appliquer.
 *
 * Un modèle dont le prompt contient une adresse est donc refusé : c'est voulu. Un lien de modèle
 * sert à partager une personnalité, pas un serveur.
 */
object BotTemplateShareCodec {
    /** La version du format que cette build émet, et la seule qu'elle lit. */
    const val VERSION = 1

    /** Le schéma et l'hôte du lien : `onyx://bot?v=1&t=…`. */
    const val SCHEME = "onyx"
    const val HOST = "bot"

    /** Le préfixe exact, sans barre finale : le lien se lit au caractère près. */
    const val PREFIX = "$SCHEME://$HOST"

    /** La même borne que les liens de configuration : au-delà, ce n'est pas un des nôtres. */
    const val MAXIMUM_LENGTH = 12_000

    /** Le paramètre du lien qui porte la charge encodée (`onyx://bot?v=1&t=…`). */
    private const val LINK_PAYLOAD = "t"

    /** Les bornes des champs, pour qu'un lien ne puisse pas porter un roman ni saturer un écran. */
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

    /** Les noms de champ lisibles, pour une erreur qui nomme ce qui cloche. */
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
     * Un nom de clé qui **dit** un secret ou un serveur. Comparé sans casse, sans tiret ni
     * souligné : `apiKey`, `api_key`, `API-KEY` et `apikey` sont le même aveu.
     */
    private val forbiddenKeys = setOf(
        "apikey", "key", "token", "accesstoken", "refreshtoken", "authtoken",
        "password", "passwd", "passphrase", "secret", "clientsecret", "authorization", "auth",
        "bearer", "server", "serverurl", "baseurl", "url", "uri", "endpoint", "host",
        "hostname", "address", "credentials",
        // Les mêmes aveux, en français : un lien forgé à la main ne passe pas par la traduction.
        "cle", "clé", "jeton", "motdepasse",
    )

    /** Une adresse de serveur, sous toutes ses écritures. */
    private val urlScheme = Regex("(?i)\\b(?:https?|wss?|ftp|ssh|onyx)://")

    /** Une affectation de secret dans un texte libre : `api_key=…`, `token: …`, `mot de passe : …`. */
    private val secretAssignment = Regex(
        "(?i)\\b(?:api[_-]?key|access[_-]?token|token|password|passwd|secret|authorization" +
            "|mot\\s+de\\s+passe|jeton|cl[eé]\\s*api)\\s*[:=]\\s*\\S",
    )

    /** Un en-tête d'autorisation recopié tel quel. */
    private val bearer = Regex("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{12,}")

    /** La forme d'une clé d'API de fournisseur (`sk-…`). */
    private val apiKeyShape = Regex("\\bsk-[A-Za-z0-9_-]{16,}")

    /** Un bloc de clé privée PEM. */
    private val privateKey = Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----")

    /** Sérialise un partage en lien. Refuse d'émettre un lien qui porterait un secret. */
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
     * Lit un lien et rend le partage qu'il porte.
     *
     * Lève [BotTemplateShareException] — jamais `null`, jamais un objet à moitié rempli : un lien
     * refusé est un lien dont on dit la raison.
     */
    fun decode(link: String): BotTemplateShare {
        val text = link.trim()
        if (text.isEmpty()) throw BotTemplateShareException(BotTemplateShareError.NOT_A_TEMPLATE)
        if (text.length > MAXIMUM_LENGTH) throw BotTemplateShareException(BotTemplateShareError.TOO_LONG)
        if (!text.startsWith(PREFIX, ignoreCase = true)) {
            throw BotTemplateShareException(BotTemplateShareError.NOT_A_TEMPLATE)
        }

        // Le reste du lien est la requête : `?v=1&t=…`. Un `://` qui traîne après le préfixe est
        // une adresse de serveur glissée dans le lien, même si la charge est encore en clair.
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

    /** La charge en clair, **sans** les secrets : c'est elle que le garde inspecte avant l'encodage. */
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

    /** L'identifiant d'un modèle sans identifiant : « Scout Two » → `scout_two`. */
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
     * Le visage d'un lien : sept champs, les identifiants **locaux** des énumérations. Un
     * identifiant inconnu ou hors bornes est une erreur, pas un visage par défaut : accepter un
     * visage qu'on ne sait pas dessiner serait promettre une créature qu'on ne rend pas.
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

    /** Un champ texte : présent et du bon type, ou une erreur qui le nomme. */
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

    /** Un entier obligatoire (la version de la charge). */
    private fun int(element: JsonElement?, key: String): Int {
        val primitive = element as? JsonPrimitive ?: throw invalid(key)
        return primitive.content.toIntOrNull() ?: throw invalid(key)
    }

    /** Une liste de noms (outils, compétences) : chaînes non vides, sans doublon, dans l'ordre reçu. */
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

    /** Le garde d'un texte libre : une adresse ou un secret, et le lien est refusé. */
    private fun guard(text: String) {
        urlScheme.find(text)?.let { throw secret(it.value) }
        secretAssignment.find(text)?.let { throw secret(it.value.take(40)) }
        bearer.find(text)?.let { throw secret(it.value.take(40)) }
        apiKeyShape.find(text)?.let { throw secret("sk-…") }
        privateKey.find(text)?.let { throw secret("private key") }
    }

    /** Le même garde, sur **chaque** clé et **chaque** valeur de la charge JSON. */
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
