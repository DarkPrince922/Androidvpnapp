package com.darkprince.vpn.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.darkprince.vpn.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Обновление приложения без Google Play.
 *
 * Приложения нет в магазине, поэтому обновляемся сами: спрашиваем манифест,
 * сверяем номер сборки, качаем APK и отдаём его системному установщику.
 * Ставит его Android, а не мы, — и он же проверяет подпись: APK, подписанный
 * другим ключом, установлен не будет. Это и есть главная защита, поэтому
 * ключ подписи релиза менять нельзя, иначе обновление сломается у всех.
 *
 * Манифест берём со своего домена, а не с GitHub: у части пользователей он
 * недоступен ровно тогда, когда обновление и нужно.
 */
class AppUpdater(
    private val context: Context,
    private val okHttp: OkHttpClient,
) {
    /** Что лежит в манифесте, если оно новее установленного. */
    data class Update(
        val versionName: String,
        val versionCode: Int,
        val url: String,
        val notes: String?,
        val sha256: String?,
    )

    /**
     * Спрашивает манифест.
     *
     * Возвращает null и когда обновления нет, и когда проверка не удалась:
     * для пользователя это одно и то же — показывать нечего. Молча гасим и
     * сетевые ошибки, и разбор: проверка обновлений не должна мешать работать.
     */
    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        val body = try {
            val request = Request.Builder().url(MANIFEST_URL).build()
            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string() ?: return@withContext null
            }
        } catch (_: Exception) {
            return@withContext null
        }

        try {
            val json = JSONObject(body)
            val versionCode = json.getInt("versionCode")
            // сравниваем номером, а не строкой: 1.10.0 строкой меньше 1.9.0
            if (versionCode <= BuildConfig.VERSION_CODE) return@withContext null

            Update(
                versionName = json.optString("version").ifBlank { "?" },
                versionCode = versionCode,
                url = json.getString("url"),
                notes = json.optString("notes").ifBlank { null },
                sha256 = json.optString("sha256").ifBlank { null },
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Качает APK во внутренний кэш.
     *
     * Файл кладём именно туда, а не во внешнюю память: разрешений не нужно, и
     * подменить его между скачиванием и установкой другое приложение не может.
     */
    suspend fun download(update: Update): File? = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates")
        directory.mkdirs()
        // старый файл мог остаться от прерванной попытки
        val target = File(directory, "DarkPrinceVPN.apk")
        target.delete()

        try {
            val request = Request.Builder().url(update.url).build()
            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val stream = response.body?.byteStream() ?: return@withContext null
                target.outputStream().use { out -> stream.copyTo(out) }
            }
        } catch (_: Exception) {
            target.delete()
            return@withContext null
        }

        // Сумма из манифеста — защита от оборванной закачки и подменённого
        // файла. Подпись всё равно проверит Android, но лучше отвалиться до
        // установщика, чем показать человеку невнятную ошибку системы.
        val expected = update.sha256
        if (expected != null && !expected.equals(sha256Of(target), ignoreCase = true)) {
            target.delete()
            return@withContext null
        }

        target
    }

    /** Разрешена ли установка из нашего приложения. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * Открывает системный экран разрешения.
     *
     * Разрешение выдаётся отдельно каждому приложению: то, что человек когда-то
     * разрешил браузеру, на нас не распространяется.
     */
    fun requestInstallPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Отдаёт скачанный файл системному установщику. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            // без этого установщик не прочитает файл: каталог наш, не его
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MANIFEST_URL = "https://dprince.online/updates/android.json"
    }
}
