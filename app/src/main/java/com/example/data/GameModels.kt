package com.example.data

import androidx.compose.ui.graphics.Color

enum class TrackMode(
    val id: String,
    val title: String,
    val subtitle: String,
    val totalLaps: Int
) {
    SUNSET_HIGHWAY(
        id = "sunset_highway",
        title = "Alpine Sunset Highway",
        subtitle = "Endless 300 KM/H Traffic Blitz • Slipstream & Near-Miss",
        totalLaps = 0
    ),
    NIGHT_CIRCUIT(
        id = "night_circuit",
        title = "Midnight GP Circuit",
        subtitle = "3-Lap Floodlit Championship • 12 Superbikes",
        totalLaps = 3
    )
}

data class BikeSpec(
    val id: String,
    val name: String,
    val engineLabel: String,
    val horsepower: Int,
    val weightKg: Int,
    val topSpeedKmh: Float,
    val accelRating: Float,      // 0.0..1.0
    val handlingRating: Float,   // 0.0..1.0
    val brakingRating: Float,    // 0.0..1.0
    val primaryLivery: Color,
    val secondaryLivery: Color,
    val accentGlow: Color,
    val suitPrimary: Color,
    val suitAccent: Color,
    val flameCore: Color
)

object BikeCatalog {
    val bikes: List<BikeSpec> = listOf(
        BikeSpec(
            id = "rosso_v4",
            name = "Rosso V4 RR",
            engineLabel = "998cc Desmo V4",
            horsepower = 221,
            weightKg = 192,
            topSpeedKmh = 300f,
            accelRating = 0.92f,
            handlingRating = 0.90f,
            brakingRating = 0.94f,
            primaryLivery = Color(0xFFE10600),
            secondaryLivery = Color(0xFF1A1F26),
            accentGlow = Color(0xFFFF4D3D),
            suitPrimary = Color(0xFFD5130B),
            suitAccent = Color(0xFFF8FAFC),
            flameCore = Color(0xFF38BDF8)
        ),
        BikeSpec(
            id = "apex_zx10r",
            name = "Apex ZX-10R",
            engineLabel = "998cc Inline-4",
            horsepower = 214,
            weightKg = 195,
            topSpeedKmh = 300f,
            accelRating = 0.96f,
            handlingRating = 0.85f,
            brakingRating = 0.88f,
            primaryLivery = Color(0xFF00C853),
            secondaryLivery = Color(0xFF141B22),
            accentGlow = Color(0xFF34D399),
            suitPrimary = Color(0xFF15202B),
            suitAccent = Color(0xFF10B981),
            flameCore = Color(0xFFFFB300)
        ),
        BikeSpec(
            id = "phantom_m1000",
            name = "Phantom M1000",
            engineLabel = "999cc ShiftCam",
            horsepower = 218,
            weightKg = 189,
            topSpeedKmh = 300f,
            accelRating = 0.89f,
            handlingRating = 0.97f,
            brakingRating = 0.92f,
            primaryLivery = Color(0xFF00B8D9),
            secondaryLivery = Color(0xFF0F131A),
            accentGlow = Color(0xFF00E5FF),
            suitPrimary = Color(0xFF1E293B),
            suitAccent = Color(0xFF00E5FF),
            flameCore = Color(0xFF00E5FF)
        )
    )

    fun findById(id: String): BikeSpec = bikes.firstOrNull { it.id == id } ?: bikes.first()
}

data class ControlSettings(
    val tiltSteerEnabled: Boolean = false,
    val autoThrottleEnabled: Boolean = false,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true
)

data class GameRecords(
    val bestScore: Int = 0,
    val bestTopSpeedKmh: Int = 0,
    val bestDistanceKm: Float = 0f,
    val bestCircuitTimeSec: Float = 0f
)

enum class RaceEndReason {
    WIPED_OUT,
    RACE_FINISHED
}

data class RaceResult(
    val trackMode: TrackMode,
    val bikeName: String,
    val reason: RaceEndReason,
    val topSpeedKmh: Int,
    val distanceKm: Float,
    val score: Int,
    val nearMisses: Int,
    val finishPosition: Int,
    val raceTimeSec: Float,
    val bestLapSec: Float,
    val isNewScoreRecord: Boolean,
    val isNewLapRecord: Boolean
)
