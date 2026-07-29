plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

import java.security.KeyStore

// Версия и подпись берутся из окружения (задаются CI при релизе по тегу),
// иначе — значения по умолчанию для локальной сборки.
val appVersionName: String = System.getenv("RELEASE_VERSION") ?: "1.0.0"
val appVersionCode: Int = System.getenv("RELEASE_VERSION_CODE")?.toIntOrNull() ?: 1
val keystorePath: String? = System.getenv("KEYSTORE_PATH")
val hasKeystore: Boolean = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

/**
 * Подбирает пароль, которым реально открывается ключ. Хранилища PKCS12 (формат
 * по умолчанию у keytool) не поддерживают отдельный пароль ключа — там он равен
 * паролю хранилища, даже если при генерации указывали другой. Проверяем оба
 * варианта, чтобы подпись не падала из-за несоответствия.
 */
fun resolveKeyPassword(store: File, storePass: String?, alias: String?, keyPass: String?): String? {
    if (storePass == null || alias.isNullOrBlank()) return keyPass ?: storePass
    val candidates = listOfNotNull(keyPass?.takeIf { it.isNotBlank() }, storePass).distinct()
    for (type in listOf("PKCS12", "JKS")) {
        val ks = try {
            KeyStore.getInstance(type).also { ks ->
                store.inputStream().use { ks.load(it, storePass.toCharArray()) }
            }
        } catch (_: Exception) {
            continue
        }
        for (candidate in candidates) {
            val ok = try {
                ks.getKey(alias, candidate.toCharArray()) != null
            } catch (_: Exception) {
                false
            }
            if (ok) return candidate
        }
    }
    return keyPass ?: storePass
}

android {
    namespace = "com.darkprince.vpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.darkprince.vpn"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        // Адрес Cabinet API бота Bedolaga (можно оставить пустым — тогда
        // приложение спросит адрес при первом запуске).
        buildConfigField("String", "DEFAULT_API_BASE_URL", "\"https://cabinet.darkprincepanel.ru/api\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            if (hasKeystore) {
                val store = file(keystorePath!!)
                val storePass = System.getenv("KEYSTORE_PASSWORD")
                val alias = System.getenv("KEY_ALIAS")
                storeFile = store
                storePassword = storePass
                keyAlias = alias
                keyPassword = resolveKeyPassword(
                    store, storePass, alias, System.getenv("KEY_PASSWORD")
                )
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // без keystore собирается неподписанный APK (как раньше)
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Ядро Xray (libv2ray.aar из релизов 2dust/AndroidLibXrayLite) и
    // libhev-socks5-tunnel.so — кладутся скриптами из scripts/ (см. README).
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
