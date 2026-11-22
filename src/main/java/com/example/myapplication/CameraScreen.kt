package com.example.myapplication

import android.net.Uri
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@Composable
fun CameraScreen(
    onClose: () -> Unit,
    onCaptured: (Uri) -> Unit,
    onFailure: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var guideRect by remember { mutableStateOf<Rect?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)

        providerFuture.addListener({
            try {
                val provider = providerFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()

                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture
                )
                imageCapture = capture
            } catch (e: Exception) {
                onFailure(e.localizedMessage ?: "Camera could not be started.")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                if (providerFuture.isDone) providerFuture.get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(900)
            focusPoint = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(camera) {
                detectTapGestures { offset ->
                    val point = previewView.meteringPointFactory
                        .createPoint(offset.x, offset.y)
                    val action = FocusMeteringAction.Builder(point)
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build()
                    camera?.cameraControl?.startFocusAndMetering(action)
                    focusPoint = offset
                }
            }
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Guide frame
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 28.dp, end = 28.dp, top = 96.dp, bottom = 190.dp)
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInRoot()
                    guideRect = Rect(
                        position.x,
                        position.y,
                        position.x + coordinates.size.width,
                        position.y + coordinates.size.height
                    )
                }
                .border(
                    BorderStroke(2.dp, Color.White.copy(alpha = 0.7f)),
                    RoundedCornerShape(22.dp)
                )
        )

        // Focus ring
        focusPoint?.let { p ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White,
                    radius = 34.dp.toPx(),
                    center = p,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(Icons.Filled.Close, "Close camera", onClose)
            Spacer(Modifier.weight(1f))
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                RoundIconButton(
                    if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    "Toggle light"
                ) {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                }
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Fit the whole payment screen inside the frame.\nTap the screen to focus.",
                color = Color.White,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (imageCapture != null && !capturing) Color.White
                        else Color.White.copy(alpha = 0.35f)
                    )
                    .clickable(enabled = imageCapture != null && !capturing) {
                        val capture = imageCapture ?: return@clickable
                        capturing = true

                        val file = File(
                            context.cacheDir,
                            "paysure_${System.currentTimeMillis()}.jpg"
                        )
                        val options = ImageCapture.OutputFileOptions.Builder(file).build()

                        capture.takePicture(
                            options,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(
                                    outputFileResults: ImageCapture.OutputFileResults
                                ) {
                                    try {
                                        cropPhotoToGuide(file, previewView, guideRect)
                                        capturing = false
                                        onCaptured(Uri.fromFile(file))
                                    } catch (e: Exception) {
                                        capturing = false
                                        onFailure(e.localizedMessage ?: "Could not crop captured photo.")
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    capturing = false
                                    onFailure(exception.localizedMessage ?: "Photo capture failed.")
                                }
                            }
                        )
                    }
            )
        }
    }
}

/** Crop the saved camera photo to the guide frame shown in PreviewView. */
private fun cropPhotoToGuide(file: File, preview: PreviewView, guide: Rect?) {
    val frame = guide ?: return
    val viewWidth = preview.width.toFloat()
    val viewHeight = preview.height.toFloat()
    if (viewWidth <= 0f || viewHeight <= 0f) return

    val original = BitmapFactory.decodeFile(file.absolutePath)
        ?: throw IllegalStateException("Captured photo could not be opened")
    try {
        // PreviewView uses FILL_CENTER: the source is scaled and centered in the view.
        val scale = maxOf(viewWidth / original.width, viewHeight / original.height)
        val offsetX = (viewWidth - original.width * scale) / 2f
        val offsetY = (viewHeight - original.height * scale) / 2f
        val left = ((frame.left - offsetX) / scale).toInt().coerceIn(0, original.width - 1)
        val top = ((frame.top - offsetY) / scale).toInt().coerceIn(0, original.height - 1)
        val right = ((frame.right - offsetX) / scale).toInt().coerceIn(left + 1, original.width)
        val bottom = ((frame.bottom - offsetY) / scale).toInt().coerceIn(top + 1, original.height)
        val cropped = Bitmap.createBitmap(original, left, top, right - left, bottom - top)
        try {
            FileOutputStream(file).use { output ->
                if (!cropped.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                    throw IllegalStateException("Cropped photo could not be saved")
                }
            }
        } finally {
            if (cropped !== original) cropped.recycle()
        }
    } finally {
        original.recycle()
    }
}


@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}
