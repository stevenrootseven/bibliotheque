package com.stevenroot.mabibliotheque.ui.detail

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevenroot.mabibliotheque.data.local.CoverStorage
import com.stevenroot.mabibliotheque.data.local.SeriesWithEpisodes
import com.stevenroot.mabibliotheque.data.repository.SeriesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SeriesDetailViewModel(
    seriesId: Long,
    private val seriesRepository: SeriesRepository,
) : ViewModel() {

    val series: StateFlow<SeriesWithEpisodes?> = seriesRepository.observeById(seriesId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun replaceCoverFromBitmap(context: Context, bitmap: Bitmap) {
        val id = series.value?.series?.id ?: return
        viewModelScope.launch {
            val path = CoverStorage.saveBitmap(context, bitmap, "series_${id}_${System.currentTimeMillis()}.jpg")
            seriesRepository.updateCoverPath(id, path)
        }
    }

    fun replaceCoverFromUri(context: Context, uri: Uri) {
        val id = series.value?.series?.id ?: return
        viewModelScope.launch {
            val path = CoverStorage.saveFromUri(context, uri, "series_${id}_${System.currentTimeMillis()}.jpg")
            seriesRepository.updateCoverPath(id, path)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val current = series.value?.series ?: return
        viewModelScope.launch {
            seriesRepository.delete(current)
            onDeleted()
        }
    }

    class Factory(
        private val seriesId: Long,
        private val seriesRepository: SeriesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SeriesDetailViewModel(seriesId, seriesRepository) as T
    }
}
