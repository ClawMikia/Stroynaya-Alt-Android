package com.hiraeth.flame.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Transaction
    @Query("SELECT * FROM albums ORDER BY name COLLATE NOCASE ASC")
    fun observeAlbumsWithMedia(): Flow<List<AlbumWithMedia>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity): Long

    @androidx.room.Update
    suspend fun updateAlbum(album: AlbumEntity)

    @Transaction
    @Query("SELECT * FROM albums WHERE id = :id")
    fun observeAlbumWithMedia(id: Long): Flow<AlbumWithMedia?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkMedia(crossRef: AlbumMediaCrossRef)

    @Query("DELETE FROM album_media WHERE albumId = :albumId AND mediaId = :mediaId")
    suspend fun unlinkMedia(albumId: Long, mediaId: Long)

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun deleteAlbumById(id: Long)

    @Query("SELECT albumId FROM album_media WHERE mediaId = :mediaId")
    suspend fun getAlbumIdsForMedia(mediaId: Long): List<Long>

    @Query("DELETE FROM album_media WHERE mediaId = :mediaId")
    suspend fun unlinkMediaFromAllAlbums(mediaId: Long)

    @Query("DELETE FROM album_media WHERE mediaId IN (:mediaIds)")
    suspend fun unlinkMediaFromAllAlbumsBulk(mediaIds: List<Long>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkMediaBulk(crossRefs: List<AlbumMediaCrossRef>)

    @Query("DELETE FROM album_media WHERE albumId = :albumId AND mediaId IN (:mediaIds)")
    suspend fun unlinkMediaBulk(albumId: Long, mediaIds: List<Long>)
}
