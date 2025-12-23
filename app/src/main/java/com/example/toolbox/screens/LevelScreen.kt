// LevelScreen.kt
package com.example.toolbox.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.abs
import com.example.toolbox.DataStoreManager

@Composable
fun LevelScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accel = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    var angle by remember { mutableStateOf(0.0) }
    var offset by remember { mutableStateOf(0.0) }

    val ds = remember { DataStoreManager(context) }
    val coroutine = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // загрузить сохранённую калибровку (может блокировать, но DataStore используется в suspend)
        offset = ds.getLevelOffset()
    }

    DisposableEffect(accel) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val ax = event.values[0].toDouble()
                val ay = event.values[1].toDouble()
                // compute tilt in degrees around plane for bubble: use atan2
                val deg = Math.toDegrees(atan2(ay, ax))
                angle = deg - offset
            }
            override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}
        }
        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Bubble Level", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        // Bubble UI
        Card(
            modifier = Modifier.size(260.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                BubbleView(angle = angle)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Angle: ${angle.roundToInt()}°")
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                // save current sensor reading as offset to zero this orientation
                coroutine.launch {
                    ds.saveLevelOffset(offset + angle)
                    offset = ds.getLevelOffset()
                }
            }) {
                Text("Calibrate (Set Zero)")
            }
            Button(onClick = {
                coroutine.launch {
                    ds.saveLevelOffset(0.0)
                    offset = 0.0
                }
            }, colors = ButtonDefaults.buttonColors()) {
                Text("Reset")
            }
        }
    }
}

@Composable
private fun BubbleView(angle: Double) {
    val absAngle = abs(angle)
    val inGreen = absAngle < 3.0
    val bgColor by animateColorAsState(if (inGreen) Color(0xFFB9F6CA) else Color(0xFFFFCDD2))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize(0.9f)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            // draw circular background
            drawCircle(color = bgColor, radius = size.minDimension / 2f)
            // bubble offset: map angle to small offset in pixels
            val maxOffset = size.minDimension / 4f
            val px = ((angle / 45.0) * maxOffset).toFloat().coerceIn(-maxOffset, maxOffset)
            // Bubble
            drawCircle(color = Color.White, radius = size.minDimension / 8f, center = Offset(cx + px, cy))
            // center marker
            drawCircle(color = Color.DarkGray, radius = 4f, center = Offset(cx, cy))
        }
    }
}
