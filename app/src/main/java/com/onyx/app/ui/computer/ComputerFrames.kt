package com.onyx.app.ui.computer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.onyx.app.data.net.ComputerBridgeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Le décodeur d'images de l'aperçu — **hors du fil principal**, et sans faire la queue.
 *
 * Le premier jet décodait le JPEG **pendant la composition** : chaque image passait par le fil de
 * l'interface, mesurée sur le chemin du dessin, et une image en retard retardait tout ce qui
 * suivait — le doigt, le défilement, la barre. Trois règles tiennent ce fichier :
 *
 * 1. **le décodage ne touche pas le fil principal** ([Dispatchers.Default] : c'est du calcul) ;
 * 2. **une image en retard se jette, elle ne s'empile pas** : le flux est un `StateFlow`, qui ne
 *    garde que la dernière valeur — un collecteur occupé saute donc les images intermédiaires,
 *    exactement ce qu'on veut d'un écran en direct ;
 * 3. **on réutilise la mémoire** ([BitmapFactory.Options.inBitmap]) : une image 1280×720 pèse
 *    3,7 Mo, et en allouer une nouvelle douze fois par seconde use le ramasse-miettes pour rien.
 *    Deux tampons suffisent — on décode dans celui qui n'est pas affiché, puis on échange. Jamais
 *    dans celui que Compose est en train de dessiner.
 */
internal class ComputerFrameDecoder {

    /** L'image affichée à l'instant. */
    private var shown: Bitmap? = null

    /** Le tampon libre, où la prochaine image se décode. */
    private var spare: Bitmap? = null

    /** Combien d'images ont été décodées — la mesure, pas la promesse. */
    var decoded: Int = 0
        private set

    fun decode(bytes: ByteArray): Bitmap? {
        val reuse = spare?.takeIf { !it.isRecycled && it.isMutable }
        val options = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (reuse != null) inBitmap = reuse
        }
        val fresh = runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull() ?: return null
        // L'image qu'on affichait devient le tampon libre : décoder dans celle qui est encore à
        // l'écran ferait clignoter (au mieux) ou déchirer (au pire) l'aperçu.
        spare = shown
        shown = fresh
        decoded += 1
        return fresh
    }

    fun clear() {
        shown = null
        spare = null
    }
}

/**
 * L'image de l'aperçu, décodée au fil de l'eau.
 *
 * Rendue telle quelle : l'écran lit ce `State` **dans le composable de l'aperçu seulement**, si
 * bien qu'une nouvelle image ne recompose que l'aperçu — la barre du bas, la ligne d'état et
 * l'en-tête ne sont pas retouchés douze fois par seconde.
 */
@Composable
internal fun rememberComputerImage(
    frames: StateFlow<ComputerBridgeClient.Frame?>,
    onDecoded: (Int) -> Unit = {},
): State<ImageBitmap?> {
    val decoder = remember { ComputerFrameDecoder() }
    val image = remember { mutableStateOf<ImageBitmap?>(null) }
    val report = androidx.compose.runtime.rememberUpdatedState(onDecoded)

    LaunchedEffect(frames) {
        frames.collect { frame ->
            val bytes = frame?.bytes
            if (bytes == null) {
                image.value = null
                return@collect
            }
            val decoded = withContext(Dispatchers.Default) { decoder.decode(bytes) }
            // Le compte est remonté **après** le décodage : c'est ce qui distingue une image
            // affichée d'une image arrivée trop tard et jetée.
            report.value(decoder.decoded)
            if (decoded != null && !decoded.isRecycled) image.value = decoded.asImageBitmap()
        }
    }

    DisposableEffect(decoder) {
        onDispose { decoder.clear() }
    }

    return image
}
