package com.onyx.app.ui.design

import com.onyx.app.localization.OnyxLanguage
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import com.onyx.app.ui.theme.ThemeColors
import com.onyx.app.ui.theme.ThemeColors.RGB
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * La forme d'un visage de bot.
 *
 * Les identifiants suivent ceux de la référence (`BotFace.Shape`) : `round`, `egg`, `pill`, `capsule`,
 * `burst`, `pick`, `hex`, `diamond`, `ghost`, `square`, `star`, `blob`, `bubble`. Trois formes plus
 * anciennes (`circle`, `squircle`, `bubble`) restent dans l'énumération pour qu'un code enregistré
 * avant ce portage se relise encore ; `circle` et `squircle` dessinent la même créature que `round`
 * et `square`.
 */
enum class FaceShape(val id: String, val frLabel: String) {
    // Les formes écrites avant le portage : gardées pour relire les anciens codes.
    CIRCLE("circle", "rond"),
    SQUIRCLE("squircle", "carré arrondi"),
    // La bulle de Onyx, sa queue rentrée en bas à gauche : personne d'autre ne l'a.
    BUBBLE("bubble", "bulle"),
    // Les formes de la référence, dans leur ordre de première écriture.
    ROUND("round", "rond"),
    EGG("egg", "œuf"),
    PILL("pill", "pilule"),
    CAPSULE("capsule", "capsule"),
    BURST("burst", "étoilé"),
    PICK("pick", "médiator"),
    HEX("hex", "hexagone"),
    DIAMOND("diamond", "losange"),
    // Des formes seulement choisies, jamais données par un nom.
    GHOST("ghost", "fantôme"),
    SQUARE("square", "carré"),
    STAR("star", "étoile"),
    BLOB("blob", "blob"),
    ;

    /** Les anciennes formes gardent leur rendu ; toute écriture serveur utilise le nom la référence. */
    val server: FaceShape
        get() = when (this) {
            CIRCLE -> ROUND
            SQUIRCLE -> SQUARE
            else -> this
        }

    companion object {
        /** Ordre figé de la référence pour les visages donnés par un nom. */
        val seeded = listOf(ROUND, EGG, PILL, CAPSULE, BURST, PICK, HEX, DIAMOND)

        /** Formes proposées au picker ; la bulle reste exclusive au profil principal. */
        val given = listOf(ROUND, EGG, PILL, CAPSULE, BURST, PICK, HEX, DIAMOND, GHOST, SQUARE, STAR, BLOB)

        /** `BotFacePicker` : la bulle en premier pour Onyx seulement. */
        fun offered(isDefault: Boolean): List<FaceShape> = if (isDefault) listOf(BUBBLE) + given else given

        fun fromId(raw: String?): FaceShape? = entries.firstOrNull { it.id == raw }

        /** Un code v1 ne reconnaît que les identifiants de la référence, jamais les anciens alias. */
        fun fromServerId(raw: String?): FaceShape? = entries.firstOrNull { it == it.server && it.id == raw }
    }
}

/**
 * Ses yeux.
 *
 * Le la référence connaît `round`, `mismatched`, `sleepy`, `happy`, `sparkle`. `line` et `wide` sont deux
 * yeux plus anciens, gardés pour relire les anciens codes ; l'humeur `WORKING` s'en sert encore.
 */
enum class FaceEyes(val id: String, val frLabel: String) {
    ROUND("round", "ronds"),
    LINE("line", "en ligne"),
    WIDE("wide", "grands"),
    SPARKLE("sparkle", "étincelants"),
    HAPPY("happy", "joyeux"),
    SLEEPY("sleepy", "ensommeillés"),
    MISMATCHED("mismatched", "dépareillés"),
    ;

    val server: FaceEyes
        get() = when (this) {
            LINE -> SLEEPY
            WIDE -> ROUND
            else -> this
        }

    companion object {
        /** `Eyes.allCases`, dans l'ordre de la référence. */
        val ordered = listOf(ROUND, MISMATCHED, SLEEPY, HAPPY, SPARKLE)

        fun fromId(raw: String?): FaceEyes? = entries.firstOrNull { it.id == raw }
        fun fromServerId(raw: String?): FaceEyes? = ordered.firstOrNull { it.id == raw }
    }
}

/**
 * Sa bouche.
 *
 * Le la référence connaît `none`, `smile`, `oh`, `grin`. `line` et `open` sont plus anciennes, gardées pour
 * relire les anciens codes ; l'humeur `WORKING` et `WAITING` s'en sert encore.
 */
enum class FaceMouth(val id: String, val frLabel: String) {
    SMILE("smile", "sourire"),
    LINE("line", "ligne"),
    OPEN("open", "ouverte"),
    NONE("none", "sans bouche"),
    GRIN("grin", "grand sourire"),
    OH("oh", "bouche en rond"),
    ;

    val server: FaceMouth
        get() = when (this) {
            LINE -> NONE
            OPEN -> OH
            else -> this
        }

    companion object {
        /** `Mouth.allCases`, dans l'ordre de la référence. */
        val ordered = listOf(NONE, SMILE, OH, GRIN)

        fun fromId(raw: String?): FaceMouth? = entries.firstOrNull { it.id == raw }
        fun fromServerId(raw: String?): FaceMouth? = ordered.firstOrNull { it.id == raw }
    }
}

/** La marque d'un visage : rien, une antenne, une étincelle, un fard, des taches. */
enum class FaceMark(val id: String, val frLabel: String) {
    NONE("none", "rien"),
    ANTENNA("antenna", "antenne"),
    SPARK("spark", "étincelle"),
    BLUSH("blush", "fard"),
    FRECKLES("freckles", "taches"),
    ;

    companion object {
        /** `Mark.allCases`, dans l'ordre de la référence. */
        val ordered = listOf(NONE, ANTENNA, SPARK, FRECKLES, BLUSH)

        fun fromId(raw: String?): FaceMark? = entries.firstOrNull { it.id == raw }
        fun fromServerId(raw: String?): FaceMark? = ordered.firstOrNull { it.id == raw }
    }
}

/**
 * Le visage d'un bot : une forme, deux yeux, une bouche, une marque, une couleur.
 *
 * Miroir de `BotFace` côté référence. Le code serveur est
 * `1:forme:yeux:bouche:marque:couleur:regard:inclinaison` ; les anciens codes à tirets restent
 * lisibles et sont normalisés vers ce format à la prochaine écriture.
 */
data class BotFace(
    val shape: FaceShape = FaceShape.CIRCLE,
    val eyes: FaceEyes = FaceEyes.ROUND,
    val mouth: FaceMouth = FaceMouth.SMILE,
    val colour: Int = 0,
    val mark: FaceMark = FaceMark.NONE,
    /** Direction des pupilles, de 0 à 3. */
    val glance: Int = 0,
    /** Légère inclinaison en degrés. */
    val tilt: Int = 0,
) {
    fun code(): String =
        "1:${shape.server.id}:${eyes.server.id}:${mouth.server.id}:${mark.id}:$colour:$glance:$tilt"

    private fun sameFeatures(other: BotFace): Boolean =
        shape == other.shape && eyes == other.eyes && mouth == other.mouth && mark == other.mark

    /**
     * Les couleurs qu'une conversation avec ce bot prend quand « Bot colors in their chats » est
     * allumé (`BotFace.chatColors` de la référence) : vos bulles et l'accent dans le profond de sa couleur.
     *
     * Rien pour un visage de la couleur du thème, qui est déjà celle du thème ; blanc et noir ne
     * colorent que les bulles (un accent dans l'un ou l'autre disparaîtrait sur une des pages).
     */
    fun chatColors(): ThemeColors? = when (colour) {
        themed -> null
        codeBlue -> ThemeColors(accent = THEME_CODE_BLUE, bubble = THEME_CODE_BLUE)
        white -> ThemeColors(accent = null, bubble = RGB(pair(colour).first))
        white + 1 -> ThemeColors(accent = null, bubble = RGB(pair(colour).second))
        else -> {
            val own = RGB(pair(colour).second)
            ThemeColors(accent = own, bubble = own)
        }
    }

    /**
     * Le visage décrit en français, pour qui ne le voit pas : « Visage bleu, yeux ronds, sourire ».
     *
     * Même intention que `BotFacePicker.describe` côté référence : la bouche et la marque se taisent
     * quand il n'y en a pas.
     */
    fun describe(locale: Locale = OnyxLanguage.preferred): String {
        val french = locale.language.equals("fr", ignoreCase = true)
        val index = colour.coerceIn(0, colourNamesFr.lastIndex)
        val colourName = if (french) colourNamesFr[index] else colourNames[index]
        val eyesLabel = OnyxLocalization.translate(eyes.frLabel, locale)
        val mouthPart = if (mouth == FaceMouth.NONE) "" else ", ${OnyxLocalization.translate(mouth.frLabel, locale)}"
        val markPart = if (mark == FaceMark.NONE) "" else ", ${OnyxLocalization.translate(mark.frLabel, locale)}"
        return if (french) {
            "Visage $colourName, yeux $eyesLabel$mouthPart$markPart"
        } else {
            "$colourName face, $eyesLabel eyes$mouthPart$markPart"
        }
    }

    companion object {
        /** Chaque couleur de la référence, déjà exprimée en dégradé clair → profond. */
        private val palette = listOf(
            Color(0.66f, 0.83f, 1.00f) to Color(0.30f, 0.55f, 0.96f),
            Color(1.00f, 0.89f, 0.54f) to Color(0.96f, 0.68f, 0.22f),
            Color(1.00f, 0.70f, 0.85f) to Color(0.91f, 0.34f, 0.62f),
            Color(0.68f, 0.73f, 1.00f) to Color(0.36f, 0.42f, 0.94f),
            Color(0.58f, 0.94f, 0.87f) to Color(0.13f, 0.69f, 0.60f),
            Color(0.82f, 0.74f, 1.00f) to Color(0.54f, 0.40f, 0.94f),
            Color(0.78f, 0.96f, 0.66f) to Color(0.34f, 0.76f, 0.40f),
            Color(1.00f, 0.78f, 0.57f) to Color(0.94f, 0.50f, 0.22f),
            Color(0.66f, 0.91f, 1.00f) to Color(0.18f, 0.68f, 0.90f),
            Color(1.00f, 0.73f, 0.66f) to Color(0.93f, 0.42f, 0.37f),
            Color(0.92f, 0.97f, 0.63f) to Color(0.70f, 0.80f, 0.17f),
            Color(1.00f, 1.00f, 1.00f) to Color(0.86f, 0.86f, 0.86f),
            Color(0.34f, 0.34f, 0.34f) to Color(0.07f, 0.07f, 0.07f),
        )
        private val laterPalette = listOf(
            Color(1.00f, 0.87f, 0.93f) to Color(0.96f, 0.64f, 0.80f),
        )

        /** Conservé pour les anciens appels ; le dessin utilise désormais [gradient]. */
        val colours: List<Color> = palette.map { it.second } + listOf(
            Color(0xFF7C7C88),
            Color(0.796f, 0.941f, 1.0f),
            laterPalette.single().second,
        )

        fun pair(colour: Int): Pair<Color, Color> =
            if (colour >= laterColors && colour - laterColors < laterPalette.size) {
                laterPalette[colour - laterColors]
            } else {
                palette[Math.floorMod(colour, palette.size)]
            }

        /** Les couleurs du picker, Soft Pink immédiatement après Pink, sans les deux de Onyx. */
        val pickable = listOf(0, 1, 2, softPink, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)

        /** Les deux couleurs de Onyx d'abord pour le principal, puis la palette commune. */
        fun offeredColors(isDefault: Boolean): List<Int> =
            (if (isDefault) listOf(themed, codeBlue) else emptyList()) + pickable

        /** « Surprise Me » : une créature quelconque, jamais la bulle, en gardant regard et lean. */
        fun random(keeping: BotFace, random: Random = Random.Default): BotFace = BotFace(
            shape = FaceShape.given.random(random),
            eyes = FaceEyes.ordered.random(random),
            mouth = FaceMouth.ordered.random(random),
            mark = FaceMark.ordered.random(random),
            colour = pickable.random(random),
            glance = keeping.glance,
            tilt = keeping.tilt,
        )

        /** La couleur dynamique de la bulle principale, ou le bleu Code dans n'importe quel thème. */
        fun gradient(colour: Int, theme: ResolvedTheme): Pair<Color, Color> {
            val own = when (colour) {
                themed -> if (theme.promptPrefix != null) theme.promptColor else theme.userBubbleColor
                codeBlue -> Color(0.796f, 0.941f, 1.0f)
                else -> return pair(colour)
            }
            return own.lighten(0.14f) to own
        }

        /** Le blanc : dessiné avec un liseré, sinon il disparaîtrait sur une page blanche. */
        const val white = 11

        /** Les noms des couleurs, dans l'ordre de la référence. */
        val colourNames = listOf(
            "Blue", "Amber", "Pink", "Indigo", "Teal", "Violet", "Green", "Orange",
            "Sky", "Coral", "Lime", "White", "Black", "Theme", "Code Blue", "Soft Pink",
        )

        /** Les mêmes noms, en français, pour décrire un visage à qui ne le voit pas. */
        val colourNamesFr = listOf(
            "bleu", "ambre", "rose", "indigo", "sarcelle", "violet", "vert", "orange",
            "ciel", "corail", "citron vert", "blanc", "noir", "thème", "bleu code", "rose pâle",
        )

        const val seededColors = 11
        const val themed = 13
        const val codeBlue = 14
        const val laterColors = 15
        const val softPink = 15
        const val colorCount = 16

        /** #CBF0FF, l'accent et la bulle du thème Code, pour `chatColors`. */
        private val THEME_CODE_BLUE = RGB(0.796, 0.941, 1.0)

        /** La bulle principale de Onyx, quelle que soit la façon dont le profil est renommé. */
        val main = BotFace(
            shape = FaceShape.BUBBLE,
            eyes = FaceEyes.ROUND,
            mouth = FaceMouth.NONE,
            mark = FaceMark.NONE,
            colour = themed,
            glance = 1,
            tilt = -3,
        )

        /**
         * Le visage d'un auteur que l'on **ne connaît pas** — la personne, un outil, le système —,
         * quand aucun nom de profil ne le décrit : un rond **gris**, deux yeux ronds, aucune bouche
         * et aucune marque.
         *
         * C'est le seul visage générique. Il ne sert **jamais** à un bot dont le profil est chargé :
         * celui-là montre la créature de **son** profil (voir `botFace`). Rien de coloré, donc rien
         * qui se lise comme un bot : la teinte `12` est le gris→noir de la palette, et l'absence de
         * bouche et de marque l'empêche de ressembler à une créature.
         */
        val unknown = BotFace(
            shape = FaceShape.CIRCLE,
            eyes = FaceEyes.ROUND,
            mouth = FaceMouth.NONE,
            mark = FaceMark.NONE,
            colour = 12,
            glance = 0,
            tilt = 0,
        )

        /** Ordre combiné figé de `BotFace.all` : 8 × 5 × 4 × 5 = 800 créatures. */
        private val allFaces: List<BotFace> = FaceShape.seeded.flatMap { shape ->
            FaceEyes.ordered.flatMap { eyes ->
                FaceMouth.ordered.flatMap { mouth ->
                    FaceMark.ordered.map { mark ->
                        BotFace(shape = shape, eyes = eyes, mouth = mouth, mark = mark)
                    }
                }
            }
        }

        /** Le visage qu'un nom donne, avec le FNV-1a 64 bits de la référence sur ses octets UTF-8. */
        fun forName(name: String): BotFace {
            if (name == "default") return main
            val hash = fnv1a(name)
            val given = allFaces[(hash % allFaces.size.toULong()).toInt()]
            return given.copy(
                colour = ((hash shr 20) % seededColors.toULong()).toInt(),
                glance = ((hash shr 28) % 4uL).toInt(),
                tilt = ((hash shr 32) % 13uL).toInt() - 6,
            )
        }

        private fun fnv1a(seed: String): ULong {
            var hash = 14_695_981_039_346_656_037uL
            for (byte in seed.toByteArray(Charsets.UTF_8)) {
                hash = (hash xor (byte.toInt() and 0xFF).toULong()) * 1_099_511_628_211uL
            }
            return hash
        }

        /**
         * Un visage par profil, sans deux créatures identiques : les choix d'abord, puis le visage
         * du nom ou le prochain libre. Les formes sont réparties et les couleurs restent uniques
         * tant que les onze couleurs semées suffisent, exactement comme `faces(for:picked:)`.
         */
        fun faces(names: List<String>, picked: Map<String, BotFace> = emptyMap()): Map<String, BotFace> {
            val allowed = picked.filter { (name, face) -> name == "default" || face.shape != FaceShape.BUBBLE }
            val unique = names.toSet()
            val taken = mutableListOf<BotFace>()
            val out = linkedMapOf<String, BotFace>()

            // Le principal est placé avant les autres : sa bulle et ses deux couleurs sont réservées.
            if ("default" in unique) {
                val face = allowed["default"] ?: main
                taken += if (face.colour == themed || face.colour == codeBlue) face.copy(colour = 0) else face
                out["default"] = face
            }

            unique.filter { it != "default" }.sorted().forEach { name ->
                allowed[name]?.let { face ->
                    taken += face
                    out[name] = face
                }
            }

            unique.filter { it != "default" && it !in allowed.keys }.sorted().forEach { name ->
                val given = forName(name)
                val start = allFaces.indexOfFirst { it.sameFeatures(given) }.coerceAtLeast(0)
                val uses = taken.groupingBy { it.shape }.eachCount()
                val fewest = FaceShape.seeded.minOf { uses[it] ?: 0 }
                var face = given
                for (step in allFaces.indices) {
                    val candidate = allFaces[(start + step) % allFaces.size].copy(
                        colour = given.colour,
                        glance = given.glance,
                        tilt = given.tilt,
                    )
                    if (taken.any { it.sameFeatures(candidate) } || (uses[candidate.shape] ?: 0) != fewest) continue
                    face = candidate
                    break
                }
                if (taken.size < seededColors) {
                    while (taken.any { it.colour == face.colour }) {
                        face = face.copy(colour = (face.colour + 1) % seededColors)
                    }
                }
                taken += face
                out[name] = face
            }
            return out
        }

        /** Lit le code serveur v1, puis les deux anciens formats Android à tirets. */
        fun parse(code: String?): BotFace? {
            if (code == null) return null
            return if (':' in code) parseServer(code) else parseLegacy(code)
        }

        private fun parseServer(code: String): BotFace? {
            val parts = code.split(':')
            if (parts.size != 8 || parts[0] != "1") return null
            val shape = FaceShape.fromServerId(parts[1]) ?: return null
            val eyes = FaceEyes.fromServerId(parts[2]) ?: return null
            val mouth = FaceMouth.fromServerId(parts[3]) ?: return null
            val mark = FaceMark.fromServerId(parts[4]) ?: return null
            val colour = parts[5].toIntOrNull() ?: return null
            val glance = parts[6].toIntOrNull() ?: return null
            val tilt = parts[7].toIntOrNull() ?: return null
            return BotFace(
                shape = shape,
                eyes = eyes,
                mouth = mouth,
                mark = mark,
                colour = Math.floorMod(colour, colorCount),
                glance = Math.floorMod(glance, 4),
                tilt = tilt.coerceIn(-8, 8),
            )
        }

        private fun parseLegacy(code: String): BotFace? {
            val parts = code.split('-')
            return when (parts.size) {
                // `forme-yeux-bouche-couleur`, avant l'arrivée des marques.
                4 -> {
                    val shape = FaceShape.fromId(parts[0]) ?: return null
                    val eyes = FaceEyes.fromId(parts[1]) ?: return null
                    val mouth = FaceMouth.fromId(parts[2]) ?: return null
                    val colour = parts[3].toIntOrNull() ?: return null
                    BotFace(shape, eyes, mouth, colour.coerceIn(0, colorCount - 1))
                }
                // `forme-yeux-bouche-marque-couleur`.
                5 -> {
                    val shape = FaceShape.fromId(parts[0]) ?: return null
                    val eyes = FaceEyes.fromId(parts[1]) ?: return null
                    val mouth = FaceMouth.fromId(parts[2]) ?: return null
                    val mark = FaceMark.fromId(parts[3]) ?: return null
                    val colour = parts[4].toIntOrNull() ?: return null
                    BotFace(shape, eyes, mouth, colour.coerceIn(0, colorCount - 1), mark)
                }
                else -> null
            }
        }
    }
}

/** L'humeur d'un bot : ce qu'il fait, et ce que son visage en montre. */
enum class BotMood {
    /** Au repos : son visage ordinaire. */
    IDLE,

    /** En train de répondre : les yeux s'ouvrent, la bouche se ferme. */
    WORKING,

    /** Il vient de finir : les yeux sourient. */
    PLEASED,

    /** Il attend une réponse : sourcils rapprochés, bouche plate. */
    WAITING,

    /** Il écoute : attentif — sourcils hauts, regard vers vous, paupières ouvertes. */
    LISTENING,

    /** Il réfléchit : sourcils rapprochés, paupières basses, regard de côté. */
    THINKING,

    /** Quelque chose s'est mal passé : sourcils froncés, moue. */
    ERROR,
}

/**
 * Les micro-expressions qu'une humeur pose sur un visage, en plus de ses yeux et de sa bouche.
 *
 * Ce sont des **nombres**, pas des dessins : ils se posent sur le visage ordinaire du bot (sourcils
 * de `-1` froncés à `+1` relevés, paupières de `0` à `1` mi-closes) et un regard propre, sans
 * remplacer sa forme ni sa couleur. Un visage au repos (humeur `IDLE`) n'en porte aucun : sa
 * créature ne change pas, et rien ne se dessine.
 */
internal data class BotExpression(
    val brow: Float = 0f,
    val squint: Float = 0f,
    /** Un regard tenu, quand l'état en demande un (`THINKING` voit de côté) ; `null` : celui du visage. */
    val look: Offset? = null,
)

/** Ce que cette humeur ajoute au visage : ses sourcils, ses paupières, son regard. */
internal val BotMood.expression: BotExpression
    get() = when (this) {
        BotMood.IDLE -> BotExpression()
        // Il compose sa réponse : sourcils un peu rapprochés, paupières basses.
        BotMood.WORKING -> BotExpression(brow = -0.30f, squint = 0.10f)
        // Content de lui : sourcils levés, les yeux s'en chargent déjà.
        BotMood.PLEASED -> BotExpression(brow = 0.30f)
        // Il attend : léger doute.
        BotMood.WAITING -> BotExpression(brow = -0.15f)
        // Il écoute : sourcils hauts, regard vers vous.
        BotMood.LISTENING -> BotExpression(brow = 0.45f, squint = -0.08f, look = Offset(0.05f, 0.35f))
        // Il réfléchit : sourcils rapprochés, paupières basses, regard de côté et vers le haut.
        BotMood.THINKING -> BotExpression(brow = -0.45f, squint = 0.20f, look = Offset(0.85f, -0.45f))
        // Mauvaise nouvelle : sourcils froncés.
        BotMood.ERROR -> BotExpression(brow = -0.70f, squint = 0.05f, look = Offset(-0.20f, 0.10f))
    }

/**
 * Un instant d'un visage qui bouge — celui d'Onyx dans la scène vocale : où il regarde, à quel
 * point ses yeux sont fermés, quelle est l'ouverture de sa bouche. Ce qui est `null` reste au
 * visage (`BotPose` de la référence).
 */
data class BotPose(
    /** De -1 à 1 dans chaque sens : la droite et le bas sont positifs. */
    val look: Offset? = null,
    /** 0 ouvert … 1 fermé. */
    val blink: Float = 0f,
    /** 0 … 1 : une bouche qui parle. `null` garde la bouche du visage. */
    val mouth: Float? = null,
    /** Une inclinaison qui bouge ; `null` garde celle du visage. */
    val tilt: Float? = null,
    /** Sourcils : -1 froncés … 0 rien … +1 relevés. Rien ne se dessine à zéro. */
    val brow: Float = 0f,
    /** Paupières : 0 ouvertes … 1 mi-closes (l'œil se referme sans cligner). */
    val squint: Float = 0f,
    /** Respiration : -1 … +1, une lente oscillation verticale du corps. 0 : rien. */
    val breath: Float = 0f,
)

/**
 * Les visages de bots, allumés ou non (`Settings.botFaces`, Réglages → Apparence → « Bot faces »).
 *
 * Éteint, chaque bot — Onyx compris — est son initiale sur un disque de sa couleur, comme
 * `BotAvatar` le fait côté référence quand le réglage est éteint.
 */
val LocalBotFaces = staticCompositionLocalOf { true }

/**
 * Les photos de bots récupérées de Hermes Desktop, par nom de profil
 * (`Settings.botPictures`, Réglages → Apparence → « Pictures from Hermes »).
 *
 * Vide quand le réglage est éteint ou qu'aucune photo n'a été obtenue : le visage est alors dessiné.
 */
val LocalBotPictures = staticCompositionLocalOf<Map<String, ImageBitmap>> { emptyMap() }

/**
 * Le visage d'un bot, dessiné ; `faceCode` l'emporte sur ce que le nom donnerait.
 *
 * L'humeur change son expression sans changer son visage : c'est le même bot, qui travaille, qui
 * vient de finir, ou qui attend. Ses yeux se ferment un court instant toutes les quatre à sept
 * secondes (sauf pendant qu'il travaille), comme la créature de la référence. `contentDescription` dit ce
 * qu'il est à qui ne le voit pas ; sans lui, le visage se décrit lui-même.
 *
 * Un bot dont la photo a été obtenue de Hermes Desktop prend sa photo ; avec « Bot faces » éteint
 * et `showsInitial`, c'est son initiale qui le représente. Les aperçus qui doivent toujours montrer
 * un visage (le choix d'un visage, l'orbe de Onyx) passent `showsInitial = false`.
 */
@Composable
fun BotAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    faceCode: String? = null,
    mood: BotMood = BotMood.IDLE,
    contentDescription: String? = null,
    showsInitial: Boolean = true,
    /** `false` : le visage ne cligne pas — la barre du haut le veut à plat et immobile. */
    blinks: Boolean = true,
    /**
     * Un instant du visage, quand il bouge (la scène vocale) : le regard, le clignement, la
     * bouche, l'inclinaison. `null` : le visage ordinaire, qui cligne tout seul.
     */
    pose: BotPose? = null,
) {
    val picture = LocalBotPictures.current[name]
    if (picture != null) {
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size * 0.9f)
                    .clip(RoundedCornerShape(size * 0.24f)),
            )
        }
        return
    }
    // Le visage de l'humeur **et** ses micro-expressions, tenus ensemble : c'est ce couple qui se
    // fond d'un état à l'autre, pour que le regard et les sourcils ne sautent jamais d'un coup.
    val stage = remember(name, faceCode, mood) { BotStage.face(name, faceCode, mood) }
    val face = stage.face
    val blinking = rememberBlink(pose == null && blinks && mood != BotMood.WORKING)
    val description = contentDescription ?: face.describe()
    val theme = LocalOnyxTheme.current
    // « Bot faces » éteint : le bot est son initiale, sur un disque de sa couleur.
    val initial = if (showsInitial && !LocalBotFaces.current) BotFace.initialFor(name) else null
    // Sans pose, c'est le clignement d'ici (un battement franc) ; avec, c'est l'appelant — la
    // scène vocale — qui donne l'instant du visage.
    val shown = pose ?: BotPose(blink = if (blinking) 1f else 0f)
    // Les transitions d'humeur sont **fondues**, jamais instantanées : l'ancien visage reste dessous
    // et le nouveau se pose par-dessus en 0,35 s ; sous Reduce Motion, la bascule est nette.
    val reduceMotion = LocalReduceMotion.current
    val progress = remember { Animatable(1f) }
    val previous = remember { mutableStateOf(stage) }
    LaunchedEffect(stage, reduceMotion) {
        if (previous.value != stage) {
            if (reduceMotion) {
                progress.snapTo(1f)
                previous.value = stage
            } else {
                progress.snapTo(0f)
                progress.animateTo(1f, OnyxMotion.easeInOut(OnyxMotion.MS_350))
                previous.value = stage
            }
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = description },
    ) {
        when {
            initial != null -> Canvas(Modifier.matchParentSize()) { drawMonogram(face, initial, theme) }
            previous.value != stage -> {
                Canvas(Modifier.matchParentSize()) {
                    drawFace(previous.value.face, previous.value.pose(shown), theme)
                }
                Canvas(Modifier.matchParentSize().alpha(progress.value)) {
                    drawFace(face, stage.pose(shown), theme)
                }
            }
            else -> Canvas(Modifier.matchParentSize()) { drawFace(face, stage.pose(shown), theme) }
        }
    }
}

/** Une image sur trente suffit à un visage qui vit (`TimelineView` de la référence). */
private const val BOT_FRAME_NANOS = 33_333_333L

/**
 * Le temps d'un visage qui vit : les secondes écoulées depuis son entrée à l'écran, écrites à une
 * image sur trente — **la même horloge que la scène vocale** (`VoiceFace`), pour que les deux
 * respirent au même rythme sans se marcher dessus.
 *
 * `null` sous « Réduire les animations » : aucun instant ne passe, donc l'appelant garde la pose de
 * repos ([BotPose]) et le visage reste **figé**, comme le `TimelineView(…, paused: true)` de la
 * référence.
 */
@Composable
fun rememberBotSeconds(): Float? {
    if (LocalReduceMotion.current) return null
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val started = withFrameNanos { it }
        var shown = -1L
        while (true) {
            withFrameNanos { now ->
                val frame = (now - started) / BOT_FRAME_NANOS
                if (frame != shown) {
                    shown = frame
                    seconds = frame / 30f
                }
            }
        }
    }
    return seconds
}

/**
 * Le visage d'une humeur : sa forme, ses yeux, sa bouche ([face]) et les micro-expressions que
 * l'humeur y pose ([expression]). Deux humeurs ne diffèrent que par là, donc deux [BotStage] se
 * composent par fondu — le corps, lui, est identique d'un état à l'autre.
 */
private data class BotStage(val face: BotFace, val expression: BotExpression) {

    /** Le visage ordinaire, porté par les micro-expressions de l'humeur. */
    fun pose(shown: BotPose): BotPose = BotPose(
        look = shown.look ?: expression.look,
        blink = shown.blink,
        mouth = shown.mouth,
        tilt = shown.tilt,
        brow = shown.brow + expression.brow,
        squint = shown.squint + expression.squint,
        breath = shown.breath,
    )

    companion object {
        /** Le visage d'un bot pour une humeur : les yeux et la bouche de l'état, et ses sourcils. */
        fun face(name: String, faceCode: String?, mood: BotMood): BotStage {
            // La résolution est **unique** : `botFace` (voir BotLook.kt) — le même appel pour la
            // barre du haut, le Flux, les listes, les cartes, les salons et la voix.
            val base = botFace(name, faceCode)
            val shaped = when (mood) {
                BotMood.IDLE -> base
                BotMood.WORKING -> base.copy(eyes = FaceEyes.WIDE, mouth = FaceMouth.LINE)
                BotMood.PLEASED -> base.copy(eyes = FaceEyes.HAPPY, mouth = FaceMouth.SMILE)
                BotMood.WAITING -> base.copy(eyes = FaceEyes.SLEEPY, mouth = FaceMouth.LINE)
                // Il écoute : son visage reste le sien, seulement attentif (sourcils hauts).
                BotMood.LISTENING -> base
                // Il réfléchit : paupières basses, bouche plate, regard de côté.
                BotMood.THINKING -> base.copy(eyes = FaceEyes.SLEEPY, mouth = FaceMouth.LINE)
                // Il a échoué : la moue se dessine sous ses yeux.
                BotMood.ERROR -> base.copy(mouth = FaceMouth.OH)
            }
            return BotStage(shaped, mood.expression)
        }
    }
}

/**
 * L'initiale d'un nom, comme `BotAvatar.initial(of:)` : sa première lettre ou son premier chiffre
 * en capitale (« @scout » donne S), sinon son premier caractère non blanc et non ponctuation
 * (un émoji) ; rien pour un nom vide.
 */
fun BotFace.Companion.initialFor(name: String): String {
    val first = name.firstOrNull { it.isLetterOrDigit() }
        ?: name.firstOrNull { !it.isWhitespace() && !isPunctuation(it) }
    return first?.uppercase() ?: ""
}

/** `Char.isPunctuation` de la référence : les catégories `P*` d'Unicode. */
private fun isPunctuation(char: Char): Boolean = when (char.category) {
    CharCategory.CONNECTOR_PUNCTUATION,
    CharCategory.DASH_PUNCTUATION,
    CharCategory.START_PUNCTUATION,
    CharCategory.END_PUNCTUATION,
    CharCategory.INITIAL_QUOTE_PUNCTUATION,
    CharCategory.FINAL_QUOTE_PUNCTUATION,
    CharCategory.OTHER_PUNCTUATION,
    -> true
    else -> false
}

/**
 * Le monogramme d'un bot dont les visages sont éteints : un disque de sa couleur profonde, un
 * liseré sur les teintes claires, et son initiale — encre sombre sur clair, blanc sur foncé.
 */
private fun DrawScope.drawMonogram(face: BotFace, initial: String, theme: ResolvedTheme) {
    val unit = size.minDimension / 100f
    if (unit <= 0f) return
    val (_, deep) = BotFace.gradient(face.colour, theme)
    val ink = Color(0.09f, 0.09f, 0.13f)
    val lightness = deep.linearLuminance()
    val inset = size.minDimension * 0.04f
    val radius = size.minDimension / 2f - inset
    val centre = Offset(size.width / 2f, size.height / 2f)
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(lerp(deep, Color.White, 0.18f), deep),
            start = Offset(size.width * 0.2f, 0f),
            end = Offset(size.width * 0.8f, size.height),
        ),
        radius = radius,
        center = centre,
    )
    if (lightness > 0.62f) {
        drawCircle(ink.copy(alpha = 0.2f), radius = radius, center = centre, style = Stroke(width = maxOf(1f, size.minDimension / 60f)))
    }
    if (initial.isEmpty()) return
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = size.minDimension * 0.46f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            color = (if (lightness > 0.45f) ink else Color.White).toArgb()
        }
        val metrics = paint.fontMetrics
        val baseline = centre.y - (metrics.ascent + metrics.descent) / 2f
        canvas.nativeCanvas.drawText(initial, centre.x, baseline, paint)
    }
}


/**
 * Le clignement : toutes les quatre à sept secondes, les yeux se ferment 120 ms.
 *
 * Le temps d'attente est tiré au sort à chaque fois, pour que plusieurs visages ne clignent pas
 * ensemble. Rien quand `enabled` est faux (un bot qui travaille garde les yeux ouverts).
 */
@Composable
private fun rememberBlink(enabled: Boolean): Boolean {
    var blinking by remember(enabled) { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            blinking = false
            return@LaunchedEffect
        }
        while (true) {
            delay(Random.nextLong(4_000L, 7_001L))
            blinking = true
            delay(120L)
            blinking = false
        }
    }
    return blinking
}

/**
 * Une palette de dessin : la couleur du visage, son dégradé de surface, son encre, et de quoi savoir
 * si le corps est pâle (les yeux reçoivent alors un liseré) ou noir (les traits passent au blanc).
 */
private data class FaceInk(
    val light: Color,
    val deep: Color,
    val ink: Color,
    val features: Color,
    val pale: Boolean,
    val dark: Boolean,
)

/** Où sont les yeux d'une forme, et le point le plus haut de son corps, dans une boîte de 100. */
private data class FaceGeometry(val eyeY: Float, val eyeGap: Float, val top: Offset)

/** Le coin de la queue : le rayon du coin dessiné presque net, en unités de 100. */
private const val tailCorner = 6.5f

/**
 * Dessine la créature dans la boîte de 100 points de la référence : dégradé, queue, regard et inclinaison.
 * Aucun décor propre à Android (notamment aucune ombre) ne vient changer sa silhouette.
 */
private fun DrawScope.drawFace(face: BotFace, pose: BotPose, theme: ResolvedTheme) {
    val unit = size.minDimension / 100f
    if (unit <= 0f) return

    val (light, deep) = BotFace.gradient(face.colour, theme)
    val luminance = deep.linearLuminance()
    val ink = Color(0.09f, 0.09f, 0.13f)
    val palette = FaceInk(
        light = light,
        deep = deep,
        ink = ink,
        features = if (luminance < 0.03f) Color.White.copy(alpha = 0.9f) else ink,
        pale = luminance > 0.62f,
        dark = luminance < 0.03f,
    )

    val body = faceBody(face.shape, theme.bubbleRadius.value)
    val geometry = faceGeometry(face.shape)
    // L'inclinaison suit la pose quand elle en donne une : c'est ainsi que le regard fait pencher
    // la tête (`OnyxVoiceFace.pose` de la référence).
    val tilt = pose.tilt ?: face.tilt.toFloat()

    scale(unit, unit, pivot = Offset.Zero) {
        // La respiration : un lent va-et-vient vertical, avec un rien d'expansion à l'inspiration.
        // Le canevas a une taille fixe, donc rien de tout cela ne bouge dans la mise en page.
        val breath = pose.breath.coerceIn(-1f, 1f)
        translate(0f, -breath * 1.4f) {
            scale(1f + breath * 0.004f, 1f + breath * 0.007f, pivot = Offset(50f, 62f)) {
                // Une antenne fait tenir toute la créature à 86 %, puis le visage penche autour de son centre.
                if (face.mark == FaceMark.ANTENNA) {
                    scale(0.86f, 0.86f, pivot = Offset(50f, 62f)) {
                        rotate(tilt, pivot = Offset(50f, 50f)) {
                            drawCreature(face, body, geometry, palette, pose)
                        }
                    }
                } else {
                    rotate(tilt, pivot = Offset(50f, 50f)) {
                        drawCreature(face, body, geometry, palette, pose)
                    }
                }
            }
        }
    }
}

/** Le corps et tout ce qu'il porte, dans la boîte de 100. */
private fun DrawScope.drawCreature(
    face: BotFace,
    body: Path,
    geometry: FaceGeometry,
    palette: FaceInk,
    pose: BotPose,
) {
    // L'antenne : derrière le bord du corps, une tige et sa bille.
    if (face.mark == FaceMark.ANTENNA) {
        val stalk = Path().apply {
            moveTo(geometry.top.x + 4f, geometry.top.y + 4f)
            quadraticTo(geometry.top.x + 9f, geometry.top.y - 1f, geometry.top.x + 9f, geometry.top.y - 9f)
        }
        drawPath(stalk, palette.deep, style = Stroke(width = 3.6f, cap = StrokeCap.Round))
        drawCircle(palette.deep, radius = 4.5f, center = Offset(geometry.top.x + 9f, geometry.top.y - 10.5f))
    }

    // Le corps, dans le dégradé de surface.
    drawPath(
        body,
        Brush.linearGradient(
            colors = listOf(palette.light, palette.deep),
            start = Offset(18f, 12f),
            end = Offset(84f, 90f),
        ),
    )
    // Le blanc ne se lit que sur une page teintée : un liseré le détache.
    if (face.colour == BotFace.white && face.shape != FaceShape.BUBBLE) {
        drawPath(body, palette.ink.copy(alpha = 0.2f), style = Stroke(width = 1.6f))
    }

    // L'étincelle, une petite étoile à quatre branches en haut à droite.
    if (face.mark == FaceMark.SPARK) {
        val centre = Offset(74f, geometry.top.y + 12f)
        val star = Path()
        for (i in 0 until 8) {
            val radius = if (i % 2 == 0) 7.5f else 2.2f
            val angle = i * Math.PI / 4.0 - Math.PI / 2.0
            val x = centre.x + (radius * cos(angle)).toFloat()
            val y = centre.y + (radius * sin(angle)).toFloat()
            if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        drawPath(star, if (palette.pale) palette.ink.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.92f))
    }

    // Le reflet : une tache de lumière en haut à gauche, découpée aux bords du corps.
    clipPath(body) {
        rotate(degrees = -28f, pivot = Offset(geometry.top.x - 17f, geometry.top.y + 13f)) {
            drawOval(
                color = Color.White.copy(alpha = 0.28f),
                topLeft = Offset(geometry.top.x - 26f, geometry.top.y + 8f),
                size = Size(18f, 10f),
            )
        }
    }

    drawEyes(face, geometry, palette, pose)
    // Les sourcils : au-dessus des yeux, ils ne se dessinent que quand l'humeur en demande
    // (un visage au repos n'en porte pas, donc sa créature reste exactement celle d'avant).
    drawBrows(geometry, palette, pose.brow)

    // Les marques du bas : les taches et le fard, sous les yeux.
    val cheekY = geometry.eyeY + 10f
    val leftX = 51f - geometry.eyeGap
    val rightX = 51f + geometry.eyeGap
    when (face.mark) {
        FaceMark.FRECKLES -> {
            for (baseX in listOf(leftX, rightX)) {
                for ((dx, dy) in listOf(-3.0f to 0.0f, 0.5f to 2.2f, 3.6f to -0.4f)) {
                    drawCircle(
                        color = if (palette.dark) Color.White.copy(alpha = 0.55f) else palette.deep.copy(alpha = 0.9f),
                        radius = 0.9f,
                        center = Offset(baseX + dx, cheekY + dy),
                    )
                }
            }
        }
        FaceMark.BLUSH -> {
            for (baseX in listOf(leftX - 3f, rightX + 3f)) {
                drawOval(
                    color = Color(1f, 0.45f, 0.55f).copy(alpha = 0.45f),
                    topLeft = Offset(baseX - 5f, cheekY - 2.4f),
                    size = Size(10f, 4.8f),
                )
            }
        }
        else -> Unit
    }

    drawMouth(face, geometry, palette, pose.mouth)
}

/**
 * Les yeux de la référence : blancs, pupilles dirigées par `glance` — ou par la pose, quand le visage
 * suit quelque chose —, et clignement par compression.
 */
private fun DrawScope.drawEyes(face: BotFace, geometry: FaceGeometry, palette: FaceInk, pose: BotPose) {
    val left = Offset(51f - geometry.eyeGap, geometry.eyeY)
    val right = Offset(51f + geometry.eyeGap, geometry.eyeY)
    val glances = listOf(
        Offset(-1.8f, 0.6f),
        Offset(1.8f, 0.6f),
        Offset(-1.4f, -1.2f),
        Offset(1.4f, -1.2f),
    )
    // `pose.look` va de -1 à 1 ; les pupilles du visage vont à ±1,9 sur ±1,4 (la référence).
    val look = pose.look
        ?.let { Offset(it.x.coerceIn(-1f, 1f) * 1.9f, it.y.coerceIn(-1f, 1f) * 1.4f) }
        ?: glances[Math.floorMod(face.glance, 4)]
    // Le clignement ferme l'œil ; les paupières (`squint`) le rétrécissent sans le fermer —
    // c'est la demi-paupière de l'attention et de la réflexion.
    val open = (1f - pose.blink.coerceIn(0f, 1f) * 0.9f) * (1f - pose.squint.coerceIn(0f, 1f) * 0.35f)

    when (face.eyes) {
        FaceEyes.ROUND -> {
            drawEye(left, 7.4f, 3.8f, palette, look, open)
            drawEye(right, 7.4f, 3.8f, palette, look, open)
        }

        FaceEyes.WIDE -> {
            drawEye(left, 9.2f, 4.2f, palette, look, open)
            drawEye(right, 9.2f, 4.2f, palette, look, open)
        }

        FaceEyes.SPARKLE -> {
            drawEye(left, 7.8f, 4.6f, palette, look, open, sparkle = true)
            drawEye(right, 7.8f, 4.6f, palette, look, open, sparkle = true)
        }

        FaceEyes.MISMATCHED -> {
            drawEye(left, 8.6f, 4.4f, palette, look, open)
            drawEye(Offset(right.x + 1f, right.y + 1f), 6f, 3.2f, palette, look, open)
        }

        FaceEyes.SLEEPY -> {
            for (centre in listOf(left, right)) {
                drawEye(centre, 7.2f, 3.6f, palette, look, open)
                // La paupière : le haut de l'œil dans la couleur du corps, son bord d'un trait.
                val lid = Path().apply {
                    addOval(Rect(centre.x - 7.4f, centre.y - 7.8f, centre.x + 7.4f, centre.y + 7.8f))
                }
                clipPath(lid) {
                    drawRect(palette.deep, topLeft = Offset(centre.x - 8f, centre.y - 9f), size = Size(16f, 8.4f))
                }
                drawLine(
                    palette.ink,
                    Offset(centre.x - 7f, centre.y - 0.6f),
                    Offset(centre.x + 7f, centre.y - 0.6f),
                    strokeWidth = 1.8f,
                    cap = StrokeCap.Round,
                )
            }
        }

        FaceEyes.HAPPY -> {
            for (centre in listOf(left, right)) {
                val arc = Path().apply {
                    moveTo(centre.x - 6f, centre.y + 2.5f)
                    quadraticTo(centre.x, centre.y - 7f, centre.x + 6f, centre.y + 2.5f)
                }
                drawPath(arc, palette.ink, style = Stroke(width = 3.4f, cap = StrokeCap.Round))
            }
        }

        FaceEyes.LINE -> {
            // Ancien œil Android, uniquement conservé pour les codes historiques et les humeurs.
            for (centre in listOf(left, right)) {
                drawLine(
                    palette.features,
                    Offset(centre.x - 6f, centre.y),
                    Offset(centre.x + 6f, centre.y),
                    strokeWidth = 4.6f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * Les sourcils, au-dessus des yeux : de `-1` froncés (les bouts descendent vers le nez) à `+1`
 * relevés (les deux bouts montent, l'intérieur plus haut). Rien n'est tracé près de zéro, pour
 * qu'un visage ordinaire reste **exactement** celui de la référence.
 */
private fun DrawScope.drawBrows(geometry: FaceGeometry, palette: FaceInk, brow: Float) {
    val b = brow.coerceIn(-1f, 1f)
    if (Math.abs(b) < 0.02f) return
    val y = geometry.eyeY - 9f
    listOf(-1f, 1f).forEach { side ->
        val eyeX = 51f + side * geometry.eyeGap
        val outerX = eyeX + side * 6f
        val innerX = eyeX - side * 3.5f
        val outerY = y - b * 1.8f
        val innerY = y - b * 3.4f
        val browPath = Path().apply {
            moveTo(outerX, outerY)
            quadraticTo((outerX + innerX) / 2f, minOf(outerY, innerY) - 1.8f, innerX, innerY)
        }
        drawPath(browPath, palette.features, style = Stroke(width = 2.6f, cap = StrokeCap.Round))
    }
}

/** Un œil : un blanc, sa pupille dirigée, et éventuellement la lumière d'un œil étincelant. */
private fun DrawScope.drawEye(
    centre: Offset,
    radius: Float,
    pupil: Float,
    palette: FaceInk,
    look: Offset,
    open: Float,
    sparkle: Boolean = false,
) {
    val top = centre.y - radius * 1.06f * open
    val white = Path().apply {
        addOval(Rect(centre.x - radius, top, centre.x + radius, top + radius * 2.12f * open))
    }
    drawPath(white, Color.White)
    if (palette.pale) drawPath(white, palette.ink.copy(alpha = 0.32f), style = Stroke(width = 1.4f))
    clipPath(white) {
        val pupilX = centre.x + look.x * radius / 7f
        val pupilY = centre.y + look.y * radius / 7f
        drawCircle(palette.ink, radius = pupil, center = Offset(pupilX, pupilY))
        if (sparkle) {
            drawCircle(
                Color.White,
                radius = pupil * 0.35f,
                center = Offset(pupilX + pupil * 0.5f, pupilY - pupil * 0.4f),
            )
        }
    }
}

/**
 * La bouche, sous les yeux. Avec une pose qui parle, c'est **une bouche qui s'ouvre avec la voix**
 * — un ovale arrondi, large de 7 + 3·ouverture et haut de 1,6 + 8·ouverture, comme la référence ;
 * sinon la bouche du visage.
 */
private fun DrawScope.drawMouth(face: BotFace, geometry: FaceGeometry, palette: FaceInk, talking: Float? = null) {
    val y = geometry.eyeY + if (face.mark == FaceMark.FRECKLES || face.mark == FaceMark.BLUSH) 15f else 13f
    if (talking != null) {
        val open = talking.coerceIn(0f, 1f)
        if (open > 0.03f) {
            val w = 7f + open * 3f
            val h = 1.6f + open * 8f
            drawRoundRect(
                color = palette.features,
                topLeft = Offset(51f - w / 2f, y - 1f),
                size = Size(w, h),
                cornerRadius = CornerRadius(minOf(w, h) / 2f, minOf(w, h) / 2f),
            )
        }
        return
    }
    when (face.mouth) {
        FaceMouth.NONE -> Unit

        FaceMouth.SMILE -> {
            val smile = Path().apply {
                moveTo(46f, y)
                quadraticTo(51f, y + 5f, 56f, y)
            }
            drawPath(smile, palette.features, style = Stroke(width = 2.6f, cap = StrokeCap.Round))
        }

        FaceMouth.LINE -> drawLine(
            palette.features,
            Offset(40f, y),
            Offset(62f, y),
            strokeWidth = 2.6f,
            cap = StrokeCap.Round,
        )

        FaceMouth.OPEN -> drawOval(
            palette.features,
            topLeft = Offset(51f - 4.6f, y - 3f),
            size = Size(9.2f, 8f),
        )

        FaceMouth.GRIN -> {
            val grin = Path().apply {
                moveTo(45f, y - 1f)
                quadraticTo(48f, y + 4f, 51f, y)
                quadraticTo(54f, y + 4f, 57f, y - 1f)
            }
            drawPath(grin, palette.features, style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        FaceMouth.OH -> drawOval(
            palette.features,
            topLeft = Offset(51f - 2.6f, y - 1.5f),
            size = Size(5.2f, 6f),
        )
    }
}

/** Où sont les yeux d'une forme, et son sommet, dans la boîte de 100 (comme le `Layout` de la référence). */
private fun faceGeometry(shape: FaceShape): FaceGeometry = when (shape) {
    FaceShape.CIRCLE -> FaceGeometry(44f, 11f, Offset(50f, 2f))
    FaceShape.SQUIRCLE -> FaceGeometry(45f, 12f, Offset(50f, 2f))
    FaceShape.ROUND -> FaceGeometry(44f, 11f, Offset(51f, 14f))
    FaceShape.EGG -> FaceGeometry(47f, 11f, Offset(51f, 10f))
    FaceShape.PILL -> FaceGeometry(47f, 13f, Offset(51f, 24f))
    FaceShape.CAPSULE -> FaceGeometry(36f, 10f, Offset(52f, 9f))
    FaceShape.BURST -> FaceGeometry(45f, 11f, Offset(51f, 10f))
    FaceShape.PICK -> FaceGeometry(38f, 11f, Offset(51f, 18f))
    FaceShape.HEX -> FaceGeometry(45f, 11f, Offset(51f, 8f))
    FaceShape.DIAMOND -> FaceGeometry(46f, 11f, Offset(51f, 9f))
    FaceShape.GHOST -> FaceGeometry(43f, 11f, Offset(51f, 11f))
    FaceShape.SQUARE -> FaceGeometry(45f, 12f, Offset(51f, 12f))
    FaceShape.STAR -> FaceGeometry(50f, 9.5f, Offset(51f, 9f))
    FaceShape.BLOB -> FaceGeometry(46f, 11f, Offset(51f, 12f))
    FaceShape.BUBBLE -> FaceGeometry(45f, 12f, Offset(51f, 13f))
}

/** Le contour du corps d'une forme, dans la boîte de 100. */
private fun faceBody(shape: FaceShape, bubbleRadius: Float = 18f): Path = when (shape) {
    FaceShape.CIRCLE -> Path().apply { addOval(Rect(0f, 0f, 100f, 100f)) }

    FaceShape.SQUIRCLE -> Path().apply {
        addRoundRect(RoundRect(Rect(0f, 0f, 100f, 100f), CornerRadius(28f)))
    }

    FaceShape.ROUND -> corneredPath(left = 17f, right = 85f, top = 14f, bottom = 80f, middle = 47f)

    FaceShape.EGG -> eggPath()

    FaceShape.PILL -> tuckedPath(left = 10f, top = 24f, width = 82f, height = 50f, radius = 25f)

    FaceShape.CAPSULE -> tuckedPath(left = 27f, top = 9f, width = 50f, height = 76f, radius = 25f)

    FaceShape.BURST -> burstPath()

    FaceShape.PICK -> roundedPolygon(
        points = listOf(Offset(8f, 14f), Offset(94f, 14f), Offset(32f, 93f)),
        softness = 0.36f,
        sharp = 2,
        sharpness = 0.08f,
    )

    FaceShape.HEX -> {
        val points = ringPoints(3, Offset(51f, 47f), 39f, 39f)
        roundedPolygon(points, sharp = nearestBottomLeft(points))
    }

    FaceShape.DIAMOND -> roundedPolygon(
        points = listOf(Offset(51f, 7f), Offset(91f, 47f), Offset(45f, 88f), Offset(11f, 47f)),
        sharp = 2,
        sharpness = 0.06f,
    )

    FaceShape.GHOST -> ghostPath()

    FaceShape.SQUARE -> tuckedPath(left = 14f, top = 12f, width = 74f, height = 74f, radius = 13f)

    FaceShape.STAR -> {
        val points = ringPoints(5, Offset(51f, 52f), 45f, 27f)
        roundedPolygon(
            points,
            softness = 0.3f,
            sharp = nearestBottomLeft(points, outerOnly = true),
            sharpness = 0.12f,
        )
    }

    FaceShape.BLOB -> {
        // Sept bosses de tailles différentes, adoucies, avec un coin en bas à gauche.
        val radii = listOf(37f, 33f, 39f, 34f, 38f, 41f, 35f)
        val points = radii.indices.map { i ->
            val angle = Math.toRadians(-90.0 + i * 360.0 / radii.size)
            Offset(51f + (radii[i] * cos(angle)).toFloat(), 49f + (radii[i] * sin(angle)).toFloat())
        }
        roundedPolygon(
            points,
            softness = 0.5f,
            sharp = nearestBottomLeft(points),
            sharpness = 0.12f,
        )
    }

    // La bulle de Onyx : le rectangle inégal du thème, agrandi de 1,45 et rentré en bas à gauche.
    FaceShape.BUBBLE -> tuckedPath(
        left = 12f,
        top = 13f,
        width = 78f,
        height = 74f,
        radius = minOf(bubbleRadius * 1.45f, 29f),
        tail = minOf(5f, bubbleRadius) * 1.45f,
    )
}

/**
 * Un ovale (de `left` à `right`, de `top` à `bottom`, le plus large en `middle`) dont le coin en bas
 * à gauche est la queue, laissée presque nette.
 */
private fun corneredPath(left: Float, right: Float, top: Float, bottom: Float, middle: Float): Path {
    val k = 0.5523f
    val centreX = (left + right) / 2f
    val radiusX = (right - left) / 2f
    val tail = tailCorner
    return Path().apply {
        moveTo(centreX, top)
        cubicTo(centreX + radiusX * k, top, right, middle - (middle - top) * k, right, middle)
        cubicTo(right, middle + (bottom - middle) * k, centreX + radiusX * k, bottom, centreX, bottom)
        lineTo(left + tail, bottom)
        quadraticTo(left, bottom, left, bottom - tail)
        lineTo(left, middle)
        cubicTo(left, middle - (middle - top) * k, centreX - radiusX * k, top, centreX, top)
        close()
    }
}

/** Un rectangle arrondi dont le coin en bas à gauche est la queue, presque nette. */
private fun tuckedPath(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    radius: Float,
    tail: Float = tailCorner,
): Path {
    val right = left + width
    val bottom = top + height
    return Path().apply {
        moveTo(left + radius, top)
        lineTo(right - radius, top)
        arcTo(Rect(right - 2f * radius, top, right, top + 2f * radius), -90f, 90f, false)
        lineTo(right, bottom - radius)
        arcTo(Rect(right - 2f * radius, bottom - 2f * radius, right, bottom), 0f, 90f, false)
        lineTo(left + tail, bottom)
        arcTo(Rect(left, bottom - 2f * tail, left + 2f * tail, bottom), 90f, 90f, false)
        lineTo(left, top + radius)
        arcTo(Rect(left, top, left + 2f * radius, top + 2f * radius), 180f, 90f, false)
        close()
    }
}

/** Un œuf : plus étroit au-dessus qu'en dessous, avec la queue en bas à gauche. */
private fun eggPath(): Path {
    val tail = tailCorner
    return Path().apply {
        moveTo(51f, 10f)
        cubicTo(70f, 10f, 84f, 33f, 84f, 55f)
        cubicTo(84f, 75f, 70f, 86f, 51f, 86f)
        lineTo(19f + tail, 86f)
        quadraticTo(19f, 86f, 19f, 86f - tail)
        lineTo(19f, 55f)
        cubicTo(19f, 33f, 32f, 10f, 51f, 10f)
        close()
    }
}

/** Un fantôme : un dôme au-dessus, des flancs droits, et un ourlet de trois festons. */
private fun ghostPath(): Path = Path().apply {
    moveTo(18f, 50f)
    cubicTo(18f, 28f, 32f, 11f, 51f, 11f)
    cubicTo(70f, 11f, 84f, 28f, 84f, 50f)
    lineTo(84f, 82f)
    quadraticTo(78.5f, 91f, 73f, 82f)
    quadraticTo(67.5f, 74f, 62f, 82f)
    quadraticTo(56.5f, 91f, 51f, 82f)
    quadraticTo(45.5f, 74f, 40f, 82f)
    quadraticTo(34.5f, 91f, 29f, 82f)
    quadraticTo(23f, 76f, 18f, 90f)
    close()
}

/** Un soleil : onze pointes, dont celle en bas à gauche un peu plus longue et plus nette. */
private fun burstPath(): Path {
    val points = ringPoints(11, Offset(51f, 47f), 38f, 31f).toMutableList()
    val tip = nearestBottomLeft(points, outerOnly = true)
    points[tip] = Offset(51f + (points[tip].x - 51f) * 1.14f, 47f + (points[tip].y - 47f) * 1.14f)
    return roundedPolygon(points, sharp = tip, sharpness = 0.04f)
}

/** Une suite de points sur un anneau : le rayon alterne entre `outer` et `inner`. */
private fun ringPoints(count: Int, center: Offset, outer: Float, inner: Float, turn: Double = -90.0): List<Offset> =
    List(count * 2) { i ->
        val radius = if (i % 2 == 0) outer else inner
        val angle = Math.toRadians(turn + i * 180.0 / count)
        Offset(center.x + (radius * cos(angle)).toFloat(), center.y + (radius * sin(angle)).toFloat())
    }

/** L'indice du point le plus proche du coin en bas à gauche. */
private fun nearestBottomLeft(points: List<Offset>, outerOnly: Boolean = false): Int =
    points.indices
        .filter { !outerOnly || it % 2 == 0 }
        .minByOrNull { hypot(points[it].x, 100f - points[it].y) } ?: 0

/**
 * Un polygone aux coins adoucis : chaque coin est coupé par une courbe qui le traverse ; celui de la
 * queue, moins.
 */
private fun roundedPolygon(
    points: List<Offset>,
    softness: Float = 0.22f,
    sharp: Int = -1,
    sharpness: Float = 0.05f,
): Path {
    val path = Path()
    val count = points.size
    if (count < 3) return path
    for (i in 0 until count) {
        val point = points[i]
        val next = points[(i + 1) % count]
        val previous = points[(i + count - 1) % count]
        val soft = if (i == sharp) sharpness else softness
        val a = Offset(point.x + (previous.x - point.x) * soft, point.y + (previous.y - point.y) * soft)
        val b = Offset(point.x + (next.x - point.x) * soft, point.y + (next.y - point.y) * soft)
        if (i == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
        path.quadraticTo(point.x, point.y, b.x, b.y)
    }
    path.close()
    return path
}

/** Luminance linéaire sRGB, comme `Color.Resolved.linearRed/Green/Blue` sur la référence. */
private fun Color.linearLuminance(): Float {
    fun linear(channel: Float): Float =
        if (channel <= 0.04045f) channel / 12.92f
        else Math.pow(((channel + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()

    return 0.2126f * linear(red) + 0.7152f * linear(green) + 0.0722f * linear(blue)
}

/** La même couleur, éclaircie vers le blanc d'une fraction. */
internal fun Color.lighten(fraction: Float): Color = Color(
    red = red + (1f - red) * fraction,
    green = green + (1f - green) * fraction,
    blue = blue + (1f - blue) * fraction,
    alpha = alpha,
)
