package com.mmpoint.tubenotes.data

import com.mmpoint.tubenotes.net.Claude
import com.mmpoint.tubenotes.net.FetchedTranscript
import com.mmpoint.tubenotes.net.VideoInfo
import com.mmpoint.tubenotes.net.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Runs the slow work (YouTube + Claude) outside any screen, so leaving a screen or rotating the
 * phone never loses a result: everything lands in [Store] when it finishes.
 */
object Work {
    // Swappable for tests.
    var dispatcherScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var fetchInfo: (String) -> VideoInfo? = { YouTube.fetchInfo(it) }
    var fetchTranscript: (String) -> FetchedTranscript = { YouTube.fetchTranscript(it) }
    var writeNotes: (apiKey: String, kind: OutputKind, title: String, channel: String, transcript: String) -> String =
        { key, kind, title, channel, text -> Claude.write(key, kind, title, channel, text) }

    /** Entry id → the output currently being made for it. */
    private val _busy = MutableStateFlow<Map<String, OutputKind>>(emptyMap())
    val busy: StateFlow<Map<String, OutputKind>> = _busy.asStateFlow()

    /** Entry id → last error message, cleared on the next attempt. */
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()

    /** Adds the title/channel for a newly shared video without blocking the screen. */
    fun fillInfo(entryId: String) {
        val e = Store.entry(entryId) ?: return
        if (e.title.isNotBlank()) return
        dispatcherScope.launch {
            val info = runCatching { fetchInfo(e.videoId) }.getOrNull() ?: return@launch
            Store.updateEntry(entryId) { if (it.title.isBlank()) it.copy(title = info.title, channel = info.channel) else it }
        }
    }

    fun request(entryId: String, kind: OutputKind, force: Boolean = false) {
        val start = Store.entry(entryId) ?: return
        Store.updateEntry(entryId) { if (kind in it.requested) it else it.copy(requested = it.requested + kind) }
        if (!force && start.output(kind) != null) return
        if (_busy.value.containsKey(entryId)) return
        _busy.update { it + (entryId to kind) }
        _errors.update { it - entryId }
        dispatcherScope.launch {
            try {
                var e = Store.entry(entryId) ?: return@launch
                if (!e.hasTranscript) {
                    val t = fetchTranscript(e.videoId)
                    Store.updateEntry(entryId) {
                        it.copy(
                            title = t.title.ifBlank { it.title },
                            channel = t.channel.ifBlank { it.channel },
                            language = t.language,
                            segments = t.segments,
                            pastedTranscript = false,
                        )
                    }
                    e = Store.entry(entryId) ?: return@launch
                }
                if (kind != OutputKind.TRANSCRIPT) {
                    val text = writeNotes(Store.current.apiKey, kind, e.title, e.channel, Transcripts.plain(e.segments))
                    Store.updateEntry(entryId) {
                        if (kind == OutputKind.SUMMARY) it.copy(summary = text) else it.copy(bullets = text)
                    }
                }
            } catch (t: Throwable) {
                _errors.update { it + (entryId to (t.message ?: "Something went wrong. Try again.")) }
            } finally {
                _busy.update { it - entryId }
            }
        }
    }

    /** Uses a transcript the user pasted; earlier notes came from the old text, so they're cleared. */
    fun usePastedTranscript(entryId: String, text: String): Boolean {
        val segments = Transcripts.fromPasted(text)
        if (segments.isEmpty()) return false
        Store.updateEntry(entryId) {
            it.copy(segments = segments, pastedTranscript = true, language = "pasted", summary = null, bullets = null)
        }
        _errors.update { it - entryId }
        return true
    }

    fun clearError(entryId: String) = _errors.update { it - entryId }
}
