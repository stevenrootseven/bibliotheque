package com.stevenroot.mabibliotheque.ui.detail

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.stevenroot.mabibliotheque.data.local.CopyKind
import com.stevenroot.mabibliotheque.data.local.MovieCopyEntity
import com.stevenroot.mabibliotheque.data.remote.TmdbApi
import com.stevenroot.mabibliotheque.data.remote.TmdbMovieSummary
import com.stevenroot.mabibliotheque.data.repository.NetworkShareConfig
import com.stevenroot.mabibliotheque.ui.common.CropImageDialog
import com.stevenroot.mabibliotheque.ui.common.ZoomableImageDialog
import com.stevenroot.mabibliotheque.ui.common.playSmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    viewModel: MovieDetailViewModel,
    onBack: () -> Unit,
) {
    val entry by viewModel.movie.collectAsState()
    val rematch by viewModel.rematch.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val app = LocalContext.current.applicationContext as MaBibliothequeApplication
    val networkConfig by app.networkShareSettingsRepository.config.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry?.movie?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    IconButton(onClick = viewModel::openRematch) {
                        Icon(Icons.Filled.Search, contentDescription = "Rechercher à nouveau")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        val current = entry ?: return@Scaffold
        val movie = current.movie
        val copyWithCover = current.copies.firstOrNull { it.frontCoverPath != null || it.backCoverPath != null }
            ?: current.copies.firstOrNull()
        var showingBack by remember(movie.id) { mutableStateOf(false) }
        var showZoom by remember { mutableStateOf(false) }
        val frontModel = copyWithCover?.frontCoverPath?.let { File(it) } ?: movie.posterUrl
        val backModel = copyWithCover?.backCoverPath?.let { File(it) }
        val context = LocalContext.current

        var editTarget by remember { mutableStateOf<Boolean?>(null) } // true = front, false = back
        var showChooser by remember { mutableStateOf(false) }
        var pendingCrop by remember { mutableStateOf<Bitmap?>(null) }
        val coroutineScope = rememberCoroutineScope()
        val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) pendingCrop = bitmap else editTarget = null
        }
        val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    val decoded = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }
                    if (decoded != null) pendingCrop = decoded else editTarget = null
                }
            } else {
                editTarget = null
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (frontModel != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AsyncImage(
                            model = if (showingBack) backModel ?: frontModel else frontModel,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showZoom = true },
                        )
                        if (backModel != null) {
                            AssistChip(
                                onClick = { showingBack = !showingBack },
                                label = { Text(if (showingBack) "Devant" else "Dos") },
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(movie.title, style = MaterialTheme.typography.titleLarge)
                    movie.originalTitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    movie.year?.let { DetailLine(stringResource(R.string.detail_year), it.toString()) }
                    movie.runtimeMinutes?.let { DetailLine(stringResource(R.string.detail_runtime), "$it min") }
                }
            }
            if (current.copies.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { editTarget = true; showChooser = true },
                        label = { Text(if (frontModel != null) "Changer le devant" else "Ajouter le devant") },
                    )
                    AssistChip(
                        onClick = { editTarget = false; showChooser = true },
                        label = { Text(if (backModel != null) "Changer le dos" else "Ajouter le dos") },
                    )
                }
            }
            movie.genres?.let { DetailLine(stringResource(R.string.detail_genres), it) }
            movie.director?.let { DetailLine(stringResource(R.string.detail_director), it) }
            movie.actors?.let { DetailLine(stringResource(R.string.detail_actors), it) }
            movie.overview?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.detail_synopsis), style = MaterialTheme.typography.labelLarge)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            val context = LocalContext.current
            OutlinedButton(onClick = {
                val query = URLEncoder.encode(movie.title, "UTF-8")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.imdb.com/find/?q=$query&s=tt"))
                context.startActivity(intent)
            }) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Voir sur IMDb")
            }

            Divider()
            Text(
                "Mes exemplaires (${current.copies.size})",
                style = MaterialTheme.typography.titleMedium,
            )
            current.copies.forEach { copy ->
                CopyRow(copy = copy, networkConfig = networkConfig, onDelete = { viewModel.deleteCopy(copy, onBack) })
            }
        }

        if (showZoom && frontModel != null) {
            ZoomableImageDialog(
                front = frontModel,
                back = backModel,
                initialShowBack = showingBack,
                onDismiss = { showZoom = false },
            )
        }

        if (showChooser) {
            AlertDialog(
                onDismissRequest = { showChooser = false; editTarget = null },
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
                title = { Text(if (editTarget == true) "Jaquette (devant)" else "Jaquette (dos)") },
                text = { Text("Prendre une photo ou choisir une image existante.") },
            )
        }

        pendingCrop?.let { bitmap ->
            val isFront = editTarget
            CropImageDialog(
                bitmap = bitmap,
                onConfirm = { cropped ->
                    pendingCrop = null
                    if (isFront != null) viewModel.replaceCoverFromBitmap(context, cropped, isFront)
                    editTarget = null
                },
                onCancel = { pendingCrop = null; editTarget = null },
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteMovie(onBack)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text("Supprimer « ${entry?.movie?.title.orEmpty()} » et tous ses exemplaires ?") },
        )
    }

    if (rematch != null) {
        val state = rematch!!
        AlertDialog(
            onDismissRequest = viewModel::dismissRematch,
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::dismissRematch) { Text(stringResource(R.string.action_cancel)) }
            },
            title = { Text("Rechercher à nouveau") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::updateRematchQuery,
                        label = { Text("Titre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = viewModel::searchRematch) { Text("Rechercher") }
                        if (state.loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(state.results) { candidate ->
                            MovieRematchCandidateRow(candidate) { viewModel.selectRematchCandidate(candidate) }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun MovieRematchCandidateRow(candidate: TmdbMovieSummary, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(candidate.title) },
        supportingContent = { candidate.releaseDate?.take(4)?.let { Text(it) } },
        leadingContent = {
            candidate.posterPath?.let {
                AsyncImage(
                    model = TmdbApi.IMAGE_BASE_URL + it,
                    contentDescription = null,
                    modifier = Modifier.size(width = 40.dp, height = 60.dp),
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun CopyRow(copy: MovieCopyEntity, networkConfig: NetworkShareConfig, onDelete: () -> Unit) {
    val context = LocalContext.current
    val sizeLabel = copy.fileSizeBytes?.let { formatFileSize(it) }
    val subtitle = when (copy.kind) {
        CopyKind.PHYSICAL -> listOfNotNull(copy.barcode, copy.languages).joinToString(" · ")
        CopyKind.DIGITAL -> listOfNotNull(sizeLabel, copy.languages, copy.sourcePath).joinToString(" · ")
    }
    ListItem(
        headlineContent = { Text(copy.format) },
        supportingContent = if (subtitle.isNotBlank()) {
            { Text(subtitle, style = MaterialTheme.typography.bodySmall) }
        } else {
            null
        },
        leadingContent = {
            Text(if (copy.kind == CopyKind.DIGITAL) "💻" else "📀")
        },
        trailingContent = {
            Row {
                if (copy.kind == CopyKind.DIGITAL && copy.sourcePath != null && networkConfig.isConfigured) {
                    IconButton(onClick = { playSmbFile(context, networkConfig, copy.sourcePath) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Lire")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        },
    )
}

private fun formatFileSize(bytes: Long): String {
    val gb = bytes / 1_073_741_824.0
    if (gb >= 1) return "%.1f Go".format(gb)
    val mb = bytes / 1_048_576.0
    return "%.0f Mo".format(mb)
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
