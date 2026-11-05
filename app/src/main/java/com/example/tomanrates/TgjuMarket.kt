package com.example.tomanrates

import org.json.JSONObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.regex.Pattern

/**
 * Reads the public market tables rendered by TGJU. This is HTML extraction, not a
 * private API or a user token. Keep all site-specific parsing isolated here.
 *
 * TGJU quotes Iranian prices in rial; the app stores/displays toman.
 */
object TgjuMarket {
    private const val HOME = "https://www.tgju.org/"
    private val rowPattern = Pattern.compile("<tr\\b([^>]*)>(.*?)</tr>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    private val cellPattern = Pattern.compile("<td\\b[^>]*>(.*?)</td>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    private val attrPattern = Pattern.compile("([a-zA-Z0-9_-]+)\\s*=\\s*[\"']([^\"']*)[\"']")
    private val tagPattern = Pattern.compile("<[^>]+>")
    private val spacePattern = Pattern.compile("\\s+")

    data class QuoteRow(
        val key: String,
        val name: String,
        val priceToman: BigDecimal,
        val lowToman: BigDecimal?,
        val highToman: BigDecimal?,
        val changePct: BigDecimal?,
        val changeSign: Int,
        val time: String?,
    )

    fun fetchHtml(): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(HOME).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 18_000
                requestMethod = "GET"
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
                setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.7")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("TGJU returned HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection?.disconnect()
        }
    }

    fun parseRows(html: String): LinkedHashMap<String, QuoteRow> {
        val result = LinkedHashMap<String, QuoteRow>()
        val rows = rowPattern.matcher(html)
        while (rows.find()) {
            val attrs = attributes(rows.group(1))
            val key = attrs["data-market-nameslug"]?.trim().orEmpty()
            if (key.isBlank()) continue
            val priceRial = number(attrs["data-price"]) ?: continue
            if (priceRial.signum() <= 0) continue

            val body = rows.group(2)
            val cells = ArrayList<String>()
            val cellMatcher = cellPattern.matcher(body)
            while (cellMatcher.find()) cells.add(cellMatcher.group(1))
            val nameMatch = Regex("<th\\b[^>]*>(.*?)</th>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(body)
            val name = clean(nameMatch?.groupValues?.getOrNull(1).orEmpty())
            if (name.isBlank()) continue

            // TGJU market-table body order: live price, change, low, high, time.
            val changeCell = cells.getOrNull(1).orEmpty()
            val low = number(clean(cells.getOrNull(2).orEmpty()))
            val high = number(clean(cells.getOrNull(3).orEmpty()))
            val time = clean(cells.getOrNull(4).orEmpty()).ifBlank { null }
            val percentText = Regex("([0-9۰-۹٠-٩]+(?:[.,٫][0-9۰-۹٠-٩]+)?)\\s*%")
                .find(clean(changeCell))?.groupValues?.getOrNull(1)
            val percent = percentText?.let { number(it) }
            val sign = when {
                Regex("class\\s*=\\s*[\"'][^\"']*\\blow\\b", RegexOption.IGNORE_CASE).containsMatchIn(changeCell) -> -1
                Regex("class\\s*=\\s*[\"'][^\"']*\\bhigh\\b", RegexOption.IGNORE_CASE).containsMatchIn(changeCell) -> 1
                else -> 0
            }
            result[key] = QuoteRow(
                key = key,
                name = name,
                priceToman = priceRial.divide(BigDecimal.TEN),
                lowToman = low?.divide(BigDecimal.TEN),
                highToman = high?.divide(BigDecimal.TEN),
                changePct = percent,
                changeSign = sign,
                time = time,
            )
        }
        if (result.isEmpty()) throw IllegalStateException("TGJU page format changed or no market rows were found")
        return result
    }

    fun ratesJson(rows: Map<String, QuoteRow>): String {
        val root = JSONObject()
        rows.values.forEach { q ->
            val slug = appSlug(q.key) ?: return@forEach
            val item = JSONObject()
                .put("slug", slug)
                .put("name", q.name)
                .put("sell", q.priceToman)
                .put("updated_at", q.time ?: "")
            q.lowToman?.let { item.put("low", it) }
            q.highToman?.let { item.put("high", it) }
            q.changePct?.let { item.put("dayChange", it.multiply(BigDecimal.valueOf(q.changeSign.toLong()))) }
            root.put(slug, item)
        }
        if (root.length() == 0) throw IllegalStateException("No supported currency quotes found on TGJU")
        return root.toString()
    }

    fun appSlug(key: String): String? {
        val known = mapOf(
            "price_dollar_rl" to "usd", "price_eur" to "eur", "price_gbp" to "gbp",
            "price_aed" to "aed", "price_try" to "try", "price_cad" to "cad",
            "price_aud" to "aud", "price_cny" to "cny", "price_omr" to "omr",
            "price_kwd" to "kwd", "price_sar" to "sar", "price_qar" to "qar",
            "price_bhd" to "bhd", "price_iqd" to "iqd", "price_afn" to "afn",
            "price_rub" to "rub", "price_chf" to "chf", "price_jpy" to "jpy",
            "price_inr" to "inr", "price_pkr" to "pkr", "price_myr" to "myr",
            "price_thb" to "thb", "price_hkd" to "hkd", "price_sgd" to "sgd",
            "price_nzd" to "nzd", "price_sek" to "sek", "price_nok" to "nok",
            "price_dkk" to "dkk", "price_azn" to "azn", "price_amd" to "amd",
            "price_gel" to "gel", "price_tjs" to "tjs", "price_tmt" to "tmt",
            "price_krw" to "krw", "price_syp" to "syp",
        )
        known[key]?.let { return it }
        if (!key.startsWith("price_")) return null
        val code = key.removePrefix("price_").removeSuffix("_rl").lowercase(Locale.ROOT)
        return when (code) {
            "dollar" -> "usd"
            "kuwait" -> "kwd"
            "saudi" -> "sar"
            "oman" -> "omr"
            "qatar" -> "qar"
            "bahrain" -> "bhd"
            else -> code.takeIf { it.matches(Regex("[a-z]{3}")) }
        }
    }

    private fun attributes(source: String): Map<String, String> {
        val out = HashMap<String, String>()
        val matcher = attrPattern.matcher(source)
        while (matcher.find()) out[matcher.group(1).lowercase(Locale.ROOT)] = matcher.group(2)
        return out
    }

    private fun clean(source: String): String {
        var value = tagPattern.matcher(source).replaceAll(" ")
        value = value.replace("&nbsp;", " ").replace("&#160;", " ")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'")
            .replace("\u200c", "").replace("\u200f", "").replace("\u200e", "")
        return spacePattern.matcher(value).replaceAll(" ").trim()
    }

    private fun number(source: String?): BigDecimal? {
        if (source.isNullOrBlank()) return null
        val normalized = buildString {
            source.forEach { c ->
                when (c) {
                    in '۰'..'۹' -> append('0' + (c - '۰'))
                    in '٠'..'٩' -> append('0' + (c - '٠'))
                    '٬', ',' -> {}
                    '٫' -> append('.')
                    else -> if (c.isDigit() || c == '.') append(c)
                }
            }
        }
        return normalized.toBigDecimalOrNull()
    }
}
