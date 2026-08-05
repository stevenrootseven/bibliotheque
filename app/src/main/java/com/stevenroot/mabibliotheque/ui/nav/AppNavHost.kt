package com.stevenroot.mabibliotheque.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stevenroot.mabibliotheque.MaBibliothequeApplication
import com.stevenroot.mabibliotheque.ui.collection.CollectionScreen
import com.stevenroot.mabibliotheque.ui.collection.CollectionViewModel
import com.stevenroot.mabibliotheque.ui.detail.BookDetailScreen
import com.stevenroot.mabibliotheque.ui.detail.BookDetailViewModel
import com.stevenroot.mabibliotheque.ui.detail.MovieDetailScreen
import com.stevenroot.mabibliotheque.ui.detail.MovieDetailViewModel
import com.stevenroot.mabibliotheque.ui.scan.ScanScreen
import com.stevenroot.mabibliotheque.ui.scan.ScanViewModel
import com.stevenroot.mabibliotheque.ui.settings.SettingsScreen
import com.stevenroot.mabibliotheque.ui.settings.SettingsViewModel

private object Routes {
    const val COLLECTION = "collection"
    const val SCAN = "scan"
    const val SETTINGS = "settings"
    const val MOVIE_DETAIL = "movie/{id}"
    const val BOOK_DETAIL = "book/{id}"
    fun movieDetail(id: Long) = "movie/$id"
    fun bookDetail(id: Long) = "book/$id"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as MaBibliothequeApplication

    NavHost(navController = navController, startDestination = Routes.COLLECTION) {
        composable(Routes.COLLECTION) {
            val vm: CollectionViewModel = viewModel(
                factory = CollectionViewModel.Factory(app.movieRepository, app.bookRepository),
            )
            CollectionScreen(
                viewModel = vm,
                onScan = { navController.navigate(Routes.SCAN) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenMovie = { id -> navController.navigate(Routes.movieDetail(id)) },
                onOpenBook = { id -> navController.navigate(Routes.bookDetail(id)) },
            )
        }
        composable(Routes.SCAN) {
            val vm: ScanViewModel = viewModel(
                factory = ScanViewModel.Factory(app.movieRepository, app.bookRepository, app.settingsRepository),
            )
            ScanScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(app.settingsRepository, app.collectionImporter),
            )
            SettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(
            Routes.MOVIE_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: return@composable
            val vm: MovieDetailViewModel = viewModel(
                factory = MovieDetailViewModel.Factory(id, app.movieRepository),
            )
            MovieDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(
            Routes.BOOK_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: return@composable
            val vm: BookDetailViewModel = viewModel(
                factory = BookDetailViewModel.Factory(id, app.bookRepository),
            )
            BookDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}
