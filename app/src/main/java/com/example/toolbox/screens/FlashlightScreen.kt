// FlashlightScreen.kt
package com.example.toolbox.screens

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.compose.foundation.layout.*
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
    var torchEnabled by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(FlashMode.ON) }
    var strobeFreq by remember { mutableStateOf(5) } // Hz
    var brightness by remember { mutableStateOf(100) } // 0..100 simulated via duty cycle
    val coroutine = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }

    fun setTorch(id: String, on: Boolean) {
        try {
            cameraManager.setTorchMode(id, on)
        } catch (e: Exception) {
            Log.e("Flashlight", "torch error", e)
        }
    }

    // pick a camera id that has a flash
    val cameraIdWithFlash by remember {
        mutableStateOf(run {
            var idFound: String? = null
            try {
                for (id in cameraManager.cameraIdList) {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val hasFlash = chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    if (hasFlash) { idFound = id; break }
                }
            } catch (_: Exception) {}
            idFound
        })
    }

    fun stopJob() {
        job?.cancel()
        job = null
        // ensure torch off
        cameraIdWithFlash?.let { setTorch(it, false) }
        torchEnabled = false
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Top) {
        Text("Flashlight", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                // toggle simple ON mode
                if (mode != FlashMode.ON) {
                    stopJob()
                    mode = FlashMode.ON
                }
                if (torchEnabled) {
                    stopJob()
                } else {
                    cameraIdWithFlash?.let { id ->
                        setTorch(id, true)
                        torchEnabled = true
                    }
                }
            }) { Text("Toggle ON") }

            Button(onClick = {
                // SOS mode
                if (mode != FlashMode.SOS) {
                    stopJob()
                    mode = FlashMode.SOS
                    job = coroutine.launch {
                        cameraIdWithFlash?.let { id ->
                            torchEnabled = true
                            while (isActive) {
                                // Morse for ...---... : dot=short, dash=long
                                suspend fun dot() { setTorch(id, true); delay(200); setTorch(id, false); delay(200) }
                                suspend fun dash() { setTorch(id, true); delay(600); setTorch(id, false); delay(200) }
                                dot(); dot(); dot()
                                delay(200)
                                dash(); dash(); dash()
                                delay(200)
                                dot(); dot(); dot()
                                delay(1500)
                            }
                        }
                    }
                } else {
                    stopJob()
                }
            }) { Text("SOS") }

            Button(onClick = {
                // Strobe
                if (mode != FlashMode.STROBE) {
                    stopJob()
                    mode = FlashMode.STROBE
                    job = coroutine.launch {
                        cameraIdWithFlash?.let { id ->
                            torchEnabled = true
                            while (isActive) {
                                setTorch(id, true)
                                delay((500L / strobeFreq).coerceAtLeast(10L))
                                setTorch(id, false)
                                delay((500L / strobeFreq).coerceAtLeast(10L))
                            }
                        }
                    }
                } else {
                    stopJob()
                }
            }) { Text("Strobe") }

            Button(onClick = {
                // Candle effect
                if (mode != FlashMode.CANDLE) {
                    stopJob()
                    mode = FlashMode.CANDLE
                    job = coroutine.launch {
                        cameraIdWithFlash?.let { id ->
                            torchEnabled = true
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
                } else {
                    stopJob()
                }
            }) { Text("Candle") }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                strobeFreq = (strobeFreq + 1).coerceAtMost(30)
            }) { Text("Freq +") }
            Text("Freq: $strobeFreq Hz", modifier = Modifier.align(Alignment.CenterVertically))
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = {
                strobeFreq = (strobeFreq - 1).coerceAtLeast(1)
            }) { Text("Freq -") }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                brightness = (brightness + 10).coerceAtMost(100)
            }) { Text("Bright +") }
            Text("Brightness: $brightness%", modifier = Modifier.align(Alignment.CenterVertically))
            OutlinedButton(onClick = {
                brightness = (brightness - 10).coerceAtLeast(10)
            }) { Text("Bright -") }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            stopJob()
        }) {
            Text("Stop / Turn Off")
        }
    }
}
