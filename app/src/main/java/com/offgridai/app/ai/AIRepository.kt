package com.offgridai.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.offgridai.app.navigation.AIFeature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

// ══════════════════════════════════════════════════════
//  OffGrid AI — AI Repository
//  Single facade that routes processing tasks to the
//  correct AI processor based on selected feature.
//
//  All processing: 100% On-Device. No internet. No API.
// ══════════════════════════════════════════════════════

class AIRepository(context: Context) {

    private val engine         = OnnxInferenceEngine(context)
    private val hexagonEngine  = HexagonInferenceEngine(context)
    
    private val bgRemoval      = BackgroundRemovalProcessor(engine)
    private val personRemoval  = PersonRemovalProcessor(engine)
    private val aiEnhance      = AIEnhanceProcessor(hexagonEngine)
    private val videoPipeline  = VideoProcessingPipeline(context, engine, hexagonEngine)

    private val cacheDir = context.cacheDir

    // ── Image Processing ─────────────────────────────
    /**
     * Processes a single image bitmap with the selected AI feature.
     * Emits [ProcessingResult.Progress] and [ProcessingResult.Success].
     */
    fun processImage(
        bitmap: Bitmap,
        feature: AIFeature,
        maskBitmap: Bitmap? = null   // Required for PERSON_REMOVAL
    ): Flow<ProcessingResult> = when (feature) {
        AIFeature.BACKGROUND_REMOVAL -> bgRemoval.process(bitmap)
        AIFeature.AI_ENHANCE         -> aiEnhance.process(bitmap)
        AIFeature.PERSON_REMOVAL     -> {
            val mask = maskBitmap ?: createFullMask(bitmap)
            personRemoval.process(bitmap, mask)
        }
    }

    // ── Video Processing ─────────────────────────────
    /**
     * Processes a video file (up to 120 seconds) fully offline.
     * Emits [VideoProcessingProgress] and final [File] output.
     */
    fun processVideo(
        videoUri: Uri,
        feature: AIFeature,
        maskBitmap: Bitmap? = null
    ): Flow<Any> = videoPipeline.processVideo(
        inputUri   = videoUri,
        feature    = when (feature) {
            AIFeature.PERSON_REMOVAL    -> VideoProcessingFeature.PersonRemoval
            AIFeature.BACKGROUND_REMOVAL -> VideoProcessingFeature.BackgroundRemoval
            AIFeature.AI_ENHANCE        -> VideoProcessingFeature.AIEnhance
        },
        outputDir  = cacheDir,
        maskBitmap = maskBitmap
    )

    // ── Helpers ──────────────────────────────────────
    /** Creates a targeted center mask (center 50% area where main person is located) */
    private fun createFullMask(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(mask)
        canvas.drawColor(android.graphics.Color.BLACK)

        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }

        // Center person bounding box (25%-75% X, 15%-85% Y)
        canvas.drawRect(w * 0.25f, h * 0.15f, w * 0.75f, h * 0.85f, paint)
        return mask
    }

    // ── Lifecycle ─────────────────────────────────────
    fun release() {
        engine.releaseAll()
        hexagonEngine.releaseAll()
    }
}
