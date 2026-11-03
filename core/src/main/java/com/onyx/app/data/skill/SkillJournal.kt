package com.onyx.app.data.skill

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * **Le journal sur le fil** : la forme exacte que les trois routes du compagnon acceptent
 * (`docs/SPEC-SKILLS-RECORD.md` §2), et la lecture inverse du journal canonique qu'elles rendent.
 *
 * Les deux sens vivent ici, dans le module `core`, sans réseau ni écran : c'est ce qui permet de
 * confronter le journal d'un test au format de la spec sans lancer le greffon Python.
 *
 * ### Trois écarts assumés, et pourquoi
 *
 * 1. **`type` secret : `chars = 0`, pas la longueur réelle.** La spec §2.2 demande la longueur du
 *    texte saisi « même quand `secret` est vrai » ; le modèle du lot dit l'inverse (« le brouillon
 *    n'en gardera rien, pas même la longueur », [TracedAction.Type]). Le serveur n'exige qu'un
 *    entier ≥ 0 : zéro dit « une saisie a eu lieu, rien n'en est gardé », et ne raconte pas la
 *    longueur d'un mot de passe.
 * 2. **Les défilements horizontaux ne partent pas.** La spec §2.2 n'admet que `up` et `down` ; un
 *    `left`/`right` ferait refuser **tout** le journal (422). L'enregistreur n'en produit pas — la
 *    tablette de l'écran Ordinateur ne défile qu'à la verticale — donc ce filtre ne perd rien, il
 *    empêche seulement un journal fabriqué à la main de tout jeter.
 * 3. **`scale` vaut 1.0.** Le pont annonce la taille de l'écran du PC en pixels, pas la densité de
 *    la fenêtre qu'il capture : écrire autre chose serait une mesure inventée. Le champ n'est là
 *    que parce que la spec l'exige.
 */
object SkillJournal {

    /** La seule version que ce client écrit ; une autre vaudrait `422 version_unsupported`. */
    const val VERSION: Int = 1

    /** La borne de la spec : au-delà, le serveur répond `413 journal_too_large`. */
    const val MAX_TEXT: Int = 2000

    /** Ce que le champ `scale` porte — voir l'écart 3 ci-dessus. */
    private const val SCALE: Double = 1.0

    /**
     * Le corps du journal envoyé à `stop`.
     *
     * [entries] sont les actions horodatées de l'enregistreur ; [startedAtSeconds] ancre les
     * `at_ms`, il reste celui du téléphone (spec §4). [endReason] vaut `stopped`, ou `expired`
     * quand le plafond a coupé la capture.
     */
    fun toJson(
        entries: List<SkillRecorder.Entry>,
        sessionId: String,
        startedAtSeconds: Double,
        durationMs: Long,
        screenWidth: Int,
        screenHeight: Int,
        bridgeEndpoint: String,
        bridgeSession: String,
        endReason: String = "stopped",
        title: String? = null,
    ): JsonObject = buildJsonObject {
        put("version", VERSION)
        put("session_id", sessionId)
        put("started_at", startedAtSeconds)
        put("duration_ms", durationMs.coerceIn(0L, SkillRecorder.MAX_DURATION_MS))
        put("end_reason", if (endReason == "expired") "expired" else "stopped")
        put(
            "screen",
            buildJsonObject {
                put("width_px", screenWidth.coerceAtLeast(1))
                put("height_px", screenHeight.coerceAtLeast(1))
                put("scale", SCALE)
            },
        )
        put(
            "bridge",
            buildJsonObject {
                put("endpoint", bridgeEndpoint)
                put("session", bridgeSession)
            },
        )
        put("events", events(entries, screenWidth, screenHeight))
        if (!title.isNullOrBlank()) put("title", title.trim().take(120))
    }

    /** Les événements numérotés, dans l'ordre, sans trou. */
    private fun events(entries: List<SkillRecorder.Entry>, width: Int, height: Int): JsonArray {
        // Les événements abandonnés (défilement horizontal, adresse non web) sont retirés **avant**
        // la numérotation : `seq` reste contigu, et le journal ne porte pas de trou gratuit. La
        // spec tolère les trous, mais un `seq` sauté sans `dropped` pour l'expliquer se lirait
        // comme une perte.
        val kept = entries.mapNotNull { entry -> event(entry, width, height) }
        return buildJsonArray {
            kept.forEachIndexed { index, event ->
                add(
                    JsonObject(
                        event + ("seq" to kotlinx.serialization.json.JsonPrimitive(index + 1)),
                    ),
                )
            }
        }
    }

    /**
     * Un événement, ou `null` quand le journal du modèle ne sait pas l'écrire.
     *
     * Le `null` ne concerne que ce que la spec refuse : un défilement horizontal, une adresse qui
     * n'est ni `http` ni `https`. Une étape perdue pour cette raison vaut mieux qu'un `422` qui
     * jetterait l'enregistrement entier.
     */
    private fun event(entry: SkillRecorder.Entry, width: Int, height: Int): JsonObject? {
        val action = entry.action
        val atMs = entry.atMs

        fun base(kind: String, target: JsonObject? = null) = buildJsonObject {
            put("at_ms", atMs)
            put("kind", kind)
            if (target != null) put("target", target)
        }

        return when (action) {
            is TracedAction.Tap -> {
                val target = buildJsonObject {
                    singleLine(action.label)?.let { put("label", it.take(200)) }
                    // Un pourcentage n'est écrit que si la fenêtre est réellement connue : sur une
                    // taille nulle, il désignerait le coin haut-gauche et ferait cliquer à côté.
                    if (width > 0 && height > 0) {
                        put("x_pct", fraction(action.x, width))
                        put("y_pct", fraction(action.y, height))
                    }
                }
                base("tap", target)
            }

            is TracedAction.Type -> buildJsonObject {
                put("at_ms", atMs)
                put("kind", "type")
                put("target", buildJsonObject { })
                if (action.isSecret) {
                    // Ni texte, ni longueur : voir l'écart 1 de l'en-tête.
                    put("secret", true)
                    put("chars", 0)
                } else {
                    val text = action.text.take(MAX_TEXT)
                    put("text", text)
                    put("chars", text.length)
                }
            }

            is TracedAction.Scroll -> {
                val direction = when (action.direction) {
                    ScrollDirection.UP -> "up"
                    ScrollDirection.DOWN -> "down"
                    // Non représentable sur le fil : l'étape est abandonnée, pas maquillée.
                    ScrollDirection.LEFT, ScrollDirection.RIGHT -> return null
                }
                buildJsonObject {
                    put("at_ms", atMs)
                    put("kind", "scroll")
                    put("target", buildJsonObject { })
                    put("direction", direction)
                    put("amount", action.amount.coerceAtLeast(0))
                }
            }

            is TracedAction.Navigate -> {
                val url = action.url.trim()
                if (!isWebAddress(url)) return null
                buildJsonObject {
                    put("at_ms", atMs)
                    put("kind", "navigate")
                    put("target", buildJsonObject { })
                    put("url", url.take(2048))
                }
            }

            is TracedAction.Wait -> {
                val ms = action.ms.coerceIn(0L, 30_000L).toInt()
                buildJsonObject {
                    put("at_ms", atMs)
                    put("kind", "wait")
                    put("ms", ms)
                }
            }

            is TracedAction.Key -> {
                val key = when (action.key) {
                    NamedKey.BACK -> "back"
                    NamedKey.HOME -> "home"
                    NamedKey.ENTER -> "enter"
                    NamedKey.ESCAPE -> "escape"
                }
                buildJsonObject {
                    put("at_ms", atMs)
                    put("kind", "key")
                    put("target", buildJsonObject { })
                    put("key", key)
                }
            }
        }
    }

    /**
     * **Le journal canonique rendu par `stop`**, relu dans le modèle de l'app.
     *
     * C'est la forme que le serveur a validée et bornée (D3) : ce que l'aperçu montre doit être ce
     * que le brouillon recevra, pas ce que le téléphone croyait avoir envoyé. Un événement que ce
     * lecteur ne comprend pas est ignoré — jamais deviné.
     */
    fun fromJson(root: JsonObject): ActionJournal {
        val screen = root["screen"] as? JsonObject
        val width = screen?.get("width_px")?.text()?.toIntOrNull() ?: 0
        val height = screen?.get("height_px")?.text()?.toIntOrNull() ?: 0
        val title = root["title"]?.text()?.takeIf { it.isNotBlank() }

        val actions = (root["events"] as? JsonArray).orEmpty().mapNotNull { element ->
            val event = element as? JsonObject ?: return@mapNotNull null
            val kind = event["kind"]?.text() ?: return@mapNotNull null
            when (kind) {
                "tap" -> {
                    val target = event["target"] as? JsonObject
                    // Le serveur écrit des fractions ; le modèle, des pixels. On repasse par la
                    // fenêtre pour que le brouillon recalculé retombe exactement sur le même
                    // pourcentage (arrondi demi vers le haut, comme `SkillDraft`).
                    val x = fraction(target?.get("x_pct")?.text()?.toDoubleOrNull(), width)
                    val y = fraction(target?.get("y_pct")?.text()?.toDoubleOrNull(), height)
                    TracedAction.Tap(x = x, y = y, label = target?.get("label")?.text())
                }

                "type" -> {
                    val secret = event["secret"]?.text()?.toBooleanStrictOrNull() == true
                    TracedAction.Type(text = if (secret) "" else event["text"]?.text().orEmpty(), isSecret = secret)
                }

                "scroll" -> {
                    val direction = when (event["direction"]?.text()) {
                        "up" -> ScrollDirection.UP
                        "down" -> ScrollDirection.DOWN
                        else -> return@mapNotNull null
                    }
                    TracedAction.Scroll(direction, event["amount"]?.text()?.toIntOrNull() ?: 0)
                }

                "navigate" -> TracedAction.Navigate(event["url"]?.text().orEmpty())

                "wait" -> TracedAction.Wait(event["ms"]?.text()?.toLongOrNull() ?: 0L)

                "key" -> {
                    val key = when (event["key"]?.text()) {
                        "back" -> NamedKey.BACK
                        "home" -> NamedKey.HOME
                        "enter" -> NamedKey.ENTER
                        "escape" -> NamedKey.ESCAPE
                        else -> return@mapNotNull null
                    }
                    TracedAction.Key(key)
                }

                // `focus` fait partie de la spec, pas du modèle du lot : le brouillon ne le
                // convertit pas, et le relire ici n'ajouterait rien qu'il sache dire.
                else -> null
            }
        }

        return ActionJournal(actions = actions, windowWidth = width, windowHeight = height, title = title)
    }

    /** Une fraction 0..1 du serveur, rendue en pixels de la fenêtre. */
    private fun fraction(ratio: Double?, span: Int): Float {
        if (ratio == null || span <= 0) return 0f
        return (ratio * span).toFloat()
    }

    /** Une position en pixels, rendue en fraction 0..1 bornée — le serveur refuse le reste. */
    private fun fraction(position: Float, span: Int): Double {
        if (span <= 0) return 0.0
        return (position.toDouble() / span).coerceIn(0.0, 1.0)
    }

    /** Une adresse réellement web — la spec refuse `file:`, `onyx:`, `content:` (422). */
    private fun isWebAddress(url: String): Boolean {
        if (url.isEmpty() || url.length > 2048) return false
        val scheme = url.substringBefore(':', "").lowercase()
        return (scheme == "http" || scheme == "https") && url.contains("//")
    }

    /** Un libellé d'une seule ligne, ou `null` s'il n'y a rien à dire. */
    private fun singleLine(label: String?): String? {
        val text = label?.trim().orEmpty()
        return text.takeIf { it.isNotEmpty() }
    }

    /** La lecture tolérante d'une primitive JSON : chaîne, nombre ou booléen. */
    private fun kotlinx.serialization.json.JsonElement.text(): String? =
        (this as? kotlinx.serialization.json.JsonPrimitive)?.content
}
