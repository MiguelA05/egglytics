package com.egglytics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.egglytics.R

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
    var showPassword by remember(isLoading) { mutableStateOf(false) }
    val canSubmit = email.isNotBlank() && password.isNotBlank() && !isLoading

    Scaffold(containerColor = egglyticsColors.background) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)
                .imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier.size(64.dp).background(egglyticsColors.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.login_egg), null, Modifier.size(36.dp), tint = egglyticsColors.primary)
            }
            Spacer(Modifier.height(8.dp))
            Text("Egglytics", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = egglyticsColors.primary)
            Spacer(Modifier.height(24.dp))
            Surface(
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                shape = panelShape,
                shadowElevation = 2.dp,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Acceso a tu cuenta", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        label = { Text("Correo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        singleLine = true,
                        enabled = !isLoading,
                        shape = fieldShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text("Contraseña") },
                        singleLine = true,
                        enabled = !isLoading,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSignIn() }),
                        trailingIcon = {
                            IconToggleButton(
                                checked = showPassword,
                                onCheckedChange = { showPassword = it },
                                enabled = !isLoading,
                            ) {
                                Icon(
                                    painterResource(if (showPassword) R.drawable.login_eye_off else R.drawable.login_eye),
                                    contentDescription = if (showPassword) "Ocultar contraseña" else "Mostrar contraseña",
                                )
                            }
                        },
                        shape = fieldShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (rejectionMessage != null) {
                        Text(
                            rejectionMessage,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = egglyticsColors.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Button(
                        onClick = onSignIn,
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = fieldShape,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isLoading) "Verificando credenciales…" else "Iniciar sesión")
                    }
                }
            }
        }
    }
}
