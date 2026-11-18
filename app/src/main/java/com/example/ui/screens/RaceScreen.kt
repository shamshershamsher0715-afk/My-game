package com.example.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Surface as AndroidSurface
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.audio.EngineSoundSynthesizer
import com.example.data.BikeSpec
import com.example.data.ControlSettings
import com.example.data.RaceEndReason
import com.example.data.RaceResult
import com.example.data.TrackMode
import com.example.engine.HapticPulse
import com.example.engine.HudSnapshot
import com.example.engine.RaceInput
import com.example.engine.RacingEngine
import com.example.engine.RealisticRoadRenderer
import com.example.ui.theme.ApexCrimson
import com.example.ui.theme.CarbonObsidian
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.PitSlate
import com.example.ui.theme.SectorGreen
import com.example.ui.theme.ShiftAmber
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TitaniumSilver
import java.util.Locale
import kotlin.math.abs

@Composable
fun RaceScreen(
    bike: BikeSpec,
    trackMode: TrackMode,
    settings: ControlSettings,
    onUpdateSettings: (ControlSettings) -> Unit,
    onRaceComplete: suspend (RaceResult) -> RaceResult,
    onExitToGarage: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var restartKey by remember { mutableIntStateOf(0) }
    val engine = remember(bike.id, trackMode, restartKey) { RacingEngine(bike, trackMode) }
    val renderer = remember { RealisticRoadRenderer() }
    val skyResId = if (trackMode == TrackMode.NIGHT_CIRCUIT) R.drawable.img_sky_night else R.drawable.img_sky_sunset
    val skyBitmap = ImageBitmap.imageResource(id = skyResId)

    var isPaused by remember { mutableStateOf(false) }
    var showSettingsModal by remember { mutableStateOf(false) }
    var finalResult by remember(restartKey) { mutableStateOf<RaceResult?>(null) }
    var hud by remember(restartKey) { mutableStateOf(engine.buildHud()) }
    var frameTick by remember { mutableLongStateOf(0L) }

    // Multi-touch input flags
    var steerLeftPressed by remember { mutableStateOf(false) }
    var steerRightPressed by remember { mutableStateOf(false) }
    var throttlePressed by remember { mutableStateOf(false) }
    var brakePressed by remember { mutableStateOf(false) }
    var boostPressed by remember { mutableStateOf(false) }
    var tiltSteerAxis by remember { mutableFloatStateOf(0f) }

    val soundSynth = remember { EngineSoundSynthesizer() }

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    // Lifecycle & Audio setup
    DisposableEffect(lifecycleOwner) {
        soundSynth.start()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                isPaused = true
                soundSynth.update(0.15f, 0f, boosting = false, activeAndEnabled = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            soundSynth.release()
        }
    }

    // Tilt steering sensor registration
    DisposableEffect(settings.tiltSteerEnabled, isPaused, finalResult) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

        if (settings.tiltSteerEnabled && accelSensor != null && !isPaused && finalResult == null) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    @Suppress("DEPRECATION")
                    val rot = windowManager?.defaultDisplay?.rotation ?: AndroidSurface.ROTATION_0
                    val rawAxis = when (rot) {
                        AndroidSurface.ROTATION_90 -> event.values[1]
                        AndroidSurface.ROTATION_270 -> -event.values[1]
                        AndroidSurface.ROTATION_180 -> event.values[0]
                        else -> -event.values[0]
                    }
                    val deadzoned = if (abs(rawAxis) < 0.45f) 0f else rawAxis
                    val norm = (deadzoned / 4.2f).coerceIn(-1f, 1f)
                    tiltSteerAxis += (norm - tiltSteerAxis) * 0.35f
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_GAME)
            onDispose {
                sensorManager.unregisterListener(listener)
                tiltSteerAxis = 0f
            }
        } else {
            tiltSteerAxis = 0f
            onDispose { }
        }
    }

    // Back button pauses mid-race rather than dropping directly out
    BackHandler(enabled = finalResult == null) {
        if (showSettingsModal) {
            showSettingsModal = false
        } else {
            isPaused = !isPaused
        }
    }

    // 60 FPS Main Racing Loop
    LaunchedEffect(engine, isPaused, finalResult == null) {
        var lastNanos = 0L
        while (finalResult == null && !isPaused) {
            withFrameNanos { frameNanos ->
                if (lastNanos == 0L) {
                    lastNanos = frameNanos
                }
                val dt = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastNanos = frameNanos

                val effectiveThrottle = throttlePressed || (settings.autoThrottleEnabled && !brakePressed)
                val input = RaceInput(
                    steerLeft = steerLeftPressed,
                    steerRight = steerRightPressed,
                    tiltAxis = if (settings.tiltSteerEnabled) tiltSteerAxis else 0f,
                    throttle = effectiveThrottle,
                    brake = brakePressed,
                    boost = boostPressed
                )

                val nextHud = engine.step(dt, input)
                hud = nextHud
                frameTick++

                // Sound synthesis
                soundSynth.update(
                    rpmNormalized = nextHud.rpmNormalized,
                    speedNormalized = nextHud.speedKmh / 300f,
                    boosting = nextHud.isBoosting,
                    activeAndEnabled = settings.soundEnabled && !isPaused && finalResult == null
                )

                // Haptics
                if (settings.hapticsEnabled && engine.pendingHaptic != HapticPulse.NONE) {
                    triggerHaptic(vibrator, engine.pendingHaptic)
                }

                // Check race completion
                val rawEnd = engine.finishedResult
                if (rawEnd != null && finalResult == null) {
                    soundSynth.update(0.15f, 0f, boosting = false, activeAndEnabled = false)
                    finalResult = rawEnd
                }
            }
        }
        soundSynth.update(0.15f, 0f, boosting = false, activeAndEnabled = false)
    }

    // Persist record when race ends
    LaunchedEffect(finalResult?.reason, finalResult?.score) {
        val current = finalResult ?: return@LaunchedEffect
        if (!current.isNewScoreRecord && !current.isNewLapRecord) {
            val updated = onRaceComplete(current)
            finalResult = updated
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonObsidian)
    ) {
        // 1. 60 FPS Pseudo-3D Realistic Road & Superbike Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tick = frameTick
            with(renderer) {
                renderFrame(
                    engine = engine,
                    skyBitmap = skyBitmap,
                    frameTicks = tick
                )
            }
        }

        // 2. Motorsport HUD Overlay
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            val isWide = maxWidth >= 660.dp

            // Top Telemetry Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                TopTelemetryCluster(trackMode = trackMode, hud = hud)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hud.isSlipstreaming) {
                        StatusPill(text = "SLIPSTREAM", color = SectorGreen)
                    }
                    if (hud.speedKmh >= 295) {
                        StatusPill(text = "MAX 300", color = ShiftAmber)
                    }

                    IconButton(
                        onClick = { isPaused = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(CarbonObsidian.copy(alpha = 0.72f))
                            .border(1.dp, HairlineBorder, CircleShape)
                            .testTag("pause_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = stringResource(R.string.control_pause),
                            tint = TitaniumSilver
                        )
                    }
                }
            }

            // Center Countdown or Callout Banner
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 68.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (hud.isCountdown && finalResult == null) {
                    Surface(
                        color = CarbonObsidian.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(2.dp, ApexCrimson)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "LIGHTS OUT IN",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedSteel
                            )
                            Text(
                                text = "${hud.countdownValue}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 52.sp,
                                color = ApexCrimson
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = hud.calloutText != null && !hud.isCountdown,
                    enter = fadeIn() + scaleIn(initialScale = 0.88f),
                    exit = fadeOut()
                ) {
                    hud.calloutText?.let { msg ->
                        Surface(
                            color = CarbonObsidian.copy(alpha = 0.82f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, hud.calloutColor)
                        ) {
                            Text(
                                text = msg,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = hud.calloutColor,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Layer: Digital Tachometer/Speedometer + Ergonomic Touch Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Speedometer Cluster positioned cleanly above thumb pads (or centered on wide screens)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isWide) Arrangement.Center else Arrangement.End
                ) {
                    SuperbikeDashCluster(hud = hud)
                }

                // Multi-Touch Control Deck
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Left: Steering Pads (with tilt indicator if enabled)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TouchHoldPad(
                            modifier = Modifier
                                .size(width = 78.dp, height = 68.dp)
                                .testTag("steer_left_btn"),
                            active = steerLeftPressed,
                            activeColor = TelemetryCyan,
                            onPressedChange = { steerLeftPressed = it }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = stringResource(R.string.control_steer_left),
                                tint = TitaniumSilver,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        TouchHoldPad(
                            modifier = Modifier
                                .size(width = 78.dp, height = 68.dp)
                                .testTag("steer_right_btn"),
                            active = steerRightPressed,
                            activeColor = TelemetryCyan,
                            onPressedChange = { steerRightPressed = it }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.control_steer_right),
                                tint = TitaniumSilver,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    // Right: Aero 300 Boost + Brake + Throttle
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            TouchHoldPad(
                                modifier = Modifier
                                    .size(width = 74.dp, height = 48.dp)
                                    .testTag("boost_btn"),
                                active = boostPressed,
                                activeColor = TelemetryCyan,
                                onPressedChange = { boostPressed = it }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = stringResource(R.string.control_boost),
                                        tint = if (hud.aeroCharge > 0.15f) TelemetryCyan else MutedSteel,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "300",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (hud.aeroCharge > 0.15f) TelemetryCyan else MutedSteel
                                    )
                                }
                            }

                            TouchHoldPad(
                                modifier = Modifier
                                    .size(width = 74.dp, height = 56.dp)
                                    .testTag("brake_btn"),
                                active = brakePressed,
                                activeColor = ApexCrimson,
                                onPressedChange = { brakePressed = it }
                            ) {
                                Text(
                                    text = "BRAKE",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (brakePressed) Color.White else ApexCrimson
                                )
                            }
                        }

                        TouchHoldPad(
                            modifier = Modifier
                                .size(width = 86.dp, height = 112.dp)
                                .testTag("throttle_btn"),
                            active = throttlePressed || settings.autoThrottleEnabled,
                            activeColor = SectorGreen,
                            onPressedChange = { throttlePressed = it }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (settings.autoThrottleEnabled) "AUTO" else "GAS",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "300",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = SectorGreen
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Pause Overlay
        if (isPaused && finalResult == null) {
            PauseOverlay(
                onResume = { isPaused = false },
                onRestart = {
                    isPaused = false
                    restartKey++
                },
                onOpenSettings = { showSettingsModal = true },
                onExitGarage = onExitToGarage
            )
        }

        // 4. Finish / Wipeout Telemetry Summary Overlay
        finalResult?.let { result ->
            RaceSummaryOverlay(
                result = result,
                onRestart = {
                    finalResult = null
                    restartKey++
                },
                onExitGarage = onExitToGarage
            )
        }

        if (showSettingsModal) {
            SettingsDialog(
                settings = settings,
                onUpdate = onUpdateSettings,
                onDismiss = { showSettingsModal = false }
            )
        }
    }
}

@Composable
private fun TopTelemetryCluster(trackMode: TrackMode, hud: HudSnapshot) {
    Surface(
        color = CarbonObsidian.copy(alpha = 0.72f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (trackMode == TrackMode.NIGHT_CIRCUIT) {
                TelemetryValue("POS", "${hud.position}/${hud.totalRacers}", ShiftAmber)
                TelemetryValue("LAP", "${hud.currentLap}/${hud.totalLaps}", TelemetryCyan)
                TelemetryValue("TIME", formatLapTime(hud.lapTimeSec), TitaniumSilver)
            } else {
                TelemetryValue("SCORE", "${hud.score}", ShiftAmber)
                TelemetryValue("DIST", String.format(Locale.US, "%.2f KM", hud.distanceKm), TelemetryCyan)
                if (hud.nearMissCombo > 1) {
                    TelemetryValue("COMBO", "x${hud.nearMissCombo}", SectorGreen)
                }
            }
        }
    }
}

@Composable
private fun TelemetryValue(label: String, value: String, color: Color) {
    Column {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 9.sp,
            color = MutedSteel
        )
        Text(
            text = value,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = color
        )
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color)
    ) {
        Text(
            text = text,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SuperbikeDashCluster(hud: HudSnapshot) {
    val speedColor = when {
        hud.speedKmh >= 295 -> ApexCrimson
        hud.speedKmh >= 250 -> ShiftAmber
        hud.speedKmh >= 180 -> TelemetryCyan
        else -> TitaniumSilver
    }

    Surface(
        color = CarbonObsidian.copy(alpha = 0.78f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (hud.shiftFlash) ShiftAmber else HairlineBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Segmented RPM Arc + Gear Badge
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 6.dp.toPx()
                    drawArc(
                        color = Color(0xFF26303E),
                        startAngle = 140f,
                        sweepAngle = 260f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                        topLeft = Offset(stroke * 0.5f, stroke * 0.5f),
                        size = Size(size.width - stroke, size.height - stroke)
                    )
                    val rpmColor = when {
                        hud.rpmNormalized > 0.88f -> ApexCrimson
                        hud.rpmNormalized > 0.68f -> ShiftAmber
                        else -> SectorGreen
                    }
                    drawArc(
                        color = rpmColor,
                        startAngle = 140f,
                        sweepAngle = 260f * hud.rpmNormalized.coerceIn(0.05f, 1f),
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                        topLeft = Offset(stroke * 0.5f, stroke * 0.5f),
                        size = Size(size.width - stroke, size.height - stroke)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "G${hud.gear}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = if (hud.shiftFlash) ShiftAmber else TitaniumSilver
                    )
                    Text(
                        text = "${hud.rpm / 1000}K",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.sp,
                        color = MutedSteel
                    )
                }
            }

            // Digital 0..300 KM/H Speedometer + Aero Boost Bar
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%03d", hud.speedKmh),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        lineHeight = 32.sp,
                        color = speedColor,
                        modifier = Modifier.testTag("speed_readout")
                    )
                    Text(
                        text = stringResource(R.string.label_kmh),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = MutedSteel,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                LinearProgressIndicator(
                    progress = { hud.aeroCharge },
                    modifier = Modifier
                        .width(94.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (hud.isBoosting) ApexCrimson else TelemetryCyan,
                    trackColor = Color(0xFF26303E)
                )
            }
        }
    }
}

@Composable
private fun TouchHoldPad(
    modifier: Modifier = Modifier,
    active: Boolean,
    activeColor: Color,
    onPressedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val bg = if (active) activeColor.copy(alpha = 0.36f) else CarbonObsidian.copy(alpha = 0.66f)
    val border = if (active) activeColor else HairlineBorder

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(if (active) 2.dp else 1.dp, border, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPressedChange(true)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        onPressedChange(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun PauseOverlay(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onOpenSettings: () -> Unit,
    onExitGarage: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBB090C10)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth(),
            color = PitSlate,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, HairlineBorder)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "RACE PAUSED",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TitaniumSilver
                )
                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("resume_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ApexCrimson)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_resume))
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = TelemetryCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("CONTROLS & AUDIO", color = TitaniumSilver)
                }
                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = ShiftAmber)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_restart), color = TitaniumSilver)
                }
                OutlinedButton(
                    onClick = onExitGarage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(stringResource(R.string.btn_exit_menu), color = MutedSteel)
                }
            }
        }
    }
}

@Composable
private fun RaceSummaryOverlay(
    result: RaceResult,
    onRestart: () -> Unit,
    onExitGarage: () -> Unit
) {
    val isFinish = result.reason == RaceEndReason.RACE_FINISHED
    val accent = if (isFinish) SectorGreen else ApexCrimson

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xCC090C10), Color(0xEC090C10))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(22.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            color = PitSlate,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.5.dp, accent)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = if (isFinish) "CHECKERED FLAG!" else "HIGH-SPEED WIPEOUT",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TitaniumSilver
                    )
                }

                Text(
                    text = "${result.bikeName} • ${result.trackMode.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSteel
                )

                if (result.isNewScoreRecord || result.isNewLapRecord) {
                    StatusPill(
                        text = if (result.isNewLapRecord) "NEW LAP RECORD!" else "NEW HIGH SCORE!",
                        color = ShiftAmber
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CarbonObsidian.copy(alpha = 0.7f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryRow("TOP SPEED", "${result.topSpeedKmh} KM/H", TelemetryCyan)
                    SummaryRow("DISTANCE", String.format(Locale.US, "%.2f KM", result.distanceKm), TitaniumSilver)
                    if (result.trackMode == TrackMode.NIGHT_CIRCUIT) {
                        SummaryRow("FINISH POS", "${result.finishPosition} / 12", ShiftAmber)
                        SummaryRow("BEST LAP", formatLapTime(result.bestLapSec), SectorGreen)
                    } else {
                        SummaryRow("NEAR MISSES", "${result.nearMisses}", SectorGreen)
                        SummaryRow("SCORE", "${result.score}", ShiftAmber)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onExitGarage,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("menu_button")
                    ) {
                        Text(stringResource(R.string.btn_exit_menu), color = TitaniumSilver)
                    }
                    Button(
                        onClick = onRestart,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("restart_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ApexCrimson)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_restart))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 12.sp,
            color = MutedSteel
        )
        Text(
            text = value,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = color
        )
    }
}

private fun formatLapTime(seconds: Float): String {
    val totalMs = (seconds * 1000f).toInt().coerceAtLeast(0)
    val mins = totalMs / 60000
    val secs = (totalMs % 60000) / 1000
    val tenths = (totalMs % 1000) / 10
    return String.format(Locale.US, "%d:%02d.%02d", mins, secs, tenths)
}

private fun triggerHaptic(vibrator: Vibrator?, pulse: HapticPulse) {
    if (vibrator == null || !vibrator.hasVibrator()) return
    val durationMs = when (pulse) {
        HapticPulse.UPSHIFT -> 18L
        HapticPulse.CURB_RUMBLE -> 14L
        HapticPulse.NEAR_MISS -> 28L
        HapticPulse.HIT_300 -> 45L
        HapticPulse.CRASH -> 140L
        HapticPulse.NONE -> return
    }
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amp = when (pulse) {
                HapticPulse.CRASH -> 255
                HapticPulse.HIT_300 -> 200
                HapticPulse.NEAR_MISS -> 150
                else -> 80
            }
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amp))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    } catch (_: Throwable) {
    }
}
