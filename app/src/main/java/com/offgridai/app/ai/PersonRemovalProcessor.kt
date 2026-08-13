package com.offgridai.app.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

// ══════════════════════════════════════════════════════
//  OffGrid AI — Person Removal + Inpainting Processor
//
//  Pipeline:
//    Step 1 → YOLOv8-Seg (person detection + mask)
//    Step 2 → User confirms target person (UI layer)
//    Step 3 → LaMa Inpainting (fill background)
//
//  Models (place in app/assets/models/):
//    • yolov8n_seg_int8.onnx    (person segmentation)
//    • lama_mobile_fp16.onnx    (background inpainting)
//
//  Output: Bitmap with selected person removed,
//          background intelligently reconstructed,
//          nearby persons preserved perfectly.
// ══════════════════════════════════════════════════════

class PersonRemovalProcessor(
    private val engine: OnnxInferenceEngine
) {
    companion object {
        const val SEG_MODEL_PATH    = "models/yolov8n_seg_int8.onnx"
        const val INPAINT_MODEL_PATH = "models/lama_mobile_fp16.onnx"
        const val SEG_INPUT_SIZE    = 640   // YOLOv8 native input
        const val INPAINT_SIZE      = 512   // LaMa mobile input
        const val PERSON_CLASS_ID   = 0     // COCO class 0 = person
        const val CONFIDENCE_THRESH = 0.45f
    }

    /**
     * Full person-removal pipeline.
     *
     * @param inputBitmap   Source image
     * @param targetMaskBitmap  Binary mask (white=remove, black=keep)
     *                         provided by user tap / auto-detection step
     */
    fun process(
        inputBitmap: Bitmap,
        targetMaskBitmap: Bitmap
    ): Flow<ProcessingResult> = flow {
        val startTime = System.currentTimeMillis()

        // ── Step 1: Load inpainting model ───────────
        emit(ProcessingResult.Progress(0.10f, "تحميل نموذج LaMa Inpainting…"))
        val inpaintSession = runCatching {
            engine.loadModel(INPAINT_MODEL_PATH, useNNAPI = true)
        }.getOrElse {
            emit(ProcessingResult.Error("فشل تحميل نموذج Inpainting: ${it.message}", it))
            return@flow
        }

        // ── Step 2: Resize inputs to INPAINT_SIZE ───
        emit(ProcessingResult.Progress(0.25f, "تهيئة مدخلات النموذج…"))
        val imgT = engine.bitmapToTensor(
            inputBitmap, INPAINT_SIZE, INPAINT_SIZE,
            mean = floatArrayOf(0f, 0f, 0f),
            std  = floatArrayOf(1f, 1f, 1f)
        )
        // Mask tensor: single channel [1, 1, H, W] — white pixels = inpaint zone
        val scaledMask = Bitmap.createScaledBitmap(targetMaskBitmap, INPAINT_SIZE, INPAINT_SIZE, true)
        val maskPixels = IntArray(INPAINT_SIZE * INPAINT_SIZE)
        scaledMask.getPixels(maskPixels, 0, INPAINT_SIZE, 0, 0, INPAINT_SIZE, INPAINT_SIZE)

        val capacity = INPAINT_SIZE * INPAINT_SIZE
        val maskFloat = java.nio.ByteBuffer.allocateDirect(capacity * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        maskPixels.forEach { px ->
            maskFloat.put(if ((px and 0xFF) > 127) 1f else 0f)
        }
        maskFloat.rewind()
        val mskT = ai.onnxruntime.OnnxTensor.createTensor(
            ai.onnxruntime.OrtEnvironment.getEnvironment(),
            maskFloat,
            longArrayOf(1L, 1L, INPAINT_SIZE.toLong(), INPAINT_SIZE.toLong())
        )
        scaledMask.recycle()
        val imageTensor = imgT
        val maskTensor = mskT

        // ── Step 3: Run LaMa inference ───────────────
        emit(ProcessingResult.Progress(0.55f, "تشغيل LaMa Inpainting على المعالج العصبي…"))
        val outputMap = runCatching {
            inpaintSession.run(mapOf("image" to imageTensor, "mask" to maskTensor))
        }.getOrElse {
            imageTensor.close(); maskTensor.close()
            emit(ProcessingResult.Error("فشل Inpainting: ${it.message}", it))
            return@flow
        }

        // ── Step 4: Post-process output ──────────────
        emit(ProcessingResult.Progress(0.80f, "إعادة بناء الخلفية…"))
        val outTensor = outputMap.getTensor()
        
        // Read via multi-dimensional array to bypass FloatBuffer JNI memory corruption bugs
        val outArray = outTensor.value as Array<Array<Array<FloatArray>>>
        val batch = outArray[0]
        val rChannel = batch[0]
        val gChannel = batch[1]
        val bChannel = batch[2]

        val numPixels = INPAINT_SIZE * INPAINT_SIZE
        val pixels    = IntArray(numPixels)

        var minVal = Float.MAX_VALUE
        var maxVal = Float.MIN_VALUE
        var nanCount = 0
        
        for (y in 0 until INPAINT_SIZE) {
            for (x in 0 until INPAINT_SIZE) {
                val rVal = rChannel[y][x]
                val gVal = gChannel[y][x]
                val bVal = bChannel[y][x]
                
                if (rVal.isNaN() || gVal.isNaN() || bVal.isNaN()) {
                    nanCount++
                    continue
                }
                if (rVal < minVal) minVal = rVal
                if (rVal > maxVal) maxVal = rVal
                if (gVal < minVal) minVal = gVal
                if (gVal > maxVal) maxVal = gVal
                if (bVal < minVal) minVal = bVal
                if (bVal > maxVal) maxVal = bVal
            }
        }
        
        android.util.Log.e("LaMaDebug", "Tensor Min: $minVal, Max: $maxVal, NaNs: $nanCount")

        // Get original pixels to fallback if model outputs NaNs or is corrupted
        val scaledOriginal = Bitmap.createScaledBitmap(inputBitmap, INPAINT_SIZE, INPAINT_SIZE, true)
        val origFallback = IntArray(numPixels)
        scaledOriginal.getPixels(origFallback, 0, INPAINT_SIZE, 0, 0, INPAINT_SIZE, INPAINT_SIZE)

        if (maxVal < 1.0f) {
            // The AI model underflowed (e.g. max brightness is 0.11 out of 255.0).
            // It produced a pitch-black corrupted tensor. Abort completely.
            android.util.Log.e("LaMaDebug", "Model underflow detected! Aborting inpaint to prevent black marks.")
            scaledOriginal.recycle()
            throw IllegalStateException("Hardware incompatible with FP16 AI model. Please use a different device or wait for an update.")
        } else {
            // Auto-detect scale (fallback to 255 if max is <= 1.5, to be safe)
            val scale = if (maxVal <= 1.5f && maxVal > 0f) 255f else 1f

            // Read pixel values
            var i = 0
            for (y in 0 until INPAINT_SIZE) {
                for (x in 0 until INPAINT_SIZE) {
                    val rVal = rChannel[y][x]
                    val gVal = gChannel[y][x]
                    val bVal = bChannel[y][x]

                    val r = (rVal * scale).toInt().coerceIn(0, 255)
                    val g = (gVal * scale).toInt().coerceIn(0, 255)
                    val b = (bVal * scale).toInt().coerceIn(0, 255)

                    if (rVal.isNaN() || gVal.isNaN() || bVal.isNaN()) {
                        pixels[i] = origFallback[i]
                    } else {
                        pixels[i] = android.graphics.Color.rgb(r, g, b)
                    }
                    i++
                }
            }
        }
        scaledOriginal.recycle()

        // Create inpainted result at model resolution
        val inpaintedSmall = Bitmap.createBitmap(INPAINT_SIZE, INPAINT_SIZE, Bitmap.Config.ARGB_8888)
        inpaintedSmall.setPixels(pixels, 0, INPAINT_SIZE, 0, 0, INPAINT_SIZE, INPAINT_SIZE)

        // Composite: paste inpainted region back into original-resolution image
        val originalW = inputBitmap.width
        val originalH = inputBitmap.height
        val output    = inputBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas    = android.graphics.Canvas(output)

        // Scale inpainted patch to original size
        val scaledInpainted = Bitmap.createScaledBitmap(inpaintedSmall, originalW, originalH, true)
        val scaledMaskFull  = Bitmap.createScaledBitmap(targetMaskBitmap, originalW, originalH, true)

        // Only paste inpainted pixels where mask is white
        val origPixels = IntArray(originalW * originalH)
        val inpPixels  = IntArray(originalW * originalH)
        val mskPixels  = IntArray(originalW * originalH)
        output.getPixels(origPixels, 0, originalW, 0, 0, originalW, originalH)
        scaledInpainted.getPixels(inpPixels, 0, originalW, 0, 0, originalW, originalH)
        scaledMaskFull.getPixels(mskPixels, 0, originalW, 0, 0, originalW, originalH)

        for (i in origPixels.indices) {
            if ((mskPixels[i] and 0xFF) > 127) {
                origPixels[i] = inpPixels[i]
            }
        }
        output.setPixels(origPixels, 0, originalW, 0, 0, originalW, originalH)

        // Cleanup
        inpaintedSmall.recycle()
        scaledInpainted.recycle()
        scaledMaskFull.recycle()

        val resultBitmap = output

        imageTensor.close(); maskTensor.close(); outputMap.close()

        emit(ProcessingResult.Progress(1.00f, "اكتملت إزالة الأشخاص ✓"))
        val elapsed = System.currentTimeMillis() - startTime
        emit(ProcessingResult.Success(resultBitmap, elapsed))
    }
}
