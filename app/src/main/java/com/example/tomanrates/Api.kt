package com.example.tomanrates

import org.json.JSONObject
import java.math.BigDecimal
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

data class Rate(
    val slug: String,
    val name: String,
    val sell: BigDecimal,
    val buy: BigDecimal?,
    val open: BigDecimal? = null,
    val high: BigDecimal? = null,
    val low: BigDecimal? = null,
    val dayChange: BigDecimal? = null,
    val sourceUpdatedAt: String?,
)

sealed class FetchResult {
    data class Ok(val raw: String) : FetchResult()
    data class Fail(val reason: String, val reachedServer: Boolean) : FetchResult()
}

/** Public TGJU market-page source. No API token or private endpoint is used. */
object Api {
    fun fetch(token: String = ""): FetchResult = try {
        val html = TgjuMarket.fetchHtml()
        val rows = TgjuMarket.parseRows(html)
        FetchResult.Ok(TgjuMarket.ratesJson(rows))
    } catch (e: UnknownHostException) {
        FetchResult.Fail("No network connection", false)
    } catch (e: ConnectException) {
        FetchResult.Fail("Could not connect to market source", true)
    } catch (e: NoRouteToHostException) {
        FetchResult.Fail("No network connection", false)
    } catch (e: java.io.IOException) {
        FetchResult.Fail("Market source network error", true)
    } catch (e: Exception) {
        FetchResult.Fail(e.message ?: "Market source unavailable or page format changed", true)
    }

    fun parse(raw: String): LinkedHashMap<String, Rate> {
        val out = LinkedHashMap<String, Rate>()
        val root = try { JSONObject(raw) } catch (_: Exception) { return out }
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val o = root.optJSONObject(key) ?: continue
            val sell = o.opt("sell")?.toString()?.toBigDecimalOrNull() ?: continue
            if (sell.signum() <= 0) continue
            val slug = key.lowercase()
            out[slug] = Rate(
                slug = slug,
                name = o.optString("name", slug.uppercase()),
                sell = sell,
                buy = o.opt("buy")?.toString()?.toBigDecimalOrNull(),
                open = o.opt("open")?.toString()?.toBigDecimalOrNull(),
                high = o.opt("high")?.toString()?.toBigDecimalOrNull(),
                low = o.opt("low")?.toString()?.toBigDecimalOrNull(),
                dayChange = o.opt("dayChange")?.toString()?.toBigDecimalOrNull(),
                sourceUpdatedAt = o.optString("updated_at", "").ifBlank { null },
            )
        }
        return out
    }
}
