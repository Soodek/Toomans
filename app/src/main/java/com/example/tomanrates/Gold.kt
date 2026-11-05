package com.example.tomanrates

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL

private const val GOLD_DATA_URL =
    "https://raw.githubusercontent.com/iran-market/iran-market.github.io/main/data/popular.json"

data class GoldQuote(
    val label: String,
    val price: BigDecimal,
    val changePct: Double?,
    val high: BigDecimal?,
    val low: BigDecimal?,
    val timestampTehran: String?,
    val stale: Boolean,
)

data class GoldMarketSnapshot(
    val gold18: GoldQuote,
    val emamiCoin: GoldQuote?,
    val updatedAt: String?,
    val source: String,
    val stale: Boolean,
)

object GoldRepository {
    private const val PREFS = "iran_gold_market"
    private const val CACHE = "popular_json"

    fun snapshot(ctx: Context): GoldMarketSnapshot? {
        val raw = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CACHE, null) ?: return null
        return parse(raw)
    }

    fun refresh(ctx: Context): GoldMarketSnapshot {
        val rows = TgjuMarket.parseRows(TgjuMarket.fetchHtml())
        val gold = rows.values.firstOrNull {
            it.key.contains("geram18", true) ||
                it.key.contains("gold_18", true) ||
                (it.name.contains("۱۸") && it.name.contains("گرم"))
        } ?: throw IllegalStateException("TGJU 18K gold quote was not found on its public market page")
        val coin = rows.values.firstOrNull {
            it.key.contains("sekkeh", true) ||
                (it.name.contains("سکه") && it.name.contains("امامی"))
        }
        val result = GoldMarketSnapshot(
            gold18 = gold.toGoldQuote("طلای ۱۸ عیار"),
            emamiCoin = coin?.toGoldQuote("سکه امامی"),
            updatedAt = gold.time,
            source = "TGJU public market page",
            stale = false,
        )
        val json = org.json.JSONObject().put("gold", quoteJson(result.gold18))
            .put("source", result.source).put("updated", result.updatedAt ?: "")
            .put("stale", result.stale)
        result.emamiCoin?.let { json.put("coin", quoteJson(it)) }
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(CACHE, json.toString()).apply()
        return result
    }

    private fun TgjuMarket.QuoteRow.toGoldQuote(fallback: String) = GoldQuote(
        label = name.ifBlank { fallback },
        price = priceToman,
        changePct = changePct?.multiply(BigDecimal.valueOf(changeSign.toLong()))?.toDouble(),
        high = highToman,
        low = lowToman,
        timestampTehran = time,
        stale = false,
    )

    private fun quoteJson(q: GoldQuote) = org.json.JSONObject()
        .put("label", q.label).put("price", q.price)
        .put("change", q.changePct ?: org.json.JSONObject.NULL)
        .put("high", q.high ?: org.json.JSONObject.NULL)
        .put("low", q.low ?: org.json.JSONObject.NULL)
        .put("time", q.timestampTehran ?: "")

    private fun parse(raw: String): GoldMarketSnapshot? = try {
        val root = org.json.JSONObject(raw)
        fun quote(key: String): GoldQuote? {
            val item = root.optJSONObject(key) ?: return null
            val price = item.opt("price")?.toString()?.toBigDecimalOrNull() ?: return null
            return GoldQuote(
                label = item.optString("label", key),
                price = price,
                changePct = if (item.isNull("change")) null else item.optDouble("change"),
                high = item.opt("high")?.toString()?.toBigDecimalOrNull(),
                low = item.opt("low")?.toString()?.toBigDecimalOrNull(),
                timestampTehran = item.optString("time", "").ifBlank { null },
                stale = root.optBoolean("stale", false),
            )
        }
        val gold = quote("gold") ?: return null
        GoldMarketSnapshot(
            gold18 = gold,
            emamiCoin = quote("coin"),
            updatedAt = root.optString("updated", "").ifBlank { null },
            source = root.optString("source", "TGJU"),
            stale = root.optBoolean("stale", false),
        )
    } catch (_: Exception) { null }
}

@Composable
fun GoldTab() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var market by remember { mutableStateOf(GoldRepository.snapshot(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var weight by remember { mutableStateOf("1") }
    var karat by remember { mutableStateOf(18) }

    fun refresh() {
        scope.launch {
            busy = true
            message = null
            try {
                market = withContext(Dispatchers.IO) { GoldRepository.refresh(ctx) }
                message = "Market data updated."
            } catch (e: Exception) {
                message = if (market == null) "Could not load gold prices. Check your internet connection."
                    else "Update failed. Showing the last saved prices."
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    val grams = parseAmount(weight)
    val basePrice = market?.gold18?.price
    val purityFactor = if (karat == 24) BigDecimal("1.3333333333") else BigDecimal.ONE
    val estimatedValue = if (grams != null && basePrice != null) {
        grams.multiply(basePrice).multiply(purityFactor)
    } else null

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Iran Gold Market", fontSize = 24.sp, fontWeight = FontWeight.Medium) }
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Prices in Toman · per gram unless noted", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Button(enabled = !busy, onClick = { refresh() }) {
                    Text(if (busy) "Updating…" else "Refresh")
                }
            }
        }
        if (market == null) {
            item { Text("No gold prices cached yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item { GoldPriceCard(market!!.gold18, "18K gold · 750") }
            item {
                val pure24 = market!!.gold18.price.multiply(BigDecimal("1.3333333333"))
                GoldPriceCard(
                    GoldQuote("24K gold · calculated equivalent", pure24, market!!.gold18.changePct, null, null, market!!.gold18.timestampTehran, market!!.gold18.stale),
                    "24K gold · estimated",
                )
            }
            if (market!!.emamiCoin != null) {
                item { GoldPriceCard(market!!.emamiCoin!!, "Emami coin · full coin") }
            }
            item {
                Text(
                    "Source: ${market!!.source} · data is published about every 30 minutes",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            if (market!!.stale) {
                item { Text("Warning: the data source marks these prices as stale.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
            item {
                Text(
                    "Last market timestamp: ${market!!.updatedAt ?: market!!.gold18.timestampTehran ?: "not provided"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Text("Gold value calculator", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Weight in grams") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = karat == 18, onClick = { karat = 18 }, label = { Text("18K") })
                        FilterChip(selected = karat == 24, onClick = { karat = 24 }, label = { Text("24K") })
                    }
                    Text(
                        estimatedValue?.let { "${Fmt.toman(it)} Toman" } ?: "Enter a valid weight to calculate.",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Estimate based on the 18K market rate. Excludes making charges, seller profit, tax and coin premium.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        if (message != null) item {
            Text(message!!, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        item {
            Text(
                "Market data is informational only. The 24K value is calculated from the 18K rate, not a separate market quote.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun GoldPriceCard(quote: GoldQuote, title: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${Fmt.toman(quote.price)} Toman", fontSize = 25.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                quote.changePct?.let {
                    val sign = if (it > 0) "+" else ""
                    Text("24h: $sign${"%.2f".format(java.util.Locale.US, it)}%", fontSize = 12.sp,
                        color = when { it > 0 -> Color(0xFF4ADE80); it < 0 -> Color(0xFFF87171); else -> MaterialTheme.colorScheme.onSurfaceVariant })
                }
                if (quote.high != null) Text("High: ${Fmt.toman(quote.high)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (quote.low != null) Text("Low: ${Fmt.toman(quote.low)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
