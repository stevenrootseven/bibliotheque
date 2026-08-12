package com.stevenroot.mabibliotheque.data.repository

data class ParsedDigitalFile(val title: String, val year: Int?, val quality: String, val languages: String?)

/**
 * Best-effort filename parsing for the common "scene release" naming convention (dots/
 * underscores instead of spaces, tags appended after the title) — e.g.
 * "Oblivion.2013.2160p.UHD.BluRay.MULTi.TrueFrench.x265.mkv" → title "Oblivion", year 2013,
 * quality "4K", languages "MULTi, TrueFrench". Never guaranteed correct (there's no
 * standard), which is why the caller must keep the result editable rather than trusting it
 * blindly — same rule as every other identification path in this app.
 */
object DigitalFileParser {

    // No \b before the year: a missing separator ("Talisman1985") still has a letter directly
    // against the digits, and \b matches at that letter/digit boundary anyway — the actual gap
    // is when a run of digits butts against MORE digits (e.g. "19661080p"), which the negative
    // digit lookaround below correctly refuses to split.
    private val YEAR_REGEX = Regex("(?<!\\d)(19\\d{2}|20\\d{2})(?!\\d)")
    private val QUALITY_4K = listOf("2160p", "4k", "uhd")
    private val QUALITY_HD = listOf("1080p", "1280p", "720p", "hdlight", "hdrip", "webrip", "web-dl", "bdrip", "bluray")
    private val LANGUAGE_TOKENS = listOf(
        "multi", "french", "truefrench", "vff", "vfq", "vfi", "vo", "vost", "vostfr", "english", "subfrench",
    )
    // Codec/audio/misc release tags that carry no film identity — stripped from the END of the
    // title (never the middle) so they don't pollute the TMDB search query. Needed whether or
    // not a year anchor was found: many of the user's personal encodes have no year in the
    // filename at all, so the whole tag block would otherwise survive as part of the "title".
    private val TRAILING_JUNK_WORDS = listOf(
        "h264", "h265", "x264", "x265", "hevc", "avc", "av1",
        "hdr10", "hdr", "10bit", "8bit",
        "dts-hdma", "dtshdma", "dts", "ac3", "eac3", "aac51", "aac6", "aac", "dd6", "ddp", "dd51",
        "atmos", "truehd8", "truehd", "opus7", "vc1", "remastered",
        "en", "fr", "ru",
    ) + QUALITY_4K + QUALITY_HD + LANGUAGE_TOKENS

    fun parse(fileName: String): ParsedDigitalFile {
        val withoutExtension = fileName.substringBeforeLast('.', fileName)
        val normalized = withoutExtension.replace('.', ' ').replace('_', ' ')
        val lower = normalized.lowercase()

        val year = YEAR_REGEX.find(normalized)?.value?.toIntOrNull()
        val quality = when {
            QUALITY_4K.any { it in lower } -> "4K"
            QUALITY_HD.any { it in lower } -> "HD"
            else -> "HD"
        }
        val languages = LANGUAGE_TOKENS.filter { it in lower }
            .map { it.uppercase() }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ")

        val cutIndex = year?.let { normalized.indexOf(it.toString()) }?.takeIf { it > 0 } ?: normalized.length
        var title = normalized.substring(0, cutIndex)
        title = title.replace(Regex("[\\[(].*?[\\])]"), " ")
        title = stripTrailingJunk(title)
        title = title.replace(Regex("\\s{2,}"), " ").trim().trim('-', '.', ' ')

        return ParsedDigitalFile(title.ifBlank { withoutExtension }, year, quality, languages)
    }

    private fun stripTrailingJunk(input: String): String {
        var title = input.trim()
        var changed = true
        while (changed) {
            changed = false
            for (word in TRAILING_JUNK_WORDS) {
                val stripped = Regex("(?i)\\b${Regex.escape(word)}\\b\\s*$").replace(title, "").trim()
                if (stripped != title) {
                    title = stripped
                    changed = true
                }
            }
        }
        return title
    }
}
