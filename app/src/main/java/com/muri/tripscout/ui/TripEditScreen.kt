package com.muri.tripscout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muri.tripscout.model.GeoPlace
import com.muri.tripscout.model.Stop
import com.muri.tripscout.model.Trip
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** Editable copy of a stop; dates and the picked place stay null until chosen. */
private data class StopDraft(
    val id: String = UUID.randomUUID().toString(),
    val place: String = "",
    val geo: GeoPlace? = null,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripEditScreen(vm: TripViewModel, existing: Trip?) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    val stops = remember {
        mutableStateListOf<StopDraft>().apply {
            existing?.stops?.sortedBy { it.startDate }?.forEach { add(StopDraft(it.id, it.place, it.geo, it.startDate, it.endDate)) }
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
            Text("Start typing a city, then tap the right one in the list. That exact place is used for weather, events and safety.",
                style = MaterialTheme.typography.bodySmall)

            stops.forEachIndexed { index, stop ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            PlaceField(
                                label = "Place ${index + 1}",
                                draft = stop,
                                onChange = { stops[index] = it },
                                suggest = vm::suggestPlaces,
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
                            stops = stops.map { Stop(it.id, it.place.trim(), it.start!!, it.end!!, it.geo) },
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

/**
 * Text field with live city suggestions. Typing clears any earlier pick, so the saved place
 * always matches what is shown.
 */
@Composable
private fun PlaceField(
    label: String,
    draft: StopDraft,
    onChange: (StopDraft) -> Unit,
    suggest: suspend (String) -> List<GeoPlace>,
    modifier: Modifier = Modifier,
) {
    var suggestions by remember { mutableStateOf<List<GeoPlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val query = draft.place.trim()

    // Restarts on every keystroke, so the delay works as a debounce.
    LaunchedEffect(query, draft.geo) {
        if (draft.geo != null || query.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        searching = true
        suggestions = suggest(query)
        searching = false
    }

    Column(modifier) {
        OutlinedTextField(
            value = draft.place,
            onValueChange = { onChange(draft.copy(place = it, geo = null)) },
            label = { Text(label) },
            placeholder = { Text("e.g. Kotor") },
            singleLine = true,
            trailingIcon = {
                when {
                    draft.geo != null -> Icon(Icons.Default.CheckCircle, "Place confirmed", tint = MaterialTheme.colorScheme.primary)
                    searching -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            },
            supportingText = {
                Text(when {
                    draft.geo != null -> "Using: ${placeDetails(draft.geo)}"
                    query.length < 2 -> "Type at least 2 letters"
                    searching -> "Searching…"
                    suggestions.isEmpty() -> "No match yet. Check the spelling or try the local name."
                    else -> "Tap the right place below"
                })
            },
            isError = draft.geo == null && query.length >= 2 && !searching && suggestions.isEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (draft.geo == null && suggestions.isNotEmpty()) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                suggestions.forEachIndexed { i, place ->
                    if (i > 0) HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onChange(draft.copy(place = "${place.name}, ${place.country}", geo = place)) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(flagEmoji(place.countryCode), style = MaterialTheme.typography.titleLarge)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(place.name, fontWeight = FontWeight.SemiBold)
                            Text(placeDetails(place), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

/** "Region, Country · 1.9M people" — enough to tell same-named places apart. */
private fun placeDetails(place: GeoPlace): String {
    val where = listOfNotNull(place.region, place.country).filter { it.isNotBlank() && it != place.name }.distinct()
    val people = place.population?.takeIf { it > 0 }?.let {
        when {
            it >= 1_000_000 -> "%.1fM people".format(it / 1_000_000.0)
            it >= 1_000 -> "${it / 1_000}k people"
            else -> "$it people"
        }
    }
    return (where + listOfNotNull(people)).joinToString(" · ")
}

/** Two regional-indicator letters render as the country's flag. */
private fun flagEmoji(countryCode: String): String {
    if (countryCode.length != 2 || !countryCode.all { it.isLetter() }) return "📍"
    return countryCode.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

private fun validate(name: String, stops: List<StopDraft>): String? = when {
    name.isBlank() -> "Give the trip a name."
    stops.isEmpty() -> "Add at least one place."
    stops.any { it.place.isBlank() } -> "Every place needs a name."
    stops.any { it.geo == null } -> "Pick each place from the suggestions so the right city is used."
    stops.any { it.start == null || it.end == null } -> "Pick dates for every place."
    else -> null
}

// The Material date pickers work in UTC milliseconds.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
