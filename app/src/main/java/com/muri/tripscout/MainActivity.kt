package com.muri.tripscout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.muri.tripscout.ui.EventDetailScreen
import com.muri.tripscout.ui.ReportScreen
import com.muri.tripscout.ui.Screen
import com.muri.tripscout.ui.SettingsScreen
import com.muri.tripscout.ui.TripEditScreen
import com.muri.tripscout.ui.TripListScreen
import com.muri.tripscout.ui.TripScoutTheme
import com.muri.tripscout.ui.TripViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: TripViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TripScoutTheme {
                val vm = viewModel
                BackHandler(enabled = vm.screen != Screen.Trips) { vm.back() }
                when (val screen = vm.screen) {
                    Screen.Trips -> TripListScreen(vm)
                    is Screen.Edit -> TripEditScreen(vm, screen.tripId?.let(vm::trip))
                    is Screen.Report -> {
                        val trip = vm.trip(screen.tripId)
                        if (trip != null) ReportScreen(vm, trip) else LaunchedEffect(Unit) { vm.back() }
                    }
                    Screen.Settings -> SettingsScreen(vm)
                    is Screen.EventDetail -> EventDetailScreen(vm, screen.event)
                }
            }
        }
    }
}
