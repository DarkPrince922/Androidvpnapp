package com.darkprince.vpn.core.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

/** Генерация и чтение QR-кодов подписки. */
object QrUtils {

    fun encode(text: String, size: Int = 720): Bitmap? = try {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until size) {
                for (y in 0 until size) {
                    setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
        }
    } catch (_: Exception) {
        null
    }

    /** Сохраняет QR в кэш и отдаёт content-ссылку для отправки в мессенджер. */
    fun saveForSharing(context: Context, bitmap: Bitmap, name: String = "vpn-qr.png"): Uri? = try {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (_: Exception) {
        null
    }

    /**
     * Читает QR с картинки из галереи. Изображение уменьшается, если оно
     * слишком большое: полноразмерные фото не помещаются в память и
     * распознаются хуже.
     */
    fun decodeFromImage(context: Context, uri: Uri): String? {
        val bitmap = loadBitmap(context, uri) ?: return null
        return decodeBitmap(bitmap)
            // повторная попытка на увеличенной копии — помогает для мелких QR
            ?: decodeBitmap(
                Bitmap.createScaledBitmap(bitmap, bitmap.width * 2, bitmap.height * 2, true)
            )
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = if (maxSide > 1600) maxSide / 1600 else 1
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    } catch (_: Exception) {
        null
    }

    private fun decodeBitmap(bitmap: Bitmap): String? = try {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.TRY_HARDER to true,
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                )
            )
        }
        reader.decode(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: Exception) {
        null
    }
}
