package com.mmpoint.tubenotes.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmpoint.tubenotes.data.LogEntry
import com.mmpoint.tubenotes.data.OutputKind
import com.mmpoint.tubenotes.data.Store
import com.mmpoint.tubenotes.data.Transcripts
import com.mmpoint.tubenotes.data.Work
import com.mmpoint.tubenotes.net.Claude
import java.text.DateFormat
import java.util.Date

// ---------------------------------------------------------------- Home: paste box + log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenLink: (String) -> Boolean, onOpenEntry: (String) -> Unit, onSettings: () -> Unit) {
    val data by Store.data.collectAsStateWithLifecycle()
    var link by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var linkError by remember { mutableStateOf(false) }
    val go = {
        if (onOpenLink(link)) link = "" else linkError = true
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TubeNotes") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it; linkError = false },
                    label = { Text("YouTube link") },
                    placeholder = { Text("https://youtu.be/…") },
                    singleLine = true,
                    isError = linkError,
                    supportingText = {
                        Text(if (linkError) "That doesn't look like a YouTube link." else "Or in YouTube tap Share → TubeNotes.")
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = go, enabled = link.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Open video") }
            }
            if (data.apiKey.isBlank()) {
                item {
                    Card(Modifier.fillMaxWidth().clickable(onClick = onSettings)) {
                        Text(
                            "Transcripts work right away. To get summaries and bullet points, add your Claude API key in Settings.",
                            Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Log (${data.entries.size})", style = MaterialTheme.typography.titleMedium)
                if (data.entries.size > 5) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search log") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            val shown = data.entries.filter { e ->
                query.isBlank() || listOf(e.title, e.channel, e.summary.orEmpty(), e.bullets.orEmpty())
                    .any { it.contains(query.trim(), ignoreCase = true) }
            }
            if (data.entries.isEmpty()) {
                item { Text("Nothing yet. Share or paste a YouTube link to start.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(shown, key = { it.id }) { e -> LogRow(e) { onOpenEntry(e.id) } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LogRow(e: LogEntry, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Text(e.displayTitle, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOf(e.channel, formatDate(e.updatedAt)).filter { it.isNotBlank() }.joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val saved = OutputKind.entries.filter { e.output(it) != null }.joinToString(" · ") { it.label }
            if (saved.isNotEmpty()) Text("Saved: $saved", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------------------------------------------------------- One video: pick an output

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(entryId: String, initialKind: OutputKind?, onBack: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val data by Store.data.collectAsStateWithLifecycle()
    val busy by Work.busy.collectAsStateWithLifecycle()
    val errors by Work.errors.collectAsStateWithLifecycle()
    val entry = data.entries.firstOrNull { it.id == entryId }
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    var timestamps by rememberSaveable { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (entry == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val working = busy[entryId]
    val error = errors[entryId]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete from log") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(entry.displayTitle, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.channel.isNotBlank()) {
                    Text(entry.channel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                } else Spacer(Modifier.weight(1f))
                TextButton(onClick = { openUrl(context, entry.url) }) { Text("Open in YouTube") }
            }

            Text("What would you like?", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                OutputKind.entries.forEachIndexed { i, k ->
                    SegmentedButton(
                        selected = kind == k,
                        onClick = { kind = k; Work.request(entryId, k) },
                        shape = SegmentedButtonDefaults.itemShape(i, OutputKind.entries.size),
                        // No check mark, so all three labels fit on narrow phones.
                        icon = {},
                        label = { Text(shortLabel(k), maxLines = 1) },
                    )
                }
            }
            HorizontalDivider()

            val k = kind
            when {
                pasting -> PastePanel(onCancel = { pasting = false }) { text ->
                    if (Work.usePastedTranscript(entryId, text)) {
                        pasting = false
                        k?.let { Work.request(entryId, it) }
                        true
                    } else false
                }
                k == null -> Text(
                    "Pick Full transcript, Brief summary or Bullet points. Results are saved to your log.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                working == k || (working != null && entry.output(k) == null) -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(if (!entry.hasTranscript) "Getting the transcript from YouTube…" else "Claude is writing your ${k.label.lowercase()}…")
                }
                error != null && entry.output(k) == null -> ErrorBox(
                    message = error,
                    onRetry = { Work.request(entryId, k) },
                    onPaste = { pasting = true },
                    onSettings = if (error.contains("API key")) onSettings else null,
                )
                else -> {
                    val text = when (k) {
                        OutputKind.TRANSCRIPT ->
                            if (timestamps) Transcripts.timestamped(entry.segments) else entry.output(k)
                        else -> entry.output(k)
                    }
                    if (text == null) {
                        // Opened from the log before this kind was made: make it now.
                        LaunchedEffect(k) { Work.request(entryId, k) }
                    } else {
                        // A failed "Redo" keeps the saved result; say why it didn't change.
                        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        OutputView(context, entry, k, text, timestamps, onTimestamps = { timestamps = it }, onPaste = { pasting = true })
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete from log?") },
        text = { Text("This removes the saved transcript, summary and bullet points for this video.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; Store.delete(entryId); onBack() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun OutputView(
    context: Context,
    entry: LogEntry,
    kind: OutputKind,
    text: String,
    timestamps: Boolean,
    onTimestamps: (Boolean) -> Unit,
    onPaste: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { copy(context, text) }) { Text("Copy") }
        OutlinedButton(onClick = { share(context, "${entry.displayTitle} — ${kind.label}", "${entry.displayTitle}\n${entry.url}\n\n$text") }) {
            Text("Share")
        }
        if (kind != OutputKind.TRANSCRIPT) {
            TextButton(onClick = { Work.request(entry.id, kind, force = true) }) { Text("Redo") }
        }
    }
    if (kind == OutputKind.TRANSCRIPT) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = timestamps, onCheckedChange = onTimestamps)
            Text("  Timestamps", Modifier.weight(1f))
            TextButton(onClick = onPaste) { Text("Paste my own") }
        }
        val source = if (entry.pastedTranscript) "Pasted by you" else "YouTube captions${entry.language.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}"
        Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text("Written by Claude from the transcript.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SelectionContainer { Text(text, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun ErrorBox(message: String, onRetry: () -> Unit, onPaste: () -> Unit, onSettings: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) { Text("Try again") }
                OutlinedButton(onClick = onPaste) { Text("Paste transcript") }
                onSettings?.let { TextButton(onClick = it) { Text("Settings") } }
            }
            Text(
                "To paste: in YouTube open the video, tap the description → Show transcript, select all the text, copy, then tap Paste transcript.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PastePanel(onCancel: () -> Unit, onSave: (String) -> Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    Text("Paste transcript", style = MaterialTheme.typography.titleSmall)
    Text(
        "Paste the text from YouTube's Show transcript panel (timestamps are fine). Saved summary and bullet points " +
            "for this video are replaced using the new text.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; bad = false },
        label = { Text("Transcript text") },
        isError = bad,
        supportingText = { if (bad) Text("Paste some transcript text first.") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { bad = !onSave(text) }) { Text("Use this") }
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

// ---------------------------------------------------------------- Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val data by Store.data.collectAsStateWithLifecycle()
    var key by rememberSaveable { mutableStateOf(data.apiKey) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Claude API key", style = MaterialTheme.typography.titleMedium)
            Text(
                "Summaries and bullet points are written by Claude (${Claude.MODEL}) using your own key from " +
                    "console.anthropic.com → API keys. It's stored only on this phone and is billed to your " +
                    "Anthropic account (usually a few cents per video). Full transcripts don't need a key.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("API key (sk-ant-…)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    Store.update { it.copy(apiKey = key.trim()) }
                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                }) { Text("Save key") }
                if (data.apiKey.isNotBlank()) {
                    OutlinedButton(onClick = { key = ""; Store.update { it.copy(apiKey = "") } }) { Text("Remove key") }
                }
            }
            HorizontalDivider()
            Text("Your log", style = MaterialTheme.typography.titleMedium)
            Text(
                "${data.entries.size} videos saved on this phone. Export sends a copy (without your API key) to Drive, email or any app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { share(context, "TubeNotes log", exportText(data.entries)) }) { Text("Export as text") }
                OutlinedButton(onClick = { share(context, "TubeNotes log (JSON)", Store.exportJson()) }) { Text("Export as JSON") }
            }
        }
    }
}

// ---------------------------------------------------------------- Helpers

private fun shortLabel(k: OutputKind) = when (k) {
    OutputKind.TRANSCRIPT -> "Transcript"
    OutputKind.SUMMARY -> "Summary"
    OutputKind.BULLETS -> "Bullets"
}

fun formatDate(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

fun exportText(entries: List<LogEntry>): String = entries.joinToString("\n\n========================================\n\n") { e ->
    buildString {
        append(e.displayTitle).append('\n')
        if (e.channel.isNotBlank()) append(e.channel).append('\n')
        append(e.url).append('\n').append("Added ").append(formatDate(e.createdAt)).append('\n')
        e.summary?.let { append("\nBRIEF SUMMARY\n").append(it).append('\n') }
        e.bullets?.let { append("\nBULLET POINTS\n").append(it).append('\n') }
        if (e.hasTranscript) append("\nFULL TRANSCRIPT\n").append(Transcripts.plain(e.segments)).append('\n')
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("TubeNotes", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

private fun share(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share"))
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
