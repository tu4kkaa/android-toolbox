// FlashlightScreen.kt
package com.example.toolbox.screens

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class FlashMode { ON, SOS, STROBE, CANDLE }

@Composable
fun FlashlightScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }

    // camera id with flash (nullable)
    val cameraIdWithFlash by remember {
        mutableStateOf(run {
            var idFound: String? = null
            try {
                for (id in cameraManager.cameraIdList) {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    if (hasFlash) { idFound = id; break }
                }
            } catch (e: Exception) {
                Log.w("Flashlight", "cameraId search failed: ${e.message}")
            }
            idFound
        })
    }

    var selectedMode by remember { mutableStateOf(FlashMode.ON) }
    var isOn by remember { mutableStateOf(false) } // logical torch state
    var strobeFreq by remember { mutableStateOf(5) } // Hz
    var brightness by remember { mutableStateOf(100) } // 0..100 - simulated via PWM
    val coroutine = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }

    fun setTorch(id: String, on: Boolean) {
        try {
            cameraManager.setTorchMode(id, on)
        } catch (e: Exception) {
            Log.e("Flashlight", "setTorchMode error", e)
        }
    }

    fun stopCurrentJobAndTurnOff() {
        job?.cancel()
        job = null
        cameraIdWithFlash?.let { setTorch(it, false) }
        isOn = false
    }

    // Start behaviour according to selected mode and current brightness/freq
    fun startMode() {
        // ensure previous job stopped
        stopCurrentJobAndTurnOff()
        val id = cameraIdWithFlash ?: run {
            Log.w("Flashlight", "No camera with flash")
            return
        }

        when (selectedMode) {
            FlashMode.ON -> {
                // If brightness == 100 -> continuous ON, otherwise simulate PWM
                if (brightness >= 100) {
                    setTorch(id, true)
                    isOn = true
                } else {
                    // PWM simulation: short period -> duty cycle
                    val periodMs = 50L
                    job = coroutine.launch {
                        isOn = true
                        while (isActive) {
                            val onMs = (periodMs * (brightness.coerceIn(1, 100)) / 100L)
                            val offMs = (periodMs - onMs).coerceAtLeast(1L)
                            setTorch(id, true)
                            delay(onMs)
                            setTorch(id, false)
                            delay(offMs)
                        }
                    }
                }
            }

            FlashMode.SOS -> {
                // SOS pattern: ... --- ...  (dot=200, dash=600, intra=200, between letters=600, between words=1400)
                val dot = 200L
                val dash = 600L
                val intra = 200L
                val betweenLetters = 600L
                val betweenWords = 1400L
                job = coroutine.launch {
                    isOn = true
                    while (isActive) {
                        suspend fun dotPulse() { setTorch(id, true); delay(dot); setTorch(id, false); delay(intra) }
                        suspend fun dashPulse() { setTorch(id, true); delay(dash); setTorch(id, false); delay(intra) }
                        dotPulse(); dotPulse(); dotPulse()
                        delay(betweenLetters)
                        dashPulse(); dashPulse(); dashPulse()
                        delay(betweenLetters)
                        dotPulse(); dotPulse(); dotPulse()
                        delay(betweenWords)
                    }
                }
            }

            FlashMode.STROBE -> {
                val freq = strobeFreq.coerceIn(1, 30)
                val halfPeriod = (500L / freq).coerceAtLeast(10L)
                job = coroutine.launch {
                    isOn = true
                    while (isActive) {
                        setTorch(id, true)
                        delay(halfPeriod)
                        setTorch(id, false)
                        delay(halfPeriod)
                    }
                }
            }

            FlashMode.CANDLE -> {
                job = coroutine.launch {
                    isOn = true
                    while (isActive) {
                        val onTime = Random.nextLong(30, 150)
                        val offTime = Random.nextLong(30, 250)
                        setTorch(id, true)
                        delay(onTime)
                        setTorch(id, false)
                        delay(offTime)
                    }
                }
            }
        }
    }

    // Toggle On/Off button behavior
    fun toggleOnOff() {
        if (isOn) {
            stopCurrentJobAndTurnOff()
        } else {
            startMode()
        }
    }

    // If user changes mode/params while running, restart
    LaunchedEffect(selectedMode, strobeFreq, brightness) {
        if (isOn) {
            startMode()
        }
    }

    // Dropdown state for modes (stable implementation)
    var expanded by remember { mutableStateOf(false) }
    val modeLabel = when (selectedMode) {
        FlashMode.ON -> "On"
        FlashMode.SOS -> "SOS"
        FlashMode.STROBE -> "Strobe"
        FlashMode.CANDLE -> "Candle"
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Top) {
        Text("Flashlight", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        // Mode selector: OutlinedTextField + DropdownMenu (stable API)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = modeLabel,
                onValueChange = { /* read-only */ },
                readOnly = true,
                label = { Text("Mode") },
                trailingIcon = {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Open modes")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                FlashMode.values().forEach { m ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (m) {
                                    FlashMode.ON -> "On"
                                    FlashMode.SOS -> "SOS"
                                    FlashMode.STROBE -> "Strobe"
                                    FlashMode.CANDLE -> "Candle"
                                }
                            )
                        },
                        onClick = {
                            selectedMode = m
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Controls that depend on mode
        if (selectedMode == FlashMode.STROBE) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { strobeFreq = (strobeFreq - 1).coerceAtLeast(1) }) { Text("-") }
                Text("Freq: $strobeFreq Hz")
                OutlinedButton(onClick = { strobeFreq = (strobeFreq + 1).coerceAtMost(30) }) { Text("+") }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Brightness control (simulated)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { brightness = (brightness - 10).coerceAtLeast(1) }) { Text("-") }
            Text("Brightness: $brightness%")
            OutlinedButton(onClick = { brightness = (brightness + 10).coerceAtMost(100) }) { Text("+") }
        }

        Spacer(Modifier.height(12.dp))

        // Single toggle button On/Off
        Button(onClick = { toggleOnOff() }, modifier = Modifier.fillMaxWidth()) {
            Text(if (isOn) "Turn Off" else "Turn On")
        }

        Spacer(Modifier.height(12.dp))

        // Info
        if (cameraIdWithFlash == null) {
            Text("No camera flash detected on this device.", color = MaterialTheme.colorScheme.error)
        } else {
            Text("Mode: ${selectedMode.name}, Device flash available.")
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Note: true brightness control requires Camera2 capture APIs; current \"brightness\" is simulated by duty-cycle (may cause flicker).",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
