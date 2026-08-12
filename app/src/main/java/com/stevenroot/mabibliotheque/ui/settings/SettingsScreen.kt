package com.stevenroot.mabibliotheque.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stevenroot.mabibliotheque.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenNetworkShareSettings: () -> Unit,
) {
    val apiKey by viewModel.apiKeyInput.collectAsState()
    val saved by viewModel.saved.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_tmdb_key_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_tmdb_key_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = viewModel::onInputChanged,
                label = { Text(stringResource(R.string.settings_tmdb_key_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = viewModel::save) { Text(stringResource(R.string.action_save)) }
            if (saved) {
                Text("Enregistré", color = MaterialTheme.colorScheme.primary)
            }

            if (viewModel.importAvailable) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Importer ma collection", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Une collection exportée depuis My Movies Pro a été trouvée sur cet appareil. " +
                        "L'import ajoute les films manquants et complète les fiches déjà scannées " +
                        "(réalisateur, acteurs, durée, jaquettes) sans rien écraser.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ImportSection(viewModel)
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Bibliothèque numérique", style = MaterialTheme.typography.titleMedium)
            Text(
                "Catalogue les films que tu possèdes en numérique sur un partage réseau (NAS).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenNetworkShareSettings) { Text("Configurer le stockage réseau") }

            Divider(modifier = Modifier.padding(vertical = 8.dp))
            ExportSection(viewModel)
        }
    }
}

@Composable
private fun ExportSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val exportState by viewModel.exportState.collectAsState()
    val running = exportState is ExportUiState.Running

    Text("Exporter ma collection", style = MaterialTheme.typography.titleMedium)
    Text(
        "Génère un fichier CSV (pour un tableur) ou un catalogue PDF avec les jaquettes, à " +
            "partager avec des amis qui n'ont pas l'application.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { viewModel.exportCsv(context) }, enabled = !running) { Text("Exporter en CSV") }
        Button(onClick = { viewModel.exportPdf(context) }, enabled = !running) { Text("Exporter en PDF") }
    }
    if (running) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("Génération en cours…")
        }
    }
    if (exportState is ExportUiState.Error) {
        Text((exportState as ExportUiState.Error).message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ImportSection(viewModel: SettingsViewModel) {
    val importState by viewModel.importState.collectAsState()
    when (val state = importState) {
        ImportUiState.Idle -> {
            Button(onClick = viewModel::runImport) { Text("Importer ma collection") }
        }
        is ImportUiState.Running -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val fraction = if (state.progress.total > 0) state.progress.done.toFloat() / state.progress.total else 0f
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("${state.progress.done} / ${state.progress.total} — ${state.progress.currentTitle}")
                }
            }
        }
        is ImportUiState.Done -> {
            Text(
                "Import terminé : ${state.result.inserted} ajoutés, ${state.result.updated} complétés" +
                    if (state.result.skipped > 0) ", ${state.result.skipped} ignorés (sans code-barres)" else "",
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
