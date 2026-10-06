package org.philmission.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 입력한 유튜브 링크를 검사한다. 비어 있으면 "" (선택 항목), 형식이 맞지 않으면 null. */
fun normalizeYoutube(input: String): String? {
    val text = input.trim()
    if (text.isEmpty()) return ""
    val url = if (text.startsWith("http://") || text.startsWith("https://")) text else "https://$text"
    val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return null
    return if (host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")) url else null
}

@Composable
fun YoutubeButton(url: String) {
    val context = LocalContext.current
    val online = rememberIsOnline()
    Button(
        onClick = {
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            catch (_: ActivityNotFoundException) { Toast.makeText(context, "링크를 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show() }
        },
        enabled = online,
    ) { Text("유튜브에서 듣기") }
    if (!online) Text("인터넷에 연결되어 있지 않아 사용할 수 없습니다.", style = MaterialTheme.typography.bodySmall)
}

private fun displayName(context: android.content.Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: "선택한 파일"

/** 찬양 추가: 제목 + 악보·가사 파일(PDF/JPG/PNG) + 선택 유튜브 링크. */
@Composable
fun AddItemDialog(users: UserStore, kind: String, onDismiss: () -> Unit) {
    val label = if (kind == "song") "찬양" else "말씀"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var youtube by rememberSaveable { mutableStateOf("") }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) { uri = picked; fileName = displayName(context, picked); error = "" }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("$label 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("$label 제목") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { picker.launch(arrayOf("image/jpeg", "image/png", "application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (uri == null) (if (kind == "song") "악보·가사 파일 선택 (PDF/JPG/PNG)" else "말씀 파일 선택 (PDF/JPG/PNG)") else fileName, maxLines = 1)
                }
                if (kind == "song") OutlinedTextField(youtube, { youtube = it }, label = { Text("유튜브 링크 (선택)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && uri != null && !saving, onClick = {
                val link = normalizeYoutube(youtube)
                val source = uri
                if (link == null) { error = "유튜브 링크 형식을 확인해 주세요."; return@TextButton }
                if (source == null) return@TextButton
                saving = true
                scope.launch {
                    val base = "$kind-${System.currentTimeMillis()}"
                    val result = withContext(Dispatchers.IO) { importUserDoc(context, source, base, allowImage = true) }
                    if (result.isSuccess) {
                        users.saveUserSong(UserSong(title = title.trim(), youtube = link, fileBase = base, createdAt = System.currentTimeMillis(), kind = kind))
                        onDismiss()
                    } else {
                        error = "파일을 열 수 없어 등록하지 못했습니다. 다른 파일을 선택해 주세요."
                        saving = false
                    }
                }
            }) { Text("등록") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("취소") } },
    )
}

/** 사용자가 등록한 찬양·말씀: 유튜브 버튼(있을 때), 삭제, 파일 보기. */
@Composable
fun UserSongScreen(song: UserSong, users: UserStore, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    val file = remember(song.fileBase) { userDocFile(context, song.fileBase) }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("${song.title} 삭제") },
        text = { Text("등록한 ${if (song.kind == "song") "찬양" else "말씀"}을 앱에서 삭제합니다. 폰에 있는 원본 파일은 그대로 남습니다.") },
        confirmButton = { TextButton(onClick = { confirm = false; scope.launch { users.deleteUserSong(song); onDeleted() } }) { Text("삭제") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } },
    )
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(song.title, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (song.youtube.isNotBlank()) YoutubeButton(song.youtube)
                OutlinedButton(onClick = { confirm = true }) { Text("삭제") }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                file == null -> DocumentError()
                file.extension == "pdf" -> PdfAssetScreen("@user/${file.name}")
                else -> ImageAssetScreen("@user/${file.name}", song.title)
            }
        }
    }
}
