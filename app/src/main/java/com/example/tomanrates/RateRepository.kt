package com.example.tomanrates

import android.content.Context

enum class Status { NO_TOKEN, NEVER, LIVE, DELAYED, UNAVAILABLE }

data class Snapshot(
    val status: Status,
    val rates: Map<String, Rate>,
    val lastSuccessAt: Long,
    val lastError: String,
    val sourceTime: String?,
)

object RateRepository {
    /** Alanchand's test tier allows one request per hour per user. Never call more often than this. */
    const val MIN_INTERVAL_MS = 60 * 60 * 1000L

    /** Prices older than this are no longer shown as prices in the widget. */
    const val STALE_AFTER_MS = 3 * 60 * 60 * 1000L

    sealed class Outcome {
        object Updated : Outcome()
        data class Failed(val reason: String) : Outcome()
        data class TooSoon(val nextAllowedAt: Long) : Outcome()
        object NoToken : Outcome()
    }

    /** The only place that talks to the network. Both the hourly worker and the Refresh button go through it. */
    @Synchronized
    fun refresh(ctx: Context): Outcome {
        val token = Store.token(ctx)
        if (token.isBlank()) return Outcome.NoToken
        val now = System.currentTimeMillis()
        val last = Store.lastAttemptAt(ctx)
        if (last > 0 && (now - last) in 0 until MIN_INTERVAL_MS) {
            return Outcome.TooSoon(last + MIN_INTERVAL_MS)
        }
        return when (val r = Api.fetch(token)) {
            is FetchResult.Ok -> {
                Store.saveSuccess(ctx, r.raw, System.currentTimeMillis())
                Outcome.Updated
            }
            is FetchResult.Fail -> {
                Store.saveFailure(ctx, r.reason, if (r.reachedServer) now else null)
                Outcome.Failed(r.reason)
            }
        }
    }

    fun nextAllowedAt(ctx: Context): Long {
        val last = Store.lastAttemptAt(ctx)
        return if (last == 0L) 0L else last + MIN_INTERVAL_MS
    }

    fun snapshot(ctx: Context): Snapshot {
        val now = System.currentTimeMillis()
        val raw = Store.cachedRaw(ctx)
        val rates = if (raw.isBlank()) emptyMap() else Api.parse(raw)
        val ok = Store.lastSuccessAt(ctx)
        val status = when {
            Store.token(ctx).isBlank() -> Status.NO_TOKEN
            ok == 0L || rates.isEmpty() -> Status.NEVER
            now - ok > STALE_AFTER_MS -> Status.UNAVAILABLE
            Store.lastAttemptOk(ctx) -> Status.LIVE
            else -> Status.DELAYED
        }
        return Snapshot(status, rates, ok, Store.lastError(ctx), rates.values.firstOrNull()?.sourceUpdatedAt)
    }
}
