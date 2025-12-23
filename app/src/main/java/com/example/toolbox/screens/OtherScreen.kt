// OtherScreen.kt
package com.example.toolbox.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.toolbox.TelemetryManager
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun OtherScreen(
    modifier: Modifier = Modifier,
    telemetryManager: TelemetryManager,
    onRequestShowRating: (() -> Unit)? = null // optional callback from parent
) {
    val scope = rememberCoroutineScope()
    val telemetryState by telemetryManager.telemetryFlow.collectAsState()

    var showDialog by rememberSaveable { mutableStateOf(false) }
    var selectedStars by rememberSaveable { mutableStateOf(5) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Other", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        // 1) Rate app button
        Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Оценить приложение")
        }
        Spacer(Modifier.height(12.dp))

        // 2) About / telemetry
        Text("Обо мне", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        val snap = telemetryState
        Text("Запусков приложения: ${snap.launchCount}")
        Text("Общее время использования: ${formatMs(snap.totalUsageMs)}")
        Text("Количество сессий: ${snap.sessionsCount}")
        Text("Средняя длительность сессии: ${formatMs(snap.averageSessionMs)}")
        Spacer(Modifier.height(8.dp))
        Text("Среднее время за сессию по вкладкам (приблизительно):")
        snap.averagePerTabMs.forEach { (k, v) ->
            Text("• $k: ${formatMs(v)}")
        }
        Spacer(Modifier.height(12.dp))
        snap.lastRating?.let {
            Text("Последняя оценка: $it ★")
        }

        Spacer(Modifier.weight(1f))

        // Optional: trigger show rating from parent (e.g. auto on 5th launch)
        OutlinedButton(onClick = { onRequestShowRating?.invoke() }) {
            Text("Показать диалог оценки (тест)")
        }
    }

    // Rating dialog
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
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Отмена") }
            },
            title = { Text("Оцените приложение") },
            text = {
                Column {
                    Text("Выберите рейтинг (1–5):")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

private fun formatMs(ms: Long): String {
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}
