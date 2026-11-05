package com.example.tomanrates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.math.BigDecimal

private val GreenC = Color(0xFF4ADE80)
private val AmberC = Color(0xFFFBBF24)
private val RedC = Color(0xFFF87171)

@Composable
fun TomanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF7DB7FF),
            background = Color(0xFF111318),
            surface = Color(0xFF17191F),
            surfaceVariant = Color(0xFF1F222B),
            onBackground = Color(0xFFECEEF3),
            onSurface = Color(0xFFECEEF3),
            onSurfaceVariant = Color(0xFFA9AEBB),
        ),
        content = content,
    )
}

@Composable
fun TomanApp(requested: String?) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var slug by remember { mutableStateOf(Store.defaultSlug(ctx)) }

    LaunchedEffect(requested) {
        if (requested != null) { slug = requested; tab = 0 }
    }

    val snap = remember(tick) { RateRepository.snapshot(ctx) }
    val selected = remember(tick) { Store.selected(ctx) }
    val defaultSlug = remember(tick) { Store.defaultSlug(ctx) }
    val changed: () -> Unit = { tick++; WidgetRenderer.updateAll(ctx) }

    // While the app is open, refresh public market pages about once per minute.
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                RateRepository.refresh(ctx)
                runCatching { GoldRepository.refreshIfDue(ctx) }
            }
            tick++
            WidgetRenderer.updateAll(ctx)
            delay(60_000L)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (tab == 6) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick = { tab = 0 }) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(onClick = { tab = 6 }) {
                    Icon(imageVector = Icons.Filled.Newspaper, contentDescription = "News")
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                listOf("Calculator", "Currencies", "Gold", "AI", "Settings").forEachIndexed { i, label ->
                    val isSelected = tab == i
                    val icon = when (i) {
                        0 -> Icons.Filled.Calculate
                        1 -> Icons.Filled.CurrencyExchange
                        2 -> Icons.Filled.Savings
                        3 -> Icons.Filled.AutoAwesome
                        else -> Icons.Filled.Settings
                    }
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { tab = i },
                        icon = { Icon(imageVector = icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = Color(0xFF24364A),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> CalculatorTab(snap, selected, slug) { slug = it }
                1 -> CurrenciesTab(snap, selected, defaultSlug, changed)
                2 -> GoldTab()
                3 -> AiAssistantTab(snap)
                6 -> NewsHomeTab()
                else -> SettingsTab(snap, changed)
            }
        }
    }
}

// ------------------------------------------------------------------ status

@Composable
fun StatusLine(snap: Snapshot) {
    val last = if (snap.lastSuccessAt > 0) Fmt.time(snap.lastSuccessAt) else ""
    val (text, color) = when (snap.status) {
        Status.LIVE -> "Live · updated $last" to GreenC
        Status.DELAYED -> "Last update attempt failed (${snap.lastError}) · rates from $last" to AmberC
        Status.UNAVAILABLE -> "Source unavailable · last rates from $last. Not live." to RedC
        Status.NEVER -> "Source unavailable. No rates received yet." + (if (snap.lastError.isNotBlank()) " (${snap.lastError})" else "") to RedC
        Status.NO_TOKEN -> "No API token required." to GreenC
    }
    Text(text, color = color, fontSize = 13.sp)
}

// ------------------------------------------------------------------ calculator

fun parseAmount(s: String): BigDecimal? {
    val sb = StringBuilder()
    for (c in s.trim()) when (c) {
        in '\u06F0'..'\u06F9' -> sb.append('0' + (c - '\u06F0'))
        in '\u0660'..'\u0669' -> sb.append('0' + (c - '\u0660'))
        '\u066B' -> sb.append('.')
        ',', '\u066C', ' ' -> {}
        else -> sb.append(c)
    }
    val v = sb.toString().toBigDecimalOrNull() ?: return null
    return if (v.signum() < 0) null else v
}

@Composable
fun CalculatorTab(snap: Snapshot, selected: List<String>, slug: String, onSlug: (String) -> Unit) {
    var amount by remember { mutableStateOf("1") }
    val cur = if (slug in selected) slug else selected.first()
    val rate = snap.rates[cur]
    val unit = rate?.let { Catalog.unitOf(it.name) } ?: 1
    val value = parseAmount(amount)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 92.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Calculator", fontSize = 24.sp, fontWeight = FontWeight.Medium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(selected) { s ->
                FilterChip(
                    selected = s == cur,
                    onClick = { onSlug(s) },
                    label = { Text("${Catalog.flag(s)} ${Catalog.code(s)}") },
                )
            }
        }
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount in ${Catalog.unitLabel(cur, 1)}") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    rate == null -> Text("No rate for ${Catalog.code(cur)} in the last API response.")
                    value == null -> Text("Enter an amount.")
                    else -> {
                        val toman = value.multiply(rate.sell).divide(BigDecimal(unit))
                        Text("${Fmt.toman(toman)} Toman", fontSize = 30.sp, fontWeight = FontWeight.Light)
                        Text(
                            "${Catalog.unitLabel(cur, unit)} = ${Fmt.toman(rate.sell)} Toman (selling price)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                        )
                    }
                }
                StatusLine(snap)
            }
        }
        MarketChartInline(snap, cur)
    }
}

// ------------------------------------------------------------------ currencies

@Composable
fun CurrenciesTab(snap: Snapshot, selected: List<String>, defaultSlug: String, changed: () -> Unit) {
    val ctx = LocalContext.current
    var symbol by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val available = remember(snap, selected) { snap.rates.keys.filter { it !in selected }.sorted() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Your currencies", fontSize = 24.sp, fontWeight = FontWeight.Medium) }
        item {
            Text(
                "The default (★) is the large rate in the widget. The others follow in this order, as many as fit.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
            )
        }
        itemsIndexed(selected, key = { _, s -> s }) { i, s ->
            val r = snap.rates[s]
            val unit = r?.let { Catalog.unitOf(it.name) } ?: 1
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(Catalog.flag(s), fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${Catalog.unitLabel(s, unit)} · ${Catalog.englishName(s, r?.name)}", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            when {
                                r != null -> "${Fmt.toman(r.sell)} Toman"
                                snap.rates.isEmpty() -> "No data yet"
                                else -> "Not in API response"
                            },
                            color = if (r != null) MaterialTheme.colorScheme.onSurfaceVariant else AmberC,
                            fontSize = 12.sp,
                        )
                    }
                    SmallBtn(if (s == defaultSlug) "★" else "☆") { Store.setDefault(ctx, s); changed() }
                    SmallBtn("↑", enabled = i > 0) { Store.move(ctx, s, -1); changed() }
                    SmallBtn("↓", enabled = i < selected.size - 1) { Store.move(ctx, s, 1); changed() }
                    SmallBtn("✕") { error = Store.remove(ctx, s); changed() }
                }
            }
        }
        item { Text("Add by API symbol", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp)) }
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = symbol, onValueChange = { symbol = it; error = null },
                    label = { Text("e.g. omr, chf, usd-hav") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                Button(onClick = {
                    error = Store.addSymbol(ctx, symbol)
                    if (error == null) symbol = ""
                    changed()
                }) { Text("Add") }
            }
        }
        if (error != null) item { Text(error!!, color = RedC, fontSize = 13.sp) }
        item {
            Text(
                if (snap.rates.isEmpty()) "Available currencies appear here after the first successful update."
                else "Available in the latest response (tap to add):",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
            )
        }
        items(available, key = { it }) { s ->
            val r = snap.rates[s]
            Text(
                "+  ${Catalog.flag(s)}  ${Catalog.code(s)} · ${Catalog.englishName(s, r?.name)}",
                modifier = Modifier.fillMaxWidth().clickable { Store.addSymbol(ctx, s); changed() }.padding(vertical = 8.dp),
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun SmallBtn(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick, enabled = enabled,
        contentPadding = PaddingValues(horizontal = 6.dp),
        modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 36.dp),
    ) { Text(label, fontSize = 16.sp) }
}

// ------------------------------------------------------------------ settings

@Composable
fun SettingsTab(snap: Snapshot, changed: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            busy = true
            val out = withContext(Dispatchers.IO) { RateRepository.refresh(ctx) }
            message = when (out) {
                is RateRepository.Outcome.Updated -> "Market prices updated from TGJU."
                is RateRepository.Outcome.Failed -> "Update failed: ${out.reason}"
                is RateRepository.Outcome.TooSoon -> "Next refresh available at ${Fmt.time(out.nextAllowedAt)}."
                is RateRepository.Outcome.NoToken -> "No token is required."
            }
            busy = false
            changed()
        }
    }

    val next = RateRepository.nextAllowedAt(ctx)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Medium) }
        item { Text("Price source: TGJU public market pages · No Alanchand token or API key required.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        item { StatusLine(snap) }
        item {
            Text(
                buildString {
                    append("Last successful update: ")
                    append(if (snap.lastSuccessAt > 0) Fmt.time(snap.lastSuccessAt) else "never")
                    if (snap.sourceTime != null) append("\\nSource timestamp: ${snap.sourceTime}")
                    if (next > 0) append("\\nNext refresh allowed: ${if (next <= System.currentTimeMillis()) "now" else Fmt.time(next)}")
                    append("\\nForeground refresh target: about once per minute. Background refresh is managed by Android and may be delayed by battery restrictions.")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
            )
        }
        item { Button(enabled = !busy, onClick = { refresh() }) { Text(if (busy) "Updating…" else "Update prices now") } }
        if (message != null) item { Text(message!!, fontSize = 13.sp) }
        item { Text("Market values are scraped from public website pages. If the source changes its page structure or blocks requests, updates may temporarily fail.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
    }
}
