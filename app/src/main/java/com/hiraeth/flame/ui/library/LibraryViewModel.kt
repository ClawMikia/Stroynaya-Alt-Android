package com.hiraeth.flame.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.data.repository.MediaRepository
import com.hiraeth.flame.domain.LibrarySort
import com.hiraeth.flame.domain.LibraryViewMode
import com.hiraeth.flame.domain.MediaTypeFilter
import com.hiraeth.flame.data.repository.AlbumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class LibraryDisplayItem {
    data class Header(val title: String, val id: String, val count: Int) : LibraryDisplayItem()
    data class Media(val entity: MediaEntity) : LibraryDisplayItem()
}

/** Holds the first four inputs for [LibraryViewModel.items] (nested [combine] avoids 7-way overload issues on K2). */
private data class LibraryMainInputs(
    val list: List<MediaEntity>,
    val query: String,
    val typeFilter: MediaTypeFilter,
    val sort: LibrarySort,
)

class LibraryViewModel(
    private val repository: MediaRepository,
    private val albumRepository: AlbumRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val typeFilter = MutableStateFlow(MediaTypeFilter.All)
    private val sort = MutableStateFlow(LibrarySort.DateNewest)
    private val viewMode = MutableStateFlow(LibraryViewMode.List)
    private val tagFilter = MutableStateFlow("")

    val viewModeState: StateFlow<LibraryViewMode> = viewMode

    private val expandedHeaders = MutableStateFlow<Set<String>>(emptySet())

    fun toggleHeader(headerId: String) {
        val current = expandedHeaders.value
        if (current.contains(headerId)) {
            expandedHeaders.value = current - headerId
        } else {
            expandedHeaders.value = current + headerId
        }
    }

    val groupedItems: StateFlow<List<LibraryDisplayItem>> = combine(
        combine(
            repository.observeAll(),
            _query,
            typeFilter,
            sort,
        ) { list, q, tf, s ->
            LibraryMainInputs(list, q, tf, s)
        },
        tagFilter,
        albumRepository.observeAlbums(),
        expandedHeaders
    ) { main, tag, albums, expanded ->
        val list = main.list
        val q = main.query
        val tf = main.typeFilter
        val s = main.sort

        val filteredList = list.asSequence()
            .filter { entity ->
                if (q.isBlank()) true else entity.displayName.contains(q, ignoreCase = true)
            }
            .filter { entity ->
                if (tag.isBlank()) true else entity.description.contains(tag, ignoreCase = true)
            }
            .filter { entity ->
                when (tf) {
                    MediaTypeFilter.All -> true
                    MediaTypeFilter.ImagesOnly -> !entity.isVideo
                }
            }
            .sortedWith(comparatorFor(s))
            .toList()

        val result = mutableListOf<LibraryDisplayItem>()
        val processedMediaIds = mutableSetOf<Long>()

        // Group by albums
        albums.forEach { awm ->
            val albumMedia = awm.media.filter { m -> filteredList.any { it.id == m.id } }
            if (albumMedia.isNotEmpty()) {
                val headerId = "album_${awm.album.id}"
                result.add(LibraryDisplayItem.Header(awm.album.name, headerId, albumMedia.size))
                if (expanded.contains(headerId)) {
                    albumMedia.forEach {
                        result.add(LibraryDisplayItem.Media(it))
                        processedMediaIds.add(it.id)
                    }
                }
            }
        }

        // Not in any album
        val notInAlbum = filteredList.filter { it.id !in processedMediaIds }
        if (notInAlbum.isNotEmpty()) {
            val headerId = "no_album"
            result.add(LibraryDisplayItem.Header("Not part of any album", headerId, notInAlbum.size))
            if (expanded.contains(headerId)) {
                notInAlbum.forEach {
                    result.add(LibraryDisplayItem.Media(it))
                }
            }
        }

        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: StateFlow<List<MediaEntity>> = combine(
        combine(
            repository.observeAll(),
            _query,
            typeFilter,
            sort,
        ) { list, q, tf, s ->
            LibraryMainInputs(list, q, tf, s)
        },
        tagFilter
    ) { main, tag ->
        val list = main.list
        val q = main.query
        val tf = main.typeFilter
        val s = main.sort
        list.asSequence()
            .filter { entity ->
                if (q.isBlank()) true else entity.displayName.contains(q, ignoreCase = true)
            }
            .filter { entity ->
                if (tag.isBlank()) true else entity.description.contains(tag, ignoreCase = true)
            }
            .filter { entity ->
                when (tf) {
                    MediaTypeFilter.All -> true
                    MediaTypeFilter.ImagesOnly -> !entity.isVideo
                }
            }
            .sortedWith(comparatorFor(s))
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setTypeFilter(value: MediaTypeFilter) {
        typeFilter.value = value
    }

    fun setSort(value: LibrarySort) {
        sort.value = value
    }

    fun toggleViewMode() {
        viewMode.value = if (viewMode.value == LibraryViewMode.Grid) LibraryViewMode.List else LibraryViewMode.Grid
    }

    fun setTagFilter(value: String) {
        tagFilter.value = value
    }

    fun delete(entity: MediaEntity) {
        viewModelScope.launch { repository.delete(entity) }
    }

    private fun comparatorFor(sort: LibrarySort): Comparator<MediaEntity> =
        when (sort) {
            LibrarySort.DateNewest -> compareByDescending { it.modifiedAtEpochMs }
            LibrarySort.DateOldest -> compareBy { it.modifiedAtEpochMs }
            LibrarySort.NameAZ -> compareBy { it.displayName.lowercase() }
            LibrarySort.NameZA -> compareByDescending { it.displayName.lowercase() }
            LibrarySort.SizeLargest -> compareByDescending { it.sizeBytes }
            LibrarySort.SizeSmallest -> compareBy { it.sizeBytes }
        }

    companion object {
        fun factory(repository: MediaRepository, albumRepository: AlbumRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
                    return LibraryViewModel(repository, albumRepository) as T
                }
            }
    }
}
