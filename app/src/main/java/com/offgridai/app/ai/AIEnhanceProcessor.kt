package com.offgridai.app.ai

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// ══════════════════════════════════════════════════════
//  OffGrid AI — AI Enhance Processor (Super-Resolution)
//
//  Model: Real-ESRGAN-General-x4v3 (Qualcomm AI Hub DLC)
//
//  Model file: app/assets/models/real_esrgan_general_x4v3.dlc
//  Input:  Any Bitmap (tiles processed for large images)
//  Output: Upscaled 4x Bitmap with CLAHE & USM Post-Processing
//
//  Strategy: Tile-based processing executed natively on
//  the Hexagon Tensor Accelerator via SNPE/QNN.
// ══════════════════════════════════════════════════════

class AIEnhanceProcessor(
    private val engine: HexagonInferenceEngine
) {
    companion object {
        const val MODEL_PATH  = "models/real_esrgan_general_x4v3.dlc"
        const val TILE_SIZE   = 128
        const val TILE_OVERLAP = 16
        const val SCALE_FACTOR = 4 // RealESRGAN natively outputs 4x resolution
    }

    /**
     * Enhances image quality using Real-ESRGAN-General-x4v3 via Hexagon NPU.
     * Processes large images in tiles to prevent OOM.
     */
    fun process(inputBitmap: Bitmap): Flow<ProcessingResult> = flow {
        val startTime = System.currentTimeMillis()

        emit(ProcessingResult.Progress(0.08f, "تحميل نموذج Real-ESRGAN (Hexagon)…"))

        runCatching {
            engine.loadModel(MODEL_PATH)
        }.onFailure {
            emit(ProcessingResult.Error(ErrorTranslator.translate(it, "فشل تحميل نموذج التحسين"), it))
            return@flow
        }

        val srcW = inputBitmap.width
        val srcH = inputBitmap.height
        val outW = srcW * SCALE_FACTOR
        val outH = srcH * SCALE_FACTOR

        emit(ProcessingResult.Progress(0.15f, "تجهيز الصورة للمعالجة المجزأة…"))

        val tilesX = ((srcW + TILE_SIZE - TILE_OVERLAP - 1) / (TILE_SIZE - TILE_OVERLAP)).coerceAtLeast(1)
        val tilesY = ((srcH + TILE_SIZE - TILE_OVERLAP - 1) / (TILE_SIZE - TILE_OVERLAP)).coerceAtLeast(1)
        val totalTiles = tilesX * tilesY

        val outputBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        var tilesDone = 0

        for (ty in 0 until tilesY) {
            for (tx in 0 until tilesX) {
                val x0 = (tx * (TILE_SIZE - TILE_OVERLAP)).coerceAtMost(srcW - TILE_SIZE).coerceAtLeast(0)
                val y0 = (ty * (TILE_SIZE - TILE_OVERLAP)).coerceAtMost(srcH - TILE_SIZE).coerceAtLeast(0)
                val x1 = (x0 + TILE_SIZE).coerceAtMost(srcW)
                val y1 = (y0 + TILE_SIZE).coerceAtMost(srcH)
                val tileW = x1 - x0
                val tileH = y1 - y0

                val tileBitmap = Bitmap.createBitmap(inputBitmap, x0, y0, tileW, tileH)

                val paddedTile = if (tileW < TILE_SIZE || tileH < TILE_SIZE) {
                    val padded = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888)
                    val c = android.graphics.Canvas(padded)
                    c.drawBitmap(tileBitmap, 0f, 0f, null)
                    tileBitmap.recycle()
                    padded
                } else tileBitmap

                // NATIVE HEXAGON INFERENCE
                val aiOutputBmp = runCatching {
                    engine.executeRealESRGAN(paddedTile)
                }.getOrElse {
                    paddedTile.recycle()
                    emit(ProcessingResult.Error(ErrorTranslator.translate(it, "فشلت المعالجة"), it))
                    return@flow
                }
                
                val actualScale = aiOutputBmp.width / TILE_SIZE
                val outTileW = tileW * SCALE_FACTOR
                val outTileH = tileH * SCALE_FACTOR
                val dstX = x0 * SCALE_FACTOR
                val dstY = y0 * SCALE_FACTOR
                
                val canvas = android.graphics.Canvas(outputBitmap)
                val srcRect = android.graphics.Rect(0, 0, tileW * actualScale, tileH * actualScale)
                val dstRect = android.graphics.Rect(dstX, dstY, dstX + outTileW, dstY + outTileH)
                
                val paint = android.graphics.Paint()
                paint.isFilterBitmap = false
                canvas.drawBitmap(aiOutputBmp, srcRect, dstRect, paint)

                aiOutputBmp.recycle()
                paddedTile.recycle()
                tilesDone++

                val pct = 0.15f + (tilesDone.toFloat() / totalTiles) * 0.70f
                emit(ProcessingResult.Progress(pct, "تشغيل نموذج Real-ESRGAN: ${tilesDone}/${totalTiles} قطعة…"))
            }
        }
        
        // APPLY POST-PROCESSING (CLAHE + USM analog)
        emit(ProcessingResult.Progress(0.90f, "تطبيق تحسين الألوان الدقيق (CLAHE/USM)…"))
        val finalBitmap = applyWowFactor(outputBitmap)
        if (finalBitmap != outputBitmap) outputBitmap.recycle()

        emit(ProcessingResult.Progress(1.00f, "اكتمل تحسين الجودة ✓"))
        val elapsed = System.currentTimeMillis() - startTime
        emit(ProcessingResult.Success(finalBitmap, elapsed))
    }

    /**
     * Enhances colors and sharpness without applying the AI Upscaling model.
     */
    fun enhanceWithoutUpscale(inputBitmap: Bitmap): Flow<ProcessingResult> = flow {
        val startTime = System.currentTimeMillis()
        emit(ProcessingResult.Progress(0.5f, "تحسين الألوان والتفاصيل…"))
        val result = applyWowFactor(inputBitmap)
        emit(ProcessingResult.Progress(1.0f, "اكتمل التحسين ✓"))
        val elapsed = System.currentTimeMillis() - startTime
        emit(ProcessingResult.Success(result, elapsed))
    }

    private fun applyWowFactor(src: Bitmap): Bitmap {
        // Professional Gaussian Unsharp Mask to match our Python pipeline
        val sharpenedBmp = applyGaussianUnsharpMask(src, 0.5f)
        
        // Color & Contrast Boost (CLAHE approximation via ColorMatrix)
        val finalBmp = Bitmap.createBitmap(sharpenedBmp.width, sharpenedBmp.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(finalBmp)
        
        val satMatrix = android.graphics.ColorMatrix()
        satMatrix.setSaturation(1.08f)
        
        val contrast = 1.03f
        val brightness = 8f // Lift shadows slightly for a small shine
        val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
        val contrastMatrix = android.graphics.ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        satMatrix.postConcat(contrastMatrix)
        
        val paint = android.graphics.Paint()
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(satMatrix)
        canvas.drawBitmap(sharpenedBmp, 0f, 0f, paint)
        
        sharpenedBmp.recycle()
        return finalBmp
    }

    private fun applyGaussianUnsharpMask(src: Bitmap, amount: Float): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(width * height)
        src.getPixels(srcPixels, 0, width, 0, 0, width, height)
        
        val blurredPixels = IntArray(width * height)
        val tempPixels = IntArray(width * height)
        
        // 1. Horizontal Gaussian Pass
        for (y in 0 until height) {
            val offset = y * width
            for (x in 2 until width - 2) {
                var r = 0; var g = 0; var b = 0
                for (dx in -2..2) {
                    val p = srcPixels[offset + x + dx]
                    val w = when(Math.abs(dx)) { 0 -> 6; 1 -> 4; else -> 1 }
                    r += ((p shr 16) and 0xFF) * w
                    g += ((p shr 8) and 0xFF) * w
                    b += (p and 0xFF) * w
                }
                tempPixels[offset + x] = (r / 16 shl 16) or (g / 16 shl 8) or (b / 16)
            }
        }
        
        // 2. Vertical Gaussian Pass
        for (x in 2 until width - 2) {
            for (y in 2 until height - 2) {
                val offset = y * width
                var r = 0; var g = 0; var b = 0
                for (dy in -2..2) {
                    val p = tempPixels[(y + dy) * width + x]
                    val w = when(Math.abs(dy)) { 0 -> 6; 1 -> 4; else -> 1 }
                    r += ((p shr 16) and 0xFF) * w
                    g += ((p shr 8) and 0xFF) * w
                    b += (p and 0xFF) * w
                }
                blurredPixels[offset + x] = (r / 16 shl 16) or (g / 16 shl 8) or (b / 16)
            }
        }
        
        // 3. Unsharp Mask: Original + (Original - Blurred) * amount
        val finalPixels = IntArray(width * height)
        for (i in 0 until width * height) {
            val orig = srcPixels[i]
            val blur = blurredPixels[i]
            
            if (blur == 0) {
                finalPixels[i] = (0xFF shl 24) or (orig and 0xFFFFFF)
                continue
            }
            
            val oR = (orig shr 16) and 0xFF
            val oG = (orig shr 8) and 0xFF
            val oB = orig and 0xFF
            
            val bR = (blur shr 16) and 0xFF
            val bG = (blur shr 8) and 0xFF
            val bB = blur and 0xFF
            
            val r = (oR + (oR - bR) * amount).toInt().coerceIn(0, 255)
            val g = (oG + (oG - bG) * amount).toInt().coerceIn(0, 255)
            val b = (oB + (oB - bB) * amount).toInt().coerceIn(0, 255)
            
            finalPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        
        dest.setPixels(finalPixels, 0, width, 0, 0, width, height)
        return dest
    }
}
