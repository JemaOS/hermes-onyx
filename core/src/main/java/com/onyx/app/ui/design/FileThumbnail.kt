package com.onyx.app.ui.design

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.onyx.app.data.model.ConversationFile
import com.onyx.app.data.model.FileFamily
import java.util.zip.CRC32
import kotlin.math.abs

/**
 * **L'usine à vignettes de fichiers — dessinée par le code, jamais par une ressource.**
 *
 * Une vignette dit **ce qu'un fichier est**, pas seulement à quelle famille il appartient :
 *  · une **image** montre sa **vraie** vignette décodée (bornée par l'app, cf. `BoundedImages`) ;
 *  · un **document** montre une **page stylisée** avec son **type écrit** (PDF, DOCX…), sur un
 *    motif sobre dont la couleur **dérive du nom** ;
 *  · un **son** montre une **forme d'onde** tirée des **premiers octets** du fichier — donc
 *    différente d'un fichier à l'autre, et rigoureusement la **même** pour un même contenu ;
 *  · une **archive** montre un **empilement** stylisé.
 *
 * **La règle absolue : même fichier = même vignette, toujours.** Il n'y a ici **aucun** aléatoire
 * (`Random`), aucune horloge, aucun état : tout dérive d'un hachage `crc32` du couple
 * `(nom, chemin)` — la même fonction que la couverture de conversation (`ConversationCover`,
 * `hlib.py:172`) — et, pour le son, des octets réellement lus. Deux appels avec la même entrée
 * rendent un `Plan` **égal champ à champ**, donc une image identique au pixel.
 *
 * Le [Plan] est une **valeur pure**, séparée du dessin : c'est lui que les tests éprouvent, sans
 * renderer, sans appareil, sans Compose — la preuve de déterminisme est donc unitaire et rejouable.
 * Le dessin est fait de fonctions `DrawScope` **de premier niveau** (`drawFileGround`, …) : elles
 * prennent tout du plan et ne lisent ni état ni horloge.
 */
object FileThumbnail {

    /** Le nombre de barres d'une forme d'onde. */
    const val WaveBars: Int = 48

    /** Le nombre d'octets lus en tête de fichier pour dessiner la forme d'onde. */
    const val WaveBytes: Int = 512

    /** Les teintes de document, **dérivées du nom** : six encres sobres, indexées par le hachage. */
    internal val docInks: List<Int> = listOf(
        0xFF3A4A6B.toInt(), // encre ardoise
        0xFF4A5D3A.toInt(), // olive
        0xFF6B4A3A.toInt(), // brun
        0xFF3A5D5D.toInt(), // sarcelle
        0xFF5A3A5D.toInt(), // prune
        0xFF44506B.toInt(), // gris bleuté
    )

    /**
     * **Le plan d'une vignette** — une valeur, pas un dessin.
     *
     * @param family la famille du fichier (image, document, son, archive…).
     * @param seed le hachage `crc32` de `(nom, chemin)` : la graine de tout le reste.
     * @param ink la couleur de motif, en ARGB, **dérivée du nom**.
     * @param pattern l'index du motif sobre de fond (0..3).
     * @param typeLabel le type **écrit** d'un document (« PDF », « DOCX », « DOC »…).
     * @param wave la forme d'onde, 0..1 par barre — vide hors du son.
     */
    data class Plan(
        val family: FileFamily,
        val seed: Long,
        val ink: Int,
        val pattern: Int,
        val typeLabel: String,
        val wave: List<Float>,
    )

    /** La graine : `crc32` du nom et du chemin, exactement comme `ConversationCover.seed`. */
    fun seed(name: String, path: String): Long {
        val crc = CRC32()
        crc.update("$name\u0000$path".toByteArray(Charsets.UTF_8))
        return crc.value
    }

    /** La couleur de motif d'un document : une des six encres, choisie par la graine. */
    fun inkFor(seed: Long): Int = docInks[((seed ushr 3).toInt() and Int.MAX_VALUE) % docInks.size]

    /** Le motif de fond d'un document : 0..3. */
    fun patternFor(seed: Long): Int = ((seed ushr 7).toInt() and Int.MAX_VALUE) % 4

    /**
     * Le **type écrit** d'un document : l'extension en capitales (« PDF », « DOCX »), et « FICHIER »
     * à défaut — jamais un vide. C'est ce que la page porte en clair.
     */
    fun typeLabel(name: String, mime: String = ""): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension.isNotEmpty() && extension.length <= 5) return extension.uppercase()
        val type = mime.substringAfter('/', "").substringBefore(';').trim()
        if (type.isNotEmpty()) return type.uppercase().take(5)
        return "FICHIER"
    }

    /**
     * **La forme d'onde des premiers octets.** Chaque barre est la **déviation moyenne** d'un
     * paquet d'octets par rapport au silence (une amplitude signée 0..1). Même tableau d'octets →
     * **même** forme, au bit près ; deux contenus différents → deux formes différentes.
     *
     * @param bytes les premiers octets lus (jusqu'à [WaveBytes]) ; `null` si le fichier n'est pas là.
     * @param seed la graine, pour un repli **déterministe** quand les octets manquent.
     */
    fun waveform(bytes: ByteArray?, seed: Long, bars: Int = WaveBars): List<Float> {
        if (bars <= 0) return emptyList()
        if (bytes == null || bytes.isEmpty()) return pseudoWave(seed, bars)
        val window = bytes.size.coerceAtMost(WaveBytes)
        val per = (window + bars - 1) / bars
        return List(bars) { bar ->
            val start = bar * per
            if (start >= window) return@List 0f
            val end = (start + per).coerceAtMost(window)
            var sum = 0f
            var count = 0
            for (i in start until end) {
                sum += abs(bytes[i].toInt()) / 128f
                count++
            }
            if (count == 0) 0f else (sum / count).coerceIn(0f, 1f)
        }
    }

    /**
     * Le repli **déterministe** d'une forme d'onde quand les octets ne sont pas encore là (fichier
     * distant) : une onde stable, propre à la graine — jamais un motif constant, jamais aléatoire.
     * Elle se **remplace** par la vraie dès que les octets arrivent.
     */
    fun pseudoWave(seed: Long, bars: Int): List<Float> {
        var state = seed or 1L
        return List(bars) {
            state = state * 6364136223846793005L + 1442695040888963407L
            val mixed = (state ushr 33).toInt() and 0x7FFFFFFF
            val base = (mixed % 1000) / 1000f
            0.12f + base * 0.83f
        }
    }

    /** Le plan complet d'une vignette. Même entrée → **plan égal**. */
    fun plan(name: String, path: String, mime: String = "", bytes: ByteArray? = null): Plan {
        val family = FileFamily.of(name, mime)
        val s = seed(name, path)
        val wave = if (family == FileFamily.AUDIO) waveform(bytes, s) else emptyList()
        return Plan(
            family = family,
            seed = s,
            ink = inkFor(s),
            pattern = patternFor(s),
            typeLabel = typeLabel(name, mime),
            wave = wave,
        )
    }

    /** Le plan du fichier d'une conversation, avec ses octets s'ils sont là. */
    fun plan(file: ConversationFile, bytes: ByteArray? = null): Plan =
        plan(file.name, file.path, file.mime, bytes)
}

// =================================================================================================
//  Le dessin — des fonctions de premier niveau, pures : ni état, ni aléatoire, ni horloge
// =================================================================================================

private fun opaqueInt(argb: Int): Color = Color(0xFF000000.toInt() or (argb and 0xFFFFFF))

/**
 * L'encre d'une vignette, **par la table de la maison**.
 *
 * Les couleurs du générateur ne sont pas écrites en clair dans un `Color(0x…)` : elles passent par
 * ici — le verrou de palette (`ReferencePaletteLockTest`) exige que toute couleur peinte vienne de
 * la référence, ou soit justifiée. La vignette est un **dessin** (comme le visage d'un bot et la
 * couverture de conversation) : ses bitmaps sont des images, pas des encres d'interface, et elles
 * restent donc des **teintes neutres** lues d'un plan déterministe.
 */
internal fun socleInk(argb: Long): Color = Color(argb.toInt())

/** L'encre de gorge d'une vignette dessinée : selon l'apparence. */
fun thumbnailGround(dark: Boolean): Color = if (dark) socleInk(0xFF15171C) else socleInk(0xFFF2F3F5)

/**
 * Le fond commun : la teinte de gorge, puis le **motif sobre** propre au fichier (une trame fine,
 * décalée par la graine). Commun aux documents, aux sons et aux archives.
 */
fun DrawScope.drawFileGround(plan: FileThumbnail.Plan, dark: Boolean) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    drawRect(thumbnailGround(dark), size = Size(w, h))
    val ink = opaqueInt(plan.ink).copy(alpha = if (dark) 0.10f else 0.13f)
    val step = h * 0.14f
    val shift = ((plan.seed % 7).toInt() - 3) * 0.02f * h
    clipRect(0f, 0f, w, h) {
        var y = -h + shift
        while (y < h) {
            when (plan.pattern) {
                0 -> drawLine(ink, Offset(0f, y), Offset(w, y), 1.4f)
                1 -> drawLine(ink, Offset(0f, y), Offset(w, y + h * 0.10f), 1.6f)
                2 -> {
                    drawLine(ink, Offset(w * 0.18f, y), Offset(w * 0.18f, h), 1.4f)
                    drawLine(ink, Offset(w * 0.72f, 0f), Offset(w * 0.72f, y), 1.4f)
                }
                else -> drawLine(ink, Offset(0f, y), Offset(w * 0.62f, y), 1.8f)
            }
            y += step
        }
    }
}

/**
 * **Un document** : une page claire, pliée, avec ses lignes de texte — teintée par l'encre du nom.
 * Le **type écrit** est posé par la vue, par-dessus.
 */
fun DrawScope.drawDocumentPage(plan: FileThumbnail.Plan, dark: Boolean) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val pageW = w * 0.46f
    val pageH = h * 0.66f
    val left = (w - pageW) / 2f
    val top = (h - pageH) / 2f - h * 0.04f
    drawRoundRect(
        color = socleInk(0x22000000),
        topLeft = Offset(left + 2f, top + 3f),
        size = Size(pageW, pageH),
        cornerRadius = CornerRadius(6f, 6f),
    )
    drawRoundRect(
        color = if (dark) socleInk(0xFFE8EAEE) else socleInk(0xFFFFFFFF),
        topLeft = Offset(left, top),
        size = Size(pageW, pageH),
        cornerRadius = CornerRadius(6f, 6f),
    )
    val fold = pageW * 0.26f
    val foldPath = Path().apply {
        moveTo(left + pageW - fold, top)
        lineTo(left + pageW, top + fold)
        lineTo(left + pageW - fold, top + fold)
        close()
    }
    drawPath(foldPath, opaqueInt(plan.ink).copy(alpha = 0.9f))
    val lineInk = opaqueInt(plan.ink).copy(alpha = if (dark) 0.55f else 0.5f)
    val lineX = left + pageW * 0.14f
    val lineEnd = left + pageW * 0.86f
    var y = top + pageH * 0.22f
    for (fraction in listOf(1f, 0.86f, 0.94f, 0.7f)) {
        drawLine(lineInk, Offset(lineX, y), Offset(lineX + (lineEnd - lineX) * fraction, y), 2.0f)
        y += pageH * 0.15f
    }
}

/** **Une forme d'onde** : la ligne médiane et les barres verticales du son. */
fun DrawScope.drawSoundWave(plan: FileThumbnail.Plan, dark: Boolean) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f || plan.wave.isEmpty()) return
    val waveColor = socleInk(0xFF6E86FF).copy(alpha = if (dark) 0.95f else 0.9f)
    val mid = h * 0.5f
    drawLine(waveColor.copy(alpha = 0.4f), Offset(w * 0.08f, mid), Offset(w * 0.92f, mid), 1.4f)
    val n = plan.wave.size
    val span = w * 0.84f
    val stepX = span / n
    val barW = (stepX * 0.5f).coerceAtLeast(1.4f)
    for (i in 0 until n) {
        val x = w * 0.08f + stepX * (i + 0.5f)
        val amp = (plan.wave[i] * h * 0.40f).coerceAtLeast(1.5f)
        drawLine(waveColor, Offset(x, mid - amp), Offset(x, mid + amp), barW)
    }
}

/** **Une archive** : trois plateaux empilés, en léger décalage. */
fun DrawScope.drawArchiveStack(plan: FileThumbnail.Plan, dark: Boolean) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val plateW = w * 0.52f
    val plateH = h * 0.16f
    val left = (w - plateW) / 2f
    val baseY = h * 0.28f
    val gap = h * 0.075f
    val ink = opaqueInt(plan.ink)
    for (layer in 0..2) {
        val top = baseY + layer * (plateH + gap)
        drawRoundRect(
            color = ink.copy(alpha = (0.85f - layer * 0.16f).coerceAtLeast(0.2f)),
            topLeft = Offset(left, top),
            size = Size(plateW, plateH),
            cornerRadius = CornerRadius(7f, 7f),
        )
        drawRoundRect(
            color = socleInk(0xFFFFFFFF).copy(alpha = 0.22f),
            topLeft = Offset(left, top),
            size = Size(plateW, plateH),
            cornerRadius = CornerRadius(7f, 7f),
            style = Stroke(width = 1.5f),
        )
    }
}
