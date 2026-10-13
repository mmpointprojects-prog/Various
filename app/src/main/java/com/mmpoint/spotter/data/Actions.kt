package com.mmpoint.spotter.data

import com.mmpoint.spotter.logic.Planner
import com.mmpoint.spotter.logic.Progression
import java.time.LocalDate

/** All state changes that touch more than one field live here. */
object Actions {

    private fun logFor(data: AppData, p: PlannedExercise): ExerciseLog {
        val t = Progression.suggest(data, p, data.settings.unit)
        return ExerciseLog(
            exerciseId = p.exerciseId, targetSets = p.sets, repMin = p.repMin, repMax = p.repMax,
            restSec = p.restSec, targetWeight = t.weight, targetReps = t.reps, targetNote = t.note,
        )
    }

    /**
     * Start a workout. When [onlyMuscles] is given, only those muscle groups are done now and
     * the rest of the day is saved as a [PendingPart] to finish later.
     */
    fun startDay(day: WorkoutDay, readiness: Readiness?, onlyMuscles: Set<Muscle>? = null) {
        Store.update { d ->
            val muscleOf = { p: PlannedExercise -> ExerciseLibrary.find(p.exerciseId, d.customExercises)?.muscle }
            val now = day.exercises.filter { onlyMuscles == null || muscleOf(it) in onlyMuscles }
            val later = day.exercises - now.toSet()
            val label = if (later.isEmpty()) null else
                "Part 1: " + Planner.musclesOf(d, now).joinToString(" & ") { it.label }
            val pending = if (later.isEmpty()) d.pending else d.pending + PendingPart(
                id = Store.newId(), dayId = day.id, dayName = day.name, exercises = later,
                createdAt = System.currentTimeMillis(),
            )
            d.copy(
                activeSession = Session(
                    id = Store.newId(), dayId = day.id, dayName = day.name, partLabel = label,
                    startedAt = System.currentTimeMillis(), exercises = now.map { logFor(d, it) },
                    readiness = readiness,
                ),
                pending = pending,
            )
        }
    }

    fun startPending(part: PendingPart, readiness: Readiness?) {
        Store.update { d ->
            val label = "Part 2: " + Planner.musclesOf(d, part.exercises).joinToString(" & ") { it.label }
            d.copy(
                activeSession = Session(
                    id = Store.newId(), dayId = part.dayId, dayName = part.dayName, partLabel = label,
                    startedAt = System.currentTimeMillis(), exercises = part.exercises.map { logFor(d, it) },
                    readiness = readiness,
                ),
                pending = d.pending.filterNot { it.id == part.id },
            )
        }
    }

    fun discardPending(id: String) = Store.update { d -> d.copy(pending = d.pending.filterNot { it.id == id }) }

    private fun editActive(index: Int, f: (ExerciseLog) -> ExerciseLog) = Store.update { d ->
        val s = d.activeSession ?: return@update d
        d.copy(activeSession = s.copy(exercises = s.exercises.mapIndexed { i, e -> if (i == index) f(e) else e }))
    }

    fun logSet(index: Int, reps: Int, weight: Double, durationSec: Int?) = editActive(index) {
        it.copy(sets = it.sets + SetLog(reps, weight, System.currentTimeMillis(), durationSec))
    }

    fun removeLastSet(index: Int) = editActive(index) { it.copy(sets = it.sets.dropLast(1)) }

    fun skip(index: Int, skipped: Boolean = true) = editActive(index) { it.copy(skipped = skipped) }

    /** "I can't do this one right now" → replace with [alt] using a history-based target. */
    fun swap(index: Int, alt: Exercise, reason: String) = Store.update { d ->
        val s = d.activeSession ?: return@update d
        val cur = s.exercises[index]
        val original = cur.swappedFrom ?: cur.exerciseId
        val planned = PlannedExercise(original, cur.targetSets, cur.repMin, cur.repMax, cur.restSec)
        val t = Progression.suggestAlternative(d, planned, alt, d.settings.unit)
        val replaced = cur.copy(
            exerciseId = alt.id, swappedFrom = original, swapReason = reason, sets = emptyList(),
            targetWeight = t.weight, targetReps = t.reps, targetNote = t.note, skipped = false,
        )
        d.copy(activeSession = s.copy(exercises = s.exercises.mapIndexed { i, e -> if (i == index) replaced else e }))
    }

    /** Move an exercise to later in today's session (e.g. machine is taken). */
    fun moveToEnd(index: Int) = Store.update { d ->
        val s = d.activeSession ?: return@update d
        val list = s.exercises.toMutableList()
        list.add(list.removeAt(index))
        d.copy(activeSession = s.copy(exercises = list))
    }

    fun addExercise(exerciseId: String) = Store.update { d ->
        val s = d.activeSession ?: return@update d
        val p = PlannedExercise(exerciseId, restSec = d.settings.defaultRestSec)
        d.copy(activeSession = s.copy(exercises = s.exercises + logFor(d, p)))
    }

    /**
     * Finish the active session. With [deferRest], exercises not started yet are saved as a
     * pending part to do later today (or another day).
     */
    fun finish(deferRest: Boolean, notes: String = "") = Store.update { d ->
        val s = d.activeSession ?: return@update d
        val untouched = s.exercises.filter { it.sets.isEmpty() && !it.skipped }
        val pending = if (deferRest && untouched.isNotEmpty()) d.pending + PendingPart(
            id = Store.newId(), dayId = s.dayId, dayName = s.dayName,
            exercises = untouched.map { PlannedExercise(it.swappedFrom ?: it.exerciseId, it.targetSets, it.repMin, it.repMax, it.restSec) },
            createdAt = System.currentTimeMillis(),
        ) else d.pending
        val kept = if (deferRest) s.exercises - untouched.toSet() else s.exercises
        val done = s.copy(endedAt = System.currentTimeMillis(), exercises = kept, notes = notes)
        val fromProgramDay = s.partLabel == null || s.partLabel.startsWith("Part 1")
        val rotation = if (d.program.mode == ScheduleMode.ROTATION && fromProgramDay && d.program.days.isNotEmpty())
            (d.rotationIndex + 1).mod(d.program.days.size) else d.rotationIndex
        d.copy(sessions = d.sessions + done, activeSession = null, pending = pending, rotationIndex = rotation)
    }

    fun cancelActive() = Store.update { it.copy(activeSession = null) }

    fun logDose(suppId: String, date: LocalDate, time: String, skipped: Boolean = false) = Store.update { d ->
        val s = d.supplements.firstOrNull { it.id == suppId } ?: return@update d
        if (d.doseLogs.any { it.supplementId == suppId && it.date == date.toString() && it.time == time }) return@update d
        val site = if (s.rotateSites && !skipped) Planner.nextSite(d, suppId) else null
        val supps = if (!skipped && s.remainingDoses != null)
            d.supplements.map { if (it.id == suppId) it.copy(remainingDoses = (it.remainingDoses!! - 1).coerceAtLeast(0)) else it }
        else d.supplements
        d.copy(
            doseLogs = d.doseLogs + DoseLog(suppId, date.toString(), time, System.currentTimeMillis(), skipped, site),
            supplements = supps,
        )
    }

    fun undoDose(suppId: String, date: LocalDate, time: String) = Store.update { d ->
        val log = d.doseLogs.lastOrNull { it.supplementId == suppId && it.date == date.toString() && it.time == time }
            ?: return@update d
        val supps = if (!log.skipped) d.supplements.map {
            if (it.id == suppId && it.remainingDoses != null) it.copy(remainingDoses = it.remainingDoses + 1) else it
        } else d.supplements
        d.copy(doseLogs = d.doseLogs - log, supplements = supps)
    }
}
