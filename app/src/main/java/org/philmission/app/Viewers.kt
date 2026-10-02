package org.philmission.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
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
            runCatching {
                if (asset.startsWith("@user/")) decodeUserImage(userPdfFile(context, asset))
                else context.assets.open(asset).use { requireNotNull(BitmapFactory.decodeStream(it)) }
            }
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

/** 사용자가 앱에 등록하는 PDF는 asset 이름이 "@user/"로 시작하고 앱 내부 저장소(filesDir)에 보관된다. */
fun userPdfFile(context: Context, asset: String) = File(context.filesDir, asset.removePrefix("@user/"))

/** 사용자가 등록한 자료 파일. 형식에 따라 "<base>.pdf" 또는 "<base>.img"(JPG·PNG)로 저장되며, 없으면 null. */
fun userDocFile(context: Context, base: String): File? =
    listOf("pdf", "img").map { File(context.filesDir, "$base.$it") }.firstOrNull { it.exists() }

fun deleteUserDoc(context: Context, base: String) {
    listOf("pdf", "img").forEach { File(context.filesDir, "$base.$it").delete() }
}

/**
 * 선택한 PDF·이미지가 열리는지 확인한 뒤 내부 저장소에 "<base>.pdf|img"로 복사한다.
 * 실패하면 기존에 등록된 파일은 그대로 두고, 성공하면 반대 형식의 이전 등록본을 지운다.
 */
fun importUserDoc(context: Context, source: Uri, base: String, allowImage: Boolean): Result<Unit> = runCatching {
    val tmp = File(context.filesDir, "$base.tmp")
    try {
        (context.contentResolver.openInputStream(source) ?: error("cannot open $source")).use { input -> tmp.outputStream().use { input.copyTo(it) } }
        val isPdf = tmp.inputStream().use { s -> ByteArray(4).also { s.read(it) }.decodeToString() == "%PDF" }
        val ext = if (isPdf) {
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { require(it.pageCount > 0) } }
            "pdf"
        } else {
            require(allowImage) { "image not allowed" }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "not an image" }
            "img"
        }
        check(tmp.renameTo(File(context.filesDir, "$base.$ext"))) { "cannot save $base" }
        File(context.filesDir, "$base.${if (ext == "pdf") "img" else "pdf"}").delete()
    } finally {
        tmp.delete()
    }
}

/** 큰 사진도 메모리를 넘지 않게 긴 변을 4096px 이하로 줄여 읽고, EXIF 회전을 반영한다. */
private fun decodeUserImage(file: File): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
    val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }))
    val degrees = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
}

/** 예배 순서: 사용자가 등록한 파일(PDF 또는 이미지)을 연다. 앱에는 내장본이 없다. */
@Composable
fun ServiceOrderScreen() {
    val context = LocalContext.current
    val custom = remember { userDocFile(context, SERVICE_ORDER_BASE) }
    when {
        custom?.extension == "pdf" -> PdfAssetScreen("@user/${custom.name}")
        custom != null -> ImageAssetScreen("@user/${custom.name}", "예배 순서")
        else -> DocumentError()
    }
}

const val SERVICE_ORDER_BASE = "service-order-custom"

/** 예전 버전은 선교 일정 PDF를 앱에 내장하고 캐시로 복사해 두었다. 개인 정보가 남지 않게 지운다. */
fun purgeLegacyScheduleCache(context: Context) {
    context.cacheDir.listFiles()?.filter { it.name.contains("_schedule.pdf") }?.forEach { it.delete() }
}

private fun openPdf(context: Context, asset: String): PdfHolder {
    if (asset.startsWith("@user/")) {
        val file = userPdfFile(context, asset)
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfHolder(file, fd, PdfRenderer(fd))
    }
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
