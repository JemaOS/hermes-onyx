package com.onyx.app.ui.design

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * **Le rendu de la famille d'ombres du socle** — trois profondeurs, chaînées correctement.
 *
 * La version livrée mettait **deux ombres dans un seul `drop-shadow(...)`** :
 * `drop-shadow(0 14px 30px -16px rgba(0,0,0,.5), 0 3px 8px -4px rgba(0,0,0,.3))`. C'est une
 * **déclaration invalide** — un filtre à deux ombres n'existe pas — donc le filtre calculé vaut
 * `none`, et **les cartes, les bulles et les plaques n'avaient aucune ombre**, ni en clair ni en
 * sombre, alors que le document en décrivait trois. Quarante-deux éléments étaient dans ce cas.
 *
 * Ici, chaque couche est **dessinée séparément** : un rectangle arrondi par couche, chacun avec son
 * décalage, son flou et son étalement. Trois couches = trois dessins, tous rendus. C'est ce que
 * [OnyxSocle.ShadowDepth] interdit de transformer en une seule ombre (chaque couche doit avoir un
 * flou et une opacité non nuls).
 */
fun Modifier.socleShadow(
    depth: OnyxSocle.ShadowDepth,
    corner: Dp,
    dark: Boolean,
): Modifier = drawBehind {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val maxRadius = min(size.width, size.height) / 2f
    val baseRadius = min(corner.toPx(), maxRadius)
    drawIntoCanvas { canvas ->
        depth.layers.forEach { layer ->
            val blurPx = layer.blur.toPx()
            val spreadPx = layer.spread.toPx()
            val dyPx = layer.dy.toPx()
            paint.color = OnyxSocle.shadowInk(layer.tier, dark).toArgb()
            paint.maskFilter =
                BlurMaskFilter(blurPx.coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)
            // La forme de l'ombre : le rectangle du contenu, **étalé** (spread négatif = plus petit),
            // décalé vers le bas. Un CSS `-6px` de spread rétrécit de 6 px de chaque côté.
            val left = -spreadPx
            val top = -spreadPx + dyPx
            val right = size.width + spreadPx
            val bottom = size.height + spreadPx + dyPx
            val radius = (baseRadius + spreadPx).coerceIn(0f, maxRadius)
            canvas.nativeCanvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        }
    }
}

/** La profondeur d'ombre d'une carte de la maison. */
fun cardShadow(dark: Boolean): OnyxSocle.ShadowDepth =
    if (dark) OnyxSocle.ShadowMid else OnyxSocle.ShadowFar
