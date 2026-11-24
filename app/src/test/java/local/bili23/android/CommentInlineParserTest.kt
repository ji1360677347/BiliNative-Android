package local.bili23.android

import local.bili23.android.core.CommentInlineParser
import local.bili23.android.core.CommentInlineRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentInlineParserTest {

    @Test
    fun `detects bare BV and normalizes lowercase prefix`() {
        val runs = CommentInlineParser.parse("推荐看看 BV1xx411c7mD 和 bv1Ab411c7mD。")
        assertEquals(
            listOf(
                CommentInlineRun.Text("推荐看看 "),
                CommentInlineRun.Video("BV1xx411c7mD", "BV1xx411c7mD"),
                CommentInlineRun.Text(" 和 "),
                CommentInlineRun.Video("bv1Ab411c7mD", "BV1Ab411c7mD"),
                CommentInlineRun.Text("。"),
            ),
            runs,
        )
    }

    @Test
    fun `rejects invalid or embedded BV tokens`() {
        val runs = CommentInlineParser.parse("abcBV1xx411c7mD BV1xx411c7mD9 BV1xx411c7m")
        assertEquals(
            listOf(CommentInlineRun.Text("abcBV1xx411c7mD BV1xx411c7mD9 BV1xx411c7m")),
            runs,
        )
    }

    @Test
    fun `does not double match BV or timestamp inside URL and trims trailing punctuation`() {
        val runs = CommentInlineParser.parse("链接 https://www.bilibili.com/video/BV1xx411c7mD?t=1:23，看一看！")
        assertEquals(
            listOf(
                CommentInlineRun.Text("链接 "),
                CommentInlineRun.Url(
                    "https://www.bilibili.com/video/BV1xx411c7mD?t=1:23",
                    "https://www.bilibili.com/video/BV1xx411c7mD?t=1:23",
                ),
                CommentInlineRun.Text("，看一看！"),
            ),
            runs,
        )
        assertEquals(
            "BV1xx411c7mD",
            CommentInlineParser.extractBvidFromUrl("https://www.bilibili.com/video/bv1xx411c7mD?p=2"),
        )
        assertNull(CommentInlineParser.extractBvidFromUrl("https://example.com/BV1xx411c7mD"))
    }

    @Test
    fun `parses half-width full-width and mixed timestamps`() {
        val runs = CommentInlineParser.parse("空降 1:23 / 01:02:03 / 1：23 / 01：02：03 / 1:02：03")
        assertEquals(
            listOf(
                CommentInlineRun.Text("空降 "),
                CommentInlineRun.Timestamp("1:23", 83.0),
                CommentInlineRun.Text(" / "),
                CommentInlineRun.Timestamp("01:02:03", 3723.0),
                CommentInlineRun.Text(" / "),
                CommentInlineRun.Timestamp("1：23", 83.0),
                CommentInlineRun.Text(" / "),
                CommentInlineRun.Timestamp("01：02：03", 3723.0),
                CommentInlineRun.Text(" / "),
                CommentInlineRun.Timestamp("1:02：03", 3723.0),
            ),
            runs,
        )
    }

    @Test
    fun `rejects invalid timestamps`() {
        val runs = CommentInlineParser.parse("无效 1:60 和 1：99 和 123:45 还有 1:23:45:67")
        assertEquals(
            listOf(CommentInlineRun.Text("无效 1:60 和 1：99 和 123:45 还有 1:23:45:67")),
            runs,
        )
    }

    @Test
    fun `parses combined url bv and full-width timestamp in one comment`() {
        val runs = CommentInlineParser.parse(
            "参考 https://b23.tv/abc123。前作 bv1Q541167Qg 在 12：34 处高能！"
        )
        assertEquals(
            listOf(
                CommentInlineRun.Text("参考 "),
                CommentInlineRun.Url("https://b23.tv/abc123", "https://b23.tv/abc123"),
                CommentInlineRun.Text("。前作 "),
                CommentInlineRun.Video("bv1Q541167Qg", "BV1Q541167Qg"),
                CommentInlineRun.Text(" 在 "),
                CommentInlineRun.Timestamp("12：34", 754.0),
                CommentInlineRun.Text(" 处高能！"),
            ),
            runs,
        )
    }
}
