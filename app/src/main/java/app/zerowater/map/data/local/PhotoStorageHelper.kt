package app.zerowater.map.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class PhotoStorageHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val photoDir: File
        get() = File(context.filesDir, "station_photos").apply {
            if (!exists()) mkdirs()
        }

    suspend fun savePhotoFromUri(sourceUri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                } ?: return@withContext null
                saveCompressedBitmap(bitmap)
            }.getOrNull()
        }

    suspend fun savePhotoFromBitmap(bitmap: Bitmap): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                saveCompressedBitmap(bitmap)
            }.getOrNull()
        }

    private fun saveCompressedBitmap(source: Bitmap): String {
        val maxDimension = 1280
        val longestSide = max(source.width, source.height)
        val scaled = if (longestSide > maxDimension) {
            val ratio = maxDimension.toFloat() / longestSide.toFloat()
            val targetW = (source.width * ratio).toInt().coerceAtLeast(1)
            val targetH = (source.height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        } else {
            source
        }

        val outFile = File(photoDir, "station_${UUID.randomUUID()}.jpg")
        FileOutputStream(outFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        }
        return Uri.fromFile(outFile).toString()
    }
}
