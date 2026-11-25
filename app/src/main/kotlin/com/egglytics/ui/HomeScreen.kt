package com.egglytics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.egglytics.R
import com.egglytics.domain.model.MemberRole
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    val currency = session.business.currency
    val numbers = remember { NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CO")) }
    val dates = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO")) }
    val summary = homeState.summary

    MaterialTheme(colorScheme = egglyticsColors) {
        Scaffold(containerColor = egglyticsColors.background) { insets ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(insets),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.login_egg), null, tint = egglyticsColors.primary)
                        Text("Egglytics", Modifier.weight(1f).padding(start = 6.dp),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onSignOut) { Text("Salir") }
                    }
                    Surface(color = egglyticsColors.surface, shape = panelShape) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(session.business.name, style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold, color = egglyticsColors.primary)
                            val role = when (session.member.role) {
                                MemberRole.ADMIN -> "Administración"
                                MemberRole.OPERATOR -> "Operación"
                            }
                            Text("${session.member.name} · $role")
                            Text("Hoy, ${dates.format(state.operationalDate)} · Desde ${dates.format(session.business.startDate)}",
                                style = MaterialTheme.typography.bodySmall, color = egglyticsColors.onSurfaceVariant)
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CaptureAction("Registrar recolección", "Huevos por clasificación y descartados",
                            egglyticsColors.primary, egglyticsColors.onPrimary, onOpenHarvests)
                        CaptureAction("Registrar venta", "Cubetas y cobro por forma de pago",
                            egglyticsColors.secondaryContainer, egglyticsColors.onSurface, onOpenSales)
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModuleButton("Alimento", onOpenFeed, Modifier.weight(1f))
                            ModuleButton("Gastos y retiros", onOpenExpenses, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModuleButton("Caja y análisis", onOpenCash, Modifier.weight(1f))
                            ModuleButton("Conteos / Cierre", onOpenCounts, Modifier.weight(1f))
                        }
                    }
                }
                item {
                    SectionTitle("Resumen de hoy")
                    Surface(shape = panelShape, color = egglyticsColors.surface) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Metric("Huevos aptos", if (homeState.harvestsIncomplete) "Incompleto" else numbers.format(summary.gradedEggs), Modifier.weight(1f))
                                Metric("Descartados", if (homeState.harvestsIncomplete) "Incompleto" else numbers.format(summary.discardedEggs), Modifier.weight(1f))
                            }
                            HorizontalDivider()
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Metric("Total vendido", if (homeState.salesIncomplete) "Incompleto" else "${numbers.format(summary.totalSold)} $currency", Modifier.weight(1f))
                                Metric("Total cobrado", if (homeState.paymentsIncomplete) "Incompleto" else "${numbers.format(summary.totalCollected)} $currency", Modifier.weight(1f))
                            }
                            HorizontalDivider()
                            Metric("Cubetas por entregar · Acumulado", if (homeState.salesIncomplete) "Incompleto" else numbers.format(summary.pendingDeliveryBuckets))
                            if (homeState.pendingSyncCount > 0) {
                                Text("Movimientos pendientes de sincronización: ${homeState.pendingSyncCount}",
                                    color = egglyticsColors.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
                            }
                            homeState.summaryError?.let { Text(it, color = egglyticsColors.error) }
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
                    SectionTitle("Precios vigentes por cubeta")
                    Surface(shape = panelShape, color = egglyticsColors.surface) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            state.pricesError?.let { Text(it, color = egglyticsColors.error) }
                            EggClassification.entries.forEachIndexed { index, classification ->
                                if (index > 0) HorizontalDivider()
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(classification.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                    val price = state.activePrices[classification] as? ActivePriceResult.Found
                                    Text(price?.let { "${numbers.format(it.price.pricePerBucket)} $currency" } ?: "Sin precio vigente",
                                        color = if (price == null) egglyticsColors.onSurfaceVariant else egglyticsColors.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val panelShape = RoundedCornerShape(16.dp)

@Composable
private fun CaptureAction(title: String, detail: String, background: Color, foreground: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = panelShape, color = background, contentColor = foreground) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            Canvas(Modifier.padding(start = 12.dp).size(18.dp)) {
                val stroke = 2.dp.toPx()
                drawLine(foreground, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke)
                drawLine(foreground, Offset(size.width / 2, 0f), Offset(size.width, size.height / 2), stroke)
                drawLine(foreground, Offset(size.width / 2, size.height), Offset(size.width, size.height / 2), stroke)
            }
        }
    }
}

@Composable
private fun ModuleButton(label: String, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) {
        Text(label)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = egglyticsColors.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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

    Surface(modifier = Modifier.fillMaxWidth(), shape = panelShape, color = egglyticsColors.errorContainer) {
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
