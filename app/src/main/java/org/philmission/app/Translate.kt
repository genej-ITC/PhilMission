package org.philmission.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

private fun speechTag(lang: String) = when (lang) { "ko" -> "ko-KR"; "en" -> "en-US"; else -> "fil-PH" }
private fun mlKitLang(lang: String) = when (lang) { "ko" -> TranslateLanguage.KOREAN; "en" -> TranslateLanguage.ENGLISH; else -> TranslateLanguage.TAGALOG }
private fun langName(lang: String) = when (lang) { "ko" -> "한국어"; "en" -> "영어"; else -> "따갈로그어" }
/** 선택한 언어 쌍("tl" 또는 "en")에서 [from]의 번역 대상 언어. 한국어는 상대 언어로, 상대 언어는 한국어로. */
private fun targetOf(from: String, partner: String) = if (from == "ko") partner else "ko"

private fun speechErrorMessage(code: Int) = when (code) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "음성을 알아듣지 못했습니다. 다시 눌러 말씀해 주세요."
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> "네트워크 문제로 음성 인식에 실패했습니다."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요합니다."
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "이 기기에서 해당 언어 음성 인식을 쓸 수 없습니다."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성 인식이 사용 중입니다. 잠시 후 다시 시도해 주세요."
    else -> "음성 인식 오류 ($code)"
}

/** 음성 → 실시간 텍스트 → 한국어↔따갈로그어 번역. 음성 인식은 인터넷이 있을 때만 켠다. */
@Composable
fun TranslateScreen(size: Float) {
    val context = LocalContext.current
    val online = rememberIsOnline()
    var listening by remember { mutableStateOf<String?>(null) }
    var partner by rememberSaveable { mutableStateOf("tl") }
    var srcLang by rememberSaveable { mutableStateOf("ko") }
    var sourceText by rememberSaveable { mutableStateOf("") }
    var resultText by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val translators = remember { mutableMapOf<String, Translator>() }
    val ticket = remember { intArrayOf(0) }
    val recognizer = remember { if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null }
    DisposableEffect(Unit) {
        onDispose { recognizer?.destroy(); translators.values.forEach { it.close() } }
    }

    fun translate(text: String, from: String) {
        if (text.isBlank()) return
        val to = targetOf(from, partner)
        val translator = translators.getOrPut("$from>$to") {
            Translation.getClient(TranslatorOptions.Builder()
                .setSourceLanguage(mlKitLang(from)).setTargetLanguage(mlKitLang(to)).build())
        }
        val mine = ++ticket[0]
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { if (mine == ticket[0]) { resultText = it; status = if (listening != null) "듣는 중…" else "" } }
                    .addOnFailureListener { if (mine == ticket[0]) status = "번역에 실패했습니다." }
            }
            .addOnFailureListener { status = "번역 모델을 받지 못했습니다. 인터넷 연결을 확인해 주세요." }
    }

    fun start(lang: String) {
        val engine = recognizer ?: return
        srcLang = lang; sourceText = ""; resultText = ""; status = "듣는 중…"; listening = lang
        engine.setRecognitionListener(object : RecognitionListener {
            private fun best(bundle: Bundle?) = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            override fun onPartialResults(partialResults: Bundle?) { best(partialResults).takeIf { it.isNotBlank() }?.let { sourceText = it; translate(it, lang) } }
            override fun onResults(results: Bundle?) {
                listening = null
                best(results).takeIf { it.isNotBlank() }?.let { sourceText = it; translate(it, lang) }
                if (status == "듣는 중…") status = ""
            }
            override fun onError(error: Int) { listening = null; status = speechErrorMessage(error) }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        engine.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechTag(lang))
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true))
    }

    var pendingLang by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingLang?.let { start(it) } else status = "마이크 권한이 있어야 통역을 쓸 수 있습니다."
        pendingLang = null
    }
    fun toggle(lang: String) {
        if (listening != null) { recognizer?.stopListening(); return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start(lang)
        else { pendingLang = lang; permission.launch(Manifest.permission.RECORD_AUDIO) }
    }

    val canUse = online && recognizer != null
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("버튼을 누르고 말씀하세요. 말하는 동안 글자와 번역이 나타납니다. 다시 누르면 멈춥니다.", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((code, label) in listOf("tl" to "한국어↔따갈로그어", "en" to "한국어↔영어")) {
                FilterChip(
                    selected = partner == code,
                    onClick = { if (partner != code) { partner = code; srcLang = "ko"; sourceText = ""; resultText = ""; status = ""; ticket[0]++ } },
                    enabled = listening == null,
                    label = { Text(label) },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (lang in listOf("ko", partner)) {
                val label = "${langName(lang)}로 말하기"
                val active = listening == lang
                Button(
                    onClick = { toggle(lang) },
                    enabled = canUse && (listening == null || active),
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                ) { Text(if (active) "듣는 중… (누르면 끝)" else label) }
            }
        }
        if (recognizer == null) Text("이 기기에는 음성 인식 서비스가 없습니다.", color = MaterialTheme.colorScheme.error)
        else if (!online) Text("인터넷에 연결되어 있지 않아 사용할 수 없습니다.", color = MaterialTheme.colorScheme.error)
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)
        if (sourceText.isNotBlank()) {
            Text(langName(srcLang), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(sourceText, fontSize = (size * 0.85f).sp, lineHeight = (size * 1.2f).sp)
            Text(langName(targetOf(srcLang, partner)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(resultText.ifBlank { "…" }, fontSize = (size * 1.3f).sp, lineHeight = (size * 1.7f).sp)
        }
        Text("자동 번역은 틀릴 수 있습니다. 중요한 내용은 현지인에게 확인하세요. 음성은 구글 음성 인식 서비스로 전송될 수 있습니다.", style = MaterialTheme.typography.bodySmall)
    }
}
