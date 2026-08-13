package com.offgridai.app.ai

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.providers.NNAPIFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer

// ══════════════════════════════════════════════════════
//  OffGrid AI — ONNX Runtime Engine (On-Device AI)
//  Handles loading models from assets and running
//  inference with NNAPI / GPU Hardware Acceleration.
//
//  This is the core AI infrastructure for 2 features:
//  • Person Removal (LaMa Inpainting)
//  • Background Removal (RMBG-1.4)
//
//  NOTE: ONNX model files (.onnx) must be placed in:
//  app/src/main/assets/models/
// ══════════════════════════════════════════════════════

class OnnxInferenceEngine(private val context: Context) {

    private val ortEnvironment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val sessionCache = mutableMapOf<String, OrtSession>()

    // ── Load Model from Assets ────────────────────────
    /**
     * Loads an ONNX model from app assets and creates an
     * optimized OrtSession with NNAPI (NPU) acceleration.
     *
     * @param modelAssetPath  e.g. "models/rmbg_14_int8.onnx"
     * @param useNNAPI        Enable Qualcomm Hexagon NPU (default: true)
     * @param useGPU          Enable Adreno GPU fallback (default: true)
     */
    suspend fun loadModel(
        modelAssetPath: String,
        useNNAPI: Boolean = true,
        useGPU: Boolean   = true
    ): OrtSession = withContext(Dispatchers.IO) {
        sessionCache[modelAssetPath]?.let { return@withContext it }

        // Copy ONNX asset file to cache dir (ONNX C++ engine requires file path)
        val modelFile = File(context.cacheDir, File(modelAssetPath).name)
        if (!modelFile.exists() || modelFile.length() == 0L) {
            context.assets.open(modelAssetPath).use { input ->
                modelFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        // Also copy companion .data file if it exists (for models exported with external data)
        val dataAssetPath = "$modelAssetPath.data"
        val dataFile = File(context.cacheDir, File(dataAssetPath).name)
        runCatching {
            if (!dataFile.exists() || dataFile.length() == 0L) {
                context.assets.open(dataAssetPath).use { input ->
                    dataFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }

        val options = SessionOptions().apply {
            val profile = HardwareTuner.detectPerformanceProfile()
            HardwareTuner.applyOptimizations(this, profile, useNNAPI)
        }

        val session = ortEnvironment.createSession(modelFile.absolutePath, options)
        sessionCache[modelAssetPath] = session
        session
    }

    // ── Bitmap → Float Tensor ─────────────────────────
    /**
     * Converts a Bitmap to a normalized [1, 3, H, W] float tensor
     * suitable as ONNX model input.
     */
    fun bitmapToTensor(
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        mean: FloatArray = floatArrayOf(0.485f, 0.456f, 0.406f),
        std: FloatArray  = floatArrayOf(0.229f, 0.224f, 0.225f)
    ): ai.onnxruntime.OnnxTensor {
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        val pixels = IntArray(targetWidth * targetHeight)
        scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

        val capacity = 3 * targetWidth * targetHeight
        val floatBuffer = java.nio.ByteBuffer.allocateDirect(capacity * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        val rBuf = FloatArray(targetWidth * targetHeight)
        val gBuf = FloatArray(targetWidth * targetHeight)
        val bBuf = FloatArray(targetWidth * targetHeight)

        pixels.forEachIndexed { i, px ->
            rBuf[i] = (((px shr 16) and 0xFF) / 255f - mean[0]) / std[0]
            gBuf[i] = (((px shr 8)  and 0xFF) / 255f - mean[1]) / std[1]
            bBuf[i] = (((px)        and 0xFF) / 255f - mean[2]) / std[2]
        }

        floatBuffer.put(rBuf)
        floatBuffer.put(gBuf)
        floatBuffer.put(bBuf)
        floatBuffer.rewind()

        return ai.onnxruntime.OnnxTensor.createTensor(
            ortEnvironment,
            floatBuffer,
            longArrayOf(1L, 3L, targetHeight.toLong(), targetWidth.toLong())
        )
    }

    /** Converts an ARGB Bitmap into a 1-channel Luma (Y) Tensor [1, 1, H, W] */
    fun bitmapToLumaTensor(
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): ai.onnxruntime.OnnxTensor {
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        val pixels = IntArray(targetWidth * targetHeight)
        scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

        val capacity = targetWidth * targetHeight
        val floatBuffer = java.nio.ByteBuffer.allocateDirect(capacity * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        
        for (i in pixels.indices) {
            val px = pixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8)  and 0xFF
            val b = (px)        and 0xFF
            // Luma (Y) BT.601
            val y = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            floatBuffer.put(y)
        }
        floatBuffer.rewind()

        if (scaled != bitmap) scaled.recycle()

        return ai.onnxruntime.OnnxTensor.createTensor(
            ortEnvironment,
            floatBuffer,
            longArrayOf(1L, 1L, targetHeight.toLong(), targetWidth.toLong())
        )
    }

    // ── Release Resources ─────────────────────────────
    fun releaseAll() {
        sessionCache.values.forEach { it.close() }
        sessionCache.clear()
        ortEnvironment.close()
    }

    fun releaseModel(modelAssetPath: String) {
        sessionCache.remove(modelAssetPath)?.close()
    }
}

// ══════════════════════════════════════════════════════
//  Processing Result Wrapper
// ══════════════════════════════════════════════════════

sealed class ProcessingResult {
    data class Success(val outputBitmap: Bitmap, val processingTimeMs: Long) : ProcessingResult()
    data class Error(val message: String, val cause: Throwable? = null) : ProcessingResult()
    data class Progress(val percent: Float, val statusMessage: String) : ProcessingResult()
}

/**
 * Safely extracts an [OnnxTensor] from [OrtSession.Result],
 * automatically unwrapping [java.util.Optional] if present.
 */
fun OrtSession.Result.getTensor(key: String? = null): ai.onnxruntime.OnnxTensor {
    val rawValue: Any? = if (key != null) {
        val entry = this.get(key)
        if (entry is java.util.Optional<*>) entry.orElse(null) else entry
    } else {
        val firstVal = this.iterator().next().value
        if (firstVal is java.util.Optional<*>) firstVal.orElse(null) else firstVal
    }
    val tensor = rawValue as? ai.onnxruntime.OnnxTensor
        ?: throw IllegalStateException("ONNX result tensor is null or invalid for key: $key")
    return tensor
}
