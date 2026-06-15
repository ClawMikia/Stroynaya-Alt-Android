package com.hiraeth.flame.data.repository

import com.hiraeth.flame.data.db.AlbumDao
import com.hiraeth.flame.data.db.AlbumEntity
import com.hiraeth.flame.data.db.AlbumMediaCrossRef
import com.hiraeth.flame.data.db.AlbumWithMedia
import kotlinx.coroutines.flow.Flow

class AlbumRepository(
    private val albumDao: AlbumDao,
) {
    fun observeAlbums(): Flow<List<AlbumWithMedia>> = albumDao.observeAlbumsWithMedia()

    fun observeAlbumWithMedia(id: Long): Flow<AlbumWithMedia?> = albumDao.observeAlbumWithMedia(id)

    suspend fun createAlbum(name: String, description: String = ""): Long {
        val album = AlbumEntity(name = name.trim(), description = description.trim())
        return albumDao.insertAlbum(album)
    }

    suspend fun updateAlbum(id: Long, name: String, description: String) {
        val album = AlbumEntity(id = id, name = name.trim(), description = description.trim())
        albumDao.updateAlbum(album)
    }

    suspend fun addToAlbum(albumId: Long, mediaId: Long) {
        albumDao.linkMedia(AlbumMediaCrossRef(albumId = albumId, mediaId = mediaId))
    }

    suspend fun removeFromAlbum(albumId: Long, mediaId: Long) {
        albumDao.unlinkMedia(albumId, mediaId)
    }

    suspend fun deleteAlbum(id: Long) {
        albumDao.deleteAlbumById(id)
    }
}
