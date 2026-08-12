package com.stevenroot.mabibliotheque.ui.detail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stevenroot.mabibliotheque.MaBibliothequeApplication
import com.stevenroot.mabibliotheque.R
import com.stevenroot.mabibliotheque.data.local.SeriesEpisodeEntity
import com.stevenroot.mabibliotheque.data.repository.NetworkShareConfig
import com.stevenroot.mabibliotheque.ui.common.CropImageDialog
import com.stevenroot.mabibliotheque.ui.common.ZoomableImageDialog
import com.stevenroot.mabibliotheque.ui.common.playSmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    viewModel: SeriesDetailViewModel,
    onBack: () -> Unit,
) {
    val entry by viewModel.series.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val app = LocalContext.current.applicationContext as MaBibliothequeApplication
    val networkConfig by app.networkShareSettingsRepository.config.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry?.series?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        val current = entry ?: return@Scaffold
        var showZoom by remember { mutableStateOf(false) }
        // coverPath holds a local file path (from the camera/gallery editor below) — never a
        // remote URL, since series covers are never auto-fetched.
        val coverModel: Any? = current.series.coverPath?.let { File(it) }
        val context = LocalContext.current

        var showChooser by remember { mutableStateOf(false) }
        var pendingCrop by remember { mutableStateOf<Bitmap?>(null) }
        val coroutineScope = rememberCoroutineScope()
        val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) pendingCrop = bitmap
        }
        val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    val decoded = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }
                    if (decoded != null) pendingCrop = decoded
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (coverModel != null) {
                            AsyncImage(
                                model = coverModel,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(120.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showZoom = true },
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(current.series.name, style = MaterialTheme.typography.titleLarge)
                            Text("${current.episodes.size} fichier(s)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    AssistChip(
                        onClick = { showChooser = true },
                        label = { Text(if (coverModel != null) "Changer la couverture" else "Ajouter une couverture") },
                    )
                }
            }
            items(current.episodes.sortedBy { it.fileName }, key = { it.id }) { episode ->
                EpisodeRow(episode = episode, networkConfig = networkConfig)
            }
        }

        if (showZoom && coverModel != null) {
            ZoomableImageDialog(front = coverModel, onDismiss = { showZoom = false })
        }

        if (showChooser) {
            AlertDialog(
                onDismissRequest = { showChooser = false },
                confirmButton = {
                    TextButton(onClick = {
                        showChooser = false
                        cameraLauncher.launch(null)
                    }) { Text("Prendre une photo") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showChooser = false
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Choisir une image") }
                },
                title = { Text("Couverture") },
                text = { Text("Prendre une photo ou choisir une image existante.") },
            )
        }

        pendingCrop?.let { bitmap ->
            CropImageDialog(
                bitmap = bitmap,
                onConfirm = { cropped ->
                    pendingCrop = null
                    viewModel.replaceCoverFromBitmap(context, cropped)
                },
                onCancel = { pendingCrop = null },
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete(onBack)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(entry?.series?.name.orEmpty()) },
        )
    }
}

@Composable
private fun EpisodeRow(episode: SeriesEpisodeEntity, networkConfig: NetworkShareConfig) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(episode.fileName) },
        trailingContent = {
            if (networkConfig.isConfigured) {
                IconButton(onClick = { playSmbFile(context, networkConfig, episode.sourcePath) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Lire")
                }
            }
        },
        modifier = if (networkConfig.isConfigured) {
            Modifier.clickable { playSmbFile(context, networkConfig, episode.sourcePath) }
        } else {
            Modifier
        },
    )
}
