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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.egglytics.data.repository.ObservedHarvest
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.OperationalDate
import com.egglytics.ui.components.SyncStatusBadge

@Composable
fun HarvestScreen(
    state: HarvestUiState,
    onBack: () -> Unit,
    onHarvestQtyChange: (EggClassification, String) -> Unit,
    onDiscardedChange: (String) -> Unit,
    onHarvestDateChange: (String) -> Unit,
    onHarvestNoteChange: (String) -> Unit,
    onSubmitHarvest: () -> Unit,
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
                    Text(
                        text = "Recolección",
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
                        OutlinedTextField(
                            value = state.form.harvestDateInput,
                            onValueChange = onHarvestDateChange,
                            label = { Text("Fecha operativa (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        EggClassification.entries.chunked(3).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowItems.forEach { classification ->
                                    OutlinedTextField(
                                        value = state.form.harvestInputs[classification].orEmpty(),
                                        onValueChange = { onHarvestQtyChange(classification, it) },
                                        label = { Text(classification.name) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = state.form.discardedInput,
                            onValueChange = onDiscardedChange,
                            label = { Text("Descartados") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.form.harvestNoteInput,
                            onValueChange = onHarvestNoteChange,
                            label = { Text("Nota (opcional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.form.harvestError != null) {
                            Text(
                                text = state.form.harvestError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(
                            onClick = onSubmitHarvest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Guardar recolección")
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Recolecciones (${state.harvests.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.harvestsError != null) {
                    Text(
                        text = state.harvestsError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (state.harvests.isEmpty()) {
                item {
                    Text(
                        text = "Sin recolecciones registradas.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(state.harvests, key = { it.harvest.id }) { item ->
                    HarvestRowCard(item)
                }
            }
        }
    }
}

@Composable
private fun HarvestRowCard(observed: ObservedHarvest) {
    val harvest = observed.harvest
    val summary = EggClassification.entries
        .mapNotNull { cls -> harvest.graded[cls]?.takeIf { it > 0L }?.let { "${cls.name}: $it" } }
        .joinToString(" · ")
        .ifEmpty { "Aptos: 0" }

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
                    text = OperationalDate.format(harvest.fechaOperativa),
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
        }
    }
}
