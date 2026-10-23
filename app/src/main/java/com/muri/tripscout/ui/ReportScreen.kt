package com.muri.tripscout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muri.tripscout.model.Article
import com.muri.tripscout.model.DayReport
import com.muri.tripscout.model.DayWeather
import com.muri.tripscout.model.Event
import com.muri.tripscout.model.LocationReport
import com.muri.tripscout.model.SafetyLevel
import com.muri.tripscout.model.SourceKind
import com.muri.tripscout.model.Trip
import com.muri.tripscout.model.TripReport
import com.muri.tripscout.model.WeatherKind
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(vm: TripViewModel, trip: Trip) {
    val state = vm.reports[trip.id]
    LaunchedEffect(trip.id) { if (state == null) vm.research(trip.id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip.name) },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { vm.open(Screen.Edit(trip.id)) }) { Icon(Icons.Default.Edit, "Edit trip") }
                    IconButton(onClick = { vm.research(trip.id) }, enabled = state !is ReportState.Loading) {
                        Icon(Icons.Default.Refresh, "Research again")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                null, is ReportState.Loading -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text((state as? ReportState.Loading)?.progress ?: "Starting…", modifier = Modifier.padding(top = 16.dp))
                    if (vm.apiKey.isNotBlank()) {
                        Text("Claude's web research can take a few minutes per place.",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                is ReportState.Failed -> Column(Modifier.padding(24.dp)) {
                    Text("Research failed: ${state.message}", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { vm.research(trip.id) }, modifier = Modifier.padding(top = 12.dp)) { Text("Try again") }
                }
                is ReportState.Ready -> ReportContent(state.report, hasAi = vm.apiKey.isNotBlank()) { event ->
                    vm.open(Screen.EventDetail(event))
                }
            }
        }
    }
}

@Composable
private fun ReportContent(report: TripReport, hasAi: Boolean, onEvent: (Event) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    TabRow(selectedTabIndex = tab) {
        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Day by day") })
        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Places") })
    }
    if (tab == 0) DailyTab(report, hasAi, onEvent) else PlacesTab(report, onEvent)
}

@Composable
private fun DailyTab(report: TripReport, hasAi: Boolean, onEvent: (Event) -> Unit) {
    val byStop = report.locations.associateBy { it.stop.id }
    val calendar = report.trip.calendar().toList()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Generated ${report.generatedAt}", style = MaterialTheme.typography.labelSmall)
            if (!hasAi) {
                Text(
                    "Showing free sources only. Add an Anthropic API key in Settings for Claude's daily " +
                        "safety ratings, events and web/Reddit/blog research.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        items(calendar, key = { it.first.toString() }) { (date, stops) ->
            Column {
                Text(formatLongDate(date), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                stops.forEach { stop ->
                    val location = byStop[stop.id] ?: return@forEach
                    val day = location.days.firstOrNull { it.date == date } ?: return@forEach
                    DayCard(location, day, hasAi, onEvent)
                }
            }
        }
    }
}

@Composable
private fun DayCard(location: LocationReport, day: DayReport, hasAi: Boolean, onEvent: (Event) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(location.geo?.name ?: location.stop.place, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f))
                day.ai?.let { SafetyBadge(it.safetyLevel) }
            }
            day.weather?.let { WeatherLine(it) } ?: Text("No weather data", style = MaterialTheme.typography.bodySmall)

            day.holidays.forEach { holiday ->
                val text = buildString {
                    append("Public holiday: ").append(holiday.name)
                    if (holiday.localName != holiday.name) append(" (").append(holiday.localName).append(")")
                    if (!holiday.nationwide) append(" – regional")
                }
                Text(text, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            }

            day.ai?.let { ai ->
                if (ai.safetyNotes.isNotBlank()) { SectionTitle("Safety"); Text(ai.safetyNotes) }
            }
            if (day.events.isNotEmpty()) {
                SectionTitle("Events (${day.events.size}) · tap for details")
                day.events.forEach { EventRow(it) { onEvent(it) } }
            } else if (hasAi && location.ai != null) {
                SectionTitle("Events")
                Text("No events found for this day.", style = MaterialTheme.typography.bodySmall)
            }
            day.ai?.let { ai ->
                if (ai.happenings.isNotBlank()) { SectionTitle("What's happening"); Text(ai.happenings) }
                if (ai.tips.isNotBlank()) { SectionTitle("Tips"); Text(ai.tips) }
            }
        }
    }
}

@Composable
private fun WeatherLine(w: DayWeather) {
    val temps = listOfNotNull(w.tempMinC, w.tempMaxC).joinToString(" – ") { "${it.roundToInt()}°C" }
    val rain = when {
        w.kind == WeatherKind.TYPICAL && w.precipitationChancePct != null -> "rain on ${w.precipitationChancePct}% of past years"
        w.precipitationChancePct != null -> "${w.precipitationChancePct}% rain chance"
        else -> null
    }
    val wind = w.windMaxKmh?.let { "wind up to ${it.roundToInt()} km/h" }
    val uv = w.uvIndexMax?.let { "UV ${it.roundToInt()}" }
    Text(
        listOfNotNull(w.description, temps.ifBlank { null }, rain, wind, uv).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
    if (w.kind == WeatherKind.TYPICAL) {
        Text("Typical weather (3-year average). A real forecast appears 16 days before.",
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SafetyBadge(level: SafetyLevel) {
    Text(
        level.label(),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(level.color(), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun PlacesTab(report: TripReport, onEvent: (Event) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(report.locations, key = { it.stop.id }) { location -> PlaceCard(location, onEvent) }
    }
}

@Composable
private fun PlaceCard(location: LocationReport, onEvent: (Event) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        location.placeInfo?.imageUrl?.let { image ->
            RemoteImage(image, location.geo?.name ?: location.stop.place, Modifier.fillMaxWidth().height(180.dp))
        }
        Column(Modifier.padding(16.dp)) {
            Text(location.geo?.label ?: location.stop.place, style = MaterialTheme.typography.titleMedium)
            Text("${formatDate(location.stop.startDate)} – ${formatDate(location.stop.endDate)}",
                style = MaterialTheme.typography.bodySmall)

            location.placeInfo?.let { info ->
                SectionTitle("About")
                ExpandableText(info.summary, collapsedLines = 3)
                ArticleLink(Article("Wikipedia article and photos", info.pageUrl, null, null, SourceKind.WEB))
            }

            val stayEvents = location.days.flatMap { it.events }
            if (stayEvents.isNotEmpty()) {
                SectionTitle("Events during your stay (${stayEvents.size})")
                stayEvents.forEach { event -> EventRow(event, showDate = true) { onEvent(event) } }
            }

            location.ai?.let { ai ->
                if (ai.rawText != null) {
                    SectionTitle("Claude's research"); ExpandableText(ai.rawText, collapsedLines = 8)
                }
                if (ai.overview.isNotBlank()) { SectionTitle("Overview"); Text(ai.overview) }
                if (ai.safetyOverview.isNotBlank()) { SectionTitle("Safety overview"); Text(ai.safetyOverview) }
            }

            location.advisory?.let { advisory ->
                SectionTitle("Official travel advice (UK FCDO, updated ${advisory.updated ?: "?"})")
                if (advisory.alerts.isEmpty()) Text("No FCDO travel warnings for this country.")
                advisory.alerts.forEach {
                    Text("⚠ $it", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
                advisory.safetySummary?.let { ExpandableText(it) }
                ArticleLink(Article("Read full advice on GOV.UK", advisory.url, null, null, SourceKind.WEB))
            }

            location.staySafe?.let { SectionTitle("Stay safe (Wikivoyage)"); ExpandableText(it) }

            ArticleGroup("Safety-related news", location, SourceKind.SAFETY_NEWS)
            ArticleGroup("Events on your dates", location, SourceKind.EVENTS)
            ArticleGroup("Latest news", location, SourceKind.NEWS)
            ArticleGroup("Local-language news", location, SourceKind.LOCAL_NEWS)
            ArticleGroup("Reddit", location, SourceKind.REDDIT)

            location.ai?.sources?.takeIf { it.isNotEmpty() }?.let { sources ->
                SectionTitle("Sources Claude used")
                sources.forEach { ArticleLink(it) }
            }

            if (location.warnings.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(top = 12.dp))
                SectionTitle("Sources with problems")
                location.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun ArticleGroup(title: String, location: LocationReport, kind: SourceKind) {
    val articles = location.articles.filter { it.kind == kind }
    if (articles.isEmpty()) return
    SectionTitle(title)
    articles.take(6).forEach { ArticleLink(it) }
}
