package com.mmpoint.spotter

import com.mmpoint.spotter.data.AppData
import com.mmpoint.spotter.data.CheckInKind
import com.mmpoint.spotter.data.DailyCheckIn
import com.mmpoint.spotter.data.ExerciseLibrary
import com.mmpoint.spotter.data.ExerciseLog
import com.mmpoint.spotter.data.PlannedExercise
import com.mmpoint.spotter.data.Program
import com.mmpoint.spotter.data.Session
import com.mmpoint.spotter.data.SetLog
import com.mmpoint.spotter.data.Settings
import com.mmpoint.spotter.data.Store
import com.mmpoint.spotter.data.SuppSchedule
import com.mmpoint.spotter.data.Supplement
import com.mmpoint.spotter.data.WorkoutDay
import com.mmpoint.spotter.logic.Goal
import com.mmpoint.spotter.logic.GoalInput
import com.mmpoint.spotter.logic.GymAccess
import com.mmpoint.spotter.logic.PlanGenerator
import com.mmpoint.spotter.logic.Planner
import com.mmpoint.spotter.logic.Progression
import com.mmpoint.spotter.logic.Recovery
import com.mmpoint.spotter.notify.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LogicTest {

    private fun session(vararg logs: ExerciseLog, at: Long = 1_000L) =
        Session(id = "s$at", dayId = "d", dayName = "Chest & Triceps", startedAt = at, endedAt = at + 3_600_000, exercises = logs.toList())

    private fun sets(weight: Double, vararg reps: Int) = reps.map { SetLog(it, weight, 0) }

    private val bench = PlannedExercise("bb_bench", sets = 3, repMin = 8, repMax = 12)

    @Test fun firstTimeHasNoWeight() {
        val t = Progression.suggest(AppData(), bench, "lb")
        assertNull(t.weight)
        assertEquals(12, t.reps)
    }

    @Test fun hittingTopOfRangeOnEverySetGoesUp() {
        val data = AppData(sessions = listOf(session(ExerciseLog("bb_bench", sets = sets(185.0, 12, 12, 12)))))
        val t = Progression.suggest(data, bench, "lb")
        assertEquals(190.0, t.weight!!, 0.01)
        assertEquals(8, t.reps)
    }

    @Test fun missingRepsKeepsWeightAndAimsHigher() {
        val data = AppData(sessions = listOf(session(ExerciseLog("bb_bench", sets = sets(185.0, 10, 9, 8)))))
        val t = Progression.suggest(data, bench, "lb")
        assertEquals(185.0, t.weight!!, 0.01)
        assertEquals(10, t.reps) // at least match best set, never below repMin
    }

    @Test fun fallingFarShortDropsWeight() {
        val data = AppData(sessions = listOf(session(ExerciseLog("bb_bench", sets = sets(225.0, 6, 5, 4)))))
        val t = Progression.suggest(data, bench, "lb")
        assertEquals(220.0, t.weight!!, 0.01)
    }

    @Test fun usesMostRecentSession() {
        val data = AppData(sessions = listOf(
            session(ExerciseLog("bb_bench", sets = sets(135.0, 12, 12, 12)), at = 1),
            session(ExerciseLog("bb_bench", sets = sets(175.0, 10, 10, 10)), at = 2),
        ))
        assertEquals(175.0, Progression.suggest(data, bench, "lb").weight!!, 0.01)
    }

    @Test fun alternativesSamePatternFirst() {
        val alts = Progression.alternatives(AppData(), "bb_bench")
        assertTrue(alts.isNotEmpty())
        assertEquals("horizontal_press", alts.first().pattern)
        assertTrue(alts.none { it.id == "bb_bench" })
        assertTrue(alts.any { it.pattern == "chest_fly" }) // same-muscle fallbacks included
    }

    @Test fun alternativeEstimatedFromOriginalWhenNoHistory() {
        val data = AppData(sessions = listOf(session(ExerciseLog("bb_bench", sets = sets(200.0, 10, 10, 10)))))
        val alt = ExerciseLibrary.find("db_bench", emptyList())!!
        val t = Progression.suggestAlternative(data, bench, alt, "lb")
        // 200 * 0.40 * 0.9 = 72 -> rounded to 5 lb = 70 per hand
        assertEquals(70.0, t.weight!!, 0.01)
        assertTrue(t.note.contains("Estimated"))
    }

    @Test fun alternativeUsesOwnHistoryWhenAvailable() {
        val data = AppData(sessions = listOf(session(
            ExerciseLog("bb_bench", sets = sets(200.0, 10, 10, 10)),
            ExerciseLog("db_bench", sets = sets(80.0, 12, 12, 12)),
        )))
        val alt = ExerciseLibrary.find("db_bench", emptyList())!!
        val t = Progression.suggestAlternative(data, bench, alt, "lb")
        assertEquals(85.0, t.weight!!, 0.01) // progressed from own 80s
    }

    @Test fun generatorFitsTime() {
        val short = PlanGenerator.generate(GoalInput(Goal.MUSCLE, 4, 30, GymAccess.FULL_GYM))
        val long = PlanGenerator.generate(GoalInput(Goal.MUSCLE, 4, 90, GymAccess.FULL_GYM))
        assertEquals(4, short.days.flatMap { it.weekdays }.size)
        assertTrue(short.days.all { it.exercises.size in 3..8 })
        assertTrue(long.days.first().exercises.size > short.days.first().exercises.size)
        val est = Progression.estimatedMinutes(long.days.first().exercises)
        assertTrue("estimate $est should be near 90", est in 75..90)
        val shortEst = Progression.estimatedMinutes(short.days.first().exercises)
        assertTrue("estimate $shortEst should fit 30", shortEst <= 35)
    }

    @Test fun generatorRespectsEquipment() {
        val p = PlanGenerator.generate(GoalInput(Goal.GENERAL, 3, 45, GymAccess.DUMBBELLS))
        val eq = p.days.flatMap { it.exercises }.map { ExerciseLibrary.find(it.exerciseId, emptyList())!!.equipment }.toSet()
        assertTrue(eq.all { it.name in setOf("DUMBBELL", "BODYWEIGHT", "KETTLEBELL") })
    }

    @Test fun everyDaySplitGeneratesEachDay() {
        (2..6).forEach { n ->
            val p = PlanGenerator.generate(GoalInput(Goal.STRENGTH, n, 60, GymAccess.FULL_GYM))
            assertEquals(n, p.days.sumOf { it.weekdays.size })
            assertTrue(p.days.all { it.exercises.isNotEmpty() })
        }
    }

    @Test fun suppSchedules() {
        val start = LocalDate.of(2026, 10, 1)
        val daily = Supplement("a", "Creatine", startDate = start.toString())
        assertTrue(Planner.isSuppDue(daily, start))
        assertFalse(Planner.isSuppDue(daily, start.minusDays(1)))

        val eod = daily.copy(schedule = SuppSchedule.EVERY_N_DAYS, everyNDays = 2)
        assertTrue(Planner.isSuppDue(eod, start.plusDays(2)))
        assertFalse(Planner.isSuppDue(eod, start.plusDays(3)))

        val mwf = daily.copy(schedule = SuppSchedule.WEEKDAYS, weekdays = setOf(1, 3, 5))
        assertTrue(Planner.isSuppDue(mwf, LocalDate.of(2026, 10, 12))) // Monday
        assertFalse(Planner.isSuppDue(mwf, LocalDate.of(2026, 10, 13))) // Tuesday

        val cycled = daily.copy(cycleOnWeeks = 8, cycleOffWeeks = 4)
        assertTrue(Planner.isSuppDue(cycled, start.plusWeeks(7)))
        assertFalse(Planner.isSuppDue(cycled, start.plusWeeks(9)))
        assertTrue(Planner.isSuppDue(cycled, start.plusWeeks(12)))
    }

    @Test fun syringeMath() {
        // 5 mg vial + 2 mL water = 2500 mcg/mL; 250 mcg = 0.1 mL = 10 units
        assertEquals(10.0, Planner.syringeUnits(5.0, 2.0, 250.0)!!, 0.001)
        assertEquals(20, Planner.dosesPerVial(5.0, 250.0))
        assertNull(Planner.syringeUnits(0.0, 2.0, 250.0))
    }

    @Test fun reminderPlan() {
        val today = LocalDate.of(2026, 10, 13) // Tuesday
        val now = today.atTime(7, 0)
        val data = AppData(
            settings = Settings(onboarded = true, morningTime = "08:00", eveningTime = "21:00", weighDay = 7, weighTime = "07:30", snackLeadMin = 60),
            supplements = listOf(Supplement("p1", "BPC-157", times = listOf("07:30", "19:00"), startDate = today.toString())),
            checkIns = listOf(DailyCheckIn("c", today.toString(), CheckInKind.MORNING, 0, wantsWorkout = true, gymTime = "17:30")),
        )
        val plan = Reminders.plan(data, now)
        assertEquals(today.atTime(8, 0), plan["morning"])
        assertEquals(today.atTime(21, 0), plan["evening"])
        assertEquals(LocalDate.of(2026, 10, 18).atTime(7, 30), plan["weigh"]) // next Sunday
        assertEquals(today.atTime(16, 30), plan["snack"])
        assertEquals(today.atTime(17, 15), plan["gym"])
        assertEquals(today.atTime(7, 30), plan["dose|p1|07:30"])
        assertEquals(today.atTime(19, 0), plan["dose|p1|19:00"])

        // After 8 am the morning check-in moves to tomorrow.
        val later = Reminders.plan(data, today.atTime(9, 0))
        assertEquals(today.plusDays(1).atTime(8, 0), later["morning"])
    }

    @Test fun reminderSkipsDoseAlreadyTaken() {
        val today = LocalDate.of(2026, 10, 13)
        val data = AppData(
            settings = Settings(onboarded = true),
            supplements = listOf(Supplement("p1", "X", times = listOf("20:00"), startDate = today.toString())),
            doseLogs = listOf(com.mmpoint.spotter.data.DoseLog("p1", today.toString(), "20:00", 0)),
        )
        assertEquals(today.plusDays(1).atTime(20, 0), Reminders.plan(data, today.atTime(18, 0))["dose|p1|20:00"])
    }

    @Test fun nothingScheduledBeforeOnboarding() {
        assertTrue(Reminders.plan(AppData(), LocalDateTime.now()).isEmpty())
    }

    @Test fun weekdayAndRotationScheduling() {
        val days = listOf(WorkoutDay("a", "Chest & Triceps", weekdays = setOf(1)), WorkoutDay("b", "Back & Biceps", weekdays = setOf(3)))
        val data = AppData(program = Program(days = days))
        assertEquals("a", Planner.dayFor(data, LocalDate.of(2026, 10, 12))?.id)
        assertNull(Planner.dayFor(data, LocalDate.of(2026, 10, 13)))
        val rot = data.copy(program = data.program.copy(mode = com.mmpoint.spotter.data.ScheduleMode.ROTATION), rotationIndex = 1)
        assertEquals("b", Planner.dayFor(rot, LocalDate.now())?.id)
    }

    @Test fun snackTime() {
        assertEquals(LocalTime.of(16, 30), Planner.snackTime(LocalTime.of(17, 30), 60))
        assertNull(Planner.snackTime(null, 60))
    }

    @Test fun recoverySuggestionsReactToInput() {
        val bad = Recovery.suggestions(Recovery.Input(1, 4.5, 5, 5, evening = false))
        assertTrue(bad.any { it.contains("Short sleep") })
        assertTrue(bad.any { it.contains("Very sore") })
        assertTrue(bad.any { it.contains("stress", ignoreCase = true) })
        assertTrue(Recovery.readiness(4, 4, 5).contains("split", ignoreCase = true))
    }

    @Test fun jsonRoundTripAndForwardCompat() {
        val data = AppData(settings = Settings(onboarded = true, unit = "kg"))
        val text = Store.json.encodeToString(AppData.serializer(), data)
        assertEquals(data, Store.json.decodeFromString(AppData.serializer(), text))
        // Unknown future fields and missing fields must not break loading.
        val odd = """{"settings":{"unit":"kg","someFutureField":1},"newTopLevel":[]}"""
        val parsed = Store.json.decodeFromString(AppData.serializer(), odd)
        assertEquals("kg", parsed.settings.unit)
        assertNotNull(parsed.program)
    }

    @Test fun libraryIdsUnique() {
        val ids = ExerciseLibrary.builtIn.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
