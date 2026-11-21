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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.egglytics.data.repository.ObservedHarvest
import com.egglytics.data.repository.ObservedSale
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.DeliveryStatus
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.usecase.ActivePriceResult

@Composable
fun MainScreen(
    state: MainUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onHarvestQtyChange: (EggClassification, String) -> Unit,
    onDiscardedChange: (String) -> Unit,
    onHarvestDateChange: (String) -> Unit,
    onHarvestNoteChange: (String) -> Unit,
    onSubmitHarvest: () -> Unit,
    onSaleClassificationChange: (EggClassification) -> Unit,
    onSaleBucketsChange: (String) -> Unit,
    onSaleDateChange: (String) -> Unit,
    onSaleNoteChange: (String) -> Unit,
    onSubmitSale: () -> Unit,
    onMarkSaleDelivered: (String) -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            when (val session = state.sessionState) {
                is SessionState.Unauthenticated,
                is SessionState.Rejected,
                -> {
                    LoginContent(
                        email = state.email,
                        password = state.password,
                        isLoading = state.isLoading,
                        rejectionMessage = (session as? SessionState.Rejected)?.message,
                        onEmailChange = onEmailChange,
                        onPasswordChange = onPasswordChange,
                        onSignIn = onSignIn,
                    )
                }

                is SessionState.Authenticated -> {
                    BusinessAndPricesContent(
                        session = session,
                        state = state,
                        onSignOut = onSignOut,
                        onHarvestQtyChange = onHarvestQtyChange,
                        onDiscardedChange = onDiscardedChange,
                        onHarvestDateChange = onHarvestDateChange,
                        onHarvestNoteChange = onHarvestNoteChange,
                        onSubmitHarvest = onSubmitHarvest,
                        onSaleClassificationChange = onSaleClassificationChange,
                        onSaleBucketsChange = onSaleBucketsChange,
                        onSaleDateChange = onSaleDateChange,
                        onSaleNoteChange = onSaleNoteChange,
                        onSubmitSale = onSubmitSale,
                        onMarkSaleDelivered = onMarkSaleDelivered,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginContent(
    email: String,
    password: String,
    isLoading: Boolean,
    rejectionMessage: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Egglytics",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Correo") },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
        if (rejectionMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = rejectionMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onSignIn,
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            } else {
                Text("Iniciar sesión")
            }
        }
    }
}

@Composable
private fun BusinessAndPricesContent(
    session: SessionState.Authenticated,
    state: MainUiState,
    onSignOut: () -> Unit,
    onHarvestQtyChange: (EggClassification, String) -> Unit,
    onDiscardedChange: (String) -> Unit,
    onHarvestDateChange: (String) -> Unit,
    onHarvestNoteChange: (String) -> Unit,
    onSubmitHarvest: () -> Unit,
    onSaleClassificationChange: (EggClassification) -> Unit,
    onSaleBucketsChange: (String) -> Unit,
    onSaleDateChange: (String) -> Unit,
    onSaleNoteChange: (String) -> Unit,
    onSubmitSale: () -> Unit,
    onMarkSaleDelivered: (String) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.business.name,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "${session.member.name} · ${session.member.role.name}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Inicio: ${OperationalDate.format(session.business.startDate)} · Hoy: ${OperationalDate.format(state.operationalDate)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = onSignOut) {
                    Text("Salir")
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Registrar venta",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        item {
            SaleFormCard(
                currency = session.business.currency,
                state = state,
                onSaleClassificationChange = onSaleClassificationChange,
                onSaleBucketsChange = onSaleBucketsChange,
                onSaleDateChange = onSaleDateChange,
                onSaleNoteChange = onSaleNoteChange,
                onSubmitSale = onSubmitSale,
            )
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
                    currency = session.business.currency,
                    onMarkDelivered = { onMarkSaleDelivered(item.sale.id) },
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Registrar recolección",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        item {
            HarvestFormCard(
                state = state,
                onHarvestQtyChange = onHarvestQtyChange,
                onDiscardedChange = onDiscardedChange,
                onHarvestDateChange = onHarvestDateChange,
                onHarvestNoteChange = onHarvestNoteChange,
                onSubmitHarvest = onSubmitHarvest,
            )
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

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Precios vigentes",
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.pricesError != null) {
                Text(
                    text = state.pricesError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        items(EggClassification.entries) { classification ->
            val result = state.activePrices[classification] ?: ActivePriceResult.NotFound
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = classification.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val label = when (result) {
                        is ActivePriceResult.Found -> "${result.price.pricePerBucket} ${session.business.currency}"
                        ActivePriceResult.NotFound -> "Sin precio vigente"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun SaleFormCard(
    currency: String,
    state: MainUiState,
    onSaleClassificationChange: (EggClassification) -> Unit,
    onSaleBucketsChange: (String) -> Unit,
    onSaleDateChange: (String) -> Unit,
    onSaleNoteChange: (String) -> Unit,
    onSubmitSale: () -> Unit,
) {
    val activePrice = state.activePrices[state.saleClassification] as? ActivePriceResult.Found
    val parsedBuckets = state.saleBucketsInput.trim().toLongOrNull()?.takeIf { it > 0L }

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
                        selected = state.saleClassification == classification,
                        onClick = { onSaleClassificationChange(classification) },
                        label = { Text(classification.name) },
                    )
                }
            }
            val priceLabel = if (activePrice != null) {
                "Precio vigente ${state.saleClassification.name}: ${activePrice.price.pricePerBucket} $currency / cubeta"
            } else {
                "Sin precio vigente para ${state.saleClassification.name}"
            }
            Text(
                text = priceLabel,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = state.saleDateInput,
                onValueChange = onSaleDateChange,
                label = { Text("Fecha venta (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.saleBucketsInput,
                onValueChange = onSaleBucketsChange,
                label = { Text("Cubetas") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.saleNoteInput,
                onValueChange = onSaleNoteChange,
                label = { Text("Nota venta (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (activePrice != null && parsedBuckets != null) {
                val totalPreview = parsedBuckets * activePrice.price.pricePerBucket
                Text(
                    text = "Total estimado: $totalPreview $currency (${parsedBuckets * 30L} huevos)",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.saleError != null) {
                Text(
                    text = state.saleError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = onSubmitSale,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Guardar venta")
            }
        }
    }
}

@Composable
private fun SaleRowCard(
    observed: ObservedSale,
    currency: String,
    onMarkDelivered: () -> Unit,
) {
    val sale = observed.sale
    val line = sale.lines.first()
    val deliveryText = when (sale.deliveryStatus) {
        DeliveryStatus.PENDING -> "Pendiente entrega"
        DeliveryStatus.DELIVERED -> "Entregada"
    }
    val syncText = if (observed.isPendingWrite) "Pendiente" else "Confirmado"

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
                Text(
                    text = syncText,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = "${line.classification.name}: ${line.buckets} cubetas (${line.eggs} huevos) · Total: ${sale.total} $currency",
                style = MaterialTheme.typography.bodyMedium,
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

@Composable
private fun HarvestFormCard(
    state: MainUiState,
    onHarvestQtyChange: (EggClassification, String) -> Unit,
    onDiscardedChange: (String) -> Unit,
    onHarvestDateChange: (String) -> Unit,
    onHarvestNoteChange: (String) -> Unit,
    onSubmitHarvest: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.harvestDateInput,
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
                            value = state.harvestInputs[classification].orEmpty(),
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
                value = state.discardedInput,
                onValueChange = onDiscardedChange,
                label = { Text("Descartados") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.harvestNoteInput,
                onValueChange = onHarvestNoteChange,
                label = { Text("Nota (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.harvestError != null) {
                Text(
                    text = state.harvestError,
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

@Composable
private fun HarvestRowCard(observed: ObservedHarvest) {
    val harvest = observed.harvest
    val summary = EggClassification.entries
        .mapNotNull { cls -> harvest.graded[cls]?.takeIf { it > 0L }?.let { "${cls.name}: $it" } }
        .joinToString(" · ")
        .ifEmpty { "Aptos: 0" }
    val statusText = if (observed.isPendingWrite) "Pendiente" else "Confirmado"
    val statusColor = if (observed.isPendingWrite) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
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
                    text = OperationalDate.format(harvest.fechaOperativa),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.labelMedium,
                )
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
