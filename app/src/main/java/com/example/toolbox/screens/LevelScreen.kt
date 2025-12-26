// LevelScreen.kt
package com.example.toolbox.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import com.example.toolbox.DataStoreManager
import java.util.Locale

@Composable
fun LevelScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accel = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    // forcedMode: null = Auto, true = Flat, false = Edge
    var forcedMode by remember { mutableStateOf<Boolean?>(null) }

    // sensor derived state
    var modeFlat by remember { mutableStateOf(false) }
    var angleEdge by remember { mutableStateOf(0.0) }
    var tiltFlat by remember { mutableStateOf(0.0) }
    var bubbleX by remember { mutableStateOf(0.0) }
    var bubbleY by remember { mutableStateOf(0.0) }

    // offsets / calibration
    var offsetEdge by remember { mutableStateOf(0.0) }
    var offsetFlat by remember { mutableStateOf(0.0) }

    val ds = remember { DataStoreManager(context) }
    val coroutine = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // width of the degrees string measured (dp)
    var degreesWidthDp by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        try {
            offsetEdge = ds.getLevelOffsetEdge()
            offsetFlat = ds.getLevelOffsetFlat()
        } catch (_: Exception) { /* ignore */ }
    }

    DisposableEffect(accel) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val ax = event.values[0].toDouble()
                val ay = event.values[1].toDouble()
                val az = event.values[2].toDouble()

                val g = sqrt(ax * ax + ay * ay + az * az)
                val isFlatDetected = abs(az) > 7.0

                val effectiveFlat = forcedMode ?: isFlatDetected

                if (effectiveFlat) {
                    modeFlat = true
                    val rawTilt = Math.toDegrees(atan2(sqrt(ax * ax + ay * ay), az))
                    // X inverted so tilting right -> bubble to right. Y inverted to match visual tilt up/down.
                    val normX = (-ax / g).coerceIn(-1.0, 1.0)
                    val normY = (-ay / g).coerceIn(-1.0, 1.0)
                    bubbleX = normX
                    bubbleY = normY
                    tiltFlat = rawTilt - offsetFlat
                } else {
                    modeFlat = false
                    val rawAngle = Math.toDegrees(atan2(ay, ax)) // -180..180
                    var a = rawAngle - offsetEdge
                    if (a > 180) a -= 360
                    if (a < -180) a += 360
                    angleEdge = a
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Bubble Level",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(12.dp))

            // Main instrument card
            Card(
                modifier = Modifier
                    .size(320.dp)
                    .align(Alignment.CenterHorizontally),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFFEEF7FF), Color(0xFFF7FBF2))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (modeFlat) {
                        BubbleViewFlatAnimated(bx = bubbleX.toFloat(), by = bubbleY.toFloat(), tilt = tiltFlat)
                    } else {
                        BubbleViewEdgeAnimated(angle = angleEdge)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Mode buttons row
            ModeButtonsRow(
                forcedMode = forcedMode,
                effectiveIsFlat = modeFlat,
                onSelect = { forcedMode = it }
            )

            Spacer(Modifier.height(12.dp))

            // HIDDEN measuring text to get the width for the maximal possible angle string (-180.0°).
            // We use alpha=0f so it does not appear; measure once and set degreesWidthDp.
            Text(
                text = "-180.0°",
                fontSize = 42.sp,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = 42.sp),
                modifier = Modifier
                    .alpha(0f)
                    .onGloballyPositioned { coords ->
                        val w = coords.size.width
                        degreesWidthDp = with(density) { w.toDp() }
                    }
            )

            // Big numeric card for degrees — fixed width based on measured maximal string
            val cardPaddingHorizontal = 24.dp
            val fixedCardModifier =
                if (degreesWidthDp > 0.dp) Modifier
                    .width(degreesWidthDp + cardPaddingHorizontal)
                    .align(Alignment.CenterHorizontally)
                else Modifier.align(Alignment.CenterHorizontally)

            Card(
                modifier = fixedCardModifier,
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .fillMaxWidth(), // ensure column fills card width
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val label = if (modeFlat) "Tilt" else "Angle"
                    val valueDouble = if (modeFlat) tiltFlat else angleEdge
                    val display = String.format(Locale.getDefault(), "%.1f", valueDouble)

                    Text(
                        label,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "$display°",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = 42.sp),
                        color = if (abs(valueDouble) <= 3.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // push controls to bottom
            Spacer(Modifier.weight(1f))

            // Bottom controls: Calibrate / Reset pinned to bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        coroutine.launch {
                            if (modeFlat) {
                                ds.saveLevelOffsetFlat(offsetFlat + tiltFlat)
                                offsetFlat = ds.getLevelOffsetFlat()
                                snackbarHostState.showSnackbar("Flat calibrated")
                            } else {
                                ds.saveLevelOffsetEdge(offsetEdge + angleEdge)
                                offsetEdge = ds.getLevelOffsetEdge()
                                snackbarHostState.showSnackbar("Edge calibrated")
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .padding(end = 8.dp)
                ) {
                    Text("Calibrate", fontSize = 16.sp)
                }

                OutlinedButton(
                    onClick = {
                        coroutine.launch {
                            if (modeFlat) {
                                ds.saveLevelOffsetFlat(0.0)
                                offsetFlat = 0.0
                                snackbarHostState.showSnackbar("Flat offset reset")
                            } else {
                                ds.saveLevelOffsetEdge(0.0)
                                offsetEdge = 0.0
                                snackbarHostState.showSnackbar("Edge offset reset")
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .padding(start = 8.dp)
                ) {
                    Text("Reset", fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Tip: use Calibrate on a known level surface to improve accuracy.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }
}

/** Row with three buttons: Auto / Edge / Flat. */
@Composable
private fun ModeButtonsRow(forcedMode: Boolean?, effectiveIsFlat: Boolean, onSelect: (Boolean?) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        val autoSelected = forcedMode == null
        val flatSelected = forcedMode == true
        val edgeSelected = forcedMode == false

        val btnShape = RoundedCornerShape(8.dp)
        Button(
            onClick = { onSelect(null) },
            shape = btnShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (autoSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            Text("Auto", color = if (autoSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { onSelect(false) },
            shape = btnShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (edgeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            Text("Edge", color = if (edgeSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { onSelect(true) },
            shape = btnShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (flatSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            Text("Flat", color = if (flatSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        }
    }
}

/** Edge-mode animated bubble */
@Composable
private fun BubbleViewEdgeAnimated(angle: Double) {
    val clamped = (angle / 45.0).toFloat().coerceIn(-1f, 1f)
    val animPx by animateFloatAsState(targetValue = clamped, animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))

    val absAngle = abs(angle)
    val neutral = absAngle < 3.0
    val bg = if (neutral) Color(0xFFDFFFE6) else Color(0xFFFFEBEE)

    Box(modifier = Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(1f)
                .background(brush = Brush.radialGradient(listOf(bg, Color.Transparent)), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize(0.92f)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = size.minDimension / 2f
                val maxOffset = radius * 0.55f
                val px = (animPx * maxOffset)
                val bubbleRadius = size.minDimension / 8f

                // subtle shadow / outer ring
                drawCircle(color = Color(0x22000000), radius = bubbleRadius + 6f, center = Offset(cx + px + 2f, cy + 2f))
                // thin stroke ring
                drawCircle(color = Color(0x22000000), radius = bubbleRadius + 2f, style = Stroke(width = 4f), center = Offset(cx + px, cy))
                // main bubble
                drawCircle(color = Color.White, radius = bubbleRadius, center = Offset(cx + px, cy))
                // glossy highlight
                drawCircle(color = Color(0x66FFFFFF), radius = bubbleRadius / 3f, center = Offset(cx + px - bubbleRadius / 3f, cy - bubbleRadius / 3f))
                // center marker
                drawCircle(color = Color.DarkGray, radius = 6f, center = Offset(cx, cy))
            }
        }
    }
}

/** Flat-mode animated bubble — с более чётким внешним видом. */
@Composable
private fun BubbleViewFlatAnimated(bx: Float, by: Float, tilt: Double) {
    val animPx by animateFloatAsState(targetValue = bx, animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
    val animPy by animateFloatAsState(targetValue = by, animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))

    val inGreen = abs(tilt) < 3.0
    val bg = if (inGreen) Color(0xFFDFFFE6) else Color(0xFFFFEBEE)

    Box(modifier = Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(1f)
                .background(brush = Brush.radialGradient(listOf(bg, Color.Transparent)), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize(0.95f)) {
                val radius = size.minDimension / 2f
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val maxOffset = radius * 0.62f
                val px = (animPx * maxOffset).coerceIn(-maxOffset, maxOffset)
                val py = (animPy * maxOffset).coerceIn(-maxOffset, maxOffset)
                val bubbleRadius = size.minDimension / 12f

                // shadow / outer ring to make bubble clear
                drawCircle(color = Color(0x22000000), radius = bubbleRadius + 6f, center = Offset(centerX + px + 2f, centerY + py + 2f))
                drawCircle(color = Color(0x22000000), radius = bubbleRadius + 2f, style = Stroke(width = 4f), center = Offset(centerX + px, centerY + py))
                drawCircle(color = Color.White, radius = bubbleRadius, center = Offset(centerX + px, centerY + py))
                drawCircle(color = Color(0x66FFFFFF), radius = bubbleRadius / 3f, center = Offset(centerX + px - bubbleRadius / 3f, centerY + py - bubbleRadius / 3f))

                // center marker
                drawCircle(color = Color.DarkGray, radius = 6f, center = Offset(centerX, centerY))
            }
        }
    }
}
