package com.mmpoint.spotter

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.mmpoint.spotter.data.ExerciseLog
import com.mmpoint.spotter.data.PlannedExercise
import com.mmpoint.spotter.data.Program
import com.mmpoint.spotter.data.Session
import com.mmpoint.spotter.data.SetLog
import com.mmpoint.spotter.data.Store
import com.mmpoint.spotter.data.WorkoutDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.ExternalResource
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives the real app UI end to end: setup → split a Chest & Triceps day → log a set with the
 * rest timer → finish → do the saved chest part later → "can't do" bench → alternative with a
 * history-based weight → check-in → supplements → every tab renders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppFlowTest {
    @get:Rule(order = 0)
    val reset = object : ExternalResource() {
        override fun before() = Store.resetForTest()
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private fun click(text: String) = rule.onNodeWithText(text).performScrollTo().performClick()

    @Test
    fun fullWorkoutFlow() {
        // A Chest & Triceps day scheduled every day, plus earlier bench history (200 lb × 10).
        Store.update { d ->
            d.copy(
                program = Program(
                    name = "Test",
                    days = listOf(
                        WorkoutDay(
                            "ct", "Chest & Triceps",
                            listOf(PlannedExercise("bb_bench", 3, 8, 12, 90), PlannedExercise("pushdown", 1, 10, 15, 60)),
                            weekdays = (1..7).toSet(),
                        )
                    ),
                ),
                sessions = listOf(
                    Session("old", "ct", "Chest & Triceps", startedAt = 1_000, endedAt = 2_000,
                        exercises = listOf(ExerciseLog("bb_bench", sets = List(3) { SetLog(10, 200.0, 0) })))
                ),
            )
        }
        rule.waitForIdle()

        // Onboarding → Today
        rule.onNodeWithText("Welcome to Spotter").assertIsDisplayed()
        click("Finish setup")
        rule.onNodeWithText("Today: Chest & Triceps").assertIsDisplayed()

        // Readiness check → split the day: triceps now, chest later
        click("Start workout")
        rule.onNodeWithText("Energy").assertIsDisplayed()
        click("Split it — do part now, the rest later")
        click("Triceps")
        click("Start Triceps")
        rule.waitForIdle()
        val active = Store.current.activeSession
        assertNotNull(active)
        assertEquals(listOf("pushdown"), active!!.exercises.map { it.exerciseId })
        assertEquals(listOf("bb_bench"), Store.current.pending.single().exercises.map { it.exerciseId })
        rule.onNodeWithText("Cable Pushdown").assertIsDisplayed()

        // Log a set → rest timer starts
        rule.onNodeWithText("Log set 1").performClick()
        rule.waitForIdle()
        assertEquals(1, Store.current.activeSession!!.exercises[0].sets.size)
        rule.onNodeWithText("Skip rest").assertIsDisplayed()
        rule.onNodeWithText("Skip rest").performClick()

        // Finish
        rule.onNodeWithText("Finish").performClick()
        rule.onAllNodesWithText("Finish").onLast().performClick()
        rule.waitForIdle()
        assertNull(Store.current.activeSession)
        val done = Store.current.sessions.last()
        assertNotNull(done.endedAt)
        assertTrue(done.partLabel!!.contains("Triceps"))

        // Later: the saved chest part is on Today
        rule.onNodeWithText("Finish later: Chest").performScrollTo().assertIsDisplayed()
        click("Start now")
        click("Start")
        rule.waitForIdle()
        assertEquals("bb_bench", Store.current.activeSession!!.exercises.single().exerciseId)
        assertTrue(Store.current.pending.isEmpty())
        // Bench target progressed from history: 200 × 10 → stay at 200, beat reps
        assertEquals(200.0, Store.current.activeSession!!.exercises[0].targetWeight!!, 0.01)

        // Can't do bench → pick dumbbell bench → weight estimated from bench history
        click("Can't do this")
        rule.onNodeWithText("Pick an alternative:").assertIsDisplayed()
        rule.onAllNodesWithText("Dumbbell Bench Press").onFirst().performScrollTo().performClick()
        rule.waitForIdle()
        val swapped = Store.current.activeSession!!.exercises.single()
        assertEquals("db_bench", swapped.exerciseId)
        assertEquals("bb_bench", swapped.swappedFrom)
        assertEquals(70.0, swapped.targetWeight!!, 0.01)
        rule.onNodeWithText("Instead of Barbell Bench Press (Equipment taken)").assertIsDisplayed()

        // Finish & save the untouched exercise for later
        rule.onNodeWithText("Finish").performClick()
        rule.onNodeWithText("Finish & save the rest for later").performClick()
        rule.waitForIdle()
        assertEquals(listOf("bb_bench"), Store.current.pending.single().exercises.map { it.exerciseId })

        // Morning check-in with recovery suggestions
        rule.onAllNodesWithText("Check in").onFirst().performScrollTo().performClick()
        rule.onNodeWithText("Save").performScrollTo().performClick()
        rule.onNodeWithText("Suggestions").assertIsDisplayed()
        assertTrue(Store.current.checkIns.isNotEmpty())
        click("Done")

        // Supplements: add a peptide
        rule.onNodeWithText("Supps").performClick()
        click("+ Add peptide / supplement")
        rule.onNodeWithText("Peptide").performClick()
        rule.onNodeWithText("Name (e.g. BPC-157, Creatine, Vitamin D3)").performTextInput("BPC-157")
        rule.onNodeWithText("Vial (mg)").performScrollTo().performTextInput("5")
        rule.onNodeWithText("Water (mL)").performTextInput("2")
        rule.onNodeWithText("Dose per injection (mcg)").performTextInput("250")
        rule.onNodeWithText("Draw 10.0 units (0.100 mL)").performScrollTo().assertIsDisplayed()
        click("Save")
        rule.waitForIdle()
        assertEquals("BPC-157", Store.current.supplements.single().name)
        rule.onNodeWithText("BPC-157").assertIsDisplayed()

        // Every tab renders
        rule.onNodeWithText("Calendar").performClick()
        rule.onNodeWithText("Workout").performScrollTo().assertIsDisplayed() // legend
        rule.onNodeWithText("Progress").performClick()
        rule.onNodeWithText("Exercise bests (estimated 1RM)").performScrollTo().assertIsDisplayed()
        click("Weight log & chart")
        rule.onNodeWithText("Weight (lb)").performTextInput("185")
        rule.onNodeWithText("Log").performClick()
        rule.waitForIdle()
        assertEquals(185.0, Store.current.weights.single().weight, 0.01)
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Make sure reminders arrive").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Today").performClick()
        rule.onAllNodes(hasText("Finish later: Chest")).onFirst().performScrollTo().assertIsDisplayed()
    }

    @Test
    fun goalGeneratorFlow() {
        rule.onNodeWithText("Give me a plan from my goals & time").performClick()
        click("5")
        click("75 min")
        click("Use this plan")
        rule.waitForIdle()
        val p = Store.current.program
        assertEquals(5, p.days.size)
        assertTrue(p.days.any { it.name == "Chest & Triceps" })
        click("Edit my splits")
        rule.onNodeWithText("Chest & Triceps").assertIsDisplayed()
    }
}
