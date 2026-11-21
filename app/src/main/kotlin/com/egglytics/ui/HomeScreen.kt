package com.egglytics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.usecase.ActivePriceResult

@Composable
fun HomeScreen(
    session: SessionState.Authenticated,
    state: SessionUiState,
    homeState: HomeUiState,
    onOpenHarvests: () -> Unit,
    onOpenSales: () -> Unit,
    onSignOut: () -> Unit,
) {
    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.business.name,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "${session.member.name} · ${session.member.role.name}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Inicio: ${OperationalDate.format(session.business.startDate)} · Hoy: ${OperationalDate.format(state.operationalDate)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(onClick = onSignOut) {
                        Text("Salir")
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Inicio",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onOpenHarvests,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Recolección")
                    }
                    Button(
                        onClick = onOpenSales,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Ventas")
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Resumen de hoy",
                    style = MaterialTheme.typography.titleMedium,
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SummaryRow(
                            label = "Huevos aptos recogidos",
                            value = if (homeState.harvestsIncomplete) "Incompleto" else "${homeState.summary.gradedEggs}",
                        )
                        SummaryRow(
                            label = "Huevos descartados",
                            value = if (homeState.harvestsIncomplete) "Incompleto" else "${homeState.summary.discardedEggs}",
                        )
                        SummaryRow(
                            label = "Total vendido",
                            value = if (homeState.salesIncomplete) {
                                "Incompleto"
                            } else {
                                "${homeState.summary.totalSold} ${session.business.currency}"
                            },
                        )
                        SummaryRow(
                            label = "Total cobrado",
                            value = if (homeState.paymentsIncomplete) {
                                "Incompleto"
                            } else {
                                "${homeState.summary.totalCollected} ${session.business.currency}"
                            },
                        )
                        SummaryRow(
                            label = "Cubetas por entregar (acumulado)",
                            value = if (homeState.salesIncomplete) {
                                "Incompleto"
                            } else {
                                "${homeState.summary.pendingDeliveryBuckets}"
                            },
                        )
                        if (homeState.pendingSyncCount > 0) {
                            Text(
                                text = "Incluye ${homeState.pendingSyncCount} movimiento(s) pendiente(s) de sincronización",
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (homeState.summaryError != null) {
                            Text(
                                text = homeState.summaryError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Precios vigentes",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.pricesError != null) {
                    Text(
                        text = state.pricesError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            items(EggClassification.entries) { classification ->
                val result = state.activePrices[classification] ?: ActivePriceResult.NotFound
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = classification.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val label = when (result) {
                            is ActivePriceResult.Found -> "${result.price.pricePerBucket} ${session.business.currency}"
                            ActivePriceResult.NotFound -> "Sin precio vigente"
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
