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
                Text("نمایش بازه: $period", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Canvas(Modifier.fillMaxWidth().height(170.dp)) {
                    val c = MaterialTheme.colorScheme.primary
                    val mid = size.height * .52f
                    drawLine(c.copy(alpha=.18f), Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
                    drawLine(c.copy(alpha=.7f), Offset(0f, size.height*.25f), Offset(size.width, size.height*.25f), 1.dp.toPx())
                    drawLine(c.copy(alpha=.7f), Offset(0f, size.height*.78f), Offset(size.width, size.height*.78f), 1.dp.toPx())
                    drawLine(c, Offset(0f, mid), Offset(size.width, mid), 3.dp.toPx(), cap=StrokeCap.Round)
                }
                Text("نکته: این نسخه فقط آخرین قیمت لحظه‌ای/ذخیره‌شده را دارد. تاریخچه‌ی واقعی برای هفته، ماه و سال هنوز از منبع داده دریافت نمی‌شود؛ بنابراین نمودار تاریخی ساختگی نمایش داده نمی‌شود.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text("منبع نرخ ارز: Alanchand · منبع طلا و سکه: داده بازار ایران / TGJU", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}
