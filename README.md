# OffGrid AI — Android Project

> **100% On-Device AI** · No Internet · No Cloud · No API
> Built with Kotlin + Jetpack Compose + ONNX Runtime

---

## 📁 Project Structure

```
d:\offgrid\
├── app/src/main/
│   ├── java/com/offgridai/app/
│   │   ├── MainActivity.kt
│   │   ├── ai/
│   │   │   ├── OnnxInferenceEngine.kt      ← Core AI engine (NNAPI/GPU)
│   │   │   ├── HexagonInferenceEngine.kt   ← SNPE/QNN Engine for AI Enhance
│   │   │   ├── BackgroundRemovalProcessor.kt
│   │   │   ├── PersonRemovalProcessor.kt
│   │   │   ├── AIEnhanceProcessor.kt       ← Uses Hexagon Engine
│   │   │   ├── VideoProcessingPipeline.kt  ← Media3 Hexagon Pipeline
│   │   │   └── AIRepository.kt             ← Dual-Engine facade
│   │   ├── navigation/
│   │   │   ├── Screen.kt
│   │   │   └── OffGridNavGraph.kt
│   │   └── ui/
│   │       ├── theme/
│   │       │   ├── Color.kt    (#E6F2F7 / #0087BF / #80AFED)
│   │       │   ├── Theme.kt
│   │       │   └── Type.kt
│   │       ├── screens/
│   │       │   ├── SplashScreen.kt
│   │       │   ├── HomeScreen.kt
│   │       │   ├── ProcessingScreen.kt
│   │       │   ├── PreviewScreen.kt
│   │       │   └── PrivacyScreen.kt
│   │       └── components/
│   │           ├── OffGridButton.kt
│   │           └── MediaPickerCard.kt
│   ├── res/
│   │   ├── values/
│   │   │   ├── strings.xml
│   │   │   ├── colors.xml
│   │   │   └── themes.xml
│   │   └── xml/
│   │       ├── network_security_config.xml ← Blocks ALL network traffic
│   │       ├── backup_rules.xml
│   │       └── data_extraction_rules.xml
│   └── AndroidManifest.xml                 ← NO INTERNET permission
└── app/src/main/assets/
    └── models/                             ← ⚠️ Add ONNX & DLC models here!
        ├── rmbg_14_int8.onnx               (Background Removal)
        ├── yolov8n_seg_int8.onnx           (Person Detection)
        ├── lama_mobile_fp16.onnx           (Person Removal/Inpainting)
        └── real_esrgan_general_x4v3.dlc    (AI Enhance - Hexagon NPU)
```

---

## ⚙️ Setup Instructions

### 1. Open in Android Studio
```
File → Open → Select d:\offgrid\
```

### 2. Add ONNX Model Files (Required for AI Features)

Place the following model files into:
```
app/src/main/assets/models/
```

| File | Size (approx.) | Download From |
|---|---|---|
| `rmbg_14_int8.onnx` | ~45 MB | [briaai/RMBG-1.4](https://huggingface.co/briaai/RMBG-1.4) |
| `yolov8n_seg_int8.onnx` | ~6 MB | [ultralytics/assets](https://github.com/ultralytics/assets) |
| `lama_mobile_fp16.onnx` | ~50 MB | [advimman/lama](https://github.com/advimman/lama) |
| `real_esrgan_general_x4v3.dlc` | ~12 MB | [Qualcomm AI Hub](https://aihub.qualcomm.com/) |

> **Note:** Models run 100% locally. Once bundled in assets, no internet is needed.

### 3. Build & Run
```bash
./gradlew assembleDebug
```
Or press **Run** in Android Studio.

---

## 🎨 Brand Colors

| Role | Hex | Usage |
|---|---|---|
| Background | `#E6F2F7` | All screens & menus |
| Primary / Buttons | `#0087BF` | CTAs, active elements |
| Text & Icons | `#80AFED` | Body text, secondary icons |

---

## 🔐 Privacy & Offline Guarantee

- ❌ **No `android.permission.INTERNET`** in AndroidManifest.xml
- ❌ **No network security trust anchors** in `network_security_config.xml`
- ❌ **No analytics / tracking / ads SDKs**
- ✅ **All AI Models (ONNX & DLC) bundled locally** — no download at runtime
- ✅ **All processing on Snapdragon NPU/GPU via NNAPI & QNN SDK**
- ✅ **Source code fully independent** — you own your data

---

## 📱 Target Devices & Hardware Limitations

| Property | Value |
|---|---|
| Primary Target | Xiaomi 17 Pro |
| Legacy Testing | Samsung S20 FE (Snapdragon 865) |
| Min SDK | Android 8.0 (API 26) |
| Target SDK | Android 15 (API 35) |
| AI Acceleration | Snapdragon Hexagon NPU via SNPE/QNN SDK & NNAPI |
| Video Engine | AndroidX Media3 (Extractor) + Hexagon NPU (Enhancer) |

> **Note:** For deep technical details on how the offline AI models process data and their hardware limitations, please read our [Technical Architecture Guide](technical_architecture.md).

---

## 🧠 Technical Description

OffGrid AI is a robust, privacy-first Android application designed to execute complex artificial intelligence models entirely on-device. The application utilizes a powerful dual-engine architecture: **ONNX Runtime** (for masking and inpainting) and the **Qualcomm SNPE/QNN SDK** (for Image & Video Enhancement).

### The AI Video Assembly Line
For high-resolution video processing, the application utilizes a highly optimized "factory" pipeline that cleanly separates video I/O from AI inference:
- **The Conveyor Belt (AndroidX Media3):** The robust Media3 Transformer demuxes the video file, extracting each individual frame perfectly without exhausting the JVM heap.
- **The Worker (Snapdragon Hexagon NPU):** Each extracted frame is instantly passed to the Hexagon Tensor Accelerator, where the Real-ESRGAN model mathematically enhances it in ~30ms. 
- **The Result:** Because Real-ESRGAN is deterministic (unlike generative AI), every frame is enhanced with 100% temporal stability (zero flickering). Because the NPU is so incredibly efficient, videos can be processed at blazing speeds with virtually **zero thermal throttling** and absolutely no length limits.

### Memory & Hardware Safety
The ONNX engine utilizes C++ JNI bridges and `ByteBuffer.allocateDirect()` with native byte ordering to securely transfer massive multi-dimensional image arrays. Additionally, complex mathematical operations—such as the Fast Fourier Convolutions (FFC) required by the LaMa Object Eraser—are protected by a strict **Hardware Safety Interceptor**. This prevents catastrophic native crashes on legacy mobile chipsets that lack support for advanced tensor calculations.

---

## 🗺️ Development Phases (Completed)

- [x] **Phase 1: Foundation** — Project structure, UI theme, screens, and AI engine skeleton (ONNX Runtime + Direct Memory Buffers)
- [x] **Phase 2: AI Integration** — Background Removal (Alpha channels) and HardwareTuner optimization (CPU/NNAPI/XNNPACK)
- [x] **Phase 3: Object Eraser** — Advanced LaMa Inpainting integration with FFC Hardware Safety Interceptor
- [x] **Phase 4: Image Enhancement** — High-resolution image upscaling with Real-ESRGAN-General-x4v3 (Qualcomm AI Hub DLC) natively on the Hexagon NPU, featuring tile-based processing and CLAHE/USM post-processing
- [x] **Phase 5: Video Pipeline & Optimization** — Media3 Transformer frame-by-frame processing powered by Hexagon Tensor Cores for temporal stability, zero flickering, and lightning-fast upscale
- [x] **Phase 6: Polish & Refinement** — UI improvements, edge-case crash prevention, vector graphics, and Final App Assembly

---

*App Name: offgridai | Developer: Ziad Seleem*
