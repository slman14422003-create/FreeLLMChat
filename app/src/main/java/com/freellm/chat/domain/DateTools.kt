package com.freellm.chat.domain

import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object DateTools {
    private val hijriMonths = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )
    private val arabic = Locale("ar")

    private fun parse(raw: String, today: LocalDate): LocalDate? {
        val s = raw.trim()
        if (s.isEmpty() || s.equals("today", true) || s.equals("now", true)) return today
        return try {
            LocalDate.parse(s)
        } catch (e: DateTimeException) {
            null
        }
    }

    private fun day(d: LocalDate): String =
        d.toString() + " (" + d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " / " +
            d.dayOfWeek.getDisplayName(TextStyle.FULL, arabic) + ")"

    fun hijri(d: LocalDate): String {
        val h = HijrahDate.from(d)
        val y = h.get(java.time.temporal.ChronoField.YEAR)
        val m = h.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
        val dd = h.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
        return String.format(Locale.ENGLISH, "%04d-%02d-%02d AH (%d %s %d)", y, m, dd, dd, hijriMonths[m - 1], y)
    }

    fun run(call: ToolCall, clock: Clock = Clock.systemDefaultZone()): String {
        val zone = try {
            call.str("zone").takeIf { it.isNotEmpty() }?.let { ZoneId.of(it) } ?: clock.zone
        } catch (e: DateTimeException) {
            return "Unknown time zone. Use names like Asia/Riyadh or UTC."
        }
        val now = ZonedDateTime.now(clock.withZone(zone))
        val today = now.toLocalDate()
        return when (call.str("op").lowercase().ifEmpty { "now" }) {
            "now" -> "Now: " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " " + zone.id + "\n" +
                "Date: " + day(today) + "\nHijri: " + hijri(today) + "\nISO: " + now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) +
                "\nUnix: " + now.toEpochSecond()
            "add" -> {
                val base = parse(call.str("date"), today) ?: return "Invalid date. Use YYYY-MM-DD."
                val r = base.plusYears((call.int("years") ?: 0).toLong())
                    .plusMonths((call.int("months") ?: 0).toLong())
                    .plusDays((call.int("days") ?: 0).toLong())
                "Result: " + day(r) + "\nHijri: " + hijri(r)
            }
            "diff" -> {
                val a = parse(call.str("date"), today) ?: return "Invalid date. Use YYYY-MM-DD."
                val b = parse(call.str("to"), today) ?: return "Invalid 'to' date. Use YYYY-MM-DD."
                val days = ChronoUnit.DAYS.between(a, b)
                val p = Period.between(a, b)
                "From " + a + " to " + b + ": " + days + " days (" + days / 7 + " weeks and " + days % 7 + " days); " +
                    p.years + " years, " + p.months + " months, " + p.days + " days"
            }
            "weekday" -> {
                val d = parse(call.str("date"), today) ?: return "Invalid date. Use YYYY-MM-DD."
                day(d)
            }
            "hijri" -> {
                val h = call.str("hijri")
                if (h.isNotEmpty()) {
                    val parts = h.split('-').mapNotNull { it.trim().toIntOrNull() }
                    if (parts.size != 3) return "Hijri date format is YYYY-MM-DD."
                    try {
                        val g = LocalDate.from(HijrahDate.of(parts[0], parts[1], parts[2]))
                        "Gregorian: " + day(g)
                    } catch (e: DateTimeException) {
                        "Invalid Hijri date."
                    }
                } else {
                    val d = parse(call.str("date"), today) ?: return "Invalid date. Use YYYY-MM-DD."
                    "Hijri: " + hijri(d) + "\nGregorian: " + day(d)
                }
            }
            else -> "Unknown op. Use now, add, diff, weekday or hijri."
        }
    }
}
