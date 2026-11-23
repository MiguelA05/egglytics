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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.egglytics.domain.model.FeedIngredient
import com.egglytics.domain.model.FeedLossType
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.ReversalTargetCollection
import com.egglytics.ui.components.SyncStatusBadge
import com.egglytics.ui.components.VoidReasonDialog

@Composable
fun FeedScreen(
    currency: String,
    state: FeedUiState,
    onBack: () -> Unit,
    onModeChange: (FeedFormMode) -> Unit,
    onIngredientChange: (FeedIngredient) -> Unit,
    onBagsChange: (String) -> Unit,
    onTotalCostChange: (String) -> Unit,
    onMethodSelect: (String) -> Unit,
    onSupplierChange: (String) -> Unit,
    onLossKgChange: (String) -> Unit,
    onLossTypeChange: (FeedLossType) -> Unit,
    onLossReasonChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onPeriodStartChange: (String) -> Unit,
    onPeriodEndChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoidMovement: (ReversalTargetCollection, String, String) -> Unit = { _, _, _ -> },
) {
    var pendingVoid by remember { mutableStateOf<Triple<ReversalTargetCollection, String, String>?>(null) }

    pendingVoid?.let { (targetCollection, targetId, description) ->
        VoidReasonDialog(
            title = "Anular movimiento",
            description = description,
            onDismiss = { pendingVoid = null },
            onConfirm = { reason ->
                onVoidMovement(targetCollection, targetId, reason)
                pendingVoid = null
            },
        )
    }

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
                    Text("Alimento", style = MaterialTheme.typography.headlineSmall)
                    OutlinedButton(onClick = onBack) { Text("Volver") }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.form.periodStartInput,
                        onValueChange = onPeriodStartChange,
                        label = { Text("Desde") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.form.periodEndInput,
                        onValueChange = onPeriodEndChange,
                        label = { Text("Hasta") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            items(FeedIngredient.entries) { ingredient ->
                val summary = state.summaries[ingredient]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = ingredientLabel(ingredient),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Saldo esperado (aprox. con bulto abierto): ${summary?.balanceKg ?: 0L} kg",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val baseNote = when {
                            summary == null -> "Sin base inicial"
                            !summary.hasBase -> "Falta apertura o conteo base (usando 0 kg)"
                            summary.baseSourceLabel != null -> "Base: ${summary.baseSourceLabel}"
                            else -> "Base configurada"
                        }
                        Text(
                            text = baseNote,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "Consumo diario estimado (${summary?.inclusiveDays ?: 0L} días): " +
                                (summary?.formattedDailyConsumption() ?: "Sin datos"),
                            style = MaterialTheme.typography.bodySmall,
                        )
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FeedFormMode.entries.forEach { mode ->
                                val label = when (mode) {
                                    FeedFormMode.PURCHASE -> "Compra"
                                    FeedFormMode.DEPLETION -> "Agotado"
                                    FeedFormMode.LOSS -> "Pérdida"
                                }
                                FilterChip(
                                    selected = state.form.mode == mode,
                                    onClick = { onModeChange(mode) },
                                    label = { Text(label) },
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FeedIngredient.entries.forEach { ing ->
                                FilterChip(
                                    selected = state.form.ingredient == ing,
                                    onClick = { onIngredientChange(ing) },
                                    label = { Text(ingredientLabel(ing)) },
                                )
                            }
                        }

                        OutlinedTextField(
                            value = state.form.dateInput,
                            onValueChange = onDateChange,
                            label = { Text("Fecha operativa (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        when (state.form.mode) {
                            FeedFormMode.PURCHASE -> {
                                OutlinedTextField(
                                    value = state.form.bagsInput,
                                    onValueChange = onBagsChange,
                                    label = { Text("Bultos (40 kg c/u)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                FeedViewModel.previewKg(state.form.bagsInput)?.let { kg ->
                                    Text("Suma $kg kg al saldo", style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedTextField(
                                    value = state.form.totalCostInput,
                                    onValueChange = onTotalCostChange,
                                    label = { Text("Costo total ($currency)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text("Forma de pago", style = MaterialTheme.typography.labelMedium)
                                if (state.paymentMethods.isEmpty()) {
                                    Text(
                                        "No hay formas de pago activas.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        state.paymentMethods.forEach { method ->
                                            FilterChip(
                                                selected = state.form.selectedMethodId == method.id,
                                                onClick = { onMethodSelect(method.id) },
                                                label = { Text(method.name) },
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = state.form.supplierInput,
                                    onValueChange = onSupplierChange,
                                    label = { Text("Proveedor (opcional)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            FeedFormMode.DEPLETION -> {
                                OutlinedTextField(
                                    value = state.form.bagsInput,
                                    onValueChange = onBagsChange,
                                    label = { Text("Bultos agotados (40 kg c/u)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                FeedViewModel.previewKg(state.form.bagsInput)?.let { kg ->
                                    Text("Resta $kg kg del saldo", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            FeedFormMode.LOSS -> {
                                OutlinedTextField(
                                    value = state.form.lossKgInput,
                                    onValueChange = onLossKgChange,
                                    label = { Text("Kg perdidos") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FeedLossType.entries.forEach { t ->
                                        val label = when (t) {
                                            FeedLossType.WASTE -> "Desperdicio"
                                            FeedLossType.LOSS -> "Pérdida"
                                            FeedLossType.OTHER -> "Otro"
                                        }
                                        FilterChip(
                                            selected = state.form.lossType == t,
                                            onClick = { onLossTypeChange(t) },
                                            label = { Text(label) },
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = state.form.lossReasonInput,
                                    onValueChange = onLossReasonChange,
                                    label = { Text("Motivo (obligatorio)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        OutlinedTextField(
                            value = state.form.noteInput,
                            onValueChange = onNoteChange,
                            label = { Text("Nota (opcional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (state.form.formError != null) {
                            Text(
                                text = state.form.formError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) {
                            Text("Guardar registro")
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Historial de alimento (${state.purchases.size + state.depletions.size + state.losses.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.feedError != null) {
                    Text(
                        text = state.feedError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            items(state.purchases, key = { "fp_${it.purchase.id}" }) { item ->
                val revReason = state.reversedReasons["${ReversalTargetCollection.FEED_PURCHASES.firestoreValue}_${item.purchase.id}"]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${OperationalDate.format(item.purchase.fechaOperativa)} · Compra ${ingredientLabel(item.purchase.ingredient)}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "${item.purchase.bags} bultos (+${item.purchase.totalKg} kg) · ${item.purchase.totalCost} $currency",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (revReason != null) {
                                Text(
                                    "Anulado · Motivo: $revReason",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else if (state.isAdmin) {
                                TextButton(
                                    onClick = {
                                        pendingVoid = Triple(
                                            ReversalTargetCollection.FEED_PURCHASES,
                                            item.purchase.id,
                                            "Compra ${ingredientLabel(item.purchase.ingredient)} (${item.purchase.bags} bultos)",
                                        )
                                    },
                                ) { Text("Anular") }
                            }
                        }
                        SyncStatusBadge(isPendingWrite = item.isPendingWrite)
                    }
                }
            }

            items(state.depletions, key = { "fd_${it.depletion.id}" }) { item ->
                val revReason = state.reversedReasons["${ReversalTargetCollection.FEED_DEPLETIONS.firestoreValue}_${item.depletion.id}"]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${OperationalDate.format(item.depletion.fechaOperativa)} · Agotado ${ingredientLabel(item.depletion.ingredient)}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "${item.depletion.bags} bultos (-${item.depletion.kg} kg)",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (revReason != null) {
                                Text(
                                    "Anulado · Motivo: $revReason",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else if (state.isAdmin) {
                                TextButton(
                                    onClick = {
                                        pendingVoid = Triple(
                                            ReversalTargetCollection.FEED_DEPLETIONS,
                                            item.depletion.id,
                                            "Agotado ${ingredientLabel(item.depletion.ingredient)} (${item.depletion.bags} bultos)",
                                        )
                                    },
                                ) { Text("Anular") }
                            }
                        }
                        SyncStatusBadge(isPendingWrite = item.isPendingWrite)
                    }
                }
            }

            items(state.losses, key = { "fl_${it.loss.id}" }) { item ->
                val revReason = state.reversedReasons["${ReversalTargetCollection.FEED_LOSSES.firestoreValue}_${item.loss.id}"]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${OperationalDate.format(item.loss.fechaOperativa)} · Pérdida ${ingredientLabel(item.loss.ingredient)}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "-${item.loss.kg} kg · ${item.loss.reason}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (revReason != null) {
                                Text(
                                    "Anulado · Motivo: $revReason",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else if (state.isAdmin) {
                                TextButton(
                                    onClick = {
                                        pendingVoid = Triple(
                                            ReversalTargetCollection.FEED_LOSSES,
                                            item.loss.id,
                                            "Pérdida ${ingredientLabel(item.loss.ingredient)} (-${item.loss.kg} kg)",
                                        )
                                    },
                                ) { Text("Anular") }
                            }
                        }
                        SyncStatusBadge(isPendingWrite = item.isPendingWrite)
                    }
                }
            }
        }
    }
}

private fun ingredientLabel(ingredient: FeedIngredient): String = when (ingredient) {
    FeedIngredient.CONCENTRATE -> "Concentrado"
    FeedIngredient.CORN_BRAN -> "Salvado de maíz"
}
