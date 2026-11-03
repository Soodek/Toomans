package com.example.tomanrates

import android.content.Context

/** All persisted state. Private app storage, backups disabled in the manifest. */
object Store {
    private val DEFAULT_SELECTION = listOf("omr", "usd", "eur", "aed")
    private val SLUG = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("toman_rates", Context.MODE_PRIVATE)

    // ---- token ----
    fun token(ctx: Context): String = p(ctx).getString("token", "") ?: ""
    fun setToken(ctx: Context, t: String) = p(ctx).edit().putString("token", t.trim()).apply()

    // ---- selection / order / default ----
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

    fun saveSuccess(ctx: Context, raw: String, now: Long) = p(ctx).edit()
        .putString("raw", raw).putLong("last_success", now).putLong("last_attempt", now)
        .putBoolean("last_ok", true).putString("last_error", "").apply()

    /** attemptAt is null when the request never reached the server (does not use up the hourly budget). */
    fun saveFailure(ctx: Context, reason: String, attemptAt: Long?) {
        val e = p(ctx).edit().putBoolean("last_ok", false).putString("last_error", reason)
        if (attemptAt != null) e.putLong("last_attempt", attemptAt)
        e.apply()
    }
}
