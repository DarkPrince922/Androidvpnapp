package com.darkprince.vpn

import android.app.Application
import com.darkprince.vpn.data.log.AppLog
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import com.darkprince.vpn.di.ServiceLocator
import com.darkprince.vpn.work.AutoConnect
import com.darkprince.vpn.work.SubscriptionRefreshWorker

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        recordCrashes()
        ServiceLocator.init(this)
        SubscriptionRefreshWorker.schedule(this)
        // Ожидание сети восстанавливаем при каждом запуске: одноразовое
        // задание живёт до первого срабатывания, и если система успела его
        // выполнить и потерять, поставить новое больше некому.
        AutoConnect.sync(this, ServiceLocator.prefs.autoConnectBlocking())
    }

    /**
     * Падение приложения записываем в файл, а при следующем запуске
     * переносим в журнал — тот самый, что уходит в поддержку вместе с
     * обращением. Без этого вылет оставлял только «приложение закрылось», и
     * искать причину приходилось вслепую.
     */
    private fun recordCrashes() {
        val file = File(filesDir, CRASH_FILE)
        runCatching {
            if (file.exists()) {
                AppLog.write("прошлый запуск закончился сбоем:\n" + file.readText().take(4_000))
                file.delete()
            }
        }
        val system = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                file.writeText("${java.util.Date()} · поток ${thread.name}\n$trace".take(16_000))
            }
            system?.uncaughtException(thread, error)
        }
    }

    companion object {
        private const val CRASH_FILE = "last_crash.txt"
    }
}
