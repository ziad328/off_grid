package com.offgridai.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.media.MediaCodecInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.Effects
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.VideoEncoderSettings
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.offgridai.app.ai.video.AiEffect
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import android.os.Handler
import android.os.Looper

// ══════════════════════════════════════════════════════
//  OffGrid AI — Video Processing Pipeline
//
//  Uses AndroidX Media3 Transformer for hardware-accelerated,
//  robust offline video processing, eliminating vendor-specific
//  driver issues (like Xiaomi's libpenguin.so crashes).
// ══════════════════════════════════════════════════════

sealed class VideoProcessingFeature {
    object PersonRemoval    : VideoProcessingFeature()
    object BackgroundRemoval : VideoProcessingFeature()
    object AIEnhance        : VideoProcessingFeature()
}

data class VideoProcessingProgress(
    val currentFrame: Int,
    val totalFrames: Int,
    val elapsedMs: Long,
    val statusMessage: String
) {
    val percent: Float get() = if (totalFrames > 0) currentFrame.toFloat() / totalFrames else 0f
    val estimatedRemainingMs: Long get() {
        if (currentFrame == 0) return 0L
        val msPerFrame = elapsedMs.toFloat() / currentFrame
        return ((totalFrames - currentFrame) * msPerFrame).toLong()
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoProcessingPipeline(
    private val context: Context,
    private val onnxEngine: OnnxInferenceEngine,
    private val hexagonEngine: HexagonInferenceEngine
) {
    private val bgRemoval     by lazy { BackgroundRemovalProcessor(onnxEngine) }
    private val personRemoval by lazy { PersonRemovalProcessor(onnxEngine) }
    private val aiEnhance     by lazy { AIEnhanceProcessor(hexagonEngine) }

    // Used for Temporal Smoothing
    private var previousFrame: Bitmap? = null

    /**
     * Processes a video file frame-by-frame with AI using Media3 Transformer.
     * Emits [VideoProcessingProgress] updates continuously.
     * Returns the output MP4 File on completion.
     */
    fun processVideo(
        inputUri: Uri,
        feature: VideoProcessingFeature,
        outputDir: File,
        maskBitmap: Bitmap? = null
    ): Flow<Any> = callbackFlow {
        val startTime = System.currentTimeMillis()
        var currentFrameCounter = 0
        var estimatedTotalFrames = 100

        trySend(VideoProcessingProgress(0, 100, 0L, "بدء معالجة الفيديو بالذكاء الاصطناعي…"))

        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, inputUri)
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            
            // Try to extract frame rate or assume 30fps
            val fpsStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            val fps = fpsStr?.toFloatOrNull() ?: 30f
            estimatedTotalFrames = ((durationMs / 1000f) * fps).toInt().coerceAtLeast(1)
        } catch (e: Exception) {
            // Ignore if retriever fails
        } finally {
            retriever.release()
        }

        val outputFile = File(outputDir, "offgridai_${System.currentTimeMillis()}.mp4")

        // 1. Define AI Processor Function
        val aiProcessor: suspend (Bitmap) -> Bitmap = { inputBitmap ->
            var frameToProcess = inputBitmap
            // Hardware encoders REQUIRE even dimensions (multiples of 2)
            var newW = inputBitmap.width
            var newH = inputBitmap.height
            if (newW % 2 != 0) newW -= 1
            if (newH % 2 != 0) newH -= 1
            if (newW != inputBitmap.width || newH != inputBitmap.height) {
                frameToProcess = Bitmap.createScaledBitmap(inputBitmap, newW, newH, true)
            }

            var aiResult: Bitmap? = null
            val flow = when (feature) {
                VideoProcessingFeature.BackgroundRemoval -> bgRemoval.process(frameToProcess)
                VideoProcessingFeature.PersonRemoval -> personRemoval.process(frameToProcess, maskBitmap!!)
                VideoProcessingFeature.AIEnhance -> {
                    aiEnhance.process(frameToProcess)
                }
            }

            // Await success result
            val result = flow.first { it is ProcessingResult.Success || it is ProcessingResult.Error }
            if (result is ProcessingResult.Error) {
                throw result.cause ?: Exception(result.message)
            }
            if (result is ProcessingResult.Success) {
                aiResult = result.outputBitmap
            }

            if (frameToProcess != inputBitmap) frameToProcess.recycle()
            if (aiResult == null) throw Exception("AI Processing failed")

            // Temporal Smoothing (Optional step)
            val processed = applyTemporalSmoothing(aiResult, previousFrame)
            previousFrame?.recycle()
            previousFrame = processed.copy(processed.config!!, false)

            if (aiResult != processed) aiResult.recycle()
            
            currentFrameCounter++
            
            val techName = if (feature == VideoProcessingFeature.AIEnhance) "بواسطة Hexagon AI" else ""
            trySend(
                VideoProcessingProgress(
                    currentFrame = currentFrameCounter,
                    totalFrames = estimatedTotalFrames,
                    elapsedMs = System.currentTimeMillis() - startTime,
                    statusMessage = "جاري معالجة الفيديو $techName... (الإطار $currentFrameCounter / $estimatedTotalFrames)"
                )
            )
            
            processed
        }

        // 2. Setup Media3 Transformer (Must be created and interacted with on the Main Thread)
        var isFinished = false
        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(15_000_000) // 15 Mbps for crisp high quality
                    .build()
            )
            .build()

        val transformer = withContext(Dispatchers.Main) {
            Transformer.Builder(context)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        isFinished = true
                        trySend(VideoProcessingProgress(currentFrameCounter, currentFrameCounter, System.currentTimeMillis() - startTime, "اكتملت المعالجة ✓"))
                        trySend(outputFile)
                        close()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        isFinished = true
                        try {
                            trySend(ProcessingResult.Error(ErrorTranslator.translate(exportException, "حدث خطأ أثناء التصدير"), exportException))
                        } catch (e: Exception) {
                            trySend(ProcessingResult.Error("حدث خطأ أثناء التصدير", exportException))
                        }
                        close(exportException)
                    }
                })
                .build()
        }

        val mediaItem = MediaItem.fromUri(inputUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(
                Effects(
                    emptyList(), // audioProcessors
                    listOf(AiEffect(aiProcessor)) // videoEffects
                )
            )
            .build()

        withContext(Dispatchers.Main) {
            transformer.start(editedMediaItem, outputFile.absolutePath)
        }

        awaitClose {
            if (!isFinished) {
                Handler(Looper.getMainLooper()).post {
                    try {
                        transformer.cancel()
                    } catch (e: Exception) {
                        // Ignore cancellation errors
                    }
                }
            }
        }
    }

    private fun applyTemporalSmoothing(currentFrame: Bitmap, previousFrame: Bitmap?): Bitmap {
        return currentFrame
    }
}
