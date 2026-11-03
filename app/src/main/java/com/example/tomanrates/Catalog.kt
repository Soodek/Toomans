package com.example.tomanrates

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Display helpers only. The list of *available* currencies always comes from the API response;
 * nothing here decides what exists. Unknown slugs fall back to the API's own name and a generic icon.
 */
object Catalog {
    private val english = mapOf(
        "usd" to "US Dollar", "eur" to "Euro", "aed" to "UAE Dirham", "try" to "Turkish Lira",
        "gbp" to "British Pound", "cny" to "Chinese Yuan", "cad" to "Canadian Dollar",
        "aud" to "Australian Dollar", "rub" to "Russian Ruble", "iqd" to "Iraqi Dinar",
        "myr" to "Malaysian Ringgit", "gel" to "Georgian Lari", "azn" to "Azerbaijani Manat",
        "amd" to "Armenian Dram", "thb" to "Thai Baht", "omr" to "Omani Rial",
        "inr" to "Indian Rupee", "pkr" to "Pakistani Rupee", "jpy" to "Japanese Yen",
        "sar" to "Saudi Riyal", "afn" to "Afghan Afghani", "sek" to "Swedish Krona",
        "chf" to "Swiss Franc", "qar" to "Qatari Riyal", "krw" to "South Korean Won",
        "nok" to "Norwegian Krone", "nzd" to "New Zealand Dollar", "sgd" to "Singapore Dollar",
        "hkd" to "Hong Kong Dollar", "kwd" to "Kuwaiti Dinar", "dkk" to "Danish Krone",
        "bhd" to "Bahraini Dinar", "tjs" to "Tajikistani Somoni", "tmt" to "Turkmenistani Manat",
        "kgs" to "Kyrgyzstani Som", "syp" to "Syrian Pound", "brl" to "Brazilian Real",
        "ars" to "Argentine Peso",
    )
    private val variantSuffix = mapOf(
        "hav" to "Transfer", "ist" to "Istanbul", "sulaymaniyah" to "Sulaymaniyah", "herat" to "Herat",
    )

    fun code(slug: String) = slug.uppercase()

    fun englishName(slug: String, apiName: String?): String {
        val fallback = apiName ?: slug.uppercase()
        val base = english[slug.substringBefore('-')] ?: return fallback
        val suffix = slug.substringAfter('-', "")
        if (suffix.isEmpty()) return base
        val s = variantSuffix[suffix] ?: return fallback
        return "$base ($s)"
    }

    /** Flag from the first two letters of the ISO currency code (works for every code seen so far, EUR -> EU). */
    fun flag(slug: String): String {
        val base = slug.substringBefore('-')
        if (base.length < 2 || !base.take(2).all { it in 'a'..'z' }) return "\uD83D\uDCB1"
        return base.take(2).uppercase()
            .map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }
            .joinToString("")
    }

    /**
     * The API has no "unit" field. Five currencies (JPY, IQD, AMD, KRW, SYP) are quoted per 100 units and
     * their Persian name starts with "صد". Checked against the data: sell == usd.sell / dolar_rate in that unit.
     */
    fun unitOf(apiName: String?): Int =
        if (apiName != null && apiName.trimStart().startsWith("\u0635\u062F ")) 100 else 1

    fun unitLabel(slug: String, unit: Int) = if (unit == 1) code(slug) else "$unit ${code(slug)}"
}

object Fmt {
    private val symbols = DecimalFormatSymbols(Locale.US)
    fun toman(v: BigDecimal): String = DecimalFormat("#,##0.##", symbols).format(v)

    fun time(ms: Long): String {
        val now = System.currentTimeMillis()
        val day = SimpleDateFormat("yyyyMMdd", Locale.US)
        val sameDay = day.format(Date(ms)) == day.format(Date(now))
        return SimpleDateFormat(if (sameDay) "HH:mm" else "dd MMM HH:mm", Locale.US).format(Date(ms))
    }
}
