package com.stevenroot.mabibliotheque.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stevenroot.mabibliotheque.R
import com.stevenroot.mabibliotheque.data.local.BookEntity
import com.stevenroot.mabibliotheque.data.local.MovieCopyEntity
import com.stevenroot.mabibliotheque.data.local.MovieWithCopies
import com.stevenroot.mabibliotheque.data.local.SeriesWithEpisodes
import java.io.File

private enum class ViewMode { GRID, LIST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    viewModel: CollectionViewModel,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMovie: (Long) -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    // Saveable, not plain remember: navigating to Scan/Settings and back disposes and
    // re-composes this screen, which would otherwise reset the tab to Films every time.
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }
    var searchVisible by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val movies by viewModel.movies.collectAsState()
    val books by viewModel.books.collectAsState()
    val series by viewModel.series.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) viewModel.onSearchQueryChanged("")
                        }) {
                            Icon(
                                if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = stringResource(R.string.action_search),
                            )
                        }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.Filled.Sort, contentDescription = "Trier")
                            }
                            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                                SortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label) },
                                        onClick = {
                                            viewModel.onSortModeChanged(mode)
                                            sortMenuExpanded = false
                                        },
                                        leadingIcon = if (mode == sortMode) {
                                            { Icon(Icons.Filled.Sort, contentDescription = null) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID }) {
                            Icon(
                                if (viewMode == ViewMode.GRID) Icons.Filled.List else Icons.Filled.GridView,
                                contentDescription = null,
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                        }
                    },
                )
                if (searchVisible) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("Titre, année, acteur, type…") },
                        singleLine = true,
                    )
                }
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.tab_movies)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.tab_books)) },
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(stringResource(R.string.tab_series)) },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onScan) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_scan))
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (selectedTab == 0) {
                if (movies.isEmpty()) {
                    EmptyState(stringResource(R.string.empty_collection_movies))
                } else if (viewMode == ViewMode.GRID) {
                    MovieGrid(movies, onOpenMovie)
                } else {
                    MovieList(movies, onOpenMovie)
                }
            } else if (selectedTab == 1) {
                if (books.isEmpty()) {
                    EmptyState(stringResource(R.string.empty_collection_books))
                } else if (viewMode == ViewMode.GRID) {
                    BookGrid(books, onOpenBook)
                } else {
                    BookList(books, onOpenBook)
                }
            } else {
                if (series.isEmpty()) {
                    EmptyState("Aucune série pour le moment. Configure et lance un scan dans Réglages.")
                } else if (viewMode == ViewMode.GRID) {
                    SeriesGrid(series, onOpenSeries)
                } else {
                    SeriesList(series, onOpenSeries)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun representativeImage(entry: MovieWithCopies): Any? =
    entry.copies.firstOrNull { it.frontCoverPath != null }?.frontCoverPath?.let { File(it) } ?: entry.movie.posterUrl

private fun formatBadges(copies: List<MovieCopyEntity>): List<String> =
    copies.map { it.format }.distinct()

@Composable
private fun MovieGrid(movies: List<MovieWithCopies>, onOpen: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(movies, key = { it.movie.id }) { entry ->
            PosterCard(
                title = entry.movie.title,
                image = representativeImage(entry),
                badges = formatBadges(entry.copies),
                onClick = { onOpen(entry.movie.id) },
            )
        }
    }
}

@Composable
private fun BookGrid(books: List<BookEntity>, onOpen: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(books, key = { it.id }) { book ->
            PosterCard(
                title = book.title,
                image = book.coverUrl,
                onClick = { onOpen(book.id) },
            )
        }
    }
}

@Composable
private fun MovieList(movies: List<MovieWithCopies>, onOpen: (Long) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(movies, key = { it.movie.id }) { entry ->
            val movie = entry.movie
            val badges = formatBadges(entry.copies)
            ListRow(
                title = movie.title,
                subtitle = (listOfNotNull(movie.year?.toString(), movie.genres) + badges).joinToString(" · "),
                image = representativeImage(entry),
                onClick = { onOpen(movie.id) },
            )
        }
    }
}

@Composable
private fun BookList(books: List<BookEntity>, onOpen: (Long) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(books, key = { it.id }) { book ->
            ListRow(
                title = book.title,
                subtitle = listOfNotNull(book.publishedYear?.toString(), book.authors).joinToString(" · "),
                image = book.coverUrl,
                onClick = { onOpen(book.id) },
            )
        }
    }
}

@Composable
private fun SeriesGrid(series: List<SeriesWithEpisodes>, onOpen: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(series, key = { it.series.id }) { entry ->
            PosterCard(
                title = entry.series.name,
                image = entry.series.coverPath?.let { File(it) },
                onClick = { onOpen(entry.series.id) },
            )
        }
    }
}

@Composable
private fun SeriesList(series: List<SeriesWithEpisodes>, onOpen: (Long) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(series, key = { it.series.id }) { entry ->
            ListRow(
                title = entry.series.name,
                subtitle = "${entry.episodes.size} fichier(s)",
                image = entry.series.coverPath?.let { File(it) },
                onClick = { onOpen(entry.series.id) },
            )
        }
    }
}

@Composable
private fun ListRow(title: String, subtitle: String, image: Any?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PosterCard(title: String, image: Any?, onClick: () -> Unit, badges: List<String> = emptyList()) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = title,
                    modifier = Modifier.align(Alignment.Center).padding(6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (badges.size > 1) {
            Text(
                text = badges.joinToString(" + "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
