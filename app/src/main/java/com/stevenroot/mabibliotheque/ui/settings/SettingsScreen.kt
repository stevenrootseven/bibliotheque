package com.stevenroot.mabibliotheque.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
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

            Divider(modifier = Modifier.padding(vertical = 8.dp))
            BackupSection(viewModel)
        }
    }
}

@Composable
private fun BackupSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val backupState by viewModel.backupState.collectAsState()
    val running = backupState is BackupUiState.Running
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val pickBackupFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    Text("Sauvegarde et transfert", style = MaterialTheme.typography.titleMedium)
    Text(
        "Sauvegarde toute ta collection (fiches et jaquettes), ta clé TMDB et ta connexion NAS " +
            "dans un seul fichier, sans dépendre d'un compte Google — pratique pour changer de " +
            "téléphone, réinstaller l'application ou garder une copie de secours. Ce fichier " +
            "contient le mot de passe de ton NAS : partage-le uniquement avec toi-même (ton " +
            "autre appareil, ton propre cloud). Restaurer remplace entièrement la collection " +
            "actuelle par celle du fichier choisi.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { viewModel.exportBackup(context) }, enabled = !running) { Text("Exporter une sauvegarde") }
        OutlinedButton(onClick = { pickBackupFile.launch("application/zip") }, enabled = !running) { Text("Restaurer une sauvegarde") }
    }
    if (running) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("Traitement en cours…")
        }
    }
    if (backupState is BackupUiState.Error) {
        Text((backupState as BackupUiState.Error).message, color = MaterialTheme.colorScheme.error)
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Restaurer cette sauvegarde ?") },
            text = {
                Text(
                    "Toute la collection actuelle (films, livres, séries et jaquettes) sera " +
                        "remplacée par le contenu de ce fichier. Cette action est irréversible. " +
                        "L'application redémarrera juste après.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restoreBackup(context, uri)
                    pendingRestoreUri = null
                }) { Text("Restaurer") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
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
