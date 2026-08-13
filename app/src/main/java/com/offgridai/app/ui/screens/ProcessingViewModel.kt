package com.offgridai.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.offgridai.app.ai.AIRepository
import com.offgridai.app.ai.ProcessingResult
import com.offgridai.app.navigation.AIFeature
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

// ══════════════════════════════════════════════════════
//  OffGrid AI — Processing ViewModel
//  Bridges ProcessingScreen ↔ AIRepository.
//  Survives configuration changes (rotation, etc.)
// ══════════════════════════════════════════════════════

data class ProcessingUiState(
    val progress: Float         = 0f,
    val statusMessage: String   = "",
    val isComplete: Boolean     = false,
    val isError: Boolean        = false,
    val errorMessage: String    = "",
    val resultBitmap: Bitmap?   = null,
    val resultFileUri: android.net.Uri? = null,
    val processingTimeMs: Long  = 0L
)

class ProcessingViewModel(
    private val aiRepository: AIRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProcessingUiState())
    val uiState: StateFlow<ProcessingUiState> = _uiState.asStateFlow()

    private var processingJob: Job? = null
    
    // Use a single-threaded dispatcher for all AI inference to prevent ONNX/XNNPACK native memory corruption 
    // caused by coroutine thread-hopping (TLS corruption on Xiaomi 17 Pro).
    private val inferenceDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    // ── Start Image Processing ────────────────────────
    fun processImage(
        bitmap: Bitmap,
        feature: AIFeature,
        maskBitmap: Bitmap? = null
    ) {
        processingJob?.cancel()
        _uiState.value = ProcessingUiState(progress = 0f, statusMessage = "جارٍ البدء…")

        processingJob = viewModelScope.launch {
            aiRepository.processImage(bitmap, feature, maskBitmap)
                .flowOn(inferenceDispatcher)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isError      = true,
                        errorMessage = e.message ?: "خطأ غير معروف"
                    )
                }
                .collect { result ->
                    when (result) {
                        is ProcessingResult.Progress -> {
                            _uiState.value = _uiState.value.copy(
                                progress      = result.percent,
                                statusMessage = result.statusMessage
                            )
                        }
                        is ProcessingResult.Success -> {
                            _uiState.value = _uiState.value.copy(
                                progress         = 1f,
                                isComplete       = true,
                                resultBitmap     = result.outputBitmap,
                                processingTimeMs = result.processingTimeMs,
                                statusMessage    = "اكتملت المعالجة ✓"
                            )
                        }
                        is ProcessingResult.Error -> {
                            android.util.Log.e("OffGridAI", "Image processing error: ${result.message}", result.cause)
                            _uiState.value = _uiState.value.copy(
                                isError      = true,
                                errorMessage = result.message
                            )
                        }
                    }
                }
        }
    }

    // ── Start Video Processing ────────────────────────
    fun processVideo(
        videoUri: Uri,
        feature: AIFeature,
        maskBitmap: Bitmap? = null
    ) {
        processingJob?.cancel()
        _uiState.value = ProcessingUiState(progress = 0f, statusMessage = "بدء معالجة الفيديو…")
        android.util.Log.d("OffGridAI", "Processing video: $videoUri with feature: $feature")

        processingJob = viewModelScope.launch {
            aiRepository.processVideo(videoUri, feature, maskBitmap)
                .flowOn(inferenceDispatcher)
                .catch { e ->
                    android.util.Log.e("OffGridAI", "Video processing error: ${e.message}", e)
                    _uiState.value = _uiState.value.copy(
                        isError      = true,
                        errorMessage = "فشل معالجة الفيديو: ${e.message}"
                    )
                }
                .collect { progressObj ->
                    android.util.Log.e("OffGridAI", "ViewModel collected event: ${progressObj.javaClass.simpleName}")
                    if (progressObj is com.offgridai.app.ai.VideoProcessingProgress) {
                        _uiState.value = _uiState.value.copy(
                            progress      = progressObj.percent,
                            statusMessage = progressObj.statusMessage
                        )
                    } else if (progressObj is java.io.File) {
                        _uiState.value = _uiState.value.copy(
                            progress         = 1f,
                            isComplete       = true,
                            resultBitmap     = null,
                            resultFileUri    = android.net.Uri.fromFile(progressObj),
                            statusMessage    = "اكتملت معالجة الفيديو ✓"
                        )
                    } else if (progressObj is ProcessingResult.Error) {
                        android.util.Log.e("OffGridAI", "ViewModel setting error state: ${progressObj.message}")
                        _uiState.value = _uiState.value.copy(
                            isError      = true,
                            errorMessage = progressObj.message
                        )
                    }
                }
        }
    }

    // ── Cancel Processing ─────────────────────────────
    fun cancelProcessing() {
        processingJob?.cancel()
        _uiState.value = ProcessingUiState()
    }

    override fun onCleared() {
        super.onCleared()
        // Delay releasing the ONNX engine to allow Media3 Transformer
        // to finish its asynchronous cancellation and stop processing frames.
        // This prevents the "pthread_mutex_lock called on a destroyed mutex" crash.
        kotlinx.coroutines.GlobalScope.launch {
            kotlinx.coroutines.delay(1500)
            aiRepository.release()
        }
    }

    // ── Set Error State manually ────────────────────────
    fun setError(message: String) {
        processingJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isError = true,
            errorMessage = message
        )
    }

    // ── ViewModelFactory ──────────────────────────────
    class Factory(private val aiRepository: AIRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ProcessingViewModel::class.java)) {
                return ProcessingViewModel(aiRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
