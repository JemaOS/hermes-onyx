package com.onyx.app.ui.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onyx.app.data.model.SetupCodeOffer
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import java.util.concurrent.Executors

private enum class CameraAccess { ASKING, ALLOWED, DENIED, UNSUPPORTED, FAILED }

fun codeScannerSupported(context: Context): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

/**
 * La caméra de: QR uniquement, premier lien Onyx accepté, message pour
 * tout autre QR, permission et refus réels. La feuille de confirmation reste chez l'appelant.
 */
@Composable
fun SetupCodeScanner(
    onCode: (SetupCodeOffer) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) = CodeScanner(
    title = "Scan a setup code",
    prompt = "Point the camera at a Onyx setup code.",
    wrong = "That isn't a Onyx setup code.",
    denied = "Allow the camera for Onyx in Settings to scan a setup code, or paste the setup link instead.",
    accept = { text ->
        val offer = SetupCodeOffer.fromText(text) ?: return@CodeScanner false
        onCode(offer)
        true
    },
    onCancel = onCancel,
    modifier = modifier,
)

/** Scanner QR générique, équivalent du `CodeScanner`: `accept` décide quel code fermer. */
@Composable
fun CodeScanner(
    title: String,
    prompt: String,
    wrong: String,
    denied: String,
    accept: (String) -> Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val theme = LocalOnyxTheme.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentAccept by rememberUpdatedState(accept)
    val supported = remember { codeScannerSupported(context) }
    var access by remember {
        mutableStateOf(
            when {
                !supported -> CameraAccess.UNSUPPORTED
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED -> CameraAccess.ALLOWED
                else -> CameraAccess.ASKING
            },
        )
    }
    var wrongCode by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        access = if (granted) CameraAccess.ALLOWED else CameraAccess.DENIED
    }

    LaunchedEffect(Unit) {
        if (access == CameraAccess.ASKING) permission.launch(Manifest.permission.CAMERA)
    }
    DisposableEffect(lifecycleOwner, supported) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && supported) {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) access = CameraAccess.ALLOWED
                else if (access == CameraAccess.ALLOWED) access = CameraAccess.DENIED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler(onBack = onCancel)

    DisposableEffect(access, retry, lifecycleOwner, previewView) {
        if (access != CameraAccess.ALLOWED) return@DisposableEffect onDispose { }
        var active = true
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            if (!active) return@Runnable
            val provider = runCatching { future.get() }.getOrNull()
            if (provider == null) {
                if (active) access = CameraAccess.FAILED
                return@Runnable
            }
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            val deduplicator = QrCodeDeduplicator()
            analysis.setAnalyzer(executor) { image ->
                try {
                    val luminance = image.luminanceBytes()
                    val text = luminance?.let { QrCodeDecoder.decodeYuv(it, image.width, image.height) }
                    if (text != null && deduplicator.accept(text)) {
                        ContextCompat.getMainExecutor(context).execute {
                            if (!active) return@execute
                            if (currentAccept(text)) {
                                previewView.performHapticFeedback(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        HapticFeedbackConstants.CONFIRM
                                    } else {
                                        HapticFeedbackConstants.LONG_PRESS
                                    },
                                )
                                onCancel()
                            } else {
                                wrongCode = true
                            }
                        }
                    }
                } finally {
                    image.close()
                }
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }.onFailure {
                if (active) access = CameraAccess.FAILED
            }
        }
        future.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            active = false
            if (future.isDone) runCatching { future.get().unbindAll() }
            executor.shutdown()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (access == CameraAccess.ALLOWED) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }

        when (access) {
            CameraAccess.ASKING -> ScannerSpinner(modifier = Modifier.align(Alignment.Center))
            CameraAccess.DENIED -> ScannerMessage(
                title = "No camera access",
                message = denied,
                action = "Open Settings",
                onAction = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
                modifier = Modifier.align(Alignment.Center),
            )
            CameraAccess.UNSUPPORTED -> ScannerMessage(
                title = "Camera unavailable",
                message = "This device has no camera available for scanning. Paste the setup link instead.",
                action = null,
                modifier = Modifier.align(Alignment.Center),
            )
            CameraAccess.FAILED -> ScannerMessage(
                title = "Camera unavailable",
                message = "Onyx couldn't start the camera. Close other camera apps and try again.",
                action = "Try Again",
                onAction = { access = CameraAccess.ALLOWED; retry++ },
                modifier = Modifier.align(Alignment.Center),
            )
            CameraAccess.ALLOWED -> Unit
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "Cancel",
                style = theme.body.copy(color = Color.White),
                modifier = Modifier.onyxClickable(onClick = onCancel),
            )
            BasicText(
                text = title,
                style = theme.headline.copy(color = Color.White, textAlign = TextAlign.Center),
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(48.dp))
        }

        if (access == CameraAccess.ALLOWED) {
            BasicText(
                text = if (wrongCode) wrong else prompt,
                style = theme.subheadline.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp)
                    .background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun ScannerMessage(
    title: String,
    message: String,
    /** Le libellé du bouton; `null`: aucun bouton, et donc aucun rappel à fournir. */
    action: String?,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = modifier.widthIn(max = 420.dp).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OnyxIcon(Symbols.CameraFill, tint = Color.White, size = 36.dp)
        BasicText(
            text = title,
            style = theme.title3.copy(color = Color.White, textAlign = TextAlign.Center),
        )
        BasicText(
            text = message,
            style = theme.body.copy(color = Color.White.copy(alpha = 0.78f), textAlign = TextAlign.Center),
        )
        // Sans libellé **et** sans rappel, aucun bouton n'est dessiné: jamais un bouton qui ne
        // fait rien (« Camera unavailable » n'en a pas).
        val label = action
        if (label != null && onAction != null) {
            BasicText(
                text = label,
                style = theme.body.copy(color = theme.accent, fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    .onyxClickable(onClick = onAction)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun ScannerSpinner(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scanner-spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing)),
        label = "scanner-spinner-angle",
    )
    Canvas(modifier = modifier.size(28.dp).rotate(angle)) {
        drawArc(
            color = Color.White,
            startAngle = 20f,
            sweepAngle = 285f,
            useCenter = false,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/** CameraX fournit YUV_420_888; ZXing n'a besoin que du plan Y, rowStride compris. */
private fun ImageProxy.luminanceBytes(): ByteArray? {
    val plane = planes.firstOrNull() ?: return null
    if (plane.pixelStride != 1) return null
    val buffer = plane.buffer.duplicate()
    val result = ByteArray(width * height)
    for (row in 0 until height) {
        val position = row * plane.rowStride
        if (position < 0 || position + width > buffer.limit()) return null
        buffer.position(position)
        buffer.get(result, row * width, width)
    }
    return result
}
