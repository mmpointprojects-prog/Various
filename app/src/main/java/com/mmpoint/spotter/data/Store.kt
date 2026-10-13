package com.mmpoint.spotter.data

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
 * `files/spotter.json` (atomically, via a temp file) after every change.
 */
object Store {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        prettyPrint = false
    }

    private val writer = Executors.newSingleThreadExecutor()
    private lateinit var file: File
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    @Volatile
    private var loaded = false

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        file = File(context.filesDir, "spotter.json")
        _data.value = if (file.exists()) {
            runCatching { json.decodeFromString(AppData.serializer(), file.readText()) }
                .getOrElse {
                    // Keep the unreadable file for recovery instead of overwriting it.
                    file.copyTo(File(context.filesDir, "spotter.corrupt.${System.currentTimeMillis()}.json"), true)
                    AppData()
                }
        } else AppData()
        loaded = true
    }

    val current: AppData get() = _data.value

    @Synchronized
    fun update(transform: (AppData) -> AppData) {
        val next = transform(_data.value)
        _data.value = next
        val text = json.encodeToString(AppData.serializer(), next)
        writer.execute {
            val tmp = File(file.parentFile, "spotter.json.tmp")
            tmp.writeText(text)
            tmp.renameTo(file)
        }
    }

    fun exportJson(): String = json.encodeToString(AppData.serializer(), _data.value)

    fun importJson(text: String) {
        val parsed = json.decodeFromString(AppData.serializer(), text)
        update { parsed }
    }

    /** Tests only: replace in-memory state. */
    fun resetForTest(data: AppData = AppData()) {
        _data.value = data
    }

    fun newId(): String = UUID.randomUUID().toString().take(12)
}
