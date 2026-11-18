package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BikeCatalog
import com.example.data.BikeSpec
import com.example.data.ControlSettings
import com.example.data.GameRecords
import com.example.data.PreferencesRepository
import com.example.data.RaceResult
import com.example.data.TrackMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen {
    GARAGE,
    RACE
}

data class GarageUiState(
    val selectedBike: BikeSpec = BikeCatalog.bikes.first(),
    val selectedTrackMode: TrackMode = TrackMode.SUNSET_HIGHWAY,
    val settings: ControlSettings = ControlSettings(),
    val records: GameRecords = GameRecords()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PreferencesRepository(application.applicationContext)

    private val _screen = MutableStateFlow(AppScreen.GARAGE)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    val garageState: StateFlow<GarageUiState> = repository.stateFlow
        .map { stored ->
            GarageUiState(
                selectedBike = BikeCatalog.findById(stored.selectedBikeId),
                selectedTrackMode = stored.selectedTrackMode,
                settings = stored.settings,
                records = stored.records
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = GarageUiState()
        )

    fun selectBike(bikeId: String) {
        viewModelScope.launch {
            repository.setSelectedBike(bikeId)
        }
    }

    fun selectTrack(mode: TrackMode) {
        viewModelScope.launch {
            repository.setSelectedTrack(mode)
        }
    }

    fun updateSettings(newSettings: ControlSettings) {
        viewModelScope.launch {
            repository.updateSettings(newSettings)
        }
    }

    fun startRace() {
        _screen.value = AppScreen.RACE
    }

    fun exitToGarage() {
        _screen.value = AppScreen.GARAGE
    }

    suspend fun recordRaceResult(result: RaceResult): RaceResult {
        val (newScore, newLap) = repository.saveRaceResult(result)
        return result.copy(
            isNewScoreRecord = newScore,
            isNewLapRecord = newLap
        )
    }
}
