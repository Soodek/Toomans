package com.example.tomanrates

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val available = (snap.rates.keys + listOf("gold18", "coin")).distinct().sorted()
    var selected by remember { mutableStateOf(listOf("omr", "usd", "eur").filter { it in available }.ifEmpty { available.take(1) }) }
    var period by remember { mutableStateOf("روز") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("مقایسه بازار", fontSize = 23.sp)
        Text("دارایی‌ها را برای مقایسه انتخاب کن؛ هرکدام مقیاس قیمت مستقل دارد.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(available) { asset ->
                val label = when(asset) { "gold18" -> "طلای ۱۸"; "coin" -> "سکه امامی"; else -> Catalog.code(asset) }
                FilterChip(selected = asset in selected, onClick = {
                    selected = if (asset in selected) {
                        if (selected.size > 1) selected - asset else selected
                    } else selected + asset
                }, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("روز", "هفته", "ماه", "سال").forEach { p ->
                FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p) })
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(selected, key = { it }) { asset -> MarketAssetCard(snap, asset, period, ctx) }
        }
    }
}

@Composable
private fun MarketAssetCard(snap: Snapshot, asset: String, period: String, ctx: android.content.Context) {
    val rate = snap.rates[asset]
    val gold = if (asset == "gold18" || asset == "coin") GoldRepository.snapshot(ctx) else null
    val quote = when(asset) { "gold18" -> gold?.gold18; "coin" -> gold?.emamiCoin; else -> null }
    val value = quote?.price ?: rate?.sell
    val high = quote?.high ?: rate?.high
    val low = quote?.low ?: rate?.low
    val open = rate?.open
    val change = quote?.changePct ?: rate?.dayChange?.toDouble()
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .25f)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(when(asset) { "gold18" -> "طلای ۱۸ عیار"; "coin" -> "سکه امامی"; else -> Catalog.englishName(asset, rate?.name) + " · " + Catalog.code(asset) }, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value?.let { Fmt.toman(it) + " تومان" } ?: "داده‌ای موجود نیست", fontSize = 22.sp)
            if (change != null) Text("تغییر روزانه: \${if (change > 0) "+" else ""}\${"%.2f".format(java.util.Locale.US, change)}٪", fontSize = 12.sp)
            if (period == "روز" && value != null && high != null && low != null) {
                Canvas(Modifier.fillMaxWidth().height(110.dp)) {
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
                Text("کمینه: \${Fmt.toman(low)} · بیشینه: \${Fmt.toman(high)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(if (period == "روز") "بازه روزانه از منبع در دسترس نیست." else "تاریخچه \${period} در API فعلی وجود ندارد؛ برای جلوگیری از نمایش داده ساختگی، نمودار تاریخی نشان داده نمی‌شود.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}
