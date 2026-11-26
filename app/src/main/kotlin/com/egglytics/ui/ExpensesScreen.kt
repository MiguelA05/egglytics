package com.egglytics.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import java.time.Instant
import java.time.LocalDate
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.ReversalTargetCollection
import com.egglytics.ui.components.SyncStatusBadge
import com.egglytics.ui.components.VoidReasonDialog

@Composable
internal fun ExpensesRoute(
    session: SessionState.Authenticated,
    expensesViewModel: ExpensesViewModel,
    onBack: () -> Unit,
) {
    val expensesUi by expensesViewModel.uiState.collectAsStateWithLifecycle()
    ExpensesScreen(
        businessName = session.business.name,
        currency = session.business.currency,
        state = expensesUi,
        onBack = onBack,
        onModeChange = expensesViewModel::onModeChange,
        onAmountChange = expensesViewModel::onAmountChange,
        onCategorySelect = expensesViewModel::onCategorySelect,
        onMethodSelect = expensesViewModel::onMethodSelect,
        onDateChange = expensesViewModel::onDateChange,
        onDescriptionChange = expensesViewModel::onDescriptionChange,
        onNoteChange = expensesViewModel::onNoteChange,
        onSubmit = { expensesViewModel.submit(session) },
        onVoidMovement = { col, id, reason ->
            expensesViewModel.voidMovement(session, col, id, reason)
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpensesScreen(
    businessName: String,
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
    val form = state.form
    val expense = form.mode == ExpenseFormMode.EXPENSE
    val money = remember { NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CO")) }
    val category = state.categories.find { it.id == form.selectedCategoryId }?.name
    val method = state.paymentMethods.find { it.id == form.selectedMethodId }?.name
    val amount = form.amountInput.trim().toLongOrNull()?.takeIf { it > 0 }
    var history by rememberSaveable { mutableStateOf(false) }
    var pendingVoid by remember { mutableStateOf<OutflowRow?>(null) }

    pendingVoid?.let { row ->
        VoidReasonDialog(
            title = if (row.collection == ReversalTargetCollection.EXPENSES) "Anular gasto" else "Anular retiro",
            description = "${row.title} del ${OperationalDate.format(row.date)} · ${money.format(row.amount)} $currency · ${row.method}",
            onDismiss = { pendingVoid = null },
            onConfirm = { reason ->
                onVoidMovement(row.collection, row.id, reason)
                pendingVoid = null
            },
        )
    }
    Scaffold(containerColor = egglyticsColors.background) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding()) {
            ModuleHeader("Gastos y retiros", businessName, onBack)
            TabRow(selectedTabIndex = if (history) 1 else 0, containerColor = egglyticsColors.background) {
                Tab(selected = !history, onClick = { history = false }, text = { Text("Registro") })
                Tab(selected = history, onClick = { history = true }, text = { Text("Historial") })
            }
            if (history) {
                val rows = remember(state.expenses, state.withdrawals, state.categories, state.paymentMethods) {
                    outflows(state)
                }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        SectionPanel("Salidas registradas") {
                            if (state.historyReady) {
                                Text("Gastos (${state.expenses.size}) · Retiros (${state.withdrawals.size})")
                                val active = rows.filter { it.key !in state.reversedReasons }
                                val expenses = active.filter { it.collection == ReversalTargetCollection.EXPENSES }.sumOf { it.amount.toBigInteger() }
                                val withdrawals = active.filter { it.collection == ReversalTargetCollection.WITHDRAWALS }.sumOf { it.amount.toBigInteger() }
                                Text("Gastos operativos · ${money.format(expenses)} $currency")
                                Text("Retiros personales · ${money.format(withdrawals)} $currency")
                                HorizontalDivider()
                                Text("Total de salidas · ${money.format(expenses + withdrawals)} $currency", style = MaterialTheme.typography.titleLarge)
                                if (active.size < rows.size) Text("Los importes excluyen movimientos anulados.", style = MaterialTheme.typography.bodySmall)
                                if (active.any { it.pending }) Text("Incluye salidas pendientes de sincronización.", style = MaterialTheme.typography.bodySmall)
                            } else if (state.error == null) Text("Cargando movimientos…")
                            state.error?.let { Text(it, color = egglyticsColors.error) }
                        }
                    }
                    if (rows.isEmpty() && state.historyReady) item {
                        SectionPanel("Sin movimientos registrados") {
                            TextButton(onClick = { history = false }) { Text("Registrar movimiento") }
                        }
                    }
                    items(rows, key = { it.key }) { row ->
                        val reason = state.reversedReasons[row.key]
                        SectionPanel(row.title) {
                            Text(OperationalDate.format(row.date), style = MaterialTheme.typography.bodySmall)
                            Text("${if (reason == null) "−" else ""}${money.format(row.amount)} $currency", style = MaterialTheme.typography.titleLarge)
                            Text("Forma de pago · ${row.method}")
                            row.description?.takeIf(String::isNotBlank)?.let { Text(it) }
                            row.note?.takeIf(String::isNotBlank)?.let { Text("Nota: $it") }
                            SyncStatusBadge(isPendingWrite = row.pending)
                            if (reason != null) Text("Anulado · Motivo: $reason", color = egglyticsColors.error)
                            else if (state.isAdmin && state.historyReady) TextButton(onClick = { pendingVoid = row }) { Text("Anular") }
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        SectionPanel("Tipo de movimiento") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = expense, onClick = { onModeChange(ExpenseFormMode.EXPENSE) }, label = { Text("Gasto operativo") })
                                FilterChip(selected = !expense, onClick = { onModeChange(ExpenseFormMode.WITHDRAWAL) }, label = { Text("Retiro personal") })
                            }
                            CalendarField("Fecha operativa", form.dateInput, onDateChange)
                            CaptureField("Monto ($currency)", form.amountInput, onAmountChange, numeric = true)
                        }
                    }
                    if (expense) item {
                        SectionPanel("Categoría de gasto") {
                            if (state.categories.isEmpty()) Text("No hay categorías activas.", color = egglyticsColors.error)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.categories.forEach { option ->
                                    FilterChip(selected = option.id == form.selectedCategoryId, onClick = { onCategorySelect(option.id) },
                                        label = { Text(option.name) })
                                }
                            }
                            CaptureField("Descripción (opcional)", form.descriptionInput, onDescriptionChange)
                        }
                    }
                    item {
                        SectionPanel("Forma de pago") {
                            if (state.paymentMethods.isEmpty()) Text("No hay formas de pago activas.", color = egglyticsColors.error)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.paymentMethods.forEach { option ->
                                    FilterChip(selected = option.id == form.selectedMethodId, onClick = { onMethodSelect(option.id) },
                                        label = { Text(option.name) })
                                }
                            }
                            CaptureField("Nota (opcional)", form.noteInput, onNoteChange)
                        }
                    }
                    item {
                        Surface(color = egglyticsColors.secondaryContainer, shape = panelShape) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Salida de caja", style = MaterialTheme.typography.labelLarge)
                                Text(amount?.let { "−${money.format(it)} $currency" } ?: "—", style = MaterialTheme.typography.headlineMedium)
                                Text(listOfNotNull(if (expense) "Gasto operativo" else "Retiro personal", category.takeIf { expense }, method).joinToString(" · "))
                            }
                        }
                    }
                    item {
                        form.formError?.let { Text(it, color = egglyticsColors.error) }
                        state.error?.let { Text(it, color = egglyticsColors.error) }
                        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = panelShape) {
                            Text(if (expense) "Guardar gasto" else "Guardar retiro")
                        }
                    }
                }
            }
        }
    }
}


private data class OutflowRow(
    val collection: ReversalTargetCollection, val id: String, val date: LocalDate, val createdAt: Instant,
    val title: String, val amount: Long, val method: String, val description: String?, val note: String?, val pending: Boolean,
) {
    val key = "${collection.firestoreValue}_$id"
}

private fun outflows(state: ExpensesUiState): List<OutflowRow> = buildList {
    val categories = state.categories.associate { it.id to it.name }
    val methods = state.paymentMethods.associate { it.id to it.name }
    state.expenses.forEach { (e, pending) ->
        add(OutflowRow(ReversalTargetCollection.EXPENSES, e.id, e.fechaOperativa, e.createdAt,
            "Gasto · ${categories[e.categoryId] ?: e.categoryId}", e.amount, methods[e.methodId] ?: e.methodId,
            e.description, e.note, pending))
    }
    state.withdrawals.forEach { (w, pending) ->
        add(OutflowRow(ReversalTargetCollection.WITHDRAWALS, w.id, w.fechaOperativa, w.createdAt,
            "Retiro personal", w.amount, methods[w.methodId] ?: w.methodId, null, w.note, pending))
    }
}.sortedWith(compareByDescending<OutflowRow> { it.date }.thenByDescending { it.createdAt }.thenBy { it.key })
