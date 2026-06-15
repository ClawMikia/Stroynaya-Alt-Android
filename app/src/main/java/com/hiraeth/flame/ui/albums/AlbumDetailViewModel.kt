package com.hiraeth.flame.ui.albums

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hiraeth.flame.data.db.AlbumWithMedia
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.data.repository.AlbumRepository
import com.hiraeth.flame.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AlbumDetailViewModel(
    private val albumRepository: AlbumRepository,
    private val mediaRepository: MediaRepository,
    private val albumId: Long,
) : ViewModel() {

    private val _filter = MutableStateFlow("")
    val filter: StateFlow<String> = _filter

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting

    val albumWithMedia: StateFlow<AlbumWithMedia?> = albumRepository.observeAlbums()
        .map { list -> list.find { it.album.id == albumId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val filteredMedia: StateFlow<List<MediaEntity>> = combine(albumWithMedia, _filter) { awm, query ->
        val media = awm?.media ?: emptyList()
        if (query.isBlank()) media
        else media.filter { it.displayName.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(query: String) {
        _filter.value = query
    }

    fun updateAlbum(name: String, description: String) {
        viewModelScope.launch {
            albumRepository.updateAlbum(albumId, name, description)
        }
    }

    fun deleteAlbum(onDone: () -> Unit) {
        viewModelScope.launch {
            albumRepository.deleteAlbum(albumId)
            onDone()
        }
    }

    fun removeFromAlbum(mediaId: Long) {
        viewModelScope.launch {
            albumRepository.removeFromAlbum(albumId, mediaId)
        }
    }

    fun exportToZip(context: Context, destUri: Uri, onDone: (Boolean) -> Unit) {
        val awm = albumWithMedia.value ?: return
        viewModelScope.launch {
            _isExporting.value = true
            val success = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(destUri)?.use { os ->
                        ZipOutputStream(BufferedOutputStream(os)).use { zos ->
                            awm.media.forEach { media ->
                                val file = mediaRepository.resolveFile(media)
                                if (file.exists()) {
                                    val entry = ZipEntry(file.name)
                                    zos.putNextEntry(entry)
                                    file.inputStream().use { input ->
                                        input.copyTo(zos)
                                    }
                                    zos.closeEntry()
                                }
                            }
                        }
                    }
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            _isExporting.value = false
            onDone(success)
        }
    }

    companion object {
        fun factory(albumRepository: AlbumRepository, mediaRepository: MediaRepository, albumId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AlbumDetailViewModel(albumRepository, mediaRepository, albumId) as T
                }
            }
    }
}
