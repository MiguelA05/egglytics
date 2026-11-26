package com.egglytics.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.egglytics.domain.model.OperationalDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = modifier.fillMaxWidth(), shape = fieldShape) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (open) {
        val initial = runCatching { OperationalDate.parse(value) }.getOrElse { LocalDate.now(OperationalDate.BOGOTA_ZONE) }
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = {
            TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let {
                    onChange(OperationalDate.format(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()))
                }
                open = false
            }) { Text("Aceptar") }
        }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } }) {
            DatePicker(state = picker, showModeToggle = false)
        }
    }
}
