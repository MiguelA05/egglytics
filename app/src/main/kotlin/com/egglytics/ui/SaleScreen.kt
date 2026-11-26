package com.egglytics.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.egglytics.domain.model.SaleLine
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egglytics.data.repository.ObservedSale
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.DeliveryStatus
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.usecase.ActivePriceResult
import com.egglytics.domain.usecase.SaleReversalEligibility
import com.egglytics.ui.components.SyncStatusBadge
import com.egglytics.ui.components.VoidReasonDialog

@Composable
internal fun SaleRoute(
    session: SessionState.Authenticated,
    saleViewModel: SaleViewModel,
    activePrices: Map<EggClassification, ActivePriceResult>,
    onBack: () -> Unit,
) {
    val saleUi by saleViewModel.uiState.collectAsStateWithLifecycle()
    SaleScreen(
        businessName = session.business.name,
        currency = session.business.currency,
        activePrices = activePrices,
        state = saleUi,
        onBack = onBack,
        onSaleClassificationChange = saleViewModel::onSaleClassificationChange,
        onPaymentMethodSelect = saleViewModel::onPaymentMethodSelect,
        onSaleBucketsChange = saleViewModel::onSaleBucketsChange,
        onSaleDateChange = saleViewModel::onSaleDateChange,
        onSaleNoteChange = saleViewModel::onSaleNoteChange,
        onSubmitSale = {
            saleViewModel.submitSale(session, activePrices)
        },
        onMarkSaleDelivered = saleViewModel::markSaleDelivered,
        onVoidSale = { item, reason ->
            saleViewModel.voidSale(session, item, reason)
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SaleScreen(
    businessName: String,
    currency: String,
    activePrices: Map<EggClassification, ActivePriceResult>,
    state: SaleUiState,
    onBack: () -> Unit,
    onSaleClassificationChange: (EggClassification) -> Unit,
    onPaymentMethodSelect: (String) -> Unit,
    onSaleBucketsChange: (String) -> Unit,
    onSaleDateChange: (String) -> Unit,
    onSaleNoteChange: (String) -> Unit,
    onSubmitSale: () -> Unit,
    onMarkSaleDelivered: (String) -> Unit,
    onVoidSale: (ObservedSale, String) -> Unit,
) {
    val form = state.form
    val numbers = remember { NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CO")) }
    val price = (activePrices[form.saleClassification] as? ActivePriceResult.Found)?.price?.pricePerBucket
    val count = form.saleBucketsInput.trim().toLongOrNull()
    val eggs = count?.takeIf { it > 0 && it <= Long.MAX_VALUE / SaleLine.EGGS_PER_BUCKET }
        ?.times(SaleLine.EGGS_PER_BUCKET)
    val total = if (eggs != null && price != null && price > 0 && count != null && count <= Long.MAX_VALUE / price) count * price else null
    val method = state.paymentMethods.find { it.id == form.selectedMethodId }
    var history by rememberSaveable { mutableStateOf(false) }
    var saleToVoid by remember { mutableStateOf<ObservedSale?>(null) }

    saleToVoid?.let { target ->
        VoidReasonDialog(
            title = "Anular venta",
            description = "Se anularán la venta y su cobro de ${numbers.format(target.sale.total)} $currency.",
            onDismiss = { saleToVoid = null },
            onConfirm = { reason ->
                saleToVoid = null
                onVoidSale(target, reason)
            },
        )
    }
    Scaffold(containerColor = egglyticsColors.background) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding()) {
            ModuleHeader("Ventas", businessName, onBack)
            TabRow(selectedTabIndex = if (history) 1 else 0, containerColor = egglyticsColors.background) {
                Tab(selected = !history, onClick = { history = false }, text = { Text("Nueva venta") })
                Tab(selected = history, onClick = { history = true }, text = { Text("Historial (${state.sales.size})") })
            }
            if (history) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.salesError?.let { item { Text(it, color = egglyticsColors.error) } }
                    if (state.sales.isEmpty() && state.salesError == null) {
                        item {
                            Surface(modifier = Modifier.fillMaxWidth(), shape = panelShape) {
                                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Sin ventas registradas", style = MaterialTheme.typography.titleMedium)
                                    Button(onClick = { history = false }) { Text("Registrar primera venta") }
                                }
                            }
                        }
                    }
                    items(state.sales, key = { it.sale.id }) { item ->
                        val voidReason = state.reversedSaleReasons[item.sale.id]
                        SaleRowCard(
                            observed = item,
                            paidAmount = state.paidAmountBySaleId[item.sale.id] ?: 0L,
                            paymentMethods = state.paymentMethodsBySaleId[item.sale.id].orEmpty(),
                            currency = currency,
                            numbers = numbers,
                            voidReason = voidReason,
                            canVoid = state.canVoid && voidReason == null,
                            eligibility = state.saleReversalEligibility[item.sale.id],
                            isVoiding = item.sale.id in state.voidingSaleIds,
                            onMarkDelivered = { onMarkSaleDelivered(item.sale.id) },
                            onVoidClick = { saleToVoid = item },
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        OutlinedTextField(
                            value = form.saleDateInput, onValueChange = onSaleDateChange,
                            label = { Text("Fecha de venta (YYYY-MM-DD)") }, singleLine = true,
                            enabled = !form.isSubmitting, modifier = Modifier.fillMaxWidth(), shape = fieldShape,
                        )
                    }
                    item {
                        SectionPanel("Clasificación") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                EggClassification.entries.forEach { classification ->
                                    FilterChip(selected = form.saleClassification == classification,
                                        onClick = { onSaleClassificationChange(classification) },
                                        enabled = !form.isSubmitting, label = { Text(classification.name) })
                                }
                            }
                            Text(price?.let { "${numbers.format(it)} $currency por cubeta" }
                                ?: "Sin precio vigente para ${form.saleClassification.name}",
                                color = if (price == null) egglyticsColors.error else egglyticsColors.primary)
                            HorizontalDivider()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(shape = fieldShape, value = form.saleBucketsInput, onValueChange = onSaleBucketsChange,
                                    label = { Text("Cubetas") }, singleLine = true, enabled = !form.isSubmitting,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                                val stepCount = if (form.saleBucketsInput.isBlank()) 0L else count
                                IconButton(onClick = { stepCount?.let { onSaleBucketsChange((it - 1).toString()) } },
                                    enabled = !form.isSubmitting && stepCount != null && stepCount > 0,
                                    modifier = Modifier.semantics { contentDescription = "Restar una cubeta" }) { Text("−") }
                                IconButton(onClick = { stepCount?.let { onSaleBucketsChange((it + 1).toString()) } },
                                    enabled = !form.isSubmitting && stepCount != null && stepCount in 0 until Long.MAX_VALUE,
                                    modifier = Modifier.semantics { contentDescription = "Sumar una cubeta" }) { Text("+") }
                            }
                            Text(eggs?.let { "${numbers.format(it)} huevos" } ?: "1 cubeta = ${SaleLine.EGGS_PER_BUCKET} huevos",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item {
                        SectionPanel("Forma de pago") {
                            if (state.paymentMethods.isEmpty()) Text("No hay formas de pago activas.", color = egglyticsColors.error)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.paymentMethods.forEach { option ->
                                    FilterChip(selected = option.id == form.selectedMethodId, enabled = !form.isSubmitting,
                                        onClick = { onPaymentMethodSelect(option.id) }, label = { Text(option.name) })
                                }
                            }
                            OutlinedTextField(shape = fieldShape, value = form.saleNoteInput, onValueChange = onSaleNoteChange,
                                label = { Text("Nota de venta (opcional)") }, enabled = !form.isSubmitting,
                                modifier = Modifier.fillMaxWidth())
                        }
                    }
                    item {
                        Surface(color = egglyticsColors.secondaryContainer, shape = panelShape) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Total a cobrar", style = MaterialTheme.typography.titleMedium)
                                Text(total?.let { "${numbers.format(it)} $currency" } ?: "—",
                                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                if (total != null) Text("${numbers.format(count)} × ${numbers.format(price)} $currency · ${form.saleClassification.name}")
                                if (method != null) Text("Cobro completo por ${method.name} · Entrega pendiente",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        if (count != null && count > 0 && (eggs == null || (price != null && total == null))) {
                            Text("La cantidad excede el límite de la venta.", color = egglyticsColors.error)
                        }
                        form.saleError?.let { Text(it, color = egglyticsColors.error) }
                        state.salesError?.let { Text(it, color = egglyticsColors.error) }
                        Button(onClick = onSubmitSale, enabled = !form.isSubmitting && method != null && total != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = panelShape) {
                            Text(if (form.isSubmitting) "Guardando…" else "Guardar venta")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaleRowCard(
    observed: ObservedSale,
    paidAmount: Long,
    paymentMethods: List<String>,
    numbers: NumberFormat,
    currency: String,
    voidReason: String?,
    canVoid: Boolean,
    eligibility: SaleReversalEligibility?,
    isVoiding: Boolean,
    onMarkDelivered: () -> Unit,
    onVoidClick: () -> Unit,
) {
    val sale = observed.sale
    val deliveryText = when {
        voidReason != null -> "Anulada"
        sale.deliveryStatus == DeliveryStatus.PENDING -> "Pendiente de entrega"
        else -> "Entregada"
    }
    val paymentText = when {
        voidReason != null -> "Cobro anulado"
        paidAmount > 0L -> listOf("Pagado: ${numbers.format(paidAmount)} $currency")
            .plus(paymentMethods).joinToString(" · ")
        else -> "Sin pago registrado"
    }

    Surface(modifier = Modifier.fillMaxWidth(), shape = panelShape) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = OperationalDate.format(sale.fechaOperativa),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row {
                    Text("Sincronización · ", style = MaterialTheme.typography.bodySmall)
                    SyncStatusBadge(isPendingWrite = observed.isPendingWrite)
                }
            }
            Text("${numbers.format(sale.total)} $currency", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            sale.lines.forEach { line ->
                Text("${line.classification.name} · ${numbers.format(line.buckets)} cubetas · ${numbers.format(line.eggs)} huevos")
            }
            Text("Entrega: $deliveryText", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = paymentText,
                style = MaterialTheme.typography.bodySmall,
            )
            if (!sale.note.isNullOrBlank()) {
                Text(
                    text = sale.note.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (voidReason != null) {
                Text(
                    text = "Anulada · Motivo: $voidReason",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                if (sale.deliveryStatus == DeliveryStatus.PENDING) {
                    OutlinedButton(
                        onClick = onMarkDelivered,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Marcar entregada")
                    }
                }
                if (canVoid) {
                    when (eligibility) {
                        is SaleReversalEligibility.Allowed -> {
                            OutlinedButton(
                                onClick = onVoidClick,
                                enabled = !isVoiding,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (isVoiding) "Anulando..." else "Anular venta")
                            }
                        }

                        is SaleReversalEligibility.Blocked -> {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Anular venta (bloqueado)")
                            }
                            Text(
                                text = eligibility.reason,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        null -> Unit
                    }
                }
            }
        }
    }
}
