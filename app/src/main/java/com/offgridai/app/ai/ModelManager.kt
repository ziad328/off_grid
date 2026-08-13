package com.offgridai.app.ai

import android.content.Context
import android.content.res.AssetManager

// ══════════════════════════════════════════════════════
//  OffGrid AI — Model Manager
//  Verifies that all required ONNX model files exist
//  in the app's assets/models/ directory before processing.
//  Provides human-readable status for each model.
// ══════════════════════════════════════════════════════

data class ModelStatus(
    val modelName: String,
    val assetPath: String,
    val isPresent: Boolean,
    val fileSizeMb: Float,
    val feature: String
)

object ModelManager {

    // All required model files
    private val REQUIRED_MODELS = listOf(
        Triple(
            "RMBG-1.4 (Background Removal)",
            BackgroundRemovalProcessor.MODEL_PATH,
            "إزالة الخلفية"
        ),
        Triple(
            "YOLOv8-Seg (Person Detection)",
            PersonRemovalProcessor.SEG_MODEL_PATH,
            "إزالة الأشخاص — الكشف"
        ),
        Triple(
            "LaMa Mobile (Inpainting)",
            PersonRemovalProcessor.INPAINT_MODEL_PATH,
            "إزالة الأشخاص — التعبئة"
        ),
        Triple(
            "Anime V3 (Enhance)",
            AIEnhanceProcessor.MODEL_PATH,
            "تحسين الجودة"
        )
    )

    /**
     * Checks which models are present in assets.
     * Returns list of [ModelStatus] for each required model.
     */
    fun checkAllModels(context: Context): List<ModelStatus> {
        val assets: AssetManager = context.assets
        return REQUIRED_MODELS.map { (name, path, feature) ->
            val (present, sizeMb) = checkModel(assets, path)
            ModelStatus(
                modelName  = name,
                assetPath  = path,
                isPresent  = present,
                fileSizeMb = sizeMb,
                feature    = feature
            )
        }
    }

    /**
     * Returns true if ALL required models are present.
     * Use this to gate AI processing in the UI.
     */
    fun areAllModelsReady(context: Context): Boolean =
        checkAllModels(context).all { it.isPresent }

    /**
     * Returns true if models required for a specific feature are present.
     */
    fun isFeatureReady(context: Context, feature: com.offgridai.app.navigation.AIFeature): Boolean {
        val assets = context.assets
        return when (feature) {
            com.offgridai.app.navigation.AIFeature.BACKGROUND_REMOVAL ->
                checkModel(assets, BackgroundRemovalProcessor.MODEL_PATH).first
            com.offgridai.app.navigation.AIFeature.AI_ENHANCE ->
                checkModel(assets, AIEnhanceProcessor.MODEL_PATH).first
            com.offgridai.app.navigation.AIFeature.PERSON_REMOVAL ->
                checkModel(assets, PersonRemovalProcessor.SEG_MODEL_PATH).first &&
                checkModel(assets, PersonRemovalProcessor.INPAINT_MODEL_PATH).first
        }
    }

    // ── Internal ──────────────────────────────────────
    private fun checkModel(assets: AssetManager, path: String): Pair<Boolean, Float> {
        return try {
            val stream = assets.open(path)
            val bytes = stream.available()
            stream.close()
            Pair(true, bytes / (1024f * 1024f))
        } catch (e: Exception) {
            Pair(false, 0f)
        }
    }
}
