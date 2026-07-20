package com.hiraeth.flame.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat {
    PNG, JPG
}

object ExportHelper {

    suspend fun exportSingleImage(
        context: Context,
        entity: MediaEntity,
        repository: MediaRepository,
        format: ExportFormat,
        quality: Int,
        destUri: Uri,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = repository.resolveFile(entity)
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext false
            context.contentResolver.openOutputStream(destUri)?.use { os ->
                when (format) {
                    ExportFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                    ExportFormat.JPG -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, os)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportMultipleAsZip(
        context: Context,
        entities: List<MediaEntity>,
        repository: MediaRepository,
        format: ExportFormat,
        quality: Int,
        destUri: Uri,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(destUri)?.use { os ->
                ZipOutputStream(BufferedOutputStream(os)).use { zos ->
                    for (entity in entities) {
                        if (entity.isVideo) continue
                        val file = repository.resolveFile(entity)
                        if (!file.exists()) continue

                        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                        val ext = when (format) {
                            ExportFormat.PNG -> ".png"
                            ExportFormat.JPG -> ".jpg"
                        }
                        val entryName = "${entity.displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}$ext"
                        zos.putNextEntry(ZipEntry(entryName))
                        when (format) {
                            ExportFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, zos)
                            ExportFormat.JPG -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, zos)
                        }
                        zos.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
