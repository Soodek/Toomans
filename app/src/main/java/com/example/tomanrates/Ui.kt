package com.example.tomanrates

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Switch
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
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

internal object UiPreferences {
    val dark = mutableStateOf(true)
    val language = mutableStateOf("en")
}

fun uiText(fa: String, en: String, ar: String): String = when (UiPreferences.language.value) {
    "fa" -> fa
    "ar" -> ar
    else -> en
}

@Composable
fun TomanTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        UiPreferences.dark.value = Store.darkThemeEnabled(ctx)
        UiPreferences.language.value = Store.appLanguage(ctx)
    }
    MaterialTheme(
        colorScheme = if (UiPreferences.dark.value) darkColorScheme(
            primary = Color(0xFF7DB7FF),
            background = Color(0xFF111318),
            surface = Color(0xFF17191F),
            surfaceVariant = Color(0xFF1F222B),
            onBackground = Color(0xFFECEEF3),
            onSurface = Color(0xFFECEEF3),
            onSurfaceVariant = Color(0xFFA9AEBB),
        ) else lightColorScheme(
            primary = Color(0xFF1769AA),
            background = Color(0xFFF5F6F8),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE8EBF0),
            onBackground = Color(0xFF17191F),
            onSurface = Color(0xFF17191F),
            onSurfaceVariant = Color(0xFF5E6570),
        ),
        content = {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (UiPreferences.language.value == "fa" || UiPreferences.language.value == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) { content() }
        },
    )
}

@Composable
fun TomanApp(requested: String?) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var slug by remember { mutableStateOf(Store.defaultSlug(ctx)) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            if (Store.autoRefreshEnabled(ctx)) {
                withContext(Dispatchers.IO) {
                    RateRepository.refresh(ctx)
                    runCatching { GoldRepository.refreshIfDue(ctx) }
                }
                tick++
                WidgetRenderer.updateAll(ctx)
            }
            delay(60_000L)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (tab == 6) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().height(48.dp).padding(horizontal = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { tab = 0 }) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = uiText("بازگشت", "Back", "رجوع"))
                    }
                    Spacer(Modifier.weight(1f))
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
            if (tab in 0..4) {
                val navShape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                        .shadow(18.dp, navShape)
                        .background(
                            Brush.linearGradient(
                                colors = if (UiPreferences.dark.value)
                                    listOf(Color(0xCC172333), Color(0xCC251C38), Color(0xCC321D35))
                                else listOf(Color(0xDDEAF6FF), Color(0xDDF0E8FF), Color(0xDDFCE8F3))
                            ), navShape
                        ).border(
                            1.dp,
                            Brush.linearGradient(listOf(Color.White.copy(alpha = .55f), Color(0xFF9CCBFF).copy(alpha = .30f), Color.White.copy(alpha = .12f))),
                            navShape
                        )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        val labels = listOf(
                            uiText("ماشین‌حساب", "Calculator", "الحاسبة"),
                            uiText("ارزها", "Currencies", "العملات"),
                            uiText("طلا", "Gold", "الذهب"),
                            uiText("هوش مصنوعی", "AI", "الذكاء الاصطناعي"),
                            uiText("تنظیمات", "Settings", "الإعدادات"),
                        )
                        val icons = listOf(Icons.Filled.Calculate, Icons.Filled.CurrencyExchange, Icons.Filled.Savings, Icons.Filled.AutoAwesome, Icons.Filled.Settings)
                        labels.forEachIndexed { i, label ->
                            val selectedItem = tab == i
                            val scale by animateFloatAsState(if (selectedItem) 1.10f else 1f, animationSpec = spring(dampingRatio = 0.62f, stiffness = 380f), label = "navIconScale")
                            val selectedAlpha by animateFloatAsState(if (selectedItem) 1f else 0f, animationSpec = spring(dampingRatio = 0.78f, stiffness = 240f), label = "navSelectedAlpha")
                            Column(
                                Modifier.weight(1f).clickable { tab = i }.animateContentSize()
                                    .background(Color(0x337DB7FF).copy(alpha = selectedAlpha), androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                                    .padding(vertical = 5.dp, horizontal = 2.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(icons[i], contentDescription = label,
                                    tint = if (selectedItem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = if (selectedItem) -3f else 0f })
                                Text(label, color = if (selectedItem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp, fontWeight = if (selectedItem) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> CalculatorTab(snap, selected, slug, { slug = it }, changed)
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
        Status.LIVE -> uiText("زنده · آخرین به‌روزرسانی $last", "Live · updated $last", "مباشر · آخر تحديث $last") to GreenC
        Status.DELAYED -> uiText("آخرین تلاش ناموفق بود (${snap.lastError}) · قیمت‌ها مربوط به $last", "Last update failed (${snap.lastError}) · rates from $last", "فشلت آخر محاولة تحديث (${snap.lastError}) · الأسعار من $last") to AmberC
        Status.UNAVAILABLE -> uiText("منبع در دسترس نیست · آخرین قیمت‌ها مربوط به $last است.", "Source unavailable · last rates from $last. Not live.", "المصدر غير متاح · آخر الأسعار من $last وليست مباشرة.") to RedC
        Status.NEVER -> uiText("هنوز قیمتی دریافت نشده است.", "Source unavailable. No rates received yet.", "المصدر غير متاح. لم يتم استلام أسعار بعد.") + (if (snap.lastError.isNotBlank()) " (${snap.lastError})" else "") to RedC
        Status.NO_TOKEN -> uiText("نیازی به توکن API نیست.", "No API token required.", "لا حاجة إلى رمز API.") to GreenC
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
fun CalculatorTab(snap: Snapshot, selected: List<String>, slug: String, onSlug: (String) -> Unit, onPriceRefresh: () -> Unit) {
    var amount by remember { mutableStateOf("1") }
    val cur = if (slug in selected) slug else selected.first()
    val rate = snap.rates[cur]
    val unit = rate?.let { Catalog.unitOf(it.name) } ?: 1
    val value = parseAmount(amount)

    val isFa = UiPreferences.language.value == "fa"
    val isAr = UiPreferences.language.value == "ar"
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var updating by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 92.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(uiText("ماشین‌حساب", "Calculator", "الحاسبة"), fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            IconButton(enabled = !updating, onClick = {
                scope.launch {
                    updating = true
                    refreshMessage = null
                    try {
                        val result = withContext(Dispatchers.IO) {
                            val rates = RateRepository.refresh(ctx)
                            val goldOk = runCatching { GoldRepository.refreshIfDue(ctx) }.isSuccess
                            rates to goldOk
                        }
                        refreshMessage = when (val outcome = result.first) {
                            is RateRepository.Outcome.Updated -> uiText("قیمت‌ها با موفقیت به‌روزرسانی شدند.", "Prices updated successfully.", "تم تحديث الأسعار بنجاح.")
                            is RateRepository.Outcome.Failed -> uiText("به‌روزرسانی ناموفق بود: ${outcome.reason}", "Update failed: ${outcome.reason}", "فشل التحديث: ${outcome.reason}")
                            is RateRepository.Outcome.TooSoon -> uiText("قیمت‌ها اخیراً بررسی شده‌اند. زمان مجاز بعدی: ${Fmt.time(outcome.nextAllowedAt)}", "Prices were checked recently. Next allowed update: ${Fmt.time(outcome.nextAllowedAt)}", "تم التحقق من الأسعار مؤخراً. التحديث التالي: ${Fmt.time(outcome.nextAllowedAt)}")
                            is RateRepository.Outcome.NoToken -> uiText("نیازی به توکن نیست.", "No API token is required.", "لا حاجة إلى رمز API.")
                        }
                        if (!result.second && result.first is RateRepository.Outcome.Updated) {
                            refreshMessage = uiText("قیمت ارز به‌روز شد؛ دریافت قیمت طلا انجام نشد.", "Currency prices updated; gold refresh was unavailable.", "تم تحديث أسعار العملات؛ تعذر تحديث الذهب.")
                        }
                        onPriceRefresh()
                    } catch (_: Exception) {
                        refreshMessage = uiText("خطا در به‌روزرسانی. اتصال اینترنت را بررسی کنید.", "Refresh failed. Check your internet connection.", "فشل التحديث. تحقق من اتصال الإنترنت.")
                    } finally {
                        updating = false
                    }
                }
            }) {
                if (updating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, contentDescription = uiText("به‌روزرسانی قیمت‌ها", "Refresh prices", "تحديث الأسعار"))
            }
        }
        if (refreshMessage != null) {
            Text(refreshMessage!!, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
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
            label = { Text(uiText("مقدار به ${Catalog.unitLabel(cur, 1)}", "Amount in ${Catalog.unitLabel(cur, 1)}", "المبلغ بـ ${Catalog.unitLabel(cur, 1)}")) },
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
                    rate == null -> Text(uiText("برای ${Catalog.code(cur)} قیمتی در آخرین پاسخ دریافت نشده است.", "No rate for ${Catalog.code(cur)} in the latest response.", "لا يوجد سعر لـ ${Catalog.code(cur)} في آخر استجابة."))
                    value == null -> Text(uiText("مقداری وارد کنید.", "Enter an amount.", "أدخل مبلغاً."))
                    else -> {
                        val toman = value.multiply(rate.sell).divide(BigDecimal(unit))
                        Text("${Fmt.toman(toman)} ${uiText("تومان", "Toman", "تومان")}", fontSize = 30.sp, fontWeight = FontWeight.Light)
                        Text(
                            "${Catalog.unitLabel(cur, unit)} = ${Fmt.toman(rate.sell)} ${uiText("تومان (قیمت فروش)", "Toman (selling price)", "تومان (سعر البيع)")}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                        )
                    }
                }
                StatusLine(snap)
            }
        }
        val goldQuote = remember(snap.lastSuccessAt) { GoldRepository.snapshot(ctx)?.gold18?.price }
        if (goldQuote != null) {
            val sot = goldQuote.divide(BigDecimal("1000"))
            val ounce = goldQuote.multiply(BigDecimal("31.1034768"))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiText("قیمت واحدهای طلا", "Gold unit prices", "أسعار وحدات الذهب"), fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text(uiText("گرم ۱۸ عیار", "18K gram", "غرام 18 قيراط"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${Fmt.toman(goldQuote)} تومان", fontWeight = FontWeight.Medium) }
                        Column { Text(uiText("سوت (۱/۱۰۰۰ گرم)", "Sot (0.001 g)", "سوت (0.001 غرام)"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${Fmt.toman(sot)} تومان", fontWeight = FontWeight.Medium) }
                    }
                    Column { Text(uiText("اونس تروا (۳۱٫۱۰۳۵ گرم)", "Troy ounce (31.1035 g)", "أونصة تروي (31.1035 غرام)"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${Fmt.toman(ounce)} تومان", fontWeight = FontWeight.Medium) }
                }
            }
        }
        val tether = snap.rates["usdt"] ?: snap.rates["tether"] ?: snap.rates["crypto-tether-toman"]
        if (tether != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Tether (USDT)", fontWeight = FontWeight.SemiBold)
                    Text("${Fmt.toman(tether.sell)} ${uiText("تومان", "Toman", "تومان")}", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    Text(uiText("قیمت فروش تتر · منبع بازار", "USDT selling rate · market source", "سعر بيع USDT · مصدر السوق"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Text(uiText("قیمت تتر در پاسخ فعلی منبع ارز موجود نیست.", "USDT isn't present in the current currency feed.", "USDT غير موجود في تغذية العملات الحالية."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        item { Text(uiText("ارزهای شما", "Your currencies", "عملاتك"), fontSize = 24.sp, fontWeight = FontWeight.Medium) }
        item {
            Text(
                uiText("ارز پیش‌فرض (★) نرخ اصلی ویجت است. بقیه به همین ترتیب نمایش داده می‌شوند.", "The default (★) is the large rate in the widget. The others follow in this order, as many as fit.", "العملة الافتراضية (★) هي السعر الرئيسي في الأداة. تظهر العملات الأخرى بهذا الترتيب."),
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
        item { Text(uiText("افزودن با نماد API", "Add by API symbol", "إضافة باستخدام رمز API"), fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp)) }
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = symbol, onValueChange = { symbol = it; error = null },
                    label = { Text(uiText("مثلاً omr، chf، usd-hav", "e.g. omr, chf, usd-hav", "مثلاً omr، chf، usd-hav")) }, singleLine = true, modifier = Modifier.weight(1f),
                )
                Button(onClick = {
                    error = Store.addSymbol(ctx, symbol)
                    if (error == null) symbol = ""
                    changed()
                }) { Text(uiText("افزودن", "Add", "إضافة")) }
            }
        }
        if (error != null) item { Text(error!!, color = RedC, fontSize = 13.sp) }
        item {
            Text(
                if (snap.rates.isEmpty()) uiText("ارزهای موجود پس از اولین به‌روزرسانی موفق نمایش داده می‌شوند.", "Available currencies appear here after the first successful update.", "ستظهر العملات المتاحة هنا بعد أول تحديث ناجح.")
                else uiText("ارزهای موجود در آخرین پاسخ (برای افزودن انتخاب کنید):", "Available in the latest response (tap to add):", "العملات المتاحة في آخر استجابة (اضغط للإضافة):"),
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
    var autoRefresh by remember { mutableStateOf(Store.autoRefreshEnabled(ctx)) }
    var backgroundRefresh by remember { mutableStateOf(Store.backgroundRefreshEnabled(ctx)) }
    var widgetExpanded by remember { mutableStateOf(false) }
    var appearanceExpanded by remember { mutableStateOf(true) }
    var updatesExpanded by remember { mutableStateOf(true) }
    var sourceExpanded by remember { mutableStateOf(true) }
    var widgetSelected by remember { mutableStateOf(Store.widgetSelected(ctx)) }
    var widgetHero by remember { mutableStateOf(Store.widgetDefaultSlug(ctx)) }

    fun refresh() {
        scope.launch {
            busy = true
            val out = withContext(Dispatchers.IO) {
                val result = RateRepository.refresh(ctx)
                runCatching { GoldRepository.refreshIfDue(ctx) }
                result
            }
            message = when (out) {
                is RateRepository.Outcome.Updated -> "Market prices updated from TGJU."
                is RateRepository.Outcome.Failed -> "Update failed: ${out.reason}"
                is RateRepository.Outcome.TooSoon -> "Prices were checked recently. Try again at ${Fmt.time(out.nextAllowedAt)}."
                is RateRepository.Outcome.NoToken -> "No token is required."
            }
            busy = false
            changed()
        }
    }

    val next = RateRepository.nextAllowedAt(ctx)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(uiText("تنظیمات", "Settings", "الإعدادات"), fontSize = 24.sp, fontWeight = FontWeight.Medium) }
        item {
            Card(modifier = Modifier.animateContentSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { appearanceExpanded = !appearanceExpanded }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text(uiText("ظاهر برنامه", "Appearance", "مظهر التطبيق"), fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)); Text(if (appearanceExpanded) "⌃" else "⌄") }
                    if (appearanceExpanded) {
                    Text(uiText("حالت پوسته", "Theme", "المظهر"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = UiPreferences.dark.value,
                            onClick = {
                                UiPreferences.dark.value = true
                                Store.setDarkThemeEnabled(ctx, true)
                            },
                            label = { Text(uiText("تیره", "Dark", "داكن")) },
                        )
                        FilterChip(
                            selected = !UiPreferences.dark.value,
                            onClick = {
                                UiPreferences.dark.value = false
                                Store.setDarkThemeEnabled(ctx, false)
                            },
                            label = { Text(uiText("روشن", "Light", "فاتح")) },
                        )
                    }
                    Text(uiText("زبان برنامه", "App language", "لغة التطبيق"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = UiPreferences.language.value == "en",
                            onClick = {
                                UiPreferences.language.value = "en"
                                Store.setAppLanguage(ctx, "en")
                            },
                            label = { Text("English") },
                        )
                        FilterChip(
                            selected = UiPreferences.language.value == "fa",
                            onClick = {
                                UiPreferences.language.value = "fa"
                                Store.setAppLanguage(ctx, "fa")
                            },
                            label = { Text("فارسی") },
                        )
                        FilterChip(
                            selected = UiPreferences.language.value == "ar",
                            onClick = {
                                UiPreferences.language.value = "ar"
                                Store.setAppLanguage(ctx, "ar")
                            },
                            label = { Text("العربية") },
                        )
                    }
                    Text(
                        uiText(
                            "تغییر زبان و پوسته فوراً اعمال می‌شود.",
                            "Language and theme changes apply immediately.",
                            "يتم تطبيق تغييرات اللغة والمظهر فوراً."
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    )
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.animateContentSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { updatesExpanded = !updatesExpanded }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text(uiText("به‌روزرسانی قیمت‌ها", "Price updates", "تحديث الأسعار"), fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)); Text(if (updatesExpanded) "⌃" else "⌄") }
                    if (updatesExpanded) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(uiText("به‌روزرسانی خودکار", "Automatic updates", "التحديثات التلقائية"))
                            Text(uiText("هنگام باز بودن برنامه، قیمت‌ها تقریباً هر یک دقیقه بررسی می‌شوند.", "Refresh prices about once a minute while the app is open.", "يتم تحديث الأسعار تقريباً كل دقيقة أثناء فتح التطبيق."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Switch(checked = autoRefresh, onCheckedChange = {
                            autoRefresh = it
                            Store.setAutoRefreshEnabled(ctx, it)
                        })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(uiText("به‌روزرسانی در پس‌زمینه", "Background updates", "التحديث في الخلفية"))
                            Text(uiText("اجازه بده هنگام بسته بودن برنامه هم قیمت‌ها به‌روز شوند. معمولاً هر ۱۵ دقیقه؛ ممکن است اندروید آن را به تأخیر بیندازد.", "Allow Android to refresh prices when the app is closed. Usually every 15 minutes; Android may delay it.", "اسمح لأندرويد بتحديث الأسعار عند إغلاق التطبيق. عادةً كل ١٥ دقيقة، وقد يؤخر أندرويد ذلك."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Switch(checked = backgroundRefresh, onCheckedChange = {
                            backgroundRefresh = it
                            Store.setBackgroundRefreshEnabled(ctx, it)
                            Scheduler.sync(ctx)
                        })
                    }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth().clickable { widgetExpanded = !widgetExpanded }.animateContentSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(uiText("شخصی‌سازی ویجت", "Widget customization", "تخصيص الأداة"), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Text(uiText("انتخاب ارزهای صفحه اصلی", "Choose home-screen currencies", "اختر عملات الشاشة الرئيسية"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Text(if (widgetExpanded) "⌃" else "⌄", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (widgetExpanded) {
                        Text(uiText("ارزهای ویجت (حداکثر ۵ مورد)", "Widget currencies (up to 5)", "عملات الأداة (حتى ٥)"), fontWeight = FontWeight.Medium)
                        val available = (snap.rates.keys + Store.selected(ctx)).distinct().sorted()
                        available.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { symbol ->
                                    FilterChip(selected = symbol in widgetSelected, onClick = {
                                        val next = if (symbol in widgetSelected) widgetSelected - symbol else if (widgetSelected.size < 5) widgetSelected + symbol else widgetSelected
                                        if (next.isNotEmpty()) {
                                            widgetSelected = next
                                            if (widgetHero !in next) widgetHero = next.first()
                                            Store.setWidgetSelected(ctx, next)
                                            Store.setWidgetDefaultSlug(ctx, widgetHero)
                                            changed()
                                        }
                                    }, label = { Text(Catalog.code(symbol)) })
                                }
                            }
                        }
                        Text(uiText("ترتیب ارزهای ویجت", "Widget currency order", "ترتيب عملات الأداة"), fontWeight = FontWeight.Medium)
                        widgetSelected.forEachIndexed { index, symbol ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(Catalog.code(symbol), modifier = Modifier.weight(1f), fontWeight = if (widgetHero == symbol) FontWeight.Bold else FontWeight.Normal)
                                SmallBtn("↑", enabled = index > 0) {
                                    val reordered = widgetSelected.toMutableList().also { list ->
                                        val tmp = list[index - 1]; list[index - 1] = list[index]; list[index] = tmp
                                    }
                                    widgetSelected = reordered
                                    Store.setWidgetSelected(ctx, reordered)
                                    changed()
                                }
                                SmallBtn("↓", enabled = index < widgetSelected.lastIndex) {
                                    val reordered = widgetSelected.toMutableList().also { list ->
                                        val tmp = list[index + 1]; list[index + 1] = list[index]; list[index] = tmp
                                    }
                                    widgetSelected = reordered
                                    Store.setWidgetSelected(ctx, reordered)
                                    changed()
                                }
                            }
                        }
                        Text(uiText("ارز اصلی ویجت", "Main widget currency", "العملة الرئيسية للأداة"), fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            widgetSelected.forEach { symbol ->
                                FilterChip(selected = widgetHero == symbol, onClick = {
                                    widgetHero = symbol
                                    Store.setWidgetDefaultSlug(ctx, symbol)
                                    changed()
                                }, label = { Text(Catalog.code(symbol)) })
                            }
                        }
                        Text(uiText("تغییرات بلافاصله روی ویجت اعمال می‌شوند.", "Changes apply to the widget immediately.", "يتم تطبيق التغييرات على الأداة فوراً."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.animateContentSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { sourceExpanded = !sourceExpanded }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text(uiText("منبع داده", "Data source", "مصدر البيانات"), fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)); Text(if (sourceExpanded) "⌃" else "⌄") }
                    if (sourceExpanded) {
                    Text(uiText("صفحات عمومی بازار TGJU · بدون نیاز به کلید API یا توکن الان‌چند.", "TGJU public market pages · No API key or Alanchand token required.", "صفحات السوق العامة من TGJU · لا حاجة إلى مفتاح API أو رمز Alanchand."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    StatusLine(snap)
                    Text(
                        buildString {
                            append("Last successful update: ")
                            append(if (snap.lastSuccessAt > 0) Fmt.time(snap.lastSuccessAt) else "never")
                            if (snap.sourceTime != null) append("\nSource timestamp: ${snap.sourceTime}")
                            if (next > 0) append("\nNext refresh allowed: ${if (next <= System.currentTimeMillis()) "now" else Fmt.time(next)}")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    )
                    Button(enabled = !busy, onClick = { refresh() }) { Text(if (busy) uiText("در حال به‌روزرسانی…", "Updating…", "جارٍ التحديث…") else uiText("به‌روزرسانی قیمت‌ها", "Update prices now", "تحديث الأسعار الآن")) }
                    if (message != null) Text(message!!, fontSize = 13.sp)
                    Text("Prices are read from public website pages. If TGJU changes its page structure or blocks requests, updates may temporarily fail.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
