package com.stevenroot.mabibliotheque.data.remote

import okhttp3.ResponseBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.StringReader

/** https://api.bnf.fr — the SRU search API of the Bibliothèque nationale de France, France's
 * legal-deposit library: every book published in France must be registered there by law, which
 * makes it the most complete and authoritative free source for French books — unlike Google
 * Books (anonymous access now hard-blocked, requires a billing-enabled Cloud project) or Open
 * Library (comprehensive for English-language/classic titles, weak for recent French releases).
 * No key, no billing, ever. Returns XML (Dublin Core), not JSON — parsed by hand below since
 * there's no JSON converter for it and adding a whole XML converter library for one endpoint
 * isn't worth it. */
interface BnfApi {
    @GET("api/SRU")
    suspend fun search(
        @Query("query") query: String,
        @Query("version") version: String = "1.2",
        @Query("operation") operation: String = "searchRetrieve",
        @Query("recordSchema") recordSchema: String = "dublincore",
        @Query("maximumRecords") maximumRecords: Int = 15,
    ): ResponseBody

    companion object {
        const val BASE_URL = "https://catalogue.bnf.fr/"
        fun isbnQuery(isbn: String) = "bib.isbn all \"$isbn\""
        fun titleQuery(title: String) = "bib.title all \"${title.replace("\"", "'")}\""
    }
}

private data class BnfRecord(
    val title: String,
    val author: String?,
    val publisher: String?,
    val date: String?,
    val isbn: String?,
    val format: String?,
) {
    fun toBookVolumeInfo(): BookVolumeInfo = BookVolumeInfo(
        title = title,
        authors = listOfNotNull(author),
        publisher = publisher,
        publishedDate = date,
        pageCount = format?.let { Regex("(\\d+)\\s*p").find(it)?.groupValues?.get(1)?.toIntOrNull() },
        description = null,
        industryIdentifiers = isbn?.let {
            listOf(BookIdentifier(type = if (it.length == 13) "ISBN_13" else "ISBN_10", identifier = it))
        }.orEmpty(),
        // BnF's catalogue exposes no cover images — left blank, fixable via the manual
        // photo/gallery cover editor already on the movie/book detail screen.
        coverUrl = null,
    )
}

/**
 * Hand-rolled Dublin Core extraction — deliberately matches tag names as written (`dc:title`,
 * `oai_dc:dc`...) rather than resolving XML namespaces properly, since BnF's feed always uses
 * these exact prefixes; full namespace-aware parsing would be more "correct" but meaningfully
 * more code for a single fixed, trusted source.
 */
internal object BnfXmlParser {
    fun parse(xml: String): List<BookVolumeInfo> {
        val records = mutableListOf<BnfRecord>()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))

        var title: String? = null
        var author: String? = null
        var publisher: String? = null
        var date: String? = null
        var isbn: String? = null
        var format: String? = null
        var isPrintedText = false
        var textBuffer = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    textBuffer = StringBuilder()
                    if (parser.name == "oai_dc:dc") {
                        title = null; author = null; publisher = null; date = null; isbn = null; format = null
                        isPrintedText = false
                    }
                }
                XmlPullParser.TEXT -> textBuffer.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val text = textBuffer.toString().trim()
                    when (parser.name) {
                        "dc:title" -> if (title == null) title = text
                        "dc:creator" -> if (author == null && "uteur" in text) author = text.substringBefore(".").trim()
                        "dc:publisher" -> if (publisher == null) publisher = text
                        "dc:date" -> if (date == null) date = text.take(4)
                        "dc:identifier" -> if (isbn == null && text.startsWith("ISBN")) isbn = text.removePrefix("ISBN").trim()
                        "dc:format" -> if (format == null) format = text
                        "dc:type" -> if (text == "texte imprimé") isPrintedText = true
                        "oai_dc:dc" -> if (isPrintedText && title != null) {
                            records += BnfRecord(title, author, publisher, date, isbn, format)
                        }
                    }
                }
            }
            event = parser.next()
        }
        return records.map { it.toBookVolumeInfo() }
    }
}
