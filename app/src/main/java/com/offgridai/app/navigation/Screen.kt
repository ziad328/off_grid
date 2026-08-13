package com.offgridai.app.navigation

// ══════════════════════════════════════════════════════
//  OffGrid AI — Navigation Routes
// ══════════════════════════════════════════════════════

sealed class Screen(val route: String) {
    object Splash      : Screen("splash")
    object Home        : Screen("home")
    object Processing  : Screen("processing/{mediaUri}/{featureType}") {
        fun createRoute(mediaUri: String, featureType: String) =
            "processing/${android.net.Uri.encode(mediaUri)}/$featureType"
    }
    object Preview     : Screen("preview/{resultUri}") {
        fun createRoute(resultUri: String) =
            "preview/${android.net.Uri.encode(resultUri)}"
    }
    object Privacy     : Screen("privacy")
    object ModelSetup  : Screen("model_setup")
    object BrushCanvas : Screen("brush_canvas/{imageUri}") {
        fun createRoute(imageUri: String) =
            "brush_canvas/${android.net.Uri.encode(imageUri)}"
    }
}

// AI Feature types passed to ProcessingScreen
enum class AIFeature(val displayName: String, val descriptionAr: String) {
    PERSON_REMOVAL(
        displayName   = "Person/Object Removal",
        descriptionAr = "إزالة الأشخاص/الأشياء مع الحفاظ على الخلفية"
    ),
    BACKGROUND_REMOVAL(
        displayName   = "Background Removal",
        descriptionAr = "إزالة الخلفية وتفريغها"
    ),
    AI_ENHANCE(
        displayName   = "AI Enhance",
        descriptionAr = "تحسين جودة الصورة والفيديو"
    )
}
