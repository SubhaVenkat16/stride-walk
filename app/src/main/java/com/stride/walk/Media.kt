package com.stride.walk

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun isVideo(path: String): Boolean {
    val ext = path.substringAfterLast('.', "").lowercase()
    return ext in setOf("mp4", "3gp", "mkv", "webm", "mov")
}

fun newMediaFile(ctx: Context, ext: String): File {
    val dir = File(ctx.filesDir, "media")
    dir.mkdirs()
    return File(dir, "m_${System.currentTimeMillis()}_${(0..999).random()}.$ext")
}

fun uriFor(ctx: Context, file: File): Uri =
    FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)

/** Copies a picked photo/video into the app's own storage so it stays available. */
fun copyIn(ctx: Context, uri: Uri): String? = try {
    val type = ctx.contentResolver.getType(uri) ?: ""
    val ext = if (type.startsWith("video")) "mp4" else "jpg"
    val out = newMediaFile(ctx, ext)
    ctx.contentResolver.openInputStream(uri)?.use { input ->
        out.outputStream().use { o -> input.copyTo(o) }
    } ?: throw IllegalStateException("no stream")
    out.absolutePath
} catch (e: Exception) {
    null
}

fun openMedia(ctx: Context, path: String) {
    try {
        val uri = uriFor(ctx, File(path))
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, if (isVideo(path)) "video/*" else "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(i)
    } catch (e: Exception) {
        // No app available to open it; ignore.
    }
}

object BitmapUtil {
    fun decode(path: String, maxDim: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(path, opts)
        val rotation = when (
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (bmp != null && rotation != 0f) {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
        } else {
            bmp
        }
    } catch (e: Throwable) {
        null
    }

    fun videoThumb(path: String): Bitmap? = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(path)
        val b = r.getFrameAtTime(0)
        r.release()
        b
    } catch (e: Throwable) {
        null
    }
}
