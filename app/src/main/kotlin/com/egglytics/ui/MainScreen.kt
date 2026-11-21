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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.egglytics.data.repository.SessionState
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
) {
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

    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Precios vigentes",
        style = MaterialTheme.typography.titleMedium,
    )

    if (state.pricesError != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.pricesError,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
