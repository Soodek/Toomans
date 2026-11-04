package com.example.tomanrates

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal

@Composable
fun MarketChartTab(snap: Snapshot) {
    var asset by remember { mutableStateOf("omr") }
    var period by remember { mutableStateOf("روز") }
    val assets = listOf("omr", "usd", "eur", "gold18", "coin")
    val rate = snap.rates[asset]
    val gold = if (asset == "gold18" || asset == "coin") GoldRepository.snapshot(androidx.compose.ui.platform.LocalContext.current) else null
    val value: BigDecimal? = when(asset) {
        "gold18" -> gold?.gold18?.price
        "coin" -> gold?.emamiCoin?.price
        else -> rate?.sell
    }
    val high = when(asset) { "gold18" -> gold?.gold18?.high; "coin" -> gold?.emamiCoin?.high; else -> rate?.high }
    val low = when(asset) { "gold18" -> gold?.gold18?.low; "coin" -> gold?.emamiCoin?.low; else -> rate?.low }
    val open = rate?.open
    val change = rate?.dayChange
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .25f)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("نمودار بازار", fontSize = 23.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            assets.forEach { a -> FilterChip(selected = asset == a, onClick = { asset = a }, label = { Text(when(a) { "gold18" -> "طلای ۱۸"; "coin" -> "سکه"; else -> Catalog.code(a) }) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("روز", "هفته", "ماه", "سال").forEach { p -> FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p) }) }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(value?.let { Fmt.toman(it) + " تومان" } ?: "داده‌ای برای این دارایی موجود نیست", fontSize = 24.sp)
                if (period == "روز" && value != null && high != null && low != null) {
                    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
                        val min = low.toFloat()
                        val max = high.toFloat()
                        val span = (max - min).coerceAtLeast(1f)
                        fun y(v: BigDecimal) = size.height - ((v.toFloat() - min) / span * size.height * .76f + size.height * .12f)
                        val center = size.width / 2f
                        drawLine(gridColor, Offset(0f, size.height*.12f), Offset(size.width, size.height*.12f), 1.dp.toPx())
                        drawLine(gridColor, Offset(0f, size.height*.88f), Offset(size.width, size.height*.88f), 1.dp.toPx())
                        drawLine(lineColor, Offset(center, y(high)), Offset(center, y(low)), 3.dp.toPx(), cap=StrokeCap.Round)
                        open?.let { drawLine(lineColor, Offset(center-22.dp.toPx(), y(it)), Offset(center, y(it)), 3.dp.toPx(), cap=StrokeCap.Round) }
                        drawLine(lineColor, Offset(center, y(value)), Offset(center+22.dp.toPx(), y(value)), 3.dp.toPx(), cap=StrokeCap.Round)
                    }
                    Text("کمینه: ${Fmt.toman(low)} · بیشینه: ${Fmt.toman(high)}", fontSize = 11.sp)
                    if (change != null) Text("تغییر روزانه: $change٪", fontSize = 12.sp)
                } else {
                    Text(if (period == "روز") "برای این دارایی هنوز داده‌ی سقف و کف روزانه از منبع دریافت نشده." else "تاریخچه‌ی $period در API فعلی موجود نیست؛ نمودار تاریخی ساختگی نمایش داده نمی‌شود.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text("داده‌ی نرخ ارز: Alanchand · طلا و سکه: منبع بازار ایران / TGJU.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}
