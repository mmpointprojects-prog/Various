package com.mmpoint.tubenotes

import android.content.Intent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.mmpoint.tubenotes.data.OutputKind
import com.mmpoint.tubenotes.data.Segment
import com.mmpoint.tubenotes.data.Store
import com.mmpoint.tubenotes.data.Work
import com.mmpoint.tubenotes.net.ClaudeException
import com.mmpoint.tubenotes.net.FetchedTranscript
import com.mmpoint.tubenotes.net.VideoInfo
import com.mmpoint.tubenotes.net.YouTubeException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives the real UI: share a link from YouTube → pick each option → results are saved to the log,
 * a missing API key is explained, and a blocked transcript can be pasted by hand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppFlowTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val claudeCalls = mutableListOf<OutputKind>()

    @Before
    fun setUp() {
        Store.resetForTest()
        Work.dispatcherScope = CoroutineScope(Dispatchers.Unconfined)
        Work.fetchInfo = { VideoInfo("Test Video", "Test Channel") }
        Work.fetchTranscript = {
            FetchedTranscript("Test Video", "Test Channel", "en", listOf(Segment(0.0, "Hello and welcome."), Segment(3.0, "Today we bake bread.")))
        }
        Work.writeNotes = { key, kind, _, _, transcript ->
            if (key.isBlank()) throw ClaudeException("Add your Claude API key in Settings to make summaries and bullet points.")
            claudeCalls += kind
            assert(transcript.contains("bake bread"))
            if (kind == OutputKind.SUMMARY) "A short video about baking bread." else "• Bake bread\n• Say hello"
        }
    }

    private fun share(text: String): ActivityScenario<MainActivity> {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        return ActivityScenario.launch(intent)
    }

    private fun click(text: String) = rule.onNodeWithText(text).performScrollTo().performClick()
    private fun shows(text: String) = rule.onNodeWithText(text, substring = true).performScrollTo()

    @Test
    fun shareThenPickEachOptionAndFindItInTheLog() {
        share("Watch this https://youtu.be/dQw4w9WgXcQ?si=abc123").use {
            rule.waitForIdle()
            shows("What would you like?")
            rule.onNodeWithText("Test Channel").performScrollTo()

            // Summary without a key: clear message plus a way to Settings.
            click("Summary")
            shows("Add your Claude API key")
            Store.update { it.copy(apiKey = "sk-ant-test") }
            click("Try again")
            shows("A short video about baking bread.")

            click("Bullets")
            shows("• Bake bread")

            click("Transcript")
            shows("Hello and welcome. Today we bake bread.")
            click("Transcript") // re-selecting must not refetch or re-bill
            assertEquals(listOf(OutputKind.SUMMARY, OutputKind.BULLETS), claudeCalls)

            rule.onNodeWithContentDescription("Back").performClick()
            shows("Log (1)")
            shows("Saved: Full transcript · Brief summary · Bullet points")

            // Opening it again from the log shows the saved result without calling Claude again.
            click("Test Video")
            click("Summary")
            shows("A short video about baking bread.")
            assertEquals(2, claudeCalls.size)
        }
        val e = Store.current.entries.single()
        assertEquals("dQw4w9WgXcQ", e.videoId)
        assertNotNull(e.summary)
    }

    @Test
    fun blockedTranscriptCanBePasted() {
        Work.fetchTranscript = { throw YouTubeException("YouTube didn't share this video's captions (LOGIN_REQUIRED). You can paste the transcript yourself instead.") }
        Store.update { it.copy(apiKey = "sk-ant-test") }
        share("https://www.youtube.com/watch?v=aircAruvnKk").use {
            rule.waitForIdle()
            click("Bullets")
            shows("didn't share this video's captions")
            click("Paste transcript")
            rule.onNode(hasText("Transcript text")).performTextInput("0:00\nWe bake bread today.\n0:05\nThen we eat it.")
            click("Use this")
            shows("• Bake bread")
            click("Transcript")
            shows("Pasted by you")
        }
        assertEquals(true, Store.current.entries.single().pastedTranscript)
    }

    @Test
    fun pastingALinkOnHomeOpensTheVideo() {
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitForIdle()
            shows("Nothing yet")
            rule.onNode(hasText("YouTube link")).performTextInput("not a link")
            click("Open video")
            shows("doesn't look like a YouTube link")
            rule.onNode(hasText("not a link")).performTextInput(" https://www.youtube.com/shorts/dQw4w9WgXcQ")
            click("Open video")
            shows("What would you like?")
        }
    }

    @Test
    fun pastingAWholeCurlExampleSavesOnlyTheKey() {
        val key = "sk-ant-api03-AbCdEfGhIjKlMnOpQrStUvWxYz_0123456789-abcdEFGH"
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitForIdle()
            rule.onNodeWithContentDescription("Settings").performClick()
            rule.onNode(hasText("API key (sk-ant-…)")).performTextInput("hello there")
            click("Save key")
            shows("No API key found")
            rule.onNode(hasText("API key (sk-ant-…)")).performTextInput(
                " curl https://api.anthropic.com/v1/messages --header \"x-api-key: $key\" --header \"anthropic-version: 2026-10-21\"",
            )
            click("Save key")
            shows("Saved key: sk-ant-…dEFGH")
        }
        assertEquals(key, Store.current.apiKey)
    }
}
