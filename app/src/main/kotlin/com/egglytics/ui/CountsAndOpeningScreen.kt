package com.egglytics.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
        businessName = session.business.name,
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
    businessName: String,
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
    val form = state.form
    val opening = form.mode == CountsMode.OPENING && state.isAdmin
    var history by rememberSaveable { mutableStateOf(false) }
    var step by rememberSaveable { mutableStateOf(CountStep.FEED) }
    val feed = FeedIngredient.entries.map { ing ->
        CountField(if (ing == FeedIngredient.CONCENTRATE) "Concentrado" else "Salvado de maíz", "kg",
            state.expectedFeedKg[ing] ?: 0L, form.feedPhysicalInputs[ing].orEmpty(), form.feedReasonInputs[ing].orEmpty(),
            { onFeedPhysicalChange(ing, it) }, { onFeedReasonChange(ing, it) })
    }
    val cash = state.paymentMethods.map { method ->
        CountField(method.name, currency, state.expectedCashByMethod[method.id] ?: 0L,
            form.cashPhysicalInputs[method.id].orEmpty(), form.cashReasonInputs[method.id].orEmpty(),
            { onCashPhysicalChange(method.id, it) }, { onCashReasonChange(method.id, it) })
    }
    val fields = feed + cash
    MaterialTheme(colorScheme = egglyticsColors) {
        Scaffold(containerColor = egglyticsColors.background) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Conteos y cierre", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(businessName, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onBack) { Text("Volver") }
                }
                if (state.sessionUid == null || state.readError != null) {
                    Text(state.readError ?: "Cargando saldos…", Modifier.padding(16.dp))
                    return@Column
                }
                val pages = if (state.isAdmin) listOf("Conteo", "Apertura", "Historial") else listOf("Conteo", "Historial")
                val selected = if (history) "Historial" else if (opening) "Apertura" else "Conteo"
                TabRow(selectedTabIndex = pages.indexOf(selected), containerColor = egglyticsColors.background) {
                    pages.forEach { page ->
                        Tab(selected = page == selected, onClick = {
                            history = page == "Historial"
                            if (!history) onModeChange(if (page == "Apertura") CountsMode.OPENING else CountsMode.COUNT_OR_CLOSING)
                        }, text = { Text(page) })
                    }
                }
                key(selected, step) {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (history) {
                            closingHistory(state, currency)
                            return@LazyColumn
                        }
                        item {
                            CountPanel(if (opening) "Apertura de saldos" else "Jornada de conteo") {
                                Text(when {
                                    state.hasPendingOpenings -> "Apertura pendiente de confirmación"
                                    state.openings.isNotEmpty() -> "Apertura confirmada · ${state.openings.size} saldos"
                                    else -> "Sin apertura confirmada"
                                })
                                OutlinedTextField(value = form.dateInput, onValueChange = onDateChange,
                                    label = { Text("Fecha operativa (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                form.lastCountedSummary?.let { Text(it, color = egglyticsColors.primary) }
                            }
                        }
                        if (!opening) item {
                            TabRow(step.ordinal, containerColor = egglyticsColors.background) {
                                CountStep.entries.forEach { option ->
                                    Tab(selected = step == option, onClick = { step = option }, text = { Text(option.label) })
                                }
                            }
                        }
                        if (opening || step != CountStep.REVIEW) {
                            if (opening || step == CountStep.FEED) item {
                                CountPanel(if (opening) "Inventario inicial" else "Conteo de alimento") {
                                    feed.forEach { CountInput(it, opening) }
                                }
                            }
                            if (opening || step == CountStep.CASH) item {
                                CountPanel(if (opening) "Fondos iniciales" else "Conteo de caja") {
                                    if (cash.isEmpty()) Text("Sin formas de pago registradas.")
                                    cash.forEach { CountInput(it, opening) }
                                }
                            }
                            if (!opening) item {
                                Text("Deja el campo vacío para omitir el ítem. Escribe 0 si lo contaste y no había existencias.",
                                    style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { step = if (step == CountStep.FEED) CountStep.CASH else CountStep.REVIEW },
                                    modifier = Modifier.fillMaxWidth()) {
                                    Text(if (step == CountStep.FEED) "Continuar a caja" else "Revisar conteo")
                                }
                            }
                        } else {
                            item {
                                val omitted = fields.count { it.raw.isBlank() }
                                CountPanel(if (form.isClosing && omitted > 0) "Cierre parcial" else "Revisión del conteo",
                                    tint = egglyticsColors.secondaryContainer) {
                                    Text("Contados: ${fields.count { it.physical != null }} · Omitidos: $omitted")
                                    Text("Diferencias: ${fields.count { it.differs }} · Justificadas: ${fields.count { it.differs && it.reason.isNotBlank() }}")
                                }
                            }
                            item { ReviewGroup("Alimento", feed) { step = CountStep.FEED } }
                            item { ReviewGroup("Caja", cash) { step = CountStep.CASH } }
                            item {
                                CountPanel("Registro de jornada") {
                                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                                        value = form.isClosing, role = Role.Checkbox, onValueChange = onIsClosingChange,
                                    ), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = form.isClosing, onCheckedChange = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Guardar como cierre de jornada")
                                    }
                                    OutlinedTextField(value = form.noteInput, onValueChange = onNoteChange,
                                        label = { Text("Nota (opcional)") }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        if (opening) item {
                            CountPanel("Saldos iniciales ingresados", tint = egglyticsColors.secondaryContainer) {
                                Text("Alimento · ${initialTotal(feed)} kg")
                                Text("Caja · ${initialTotal(cash)} $currency")
                            }
                        }
                        if (opening || step == CountStep.REVIEW) item {
                            form.formError?.let { Text(it, color = egglyticsColors.error) }
                            Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(when {
                                    opening -> "Guardar apertura"
                                    form.isClosing -> "Guardar cierre"
                                    else -> "Guardar conteo"
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class CountStep(val label: String) { FEED("Alimento"), CASH("Caja"), REVIEW("Revisión") }

private data class CountField(
    val label: String, val unit: String, val expected: Long, val raw: String, val reason: String,
    val change: (String) -> Unit, val changeReason: (String) -> Unit,
) {
    val physical = raw.trim().toLongOrNull()?.takeIf { it >= 0 }
    val differs = physical != null && physical != expected
    val difference = physical?.let { it.toBigInteger() - expected.toBigInteger() }
}

private fun initialTotal(fields: List<CountField>): String {
    if (fields.none { it.raw.isNotBlank() } || fields.any { it.raw.isNotBlank() && it.physical == null }) return "—"
    return fields.sumOf { (it.physical ?: 0L).toBigInteger() }.toString()
}

@Composable
private fun CountPanel(title: String, tint: Color = egglyticsColors.surface, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = tint) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun CountInput(field: CountField, opening: Boolean) {
    Text(field.label, fontWeight = FontWeight.SemiBold)
    if (!opening) Text("Esperado: ${field.expected} ${field.unit}", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(value = field.raw, onValueChange = field.change, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        label = { Text(if (opening) "Saldo inicial (${field.unit})" else "Cantidad física (${field.unit})") })
    if (!opening && field.differs) {
        Text("Diferencia: ${field.difference} ${field.unit}", color = egglyticsColors.onSecondaryContainer)
        OutlinedTextField(value = field.reason, onValueChange = field.changeReason, label = { Text("Motivo de la diferencia") },
            supportingText = { Text("Obligatorio cuando hay diferencia") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ReviewGroup(title: String, fields: List<CountField>, edit: () -> Unit) {
    CountPanel(title) {
        fields.forEach { field ->
            val status = when {
                field.raw.isBlank() -> "Omitido"
                field.physical == null -> "Cantidad inválida"
                field.differs -> "Diferencia: ${field.difference} ${field.unit}"
                else -> "Cuadrado"
            }
            Text("${field.label} · $status", fontWeight = FontWeight.SemiBold)
            Text(listOfNotNull("Esperado: ${field.expected} ${field.unit}", field.physical?.let { "Contado: $it ${field.unit}" }).joinToString(" · "))
            if (field.differs) Text(field.reason.ifBlank { "Falta justificar la diferencia" })
        }
        TextButton(onClick = edit) { Text("Editar ${title.lowercase()}") }
    }
}

private fun LazyListScope.closingHistory(state: CountsAndOpeningUiState, currency: String) {
    if (state.closings.isEmpty()) item { Text("Sin cierres registrados.") }
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
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
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
