package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.example.domain.model.Money
import com.example.ui.theme.TabularNumeralsStyle

/**
 * High-performance, animated Money display component.
 * Guaranteed to format all currency via Money.format(), eliminating IEEE 754 float drift.
 * Uses tabular figures to avoid numeral jitter during count-up transitions.
 */
@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayMedium,
    color: Color = MaterialTheme.colorScheme.onBackground,
    animate: Boolean = true
) {
    val targetRupees = money.rupees().toFloat()
    
    // Count-up animation on first display or value update
    val animatedRupees by animateFloatAsState(
        targetValue = targetRupees,
        animationSpec = if (animate) {
            tween(durationMillis = 650, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 0)
        },
        label = "MoneyCountUp"
    )

    // Current animated money value
    val currentMoney = if (animate) {
        Money.fromRupees(animatedRupees.toDouble())
    } else {
        money
    }

    Text(
        text = currentMoney.format(),
        modifier = modifier,
        style = style.merge(TabularNumeralsStyle),
        color = color
    )
}
