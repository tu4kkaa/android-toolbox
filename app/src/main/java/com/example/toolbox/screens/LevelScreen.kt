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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt
import com.example.toolbox.DataStoreManager

@Composable
fun LevelScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accel = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    // состояние
    var modeFlat by remember { mutableStateOf(false) } // true = flat (screen up/down), false = edge
    var angleEdge by remember { mutableStateOf(0.0) } // degrees for edge mode (0..360)
    var tiltFlat by remember { mutableStateOf(0.0) } // degrees from flat plane (0..90)
    var bubbleX by remember { mutableStateOf(0.0) } // normalized -1..1 for BubbleView in flat mode
    var bubbleY by remember { mutableStateOf(0.0) } // normalized -1..1

    // offsets / calibration
    var offsetEdge by remember { mutableStateOf(0.0) }
    var offsetFlat by remember { mutableStateOf(0.0) }

    val ds = remember { DataStoreManager(context) }
    val coroutine = rememberCoroutineScope()

    // load saved offsets
    LaunchedEffect(Unit) {
        try {
            offsetEdge = ds.getLevelOffsetEdge()
            offsetFlat = ds.getLevelOffsetFlat()
        } catch (_: Exception) { /* ignore */ }
    }

    DisposableEffect(accel) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // raw accelerometer values in m/s^2 (including gravity)
                val ax = event.values[0].toDouble()
                val ay = event.values[1].toDouble()
                val az = event.values[2].toDouble()

                // magnitude of gravity vector (approx 9.8 when mostly static)
                val g = sqrt(ax * ax + ay * ay + az * az)

                // detect whether device is lying flat (screen up/down) by checking Z dominance
                // threshold 7 m/s^2 is empirical — tune if necessary
                val isFlat = abs(az) > 7.0

                if (isFlat) {
                    modeFlat = true
                    // tilt from flat: 0 deg when perfectly flat, grows as device is tilted toward edge
                    // formula: tilt = atan2(sqrt(ax^2 + ay^2), az)
                    val rawTilt = Math.toDegrees(atan2(sqrt(ax * ax + ay * ay), az))
                    // normalized bubble position from X,Y: use ax/|g| and ay/|g|
                    // invert X so that tilting to the right moves bubble to the right visually (adjust if needed)
                    val normX = (-ax / g).coerceIn(-1.0, 1.0)
                    val normY = (ay / g).coerceIn(-1.0, 1.0)
                    bubbleX = normX
                    bubbleY = normY
                    // apply saved flat offset (calibration): subtract offset so calibrate sets current to zero
                    tiltFlat = rawTilt - offsetFlat
                } else {
                    modeFlat = false
                    // Edge mode: 2D bubble along one axis (simulate spirit level along one rotation)
                    val rawAngle = Math.toDegrees(atan2(ay, ax)) // -180..180
                    angleEdge = rawAngle - offsetEdge
                    // normalize to -180..180 if needed
                    if (angleEdge > 180) angleEdge -= 360
                    if (angleEdge < -180) angleEdge += 360
                }
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

        Card(
            modifier = Modifier.size(300.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // pass mode + parameters to view
                if (modeFlat) {
                    BubbleViewFlat(bx = bubbleX.toFloat(), by = bubbleY.toFloat(), tilt = tiltFlat)
                } else {
                    BubbleViewEdge(angle = angleEdge)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (modeFlat) {
            Text("Mode: Flat (screen up/down)")
            Spacer(Modifier.height(6.dp))
            Text("Tilt from plane: ${tiltFlat.roundToInt()}°")
        } else {
            Text("Mode: Edge (upright / on side)")
            Spacer(Modifier.height(6.dp))
            Text("Angle: ${angleEdge.roundToInt()}°")
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                // calibrate current orientation according to active mode
                coroutine.launch {
                    if (modeFlat) {
                        // save current raw tilt as offset so this position becomes "zero"
                        ds.saveLevelOffsetFlat(offsetFlat + tiltFlat)
                        offsetFlat = ds.getLevelOffsetFlat()
                    } else {
                        ds.saveLevelOffsetEdge(offsetEdge + angleEdge)
                        offsetEdge = ds.getLevelOffsetEdge()
                    }
                }
            }) {
                Text("Calibrate (Set Zero)")
            }

            Button(onClick = {
                coroutine.launch {
                    if (modeFlat) {
                        ds.saveLevelOffsetFlat(0.0)
                        offsetFlat = 0.0
                    } else {
                        ds.saveLevelOffsetEdge(0.0)
                        offsetEdge = 0.0
                    }
                }
            }, colors = ButtonDefaults.buttonColors()) {
                Text("Reset")
            }
        }
    }
}

/**
 * Edge-mode bubble: single-axis bubble (horizontal movement based on angle).
 */
@Composable
private fun BubbleViewEdge(angle: Double) {
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
            drawCircle(color = bgColor, radius = size.minDimension / 2f)
            val maxOffset = size.minDimension / 4f
            val px = ((angle / 45.0) * maxOffset).toFloat().coerceIn(-maxOffset, maxOffset)
            drawCircle(color = Color.White, radius = size.minDimension / 8f, center = Offset(cx + px, cy))
            drawCircle(color = Color.DarkGray, radius = 4f, center = Offset(cx, cy))
        }
    }
}

/**
 * Flat-mode bubble: 2D bubble (x and y), using normalized bx,by in range [-1,1].
 * Also shows small central marker.
 */
@Composable
private fun BubbleViewFlat(bx: Float, by: Float, tilt: Double) {
    // Consider green if tilt small (close to flat)
    val inGreen = abs(tilt) < 3.0
    val bgColor by animateColorAsState(if (inGreen) Color(0xFFB9F6CA) else Color(0xFFFFCDD2))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize(0.95f)) {
            val w = size.width
            val h = size.height
            // circular background
            val radius = size.minDimension / 2f
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            drawCircle(color = bgColor, radius = radius, center = Offset(centerX, centerY))
            // map bx,by (-1..1) to pixel offsets within circle
            val maxOffset = radius * 0.6f
            val px = (bx * maxOffset).coerceIn(-maxOffset, maxOffset)
            val py = (by * maxOffset).coerceIn(-maxOffset, maxOffset)
            drawCircle(color = Color.White, radius = size.minDimension / 12f, center = Offset(centerX + px, centerY + py))
            // center marker
            drawCircle(color = Color.DarkGray, radius = 4f, center = Offset(centerX, centerY))
        }
    }
}
