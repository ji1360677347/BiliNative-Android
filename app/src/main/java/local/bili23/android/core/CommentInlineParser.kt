package local.bili23.android.core

sealed interface CommentInlineRun {
    val text: String

    data class Text(override val text: String) : CommentInlineRun
    data class Url(override val text: String, val url: String) : CommentInlineRun
    data class Video(override val text: String, val bvid: String) : CommentInlineRun
    data class Timestamp(override val text: String, val seconds: Double) : CommentInlineRun
}

object CommentInlineParser {
    private val URL_REGEX = Regex("""https?://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+""")
    private val BV_REGEX = Regex("""(?<![A-Za-z0-9])[Bb][Vv][A-Za-z0-9]{10}(?![A-Za-z0-9])""")
    private val TIMESTAMP_REGEX = Regex("""(?<![0-9:：])([0-9]{1,2})[:：]([0-5][0-9])(?:[:：]([0-5][0-9]))?(?![0-9:：])""")
    private val BILIBILI_URL_HOST_REGEX = Regex("""^https?://(?:[^/]*\.)?bilibili\.com(?:/|\?|#|$)""", RegexOption.IGNORE_CASE)
    private val TRAILING_PUNCTUATION = setOf(
        '.', ',', ';', ':', '!', '?', ')', ']', '}',
        '。', '，', '；', '：', '！', '？', '、', '…', '”', '’', '）', '】', '》',
    )

    private data class Span(val start: Int, val end: Int, val run: CommentInlineRun)

    fun parse(input: String): List<CommentInlineRun> {
        if (input.isEmpty()) return emptyList()

        // Step A: 先提取 URL，避免 URL 内部包含的 BV 号或时间格式被重复匹配。
        val urlSpans = mutableListOf<Span>()
        for (match in URL_REGEX.findAll(input)) {
            val start = match.range.first
            var end = match.range.last + 1
            while (end > start && input[end - 1] in TRAILING_PUNCTUATION) {
                end--
            }
            val trimmed = input.substring(start, end)
            val schemeLen = if (trimmed.startsWith("https://", ignoreCase = true)) 8 else 7
            if (trimmed.length > schemeLen) {
                urlSpans += Span(start, end, CommentInlineRun.Url(trimmed, trimmed))
            }
        }

        // Step B: 仅在非 URL 文本片段中匹配裸 BV 号与时间戳。
        val rawRuns = mutableListOf<CommentInlineRun>()
        var segmentStart = 0
        for (urlSpan in urlSpans) {
            if (urlSpan.start > segmentStart) {
                appendNonUrlRuns(input, segmentStart, urlSpan.start, rawRuns)
            }
            rawRuns += urlSpan.run
            segmentStart = urlSpan.end
        }
        if (segmentStart < input.length) {
            appendNonUrlRuns(input, segmentStart, input.length, rawRuns)
        }

        if (rawRuns.isEmpty()) return emptyList()
        val merged = mutableListOf<CommentInlineRun>()
        for (run in rawRuns) {
            val last = merged.lastOrNull()
            if (run is CommentInlineRun.Text && last is CommentInlineRun.Text) {
                merged[merged.lastIndex] = CommentInlineRun.Text(last.text + run.text)
            } else {
                merged += run
            }
        }
        return merged
    }

    fun extractBvidFromUrl(url: String): String? {
        if (!BILIBILI_URL_HOST_REGEX.containsMatchIn(url)) return null
        val match = BV_REGEX.find(url) ?: return null
        return "BV" + match.value.drop(2)
    }

    private fun appendNonUrlRuns(
        input: String,
        start: Int,
        end: Int,
        out: MutableList<CommentInlineRun>,
    ) {
        val segment = input.substring(start, end)
        val candidates = mutableListOf<Span>()

        for (match in BV_REGEX.findAll(segment)) {
            val raw = match.value
            val normalized = "BV" + raw.drop(2)
            candidates += Span(
                start = start + match.range.first,
                end = start + match.range.last + 1,
                run = CommentInlineRun.Video(raw, normalized),
            )
        }

        for (match in TIMESTAMP_REGEX.findAll(segment)) {
            val first = match.groupValues[1].toIntOrNull() ?: continue
            val second = match.groupValues[2].toIntOrNull() ?: continue
            val thirdStr = match.groupValues[3]
            val seconds = if (thirdStr.isNotEmpty()) {
                val third = thirdStr.toIntOrNull() ?: continue
                (first * 3600 + second * 60 + third).toDouble()
            } else {
                (first * 60 + second).toDouble()
            }
            candidates += Span(
                start = start + match.range.first,
                end = start + match.range.last + 1,
                run = CommentInlineRun.Timestamp(match.value, seconds),
            )
        }

        if (candidates.isEmpty()) {
            out += CommentInlineRun.Text(segment)
            return
        }

        candidates.sortWith(compareBy<Span>({ it.start }, { -(it.end - it.start) }))
        var cursor = start
        for (candidate in candidates) {
            if (candidate.start < cursor) continue
            if (candidate.start > cursor) {
                out += CommentInlineRun.Text(input.substring(cursor, candidate.start))
            }
            out += candidate.run
            cursor = candidate.end
        }
        if (cursor < end) {
            out += CommentInlineRun.Text(input.substring(cursor, end))
        }
    }
}
