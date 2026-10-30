package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.alerts.AlertSeverity
import com.example.domain.model.coach.Insight
import com.example.ui.theme.SemanticDanger
import com.example.ui.theme.SemanticInfo
import com.example.ui.theme.SemanticWarning

/**
 * High-density Insight Card for the AI Behavioral Coach feed.
 * Features inline feedback (👍/👎), urgency badges, and one-tap action trigger.
 */
@Composable
fun InsightCard(
    headline: String,
    body: String = "",
    severity: AlertSeverity = AlertSeverity.INFO,
    actionText: String? = null,
    impactScore: Float = 0f,
    modifier: Modifier = Modifier,
    onCardClick: () -> Unit = {},
    onFeedback: (isHelpful: Boolean) -> Unit = {}
) {
    val (badgeColor, badgeText) = when (severity) {
        AlertSeverity.CRITICAL -> SemanticDanger to "CRITICAL"
        AlertSeverity.WARNING -> SemanticWarning to "WARNING"
        AlertSeverity.INFO -> SemanticInfo to "TIP"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable { onCardClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Severity Badge + Impact Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (impactScore > 0) {
                    Text(
                        text = "Impact: ${(impactScore * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Headline
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Body
            if (body.isNotBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            // Footer: Action Prompt & Inline Feedback
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!actionText.isNullOrBlank()) {
                    Text(
                        text = "⚡ $actionText",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Inline Feedback Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onFeedback(true) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(text = "👍", fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = { onFeedback(false) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(text = "👎", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun InsightCard(
    insight: Insight,
    modifier: Modifier = Modifier,
    onCardClick: () -> Unit = {},
    onFeedback: (isHelpful: Boolean) -> Unit = {}
) {
    InsightCard(
        headline = insight.message.substringBefore(".").trim(),
        body = insight.message.substringAfter(".", "").trim().ifEmpty { insight.message },
        severity = insight.severity,
        actionText = insight.actionable?.title,
        impactScore = insight.confidence.toFloat(),
        modifier = modifier,
        onCardClick = onCardClick,
        onFeedback = onFeedback
    )
}

@Composable
fun InsightCard(
    message: String,
    modifier: Modifier = Modifier,
    onCardClick: () -> Unit = {},
    onFeedback: (isHelpful: Boolean) -> Unit = {}
) {
    val headline = message.substringBefore(".").trim()
    val body = message.substringAfter(".", "").trim()
    InsightCard(
        headline = if (headline.isNotEmpty()) headline else message,
        body = body,
        severity = if (message.contains("⚠️") || message.contains("Overrun") || message.contains("Alert")) AlertSeverity.WARNING else AlertSeverity.INFO,
        actionText = "Review Details",
        impactScore = 0.85f,
        modifier = modifier,
        onCardClick = onCardClick,
        onFeedback = onFeedback
    )
}
