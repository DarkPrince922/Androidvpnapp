package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.data.api.dto.MergePreviewUserDto
import com.darkprince.vpn.ui.theme.GroupCard
import com.darkprince.vpn.ui.theme.ServiceDownBanner
import com.darkprince.vpn.ui.theme.serviceDown
import com.darkprince.vpn.ui.vm.EmailLinkStep
import com.darkprince.vpn.ui.vm.EmailLinkViewModel

/**
 * Привязка почты к аккаунту.
 *
 * Нужна тем, кто вошёл через Telegram: с почтой в тот же аккаунт можно войти
 * и без Telegram — с компьютера, с сайта, если Telegram недоступен.
 *
 * Если почта уже занята другим аккаунтом (например, человек когда-то
 * зарегистрировался по почте отдельно), бот предлагает их объединить: шлёт
 * код на эту почту — так он убеждается, что почта правда ваша, — и
 * спрашивает, чью подписку оставить.
 */
@Composable
fun EmailLinkScreen(viewModel: EmailLinkViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val down = serviceDown()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Привязать почту", style = MaterialTheme.typography.headlineSmall)
        ServiceDownBanner("Привязать почту можно будет, когда сервис вернётся.")

        when (val step = state.step) {
            EmailLinkStep.Form -> EmailForm(
                busy = state.busy || down,
                onSubmit = viewModel::submit,
            )

            is EmailLinkStep.Sent -> Note(
                title = if (step.linkedNow) "Почта привязана" else "Проверьте почту",
                text = if (step.linkedNow) {
                    "Теперь в этот аккаунт можно входить и по адресу ${step.email} с паролем."
                } else {
                    "Мы отправили письмо на ${step.email}. Перейдите по ссылке из него — после " +
                        "этого в этот аккаунт можно будет входить и по почте с паролем. " +
                        "Письма нет — проверьте «Спам»."
                },
                action = "Готово",
                onAction = onDone,
            )

            is EmailLinkStep.Code -> CodeForm(
                email = step.email,
                busy = state.busy || down,
                onSubmit = viewModel::confirmCode,
                onRestart = viewModel::restart,
            )

            is EmailLinkStep.Choose -> ChooseSubscription(
                current = step.preview.primary,
                other = step.preview.secondary,
                busy = state.busy || down,
                onMerge = viewModel::merge,
            )

            EmailLinkStep.Merged -> Note(
                title = "Аккаунты объединены",
                text = "Теперь это один аккаунт: в него можно входить и через Telegram, и по почте.",
                action = "Готово",
                onAction = onDone,
            )
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmailForm(busy: Boolean, onSubmit: (String, String, String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    Text(
        "С почтой в этот аккаунт можно будет входить и без Telegram — например, " +
            "с компьютера. Пароль придумайте новый, от 8 символов.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = email,
        onValueChange = { email = it.trim() },
        label = { Text("Почта") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Пароль") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = repeat,
        onValueChange = { repeat = it },
        label = { Text("Пароль ещё раз") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { onSubmit(email, password, repeat) },
        enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(18.dp),
    ) { Text(if (busy) "Отправляю…" else "Привязать") }
}

@Composable
private fun CodeForm(
    email: String,
    busy: Boolean,
    onSubmit: (String) -> Unit,
    onRestart: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    Note(
        title = "Эта почта уже есть у другого аккаунта",
        text = "Их можно объединить в один. Мы отправили 6-значный код на $email — введите его, " +
            "чтобы подтвердить, что почта ваша.",
    )
    OutlinedTextField(
        value = code,
        onValueChange = { text -> code = text.filter(Char::isDigit).take(6) },
        label = { Text("Код из письма") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { onSubmit(code) },
        enabled = !busy && code.length == 6,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(18.dp),
    ) { Text(if (busy) "Проверяю…" else "Подтвердить") }
    TextButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) { Text("Указать другую почту") }
}

/**
 * Выбор подписки при объединении. Остальное — баланс, вход через Telegram и
 * по почте — бот сводит в один аккаунт сам; выбрать надо только подписку,
 * потому что у аккаунта она одна.
 */
@Composable
private fun ChooseSubscription(
    current: MergePreviewUserDto,
    other: MergePreviewUserDto,
    busy: Boolean,
    onMerge: (Long) -> Unit,
) {
    var keep by rememberSaveable { mutableStateOf(current.id) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    Text(
        "Какую подписку оставить? Вторая пропадёт — это нельзя будет отменить.",
        style = MaterialTheme.typography.bodyMedium,
    )
    AccountOption("Этот аккаунт", current, keep == current.id) { keep = current.id }
    AccountOption("Аккаунт с почтой", other, keep == other.id) { keep = other.id }
    Button(
        onClick = { confirm = true },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(18.dp),
    ) { Text(if (busy) "Объединяю…" else "Объединить аккаунты") }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Объединить аккаунты?") },
            text = {
                Text(
                    "Останется подписка «${(if (keep == current.id) current else other).subscriptionTitle()}». " +
                        "Другая будет удалена, отменить это нельзя.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onMerge(keep)
                }) { Text("Объединить") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun AccountOption(label: String, user: MergePreviewUserDto, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) scheme.primary.copy(alpha = 0.10f) else scheme.surface)
            .border(1.dp, if (selected) scheme.primary else scheme.outline.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(start = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(user.subscriptionTitle(), style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    user.subscription?.endDate?.take(10)?.let { "до $it" },
                    "баланс ${formatKopeks(user.balanceKopeks)}",
                    user.email ?: user.username?.let { "@$it" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

private fun MergePreviewUserDto.subscriptionTitle(): String =
    subscription?.tariffName ?: if (subscription != null) "Подписка" else "Без подписки"

@Composable
private fun Note(title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    GroupCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                Spacer(Modifier.height(4.dp))
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
            }
        }
    }
}
