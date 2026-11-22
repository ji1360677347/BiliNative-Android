package local.bili23.android.core

/**
 * 弹幕段接口返回 protobuf（DmSegMobileReply），只解析播放器需要的字段：
 *   DanmakuElem: id=1, progress=2(ms), mode=3, color=5, content=7
 */
object DanmakuParser {
    private const val MAX_COUNT = 10_000

    fun parse(data: ByteArray): List<Danmaku> {
        if (data.isEmpty()) return emptyList()
        if (data.size <= 8 * 1024 * 1024 && data[0] == '{'.code.toByte()) {
            // 接口错误时返回 JSON，交给上层提示，不当作弹幕解析。
            return emptyList()
        }
        val result = ArrayList<Danmaku>()
        val ids = HashSet<Long>()
        val reader = ProtoReader(data)
        while (!reader.atEnd) {
            val tag = reader.varint()
            if (tag == 10L) {
                val element = ProtoReader(reader.bytes())
                var id = 0L
                var ms = 0L
                var mode = 1L
                var color = 0xFFFFFFL
                var text = ""
                while (!element.atEnd) {
                    when (val t = element.varint()) {
                        8L -> id = element.varint()
                        16L -> ms = element.varint()
                        24L -> mode = element.varint()
                        40L -> color = element.varint()
                        58L -> text = String(element.bytes(), Charsets.UTF_8)
                        else -> element.skip(t)
                    }
                }
                if (result.size >= MAX_COUNT) break
                if (text.isNotEmpty() && mode in 1..6 && ms <= 7L * 24 * 3600 * 1000 && ids.add(id)) {
                    result.add(
                        Danmaku(
                            id = id,
                            time = ms / 1000.0,
                            mode = mode.toInt(),
                            color = (color and 0xFFFFFF).toInt(),
                            text = text.take(80),
                        )
                    )
                }
            } else {
                reader.skip(tag)
            }
        }
        return result.sortedWith(compareBy({ it.time }, { it.id }))
    }

    private class ProtoReader(private val data: ByteArray) {
        private var index = 0
        val atEnd: Boolean get() = index >= data.size

        fun varint(): Long {
            var value = 0L
            var shift = 0
            while (shift < 70) {
                if (index >= data.size) throw BiliException("弹幕数据不完整")
                val b = data[index].toInt() and 0xFF
                index++
                if (shift >= 63 && b > 1) throw BiliException("弹幕数据无效")
                value = value or ((b and 0x7F).toLong() shl shift)
                if (b < 0x80) return value
                shift += 7
            }
            throw BiliException("弹幕数据无效")
        }

        fun bytes(): ByteArray {
            val length = varint()
            if (length < 0 || length > data.size - index) throw BiliException("弹幕数据不完整")
            val end = index + length.toInt()
            val slice = data.copyOfRange(index, end)
            index = end
            return slice
        }

        fun skip(tag: Long) {
            if (tag ushr 3 == 0L) throw BiliException("弹幕字段无效")
            when ((tag and 7).toInt()) {
                0 -> varint()
                1 -> index += 8
                2 -> bytes()
                5 -> index += 4
                else -> throw BiliException("不支持的弹幕字段")
            }
            if (index > data.size) throw BiliException("弹幕数据不完整")
        }
    }
}
