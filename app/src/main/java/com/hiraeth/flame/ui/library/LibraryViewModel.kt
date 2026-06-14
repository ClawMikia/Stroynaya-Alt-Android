package com.hiraeth.flame.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.data.repository.MediaRepository
import com.hiraeth.flame.domain.LibrarySort
import com.hiraeth.flame.domain.LibraryViewMode
import com.hiraeth.flame.domain.MediaTypeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds the first four inputs for [LibraryViewModel.items] (nested [combine] avoids 7-way overload issues on K2). */
private data class LibraryMainInputs(
    val list: List<MediaEntity>,
    val query: String,
    val typeFilter: MediaTypeFilter,
    val sort: LibrarySort,
)

class LibraryViewModel(
    private val repository: MediaRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val typeFilter = MutableStateFlow(MediaTypeFilter.All)
    private val sort = MutableStateFlow(LibrarySort.DateNewest)
    private val viewMode = MutableStateFlow(LibraryViewMode.Grid)
    private val tagFilter = MutableStateFlow("")

    val viewModeState: StateFlow<LibraryViewMode> = viewMode

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
        fun factory(repository: MediaRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
                    return LibraryViewModel(repository) as T
                }
            }
    }
}
