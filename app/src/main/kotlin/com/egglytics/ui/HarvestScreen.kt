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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.egglytics.R
import com.egglytics.domain.model.SaleLine
import java.math.BigInteger
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egglytics.data.repository.ObservedHarvest
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.OperationalDate
import com.egglytics.ui.components.SyncStatusBadge
import com.egglytics.ui.components.VoidReasonDialog

@Composable
internal fun HarvestRoute(
    session: SessionState.Authenticated,
    harvestViewModel: HarvestViewModel,
    onBack: () -> Unit,
) {
    val harvestUi by harvestViewModel.uiState.collectAsStateWithLifecycle()
    HarvestScreen(
        businessName = session.business.name,
        state = harvestUi,
        onBack = onBack,
        onHarvestQtyChange = harvestViewModel::onHarvestQtyChange,
        onDiscardedChange = harvestViewModel::onDiscardedChange,
        onHarvestDateChange = harvestViewModel::onHarvestDateChange,
        onHarvestNoteChange = harvestViewModel::onHarvestNoteChange,
        onSubmitHarvest = { harvestViewModel.submitHarvest(session) },
        onVoidHarvest = { item, reason ->
            harvestViewModel.voidHarvest(session, item, reason)
        },
    )
}

@Composable
fun HarvestScreen(
    businessName: String,
    state: HarvestUiState,
    onBack: () -> Unit,
    onHarvestQtyChange: (EggClassification, String) -> Unit,
    onDiscardedChange: (String) -> Unit,
    onHarvestDateChange: (String) -> Unit,
    onHarvestNoteChange: (String) -> Unit,
    onSubmitHarvest: () -> Unit,
    onVoidHarvest: (ObservedHarvest, String) -> Unit,
) {
    var harvestToVoid by remember { mutableStateOf<ObservedHarvest?>(null) }

    var showHistory by rememberSaveable { mutableStateOf(false) }
    val target = harvestToVoid
    if (target != null) {
        VoidReasonDialog(
            title = "Anular recolección",
            description = "Se registrará un reverso para la recolección del ${OperationalDate.format(target.harvest.fechaOperativa)}.",
            onDismiss = { harvestToVoid = null },
            onConfirm = { reason ->
                harvestToVoid = null
                onVoidHarvest(target, reason)
            },
        )
    }
    Scaffold(containerColor = egglyticsColors.background) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding()) {
            ModuleHeader("Recolección", businessName, onBack)
            TabRow(selectedTabIndex = if (showHistory) 1 else 0, containerColor = egglyticsColors.background) {
                Tab(selected = !showHistory, onClick = { showHistory = false }, text = { Text("Registro") })
                Tab(selected = showHistory, onClick = { showHistory = true }, text = { Text("Historial (${state.harvests.size})") })
            }
            if (showHistory) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.harvestsError?.let { item { Text(it, color = egglyticsColors.error) } }
                    if (state.harvests.isEmpty() && state.harvestsError == null) {
                        item {
                            Surface(shape = panelShape, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(painterResource(R.drawable.login_egg), null, tint = egglyticsColors.primary)
                                    Text("Sin recolecciones registradas", style = MaterialTheme.typography.titleMedium)
                                    Button(onClick = { showHistory = false }) { Text("Registrar primera recolección") }
                                }
                            }
                        }
                    }
                    items(state.harvests, key = { it.harvest.id }) { item ->
                        val reason = state.reversedHarvestReasons[item.harvest.id]
                        HarvestRowCard(item, reason, state.canVoid && !item.isPendingWrite && reason == null,
                            item.harvest.id in state.voidingHarvestIds, onVoidClick = { harvestToVoid = item })
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        CalendarField("Fecha operativa", state.form.harvestDateInput, onHarvestDateChange)
                    }
                    item {
                        SectionPanel("Huevos por clasificación") {
                            EggClassification.entries.forEach { classification ->
                                QuantityField(classification.name, state.form.harvestInputs[classification].orEmpty()) {
                                    onHarvestQtyChange(classification, it)
                                }
                            }
                            HorizontalDivider()
                            val summary = summarizeGradedEggs(state.form.harvestInputs)
                            Text("Total de aptos", style = MaterialTheme.typography.labelLarge)
                            val totalLabel = when (val total = summary?.total) {
                                null -> "Revisa las cantidades"
                                BigInteger.ONE -> "1 huevo"
                                else -> "$total huevos"
                            }
                            Text(totalLabel,
                                color = egglyticsColors.primary, style = MaterialTheme.typography.headlineSmall)
                            if (summary != null) {
                                Text("Cubetas completas: ${summary.fullBuckets} · Huevos sueltos: ${summary.looseEggs}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        SectionPanel("Huevos descartados", tint = egglyticsColors.secondaryContainer) {
                            QuantityField("Descartados", state.form.discardedInput, onDiscardedChange)
                        }
                    }
                    item {
                        OutlinedTextField(value = state.form.harvestNoteInput, onValueChange = onHarvestNoteChange,
                            label = { Text("Nota (opcional)") }, modifier = Modifier.fillMaxWidth(), shape = fieldShape)
                    }
                    item {
                        state.form.harvestError?.let { Text(it, color = egglyticsColors.error) }
                        Button(onClick = onSubmitHarvest, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = panelShape) {
                            Text("Guardar recolección")
                        }
                    }
                }
            }
        }
    }
}

internal data class GradedEggSummary(val fullBuckets: BigInteger, val looseEggs: Long) {
    val total = fullBuckets * SaleLine.EGGS_PER_BUCKET.toBigInteger() + looseEggs.toBigInteger()
}

internal fun summarizeGradedEggs(inputs: Map<EggClassification, String>): GradedEggSummary? {
    var fullBuckets = BigInteger.ZERO
    var looseEggs = 0L
    for (raw in inputs.values) {
        if (raw.isBlank()) continue
        val count = raw.trim().toLongOrNull()?.takeIf { it >= 0 } ?: return null
        fullBuckets += (count / SaleLine.EGGS_PER_BUCKET).toBigInteger()
        looseEggs += count % SaleLine.EGGS_PER_BUCKET
    }
    return GradedEggSummary(fullBuckets, looseEggs)
}

@Composable
private fun QuantityField(label: String, value: String, onChange: (String) -> Unit) {
    val count = if (value.isBlank()) 0L else value.trim().toLongOrNull()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f), shape = fieldShape,
        )
        IconButton(enabled = count != null && count > 0, onClick = { count?.let { onChange((it - 1).toString()) } },
            modifier = Modifier.semantics { contentDescription = "Restar un huevo: $label" }) { Text("−") }
        IconButton(enabled = count != null && count in 0 until Long.MAX_VALUE, onClick = { count?.let { onChange((it + 1).toString()) } },
            modifier = Modifier.semantics { contentDescription = "Sumar un huevo: $label" }) { Text("+") }
    }
}

@Composable
private fun HarvestRowCard(
    observed: ObservedHarvest,
    voidReason: String?,
    canVoid: Boolean,
    isVoiding: Boolean,
    onVoidClick: () -> Unit,
) {
    val harvest = observed.harvest
    val summary = EggClassification.entries
        .mapNotNull { cls -> harvest.graded[cls]?.takeIf { it > 0L }?.let { "${cls.name}: $it" } }
        .joinToString(" · ")
        .ifEmpty { "Aptos: 0" }

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
                val dateLabel = OperationalDate.format(harvest.fechaOperativa)
                Text(
                    text = if (voidReason != null) "$dateLabel · Anulada" else dateLabel,
                    style = MaterialTheme.typography.titleSmall,
                )
                SyncStatusBadge(isPendingWrite = observed.isPendingWrite)
            }
            Text(
                text = "$summary · Descartados: ${harvest.discarded}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!harvest.note.isNullOrBlank()) {
                Text(
                    text = harvest.note.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (voidReason != null) {
                Text(
                    text = "Anulada · Motivo: $voidReason",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (canVoid) {
                OutlinedButton(
                    onClick = onVoidClick,
                    enabled = !isVoiding,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isVoiding) "Anulando..." else "Anular recolección")
                }
            }
        }
    }
}
