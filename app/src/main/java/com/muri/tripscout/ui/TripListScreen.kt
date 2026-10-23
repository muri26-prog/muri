package com.muri.tripscout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(vm: TripViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Scout") },
                actions = {
                    IconButton(onClick = { vm.open(Screen.Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.open(Screen.Edit(null)) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New trip") },
            )
        },
    ) { padding ->
        if (vm.trips.isEmpty()) {
            Column(Modifier.padding(padding).padding(24.dp)) {
                Text("No trips yet", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Add a trip with the places you'll visit and the dates you'll be there. " +
                        "Trip Scout collects weather, holidays, travel advisories, news, Reddit and " +
                        "events for every day and every place.",
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (vm.apiKey.isBlank()) {
                    Text(
                        "Tip: add an Anthropic API key in Settings to get Claude's daily safety and events research.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(vm.trips, key = { it.id }) { trip ->
                Card(Modifier.fillMaxWidth().clickable { vm.open(Screen.Report(trip.id)) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(trip.name, style = MaterialTheme.typography.titleMedium)
                        Text("${formatDate(trip.startDate)} – ${formatDate(trip.endDate)}",
                            style = MaterialTheme.typography.bodySmall)
                        Text(trip.stops.sortedBy { it.startDate }.joinToString(" → ") { it.place },
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}
