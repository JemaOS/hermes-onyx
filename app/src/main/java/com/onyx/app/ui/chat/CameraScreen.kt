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
 * The Onyx camera (`CameraPicker`).
 *
 * The full-screen image, the shutter at the bottom centre, and the exit on the left. The
 * front/back flip is on the right, the torch at the top right when the lens has one, and the
 * thirds grid is laid over the image. The photo taken is returned to the caller as an attachment.
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

    // The lens, the torch, and what this device can do.
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
                // The torch resumes its state on each lens change.
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

    // The front/back flip: the lens changes, the torch goes off (the front lens rarely
    // has one).
    val flip: () -> Unit = {
        torchOn = false
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    // The torch: we turn it on and off on the current lens.
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
            // The shutter.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(72.dp)
                    .background(Color.White.copy(alpha = 0.9f), CircleShape)
                    .onyxClickable(pressedScale = 0.9f) { takePhoto(context, capture, onCapture) }
                    // The shutter is a **text-free** circle: its name is the only way to know
                    // what it does (tree dump: a silent clickable area).
                    .tapLabel(resolveOnyxText("Take photo")),
            )
            // The exit.
            OnyxIcon(
                symbol = Symbols.Xmark,
                tint = Color.White,
                size = 24.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 28.dp, bottom = 48.dp)
                    .onyxClickable(onClick = onClose)
                    // The label is carried by the **area**, the icon becomes decorative: carrying it
                    // twice would make two announcements for a single button.
                    .tapLabel(resolveOnyxText("Close")),
            )
            // The front/back flip, when there is a front lens.
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

        // The torch: the same "bolt.fill" as the reference camera, lit in orange.
        if (granted && hasFlash) {
            CameraRoundControl(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
                // The lamp is **one** control whose name says what the next press will do —
                // like the drawer control ("Collapse" / "Expand", « Replier » / « Déplier »).
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
 * The thirds grid, like the grid of the reference camera: two vertical lines and
 * two horizontal lines, thin and translucent, laid over the image.
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

/** A round camera control, laid over the image (flip, torch). */
@Composable
private fun CameraRoundControl(
    modifier: Modifier = Modifier,
    /** The control's name: the circle has only an icon, and an icon alone is not announced. */
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
 * Takes the photo, writes it to **durable private storage** and returns it to the caller
 * (`CameraPicker.onCapture`).
 *
 * Writing it to `cacheDir` (what this code used to do) condemned it: the system purges the cache,
 * and the feed bubble — which decodes from that URI — became "Unreadable" (« Illisible ») after closing.
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
