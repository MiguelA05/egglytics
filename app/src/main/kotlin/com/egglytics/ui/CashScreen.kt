package com.egglytics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egglytics.data.repository.SessionState

@Composable
internal fun CashRoute(
    session: SessionState.Authenticated,
    cashViewModel: CashViewModel,
    onBack: () -> Unit,
) {
    val cashUi by cashViewModel.uiState.collectAsStateWithLifecycle()
    CashScreen(
        currency = session.business.currency,
        state = cashUi,
        onBack = onBack,
        onPeriodStartChange = cashViewModel::onPeriodStartChange,
        onPeriodEndChange = cashViewModel::onPeriodEndChange,
    )
}

@Composable
fun CashScreen(
    currency: String,
    state: CashUiState,
    onBack: () -> Unit,
    onPeriodStartChange: (String) -> Unit,
    onPeriodEndChange: (String) -> Unit,
) {
    val summary = state.summary

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Caja y análisis", style = MaterialTheme.typography.headlineSmall)
                    OutlinedButton(onClick = onBack) { Text("Volver") }
                }
            }

            state.error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
                return@LazyColumn
            }

            if (state.hasPendingSync) {
                item {
                    Text(
                        text = "Incluye movimientos pendientes de sincronización.",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.periodStartInput,
                        onValueChange = onPeriodStartChange,
                        label = { Text("Desde") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.periodEndInput,
                        onValueChange = onPeriodEndChange,
                        label = { Text("Hasta") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (summary != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Resultado del periodo (aproximado, no contable)",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            CashLine("Ventas netas", "${summary.resultSummary.netSales} $currency")
                            CashLine("Gastos operativos", "${summary.resultSummary.operatingExpenses} $currency")
                            CashLine(
                                "Costo estimado alimento agotado",
                                summary.resultSummary.estimatedFeedCost?.let { "$it $currency" } ?: "Sin dato",
                            )
                            CashLine(
                                "Resultado aproximado",
                                summary.resultSummary.approximateResult?.let { "$it $currency" } ?: "Sin dato",
                            )
                            CashLine(
                                "Egresos por compras de alimento (caja)",
                                "${summary.resultSummary.feedPurchaseOutflows} $currency",
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Caja por forma de pago (Total: ${summary.totalCashBalance} $currency)",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                items(summary.balancesByMethod, key = { it.method.id }) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val title = if (item.method.active) {
                                item.method.name
                            } else {
                                "${item.method.name} (desactivada)"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(title, style = MaterialTheme.typography.titleSmall)
                                Text("${item.balance} $currency", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                text = "Base: ${item.baseAmount} · Cobros: +${item.collections} · Reintegros: -${item.refunds}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = "Gastos: -${item.expenses} · Alimento: -${item.feedPurchases} · Retiros: -${item.withdrawals}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (!item.hasBase) {
                                Text(
                                    text = "Sin saldo de apertura/conteo base registrado",
                                    color = MaterialTheme.colorScheme.tertiary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CashLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
