package com.stevenroot.mabibliotheque.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkShareSettingsScreen(
    viewModel: NetworkShareSettingsViewModel,
    onBack: () -> Unit,
) {
    val form by viewModel.form.collectAsState()
    val testState by viewModel.testState.collectAsState()
    var showBrowser by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stockage réseau (NAS)") },
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
            Text(
                "Connecte l'appli à un partage SMB (ex. un NAS Synology) pour cataloguer les films " +
                    "que tu possèdes en numérique, en plus de ta collection physique.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = form.host,
                onValueChange = viewModel::updateHost,
                label = { Text("Adresse du NAS (ex. 192.168.1.7)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.shareName,
                onValueChange = viewModel::updateShare,
                label = { Text("Nom du partage (ex. video)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.username,
                onValueChange = viewModel::updateUsername,
                label = { Text("Utilisateur") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.password,
                onValueChange = viewModel::updatePassword,
                label = { Text("Mot de passe") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::testConnection, enabled = form.isConfigured) {
                    Text("Tester la connexion")
                }
                TextButton(onClick = viewModel::saveCredentials) { Text("Enregistrer") }
            }
            when (val state = testState) {
                ConnectionTestState.Idle -> Unit
                ConnectionTestState.Testing -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Connexion en cours…")
                }
                ConnectionTestState.Success -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Connecté avec succès", color = MaterialTheme.colorScheme.primary)
                }
                is ConnectionTestState.Failure -> Text(state.message, color = MaterialTheme.colorScheme.error)
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Dossiers de films à analyser", style = MaterialTheme.typography.titleMedium)
            if (form.includedFolders.isEmpty()) {
                Text(
                    "Aucun dossier sélectionné pour l'instant.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                form.includedFolders.sorted().forEach { path ->
                    Text("• $path", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(onClick = {
                showBrowser = true
                viewModel.openFolder("", FolderBrowserMode.MOVIES)
            }, enabled = testState == ConnectionTestState.Success) {
                Text("Choisir les dossiers")
            }
            if (testState != ConnectionTestState.Success) {
                Text(
                    "Teste d'abord la connexion pour parcourir le partage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (showBrowser) {
                FolderBrowser(viewModel = viewModel, onClose = { showBrowser = false })
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Bibliothèque numérique", style = MaterialTheme.typography.titleMedium)
            Text(
                "Analyse les dossiers choisis, ajoute les nouveaux films et retire ceux qui ont " +
                    "disparu. À relancer de temps en temps, ou après avoir ajouté des fichiers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScanSection(viewModel = viewModel, enabled = form.includedFolders.isNotEmpty())
            RetryUnmatchedSection(viewModel = viewModel)

            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Séries", style = MaterialTheme.typography.titleMedium)
            Text(
                "Dossier contenant un sous-dossier par série (ex. Films/Series). Chaque sous-dossier " +
                    "devient une fiche avec la liste de ses fichiers — sans détail épisode par épisode.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                form.seriesFolder?.let { "Dossier choisi : $it" } ?: "Aucun dossier choisi pour l'instant.",
                style = MaterialTheme.typography.bodyMedium,
            )
            var showSeriesBrowser by remember { mutableStateOf(false) }
            Button(onClick = {
                showSeriesBrowser = true
                viewModel.openFolder("", FolderBrowserMode.SERIES)
            }, enabled = testState == ConnectionTestState.Success) {
                Text("Choisir le dossier des séries")
            }
            if (showSeriesBrowser) {
                FolderBrowser(
                    viewModel = viewModel,
                    onClose = { showSeriesBrowser = false },
                    onSeriesFolderChosen = { showSeriesBrowser = false },
                )
            }
            SeriesScanSection(viewModel = viewModel, enabled = form.seriesFolder != null)
        }
    }
}

@Composable
private fun ScanSection(viewModel: NetworkShareSettingsViewModel, enabled: Boolean) {
    val scanState by viewModel.scanState.collectAsState()
    when (val state = scanState) {
        DigitalScanUiState.Idle -> {
            Button(onClick = viewModel::rescan, enabled = enabled) { Text("Rescanner maintenant") }
            if (!enabled) {
                Text(
                    "Choisis au moins un dossier d'abord.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is DigitalScanUiState.Running -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("${state.progress.phase} (${state.progress.done}/${state.progress.total})")
            }
            TextButton(onClick = viewModel::cancelScan) { Text("Annuler") }
        }
        is DigitalScanUiState.Done -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Scan terminé : ${state.result.totalFiles} fichiers trouvés, " +
                    "${state.result.added} ajoutés, ${state.result.removed} retirés" +
                    if (state.result.unmatched > 0) ", ${state.result.unmatched} non identifiés" else "",
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = viewModel::rescan) { Text("Relancer") }
        }
        is DigitalScanUiState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::rescan) { Text("Réessayer") }
        }
    }
}

@Composable
private fun RetryUnmatchedSection(viewModel: NetworkShareSettingsViewModel) {
    val retryState by viewModel.retryUnmatchedState.collectAsState()
    when (val state = retryState) {
        DigitalScanUiState.Idle -> {
            TextButton(onClick = viewModel::retryUnmatched) {
                Text("Réessayer les titres non identifiés")
            }
        }
        is DigitalScanUiState.Running -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("${state.progress.phase} (${state.progress.done}/${state.progress.total})")
            }
            TextButton(onClick = viewModel::cancelRetryUnmatched) { Text("Annuler") }
        }
        is DigitalScanUiState.Done -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${state.result.added} titres corrigés" +
                    if (state.result.unmatched > 0) ", ${state.result.unmatched} toujours non identifiés" else "",
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = viewModel::retryUnmatched) { Text("Relancer") }
        }
        is DigitalScanUiState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::retryUnmatched) { Text("Réessayer") }
        }
    }
}

@Composable
private fun SeriesScanSection(viewModel: NetworkShareSettingsViewModel, enabled: Boolean) {
    val scanState by viewModel.seriesScanState.collectAsState()
    when (val state = scanState) {
        SeriesScanUiState.Idle -> {
            Button(onClick = viewModel::rescanSeries, enabled = enabled) { Text("Rescanner les séries") }
        }
        is SeriesScanUiState.Running -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("${state.progress.phase} (${state.progress.done}/${state.progress.total})")
            }
            TextButton(onClick = viewModel::cancelSeriesScan) { Text("Annuler") }
        }
        is SeriesScanUiState.Done -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${state.result.showsFound} séries, ${state.result.episodesAdded} fichiers ajoutés, " +
                    "${state.result.episodesRemoved} retirés",
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = viewModel::rescanSeries) { Text("Relancer") }
        }
        is SeriesScanUiState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::rescanSeries) { Text("Réessayer") }
        }
    }
}

@Composable
private fun FolderBrowser(
    viewModel: NetworkShareSettingsViewModel,
    onClose: () -> Unit,
    onSeriesFolderChosen: (() -> Unit)? = null,
) {
    val path by viewModel.currentPath.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val browsing by viewModel.browsing.collectAsState()
    val error by viewModel.browseError.collectAsState()
    val form by viewModel.form.collectAsState()
    val mode by viewModel.browserMode.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (path.isNotEmpty()) {
                TextButton(onClick = viewModel::navigateUp) { Text("⬅ Remonter") }
            }
            TextButton(onClick = onClose) { Text("Fermer") }
            if (mode == FolderBrowserMode.SERIES && onSeriesFolderChosen != null) {
                Button(onClick = {
                    viewModel.useSeriesFolder(path)
                    onSeriesFolderChosen()
                }) { Text("Utiliser ce dossier") }
            }
        }
        Text("/${path}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (browsing) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        entries.forEach { entry ->
            ListItem(
                leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                headlineContent = { Text(entry.name) },
                trailingContent = {
                    if (mode == FolderBrowserMode.MOVIES) {
                        Checkbox(
                            checked = entry.path in form.includedFolders,
                            onCheckedChange = { viewModel.toggleFolderIncluded(entry.path) },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { viewModel.openFolder(entry.path, mode) }) {
                Text("Ouvrir")
            }
        }
    }
}
