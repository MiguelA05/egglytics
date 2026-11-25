package com.egglytics.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.egglytics.domain.model.KG_PER_FEED_BAG
import java.time.Instant
import java.time.LocalDate
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.FeedIngredient
import com.egglytics.domain.model.FeedLossType
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.ReversalTargetCollection
import com.egglytics.ui.components.SyncStatusBadge
import com.egglytics.ui.components.VoidReasonDialog

@Composable
internal fun FeedRoute(
    session: SessionState.Authenticated,
    feedViewModel: FeedViewModel,
    onBack: () -> Unit,
) {
    val feedUi by feedViewModel.uiState.collectAsStateWithLifecycle()
    FeedScreen(
        businessName = session.business.name,
        currency = session.business.currency,
        state = feedUi,
        onBack = onBack,
        onModeChange = feedViewModel::onModeChange,
        onIngredientChange = feedViewModel::onIngredientChange,
        onBagsChange = feedViewModel::onBagsChange,
        onTotalCostChange = feedViewModel::onTotalCostChange,
        onMethodSelect = feedViewModel::onMethodSelect,
        onSupplierChange = feedViewModel::onSupplierChange,
        onLossKgChange = feedViewModel::onLossKgChange,
        onLossTypeChange = feedViewModel::onLossTypeChange,
        onLossReasonChange = feedViewModel::onLossReasonChange,
        onDateChange = feedViewModel::onDateChange,
        onNoteChange = feedViewModel::onNoteChange,
        onPeriodStartChange = feedViewModel::onPeriodStartChange,
        onPeriodEndChange = feedViewModel::onPeriodEndChange,
        onSubmit = { feedViewModel.submit(session) },
        onVoidMovement = { col, id, reason ->
            feedViewModel.voidFeedMovement(session, col, id, reason)
        },
    )
}

@Composable
fun FeedScreen(
    businessName: String,
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
    var tab by rememberSaveable { mutableStateOf(FeedTab.REGISTER) }
    var filter by rememberSaveable { mutableStateOf<FeedIngredient?>(null) }
    var pendingVoid by remember { mutableStateOf<FeedHistoryEntry?>(null) }
    val movements = remember(state.purchases, state.depletions, state.losses, state.paymentMethods, currency) {
        historyEntries(state, currency)
    }
    val form = state.form
    val weight = when (form.mode) {
        FeedFormMode.LOSS -> form.lossKgInput.trim().toLongOrNull()?.takeIf { it > 0 }
        else -> FeedViewModel.previewKg(form.bagsInput)
    }
    val purchase = form.mode == FeedFormMode.PURCHASE

    MaterialTheme(colorScheme = egglyticsColors) {
        pendingVoid?.let { target ->
            VoidReasonDialog(
                title = "Anular movimiento",
                description = "${target.title} del ${OperationalDate.format(target.date)} · ${target.detail}",
                onDismiss = { pendingVoid = null },
                onConfirm = { reason ->
                    onVoidMovement(target.collection, target.id, reason)
                    pendingVoid = null
                },
            )
        }
        Scaffold(containerColor = egglyticsColors.background) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Alimento", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(businessName, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onBack) { Text("Volver") }
                }
                TabRow(selectedTabIndex = tab.ordinal, containerColor = egglyticsColors.background) {
                    FeedTab.entries.forEach { page ->
                        Tab(selected = tab == page, onClick = { tab = page }, text = { Text(page.label) })
                    }
                }
                key(tab) {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.feedError?.let { item { Text(it, color = egglyticsColors.error) } }
                        when (tab) {
                            FeedTab.REGISTER -> {
                                item {
                                    FeedPanel("Tipo de movimiento") {
                                        Choices(FeedFormMode.entries, form.mode, onModeChange) { it.label() }
                                        Text("Ingrediente", style = MaterialTheme.typography.labelLarge)
                                        Choices(FeedIngredient.entries, form.ingredient, onIngredientChange, ::ingredientLabel)
                                        Field("Fecha operativa (YYYY-MM-DD)", form.dateInput, onDateChange)
                                    }
                                }
                                item {
                                    FeedPanel(if (purchase) "Entrada por compra" else "Salida de alimento") {
                                        if (form.mode == FeedFormMode.LOSS) {
                                            Field("Kg perdidos", form.lossKgInput, onLossKgChange, numeric = true)
                                            Text("Tipo de pérdida", style = MaterialTheme.typography.labelLarge)
                                            Choices(FeedLossType.entries, form.lossType, onLossTypeChange) { it.label() }
                                            Field("Motivo (obligatorio)", form.lossReasonInput, onLossReasonChange)
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Field("Bultos de $KG_PER_FEED_BAG kg", form.bagsInput, onBagsChange, Modifier.weight(1f), numeric = true)
                                                val n = if (form.bagsInput.isBlank()) 0L else form.bagsInput.trim().toLongOrNull()
                                                StepButton("Restar un bulto", "−", n != null && n > 0) { n?.let { onBagsChange((it - 1).toString()) } }
                                                StepButton("Sumar un bulto", "+", n != null && n in 0 until Long.MAX_VALUE / KG_PER_FEED_BAG) { n?.let { onBagsChange((it + 1).toString()) } }
                                            }
                                            if (purchase) {
                                                Field("Costo total ($currency)", form.totalCostInput, onTotalCostChange, numeric = true)
                                                Text("Forma de pago", style = MaterialTheme.typography.labelLarge)
                                                if (state.paymentMethods.isEmpty()) Text("No hay formas de pago activas.", color = egglyticsColors.error)
                                                Choices(state.paymentMethods, state.paymentMethods.find { it.id == form.selectedMethodId },
                                                    { onMethodSelect(it.id) }) { it.name }
                                                Field("Proveedor (opcional)", form.supplierInput, onSupplierChange)
                                            }
                                        }
                                        Field("Nota (opcional)", form.noteInput, onNoteChange)
                                    }
                                }
                                item {
                                    Surface(color = egglyticsColors.secondaryContainer, shape = panelShape) {
                                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Efecto en el saldo", style = MaterialTheme.typography.labelLarge)
                                            val sign = if (purchase) "+" else "−"
                                            Text(weight?.let { "$sign$it kg" } ?: "—", style = MaterialTheme.typography.headlineSmall)
                                            Text("${form.mode.label()} · ${ingredientLabel(form.ingredient)}")
                                            if (purchase) {
                                                val cost = form.totalCostInput.trim().toLongOrNull()
                                                val method = state.paymentMethods.find { it.id == form.selectedMethodId }
                                                if (cost != null && cost > 0) {
                                                    val amount = NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CO")).format(cost)
                                                    Text(listOfNotNull("$amount $currency", method?.name).joinToString(" · "))
                                                }
                                            }
                                        }
                                    }
                                }
                                item {
                                    form.formError?.let { Text(it, color = egglyticsColors.error) }
                                    Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        shape = panelShape) { Text("Guardar registro") }
                                }
                            }
                            FeedTab.SUMMARY -> {
                                item {
                                    FeedPanel("Periodo de consumo") {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Field("Desde", form.periodStartInput, onPeriodStartChange, Modifier.weight(1f))
                                            Field("Hasta", form.periodEndInput, onPeriodEndChange, Modifier.weight(1f))
                                        }
                                    }
                                }
                                items(FeedIngredient.entries) { ingredient ->
                                    val summary = state.summaries[ingredient]
                                    FeedPanel(ingredientLabel(ingredient)) {
                                        Text("Saldo esperado", style = MaterialTheme.typography.labelLarge)
                                        Text(summary?.let { "${it.balanceKg} kg" } ?: "Sin datos",
                                            style = MaterialTheme.typography.headlineMedium, color = egglyticsColors.primary)
                                        Text("Aproximado con un bulto abierto", style = MaterialTheme.typography.bodySmall)
                                        HorizontalDivider()
                                        Text(when {
                                            summary == null -> "Sin base inicial"
                                            !summary.hasBase -> "Falta apertura o conteo base (usando 0 kg)"
                                            else -> summary.baseSourceLabel?.let { "Base: $it" } ?: "Base configurada"
                                        })
                                        Text("Consumo diario estimado" + (summary?.let { " (${it.inclusiveDays} días)" } ?: ""))
                                        Text(summary?.formattedDailyConsumption() ?: "Sin datos", fontWeight = FontWeight.Bold)
                                    }
                                }
                                item { Button(onClick = { tab = FeedTab.REGISTER }) { Text("Registrar movimiento") } }
                            }
                            FeedTab.HISTORY -> {
                                val visible = movements.filter { filter == null || it.ingredient == filter }
                                item {
                                    FeedPanel("Movimientos (${visible.size})") {
                                        Choices(listOf(null) + FeedIngredient.entries, filter, { filter = it }) {
                                            it?.let(::ingredientLabel) ?: "Todos"
                                        }
                                    }
                                }
                                if (visible.isEmpty() && state.feedError == null) {
                                    item { FeedPanel("Sin movimientos registrados") {
                                        Button(onClick = { tab = FeedTab.REGISTER }) { Text("Registrar movimiento") }
                                    } }
                                }
                                items(visible, key = { it.key }) { entry ->
                                    val reason = state.reversedReasons[entry.key]
                                    FeedPanel(entry.title) {
                                        Text(OperationalDate.format(entry.date), style = MaterialTheme.typography.bodySmall)
                                        Text(entry.detail, fontWeight = FontWeight.Bold)
                                        entry.extra?.let { Text(it) }
                                        entry.note?.takeIf { it.isNotBlank() }?.let { Text(it) }
                                        SyncStatusBadge(isPendingWrite = entry.pending)
                                        if (reason != null) Text("Anulado · Motivo: $reason", color = egglyticsColors.error)
                                        else if (state.isAdmin) TextButton(onClick = { pendingVoid = entry }) { Text("Anular") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class FeedTab(val label: String) { SUMMARY("Resumen"), REGISTER("Registro"), HISTORY("Historial") }
private val panelShape = RoundedCornerShape(16.dp)

@Composable
private fun FeedPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = panelShape) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun Field(label: String, value: String, change: (String) -> Unit, modifier: Modifier = Modifier, numeric: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = change, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
}

@Composable
private fun StepButton(description: String, symbol: String, enabled: Boolean, click: () -> Unit) {
    IconButton(onClick = click, enabled = enabled, modifier = Modifier.semantics { contentDescription = description }) { Text(symbol) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(options: List<T>, selected: T?, change: (T) -> Unit, label: (T) -> String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { change(option) }, label = { Text(label(option)) })
        }
    }
}

private data class FeedHistoryEntry(
    val id: String, val date: LocalDate, val created: Instant, val collection: ReversalTargetCollection,
    val ingredient: FeedIngredient, val mode: FeedFormMode, val detail: String, val extra: String?,
    val note: String?, val pending: Boolean,
) {
    val key = "${collection.firestoreValue}_$id"
    val title = "${mode.label()} · ${ingredientLabel(ingredient)}"
}

private fun historyEntries(state: FeedUiState, currency: String): List<FeedHistoryEntry> = buildList {
    val money = NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CO"))
    state.purchases.forEach { (p, pending) ->
        val method = state.paymentMethods.find { it.id == p.methodId }?.name ?: p.methodId
        add(FeedHistoryEntry(p.id, p.fechaOperativa, p.createdAt, ReversalTargetCollection.FEED_PURCHASES,
            p.ingredient, FeedFormMode.PURCHASE, "+${p.totalKg} kg · ${money.format(p.totalCost)} $currency",
            listOfNotNull("${p.bags} bultos · $method", p.supplier?.takeIf { it.isNotBlank() }).joinToString(" · "), p.note, pending))
    }
    state.depletions.forEach { (d, pending) ->
        add(FeedHistoryEntry(d.id, d.fechaOperativa, d.createdAt, ReversalTargetCollection.FEED_DEPLETIONS,
            d.ingredient, FeedFormMode.DEPLETION, "−${d.kg} kg · ${d.bags} bultos", null, d.note, pending))
    }
    state.losses.forEach { (l, pending) ->
        add(FeedHistoryEntry(l.id, l.fechaOperativa, l.createdAt, ReversalTargetCollection.FEED_LOSSES,
            l.ingredient, FeedFormMode.LOSS, "−${l.kg} kg", "${l.type.label()} · ${l.reason}", l.note, pending))
    }
}.sortedWith(compareByDescending<FeedHistoryEntry> { it.date }.thenByDescending { it.created }.thenBy { it.key })

private fun FeedFormMode.label() = when (this) {
    FeedFormMode.PURCHASE -> "Compra"
    FeedFormMode.DEPLETION -> "Agotado"
    FeedFormMode.LOSS -> "Pérdida"
}
private fun FeedLossType.label() = when (this) {
    FeedLossType.WASTE -> "Desperdicio"
    FeedLossType.LOSS -> "Pérdida"
    FeedLossType.OTHER -> "Otro"
}

private fun ingredientLabel(ingredient: FeedIngredient): String = when (ingredient) {
    FeedIngredient.CONCENTRATE -> "Concentrado"
    FeedIngredient.CORN_BRAN -> "Salvado de maíz"
}
