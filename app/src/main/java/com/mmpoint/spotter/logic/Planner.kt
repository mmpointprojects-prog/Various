package com.mmpoint.spotter.logic

import com.mmpoint.spotter.data.AppData
import com.mmpoint.spotter.data.CheckInKind
import com.mmpoint.spotter.data.DailyCheckIn
import com.mmpoint.spotter.data.ExerciseLibrary
import com.mmpoint.spotter.data.Muscle
import com.mmpoint.spotter.data.PlannedExercise
import com.mmpoint.spotter.data.ScheduleMode
import com.mmpoint.spotter.data.Supplement
import com.mmpoint.spotter.data.SuppSchedule
import com.mmpoint.spotter.data.WorkoutDay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object TimeUtil {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private val pretty = DateTimeFormatter.ofPattern("h:mm a")

    fun parse(hhmm: String?): LocalTime? = runCatching { LocalTime.parse(hhmm, hm) }.getOrNull()
    fun fmt(t: LocalTime): String = t.format(hm)
    fun pretty(hhmm: String?): String = parse(hhmm)?.format(pretty) ?: (hhmm ?: "")
    fun pretty(t: LocalTime): String = t.format(pretty)
    fun prettyMillis(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().format(pretty)
    fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    fun duration(ms: Long): String {
        val totalMin = ms / 60000
        return if (totalMin >= 60) "${totalMin / 60}h ${totalMin % 60}m" else "${totalMin}m"
    }
    fun clock(sec: Long): String = "%d:%02d".format(sec / 60, sec % 60)
}

/** Day-to-day scheduling: which workout is today, snack timing, which doses are due. */
object Planner {

    fun musclesOf(data: AppData, exercises: List<PlannedExercise>): List<Muscle> =
        exercises.mapNotNull { ExerciseLibrary.find(it.exerciseId, data.customExercises)?.muscle }.distinct()

    /** Workout scheduled for [date], or null for a rest day. */
    fun dayFor(data: AppData, date: LocalDate): WorkoutDay? {
        val days = data.program.days
        if (days.isEmpty()) return null
        return when (data.program.mode) {
            ScheduleMode.WEEKDAYS -> days.firstOrNull { date.dayOfWeek.value in it.weekdays }
            ScheduleMode.ROTATION -> if (date == LocalDate.now()) days[data.rotationIndex.mod(days.size)] else null
        }
    }

    fun morningCheckIn(data: AppData, date: LocalDate): DailyCheckIn? =
        data.checkIns.lastOrNull { it.date == date.toString() && it.kind == CheckInKind.MORNING }

    fun eveningCheckIn(data: AppData, date: LocalDate): DailyCheckIn? =
        data.checkIns.lastOrNull { it.date == date.toString() && it.kind == CheckInKind.EVENING }

    /** Pre-gym snack ~60 min before (configurable). Returns null if no gym time today. */
    fun snackTime(gymTime: LocalTime?, leadMin: Int): LocalTime? = gymTime?.minusMinutes(leadMin.toLong())

    fun isSuppDue(s: Supplement, date: LocalDate): Boolean {
        if (!s.active) return false
        val start = runCatching { LocalDate.parse(s.startDate) }.getOrDefault(date)
        if (date.isBefore(start)) return false
        val days = ChronoUnit.DAYS.between(start, date)
        if (s.cycleOnWeeks > 0 && s.cycleOffWeeks > 0) {
            val cycleDays = (s.cycleOnWeeks + s.cycleOffWeeks) * 7L
            if (days % cycleDays >= s.cycleOnWeeks * 7L) return false
        }
        return when (s.schedule) {
            SuppSchedule.DAILY -> true
            SuppSchedule.WEEKDAYS -> date.dayOfWeek.value in s.weekdays
            SuppSchedule.EVERY_N_DAYS -> days % s.everyNDays.coerceAtLeast(1) == 0L
        }
    }

    data class DueDose(val supp: Supplement, val time: String, val taken: Boolean, val skipped: Boolean)

    fun dosesFor(data: AppData, date: LocalDate): List<DueDose> =
        data.supplements.filter { isSuppDue(it, date) }.flatMap { s ->
            s.times.map { t ->
                val log = data.doseLogs.lastOrNull { it.supplementId == s.id && it.date == date.toString() && it.time == t }
                DueDose(s, t, log != null && !log.skipped, log?.skipped == true)
            }
        }.sortedBy { it.time }

    /** Units to draw on a U-100 insulin syringe for a reconstituted peptide. */
    fun syringeUnits(vialMg: Double, waterMl: Double, doseMcg: Double): Double? {
        if (vialMg <= 0 || waterMl <= 0 || doseMcg <= 0) return null
        val mcgPerMl = vialMg * 1000 / waterMl
        return doseMcg / mcgPerMl * 100
    }

    fun dosesPerVial(vialMg: Double, doseMcg: Double): Int? =
        if (vialMg <= 0 || doseMcg <= 0) null else (vialMg * 1000 / doseMcg).toInt()

    val injectionSites = listOf("Abdomen L", "Abdomen R", "Thigh L", "Thigh R", "Glute L", "Glute R", "Delt L", "Delt R")

    fun nextSite(data: AppData, suppId: String): String {
        val last = data.doseLogs.lastOrNull { it.supplementId == suppId && it.site != null }?.site
        val i = injectionSites.indexOf(last)
        return injectionSites[(i + 1).mod(injectionSites.size)]
    }
}
