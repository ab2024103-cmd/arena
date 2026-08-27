package net.morsecode.shared.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import net.morsecode.shared.platform.AndroidEnv
import net.morsecode.shared.ui.AppViewModel

/**
 * CameraX + ZXing QR scanner dialog (Android actual). Falls back to manual
 * ip:port entry when the camera is unavailable.
 */
@Composable
actual fun QrScanDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var useManual by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCamera = granted }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (useManual || !hasCamera) {
        ManualConnectDialog(vm = vm, onDismiss = onDismiss)
        return
    }

    var scanned by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan pairing QR") },
        text = {
            Column {
                Text(
                    "Point the camera at the other device's pairing QR.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(top = 8.dp3)) {
                    CameraScannerView(
                        onQr = { content -> if (scanned == null) scanned = content },
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                }
                TextButton(onClick = { useManual = true }) { Text("Enter address manually instead") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    scanned?.let { content ->
        DisposableEffect(Unit) { onDispose { } }
        vm.connectByQr(content) { ok, err -> vm.toast(if (ok) "Paired!" else "Failed: $err") }
        onDismiss()
    }
}

private val Int.dp3 get() = androidx.compose.ui.unit.Dp(this.toFloat())

@Composable
private fun CameraScannerView(onQr: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember {
        MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }
    }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { image ->
                        val text = decodeQr(reader, image)
                        image.close()
                        if (text != null) onQr(text)
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (e: Exception) {
                    // camera binding failed (emulator without camera, etc.)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier,
    )
}

/** YUV_420_888 -> luminance source -> ZXing decode. */
private fun decodeQr(reader: MultiFormatReader, image: ImageProxy): String? = try {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val width = image.width
    val height = image.height
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val data = ByteArray(width * height)
    if (pixelStride == 1 && rowStride == width) {
        buffer.get(data)
    } else {
        var pos = 0
        for (row in 0 until height) {
            buffer.position(row * rowStride)
            if (pixelStride == 1) {
                buffer.get(data, pos, width)
                pos += width
            } else {
                for (col in 0 until width) {
                    data[pos++] = buffer.get(row * rowStride + col * pixelStride)
                }
            }
        }
    }
    val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
    val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
    reader.reset()
    result.text
} catch (e: Exception) {
    null
}
