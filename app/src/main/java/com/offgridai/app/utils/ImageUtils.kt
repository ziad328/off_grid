package com.offgridai.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

// ══════════════════════════════════════════════════════
//  OffGrid AI — Image Utilities
//  Loads Bitmaps from URIs with auto-rotation correction
//  and safe downsampling for large photos (12MP+)
// ══════════════════════════════════════════════════════

object ImageUtils {

    // Max dimension we'll process (larger images are downsampled)
    private const val MAX_DIMENSION = 2048

    /**
     * Loads a Bitmap from a content:// or file:// URI.
     * - Automatically corrects EXIF rotation (critical for camera photos)
     * - Downsamples if larger than MAX_DIMENSION to prevent OOM
     * - Returns null on failure (caller should handle gracefully)
     */
    suspend fun loadBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = MAX_DIMENSION
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // 1. Read image dimensions without loading pixels
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val rawW = options.outWidth
            val rawH = options.outHeight
            if (rawW <= 0 || rawH <= 0) return@withContext null

            // 2. Calculate appropriate inSampleSize
            val sampleSize = calculateSampleSize(rawW, rawH, maxDimension)

            // 3. Load downsampled bitmap
            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize        = sampleSize
                inPreferredConfig   = Bitmap.Config.ARGB_8888
                inMutable           = true
            }

            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, loadOptions)
            } ?: return@withContext null

            // 4. Fix EXIF rotation
            val rotated = fixExifRotation(context, uri, bitmap)
            rotated
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves a Bitmap to a temp file in the app cache and returns its URI.
     * Used to pass processed results between screens via navigation.
     */
    suspend fun saveBitmapToCache(
        context: Context,
        bitmap: Bitmap,
        quality: Int = 95
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val file = java.io.File(context.cacheDir, "offgrid_result_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves a transparent-background Bitmap as PNG to cache.
     * Required for background removal output (needs alpha channel).
     */
    suspend fun saveBitmapToCachePng(
        context: Context,
        bitmap: Bitmap
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val file = java.io.File(context.cacheDir, "offgrid_result_${System.currentTimeMillis()}.png")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ── Private Helpers ───────────────────────────────

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        var w = width
        var h = height
        while (w > maxDimension || h > maxDimension) {
            sampleSize *= 2
            w /= 2
            h /= 2
        }
        return sampleSize
    }

    private fun fixExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return bitmap
            val exif = ExifInterface(inputStream)
            inputStream.close()

            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90  -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL   -> matrix.preScale(1f, -1f)
                else -> return bitmap
            }

            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (e: Exception) {
            bitmap
        }
    }
}
