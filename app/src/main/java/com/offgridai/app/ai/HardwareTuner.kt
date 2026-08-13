package com.offgridai.app.ai

import android.content.Context
import android.os.Build
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.providers.NNAPIFlags
import java.io.File

// ══════════════════════════════════════════════════════
//  OffGrid AI — Hardware Tuner for Xiaomi 17 Pro
//  Optimizes ONNX Runtime session options for Snapdragon NPU/GPU
//  and manages device-specific performance profiles.
// ══════════════════════════════════════════════════════

enum class PerformanceProfile {
    HIGH_PERFORMANCE,  // Xiaomi 17 Pro / Snapdragon 8 Elite / Gen 3 — Full NPU + FP16
    BALANCED,          // Standard mid-range devices — NPU + CPU fallback
    BATTERY_SAVER      // CPU only, 2 threads
}

object HardwareTuner {

    /**
     * Auto-detects the hardware capability of the current device.
     * Xiaomi 17 Pro (Snapdragon 8 Elite/Gen 3, 12GB+ RAM, ARM64) -> HIGH_PERFORMANCE
     */
    fun detectPerformanceProfile(): PerformanceProfile {
        val totalRamGb = getTotalRamGb()
        val isArm64    = Build.SUPPORTED_ABIS.contains("arm64-v8a")
        val cpuCores   = Runtime.getRuntime().availableProcessors()

        return when {
            isArm64 && totalRamGb >= 8 && cpuCores >= 8 -> PerformanceProfile.HIGH_PERFORMANCE
            isArm64 && totalRamGb >= 4                 -> PerformanceProfile.BALANCED
            else                                       -> PerformanceProfile.BATTERY_SAVER
        }
    }

    /**
     * Applies Xiaomi 17 Pro optimized SessionOptions to an ONNX Session.
     */
    fun applyOptimizations(options: SessionOptions, profile: PerformanceProfile, useNNAPI: Boolean = true) {
        when (profile) {
            PerformanceProfile.HIGH_PERFORMANCE -> {
                // Maximum performance for Xiaomi 17 Pro Hexagon NPU
                options.setIntraOpNumThreads(4)
                options.setInterOpNumThreads(2)
                options.setOptimizationLevel(SessionOptions.OptLevel.BASIC_OPT) // Downgraded to BASIC_OPT to prevent FP16 UNet underflow on CPU
                options.addConfigEntry("session.disable_xnnpack", "1") // Prevent XNNPACK from corrupting FFC layers in LaMa

                // NNAPI Delegate — uses Hexagon NPU on Xiaomi 17 Pro
                if (useNNAPI) options.addNnapi()
            }
            PerformanceProfile.BALANCED -> {
                options.setIntraOpNumThreads(2)
                options.setInterOpNumThreads(1)
                options.setOptimizationLevel(SessionOptions.OptLevel.BASIC_OPT)
                options.addConfigEntry("session.disable_xnnpack", "1")
                if (useNNAPI) options.addNnapi()
            }
            PerformanceProfile.BATTERY_SAVER -> {
                options.setIntraOpNumThreads(2)
                options.setInterOpNumThreads(1)
                options.setOptimizationLevel(SessionOptions.OptLevel.BASIC_OPT)
                options.addConfigEntry("session.disable_xnnpack", "1")
            }
        }
    }

    /**
     * Helper to read total system RAM in GB.
     */
    private fun getTotalRamGb(): Int {
        return try {
            val memInfo = File("/proc/meminfo").readLines()
            val totalKbLine = memInfo.firstOrNull { it.startsWith("MemTotal:") } ?: return 4
            val kb = totalKbLine.replace(Regex("[^0-9]"), "").toLong()
            (kb / (1024 * 1024)).toInt()
        } catch (e: Exception) {
            4
        }
    }
}
