package com.offgridai.app.ui.screens

import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offgridai.app.ai.AIRepository
import com.offgridai.app.ai.ModelManager
import com.offgridai.app.navigation.AIFeature
import com.offgridai.app.utils.ImageUtils
import com.offgridai.app.ui.theme.*
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════
//  OffGrid AI — Processing Screen (Phase 2 — Wired)
//  Uses real AIRepository + ProcessingViewModel.
//  Shows ModelManager status if models are missing.
// ══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    mediaUri: String,
    featureType: String,
    onDone: (resultUri: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val feature = runCatching { AIFeature.valueOf(featureType) }.getOrElse { AIFeature.AI_ENHANCE }

    // Build ViewModel with real AIRepository
    val aiRepository = remember { AIRepository(context) }
    val viewModel: ProcessingViewModel = viewModel(
        factory = ProcessingViewModel.Factory(aiRepository)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Check if required models exist
    val modelReady = remember { ModelManager.isFeatureReady(context, feature) }

    val isVideo = remember(mediaUri) {
        val type = context.contentResolver.getType(Uri.parse(mediaUri))
        type?.startsWith("video") == true || mediaUri.contains("video", ignoreCase = true) || mediaUri.endsWith(".mp4", ignoreCase = true)
    }

    // Start processing once model is confirmed present
    LaunchedEffect(mediaUri, featureType) {
        if (!modelReady) return@LaunchedEffect

        val uri = Uri.parse(mediaUri)
        android.util.Log.d("OffGridAI", "ProcessingScreen started for uri: $uri, isVideo: $isVideo")

        if (isVideo) {
            viewModel.processVideo(uri, feature)
        } else {
            val bitmap = ImageUtils.loadBitmapFromUri(context, uri)
            if (bitmap == null) {
                android.util.Log.e("OffGridAI", "Failed to load bitmap from uri: $uri, attempting video fallback...")
                // Fallback: If it failed to load as an image, it might be a video with a missing MIME type!
                viewModel.processVideo(uri, feature)
            } else {
                if (feature == AIFeature.PERSON_REMOVAL) {
                    val maskFile = java.io.File(context.cacheDir, "current_mask.png")
                    if (maskFile.exists()) {
                        val maskBmp = android.graphics.BitmapFactory.decodeFile(maskFile.absolutePath)
                        viewModel.processImage(bitmap, feature, maskBmp)
                    } else {
                        viewModel.processImage(bitmap, feature)
                    }
                } else {
                    viewModel.processImage(bitmap, feature)
                }
            }
        }
    }

    // Navigate to Preview when done
    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) {
            val resultUri = if (uiState.resultBitmap != null) {
                if (feature == AIFeature.BACKGROUND_REMOVAL) {
                    ImageUtils.saveBitmapToCachePng(context, uiState.resultBitmap!!)
                } else {
                    ImageUtils.saveBitmapToCache(context, uiState.resultBitmap!!)
                }
            } else if (uiState.resultFileUri != null) {
                uiState.resultFileUri
            } else {
                Uri.parse(mediaUri)
            }
            resultUri?.let { onDone(it.toString()) }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Scaffold(
        containerColor = OffGridBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = feature.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(color = OffGridOnBackground)
                    )
                },
                navigationIcon = {
                    if (!uiState.isComplete) {
                        IconButton(onClick = {
                            viewModel.cancelProcessing()
                            onBack()
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = "إلغاء", tint = OffGridText)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OffGridBackground)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            // ── Models Missing Warning ───────────────
            if (!modelReady) {
                ModelsMissingCard(feature = feature)
                return@Box
            }

            // ── Error State ──────────────────────────
            if (uiState.isError) {
                ErrorCard(
                    message = uiState.errorMessage,
                    onRetry = { onBack() }
                )
                return@Box
            }

            // ── Normal Processing UI ─────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // Animated icon
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            OffGridProgressBg.copy(
                                alpha = if (uiState.isComplete) 1f else pulseAlpha
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isComplete) Icons.Outlined.CheckCircle
                                      else Icons.Outlined.Memory,
                        contentDescription = null,
                        tint     = OffGridPrimary,
                        modifier = Modifier.size(52.dp)
                    )
                }

                // Feature description
                Text(
                    text      = feature.descriptionAr,
                    style     = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center
                )

                // Progress bar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LinearProgressIndicator(
                        progress          = { uiState.progress },
                        modifier          = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        color             = OffGridPrimary,
                        trackColor        = OffGridProgressBg,
                        strokeCap         = StrokeCap.Round
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text  = "${(uiState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(color = OffGridPrimary)
                        )
                    }
                }

                // Processing time (shown when complete)
                if (uiState.isComplete && uiState.processingTimeMs > 0) {
                    Text(
                        text  = "⏱ ${uiState.processingTimeMs / 1000.0} ثانية",
                        style = MaterialTheme.typography.bodySmall.copy(color = OffGridPrimary)
                    )
                }

                // Offline badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(OffGridSurfaceVar)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Outlined.WifiOff, null, tint = OffGridText, modifier = Modifier.size(14.dp))
                    Text(
                        text  = "المعالجة على المعالج العصبي NPU • بدون إنترنت",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

// ── Models Missing Card ──────────────────────────────
@Composable
private fun ModelsMissingCard(feature: AIFeature) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(OffGridSurface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Outlined.Warning,
            contentDescription = null,
            tint = OffGridPrimary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text  = "نماذج الذكاء الاصطناعي غير موجودة",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Text(
            text = "يجب إضافة ملفات النماذج (.onnx) إلى:\n" +
                   "app/src/main/assets/models/\n\n" +
                   "راجع ملف README.md للتعليمات الكاملة.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

// ── Error Card ────────────────────────────────────────
@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(OffGridSurface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = OffGridError, modifier = Modifier.size(48.dp))
        Text(
            text  = "حدث خطأ أثناء المعالجة",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(text = message, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onRetry,
            colors  = ButtonDefaults.buttonColors(containerColor = OffGridPrimary),
            shape   = RoundedCornerShape(12.dp)
        ) {
            Text("رجوع والمحاولة مجدداً")
        }
    }
}
