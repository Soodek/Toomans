package com.example.tomanrates

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL


private data class ChartPoint(val timestamp: Long, val price: Double)
private fun historySymbol(asset: String): String = when (asset) {
    "gold18" -> "GOLD_18K_IRR"
    "coin" -> "COIN_EMAMI_IRR"
    else -> Catalog.code(asset).uppercase(Locale.US) + "_IRR_FREE"
}
private suspend fun loadOnlineHistory(asset: String): List<ChartPoint> = withContext(Dispatchers.IO) {
    runCatching {
        val symbol = historySymbol(asset)
        val conn = URL("https://raw.githubusercontent.com/iran-market/iran-market.github.io/main/data/history/" + symbol + ".json").openConnection().apply {
            connectTimeout = 12000; readTimeout = 15000
        }
        val json = JSONObject(conn.getInputStream().bufferedReader().use { it.readText() })
        val data = json.getJSONArray("data")
        buildList {
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val price = item.optDouble("close", item.optDouble("price", item.optDouble("value", Double.NaN)))
                val txt = item.optString("t", item.optString("timestamp", item.optString("date", item.optString("time"))))
                val raw = item.optLong("timestamp", item.optLong("t", 0L))
                val ts = if (raw > 0) (if (raw < 10000000000L) raw * 1000 else raw) else runCatching { java.time.Instant.parse(txt).toEpochMilli() }.getOrDefault(0L)
                if (price.isFinite() && price > 0 && ts > 0) add(ChartPoint(ts, price))
            }
        }.sortedBy { it.timestamp }
    }.getOrElse { emptyList() }
}

private data class ChartRange(val label: String, val durationMs: Long)
private val chartRanges = listOf(
    ChartRange("۲۴ ساعت", 24L * 60 * 60 * 1000),
    ChartRange("۷ روز", 7L * 24 * 60 * 60 * 1000),
    ChartRange("۳۰ روز", 30L * 24 * 60 * 60 * 1000),
    ChartRange("۱ سال", 365L * 24 * 60 * 60 * 1000),
)

@Composable
fun MarketChartInline(snap: Snapshot, calculatorAsset: String) {
    val ctx = LocalContext.current
    val assets = (snap.rates.keys + listOf("gold18", "coin")).distinct().sorted()
    var asset by remember(calculatorAsset) { mutableStateOf(calculatorAsset) }
    var rangeIndex by remember { mutableIntStateOf(0) }
    val rate = snap.rates[asset]
    val gold = remember(asset, snap.lastSuccessAt) {
        if (asset == "gold18" || asset == "coin") GoldRepository.snapshot(ctx) else null
    }
    val quote = when (asset) { "gold18" -> gold?.gold18; "coin" -> gold?.emamiCoin; else -> null }
    val current = quote?.price ?: rate?.sell
    val change = quote?.changePct ?: rate?.dayChange?.toDouble()
    val name = when(asset) {
        "gold18" -> uiText("طلای ۱۸ عیار", "18K Gold", "ذهب عيار ١٨")
        "coin" -> uiText("سکه امامی", "Emami Coin", "عملة إمامي")
        else -> "${Catalog.flag(asset)}  ${Catalog.code(asset)} · ${Catalog.englishName(asset, rate?.name)}"
    }
    var onlinePoints by remember(asset) { mutableStateOf<List<ChartPoint>>(emptyList()) }
    var historyLoading by remember(asset) { mutableStateOf(true) }
    LaunchedEffect(asset) {
        historyLoading = true
        onlinePoints = loadOnlineHistory(asset)
        historyLoading = false
    }
    val cutoff = System.currentTimeMillis() - chartRanges[rangeIndex].durationMs
    val points = remember(onlinePoints, rangeIndex) { onlinePoints.filter { it.timestamp >= cutoff } }
    val displayPoints = remember(points) {
        if (points.size <= 180) points else points.filterIndexed { index, _ ->
            index == 0 || index == points.lastIndex || index % (points.size / 180) == 0
        }
    }
    val accent = if ((change ?: 0.0) >= 0) androidx.compose.ui.graphics.Color(0xFF36D399) else androidx.compose.ui.graphics.Color(0xFFFF6B78)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val lineColor = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(uiText("نمودار بازار", "Market chart", "مخطط السوق"), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(uiText("تاریخچه قیمت واقعی", "Historical prices", "سجل الأسعار"), color = muted, fontSize = 12.sp)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(assets) { item ->
                FilterChip(selected = asset == item, onClick = { asset = item }, label = {
                    Text(when(item) { "gold18" -> "طلا"; "coin" -> "سکه"; else -> Catalog.code(item) })
                })
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(name, color = muted, fontSize = 13.sp)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(current?.let { Fmt.toman(it) } ?: "—", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text(uiText("تومان", "Toman", "تومان"), color = muted, fontSize = 12.sp)
                    }
                    if (change != null) {
                        Text("${if (change >= 0) "▲ +" else "▼ "}${"%.2f".format(Locale.US, change)}٪",
                            color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.background(accent.copy(alpha=.12f), RoundedCornerShape(12.dp)).padding(horizontal=10.dp, vertical=7.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    chartRanges.forEachIndexed { index, item ->
                        FilterChip(selected = rangeIndex == index, onClick = { rangeIndex = index }, label = { Text(when(index) { 0 -> uiText("۲۴ ساعت", "24 hours", "٢٤ ساعة"); 1 -> uiText("۷ روز", "7 days", "٧ أيام"); 2 -> uiText("۳۰ روز", "30 days", "٣٠ يوماً"); else -> uiText("۱ سال", "1 year", "سنة") }, fontSize = 12.sp) })
                    }
                }
                if (displayPoints.size >= 2) {
                    val first = displayPoints.first()
                    val last = displayPoints.last()
                    val minPoint = displayPoints.minByOrNull { it.price }!!
                    val maxPoint = displayPoints.maxByOrNull { it.price }!!
                    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
                        val left = 8.dp.toPx()
                        val right = size.width - 8.dp.toPx()
                        val top = 12.dp.toPx()
                        val bottom = size.height - 15.dp.toPx()
                        val min = displayPoints.minOf { it.price }
                        val max = displayPoints.maxOf { it.price }
                        val spread = (max - min).coerceAtLeast(max * 0.0005)
                        val low = min - spread * 0.12
                        val high = max + spread * 0.12
                        val timeSpan = (last.timestamp - first.timestamp).coerceAtLeast(1L).toDouble()
                        fun point(index: Int): Offset {
                            val p = displayPoints[index]
                            val x = left + ((p.timestamp - first.timestamp) / timeSpan).toFloat() * (right - left)
                            val y = bottom - ((p.price - low) / (high - low)).toFloat() * (bottom - top)
                            return Offset(x, y)
                        }
                        for (i in 0..3) {
                            val y = top + (bottom - top) * i / 3f
                            drawLine(muted.copy(alpha = .15f), Offset(left, y), Offset(right, y), 1.dp.toPx())
                        }
                        val line = Path()
                        displayPoints.forEachIndexed { index, _ ->
                            val p = point(index)
                            if (index == 0) line.moveTo(p.x, p.y) else line.lineTo(p.x, p.y)
                        }
                        val fill = Path().apply {
                            addPath(line)
                            lineTo(point(displayPoints.lastIndex).x, bottom)
                            lineTo(point(0).x, bottom)
                            close()
                        }
                        drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = .27f), lineColor.copy(alpha = .015f)), top, bottom))
                        drawPath(line, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                        val latest = point(displayPoints.lastIndex)
                        drawCircle(lineColor.copy(alpha = .18f), 10.dp.toPx(), latest)
                        drawCircle(lineColor, 5.dp.toPx(), latest)
                        drawCircle(surface, 2.dp.toPx(), latest)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text(uiText("کمترین", "Low", "الأدنى"), color=muted, fontSize=11.sp); Text(Fmt.toman(java.math.BigDecimal.valueOf(minPoint.price)), fontSize=12.sp) }
                        Column(horizontalAlignment=androidx.compose.ui.Alignment.End) { Text(uiText("بیشترین", "High", "الأعلى"), color=muted, fontSize=11.sp); Text(Fmt.toman(java.math.BigDecimal.valueOf(maxPoint.price)), fontSize=12.sp) }
                    }
                    Text(uiText("از ${Fmt.time(first.timestamp)} تا ${Fmt.time(last.timestamp)} · ${points.size} نمونه", "From ${Fmt.time(first.timestamp)} to ${Fmt.time(last.timestamp)} · ${points.size} samples", "من ${Fmt.time(first.timestamp)} إلى ${Fmt.time(last.timestamp)} · ${points.size} عينة"), color=muted, fontSize=11.sp)
                } else {
                    Text(
                        if (historyLoading) uiText("در حال دریافت تاریخچه آنلاین…", "Loading online history…", "جارٍ تحميل السجل…")
                        else if (onlinePoints.isEmpty()) uiText("دریافت تاریخچه آنلاین ناموفق بود؛ اتصال اینترنت یا منبع داده را بررسی کن.", "Online history could not be loaded. Check your connection or data source.", "تعذر تحميل السجل عبر الإنترنت. تحقق من الاتصال أو مصدر البيانات.")
                        else if (rangeIndex == 0) uiText("منبع تاریخچه روزانه است؛ نمودار ۲۴ ساعت واقعی در دسترس نیست.", "History is daily; true intraday 24-hour chart isn't available.", "السجل يومي؛ مخطط ٢٤ ساعة داخل اليوم غير متاح.")
                        else if (displayPoints.isEmpty()) uiText("برای این بازه داده تاریخی موجود نیست.", "No historical data for this range.", "لا توجد بيانات تاريخية لهذه الفترة.")
                        else uiText("برای رسم نمودار حداقل دو داده لازم است.", "At least two data points are needed to draw the chart.", "يلزم نقطتا بيانات على الأقل لرسم المخطط."),
                        color = muted, fontSize = 12.sp
                    )
                }
                if (current != null) Text(uiText("قیمت فعلی از آخرین به‌روزرسانی · نمودار از تاریخچه آنلاین", "Current quote from latest update · chart from online history", "السعر الحالي من آخر تحديث · المخطط من السجل عبر الإنترنت"), color=muted, fontSize=11.sp)
            }
        }
    }
}
