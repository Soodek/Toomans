package com.example.tomanrates

import android.content.Context
import android.text.Html
import android.util.Xml
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Persian news per country. Sources are two public RSS feeds (BBC Persian, DW Persian). They are general world
 * feeds, so "news about a country" means: the item's title/summary names that country. Nothing is generated,
 * translated or summarised; every item links to the original article.
 */
data class NewsItem(val title: String, val link: String, val source: String, val at: Long, val text: String, val imageUrl: String? = null)
data class NewsResult(val items: List<NewsItem>, val fetchedAt: Long, val failedFeeds: Int)

// ------------------------------------------------------------------ text helpers

object NewsText {
    /** Unifies Arabic/Persian letter variants and drops ZWNJ + diacritics so "می‌شود" style differences never block a match. */
    fun norm(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '\u064A', '\u0649' -> sb.append('\u06CC')
                '\u0643' -> sb.append('\u06A9')
                '\u200C', '\u200D', '\u200E', '\u200F' -> {}
                in '\u064B'..'\u065F' -> {}
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}

// ------------------------------------------------------------------ currency -> country

data class Country(val label: String, val names: List<String>, val exclude: List<String> = emptyList())

object NewsCountries {
    private val map: Map<String, Country> = mapOf(
        "usd" to Country("United States", listOf("آمریکا", "امریکا", "ایالات متحده", "واشنگتن", "United States", "USA", "US")),
        "eur" to Country("Europe (EU)", listOf("اتحادیه اروپا", "اروپا", "شنگن", "آلمان", "فرانسه", "ایتالیا", "اسپانیا", "هلند", "اتریش", "بلژیک", "یونان", "پرتغال", "ایرلند", "فنلاند")),
        "gbp" to Country("United Kingdom", listOf("بریتانیا", "انگلیس", "انگلستان", "لندن", "United Kingdom", "Britain", "London")),
        "aed" to Country("UAE", listOf("امارات", "دبی", "ابوظبی", "شارجه", "UAE", "Dubai", "Abu Dhabi")),
        "omr" to Country("Oman", listOf("عمان", "مسقط", "Oman", "Muscat"), listOf("دریای عمان", "خلیج عمان", "Gulf of Oman")),
        "try" to Country("Turkey", listOf("ترکیه", "استانبول", "آنکارا", "Turkey", "Istanbul", "Ankara")),
        "cny" to Country("China", listOf("چین", "پکن", "China", "Beijing")),
        "cad" to Country("Canada", listOf("کانادا", "تورنتو", "ونکوور", "Canada", "Toronto", "Vancouver")),
        "aud" to Country("Australia", listOf("استرالیا", "سیدنی", "ملبورن", "Australia", "Sydney", "Melbourne")),
        "rub" to Country("Russia", listOf("روسیه", "مسکو", "Russia", "Moscow")),
        "iqd" to Country("Iraq", listOf("عراق", "بغداد", "Iraq", "Baghdad")),
        "myr" to Country("Malaysia", listOf("مالزی", "کوالالامپور")),
        "gel" to Country("Georgia", listOf("گرجستان", "تفلیس")),
        "azn" to Country("Azerbaijan", listOf("جمهوری آذربایجان", "باکو")),
        "amd" to Country("Armenia", listOf("ارمنستان", "ایروان")),
        "thb" to Country("Thailand", listOf("تایلند", "بانکوک")),
        "inr" to Country("India", listOf("هند", "دهلی", "India", "New Delhi"), listOf("اقیانوس هند", "Indian Ocean")),
        "pkr" to Country("Pakistan", listOf("پاکستان", "اسلام آباد", "Pakistan", "Islamabad")),
        "jpy" to Country("Japan", listOf("ژاپن", "توکیو", "Japan", "Tokyo")),
        "sar" to Country("Saudi Arabia", listOf("عربستان", "ریاض", "Saudi Arabia", "Riyadh")),
        "afn" to Country("Afghanistan", listOf("افغانستان", "کابل", "Afghanistan", "Kabul")),
        "sek" to Country("Sweden", listOf("سوئد", "استکهلم")),
        "chf" to Country("Switzerland", listOf("سوئیس", "ژنو", "زوریخ")),
        "qar" to Country("Qatar", listOf("قطر", "دوحه", "Qatar", "Doha")),
        "krw" to Country("South Korea", listOf("کره جنوبی", "سئول")),
        "nok" to Country("Norway", listOf("نروژ", "اسلو")),
        "nzd" to Country("New Zealand", listOf("نیوزیلند", "زلاند نو")),
        "sgd" to Country("Singapore", listOf("سنگاپور")),
        "hkd" to Country("Hong Kong", listOf("هنگ کنگ", "هنگکنگ")),
        "kwd" to Country("Kuwait", listOf("کویت", "Kuwait")),
        "dkk" to Country("Denmark", listOf("دانمارک", "کپنهاگ")),
        "bhd" to Country("Bahrain", listOf("بحرین", "منامه", "Bahrain", "Manama")),
        "tjs" to Country("Tajikistan", listOf("تاجیکستان", "دوشنبه")),
        "tmt" to Country("Turkmenistan", listOf("ترکمنستان", "عشق آباد")),
        "kgs" to Country("Kyrgyzstan", listOf("قرقیزستان", "بیشکک")),
        "syp" to Country("Syria", listOf("سوریه", "دمشق")),
        "brl" to Country("Brazil", listOf("برزیل", "برازیل")),
        "ars" to Country("Argentina", listOf("آرژانتین")),
    )

    /** Words that make an item about migration, residency, visas or work permits (matched inside words on purpose). */
    private val MIGRATION: List<String> = listOf(
        "مهاجر", "ویزا", "روادید", "اقامت", "پناهندگی", "پناهجو", "پناهنده", "تابعیت", "شهروندی",
        "اجازه کار", "مجوز کار", "دیپورت", "شنگن", "گرین کارت", "visa", "immigration", "residency", "migrant", "migration", "work permit",
    ).map { NewsText.norm(it) }

    private val regexCache = HashMap<String, Regex>()

    fun of(slug: String): Country? = map[slug.substringBefore('-')]

    private fun regexFor(base: String, c: Country): Regex = regexCache.getOrPut(base) {
        val alt = c.names.joinToString("|") { Regex.escape(NewsText.norm(it)) }
        Regex("(?<![\\p{L}\\p{N}])(?:$alt)(?:یی|ای|ایی|ها|های|ی)?(?![\\p{L}\\p{N}])")
    }

    fun filter(items: List<NewsItem>, slug: String, migrationOnly: Boolean): List<NewsItem> {
        val base = slug.substringBefore('-')
        val c = map[base] ?: return emptyList()
        val re = regexFor(base, c)
        val excl = c.exclude.map { NewsText.norm(it) }
        return items.filter { n ->
            var t = NewsText.norm(n.title + " " + n.text)
            for (x in excl) t = t.replace(x, " ")
            re.containsMatchIn(t) && (!migrationOnly || MIGRATION.any { m -> t.contains(m) })
        }
    }
}

// ------------------------------------------------------------------ fetching + parsing

object NewsRepository {
    private val FEEDS = listOf(
        Triple("aljazeera", "الجزيرة", "https://www.aljazeera.net/rss"),
        Triple("bbc", "BBC Persian", "https://feeds.bbci.co.uk/persian/rss.xml"),
        Triple("dw", "DW فارسی", "https://rss.dw.com/xml/rss-per-all_volltext"),
        Triple("radiofarda", "رادیو فردا", "https://www.radiofarda.com/api/zrttpol-vomx-tpeoogpi"),
        Triple("voa", "صدای آمریکا", "https://ir.voanews.com/api/zbtpil-vomx-tpeqiyp"),
    )
    private val OMAN_FEED = Triple("omanobserver", "Oman Observer", "https://www.omanobserver.om/rssFeed/home-page")
    private const val TTL_MS = 3 * 60 * 60 * 1000L      // a feed is re-downloaded at most every 3 hours
    private const val MIN_FORCE_MS = 10 * 60 * 1000L    // the Refresh button cannot go below 10 minutes
    private const val MAX_BYTES = 4_000_000

    /** Blocking: call from Dispatchers.IO. Uses the cached copy when it is fresh or when the download fails. */
    @Synchronized
    fun load(ctx: Context, force: Boolean, slug: String): NewsResult {
        val now = System.currentTimeMillis()
        val all = ArrayList<NewsItem>()
        var failed = 0
        var newest = 0L
        for ((id, name, url) in (FEEDS + if (slug.substringBefore("-") == "omr") listOf(OMAN_FEED) else emptyList())) {
            val f = File(ctx.cacheDir, "news_$id.xml")
            val age = if (f.exists()) now - f.lastModified() else Long.MAX_VALUE
            var xml: String? = null
            if (age > TTL_MS || (force && age > MIN_FORCE_MS)) {
                xml = fetch(url)
                if (xml != null) {
                    try { f.writeText(xml) } catch (e: Exception) { }
                } else failed++
            }
            if (xml == null && f.exists()) xml = try { f.readText() } catch (e: Exception) { null }
            if (xml != null) {
                all += parseRss(xml, name)
                newest = maxOf(newest, f.lastModified().takeIf { it > 0 } ?: now)
            }
        }
        return NewsResult(all.distinctBy { it.link }.sortedByDescending { it.at }, newest, failed)
    }

    private fun fetch(url: String): String? {
        var c: HttpURLConnection? = null
        return try {
            c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "TomanRates/1.0")
            }
            if (c.responseCode != 200) null
            else {
                val bos = ByteArrayOutputStream()
                val buf = ByteArray(16_384)
                var total = 0
                c.inputStream.use { ins ->
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) return null
                        bos.write(buf, 0, n)
                    }
                }
                String(bos.toByteArray(), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }

    private fun parseDate(s: String): Long {
        for (fmt in listOf("EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd MMM yyyy HH:mm:ss Z")) {
            try {
                val d = SimpleDateFormat(fmt, Locale.US).parse(s.trim())
                if (d != null) return d.time
            } catch (e: Exception) { }
        }
        return 0L
    }

    fun parseRss(xml: String, source: String): List<NewsItem> {
        val out = ArrayList<NewsItem>()
        try {
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(StringReader(xml))
            var inItem = false
            var title = ""; var link = ""; var date = ""; var desc = ""; var image = ""
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    when (p.name) {
                        "item" -> { inItem = true; title = ""; link = ""; date = ""; desc = ""; image = "" }
                        "title" -> if (inItem) title = p.nextText()
                        "link" -> if (inItem) link = p.nextText()
                        "pubDate" -> if (inItem) date = p.nextText()
                        "description" -> if (inItem) desc = p.nextText()
                        "enclosure", "thumbnail", "content" -> if (inItem) {
                            val u = p.getAttributeValue(null, "url")
                            if (u != null && u.startsWith("http") && image.isBlank()) image = u
                        }
                    }
                } else if (ev == XmlPullParser.END_TAG && p.name == "item" && inItem) {
                    inItem = false
                    val safeLink = link.trim().replaceFirst("http://", "https://")
                    if (title.isNotBlank() && safeLink.startsWith("https://")) {
                        val text = Html.fromHtml(desc, Html.FROM_HTML_MODE_LEGACY).toString()
                            .replace('\uFFFC', ' ').trim().take(400)
                        out += NewsItem(title.trim(), safeLink, source, parseDate(date), text, image.takeIf { it.startsWith("http") })
                    }
                }
                ev = p.next()
            }
        } catch (e: Exception) {
            // keep whatever was parsed before the error
        }
        return out
    }
}

// ------------------------------------------------------------------ UI

@Composable
fun NewsSection(slug: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<NewsResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var migrationOnly by remember { mutableStateOf(true) }

    fun load(force: Boolean) {
        scope.launch {
            loading = true
            result = withContext(Dispatchers.IO) { NewsRepository.load(ctx, force, slug) }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load(false) }

    val country = NewsCountries.of(slug)
    val list = remember(result, slug, migrationOnly) {
        result?.let { NewsCountries.filter(it.items, slug, migrationOnly) } ?: emptyList()
    }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${Catalog.flag(slug)}  News · ${country?.label ?: Catalog.code(slug)}",
                fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { load(true) }, enabled = !loading) { Text(if (loading) "Loading…" else "Refresh") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = migrationOnly, onClick = { migrationOnly = true }, label = { Text("Migration") })
            FilterChip(selected = !migrationOnly, onClick = { migrationOnly = false }, label = { Text("All mentions") })
        }
        val r = result
        when {
            country == null -> Text("No country is linked to ${Catalog.code(slug)}.", color = dim, fontSize = 13.sp)
            r == null -> Text(if (loading) "Loading news…" else "News unavailable.", color = dim, fontSize = 13.sp)
            r.items.isEmpty() -> Text("Couldn't load news. Check your connection and tap Refresh.", color = dim, fontSize = 13.sp)
            list.isEmpty() -> Text(
                if (migrationOnly) "No Persian migration news about ${country.label} in the latest BBC / DW items. Try \"All mentions\"."
                else "No mentions of ${country.label} in the latest BBC / DW items.",
                color = dim, fontSize = 13.sp,
            )
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.link }) { n -> NewsCard(n) { uri.openUri(n.link) } }
            }
        }
        if (r != null && r.fetchedAt > 0) {
            Text(
                "الجزيرة · BBC فارسی · DW فارسی · رادیو فردا · صدای آمریکا${if (slug.substringBefore("-") == "omr") " · العُمانية" else ""} · fetched ${Fmt.time(r.fetchedAt)}" +
                    (if (r.failedFeeds > 0) " · ${r.failedFeeds} feed(s) failed" else ""),
                color = dim, fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun NewsCard(n: NewsItem, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (n.imageUrl != null) NewsImage(n.imageUrl)
            Text(n.title, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                n.source + (if (n.at > 0) " · ${Fmt.time(n.at)}" else ""),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

 
@Composable
private fun NewsImage(url: String) {
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "TomanRates/1.0")
                }
                conn.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }.also { conn.disconnect() }
            } catch (_: Exception) { null }
        }
    }
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = androidx.compose.ui.graphics.asImageBitmap(bitmap!!),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(150.dp)
        )
    }
}
