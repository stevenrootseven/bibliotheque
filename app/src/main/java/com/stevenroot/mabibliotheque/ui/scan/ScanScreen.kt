package com.stevenroot.mabibliotheque.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.stevenroot.mabibliotheque.R
import com.stevenroot.mabibliotheque.data.remote.GoogleBookVolumeInfo
import com.stevenroot.mabibliotheque.data.remote.TmdbApi
import com.stevenroot.mabibliotheque.data.remote.TmdbMovieSummary
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { captureExecutor.shutdown() } }

    fun captureCoverPhoto() {
        val capture = imageCapture ?: return
        capture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    viewModel.onCoverPhotoCaptured(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    viewModel.onCoverPhotoError(exception.message ?: "Échec de la photo")
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            active = state is ScanUiState.Scanning,
            onBarcodeDetected = viewModel::onBarcodeDetected,
            onImageCaptureReady = { imageCapture = it },
        )

        TopAppBar(
            title = { Text(stringResource(R.string.action_scan)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null)
                }
            },
            actions = {
                if (state !is ScanUiState.Scanning) {
                    IconButton(onClick = viewModel::resetToScanning) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Scanner le disque suivant")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            ),
        )

        if (state is ScanUiState.Scanning) {
            Text(
                text = stringResource(R.string.scan_hint),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
            ) {
                ScanResultPanel(
                    state = state,
                    viewModel = viewModel,
                    onOpenSettings = onOpenSettings,
                    onCapturePhoto = ::captureCoverPhoto,
                )
            }
        }
    }
}

@Composable
private fun ScanResultPanel(
    state: ScanUiState,
    viewModel: ScanViewModel,
    onOpenSettings: () -> Unit,
    onCapturePhoto: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            ScanUiState.Scanning -> Unit

            is ScanUiState.LookingUp -> CenteredSpinnerRow(stringResource(R.string.scan_looking_up))

            is ScanUiState.MissingApiKey -> {
                Text(stringResource(R.string.settings_tmdb_key_missing))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenSettings) { Text(stringResource(R.string.action_settings)) }
                    TextButton(onClick = viewModel::resetToScanning) { Text(stringResource(R.string.action_cancel)) }
                }
            }

            is ScanUiState.MovieCandidates -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sélectionnez le bon film", style = MaterialTheme.typography.titleMedium)
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(state.candidates) { candidate ->
                        MovieCandidateRow(candidate) { viewModel.selectMovieCandidate(state.barcode, candidate) }
                    }
                }
                TextButton(onClick = viewModel::resetToScanning) { Text(stringResource(R.string.action_cancel)) }
            }

            is ScanUiState.BookCandidates -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sélectionnez le bon livre", style = MaterialTheme.typography.titleMedium)
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(state.candidates) { candidate ->
                        BookCandidateRow(candidate) { viewModel.selectBookCandidate(state.barcode, candidate) }
                    }
                }
                TextButton(onClick = viewModel::resetToScanning) { Text(stringResource(R.string.action_cancel)) }
            }

            is ScanUiState.MovieConfirm -> MovieConfirmPanel(state, viewModel)

            is ScanUiState.BookConfirm -> BookConfirmPanel(state, viewModel)

            is ScanUiState.NotFound -> {
                Text(stringResource(R.string.scan_not_found))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.kind == MediaKind.MOVIE) {
                        Button(onClick = { viewModel.startCoverPhoto(state.kind, state.barcode) }) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Photographier la jaquette")
                        }
                    }
                    TextButton(onClick = { viewModel.startManualSearch(state.kind, state.barcode) }) {
                        Text(stringResource(R.string.scan_search_manually))
                    }
                }
                TextButton(onClick = viewModel::resetToScanning) { Text(stringResource(R.string.action_cancel)) }
            }

            is ScanUiState.CoverPhoto -> {
                Text("Cadrez le titre du film sur la jaquette, puis prenez la photo")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCapturePhoto) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Photographier")
                    }
                    TextButton(onClick = viewModel::resetToScanning) { Text(stringResource(R.string.action_cancel)) }
                }
            }

            is ScanUiState.RecognizingCover -> CenteredSpinnerRow("Lecture du texte…")

            is ScanUiState.ManualSearch -> ManualSearchPanel(state, viewModel)

            is ScanUiState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.barcode != null) {
                        TextButton(onClick = { viewModel.onBarcodeDetected(state.barcode) }) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                    TextButton(onClick = viewModel::resetToScanning) { Text(stringResource(R.string.action_cancel)) }
                }
            }

            is ScanUiState.Saved -> {
                LaunchedEffect(state) {
                    delay(1200)
                    viewModel.resetToScanning()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("« ${state.title} » ajouté", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CenteredSpinnerRow(label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(label)
    }
}

@Composable
private fun MovieCandidateRow(candidate: TmdbMovieSummary, onClick: () -> Unit) {
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
private fun BookCandidateRow(candidate: GoogleBookVolumeInfo, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(candidate.title) },
        supportingContent = { Text(candidate.authors.joinToString(", ")) },
        leadingContent = {
            candidate.imageLinks?.thumbnail?.let {
                AsyncImage(
                    model = it.replace("http://", "https://"),
                    contentDescription = null,
                    modifier = Modifier.size(width = 40.dp, height = 60.dp),
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun MovieConfirmPanel(state: ScanUiState.MovieConfirm, viewModel: ScanViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            state.details.posterPath?.let {
                AsyncImage(
                    model = TmdbApi.IMAGE_BASE_URL + it,
                    contentDescription = null,
                    modifier = Modifier.size(width = 70.dp, height = 105.dp),
                )
            }
            Column {
                Text(state.details.title, style = MaterialTheme.typography.titleMedium)
                state.details.releaseDate?.take(4)?.let { Text(it) }
                state.details.genres.takeIf { it.isNotEmpty() }?.let { genres ->
                    Text(genres.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text("Édition", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AVAILABLE_MOVIE_FORMATS.forEach { format ->
                FilterChip(
                    selected = state.format == format,
                    onClick = { viewModel.setMovieFormat(format) },
                    label = { Text(format) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::confirmSaveMovie) { Text(stringResource(R.string.action_save)) }
            TextButton(onClick = viewModel::resetToScanning) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@Composable
private fun BookConfirmPanel(state: ScanUiState.BookConfirm, viewModel: ScanViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            state.info.imageLinks?.thumbnail?.let {
                AsyncImage(
                    model = it.replace("http://", "https://"),
                    contentDescription = null,
                    modifier = Modifier.size(width = 70.dp, height = 105.dp),
                )
            }
            Column {
                Text(state.info.title, style = MaterialTheme.typography.titleMedium)
                if (state.info.authors.isNotEmpty()) {
                    Text(state.info.authors.joinToString(", "))
                }
                state.info.publishedDate?.take(4)?.let { Text(it) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::confirmSaveBook) { Text(stringResource(R.string.action_save)) }
            TextButton(onClick = viewModel::resetToScanning) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@Composable
private fun ManualSearchPanel(state: ScanUiState.ManualSearch, viewModel: ScanViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::updateManualQuery,
            label = { Text("Titre") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (state.suggestions.isNotEmpty()) {
            Text("Autre texte détecté sur la jaquette :", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.suggestions.forEach { suggestion ->
                    AssistChip(
                        onClick = { viewModel.selectManualSuggestion(suggestion) },
                        label = { Text(suggestion, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::runManualSearch) { Text(stringResource(R.string.action_search)) }
            TextButton(onClick = viewModel::resetToScanning) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

@Composable
private fun CameraPreview(
    active: Boolean,
    onBarcodeDetected: (String) -> Unit,
    onImageCaptureReady: (ImageCapture) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor, BarcodeAnalyzer(onBarcodeDetected))
                val capture = ImageCapture.Builder().build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                    capture,
                )
                onImageCaptureReady(capture)
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        update = { },
    )
}
