package com.muri.tripscout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.muri.tripscout.model.Event
import java.net.URLEncoder

/** Photo from the internet, cropped to fill; shows the surface colour while loading or on failure. */
@Composable
fun RemoteImage(url: String, description: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** Compact, tappable event line: thumbnail, title, time and venue. */
@Composable
fun EventRow(event: Event, showDate: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (event.imageUrl != null) {
            RemoteImage(event.imageUrl, event.title, Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
        } else {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(event.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val whenText = listOfNotNull(if (showDate) event.date?.let(::formatDate) else null, event.time).joinToString(" ")
            val meta = listOfNotNull(whenText.ifBlank { null }, event.venue).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            event.category?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Details")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(vm: TripViewModel, event: Event) {
    val uriHandler = LocalUriHandler.current
    fun open(url: String) = runCatching { uriHandler.openUri(url) }
    val searchTerms = listOfNotNull(event.title, event.venue, event.city).joinToString(" ")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event", maxLines = 1) },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            event.imageUrl?.let { RemoteImage(it, event.title, Modifier.fillMaxWidth().height(240.dp)) }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(event.title, style = MaterialTheme.typography.headlineSmall)
                event.category?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }

                val whenText = listOfNotNull(event.date?.let(::formatLongDate), event.time).joinToString(" at ")
                if (whenText.isNotBlank()) DetailLine(Icons.Default.DateRange, whenText)
                event.venue?.let { venue ->
                    DetailLine(Icons.Default.Place, listOfNotNull(venue, event.city).joinToString(", "))
                }

                Text(
                    event.description ?: "No description available. Open the event page or search for it below.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )

                event.url?.let { url ->
                    Button(onClick = { open(url) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (event.source == "Ticketmaster") "Tickets and details" else "Open event page")
                    }
                }
                if (event.venue != null || event.city != null) {
                    OutlinedButton(onClick = { open(mapsUrl(listOfNotNull(event.venue, event.city).joinToString(", "))) },
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Place, contentDescription = null)
                        Text("Show on map", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedButton(onClick = { open(googleUrl(searchTerms, images = true)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Text("See photos", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = { open(googleUrl(searchTerms, images = false)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Text("Search the web", modifier = Modifier.padding(start = 8.dp))
                }

                DetailLine(Icons.Default.Info, "Source: ${event.source}. Check the official page before you go; times and tickets can change.")
            }
        }
    }
}

@Composable
private fun DetailLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")
private fun mapsUrl(query: String) = "https://www.google.com/maps/search/?api=1&query=${encode(query)}"
private fun googleUrl(query: String, images: Boolean) =
    "https://www.google.com/search?q=${encode(query)}" + if (images) "&tbm=isch" else ""
