package com.hiraeth.flame.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.hiraeth.flame.data.db.MediaDao
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.data.local.MediaStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MediaRepository(
    private val dao: MediaDao,
    private val storage: MediaStorage,
) {

    private val fantasyPrefixes = listOf("Brave", "Dark", "Holy", "Ancient", "Cursed", "Mystic", "Royal", "Shadow", "Silver", "Golden", "Eternal", "Frozen", "Burning", "Silent", "Lost")
    private val fantasyCharacters = listOf("Knight", "Mage", "Dragon", "Elf", "King", "Queen", "Warrior", "Spirit", "Ranger", "Slayer", "Paladin", "Oracle", "Hunter", "Lord", "Reaper")

    fun generateFantasyName(): String {
        return "${fantasyPrefixes.random()} ${fantasyCharacters.random()}"
    }

    fun observeAll(): Flow<List<MediaEntity>> = dao.observeAll()

    fun observeById(id: Long): Flow<MediaEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): MediaEntity? = dao.getById(id)

    suspend fun update(entity: MediaEntity) {
        dao.update(entity.copy(modifiedAtEpochMs = System.currentTimeMillis()))
    }

    suspend fun delete(entity: MediaEntity) = withContext(Dispatchers.IO) {
        storage.resolveRelative(entity.relativePath).delete()
        dao.delete(entity)
    }

    suspend fun importFromUri(uri: Uri, suggestedName: String, description: String, isVideo: Boolean): Long =
        withContext(Dispatchers.IO) {
            val (file, mime) = storage.importFromUri(uri, isVideo)
            val dims = if (isVideo) videoDimensionsAndDuration(file) else imageDimensions(file)
            val entity = MediaEntity(
                relativePath = storage.relativeToRoot(file),
                displayName = generateFantasyName(), // Automatically name in 2 words
                mimeType = mime,
                isVideo = isVideo,
                sizeBytes = file.length(),
                width = dims.first,
                height = dims.second,
                durationMs = dims.third,
                description = description.trim(),
            )
            dao.insert(entity)
        }

    suspend fun registerCapturedPhoto(file: File, title: String, description: String): Long =
        insertFileRecord(file, isVideo = false, generateFantasyName(), description)

    suspend fun registerCapturedVideo(file: File, title: String, description: String): Long =
        insertFileRecord(file, isVideo = true, generateFantasyName(), description)

    suspend fun saveBitmapAsMedia(bitmap: Bitmap, title: String, description: String): Long = withContext(Dispatchers.IO) {
        val file = File(storage.imagesDir, "STRO_COMBINED_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        insertFileRecord(file, isVideo = false, generateFantasyName(), description)
    }

    private suspend fun insertFileRecord(file: File, isVideo: Boolean, title: String, description: String): Long =
        withContext(Dispatchers.IO) {
            val mime = if (isVideo) "video/mp4" else "image/jpeg"
            val dims = if (isVideo) videoDimensionsAndDuration(file) else imageDimensions(file)
            val entity = MediaEntity(
                relativePath = storage.relativeToRoot(file),
                displayName = title.trim(),
                mimeType = mime,
                isVideo = isVideo,
                sizeBytes = file.length(),
                width = dims.first,
                height = dims.second,
                durationMs = dims.third,
                description = description.trim(),
            )
            dao.insert(entity)
        }

    fun resolveFile(entity: MediaEntity): File = storage.resolveRelative(entity.relativePath)

    private fun imageDimensions(file: File): Triple<Int, Int, Long> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return Triple(opts.outWidth, opts.outHeight, 0L)
    }

    private fun videoDimensionsAndDuration(file: File): Triple<Int, Int, Long> {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            Triple(w, h, dur)
        } finally {
            r.release()
        }
    }
}
