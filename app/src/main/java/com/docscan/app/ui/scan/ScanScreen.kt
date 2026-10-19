package com.docscan.app.ui.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.docscan.app.core.Pt
import com.docscan.app.imaging.EdgeDetector
import com.docscan.app.ui.PageThumb
import com.docscan.app.ui.fitRect
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Page outline detected in the live preview, normalised to the (upright) analysed frame. */
private data class LiveQuad(val points: List<Pt>, val frameWidth: Int, val frameHeight: Int)

/** How many consecutive still frames (about a quarter of a second each) trigger an automatic shot. */
private const val HOLD_FRAMES = 5

/** How different (0..255) the picture must become after a shot before the next automatic shot is allowed. */
private const val NEW_PAGE_DIFFERENCE = 14

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider = suspendCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(this))
}

/** 24x24 brightness fingerprint of a frame, used to notice that the page was turned. */
private fun fingerprint(frame: Bitmap): IntArray {
    val small = Bitmap.createScaledBitmap(frame, 24, 24, true)
    val pixels = IntArray(24 * 24)
    small.getPixels(pixels, 0, 24, 0, 0, 24, 24)
    small.recycle()
    return IntArray(pixels.size) { i ->
        val p = pixels[i]
        (AndroidColor.red(p) * 3 + AndroidColor.green(p) * 6 + AndroidColor.blue(p)) / 10
    }
}

private fun meanDifference(a: IntArray, b: IntArray): Int {
    var total = 0
    for (i in a.indices) total += abs(a[i] - b[i])
    return total / a.size
}

private fun largestShift(a: List<Pt>, b: List<Pt>): Float {
    var worst = 0f
    for (i in a.indices) worst = max(worst, hypot(a[i].x - b[i].x, a[i].y - b[i].y))
    return worst
}

@Composable
fun ScanScreen(vm: ScanViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val pageCount by vm.pageCount.collectAsState()
    val lastPage by vm.lastPage.collectAsState()
    val pending by vm.pending.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val accent = MaterialTheme.colorScheme.secondary

    var mode by remember { mutableStateOf(ScanMode.DOCUMENT) }
    var autoCapture by remember { mutableStateOf(false) }
    var removeFingers by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var liveQuad by remember { mutableStateOf<LiveQuad?>(null) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var shotRequest by remember { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    val autoFlag = remember { AtomicBoolean(false) }
    SideEffect { autoFlag.set(autoCapture) }

    // Books and spreads are usually held by hand, so finger removal starts on there.
    LaunchedEffect(mode) { removeFingers = mode != ScanMode.DOCUMENT }

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        vm.addFromGallery(context, uris, CaptureOptions(mode, removeFingers))
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    LaunchedEffect(flashOn) {
        imageCapture.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        val provider = context.awaitCameraProvider()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        // Hold-still detection (runs on the analysis thread only).
        var lastRun = 0L
        var lastPoints: List<Pt>? = null
        var stableHits = 0
        var armed = true
        var shotPrint: IntArray? = null
        analysis.setAnalyzer(analysisExecutor) { proxy ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastRun >= 250) {
                lastRun = now
                runCatching {
                    val frame = proxy.toBitmap()
                    val scale = 480f / max(frame.width, frame.height)
                    val matrix = Matrix().apply {
                        postScale(scale, scale)
                        postRotate(proxy.imageInfo.rotationDegrees.toFloat())
                    }
                    val upright = Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
                    val points = EdgeDetector.detect(upright)
                    liveQuad = points?.let { LiveQuad(it, upright.width, upright.height) }

                    if (autoFlag.get()) {
                        val print = fingerprint(upright)
                        // After a shot, wait until the picture really changes (page turned) before the next one.
                        if (!armed) {
                            val reference = shotPrint
                            if (reference == null || meanDifference(reference, print) > NEW_PAGE_DIFFERENCE) armed = true
                        }
                        val previous = lastPoints
                        val shift = if (points != null && previous != null) largestShift(previous, points) else 1f
                        lastPoints = points
                        if (points != null && shift < 0.02f) stableHits++ else stableHits = 0
                        holdProgress = if (armed && points != null) (stableHits / HOLD_FRAMES.toFloat()).coerceIn(0f, 1f) else 0f
                        if (armed && points != null && stableHits >= HOLD_FRAMES) {
                            armed = false
                            stableHits = 0
                            shotPrint = print
                            holdProgress = 0f
                            shotRequest++
                        }
                    } else {
                        holdProgress = 0f
                        stableHits = 0
                    }
                }
            }
            proxy.close()
        }
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } }
    }

    fun capture() {
        if (capturing) return
        capturing = true
        val file = File.createTempFile("capture", ".jpg", context.cacheDir)
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    vm.addCapture(file, CaptureOptions(mode, removeFingers))
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    file.delete()
                    vm.showMessage("Could not take the photo. Please try again.")
                }
            },
        )
    }

    LaunchedEffect(shotRequest) {
        if (shotRequest > 0 && autoCapture) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            capture()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            QuadOverlay(liveQuad, accent, Modifier.fillMaxSize())
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Camera permission is needed to scan.", color = Color.White)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Allow camera")
                }
                TextButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Import from gallery instead") }
            }
        }

        // Soft scrims keep the controls readable on any scene without a heavy toolbar.
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(320.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))),
        )

        // Top: close on the left, switches on the right.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassButton(onClick = onBack, description = "Close") {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
            }
            Box(Modifier.weight(1f))
            Switch("Auto", autoCapture, accent) { autoCapture = !autoCapture }
            Switch("Fingers", removeFingers, accent) { removeFingers = !removeFingers }
            GlassButton(onClick = { flashOn = !flashOn }, description = if (flashOn) "Flash on" else "Flash off") {
                Icon(if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff, contentDescription = null, tint = Color.White)
            }
        }

        if (pending > 0) {
            Row(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                Text("Processing $pending", color = Color.White, fontSize = 12.sp)
            }
        }

        // Bottom dock: a hint, the mode tabs, then gallery · shutter · finished stack.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = when {
                    autoCapture && liveQuad == null -> "Point at the page"
                    autoCapture -> "Hold still, shooting automatically"
                    mode == ScanMode.BOOK -> "One book page, curves get flattened"
                    mode == ScanMode.SPREAD -> "Turn the phone sideways to fit both pages"
                    else -> "Fit the page in the frame"
                },
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
            )
            Row(
                Modifier.padding(top = 14.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                ScanMode.entries.forEach { entry ->
                    ModeTab(entry.label, selected = entry == mode, accent = accent) { mode = entry }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    description = "Import from gallery",
                    size = 52,
                ) { Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White) }

                Shutter(progress = holdProgress, accent = accent, enabled = hasPermission, onClick = { capture() })

                Stack(
                    page = lastPage,
                    count = pageCount,
                    busy = pending > 0,
                    accent = accent,
                    onClick = { if (pageCount > 0 && pending == 0) onDone() },
                )
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 200.dp))
    }
}

@Composable
private fun GlassButton(onClick: () -> Unit, description: String, size: Int = 44, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.40f))
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Small labelled on/off pill for the camera options. */
@Composable
private fun Switch(label: String, on: Boolean, accent: Color, onToggle: () -> Unit) {
    Box(
        Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(if (on) accent else Color.Black.copy(alpha = 0.40f))
            .clickable(onClickLabel = label, role = Role.Switch, onClick = onToggle)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClickLabel = label, role = Role.Tab, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label.uppercase(),
            color = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
            letterSpacing = 1.4.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
        Box(
            Modifier.padding(top = 6.dp).height(3.dp).width(20.dp).clip(CircleShape)
                .background(if (selected) accent else Color.Transparent),
        )
    }
}

/** Shutter whose ring fills while auto-capture is waiting for the page to hold still. */
@Composable
private fun Shutter(progress: Float, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    val animated by animateFloatAsState(progress, label = "hold")
    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            drawCircle(Color.White.copy(alpha = 0.9f), radius = size.minDimension / 2 - stroke / 2, style = Stroke(width = 3.dp.toPx()))
            if (animated > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(enabled = enabled, onClickLabel = "Take photo", role = Role.Button, onClick = onClick),
        )
    }
}

/** The pages scanned so far as a little stack; tapping it finishes the session. */
@Composable
private fun Stack(page: com.docscan.app.data.PageEntity?, count: Int, busy: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(2.dp, Color.White.copy(alpha = if (count > 0) 0.95f else 0.35f)), RoundedCornerShape(14.dp))
            .clickable(enabled = count > 0 && !busy, onClickLabel = "Finish scanning", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (page != null) PageThumb(page, Modifier.fillMaxSize())
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        if (count > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(3.dp).clip(CircleShape).background(accent).padding(horizontal = 6.dp, vertical = 1.dp),
            ) { Text("$count", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun QuadOverlay(quad: LiveQuad?, accent: Color, modifier: Modifier = Modifier) {
    if (quad == null) return
    Canvas(modifier) {
        val rect = fitRect(size.width, size.height, quad.frameWidth, quad.frameHeight)
        val points = quad.points.map { Offset(rect.left + it.x * rect.width, rect.top + it.y * rect.height) }
        val path = Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        path.close()
        drawPath(path, accent.copy(alpha = 0.14f))
        drawPath(path, accent, style = Stroke(width = 2.5.dp.toPx()))
        points.forEach { drawCircle(accent, radius = 6.dp.toPx(), center = it) }
    }
}
