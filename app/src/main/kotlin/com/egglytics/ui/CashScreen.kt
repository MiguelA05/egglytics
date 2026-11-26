package com.egglytics.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale
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
        businessName = session.business.name,
        currency = session.business.currency,
        state = cashUi,
        onBack = onBack,
        onPeriodStartChange = cashViewModel::onPeriodStartChange,
        onPeriodEndChange = cashViewModel::onPeriodEndChange,
    )
}

@Composable
fun CashScreen(
    businessName: String,
    currency: String,
    state: CashUiState,
    onBack: () -> Unit,
    onPeriodStartChange: (String) -> Unit,
    onPeriodEndChange: (String) -> Unit,
) {
    var analysis by rememberSaveable { mutableStateOf(false) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    val formatter = remember { NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CO")) }
    fun money(value: Long) = "${formatter.format(value)} $currency"

    Scaffold(containerColor = egglyticsColors.background) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding()) {
            ModuleHeader("Caja y análisis", businessName, onBack)
            TabRow(selectedTabIndex = if (analysis) 1 else 0, containerColor = egglyticsColors.background) {
                Tab(selected = !analysis, onClick = { analysis = false }, text = { Text("Saldo de caja") })
                Tab(selected = analysis, onClick = { analysis = true }, text = { Text("Resultado del periodo") })
            }
            key(analysis) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.error?.let {
                        item { Text(it, color = egglyticsColors.error) }
                        return@LazyColumn
                    }
                    val summary = state.summary
                    if (summary == null) {
                        item { Text("Cargando movimientos…") }
                        return@LazyColumn
                    }
                    if (state.hasPendingSync) item {
                        Surface(shape = panelShape, color = egglyticsColors.secondaryContainer) {
                            Text("Incluye movimientos pendientes de sincronización.", Modifier.fillMaxWidth().padding(12.dp))
                        }
                    }
                    if (!analysis) {
                        item {
                            BalanceHighlight("Saldo total de caja", money(summary.totalCashBalance))
                        }
                        item { Text("Por forma de pago", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        if (summary.balancesByMethod.isEmpty()) item { Text("Sin formas de pago registradas.") }
                        items(summary.balancesByMethod, key = { it.method.id }) { account ->
                            val expanded = expandedId == account.method.id
                            Surface(shape = panelShape) {
                                Column {
                                    Column(
                                        Modifier.fillMaxWidth()
                                            .clickable(role = Role.Button, onClickLabel = if (expanded) "Ocultar desglose" else "Ver desglose") {
                                                expandedId = if (expanded) null else account.method.id
                                            }
                                            .padding(16.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(account.method.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                            Text(if (expanded) "Ocultar desglose" else "Ver desglose", color = egglyticsColors.primary,
                                                style = MaterialTheme.typography.labelMedium)
                                        }
                                        Text(money(account.balance), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                        if (!account.method.active) Text("Forma de pago desactivada", style = MaterialTheme.typography.bodySmall)
                                        if (!account.hasBase) Text("Sin saldo de apertura o conteo base", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (expanded) Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        HorizontalDivider()
                                        CashLine("Saldo base", money(account.baseAmount))
                                        account.baseSourceLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                        CashLine("Cobros", money(account.collections))
                                        CashLine("Reintegros", money(-account.refunds))
                                        CashLine("Gastos operativos", money(-account.expenses))
                                        CashLine("Compras de alimento", money(-account.feedPurchases))
                                        CashLine("Retiros personales", money(-account.withdrawals))
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            SectionPanel("Periodo de análisis") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CalendarField("Desde", state.periodStartInput, onPeriodStartChange, Modifier.weight(1f))
                                    CalendarField("Hasta", state.periodEndInput, onPeriodEndChange, Modifier.weight(1f))
                                }
                                state.periodError?.let { Text(it, color = egglyticsColors.error) }
                            }
                        }
                        if (state.periodError != null) return@LazyColumn
                        val result = summary.resultSummary
                        item { BalanceHighlight("Resultado aproximado · No contable", result.approximateResult?.let(::money) ?: "Sin dato") }
                        item {
                            SectionPanel("Cálculo del resultado") {
                                CashLine("Ventas netas", money(result.netSales))
                                CashLine("Gastos operativos", money(-result.operatingExpenses))
                                CashLine("Costo estimado del alimento agotado", result.estimatedFeedCost?.let { money(-it) } ?: "Sin dato")
                            }
                        }
                        item {
                            SectionPanel("Compras de alimento") {
                                CashLine("Salida de caja del periodo", money(-result.feedPurchaseOutflows))
                                Text("La compra afecta la caja al pagar. El resultado usa el costo estimado del alimento agotado.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun BalanceHighlight(label: String, amount: String) {
    Surface(color = egglyticsColors.primary, contentColor = egglyticsColors.onPrimary, shape = panelShape) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(amount, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CashLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
