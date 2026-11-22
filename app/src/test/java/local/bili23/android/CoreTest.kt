package local.bili23.android

import local.bili23.android.core.DanmakuParser
import local.bili23.android.core.Wbi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {

    @Test
    fun `wbi mixin key follows official table`() {
        val key = Wbi.mixinKey(
            "7cd084941338484aae1ad9425b84077c",
            "4932caff0ff746eab6f01bf08b70ac45",
        )
        assertEquals("ea1db124af3c7062474693fa704f4ff8", key)
    }

    @Test
    fun `wbi signature is stable`() {
        val signed = Wbi.sign(
            params = mapOf("foo" to "bar", "baz" to "1"),
            key = "testkey",
            timestamp = 1_700_000_000L,
        )
        assertEquals("1700000000", signed["wts"])
        assertEquals("06ab20b10268519d69069844eece791c", signed["w_rid"])
    }

    @Test
    fun `danmaku protobuf decodes known fields`() {
        val hex = "0a1d08959aef3a10d4611801288080fc073a0ce6b58be8af95e5bcb9e5b995"
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val list = DanmakuParser.parse(bytes)
        assertEquals(1, list.size)
        assertEquals(123456789L, list[0].id)
        assertEquals(12.5, list[0].time, 0.0001)
        assertEquals(1, list[0].mode)
        assertEquals(0xFF0000, list[0].color)
        assertEquals("测试弹幕", list[0].text)
    }

    @Test
    fun `danmaku json error yields empty list`() {
        val json = """{"code":-352,"message":"blocked"}""".toByteArray()
        assertTrue(DanmakuParser.parse(json).isEmpty())
    }
}
