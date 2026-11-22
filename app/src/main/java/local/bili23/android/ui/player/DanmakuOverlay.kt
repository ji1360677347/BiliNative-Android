package local.bili23.android.ui.player

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import local.bili23.android.core.Danmaku
import kotlin.math.max

private const val SCROLL_SECONDS = 8.0
private const val FIXED_SECONDS = 4.0

private data class Placement(
    val danmaku: Danmaku,
    val lane: Int,
    val width: Float,
    val start: Double,
    val mode: Int,
)

/**
 * 弹幕层：以播放器进度为准计算位置，暂停、拖动、倍速都会自然同步。
 * 轨道只按时间顺序排一次，绘制阶段只做可见性过滤。
 */
@Composable
fun DanmakuOverlay(
    items: List<Danmaku>,
    enabled: Boolean,
    opacity: Float,
    scale: Float,
    area: Float,
    positionProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    if (!enabled || items.isEmpty()) return
    val density = LocalDensity.current
    val textSizePx = with(density) { (15f * scale).sp.toPx() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var tick by remember { mutableLongStateOf(0L) }
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    val placements = remember(items, size, textSizePx, area) {
        if (size.width <= 0 || size.height <= 0) emptyList() else {
            paint.textSize = textSizePx
            val width = size.width.toFloat()
            val height = size.height.toFloat()
            val laneHeight = max(textSizePx * 1.55f, 1f)
            val topOffset = textSizePx * 1.6f
            val lanes = ((height - topOffset) * area.coerceIn(0.2f, 1f) / laneHeight)
                .toInt().coerceAtLeast(1)
            val scrollFree = DoubleArray(lanes)
            val topFree = DoubleArray(lanes)
            val bottomFree = DoubleArray(lanes)
            val fixedLanes = max(1, lanes / 2)
            val result = ArrayList<Placement>()
            for (item in items) {
                val textWidth = paint.measureText(item.text)
                when (item.mode) {
                    1 -> {
                        var lane = -1
                        for (i in 0 until lanes) {
                            if (scrollFree[i] <= item.time) {
                                lane = i
                                break
                            }
                        }
                        if (lane < 0) continue
                        scrollFree[lane] = item.time + SCROLL_SECONDS * textWidth / (width + textWidth) + 0.2
                        result.add(Placement(item, lane, textWidth, item.time, 1))
                    }
                    4, 5 -> {
                        val free = if (item.mode == 5) topFree else bottomFree
                        var lane = -1
                        for (i in 0 until fixedLanes) {
                            if (free[i] <= item.time) {
                                lane = i
                                break
                            }
                        }
                        if (lane < 0) continue
                        free[lane] = item.time + FIXED_SECONDS
                        result.add(Placement(item, lane, textWidth, item.time, item.mode))
                    }
                }
            }
            result
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { tick = it }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it },
    ) {
        if (tick < 0) return@Canvas
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        if (width <= 0f || placements.isEmpty()) return@Canvas
        val laneHeight = max(textSizePx * 1.55f, 1f)
        val topOffset = textSizePx * 1.6f
        val position = positionProvider() / 1000.0
        val textAlpha = (255 * opacity.coerceIn(0.1f, 1f)).toInt()
        for (p in placements) {
            val elapsed = position - p.start
            if (elapsed < 0) continue
            when (p.mode) {
                1 -> {
                    if (elapsed > SCROLL_SECONDS) continue
                    paint.textSize = textSizePx
                    paint.color = p.danmaku.color or 0xFF000000.toInt()
                    paint.alpha = textAlpha
                    val x = (width - (elapsed / SCROLL_SECONDS) * (width + p.width)).toFloat()
                    val y = topOffset + (p.lane + 1) * laneHeight - paint.descent()
                    drawContext.canvas.nativeCanvas.drawText(p.danmaku.text, x, y, paint)
                }
                4, 5 -> {
                    if (elapsed > FIXED_SECONDS) continue
                    paint.textSize = textSizePx
                    paint.color = p.danmaku.color or 0xFF000000.toInt()
                    paint.alpha = textAlpha
                    val x = (width - p.width) / 2f
                    val y = if (p.mode == 5) {
                        topOffset + (p.lane + 1) * laneHeight - paint.descent()
                    } else {
                        height - p.lane * laneHeight - paint.descent()
                    }
                    drawContext.canvas.nativeCanvas.drawText(p.danmaku.text, x, y, paint)
                }
            }
        }
    }
}
