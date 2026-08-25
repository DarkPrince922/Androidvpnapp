package com.darkprince.vpn.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.darkprince.vpn.data.log.AppLog
import com.darkprince.vpn.data.prefs.AppPrefs
import com.darkprince.vpn.di.ServiceLocator
import com.darkprince.vpn.vpn.VpnState
import com.darkprince.vpn.vpn.VpnStateStore
import com.darkprince.vpn.vpn.XVpnService

/**
 * Автоподключение.
 *
 * Общее для всех поводов условие одно: человек включил автоподключение,
 * разрешение на VPN уже выдано и есть к чему подключаться. Разрешение
 * спросить в фоне нельзя — оно требует диалога, поэтому без него мы просто
 * молчим: показывать окно поверх чужого приложения хуже, чем не подключиться.
 */
object AutoConnect {

    fun shouldConnect(context: Context, prefs: AppPrefs): Boolean {
        if (prefs.autoConnectBlocking() == AppPrefs.AUTO_OFF) return false
        if (VpnService.prepare(context) != null) {
            AppLog.write("автоподключение: нет разрешения на VPN")
            return false
        }
        if (!XVpnService.hasSavedProfile(context)) {
            AppLog.write("автоподключение: нечего поднимать, профиля нет")
            return false
        }
        val state = VpnStateStore.state.value
        return state != VpnState.CONNECTED && state != VpnState.CONNECTING
    }

    fun connect(context: Context, reason: String) {
        AppLog.write("автоподключение: $reason")
        runCatching { XVpnService.startLast(context) }
            .onFailure { AppLog.write("автоподключение не вышло: ${it.javaClass.simpleName}") }
    }

    /**
     * Ждать появления сети.
     *
     * Своего наблюдателя за сетью держать негде: пока туннель не поднят, у
     * приложения нет ни одного живого процесса, а система его не разбудит.
     * Поэтому просим об этом её саму — задание с условием «есть сеть»
     * запускается тогда, когда сеть появилась, и переживает перезагрузку.
     * После каждого срабатывания ставим следующее: одноразовое задание с
     * условием — единственный способ получить событие, а не опрос.
     */
    fun arm(context: Context) {
        val request = OneTimeWorkRequestBuilder<AutoConnectWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun disarm(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Привести расписание в соответствие с настройкой. */
    fun sync(context: Context, mode: String) {
        if (mode == AppPrefs.AUTO_NETWORK) arm(context) else disarm(context)
    }

    const val WORK_NAME = "autoconnect"
}

/** Сеть появилась — поднимаемся и снова становимся в ожидание. */
class AutoConnectWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ServiceLocator.init(applicationContext)
        val prefs = ServiceLocator.prefs
        if (prefs.autoConnectBlocking() != AppPrefs.AUTO_NETWORK) return Result.success()

        if (AutoConnect.shouldConnect(applicationContext, prefs)) {
            AutoConnect.connect(applicationContext, "появилась сеть")
        }
        // Встаём в ожидание следующего раза независимо от того, подключились
        // ли сейчас: сеть пропадёт и появится снова, и повод повторится.
        AutoConnect.arm(applicationContext)
        return Result.success()
    }
}

/**
 * Телефон включился.
 *
 * Это единственный повод, ради которого система будит приложение сама, и
 * единственный, на котором запуск службы из фона разрешён без оговорок.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        ServiceLocator.init(context.applicationContext)
        val prefs = ServiceLocator.prefs
        val mode = prefs.autoConnectBlocking()
        if (mode == AppPrefs.AUTO_OFF) return

        AutoConnect.sync(context.applicationContext, mode)
        if (AutoConnect.shouldConnect(context.applicationContext, prefs)) {
            AutoConnect.connect(context.applicationContext, "телефон включился")
        }
    }
}
