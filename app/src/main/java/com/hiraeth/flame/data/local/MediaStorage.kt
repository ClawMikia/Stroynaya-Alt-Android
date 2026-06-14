package com.hiraeth.flame.data.local

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * App-specific media root under [Context.getFilesDir] — avoids exposing user media publicly
 * and stays scoped-storage compliant (no broad MANAGE_EXTERNAL_STORAGE).
 */
class MediaStorage(private val context: Context) {

    val root: File = File(context.filesDir, "hiraeth_media_alt").also { it.mkdirs() }

    val imagesDir: File = File(root, "images").also { it.mkdirs() }
    val videosDir: File = File(root, "videos").also { it.mkdirs() }
    /** Exported short vertical compositions (reels-style). */
    val reelsDir: File = File(root, "reels").also { it.mkdirs() }

    fun relativeToRoot(file: File): String = file.relativeTo(root).path.replace('\\', '/')

    fun resolveRelative(relativePath: String): File = File(root, relativePath)

    /**
     * Copy content from a content [Uri] (gallery, files) into app storage.
     * @return Pair of absolute [File] and guessed MIME type.
     */
    fun importFromUri(uri: Uri, isVideo: Boolean): Pair<File, String> {
        val ext = if (isVideo) "mp4" else "jpg"
        val dir = if (isVideo) videosDir else imagesDir
        val out = File(dir, "${UUID.randomUUID()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        } ?: error("Unable to open $uri")
        val mime = context.contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: if (isVideo) "video/mp4" else "image/jpeg"
        return out to mime
    }

    fun createCameraPhotoFile(): File =
        File(imagesDir, "CAM_${System.currentTimeMillis()}.jpg")

    fun createCameraVideoFile(): File =
        File(videosDir, "VID_${System.currentTimeMillis()}.mp4")
}
