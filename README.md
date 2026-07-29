# DarkPrince VPN — Android-клиент для Remnawave + Bedolaga

Android-приложение VPN, полностью совместимое с панелью
[Remnawave](https://remna.st) и ботом
[Bedolaga](https://github.com/BEDOLAGA-DEV/remnawave-bedolaga-telegram-bot):
существующие клиенты входят через Telegram (тот же аккаунт, что и в боте),
новые — регистрируются по e-mail. Баланс, тарифы и оплата — те же, что в
кабинете: приложение работает напрямую с **Cabinet API** бота.

## Возможности

- **Авторизация через Telegram** — deep-link `t.me/<бот>?start=webauth_…`,
  как в веб-кабинете Bedolaga (нужен бот **v3.33.0+** с `CABINET_ENABLED=true`).
- **Вход и регистрация по e-mail** (`/cabinet/auth/email/*`), восстановление
  пароля.
- **Подписка Remnawave**: приложение получает `subscription_url` из кабинета,
  скачивает подписку и парсит серверы **VLESS (Reality/TLS), VMess, Trojan,
  Shadowsocks**; сети tcp / ws / grpc / httpupgrade / xhttp. Поддерживается и
  формат Xray JSON (полные конфиги панели с роутингом и балансировщиками).
- **Учёт устройств**: при загрузке подписки передаются `x-hwid` и сведения об
  устройстве, поэтому в Remnawave работает лимит устройств из тарифа, а в
  панели видно, с каких телефонов используется подписка.
- **Несколько подписок**: если в боте включён мультитариф, на главном экране
  появляется переключатель между подписками — каждая держит свои серверы,
  поэтому переключение мгновенное и ничего не теряет.
- **Совместный доступ**: владелец выбирает подписку в «Ещё → Поделиться
  подпиской» и отдаёт её QR-кодом, картинкой или ссылкой. Второй человек
  сканирует код камерой либо загружает присланную картинку и работает в
  гостевом режиме — VPN и выбор приложений доступны, кабинет и оплата нет.
  Каждое устройство занимает своё место в лимите устройств тарифа.
- **Раздельное туннелирование**: «Ещё → Приложения через VPN» — весь трафик,
  только выбранные приложения или все, кроме выбранных.
- **VPN-подключение**: ядро Xray (libv2ray из
  [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)) +
  [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)
  (TUN → SOCKS), схема как в v2rayNG. Статистика трафика в реальном времени.
- **Оплата внутри приложения как в кабинете**: баланс, способы оплаты из
  `/cabinet/balance/payment-methods`, создание платежа
  (`/cabinet/balance/topup`) с открытием платёжной страницы (ЮKassa,
  CryptoBot, Stars и т.д. — что включено в боте), история операций, проверка
  платежа.
- **Тарифы**: покупка (`purchase-tariff`), продление (`renew`), пробный
  период (`trial`).

## Требования на стороне сервера

В `.env` бота Bedolaga:

```env
CABINET_ENABLED=true
CABINET_JWT_SECRET=<openssl rand -hex 32>
CABINET_ALLOWED_ORIGINS=...
```

Cabinet API должен быть доступен извне (тот же адрес, что использует
веб-кабинет, например `https://cabinet.example.com/api`). Именно этот адрес
пользователь вводит при первом запуске приложения (или задайте его по
умолчанию в `app/build.gradle.kts` → `DEFAULT_API_BASE_URL`).

## Сборка

### Вариант 1: GitHub Actions (проще всего)

Workflow `.github/workflows/build.yml` при каждом пуше сам скачивает ядро,
собирает нативную библиотеку и публикует готовые APK в артефактах сборки.

### Вариант 2: локально

1. Android Studio (SDK 35) + Android NDK.
2. Скачайте ядро Xray и геоданные:
   ```bash
   bash scripts/download-libv2ray.sh          # -> app/libs/libv2ray.aar
   bash scripts/download-geodata.sh           # -> app/src/main/assets/geo*.dat
   ```
3. Соберите мост TUN→SOCKS:
   ```bash
   export NDK_HOME=$ANDROID_HOME/ndk/<версия>
   bash scripts/compile-hevtun.sh             # -> app/src/main/jniLibs/*
   ```
4. `./gradlew assembleDebug` или сборка из Android Studio.

## Релиз

Релизный APK собирается и публикуется автоматически по тегу.

### 1. Ключ подписи (один раз)

```bash
keytool -genkeypair -v -keystore release.jks -alias darkprince \
  -keyalg RSA -keysize 4096 -validity 10000
```

**Храните `release.jks` и пароли в надёжном месте.** Если ключ потерян, выпустить
обновление поверх установленного приложения будет невозможно — пользователям
придётся удалять старое и ставить новое.

### 2. Секреты репозитория

Settings → Secrets and variables → Actions → New repository secret:

| Секрет | Значение |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `KEYSTORE_PASSWORD` | пароль хранилища |
| `KEY_ALIAS` | `darkprince` |
| `KEY_PASSWORD` | пароль ключа (в PKCS12 совпадает с паролем хранилища) |

Формат PKCS12, который keytool использует по умолчанию, не поддерживает
отдельный пароль ключа: указанный при генерации `-keypass` игнорируется, и
пароль ключа всегда равен паролю хранилища. Сборка подбирает рабочий пароль
сама, поэтому расхождение в этом секрете не ломает подпись.

Без этих секретов релиз соберётся, но APK будет неподписанным.

### 3. Выпуск версии

```bash
git tag v1.0.0
git push origin v1.0.0
```

Workflow `release.yml` соберёт подписанный APK, посчитает SHA-256 и создаст
GitHub Release с файлом `DarkPrinceVPN-<версия>.apk`. Версия приложения берётся
из тега, `versionCode` — из номера сборки, так что каждая новая публикация
корректно обновляет установленное приложение.

Тот же workflow можно запустить вручную: Actions → Release → Run workflow.

### Раздача клиентам

Репозиторий приватный, поэтому ссылка на GitHub Release не открывается без
доступа. Варианты: выложить APK на свой сервер рядом с кабинетом, отдавать
файл через бота или создать отдельный публичный репозиторий только для
релизов.

## Архитектура

```
app/src/main/java/com/darkprince/vpn/
├── data/
│   ├── api/        Retrofit-клиент Cabinet API (auth, subscription, balance)
│   ├── prefs/      DataStore: адрес кабинета, JWT-токены, кэш серверов
│   └── repo/       AuthRepository (Telegram deep-link + email),
│                   SubscriptionRepository (подписка Remnawave, тарифы),
│                   BalanceRepository (баланс, оплата)
├── core/
│   ├── parser/     Парсер ссылок vless/vmess/trojan/ss из подписки
│   └── xray/       Генератор JSON-конфига Xray
├── vpn/            XVpnService (VpnService), TProxyService (JNI hev),
│                   VpnStateStore (состояние/трафик)
└── ui/             Jetpack Compose: Setup, Login (Telegram/Email),
                    Home, Servers, Plans, Balance, Settings
```

### Как работает авторизация через Telegram

1. `POST /cabinet/auth/deeplink/request` → `{token, bot_username}`.
2. Приложение открывает `tg://resolve?domain=<бот>&start=webauth_<token>`.
3. Пользователь жмёт **Start** в боте.
4. Приложение опрашивает `POST /cabinet/auth/deeplink/poll` (202 — ждём,
   200 — получены `access_token`/`refresh_token`).

### Как работает подключение

1. `GET /cabinet/subscription/connection-link` → `subscription_url` (Remnawave).
2. Подписка скачивается с User-Agent v2rayNG → base64-список ссылок.
3. Выбранный сервер превращается в конфиг Xray (SOCKS-инбаунд на 10808).
4. `VpnService` поднимает TUN, весь трафик через hev-socks5-tunnel уходит в
   SOCKS ядра Xray. Приложение исключает само себя из VPN
   (`addDisallowedApplication`), чтобы не было петли.

## Брендинг

- Имя приложения: `app/src/main/res/values/strings.xml` → `app_name`.
- Иконка/цвета: `res/drawable/ic_launcher_foreground.xml`,
  `ui/theme/Theme.kt`.
- ApplicationId: `app/build.gradle.kts`.

## Лицензии

Приложение использует libv2ray (AndroidLibXrayLite, LGPL), Xray-core
(MPL-2.0) и hev-socks5-tunnel (GPL-3.0 для несвободного использования —
см. лицензию проекта). При распространении соблюдайте условия лицензий
этих компонентов.
