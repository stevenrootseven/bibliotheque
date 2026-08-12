package com.stevenroot.mabibliotheque

import android.app.Application
import com.stevenroot.mabibliotheque.data.local.AppDatabase
import com.stevenroot.mabibliotheque.data.remote.NetworkModule
import com.stevenroot.mabibliotheque.data.remote.SmbClient
import com.stevenroot.mabibliotheque.data.repository.BookRepository
import com.stevenroot.mabibliotheque.data.repository.CollectionImporter
import com.stevenroot.mabibliotheque.data.repository.DigitalLibraryScanner
import com.stevenroot.mabibliotheque.data.repository.MovieRepository
import com.stevenroot.mabibliotheque.data.repository.NetworkShareSettingsRepository
import com.stevenroot.mabibliotheque.data.repository.SeriesRepository
import com.stevenroot.mabibliotheque.data.repository.SeriesScanner
import com.stevenroot.mabibliotheque.data.repository.SettingsRepository

class MaBibliothequeApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var networkShareSettingsRepository: NetworkShareSettingsRepository
        private set

    lateinit var smbClient: SmbClient
        private set

    lateinit var movieRepository: MovieRepository
        private set

    lateinit var bookRepository: BookRepository
        private set

    lateinit var collectionImporter: CollectionImporter
        private set

    lateinit var digitalLibraryScanner: DigitalLibraryScanner
        private set

    lateinit var seriesRepository: SeriesRepository
        private set

    lateinit var seriesScanner: SeriesScanner
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.build(this)
        settingsRepository = SettingsRepository(this)
        networkShareSettingsRepository = NetworkShareSettingsRepository(this)
        smbClient = SmbClient()
        val network = NetworkModule(settingsRepository)
        movieRepository = MovieRepository(database.movieDao(), network.tmdbApi, network.upcItemDbApi)
        bookRepository = BookRepository(database.bookDao(), network.bnfApi, network.openLibraryApi)
        collectionImporter = CollectionImporter(this, database.movieDao())
        digitalLibraryScanner = DigitalLibraryScanner(smbClient, database.movieDao(), movieRepository, settingsRepository)
        seriesRepository = SeriesRepository(database.seriesDao())
        seriesScanner = SeriesScanner(smbClient, database.seriesDao())
    }
}
