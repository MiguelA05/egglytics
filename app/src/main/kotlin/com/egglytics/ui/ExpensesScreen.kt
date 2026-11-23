package com.egglytics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.ReversalTargetCollection
import com.egglytics.ui.components.SyncStatusBadge
import com.egglytics.ui.components.VoidReasonDialog

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpensesScreen(
    currency: String,
    state: ExpensesUiState,
    onBack: () -> Unit,
    onModeChange: (ExpenseFormMode) -> Unit,
    onAmountChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onMethodSelect: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoidMovement: (ReversalTargetCollection, String, String) -> Unit = { _, _, _ -> },
) {
    val categoryNames = state.categories.associate { it.id to it.name }
    val methodNames = state.paymentMethods.associate { it.id to it.name }
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
                    Text("Gastos y retiros", style = MaterialTheme.typography.headlineSmall)
                    OutlinedButton(onClick = onBack) { Text("Volver") }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.form.mode == ExpenseFormMode.EXPENSE,
                                onClick = { onModeChange(ExpenseFormMode.EXPENSE) },
                                label = { Text("Gasto operativo") },
                            )
                            FilterChip(
                                selected = state.form.mode == ExpenseFormMode.WITHDRAWAL,
                                onClick = { onModeChange(ExpenseFormMode.WITHDRAWAL) },
                                label = { Text("Retiro personal") },
                            )
                        }

                        OutlinedTextField(
                            value = state.form.dateInput,
                            onValueChange = onDateChange,
                            label = { Text("Fecha operativa (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = state.form.amountInput,
                            onValueChange = onAmountChange,
                            label = { Text("Monto ($currency)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (state.form.mode == ExpenseFormMode.EXPENSE) {
                            Text("Categoría", style = MaterialTheme.typography.labelMedium)
                            if (state.categories.isEmpty()) {
                                Text(
                                    "No hay categorías activas.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.categories.forEach { cat ->
                                        FilterChip(
                                            selected = state.form.selectedCategoryId == cat.id,
                                            onClick = { onCategorySelect(cat.id) },
                                            label = { Text(cat.name) },
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = state.form.descriptionInput,
                                onValueChange = onDescriptionChange,
                                label = { Text("Descripción (opcional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Text("Forma de pago", style = MaterialTheme.typography.labelMedium)
                        if (state.paymentMethods.isEmpty()) {
                            Text(
                                "No hay formas de pago activas.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.paymentMethods.forEach { m ->
                                    FilterChip(
                                        selected = state.form.selectedMethodId == m.id,
                                        onClick = { onMethodSelect(m.id) },
                                        label = { Text(m.name) },
                                    )
                                }
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
                            Text(
                                if (state.form.mode == ExpenseFormMode.EXPENSE) {
                                    "Guardar gasto"
                                } else {
                                    "Guardar retiro"
                                },
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Gastos (${state.expenses.size}) · Retiros (${state.withdrawals.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.error != null) {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            items(state.expenses, key = { "exp_${it.expense.id}" }) { item ->
                val catName = categoryNames[item.expense.categoryId] ?: item.expense.categoryId
                val methodName = methodNames[item.expense.methodId] ?: item.expense.methodId
                val revReason = state.reversedReasons["${ReversalTargetCollection.EXPENSES.firestoreValue}_${item.expense.id}"]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${OperationalDate.format(item.expense.fechaOperativa)} · Gasto ($catName)",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "${item.expense.amount} $currency · $methodName",
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
                                            ReversalTargetCollection.EXPENSES,
                                            item.expense.id,
                                            "Gasto $catName (${item.expense.amount} $currency)",
                                        )
                                    },
                                ) { Text("Anular") }
                            }
                        }
                        SyncStatusBadge(isPendingWrite = item.isPendingWrite)
                    }
                }
            }

            items(state.withdrawals, key = { "w_${it.withdrawal.id}" }) { item ->
                val methodName = methodNames[item.withdrawal.methodId] ?: item.withdrawal.methodId
                val revReason = state.reversedReasons["${ReversalTargetCollection.WITHDRAWALS.firestoreValue}_${item.withdrawal.id}"]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${OperationalDate.format(item.withdrawal.fechaOperativa)} · Retiro personal",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "${item.withdrawal.amount} $currency · $methodName",
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
                                            ReversalTargetCollection.WITHDRAWALS,
                                            item.withdrawal.id,
                                            "Retiro personal (${item.withdrawal.amount} $currency)",
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
