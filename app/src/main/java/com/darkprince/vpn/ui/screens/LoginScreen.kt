package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.darkprince.vpn.R
import com.darkprince.vpn.ui.vm.AuthUiState

@Composable
fun LoginScreen(
    state: AuthUiState,
    onTelegramLogin: () -> Unit,
    onCancelTelegram: () -> Unit,
    onEmailLogin: (String, String) -> Unit,
    onEmailRegister: (String, String, String?) -> Unit,
    onForgotPassword: (String) -> Unit,
    onChangeServer: () -> Unit,
    onScanSubscription: () -> Unit,
    onPickQrImage: () -> Unit,
    /** Гость заводит собственный аккаунт, не теряя чужую подписку. */
    upgradeMode: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var registerMode by rememberSaveable { mutableStateOf(upgradeMode) }
    var referralCode by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_mark),
            contentDescription = null,
            modifier = Modifier
                .size(148.dp)
                .clip(RoundedCornerShape(32.dp)),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            if (upgradeMode) "Свой аккаунт" else "Вход",
            style = MaterialTheme.typography.headlineMedium,
        )
        if (upgradeMode) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Зарегистрируйтесь или войдите, чтобы купить собственную подписку. " +
                    "Доступ, которым с вами поделились, продолжит работать, пока " +
                    "не заработает ваша.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(24.dp))

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Telegram") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Почта") })
        }
        Spacer(Modifier.height(24.dp))

        if (tab == 0) {
            if (state.waitingTelegram) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    "Откройте Telegram и нажмите «Start», ждём подтверждения…",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onCancelTelegram) { Text("Отмена") }
            } else {
                Button(
                    onClick = onTelegramLogin,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading,
                ) {
                    Text("Войти через Telegram")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Подойдёт, если вы уже пользовались нашим ботом в Telegram — подписка и баланс подтянутся автоматически.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("E-mail") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            if (registerMode) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = referralCode,
                    onValueChange = { referralCode = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Реферальный код (необязательно)") },
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (registerMode) {
                        onEmailRegister(email, password, referralCode.takeIf { it.isNotBlank() })
                    } else {
                        onEmailLogin(email, password)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading && email.contains('@') && password.length >= 6,
            ) {
                Text(if (registerMode) "Зарегистрироваться" else "Войти")
            }
            TextButton(onClick = { registerMode = !registerMode }) {
                Text(if (registerMode) "Уже есть аккаунт? Войти" else "Нет аккаунта? Зарегистрируйтесь")
            }
            if (!registerMode) {
                TextButton(
                    onClick = { if (email.contains('@')) onForgotPassword(email) },
                ) {
                    Text("Забыли пароль?")
                }
            }
        }

        state.error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        state.info?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        if (upgradeMode) {
            // подписка по QR у гостя уже есть — здесь нужен только аккаунт
            OutlinedButton(
                onClick = { onBack?.invoke() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Вернуться")
            }
        } else {
            OutlinedButton(
                onClick = onScanSubscription,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading,
            ) {
                Text("Сканировать QR камерой")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onPickQrImage,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading,
            ) {
                Text("Загрузить QR из галереи")
            }
            Text(
                "Если вам дали доступ к подписке — отсканируйте QR владельца или " +
                    "выберите присланную картинку",
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onChangeServer) { Text("Изменить адрес кабинета") }
        }
    }
}
