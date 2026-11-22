package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.security.MessageDigest

object ImageUtils {

    /** Copies any picked image into app cache so access never expires. */
    fun copyToCache(context: Context, source: Uri): Uri? {
        return try {
            val file = File(context.cacheDir, "paysure_${System.currentTimeMillis()}.img")
            val input = context.contentResolver.openInputStream(source) ?: return null
            input.use { stream -> file.outputStream().use { stream.copyTo(it) } }
            Uri.fromFile(file)
        } catch (e: Exception) {
            null
        }
    }

    /** Removes scan images left behind by earlier sessions. */
    fun cleanCache(context: Context) {
        try {
            context.cacheDir.listFiles { f -> f.name.startsWith("paysure_") }
                ?.forEach { it.delete() }
        } catch (_: Exception) {
        }
    }

    fun sha256(context: Context, uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Decodes with down-sampling and applies EXIF rotation (camera photos). */
    fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap

        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private val editorRegex = Regex(
        """(?i)photoshop|lightroom|gimp|canva|picsart|snapseed|pixlr|photoroom|fotor|paint\.net|facetune|remini|picmonkey|befunky|inshot|vsco|editor"""
    )

    /** Returns the editing software name if EXIF says an editor saved the file. */
    fun editorTag(context: Context, uri: Uri): String? {
        return try {
            val software = context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttribute(ExifInterface.TAG_SOFTWARE)
            }
            if (software != null && editorRegex.containsMatchIn(software)) software.trim() else null
        } catch (e: Exception) {
            null
        }
    }
}
