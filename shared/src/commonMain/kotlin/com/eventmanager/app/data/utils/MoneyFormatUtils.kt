package com.eventmanager.app.data.utils

import kotlin.math.abs
import kotlin.math.round

fun formatMoney(amount: Double, currencyCode: String = "CHF"): String {
    val rounded = round(amount * 100.0) / 100.0
    val sign = if (rounded < 0) "-" else ""
    val absolute = abs(rounded)
    val whole = absolute.toLong()
    val fraction = round((absolute - whole) * 100.0).toInt().coerceIn(0, 99)
    val fractionText = fraction.toString().padStart(2, '0')
    val number = "$whole.$fractionText"
    return when (currencyCode.uppercase()) {
        "CHF" -> "CHF $sign$number"
        "EUR" -> "€$sign$number"
        "USD" -> "$$sign$number"
        else -> "$currencyCode $sign$number"
    }
}
