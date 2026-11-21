package com.egglytics.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SyncStatusBadge(isPendingWrite: Boolean) {
    val label = if (isPendingWrite) "Pendiente" else "Confirmado"
    val color = if (isPendingWrite) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelMedium,
    )
}
