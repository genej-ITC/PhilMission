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

private val CATEGORIES = linkedMapOf(
    "all" to "전체", "greeting" to "인사·자기소개", "children" to "어린이 사역",
    "meal" to "배식·식사", "visit" to "심방·위로", "emergency" to "응급·병원",
)

@Composable
private fun NavCard(title: String, subtitle: String = "", onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Section(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

// ---- 홈 탭 ----

@Composable
fun WorshipHome(content: Content, push: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Section("예배문·기도문") }
        items(content.worship, key = { it.id }) { NavCard(it.title) { push("w:${it.id}") } }
        item { Section("찬양") }
        items(content.songs, key = { it.id }) { NavCard(it.titleKo, it.title + if (it.hasChords) " · 코드" else "") { push("s:${it.id}") } }
        item { Section("가정심방") }
        item { NavCard("가정심방 말씀", content.messageTitle) { push("msg") } }
        item { NavCard("예배 순서", "제공 이미지 · 확대해서 보기") { push("img") } }
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
        NavCard("현장 자료", "선교 일정 · 가정심방 말씀 · 예배 순서") { push("docs") }
        NavCard("연락처", "기본 연락처 · 개인 연락처") { push("contacts") }
        NavCard("현장 준비 점검", "내장 파일과 콘텐츠 버전 확인") { push("check") }
        NavCard("설정", "글자 크기 · 화면 켜짐 유지") { push("settings") }
        Text("자료는 앱에 포함되어 있습니다. 출국 전 비행기 모드에서 확인하세요.", style = MaterialTheme.typography.bodySmall)
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
        item { Text("출처: ${doc.source}", style = MaterialTheme.typography.bodySmall) }
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
            if (song.youtube.isNotBlank()) {
                val context = LocalContext.current
                val online = rememberIsOnline()
                Button(
                    onClick = {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(song.youtube))) }
                        catch (_: ActivityNotFoundException) { Toast.makeText(context, "링크를 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show() }
                    },
                    enabled = online,
                ) { Text("유튜브에서 듣기") }
                if (!online) Text("인터넷에 연결되어 있지 않아 사용할 수 없습니다.", style = MaterialTheme.typography.bodySmall)
            }
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
        item { Text("제공 원문 · 따갈로그어 / 한국어 / 독음이 섞여 있습니다. 영어 번역과 문단별 대응은 준비 중입니다.", style = MaterialTheme.typography.bodySmall) }
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

@Composable
fun DocsScreen(content: Content, push: (String) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        content.fieldDocuments.forEach { doc ->
            val route = when (doc.type) { "pdf" -> "pdf:${doc.asset}"; "image" -> "img"; else -> "msg" }
            NavCard(doc.title, listOf(doc.docDate.takeIf { it.isNotBlank() }?.let { "문서 기준일: $it" }, doc.note.takeIf { it.isNotBlank() }).filterNotNull().joinToString("\n")) { push(route) }
        }
        Text("자료는 앱 재설치(APK 업데이트)로만 갱신됩니다. 자동으로 바뀌지 않습니다.", style = MaterialTheme.typography.bodySmall)
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
    var editing by remember { mutableStateOf<PersonalContact?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Section("기본 연락처") }
        item { Text("통화에는 사용 가능한 통신 환경이 필요합니다. 번호는 출국 전 공식 자료로 다시 확인하세요.", style = MaterialTheme.typography.bodySmall) }
        items(content.contacts, key = { it.id }) { c ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (c.phone.isBlank()) "전화번호 없음" else c.phone)
                    if (c.memo.isNotBlank()) Text(c.memo)
                    Text("출처: ${c.source}${if (c.checkedOn.isBlank()) " · 확인일 미기록" else " · 확인일 ${c.checkedOn}"}", style = MaterialTheme.typography.bodySmall)
                    PhoneActions(c.phone)
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
}

@Composable
private fun ContactDialog(initial: PersonalContact, onDismiss: () -> Unit, onSave: (PersonalContact) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var phone by remember { mutableStateOf(initial.phone) }
    var memo by remember { mutableStateOf(initial.memo) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "연락처 추가" else "연락처 수정") },
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
                val problem = validateContact(name, phone)
                if (problem != null) error = problem
                else onSave(initial.copy(name = name.trim(), phone = phone.trim(), memo = memo.trim()))
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

// ---- 점검 / 설정 ----

@Composable
fun ReadinessScreen(content: Content) {
    val context = LocalContext.current
    val results by produceState<List<Triple<String, Boolean, String>>?>(null) {
        value = withContext(Dispatchers.IO) {
            content.fileHashes.map { (name, expected) ->
                val actual = assetSha256(context, name)
                Triple(name, actual == expected, if (actual == null) "파일 없음" else if (actual != expected) "해시 불일치" else "정상")
            }
        }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("콘텐츠 버전 ${content.version}", style = MaterialTheme.typography.titleMedium) }
        item { Text("검수 상태: ${if (content.reviewStatus == "approved") "검수 완료" else "검수 대기 (개발판)"}") }
        item { Text("텍스트: 회화 ${content.phrases.size}문장 · 예배문 ${content.worship.size}종 · 찬양 ${content.songs.size}곡 · 전도 카드 ${content.gospelCards.size}장 (내장)") }
        item { Text("문장 음성: 포함된 파일 없음 (선택 기능 — 없어도 오류가 아닙니다)") }
        if (!content.scheduleSanitized) item { Text("주의: 선교 일정 PDF는 개인 사정이 포함된 원본입니다. 배포 전 정리본으로 교체해야 합니다.", color = MaterialTheme.colorScheme.error) }
        item { Section("필수 파일 점검") }
        val list = results
        if (list == null) item { CircularProgressIndicator() }
        else items(list) { (name, ok, message) ->
            Text("${if (ok) "✓" else "✗"} $name — $message", color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
        }
        if (list != null && list.any { !it.second }) item { Text("필수 파일에 문제가 있습니다. 앱을 다시 설치하거나 배포 담당자에게 문의해 주세요.", color = MaterialTheme.colorScheme.error) }
        item { Section("출국 전 점검") }
        item { Text("1. 비행기 모드를 켜고 앱을 완전히 종료한 뒤 다시 실행합니다.\n2. 예배문·찬양·회화·전도·연락처·현장 자료 3종을 각각 열어 봅니다.\n3. 회화에서 큰 글씨 모드가 열리는지 확인합니다.\n\n이 점검은 번역의 정확성이나 연락처의 최신성을 보장하지 않습니다.") }
    }
}

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
