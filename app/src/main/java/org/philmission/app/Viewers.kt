package org.philmission.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val COMPACT = PaddingValues(horizontal = 10.dp, vertical = 6.dp)

@Composable
fun ZoomableBitmap(bitmap: Bitmap, description: String, modifier: Modifier = Modifier, header: @Composable RowScope.() -> Unit = {}) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    Column(modifier) {
        // 가로 화면에서도 문서 영역이 넓게 남도록 조작 버튼을 한 줄에 모은다.
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            header()
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { scale = (scale - 0.5f).coerceAtLeast(1f); if (scale == 1f) offset = Offset.Zero }, contentPadding = COMPACT) { Text("축소") }
            TextButton(onClick = { scale = (scale + 0.5f).coerceAtMost(5f) }, contentPadding = COMPACT) { Text("확대") }
            TextButton(onClick = { scale = 1f; offset = Offset.Zero }, contentPadding = COMPACT) { Text("원래 크기") }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFEFEFEF))) {
            Image(
                bitmap = bitmap.asImageBitmap(), contentDescription = description, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(bitmap) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
        }
    }
}

@Composable
fun ImageAssetScreen(asset: String, description: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by produceState<Result<Bitmap>?>(null, asset) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(asset).use { requireNotNull(BitmapFactory.decodeStream(it)) } }
        }
    }
    when (val result = state) {
        null -> Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        else -> result.fold(
            onSuccess = { ZoomableBitmap(it, description, modifier) },
            onFailure = { DocumentError(modifier) },
        )
    }
}

@Composable
fun DocumentError(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("자료를 열 수 없습니다.", style = MaterialTheme.typography.titleLarge)
        Text("뒤로 가서 다른 자료를 사용할 수 있습니다. 문제가 계속되면 앱을 다시 설치하거나 배포 담당자에게 문의해 주세요.")
    }
}

private class PdfHolder(val file: File, val fd: ParcelFileDescriptor, val renderer: PdfRenderer) {
    fun close() { runCatching { renderer.close() }; runCatching { fd.close() } }
}

private fun openPdf(context: Context, asset: String): PdfHolder {
    // assets는 압축되어 있을 수 있어 openFd를 쓰지 않고 캐시로 복사한다. 앱 업데이트마다 새 파일을 쓴다.
    val stamp = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    val target = File(context.cacheDir, "${stamp}_$asset")
    if (!target.exists()) {
        val tmp = File(context.cacheDir, "${target.name}.tmp")
        context.assets.open(asset).use { input -> tmp.outputStream().use { input.copyTo(it) } }
        check(tmp.renameTo(target)) { "cannot cache $asset" }
    }
    val fd = ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
    return PdfHolder(target, fd, PdfRenderer(fd))
}

@Composable
fun PdfAssetScreen(asset: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val holder by produceState<Result<PdfHolder>?>(null, asset) {
        value = withContext(Dispatchers.IO) { runCatching { openPdf(context, asset) }.onFailure { Log.e("PhilMission", "PDF open failed: $asset", it) } }
    }
    DisposableEffect(holder) {
        val opened = holder?.getOrNull() // 효과 시작 시점의 문서를 캡처해야 새로 열린 문서를 닫지 않는다
        onDispose { opened?.close() }
    }
    // 회전해도 현재 페이지를 유지한다.
    var page by rememberSaveable(asset) { mutableIntStateOf(0) }
    when (val result = holder) {
        null -> Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        else -> result.fold(
            onFailure = { DocumentError(modifier) },
            onSuccess = { pdf ->
                val count = pdf.renderer.pageCount
                if (count == 0) { DocumentError(modifier); return@fold }
                val safePage = page.coerceIn(0, count - 1)
                val bitmap by produceState<Result<Bitmap>?>(null, pdf, safePage) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            synchronized(pdf) {
                                pdf.renderer.openPage(safePage).use { p ->
                                    // 메모리 사용을 제한하기 위해 긴 변을 2200px 이하로 렌더링한다.
                                    val scale = minOf(2200f / p.width, 2200f / p.height, 3f)
                                    val bmp = Bitmap.createBitmap((p.width * scale).toInt(), (p.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                                    bmp.eraseColor(AndroidColor.WHITE)
                                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bmp
                                }
                            }
                        }
                    }
                }
                val header: @Composable RowScope.() -> Unit = {
                    OutlinedButton(onClick = { page = (safePage - 1).coerceAtLeast(0) }, enabled = safePage > 0, contentPadding = COMPACT) { Text("이전") }
                    Text("${safePage + 1} / $count 쪽", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick = { page = (safePage + 1).coerceAtMost(count - 1) }, enabled = safePage < count - 1, contentPadding = COMPACT) { Text("다음") }
                }
                Column(modifier.fillMaxSize()) {
                    when (val b = bitmap) {
                        null -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() }
                        else -> b.fold(
                            onSuccess = { ZoomableBitmap(it, "${safePage + 1}쪽", Modifier.weight(1f), header) },
                            onFailure = { Box(Modifier.weight(1f)) { DocumentError() } },
                        )
                    }
                }
            },
        )
    }
}
