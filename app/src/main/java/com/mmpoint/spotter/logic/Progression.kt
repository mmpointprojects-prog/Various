package com.mmpoint.spotter.logic

import com.mmpoint.spotter.data.AppData
import com.mmpoint.spotter.data.Equipment
import com.mmpoint.spotter.data.Exercise
import com.mmpoint.spotter.data.ExerciseLibrary
import com.mmpoint.spotter.data.ExerciseLog
import com.mmpoint.spotter.data.PlannedExercise
import kotlin.math.max
import kotlin.math.roundToInt

data class Target(
    /** null = no basis yet, user picks. 0 = bodyweight. */
    val weight: Double?,
    val reps: Int,
    val note: String,
)

/**
 * Double progression: stay at a weight until you hit the top of the rep range on every
 * set, then go up one increment and restart at the bottom of the range.
 */
object Progression {

    fun increment(ex: Exercise, unit: String): Double {
        val kg = unit == "kg"
        return when (ex.equipment) {
            Equipment.BODYWEIGHT -> 0.0
            Equipment.BARBELL, Equipment.SMITH -> if (ex.lowerBody) (if (kg) 5.0 else 10.0) else (if (kg) 2.5 else 5.0)
            Equipment.DUMBBELL, Equipment.KETTLEBELL -> if (kg) 2.0 else 5.0
            else -> if (kg) 2.5 else 5.0
        }
    }

    fun roundTo(weight: Double, step: Double): Double {
        if (step <= 0.0) return weight
        return (weight / step).roundToInt() * step
    }

    /** All completed logs for an exercise, newest first. Includes the active session. */
    fun history(data: AppData, exerciseId: String): List<ExerciseLog> {
        val sessions = data.sessions.sortedByDescending { it.startedAt }
        return sessions.flatMap { s -> s.exercises.filter { it.exerciseId == exerciseId && it.sets.isNotEmpty() } }
    }

    fun lastLog(data: AppData, exerciseId: String): ExerciseLog? = history(data, exerciseId).firstOrNull()

    fun suggest(data: AppData, planned: PlannedExercise, unit: String): Target {
        val ex = ExerciseLibrary.find(planned.exerciseId, data.customExercises)
        val last = lastLog(data, planned.exerciseId)
        return suggestFrom(last, planned, ex, unit)
    }

    fun suggestFrom(last: ExerciseLog?, planned: PlannedExercise, ex: Exercise?, unit: String): Target {
        if (last == null || last.sets.isEmpty()) {
            return Target(
                weight = if (ex?.equipment == Equipment.BODYWEIGHT) 0.0 else null,
                reps = planned.repMax,
                note = "First time: pick a weight you could do for ${planned.repMax} reps with ~2 left in the tank.",
            )
        }
        val top = last.sets.maxOf { it.weight }
        val atTop = last.sets.filter { it.weight == top }
        val step = ex?.let { increment(it, unit) } ?: if (unit == "kg") 2.5 else 5.0
        val bodyweight = ex?.equipment == Equipment.BODYWEIGHT && top == 0.0
        val hitAll = atTop.size >= planned.sets && atTop.all { it.reps >= planned.repMax }
        val bestReps = atTop.maxOf { it.reps }
        val worstReps = atTop.minOf { it.reps }
        val lastStr = "Last: ${atTop.size}×${formatReps(atTop.map { it.reps })} @ ${fmtW(top, unit)}"

        return when {
            hitAll && bodyweight ->
                Target(0.0, planned.repMax + 1, "$lastStr. You owned it — add a rep (or add weight).")
            hitAll ->
                Target(top + step, planned.repMin, "$lastStr. Every set hit ${planned.repMax} → go up to ${fmtW(top + step, unit)}.")
            worstReps < planned.repMin - 2 && !bodyweight && top - step > 0 ->
                Target(top - step, planned.repMin, "$lastStr. Fell well short of ${planned.repMin} — drop to ${fmtW(top - step, unit)} and own the reps.")
            else -> {
                val aim = (worstReps + 1).coerceIn(planned.repMin, planned.repMax)
                Target(top, max(aim, minOf(bestReps, planned.repMax)), "$lastStr. Same weight, beat last time.")
            }
        }
    }

    /**
     * Alternatives for an exercise you can't do right now: same movement pattern first,
     * then other exercises for the same muscle.
     */
    fun alternatives(data: AppData, exerciseId: String): List<Exercise> {
        val all = ExerciseLibrary.all(data.customExercises)
        val ex = ExerciseLibrary.find(exerciseId, data.customExercises) ?: return emptyList()
        val samePattern = all.filter { it.id != ex.id && it.pattern == ex.pattern }
        val sameMuscle = all.filter { it.id != ex.id && it.pattern != ex.pattern && it.muscle == ex.muscle }
        // Within each group, exercises you've done before come first.
        val done = data.sessions.flatMap { s -> s.exercises.map { it.exerciseId } }.toSet()
        return samePattern.sortedByDescending { it.id in done } + sameMuscle.sortedByDescending { it.id in done }
    }

    /**
     * Weight and reps for an alternative. Uses your own history on the alternative when you
     * have it; otherwise converts your last performance on the exercise you can't do.
     */
    fun suggestAlternative(data: AppData, planned: PlannedExercise, alt: Exercise, unit: String): Target {
        val altPlanned = planned.copy(exerciseId = alt.id)
        val altHistory = lastLog(data, alt.id)
        if (altHistory != null) {
            val t = suggestFrom(altHistory, altPlanned, alt, unit)
            return t.copy(note = "From your ${alt.name} history. ${t.note}")
        }
        val original = ExerciseLibrary.find(planned.exerciseId, data.customExercises)
        val origLast = lastLog(data, planned.exerciseId)
        if (alt.equipment == Equipment.BODYWEIGHT) {
            return Target(0.0, planned.repMax, "Bodyweight: aim for ${planned.repMin}–${planned.repMax} controlled reps.")
        }
        if (original == null || origLast == null || origLast.sets.isEmpty()) {
            return suggestFrom(null, altPlanned, alt, unit)
        }
        val top = origLast.sets.maxOf { it.weight }
        val reps = origLast.sets.filter { it.weight == top }.maxOf { it.reps }
        if (original.pattern != alt.pattern || original.factor <= 0.0 || alt.factor <= 0.0 || top <= 0.0) {
            return Target(null, planned.repMax,
                "No direct conversion from ${original.name}. Start light, find a weight for ${planned.repMax} reps with ~2 in reserve.")
        }
        // Convert, then shave 10% because a new movement pattern costs some strength at first.
        val raw = top * (alt.factor / original.factor) * 0.9
        val est = max(increment(alt, unit), roundTo(raw, increment(alt, unit)))
        return Target(est, planned.repMin.coerceAtLeast(minOf(reps, planned.repMax)),
            "Estimated from your ${original.name} (${fmtW(top, unit)} × $reps). First time on ${alt.name} — adjust after set 1.")
    }

    fun estimatedMinutes(exercises: List<PlannedExercise>): Int {
        val sec = exercises.sumOf { it.sets * (45 + it.restSec) + 90 }
        return (sec / 60.0).roundToInt() + 5
    }

    fun fmtW(w: Double, unit: String): String =
        if (w == 0.0) "bodyweight" else (if (w % 1.0 == 0.0) w.toInt().toString() else "%.1f".format(w)) + " " + unit

    private fun formatReps(reps: List<Int>): String =
        if (reps.distinct().size == 1) reps.first().toString() else reps.joinToString("/")

    /** Estimated one-rep max (Epley). */
    fun e1rm(weight: Double, reps: Int): Double = if (reps <= 1) weight else weight * (1 + reps / 30.0)
}
