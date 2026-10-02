package org.philmission.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class Phrase(val id: String, val category: String, val tl: String, val en: String, val ko: String, val pronunciation: String) {
    fun text(language: String) = if (language == "en") en else tl
    fun matches(query: String) = query.isBlank() || listOf(tl, en, ko).any { it.contains(query.trim(), ignoreCase = true) }
}

data class Sentence(val id: String, val tl: String, val en: String, val ko: String, val pron: String) {
    fun text(language: String) = if (language == "en") en else tl
}
data class Worship(val id: String, val title: String, val source: String, val sentences: List<Sentence>)
data class SongLine(val chords: List<String>, val text: String, val pron: String, val ko: String)
data class Song(val id: String, val title: String, val titleKo: String, val singingLanguage: String, val category: String, val source: String, val lines: List<SongLine>, val youtube: String = "") {
    val hasChords get() = lines.any { it.chords.isNotEmpty() }
}
data class Verse(val ref: String, val tl: String, val en: String, val ko: String, val pron: String) {
    fun text(language: String) = if (language == "en") en else tl
}
data class GospelCard(val id: String, val tl: String, val en: String, val ko: String, val pron: String, val guide: String, val verse: Verse? = null) {
    fun text(language: String) = if (language == "en") en else tl
}
data class BaseContact(val id: String, val name: String, val phone: String, val memo: String, val source: String, val checkedOn: String)
data class FieldDocument(val id: String, val type: String, val title: String, val asset: String, val docDate: String, val note: String)

data class Content(
    val version: String,
    val reviewStatus: String,
    val phrases: List<Phrase>,
    val worship: List<Worship>,
    val songs: List<Song>,
    val gospelCards: List<GospelCard>,
    val gospelPrayer: GospelCard,
    val contacts: List<BaseContact>,
    val fieldDocuments: List<FieldDocument>,
    val messageTitle: String,
    val messageBlocks: List<String>,
    val fileHashes: Map<String, String>,
) {
    companion object {
        fun load(context: Context): Content {
            val json = JSONObject(context.assets.open("content.json").bufferedReader().use { it.readText() })
            val document = json.getJSONArray("documents").getJSONObject(0)
            val gospel = json.getJSONObject("gospel")
            val files = json.getJSONObject("files")
            return Content(
                version = json.getString("version"),
                reviewStatus = json.getString("reviewStatus"),
                phrases = json.getJSONArray("phrases").map { Phrase(it.getString("id"), it.getString("category"), it.getString("tl"), it.getString("en"), it.getString("ko"), it.getString("pronunciationTl")) },
                worship = json.getJSONArray("worship").map { w ->
                    Worship(w.getString("id"), w.getString("title"), w.getString("source"),
                        w.getJSONArray("sentences").map { Sentence(it.getString("id"), it.getString("tl"), it.getString("en"), it.getString("ko"), it.getString("pron")) })
                },
                songs = json.getJSONArray("songs").map { s ->
                    Song(s.getString("id"), s.getString("title"), s.getString("titleKo"), s.getString("singingLanguage"), s.getString("category"), s.getString("source"),
                        s.getJSONArray("lines").map { l ->
                            SongLine(l.getJSONArray("chords").let { c -> (0 until c.length()).map { c.getString(it) } }, l.getString("text"), l.getString("pron"), l.getString("ko"))
                        }, s.optString("youtube", ""))
                },
                gospelCards = gospel.getJSONArray("cards").map(::gospelCard),
                gospelPrayer = gospelCard(gospel.getJSONObject("prayer")),
                contacts = json.getJSONArray("contacts").map { BaseContact(it.getString("id"), it.getString("name"), it.getString("phone"), it.getString("memo"), it.getString("source"), it.getString("checkedOn")) },
                fieldDocuments = json.getJSONArray("fieldDocuments").map { FieldDocument(it.getString("id"), it.getString("type"), it.getString("title"), it.getString("asset"), it.getString("docDate"), it.getString("note")) },
                messageTitle = document.getString("title"),
                messageBlocks = document.getJSONArray("blocks").let { b -> (0 until b.length()).map { b.getString(it) } },
                fileHashes = files.keys().asSequence().associateWith { files.getString(it) },
            )
        }

        private fun gospelCard(o: JSONObject) = GospelCard(o.getString("id"), o.getString("tl"), o.getString("en"), o.getString("ko"), o.getString("pron"), o.optString("guide", ""),
            o.optJSONObject("verse")?.let { Verse(it.getString("ref"), it.getString("tl"), it.getString("en"), it.getString("ko"), it.getString("pron")) })
    }
}

private fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> = (0 until length()).map { transform(getJSONObject(it)) }

fun assetSha256(context: Context, name: String): String? = runCatching {
    val digest = MessageDigest.getInstance("SHA-256")
    context.assets.open(name).use { input ->
        val buffer = ByteArray(8192)
        while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}.getOrNull()
