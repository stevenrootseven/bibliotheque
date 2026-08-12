package com.stevenroot.mabibliotheque.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stevenroot.mabibliotheque.R
import com.stevenroot.mabibliotheque.data.remote.BookVolumeInfo
import com.stevenroot.mabibliotheque.ui.common.CropImageDialog
import com.stevenroot.mabibliotheque.ui.common.ZoomableImageDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    viewModel: BookDetailViewModel,
    onBack: () -> Unit,
) {
    val book by viewModel.book.collectAsState()
    val rematch by viewModel.rematch.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.title ?: "") },
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
        val current = book ?: return@Scaffold
        var showZoom by remember { mutableStateOf(false) }
        // coverUrl holds either a remote URL (from BnF/Open Library search) or a local file path
        // (from the camera/gallery editor below) — Coil needs a File model for the latter.
        val coverModel: Any? = current.coverUrl?.let { path -> if (path.startsWith("http")) path else File(path) }
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (coverModel != null) {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showZoom = true },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(current.title, style = MaterialTheme.typography.titleLarge)
                    current.publishedYear?.let { DetailLine(stringResource(R.string.detail_year), it.toString()) }
                    current.pageCount?.let { DetailLine(stringResource(R.string.detail_pages), it.toString()) }
                }
            }
            AssistChip(
                onClick = { showChooser = true },
                label = { Text(if (coverModel != null) "Changer la couverture" else "Ajouter une couverture") },
            )
            current.authors?.let { DetailLine(stringResource(R.string.detail_authors), it) }
            current.publisher?.let { DetailLine(stringResource(R.string.detail_publisher), it) }
            current.isbn13?.let { DetailLine(stringResource(R.string.detail_isbn), it) }
            DetailLine(stringResource(R.string.detail_barcode), current.barcode)
            current.description?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.detail_synopsis), style = MaterialTheme.typography.labelLarge)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (showZoom && coverModel != null) {
            ZoomableImageDialog(front = coverModel, onDismiss = { showZoom = false })
        }

        if (showChooser) {
            AlertDialog(
                onDismissRequest = { showChooser = false },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showChooser = false }) { Text(stringResource(R.string.action_cancel)) }
                },
                title = { Text("Couverture") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            showChooser = false
                            cameraLauncher.launch(null)
                        }) { Text("Prendre une photo") }
                        TextButton(onClick = {
                            showChooser = false
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text("Choisir une image") }
                        TextButton(onClick = {
                            showChooser = false
                            searchCoverOnline(context, current.title, current.authors)
                        }) { Text("Rechercher une image en ligne") }
                    }
                },
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
            text = { Text(book?.title.orEmpty()) },
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
                        if (viewModel.rematchIsbn() != null) {
                            TextButton(onClick = viewModel::searchRematchByIsbn) { Text("Rechercher via l'ISBN") }
                        }
                        if (state.loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(state.results) { candidate ->
                            BookRematchCandidateRow(candidate) { viewModel.selectRematchCandidate(candidate) }
                        }
                    }
                }
            },
        )
    }
}

/** Opens the device's browser on a Google Images search for the book, so the user can pick
 * whichever cover they find (French covers are rarely in BnF/Open Library — see those repos'
 * lookup logic). This intentionally avoids any scraping or third-party API: it's the same as the
 * user typing the search themselves, so there's no key, quota, or ToS to depend on. The chosen
 * image is then saved via the existing gallery-import + crop flow, not fetched automatically. */
private fun searchCoverOnline(context: Context, title: String, authors: String?) {
    val query = listOfNotNull(title, authors?.takeIf { it.isNotBlank() }, "couverture livre")
        .joinToString(" ")
    val uri = Uri.parse("https://www.google.com/search?tbm=isch&q=" + Uri.encode(query))
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Aucun navigateur disponible.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun BookRematchCandidateRow(candidate: BookVolumeInfo, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(candidate.title) },
        supportingContent = { Text(candidate.authors.joinToString(", ")) },
        leadingContent = {
            candidate.coverUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    modifier = Modifier.size(width = 40.dp, height = 60.dp),
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
