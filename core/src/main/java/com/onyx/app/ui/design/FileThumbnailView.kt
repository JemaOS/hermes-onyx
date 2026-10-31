package com.onyx.app.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.onyx.app.data.model.FileFamily
import com.onyx.app.ui.localization.BasicText

/**
 * **La vue d'une vignette de fichier.** Elle compose le [FileThumbnail.Plan] (dessiné par le code)
 * et, pour une image, la **vraie vignette décodée** que la ligne lui passe.
 *
 * Le **type écrit** d'un document et la **pastille de famille** sont posés ici, par-dessus le
 * dessin : c'est le détail signature de l'écran Fichiers — « la pastille de type sur la vignette et
 * le compte sous le mot ».
 */
@Composable
fun FileThumbnailView(
    plan: FileThumbnail.Plan,
    dark: Boolean,
    modifier: Modifier = Modifier,
    image: ImageBitmap? = null,
    /**
     * Le coin de la vignette. **14 dp par défaut** — le coin de champ du socle
     * ([OnyxSocle.RadiusField]) ; une carte à vignettes le porte à **0** quand la vignette est
     * **plein cadre dans le haut de la carte** : la carte découpe alors ses propres coins
     * ([OnyxSocle.RadiusCard]) et la vignette ne redessine pas un second arrondi par-dessus.
     */
    corner: androidx.compose.ui.unit.Dp = OnyxSocle.RadiusField,
) {
    Box(modifier = modifier.clip(RoundedCornerShape(corner))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawFileGround(plan, dark)
            when (plan.family) {
                FileFamily.DOCUMENT -> drawDocumentPage(plan, dark)
                FileFamily.AUDIO -> drawSoundWave(plan, dark)
                FileFamily.ARCHIVE -> drawArchiveStack(plan, dark)
                else -> Unit
            }
        }
        if (image != null && (plan.family == FileFamily.IMAGE || plan.family == FileFamily.VIDEO)) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // La pastille de type, en bas à gauche, sur la vignette.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 8.dp)
                .background(socleInk(0xCC0B0E14), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            BasicText(
                text = badgeLabel(plan.family, plan.typeLabel),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.06.em,
                    fontFamily = FontFamily.SansSerif,
                ),
                maxLines = 1,
            )
        }
    }
}

/** Le mot de la pastille : la famille, ou le type écrit pour un document. */
private fun badgeLabel(family: FileFamily, typeLabel: String): String = when (family) {
    FileFamily.IMAGE -> "IMAGE"
    FileFamily.VIDEO -> "VIDÉO"
    FileFamily.AUDIO -> "AUDIO"
    FileFamily.ARCHIVE -> "ARCHIVE"
    FileFamily.DOCUMENT -> typeLabel
    FileFamily.OTHER, FileFamily.ALL -> typeLabel
}
