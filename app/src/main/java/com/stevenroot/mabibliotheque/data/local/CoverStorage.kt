package com.stevenroot.mabibliotheque.data.local

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** Permanent on-device home for cover images (imported or captured) — internal storage, not
 * cache, so it isn't cleared by "free up space" and isn't visible/removable by the user via a
 * file manager the way external storage would be. */
object CoverStorage {
    private const val DIR_NAME = "covers"

    private fun coversDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun copyInto(context: Context, source: File, destFileName: String): String {
        val dest = File(coversDir(context), destFileName)
        source.copyTo(dest, overwrite = true)
        return dest.absolutePath
    }

    /** Used when the user replaces/adds a cover by taking a photo (system camera returns a
     * downscaled preview Bitmap, not a full-resolution file). */
    fun saveBitmap(context: Context, bitmap: Bitmap, destFileName: String): String {
        val dest = File(coversDir(context), destFileName)
        FileOutputStream(dest).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        return dest.absolutePath
    }

    /** Used when the user picks a cover from the gallery. */
    fun saveFromUri(context: Context, uri: Uri, destFileName: String): String {
        val dest = File(coversDir(context), destFileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest.absolutePath
    }
}
