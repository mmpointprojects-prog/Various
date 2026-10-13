package com.mmpoint.spotter.ui.screens

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.mmpoint.spotter.data.Store
import com.mmpoint.spotter.data.SuppKind
import com.mmpoint.spotter.data.SuppSchedule
import com.mmpoint.spotter.data.Supplement
import com.mmpoint.spotter.logic.Planner
import com.mmpoint.spotter.logic.TimeUtil
import com.mmpoint.spotter.notify.Reminders
import com.mmpoint.spotter.ui.BackBar
import com.mmpoint.spotter.ui.NumberField
import com.mmpoint.spotter.ui.SectionCard
import com.mmpoint.spotter.ui.TimeField
import com.mmpoint.spotter.ui.WeekdayChips
import com.mmpoint.spotter.ui.weekdayNames
import java.time.LocalDate
import java.time.LocalDateTime

fun scheduleText(s: Supplement): String {
    val days = when (s.schedule) {
        SuppSchedule.DAILY -> "Every day"
        SuppSchedule.WEEKDAYS -> s.weekdays.sorted().joinToString { weekdayNames[it - 1] }
        SuppSchedule.EVERY_N_DAYS -> "Every ${s.everyNDays} days"
    }
    val cycle = if (s.cycleOnWeeks > 0 && s.cycleOffWeeks > 0) " · ${s.cycleOnWeeks} wk on / ${s.cycleOffWeeks} wk off" else ""
    return "$days at ${s.times.joinToString { TimeUtil.pretty(it) }}$cycle"
}

@Composable
fun SupplementsScreen(nav: NavHostController) {
    val data by Store.data.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Peptides & supplements", style = MaterialTheme.typography.headlineSmall)
        Text("Add each one with the dose and schedule from your prescriber or protocol. Spotter puts it on your calendar and reminds you; it doesn't recommend doses.",
            style = MaterialTheme.typography.bodySmall)
        Button(onClick = { nav.navigate("supplement/new") }, modifier = Modifier.fillMaxWidth()) { Text("+ Add peptide / supplement") }
        SuppKind.entries.forEach { kind ->
            val list = data.supplements.filter { it.kind == kind }
            if (list.isNotEmpty()) {
                Text(kind.label + "s", style = MaterialTheme.typography.titleMedium)
                list.forEach { s ->
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name + if (!s.active) " (paused)" else "", fontWeight = FontWeight.SemiBold)
                                Text("${s.dose} ${s.unit}".trim())
                                Text(scheduleText(s), style = MaterialTheme.typography.bodySmall)
                                if (Planner.isSuppDue(s, LocalDate.now()).not() && s.active) Text("Not due today", style = MaterialTheme.typography.bodySmall)
                                s.remainingDoses?.let { Text("$it doses left", style = MaterialTheme.typography.bodySmall) }
                                if (s.instructions.isNotBlank()) Text(s.instructions, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { nav.navigate("supplement/${s.id}") }) { Text("Edit") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupplementEditorScreen(nav: NavHostController, id: String) {
    val ctx = LocalContext.current
    val existing = Store.current.supplements.firstOrNull { it.id == id }
    var s by remember {
        mutableStateOf(existing ?: Supplement(id = Store.newId(), name = "", startDate = LocalDate.now().toString()))
    }
    var dose by remember { mutableStateOf(s.dose) }
    var vialMg by remember { mutableStateOf(s.vialMg?.toString() ?: "") }
    var waterMl by remember { mutableStateOf(s.waterMl?.toString() ?: "") }
    var doseMcg by remember { mutableStateOf(s.doseMcg?.toString() ?: "") }
    var remaining by remember { mutableStateOf(s.remainingDoses?.toString() ?: "") }
    var everyN by remember { mutableStateOf(s.everyNDays.toString()) }
    var onW by remember { mutableStateOf(s.cycleOnWeeks.toString()) }
    var offW by remember { mutableStateOf(s.cycleOffWeeks.toString()) }

    Column(Modifier.fillMaxSize()) {
        BackBar(if (existing == null) "Add" else "Edit ${existing.name}") { nav.popBackStack() }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SuppKind.entries.forEach { k -> FilterChip(s.kind == k, { s = s.copy(kind = k, rotateSites = k == SuppKind.PEPTIDE) }, label = { Text(k.label) }) }
            }
            OutlinedTextField(s.name, { s = s.copy(name = it) }, label = { Text("Name (e.g. BPC-157, Creatine, Vitamin D3)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row {
                OutlinedTextField(dose, { dose = it }, label = { Text("Dose") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(s.unit, { s = s.copy(unit = it) }, label = { Text("Unit (mcg, mg, IU, g, caps)") }, singleLine = true, modifier = Modifier.weight(1f))
            }

            Text("When", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SuppSchedule.entries.forEach { sc -> FilterChip(s.schedule == sc, { s = s.copy(schedule = sc) }, label = { Text(sc.label) }) }
            }
            when (s.schedule) {
                SuppSchedule.WEEKDAYS -> WeekdayChips(s.weekdays) { s = s.copy(weekdays = it) }
                SuppSchedule.EVERY_N_DAYS -> NumberField("Every how many days?", everyN, decimal = false) { everyN = it }
                else -> {}
            }
            s.times.forEachIndexed { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeField("Dose ${i + 1}", t, Modifier.weight(1f)) { nt -> s = s.copy(times = s.times.mapIndexed { j, o -> if (j == i) nt else o }) }
                    if (s.times.size > 1) TextButton(onClick = { s = s.copy(times = s.times.filterIndexed { j, _ -> j != i }) }) { Text("Remove") }
                }
            }
            TextButton(onClick = { s = s.copy(times = s.times + "20:00") }) { Text("+ Another time each day") }
            OutlinedTextField(s.startDate, { s = s.copy(startDate = it) }, label = { Text("Start date (YYYY-MM-DD)") }, singleLine = true)
            Text("Cycling (optional) — e.g. 8 weeks on, 4 weeks off", style = MaterialTheme.typography.bodySmall)
            Row {
                NumberField("Weeks on", onW, Modifier.weight(1f), decimal = false) { onW = it }
                Spacer(Modifier.width(8.dp))
                NumberField("Weeks off", offW, Modifier.weight(1f), decimal = false) { offW = it }
            }
            OutlinedTextField(s.instructions, { s = s.copy(instructions = it) },
                label = { Text("Instructions (e.g. fasted, 30 min before food; with dinner)") }, modifier = Modifier.fillMaxWidth())

            if (s.kind == SuppKind.PEPTIDE) SectionCard("Vial & syringe math") {
                Text("Optional. Fill in your vial and reconstitution to see how many units to draw on a U-100 insulin syringe. Always double-check against your prescriber's instructions.",
                    style = MaterialTheme.typography.bodySmall)
                Row {
                    NumberField("Vial (mg)", vialMg, Modifier.weight(1f)) { vialMg = it }
                    Spacer(Modifier.width(8.dp))
                    NumberField("Water (mL)", waterMl, Modifier.weight(1f)) { waterMl = it }
                }
                NumberField("Dose per injection (mcg)", doseMcg, Modifier.fillMaxWidth()) { doseMcg = it }
                val units = Planner.syringeUnits(vialMg.toDoubleOrNull() ?: 0.0, waterMl.toDoubleOrNull() ?: 0.0, doseMcg.toDoubleOrNull() ?: 0.0)
                units?.let {
                    Text("Draw %.1f units (%.3f mL)".format(it, it / 100), fontWeight = FontWeight.Bold)
                    Text("≈ ${Planner.dosesPerVial(vialMg.toDouble(), doseMcg.toDouble())} doses per vial", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rotate injection sites", Modifier.weight(1f))
                    Switch(s.rotateSites, { s = s.copy(rotateSites = it) })
                }
            }
            NumberField("Doses left (optional, for refill alerts)", remaining, Modifier.fillMaxWidth(), decimal = false) { remaining = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active", Modifier.weight(1f))
                Switch(s.active, { s = s.copy(active = it) })
            }

            Button(
                enabled = s.name.isNotBlank() && runCatching { LocalDate.parse(s.startDate) }.isSuccess &&
                    (s.schedule != SuppSchedule.WEEKDAYS || s.weekdays.isNotEmpty()),
                onClick = {
                    val saved = s.copy(
                        dose = dose.trim(),
                        everyNDays = everyN.toIntOrNull()?.coerceAtLeast(1) ?: 2,
                        cycleOnWeeks = onW.toIntOrNull() ?: 0, cycleOffWeeks = offW.toIntOrNull() ?: 0,
                        vialMg = vialMg.toDoubleOrNull(), waterMl = waterMl.toDoubleOrNull(), doseMcg = doseMcg.toDoubleOrNull(),
                        remainingDoses = remaining.toIntOrNull(),
                    )
                    Store.update { d -> d.copy(supplements = d.supplements.filterNot { it.id == saved.id } + saved) }
                    Reminders.rescheduleAll(ctx)
                    nav.popBackStack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            if (existing != null) TextButton(onClick = {
                Store.update { d -> d.copy(supplements = d.supplements.filterNot { it.id == existing.id }) }
                Reminders.rescheduleAll(ctx)
                nav.popBackStack()
            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val data by Store.data.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val s = data.settings
    fun set(f: (com.mmpoint.spotter.data.Settings) -> com.mmpoint.spotter.data.Settings) {
        Store.update { it.copy(settings = f(it.settings)) }
        Reminders.rescheduleAll(ctx)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(Store.exportJson().toByteArray()) } }
            .onSuccess { Toast.makeText(ctx, "Backup saved", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(ctx, "Backup failed: ${it.message}", Toast.LENGTH_LONG).show() }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.openInputStream(uri)?.use { Store.importJson(it.readBytes().decodeToString()) } }
            .onSuccess { Reminders.rescheduleAll(ctx); Toast.makeText(ctx, "Backup restored", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(ctx, "Restore failed: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        SectionCard("Program") {
            Text(data.program.name + (data.program.goalSummary?.let { "\n$it" } ?: ""))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { nav.navigate("program") }) { Text("Edit splits") }
                OutlinedButton(onClick = { nav.navigate("generator") }) { Text("New plan from goals") }
            }
        }
        ReminderSettings(data)
        SectionCard("Turn reminders on/off") {
            Toggle("Morning check-in", s.morningEnabled) { v -> set { it.copy(morningEnabled = v) } }
            Toggle("Evening check-in", s.eveningEnabled) { v -> set { it.copy(eveningEnabled = v) } }
            Toggle("Weekly weigh-in", s.weighEnabled) { v -> set { it.copy(weighEnabled = v) } }
            Toggle("Rest-timer alarm (works with screen off)", s.restAlarm) { v -> set { it.copy(restAlarm = v) } }
            Text("\"Leave for the gym\" reminder (minutes before)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 10, 15, 20, 30).forEach { m -> FilterChip(s.gymLeadMin == m, { set { it.copy(gymLeadMin = m) } }, label = { Text(if (m == 0) "Off" else "$m") }) }
            }
            Text("Default rest for new exercises (seconds)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(45, 60, 90, 120, 180).forEach { r -> FilterChip(s.defaultRestSec == r, { set { it.copy(defaultRestSec = r) } }, label = { Text("$r") }) }
            }
        }
        SectionCard("Make sure reminders arrive") {
            val am = ctx.getSystemService(AlarmManager::class.java)
            val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            Text("Notifications: " + if (Reminders.canNotify(ctx)) "allowed" else "BLOCKED — allow them in app settings")
            Text("Exact alarms: " + if (exactOk) "allowed" else "not allowed — reminders may be late")
            Text("Some phones (Samsung, Xiaomi, OnePlus…) kill background apps. Set battery usage for Spotter to \"Unrestricted\".",
                style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                }) { Text("App settings") }
                OutlinedButton(onClick = {
                    Reminders.notify(ctx, 4242, Reminders.CH_CHECKIN, "Test notification", "Reminders are working.", "today")
                }) { Text("Send test") }
                OutlinedButton(onClick = {
                    Reminders.schedule(ctx, "test", LocalDateTime.now().plusMinutes(1))
                    Toast.makeText(ctx, "Test alarm will fire in ~1 minute — lock your phone to check", Toast.LENGTH_SHORT).show()
                }) { Text("Test alarm in 1 min") }
            }
        }
        SectionCard("Backup") {
            Text("Your data only lives on this phone. Export a backup now and then (e.g. to Google Drive).", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("spotter-backup-${LocalDate.now()}.json") }) { Text("Export") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) { Text("Restore") }
            }
        }
        Text("Spotter v0.1 · General fitness info only, not medical advice.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange)
    }
}
