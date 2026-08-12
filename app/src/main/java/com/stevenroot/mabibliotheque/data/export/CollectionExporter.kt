package com.stevenroot.mabibliotheque.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.stevenroot.mabibliotheque.data.local.BookEntity
import com.stevenroot.mabibliotheque.data.local.MovieWithCopies
import com.stevenroot.mabibliotheque.data.local.SeriesWithEpisodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Builds shareable exports of the whole collection — CSV for spreadsheets, PDF as a catalog to
 * hand to friends who don't have the app. The PDF is drawn page by page with android.graphics.pdf's
 * built-in PdfDocument rather than a PDF library: a simple catalog layout (rows of cover + text)
 * doesn't need more than a Canvas, same reasoning that kept the crop tool free of the ML Kit
 * document scanner elsewhere in this app.
 */
object CollectionExporter {

    private const val PAGE_WIDTH = 595 // A4 at 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f
    private const val ROW_HEIGHT = 76f
    // 2x the on-page point size below is plenty sharp for a thumbnail and keeps embedded image
    // data small — PdfDocument stores bitmaps close to raw, not re-compressed on write.
    private const val THUMB_PIXEL_WIDTH = 100
    private const val THUMB_PIXEL_HEIGHT = 150
    private const val THUMB_WIDTH = 50f
    private const val THUMB_HEIGHT = 75f

    suspend fun exportCsv(
        context: Context,
        movies: List<MovieWithCopies>,
        books: List<BookEntity>,
        series: List<SeriesWithEpisodes>,
    ): File = withContext(Dispatchers.IO) {
        val file = File(exportsDir(context), "ma_bibliotheque_${timestamp()}.csv")
        file.bufferedWriter(Charsets.UTF_8).use { out ->
            out.write("﻿") // BOM so Excel detects UTF-8 accents correctly
            out.write(csvRow("Type", "Titre", "Année", "Détails", "Code-barres / ISBN", "Date d'ajout"))
            movies.sortedBy { it.movie.title }.forEach { entry ->
                val movie = entry.movie
                val formats = entry.copies.map { it.format }.distinct().joinToString(" / ")
                val details = listOfNotNull(
                    movie.director?.let { "Réalisateur : $it" },
                    movie.genres?.let { "Genres : $it" },
                    formats.takeIf { it.isNotBlank() }?.let { "Formats : $it" },
                ).joinToString(" | ")
                val barcode = entry.copies.mapNotNull { it.barcode }.firstOrNull().orEmpty()
                out.write(
                    csvRow(
                        "Film", movie.title, movie.year?.toString().orEmpty(), details, barcode,
                        formatDate(movie.dateAddedMillis),
                    ),
                )
            }
            books.sortedBy { it.title }.forEach { book ->
                val details = listOfNotNull(
                    book.authors?.let { "Auteur(s) : $it" },
                    book.publisher?.let { "Éditeur : $it" },
                    book.pageCount?.let { "Pages : $it" },
                ).joinToString(" | ")
                out.write(
                    csvRow(
                        "Livre", book.title, book.publishedYear?.toString().orEmpty(), details,
                        book.isbn13 ?: book.isbn10 ?: book.barcode, formatDate(book.dateAddedMillis),
                    ),
                )
            }
            series.sortedBy { it.series.name }.forEach { entry ->
                out.write(
                    csvRow(
                        "Série", entry.series.name, "", "${entry.episodes.size} fichier(s)", "",
                        formatDate(entry.series.dateAddedMillis),
                    ),
                )
            }
        }
        file
    }

    suspend fun exportPdf(
        context: Context,
        movies: List<MovieWithCopies>,
        books: List<BookEntity>,
        series: List<SeriesWithEpisodes>,
    ): File = withContext(Dispatchers.IO) {
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; isFakeBoldText = true; color = Color.BLACK }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.DKGRAY }
        val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 17f; isFakeBoldText = true; color = Color.BLACK }
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; isFakeBoldText = true; color = Color.BLACK }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; strokeWidth = 1f }
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 230, 230) }

        val document = PdfDocument()
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        fun newPage() {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN) newPage()
        }

        canvas.drawText("Ma Bibliothèque", MARGIN, y + 26f, headerPaint)
        y += 46f
        canvas.drawText(
            "Collection exportée le ${formatDate(System.currentTimeMillis())} — " +
                "${movies.size} film(s), ${books.size} livre(s), ${series.size} série(s)",
            MARGIN, y, subtitlePaint,
        )
        y += 30f

        fun sectionHeader(title: String) {
            ensureSpace(46f)
            y += 8f
            canvas.drawText(title, MARGIN, y, sectionPaint)
            y += 14f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 16f
        }

        fun row(title: String, subtitle: String, coverModel: String?) {
            ensureSpace(ROW_HEIGHT)
            val top = y
            val bitmap = coverModel?.let { loadThumbnail(it) }
            val dest = RectF(MARGIN, top, MARGIN + THUMB_WIDTH, top + THUMB_HEIGHT)
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, null, dest, null)
                bitmap.recycle()
            } else {
                canvas.drawRect(dest, placeholderPaint)
            }
            val textX = MARGIN + THUMB_WIDTH + 12f
            val maxWidth = PAGE_WIDTH - MARGIN - textX
            canvas.drawText(TextUtils.ellipsize(title, titlePaint, maxWidth, TextUtils.TruncateAt.END).toString(), textX, top + 18f, titlePaint)
            if (subtitle.isNotBlank()) {
                canvas.drawText(TextUtils.ellipsize(subtitle, subtitlePaint, maxWidth, TextUtils.TruncateAt.END).toString(), textX, top + 36f, subtitlePaint)
            }
            y += ROW_HEIGHT
        }

        if (movies.isNotEmpty()) {
            sectionHeader("Films (${movies.size})")
            movies.sortedBy { it.movie.title }.forEach { entry ->
                val movie = entry.movie
                val formats = entry.copies.map { it.format }.distinct().joinToString(" / ")
                val subtitle = listOfNotNull(movie.year?.toString(), formats.takeIf { it.isNotBlank() }).joinToString(" · ")
                row(movie.title, subtitle, representativeMovieCover(entry))
            }
        }
        if (books.isNotEmpty()) {
            sectionHeader("Livres (${books.size})")
            books.sortedBy { it.title }.forEach { book ->
                val subtitle = listOfNotNull(book.authors, book.publishedYear?.toString()).joinToString(" · ")
                row(book.title, subtitle, book.coverUrl)
            }
        }
        if (series.isNotEmpty()) {
            sectionHeader("Séries (${series.size})")
            series.sortedBy { it.series.name }.forEach { entry ->
                row(entry.series.name, "${entry.episodes.size} fichier(s)", entry.series.coverPath)
            }
        }

        document.finishPage(page)
        val file = File(exportsDir(context), "ma_bibliotheque_${timestamp()}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        file
    }

    /** Builds a share-sheet intent for a just-generated export — content:// via FileProvider,
     * never a raw file:// Uri (blocked on modern Android and would crash on startActivity). */
    fun shareIntent(context: Context, file: File, mimeType: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Partager ma collection").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun representativeMovieCover(entry: MovieWithCopies): String? =
        entry.copies.firstOrNull { it.frontCoverPath != null }?.frontCoverPath ?: entry.movie.posterUrl

    /** Decodes straight to a small thumbnail instead of the source resolution — PdfDocument
     * embeds bitmaps close to raw, so drawing full-size TMDB posters (and just scaling on the
     * canvas) inflated a ~250-item PDF to over 500MB, too big to actually share. Downsampling at
     * decode time (not after) also keeps peak memory sane across a large collection. */
    private fun loadThumbnail(pathOrUrl: String): Bitmap? = runCatching {
        val decoded = if (pathOrUrl.startsWith("http")) {
            val bytes = URL(pathOrUrl).openStream().use { it.readBytes() }
            decodeSampled(
                bounds = { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) },
                final = { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) },
            )
        } else {
            decodeSampled(
                bounds = { BitmapFactory.decodeFile(pathOrUrl, it) },
                final = { BitmapFactory.decodeFile(pathOrUrl, it) },
            )
        } ?: return@runCatching null
        val scaled = Bitmap.createScaledBitmap(decoded, THUMB_PIXEL_WIDTH, THUMB_PIXEL_HEIGHT, true)
        if (scaled !== decoded) decoded.recycle()
        scaled
    }.getOrNull()

    private fun decodeSampled(
        bounds: (BitmapFactory.Options) -> Unit,
        final: (BitmapFactory.Options) -> Bitmap?,
    ): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        bounds(boundsOptions)
        var sample = 1
        while (boundsOptions.outWidth / (sample * 2) >= THUMB_PIXEL_WIDTH && boundsOptions.outHeight / (sample * 2) >= THUMB_PIXEL_HEIGHT) {
            sample *= 2
        }
        return final(BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun exportsDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(java.util.Date())

    private fun formatDate(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(java.util.Date(millis))

    /** Every field is quoted unconditionally — simpler and just as safe as conditional quoting. */
    private fun csvRow(vararg fields: String): String =
        fields.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } + "\n"
}
