// SoundMeterScreen.kt
package com.example.toolbox.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.toolbox.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt

@Composable
fun SoundMeterScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ds = remember { DataStoreManager(context) }
    val coroutine = rememberCoroutineScope()

    var isRunning by remember { mutableStateOf(false) }
    var dbVal by remember { mutableStateOf(0.0) }
    var peak by remember { mutableStateOf(0.0) }
    var calibOffset by remember { mutableStateOf(0.0) }
    val history = remember { mutableStateListOf<Double>() }
    var audioJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        try {
            calibOffset = ds.getSoundCalib()
        } catch (e: Exception) {
            Log.w("SoundMeter", "Failed to read calibration: ${e.message}")
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        // nothing special here - the start logic will re-check permission
    }

    // helpers to start/stop audio
    val startAudio: () -> Unit = {
        if (audioJob == null) {
            isRunning = true
            audioJob = coroutine.launch {
                try {
                    runAudioLoop(onLevel = {
                        dbVal = it + calibOffset
                        history.add(dbVal)
                        if (dbVal > peak) peak = dbVal
                        if (history.size > 200) history.removeAt(0)
                    })
                } catch (e: Exception) {
                    Log.e("SoundMeter", "Audio loop error: ${e.message}", e)
                } finally {
                    isRunning = false
                    audioJob = null
                }
            }
        }
    }

    val stopAudio: () -> Unit = {
        audioJob?.cancel()
        audioJob = null
        isRunning = false
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Sound Meter", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))

        // Card now contains only the visual canvas to maximize available vertical space
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp), // increased height to make the level indicator visually tall
            shape = RoundedCornerShape(12.dp)
        ) {
            // Canvas fills the whole card area (no extra space for texts inside the card)
            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                Canvas(modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))) {
                    val w = size.width
                    val h = size.height

                    // bounds: -100 dB .. +40 dB
                    val minDb = -100.0
                    val maxDb = 40.0

                    val perc = ((dbVal - minDb) / (maxDb - minDb)).coerceIn(0.0, 1.0)

                    // background
                    drawRect(color = Color(0xFFF5F5F5), size = size)

                    // level color
                    val color = when {
                        dbVal < 60 -> Color(0xFF4CAF50)
                        dbVal < 85 -> Color(0xFFFFC107)
                        else -> Color(0xFFF44336)
                    }

                    // filled rect from bottom up according to perc
                    drawRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, h * (1f - perc.toFloat())),
                        size = androidx.compose.ui.geometry.Size(w, h * perc.toFloat())
                    )

                    // 0 dB dashed line
                    val zeroPerc = ((0.0 - minDb) / (maxDb - minDb)).coerceIn(0.0, 1.0)
                    val yZero = h * (1f - zeroPerc.toFloat())
                    drawLine(
                        color = Color.Black,
                        start = androidx.compose.ui.geometry.Offset(0f, yZero),
                        end = androidx.compose.ui.geometry.Offset(w, yZero),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )

                    // border
                    drawRect(color = Color.Black, size = size, style = Stroke(width = 2f))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Level and Peak moved outside the card so canvas uses maximum vertical space
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Level", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("${"%.1f".format(dbVal)} dB", fontWeight = FontWeight.Bold, fontSize = 32.sp)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("Peak", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("${"%.1f".format(peak)} dB", fontWeight = FontWeight.Bold, fontSize = 28.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Session peaks summary
        if (history.isNotEmpty()) {
            Text("Session Peaks: ${history.maxOrNull()?.let { "%.1f".format(it) } ?: "--"} dB")
        }

        Spacer(Modifier.weight(1f)) // push controls to bottom

        // Bottom control bar
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {

            // Calibrate (left)
            Button(onClick = {
                coroutine.launch {
                    try {
                        ds.saveSoundCalib(-dbVal)
                        calibOffset = ds.getSoundCalib()
                    } catch (e: Exception) {
                        Log.w("SoundMeter", "Calibration save failed: ${e.message}")
                    }
                }
            }) {
                Text("Calibrate")
            }

            // Start/Stop circular toggle (center) with microphone icon
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                // permission not granted: show disabled-looking outline and launch permission when tapped
                OutlinedButton(
                    onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Request mic", modifier = Modifier.size(32.dp))
                }
            } else {
                if (isRunning) {
                    // running: full circular primary button
                    Button(
                        onClick = { stopAudio() },
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Stop", modifier = Modifier.size(32.dp), tint = Color.White)
                    }
                } else {
                    // stopped: transparent/outlined circular button
                    OutlinedButton(
                        onClick = {
                            // before starting, re-check permission
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                startAudio()
                            }
                        },
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Start", modifier = Modifier.size(32.dp))
                    }
                }
            }

            // A placeholder for future action on the right (keeps center button balanced)
            Spacer(modifier = Modifier.width(72.dp))
        }
    }
}

/**
 * Reads microphone in a cancellable coroutine. Converts PCM to dBFS and reports via onLevel.
 * The function is cooperative and will stop when the coroutine is cancelled.
 */
suspend fun runAudioLoop(onLevel: (Double) -> Unit) {
    withContext(Dispatchers.Default) {
        val sampleRate = 44100
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).let { if (it == AudioRecord.ERROR || it == AudioRecord.ERROR_BAD_VALUE) sampleRate else it }

        val bufferSize = (minBuf).coerceAtLeast(sampleRate / 10)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (se: SecurityException) {
            Log.e("SoundMeter", "Permission denied for AudioRecord: ${se.message}")
            return@withContext
        } catch (e: Exception) {
            Log.e("SoundMeter", "Failed to create AudioRecord: ${e.message}")
            return@withContext
        }

        val buffer = ShortArray(bufferSize)
        try {
            recorder.startRecording()
            val ref = 32768.0 // max amplitude for 16-bit
            while (true) { // cancellable because of suspending delay
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    var sum = 0.0
                    for (i in 0 until read) {
                        val v = buffer[i].toDouble() / ref
                        sum += v * v
                    }
                    val rms = if (read > 0) sqrt(sum / read) else 0.0
                    val db = if (rms > 0) 20.0 * log10(rms) else -120.0
                    onLevel(db)
                } else {
                    onLevel(-120.0)
                }

                // cooperative cancellation point
                kotlinx.coroutines.delay(50)
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // coroutine was cancelled — just propagate after cleanup
            throw ce
        } catch (e: Exception) {
            Log.e("SoundMeter", "Error during recording: ${e.message}", e)
        } finally {
            try {
                recorder.stop()
            } catch (_: Exception) { /* ignore */ }
            try {
                recorder.release()
            } catch (_: Exception) { /* ignore */ }
        }
    }
}
