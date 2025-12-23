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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment


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
        // permissions
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

    // Session tracking
    val lastTab = remember { mutableStateOf(current) }
    val sessionStart = remember { AtomicLong(System.currentTimeMillis()) }

    // handle tab switching: save previous session duration
    LaunchedEffect(current) {
        val now = System.currentTimeMillis()
        val start = sessionStart.getAndSet(now)
        val duration = (now - start).coerceAtLeast(0L)
        // update telemetry (non-suspending)
        telemetryManager.addSession(duration)
        telemetryManager.addTabTime(lastTab.value, duration)
        lastTab.value = current
    }

    // lifecycle observer: save session on app pause/stop
    val lifecycleOwner = androidx.compose.runtime.rememberUpdatedState(LocalContext.current)
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                val now = System.currentTimeMillis()
                val start = sessionStart.getAndSet(now)
                val duration = (now - start).coerceAtLeast(0L)
                telemetryManager.addSession(duration)
                telemetryManager.addTabTime(lastTab.value, duration)
            } else if (event == Lifecycle.Event.ON_RESUME) {
                sessionStart.set(System.currentTimeMillis())
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

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

    // Rating dialog (global; appears when either auto or user requested)
    if (showRatingDialog) {
        // simple rating dialog (1..5)
        var rating by remember { mutableStateOf(5) }
        AlertDialog(
            onDismissRequest = { showRatingDialog = false },
            title = { Text("Оцените приложение") },
            text = {
                Column {
                    Text("Спасибо! Пожалуйста, поставьте оценку (1–5):")
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
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRatingDialog = false }) {
                    Text("Отмена")
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
