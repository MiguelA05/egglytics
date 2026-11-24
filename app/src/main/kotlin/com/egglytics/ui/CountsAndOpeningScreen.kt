package com.egglytics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.FeedIngredient
import com.egglytics.domain.model.OperationalDate

@Composable
internal fun CountsAndOpeningRoute(
    session: SessionState.Authenticated,
    countsAndOpeningViewModel: CountsAndOpeningViewModel,
    onBack: () -> Unit,
) {
    val countsUi by countsAndOpeningViewModel.uiState.collectAsStateWithLifecycle()
    CountsAndOpeningScreen(
        currency = session.business.currency,
        state = countsUi,
        onBack = onBack,
        onModeChange = countsAndOpeningViewModel::onModeChange,
        onDateChange = countsAndOpeningViewModel::onDateChange,
        onFeedPhysicalChange = countsAndOpeningViewModel::onFeedPhysicalChange,
        onFeedReasonChange = countsAndOpeningViewModel::onFeedReasonChange,
        onCashPhysicalChange = countsAndOpeningViewModel::onCashPhysicalChange,
        onCashReasonChange = countsAndOpeningViewModel::onCashReasonChange,
        onIsClosingChange = countsAndOpeningViewModel::onIsClosingChange,
        onNoteChange = countsAndOpeningViewModel::onNoteChange,
        onSubmit = { countsAndOpeningViewModel.submit(session) },
    )
}

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
            if (state.sessionUid == null) {
                item { Text(state.readError ?: "Cargando saldos…") }
                return@LazyColumn
            }
            item {
                val openingBanner = when {
                    state.hasPendingOpenings -> "Apertura pendiente de confirmación del servidor"
                    state.openings.isNotEmpty() -> "Apertura confirmada en servidor (${state.openings.size} saldos)"
                    else -> "Sin apertura confirmada en el servidor"
                }
                Text(openingBanner, style = MaterialTheme.typography.bodySmall)
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

            if (state.closings.isNotEmpty()) {
                item {
                    Text(
                        text = "Historial de cierres (${state.closings.size})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                val countsById = state.counts.associateBy { it.id }
                val allExpectedKeys = FeedIngredient.entries.map { it.code } + state.paymentMethods.map { it.id }
                items(state.closings, key = { "cl_${it.id}" }) { closing ->
                    val linkedCounts = closing.countIds.mapNotNull { countsById[it] }
                    val countedKeys = linkedCounts.flatMap { it.items.keys }.toSet()
                    val omittedKeys = allExpectedKeys.filter { it !in countedKeys }
                    val s = closing.summary
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Cierre ${OperationalDate.format(closing.fechaOperativa)}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Ventas: ${s["salesTotal"] ?: 0L} $currency · Cobros: ${s["collectionsTotal"] ?: 0L} $currency" +
                                    if ((s["refundsTotal"] ?: 0L) > 0L) " · Reintegros: ${s["refundsTotal"] ?: 0L} $currency" else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = "Gastos op.: ${s["operatingExpensesTotal"] ?: 0L} $currency · Compras alim.: ${s["feedPurchasesTotal"] ?: 0L} $currency · Retiros: ${s["withdrawalsTotal"] ?: 0L} $currency",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (omittedKeys.isEmpty()) {
                                Text("Cierre completo (${countedKeys.size} claves contadas)", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text(
                                    text = "Cierre parcial · Contadas: ${countedKeys.joinToString(", ").ifEmpty { "ninguna" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = "Omitidas: ${omittedKeys.joinToString(", ")}",
                                    color = MaterialTheme.colorScheme.tertiary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            linkedCounts.forEach { count ->
                                count.items.forEach { (key, item) ->
                                    val reasonTxt = item.reason?.let { " · Motivo: $it" }.orEmpty()
                                    Text(
                                        text = "  $key: contado ${item.physical} / esp. ${item.expected} (dif ${item.difference}$reasonTxt)",
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
}
