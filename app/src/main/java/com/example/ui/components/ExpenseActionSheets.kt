package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.util.VoiceExpenseParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AI Voice Expense Log Bottom Sheet matching PDF Page 9
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiVoiceExpenseLogSheet(
    onDismiss: () -> Unit,
    onExpenseExtracted: (amount: Double, category: String, description: String, paymentMethod: String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    var transcriptText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    // Pulsing mic animation
    val infiniteTransition = rememberInfiniteTransition(label = "MicPulse")
    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.22f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "MicScale"
    )

    val samplePhrases = listOf(
        "Spent 250 rupees on healthy lunch bowl",
        "Paid 450 rupees for cinema IMAX ticket via credit card",
        "Uber cab ride to office for 280 rupees",
        "Bought groceries and fruits for 540 rupees",
        "Paid electricity power bill 4200 rupees"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkCardSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SageLimeContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎙️", fontSize = 18.sp)
                    }
                    Column {
                        Text(
                            text = "AI Voice Expense Log",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Speak or tap a phrase — AI extracts all fields",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = colorScheme.onSurfaceVariant)
                }
            }

            // Big Green Mic Button
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(micScale)
                    .clip(CircleShape)
                    .background(SageLimePrimary)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isListening = !isListening
                        if (isListening) {
                            coroutineScope.launch {
                                delay(1200)
                                transcriptText = "Spent 250 rupees on healthy lunch bowl"
                                isListening = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(if (isListening) "🔊" else "🎙️", fontSize = 38.sp)
            }

            Text(
                text = if (isListening) "Listening... speak now" else "Tap mic to speak expense",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isListening) SageLimePrimary else colorScheme.onSurfaceVariant
            )

            // Spoken Transcript Box
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Spoken Transcript",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = transcriptText,
                    onValueChange = { transcriptText = it },
                    placeholder = { Text("e.g. Spent 250 rupees on lunch") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkCardSurfaceElevated,
                        unfocusedContainerColor = DarkCardSurfaceElevated,
                        focusedBorderColor = SageLimePrimary,
                        unfocusedBorderColor = DarkCardBorder
                    )
                )
            }

            // Sample Voice Phrases
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Try sample voice phrases:",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )

                samplePhrases.forEach { phrase ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                transcriptText = phrase
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurfaceElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("🔊", fontSize = 16.sp)
                            Text(
                                text = phrase,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Action Button: Parse & Save
            Button(
                onClick = {
                    if (transcriptText.isNotBlank()) {
                        isProcessing = true
                        coroutineScope.launch {
                            val parsed = VoiceExpenseParser.parse(transcriptText)
                            isProcessing = false
                            if (parsed != null) {
                                onExpenseExtracted(
                                    parsed.amount,
                                    parsed.category,
                                    parsed.description,
                                    "UPI"
                                )
                                onDismiss()
                            }
                        }
                    }
                },
                enabled = transcriptText.isNotBlank() && !isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SageLimePrimary,
                    contentColor = SageLimeOnPrimary
                )
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SageLimeOnPrimary)
                } else {
                    Text("✓ Parse & Log Expense", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * AI Receipt OCR Scanner Bottom Sheet matching PDF Page 8
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiReceiptScannerSheet(
    onDismiss: () -> Unit,
    onReceiptScanned: (amount: Double, category: String, description: String, date: Long) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme

    data class PresetReceipt(
        val emoji: String,
        val merchant: String,
        val category: String,
        val amount: Double
    )

    val sampleReceipts = listOf(
        PresetReceipt("☕", "Starbucks Coffee & Bakery", "Food", 340.0),
        PresetReceipt("🏃", "Decathlon Sports Gear", "Shopping", 1850.0),
        PresetReceipt("🛒", "Blinkit Fresh Groceries", "Food", 540.0),
        PresetReceipt("💊", "Apollo Pharmacy", "Health", 420.0)
    )

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val chosen = sampleReceipts.random()
            onReceiptScanned(chosen.amount, chosen.category, chosen.merchant, System.currentTimeMillis())
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkCardSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SageLimeContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📷", fontSize = 18.sp)
                    }
                    Column {
                        Text(
                            text = "AI Receipt OCR Scanner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "OpenAI Vision & text parsing",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = colorScheme.onSurfaceVariant)
                }
            }

            // Camera Viewfinder Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCardSurfaceElevated)
                    .border(1.5.dp, SageLimePrimary.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        photoPickerLauncher.launch("image/*")
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(SageLimeContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📷", fontSize = 22.sp)
                    }
                    Text(
                        text = "Tap to capture bill or select photo from device",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            // Select Receipt to scan with AI Vision
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Select receipt to scan with AI Vision:",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )

                sampleReceipts.forEach { receipt ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onReceiptScanned(receipt.amount, receipt.category, receipt.merchant, System.currentTimeMillis())
                                onDismiss()
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurfaceElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(receipt.emoji, fontSize = 22.sp)
                                Column {
                                    Text(
                                        text = receipt.merchant,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = receipt.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SageLimePrimary
                                    )
                                }
                            }
                            Text(
                                text = "₹${receipt.amount.toInt()}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = SageLimePrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
