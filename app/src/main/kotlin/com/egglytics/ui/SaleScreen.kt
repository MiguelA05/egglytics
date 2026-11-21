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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.egglytics.data.repository.ObservedSale
import com.egglytics.domain.model.DeliveryStatus
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.usecase.ActivePriceResult
import com.egglytics.ui.components.SyncStatusBadge

@Composable
fun SaleScreen(
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
) {
    val activePrice = activePrices[state.form.saleClassification] as? ActivePriceResult.Found
    val parsedBuckets = state.form.saleBucketsInput.trim().toLongOrNull()?.takeIf { it > 0L }

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
                    Text(
                        text = "Ventas",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    OutlinedButton(onClick = onBack) {
                        Text("Volver")
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            EggClassification.entries.forEach { classification ->
                                FilterChip(
                                    selected = state.form.saleClassification == classification,
                                    onClick = { onSaleClassificationChange(classification) },
                                    label = { Text(classification.name) },
                                )
                            }
                        }
                        val priceLabel = if (activePrice != null) {
                            "Precio vigente ${state.form.saleClassification.name}: ${activePrice.price.pricePerBucket} $currency / cubeta"
                        } else {
                            "Sin precio vigente para ${state.form.saleClassification.name}"
                        }
                        Text(
                            text = priceLabel,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "Forma de pago",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (state.paymentMethods.isEmpty()) {
                            Text(
                                text = "No hay formas de pago activas.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                state.paymentMethods.forEach { method ->
                                    FilterChip(
                                        selected = state.form.selectedMethodId == method.id,
                                        onClick = { onPaymentMethodSelect(method.id) },
                                        label = { Text(method.name) },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = state.form.saleDateInput,
                            onValueChange = onSaleDateChange,
                            label = { Text("Fecha venta (YYYY-MM-DD)") },
                            singleLine = true,
                            enabled = !state.form.isSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.form.saleBucketsInput,
                            onValueChange = onSaleBucketsChange,
                            label = { Text("Cubetas") },
                            singleLine = true,
                            enabled = !state.form.isSubmitting,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.form.saleNoteInput,
                            onValueChange = onSaleNoteChange,
                            label = { Text("Nota venta (opcional)") },
                            singleLine = true,
                            enabled = !state.form.isSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (activePrice != null && parsedBuckets != null) {
                            val totalPreview = parsedBuckets * activePrice.price.pricePerBucket
                            Text(
                                text = "Total a cobrar: $totalPreview $currency (${parsedBuckets * 30L} huevos)",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (state.form.saleError != null) {
                            Text(
                                text = state.form.saleError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(
                            onClick = onSubmitSale,
                            enabled = !state.form.isSubmitting && state.paymentMethods.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.form.isSubmitting) "Guardando..." else "Guardar venta")
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ventas (${state.sales.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.salesError != null) {
                    Text(
                        text = state.salesError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (state.sales.isEmpty()) {
                item {
                    Text(
                        text = "Sin ventas registradas.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(state.sales, key = { it.sale.id }) { item ->
                    SaleRowCard(
                        observed = item,
                        paidAmount = state.paidAmountBySaleId[item.sale.id] ?: 0L,
                        currency = currency,
                        onMarkDelivered = { onMarkSaleDelivered(item.sale.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SaleRowCard(
    observed: ObservedSale,
    paidAmount: Long,
    currency: String,
    onMarkDelivered: () -> Unit,
) {
    val sale = observed.sale
    val line = sale.lines.first()
    val deliveryText = when (sale.deliveryStatus) {
        DeliveryStatus.PENDING -> "Pendiente entrega"
        DeliveryStatus.DELIVERED -> "Entregada"
    }
    val paymentText = if (paidAmount > 0L) {
        "Pagado: $paidAmount $currency"
    } else {
        "Sin pago registrado"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
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
                    text = "${OperationalDate.format(sale.fechaOperativa)} · $deliveryText",
                    style = MaterialTheme.typography.titleSmall,
                )
                SyncStatusBadge(isPendingWrite = observed.isPendingWrite)
            }
            Text(
                text = "${line.classification.name}: ${line.buckets} cubetas (${line.eggs} huevos) · Total: ${sale.total} $currency",
                style = MaterialTheme.typography.bodyMedium,
            )
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
            if (sale.deliveryStatus == DeliveryStatus.PENDING) {
                OutlinedButton(
                    onClick = onMarkDelivered,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Marcar entregada")
                }
            }
        }
    }
}
