package com.onyx.app.data.image

/**
 * Combien d'une image on garde en mémoire, et de combien on la réduit.
 *
 * Une photo de téléphone fait couramment 4000×3000, et une image « pour analyse » peut être bien
 * plus grande : 8000×6000 pèse 192 Mo une fois décodée en ARGB_8888. Un `Bitmap` de cette taille
 * n'est pas seulement gourmand — **il ne peut pas être dessiné**. `RecordingCanvas` refuse au-delà
 * d'une centaine de mégaoctets et lève une `RuntimeException` sur le fil principal : le processus
 * meurt, l'app se relance, redessine la même bulle, et remeurt. C'est exactement la boucle
 * « fermeture / réouverture » signalée.
 *
 * Cette classe porte donc la **politique** — pure, sans Android, donc éprouvable hors appareil :
 * le sous-échantillonnage se calcule sur les dimensions annoncées par l'en-tête du fichier
 * (`inJustDecodeBounds`), jamais sur l'image entière, et le résultat doit tenir dans un budget
 * d'octets. Quand aucun plan ne tient, [plan] rend `null` : on préfère une carte d'erreur à un
 * plantage.
 *
 * Le rendu Android vit dans `BoundedImages` (côté app) : il applique ce plan et **revérifie** la
 * taille du `Bitmap` obtenu, parce qu'un fichier peut mentir sur ses dimensions.
 */
object BoundedImage {

    /** Ce qu'on accepte de garder en mémoire pour une vignette ou un aperçu. */
    const val MAX_BYTES: Int = 16 * 1024 * 1024

    /**
     * Le plafond au-delà duquel un `Bitmap` ne peut plus être dessiné.
     *
     * La limite du moteur de rendu est de 100 Mo ; on s'arrête **sous** elle avec une marge, pour
     * qu'aucun chemin ne puisse la franchir même si une dimension est arrondie à la hausse.
     */
    const val MAX_DRAW_BYTES: Long = 96L * 1024 * 1024

    /** Un pixel ARGB_8888 pèse quatre octets. */
    const val BYTES_PER_PIXEL: Int = 4

    /** Un facteur de réduction absurde (65536) signale une entrée incohérente : on refuse. */
    private const val MAX_SAMPLE = 65536

    /** Une image à décoder : le facteur retenu, et ce qui restera après décodage. */
    data class Plan(val sampleSize: Int, val width: Int, val height: Int) {
        val bytes: Long get() = width.toLong() * height.toLong() * BYTES_PER_PIXEL

        /** Vrai si ce plan tient sous le plafond de dessin — donc s'il peut être affiché. */
        val drawable: Boolean get() = canDraw(bytes)
    }

    /** Vrai si un `Bitmap` de ce poids peut être dessiné sans que le moteur lève une exception. */
    fun canDraw(bytes: Long): Boolean = bytes > 0L && bytes <= MAX_DRAW_BYTES

    /**
     * Le plus grand facteur de réduction qui laisse **les deux côtés** au moins à [maxPx].
     *
     * `BitmapFactory` ne sait diviser que par une puissance de deux : on double tant que la moitié
     * restante tient encore la cible. Une source plus petite que la cible garde `1` (aucune
     * réduction), et une dimension nulle rend `1` — l'appelant refusera ensuite le décodage.
     */
    fun sampleSize(width: Int, height: Int, maxPx: Int): Int {
        if (width <= 0 || height <= 0) return 1
        val target = maxPx.coerceAtLeast(1)
        var sample = 1
        while (sample < MAX_SAMPLE &&
            width / (sample * 2) >= target &&
            height / (sample * 2) >= target
        ) {
            sample *= 2
        }
        return sample
    }

    /**
     * Le plan de décodage d'une source de [width]×[height], ou `null` si rien de tenable n'en sort.
     *
     * Deux contraintes, dans cet ordre : la cible en pixels ([maxPx], ce que l'écran demande
     * vraiment) puis le budget d'octets ([maxBytes]). La seconde l'emporte quand la première
     * laisserait un carré de 2048×2048 (16,7 Mo) dépasser le budget : on continue de réduire
     * jusqu'à tenir, plutôt que de rendre une image trop lourde.
     */
    fun plan(
        width: Int,
        height: Int,
        maxPx: Int,
        maxBytes: Int = MAX_BYTES,
    ): Plan? {
        if (width <= 0 || height <= 0) return null
        val budget = if (maxBytes <= 0) MAX_BYTES else maxBytes
        var sample = sampleSize(width, height, maxPx)
        while (true) {
            val scaled = scaled(width, height, sample)
            val bytes = scaled.first.toLong() * scaled.second.toLong() * BYTES_PER_PIXEL
            if (bytes <= budget.toLong()) {
                if (!canDraw(bytes)) return null
                return Plan(sample, scaled.first, scaled.second)
            }
            if (sample >= MAX_SAMPLE) return null
            sample *= 2
        }
    }

    /**
     * Vrai quand le décodage a rendu beaucoup moins de pixels que l'en-tête n'en annonçait.
     *
     * Un JPEG coupé ne lève pas : `BitmapFactory` rend les bandes qu'il a pu lire, et la vignette
     * montrait alors une image à moitié dessinée au lieu de dire qu'elle était illisible. La marge
     * de 20 % laisse passer un arrondi d'une ligne ou une orientation EXIF, pas un fichier qui
     * s'arrête aux deux tiers.
     */
    fun isTruncated(expectedWidth: Int, expectedHeight: Int, actualWidth: Int, actualHeight: Int): Boolean {
        if (expectedWidth <= 0 || expectedHeight <= 0 || actualWidth <= 0 || actualHeight <= 0) return false
        val enoughWidth = actualWidth.toLong() * 100 >= expectedWidth.toLong() * 80
        val enoughHeight = actualHeight.toLong() * 100 >= expectedHeight.toLong() * 80
        return !enoughWidth || !enoughHeight
    }

    /** Les dimensions après réduction, arrondies vers le haut (jamais zéro). */
    fun scaled(width: Int, height: Int, sample: Int): Pair<Int, Int> {
        val divisor = sample.coerceAtLeast(1)
        val w = (width + divisor - 1) / divisor
        val h = (height + divisor - 1) / divisor
        return w.coerceAtLeast(1) to h.coerceAtLeast(1)
    }
}
