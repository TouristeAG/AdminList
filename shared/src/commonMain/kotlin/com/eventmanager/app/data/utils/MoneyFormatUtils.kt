package com.eventmanager.app.data.utils

import kotlin.math.round

fun formatMoney(amount: Double, currencyCode: String = "CHF"): String {
    val rounded = round(amount * 100.0) / 100.0
    val whole = rounded.toLong()
    val fraction = kotlin.math.abs(round((rounded - whole) * 100).toInt())
    val fractionText = fraction.toString().padStart(2, '0')
    return when (currencyCode.uppercase()) {
        "CHF" -> "CHF $whole.$fractionText"
        "EUR" -> "€$whole.$fractionText"
        "USD" -> "$$whole.$fractionText"
        else -> "$currencyCode $whole.$fractionText"
    }
}
