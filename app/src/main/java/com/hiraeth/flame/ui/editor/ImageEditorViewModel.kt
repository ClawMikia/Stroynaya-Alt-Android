package com.hiraeth.flame.ui.editor

import android.graphics.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.data.local.MediaStorage
import com.hiraeth.flame.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

class ImageEditorViewModel(
    private val repository: MediaRepository,
    private val storage: MediaStorage,
    private val mediaId: Long,
) : ViewModel() {

    private val _media = MutableStateFlow<MediaEntity?>(null)
    val media: StateFlow<MediaEntity?> = _media

    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap: StateFlow<Bitmap?> = _originalBitmap

    init {
        loadMedia()
    }

    private fun loadMedia() {
        viewModelScope.launch {
            val item = repository.getById(mediaId)
            _media.value = item
            if (item != null) {
                val file = storage.resolveRelative(item.relativePath)
                withContext(Dispatchers.IO) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    _originalBitmap.value = bitmap
                }
            }
        }
    }

    fun saveEditedBitmap(bitmap: Bitmap, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = _media.value ?: return@launch
            val file = storage.resolveRelative(item.relativePath)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            // Update the database to notify observers
            repository.update(item.copy(width = bitmap.width, height = bitmap.height, sizeBytes = file.length()))
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    companion object {
        fun factory(repository: MediaRepository, storage: MediaStorage, mediaId: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ImageEditorViewModel::class.java)) {
                    return ImageEditorViewModel(repository, storage, mediaId) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
