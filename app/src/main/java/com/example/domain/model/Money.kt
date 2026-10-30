package com.example.domain.model

import kotlin.math.roundToLong

/**
 * Fintech-grade financial precision value class.
 * Stores monetary amounts as integer paise (1 Rupee = 100 Paise) to completely
 * eliminate IEEE 754 floating-point rounding errors without allocation overhead.
 */
@JvmInline
value class Money(val paise: Long) : Comparable<Money> {

    companion object {
        val ZERO = Money(0L)

        fun fromRupees(rupees: Double): Money =
            Money((rupees * 100.0).roundToLong())

        fun fromPaise(paise: Long): Money =
            Money(paise)
    }

    operator fun plus(other: Money): Money = Money(paise + other.paise)

    operator fun minus(other: Money): Money = Money(paise - other.paise)

    operator fun times(factor: Double): Money =
        Money((paise * factor).roundToLong())

    operator fun div(divisor: Long): Money =
        if (divisor == 0L) ZERO else Money(paise / divisor)

    operator fun div(divisor: Double): Money =
        if (divisor == 0.0) ZERO else Money((paise / divisor).roundToLong())

    override fun compareTo(other: Money): Int = paise.compareTo(other.paise)

    fun rupees(): Double = paise / 100.0

    fun format(): String = "₹%,.2f".format(rupees())

    fun formatWithoutDecimals(): String = "₹%,d".format(paise / 100)

    fun coerceAtLeast(minimumValue: Money): Money =
        if (this < minimumValue) minimumValue else this

    fun coerceAtMost(maximumValue: Money): Money =
        if (this > maximumValue) maximumValue else this

    val isPositive: Boolean get() = paise > 0L
    val isZero: Boolean get() = paise == 0L
    val isNegative: Boolean get() = paise < 0L
}
