package com.example.tomanrates

import org.json.JSONObject
import java.math.BigDecimal
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

/**
 * One entry of the Alanchand "currencies" response.
 * Observed shape (top-level object keyed by slug):
 *   "omr": {"slug":"omr","name":"ریال عمان","sell":714500,"buy":700200,"dolar_rate":0.385,
 *           "open":..,"high":..,"low":..,"dayChange":0.88,"updated_at":"2026-11-03 16:20:06"}
 * `sell` is the free-market selling price in Tomans per `unit` units (see Catalog.unitOf).
 */
data class Rate(
    val slug: String,
    val name: String,            // Persian name as sent by the API
    val sell: BigDecimal,        // Tomans
    val buy: BigDecimal?,
    val sourceUpdatedAt: String?, // as reported by the API, timezone not documented
)

sealed class FetchResult {
    data class Ok(val raw: String) : FetchResult()
    /** reachedServer=false means the request never left the phone (no network), so it must not use up the hourly budget. */
    data class Fail(val reason: String, val reachedServer: Boolean) : FetchResult()
}

object Api {
    private const val BASE = "https://api.alanchand.com/"

    /** Never put the URL or exception messages into results: they can contain the token. */
    fun fetch(token: String): FetchResult {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(BASE + "?type=currencies&token=" + URLEncoder.encode(token, "UTF-8"))
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code != 200) {
                val msg = if (code == 429) "Rate limit reached (HTTP 429)" else "HTTP $code"
                FetchResult.Fail(msg, true)
            } else {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                if (parse(body).isEmpty()) FetchResult.Fail("Unexpected response format", true)
                else FetchResult.Ok(body)
            }
        } catch (e: UnknownHostException) {
            FetchResult.Fail("No network connection", false)
        } catch (e: ConnectException) {
            FetchResult.Fail("No network connection", false)
        } catch (e: NoRouteToHostException) {
            FetchResult.Fail("No network connection", false)
        } catch (e: java.io.IOException) {
            FetchResult.Fail("Network error", true)
        } catch (e: Exception) {
            FetchResult.Fail("Unexpected error", true)
        } finally {
            conn?.disconnect()
        }
    }

    /** Lenient parser: skips anything without a positive numeric `sell`. Order of the response is kept. */
    fun parse(raw: String): LinkedHashMap<String, Rate> {
        val out = LinkedHashMap<String, Rate>()
        val root = try { JSONObject(raw) } catch (e: Exception) { return out }
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
                sourceUpdatedAt = o.optString("updated_at", "").ifBlank { null },
            )
        }
        return out
    }
}
