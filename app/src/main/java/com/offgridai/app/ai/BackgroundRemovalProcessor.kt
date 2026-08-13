package com.offgridai.app.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

// ══════════════════════════════════════════════════════
//  OffGrid AI — Background Removal Processor
//  Model: RMBG-1.4 (ONNX INT8) or BiRefNet-Tiny
//
//  Input:  Any image Bitmap
//  Output: Bitmap with transparent background (ARGB_8888)
//
//  Model file: app/assets/models/rmbg_14_int8.onnx
//  Input size: 1024×1024 (model's native resolution)
// ══════════════════════════════════════════════════════

class BackgroundRemovalProcessor(
    private val engine: OnnxInferenceEngine
) {
    companion object {
        const val MODEL_PATH   = "models/rmbg_14_int8.onnx"
        const val INPUT_SIZE   = 1024  // RMBG-1.4 native input
    }

    /**
     * Removes background from a Bitmap.
     * Emits [ProcessingResult.Progress] updates during processing.
     * Emits [ProcessingResult.Success] with transparent PNG-ready Bitmap.
     */
    fun process(inputBitmap: Bitmap): Flow<ProcessingResult> = flow {
        val startTime = System.currentTimeMillis()

        emit(ProcessingResult.Progress(0.10f, "تحميل نموذج إزالة الخلفية…"))

        // 1. Load ONNX model (cached after first call)
        val session = runCatching {
            engine.loadModel(MODEL_PATH, useNNAPI = true, useGPU = true)
        }.getOrElse {
            emit(ProcessingResult.Error("فشل تحميل النموذج: ${it.message}", it))
            return@flow
        }

        emit(ProcessingResult.Progress(0.25f, "تحليل الصورة…"))

        // 2. Pre-process: resize + normalize → float tensor [-1, 1]
        val inputTensor = engine.bitmapToTensor(
            bitmap = inputBitmap,
            targetWidth = INPUT_SIZE,
            targetHeight = INPUT_SIZE,
            mean = floatArrayOf(0.5f, 0.5f, 0.5f),
            std  = floatArrayOf(0.5f, 0.5f, 0.5f)
        )

        emit(ProcessingResult.Progress(0.50f, "تشغيل نموذج RMBG-1.4 على المعالج العصبي…"))

        // 3. Run inference
        val outputMap = runCatching {
            session.run(mapOf("input" to inputTensor))
        }.getOrElse {
            inputTensor.close()
            emit(ProcessingResult.Error("فشل الاستنتاج: ${it.message}", it))
            return@flow
        }

        emit(ProcessingResult.Progress(0.75f, "معالجة قناع الشفافية…"))

        // 4. Post-process: extract alpha mask from model output
        val maskTensor = outputMap.getTensor()
        val maskData   = maskTensor.floatBuffer

        val origW = inputBitmap.width
        val origH = inputBitmap.height
        val output = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)

        val maskBmp = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val maskInts = IntArray(INPUT_SIZE * INPUT_SIZE)
        for (i in maskInts.indices) {
            val maskVal = maskData.get().coerceIn(0f, 1f)
            val alpha = (maskVal * 255).toInt().coerceIn(0, 255)
            maskInts[i] = Color.argb(alpha, 255, 255, 255)
        }
        maskBmp.setPixels(maskInts, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val scaledMask = Bitmap.createScaledBitmap(maskBmp, origW, origH, true)
        val origPixels = IntArray(origW * origH)
        val finalMaskInts = IntArray(origW * origH)

        inputBitmap.getPixels(origPixels, 0, origW, 0, 0, origW, origH)
        scaledMask.getPixels(finalMaskInts, 0, origW, 0, 0, origW, origH)

        for (i in origPixels.indices) {
            val alpha = (finalMaskInts[i] shr 24) and 0xFF
            val px = origPixels[i]
            origPixels[i] = (px and 0x00FFFFFF) or (alpha shl 24)
        }

        output.setPixels(origPixels, 0, origW, 0, 0, origW, origH)

        maskBmp.recycle()
        scaledMask.recycle()

        inputTensor.close()
        outputMap.close()

        emit(ProcessingResult.Progress(1.00f, "اكتملت إزالة الخلفية ✓"))

        val elapsed = System.currentTimeMillis() - startTime
        emit(ProcessingResult.Success(output, elapsed))
    }
}
