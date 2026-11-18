package com.example.engine

import androidx.compose.ui.graphics.Color
import com.example.data.BikeSpec
import com.example.data.RaceEndReason
import com.example.data.RaceResult
import com.example.data.TrackMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class VehicleKind {
    SPORTS_CAR,
    SUV,
    RIVAL_BIKE
}

enum class HapticPulse {
    NONE,
    UPSHIFT,
    NEAR_MISS,
    HIT_300,
    CURB_RUMBLE,
    CRASH
}

data class RoadSegment(
    val index: Int,
    val zStart: Float,
    val zEnd: Float,
    val yStart: Float,
    val yEnd: Float,
    val curve: Float,
    val isAltBand: Boolean,
    val hasArch: Boolean,
    val hasLampLeft: Boolean,
    val hasLampRight: Boolean,
    val treeLeftKind: Int,   // 0 = none, 1 = pine/cypress, 2 = billboard/post
    val treeRightKind: Int
)

data class TrafficEntity(
    val id: Int,
    val kind: VehicleKind,
    var laneX: Float,           // -0.78f .. +0.78f
    var targetLaneX: Float,
    var totalZ: Float,          // continuous forward world Z
    var speedKmh: Float,
    val bodyColor: Color,
    val accentColor: Color,
    var leanDeg: Float = 0f,
    var passedByPlayer: Boolean = false
)

data class SparkParticle(
    var xNorm: Float,
    var yNorm: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val color: Color
)

data class RaceInput(
    val steerLeft: Boolean = false,
    val steerRight: Boolean = false,
    val tiltAxis: Float = 0f,       // -1f (left) .. +1f (right)
    val throttle: Boolean = false,
    val brake: Boolean = false,
    val boost: Boolean = false
)

data class HudSnapshot(
    val speedKmh: Int = 0,
    val rpm: Int = 2400,
    val rpmNormalized: Float = 0.1f,
    val gear: Int = 1,
    val aeroCharge: Float = 1f,
    val isBoosting: Boolean = false,
    val isSlipstreaming: Boolean = false,
    val shiftFlash: Boolean = false,
    val leanDeg: Float = 0f,
    val score: Int = 0,
    val distanceKm: Float = 0f,
    val nearMissCount: Int = 0,
    val nearMissCombo: Int = 0,
    val currentLap: Int = 1,
    val totalLaps: Int = 3,
    val position: Int = 1,
    val totalRacers: Int = 12,
    val lapTimeSec: Float = 0f,
    val topSpeedReached: Int = 0,
    val calloutText: String? = null,
    val calloutColor: Color = Color.White,
    val isCountdown: Boolean = true,
    val countdownValue: Int = 3
)

class RacingEngine(
    val bike: BikeSpec,
    val trackMode: TrackMode
) {
    companion object {
        const val SEGMENT_LENGTH = 200f
        const val SEGMENT_COUNT = 1200
        const val TRACK_LENGTH = SEGMENT_COUNT * SEGMENT_LENGTH
        const val MAX_SPEED_KMH = 300f
        const val WORLD_UNITS_PER_KMH = 22.0f
        const val ROAD_HALF_WIDTH = 1000f
        const val DRAW_DISTANCE = 125
    }

    val segments: List<RoadSegment> = buildTrack(trackMode)
    val entities: MutableList<TrafficEntity> = mutableListOf()
    val particles: MutableList<SparkParticle> = mutableListOf()

    var playerZ: Float = 0f
        private set
    var playerTotalZ: Float = 0f
        private set
    var playerX: Float = 0f         // -1.0..1.0 = asphalt edges, +/- 1.32 = barrier
        private set
    var speedKmh: Float = 0f
        private set
    var leanDeg: Float = 0f
        private set
    var aeroTuck: Float = 0f
        private set
    var aeroCharge: Float = 1.0f
        private set
    var isBraking: Boolean = false
        private set
    var isBoosting: Boolean = false
        private set
    var isSlipstreaming: Boolean = false
        private set
    var cameraShake: Float = 0f
        private set
    var skyScrollOffset: Float = 0f
        private set

    private var gear: Int = 1
    private var rpm: Int = 2400
    private var rpmNorm: Float = 0.1f
    private var shiftFlashTimer: Float = 0f
    private var hit300Announced: Boolean = false

    private var score: Int = 0
    private var distanceKm: Float = 0f
    private var nearMissCount: Int = 0
    private var nearMissCombo: Int = 0
    private var comboDecayTimer: Float = 0f
    private var topSpeedReached: Int = 0

    private var currentLap: Int = 1
    private var raceTimeSec: Float = 0f
    private var currentLapTimeSec: Float = 0f
    private var bestLapSec: Float = 0f
    private var position: Int = if (trackMode == TrackMode.NIGHT_CIRCUIT) 12 else 1

    private var countdownTimer: Float = 2.6f
    private var invulnTimer: Float = 0f
    private var calloutText: String? = null
    private var calloutColor: Color = Color.White
    private var calloutTimer: Float = 0f

    var finishedResult: RaceResult? = null
        private set
    var pendingHaptic: HapticPulse = HapticPulse.NONE
        private set

    private val random = Random(System.currentTimeMillis())

    init {
        spawnInitialEntities()
    }

    private fun buildTrack(mode: TrackMode): List<RoadSegment> {
        val list = ArrayList<RoadSegment>(SEGMENT_COUNT)
        var currentY = 0f
        val baseY = FloatArray(SEGMENT_COUNT + 1)

        // Build elevation profile (rolling alpine hills or sweeping circuit dips)
        for (i in 0..SEGMENT_COUNT) {
            val t = i.toFloat() / SEGMENT_COUNT
            val hill = if (mode == TrackMode.SUNSET_HIGHWAY) {
                (sin(t * 2.0 * PI * 3.0) * 620.0 + cos(t * 2.0 * PI * 7.0) * 240.0).toFloat()
            } else {
                (sin(t * 2.0 * PI * 4.0) * 420.0 + sin(t * 2.0 * PI * 9.0) * 160.0).toFloat()
            }
            // Smooth back to 0 at loop boundary
            val blend = sin(t * PI).toFloat()
            baseY[i] = hill * blend
        }

        for (i in 0 until SEGMENT_COUNT) {
            val curve = computeTrackCurve(i, mode)
            val isAlt = (i / 3) % 2 == 1
            val hasArch = (i == 10 || i == SEGMENT_COUNT / 2 || i == SEGMENT_COUNT - 15)
            val lampStep = if (mode == TrackMode.NIGHT_CIRCUIT) 8 else 14
            val hasLamp = i % lampStep == 0
            val treeStep = if (mode == TrackMode.SUNSET_HIGHWAY) 5 else 9
            val treeL = if (i % treeStep == 0) 1 else if (i % 45 == 12) 2 else 0
            val treeR = if ((i + 2) % treeStep == 0) 1 else if (i % 55 == 25) 2 else 0

            list.add(
                RoadSegment(
                    index = i,
                    zStart = i * SEGMENT_LENGTH,
                    zEnd = (i + 1) * SEGMENT_LENGTH,
                    yStart = baseY[i],
                    yEnd = baseY[i + 1],
                    curve = curve,
                    isAltBand = isAlt,
                    hasArch = hasArch,
                    hasLampLeft = hasLamp,
                    hasLampRight = hasLamp,
                    treeLeftKind = treeL,
                    treeRightKind = treeR
                )
            )
            currentY = baseY[i + 1]
        }
        return list
    }

    private fun computeTrackCurve(i: Int, mode: TrackMode): Float {
        return if (mode == TrackMode.SUNSET_HIGHWAY) {
            when (i) {
                in 0..85 -> 0f
                in 86..170 -> smoothCurve(i, 86, 170, 0.95f)
                in 171..250 -> 0f
                in 251..360 -> smoothCurve(i, 251, 360, -1.15f)
                in 361..450 -> 0f
                in 451..540 -> smoothCurve(i, 451, 540, 1.35f)
                in 541..620 -> smoothCurve(i, 541, 620, -1.25f)
                in 621..760 -> 0f // Long 300 km/h sprint straight
                in 761..880 -> smoothCurve(i, 761, 880, 0.85f)
                in 881..960 -> 0f
                in 961..1080 -> smoothCurve(i, 961, 1080, -0.95f)
                else -> 0f
            }
        } else {
            // Midnight GP Circuit: crisp technical corners + long main straight
            when (i) {
                in 0..100 -> 0f
                in 101..195 -> smoothCurve(i, 101, 195, 1.45f)
                in 196..255 -> 0f
                in 256..345 -> smoothCurve(i, 256, 345, -1.6f)
                in 346..430 -> smoothCurve(i, 346, 430, 1.25f)
                in 431..530 -> 0f
                in 531..650 -> smoothCurve(i, 531, 650, -1.5f)
                in 651..760 -> smoothCurve(i, 651, 760, 1.1f)
                in 761..900 -> 0f // Floodlit Back Straight for 300 km/h
                in 901..1020 -> smoothCurve(i, 901, 1020, -1.35f)
                in 1021..1110 -> smoothCurve(i, 1021, 1110, 0.9f)
                else -> 0f
            }
        }
    }

    private fun smoothCurve(i: Int, start: Int, end: Int, peak: Float): Float {
        val t = (i - start).toFloat() / (end - start).coerceAtLeast(1)
        return sin(t * PI).toFloat() * peak
    }

    private fun spawnInitialEntities() {
        entities.clear()
        val carPalette = listOf(
            Color(0xFFDC2626) to Color(0xFF1F2937),
            Color(0xFF2563EB) to Color(0xFF0F172A),
            Color(0xFFE2E8F0) to Color(0xFF334155),
            Color(0xFFF59E0B) to Color(0xFF1E293B),
            Color(0xFF10B981) to Color(0xFF111827),
            Color(0xFF6366F1) to Color(0xFF1E1B4B),
            Color(0xFF475569) to Color(0xFF0F172A)
        )

        if (trackMode == TrackMode.SUNSET_HIGHWAY) {
            val lanes = listOf(-0.66f, -0.22f, 0.22f, 0.66f)
            for (i in 0 until 26) {
                val z = 3200f + i * 3200f + random.nextFloat() * 1400f
                val lane = lanes[i % lanes.size]
                val kind = when {
                    i % 5 == 0 -> VehicleKind.RIVAL_BIKE
                    i % 3 == 0 -> VehicleKind.SUV
                    else -> VehicleKind.SPORTS_CAR
                }
                val (body, accent) = carPalette[i % carPalette.size]
                val vSpeed = when (kind) {
                    VehicleKind.RIVAL_BIKE -> 145f + random.nextFloat() * 65f
                    VehicleKind.SUV -> 92f + random.nextFloat() * 38f
                    VehicleKind.SPORTS_CAR -> 115f + random.nextFloat() * 55f
                }
                entities.add(
                    TrafficEntity(
                        id = i,
                        kind = kind,
                        laneX = lane,
                        targetLaneX = lane,
                        totalZ = z,
                        speedKmh = vSpeed,
                        bodyColor = body,
                        accentColor = accent
                    )
                )
            }
        } else {
            // 11 rival superbikes staggered ahead of grid
            val rivalColors = listOf(
                Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF22C55E),
                Color(0xFFF97316), Color(0xFFA855F7), Color(0xFF06B6D4),
                Color(0xFFEAB308), Color(0xFFEC4899), Color(0xFF94A3B8),
                Color(0xFF14B8A6), Color(0xFFF43F5E)
            )
            for (i in 0 until 11) {
                val row = i / 2
                val col = if (i % 2 == 0) -0.38f else 0.38f
                val z = 1400f + row * 1200f
                val baseSpeed = 235f + (i * 4.8f) // fastest rival ~283 km/h, player can reach 300 km/h!
                entities.add(
                    TrafficEntity(
                        id = i,
                        kind = VehicleKind.RIVAL_BIKE,
                        laneX = col,
                        targetLaneX = col,
                        totalZ = z,
                        speedKmh = baseSpeed,
                        bodyColor = rivalColors[i % rivalColors.size],
                        accentColor = Color(0xFF0F172A)
                    )
                )
            }
        }
    }

    fun step(dtRaw: Float, input: RaceInput): HudSnapshot {
        val dt = dtRaw.coerceIn(0.001f, 0.05f)
        pendingHaptic = HapticPulse.NONE

        if (finishedResult != null) {
            updateParticles(dt)
            return buildHud()
        }

        if (calloutTimer > 0f) {
            calloutTimer -= dt
            if (calloutTimer <= 0f) calloutText = null
        }
        if (shiftFlashTimer > 0f) shiftFlashTimer -= dt
        if (invulnTimer > 0f) invulnTimer -= dt
        if (comboDecayTimer > 0f) {
            comboDecayTimer -= dt
            if (comboDecayTimer <= 0f) nearMissCombo = 0
        }

        // Start lights countdown
        if (countdownTimer > 0f) {
            countdownTimer -= dt
            if (countdownTimer <= 0f) {
                triggerCallout("FULL THROTTLE • 300 KM/H", Color(0xFF00E5FF), 1.4f)
            }
            // Allow revving on the grid
            val revTarget = if (input.throttle) 0.72f else 0.16f
            rpmNorm += (revTarget - rpmNorm) * (dt * 6f)
            rpm = (2200 + rpmNorm * 12300).toInt()
            return buildHud()
        }

        raceTimeSec += dt
        currentLapTimeSec += dt

        val segIdx = segmentIndexAt(playerZ)
        val currentSeg = segments[segIdx]

        // Steering input (combine buttons + tilt)
        val btnSteer = (if (input.steerRight) 1f else 0f) - (if (input.steerLeft) 1f else 0f)
        val combinedSteer = (btnSteer + input.tiltAxis).coerceIn(-1.2f, 1.2f)

        // Aero boost availability
        val canBoost = input.boost && aeroCharge > 0.04f && speedKmh > 60f
        isBoosting = canBoost
        if (canBoost) {
            aeroCharge = (aeroCharge - dt * 0.34f).coerceAtLeast(0f)
        } else {
            val regen = if (isSlipstreaming) 0.28f else 0.07f
            aeroCharge = (aeroCharge + dt * regen).coerceAtMost(1f)
        }

        isBraking = input.brake && speedKmh > 5f

        // Realistic superbike longitudinal acceleration up to 300 km/h
        val accelFactor = 0.85f + bike.accelRating * 0.35f
        val brakeFactor = 0.85f + bike.brakingRating * 0.35f

        val accelRate = when {
            isBraking -> -155f * brakeFactor
            input.throttle -> {
                // Strong torque down low, smooth aerodynamic push from 260 -> 300 km/h
                val speedRatio = (speedKmh / MAX_SPEED_KMH).coerceIn(0f, 1f)
                val basePull = (62f - 42f * speedRatio * speedRatio) * accelFactor
                val boostBonus = if (isBoosting) 38f else 0f
                val draftBonus = if (isSlipstreaming) 18f else 0f
                basePull + boostBonus + draftBonus
            }
            else -> -16f - (speedKmh / MAX_SPEED_KMH) * 14f // Engine braking + aero drag
        }

        // Off-asphalt gravel drag (outside +/- 1.04)
        val offRoad = abs(playerX) > 1.04f
        val offRoadDrag = if (offRoad && speedKmh > 95f) -46f else 0f

        val hillSlope = (currentSeg.yEnd - currentSeg.yStart) / SEGMENT_LENGTH
        val gravityForce = -hillSlope * 18f

        val maxCap = bike.topSpeedKmh // 300 km/h!
        speedKmh = (speedKmh + (accelRate + offRoadDrag + gravityForce) * dt)
            .coerceIn(0f, maxCap)

        val roundedSpeed = speedKmh.toInt()
        if (roundedSpeed > topSpeedReached) {
            topSpeedReached = roundedSpeed
        }
        if (roundedSpeed >= 300 && !hit300Announced) {
            hit300Announced = true
            pendingHaptic = HapticPulse.HIT_300
            triggerCallout("300 KM/H TOP SPEED!", Color(0xFFFFB300), 1.8f)
        } else if (roundedSpeed < 270) {
            hit300Announced = false
        }

        // Gearbox & RPM calculation
        val prevGear = gear
        gear = when {
            speedKmh < 95f -> 1
            speedKmh < 148f -> 2
            speedKmh < 198f -> 3
            speedKmh < 242f -> 4
            speedKmh < 276f -> 5
            else -> 6
        }
        if (gear > prevGear) {
            shiftFlashTimer = 0.16f
            if (pendingHaptic == HapticPulse.NONE) pendingHaptic = HapticPulse.UPSHIFT
            spawnExhaustPop()
        }
        val (bandMin, bandMax) = when (gear) {
            1 -> 0f to 95f
            2 -> 80f to 148f
            3 -> 125f to 198f
            4 -> 170f to 242f
            5 -> 212f to 276f
            else -> 240f to 300f
        }
        val inGearRatio = ((speedKmh - bandMin) / (bandMax - bandMin)).coerceIn(0.08f, 1f)
        rpmNorm = if (speedKmh < 2f && input.throttle) 0.55f else (0.18f + inGearRatio * 0.82f)
        rpm = (2200 + rpmNorm * 12300).toInt().coerceIn(2200, 14500)

        // Aero tuck animation above 200 km/h
        val targetTuck = ((speedKmh - 190f) / 110f).coerceIn(0f, 1f)
        aeroTuck += (targetTuck - aeroTuck) * (dt * 5f)

        // Lean physics + steering
        val steerSensitivity = 1.38f + bike.handlingRating * 0.42f
        val speedSteerFactor = (speedKmh / 65f).coerceIn(0.15f, 1f)
        val centrifugalPush = currentSeg.curve * (speedKmh / MAX_SPEED_KMH) * (speedKmh / MAX_SPEED_KMH) * 1.15f

        playerX += (combinedSteer * steerSensitivity * speedSteerFactor - centrifugalPush) * dt

        // Guardrail scrape clamp
        if (abs(playerX) > 1.24f) {
            playerX = playerX.coerceIn(-1.24f, 1.24f)
            speedKmh = (speedKmh * (1f - dt * 0.8f)).coerceAtLeast(45f)
            cameraShake = 0.45f
            spawnBarrierSparks(playerX > 0f)
        } else if (abs(playerX) > 0.96f && speedKmh > 100f) {
            // Curb rumble
            cameraShake = (cameraShake + dt * 1.5f).coerceAtMost(0.28f)
            if (pendingHaptic == HapticPulse.NONE && random.nextFloat() < 0.25f) {
                pendingHaptic = HapticPulse.CURB_RUMBLE
            }
        } else {
            // Subtle high-speed aero vibration above 260 km/h
            val speedShake = if (speedKmh > 260f) ((speedKmh - 260f) / 40f) * 0.08f else 0f
            cameraShake = (cameraShake * 0.85f).coerceAtLeast(speedShake)
        }

        // Smooth visual lean angle (-46 .. +46 deg)
        val targetLean = (combinedSteer * 44f * speedSteerFactor)
            .coerceIn(-46f, 46f)
        leanDeg += (targetLean - leanDeg) * (dt * 10f)

        // Forward movement
        val worldDeltaZ = speedKmh * WORLD_UNITS_PER_KMH * dt
        val prevZ = playerZ
        playerZ = (playerZ + worldDeltaZ) % TRACK_LENGTH
        playerTotalZ += worldDeltaZ
        distanceKm += (speedKmh / 3600f) * dt
        skyScrollOffset += currentSeg.curve * (worldDeltaZ / SEGMENT_LENGTH) * 0.012f

        // Score accumulation from high speed
        if (speedKmh > 120f) {
            val speedMult = if (speedKmh >= 285f) 3 else if (speedKmh >= 220f) 2 else 1
            score += ((speedKmh * 0.14f) * speedMult * dt * 10f).toInt()
        }

        // Lap completion check
        if (playerZ < prevZ) {
            if (trackMode == TrackMode.NIGHT_CIRCUIT) {
                if (bestLapSec == 0f || currentLapTimeSec < bestLapSec) {
                    bestLapSec = currentLapTimeSec
                }
                if (currentLap >= trackMode.totalLaps) {
                    finishRace(RaceEndReason.RACE_FINISHED)
                    return buildHud()
                } else {
                    currentLap++
                    currentLapTimeSec = 0f
                    triggerCallout("LAP $currentLap / ${trackMode.totalLaps}", Color(0xFF00E5FF), 1.8f)
                }
            }
        }

        updateEntities(dt)
        updateParticles(dt)

        if (isBoosting && random.nextFloat() < 0.7f) {
            spawnBoostFlameParticle()
        }

        return buildHud()
    }

    private fun updateEntities(dt: Float) {
        isSlipstreaming = false
        var racersAhead = 0

        for (entity in entities) {
            // Move entity forward
            val entitySeg = segments[segmentIndexAt(entity.totalZ % TRACK_LENGTH)]
            if (entity.kind == VehicleKind.RIVAL_BIKE && trackMode == TrackMode.NIGHT_CIRCUIT) {
                // Slow slightly in sharp curves, follow racing apex
                val curvePenalty = abs(entitySeg.curve) * 28f
                val effSpeed = (entity.speedKmh - curvePenalty).coerceAtLeast(165f)
                entity.totalZ += effSpeed * WORLD_UNITS_PER_KMH * dt
                entity.leanDeg += ((entitySeg.curve * 26f) - entity.leanDeg) * (dt * 6f)
                if (entity.totalZ > playerTotalZ) racersAhead++
            } else {
                entity.totalZ += entity.speedKmh * WORLD_UNITS_PER_KMH * dt
                // Occasional lane change on highway
                if (random.nextFloat() < dt * 0.18f) {
                    val lanes = listOf(-0.66f, -0.22f, 0.22f, 0.66f)
                    entity.targetLaneX = lanes[random.nextInt(lanes.size)]
                }
                entity.laneX += (entity.targetLaneX - entity.laneX) * (dt * 1.8f)
            }

            // Wrap highway traffic ahead of player once far behind
            if (trackMode == TrackMode.SUNSET_HIGHWAY) {
                val relZ = entity.totalZ - playerTotalZ
                if (relZ < -1200f) {
                    val lanes = listOf(-0.66f, -0.22f, 0.22f, 0.66f)
                    val newLane = lanes[random.nextInt(lanes.size)]
                    entity.totalZ = playerTotalZ + 18000f + random.nextFloat() * 14000f
                    entity.laneX = newLane
                    entity.targetLaneX = newLane
                    entity.passedByPlayer = false
                }
            }

            // Check Slipstream, Near-Miss & Collision relative to player
            val relZ = entity.totalZ - playerTotalZ
            val lateralDist = abs(playerX - entity.laneX)
            val hitHalfWidth = when (entity.kind) {
                VehicleKind.RIVAL_BIKE -> 0.14f
                VehicleKind.SPORTS_CAR -> 0.22f
                VehicleKind.SUV -> 0.24f
            }

            // Slipstream draft zone behind vehicle
            if (relZ in 160f..1150f && lateralDist < hitHalfWidth + 0.10f && speedKmh > 140f) {
                isSlipstreaming = true
            }

            // Near-miss reward when overtaking cleanly at high speed
            if (trackMode == TrackMode.SUNSET_HIGHWAY &&
                !entity.passedByPlayer &&
                relZ < -30f && relZ > -360f
            ) {
                entity.passedByPlayer = true
                if (lateralDist < hitHalfWidth + 0.26f && speedKmh >= 165f) {
                    nearMissCount++
                    nearMissCombo = (nearMissCombo + 1).coerceAtMost(8)
                    comboDecayTimer = 3.5f
                    val bonus = 350 * nearMissCombo
                    score += bonus
                    aeroCharge = (aeroCharge + 0.18f).coerceAtMost(1f)
                    pendingHaptic = HapticPulse.NEAR_MISS
                    triggerCallout("NEAR MISS +$bonus (x$nearMissCombo)", Color(0xFF10B981), 1.1f)
                }
            }

            // Collision check
            if (invulnTimer <= 0f && abs(relZ) < 135f && lateralDist < hitHalfWidth) {
                if (trackMode == TrackMode.NIGHT_CIRCUIT) {
                    // Rival bump: realistic rub, sparks, speed drop, push apart
                    speedKmh = (speedKmh * 0.68f).coerceAtLeast(75f)
                    val pushSign = if (playerX >= entity.laneX) 1f else -1f
                    playerX = (playerX + pushSign * 0.16f).coerceIn(-1.15f, 1.15f)
                    entity.laneX = (entity.laneX - pushSign * 0.14f).coerceIn(-0.85f, 0.85f)
                    cameraShake = 0.65f
                    invulnTimer = 0.7f
                    pendingHaptic = HapticPulse.CURB_RUMBLE
                    spawnCrashSparks()
                } else {
                    if (speedKmh > 145f) {
                        // Dramatic Wipeout!
                        cameraShake = 1.0f
                        pendingHaptic = HapticPulse.CRASH
                        spawnCrashSparks()
                        finishRace(RaceEndReason.WIPED_OUT)
                        return
                    } else {
                        // Low-speed fender graze
                        speedKmh = (speedKmh * 0.5f).coerceAtLeast(35f)
                        val pushSign = if (playerX >= entity.laneX) 1f else -1f
                        playerX = (playerX + pushSign * 0.22f).coerceIn(-1.15f, 1.15f)
                        invulnTimer = 0.9f
                        cameraShake = 0.6f
                        spawnCrashSparks()
                    }
                }
            }
        }

        if (trackMode == TrackMode.NIGHT_CIRCUIT) {
            position = (racersAhead + 1).coerceIn(1, 12)
        }
    }

    private fun finishRace(reason: RaceEndReason) {
        if (finishedResult != null) return
        val finalBestLap = if (trackMode == TrackMode.NIGHT_CIRCUIT) {
            if (bestLapSec == 0f) currentLapTimeSec else bestLapSec
        } else 0f

        finishedResult = RaceResult(
            trackMode = trackMode,
            bikeName = bike.name,
            reason = reason,
            topSpeedKmh = topSpeedReached.coerceAtLeast(speedKmh.toInt()),
            distanceKm = distanceKm,
            score = score,
            nearMisses = nearMissCount,
            finishPosition = position,
            raceTimeSec = raceTimeSec,
            bestLapSec = finalBestLap,
            isNewScoreRecord = false,
            isNewLapRecord = false
        )
    }

    private fun triggerCallout(text: String, color: Color, duration: Float) {
        calloutText = text
        calloutColor = color
        calloutTimer = duration
    }

    private fun spawnBarrierSparks(onRight: Boolean) {
        val baseX = if (onRight) 0.58f else 0.42f
        for (i in 0 until 4) {
            particles.add(
                SparkParticle(
                    xNorm = baseX + (random.nextFloat() - 0.5f) * 0.04f,
                    yNorm = 0.82f + (random.nextFloat() - 0.5f) * 0.04f,
                    vx = (if (onRight) -1f else 1f) * (0.25f + random.nextFloat() * 0.4f),
                    vy = -0.25f - random.nextFloat() * 0.45f,
                    life = 0.28f,
                    maxLife = 0.28f,
                    color = if (i % 2 == 0) Color(0xFFFFB300) else Color(0xFFFF5722)
                )
            )
        }
    }

    private fun spawnCrashSparks() {
        for (i in 0 until 22) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val spd = 0.2f + random.nextFloat() * 0.7f
            particles.add(
                SparkParticle(
                    xNorm = 0.5f,
                    yNorm = 0.78f,
                    vx = cos(angle) * spd,
                    vy = sin(angle) * spd - 0.2f,
                    life = 0.45f + random.nextFloat() * 0.25f,
                    maxLife = 0.7f,
                    color = if (i % 3 == 0) Color.White else if (i % 2 == 0) Color(0xFFFFB300) else Color(0xFFE10600)
                )
            )
        }
    }

    private fun spawnExhaustPop() {
        for (i in 0 until 5) {
            particles.add(
                SparkParticle(
                    xNorm = 0.5f + (if (i % 2 == 0) -0.025f else 0.025f),
                    yNorm = 0.85f,
                    vx = (random.nextFloat() - 0.5f) * 0.12f,
                    vy = 0.22f + random.nextFloat() * 0.2f,
                    life = 0.18f,
                    maxLife = 0.18f,
                    color = bike.flameCore
                )
            )
        }
    }

    private fun spawnBoostFlameParticle() {
        particles.add(
            SparkParticle(
                xNorm = 0.5f + (random.nextFloat() - 0.5f) * 0.045f,
                yNorm = 0.85f,
                vx = (random.nextFloat() - 0.5f) * 0.08f,
                vy = 0.35f + random.nextFloat() * 0.25f,
                life = 0.15f,
                maxLife = 0.15f,
                color = bike.flameCore
            )
        )
    }

    private fun updateParticles(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.xNorm += p.vx * dt
            p.yNorm += p.vy * dt
            p.vy += 0.9f * dt
            p.life -= dt
            if (p.life <= 0f) it.remove()
        }
    }

    fun segmentIndexAt(z: Float): Int {
        val wrapped = ((z % TRACK_LENGTH) + TRACK_LENGTH) % TRACK_LENGTH
        return (wrapped / SEGMENT_LENGTH).toInt().coerceIn(0, SEGMENT_COUNT - 1)
    }

    fun buildHud(): HudSnapshot {
        return HudSnapshot(
            speedKmh = speedKmh.toInt().coerceIn(0, 300),
            rpm = rpm,
            rpmNormalized = rpmNorm,
            gear = gear,
            aeroCharge = aeroCharge,
            isBoosting = isBoosting,
            isSlipstreaming = isSlipstreaming,
            shiftFlash = shiftFlashTimer > 0f,
            leanDeg = leanDeg,
            score = score,
            distanceKm = distanceKm,
            nearMissCount = nearMissCount,
            nearMissCombo = nearMissCombo,
            currentLap = currentLap,
            totalLaps = trackMode.totalLaps.coerceAtLeast(1),
            position = position,
            totalRacers = 12,
            lapTimeSec = currentLapTimeSec,
            topSpeedReached = topSpeedReached,
            calloutText = calloutText,
            calloutColor = calloutColor,
            isCountdown = countdownTimer > 0f,
            countdownValue = kotlin.math.ceil(countdownTimer.toDouble()).toInt().coerceIn(1, 3)
        )
    }
}
