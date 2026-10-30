package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Investment
import com.example.domain.model.Money
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

/**
 * Production Net Worth & Portfolio Screen matching PDF Page 16:
 * - Header: 📈 "Net Worth & Portfolio", Close button ✕
 * - Summary Card: Total Net Worth ₹6,03,000, Total Assets ₹6,71,000, Liabilities (EMI/Cards) ₹68,000
 * - Investments Tracker (+ Add Asset button): Stocks, Mutual Funds, SIPs & Crypto
 * - Total Return: +₹1,03,000 (26.41%) Profitable badge
 * - Items: Nifty 50 Index Fund (MUTUAL_FUND) ₹1,56,000 (+₹36,000), Tata Motors & Bluechips (STOCK) ₹1,24,000 (+₹29,000)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val profileState by viewModel.userProfile.collectAsStateWithLifecycle()
    val investmentsState by viewModel.investments.collectAsStateWithLifecycle()
    val spentThisMonthState by viewModel.spentThisMonth.collectAsStateWithLifecycle()

    val colorScheme = MaterialTheme.colorScheme
    var showAddInvestmentDialog by remember { mutableStateOf(false) }

    val currentInvestments = investmentsState

    val totalInvested = currentInvestments.sumOf { it.investedAmount }
    val totalCurrent = currentInvestments.sumOf { it.currentValue }
    val totalReturn = totalCurrent - totalInvested
    val returnPct = if (totalInvested > 0) (totalReturn / totalInvested) * 100 else 0.0

    val baseAssets = (profileState?.netWorth ?: 0.0) + totalCurrent
    val baseLiabilities = spentThisMonthState
    val totalNetWorthRupees = maxOf(0.0, baseAssets - baseLiabilities)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================= HEADER (PDF Page 16) =================
            item(key = "net_worth_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(SageLimeContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📈", fontSize = 18.sp)
                        }
                        Text(
                            text = "Net Worth & Portfolio",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ================= TOTAL NET WORTH CARD (PDF Page 16) =================
            item(key = "total_net_worth_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Total Net Worth",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = Money.fromRupees(totalNetWorthRupees).format(),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            color = SageLimePrimary
                        )

                        HorizontalDivider(color = DarkCardBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Total Assets", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                                Text(
                                    text = Money.fromRupees(baseAssets).format(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SageLimePrimary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Liabilities (EMI/Cards)", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                                Text(
                                    text = Money.fromRupees(baseLiabilities).format(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF6B6B)
                                )
                            }
                        }
                    }
                }
            }

            // ================= INVESTMENTS TRACKER (PDF Page 16) =================
            item(key = "investments_tracker_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Investments Tracker",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Stocks, Mutual Funds, SIPs & Crypto",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { showAddInvestmentDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SageLimePrimary,
                            contentColor = SageLimeOnPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("+ Add Asset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Total Return Banner Card
            item(key = "total_return_banner") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Return", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                            Text(
                                text = if (totalInvested > 0) "+${Money.fromRupees(totalReturn).format()} (${String.format("%.2f", returnPct)}%)" else "${Money.fromRupees(0.0).format()} (0.00%)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (totalReturn >= 0) SageLimePrimary else Color(0xFFFF6B6B)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(SageLimeContainer)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (totalInvested > 0) "↗" else "🌱", color = SageLimePrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(if (totalInvested > 0) "Profitable" else "Day 1 Ready", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SageLimeOnContainer)
                            }
                        }
                    }
                }
            }

            // Investment Items List or Clean Empty State
            if (currentInvestments.isEmpty()) {
                item(key = "empty_investments_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("🌱", fontSize = 36.sp)
                            Text(
                                text = "No investments logged yet",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Tap '+ Add Asset' above to track Stocks, Mutual Funds, SIPs, or Crypto holdings.",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(currentInvestments, key = { it.id }) { inv ->
                val profit = inv.currentValue - inv.investedAmount
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = inv.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkCardSurfaceElevated)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = inv.type,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SageLimePrimary
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = Money.fromRupees(inv.currentValue).format(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "+${Money.fromRupees(profit).format()}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SageLimePrimary
                            )
                        }
                    }
                }
            }
        }
    }

        // Add Asset Dialog
        if (showAddInvestmentDialog) {
            var assetName by remember { mutableStateOf("") }
            var assetType by remember { mutableStateOf("STOCK") }
            var investedStr by remember { mutableStateOf("") }
            var currentStr by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showAddInvestmentDialog = false },
                title = { Text("Add Investment Asset", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = assetName,
                            onValueChange = { assetName = it },
                            label = { Text("Asset Name (e.g. Nifty ETF)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = assetType,
                            onValueChange = { assetType = it },
                            label = { Text("Type (STOCK, MUTUAL_FUND, CRYPTO)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = investedStr,
                            onValueChange = { investedStr = it },
                            label = { Text("Invested Amount (₹)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = currentStr,
                            onValueChange = { currentStr = it },
                            label = { Text("Current Value (₹)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val inv = investedStr.toDoubleOrNull() ?: 10000.0
                            val cur = currentStr.toDoubleOrNull() ?: 12000.0
                            viewModel.addInvestment(
                                name = assetName.ifBlank { "New Asset" },
                                type = assetType,
                                invested = inv,
                                currentVal = cur
                            )
                            showAddInvestmentDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SageLimePrimary,
                            contentColor = SageLimeOnPrimary
                        )
                    ) {
                        Text("Add Asset")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddInvestmentDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
