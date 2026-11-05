package com.example.tomanrates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NewsHomeTab() {
    var country by remember { mutableStateOf("omr") }
    val choices = listOf("omr" to "عمان", "iqd" to "عراق", "usd" to "آمریکا", "eur" to "اروپا", "irr" to "ایران")
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("اتاق خبر", fontSize = 23.sp)
        Text("خبرهای فارسی از منابع خبری؛ برای مشاهده متن کامل، خبر را باز کن.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            choices.forEach { (slug, label) -> FilterChip(selected = country == slug, onClick = { country = slug }, label = { Text(label) }) }
        }
        if (country == "irr") {
            Text("برای ایران، فیدهای فارسی عمومی نمایش داده می‌شوند. فیلتر موضوعی ایران در حال حاضر محدود به تشخیص نام کشور در عنوان/خلاصه است.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            NewsSection("irr", Modifier.weight(1f).fillMaxWidth())
        } else NewsSection(country, Modifier.weight(1f).fillMaxWidth())
    }
}
