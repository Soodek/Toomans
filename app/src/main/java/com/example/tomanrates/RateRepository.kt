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
    const val MIN_INTERVAL_MS = 60 * 1000L
    const val STALE_AFTER_MS = 10 * 60 * 1000L

    sealed class Outcome {
        object Updated : Outcome()
        data class Failed(val reason: String) : Outcome()
        data class TooSoon(val nextAllowedAt: Long) : Outcome()
        object NoToken : Outcome()
    }

    @Synchronized
    fun refresh(ctx: Context): Outcome {
        val now = System.currentTimeMillis()
        val last = Store.lastAttemptAt(ctx)
        if (last > 0 && (now - last) in 0 until MIN_INTERVAL_MS) {
            return Outcome.TooSoon(last + MIN_INTERVAL_MS)
        }
        return when (val result = Api.fetch()) {
            is FetchResult.Ok -> {
                val parsed = Api.parse(result.raw)
                if (parsed.isEmpty()) {
                    Store.saveFailure(ctx, "No supported market prices found", now)
                    Outcome.Failed("No supported market prices found")
                } else {
                    Store.saveSuccess(ctx, result.raw, System.currentTimeMillis())
                    Outcome.Updated
                }
            }
            is FetchResult.Fail -> {
                Store.saveFailure(ctx, result.reason, if (result.reachedServer) now else null)
                Outcome.Failed(result.reason)
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
            ok == 0L || rates.isEmpty() -> Status.NEVER
            now - ok > STALE_AFTER_MS -> Status.UNAVAILABLE
            Store.lastAttemptOk(ctx) -> Status.LIVE
            else -> Status.DELAYED
        }
        return Snapshot(status, rates, ok, Store.lastError(ctx), rates.values.firstOrNull()?.sourceUpdatedAt)
    }
}
