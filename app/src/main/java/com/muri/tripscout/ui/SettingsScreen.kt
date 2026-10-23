package com.muri.tripscout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: TripViewModel) {
    var key by remember { mutableStateOf(vm.apiKey) }
    var model by remember { mutableStateOf(vm.model) }
    var ticketmaster by remember { mutableStateOf(vm.ticketmasterKey) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Claude web research (optional)", style = MaterialTheme.typography.titleMedium)
            Text(
                "With an Anthropic API key, Claude searches the web for every place and day: local news, " +
                    "Reddit and forums, blogs, official city sources, events and safety issues, and rates " +
                    "each day's safety. Without a key you still get weather, holidays, official advice, " +
                    "news headlines, Reddit threads and Wikivoyage tips.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = key, onValueChange = { key = it },
                label = { Text("Anthropic API key") },
                placeholder = { Text("sk-ant-…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model, onValueChange = { model = it },
                label = { Text("Claude model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Costs are billed to your Anthropic account: tokens plus web searches (up to 10 per place " +
                    "per research run). Expect roughly a few tens of cents per place. Create a key at " +
                    "console.anthropic.com and set a spend limit there.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "The key is stored in this app's private storage on this phone only. Anyone with full " +
                    "access to an unlocked or rooted phone could read it, so use a dedicated key with a low limit.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Event listings (optional)", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp))
            Text(
                "A free Ticketmaster key adds real concerts, sports, theatre and festivals with photos and " +
                    "ticket links. Get one at developer.ticketmaster.com (\"Consumer Key\"). Best coverage in " +
                    "Europe and North America.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = ticketmaster, onValueChange = { ticketmaster = it },
                label = { Text("Ticketmaster API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { vm.saveSettings(key, model, ticketmaster) }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}
