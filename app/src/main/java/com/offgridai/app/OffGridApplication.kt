package com.offgridai.app

import android.app.Application
import com.offgridai.app.ai.AIRepository
import com.offgridai.app.ai.ModelManager

// ══════════════════════════════════════════════════════
//  OffGrid AI — Application Class
//  Initializes app-level singletons and validates
//  that AI models are present in assets on startup.
// ══════════════════════════════════════════════════════

class OffGridApplication : Application() {

    // Lazy singleton — created only when first accessed
    val aiRepository: AIRepository by lazy { AIRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // Log model readiness on startup (debug only)
        if (BuildConfig.DEBUG) {
            val statuses = ModelManager.checkAllModels(this)
            statuses.forEach { status ->
                if (!status.isPresent) {
                    android.util.Log.w(
                        "OffGridAI",
                        "⚠️ Missing model: ${status.modelName} → ${status.assetPath}"
                    )
                }
            }
            val allReady = statuses.all { it.isPresent }
            android.util.Log.i(
                "OffGridAI",
                if (allReady) "✅ All AI models present" else "❌ Some models missing — see warnings above"
            )
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        // Release ONNX sessions on memory pressure
        aiRepository.release()
    }
}
