package com.mmpoint.tubenotes

import com.mmpoint.tubenotes.data.OutputKind
import com.mmpoint.tubenotes.data.Segment
import com.mmpoint.tubenotes.data.Transcripts
import com.mmpoint.tubenotes.net.CaptionTrack
import com.mmpoint.tubenotes.net.Claude
import com.mmpoint.tubenotes.net.ClaudeException
import com.mmpoint.tubenotes.net.YouTube
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    @Test
    fun extractsVideoIdFromEveryLinkShape() {
        val id = "dQw4w9WgXcQ"
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://m.youtube.com/watch?feature=share&v=$id&t=42s",
            "https://youtu.be/$id?si=AbCdEf123",
            "Watch this! https://youtu.be/$id",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/live/$id?feature=shared",
            "https://music.youtube.com/watch?v=$id&list=RD",
            "https://www.youtube.com/embed/$id",
            id,
        ).forEach { assertEquals(it, id, YouTube.extractVideoId(it)) }
        assertNull(YouTube.extractVideoId("https://example.com/watch?v=$id"))
        assertNull(YouTube.extractVideoId("hello there"))
        assertNull(YouTube.extractVideoId(""))
    }

    @Test
    fun parsesClassicTimedTextWithDoubleEscapedEntities() {
        val xml = """<?xml version="1.0" encoding="utf-8" ?><transcript><text start="1.36" dur="1.68">[Music]</text>""" +
            """<text start="18.64" dur="3.24">We&amp;#39;re no strangers
to love</text><text start="22" dur="1">Tom &amp;amp; Jerry &amp;quot;hi&amp;quot;</text><text start="23" dur="1"> </text></transcript>"""
        val s = YouTube.parseTimedText(xml)
        assertEquals(3, s.size)
        assertEquals("We're no strangers to love", s[1].text)
        assertEquals(18.64, s[1].startSec, 0.001)
        assertEquals("Tom & Jerry \"hi\"", s[2].text)
    }

    @Test
    fun parsesSrv3Format() {
        val xml = """<timedtext format="3"><body><p t="1500" d="2000"><s>Hello</s><s> world</s></p><p t="61000" d="1000">Next &#x27;line&#x27;</p></body></timedtext>"""
        val s = YouTube.parseTimedText(xml)
        assertEquals(listOf(Segment(1.5, "Hello world"), Segment(61.0, "Next 'line'")), s)
    }

    @Test
    fun picksBestCaptionTrack() {
        val asrEn = CaptionTrack("a", "en", true)
        val manualEn = CaptionTrack("b", "en-US", false)
        val manualDe = CaptionTrack("c", "de", false)
        val asrJa = CaptionTrack("d", "ja", true)
        assertEquals(manualEn, YouTube.pickTrack(listOf(asrEn, manualDe, manualEn), "en"))
        assertEquals(asrEn, YouTube.pickTrack(listOf(manualDe, asrEn), "en"))
        assertEquals(manualDe, YouTube.pickTrack(listOf(asrJa, manualDe), "en"))
        assertEquals(asrJa, YouTube.pickTrack(listOf(asrJa), "en"))
    }

    @Test
    fun readsCaptionTracksFromPlayerResponse() {
        val player = Json.parseToJsonElement(
            """{"captions":{"playerCaptionsTracklistRenderer":{"captionTracks":[
              {"baseUrl":"https://x/1","languageCode":"en","kind":"asr"},{"baseUrl":"https://x/2","languageCode":"fr"}]}}}""",
        ).jsonObject
        assertEquals(listOf(CaptionTrack("https://x/1", "en", true), CaptionTrack("https://x/2", "fr", false)), YouTube.parseTracks(player))
        assertTrue(YouTube.parseTracks(Json.parseToJsonElement("{}").jsonObject).isEmpty())
    }

    @Test
    fun transcriptFormatting() {
        val segs = listOf(Segment(0.0, "hello"), Segment(65.0, "there."), Segment(3725.0, "end"))
        assertEquals("hello there. end", Transcripts.plain(segs))
        assertEquals("[0:00] hello\n[1:05] there.\n[1:02:05] end", Transcripts.timestamped(segs))
    }

    @Test
    fun parsesPastedTranscriptFromYouTubePanel() {
        val pasted = "0:00\nhey everyone welcome back\n0:04\ntoday we test\n1:02:03\nbye\n"
        assertEquals(
            listOf(Segment(0.0, "hey everyone welcome back"), Segment(4.0, "today we test"), Segment(3723.0, "bye")),
            Transcripts.fromPasted(pasted),
        )
        assertEquals(listOf(Segment(5.0, "inline text")), Transcripts.fromPasted("0:05 inline text"))
        assertEquals(listOf(Segment(0.0, "just plain words")), Transcripts.fromPasted("just plain words"))
        assertTrue(Transcripts.fromPasted("  \n ").isEmpty())
    }

    @Test
    fun buildsClaudeRequest() {
        val body = Json.parseToJsonElement(Claude.buildRequest(OutputKind.BULLETS, "My Title", "My Channel", "words words")).jsonObject
        assertEquals("claude-opus-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("low", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals("default", body["fallbacks"]!!.jsonPrimitive.content)
        val content = body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(content.contains("Video title: My Title"))
        assertTrue(content.contains("<transcript>\nwords words\n</transcript>"))
        assertTrue(content.contains("bullet points"))
        assertThrows(IllegalStateException::class.java) { Claude.buildRequest(OutputKind.TRANSCRIPT, "", "", "") }
    }

    @Test
    fun parsesClaudeResponses() {
        val ok = """{"content":[{"type":"thinking","thinking":""},{"type":"text","text":"• One\n"},{"type":"text","text":"• Two"}],"stop_reason":"end_turn"}"""
        assertEquals("• One\n• Two", Claude.parseResponse(200, ok))
        assertThrows(ClaudeException::class.java) { Claude.parseResponse(200, """{"content":[],"stop_reason":"refusal"}""") }
        val bad = assertThrows(ClaudeException::class.java) {
            Claude.parseResponse(401, """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""")
        }
        assertTrue(bad.message!!.contains("API key"))
        val other = assertThrows(ClaudeException::class.java) { Claude.parseResponse(400, """{"error":{"message":"bad thing"}}""") }
        assertTrue(other.message!!.contains("bad thing"))
        assertThrows(ClaudeException::class.java) { Claude.parseResponse(500, "<html>") }
        assertThrows(ClaudeException::class.java) { Claude.write("", OutputKind.SUMMARY, "", "", "x") }
    }
}
