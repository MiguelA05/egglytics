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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egglytics.data.repository.SessionState
import com.egglytics.data.repository.SubmissionKind
import com.egglytics.data.repository.SubmissionReceipt
import com.egglytics.data.repository.SubmissionSyncStatus
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.usecase.ActivePriceResult

@Composable
internal fun HomeRoute(
    session: SessionState.Authenticated,
    homeViewModel: HomeViewModel,
    sessionUi: SessionUiState,
    navigateTo: (AppDestination) -> Unit,
    onSignOut: () -> Unit,
) {
    val homeUi by homeViewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        session = session,
        state = sessionUi,
        homeState = homeUi,
        onOpenHarvests = { navigateTo(AppDestination.HARVESTS) },
        onOpenSales = { navigateTo(AppDestination.SALES) },
        onOpenFeed = { navigateTo(AppDestination.FEED) },
        onOpenExpenses = { navigateTo(AppDestination.EXPENSES) },
        onOpenCash = { navigateTo(AppDestination.CASH) },
        onOpenCounts = { navigateTo(AppDestination.COUNTS) },
        onRetryReconcile = homeViewModel::retryReconcile,
        onDismissSyncNotice = homeViewModel::dismissSyncNotice,
        onSignOut = onSignOut,
    )
}

@Composable
fun HomeScreen(
    session: SessionState.Authenticated,
    state: SessionUiState,
    homeState: HomeUiState,
    onOpenHarvests: () -> Unit,
    onOpenSales: () -> Unit,
    onOpenFeed: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenCash: () -> Unit,
    onOpenCounts: () -> Unit,
    onRetryReconcile: () -> Unit,
    onDismissSyncNotice: (String) -> Unit,
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
                    Button(
                        onClick = onOpenFeed,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Alimento")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onOpenExpenses,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Gastos")
                    }
                    Button(
                        onClick = onOpenCash,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Caja")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onOpenCounts,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Conteos / Cierre")
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

            if (homeState.syncNotices.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Avisos de sincronización (${homeState.syncNotices.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                items(homeState.syncNotices, key = { it.submissionId }) { notice ->
                    SyncNoticeCard(
                        notice = notice,
                        isReconciling = homeState.isReconciling,
                        onOpenDestination = {
                            when (notice.kind) {
                                SubmissionKind.HARVEST -> onOpenHarvests()
                                SubmissionKind.SALE_WITH_PAYMENT -> onOpenSales()
                                SubmissionKind.FEED_PURCHASE,
                                SubmissionKind.FEED_DEPLETION,
                                SubmissionKind.FEED_LOSS,
                                -> onOpenFeed()
                                SubmissionKind.EXPENSE,
                                SubmissionKind.WITHDRAWAL,
                                -> onOpenExpenses()
                            }
                        },
                        onRetryReconcile = onRetryReconcile,
                        onDismiss = { onDismissSyncNotice(notice.submissionId) },
                    )
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

@Composable
private fun SyncNoticeCard(
    notice: SubmissionReceipt,
    isReconciling: Boolean,
    onOpenDestination: () -> Unit,
    onRetryReconcile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val kindLabel = when (notice.kind) {
        SubmissionKind.HARVEST -> "Recolección"
        SubmissionKind.SALE_WITH_PAYMENT -> "Venta + pago"
        SubmissionKind.FEED_PURCHASE -> "Compra de alimento"
        SubmissionKind.FEED_DEPLETION -> "Agotamiento de alimento"
        SubmissionKind.FEED_LOSS -> "Pérdida de alimento"
        SubmissionKind.EXPENSE -> "Gasto"
        SubmissionKind.WITHDRAWAL -> "Retiro"
    }
    val statusLabel = when (notice.status) {
        SubmissionSyncStatus.REJECTED -> "Rechazado al sincronizar"
        SubmissionSyncStatus.UNCONFIRMED -> "Sin confirmar en servidor"
        SubmissionSyncStatus.PENDING -> "Pendiente de sincronización"
        SubmissionSyncStatus.CONFIRMED -> "Confirmado"
    }
    val destinationLabel = when (notice.kind) {
        SubmissionKind.HARVEST -> "Ir a Recolección"
        SubmissionKind.SALE_WITH_PAYMENT -> "Ir a Ventas"
        SubmissionKind.FEED_PURCHASE,
        SubmissionKind.FEED_DEPLETION,
        SubmissionKind.FEED_LOSS,
        -> "Ir a Alimento"
        SubmissionKind.EXPENSE -> "Ir a Gastos"
        SubmissionKind.WITHDRAWAL -> "Ir a Gastos"
    }
    val idsLabel = if (notice.secondaryDocId != null) {
        "ID: ${notice.primaryDocId} · Pago: ${notice.secondaryDocId}"
    } else {
        "ID: ${notice.primaryDocId}"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "$statusLabel · $kindLabel",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = notice.summaryText,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = idsLabel,
                style = MaterialTheme.typography.bodySmall,
            )
            if (!notice.statusDetail.isNullOrBlank()) {
                Text(
                    text = notice.statusDetail.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenDestination,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(destinationLabel)
                }
                if (notice.status == SubmissionSyncStatus.UNCONFIRMED) {
                    OutlinedButton(
                        onClick = onRetryReconcile,
                        enabled = !isReconciling,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (isReconciling) "Verificando..." else "Reverificar")
                    }
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Descartar")
                }
            }
        }
    }
}
