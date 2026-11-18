package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.screens.HomeGarageScreen
import com.example.ui.screens.RaceScreen
import com.example.ui.theme.Apex300Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            Apex300Theme {
                val vm: MainViewModel = viewModel()
                val screen by vm.screen.collectAsStateWithLifecycle()
                val garageState by vm.garageState.collectAsStateWithLifecycle()

                when (screen) {
                    AppScreen.GARAGE -> {
                        HomeGarageScreen(
                            uiState = garageState,
                            onSelectBike = vm::selectBike,
                            onSelectTrack = vm::selectTrack,
                            onUpdateSettings = vm::updateSettings,
                            onStartRace = vm::startRace
                        )
                    }
                    AppScreen.RACE -> {
                        RaceScreen(
                            bike = garageState.selectedBike,
                            trackMode = garageState.selectedTrackMode,
                            settings = garageState.settings,
                            onUpdateSettings = vm::updateSettings,
                            onRaceComplete = vm::recordRaceResult,
                            onExitToGarage = vm::exitToGarage
                        )
                    }
                }
            }
        }
    }
}
