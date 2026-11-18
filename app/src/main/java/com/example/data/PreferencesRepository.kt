package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.apexDataStore: DataStore<Preferences> by preferencesDataStore(name = "apex300_prefs")

data class StoredGameState(
    val selectedBikeId: String = "rosso_v4",
    val selectedTrackMode: TrackMode = TrackMode.SUNSET_HIGHWAY,
    val settings: ControlSettings = ControlSettings(),
    val records: GameRecords = GameRecords()
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val SELECTED_BIKE = stringPreferencesKey("selected_bike_id")
        val SELECTED_TRACK = stringPreferencesKey("selected_track_id")
        val TILT_STEER = booleanPreferencesKey("tilt_steer_enabled")
        val AUTO_THROTTLE = booleanPreferencesKey("auto_throttle_enabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val BEST_SCORE = intPreferencesKey("best_score")
        val BEST_TOP_SPEED = intPreferencesKey("best_top_speed")
        val BEST_DISTANCE = floatPreferencesKey("best_distance_km")
        val BEST_CIRCUIT_TIME = floatPreferencesKey("best_circuit_time_sec")
    }

    val stateFlow: Flow<StoredGameState> = context.apexDataStore.data.map { prefs ->
        val trackId = prefs[Keys.SELECTED_TRACK] ?: TrackMode.SUNSET_HIGHWAY.id
        val trackMode = TrackMode.entries.firstOrNull { it.id == trackId } ?: TrackMode.SUNSET_HIGHWAY
        StoredGameState(
            selectedBikeId = prefs[Keys.SELECTED_BIKE] ?: "rosso_v4",
            selectedTrackMode = trackMode,
            settings = ControlSettings(
                tiltSteerEnabled = prefs[Keys.TILT_STEER] ?: false,
                autoThrottleEnabled = prefs[Keys.AUTO_THROTTLE] ?: false,
                soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
                hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true
            ),
            records = GameRecords(
                bestScore = prefs[Keys.BEST_SCORE] ?: 0,
                bestTopSpeedKmh = prefs[Keys.BEST_TOP_SPEED] ?: 0,
                bestDistanceKm = prefs[Keys.BEST_DISTANCE] ?: 0f,
                bestCircuitTimeSec = prefs[Keys.BEST_CIRCUIT_TIME] ?: 0f
            )
        )
    }

    suspend fun setSelectedBike(bikeId: String) {
        context.apexDataStore.edit { it[Keys.SELECTED_BIKE] = bikeId }
    }

    suspend fun setSelectedTrack(trackMode: TrackMode) {
        context.apexDataStore.edit { it[Keys.SELECTED_TRACK] = trackMode.id }
    }

    suspend fun updateSettings(settings: ControlSettings) {
        context.apexDataStore.edit { prefs ->
            prefs[Keys.TILT_STEER] = settings.tiltSteerEnabled
            prefs[Keys.AUTO_THROTTLE] = settings.autoThrottleEnabled
            prefs[Keys.SOUND_ENABLED] = settings.soundEnabled
            prefs[Keys.HAPTICS_ENABLED] = settings.hapticsEnabled
        }
    }

    suspend fun saveRaceResult(result: RaceResult): Pair<Boolean, Boolean> {
        var newScoreRecord = false
        var newLapRecord = false
        context.apexDataStore.edit { prefs ->
            val prevScore = prefs[Keys.BEST_SCORE] ?: 0
            if (result.score > prevScore) {
                prefs[Keys.BEST_SCORE] = result.score
                newScoreRecord = true
            }
            val prevSpeed = prefs[Keys.BEST_TOP_SPEED] ?: 0
            if (result.topSpeedKmh > prevSpeed) {
                prefs[Keys.BEST_TOP_SPEED] = result.topSpeedKmh
            }
            val prevDist = prefs[Keys.BEST_DISTANCE] ?: 0f
            if (result.distanceKm > prevDist) {
                prefs[Keys.BEST_DISTANCE] = result.distanceKm
            }
            if (result.trackMode == TrackMode.NIGHT_CIRCUIT &&
                result.reason == RaceEndReason.RACE_FINISHED &&
                result.bestLapSec > 0f
            ) {
                val prevLap = prefs[Keys.BEST_CIRCUIT_TIME] ?: 0f
                if (prevLap == 0f || result.bestLapSec < prevLap) {
                    prefs[Keys.BEST_CIRCUIT_TIME] = result.bestLapSec
                    newLapRecord = true
                }
            }
        }
        return newScoreRecord to newLapRecord
    }
}
