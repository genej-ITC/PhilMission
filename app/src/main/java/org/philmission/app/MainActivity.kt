package org.philmission.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.graphics.Color as AColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 앱 테마는 항상 밝으므로 시스템 다크 모드여도 상태바 아이콘을 어둡게 유지한다.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT))
        val users = UserStore(applicationContext)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF246454), secondary = Color(0xFF526B5E))) {
                var content by remember { mutableStateOf<Content?>(null) }
                var error by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    val result = withContext(Dispatchers.IO) { runCatching { Content.load(applicationContext) } }
                    content = result.getOrNull()
                    error = result.isFailure
                }
                Surface(Modifier.fillMaxSize()) {
                    when {
                        error -> Column(Modifier.padding(24.dp).statusBarsPadding()) {
                            Text("자료를 열 수 없습니다.", style = MaterialTheme.typography.headlineSmall)
                            Text("앱을 다시 설치하거나 배포 담당자에게 문의해 주세요.")
                        }
                        content == null -> Box(Modifier.padding(32.dp)) { CircularProgressIndicator() }
                        else -> MissionApp(content!!, users)
                    }
                }
            }
        }
    }
}

/** 읽기 화면에 있는 동안만 화면 꺼짐을 막는다. */
@Composable
fun KeepAwake(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

private val TABS = listOf("예배", "회화", "전도", "더보기")
private val TAB_ICONS = listOf("◇", "◎", "♡", "≡")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionApp(content: Content, users: UserStore) {
    val language by users.language.collectAsState(initial = "tl")
    val favorites by users.favorites.collectAsState(initial = emptyList())
    val fontStep by users.fontStep.collectAsState(initial = 1)
    val keepAwake by users.keepAwake.collectAsState(initial = true)
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // 화면 경로 스택. 회전·프로세스 복원 시에도 유지되도록 문자열로 저장한다.
    var stack by rememberSaveable { mutableStateOf("") }
    val route = stack.substringAfterLast('|', stack)
    fun push(next: String) { stack = if (stack.isEmpty()) next else "$stack|$next" }
    fun pop() { stack = if ('|' in stack) stack.substringBeforeLast('|') else "" }
    BackHandler(stack.isNotEmpty()) { pop() }

    val base = when (fontStep) { 0 -> 20f; 2 -> 28f; else -> 24f }
    val reading = route.startsWith("w:") || route.startsWith("s:") || route.startsWith("p:") ||
        route.startsWith("large:") || route == "msg" || route == "img" || route.startsWith("pdf:") || route == "gospel" || route == "prayer"

    val title = when {
        route.startsWith("large:") -> "보여주기"
        route.startsWith("w:") -> content.worship.find { it.id == route.removePrefix("w:") }?.title ?: "예배문"
        route.startsWith("s:") -> content.songs.find { it.id == route.removePrefix("s:") }?.titleKo ?: "찬양"
        route.startsWith("p:") -> "회화"
        route == "msg" -> "가정심방 말씀"
        route == "img" -> "예배 순서"
        route.startsWith("pdf:") -> "선교 일정"
        route == "gospel" -> "복음 안내 카드"
        route == "prayer" -> "영접 기도문"
        route == "contacts" -> "연락처"
        route == "docs" -> "현장 자료"
        route == "check" -> "현장 준비 점검"
        route == "settings" -> "설정"
        else -> "PhilMission"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { if (stack.isNotEmpty()) TextButton(onClick = { pop() }) { Text("뒤로") } },
                actions = {
                    TextButton(onClick = { scope.launch { users.language(if (language == "tl") "en" else "tl") } }) {
                        Text(if (language == "tl") "따갈로그어" else "English")
                    }
                },
            )
        },
        bottomBar = {
            if (stack.isEmpty()) NavigationBar {
                TABS.forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Text(TAB_ICONS[index]) }, label = { Text(label) })
                }
            }
        },
    ) { inset ->
        Column(Modifier.padding(inset).fillMaxSize()) {
            KeepAwake(reading && keepAwake)
            if (!route.startsWith("large:") && !route.startsWith("pdf:") && route != "img") {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("개발판 · 번역과 독음은 현지 검수 전입니다", Modifier.fillMaxWidth().padding(8.dp), fontSize = 12.sp)
                }
            }
            when {
                route.startsWith("large:") -> content.phrases.find { it.id == route.removePrefix("large:") }?.let { LargePhrase(it, language) }
                route.startsWith("p:") -> content.phrases.find { it.id == route.removePrefix("p:") }?.let {
                    PhraseDetail(it, language, base, it.id in favorites, { fav -> scope.launch { users.favorite(it.id, fav) } }, { push("large:${it.id}") })
                }
                route.startsWith("w:") -> content.worship.find { it.id == route.removePrefix("w:") }?.let { WorshipScreen(it, language, base) }
                route.startsWith("s:") -> content.songs.find { it.id == route.removePrefix("s:") }?.let { SongScreen(it, base) }
                route == "msg" -> MessageScreen(content, base)
                route == "img" -> ImageAssetScreen("service-order.jpg", "예배 순서")
                route.startsWith("pdf:") -> PdfAssetScreen(route.removePrefix("pdf:"))
                route == "gospel" -> GospelScreen(content.gospelCards, language, base)
                route == "prayer" -> GospelScreen(listOf(content.gospelPrayer), language, base)
                route == "contacts" -> ContactsScreen(content, users)
                route == "docs" -> DocsScreen(content, ::push)
                route == "check" -> ReadinessScreen(content)
                route == "settings" -> SettingsScreen(fontStep, keepAwake, { scope.launch { users.fontStep(it) } }, { scope.launch { users.keepAwake(it) } })
                tab == 0 -> WorshipHome(content, ::push)
                tab == 1 -> PhraseList(content, language, favorites, ::push)
                tab == 2 -> GospelHome(::push)
                else -> MoreHome(::push)
            }
        }
    }
}
