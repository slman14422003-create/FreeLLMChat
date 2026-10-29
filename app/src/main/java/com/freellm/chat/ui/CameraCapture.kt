package com.freellm.chat.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlipCameraAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.freellm.chat.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class Shot(val file: File, val selected: Boolean)

private const val MAX_SHOTS = 6
private const val STALE_MS = 3_600_000L

private fun decodeThumb(f: File, target: Int): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
    val raw = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    val deg = when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (raw != null && deg != 0f) {
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(deg) }, true)
    } else raw
} catch (e: Throwable) {
    null
}

@Composable
fun CameraCapture(onDone: (List<Uri>) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { ContextCompat.getMainExecutor(ctx) }
    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var lens by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flash by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var shots by remember { mutableStateOf<List<Shot>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            ctx.cacheDir.listFiles { f -> f.name.startsWith("cam_") }?.forEach {
                if (now - it.lastModified() > STALE_MS) it.delete()
            }
        }
    }

    DisposableEffect(lens) {
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val ic = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(flash)
                    .build()
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.Builder().requireLensFacing(lens).build(), preview, ic)
                capture = ic
                failed = false
            } catch (e: Exception) {
                failed = true
            }
        }, executor)
        onDispose { provider?.unbindAll() }
    }

    LaunchedEffect(flash, capture) { capture?.setFlashMode(flash) }

    fun shoot() {
        val ic = capture ?: return
        if (busy || shots.size >= MAX_SHOTS) return
        busy = true
        val f = File(ctx.cacheDir, "cam_" + System.currentTimeMillis() + ".jpg")
        ic.takePicture(
            ImageCapture.OutputFileOptions.Builder(f).build(),
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    shots = shots + Shot(f, true)
                    busy = false
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    failed = true
                }
            }
        )
    }

    val chosen = shots.count { it.selected }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundTool(onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.camera_close), tint = Color.White) }
                RoundTool({
                    flash = when (flash) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                }) {
                    Icon(
                        when (flash) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Outlined.FlashOn
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Outlined.FlashAuto
                            else -> Icons.Outlined.FlashOff
                        },
                        stringResource(R.string.camera_flash),
                        tint = Color.White
                    )
                }
            }

            if (failed) {
                Text(
                    stringResource(R.string.camera_error),
                    Modifier.align(Alignment.Center).padding(24.dp),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x66000000)).padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (shots.isNotEmpty()) {
                    Text(
                        stringResource(R.string.camera_hint),
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                    LazyRow(
                        Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(shots, key = { _, s -> s.file.path }) { i, s ->
                            ShotThumb(
                                s,
                                onToggle = { shots = shots.mapIndexed { j, x -> if (j == i) x.copy(selected = !x.selected) else x } },
                                onDelete = {
                                    s.file.delete()
                                    shots = shots.filterIndexed { j, _ -> j != i }
                                }
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundTool({ lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }) {
                        Icon(Icons.Outlined.FlipCameraAndroid, stringResource(R.string.camera_flip), tint = Color.White)
                    }
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(BorderStroke(4.dp, Color.White), CircleShape)
                            .padding(7.dp)
                            .clip(CircleShape)
                            .background(if (busy || shots.size >= MAX_SHOTS) Color.Gray else Color.White)
                            .clickable(enabled = !busy && capture != null && shots.size < MAX_SHOTS) { shoot() }
                    )
                    Button(onClick = { onDone(shots.filter { it.selected }.map { Uri.fromFile(it.file) }) }, enabled = chosen > 0) {
                        Text(stringResource(R.string.camera_done, chosen))
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundTool(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0x66000000))
    ) { content() }
}

@Composable
private fun ShotThumb(shot: Shot, onToggle: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bmp by produceState<Bitmap?>(null, shot.file) {
        value = withContext(Dispatchers.IO) { decodeThumb(shot.file, 240) }
    }
    Box(
        Modifier
            .size(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(BorderStroke(if (shot.selected) 3.dp else 1.dp, if (shot.selected) cs.primary else Color.Gray), RoundedCornerShape(12.dp))
            .clickable { onToggle() }
    ) {
        bmp?.let {
            Image(
                it.asImageBitmap(),
                null,
                Modifier.fillMaxSize().alpha(if (shot.selected) 1f else 0.4f),
                contentScale = ContentScale.Crop
            )
        }
        if (shot.selected) {
            Icon(
                Icons.Outlined.CheckCircle,
                null,
                Modifier.align(Alignment.BottomStart).padding(3.dp).size(18.dp),
                tint = cs.primary
            )
        }
        Icon(
            Icons.Outlined.Close,
            stringResource(R.string.camera_remove),
            Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0x99000000))
                .clickable { onDelete() }
                .padding(3.dp),
            tint = Color.White
        )
    }
}
