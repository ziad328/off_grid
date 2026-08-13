package com.offgridai.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offgridai.app.navigation.AIFeature
import com.offgridai.app.ui.components.*
import com.offgridai.app.ui.theme.*

// ══════════════════════════════════════════════════════
//  OffGrid AI — Home Screen
//  1) Pick Image or Video
//  2) Select AI Feature
//  3) Tap Process → go to ProcessingScreen
// ══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToProcessing: (uri: String, feature: String) -> Unit,
    onNavigateToBrushCanvas: (uri: String) -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToModelSetup: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedUri     by remember { mutableStateOf<Uri?>(null) }
    var isVideo         by remember { mutableStateOf(false) }
    var mediaLabel      by remember { mutableStateOf("") }
    var selectedFeature by remember { mutableStateOf<AIFeature?>(null) }
    var errorMessage    by remember { mutableStateOf<String?>(null) }
    val allModelsReady  = remember { com.offgridai.app.ai.ModelManager.areAllModelsReady(context) }

    // Image picker
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            selectedUri   = it
            isVideo       = false
            mediaLabel    = "صورة محددة ✓"
        }
    }

    // Video picker (universal GetContent contract)
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, it)
                val timeStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = timeStr?.toLongOrNull() ?: 0L
                if (durationMs <= 10000) {
                    selectedUri   = it
                    isVideo       = true
                    mediaLabel    = "فيديو محدد ✓"
                    errorMessage  = null
                    if (selectedFeature != AIFeature.AI_ENHANCE) {
                        selectedFeature = null
                    }
                } else {
                    selectedUri   = null
                    mediaLabel    = ""
                    errorMessage  = "يرجى اختيار فيديو لا تتجاوز مدته 10 ثوانٍ (المدة الحالية: ${durationMs / 1000} ثانية)"
                }
            } catch (e: Exception) {
                selectedUri   = null
                mediaLabel    = ""
                errorMessage  = "لا يمكن قراءة ملف الفيديو"
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }
        }
    }

    val features = remember {
        listOf(
            FeatureCardData(
                title         = "إزالة الأشخاص/الأشياء",
                descriptionAr = "احذف أشخاصاً أو أشياء غير مرغوب فيها مع الحفاظ على الخلفية",
                icon          = Icons.Outlined.PersonRemove,
                featureKey    = AIFeature.PERSON_REMOVAL.name
            ),
            FeatureCardData(
                title         = "إزالة الخلفية",
                descriptionAr = "فرّغ الخلفية تماماً وحافظ على الشخص أو الكائن الرئيسي",
                icon          = Icons.Outlined.AutoFixHigh,
                featureKey    = AIFeature.BACKGROUND_REMOVAL.name
            ),
            FeatureCardData(
                title         = "تحسين الجودة",
                descriptionAr = "رفع دقة الصور والفيديو وزيادة حدة التفاصيل بالذكاء الاصطناعي",
                icon          = Icons.Outlined.HighQuality,
                featureKey    = AIFeature.AI_ENHANCE.name
            )
        )
    }

    val canProcess = selectedUri != null && selectedFeature != null

    Scaffold(
        containerColor = OffGridBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = com.offgridai.app.R.drawable.logo),
                            contentDescription = "offgridai logo",
                            modifier = Modifier.height(32.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToPrivacy) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = "الخصوصية والأمان",
                            tint = OffGridPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OffGridBackground
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OffGridBackground)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                OffGridPrimaryButton(
                    text    = "ابدأ المعالجة",
                    enabled = canProcess,
                    onClick = {
                        selectedUri?.let { uri ->
                            selectedFeature?.let { feature ->
                                if (feature == AIFeature.PERSON_REMOVAL) {
                                    onNavigateToBrushCanvas(uri.toString())
                                } else {
                                    onNavigateToProcessing(uri.toString(), feature.name)
                                }
                            }
                        }
                    },
                    icon = Icons.Outlined.AutoFixHigh
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Section: Pick Media ──────────────────────
            Text(
                text  = "اختر الملف",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            MediaPickerCard(
                mediaUri    = selectedUri,
                isVideo     = isVideo,
                mediaLabel  = mediaLabel,
                onPickImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onPickVideo = {
                    videoPicker.launch("video/*")
                }
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // ── Section: Choose AI Feature ───────────────
            Text(
                text  = "اختر الميزة",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            features.forEach { featureData ->
                val isFeatureSupported = if (isVideo && selectedUri != null) {
                    featureData.featureKey == AIFeature.AI_ENHANCE.name
                } else {
                    true
                }
                
                FeatureCard(
                    data           = featureData,
                    isSelected     = selectedFeature?.name == featureData.featureKey,
                    enabled        = isFeatureSupported,
                    disabledReason = if (!isFeatureSupported) "للصور فقط" else null,
                    onClick        = {
                        selectedFeature = AIFeature.valueOf(featureData.featureKey)
                    }
                )
            }

            // ── Offline Badge ───────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.WifiOff,
                    contentDescription = null,
                    tint     = OffGridSubtle,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text  = "جميع المعالجات تتم محلياً • بدون إنترنت",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
