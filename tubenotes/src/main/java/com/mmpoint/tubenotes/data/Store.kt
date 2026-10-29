package com.mmpoint.tubenotes.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Single source of truth. Data lives in memory as a StateFlow and is written to
 * `files/tubenotes.json` (atomically, via a temp file) after every change.
 */
object Store {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val writer = Executors.newSingleThreadExecutor()
    private var file: File? = null
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, "tubenotes.json")
        _data.value = if (f.exists()) {
            runCatching { json.decodeFromString(AppData.serializer(), f.readText()) }
                .getOrElse {
                    // Keep the unreadable file for recovery instead of overwriting it.
                    f.copyTo(File(context.filesDir, "tubenotes.corrupt.${System.currentTimeMillis()}.json"), true)
                    AppData()
                }
        } else AppData()
        file = f
    }

    val current: AppData get() = _data.value

    @Synchronized
    fun update(transform: (AppData) -> AppData) {
        val next = transform(_data.value)
        _data.value = next
        val f = file ?: return
        val text = json.encodeToString(AppData.serializer(), next)
        writer.execute {
            val tmp = File(f.parentFile, "tubenotes.json.tmp")
            tmp.writeText(text)
            tmp.renameTo(f)
        }
    }

    fun entry(id: String): LogEntry? = current.entries.firstOrNull { it.id == id }

    fun updateEntry(id: String, transform: (LogEntry) -> LogEntry) = update { d ->
        d.copy(entries = d.entries.map { if (it.id == id) transform(it).copy(updatedAt = System.currentTimeMillis()) else it })
    }

    /** Returns the existing log entry for this video, or adds a new one at the top. */
    fun entryFor(videoId: String, url: String): LogEntry {
        current.entries.firstOrNull { it.videoId == videoId }?.let { return it }
        val e = LogEntry(id = newId(), videoId = videoId, url = url, createdAt = System.currentTimeMillis())
        update { d -> d.copy(entries = listOf(e) + d.entries) }
        return e
    }

    fun delete(id: String) = update { d -> d.copy(entries = d.entries.filterNot { it.id == id }) }

    /** The log without the API key, for exporting. */
    fun exportJson(): String = json.encodeToString(AppData.serializer(), current.copy(apiKey = ""))

    /** Tests only: replace in-memory state. */
    fun resetForTest(data: AppData = AppData()) {
        _data.value = data
    }

    fun newId(): String = UUID.randomUUID().toString().take(12)
}
