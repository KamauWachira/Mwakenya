package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.util.ImageUtils
import java.io.File

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    isTorchOn: Boolean = false,
    isFrontCamera: Boolean = false,
    showGrid: Boolean = true,
    onImageCaptured: (Bitmap) -> Unit,
    onError: (String) -> Unit,
    captureTrigger: Int = 0
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var isCameraReady by remember { mutableStateOf(false) }
    var cameraInitializationError by remember { mutableStateOf<String?>(null) }

    // Toggle torch mode
    LaunchedEffect(isTorchOn, camera) {
        try {
            camera?.cameraControl?.enableTorch(isTorchOn)
        } catch (e: Exception) {
            Log.w("CameraPreview", "Torch toggle failed", e)
        }
    }

    // Capture photo when trigger changes
    LaunchedEffect(captureTrigger) {
        if (captureTrigger > 0 && imageCapture != null) {
            val photoFile = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            imageCapture?.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val bitmap = ImageUtils.loadBitmapFromPath(photoFile.absolutePath, 1600)
                        if (bitmap != null) {
                            onImageCaptured(bitmap)
                        } else {
                            onError("Could not decode captured image")
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("CameraPreview", "Photo capture failed: ${exception.message}", exception)
                        onError("Failed to capture photo: ${exception.localizedMessage}")
                    }
                }
            )
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (cameraInitializationError != null) {
            // Friendly fallback if camera hardware is unavailable (e.g. running on headless emulator)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera Simulated",
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(56.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(16.dp))
                        Text(
                            text = "Camera Viewfinder Ready",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Select an exam question from the gallery below, or tap a sample problem to test instantly!",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val pView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    previewView = pView

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val provider = cameraProviderFuture.get()
                            cameraProvider = provider

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(pView.surfaceProvider)
                            }

                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            imageCapture = capture

                            val cameraSelector = if (isFrontCamera) {
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            } else {
                                CameraSelector.DEFAULT_BACK_CAMERA
                            }

                            provider.unbindAll()
                            val cam = provider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                capture
                            )
                            camera = cam
                            isCameraReady = true
                        } catch (e: Exception) {
                            Log.e("CameraPreview", "Camera initialization failed", e)
                            cameraInitializationError = e.localizedMessage ?: "Camera not available"
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    pView
                }
            )
        }

        // Animated Scanning Overlay Box
        ViewfinderOverlay(showGrid = showGrid)
    }
}

@Composable
fun ViewfinderOverlay(showGrid: Boolean = true) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_laser")
    val laserOffsetY by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_sweep"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Define exam paper scanning target window (approx 84% width, 60% height)
        val boxWidth = width * 0.86f
        val boxHeight = height * 0.58f
        val left = (width - boxWidth) / 2f
        val top = (height - boxHeight) / 2.3f
        val right = left + boxWidth
        val bottom = top + boxHeight

        // Semi-transparent dimming outside the scanner frame
        val dimColor = Color(0x66000000)
        // Top rect
        drawRect(dimColor, Offset(0f, 0f), Size(width, top))
        // Bottom rect
        drawRect(dimColor, Offset(0f, bottom), Size(width, height - bottom))
        // Left rect
        drawRect(dimColor, Offset(0f, top), Size(left, boxHeight))
        // Right rect
        drawRect(dimColor, Offset(right, top), Size(width - right, boxHeight))

        // Grid lines if enabled
        if (showGrid) {
            val gridColor = Color(0x33FFFFFF)
            val stroke = Stroke(width = 1.5f)
            // Vertical thirds
            val thirdW = boxWidth / 3f
            drawLine(gridColor, Offset(left + thirdW, top), Offset(left + thirdW, bottom), strokeWidth = 1.5f)
            drawLine(gridColor, Offset(left + thirdW * 2, top), Offset(left + thirdW * 2, bottom), strokeWidth = 1.5f)
            // Horizontal thirds
            val thirdH = boxHeight / 3f
            drawLine(gridColor, Offset(left, top + thirdH), Offset(right, top + thirdH), strokeWidth = 1.5f)
            drawLine(gridColor, Offset(left, top + thirdH * 2), Offset(right, top + thirdH * 2), strokeWidth = 1.5f)
        }

        // Corner Brackets
        val cornerLength = 48f
        val cornerStroke = 7f
        val cornerColor = Color(0xFF818CF8) // Vibrant indigo/violet

        // Top-Left
        drawLine(cornerColor, Offset(left - 2, top), Offset(left + cornerLength, top), cornerStroke)
        drawLine(cornerColor, Offset(left, top - 2), Offset(left, top + cornerLength), cornerStroke)

        // Top-Right
        drawLine(cornerColor, Offset(right + 2, top), Offset(right - cornerLength, top), cornerStroke)
        drawLine(cornerColor, Offset(right, top - 2), Offset(right, top + cornerLength), cornerStroke)

        // Bottom-Left
        drawLine(cornerColor, Offset(left - 2, bottom), Offset(left + cornerLength, bottom), cornerStroke)
        drawLine(cornerColor, Offset(left, bottom + 2), Offset(left, bottom - cornerLength), cornerStroke)

        // Bottom-Right
        drawLine(cornerColor, Offset(right + 2, bottom), Offset(right - cornerLength, bottom), cornerStroke)
        drawLine(cornerColor, Offset(right, bottom + 2), Offset(right, bottom - cornerLength), cornerStroke)

        // Laser scan line sweep
        val laserY = top + (boxHeight * laserOffsetY)
        val laserBrush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                Color(0x80818CF8),
                Color(0xFF6366F1),
                Color(0xFF38BDF8),
                Color(0xFF6366F1),
                Color(0x80818CF8),
                Color.Transparent
            ),
            startX = left,
            endX = right
        )
        drawLine(
            brush = laserBrush,
            start = Offset(left, laserY),
            end = Offset(right, laserY),
            strokeWidth = 3.5f
        )
    }
}
