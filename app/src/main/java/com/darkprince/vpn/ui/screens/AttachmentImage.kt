package com.darkprince.vpn.ui.screens

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Картинки из переписки, по номеру файла. Ссылки бот выдаёт со сроком действия
 * и меняет их при каждом обновлении обращения — по ссылке кэш промахивался бы
 * всякий раз, и фото грузилось бы заново.
 */
private val imageCache = LruCache<String, ImageBitmap>(24)

/** Фото из обращения: превью в переписке, по нажатию — целиком в браузере. */
@Composable
fun AttachmentImage(fileId: String, url: String, onOpen: (String) -> Unit) {
    val image by produceState<Result<ImageBitmap>?>(imageCache.get(fileId)?.let { Result.success(it) }, fileId) {
        if (value != null) return@produceState
        value = runCatching { withContext(Dispatchers.IO) { loadImage(url) } }
            .onSuccess { imageCache.put(fileId, it) }
    }
    val shape = RoundedCornerShape(12.dp)
    when {
        image == null -> Box(
            Modifier
                .size(220.dp, 140.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }

        image!!.isSuccess -> Image(
            bitmap = image!!.getOrThrow(),
            contentDescription = "Фото от клиента",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .heightIn(max = 360.dp)
                .clip(shape)
                .clickable { onOpen(url) },
        )

        else -> Box(Modifier.padding(top = 2.dp)) {
            TextButton(onClick = { onOpen(url) }) { Text("Фото не загрузилось — открыть в браузере") }
        }
    }
}

/** Скачать и уменьшить до разумного размера: превью незачем держать в памяти в полный кадр. */
private fun loadImage(url: String): ImageBitmap {
    val bytes = ServiceLocator.apiClient.plainOkHttp
        .newCall(Request.Builder().url(url).build())
        .execute()
        .use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            response.body!!.bytes()
        }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 900 || bounds.outHeight / (sample * 2) >= 1600) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("не картинка")
    return bitmap.asImageBitmap()
}
