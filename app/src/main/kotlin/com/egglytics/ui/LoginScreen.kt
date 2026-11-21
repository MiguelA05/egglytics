package com.egglytics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    email: String,
    password: String,
    isLoading: Boolean,
    rejectionMessage: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
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
}
