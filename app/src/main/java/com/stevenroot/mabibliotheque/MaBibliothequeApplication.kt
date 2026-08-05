package com.stevenroot.mabibliotheque

import android.app.Application
import com.stevenroot.mabibliotheque.data.local.AppDatabase
import com.stevenroot.mabibliotheque.data.remote.NetworkModule
import com.stevenroot.mabibliotheque.data.repository.BookRepository
import com.stevenroot.mabibliotheque.data.repository.CollectionImporter
import com.stevenroot.mabibliotheque.data.repository.MovieRepository
import com.stevenroot.mabibliotheque.data.repository.SettingsRepository

class MaBibliothequeApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var movieRepository: MovieRepository
        private set

    lateinit var bookRepository: BookRepository
        private set

    lateinit var collectionImporter: CollectionImporter
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.build(this)
        settingsRepository = SettingsRepository(this)
        val network = NetworkModule(settingsRepository)
        movieRepository = MovieRepository(database.movieDao(), network.tmdbApi, network.upcItemDbApi)
        bookRepository = BookRepository(database.bookDao(), network.googleBooksApi)
        collectionImporter = CollectionImporter(this, database.movieDao())
    }
}
