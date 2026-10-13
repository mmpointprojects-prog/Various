package com.mmpoint.spotter

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mmpoint.spotter.data.CheckInKind
import com.mmpoint.spotter.data.Store
import com.mmpoint.spotter.notify.Reminders
import com.mmpoint.spotter.ui.screens.CalendarScreen
import com.mmpoint.spotter.ui.screens.CheckInScreen
import com.mmpoint.spotter.ui.screens.GeneratorScreen
import com.mmpoint.spotter.ui.screens.OnboardingScreen
import com.mmpoint.spotter.ui.screens.ProgramEditorScreen
import com.mmpoint.spotter.ui.screens.ProgressScreen
import com.mmpoint.spotter.ui.screens.ReadinessScreen
import com.mmpoint.spotter.ui.screens.SettingsScreen
import com.mmpoint.spotter.ui.screens.SupplementEditorScreen
import com.mmpoint.spotter.ui.screens.SupplementsScreen
import com.mmpoint.spotter.ui.screens.TodayScreen
import com.mmpoint.spotter.ui.screens.WeightScreen
import com.mmpoint.spotter.ui.screens.WorkoutScreen

class SpotterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Reminders.createChannels(this)
        Reminders.rescheduleAll(this)
    }
}

class MainActivity : ComponentActivity() {
    private val pendingRoute = mutableStateOf<String?>(null)

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        Reminders.rescheduleAll(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute.value = intent.getStringExtra(Reminders.EXTRA_ROUTE)
        if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(this)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { SpotterTheme { SpotterNav(pendingRoute.value) { pendingRoute.value = null } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingRoute.value = intent.getStringExtra(Reminders.EXTRA_ROUTE)
    }
}

/** Stock Material 3 theme: dynamic (wallpaper) colors on Android 12+, defaults below that. */
@Composable
fun SpotterTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "Today", Icons.Default.Home),
    Tab("calendar", "Calendar", Icons.Default.DateRange),
    Tab("supplements", "Supps", Icons.Default.Favorite),
    Tab("progress", "Progress", Icons.AutoMirrored.Filled.List),
    Tab("settings", "Settings", Icons.Default.Settings),
)

@Composable
fun SpotterNav(route: String?, consumeRoute: () -> Unit) {
    val nav = rememberNavController()
    val data by Store.data.collectAsStateWithLifecycle()
    val start = remember { if (Store.current.settings.onboarded) "today" else "onboarding" }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    LaunchedEffect(route, data.settings.onboarded) {
        if (route != null && data.settings.onboarded) {
            nav.navigate(route) { launchSingleTop = true }
            consumeRoute()
        }
    }

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == current }) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = { nav.goTab(t.route) },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(pad)) {
            composable("onboarding") { OnboardingScreen(nav) }
            composable("today") { TodayScreen(nav) }
            composable("calendar") { CalendarScreen() }
            composable("supplements") { SupplementsScreen(nav) }
            composable("progress") { ProgressScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable("generator") { GeneratorScreen(nav) }
            composable("program") { ProgramEditorScreen(nav) }
            composable("workout") { WorkoutScreen(nav) }
            composable("weight") { WeightScreen(nav) }
            composable("readiness/{source}/{id}") { e ->
                ReadinessScreen(nav, e.arguments?.getString("source") ?: "day", e.arguments?.getString("id") ?: "")
            }
            composable("checkin/{kind}") { e ->
                val kind = runCatching { CheckInKind.valueOf(e.arguments?.getString("kind") ?: "") }.getOrDefault(CheckInKind.MORNING)
                CheckInScreen(nav, kind)
            }
            composable("supplement/{id}") { e -> SupplementEditorScreen(nav, e.arguments?.getString("id") ?: "new") }
        }
    }
}

/** Tabs sit directly on top of Today; going to a tab clears whatever screen was above it. */
fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo("today")
    launchSingleTop = true
}
