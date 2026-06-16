package com.hiraeth.flame.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hiraeth.flame.data.db.AlbumWithMedia
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.data.repository.AlbumRepository
import com.hiraeth.flame.data.repository.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MediaDetailViewModel(
    private val repository: MediaRepository,
    private val albumRepository: AlbumRepository,
    initialMediaId: Long,
    private val albumId: Long = -1L,
) : ViewModel() {

    private val _currentId = MutableStateFlow(initialMediaId)
    val currentId: StateFlow<Long> = _currentId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val media: StateFlow<MediaEntity?> = _currentId
        .flatMapLatest { id -> repository.observeById(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val albums: StateFlow<List<AlbumWithMedia>> = albumRepository.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // The list of entities we are navigating through
    val navigationItems: StateFlow<List<MediaEntity>> = if (albumId != -1L) {
        albumRepository.observeAlbumWithMedia(albumId)
            .map { album -> album?.media ?: emptyList() }
    } else {
        repository.observeAll()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val navigationIds: StateFlow<List<Long>> = navigationItems
        .map { list -> list.map { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setCurrentId(id: Long) {
        _currentId.value = id
    }

    fun saveMetadata(title: String, description: String) {
        viewModelScope.launch {
            try {
                val current = media.value ?: return@launch
                repository.update(
                    current.copy(
                        displayName = title.trim(),
                        description = description.trim(),
                    ),
                )
            } catch (_: Exception) {
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val idToDelete = _currentId.value
                val ids = navigationIds.value
                val currentIndex = ids.indexOf(idToDelete)

                repository.getById(idToDelete)?.let { repository.delete(it) }

                if (ids.size > 1) {
                    if ((currentIndex < ids.size - 1)) {
                        _currentId.value = ids[currentIndex + 1]
                    } else {
                        _currentId.value = ids[currentIndex - 1]
                    }
                } else {
                    onDone()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun addToAlbum(targetAlbumId: Long) {
        viewModelScope.launch {
            try {
                albumRepository.addToAlbum(targetAlbumId, _currentId.value)
            } catch (_: Exception) {
            }
        }
    }

    fun removeFromAlbum(onDone: () -> Unit) {
        if (albumId == -1L) return
        viewModelScope.launch {
            try {
                val idToRemove = _currentId.value
                val ids = navigationIds.value
                val currentIndex = ids.indexOf(idToRemove)

                albumRepository.removeFromAlbum(albumId, idToRemove)

                if (ids.size > 1) {
                    if ((currentIndex < ids.size - 1)) {
                        _currentId.value = ids[currentIndex + 1]
                    } else {
                        _currentId.value = ids[currentIndex - 1]
                    }
                } else {
                    onDone()
                }
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        fun factory(
            repository: MediaRepository,
            albumRepository: AlbumRepository,
            mediaId: Long,
            albumId: Long,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MediaDetailViewModel(repository, albumRepository, mediaId, albumId) as T
                }
            }
    }
}
