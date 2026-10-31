package com.onyx.app.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import java.io.File
import com.onyx.app.ui.design.tapLabel
import com.onyx.app.ui.localization.resolveOnyxText

/**
 * L'appareil photo de Onyx (`CameraPicker`).
 *
 * L'image en plein écran, l'obturateur au centre en bas, et la sortie à gauche. La bascule
 * avant/arrière est à droite, la torche en haut à droite quand l'objectif en a une, et la grille
 * des tiers se pose sur l'image. La photo prise est rendue à l'appelant comme une pièce jointe.
 */
@Composable
fun CameraScreen(
    onCapture: (android.net.Uri) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val theme = LocalOnyxTheme.current
    val lifecycle = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val capture = remember { ImageCapture.Builder().build() }

    // L'objectif, la torche et ce que cet appareil sait faire.
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var hasFrontCamera by remember { mutableStateOf(true) }
    var hasFlash by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    DisposableEffect(lifecycle, granted, lensFacing) {
        if (!granted) return@DisposableEffect onDispose { }
        val future = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val provider = runCatching { future.get() }.getOrNull() ?: return@Runnable
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            runCatching {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycle,
                    CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                    preview,
                    capture,
                )
                hasFrontCamera = runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }
                    .getOrDefault(false)
                hasFlash = camera.cameraInfo.hasFlashUnit()
                cameraControl = camera.cameraControl
                // La torche reprend son état à chaque changement d'objectif.
                runCatching { camera.cameraControl.enableTorch(torchOn) }
            }
        }
        future.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { future.get().unbindAll() }
            cameraControl = null
            hasFlash = false
        }
    }

    // La bascule avant/arrière : l'objectif change, la torche s'éteint (l'objectif avant en a
    // rarement une).
    val flip: () -> Unit = {
        torchOn = false
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    // La torche : on l'allume et on l'éteint sur l'objectif courant.
    val toggleTorch: () -> Unit = {
        val next = !torchOn
        torchOn = next
        cameraControl?.let { runCatching { it.enableTorch(next) } }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            ThirdsGrid(modifier = Modifier.fillMaxSize())
        }

        Box(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp).fillMaxSize(),
        ) {
            // L'obturateur.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(72.dp)
                    .background(Color.White.copy(alpha = 0.9f), CircleShape)
                    .onyxClickable(pressedScale = 0.9f) { takePhoto(context, capture, onCapture) }
                    // L'obturateur est un rond **sans texte** : son nom est le seul moyen de savoir
                    // ce qu'il fait (relevé d'arbre : zone cliquable muette).
                    .tapLabel(resolveOnyxText("Take photo")),
            )
            // La sortie.
            OnyxIcon(
                symbol = Symbols.Xmark,
                tint = Color.White,
                size = 24.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 28.dp, bottom = 48.dp)
                    .onyxClickable(onClick = onClose)
                    // Le libellé est porté par la **zone**, l'icône devient décorative : le porter
                    // deux fois ferait deux annonces pour un seul bouton.
                    .tapLabel(resolveOnyxText("Close")),
            )
            // La bascule avant/arrière, quand il y a un objectif avant.
            if (granted && hasFrontCamera) {
                CameraRoundControl(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 28.dp, bottom = 44.dp),
                    label = resolveOnyxText("Flip camera"),
                    onClick = flip,
                ) {
                    OnyxIcon(
                        symbol = Symbols.ArrowClockwise,
                        tint = Color.White,
                        size = 26.dp,
                    )
                }
            }
        }

        // La torche : le même « bolt.fill » que l'appareil photo de la référence, allumé en orangé.
        if (granted && hasFlash) {
            CameraRoundControl(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
                // La lampe est **un** contrôle dont le nom dit ce que le prochain appui fera —
                // comme le contrôle du volet (« Replier » / « Déplier »).
                label = resolveOnyxText(if (torchOn) "Turn off the light" else "Turn on the light"),
                onClick = toggleTorch,
            ) {
                OnyxIcon(
                    symbol = Symbols.Bolt,
                    tint = if (torchOn) theme.warning else Color.White,
                    size = 20.dp,
                )
            }
        }

        Box(modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp)) {
            if (!granted) {
                OnyxIcon(symbol = Symbols.Camera, tint = Color.White, size = 22.dp)
            }
        }
    }
}

/**
 * La grille des tiers, comme le quadrillage de l'appareil photo de la référence : deux traits verticaux et
 * deux traits horizontaux, fins et translucides, posés sur l'image.
 */
@Composable
private fun ThirdsGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val line = Color.White.copy(alpha = 0.35f)
        val stroke = 1.dp.toPx()
        for (step in 1..2) {
            val x = size.width * step / 3f
            drawLine(line, Offset(x, 0f), Offset(x, size.height), stroke)
            val y = size.height * step / 3f
            drawLine(line, Offset(0f, y), Offset(size.width, y), stroke)
        }
    }
}

/** Un contrôle rond de l'appareil photo, posé sur l'image (bascule, torche). */
@Composable
private fun CameraRoundControl(
    modifier: Modifier = Modifier,
    /** Le nom du contrôle : le rond n'a qu'une icône, et une icône seule ne se annonce pas. */
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            .onyxClickable(pressedScale = 0.9f, onClick = onClick)
            .tapLabel(label),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Prend la photo, l'écrit dans le **stockage privé durable** et la rend à l'appelant
 * (`CameraPicker.onCapture`).
 *
 * L'écrire dans `cacheDir` (ce que faisait ce code) la condamnait : le système purge le cache, et
 * la bulle du fil — qui se décode depuis cette URI — devenait « Illisible » après fermeture.
 */
private fun takePhoto(context: Context, capture: ImageCapture, onCapture: (android.net.Uri) -> Unit) {
    val folder = File(context.filesDir, "photos").apply { mkdirs() }
    val file = File(folder, "photo-${System.currentTimeMillis()}.jpg")
    capture.takePicture(
        ImageCapture.OutputFileOptions.Builder(file).build(),
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                onCapture(uri)
            }

            override fun onError(exception: ImageCaptureException) = Unit
        },
    )
}
