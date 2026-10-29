package com.mmpoint.tubenotes.data

import kotlinx.serialization.Serializable

/** What the user can ask for about a video. */
enum class OutputKind(val label: String) {
    TRANSCRIPT("Full transcript"),
    SUMMARY("Brief summary"),
    BULLETS("Bullet points"),
}

@Serializable
data class Segment(val startSec: Double, val text: String)

/** One video in the log. Outputs are filled in as the user requests them and kept for later. */
@Serializable
data class LogEntry(
    val id: String,
    val videoId: String,
    val url: String,
    val title: String = "",
    val channel: String = "",
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val language: String = "",
    /** True when the transcript was pasted by the user instead of fetched from YouTube. */
    val pastedTranscript: Boolean = false,
    val segments: List<Segment> = emptyList(),
    val summary: String? = null,
    val bullets: String? = null,
    /** Kinds the user has opened for this video, for the log's labels. */
    val requested: List<OutputKind> = emptyList(),
) {
    val hasTranscript: Boolean get() = segments.isNotEmpty()
    val displayTitle: String get() = title.ifBlank { "YouTube video $videoId" }

    fun output(kind: OutputKind): String? = when (kind) {
        OutputKind.TRANSCRIPT -> if (hasTranscript) Transcripts.plain(segments) else null
        OutputKind.SUMMARY -> summary
        OutputKind.BULLETS -> bullets
    }
}

@Serializable
data class AppData(
    val apiKey: String = "",
    val entries: List<LogEntry> = emptyList(),
)

object Transcripts {
    /** Joins caption lines into readable paragraphs (a break after sentence ends every ~60 words). */
    fun plain(segments: List<Segment>): String {
        val out = StringBuilder()
        var words = 0
        for (s in segments) {
            val t = s.text.trim()
            if (t.isEmpty()) continue
            if (out.isNotEmpty() && !out.endsWith("\n\n")) out.append(' ')
            out.append(t)
            words += t.split(Regex("\\s+")).size
            if (words >= 60 && t.last() in ".?!") {
                out.append("\n\n")
                words = 0
            }
        }
        return out.toString().trim()
    }

    fun timestamped(segments: List<Segment>): String =
        segments.filter { it.text.isNotBlank() }.joinToString("\n") { "[${clock(it.startSec)}] ${it.text.trim()}" }

    fun clock(sec: Double): String {
        val s = sec.toLong()
        val h = s / 3600
        val m = (s % 3600) / 60
        val r = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%d:%02d".format(m, r)
    }

    /** Turns text the user pasted from YouTube's "Show transcript" panel into segments. */
    fun fromPasted(text: String): List<Segment> {
        val stamp = Regex("^(?:(\\d+):)?(\\d{1,2}):(\\d{2})$")
        val out = mutableListOf<Segment>()
        var pendingStart = 0.0
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val m = stamp.matchEntire(line)
            if (m != null) {
                val (h, mm, ss) = m.destructured
                pendingStart = (h.toIntOrNull() ?: 0) * 3600.0 + mm.toInt() * 60 + ss.toInt()
                continue
            }
            // Lines like "0:05 some words" (timestamp and text on one line).
            val inline = Regex("^((?:\\d+:)?\\d{1,2}:\\d{2})\\s+(.+)$").matchEntire(line)
            if (inline != null) {
                val (h, mm, ss) = stamp.matchEntire(inline.groupValues[1])!!.destructured
                out += Segment((h.toIntOrNull() ?: 0) * 3600.0 + mm.toInt() * 60 + ss.toInt(), inline.groupValues[2])
            } else {
                out += Segment(pendingStart, line)
            }
        }
        return out
    }
}
