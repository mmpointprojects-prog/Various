package com.mmpoint.spotter.data

import kotlinx.serialization.Serializable

// Everything is stored in one JSON file. Every field has a default so that adding
// new fields later never breaks (or wipes) existing data.

@Serializable
enum class Muscle(val label: String) {
    CHEST("Chest"), BACK("Back"), SHOULDERS("Shoulders"), BICEPS("Biceps"), TRICEPS("Triceps"),
    QUADS("Quads"), HAMSTRINGS("Hamstrings"), GLUTES("Glutes"), CALVES("Calves"), CORE("Core"),
    FOREARMS("Forearms"), CARDIO("Cardio")
}

@Serializable
enum class Equipment(val label: String) {
    BARBELL("Barbell"), DUMBBELL("Dumbbell"), MACHINE("Machine"), CABLE("Cable"), SMITH("Smith machine"),
    EZ_BAR("EZ bar"), KETTLEBELL("Kettlebell"), BODYWEIGHT("Bodyweight"), BAND("Band")
}

/**
 * [pattern] groups exercises that train the same movement; those are the first alternatives offered.
 * [factor] is the relative load vs. the pattern's reference lift (per hand for dumbbells), used to
 * estimate a starting weight on an alternative you have never done.
 */
@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val muscle: Muscle,
    val pattern: String,
    val equipment: Equipment,
    val factor: Double = 1.0,
    val lowerBody: Boolean = false,
    val custom: Boolean = false,
)

@Serializable
enum class ScheduleMode { WEEKDAYS, ROTATION }

@Serializable
data class PlannedExercise(
    val exerciseId: String,
    val sets: Int = 3,
    val repMin: Int = 8,
    val repMax: Int = 12,
    val restSec: Int = 90,
)

/** [weekdays] uses java.time numbering: 1 = Monday ... 7 = Sunday. */
@Serializable
data class WorkoutDay(
    val id: String,
    val name: String,
    val exercises: List<PlannedExercise> = emptyList(),
    val weekdays: Set<Int> = emptySet(),
)

@Serializable
data class Program(
    val name: String = "My program",
    val days: List<WorkoutDay> = emptyList(),
    val mode: ScheduleMode = ScheduleMode.WEEKDAYS,
    val goalSummary: String? = null,
)

@Serializable
data class SetLog(
    val reps: Int,
    val weight: Double,
    val loggedAt: Long,
    val durationSec: Int? = null,
)

@Serializable
data class ExerciseLog(
    val exerciseId: String,
    /** Exercise that was originally planned, when this one was swapped in. */
    val swappedFrom: String? = null,
    val swapReason: String? = null,
    val targetSets: Int = 3,
    val repMin: Int = 8,
    val repMax: Int = 12,
    val restSec: Int = 90,
    val targetWeight: Double? = null,
    val targetReps: Int? = null,
    val targetNote: String = "",
    val sets: List<SetLog> = emptyList(),
    val skipped: Boolean = false,
)

@Serializable
data class Readiness(
    val energy: Int = 3,
    val sleep: Int = 3,
    val soreness: Int = 2,
    val decision: String = "full",
)

@Serializable
data class Session(
    val id: String,
    val dayId: String?,
    val dayName: String,
    /** e.g. "Part 1 of 2" when a day was split. */
    val partLabel: String? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val exercises: List<ExerciseLog> = emptyList(),
    val readiness: Readiness? = null,
    val notes: String = "",
)

/** Part of a workout day you chose to do later (e.g. chest moved out of Chest & Triceps day). */
@Serializable
data class PendingPart(
    val id: String,
    val dayId: String?,
    val dayName: String,
    val exercises: List<PlannedExercise>,
    val createdAt: Long,
)

@Serializable
enum class CheckInKind { MORNING, EVENING }

@Serializable
data class DailyCheckIn(
    val id: String,
    val date: String,
    val kind: CheckInKind,
    val at: Long,
    val feeling: Int = 3,
    val sleepHours: Double? = null,
    val soreness: Int = 2,
    val stress: Int = 2,
    val wantsWorkout: Boolean? = null,
    /** "HH:mm" */
    val gymTime: String? = null,
    val trainedToday: Boolean? = null,
    val notes: String = "",
)

@Serializable
enum class SuppKind(val label: String) {
    PEPTIDE("Peptide"), SUPPLEMENT("Supplement"), VITAMIN("Vitamin"), MEDICATION("Medication")
}

@Serializable
enum class SuppSchedule(val label: String) {
    DAILY("Every day"), WEEKDAYS("Specific days"), EVERY_N_DAYS("Every N days")
}

@Serializable
data class Supplement(
    val id: String,
    val name: String,
    val kind: SuppKind = SuppKind.SUPPLEMENT,
    val dose: String = "",
    val unit: String = "",
    /** "HH:mm" times per due day. */
    val times: List<String> = listOf("08:00"),
    val schedule: SuppSchedule = SuppSchedule.DAILY,
    val weekdays: Set<Int> = emptySet(),
    val everyNDays: Int = 2,
    /** ISO date the schedule starts counting from. */
    val startDate: String,
    /** Cycle in weeks; 0 = no cycling. */
    val cycleOnWeeks: Int = 0,
    val cycleOffWeeks: Int = 0,
    val instructions: String = "",
    val active: Boolean = true,
    // Peptide vial / reconstitution info (optional).
    val vialMg: Double? = null,
    val waterMl: Double? = null,
    val doseMcg: Double? = null,
    val remainingDoses: Int? = null,
    val rotateSites: Boolean = false,
)

@Serializable
data class DoseLog(
    val supplementId: String,
    val date: String,
    val time: String,
    val at: Long,
    val skipped: Boolean = false,
    val site: String? = null,
)

@Serializable
data class WeightEntry(val date: String, val at: Long, val weight: Double)

@Serializable
data class Settings(
    val onboarded: Boolean = false,
    val unit: String = "lb",
    val morningTime: String = "08:00",
    val eveningTime: String = "21:00",
    val morningEnabled: Boolean = true,
    val eveningEnabled: Boolean = true,
    /** 1 = Monday ... 7 = Sunday */
    val weighDay: Int = 7,
    val weighTime: String = "07:30",
    val weighEnabled: Boolean = true,
    val snackLeadMin: Int = 60,
    val gymLeadMin: Int = 15,
    val defaultRestSec: Int = 90,
    val restAlarm: Boolean = true,
)

@Serializable
data class AppData(
    val settings: Settings = Settings(),
    val program: Program = Program(),
    val customExercises: List<Exercise> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val activeSession: Session? = null,
    val pending: List<PendingPart> = emptyList(),
    val checkIns: List<DailyCheckIn> = emptyList(),
    val supplements: List<Supplement> = emptyList(),
    val doseLogs: List<DoseLog> = emptyList(),
    val weights: List<WeightEntry> = emptyList(),
    /** Rotation mode: index of the next day to do. */
    val rotationIndex: Int = 0,
)
