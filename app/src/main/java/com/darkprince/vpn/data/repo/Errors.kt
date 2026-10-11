package com.darkprince.vpn.data.repo

/**
 * Ошибка кабинета человеческим языком.
 *
 * Новые разделы кабинета — подарки, скидки, вывод, смена тарифа — отвечают
 * на отказ английской строкой. Те, что человек реально встретит, переводим
 * по [known]: пара «кусок английского текста → что показать». Русский ответ
 * кабинета показываем как есть, остальное — общей фразой по коду.
 *
 * Тело ответа читается один раз, поэтому весь разбор — здесь.
 */
fun Exception.cabinetMessage(known: List<Pair<String, String>> = emptyList()): String {
    val http = this as? retrofit2.HttpException ?: return userMessage()
    val body = runCatching { http.response()?.errorBody()?.string() }.getOrNull().orEmpty()
    known.firstOrNull { (marker, _) -> body.contains(marker, ignoreCase = true) }
        ?.let { return it.second }
    val detail = runCatching {
        val root = org.json.JSONObject(body)
        when (val d = root.opt("detail")) {
            is String -> d
            is org.json.JSONObject -> d.optString("message")
            else -> null
        }
    }.getOrNull()?.trim()
    if (detail != null && detail.any { it in 'а'..'я' || it in 'А'..'Я' } && detail.length <= 300) {
        return detail
    }
    return when (http.code()) {
        400, 422 -> "Кабинет отклонил запрос. Обновите экран и попробуйте снова."
        401 -> "Сессия истекла. Войдите в аккаунт ещё раз."
        402 -> "Не хватает денег на балансе."
        403 -> "Эта возможность отключена в боте."
        404 -> "Не найдено. Обновите экран."
        409 -> "Это уже сделано — обновите экран."
        429 -> "Слишком часто. Подождите немного."
        in 500..599 -> "Сервер временно недоступен."
        else -> "Не получилось (ошибка ${http.code()})."
    }
}

/** Секунды из «Try again in N seconds.» — сколько ждать до повтора. */
internal fun String.secondsToWait(): Int? =
    Regex("in (\\d+) seconds").find(this)?.groupValues?.get(1)?.toIntOrNull()

/** Минуты словом: «1 минуту», «3 минуты», «15 минут». */
internal fun minutesText(minutes: Int): String {
    val word = when {
        minutes % 10 == 1 && minutes % 100 != 11 -> "минуту"
        minutes % 10 in 2..4 && minutes % 100 !in 12..14 -> "минуты"
        else -> "минут"
    }
    return "$minutes $word"
}
