package local.bili23.android

import local.bili23.android.core.DanmakuParser
import local.bili23.android.core.Wbi
import local.bili23.android.core.orderedMediaUrls
import local.bili23.android.core.parseWbiCommentsResponse
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `orderedMediaUrls prioritizes standard CDN over mcdn pcdn and szbdyd`() {
        val input = listOf(
            "https://123-45-67-89.mcdn.bilivideo.cn/v1.m4s",
            "https://upos-sz-mirrorcos.bilivideo.com/v2.m4s",
            "https://xy123.pcdn.bilivideo.cn/v3.m4s",
            "https://upos-sz-staticcos.szbdyd.com/v4.m4s",
            "https://cn-gdgz-cm-01.bilivideo.com/v5.m4s",
        )
        assertEquals(
            listOf(
                "https://upos-sz-mirrorcos.bilivideo.com/v2.m4s",
                "https://cn-gdgz-cm-01.bilivideo.com/v5.m4s",
                "https://123-45-67-89.mcdn.bilivideo.cn/v1.m4s",
                "https://xy123.pcdn.bilivideo.cn/v3.m4s",
                "https://upos-sz-staticcos.szbdyd.com/v4.m4s",
            ),
            orderedMediaUrls(input),
        )
    }

    @Test
    fun `wbi comments response parses cursor and top upper`() {
        val json = JSONObject(
            """
            {
              "cursor": {
                "all_count": 42,
                "is_end": false,
                "pagination_reply": {
                  "next_offset": "{\"type\":3,\"direction\":1}"
                }
              },
              "top": {
                "upper": {
                  "rpid": 1001,
                  "like": 88,
                  "ctime": 1700000000,
                  "rcount": 2,
                  "member": {"uname": "UP主", "mid": 7, "avatar": "//i0.hdslb.com/up.jpg", "level_info": {"current_level": 6}},
                  "content": {"message": "置顶 1：23"}
                }
              },
              "replies": [
                {
                  "rpid": 1002,
                  "like": 12,
                  "ctime": 1700000100,
                  "rcount": 0,
                  "member": {"uname": "路人", "mid": 8, "avatar": "https://i0.hdslb.com/a.jpg", "level_info": {"current_level": 4}},
                  "content": {"message": "支持 BV1xx411c7mD"}
                }
              ]
            }
            """.trimIndent()
        )
        val page = parseWbiCommentsResponse(json, includeTop = true)
        assertFalse(page.isEnd)
        assertTrue(page.usedWbi)
        assertEquals("""{"type":3,"direction":1}""", page.nextOffset)
        assertEquals(2, page.items.size)
        assertEquals(1001L, page.items[0].id)
        assertEquals("置顶 1：23", page.items[0].message)
        assertEquals(1002L, page.items[1].id)
    }

}
