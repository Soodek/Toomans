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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import java.math.BigDecimal

@Composable
fun MarketChartInline(snap: Snapshot, calculatorAsset: String) {
    val ctx = LocalContext.current
    val assets = (snap.rates.keys + listOf("gold18", "coin")).distinct().sorted()
    var asset by remember(calculatorAsset) { mutableStateOf(calculatorAsset) }
    val rate = snap.rates[asset]
    val gold = remember(asset) { if (asset == "gold18" || asset == "coin") GoldRepository.snapshot(ctx) else null }
    val quote = when (asset) { "gold18" -> gold?.gold18; "coin" -> gold?.emamiCoin; else -> null }
    val current = quote?.price ?: rate?.sell
    val high = quote?.high ?: rate?.high
    val low = quote?.low ?: rate?.low
    val change = quote?.changePct ?: rate?.dayChange?.toDouble()
    val name = when(asset) {
        "gold18" -> "طلای ۱۸ عیار"
        "coin" -> "سکه امامی"
        else -> "${Catalog.flag(asset)}  ${Catalog.code(asset)} · ${Catalog.englishName(asset, rate?.name)}"
    }
    val accent = if ((change ?: 0.0) >= 0) androidx.compose.ui.graphics.Color(0xFF36D399) else androidx.compose.ui.graphics.Color(0xFFFF6B78)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val cardSurface = MaterialTheme.colorScheme.surface
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("نمودار بازار", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("نمای روزانه", color = muted, fontSize = 12.sp)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(assets) { item ->
                FilterChip(selected = asset == item, onClick = { asset = item }, label = {
                    Text(when(item) { "gold18" -> "طلا"; "coin" -> "سکه"; else -> Catalog.code(item) })
                })
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(name, color = muted, fontSize = 13.sp)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(current?.let { Fmt.toman(it) } ?: "—", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("تومان", color = muted, fontSize = 12.sp)
                    }
                    if (change != null) {
                        Text("${if (change >= 0) "▲ +" else "▼ "}${"%.2f".format(java.util.Locale.US, change)}٪",
                            color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.background(accent.copy(alpha=.12f), RoundedCornerShape(12.dp)).padding(horizontal=10.dp, vertical=7.dp))
                    }
                }
                if (current != null && high != null && low != null && high > low) {
                    Canvas(Modifier.fillMaxWidth().height(112.dp)) {
                        val min = low.toFloat(); val max = high.toFloat(); val v = current.toFloat()
                        val span = (max-min).coerceAtLeast(1f)
                        val x = ((v-min)/span).coerceIn(0f,1f)*size.width
                        // Range track with current quote marker (actual provider range, not fabricated history).
                        drawLine(accent.copy(alpha=.16f), Offset(0f,size.height*.52f), Offset(size.width,size.height*.52f), 13.dp.toPx(), cap=StrokeCap.Round)
                        drawLine(accent, Offset(0f,size.height*.52f), Offset(x,size.height*.52f), 13.dp.toPx(), cap=StrokeCap.Round)
                        drawCircle(accent, 8.dp.toPx(), Offset(x,size.height*.52f))
                        drawCircle(cardSurface, 4.dp.toPx(), Offset(x,size.height*.52f))
                        drawLine(muted.copy(alpha=.45f), Offset(0f,size.height*.82f), Offset(0f,size.height*.25f), 1.dp.toPx())
                        drawLine(muted.copy(alpha=.45f), Offset(size.width,size.height*.82f), Offset(size.width,size.height*.25f), 1.dp.toPx())
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("کمترین", color=muted, fontSize=11.sp); Text(Fmt.toman(low), fontSize=12.sp) }
                        Column(horizontalAlignment=androidx.compose.ui.Alignment.End) { Text("بیشترین", color=muted, fontSize=11.sp); Text(Fmt.toman(high), fontSize=12.sp) }
                    }
                } else {
                    Text("اطلاعات بازه روزانه برای این دارایی موجود نیست.", color=muted, fontSize=12.sp)
                }
                Text("موقعیت قیمت فعلی در بازه روز · داده واقعی منبع", color=muted, fontSize=11.sp)
            }
        }
    }
}
