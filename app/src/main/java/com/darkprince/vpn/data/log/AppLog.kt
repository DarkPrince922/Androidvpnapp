package com.darkprince.vpn.data.log

import android.os.Build
import com.darkprince.vpn.BuildConfig
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Журнал приложения на последние несколько сотен строк.
 *
 * Нужен ровно для одного: человек пишет в поддержку «не подключается», и по
 * его словам понять причину нельзя. Журнал прикладывается к обращению и
 * показывает, что происходило на самом деле.
 *
 * Живёт только в памяти. На диск попадает единственный раз — когда его
 * прикладывают к тикету. Так на устройстве не остаётся файла, который можно
 * забыть и потом отдать кому-то вместе с телефоном.
 *
 * **Секретов здесь быть не должно.** Ссылка подписки, токены кабинета и адреса
 * серверов — это доступ к VPN: человек приложит журнал к тикету и раздаст их.
 * Поэтому на записи стоит [redact] как страховка, но полагаться только на неё
 * нельзя — не пишите сюда того, чего не готовы показать постороннему.
 */
object AppLog {

    private const val MAX_LINES = 300

    private val lines = ArrayDeque<String>()
    private val stamp = SimpleDateFormat("HH:mm:ss", Locale.US)

    /** Ссылки целиком: подписка и есть ссылка. */
    private val urls = Regex("""\bhttps?://\S+""", RegexOption.IGNORE_CASE)

    /** Идентификаторы узлов в конфигах панели. */
    private val uuids = Regex(
        """\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Длинные строки с цифрой внутри — обычно токены и ключи. Цифра в условии
     * не для красоты: без неё под шаблон попадали бы имена исключений вроде
     * IllegalStateException, а именно они в журнале и нужны.
     */
    private val secrets = Regex("""\b(?=[A-Za-z0-9_\-+/=]*\d)[A-Za-z0-9_\-+/=]{20,}\b""")

    /** Убирает из строки то, что нельзя показывать постороннему. */
    fun redact(message: String): String = message
        .replace(urls, "<ссылка>")
        .replace(uuids, "<идентификатор>")
        .replace(secrets, "<скрыто>")

    @Synchronized
    fun write(message: String) {
        if (lines.size >= MAX_LINES) lines.removeFirst()
        lines.addLast("${stamp.format(Date())}  ${redact(message)}")
    }

    /**
     * Журнал целиком, с шапкой про версию и устройство: без неё половина
     * обращений упирается в вопрос «а какая у вас версия».
     */
    @Synchronized
    fun snapshot(): String = buildString {
        appendLine("DarkPrince VPN ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Записей: ${lines.size}")
        appendLine("—")
        lines.forEach { appendLine(it) }
    }

    @Synchronized
    fun clear() = lines.clear()
}
