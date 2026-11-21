package com.example.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DhakaTime {
    val timeZone: TimeZone = TimeZone.getTimeZone("Asia/Dhaka")

    private fun newCalendar(millis: Long = System.currentTimeMillis()): Calendar =
        Calendar.getInstance(timeZone, Locale.US).apply { timeInMillis = millis }

    private fun dateKeyFormatter(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = this@DhakaTime.timeZone
            isLenient = false
        }

    private fun monthKeyFormatter(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM", Locale.US).apply {
            timeZone = this@DhakaTime.timeZone
            isLenient = false
        }

    fun todayDateKey(nowMillis: Long = System.currentTimeMillis()): String =
        dateKeyFormatter().format(Date(nowMillis))

    fun currentMonthKey(nowMillis: Long = System.currentTimeMillis()): String =
        monthKeyFormatter().format(Date(nowMillis))

    fun currentYear(nowMillis: Long = System.currentTimeMillis()): Int =
        newCalendar(nowMillis).get(Calendar.YEAR)

    fun dateKeyFromMillis(millis: Long): String =
        dateKeyFormatter().format(Date(millis))

    fun monthKeyFromDateKey(dateKey: String): String =
        dateKey.take(7)

    fun parseDateKeyStartMillis(dateKey: String): Long {
        val parsed = dateKeyFormatter().parse(dateKey.trim())
            ?: throw IllegalArgumentException("Invalid date format (use yyyy-MM-dd): $dateKey")
        return newCalendar(parsed.time).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun datesBetweenKeys(startDateKey: String, endDateKey: String): List<String> {
        val startCal = newCalendar(parseDateKeyStartMillis(startDateKey))
        val endMillis = parseDateKeyStartMillis(endDateKey)
        require(endMillis >= startCal.timeInMillis) { "End date cannot be earlier than start date" }
        val fmt = dateKeyFormatter()
        val result = mutableListOf<String>()
        var guard = 0
        while (startCal.timeInMillis <= endMillis && guard < 366) {
            result.add(fmt.format(startCal.time))
            startCal.add(Calendar.DAY_OF_MONTH, 1)
            guard++
        }
        return result
    }

    fun daysInMonth(monthKey: String): List<String> {
        val parsed = monthKeyFormatter().parse(monthKey.trim())
            ?: throw IllegalArgumentException("Invalid month format (use yyyy-MM): $monthKey")
        val cal = newCalendar(parsed.time).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val fmt = dateKeyFormatter()
        return (1..maxDay).map { day ->
            cal.set(Calendar.DAY_OF_MONTH, day)
            fmt.format(cal.time)
        }
    }

    fun shiftMonthKey(monthKey: String, deltaMonths: Int): String {
        val parsed = monthKeyFormatter().parse(monthKey.trim())
            ?: throw IllegalArgumentException("Invalid month format: $monthKey")
        val cal = newCalendar(parsed.time).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, deltaMonths)
        }
        return monthKeyFormatter().format(cal.time)
    }

    fun startOfDayMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        newCalendar(nowMillis).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun startOfMonthMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        newCalendar(nowMillis).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}

object Formatters {
    private val dateFormat: SimpleDateFormat
        get() = SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
            timeZone = DhakaTime.timeZone
        }
    private val dateTimeFormat: SimpleDateFormat
        get() = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).apply {
            timeZone = DhakaTime.timeZone
        }

    fun roundMoney(value: Double): Double =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

    fun roundQty(value: Double): Double =
        BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).toDouble()

    fun formatTaka(amount: Double, symbol: String = "৳"): String {
        val formatter = NumberFormat.getNumberInstance(Locale("en", "BD")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "$symbol ${formatter.format(roundMoney(amount))}"
    }

    fun formatQty(qty: Double, unit: String = ""): String {
        val formatted = if (qty % 1.0 == 0.0) {
            qty.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", qty)
        }
        return if (unit.isBlank()) formatted else "$formatted $unit"
    }

    fun formatDate(epochMillis: Long): String =
        dateFormat.format(Date(epochMillis))

    fun formatDateTime(epochMillis: Long): String =
        dateTimeFormat.format(Date(epochMillis))
}
