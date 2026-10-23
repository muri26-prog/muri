package com.muri.tripscout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muri.tripscout.model.Stop
import com.muri.tripscout.model.Trip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** Editable copy of a stop; dates stay null until picked. */
private data class StopDraft(
    val id: String = UUID.randomUUID().toString(),
    val place: String = "",
    val start: LocalDate? = null,
    val end: LocalDate? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripEditScreen(vm: TripViewModel, existing: Trip?) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    val stops = remember {
        mutableStateListOf<StopDraft>().apply {
            existing?.stops?.sortedBy { it.startDate }?.forEach { add(StopDraft(it.id, it.place, it.startDate, it.endDate)) }
            if (isEmpty()) add(StopDraft())
        }
    }
    var pickingFor by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New trip" else "Edit trip") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete trip") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Trip name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Text("Places and dates", style = MaterialTheme.typography.titleMedium)
            Text("Tip: use \"City, Country\" (e.g. \"Kotor, Montenegro\") so the right place is found.",
                style = MaterialTheme.typography.bodySmall)

            stops.forEachIndexed { index, stop ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = stop.place,
                                onValueChange = { stops[index] = stop.copy(place = it) },
                                label = { Text("Place ${index + 1}") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            if (stops.size > 1) {
                                IconButton(onClick = { stops.removeAt(index) }) { Icon(Icons.Default.Delete, "Remove place") }
                            }
                        }
                        OutlinedButton(onClick = { pickingFor = index }, modifier = Modifier.padding(top = 8.dp)) {
                            Icon(Icons.Default.DateRange, contentDescription = null)
                            Text(
                                if (stop.start == null) "Pick dates"
                                else "${formatDate(stop.start)} – ${formatDate(stop.end)}",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }

            OutlinedButton(onClick = {
                // New stops default to starting the day the previous one ends (a travel day).
                val lastEnd = stops.mapNotNull { it.end }.maxOrNull()
                stops.add(StopDraft(start = lastEnd, end = lastEnd?.plusDays(1)))
            }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add place", modifier = Modifier.padding(start = 8.dp))
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    error = validate(name, stops)
                    if (error == null) {
                        vm.saveTrip(Trip(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            stops = stops.map { Stop(it.id, it.place.trim(), it.start!!, it.end!!) },
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save and research") }
        }
    }

    pickingFor?.let { index ->
        val stop = stops[index]
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = stop.start?.toUtcMillis(),
            initialSelectedEndDateMillis = stop.end?.toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingFor = null },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val start = state.selectedStartDateMillis!!.toLocalDate()
                        val end = state.selectedEndDateMillis?.toLocalDate() ?: start
                        stops[index] = stop.copy(start = start, end = end)
                        pickingFor = null
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingFor = null }) { Text("Cancel") } },
        ) {
            DateRangePicker(state = state, modifier = Modifier.weight(1f))
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete trip?") },
            text = { Text("\"${existing.name}\" will be removed from this device. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.deleteTrip(existing.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun validate(name: String, stops: List<StopDraft>): String? = when {
    name.isBlank() -> "Give the trip a name."
    stops.isEmpty() -> "Add at least one place."
    stops.any { it.place.isBlank() } -> "Every place needs a name."
    stops.any { it.start == null || it.end == null } -> "Pick dates for every place."
    else -> null
}

// The Material date pickers work in UTC milliseconds.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
