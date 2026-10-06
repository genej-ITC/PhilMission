package org.philmission.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CATEGORIES = linkedMapOf(
    "all" to "전체", "greeting" to "인사·자기소개", "children" to "어린이 사역",
    "meal" to "배식·식사", "visit" to "심방·위로", "emergency" to "응급·병원",
)

@Composable
private fun NavCard(title: String, subtitle: String = "", actions: (@Composable RowScope.() -> Unit)? = null, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            if (actions != null) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
        }
    }
}

@Composable
private fun Section(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

// ---- 홈 탭 ----

@Composable
fun WorshipHome(content: Content, userItems: List<UserSong>, push: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Section("예배문·기도문") }
        items(content.worship, key = { it.id }) { NavCard(it.title) { push("w:${it.id}") } }
        item { Section("찬양") }
        items(content.songs, key = { it.id }) { NavCard(it.titleKo, it.title + if (it.hasChords) " · 코드" else "") { push("s:${it.id}") } }
        items(userItems.filter { it.kind == "song" }, key = { "us-${it.id}" }) { NavCard(it.title, "내가 추가한 찬양") { push("us:${it.id}") } }
        item { Section("말씀") }
        item { NavCard("가정심방 말씀", content.messageTitle) { push("msg") } }
        item { NavCard("예배 순서", "제공 이미지 · 확대해서 보기") { push("img") } }
        items(userItems.filter { it.kind == "message" }, key = { "us-${it.id}" }) { NavCard(it.title, "내가 추가한 말씀") { push("us:${it.id}") } }
    }
}

@Composable
fun GospelHome(push: (String) -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NavCard("복음 안내 카드", "5장 · 이전/다음으로 넘기며 보여주기") { push("gospel") }
        NavCard("영접 기도문", "함께 기도하기") { push("prayer") }
    }
}

@Composable
fun MoreHome(push: (String) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NavCard("자료 추가", "찬양 · 말씀 · 선교 일정 · 예배 순서") { push("docs") }
        NavCard("연락처", "기본 연락처 · 개인 연락처") { push("contacts") }
        NavCard("설정", "글자 크기 · 화면 켜짐 유지") { push("settings") }
    }
}

// ---- 회화 ----

@Composable
fun PhraseList(content: Content, language: String, favorites: List<String>, push: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("all") }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    Column {
        Button(onClick = { push("translate") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text("음성 통역 (한국어 ↔ 따갈로그어)") }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, label = { Text("한국어 · 따갈로그어 · 영어 검색") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true,
        )
        Row(Modifier.padding(horizontal = 16.dp)) {
            Box {
                TextButton(onClick = { expanded = true }) { Text(CATEGORIES[category] ?: "전체") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    CATEGORIES.forEach { (key, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { category = key; expanded = false }) }
                }
            }
            FilterChip(selected = onlyFavorites, onClick = { onlyFavorites = !onlyFavorites }, label = { Text("즐겨찾기") })
        }
        val rows = content.phrases.filter { it.matches(query) && (category == "all" || it.category == category) && (!onlyFavorites || it.id in favorites) }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (rows.isEmpty()) item { Text("해당하는 문장이 없습니다.") }
            items(rows, key = { it.id }) { item ->
                Card(onClick = { push("p:${item.id}") }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text((if (item.id in favorites) "★ " else "") + item.ko, style = MaterialTheme.typography.titleMedium)
                        Text(item.text(language), fontSize = 18.sp)
                        if (language == "tl") Text(item.pronunciation, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PhraseDetail(phrase: Phrase, language: String, size: Float, favorite: Boolean, onFavorite: (Boolean) -> Unit, onLarge: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(phrase.text(language), fontSize = (if (language == "tl") size * 0.8f else size).sp, lineHeight = (if (language == "tl") size * 1.0f else size * 1.5f).sp)
        if (language == "tl") Text(phrase.pronunciation, color = MaterialTheme.colorScheme.primary, fontSize = size.sp, lineHeight = (size * 1.5f).sp)
        Text(phrase.ko, fontSize = (size * 0.85f).sp)
        Button(onClick = onLarge, modifier = Modifier.fillMaxWidth()) { Text("큰 글씨로 보여주기") }
        OutlinedButton(onClick = { onFavorite(!favorite) }) { Text(if (favorite) "즐겨찾기 해제" else "즐겨찾기 추가") }
    }
}

/** 현지인용 전체화면. 선택한 언어만 표시하고 한국어 뜻·독음은 숨긴다. */
@Composable
fun LargePhrase(phrase: Phrase, language: String) {
    Text(phrase.text(language), Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), fontSize = 44.sp, lineHeight = 58.sp)
}

// ---- 예배 / 찬양 / 말씀 ----

@Composable
fun WorshipScreen(doc: Worship, language: String, size: Float) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        items(doc.sentences, key = { it.id }) { s ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.text(language), fontSize = (if (language == "tl") size * 0.75f else size).sp, lineHeight = (if (language == "tl") size * 0.95f else size * 1.5f).sp)
                if (language == "tl") Text(s.pron, color = MaterialTheme.colorScheme.primary, fontSize = size.sp, lineHeight = (size * 1.5f).sp)
                Text(s.ko, fontSize = (size * 0.75f).sp)
            }
        }
    }
}

/** 인터넷 연결 여부. 연결이 바뀌면 바로 갱신한다. */
@Composable
internal fun rememberIsOnline(): Boolean {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    fun current(): Boolean = manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    var online by remember { mutableStateOf(current()) }
    DisposableEffect(manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = current() }
            override fun onLost(network: Network) { online = current() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { online = current() }
        }
        manager.registerDefaultNetworkCallback(callback)
        onDispose { manager.unregisterNetworkCallback(callback) }
    }
    return online
}

@Composable
fun SongScreen(song: Song, size: Float) {
    var showChords by rememberSaveable(song.id) { mutableStateOf(true) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Text(song.title, style = MaterialTheme.typography.headlineSmall)
            Text(if (song.singingLanguage == "tl") "가창 언어: 따갈로그어" else "가창 언어: 영어 (따갈로그어 가창 가사 없음)", style = MaterialTheme.typography.bodySmall)
            if (song.youtube.isNotBlank()) YoutubeButton(song.youtube)
            if (song.hasChords) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked = showChords, onCheckedChange = { showChords = it })
                Spacer(Modifier.width(8.dp)); Text("코드 표시")
            }
        }
        items(song.lines) { line ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (showChords && line.chords.isNotEmpty()) Text(line.chords.joinToString("   "), color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = (size * 0.85f).sp)
                Text(line.text, fontSize = (if (line.pron.isNotBlank()) size * 0.75f else size).sp, lineHeight = (if (line.pron.isNotBlank()) size * 0.95f else size * 1.4f).sp)
                if (line.pron.isNotBlank()) Text(line.pron, color = MaterialTheme.colorScheme.primary, fontSize = size.sp, lineHeight = (size * 1.4f).sp)
                Text(line.ko, fontSize = (size * 0.75f).sp)
            }
        }
        item { Text("출처: ${song.source}", style = MaterialTheme.typography.bodySmall) }
    }
}

private enum class BlockKind { KOREAN, TAGALOG, PRONUNCIATION }

/** 원문은 한국어/따갈로그어/독음 순서로 묶여 있다. 라틴 문자가 많은 블록은 따갈로그어, 그 직후 같은 개수의 한글 블록은 독음. */
private fun classifyMessageBlocks(blocks: List<String>): List<Pair<BlockKind, String>> {
    var tagalogRun = 0
    var pronunciationLeft = 0
    return blocks.map { text ->
        val latin = text.count { it in 'A'..'Z' || it in 'a'..'z' }
        val hangul = text.count { it in '가'..'힣' }
        if (latin > hangul) {
            tagalogRun++
            BlockKind.TAGALOG to text
        } else {
            if (tagalogRun > 0) { pronunciationLeft = tagalogRun; tagalogRun = 0 }
            if (pronunciationLeft > 0) { pronunciationLeft--; BlockKind.PRONUNCIATION to text } else BlockKind.KOREAN to text
        }
    }
}

@Composable
fun MessageScreen(content: Content, size: Float) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(content.messageTitle, style = MaterialTheme.typography.headlineSmall) }
        items(classifyMessageBlocks(content.messageBlocks)) { (kind, text) ->
            when (kind) {
                BlockKind.PRONUNCIATION -> Text(text, color = MaterialTheme.colorScheme.primary, fontSize = size.sp, lineHeight = (size * 1.5f).sp)
                else -> Text(text, fontSize = (size * 0.75f).sp, lineHeight = (size * 0.95f).sp)
            }
        }
    }
}

// ---- 전도 ----

@Composable
fun GospelScreen(cards: List<GospelCard>, language: String, size: Float) {
    var index by rememberSaveable(cards.size) { mutableIntStateOf(0) }
    val card = cards[index.coerceIn(0, cards.lastIndex)]
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (cards.size > 1) Text("${index + 1} / ${cards.size}", style = MaterialTheme.typography.titleMedium)
            Text(card.text(language), fontSize = (if (language == "tl") size * 0.75f else size * 1.3f).sp, lineHeight = (if (language == "tl") size * 0.95f else size * 1.9f).sp)
            if (language == "tl") Text(card.pron, color = MaterialTheme.colorScheme.primary, fontSize = (size * 1.3f).sp, lineHeight = (size * 1.9f).sp)
            Text(card.ko, fontSize = (size * 0.9f).sp, lineHeight = (size * 1.2f).sp)
            card.verse?.let { v ->
                Text(v.ref, style = MaterialTheme.typography.titleMedium)
                Text(v.text(language), fontSize = (if (language == "tl") size * 0.75f else size).sp, lineHeight = (if (language == "tl") size * 0.95f else size * 1.5f).sp)
                if (language == "tl") Text(v.pron, color = MaterialTheme.colorScheme.primary, fontSize = size.sp, lineHeight = (size * 1.5f).sp)
                Text(v.ko, fontSize = (size * 0.75f).sp, lineHeight = (size * 0.95f).sp)
            }
        }
        if (cards.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { index -= 1 }, enabled = index > 0) { Text("이전") }
            OutlinedButton(onClick = { index += 1 }, enabled = index < cards.lastIndex) { Text("다음") }
        }
    }
}

// ---- 현장 자료 ----

/** 앱에서 등록·교체할 수 있는 자료(type이 "-user"로 끝남)의 저장 파일 이름(확장자 제외). */
private fun userBase(doc: FieldDocument) =
    if (doc.type == "pdf-user") doc.asset.removePrefix("@user/").substringBeforeLast('.') else SERVICE_ORDER_BASE

@Composable
fun DocsScreen(content: Content, users: UserStore, push: (String) -> Unit) {
    var adding by remember { mutableStateOf<String?>(null) }
    adding?.let { AddItemDialog(users, it) { adding = null } }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) } // 등록·삭제 후 화면을 다시 그리기 위한 값
    var message by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<FieldDocument?>(null) }
    var confirmDelete by remember { mutableStateOf<FieldDocument?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val doc = pending
        pending = null
        if (uri != null && doc != null) scope.launch {
            val result = withContext(Dispatchers.IO) { importUserDoc(context, uri, userBase(doc), allowImage = doc.type == "image-user") }
            message = if (result.isSuccess) "${doc.title}을(를) 등록했습니다." else "파일을 열 수 없어 등록하지 못했습니다. 다른 파일을 선택해 주세요."
            version++
        }
    }
    confirmDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("${doc.title} 삭제") },
            text = { Text("등록된 파일을 앱에서 삭제합니다. 폰에 있는 원본 파일은 그대로 남습니다.") },
            confirmButton = { TextButton(onClick = { deleteUserDoc(context, userBase(doc)); confirmDelete = null; message = ""; version++ }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("취소") } },
        )
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { adding = "song" }, modifier = Modifier.weight(1f)) { Text("찬양 추가") }
            Button(onClick = { adding = "message" }, modifier = Modifier.weight(1f)) { Text("말씀 추가") }
        }
        // 가정심방 말씀(type "text")은 예배 탭에 고정으로 있으므로 여기에는 표시하지 않는다.
        content.fieldDocuments.filter { it.type != "text" }.forEach { doc ->
            if (doc.type == "pdf-user" || doc.type == "image-user") {
                val withImage = doc.type == "image-user"
                val mimes = if (withImage) arrayOf("image/jpeg", "image/png", "application/pdf") else arrayOf("application/pdf")
                val saved = remember(version) { userDocFile(context, userBase(doc))?.lastModified() }
                val savedText = saved?.let { "등록일: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Date(it))}" }
                if (saved != null) {
                    NavCard(doc.title, savedText.orEmpty(), actions = {
                        OutlinedButton(onClick = { pending = doc; picker.launch(mimes) }) { Text(if (withImage) "다른 파일로 바꾸기" else "다른 PDF로 바꾸기") }
                        OutlinedButton(onClick = { confirmDelete = doc }) { Text("삭제") }
                    }) { push(if (withImage) "img" else "pdf:${doc.asset}") }
                } else {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(doc.title, style = MaterialTheme.typography.titleLarge)
                            Text(if (withImage) "등록된 ${doc.title}가 없습니다. PDF/JPG/PNG 파일을 등록해 주세요." else "등록된 ${doc.title}이 없습니다. PDF 파일을 등록해 주세요.", style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { pending = doc; picker.launch(mimes) }) { Text(if (withImage) "예배 순서 등록" else "선교 일정 등록") }
                        }
                    }
                }
                return@forEach
            }
            val route = when (doc.type) { "pdf" -> "pdf:${doc.asset}"; "image" -> "img"; else -> "msg" }
            NavCard(doc.title, listOf(doc.docDate.takeIf { it.isNotBlank() }?.let { "문서 기준일: $it" }, doc.note.takeIf { it.isNotBlank() }).filterNotNull().joinToString("\n")) { push(route) }
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---- 연락처 ----

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("phone", text))
    Toast.makeText(context, "번호를 복사했습니다.", Toast.LENGTH_SHORT).show()
}

/** 자동 발신하지 않고 전화 앱만 연다. 실패하면 번호를 복사한다. */
private fun dial(context: Context, phone: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone.filter { it.isDigit() || it == '+' }))))
    } catch (_: ActivityNotFoundException) {
        copyToClipboard(context, phone)
    }
}

@Composable
private fun PhoneActions(phone: String) {
    val context = LocalContext.current
    if (phone.isBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { dial(context, phone) }) { Text("전화 앱 열기") }
        OutlinedButton(onClick = { copyToClipboard(context, phone) }) { Text("번호 복사") }
    }
}

@Composable
fun ContactsScreen(content: Content, users: UserStore) {
    val personal by users.personalContacts.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val overrides by users.baseContactOverrides.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<PersonalContact?>(null) }
    var editingBase by remember { mutableStateOf<BaseContact?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Section("기본 연락처") }
        items(content.contacts, key = { it.id }) { base ->
            val saved = overrides.find { it.id == base.id }
            val c = if (saved == null) base else base.copy(name = saved.name, phone = saved.phone, memo = saved.memo)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                    if (c.phone.isNotBlank()) Text(c.phone)
                    if (c.memo.isNotBlank()) Text(c.memo)
                    PhoneActions(c.phone)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editingBase = c }) { Text("수정") }
                        if (saved != null) OutlinedButton(onClick = { scope.launch { users.resetBaseContact(base.id) } }) { Text("기본값으로 되돌리기") }
                    }
                }
            }
        }
        item { Section("개인 연락처") }
        item { Button(onClick = { editing = PersonalContact(name = "", phone = "", memo = "") }) { Text("연락처 추가") } }
        if (personal.isEmpty()) item { Text("저장된 개인 연락처가 없습니다.") }
        items(personal, key = { it.id }) { c ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                    Text(c.phone)
                    if (c.memo.isNotBlank()) Text(c.memo)
                    PhoneActions(c.phone)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editing = c }) { Text("수정") }
                        OutlinedButton(onClick = { scope.launch { users.deleteContact(c) } }) { Text("삭제") }
                    }
                }
            }
        }
    }
    editing?.let { draft ->
        ContactDialog(draft, onDismiss = { editing = null }, onSave = { scope.launch { users.saveContact(it) }; editing = null })
    }
    editingBase?.let { base ->
        ContactDialog(
            PersonalContact(name = base.name, phone = base.phone, memo = base.memo), title = "기본 연락처 수정", phoneRequired = false,
            onDismiss = { editingBase = null },
            onSave = { scope.launch { users.saveBaseContact(BaseContactOverride(base.id, it.name, it.phone, it.memo)) }; editingBase = null },
        )
    }
}

@Composable
private fun ContactDialog(initial: PersonalContact, title: String? = null, phoneRequired: Boolean = true, onDismiss: () -> Unit, onSave: (PersonalContact) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var phone by remember { mutableStateOf(initial.phone) }
    var memo by remember { mutableStateOf(initial.memo) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title ?: if (initial.id == 0L) "연락처 추가" else "연락처 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("이름") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("전화번호") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(memo, { memo = it }, label = { Text("메모") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = validateContact(name, phone, phoneRequired)
                if (problem != null) error = problem
                else onSave(initial.copy(name = name.trim(), phone = phone.trim(), memo = memo.trim()))
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

// ---- 점검 / 설정 ----

@Composable
fun SettingsScreen(fontStep: Int, keepAwake: Boolean, onFont: (Int) -> Unit, onAwake: (Boolean) -> Unit) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("읽기 글자 크기", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("보통", "크게", "아주 크게").forEachIndexed { i, label ->
                FilterChip(selected = fontStep == i, onClick = { onFont(i) }, label = { Text(label) })
            }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(checked = keepAwake, onCheckedChange = onAwake)
            Spacer(Modifier.width(12.dp))
            Text("읽기 화면에서 화면 켜짐 유지")
        }
        Text("언어는 화면 위쪽 버튼으로 바꿉니다.", style = MaterialTheme.typography.bodySmall)
    }
}
