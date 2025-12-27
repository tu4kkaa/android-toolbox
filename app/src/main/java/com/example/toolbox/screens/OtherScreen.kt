// OtherScreen.kt
package com.example.toolbox.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.toolbox.TelemetryManager
import com.example.toolbox.TelemetrySnapshot
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Row

private enum class OtherScreenState { MAIN, ABOUT, CRASH }

/**
 * Простая независимая реализация TopBar, совместимая с любыми версиями material3.
 * Показывает заголовок и (опционально) кнопку навигации слева.
 */
@Composable
fun SimpleTopBar(
    title: String,
    showNavIcon: Boolean = false,
    onNavClick: (() -> Unit)? = null
) {
    Surface(
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showNavIcon && onNavClick != null) {
                IconButton(onClick = onNavClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = if (showNavIcon) 4.dp else 0.dp)
            )
        }
    }
}

@Composable
fun OtherScreen(
    modifier: Modifier = Modifier,
    telemetryManager: TelemetryManager,
    onRequestShowRating: (() -> Unit)? = null // optional callback from parent
) {
    val scope = rememberCoroutineScope()
    val telemetryState by telemetryManager.telemetryFlow.collectAsState()

    var currentScreen by rememberSaveable { mutableStateOf(OtherScreenState.MAIN) }

    var showDialog by rememberSaveable { mutableStateOf(false) }
    var selectedStars by rememberSaveable { mutableStateOf(5) }

    when (currentScreen) {
        OtherScreenState.MAIN -> {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                topBar = {
                    SimpleTopBar(title = "Other")
                },
                content = { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = { currentScreen = OtherScreenState.ABOUT },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text("About me")
                        }

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = { showDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text("Rate app")
                        }

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = { currentScreen = OtherScreenState.CRASH },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text("Crash reports")
                        }

                        Spacer(Modifier.weight(1f))
                    }
                }
            )
        }

        OtherScreenState.ABOUT -> {
            Scaffold(
                topBar = {
                    SimpleTopBar(
                        title = "About me",
                        showNavIcon = true,
                        onNavClick = { currentScreen = OtherScreenState.MAIN }
                    )
                },
                content = { innerPadding ->
                    AboutMeContent(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(16.dp),
                        telemetryState = telemetryState,
                        onRateClick = { showDialog = true }
                    )
                }
            )
        }

        OtherScreenState.CRASH -> {
            Scaffold(
                topBar = {
                    SimpleTopBar(
                        title = "Crash reports",
                        showNavIcon = true,
                        onNavClick = { currentScreen = OtherScreenState.MAIN }
                    )
                },
                content = { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        // Placeholder — оставлено пустым по заданию
                        Text(
                            "Здесь будут отчёты об авариях (пока пусто).",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            )
        }
    }

    // Rating dialog (shared)
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    // save rating
                    scope.launch {
                        telemetryManager.saveRating(selectedStars)
                    }
                    showDialog = false
                }) {
                    Text("Оценить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Позже") }
            },
            title = { Text("Оцените приложение") },
            text = {
                Column {
                    Text("Выберите оценку (1–5):")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        for (i in 1..5) {
                            val selected = i <= selectedStars
                            IconToggleButton(checked = selected, onCheckedChange = { checked ->
                                if (checked) selectedStars = i
                                else if (i == selectedStars) selectedStars = i - 1
                            }) {
                                Text(if (selected) "★" else "☆", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun AboutMeContent(
    modifier: Modifier = Modifier,
    telemetryState: TelemetrySnapshot,
    onRateClick: () -> Unit
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Обо мне", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
        }

        item {
            Text("Запусков приложения: ${telemetryState.launchCount}", style = MaterialTheme.typography.bodyLarge)
        }
        item {
            Text("Общее время использования: ${formatMs(telemetryState.totalUsageMs)}", style = MaterialTheme.typography.bodyLarge)
        }
        item {
            Text("Количество сессий: ${telemetryState.sessionsCount}", style = MaterialTheme.typography.bodyLarge)
        }
        item {
            Text("Средняя длительность сессии: ${formatMs(telemetryState.averageSessionMs)}", style = MaterialTheme.typography.bodyLarge)
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Общее суммарное время по вкладкам:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            // показываем суммарное время, а не среднее
            telemetryState.totalPerTabMs.forEach { (k, v) ->
                Text("• $k: ${formatMs(v)}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            telemetryState.lastRating?.let {
                Text("Последняя оценка: $it ★", style = MaterialTheme.typography.bodyLarge)
            } ?: Text("Оценок пока нет", style = MaterialTheme.typography.bodyLarge)
        }
        item {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRateClick, modifier = Modifier.fillMaxWidth()) {
                Text("Оценить приложение")
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
        }
    }
}


private fun formatMs(ms: Long): String {
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}
