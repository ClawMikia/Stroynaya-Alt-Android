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

    suspend fun getAlbumIdsForMedia(mediaId: Long): List<Long> {
        return albumDao.getAlbumIdsForMedia(mediaId)
    }

    suspend fun unlinkFromAllAlbums(mediaId: Long) {
        albumDao.unlinkMediaFromAllAlbums(mediaId)
    }

    suspend fun unlinkFromAllAlbumsBulk(mediaIds: List<Long>) {
        albumDao.unlinkMediaFromAllAlbumsBulk(mediaIds)
    }

    suspend fun moveToAlbum(sourceAlbumId: Long, targetAlbumId: Long, mediaIds: List<Long>) {
        if (sourceAlbumId == targetAlbumId) return
        albumDao.unlinkMediaBulk(sourceAlbumId, mediaIds)
        val crossRefs = mediaIds.map { AlbumMediaCrossRef(albumId = targetAlbumId, mediaId = it) }
        albumDao.linkMediaBulk(crossRefs)
    }

    suspend fun moveToNoAlbum(sourceAlbumId: Long, mediaIds: List<Long>) {
        albumDao.unlinkMediaBulk(sourceAlbumId, mediaIds)
    }

    suspend fun bulkAddToAlbum(albumId: Long, mediaIds: List<Long>) {
        val crossRefs = mediaIds.map { AlbumMediaCrossRef(albumId = albumId, mediaId = it) }
        albumDao.linkMediaBulk(crossRefs)
    }
}
