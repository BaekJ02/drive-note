package com.example.drivenote

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- 대체 지도(Canvas) 공통 배경: 실제 지도처럼 보이는 단순화된 도로망
internal val MapIllustrationBg = Color(0xFFF2F3F5)
private val RiverColor = Color(0xFFDCE8F4)
private val ParkColor = Color(0xFFE2EFDF)
private val MinorRoad = Color(0xFFE3E5E9)
private val ArterialCasing = Color(0xFFD9DCE1)
private val ArterialFill = Color.White
private val LabelColor = Color(0xFF5F6672) // 보조 텍스트 색

/** 0..1 비율 좌표로 정의한 점 */
private typealias P = Pair<Float, Float>

/** 간선도로 (곡선): 시작점, 제어점1, 제어점2, 끝점 */
private val arterials: List<List<P>> = listOf(
    listOf(0f to 0.80f, 0.30f to 0.62f, 0.62f to 0.74f, 1f to 0.52f),
    listOf(0.24f to 1f, 0.34f to 0.66f, 0.18f to 0.34f, 0.32f to 0f),
    listOf(0.74f to 1f, 0.70f to 0.62f, 0.90f to 0.36f, 0.82f to 0f),
)

/** 이면도로 (꺾인 선) */
private val minorRoads: List<List<P>> = listOf(
    listOf(0f to 0.30f, 0.20f to 0.27f, 0.46f to 0.31f, 0.62f to 0.26f),
    listOf(0.40f to 0.20f, 0.44f to 0.48f, 0.42f to 0.66f),
    listOf(0.52f to 0.18f, 0.88f to 0.22f, 1f to 0.20f),
    listOf(0.06f to 0.52f, 0.30f to 0.48f, 0.56f to 0.50f),
    listOf(0.56f to 0.36f, 0.60f to 0.58f, 0.58f to 0.98f),
    listOf(0.10f to 0.66f, 0.12f to 1f),
    listOf(0.36f to 0.86f, 0.62f to 0.90f, 0.86f to 0.84f),
    listOf(0.88f to 0.44f, 1f to 0.42f),
    listOf(0.66f to 0.42f, 0.84f to 0.48f),
    listOf(0f to 0.94f, 0.22f to 0.90f),
    listOf(0.46f to 0.72f, 0.50f to 1f),
    listOf(0.92f to 0.62f, 0.96f to 0.96f),
)

/** 배경·한강·공원·도로망을 그린다 (경로·마커는 호출하는 쪽에서 그 위에 그림) */
internal fun DrawScope.drawMapBase() {
    val w = size.width
    val h = size.height
    fun o(p: P) = Offset(p.first * w, p.second * h)

    drawRect(MapIllustrationBg)

    // 공원 (연한 초록)
    drawRoundRect(ParkColor, Offset(w * 0.60f, h * 0.56f), Size(w * 0.18f, h * 0.22f),
        androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()))
    drawOval(ParkColor, Offset(w * 0.04f, h * 0.36f), Size(w * 0.13f, h * 0.18f))

    // 한강 (화면 위쪽을 가로지르는 연한 파란 곡선 띠)
    val river = Path().apply {
        moveTo(-0.05f * w, 0.16f * h)
        cubicTo(0.30f * w, 0.02f * h, 0.62f * w, 0.20f * h, 1.05f * w, 0.05f * h)
    }
    drawPath(river, RiverColor, style = Stroke(h * 0.075f, cap = StrokeCap.Butt))

    // 이면도로 (가는 회색)
    minorRoads.forEach { line ->
        val p = Path().apply {
            line.forEachIndexed { i, pt -> val q = o(pt); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }
        }
        drawPath(p, MinorRoad, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // 간선도로 (회색 테두리 + 흰 노면)
    arterials.forEach { c ->
        val (a, b, d, e) = c.map(::o)
        val p = Path().apply { moveTo(a.x, a.y); cubicTo(b.x, b.y, d.x, d.y, e.x, e.y) }
        drawPath(p, ArterialCasing, style = Stroke(11.dp.toPx(), cap = StrokeCap.Round))
        drawPath(p, ArterialFill, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** 지명 라벨: 지점 옆에 작게 표시하고, 지도 밖으로 나가지 않게 위치를 조정한다 */
internal fun DrawScope.drawPlaceLabel(measurer: TextMeasurer, text: String, at: Offset) {
    val layout = measurer.measure(text, TextStyle(fontSize = 18.sp, color = LabelColor))
    val gap = 16.dp.toPx()
    // 기본은 오른쪽, 공간이 없으면 왼쪽
    var x = at.x + gap
    if (x + layout.size.width > size.width - 4.dp.toPx()) x = at.x - gap - layout.size.width
    val y = (at.y - layout.size.height / 2f).coerceIn(4.dp.toPx(), size.height - layout.size.height - 4.dp.toPx())
    // 도로·경로 위에서도 읽히도록 배경색 바탕을 살짝 깐다
    drawRect(MapIllustrationBg.copy(alpha = 0.85f), Offset(x - 4.dp.toPx(), y),
        Size(layout.size.width + 8.dp.toPx(), layout.size.height.toFloat()))
    drawText(layout, topLeft = Offset(x, y))
}
