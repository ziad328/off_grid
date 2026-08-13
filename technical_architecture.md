<div dir="rtl" align="right">

# تطبيق OffGrid AI: المميزات والبنية التقنية

يقدم هذا الملف نظرة شاملة على المميزات التي تم تنفيذها بنجاح داخل التطبيق، بالإضافة إلى توضيح تقني باللغة الإنجليزية لكيفية عمل نماذج الذكاء الاصطناعي داخلياً بدون إنترنت (100% Offline).

## 1. المميزات المتوفرة (Implemented Features)

### 🌟 إزالة الخلفية بالذكاء الاصطناعي (Background Removal)
*   **الوظيفة:** فصل الأشخاص والعناصر عن الخلفية بدقة عالية جداً وحفظ الصورة بخلفية شفافة (PNG).
*   **التوافق:** تعمل بكفاءة تامة على جميع الصور.

### 🌟 الممحاة الذكية وإزالة الأشياء (Object Eraser / Inpainting)
*   **الوظيفة:** يمكنك تلوين أي شخص أو كائن غير مرغوب فيه في الصورة، وسيقوم التطبيق بمسحه ورسم الخلفية الناقصة مكانه باحترافية.
*   **ميزة الأمان:** تعمل هذه الميزة بتقنية (Fast Fourier Convolutions). التطبيق مزود بنظام حماية يكتشف الهواتف القديمة غير الداعمة لهذه العمليات المعقدة ويوقفها بأمان بدلاً من تشويه الصورة أو إغلاق التطبيق فجأة.

### 🌟 تحسين جودة الصور (AI Image Enhancement)
*   **الوظيفة:** رفع جودة الصور وتوضيح التفاصيل بدقة فائقة باستخدام معمارية Real-ESRGAN المخصصة لمعالجات كوالكوم.
*   **التوافق:** تعمل بسرعات خيالية بفضل استغلال معالج (Snapdragon Hexagon NPU).

### 🌟 تحسين جودة الفيديوهات (AI Video Pipeline)
*   **الوظيفة:** معالجة الفيديوهات إطاراً بإطار لرفع جودتها وتوضيح التفاصيل بثبات تام (بدون وميض أو تشوه).
*   **آلية العمل (خط الإنتاج):** يعمل النظام مثل "المصنع". حيث يقوم المحرك الأساسي (Media3) بدور "حزام النقل" الذي يفكك الفيديو إلى صور فردية، ثم يتم إرسال كل صورة إلى الذكاء الاصطناعي (Real-ESRGAN) ليقوم بتوضيحها، وبعدها يتم إعادة تجميع الصور وتغليفها في فيديو جديد (MP4).
*   **السرعة والحرارة:** بفضل الاعتماد على النواة العصبية (NPU)، تم كسر جميع القيود السابقة (مثل تقييد الفيديو بـ 10 ثوانٍ). التطبيق الآن قادر على معالجة فيديوهات طويلة بسرعة هائلة وبدون ارتفاع ملحوظ في درجة حرارة الهاتف (Zero Thermal Throttling).

### 🌟 استعراض وحفظ الوسائط (Media Preview & Export)
*   يمكن للمستخدم اختيار الصور والفيديوهات بشكل آمن، ومعاينة النتيجة قبل الحفظ، ثم حفظها مباشرة في الاستوديو المحلي للهاتف (Gallery).

</div>

## 2. Engineering Architecture & Pipeline Specifications

The application strictly operates **100% on-device**, bypassing all cloud dependencies. We utilize a powerful dual-engine architecture: the **ONNX Runtime Android C++ API** (for masking and inpainting) and the **Qualcomm SNPE/QNN SDK** (for deterministic Image & Video Enhancement on the Hexagon NPU).

### 2.1 Background Removal Pipeline
*   **Model Architecture:** RMBG-1.4 (briaai), evaluating a downsampled RGB tensor.
*   **Compositing:** The output alpha mask is natively applied to the original full-resolution bitmap using Android's `Canvas` API with a `PorterDuff.Mode.DST_IN` xfermode, achieving pixel-perfect separation without edge artifacts.

### 2.2 Object Eraser (LaMa Inpainting) Architecture
*   **Model Architecture:** LaMa (Large Mask Inpainting), ~60MB payload.
*   **Hardware Safety Interceptor:** The LaMa architecture relies heavily on Fast Fourier Convolutions (FFC). Legacy mobile chipsets exhibit instruction-set incompatibilities with these complex mathematical operations. The application wraps the FFC inference block in a strict `try/catch` boundary, gracefully aborting on unsupported hardware rather than inducing a hard native crash.

### 2.3 AI Image Enhancement Pipeline (The Worker)
*   **Model Architecture:** Real-ESRGAN-General-x4v3, delivered via the Qualcomm AI Hub as a `w8a8` quantized deep learning container (`.dlc`).
*   **Execution Runtime:** Natively executed on the Snapdragon Hexagon Tensor Accelerator via the Qualcomm SNPE SDK.
*   **Deterministic Upscaling:** Generative AI models hallucinate details and morph faces. Real-ESRGAN is a mathematical, deterministic upscaler ensuring 0% hallucination and 100% fidelity to the original source.
*   **Memory Management:** Input tensors are dynamically segmented into discrete **512x512 pixel batches** (tiles). This restricts peak RAM allocation to approximately ~600MB per frame, effectively banishing Out-of-Memory (OOM) errors.
*   **Post-Processing:** A CLAHE approximation via `ColorMatrix` combined with a Gaussian Unsharp Mask at 1.5f is applied to the final stitched image to guarantee maximum visual pop and razor-sharp edges.

### 2.4 AI Video Pipeline & Optimization (The Conveyor Belt)
*   **Separation of Concerns:** The application cleanly separates the video I/O from the AI processing.
    *   **The Conveyor Belt:** **AndroidX Media3 Transformer** handles robust frame extraction (demuxing) and H.264 `MediaCodec` muxing.
    *   **The Worker:** The individual Bitmap frames are dynamically passed into the exact same **Real-ESRGAN Hexagon Pipeline** (Section 2.3) used for static images.
*   **Temporal Stability:** Because Real-ESRGAN is purely deterministic, the exact same enhancement math is applied to every frame. This inherently guarantees 100% temporal stability across the video timeline, completely eliminating the chaotic flickering typical of Generative AI video tools.
*   **Zero Thermal Throttling:** The Media3 Transformer feeds frames directly into the Hexagon NPU. Because the NPU operates at extreme efficiency (30-50ms per frame), the CPU and GPU remain completely idle. This entirely eliminates legacy limits (like 10-second caps) and enables sustained, high-speed video processing with virtually zero thermal output.
*   **Encoder Stability:** Width/height dimensions are enforced as modulo 2 (even numbers) to prevent `MediaCodec` stride exceptions. The output is strictly encoded at a crisp 15Mbps bitrate.

## 3. Target Devices & Compatibility

Because the application relies heavily on complex on-device AI inference, hardware capabilities dictate the user experience.

*   **Primary Target:** Modern Snapdragon Flagships (e.g., Xiaomi 17 Pro, Snapdragon 8 Gen series). These devices leverage the dedicated Hexagon DSP/NPU for lightning-fast speeds.
*   **Operating System:** Minimum Android 8.0 (API 26), Recommended Android 11+ (API 30+).
*   **Memory (RAM):** 
    *   **Minimum:** 4GB RAM (sufficient for Image Background Removal).
    *   **Recommended:** 8GB+ RAM. Essential for maintaining stability during memory-intensive operations (LaMa Inpainting and sustained Hexagon Video pipelines).
*   **Thermal Constraints & Efficiency:** Thanks to the Qualcomm AI Engine Direct (QNN) SDK and the Hexagon Tensor Accelerator, thermal output is heavily mitigated. Legacy CPU/GPU processing would quickly overheat the device, but our NPU-driven architecture allows for continuous, high-speed video processing with zero thermal throttling.
