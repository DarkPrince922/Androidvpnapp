package com.darkprince.vpn.ui

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Подтверждение личности перед админской вкладкой.
 *
 * Телефон теряют чаще, чем ноутбук, а за вкладкой лежат чужие балансы и
 * переписка. Разблокированный экран не должен означать доступ к панели,
 * поэтому спрашиваем каждый раз при входе, а не один раз при запуске.
 *
 * Если на устройстве вообще нечем подтвердить личность — ни отпечатка, ни
 * PIN-кода, — пускаем без проверки. Требовать завести блокировку ради этой
 * вкладки мы не вправе, а замок, который нечем открыть, просто отрезал бы
 * человека от его же панели.
 */
object AdminLock {

    private const val ALLOWED = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** Есть ли на устройстве чем подтвердить личность. */
    fun available(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(ALLOWED) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onCancel: () -> Unit,
    ) {
        if (!available(activity)) {
            onSuccess()
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                    onSuccess()

                override fun onAuthenticationError(code: Int, message: CharSequence) = onCancel()
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Панель управления")
            .setSubtitle("Подтвердите, что это вы")
            .apply {
                // Сочетание «биометрия или код устройства» разрешено только
                // с Android 11. На старых версиях тот же смысл даёт отдельный
                // устаревший флаг — другого способа там нет.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(ALLOWED)
                } else {
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()

        prompt.authenticate(info)
    }
}

/**
 * Замок перед админским содержимым.
 *
 * Пока личность не подтверждена, содержимое не собирается вовсе — не просто
 * прячется под заглушкой. Отказ или отмена возвращают человека назад: висеть
 * на пустом экране с кнопкой «попробовать ещё» незачем, вкладка никуда не
 * денется.
 */
@Composable
fun AdminGate(
    activity: FragmentActivity,
    unlocked: Boolean,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(unlocked) {
        if (!unlocked) AdminLock.prompt(activity, onSuccess = onUnlocked, onCancel = onCancel)
    }

    if (unlocked) {
        content()
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
