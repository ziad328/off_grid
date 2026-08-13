package com.offgridai.app.ai

object ErrorTranslator {
    /**
     * Translates cryptic native crashes and system exceptions into user-friendly Arabic messages.
     */
    fun translate(throwable: Throwable?, defaultMsg: String): String {
        if (throwable == null) return defaultMsg
        
        val msg = throwable.message ?: ""
        val fullTrace = throwable.stackTraceToString()
        
        // 1. Hardware Incompatibility (Qualcomm SNPE)
        if (msg.contains("73") && msg.contains("None of the selected runtime targets")) {
            return "هذا الجهاز غير مدعوم. المعالجة الفائقة تتطلب معالج (Snapdragon) حصرياً."
        }
        
        // 2. Out of Memory (OOM)
        if (throwable is OutOfMemoryError || msg.contains("Failed to allocate") || fullTrace.contains("OutOfMemoryError")) {
            return "نفدت ذاكرة الهاتف العشوائية (RAM). الصورة كبيرة جداً، يرجى إغلاق التطبيقات الأخرى والمحاولة مجدداً."
        }
        
        // 3. Tensor Dimension Mismatch
        if (msg.contains("unexpected dimension") || msg.contains("InvalidInputException")) {
            return "حدث خطأ في أبعاد الصورة المدخلة للنموذج. لم يتم التعرف على المقاس."
        }
        
        // 4. Missing Model / Load Failure
        if (msg.contains("Failed to build Hexagon") || msg.contains("Failed to load")) {
            return "فشل تحميل محرك الذكاء الاصطناعي. يرجى التأكد من أن الهاتف يدعم هذه الميزة."
        }
        
        return "$defaultMsg : ${msg.take(100)}" // Fallback but shortened to prevent massive dialogs
    }
}
