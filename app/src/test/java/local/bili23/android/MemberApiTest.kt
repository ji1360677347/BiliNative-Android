package local.bili23.android

import local.bili23.android.core.BiliException
import local.bili23.android.core.MemberApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberApiTest {

    @Test
    fun `json array keeps only bilibili domain`() {
        val json = """
            [
              {"name":"SESSDATA","value":"abc123456789","domain":".bilibili.com"},
              {"name":"bili_jct","value":"csrf123","domain":"bilibili.com"},
              {"name":"DedeUserID","value":"42","domain":"evil.example.com"},
              {"name":"SESSDATA","value":"evil","domain":"evil.example.com"}
            ]
        """.trimIndent()
        assertEquals(
            "SESSDATA=abc123456789; bili_jct=csrf123",
            MemberApi.parseResponse(json),
        )
    }

    @Test
    fun `object with cookies array is supported`() {
        val json = """{"success":true,"cookies":[{"name":"SESSDATA","value":"abcdefghijk","domain":".bilibili.com"}]}"""
        assertEquals("SESSDATA=abcdefghijk", MemberApi.parseResponse(json))
    }

    @Test
    fun `object header is supported`() {
        val json = """{"header":"SESSDATA=abcdefghijk; bili_jct=token"}"""
        assertEquals("SESSDATA=abcdefghijk; bili_jct=token", MemberApi.parseResponse(json))
    }

    @Test
    fun `plain cookie text is supported`() {
        val text = "SESSDATA=abcdefghijk; bili_jct=token; DedeUserID=1"
        assertEquals("SESSDATA=abcdefghijk; bili_jct=token; DedeUserID=1", MemberApi.parseResponse(text))
    }

    @Test
    fun `failed response is rejected`() {
        val error = assertThrows(BiliException::class.java) {
            MemberApi.parseResponse("""{"success":false}""")
        }
        assertTrue(error.message!!.contains("访问密钥"))
    }

    @Test
    fun `missing sessdata is rejected`() {
        val error = assertThrows(BiliException::class.java) {
            MemberApi.parseResponse("""[{"name":"bili_jct","value":"x"}]""")
        }
        assertTrue(error.message!!.contains("SESSDATA"))
    }

    @Test
    fun `url must be https without credentials`() {
        assertEquals(
            "https://nas.example.com:20005/api/pages/x/cookies?key=abc",
            MemberApi.validateUrl("https://nas.example.com:20005/api/pages/x/cookies?key=abc"),
        )
        assertThrows(BiliException::class.java) {
            MemberApi.validateUrl("http://nas.example.com/cookies")
        }
        assertThrows(BiliException::class.java) {
            MemberApi.validateUrl("https://user:pass@nas.example.com/cookies")
        }
    }
}
