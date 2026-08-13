package com.offgridai.app.ui.screens

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.offgridai.app.ui.components.OffGridPrimaryButton
import com.offgridai.app.ui.components.OffGridSecondaryButton
import com.offgridai.app.ui.theme.*

// ══════════════════════════════════════════════════════
//  OffGrid AI — Preview & Save Screen
//  Shows the processed result and lets user save to gallery
// ══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    resultUri: String,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    val context = LocalContext.current
    var isSaving by remember { mutableStateOf(false) }
    var isSaved  by remember { mutableStateOf(false) }
    val isVideo = resultUri.endsWith(".mp4", ignoreCase = true)

    Scaffold(
        containerColor = OffGridBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = "النتيجة",
                        style = MaterialTheme.typography.titleMedium.copy(color = OffGridOnBackground)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "رجوع", tint = OffGridText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OffGridBackground)
            )
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

            // ── Result Preview ───────────────────────────
            var isPlaying by remember { mutableStateOf(false) }
            val videoRatio = remember(resultUri) {
                var ratio = 4f / 3f // default fallback
                if (isVideo) {
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(context, Uri.parse(resultUri))
                        val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloat() ?: 4f
                        val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloat() ?: 3f
                        val rot = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
                        ratio = if (rot == 90 || rot == 270) h / w else w / h
                        retriever.release()
                    } catch (e: Exception) { }
                }
                ratio
            }

            if (resultUri.isNotBlank()) {
                if (isVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(videoRatio)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, OffGridSubtle, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoURI(Uri.parse(resultUri))
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        seekTo(1) // Show first frame
                                    }
                                    setOnCompletionListener {
                                        isPlaying = false
                                    }
                                }
                            },
                            update = { view ->
                                if (isPlaying && !view.isPlaying) view.start()
                                else if (!isPlaying && view.isPlaying) view.pause()
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Play/Pause Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    indication = null, 
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                ) {
                                    isPlaying = !isPlaying
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !isPlaying,
                                enter = androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.fadeOut()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PlayArrow,
                                        contentDescription = "تشغيل",
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model             = Uri.parse(resultUri),
                            contentDescription = "نتيجة المعالجة",
                            contentScale      = ContentScale.Fit,
                            modifier          = Modifier
                                .heightIn(max = 500.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .border(1.dp, OffGridSubtle, RoundedCornerShape(20.dp))
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(OffGridSurface)
                        .border(1.dp, OffGridSubtle, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.BrokenImage,
                            contentDescription = null,
                            tint = OffGridSubtle,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("لا يمكن عرض الملف", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ── Offline badge ────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OffGridSurfaceVar)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = OffGridPrimary, modifier = Modifier.size(18.dp))
                Text(
                    text  = "تمت المعالجة محلياً بالكامل — لم تغادر ملفاتك الجهاز",
                    style = MaterialTheme.typography.bodySmall,
                    color = OffGridOnBackground
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Save to Gallery Button ───────────────────
            OffGridPrimaryButton(
                text    = if (isSaved) "تم الحفظ ✓" else "حفظ في المعرض",
                enabled = !isSaving && !isSaved,
                onClick = {
                    isSaving = true
                    saveMediaToGallery(context, resultUri, isVideo) { success ->
                        isSaving = false
                        isSaved  = success
                        val msg = if (success) "تم الحفظ بنجاح" else "فشل الحفظ، حاول مجدداً"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                icon = Icons.Outlined.SaveAlt
            )

            // ── Process Another Button ───────────────────
            OffGridSecondaryButton(
                text    = "معالجة ملف آخر",
                onClick = onHome,
                icon    = Icons.Outlined.Home
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Helper: Save processed file to MediaStore (Gallery) ─────────────
private fun saveMediaToGallery(
    context: Context,
    sourceUri: String,
    isVideo: Boolean,
    onResult: (Boolean) -> Unit
) {
    try {
        val inputStream = context.contentResolver.openInputStream(Uri.parse(sourceUri))
            ?: return onResult(false)

        val timeStamp = System.currentTimeMillis()
        val fileName = if (isVideo) "offgridai_$timeStamp.mp4" else "offgridai_$timeStamp.jpg"
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val dir = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/OffGridAI")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val outputUri = context.contentResolver.insert(collection, contentValues)
            ?: return onResult(false)

        context.contentResolver.openOutputStream(outputUri)?.use { out ->
            inputStream.copyTo(out)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(outputUri, contentValues, null, null)
        }

        inputStream.close()
        onResult(true)
    } catch (e: Exception) {
        onResult(false)
    }
}
