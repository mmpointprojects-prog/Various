package com.mmpoint.spotter.logic

/**
 * Rule-based suggestions from how you feel. General wellness info only — not medical
 * advice. Peptide/medication doses are never suggested here; enter the protocol your
 * prescriber gave you.
 */
object Recovery {

    data class Input(
        val feeling: Int,        // 1 awful … 5 great
        val sleepHours: Double?, // morning only
        val soreness: Int,       // 1 none … 5 very sore
        val stress: Int,         // 1 low … 5 high
        val evening: Boolean,
        val trainedToday: Boolean? = null,
        val trainingTomorrow: Boolean = false,
    )

    fun suggestions(i: Input): List<String> {
        val out = mutableListOf<String>()
        val sleep = i.sleepHours
        if (sleep != null && sleep < 6) {
            out += "Short sleep (${"%.1f".format(sleep)} h): keep today's session but cut 1 set per exercise or stop 2 reps shy of failure."
            out += "Caffeine before noon only today so it doesn't wreck tonight's sleep."
        }
        if (i.soreness >= 4) {
            out += "Very sore: 10 min easy cardio + mobility before lifting. If the sore muscle is on today's plan, consider splitting the day and training it later."
            out += "Hydrate (about half your bodyweight in lb as oz of water) and hit your protein (~0.7–1 g per lb bodyweight)."
            out += "Magnesium glycinate in the evening and an Epsom-salt bath are common recovery aids — check with your doctor first."
        } else if (i.soreness == 3) {
            out += "Moderate soreness: a longer warm-up and lighter first working set should do it."
        }
        if (i.feeling <= 2) {
            out += "Low energy: eat carbs + a little protein 60–90 min before training; add electrolytes to your water."
            out += "If you still feel flat after the warm-up, switch to a lighter 'pump' session or take a rest day — consistency beats one bad workout."
        }
        if (i.stress >= 4) {
            out += "High stress: 5 minutes of slow breathing (4 s in, 6 s out) and a short walk outside lower stress hormones."
        }
        if (i.evening) {
            out += "Wind down: screens off 30–60 min before bed, cool dark room, same bedtime as yesterday."
            if (i.trainedToday == true) out += "You trained today — a protein-rich dinner (30–50 g) helps recovery overnight."
            if (i.trainingTomorrow) out += "Training tomorrow: get 7–9 h of sleep and lay out your gym gear tonight."
        } else {
            out += "Common daily basics many lifters use: vitamin D3 (esp. in winter), omega-3s, creatine monohydrate 3–5 g. Ask your doctor what fits you."
        }
        if (out.isEmpty() || (i.feeling >= 4 && i.soreness <= 2)) {
            out.add(0, "You're in good shape today — go hit it.")
        }
        return out
    }

    /** Readiness verdict for the pre-workout check-in. */
    fun readiness(energy: Int, sleep: Int, targetSoreness: Int): String = when {
        targetSoreness >= 4 -> "The muscles on today's plan are still very sore. Consider splitting the day: do the fresh muscle group now and the sore one later."
        energy <= 2 && sleep <= 2 -> "Low energy and poor sleep. Do a shorter session (drop the last set of each exercise) or take a rest day."
        energy <= 2 || sleep <= 2 -> "A bit below par. Train, but leave 2–3 reps in reserve on every set."
        else -> "You look ready. Full workout."
    }
}
