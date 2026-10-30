package com.toko.kasir.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.toko.kasir.logic.ScanDebouncer
import java.util.concurrent.Executors

/** Mengirim setiap frame kamera ke ML Kit. */
private class BarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val onCode: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                codes.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf { v -> v.isNotEmpty() } }
                    ?.let(onCode)
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}

private class Beeper {
    private val tone: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    } catch (e: RuntimeException) {
        null
    }

    fun beep() {
        try {
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (_: RuntimeException) {
        }
    }

    fun release() {
        tone?.release()
    }
}

private val scannerOptions = BarcodeScannerOptions.Builder()
    .setBarcodeFormats(
        Barcode.FORMAT_EAN_13,
        Barcode.FORMAT_EAN_8,
        Barcode.FORMAT_UPC_A,
        Barcode.FORMAT_UPC_E,
        Barcode.FORMAT_CODE_128,
        Barcode.FORMAT_CODE_39,
        Barcode.FORMAT_CODE_93,
        Barcode.FORMAT_ITF,
        Barcode.FORMAT_CODABAR,
        Barcode.FORMAT_QR_CODE,
    )
    .build()

/**
 * Tampilan kamera yang memindai barcode terus-menerus.
 * CameraX memakai autofokus kontinu dan resolusi analisis 1280x720,
 * cukup tajam untuk barcode kecil tapi tetap cepat diproses.
 */
@Composable
fun BarcodeScannerView(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScanned by rememberUpdatedState(onScanned)
    val currentEnabled by rememberUpdatedState(enabled)

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    val beeper = remember { Beeper() }
    val debouncer = remember { ScanDebouncer() }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(scannerOptions)
        val preview = Preview.Builder().build()
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(executor, BarcodeAnalyzer(scanner) { code ->
            // Dipanggil di main thread oleh ML Kit.
            if (currentEnabled && debouncer.accept(code, SystemClock.elapsedRealtime())) {
                beeper.beep()
                currentOnScanned(code)
            }
        })

        var provider: ProcessCameraProvider? = null
        var disposed = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            try {
                val p = future.get()
                provider = p
                preview.setSurfaceProvider(previewView.surfaceProvider)
                camera = p.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
            } catch (e: Exception) {
                Log.e("BarcodeScanner", "Gagal membuka kamera", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            try {
                provider?.unbind(preview, analysis)
            } catch (_: Exception) {
            }
            analysis.clearAnalyzer()
            scanner.close()
            executor.shutdown()
            camera = null
            torchOn = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { beeper.release() }
    }

    // clipToBounds: gambar kamera tidak boleh meluber menutupi daftar barang di bawahnya.
    Box(modifier.clipToBounds().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().clipToBounds())
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.78f)
                .fillMaxHeight(0.6f)
                .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.72f)
                .height(2.dp)
                .background(Color(0xFFFF5252).copy(alpha = 0.8f))
        )
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            IconButton(
                onClick = {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            ) {
                Icon(
                    if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = "Senter",
                    tint = Color.White,
                )
            }
        }
    }
}

/** Meminta izin kamera sebelum menampilkan [content]. */
@Composable
fun CameraPermissionGate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier.clipToBounds().background(Color.Black), contentAlignment = Alignment.Center) {
        if (granted) {
            content()
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    "Izin kamera diperlukan untuk scan barcode",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text("Izinkan kamera")
                }
            }
        }
    }
}
