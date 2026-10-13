package com.mmpoint.spotter.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.mmpoint.spotter.data.AppData
import com.mmpoint.spotter.data.Equipment
import com.mmpoint.spotter.data.Exercise
import com.mmpoint.spotter.data.ExerciseLibrary
import com.mmpoint.spotter.data.Muscle
import com.mmpoint.spotter.data.PlannedExercise
import com.mmpoint.spotter.data.Program
import com.mmpoint.spotter.data.ScheduleMode
import com.mmpoint.spotter.data.Store
import com.mmpoint.spotter.data.WorkoutDay
import com.mmpoint.spotter.logic.GoalInput
import com.mmpoint.spotter.logic.Goal
import com.mmpoint.spotter.logic.GymAccess
import com.mmpoint.spotter.logic.PlanGenerator
import com.mmpoint.spotter.logic.Planner
import com.mmpoint.spotter.logic.Progression
import com.mmpoint.spotter.notify.Reminders
import com.mmpoint.spotter.ui.BackBar
import com.mmpoint.spotter.ui.ExercisePicker
import com.mmpoint.spotter.ui.NumberField
import com.mmpoint.spotter.ui.SectionCard
import com.mmpoint.spotter.ui.TimeField
import com.mmpoint.spotter.ui.WeekdayChips
import com.mmpoint.spotter.ui.weekdayNames
import androidx.compose.ui.platform.LocalContext

@Composable
fun OnboardingScreen(nav: NavHostController) {
    val data by Store.data.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Welcome to Spotter", style = MaterialTheme.typography.headlineMedium)
        Text("Let's set up your training, your daily check-ins and your reminders. You can change any of this later in Settings.")

        SectionCard("1 · Your program") {
            if (data.program.days.isEmpty()) {
                Text("Pick one:")
            } else {
                Text("${data.program.name}: " + data.program.days.joinToString(" · ") { it.name })
            }
            Button(onClick = { nav.navigate("program") }, modifier = Modifier.fillMaxWidth()) {
                Text(if (data.program.days.isEmpty()) "Build my own splits" else "Edit my splits")
            }
            OutlinedButton(onClick = { nav.navigate("generator") }, modifier = Modifier.fillMaxWidth()) {
                Text("Give me a plan from my goals & time")
            }
        }

        ReminderSettings(data, "2 · Check-ins & reminders")

        SectionCard("3 · Peptides & supplements") {
            Text("Add what you take and when — I'll put it on the calendar and remind you. You can also do this later from the Supps tab.")
            OutlinedButton(onClick = { nav.navigate("supplement/new") }) { Text("Add one now") }
            if (data.supplements.isNotEmpty()) Text(data.supplements.joinToString { it.name })
        }

        Button(
            onClick = {
                Store.update { it.copy(settings = it.settings.copy(onboarded = true)) }
                Reminders.rescheduleAll(ctx)
                nav.navigate("today") { popUpTo("onboarding") { inclusive = true } }
            },
            enabled = data.program.days.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Finish setup") }
        if (data.program.days.isEmpty()) Text("Set up a program first.", style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderSettings(data: AppData, title: String = "Check-ins & reminders") {
    val s = data.settings
    val ctx = LocalContext.current
    fun set(f: (com.mmpoint.spotter.data.Settings) -> com.mmpoint.spotter.data.Settings) {
        Store.update { it.copy(settings = f(it.settings)) }
        Reminders.rescheduleAll(ctx)
    }
    SectionCard(title) {
        Text("Units")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(s.unit == "lb", { set { it.copy(unit = "lb") } }, label = { Text("lb") })
            FilterChip(s.unit == "kg", { set { it.copy(unit = "kg") } }, label = { Text("kg") })
        }
        Text("Morning check-in asks how you feel, whether you're training, and when you'll be at the gym.", style = MaterialTheme.typography.bodySmall)
        TimeField("Morning check-in", s.morningTime) { t -> set { it.copy(morningTime = t) } }
        TimeField("Evening check-in", s.eveningTime) { t -> set { it.copy(eveningTime = t) } }
        Text("Weekly weigh-in day")
        WeekdayChips(setOf(s.weighDay), single = true) { d -> set { it.copy(weighDay = d.first()) } }
        TimeField("Weigh-in time", s.weighTime) { t -> set { it.copy(weighTime = t) } }
        Text("Pre-gym snack reminder (minutes before gym)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(30, 45, 60, 75, 90, 120).forEach { m ->
                FilterChip(s.snackLeadMin == m, { set { it.copy(snackLeadMin = m) } }, label = { Text("$m") })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeneratorScreen(nav: NavHostController) {
    val data by Store.data.collectAsStateWithLifecycle()
    var goal by remember { mutableStateOf(Goal.MUSCLE) }
    var days by remember { mutableStateOf(4) }
    var minutes by remember { mutableStateOf(60) }
    var access by remember { mutableStateOf(GymAccess.FULL_GYM) }
    val preview = remember(goal, days, minutes, access) { PlanGenerator.generate(GoalInput(goal, days, minutes, access)) }

    Column(Modifier.fillMaxSize()) {
        BackBar("Plan from goals") { nav.popBackStack() }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Main goal", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Goal.entries.forEach { g -> FilterChip(goal == g, { goal = g }, label = { Text(g.label) }) }
            }
            Text("Days per week", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (2..6).forEach { n -> FilterChip(days == n, { days = n }, label = { Text("$n") }) }
            }
            Text("Time in the gym", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30, 45, 60, 75, 90).forEach { m -> FilterChip(minutes == m, { minutes = m }, label = { Text("$m min") }) }
            }
            Text("Equipment", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GymAccess.entries.forEach { a -> FilterChip(access == a, { access = a }, label = { Text(a.label) }) }
            }
            Text("Preview", style = MaterialTheme.typography.titleMedium)
            preview.days.forEach { d ->
                SectionCard("${d.name} · ${d.weekdays.sorted().joinToString { weekdayNames[it - 1] }}") {
                    d.exercises.forEach { p ->
                        Text("${ExerciseLibrary.name(p.exerciseId, data.customExercises)} — ${p.sets} × ${p.repMin}–${p.repMax}, rest ${p.restSec}s")
                    }
                    Text("≈ ${Progression.estimatedMinutes(d.exercises)} min", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(
                onClick = {
                    Store.update { it.copy(program = preview, rotationIndex = 0) }
                    nav.popBackStack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Use this plan") }
            Text("You can edit exercises, days and sets after.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgramEditorScreen(nav: NavHostController) {
    val data by Store.data.collectAsStateWithLifecycle()
    val program = data.program
    var pickFor by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var customFor by remember { mutableStateOf<String?>(null) }

    fun updateProgram(f: (Program) -> Program) = Store.update { it.copy(program = f(it.program)) }
    fun updateDay(id: String, f: (WorkoutDay) -> WorkoutDay) =
        updateProgram { p -> p.copy(days = p.days.map { if (it.id == id) f(it) else it }) }

    Column(Modifier.fillMaxSize()) {
        BackBar("My splits") { nav.popBackStack() }
        LazyColumn(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(program.name, { v -> updateProgram { it.copy(name = v) } }, label = { Text("Program name") },
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.padding(4.dp))
                Text("How should I pick today's workout?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(program.mode == ScheduleMode.WEEKDAYS, { updateProgram { it.copy(mode = ScheduleMode.WEEKDAYS) } },
                        label = { Text("By weekday") })
                    FilterChip(program.mode == ScheduleMode.ROTATION, { updateProgram { it.copy(mode = ScheduleMode.ROTATION) } },
                        label = { Text("Next in rotation") })
                }
                Text(
                    if (program.mode == ScheduleMode.WEEKDAYS) "Each split is tied to days of the week."
                    else "Whatever day you go, you get the next split in order.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            itemsIndexed(program.days, key = { _, d -> d.id }) { _, day ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(day.name, { v -> updateDay(day.id) { it.copy(name = v) } }, label = { Text("Split name") },
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = { updateProgram { p -> p.copy(days = p.days.filterNot { it.id == day.id }) } }) {
                            Icon(Icons.Default.Delete, "Delete split")
                        }
                    }
                    if (program.mode == ScheduleMode.WEEKDAYS) WeekdayChips(day.weekdays) { w -> updateDay(day.id) { it.copy(weekdays = w) } }
                    val muscles = Planner.musclesOf(data, day.exercises)
                    if (muscles.isNotEmpty()) Text(muscles.joinToString(" · ") { it.label } + " · ≈ ${Progression.estimatedMinutes(day.exercises)} min",
                        style = MaterialTheme.typography.bodySmall)
                    day.exercises.forEachIndexed { i, p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ExerciseLibrary.name(p.exerciseId, data.customExercises))
                                Text("${p.sets} × ${p.repMin}–${p.repMax} · rest ${p.restSec}s", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = {
                                if (i > 0) updateDay(day.id) { d -> d.copy(exercises = d.exercises.toMutableList().apply { add(i - 1, removeAt(i)) }) }
                            }) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                            IconButton(onClick = {
                                if (i < day.exercises.size - 1) updateDay(day.id) { d -> d.copy(exercises = d.exercises.toMutableList().apply { add(i + 1, removeAt(i)) }) }
                            }) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                            IconButton(onClick = { editing = day.id to i }) { Icon(Icons.Default.Edit, "Edit") }
                            IconButton(onClick = { updateDay(day.id) { d -> d.copy(exercises = d.exercises.filterIndexed { j, _ -> j != i }) } }) {
                                Icon(Icons.Default.Delete, "Remove")
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickFor = day.id }) { Text("+ Exercise") }
                        TextButton(onClick = { customFor = day.id }) { Text("+ Custom exercise") }
                    }
                }
            }
            item {
                Button(onClick = {
                    updateProgram { p -> p.copy(days = p.days + WorkoutDay(id = Store.newId(), name = "Split ${p.days.size + 1}")) }
                }, modifier = Modifier.fillMaxWidth()) { Text("+ Add split") }
                Spacer(Modifier.padding(8.dp))
            }
        }
    }

    pickFor?.let { dayId ->
        ExercisePicker("Add exercise", ExerciseLibrary.all(data.customExercises), onDismiss = { pickFor = null }) { e ->
            updateDay(dayId) { it.copy(exercises = it.exercises + PlannedExercise(e.id, restSec = data.settings.defaultRestSec)) }
            pickFor = null
        }
    }

    editing?.let { (dayId, idx) ->
        val p = program.days.firstOrNull { it.id == dayId }?.exercises?.getOrNull(idx)
        if (p != null) PlannedEditDialog(p, onDismiss = { editing = null }) { np ->
            updateDay(dayId) { d -> d.copy(exercises = d.exercises.mapIndexed { j, e -> if (j == idx) np else e }) }
            editing = null
        }
    }

    customFor?.let { dayId ->
        CustomExerciseDialog(onDismiss = { customFor = null }) { ex ->
            Store.update { it.copy(customExercises = it.customExercises + ex) }
            updateDay(dayId) { it.copy(exercises = it.exercises + PlannedExercise(ex.id, restSec = data.settings.defaultRestSec)) }
            customFor = null
        }
    }
}

@Composable
fun PlannedEditDialog(p: PlannedExercise, onDismiss: () -> Unit, onSave: (PlannedExercise) -> Unit) {
    var sets by remember { mutableStateOf(p.sets.toString()) }
    var lo by remember { mutableStateOf(p.repMin.toString()) }
    var hi by remember { mutableStateOf(p.repMax.toString()) }
    var rest by remember { mutableStateOf(p.restSec.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val min = lo.toIntOrNull() ?: p.repMin
                onSave(p.copy(
                    sets = sets.toIntOrNull()?.coerceIn(1, 10) ?: p.sets,
                    repMin = min,
                    repMax = (hi.toIntOrNull() ?: p.repMax).coerceAtLeast(min),
                    restSec = rest.toIntOrNull()?.coerceIn(0, 600) ?: p.restSec,
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Sets, reps & rest") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Sets", sets, decimal = false) { sets = it }
                Row {
                    NumberField("Reps min", lo, Modifier.weight(1f), decimal = false) { lo = it }
                    Spacer(Modifier.width(8.dp))
                    NumberField("Reps max", hi, Modifier.weight(1f), decimal = false) { hi = it }
                }
                NumberField("Rest (seconds)", rest, decimal = false) { rest = it }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomExerciseDialog(onDismiss: () -> Unit, onSave: (Exercise) -> Unit) {
    var name by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf(Muscle.CHEST) }
    var eq by remember { mutableStateOf(Equipment.MACHINE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(Exercise(
                    id = "custom_" + Store.newId(), name = name.trim(), muscle = muscle,
                    pattern = "custom_${muscle.name.lowercase()}", equipment = eq,
                    lowerBody = muscle in setOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES), custom = true,
                ))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Custom exercise") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Text("Main muscle")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Muscle.entries.forEach { m -> FilterChip(muscle == m, { muscle = m }, label = { Text(m.label) }) }
                }
                Text("Equipment")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Equipment.entries.forEach { e -> FilterChip(eq == e, { eq = e }, label = { Text(e.label) }) }
                }
            }
        },
    )
}
