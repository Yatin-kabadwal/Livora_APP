package com.livora.corbett.util

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Formatting helpers: INR with Indian digit grouping, IST dates. */
object Fmt {
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    private val indiaLocale: Locale = Locale.Builder().setLanguage("en").setRegion("IN").build()

    fun money(v: Double?): String {
        val nf = NumberFormat.getNumberInstance(indiaLocale)
        nf.maximumFractionDigits = 0
        val n = Math.round(v ?: 0.0)
        return "₹" + nf.format(n)
    }

    fun moneyInt(v: Int): String = money(v.toDouble())

    fun today(): LocalDate = LocalDate.now(IST)

    /** Parses "YYYY-MM-DD" or an ISO instant, resolving to the calendar day in IST. */
    fun parseDay(s: String?): LocalDate? {
        if (s.isNullOrBlank()) return null
        return try {
            if (s.length == 10) LocalDate.parse(s) else Instant.parse(s).atZone(IST).toLocalDate()
        } catch (e: Exception) {
            try {
                LocalDate.parse(s.take(10))
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun fmt(pattern: String) = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
    private val fShort = fmt("EEE, d MMM")
    private val fMed = fmt("d MMM yyyy")
    private val fDayMonth = fmt("d MMM")
    private val fMonthYear = fmt("MMMM yyyy")
    private val fTime = fmt("h:mm a")
    private val fDateTime = fmt("d MMM, h:mm a")

    fun dayShort(d: LocalDate?): String = d?.format(fShort) ?: "-"
    fun dayMedium(d: LocalDate?): String = d?.format(fMed) ?: "-"
    fun dayMonth(d: LocalDate?): String = d?.format(fDayMonth) ?: "-"
    fun monthYear(d: LocalDate): String = d.format(fMonthYear)
    fun dayShort(s: String?): String = dayShort(parseDay(s))
    fun dayMedium(s: String?): String = dayMedium(parseDay(s))
    fun iso(d: LocalDate): String = d.toString()

    fun nights(a: LocalDate?, b: LocalDate?): Int =
        if (a == null || b == null) 0 else ChronoUnit.DAYS.between(a, b).toInt().coerceAtLeast(0)

    private fun instant(s: String?): Instant? = try {
        if (s.isNullOrBlank()) null else Instant.parse(s)
    } catch (e: Exception) {
        null
    }

    fun time(s: String?): String = instant(s)?.atZone(IST)?.format(fTime) ?: ""
    fun dateTime(s: String?): String = instant(s)?.atZone(IST)?.format(fDateTime) ?: ""

    /** "3:45 PM" for today, "12 Sep" otherwise. */
    fun smartTime(s: String?): String {
        val i = instant(s) ?: return ""
        val z = i.atZone(IST)
        return if (z.toLocalDate() == today()) z.format(fTime) else z.format(fDayMonth)
    }

    fun titleCase(s: String): String =
        s.replace('_', ' ').split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    fun plural(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}"

    fun initials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }
}
