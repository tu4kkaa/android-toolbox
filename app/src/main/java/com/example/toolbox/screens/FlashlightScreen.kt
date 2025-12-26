// FlashlightScreen.kt
package com.example.toolbox.screens

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
    var brightness by remember { mutableStateOf(100) } // 1..100 - percent (try to map to real level if supported)
    val coroutine = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }

    fun setTorch(id: String, on: Boolean) {
        try {
            cameraManager.setTorchMode(id, on)
        } catch (e: Exception) {
            Log.e("Flashlight", "setTorchMode error", e)
        }
    }

    /**
     * Try to set torch strength via CameraManager.setTorchStrengthLevel if available.
     * Returns true if call was attempted successfully, false otherwise.
     * Using reflection so project can compile with older SDK but still call on newer devices.
     */
    fun trySetTorchStrength(id: String, levelPercent: Int): Boolean {
        val level = levelPercent.coerceIn(1, 100)
        return try {
            val clazz = CameraManager::class.java
            val method = clazz.getMethod("setTorchStrengthLevel", String::class.java, Int::class.javaPrimitiveType)
            method.invoke(cameraManager, id, level)
            true
        } catch (t: NoSuchMethodException) {
            false
        } catch (e: Exception) {
            Log.w("Flashlight", "setTorchStrengthLevel failed: ${e.message}")
            false
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
                // Try to use real platform brightness first (if available)
                val realSupported = trySetTorchStrength(id, brightness)
                if (realSupported) {
                    // turn torch on (strength applied)
                    setTorch(id, true)
                    isOn = true
                } else {
                    // If real brightness not supported - do NOT emulate via blinking.
                    // Just turn torch fully on regardless of brightness setting.
                    setTorch(id, true)
                    isOn = true
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
        Text("Flashlight", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

        // Mode selector: OutlinedTextField + DropdownMenu (stable API)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = modeLabel,
                onValueChange = { /* read-only */ },
                readOnly = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                label = { Text("Mode", fontSize = 16.sp) },
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
                                },
                                fontSize = 18.sp
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { strobeFreq = (strobeFreq - 1).coerceAtLeast(1) }, modifier = Modifier.size(64.dp)) { Text("-", fontSize = 22.sp) }
                Text("Freq: $strobeFreq Hz", fontSize = 20.sp)
                OutlinedButton(onClick = { strobeFreq = (strobeFreq + 1).coerceAtMost(30) }, modifier = Modifier.size(64.dp)) { Text("+", fontSize = 22.sp) }
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.weight(1f)) // push the bottom controls to the bottom

        // Bottom control: brightness - | (toggle) | + centered
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decrease brightness
            OutlinedButton(
                onClick = {
                    val newB = (brightness - 10).coerceAtLeast(1)
                    brightness = newB
                    // If torch supports real strength, try to apply immediately
                    if (isOn && cameraIdWithFlash != null) {
                        trySetTorchStrength(cameraIdWithFlash!!, brightness)
                    }
                },
                modifier = Modifier.size(64.dp)
            ) { Text("-", fontSize = 22.sp) }

            // Central circular toggle button with flashlight icon
            if (isOn) {
                Button(
                    onClick = { toggleOnOff() },
                    shape = MaterialTheme.shapes.small.copy(all = CornerSize(percent = 50)),
                    modifier = Modifier.size(110.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = "Turn off", modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        toggleOnOff()
                    },
                    shape = MaterialTheme.shapes.small.copy(all = CornerSize(percent = 50)),
                    modifier = Modifier.size(110.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = "Turn on", modifier = Modifier.size(44.dp))
                }
            }

            // Increase brightness
            OutlinedButton(
                onClick = {
                    val newB = (brightness + 10).coerceAtMost(100)
                    brightness = newB
                    // If torch supports real strength, try to apply immediately
                    if (isOn && cameraIdWithFlash != null) {
                        trySetTorchStrength(cameraIdWithFlash!!, brightness)
                    }
                },
                modifier = Modifier.size(64.dp)
            ) { Text("+", fontSize = 22.sp) }
        }

        Spacer(Modifier.height(8.dp))
        // Removed Mode:... and Note:... texts as requested
    }
}
