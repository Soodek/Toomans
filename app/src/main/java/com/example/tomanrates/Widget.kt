package com.example.tomanrates

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews

class RatesWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        // Renders from the cache only: no network here, so system-triggered updates can't touch the rate limit.
        for (id in ids) WidgetRenderer.update(context, mgr, id)
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        WidgetRenderer.update(context, mgr, id)
    }

    override fun onEnabled(context: Context) {
        Scheduler.ensure(context)
        Scheduler.refreshSoon(context)
    }
}

object WidgetRenderer {
    private val ROWS = arrayOf(
        intArrayOf(R.id.row1, R.id.row1_flag, R.id.row1_name, R.id.row1_rate),
        intArrayOf(R.id.row2, R.id.row2_flag, R.id.row2_name, R.id.row2_rate),
        intArrayOf(R.id.row3, R.id.row3_flag, R.id.row3_name, R.id.row3_rate),
        intArrayOf(R.id.row4, R.id.row4_flag, R.id.row4_name, R.id.row4_rate),
    )
    private const val GREEN = "#4ADE80"
    private const val AMBER = "#FBBF24"
    private const val RED = "#F87171"

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, RatesWidgetProvider::class.java))
        for (id in ids) update(ctx, mgr, id)
    }

    fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
        mgr.updateAppWidget(id, build(ctx, mgr.getAppWidgetOptions(id)))
    }

    private fun openApp(ctx: Context, requestCode: Int, slug: String?): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (slug != null) i.putExtra(MainActivity.EXTRA_SLUG, slug)
        return PendingIntent.getActivity(ctx, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun build(ctx: Context, options: Bundle?): RemoteViews {
        val snap = RateRepository.snapshot(ctx)
        val selected = Store.widgetSelected(ctx)
        val heroSlug = Store.widgetDefaultSlug(ctx)
        val others = selected.filter { it != heroSlug }
        val minH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
        val maxRows = if (minH <= 0) 2 else ((minH - 115) / 28).coerceIn(0, 4)

        // Prices are only shown while the data is recent. Otherwise the widget says so instead of showing old numbers.
        val showPrices = snap.status == Status.LIVE || snap.status == Status.DELAYED
        fun priceOf(slug: String): String =
            if (!showPrices) "—" else snap.rates[slug]?.let { Fmt.toman(it.sell) } ?: "—"
        fun unitOfSlug(slug: String) = snap.rates[slug]?.let { Catalog.unitOf(it.name) } ?: 1

        val rv = RemoteViews(ctx.packageName, R.layout.widget_main)

        // status line
        val last = if (snap.lastSuccessAt > 0) Fmt.time(snap.lastSuccessAt) else null
        val (statusText, statusColor) = when (snap.status) {
            Status.LIVE -> "● Live · $last" to GREEN
            Status.DELAYED -> "● Update failed · last $last" to AMBER
            Status.UNAVAILABLE -> "● Source unavailable · last update $last" to RED
            Status.NEVER -> "● Source unavailable" to RED
            Status.NO_TOKEN -> "● API token needed" to AMBER
        }
        rv.setTextViewText(R.id.status, statusText)
        rv.setTextColor(R.id.status, Color.parseColor(statusColor))

        // hero = default currency
        val heroRate = snap.rates[heroSlug]
        val heroUnit = unitOfSlug(heroSlug)
        rv.setTextViewText(R.id.hero_flag, Catalog.flag(heroSlug))
        rv.setTextViewText(
            R.id.hero_label,
            (if (heroUnit > 1) "$heroUnit " else "") + Catalog.englishName(heroSlug, heroRate?.name).uppercase()
        )
        rv.setTextViewText(R.id.hero_rate, priceOf(heroSlug))
        rv.setTextViewText(
            R.id.hero_unit,
            when {
                showPrices && heroRate != null -> "TOMAN"
                showPrices -> "Not in API response"
                snap.status == Status.NO_TOKEN -> "Add token in the app"
                else -> "No live rate"
            }
        )

        // extra rows, as many as fit
        val shown = minOf(maxRows, others.size, ROWS.size)
        rv.setViewVisibility(R.id.divider, if (shown > 0) View.VISIBLE else View.GONE)
        for (i in ROWS.indices) {
            val (row, flag, name, rate) = ROWS[i].let { listOf(it[0], it[1], it[2], it[3]) }
            if (i < shown) {
                val slug = others[i]
                rv.setViewVisibility(row, View.VISIBLE)
                rv.setTextViewText(flag, Catalog.flag(slug))
                rv.setTextViewText(name, Catalog.unitLabel(slug, unitOfSlug(slug)))
                rv.setTextViewText(rate, priceOf(slug))
                rv.setOnClickPendingIntent(row, openApp(ctx, i + 1, slug))
            } else {
                rv.setViewVisibility(row, View.GONE)
            }
        }

        rv.setOnClickPendingIntent(R.id.widget_root, openApp(ctx, 0, heroSlug))
        return rv
    }
}
