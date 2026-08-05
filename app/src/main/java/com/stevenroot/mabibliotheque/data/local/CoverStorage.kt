package com.stevenroot.mabibliotheque.data.local

import android.content.Context
import java.io.File

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
}
