package com.example.tomanrates

import android.content.Context
import java.math.BigDecimal

/** All persisted state. Private app storage, backups disabled in the manifest. */
object Store {
    private val DEFAULT_SELECTION = listOf("omr", "usd", "eur", "aed")
    private val SLUG = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("toman_rates", Context.MODE_PRIVATE)

    // ---- token ----
    fun token(ctx: Context): String = p(ctx).getString("token", "") ?: ""
    fun setToken(ctx: Context, t: String) = p(ctx).edit().putString("token", t.trim()).apply()
    fun clearLegacyToken(ctx: Context) = p(ctx).edit().remove("token").apply()

    // ---- update preferences
    fun autoRefreshEnabled(ctx: Context): Boolean = p(ctx).getBoolean("auto_refresh_enabled", true)
    fun setAutoRefreshEnabled(ctx: Context, enabled: Boolean) =
        p(ctx).edit().putBoolean("auto_refresh_enabled", enabled).apply()

    fun backgroundRefreshEnabled(ctx: Context): Boolean = p(ctx).getBoolean("background_refresh_enabled", true)
    fun setBackgroundRefreshEnabled(ctx: Context, enabled: Boolean) =
        p(ctx).edit().putBoolean("background_refresh_enabled", enabled).apply()

    fun darkThemeEnabled(ctx: Context): Boolean = p(ctx).getBoolean("dark_theme_enabled", true)
    fun setDarkThemeEnabled(ctx: Context, enabled: Boolean) =
        p(ctx).edit().putBoolean("dark_theme_enabled", enabled).apply()

    fun appLanguage(ctx: Context): String = p(ctx).getString("app_language", "en") ?: "en"
    fun setAppLanguage(ctx: Context, language: String) =
        p(ctx).edit().putString("app_language", language).apply()

    // ---- selection / order / default
    fun selected(ctx: Context): List<String> {
        val s = p(ctx).getString("selected", null)?.split(",")?.filter { it.isNotBlank() }
        return if (s.isNullOrEmpty()) DEFAULT_SELECTION else s
    }

    private fun saveSelected(ctx: Context, list: List<String>) =
        p(ctx).edit().putString("selected", list.joinToString(",")).apply()

    fun defaultSlug(ctx: Context): String {
        val sel = selected(ctx)
        val d = p(ctx).getString("default", null)
        return if (d != null && d in sel) d else sel.first()
    }

    fun setDefault(ctx: Context, slug: String) {
        val sel = selected(ctx)
        if (slug !in sel) saveSelected(ctx, listOf(slug) + sel)
        p(ctx).edit().putString("default", slug).apply()
    }

    /** Returns an error message, or null on success. Any well-formed slug is accepted; availability is shown per row. */
    fun addSymbol(ctx: Context, input: String): String? {
        val slug = input.trim().lowercase()
        if (!SLUG.matches(slug)) return "Use the API symbol, e.g. usd, omr or usd-hav"
        val sel = selected(ctx)
        if (slug in sel) return "Already in your list"
        saveSelected(ctx, sel + slug)
        return null
    }

    fun remove(ctx: Context, slug: String): String? {
        val sel = selected(ctx)
        if (sel.size <= 1) return "Keep at least one currency"
        val wasDefault = defaultSlug(ctx) == slug
        val next = sel - slug
        saveSelected(ctx, next)
        if (wasDefault) p(ctx).edit().putString("default", next.first()).apply()
        return null
    }

    fun move(ctx: Context, slug: String, delta: Int) {
        val l = selected(ctx).toMutableList()
        val i = l.indexOf(slug)
        val j = i + delta
        if (i < 0 || j !in l.indices) return
        l[i] = l[j].also { l[j] = l[i] }
        saveSelected(ctx, l)
    }

    // ---- cache of the last successful response + attempt bookkeeping ----
    fun cachedRaw(ctx: Context): String = p(ctx).getString("raw", "") ?: ""
    fun lastSuccessAt(ctx: Context): Long = p(ctx).getLong("last_success", 0L)
    fun lastAttemptAt(ctx: Context): Long = p(ctx).getLong("last_attempt", 0L)
    fun lastAttemptOk(ctx: Context): Boolean = p(ctx).getBoolean("last_ok", false)
    fun lastError(ctx: Context): String = p(ctx).getString("last_error", "") ?: ""

    fun saveSuccess(ctx: Context, raw: String, now: Long) {
        p(ctx).edit()
            .putString("raw", raw).putLong("last_success", now).putLong("last_attempt", now)
            .putBoolean("last_ok", true).putString("last_error", "").apply()
        PriceHistory.append(ctx, Api.parse(raw), now)
    }

    fun priceHistory(ctx: Context, slug: String, rangeMs: Long): List<PricePoint> =
        PriceHistory.read(ctx, slug, rangeMs)

    fun recordPrice(ctx: Context, slug: String, price: BigDecimal, timestamp: Long = System.currentTimeMillis()) {
        if (price.signum() <= 0) return
        val quote = Rate(slug, slug, price, null, sourceUpdatedAt = null)
        PriceHistory.append(ctx, mapOf(slug to quote), timestamp)
    }

    /** attemptAt is null when the request never reached the server (does not use up the hourly budget). */
    fun saveFailure(ctx: Context, reason: String, attemptAt: Long?) {
        val e = p(ctx).edit().putBoolean("last_ok", false).putString("last_error", reason)
        if (attemptAt != null) e.putLong("last_attempt", attemptAt)
        e.apply()
    }
}


/** Locally collected, real quote history. Samples are captured only after successful API responses. */
data class PricePoint(val timestamp: Long, val price: Double)

private object PriceHistory {
    private const val KEY = "price_history_v1"
    private const val RETENTION_MS = 31L * 24 * 60 * 60 * 1000
    private const val MAX_SAMPLES = 800

    @Synchronized
    fun append(ctx: Context, rates: Map<String, Rate>, timestamp: Long) {
        if (rates.isEmpty()) return
        val prefs = ctx.applicationContext.getSharedPreferences("toman_rates", Context.MODE_PRIVATE)
        val samples = try { org.json.JSONArray(prefs.getString(KEY, "[]") ?: "[]") }
            catch (_: Exception) { org.json.JSONArray() }
        val cutoff = timestamp - RETENTION_MS
        val kept = org.json.JSONArray()
        for (i in 0 until samples.length()) {
            val item = samples.optJSONObject(i) ?: continue
            if (item.optLong("t") >= cutoff) kept.put(item)
        }
        val values = org.json.JSONObject()
        rates.forEach { (slug, rate) ->
            values.put(slug, rate.sell.toDouble())
        }
        kept.put(org.json.JSONObject().put("t", timestamp).put("v", values))
        val trimmed = org.json.JSONArray()
        val start = (kept.length() - MAX_SAMPLES).coerceAtLeast(0)
        for (i in start until kept.length()) trimmed.put(kept.getJSONObject(i))
        prefs.edit().putString(KEY, trimmed.toString()).apply()
    }

    @Synchronized
    fun read(ctx: Context, slug: String, rangeMs: Long): List<PricePoint> {
        val prefs = ctx.applicationContext.getSharedPreferences("toman_rates", Context.MODE_PRIVATE)
        val samples = try { org.json.JSONArray(prefs.getString(KEY, "[]") ?: "[]") }
            catch (_: Exception) { return emptyList() }
        val cutoff = System.currentTimeMillis() - rangeMs
        val out = ArrayList<PricePoint>()
        for (i in 0 until samples.length()) {
            val item = samples.optJSONObject(i) ?: continue
            val time = item.optLong("t")
            val value = item.optJSONObject("v")?.optDouble(slug, Double.NaN) ?: Double.NaN
            if (time >= cutoff && value.isFinite() && value > 0.0) out.add(PricePoint(time, value))
        }
        return out
    }
}
