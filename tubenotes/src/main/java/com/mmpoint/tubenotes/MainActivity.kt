package com.mmpoint.tubenotes

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import com.mmpoint.tubenotes.data.OutputKind
import com.mmpoint.tubenotes.data.Store
import com.mmpoint.tubenotes.data.Work
import com.mmpoint.tubenotes.net.YouTube
import com.mmpoint.tubenotes.ui.EntryScreen
import com.mmpoint.tubenotes.ui.HomeScreen
import com.mmpoint.tubenotes.ui.SettingsScreen

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Entry(val id: String, val kind: OutputKind? = null) : Screen
}

class MainActivity : ComponentActivity() {
    private val screen = mutableStateOf<Screen>(Screen.Home)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            AppTheme {
                val current by screen
                BackHandler(enabled = current != Screen.Home) { screen.value = Screen.Home }
                when (val s = current) {
                    Screen.Home -> HomeScreen(
                        onOpenLink = { openLink(it) },
                        onOpenEntry = { screen.value = Screen.Entry(it) },
                        onSettings = { screen.value = Screen.Settings },
                    )
                    Screen.Settings -> SettingsScreen(onBack = { screen.value = Screen.Home })
                    is Screen.Entry -> EntryScreen(
                        entryId = s.id,
                        initialKind = s.kind,
                        onBack = { screen.value = Screen.Home },
                        onSettings = { screen.value = Screen.Settings },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = listOfNotNull(intent.getStringExtra(Intent.EXTRA_TEXT), intent.getStringExtra(Intent.EXTRA_SUBJECT))
            .joinToString(" ")
        if (!openLink(text)) Toast.makeText(this, "That share didn't contain a YouTube link.", Toast.LENGTH_LONG).show()
    }

    /** Opens (or adds) the log entry for a link. Returns false if no YouTube video was found in [text]. */
    fun openLink(text: String): Boolean {
        val id = YouTube.extractVideoId(text) ?: return false
        val entry = Store.entryFor(id, YouTube.watchUrl(id))
        Work.fillInfo(entry.id)
        screen.value = Screen.Entry(entry.id)
        return true
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
