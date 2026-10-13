package com.mmpoint.spotter.data

import com.mmpoint.spotter.data.Equipment.BARBELL
import com.mmpoint.spotter.data.Equipment.BODYWEIGHT
import com.mmpoint.spotter.data.Equipment.CABLE
import com.mmpoint.spotter.data.Equipment.DUMBBELL
import com.mmpoint.spotter.data.Equipment.EZ_BAR
import com.mmpoint.spotter.data.Equipment.KETTLEBELL
import com.mmpoint.spotter.data.Equipment.MACHINE
import com.mmpoint.spotter.data.Equipment.SMITH
import com.mmpoint.spotter.data.Muscle.BACK
import com.mmpoint.spotter.data.Muscle.BICEPS
import com.mmpoint.spotter.data.Muscle.CALVES
import com.mmpoint.spotter.data.Muscle.CHEST
import com.mmpoint.spotter.data.Muscle.CORE
import com.mmpoint.spotter.data.Muscle.FOREARMS
import com.mmpoint.spotter.data.Muscle.GLUTES
import com.mmpoint.spotter.data.Muscle.HAMSTRINGS
import com.mmpoint.spotter.data.Muscle.QUADS
import com.mmpoint.spotter.data.Muscle.SHOULDERS
import com.mmpoint.spotter.data.Muscle.TRICEPS

/**
 * Built-in exercises. Factors are rough load ratios within a movement pattern
 * (reference lift = 1.0; dumbbell values are per hand). They only seed a first-time
 * estimate on an alternative; after you log it once, your own numbers take over.
 */
object ExerciseLibrary {
    private fun ex(id: String, name: String, m: Muscle, p: String, e: Equipment, f: Double = 1.0, lower: Boolean = false) =
        Exercise(id, name, m, p, e, f, lower)

    val builtIn: List<Exercise> = listOf(
        // Chest
        ex("bb_bench", "Barbell Bench Press", CHEST, "horizontal_press", BARBELL, 1.0),
        ex("db_bench", "Dumbbell Bench Press", CHEST, "horizontal_press", DUMBBELL, 0.40),
        ex("machine_chest_press", "Machine Chest Press", CHEST, "horizontal_press", MACHINE, 0.95),
        ex("smith_bench", "Smith Machine Bench Press", CHEST, "horizontal_press", SMITH, 0.90),
        ex("pushup", "Push-Up", CHEST, "horizontal_press", BODYWEIGHT, 0.0),
        ex("incline_bb_bench", "Incline Barbell Bench Press", CHEST, "incline_press", BARBELL, 1.0),
        ex("incline_db_bench", "Incline Dumbbell Press", CHEST, "incline_press", DUMBBELL, 0.40),
        ex("incline_machine_press", "Incline Machine Press", CHEST, "incline_press", MACHINE, 0.95),
        ex("incline_smith", "Incline Smith Press", CHEST, "incline_press", SMITH, 0.90),
        ex("db_fly", "Dumbbell Fly", CHEST, "chest_fly", DUMBBELL, 0.45),
        ex("cable_fly", "Cable Fly", CHEST, "chest_fly", CABLE, 0.50),
        ex("pec_deck", "Pec Deck", CHEST, "chest_fly", MACHINE, 1.0),
        ex("dips_chest", "Chest Dips", CHEST, "dip", BODYWEIGHT, 0.0),
        ex("machine_dip", "Assisted/Machine Dip", CHEST, "dip", MACHINE, 1.0),

        // Back
        ex("pullup", "Pull-Up", BACK, "vertical_pull", BODYWEIGHT, 0.0),
        ex("lat_pulldown", "Lat Pulldown", BACK, "vertical_pull", CABLE, 1.0),
        ex("close_pulldown", "Close-Grip Pulldown", BACK, "vertical_pull", CABLE, 0.95),
        ex("machine_pulldown", "Machine Pulldown", BACK, "vertical_pull", MACHINE, 1.0),
        ex("bb_row", "Barbell Row", BACK, "horizontal_row", BARBELL, 1.0),
        ex("db_row", "One-Arm Dumbbell Row", BACK, "horizontal_row", DUMBBELL, 0.45),
        ex("cable_row", "Seated Cable Row", BACK, "horizontal_row", CABLE, 0.95),
        ex("machine_row", "Machine Row", BACK, "horizontal_row", MACHINE, 1.0),
        ex("tbar_row", "T-Bar Row", BACK, "horizontal_row", BARBELL, 0.85),
        ex("chest_supported_row", "Chest-Supported Dumbbell Row", BACK, "horizontal_row", DUMBBELL, 0.35),
        ex("straight_arm_pulldown", "Straight-Arm Pulldown", BACK, "lat_isolation", CABLE, 1.0),
        ex("db_pullover", "Dumbbell Pullover", BACK, "lat_isolation", DUMBBELL, 1.0),
        ex("deadlift", "Deadlift", BACK, "hinge", BARBELL, 1.0, lower = true),

        // Shoulders
        ex("ohp", "Overhead Press", SHOULDERS, "vertical_press", BARBELL, 1.0),
        ex("db_shoulder_press", "Dumbbell Shoulder Press", SHOULDERS, "vertical_press", DUMBBELL, 0.40),
        ex("machine_shoulder_press", "Machine Shoulder Press", SHOULDERS, "vertical_press", MACHINE, 1.0),
        ex("smith_ohp", "Smith Shoulder Press", SHOULDERS, "vertical_press", SMITH, 0.90),
        ex("arnold_press", "Arnold Press", SHOULDERS, "vertical_press", DUMBBELL, 0.35),
        ex("db_lateral", "Dumbbell Lateral Raise", SHOULDERS, "lateral_raise", DUMBBELL, 1.0),
        ex("cable_lateral", "Cable Lateral Raise", SHOULDERS, "lateral_raise", CABLE, 0.8),
        ex("machine_lateral", "Machine Lateral Raise", SHOULDERS, "lateral_raise", MACHINE, 2.0),
        ex("rear_delt_fly", "Rear Delt Dumbbell Fly", SHOULDERS, "rear_delt", DUMBBELL, 1.0),
        ex("reverse_pec_deck", "Reverse Pec Deck", SHOULDERS, "rear_delt", MACHINE, 3.0),
        ex("face_pull", "Face Pull", SHOULDERS, "rear_delt", CABLE, 2.5),

        // Biceps
        ex("bb_curl", "Barbell Curl", BICEPS, "curl", BARBELL, 1.0),
        ex("ez_curl", "EZ-Bar Curl", BICEPS, "curl", EZ_BAR, 1.0),
        ex("db_curl", "Dumbbell Curl", BICEPS, "curl", DUMBBELL, 0.45),
        ex("cable_curl", "Cable Curl", BICEPS, "curl", CABLE, 0.9),
        ex("machine_curl", "Machine Curl", BICEPS, "curl", MACHINE, 0.9),
        ex("hammer_curl", "Hammer Curl", BICEPS, "hammer_curl", DUMBBELL, 1.0),
        ex("rope_hammer_curl", "Cable Rope Hammer Curl", BICEPS, "hammer_curl", CABLE, 2.0),
        ex("preacher_curl", "Preacher Curl", BICEPS, "curl", EZ_BAR, 0.85),
        ex("incline_db_curl", "Incline Dumbbell Curl", BICEPS, "curl", DUMBBELL, 0.38),

        // Triceps
        ex("pushdown", "Cable Pushdown", TRICEPS, "triceps_pushdown", CABLE, 1.0),
        ex("rope_pushdown", "Rope Pushdown", TRICEPS, "triceps_pushdown", CABLE, 0.85),
        ex("machine_tri_ext", "Machine Triceps Extension", TRICEPS, "triceps_pushdown", MACHINE, 1.0),
        ex("skullcrusher", "EZ-Bar Skull Crusher", TRICEPS, "triceps_extension", EZ_BAR, 1.0),
        ex("oh_db_ext", "Overhead Dumbbell Extension", TRICEPS, "triceps_extension", DUMBBELL, 0.9),
        ex("oh_cable_ext", "Overhead Cable Extension", TRICEPS, "triceps_extension", CABLE, 0.9),
        ex("cgbp", "Close-Grip Bench Press", TRICEPS, "triceps_press", BARBELL, 1.0),
        ex("bench_dips", "Bench Dips", TRICEPS, "triceps_press", BODYWEIGHT, 0.0),
        ex("dips_tri", "Triceps Dips", TRICEPS, "triceps_press", BODYWEIGHT, 0.0),

        // Legs
        ex("back_squat", "Back Squat", QUADS, "squat", BARBELL, 1.0, lower = true),
        ex("front_squat", "Front Squat", QUADS, "squat", BARBELL, 0.80, lower = true),
        ex("smith_squat", "Smith Machine Squat", QUADS, "squat", SMITH, 0.90, lower = true),
        ex("hack_squat", "Hack Squat", QUADS, "squat", MACHINE, 1.10, lower = true),
        ex("goblet_squat", "Goblet Squat", QUADS, "squat", DUMBBELL, 0.35, lower = true),
        ex("leg_press", "Leg Press", QUADS, "leg_press", MACHINE, 1.0, lower = true),
        ex("leg_ext", "Leg Extension", QUADS, "knee_extension", MACHINE, 1.0, lower = true),
        ex("split_squat", "Bulgarian Split Squat", QUADS, "lunge", DUMBBELL, 1.0, lower = true),
        ex("walking_lunge", "Walking Lunge", QUADS, "lunge", DUMBBELL, 1.1, lower = true),
        ex("smith_lunge", "Smith Machine Lunge", QUADS, "lunge", SMITH, 2.0, lower = true),
        ex("rdl", "Romanian Deadlift", HAMSTRINGS, "hip_hinge", BARBELL, 1.0, lower = true),
        ex("db_rdl", "Dumbbell Romanian Deadlift", HAMSTRINGS, "hip_hinge", DUMBBELL, 0.40, lower = true),
        ex("good_morning", "Good Morning", HAMSTRINGS, "hip_hinge", BARBELL, 0.55, lower = true),
        ex("lying_leg_curl", "Lying Leg Curl", HAMSTRINGS, "knee_flexion", MACHINE, 1.0, lower = true),
        ex("seated_leg_curl", "Seated Leg Curl", HAMSTRINGS, "knee_flexion", MACHINE, 1.1, lower = true),
        ex("hip_thrust", "Barbell Hip Thrust", GLUTES, "hip_thrust", BARBELL, 1.0, lower = true),
        ex("machine_hip_thrust", "Machine Hip Thrust", GLUTES, "hip_thrust", MACHINE, 0.9, lower = true),
        ex("glute_bridge", "Glute Bridge", GLUTES, "hip_thrust", BODYWEIGHT, 0.0, lower = true),
        ex("cable_kickback", "Cable Glute Kickback", GLUTES, "glute_iso", CABLE, 1.0, lower = true),
        ex("hip_abduction", "Hip Abduction Machine", GLUTES, "glute_iso", MACHINE, 3.0, lower = true),
        ex("standing_calf", "Standing Calf Raise", CALVES, "calf_raise", MACHINE, 1.0, lower = true),
        ex("seated_calf", "Seated Calf Raise", CALVES, "calf_raise", MACHINE, 0.6, lower = true),
        ex("leg_press_calf", "Leg Press Calf Raise", CALVES, "calf_raise", MACHINE, 1.3, lower = true),
        ex("db_calf", "Dumbbell Calf Raise", CALVES, "calf_raise", DUMBBELL, 0.3, lower = true),

        // Core / misc
        ex("cable_crunch", "Cable Crunch", CORE, "ab_flexion", CABLE, 1.0),
        ex("machine_crunch", "Machine Crunch", CORE, "ab_flexion", MACHINE, 1.0),
        ex("crunch", "Crunch", CORE, "ab_flexion", BODYWEIGHT, 0.0),
        ex("hanging_leg_raise", "Hanging Leg Raise", CORE, "leg_raise", BODYWEIGHT, 0.0),
        ex("lying_leg_raise", "Lying Leg Raise", CORE, "leg_raise", BODYWEIGHT, 0.0),
        ex("plank", "Plank (reps = seconds)", CORE, "anti_extension", BODYWEIGHT, 0.0),
        ex("ab_wheel", "Ab Wheel Rollout", CORE, "anti_extension", BODYWEIGHT, 0.0),
        ex("pallof", "Pallof Press", CORE, "anti_rotation", CABLE, 1.0),
        ex("wrist_curl", "Wrist Curl", FOREARMS, "wrist", DUMBBELL, 1.0),
        ex("farmer_carry", "Farmer Carry (reps = steps)", FOREARMS, "carry", DUMBBELL, 1.0),
        ex("kb_swing", "Kettlebell Swing", GLUTES, "hip_hinge_ballistic", KETTLEBELL, 1.0, lower = true),
    )

    private val byId = builtIn.associateBy { it.id }

    fun all(custom: List<Exercise>): List<Exercise> = builtIn + custom

    fun find(id: String, custom: List<Exercise>): Exercise? = byId[id] ?: custom.firstOrNull { it.id == id }

    fun name(id: String, custom: List<Exercise>): String = find(id, custom)?.name ?: id
}
