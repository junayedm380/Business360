package com.example.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)
    private val dateTimeFormat = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US)

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
