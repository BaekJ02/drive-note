package com.example.drivenote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

// ---------------- 리포트 시각화 (모두 Compose Canvas, 외부 라이브러리 없음)

private val VIZ_HEIGHT = 220.dp
private val RoadGray = Color(0xFFE9EBEE)   // 위에서 본 도로 띠
private val SignalOrange = Color(0xFFE8833A) // 방향지시등 아이콘
private val VizTitleSize = 18.sp           // 왼쪽 위 제목·범례·축 라벨 공통 크기
private val VizSmallSize = 15.sp

/** 문제 항목의 '내 운전 vs 권장' 그림 + 지표 줄 + 요약 문장 */
@Composable
fun ComparisonView(c: Comparison, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        when (c) {
            is Comparison.Chart -> VizBox(c.unit, mineVsRecommended(color, LegendKind.Solid)) { CompareChart(c, color) }
            is Comparison.StopApproach -> VizBox("정지선 접근 (0.5초 간격)", mineVsRecommended(color, LegendKind.Dot)) { StopApproachViz(c, color) }
            is Comparison.LaneChange -> VizBox("차로 변경 위에서 본 모습", mineVsRecommended(color, LegendKind.Solid)) { LaneChangeViz(c, color) }
            is Comparison.CrossingSpeed -> VizBox("횡단보도 앞 속도", mineVsRecommended(color, LegendKind.Solid)) { CrossingSpeedViz(c, color) }
            is Comparison.Timeline -> Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ChartBg).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                Text(boldMarkup(c.summary), fontSize = SMALL, color = Ink, modifier = Modifier.padding(start = 14.dp))
            }
        }
        if (c !is Comparison.Timeline) VizText(c.metric, c.summary)
    }
}

/** 문장의 **굵게** 부분을 굵은 글씨로 */
fun boldMarkup(text: String) = buildAnnotatedString {
    text.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}

// ---------------- 공통: 박스 · 범례 · 지표 줄

enum class LegendKind { Solid, Dashed, Dot }
data class LegendItem(val label: String, val color: Color, val kind: LegendKind)

private fun mineVsRecommended(color: Color, mineKind: LegendKind) = listOf(
    LegendItem("내 운전", color, mineKind),
    LegendItem("권장", RecommendGray, if (mineKind == LegendKind.Dot) LegendKind.Dot else LegendKind.Dashed),
)

/** 흰 박스(테두리 1dp, 모서리 20dp, 여백 20dp) + 왼쪽 위 제목 + 오른쪽 위 범례 */
@Composable
fun VizBox(title: String, legend: List<LegendItem>, modifier: Modifier = Modifier.height(VIZ_HEIGHT), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)
            .border(1.dp, ChartBorder, RoundedCornerShape(20.dp)).padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = VizTitleSize, color = ChartLabel)
            Spacer(Modifier.weight(1f))
            legend.forEachIndexed { i, item ->
                LegendMark(item.color, item.kind)
                Text(item.label, fontSize = VizTitleSize, color = IconGray,
                    modifier = Modifier.padding(start = 8.dp, end = if (i < legend.lastIndex) 20.dp else 0.dp))
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp), content = content)
    }
}

/** 그림 아래: 지표 한 줄(회색, 숫자만 굵게) + 요약 문장 */
@Composable
private fun VizText(metric: String?, summary: String) {
    if (metric != null) Text(boldMarkup(metric), fontSize = 24.sp, color = Sub, modifier = Modifier.padding(top = 14.dp))
    Text(boldMarkup(summary), fontSize = 28.sp, color = Ink, modifier = Modifier.padding(top = if (metric != null) 6.dp else 14.dp))
}

/** 범례 표시 (실선 / 점선 / 점) */
@Composable
private fun LegendMark(color: Color, kind: LegendKind) {
    Canvas(Modifier.width(if (kind == LegendKind.Dot) 12.dp else 28.dp).height(12.dp)) {
        val y = size.height / 2
        when (kind) {
            LegendKind.Dot -> drawCircle(color, size.height / 2)
            else -> drawLine(color, Offset(0f, y), Offset(size.width, y),
                strokeWidth = (if (kind == LegendKind.Dashed) 2.5f else 3.5f).dp.toPx(), cap = StrokeCap.Round,
                pathEffect = if (kind == LegendKind.Dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())) else null)
        }
    }
}

// ---------------- 공통 그리기 도우미

/** 내 운전 선: 은은한 빛번짐(같은 색 30%, 10dp, 흐림) 위에 3.5dp 본선 */
private fun DrawScope.drawGlowLine(path: Path, color: Color) {
    drawIntoCanvas { canvas ->
        val glow = androidx.compose.ui.graphics.Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 10.dp.toPx(); strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
            this.color = color.copy(alpha = 0.3f).toArgb()
            maskFilter = android.graphics.BlurMaskFilter(6.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), glow)
    }
    drawPath(path, color, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 권장 선: 회색 2.5dp 점선 */
private fun DrawScope.drawRecommendLine(path: Path) {
    drawPath(path, RecommendGray, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx()))))
}

/** 가로 보조선 5줄 (0, 25, 50, 75, 100%) */
private fun DrawScope.drawGrid(top: Float, bottom: Float) {
    for (k in 0..4) {
        val gy = bottom - (bottom - top) * k / 4f
        drawLine(ChartGrid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
    }
}

private fun DrawScope.label(m: TextMeasurer, text: String, at: Offset, color: Color = ChartLabel, size: Float = 15f,
                            bold: Boolean = false, alignX: Float = 0.5f, alignY: Float = 0f) {
    val l = m.measure(text, TextStyle(fontSize = size.sp, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
    val x = (at.x - l.size.width * alignX).coerceIn(0f, this.size.width - l.size.width)
    drawText(l, topLeft = Offset(x, at.y - l.size.height * alignY))
}

/** 점들을 지나는 부드러운 곡선 (Catmull-Rom → cubicTo). 제어점 y를 [minY, maxY]로 제한해 범위 밖으로 넘치지 않게 함 */
fun smoothPath(p: List<Offset>, minY: Float = Float.NEGATIVE_INFINITY, maxY: Float = Float.POSITIVE_INFINITY) = Path().apply {
    if (p.isEmpty()) return@apply
    moveTo(p[0].x, p[0].y)
    for (i in 0 until p.lastIndex) {
        val p0 = p[maxOf(i - 1, 0)]; val p1 = p[i]; val p2 = p[i + 1]; val p3 = p[minOf(i + 2, p.lastIndex)]
        cubicTo(
            p1.x + (p2.x - p0.x) / 6f, (p1.y + (p2.y - p0.y) / 6f).coerceIn(minY, maxY),
            p2.x - (p3.x - p1.x) / 6f, (p2.y - (p3.y - p1.y) / 6f).coerceIn(minY, maxY),
            p2.x, p2.y,
        )
    }
}

// ---------------- 기존: 시간축 꺾은선 (조향각·가속 페달·감속도)

@Composable
private fun ColumnScope.CompareChart(c: Comparison.Chart, color: Color) {
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().weight(1f)) {
        val plotTop = 6.dp.toPx(); val plotBottom = size.height - 26.dp.toPx()
        val side = 16.dp.toPx()
        val n = c.mine.size
        val tEnd = ComparisonData.T_START + (n - 1) * ComparisonData.T_STEP
        fun x(t: Float) = side + (t - ComparisonData.T_START) / (tEnd - ComparisonData.T_START) * (size.width - 2 * side)
        fun y(v: Float) = plotBottom - (v - c.yMin) / (c.yMax - c.yMin) * (plotBottom - plotTop)
        fun points(vs: List<Float>) = vs.mapIndexed { i, v -> Offset(x(ComparisonData.T_START + i * ComparisonData.T_STEP), y(v)) }
        fun smooth(pts: List<Offset>) = smoothPath(pts, plotTop, plotBottom)

        drawGrid(plotTop, plotBottom)
        val mine = points(c.mine)
        // 선 아래 면: 분류 색 22% → 바닥 0%
        val area = smooth(mine).apply { lineTo(mine.last().x, plotBottom); lineTo(mine.first().x, plotBottom); close() }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f)), startY = plotTop, endY = plotBottom))
        drawRecommendLine(smooth(points(c.recommended)))
        drawGlowLine(smooth(mine), color)
        listOf(-2f to "-2초", 0f to "0", 2f to "2초", 4f to "4초").forEach { (t, l) ->
            label(measurer, l, Offset(x(t), plotBottom + 6.dp.toPx()), size = 16f)
        }
    }
}

// ---------------- [1] 정차 직전 급제동: 정지선 접근 연속 사진

@Composable
private fun ColumnScope.StopApproachViz(c: Comparison.StopApproach, color: Color) {
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().weight(1f)) {
        val labelW = 76.dp.toPx()
        val bandTop = 4.dp.toPx(); val bandBottom = size.height - 24.dp.toPx()
        val x0 = labelW; val x1 = size.width
        // 도로 띠 + 가운데 흰 점선
        drawRect(RoadGray, Offset(x0, bandTop), Size(x1 - x0, bandBottom - bandTop))
        val mid = (bandTop + bandBottom) / 2
        drawLine(Color.White, Offset(x0, mid), Offset(x1, mid), strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 10.dp.toPx())))
        // 정지선 (오른쪽 끝, 흰 굵은 선)
        val stopX = x1 - 28.dp.toPx()
        drawLine(Color.White, Offset(stopX, bandTop), Offset(stopX, bandBottom), strokeWidth = 7.dp.toPx())
        label(measurer, "정지선", Offset(stopX, bandBottom + 4.dp.toPx()))

        // 두 줄 모두 같은 x 스케일: 정지선까지 남은 거리(m)
        val maxRemain = maxOf(c.mine.first(), c.recommended.first())
        val usable = stopX - x0 - 16.dp.toPx()
        fun x(remain: Float) = stopX - remain / maxRemain * usable
        val rows = listOf(Triple("내 운전", c.mine, color), Triple("권장", c.recommended, RecommendGray))
        rows.forEachIndexed { r, (name, series, col) ->
            val cy = if (r == 0) (bandTop + mid) / 2 else (mid + bandBottom) / 2
            label(measurer, name, Offset(0f, cy), color = IconGray, size = 18f, alignX = 0f, alignY = 0.5f)
            // 0.5초(5샘플)마다 차 위치. 마지막(정지) 점이 가장 진함
            val idx = (series.indices step 5).toMutableList().apply { if (last() != series.lastIndex) add(series.lastIndex) }
            idx.forEachIndexed { k, i ->
                val alpha = 0.3f + 0.7f * k / (idx.size - 1).coerceAtLeast(1)
                drawCircle(col.copy(alpha = alpha), 7.dp.toPx(), Offset(x(series[i]), cy))
            }
        }
    }
}

// ---------------- [3] 방향지시등 늦게 켬: 차로 변경 위에서 본 모습

@Composable
private fun ColumnScope.LaneChangeViz(c: Comparison.LaneChange, color: Color) {
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().weight(1f)) {
        val labelW = 76.dp.toPx()
        val x0 = labelW; val x1 = size.width
        val dMin = -40f; val dMax = 20f // 차로를 넘는 지점(0m) 기준 거리
        fun x(d: Float) = x0 + (d - dMin) / (dMax - dMin) * (x1 - x0)
        fun s01(v: Float): Float { val q = v.coerceIn(0f, 1f); return q * q * (3 - 2 * q) }
        val rowH = size.height / 2
        listOf(Triple("내 운전", c.mineSignalM, color), Triple("권장", c.recSignalM, RecommendGray)).forEachIndexed { r, (name, signalM, col) ->
            val top = r * rowH + 26.dp.toPx(); val bottom = (r + 1) * rowH - 4.dp.toPx()
            // 2차로 도로: 연회색 띠 + 차로 사이 흰 점선
            drawRect(RoadGray, Offset(x0, top), Size(x1 - x0, bottom - top))
            val mid = (top + bottom) / 2
            drawLine(Color.White, Offset(x0, mid), Offset(x1, mid), strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 10.dp.toPx())))
            label(measurer, name, Offset(0f, mid), color = IconGray, size = 18f, alignX = 0f, alignY = 0.5f)
            // 아래 차로 → 위 차로 S자 궤적 (0m에서 차로 경계를 넘음)
            val lower = (mid + bottom) / 2; val upper = (top + mid) / 2
            fun y(d: Float) = lower + (upper - lower) * s01((d + 14f) / 28f)
            val pts = (0..120).map { i -> val d = dMin + i * 0.5f; Offset(x(d), y(d)) }
            val path = smoothPath(pts)
            if (r == 0) drawGlowLine(path, col) else drawRecommendLine(path)
            // 깜빡이 켠 지점: 주황 점 + 작은 깜빡이(위쪽 화살표) 아이콘
            val sp = Offset(x(-signalM), y(-signalM))
            drawCircle(Color.White, 7.dp.toPx(), sp); drawCircle(SignalOrange, 5.dp.toPx(), sp)
            val ic = sp + Offset(0f, -12.dp.toPx())
            drawPath(Path().apply {
                moveTo(ic.x, ic.y - 5.dp.toPx()); lineTo(ic.x - 5.dp.toPx(), ic.y + 3.dp.toPx()); lineTo(ic.x + 5.dp.toPx(), ic.y + 3.dp.toPx()); close()
            }, SignalOrange)
            // 차로 넘은 지점
            val cp = Offset(x(0f), y(0f))
            drawCircle(Color.White, 7.dp.toPx(), cp); drawCircle(col, 5.dp.toPx(), cp)
            // 두 지점 사이 거리 괄호 + "12m"
            val by = top - 6.dp.toPx(); val tick = 5.dp.toPx(); val bc = if (r == 0) IconGray else RecommendGray
            drawLine(bc, Offset(sp.x, by), Offset(cp.x, by), strokeWidth = 1.5.dp.toPx())
            drawLine(bc, Offset(sp.x, by), Offset(sp.x, by + tick), strokeWidth = 1.5.dp.toPx())
            drawLine(bc, Offset(cp.x, by), Offset(cp.x, by + tick), strokeWidth = 1.5.dp.toPx())
            label(measurer, "${signalM.toInt()}m", Offset((sp.x + cp.x) / 2, by - 2.dp.toPx()), color = IconGray, size = 15f, bold = true, alignY = 1f)
        }
    }
}

// ---------------- [4] 우회전 전 일시정지: 횡단보도 앞 속도

@Composable
private fun ColumnScope.CrossingSpeedViz(c: Comparison.CrossingSpeed, color: Color) {
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().weight(1f)) {
        val plotTop = 6.dp.toPx(); val plotBottom = size.height - 26.dp.toPx()
        val side = 16.dp.toPx()
        val dStart = c.distance.first(); val dEnd = c.distance.last()
        fun x(d: Float) = side + (dStart - d) / (dStart - dEnd) * (size.width - 2 * side)
        val vMax = 30f
        fun y(v: Float) = plotBottom - v / vMax * (plotBottom - plotTop)
        drawGrid(plotTop, plotBottom)
        // 횡단보도 (0 ~ -4m): 세로 연회색 줄무늬 띠
        val cx0 = x(0f); val cx1 = x(-4f); val stripe = 6.dp.toPx()
        var sx = cx0
        while (sx < cx1) { drawRect(ChartGrid, Offset(sx, plotTop), Size(min(stripe / 2, cx1 - sx), plotBottom - plotTop)); sx += stripe }
        label(measurer, "횡단보도", Offset((cx0 + cx1) / 2, plotTop + 2.dp.toPx()))
        fun pts(vs: List<Float>) = c.distance.mapIndexed { i, d -> Offset(x(d), y(vs[i])) }
        drawRecommendLine(smoothPath(pts(c.recommended), plotTop, plotBottom))
        drawGlowLine(smoothPath(pts(c.mine), plotTop, plotBottom), color)
        // 권장: 완전히 멈춘 지점
        val ri = c.recommended.indices.minBy { c.recommended[it] }
        val rp = Offset(x(c.distance[ri]), y(0f))
        drawCircle(RecommendGray, 5.dp.toPx(), rp)
        // 내 운전 최저점
        val mi = c.mine.indices.minBy { c.mine[it] }
        val mp = Offset(x(c.distance[mi]), y(c.mine[mi]))
        drawCircle(Color.White, 7.dp.toPx(), mp); drawCircle(color, 5.dp.toPx(), mp)
        label(measurer, "최저 ${c.mine[mi].toInt()}km/h", mp + Offset(-8.dp.toPx(), -10.dp.toPx()), color = IconGray, size = 15f, bold = true, alignX = 1f, alignY = 1f)
        listOf(30f, 20f, 10f, 0f).forEach { d -> label(measurer, "${d.toInt()}m", Offset(x(d), plotBottom + 6.dp.toPx()), size = 16f) }
    }
}

// ---------------- [2] 주행 전체 쏠림 요약 (G-G 다이어그램)

/** '지도 | 쏠림 요약' 텍스트 탭 (선택된 쪽만 진하게 + 밑줄) */
@Composable
fun SegmentTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        options.forEachIndexed { i, text ->
            if (i > 0) Text("|", fontSize = 22.sp, color = Color(0xFFD0D4D9), modifier = Modifier.padding(horizontal = 14.dp))
            val sel = i == selected
            Text(
                text, fontSize = 22.sp, color = if (sel) Ink else Sub, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onSelect(i) }.padding(vertical = 6.dp)
                    .drawBehind { if (sel) drawRect(Ink, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx())) },
            )
        }
    }
}

@Composable
fun GForceViz(t: TripLog, modifier: Modifier = Modifier) {
    val sum = remember(t) { GForceData.forTrip(t) }
    val measurer = rememberTextMeasurer()
    val legend = listOf(
        LegendItem("제동", IssueType.BRAKE.color, LegendKind.Dot),
        LegendItem("가속", IssueType.ACCEL.color, LegendKind.Dot),
        LegendItem("회전", IssueType.STEER.color, LegendKind.Dot),
    )
    VizBox("쏠림 요약", legend, modifier) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.fillMaxHeight().aspectRatio(1.7f)) {
                val c = Offset(size.width / 2, size.height / 2)
                val r06 = size.height / 2 - 26.dp.toPx() // 0.6g 원 반지름
                fun r(g: Float) = g / 0.6f * r06
                // 편안한 범위(0.2g) 안쪽 연한 초록
                drawCircle(IssueType.SIGNAL.color.copy(alpha = 0.08f), r(0.2f), c)
                listOf(0.2f, 0.4f, 0.6f).forEach { g ->
                    drawCircle(ChartGrid, r(g), c, style = Stroke(1.dp.toPx()))
                    label(measurer, "${g}g", Offset(c.x + r(g) * 0.71f + 4.dp.toPx(), c.y - r(g) * 0.71f), size = 13f, alignX = 0f, alignY = 1f)
                }
                // 십자 축
                drawLine(ChartGrid, Offset(c.x - r06, c.y), Offset(c.x + r06, c.y), strokeWidth = 1.dp.toPx())
                drawLine(ChartGrid, Offset(c.x, c.y - r06), Offset(c.x, c.y + r06), strokeWidth = 1.dp.toPx())
                label(measurer, "가속", Offset(c.x, c.y - r06 - 4.dp.toPx()), alignY = 1f)
                label(measurer, "제동", Offset(c.x, c.y + r06 + 4.dp.toPx()))
                label(measurer, "왼쪽 쏠림", Offset(c.x - r06 - 8.dp.toPx(), c.y), alignX = 1f, alignY = 0.5f)
                label(measurer, "오른쪽 쏠림", Offset(c.x + r06 + 8.dp.toPx(), c.y), alignX = 0f, alignY = 0.5f)
                // 샘플: 안쪽은 반투명 진회색, 0.2g 밖은 분류 색으로 진하게
                sum.samples.forEach { s ->
                    val p = Offset(c.x + r(s.lat), c.y - r(s.lon))
                    if (hypot(s.lat, s.lon) <= GForceData.COMFORT) {
                        drawCircle(IconGray.copy(alpha = 0.25f), 3.dp.toPx(), p)
                    } else {
                        val col = when {
                            abs(s.lat) > abs(s.lon) -> IssueType.STEER.color
                            s.lon < 0 -> IssueType.BRAKE.color
                            else -> IssueType.ACCEL.color
                        }
                        drawCircle(col.copy(alpha = 0.9f), 4.5.dp.toPx(), p)
                    }
                }
                // 라벨은 안쪽 원 바로 바깥 왼쪽 아래 (점 무리와 겹치지 않게)
                label(measurer, "편안한 범위", Offset(c.x - r(0.2f) * 0.75f, c.y + r(0.2f) * 0.75f), color = IssueType.SIGNAL.color, size = 13f, alignX = 1f)
            }
            Column(Modifier.weight(1f).padding(start = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(boldMarkup("편안한 범위 안 **${sum.insidePct}%**"), fontSize = 24.sp, color = Ink)
                Text(boldMarkup("제동 쏠림 최대 **${"%.2f".format(sum.maxBrake)}g**"), fontSize = 24.sp, color = Ink)
                Text(boldMarkup("회전 쏠림 최대 **${"%.2f".format(sum.maxTurn)}g**"), fontSize = 24.sp, color = Ink)
            }
        }
    }
}
