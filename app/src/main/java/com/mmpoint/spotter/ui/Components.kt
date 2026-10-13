package com.mmpoint.spotter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mmpoint.spotter.data.Exercise
import com.mmpoint.spotter.data.Muscle
import com.mmpoint.spotter.logic.TimeUtil
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, onBack: (() -> Unit)?) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    )
}

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/** 1–5 scale as chips, e.g. energy or soreness. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RatingRow(label: String, value: Int, low: String, high: String, onChange: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { n -> FilterChip(selected = value == n, onClick = { onChange(n) }, label = { Text("$n") }) }
        }
        Text("1 = $low · 5 = $high", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, hhmm: String?, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Text("$label: " + (hhmm?.let { TimeUtil.pretty(it) } ?: "Set time"))
    }
    if (open) {
        val init = TimeUtil.parse(hhmm) ?: LocalTime.of(17, 0)
        val state = rememberTimePickerState(init.hour, init.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { onChange(TimeUtil.fmt(LocalTime.of(state.hour, state.minute))); open = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
fun NumberField(label: String, value: String, modifier: Modifier = Modifier, decimal: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.isEmpty() || v.matches(Regex(if (decimal) "\\d*\\.?\\d*" else "\\d*"))) onChange(v) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

val weekdayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekdayChips(selected: Set<Int>, single: Boolean = false, onChange: (Set<Int>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        weekdayNames.forEachIndexed { i, n ->
            val d = i + 1
            FilterChip(
                selected = d in selected,
                onClick = { onChange(if (single) setOf(d) else if (d in selected) selected - d else selected + d) },
                label = { Text(n) },
            )
        }
    }
}

/** Searchable exercise list, filterable by muscle. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExercisePicker(
    title: String,
    exercises: List<Exercise>,
    initialMuscle: Muscle? = null,
    subtitle: (Exercise) -> String? = { null },
    onDismiss: () -> Unit,
    onPick: (Exercise) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf(initialMuscle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(muscle == null, { muscle = null }, label = { Text("All") })
                    Muscle.entries.filter { m -> exercises.any { it.muscle == m } }.forEach { m ->
                        FilterChip(muscle == m, { muscle = m }, label = { Text(m.label) })
                    }
                }
                val shown = exercises.filter { (muscle == null || it.muscle == muscle) && it.name.contains(query, ignoreCase = true) }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.id }) { e ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(e) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(e.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                subtitle(e) ?: "${e.muscle.label} · ${e.equipment.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
    )
}

@Composable
fun Labeled(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
