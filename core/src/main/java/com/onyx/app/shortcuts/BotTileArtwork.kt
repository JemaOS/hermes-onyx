package com.onyx.app.shortcuts

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.onyx.app.ui.design.BotFace
import com.onyx.app.ui.design.botFace
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * Les tuiles du menu d'appui long, **dessinées en code** — celles des bots.
 *
 * Les trois raccourcis d'action sont des ressources vectorielles (`ic_shortcut_ask`,
 * `ic_shortcut_talk`, `ic_shortcut_note`), dessinées une fois pour toutes. La tuile d'un bot ne
 * peut pas l'être : elle dépend du bot — sa **couleur** vient de son visage (`BotFace.gradient`),
 * celle que l'app lui donne partout ailleurs, et son libellé du roster.
 *
 * **Forme** : le même carré à coins arrondis que les tuiles d'action (rayon 22 % du côté), plein
 * cadre, avec l'initiale du bot au centre — à l'intérieur du disque de sûreté
 * ([SAFE_RADIUS_FRACTION]), donc lisible dans les deux cas : le lanceur qui garde la forme voit la
 * tuile ; celui qui impose un masque circulaire rogne la tuile au cercle sans toucher l'initiale.
 *
 * **L'avatar** : le visage d'un bot est un dessin Compose (`BotAvatar`, un `Canvas`), qui n'existe
 * pas hors composition — on ne peut donc pas le peindre dans un `Bitmap` ici. La tuile porte la
 * **photo** du bot quand le roster en fournit une (« Pictures from Hermes », [photoTile]), et son
 * **initiale** sur sa couleur sinon. C'est la variante retenue, et elle est dite.
 *
 * Même famille d'objets que les images du poste Auto (`AutoArtwork`) : des `Bitmap` dessinés en
 * code, partagés par les aperçus Compose et les icônes des services hors écran.
 */
object BotTileArtwork {

    /** La taille de la tuile publiée : l'icône d'un raccourci est demandée en `dp`, pas en px. */
    const val SIZE = 192

    /** Le rayon des coins : 22 % du côté, la forme des trois tuiles d'action. */
    const val CORNER_FRACTION = 0.22f

    /**
     * Le disque de sûreté du glyphe : 72 % du rayon du cercle inscrit.
     *
     * C'est la contrainte qui tient le dessin lisible **dans les deux cas** — un lanceur qui garde
     * la forme et un lanceur qui impose un masque circulaire. Ce qui doit rester visible (une
     * initiale, un glyphe) tient dans ce disque, donc le masque ne le rogne jamais ; la tuile,
     * elle, est pleine cadre et se laisse rogner sans que ça se voie.
     */
    const val SAFE_RADIUS_FRACTION = 0.72f

    /** L'encre d'une tuile claire (un bot blanc reçoit une encre sombre, sinon illisible). */
    const val DARK_INK = 0xFF101014.toInt()

    /** L'encre d'une tuile profonde. */
    const val LIGHT_INK = 0xFFFFFFFF.toInt()

    private val paint by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }

    /**
     * L'initiale d'un bot, sur sa tuile.
     *
     * La première lettre ou le premier chiffre de son libellé, en capitale — donc `@qa` donne `Q`
     * et `- scout` donne `S`, pas un signe de ponctuation. Un libellé qui n'en porte aucun (`***`)
     * garde son premier caractère visible, et un libellé vide ne laisse pas la tuile nue : elle
     * porte un point.
     */
    fun initialFor(label: String): String {
        val trimmed = label.trim()
        val pick = trimmed.firstOrNull { it.isLetterOrDigit() } ?: trimmed.firstOrNull()
        return pick?.uppercase() ?: "•"
    }

    /**
     * L'encre lisible sur un fond donné : la luminance relative du fond décide.
     *
     * Un visage peut être **blanc** (`BotFace.white`, `#DBDBDB` au profond) : une initiale blanche
     * y disparaîtrait, exactement comme le liseré blanc d'un visage blanc. Le seuil est à 0,55 de
     * luminance (autour du blanc cassé `#D6D6D6`).
     */
    fun inkOn(argb: Int): Int = if (luminance(argb) > 0.55f) DARK_INK else LIGHT_INK

    /** La luminance relative d'une couleur (même calcul que le plancher de contraste de l'app). */
    fun luminance(argb: Int): Float {
        fun channel(value: Int): Float {
            val v = value / 255f
            return if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /** La couleur profonde du visage d'un bot : celle de sa tuile, comme partout dans l'app. */
    fun botColour(name: String, faceCode: String?, theme: ResolvedTheme): Int =
        BotFace.gradient(botFace(name, faceCode).colour, theme).second.toArgb()

    /** La couleur claire du même visage : le haut du dégradé de la tuile. */
    fun botLightColour(name: String, faceCode: String?, theme: ResolvedTheme): Int =
        BotFace.gradient(botFace(name, faceCode).colour, theme).first.toArgb()

    /**
     * La tuile d'un bot : son dégradé, son initiale.
     *
     * Le dégradé est celui de son visage — la **même** couleur que son aura, ses bulles et son rond
     * dans l'app : un bot se reconnaît à sa tuile comme au reste.
     */
    fun botTile(
        name: String,
        faceCode: String?,
        label: String,
        theme: ResolvedTheme,
        size: Int = SIZE,
    ): Bitmap {
        val light = botLightColour(name, faceCode, theme)
        val deep = botColour(name, faceCode, theme)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val side = size.toFloat()
        val corners = RectF(0f, 0f, side, side)
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, side, side, light, deep, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(corners, side * CORNER_FRACTION, side * CORNER_FRACTION, paint)
        paint.shader = null
        drawInitial(canvas, initialFor(label), side, inkOn(deep))
        return bitmap
    }

    /**
     * La tuile d'un bot **photographié** : sa photo, dans la même forme, cerclée de sa couleur.
     *
     * Le cerne garde la couleur du bot même quand la photo, elle, n'en dit rien — c'est ce qui
     * fait qu'une tuile reste « celle de ce bot » comme les autres.
     */
    fun photoTile(photo: Bitmap, deep: Int, size: Int = SIZE): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val side = size.toFloat()
        val radius = side * CORNER_FRACTION
        val corners = RectF(0f, 0f, side, side)
        val clip = Path().apply { addRoundRect(corners, radius, radius, Path.Direction.CW) }
        // La photo est recadrée au carré par le centre, puis rognée aux coins de la tuile.
        val edge = minOf(photo.width, photo.height)
        val source = android.graphics.Rect(
            (photo.width - edge) / 2,
            (photo.height - edge) / 2,
            (photo.width + edge) / 2,
            (photo.height + edge) / 2,
        )
        val saved = canvas.save()
        canvas.clipPath(clip)
        paint.style = Paint.Style.FILL
        paint.shader = null
        canvas.drawBitmap(photo, source, corners, paint)
        canvas.restoreToCount(saved)
        // Le cerne : la couleur du bot, sur le bord de la tuile.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = side * 0.055f
        paint.color = deep
        canvas.drawRoundRect(corners, radius, radius, paint)
        return bitmap
    }

    /** L'initiale, centrée dans le carré de sûreté. */
    private fun drawInitial(canvas: Canvas, initial: String, side: Float, ink: Int) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = ink
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textSize = side * 0.42f
        paint.textAlign = Paint.Align.CENTER
        val middle = side / 2f
        // `y` du dessin d'un texte = sa ligne de base : on la descend de la moitié de la hauteur
        // d'x pour que l'initiale soit **centrée sur la tuile**, et non posée au-dessus.
        val metrics = paint.fontMetrics
        val baseline = middle - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(initial, middle, baseline, paint)
    }
}
