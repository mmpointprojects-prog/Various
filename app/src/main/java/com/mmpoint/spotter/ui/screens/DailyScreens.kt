package com.mmpoint.spotter.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.mmpoint.spotter.data.CheckInKind
import com.mmpoint.spotter.data.DailyCheckIn
import com.mmpoint.spotter.data.ExerciseLibrary
import com.mmpoint.spotter.data.Store
import com.mmpoint.spotter.data.WeightEntry
import com.mmpoint.spotter.logic.Planner
import com.mmpoint.spotter.logic.Progression
import com.mmpoint.spotter.logic.Recovery
import com.mmpoint.spotter.logic.TimeUtil
import com.mmpoint.spotter.notify.Reminders
import com.mmpoint.spotter.ui.BackBar
import com.mmpoint.spotter.ui.NumberField
import com.mmpoint.spotter.ui.RatingRow
import com.mmpoint.spotter.ui.SectionCard
import com.mmpoint.spotter.ui.TimeField
import com.mmpoint.spotter.ui.weekdayNames
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun CheckInScreen(nav: NavHostController, kind: CheckInKind) {
    val data by Store.data.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val today = LocalDate.now()
    val morningKind = kind == CheckInKind.MORNING
    val existing = if (morningKind) Planner.morningCheckIn(data, today) else Planner.eveningCheckIn(data, today)
    val lastGym = data.checkIns.lastOrNull { it.gymTime != null }?.gymTime ?: "17:00"
    val todayDay = Planner.dayFor(data, today)
    val tomorrowDay = Planner.dayFor(data, today.plusDays(1))
    val trainedToday = data.sessions.any { TimeUtil.dateOf(it.startedAt) == today }

    var feeling by remember { mutableStateOf(existing?.feeling ?: 3) }
    var sleep by remember { mutableStateOf(existing?.sleepHours?.toString() ?: "") }
    var soreness by remember { mutableStateOf(existing?.soreness ?: 2) }
    var stress by remember { mutableStateOf(existing?.stress ?: 2) }
    var wants by remember { mutableStateOf(existing?.wantsWorkout ?: (todayDay != null)) }
    var gym by remember { mutableStateOf(existing?.gymTime ?: lastGym) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var saved by remember { mutableStateOf<DailyCheckIn?>(null) }

    Column(Modifier.fillMaxSize()) {
        BackBar(if (morningKind) "Morning check-in" else "Evening check-in") { nav.popBackStack() }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (saved == null) {
                RatingRow("How do you feel?", feeling, "awful", "great") { feeling = it }
                if (morningKind) NumberField("Hours slept", sleep, Modifier.fillMaxWidth()) { sleep = it }
                RatingRow("Soreness", soreness, "none", "very sore") { soreness = it }
                RatingRow("Stress", stress, "calm", "maxed out") { stress = it }
                if (morningKind) {
                    Text(todayDay?.let { "Today's plan: ${it.name}. Do you want to work out today?" } ?: "Rest day planned. Want to work out anyway?")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(wants, { wants = true }, label = { Text("Yes") })
                        FilterChip(!wants, { wants = false }, label = { Text("No, rest") })
                    }
                    if (wants) {
                        Text("What time do you think you'll be at the gym?")
                        TimeField("Gym time", gym) { gym = it }
                    }
                } else {
                    Text(if (trainedToday) "You trained today." else "No workout logged today.")
                    Text(tomorrowDay?.let { "Tomorrow: ${it.name}" } ?: "Tomorrow: rest day")
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val c = DailyCheckIn(
                        id = existing?.id ?: Store.newId(), date = today.toString(), kind = kind, at = System.currentTimeMillis(),
                        feeling = feeling, sleepHours = sleep.toDoubleOrNull(), soreness = soreness, stress = stress,
                        wantsWorkout = if (morningKind) wants else null, gymTime = if (morningKind && wants) gym else null,
                        trainedToday = if (!morningKind) trainedToday else null, notes = notes,
                    )
                    Store.update { d -> d.copy(checkIns = d.checkIns.filterNot { it.date == c.date && it.kind == c.kind } + c) }
                    Reminders.rescheduleAll(ctx)
                    saved = c
                }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            } else {
                val c = saved!!
                if (morningKind && c.wantsWorkout == true) {
                    val g = TimeUtil.parse(c.gymTime)
                    val snack = Planner.snackTime(g, data.settings.snackLeadMin)
                    SectionCard("Today's timing") {
                        Text("Gym at ${TimeUtil.pretty(c.gymTime)}", fontWeight = FontWeight.Medium)
                        snack?.let {
                            Text("Small snack at ${TimeUtil.pretty(it)} — I'll remind you.")
                            Text("Ideas: banana + Greek yogurt, rice cakes + peanut butter, toast + honey, a small protein shake with fruit.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                SectionCard("Suggestions") {
                    Recovery.suggestions(
                        Recovery.Input(c.feeling, c.sleepHours, c.soreness, c.stress, evening = !morningKind,
                            trainedToday = c.trainedToday, trainingTomorrow = tomorrowDay != null)
                    ).forEach { Text("• $it") }
                    Text("General wellness info, not medical advice.", style = MaterialTheme.typography.bodySmall)
                }
                val doses = Planner.dosesFor(data, today).filter { !it.taken && !it.skipped }
                if (doses.isNotEmpty()) SectionCard("Still to take today") {
                    doses.forEach { Text("${TimeUtil.pretty(it.time)} — ${it.supp.name} ${it.supp.dose} ${it.supp.unit}".trim()) }
                }
                Button(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
        }
    }
}

@Composable
fun CalendarScreen() {
    val data by Store.data.collectAsStateWithLifecycle()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val today = LocalDate.now()
    val sessionDates = data.sessions.groupBy { TimeUtil.dateOf(it.startedAt) }
    val doseDates = data.doseLogs.filter { !it.skipped }.map { it.date }.toSet()
    val weightDates = data.weights.map { it.date }.toSet()
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val secondary = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outline

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
        }
        Row { weekdayNames.forEach { Text(it.take(2), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) } }
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - 1
        val cells = lead + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                        if (dayNum in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayNum)
                            val isSel = date == selected
                            val planned = date >= today && Planner.dayFor(data, date) != null
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { selected = date },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text("$dayNum", fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal)
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (sessionDates.containsKey(date)) Dot(primary)
                                    else if (planned) Dot(outline)
                                    if (date.toString() in doseDates) Dot(tertiary)
                                    if (date.toString() in weightDates) Dot(secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(primary, "Workout"); Legend(outline, "Planned"); Legend(tertiary, "Doses"); Legend(secondary, "Weigh-in")
        }
        HorizontalDivider()
        DayDetail(selected)
    }
}

@Composable
private fun Dot(color: Color) = Box(Modifier.size(6.dp).clip(CircleShape).background(color))

@Composable
private fun Legend(color: Color, label: String) =
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Dot(color); Text(label, style = MaterialTheme.typography.labelSmall)
    }

@Composable
private fun DayDetail(date: LocalDate) {
    val data by Store.data.collectAsStateWithLifecycle()
    val unit = data.settings.unit
    Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")), style = MaterialTheme.typography.titleMedium)
    val sessions = data.sessions.filter { TimeUtil.dateOf(it.startedAt) == date }
    if (sessions.isEmpty()) {
        val planned = if (date >= LocalDate.now()) Planner.dayFor(data, date) else null
        Text(planned?.let { "Planned: ${it.name}" } ?: "No workout")
    }
    sessions.forEach { s ->
        SectionCard("${s.dayName}${s.partLabel?.let { " · $it" } ?: ""}") {
            Text("Start ${TimeUtil.prettyMillis(s.startedAt)} · Stop ${s.endedAt?.let { TimeUtil.prettyMillis(it) } ?: "—"}" +
                (s.endedAt?.let { " · ${TimeUtil.duration(it - s.startedAt)}" } ?: ""), fontWeight = FontWeight.Medium)
            s.readiness?.let { Text("Readiness: energy ${it.energy}/5, sleep ${it.sleep}/5, soreness ${it.soreness}/5 (${it.decision})", style = MaterialTheme.typography.bodySmall) }
            s.exercises.forEach { e ->
                val name = ExerciseLibrary.name(e.exerciseId, data.customExercises)
                val sets = if (e.skipped) "skipped" else e.sets.joinToString(", ") { "${it.reps}×${Progression.fmtW(it.weight, unit)}" }
                Text("$name: $sets" + (e.swappedFrom?.let { " (swapped for ${ExerciseLibrary.name(it, data.customExercises)})" } ?: ""),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (s.notes.isNotBlank()) Text("Notes: ${s.notes}", style = MaterialTheme.typography.bodySmall)
        }
    }
    data.checkIns.filter { it.date == date.toString() }.forEach { c ->
        Text("${if (c.kind == CheckInKind.MORNING) "Morning" else "Evening"} check-in ${TimeUtil.prettyMillis(c.at)}: feeling ${c.feeling}/5, soreness ${c.soreness}/5, stress ${c.stress}/5" +
            (c.sleepHours?.let { ", slept ${it}h" } ?: "") + (c.gymTime?.let { ", gym ${TimeUtil.pretty(it)}" } ?: ""),
            style = MaterialTheme.typography.bodySmall)
    }
    val doses = Planner.dosesFor(data, date)
    if (doses.isNotEmpty()) SectionCard("Doses") {
        doses.forEach { d ->
            val log = data.doseLogs.lastOrNull { it.supplementId == d.supp.id && it.date == date.toString() && it.time == d.time }
            val status = when {
                d.taken -> "✓ taken ${log?.let { TimeUtil.prettyMillis(it.at) } ?: ""}" + (log?.site?.let { " · $it" } ?: "")
                d.skipped -> "skipped"
                date < LocalDate.now() -> "missed"
                else -> "due"
            }
            Text("${TimeUtil.pretty(d.time)} ${d.supp.name} ${d.supp.dose} ${d.supp.unit} — $status", style = MaterialTheme.typography.bodySmall)
        }
    }
    data.weights.filter { it.date == date.toString() }.forEach { Text("Weight: ${it.weight} $unit") }
}

@Composable
fun WeightScreen(nav: NavHostController) {
    val data by Store.data.collectAsStateWithLifecycle()
    val unit = data.settings.unit
    var input by remember { mutableStateOf("") }
    val sorted = data.weights.sortedBy { it.at }
    Column(Modifier.fillMaxSize()) {
        BackBar("Weight") { nav.popBackStack() }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Weigh-in reminder: ${weekdayNames[data.settings.weighDay - 1]} at ${TimeUtil.pretty(data.settings.weighTime)} (change in Settings)",
                style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Weight ($unit)", input, Modifier.weight(1f)) { input = it }
                Button(enabled = input.toDoubleOrNull() != null, onClick = {
                    val w = WeightEntry(LocalDate.now().toString(), System.currentTimeMillis(), input.toDouble())
                    Store.update { d -> d.copy(weights = d.weights.filterNot { it.date == w.date } + w) }
                    input = ""
                }) { Text("Log") }
            }
            if (sorted.size >= 2) {
                WeightChart(sorted.takeLast(26).map { it.weight })
                val change = sorted.last().weight - sorted.first().weight
                Text("Change since ${sorted.first().date}: ${"%+.1f".format(change)} $unit")
            }
            sorted.reversed().forEach { w ->
                Row {
                    Text(LocalDate.parse(w.date).format(DateTimeFormatter.ofPattern("EEE MMM d, yyyy")), Modifier.weight(1f))
                    Text("${w.weight} $unit", fontWeight = FontWeight.Medium)
                    TextButton(onClick = { Store.update { d -> d.copy(weights = d.weights - w) } }) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
fun WeightChart(values: List<Double>) {
    val color = MaterialTheme.colorScheme.primary
    val min = values.min()
    val max = values.max()
    val range = (max - min).takeIf { it > 0 } ?: 1.0
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / range * (size.height - 16)).toFloat() - 8
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(color, 5f, Offset(x, y))
        }
        drawPath(path, color, style = Stroke(width = 4f))
    }
    Row { Text("%.1f".format(min), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f)); Text("%.1f".format(max), style = MaterialTheme.typography.labelSmall) }
}

@Composable
fun ProgressScreen(nav: NavHostController) {
    val data by Store.data.collectAsStateWithLifecycle()
    val unit = data.settings.unit
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Progress", style = MaterialTheme.typography.headlineSmall)
        SectionCard("Body weight") {
            val last = data.weights.maxByOrNull { it.at }
            Text(last?.let { "Latest: ${it.weight} $unit on ${it.date}" } ?: "No weigh-ins yet.")
            Button(onClick = { nav.navigate("weight") }) { Text("Weight log & chart") }
        }
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        val recent = data.sessions.filter { it.startedAt > weekAgo }
        SectionCard("Last 7 days") {
            Text("${recent.size} workouts · ${recent.sumOf { s -> s.exercises.sumOf { it.sets.size } }} sets · " +
                TimeUtil.duration(recent.sumOf { (it.endedAt ?: it.startedAt) - it.startedAt }) + " in the gym")
        }
        val exerciseIds = data.sessions.flatMap { s -> s.exercises.filter { it.sets.isNotEmpty() }.map { it.exerciseId } }.distinct()
        if (exerciseIds.isNotEmpty()) SectionCard("Exercise bests (estimated 1RM)") {
            exerciseIds.map { id ->
                val sets = Progression.history(Store.current, id).flatMap { it.sets }
                val best = sets.maxBy { Progression.e1rm(it.weight, it.reps) }
                Triple(id, best, Progression.lastLog(data, id))
            }.sortedBy { ExerciseLibrary.name(it.first, data.customExercises) }.forEach { (id, best, last) ->
                Column {
                    Text(ExerciseLibrary.name(id, data.customExercises), fontWeight = FontWeight.Medium)
                    Text("Best: ${best.reps} × ${Progression.fmtW(best.weight, unit)}" +
                        (if (best.weight > 0) " (≈ ${Progression.fmtW(Math.round(Progression.e1rm(best.weight, best.reps)).toDouble(), unit)} 1RM)" else "") +
                        (last?.let { " · last: " + it.sets.joinToString("/") { s -> "${s.reps}" } + " @ " + Progression.fmtW(it.sets.maxOf { s -> s.weight }, unit) } ?: ""),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        SectionCard("Recent workouts") {
            if (data.sessions.isEmpty()) Text("Nothing logged yet.")
            data.sessions.sortedByDescending { it.startedAt }.take(15).forEach { s ->
                Text("${TimeUtil.dateOf(s.startedAt).format(DateTimeFormatter.ofPattern("EEE MMM d"))} · ${s.dayName}" +
                    (s.partLabel?.let { " ($it)" } ?: "") + (s.endedAt?.let { " · ${TimeUtil.duration(it - s.startedAt)}" } ?: ""))
            }
        }
    }
}
