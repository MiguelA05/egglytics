package com.egglytics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.egglytics.domain.model.FeedIngredient

@Composable
fun CountsAndOpeningScreen(
    currency: String,
    state: CountsAndOpeningUiState,
    onBack: () -> Unit,
    onModeChange: (CountsMode) -> Unit,
    onDateChange: (String) -> Unit,
    onFeedPhysicalChange: (FeedIngredient, String) -> Unit,
    onFeedReasonChange: (FeedIngredient, String) -> Unit,
    onCashPhysicalChange: (String, String) -> Unit,
    onCashReasonChange: (String, String) -> Unit,
    onIsClosingChange: (Boolean) -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
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
                    Text("Conteos y apertura", style = MaterialTheme.typography.headlineSmall)
                    OutlinedButton(onClick = onBack) { Text("Volver") }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.form.mode == CountsMode.COUNT_OR_CLOSING,
                        onClick = { onModeChange(CountsMode.COUNT_OR_CLOSING) },
                        label = { Text("Conteo / Cierre") },
                    )
                    if (state.isAdmin) {
                        FilterChip(
                            selected = state.form.mode == CountsMode.OPENING,
                            onClick = { onModeChange(CountsMode.OPENING) },
                            label = { Text("Apertura") },
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.form.dateInput,
                    onValueChange = onDateChange,
                    label = { Text("Fecha operativa (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.form.lastCountedSummary != null) {
                item {
                    Text(
                        text = state.form.lastCountedSummary,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item {
                Text("Alimento (kg)", style = MaterialTheme.typography.titleMedium)
            }
            FeedIngredient.entries.forEach { ing ->
                item(key = "feed_${ing.code}") {
                    val expected = state.expectedFeedKg[ing] ?: 0L
                    val raw = state.form.feedPhysicalInputs[ing].orEmpty()
                    val physical = raw.trim().toLongOrNull()
                    val diff = physical?.minus(expected)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                if (ing == FeedIngredient.CONCENTRATE) "Concentrado" else "Salvado de maíz",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (state.form.mode == CountsMode.COUNT_OR_CLOSING) {
                                Text("Esperado: $expected kg", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedTextField(
                                value = raw,
                                onValueChange = { onFeedPhysicalChange(ing, it) },
                                label = {
                                    Text(
                                        if (state.form.mode == CountsMode.OPENING) "Kg inicial"
                                        else "Kg contado (vacío = omitir)",
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (state.form.mode == CountsMode.COUNT_OR_CLOSING && diff != null && diff != 0L) {
                                Text("Diferencia: $diff kg", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(
                                    value = state.form.feedReasonInputs[ing].orEmpty(),
                                    onValueChange = { onFeedReasonChange(ing, it) },
                                    label = { Text("Motivo diferencia (obligatorio)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text("Caja ($currency)", style = MaterialTheme.typography.titleMedium)
            }
            state.paymentMethods.forEach { method ->
                item(key = "cash_${method.id}") {
                    val expected = state.expectedCashByMethod[method.id] ?: 0L
                    val raw = state.form.cashPhysicalInputs[method.id].orEmpty()
                    val physical = raw.trim().toLongOrNull()
                    val diff = physical?.minus(expected)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(method.name, style = MaterialTheme.typography.titleSmall)
                            if (state.form.mode == CountsMode.COUNT_OR_CLOSING) {
                                Text("Esperado: $expected $currency", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedTextField(
                                value = raw,
                                onValueChange = { onCashPhysicalChange(method.id, it) },
                                label = {
                                    Text(
                                        if (state.form.mode == CountsMode.OPENING) "Saldo inicial ($currency)"
                                        else "Contado ($currency, vacío = omitir)",
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (state.form.mode == CountsMode.COUNT_OR_CLOSING && diff != null && diff != 0L) {
                                Text("Diferencia: $diff $currency", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(
                                    value = state.form.cashReasonInputs[method.id].orEmpty(),
                                    onValueChange = { onCashReasonChange(method.id, it) },
                                    label = { Text("Motivo diferencia (obligatorio)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            if (state.form.mode == CountsMode.COUNT_OR_CLOSING) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.form.isClosing, onCheckedChange = onIsClosingChange)
                        Text("Guardar como cierre de jornada (WriteBatch)")
                    }
                    OutlinedTextField(
                        value = state.form.noteInput,
                        onValueChange = onNoteChange,
                        label = { Text("Nota (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                if (state.form.formError != null) {
                    Text(state.form.formError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        when {
                            state.form.mode == CountsMode.OPENING -> "Guardar apertura"
                            state.form.isClosing -> "Guardar cierre"
                            else -> "Guardar conteo"
                        },
                    )
                }
            }
        }
    }
}
