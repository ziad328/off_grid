package com.offgridai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offgridai.app.ai.ModelManager
import com.offgridai.app.ai.ModelStatus
import com.offgridai.app.ui.theme.*

// ══════════════════════════════════════════════════════
//  OffGrid AI — Model Setup Screen
//  Shown when models are missing. Displays status for each
//  model and guides the developer to add them correctly.
// ══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSetupScreen(onBack: () -> Unit) {
    val context     = LocalContext.current
    val modelStatus = remember { ModelManager.checkAllModels(context) }
    val allReady    = modelStatus.all { it.isPresent }

    Scaffold(
        containerColor = OffGridBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "إعداد النماذج",
                        style = MaterialTheme.typography.titleMedium.copy(color = OffGridOnBackground)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBackIosNew, null, tint = OffGridText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OffGridBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Status Banner ────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (allReady) OffGridProgressBg else OffGridSurfaceVar)
                    .padding(16.dp),
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (allReady) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = OffGridPrimary,
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        text  = if (allReady) "كل النماذج جاهزة ✅" else "بعض النماذج مفقودة",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text  = "ملفات .onnx في: assets/models/",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ── Each Model Status ─────────────────────
            Text(
                text  = "حالة النماذج",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            modelStatus.forEach { status ->
                ModelStatusCard(status)
            }

            // ── Instructions ──────────────────────────
            if (!allReady) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "طريقة إضافة النماذج",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OffGridSurface)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InstructionStep("1", "شغّل سكريبت Python على جهاز الكمبيوتر:")
                    CodeBlock("python scripts/prepare_models.py")
                    InstructionStep("2", "أو أضف الملفات يدوياً في مسار:")
                    CodeBlock("app/src/main/assets/models/")
                    InstructionStep("3", "ابنِ التطبيق من جديد في Android Studio:")
                    CodeBlock("Build → Make Project  (Ctrl+F9)")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModelStatusCard(status: ModelStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(OffGridSurface)
            .padding(14.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (status.isPresent) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
            contentDescription = null,
            tint = if (status.isPresent) OffGridSuccess else OffGridError,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = status.modelName,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = OffGridOnBackground
                )
            )
            Text(
                text  = status.feature,
                style = MaterialTheme.typography.labelSmall
            )
            if (status.isPresent) {
                Text(
                    text  = "%.1f MB".format(status.fileSizeMb),
                    style = MaterialTheme.typography.labelSmall.copy(color = OffGridSuccess)
                )
            } else {
                Text(
                    text  = status.assetPath,
                    style = MaterialTheme.typography.labelSmall.copy(color = OffGridError)
                )
            }
        }
    }
}

@Composable
private fun InstructionStep(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(OffGridPrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(number, style = MaterialTheme.typography.labelSmall.copy(color = OffGridOnPrimary))
        }
        Text(text, style = MaterialTheme.typography.bodySmall.copy(color = OffGridOnBackground))
    }
}

@Composable
private fun CodeBlock(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OffGridBackground)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(code, style = MaterialTheme.typography.labelSmall.copy(color = OffGridPrimary))
    }
}
