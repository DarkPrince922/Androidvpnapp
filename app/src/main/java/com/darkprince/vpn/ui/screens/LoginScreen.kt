package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.darkprince.vpn.R
import com.darkprince.vpn.ui.vm.AuthUiState

/**
 * Вход.
 *
 * Почта — основной путь, и она сразу на экране: раньше вход был двумя
 * равными вкладками, причём открывался на Telegram. Человеку без бота это
 * показывало сначала тупик, а завести почту может кто угодно.
 *
 * Telegram остался внизу отдельной кнопкой. Он не хуже — он просто для
 * тех, кто уже пользовался ботом, и таких меньше.
 */
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
    /** Гостевой доступ по присланной ссылке — без камеры и картинок. */
    onSubscriptionLink: (String) -> Unit,
    /** Гость заводит собственный аккаунт, не теряя чужую подписку. */
    upgradeMode: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var registerMode by rememberSaveable { mutableStateOf(upgradeMode) }
    var referralCode by rememberSaveable { mutableStateOf("") }
    var guestSheet by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_wordmark),
            contentDescription = "DarkPrince VPN",
            modifier = Modifier
                .fillMaxWidth(0.88f)
                // соотношение сторон исходника: иначе высота поедет от плотности
                .aspectRatio(1200f / 280f),
        )

        if (upgradeMode) {
            Spacer(Modifier.height(20.dp))
            Text("Свой аккаунт", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Зарегистрируйтесь или войдите, чтобы купить собственную подписку. " +
                    "Доступ, которым с вами поделились, продолжит работать, пока " +
                    "не заработает ваша.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(28.dp))

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

        state.error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        state.info?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(20.dp))
        OrDivider()
        Spacer(Modifier.height(16.dp))

        if (state.waitingTelegram) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                "Откройте Telegram и нажмите «Start», ждём подтверждения…",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onCancelTelegram) { Text("Отмена") }
        } else {
            OutlinedIconButton(
                onClick = onTelegramLogin,
                enabled = !state.loading,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_telegram),
                    contentDescription = "Войти через Telegram",
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Войти через Telegram — если вы уже пользовались нашим ботом",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))

        if (upgradeMode) {
            // подписка по ссылке у гостя уже есть — здесь нужен только аккаунт
            OutlinedButton(
                onClick = { onBack?.invoke() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Вернуться")
            }
        } else {
            // Одна строка вместо двух кнопок с пояснением: чужим доступом
            // пользуется меньшинство, а место занимало это больше всего
            // остального вместе взятого.
            TextButton(onClick = { guestSheet = true }) {
                Text("Вам дали доступ по ссылке или QR?")
            }
        }
    }

    if (guestSheet) {
        GuestAccessSheet(
            loading = state.loading,
            onDismiss = { guestSheet = false },
            onLink = { link ->
                guestSheet = false
                onSubscriptionLink(link)
            },
            onScan = {
                guestSheet = false
                onScanSubscription()
            },
            onPickImage = {
                guestSheet = false
                onPickQrImage()
            },
            onChangeServer = {
                guestSheet = false
                onChangeServer()
            },
        )
    }
}

/**
 * Чужой доступ и настройка адреса — всё, что нужно редко.
 *
 * Ссылка первой: её присылают в сообщении, и до сих пор её приходилось
 * сначала превращать в картинку с кодом, чтобы приложение согласилось её
 * прочитать.
 *
 * Смена адреса кабинета живёт здесь же не для красоты: это единственный
 * выход, если адрес неверный. Экран настроек за входом, и без этой строки
 * человек остался бы заперт — войти не может, поправить негде.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuestAccessSheet(
    loading: Boolean,
    onDismiss: () -> Unit,
    onLink: (String) -> Unit,
    onScan: () -> Unit,
    onPickImage: () -> Unit,
    onChangeServer: () -> Unit,
) {
    var link by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text("Доступ к чужой подписке", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Вставьте присланную ссылку или считайте QR-код владельца. " +
                    "Своя подписка при этом не нужна.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ссылка на подписку") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onLink(link.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && link.isNotBlank(),
            ) {
                Text("Подключить по ссылке")
            }

            Spacer(Modifier.height(16.dp))
            OrDivider()
            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onScan,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            ) {
                Text("Сканировать QR камерой")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onPickImage,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            ) {
                Text("Загрузить QR из галереи")
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            TextButton(onClick = onChangeServer) {
                Text(
                    "Изменить адрес кабинета",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Черта с «или» посередине: отделяет запасной вход от основного. */
@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            "или",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}
