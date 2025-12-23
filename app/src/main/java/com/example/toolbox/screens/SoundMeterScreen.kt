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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        // nothing special here
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Sound Meter", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier
            .height(180.dp)
            .fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    // draw simple bar based on dbVal
                    val perc = ((dbVal + 100) / 100).coerceIn(0.0, 1.0)
                    drawRect(color = Color.LightGray, size = size)
                    val color = when {
                        dbVal < 60 -> Color(0xFF4CAF50)
                        dbVal < 85 -> Color(0xFFFFC107)
                        else -> Color(0xFFF44336)
                    }
                    drawRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, h * (1f - perc.toFloat())),
                        size = androidx.compose.ui.geometry.Size(w, h * perc.toFloat())
                    )
                    drawRect(color = Color.Black, size = size, style = Stroke(width = 2f))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Level: ${"%.1f".format(dbVal)} dB", fontWeight = FontWeight.SemiBold)
        Text("Peak: ${"%.1f".format(peak)} dB")

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                // Check permission
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    return@Button
                }
                if (audioJob == null) {
                    // start audio measurement in a coroutine job so we can cancel it reliably
                    isRunning = true
                    audioJob = coroutine.launch {
                        try {
                            runAudioLoop(onLevel = {
                                // update UI from coroutine
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
            }) {
                Text(if (isRunning) "Running..." else "Start")
            }

            Button(onClick = {
                // stop measurement by cancelling job
                audioJob?.cancel()
                audioJob = null
                isRunning = false
            }) {
                Text("Stop")
            }

            Button(onClick = {
                // calibrate reference so current reading becomes zero-offset
                coroutine.launch {
                    try {
                        ds.saveSoundCalib(-dbVal)
                        calibOffset = ds.getSoundCalib()
                    } catch (e: Exception) {
                        Log.w("SoundMeter", "Calibration save failed: ${e.message}")
                    }
                }
            }, colors = ButtonDefaults.buttonColors()) {
                Text("Calibrate")
            }
        }

        Spacer(Modifier.height(12.dp))
        if (history.isNotEmpty()) {
            Text("Session Peaks: ${history.maxOrNull()?.let { "%.1f".format(it) } ?: "--"} dB")
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
