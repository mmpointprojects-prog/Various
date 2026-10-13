package com.mmpoint.spotter.logic

import com.mmpoint.spotter.data.Equipment
import com.mmpoint.spotter.data.ExerciseLibrary
import com.mmpoint.spotter.data.PlannedExercise
import com.mmpoint.spotter.data.Program
import com.mmpoint.spotter.data.ScheduleMode
import com.mmpoint.spotter.data.WorkoutDay

enum class Goal(val label: String, val sets: Int, val repMin: Int, val repMax: Int, val rest: Int) {
    STRENGTH("Get stronger", 4, 4, 6, 180),
    MUSCLE("Build muscle", 3, 8, 12, 90),
    FAT_LOSS("Lose fat / lean out", 3, 12, 15, 60),
    GENERAL("General fitness", 3, 8, 12, 75),
}

enum class GymAccess(val label: String) {
    FULL_GYM("Full gym"), DUMBBELLS("Dumbbells + bench"), MINIMAL("Bodyweight / minimal"),
}

data class GoalInput(
    val goal: Goal,
    val daysPerWeek: Int,
    val minutes: Int,
    val access: GymAccess,
)

/** Builds a preset program from goals and the time you have in the gym. */
object PlanGenerator {

    // Each template is an ordered list of movement patterns; compound lifts first.
    private val templates = mapOf(
        "Full Body A" to listOf("squat", "horizontal_press", "horizontal_row", "vertical_press", "knee_flexion", "curl", "triceps_pushdown", "ab_flexion"),
        "Full Body B" to listOf("hip_hinge", "incline_press", "vertical_pull", "lunge", "lateral_raise", "triceps_extension", "hammer_curl", "leg_raise"),
        "Full Body C" to listOf("leg_press", "horizontal_press", "horizontal_row", "hip_thrust", "rear_delt", "curl", "triceps_pushdown", "anti_extension"),
        "Push" to listOf("horizontal_press", "incline_press", "vertical_press", "lateral_raise", "chest_fly", "triceps_pushdown", "triceps_extension"),
        "Pull" to listOf("vertical_pull", "horizontal_row", "lat_isolation", "rear_delt", "curl", "hammer_curl", "ab_flexion"),
        "Legs" to listOf("squat", "hip_hinge", "leg_press", "knee_flexion", "knee_extension", "calf_raise", "leg_raise"),
        "Upper" to listOf("horizontal_press", "horizontal_row", "vertical_press", "vertical_pull", "lateral_raise", "curl", "triceps_pushdown"),
        "Lower" to listOf("squat", "hip_hinge", "lunge", "knee_flexion", "calf_raise", "ab_flexion", "knee_extension"),
        "Chest & Triceps" to listOf("horizontal_press", "incline_press", "chest_fly", "dip", "triceps_pushdown", "triceps_extension", "triceps_press"),
        "Back & Biceps" to listOf("vertical_pull", "horizontal_row", "lat_isolation", "horizontal_row", "curl", "hammer_curl", "curl"),
        "Shoulders & Abs" to listOf("vertical_press", "lateral_raise", "rear_delt", "lateral_raise", "ab_flexion", "leg_raise", "anti_extension"),
    )

    private val splits: Map<Int, List<Pair<String, Set<Int>>>> = mapOf(
        2 to listOf("Full Body A" to setOf(1), "Full Body B" to setOf(4)),
        3 to listOf("Push" to setOf(1), "Pull" to setOf(3), "Legs" to setOf(5)),
        4 to listOf("Upper" to setOf(1, 4), "Lower" to setOf(2, 5)),
        5 to listOf("Chest & Triceps" to setOf(1), "Back & Biceps" to setOf(2), "Legs" to setOf(3),
            "Shoulders & Abs" to setOf(4), "Full Body A" to setOf(5)),
        6 to listOf("Push" to setOf(1, 4), "Pull" to setOf(2, 5), "Legs" to setOf(3, 6)),
    )

    private val fullBody3 = listOf("Full Body A" to setOf(1), "Full Body B" to setOf(3), "Full Body C" to setOf(5))

    fun exercisesThatFit(minutes: Int, goal: Goal): Int {
        val perExercise = goal.sets * (45 + goal.rest) + 120 // seconds incl. setup/warm-up set
        val available = (minutes - 8) * 60 // ~8 min general warm-up
        return (available / perExercise).coerceIn(3, 8)
    }

    fun generate(input: GoalInput): Program {
        val days = input.daysPerWeek.coerceIn(2, 6)
        val plan = if (days == 3 && (input.goal == Goal.FAT_LOSS || input.goal == Goal.GENERAL)) fullBody3 else splits.getValue(days)
        val count = exercisesThatFit(input.minutes, input.goal)
        val workoutDays = plan.mapIndexed { i, (name, weekdays) ->
            val patterns = templates.getValue(name)
            val used = mutableSetOf<String>()
            val exercises = patterns.mapNotNull { p -> pick(p, input.access, used) }
                .take(count)
                .mapIndexed { idx, id ->
                    val compound = idx < 2 && input.goal != Goal.FAT_LOSS
                    PlannedExercise(
                        exerciseId = id,
                        sets = input.goal.sets,
                        repMin = if (compound) input.goal.repMin else maxOf(input.goal.repMin, 8),
                        repMax = if (compound) input.goal.repMax else maxOf(input.goal.repMax, 12),
                        restSec = if (compound) input.goal.rest else minOf(input.goal.rest, 90),
                    )
                }
            WorkoutDay(id = "day${i + 1}", name = name, exercises = fillTime(exercises, input.minutes), weekdays = weekdays)
        }
        return Program(
            name = "${input.goal.label} · $days days",
            days = workoutDays,
            mode = ScheduleMode.WEEKDAYS,
            goalSummary = "${input.goal.label}, $days days/week, ~${input.minutes} min, ${input.access.label}",
        )
    }

    /** When the template runs out of exercises before the time does, add sets to the main lifts first. */
    private fun fillTime(exercises: List<PlannedExercise>, minutes: Int): List<PlannedExercise> {
        val list = exercises.toMutableList()
        var i = 0
        var guard = 0
        while (list.isNotEmpty() && guard++ < 20) {
            val next = list.toMutableList().also { it[i] = it[i].copy(sets = it[i].sets + 1) }
            if (next[i].sets > 5 || Progression.estimatedMinutes(next) > minutes) break
            list[i] = next[i]
            i = (i + 1) % list.size
        }
        return list
    }

    private fun pick(pattern: String, access: GymAccess, used: MutableSet<String>): String? {
        val allowed: List<Equipment> = when (access) {
            GymAccess.FULL_GYM -> listOf(Equipment.BARBELL, Equipment.MACHINE, Equipment.CABLE, Equipment.DUMBBELL,
                Equipment.EZ_BAR, Equipment.SMITH, Equipment.BODYWEIGHT)
            GymAccess.DUMBBELLS -> listOf(Equipment.DUMBBELL, Equipment.BODYWEIGHT, Equipment.KETTLEBELL)
            GymAccess.MINIMAL -> listOf(Equipment.BODYWEIGHT, Equipment.BAND, Equipment.DUMBBELL)
        }
        val candidates = ExerciseLibrary.builtIn.filter { it.pattern == pattern && it.id !in used }
        val choice = allowed.firstNotNullOfOrNull { eq -> candidates.firstOrNull { it.equipment == eq } }
        return choice?.id?.also { used += it }
    }
}
