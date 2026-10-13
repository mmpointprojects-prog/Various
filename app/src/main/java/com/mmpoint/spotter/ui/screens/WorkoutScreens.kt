package com.mmpoint.spotter.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.mmpoint.spotter.data.Actions
import com.mmpoint.spotter.data.AppData
import com.mmpoint.spotter.data.ExerciseLibrary
import com.mmpoint.spotter.data.ExerciseLog
import com.mmpoint.spotter.data.Muscle
import com.mmpoint.spotter.data.PlannedExercise
import com.mmpoint.spotter.data.Readiness
import com.mmpoint.spotter.data.Store
import com.mmpoint.spotter.goTab
import com.mmpoint.spotter.logic.Planner
import com.mmpoint.spotter.logic.Progression
import com.mmpoint.spotter.logic.Recovery
import com.mmpoint.spotter.logic.TimeUtil
import com.mmpoint.spotter.notify.Reminders
import com.mmpoint.spotter.ui.BackBar
import com.mmpoint.spotter.ui.ExercisePicker
import com.mmpoint.spotter.ui.NumberField
import com.mmpoint.spotter.ui.RatingRow
import com.mmpoint.spotter.ui.SectionCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Rest countdown shared between screens. The alarm keeps it working with the screen off. */
object RestTimer {
    val endsAt = MutableStateFlow<Long?>(null)
    val total = MutableStateFlow(0)

    fun start(ctx: Context, seconds: Int, nextLabel: String) {
        if (seconds <= 0) return
        endsAt.value = System.currentTimeMillis() + seconds * 1000L
        total.value = seconds
        if (Store.current.settings.restAlarm) Reminders.scheduleRest(ctx, seconds, nextLabel)
    }

    fun add(ctx: Context, seconds: Int, nextLabel: String) {
        val end = endsAt.value ?: return
        val newEnd = end + seconds * 1000L
        endsAt.value = newEnd
        total.value += seconds
        val left = ((newEnd - System.currentTimeMillis()) / 1000).toInt()
        if (left > 0 && Store.current.settings.restAlarm) Reminders.scheduleRest(ctx, left, nextLabel)
    }

    fun stop(ctx: Context) {
        endsAt.value = null
        Reminders.cancelRest(ctx)
    }
}

@Composable
fun TodayScreen(nav: NavHostController) {
    val data by Store.data.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val now = LocalTime.now()
    val day = Planner.dayFor(data, today)
    val morning = Planner.morningCheckIn(data, today)
    val evening = Planner.eveningCheckIn(data, today)
    var chooseOther by remember { mutableStateOf(false) }
    val doneToday = data.sessions.filter { TimeUtil.dateOf(it.startedAt) == today }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(today.format(DateTimeFormatter.ofPattern("EEEE, MMM d")), style = MaterialTheme.typography.headlineSmall)

        data.activeSession?.let { s ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Workout in progress", style = MaterialTheme.typography.titleMedium)
                    Text("${s.dayName}${s.partLabel?.let { " · $it" } ?: ""} — started ${TimeUtil.prettyMillis(s.startedAt)}")
                    Button(onClick = { nav.navigate("workout") }) { Text("Resume") }
                }
            }
        }

        if (morning == null) {
            SectionCard("Morning check-in") {
                Text("How are you feeling, are you training today, and when will you hit the gym?")
                Button(onClick = { nav.navigate("checkin/MORNING") }) { Text("Check in") }
            }
        } else {
            SectionCard("This morning") {
                val gym = TimeUtil.parse(morning.gymTime)
                Text("Feeling ${morning.feeling}/5 · soreness ${morning.soreness}/5" + (morning.sleepHours?.let { " · slept ${it}h" } ?: ""))
                if (morning.wantsWorkout == true && gym != null) {
                    val snack = Planner.snackTime(gym, data.settings.snackLeadMin)!!
                    Text("Gym at ${TimeUtil.pretty(gym)} · snack at ${TimeUtil.pretty(snack)}", fontWeight = FontWeight.Medium)
                } else if (morning.wantsWorkout == false) Text("You planned to rest today.")
                TextButton(onClick = { nav.navigate("checkin/MORNING") }) { Text("Update") }
            }
        }

        if (data.activeSession == null) {
            SectionCard(if (day != null) "Today: ${day.name}" else "Rest day") {
                if (day != null) {
                    val muscles = Planner.musclesOf(data, day.exercises)
                    Text("${muscles.joinToString(" · ") { it.label }} — ${day.exercises.size} exercises, ≈ ${Progression.estimatedMinutes(day.exercises)} min")
                    Button(onClick = { nav.navigate("readiness/day/${day.id}") }) { Text("Start workout") }
                } else Text("Nothing scheduled. Recover — or pick a split below if you want to train anyway.")
                TextButton(onClick = { chooseOther = !chooseOther }) { Text(if (chooseOther) "Hide other splits" else "Do a different split") }
                if (chooseOther) data.program.days.filter { it.id != day?.id }.forEach { d ->
                    OutlinedButton(onClick = { nav.navigate("readiness/day/${d.id}") }, modifier = Modifier.fillMaxWidth()) { Text(d.name) }
                }
            }
            data.pending.forEach { p ->
                SectionCard("Finish later: " + Planner.musclesOf(data, p.exercises).joinToString(" & ") { it.label }) {
                    Text("Saved from ${p.dayName} on ${TimeUtil.dateOf(p.createdAt).format(DateTimeFormatter.ofPattern("EEE MMM d"))} · ${p.exercises.size} exercises")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { nav.navigate("readiness/pending/${p.id}") }) { Text("Start now") }
                        TextButton(onClick = { Actions.discardPending(p.id) }) { Text("Discard") }
                    }
                }
            }
        }

        if (doneToday.isNotEmpty()) SectionCard("Done today") {
            doneToday.forEach { s ->
                Text("${s.dayName}${s.partLabel?.let { " ($it)" } ?: ""}: ${TimeUtil.prettyMillis(s.startedAt)} – " +
                    (s.endedAt?.let { TimeUtil.prettyMillis(it) + " · " + TimeUtil.duration(it - s.startedAt) } ?: "…"))
            }
        }

        val doses = Planner.dosesFor(data, today)
        if (doses.isNotEmpty()) SectionCard("Peptides & supplements today") {
            doses.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = d.taken, onCheckedChange = { c ->
                        if (c) Actions.logDose(d.supp.id, today, d.time) else Actions.undoDose(d.supp.id, today, d.time)
                    })
                    Column {
                        Text("${d.supp.name} ${d.supp.dose} ${d.supp.unit}".trim(), fontWeight = FontWeight.Medium)
                        Text(TimeUtil.pretty(d.time) + if (d.skipped) " · skipped" else "", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (today.dayOfWeek.value == data.settings.weighDay && data.weights.none { it.date == today.toString() }) {
            SectionCard("Weekly weigh-in") {
                Text("It's weigh-in day.")
                Button(onClick = { nav.navigate("weight") }) { Text("Log weight") }
            }
        }

        val eveningAt = TimeUtil.parse(data.settings.eveningTime) ?: LocalTime.of(21, 0)
        if (evening == null && now.isAfter(eveningAt.minusHours(2))) SectionCard("Evening check-in") {
            Text("Wrap up the day and get recovery suggestions.")
            Button(onClick = { nav.navigate("checkin/EVENING") }) { Text("Check in") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadinessScreen(nav: NavHostController, source: String, id: String) {
    val data by Store.data.collectAsStateWithLifecycle()
    val day = if (source == "day") data.program.days.firstOrNull { it.id == id } else null
    val part = if (source == "pending") data.pending.firstOrNull { it.id == id } else null
    val exercises: List<PlannedExercise> = day?.exercises ?: part?.exercises ?: emptyList()
    val muscles = Planner.musclesOf(data, exercises)
    val morning = Planner.morningCheckIn(data, LocalDate.now())

    var energy by remember { mutableStateOf(morning?.feeling ?: 3) }
    var sleep by remember { mutableStateOf(3) }
    var soreness by remember { mutableStateOf(morning?.soreness ?: 2) }
    var splitMode by remember { mutableStateOf(false) }
    var nowMuscles by remember { mutableStateOf(setOf<Muscle>()) }

    fun go() = nav.navigate("workout") { popUpTo("today") }

    Column(Modifier.fillMaxSize()) {
        BackBar(day?.name ?: part?.let { "Finish: ${it.dayName}" } ?: "Workout") { nav.popBackStack() }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("How do you feel? Are you good to do ${muscles.joinToString(" & ") { it.label }} today?",
                style = MaterialTheme.typography.titleMedium)
            RatingRow("Energy", energy, "drained", "fired up") { energy = it }
            RatingRow("Last night's sleep", sleep, "terrible", "great") { sleep = it }
            RatingRow("Soreness in today's muscles", soreness, "none", "very sore") { soreness = it }
            SectionCard { Text(Recovery.readiness(energy, sleep, soreness)) }

            val readiness = { decision: String -> Readiness(energy, sleep, soreness, decision) }
            Button(onClick = {
                if (day != null) Actions.startDay(day, readiness("full")) else if (part != null) Actions.startPending(part, readiness("full"))
                go()
            }, modifier = Modifier.fillMaxWidth()) { Text(if (part != null) "Start" else "Full workout") }

            if (day != null && muscles.size > 1) {
                OutlinedButton(onClick = { splitMode = !splitMode }, modifier = Modifier.fillMaxWidth()) {
                    Text("Split it — do part now, the rest later")
                }
                if (splitMode) SectionCard("What do you want to do now?") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        muscles.forEach { m ->
                            FilterChip(m in nowMuscles, { nowMuscles = if (m in nowMuscles) nowMuscles - m else nowMuscles + m },
                                label = { Text(m.label) })
                        }
                    }
                    val later = muscles.filterNot { it in nowMuscles }
                    if (nowMuscles.isNotEmpty() && later.isNotEmpty())
                        Text("Later: ${later.joinToString(" & ") { it.label }} — it'll wait on your Today screen.")
                    Button(
                        enabled = nowMuscles.isNotEmpty() && later.isNotEmpty(),
                        onClick = { Actions.startDay(day, readiness("split"), nowMuscles); go() },
                    ) { Text("Start ${nowMuscles.joinToString(" & ") { it.label }}") }
                }
            }
            TextButton(onClick = { nav.popBackStack() }) { Text("Not today — rest") }
        }
    }
}

@Composable
fun WorkoutScreen(nav: NavHostController) {
    val data by Store.data.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val s = data.activeSession
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { nowMs = System.currentTimeMillis(); delay(500) } }
    val restEnd by RestTimer.endsAt.collectAsStateWithLifecycle()
    var finishing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<Int?>(null) }

    if (s == null) {
        Column(Modifier.padding(16.dp)) {
            BackBar("Workout") { nav.goTab("today") }
            Text("No workout in progress.")
        }
        return
    }
    val current = s.exercises.indexOfFirst { it.sets.size < it.targetSets && !it.skipped }
    val open = expanded ?: current

    Column(Modifier.fillMaxSize()) {
        BackBar("${s.dayName}${s.partLabel?.let { " · $it" } ?: ""}") { nav.goTab("today") }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Elapsed ${TimeUtil.clock((nowMs - s.startedAt) / 1000)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = { finishing = true }) { Text("Finish") }
        }

        restEnd?.let { end ->
            val left = ((end - nowMs) / 1000).coerceAtLeast(0)
            val next = s.exercises.getOrNull(current)?.let { ExerciseLibrary.name(it.exerciseId, data.customExercises) } ?: "next set"
            Card(
                Modifier.padding(16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (left == 0L) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (left == 0L) "Rest over — go!" else "Rest", style = MaterialTheme.typography.titleMedium)
                    Text(TimeUtil.clock(left), fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    Text("Next: $next", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { RestTimer.add(ctx, 30, next) }) { Text("+30s") }
                        Button(onClick = { RestTimer.stop(ctx) }) { Text(if (left == 0L) "Dismiss" else "Skip rest") }
                    }
                }
            }
        }

        LazyColumn(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(s.exercises) { i, e ->
                ExerciseCard(data, i, e, expanded = i == open, isCurrent = i == current,
                    onToggle = { expanded = if (open == i) -1 else i },
                    onLogged = { done ->
                        val nextName = if (done) s.exercises.getOrNull(i + 1)?.let { ExerciseLibrary.name(it.exerciseId, data.customExercises) } ?: "Finish"
                        else ExerciseLibrary.name(e.exerciseId, data.customExercises)
                        RestTimer.start(ctx, e.restSec, "Up next: $nextName")
                        expanded = null
                    })
            }
            item {
                OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Add an exercise") }
                Spacer(Modifier.padding(16.dp))
            }
        }
    }

    if (adding) ExercisePicker("Add exercise", ExerciseLibrary.all(data.customExercises), onDismiss = { adding = false }) {
        Actions.addExercise(it.id); adding = false
    }

    if (finishing) {
        var notes by remember { mutableStateOf("") }
        val untouched = s.exercises.filter { it.sets.isEmpty() && !it.skipped }
        AlertDialog(
            onDismissRequest = { finishing = false },
            title = { Text("Finish workout?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Duration: ${TimeUtil.duration(nowMs - s.startedAt)} · ${s.exercises.sumOf { it.sets.size }} sets")
                    if (untouched.isNotEmpty()) Text("Not started: " + untouched.joinToString { ExerciseLibrary.name(it.exerciseId, data.customExercises) })
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") })
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (untouched.isNotEmpty()) TextButton(onClick = {
                        Actions.finish(deferRest = true, notes); RestTimer.stop(ctx); finishing = false; nav.goTab("today")
                    }) { Text("Finish & save the rest for later") }
                    TextButton(onClick = {
                        Actions.finish(deferRest = false, notes); RestTimer.stop(ctx); finishing = false; nav.goTab("today")
                    }) { Text(if (untouched.isEmpty()) "Finish" else "Finish & drop the rest") }
                    TextButton(onClick = {
                        Actions.cancelActive(); RestTimer.stop(ctx); finishing = false; nav.goTab("today")
                    }) { Text("Discard workout", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { finishing = false }) { Text("Keep going") }
                }
            },
        )
    }
}

private val swapReasons = listOf("Equipment taken", "Pain / tweak", "Too fatigued", "Not at this gym")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCard(
    data: AppData, index: Int, e: ExerciseLog, expanded: Boolean, isCurrent: Boolean,
    onToggle: () -> Unit, onLogged: (exerciseDone: Boolean) -> Unit,
) {
    val unit = data.settings.unit
    val name = ExerciseLibrary.name(e.exerciseId, data.customExercises)
    val done = e.sets.size >= e.targetSets
    var weight by remember(e.exerciseId, e.sets.size) {
        mutableStateOf((e.sets.lastOrNull()?.weight ?: e.targetWeight)?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "")
    }
    var reps by remember(e.exerciseId, e.sets.size) { mutableStateOf((e.sets.lastOrNull()?.reps ?: e.targetReps ?: e.repMax).toString()) }
    var setStart by remember(e.exerciseId, e.sets.size) { mutableStateOf<Long?>(null) }
    var cantDo by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf(swapReasons.first()) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                e.skipped -> MaterialTheme.colorScheme.surfaceVariant
                isCurrent -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainer
            }
        ),
    ) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.clickable { onToggle() }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    e.swappedFrom?.let { Text("Instead of ${ExerciseLibrary.name(it, data.customExercises)} (${e.swapReason})", style = MaterialTheme.typography.bodySmall) }
                }
                Text(
                    when {
                        e.skipped -> "skipped"
                        done -> "${e.sets.size}/${e.targetSets} ✓"
                        else -> "${e.sets.size}/${e.targetSets}"
                    }
                )
            }
            if (!expanded) return@Column

            val tw = e.targetWeight
            Text(
                "Target: ${e.targetSets} × ${e.targetReps ?: e.repMax}" + (tw?.let { " @ ${Progression.fmtW(it, unit)}" } ?: "") +
                    "  (range ${e.repMin}–${e.repMax}, rest ${e.restSec}s)",
                fontWeight = FontWeight.Medium,
            )
            if (e.targetNote.isNotBlank()) Text(e.targetNote, style = MaterialTheme.typography.bodySmall)

            e.sets.forEachIndexed { n, set ->
                Text("Set ${n + 1}: ${set.reps} × ${Progression.fmtW(set.weight, unit)}" + (set.durationSec?.let { " · ${it}s" } ?: ""))
            }
            val prevBest = Progression.history(data, e.exerciseId).flatMap { it.sets }.maxOfOrNull { Progression.e1rm(it.weight, it.reps) }
            val curBest = e.sets.maxOfOrNull { Progression.e1rm(it.weight, it.reps) }
            if (prevBest != null && curBest != null && curBest > prevBest && curBest > 0) {
                Text("New PR — est. 1RM ${Progression.fmtW(Math.round(curBest).toDouble(), unit)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }

            if (!e.skipped) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Weight ($unit)", weight, Modifier.weight(1f)) { weight = it }
                    NumberField("Reps", reps, Modifier.weight(1f), decimal = false) { reps = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (setStart == null) OutlinedButton(onClick = { setStart = System.currentTimeMillis() }) { Text("Start set timer") }
                    else Text("Set running…", color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    Button(
                        enabled = reps.toIntOrNull() != null,
                        onClick = {
                            val dur = setStart?.let { ((System.currentTimeMillis() - it) / 1000).toInt() }
                            Actions.logSet(index, reps.toInt(), weight.toDoubleOrNull() ?: 0.0, dur)
                            onLogged(e.sets.size + 1 >= e.targetSets)
                        },
                    ) { Text("Log set ${e.sets.size + 1}") }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (e.sets.isEmpty() && !e.skipped) FilledTonalButton(onClick = { cantDo = true }) { Text("Can't do this") }
                if (e.sets.isEmpty() && !e.skipped) TextButton(onClick = { Actions.moveToEnd(index) }) { Text("Do it later") }
                if (e.sets.isNotEmpty()) TextButton(onClick = { Actions.removeLastSet(index) }) { Text("Undo last set") }
                if (!done) TextButton(onClick = { Actions.skip(index, !e.skipped) }) { Text(if (e.skipped) "Unskip" else "Skip") }
            }
        }
    }

    if (cantDo) {
        val planned = PlannedExercise(e.swappedFrom ?: e.exerciseId, e.targetSets, e.repMin, e.repMax, e.restSec)
        val alts = Progression.alternatives(data, planned.exerciseId).filter { it.id != e.exerciseId }
        AlertDialog(
            onDismissRequest = { cantDo = false },
            confirmButton = { TextButton(onClick = { cantDo = false }) { Text("Cancel") } },
            title = { Text("Can't do $name?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        swapReasons.forEach { r -> FilterChip(reason == r, { reason = r }, label = { Text(r) }) }
                    }
                    if (reason == "Pain / tweak") Text("If it's sharp or joint pain, skip it and anything that loads the same area. Don't push through.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Text("Pick an alternative:", fontWeight = FontWeight.Medium)
                    alts.take(12).forEach { alt ->
                        val t = Progression.suggestAlternative(data, planned, alt, unit)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { Actions.swap(index, alt, reason); cantDo = false }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(alt.name, fontWeight = FontWeight.Medium)
                            Text("${e.targetSets} × ${t.reps}" + (t.weight?.let { " @ ${Progression.fmtW(it, unit)}" } ?: " · pick a weight"),
                                color = MaterialTheme.colorScheme.primary)
                            Text(t.note, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (alts.isEmpty()) Text("No alternatives for this one — skip it or add a custom exercise.")
                }
            },
        )
    }
}
