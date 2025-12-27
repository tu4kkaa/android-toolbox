// MainActivity.kt
package com.example.toolbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.toolbox.screens.FlashlightScreen
import com.example.toolbox.screens.LevelScreen
import com.example.toolbox.screens.SoundMeterScreen
import com.example.toolbox.screens.OtherScreen
import com.example.toolbox.ui.theme.ToolboxTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import java.util.concurrent.atomic.AtomicLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.LocalLifecycleOwner


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ToolboxTheme {
                ToolboxApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxApp() {
    var current by rememberSaveable { mutableStateOf(AppDestinations.LEVEL) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // telemetry manager
    val telemetryManager = remember { TelemetryManager(context) }

    // Request permissions at app start if not granted
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = RequestMultiplePermissions(),
        onResult = { /* no-op */ }
    )

    // dialog state for rating (may be triggered automatically or from UI)
    var showRatingDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // permissions (unchanged)
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (needed.isNotEmpty()) {
            permissionsLauncher.launch(needed.toTypedArray())
        }

        // increment launch count and maybe show rating on every 5th launch
        val launches = telemetryManager.incrementLaunchCount() // suspend
        if (launches % 5 == 0) {
            showRatingDialog = true
        }
    }

    // ---- Session & tab tracking: separate timers ----
    // appSessionStart: start of app session (resume -> pause)
    val appSessionStart = remember { AtomicLong(System.currentTimeMillis()) }
    // tabStart: start of current tab visit (enter tab -> leave tab)
    val tabStart = remember { AtomicLong(System.currentTimeMillis()) }
    val lastTab = remember { mutableStateOf(current) }

    // Minimum duration (ms) to record a tab visit — filters noisy very-short events (initial composition)
    val MIN_TAB_RECORD_MS = 200L

    // handle tab switching: save previous tab duration only (do NOT call addSession here)
    LaunchedEffect(current) {
        val now = System.currentTimeMillis()
        val prevTabStart = tabStart.getAndSet(now)
        val duration = (now - prevTabStart).coerceAtLeast(0L)
        // record tab time only if above threshold
        if (duration >= MIN_TAB_RECORD_MS) {
            telemetryManager.addTabTime(lastTab.value, duration)
        }
        lastTab.value = current
    }

    // lifecycle observer: save app session on pause/stop and also save current tab time
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            val now = System.currentTimeMillis()
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    // 1) app session duration (resume -> pause)
                    val appStart = appSessionStart.getAndSet(now)
                    val appDuration = (now - appStart).coerceAtLeast(0L)
                    if (appDuration > 0L) {
                        telemetryManager.addSession(appDuration)
                    }

                    // 2) current tab duration (record and reset tabStart)
                    val prevTabStart = tabStart.getAndSet(now)
                    val tabDuration = (now - prevTabStart).coerceAtLeast(0L)
                    if (tabDuration >= MIN_TAB_RECORD_MS) {
                        telemetryManager.addTabTime(lastTab.value, tabDuration)
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    // restart both timers
                    appSessionStart.set(System.currentTimeMillis())
                    tabStart.set(System.currentTimeMillis())
                }
                else -> { /* no-op */ }
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    // UI scaffold (unchanged)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                AppDestinations.entries.forEach { dest ->
                    NavigationBarItem(
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                        selected = dest == current,
                        onClick = { current = dest }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (current) {
            AppDestinations.LEVEL -> LevelScreen(Modifier.padding(innerPadding))
            AppDestinations.SOUNDMETER -> SoundMeterScreen(Modifier.padding(innerPadding))
            AppDestinations.FLASHLIGHT -> FlashlightScreen(Modifier.padding(innerPadding))
            AppDestinations.OTHER -> OtherScreen(
                modifier = Modifier.padding(innerPadding),
                telemetryManager = telemetryManager,
                onRequestShowRating = { showRatingDialog = true }
            )
        }
    }

    // Rating dialog (global; unchanged)
    if (showRatingDialog) {
        var rating by remember { mutableStateOf(5) }
        AlertDialog(
            onDismissRequest = { showRatingDialog = false },
            title = { Text("Оцените приложение") },
            text = {
                Column {
                    Text("Выберите оценку (1–5):")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        for (i in 1..5) {
                            val selected = i <= rating
                            IconToggleButton(checked = selected, onCheckedChange = {
                                rating = if (it) i else i - 1
                            }) {
                                Text(if (selected) "★" else "☆", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    telemetryManager.saveRating(rating)
                    showRatingDialog = false
                }) {
                    Text("Оценить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRatingDialog = false }) {
                    Text("Позже")
                }
            }
        )
    }
}


enum class AppDestinations(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    LEVEL("Level", Icons.Default.Straighten),
    SOUNDMETER("Sound", Icons.Default.Mic),
    FLASHLIGHT("Flashlight", Icons.Default.FlashlightOn),
    OTHER("Other", Icons.Default.MoreHoriz)
}
