package com.offgridai.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offgridai.app.ui.theme.*

// ══════════════════════════════════════════════════════
//  OffGrid AI — Feature Selection Card
//  Displayed on HomeScreen for each AI feature
// ══════════════════════════════════════════════════════

data class FeatureCardData(
    val title: String,
    val descriptionAr: String,
    val icon: ImageVector,
    val featureKey: String
)

@Composable
fun FeatureCard(
    data: FeatureCardData,
    isSelected: Boolean,
    enabled: Boolean = true,
    disabledReason: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) OffGridPrimary else OffGridSubtle,
        animationSpec = tween(200), label = "borderAnim"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) OffGridProgressBg else OffGridSurface,
        animationSpec = tween(200), label = "bgAnim"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Icon bubble
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) OffGridPrimary else OffGridBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = data.icon,
                contentDescription = null,
                tint               = if (isSelected) OffGridOnPrimary else OffGridPrimary,
                modifier           = Modifier.size(24.dp)
            )
        }

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = data.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = OffGridOnBackground
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text  = data.descriptionAr,
                style = MaterialTheme.typography.bodySmall
            )
            if (!enabled && disabledReason != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = disabledReason,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // Selection indicator
        if (isSelected) {
            Icon(
                imageVector        = Icons.Outlined.CheckCircle,
                contentDescription = "Selected",
                tint               = OffGridPrimary,
                modifier           = Modifier.size(20.dp)
            )
        }
    }
}

// ══════════════════════════════════════════════════════
//  Media Picker Card — tap to pick photo or video
// ══════════════════════════════════════════════════════

@Composable
fun MediaPickerCard(
    mediaUri: android.net.Uri?,
    isVideo: Boolean,
    mediaLabel: String,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var videoThumbnail by remember(mediaUri, isVideo) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(mediaUri, isVideo) {
        if (isVideo && mediaUri != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, mediaUri)
                    videoThumbnail = retriever.getFrameAtTime(1000000) // 1 second in microsecs
                    retriever.release()
                } catch (e: Exception) { }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(OffGridSurface)
            .border(1.dp, OffGridSubtle, RoundedCornerShape(20.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (mediaUri != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OffGridPrimary.copy(alpha = 0.1f))
                    .border(1.dp, OffGridPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = OffGridPrimary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isVideo) "تم اختيار فيديو بنجاح" else "تم اختيار صورة بنجاح",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = OffGridPrimary
                    )
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            Icon(
                imageVector        = Icons.Outlined.AddPhotoAlternate,
                contentDescription = null,
                tint               = OffGridPrimary,
                modifier           = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Text(
            text  = if (mediaUri != null) mediaLabel else "اختر صورة أو فيديو",
            style = MaterialTheme.typography.titleMedium,
            color = if (mediaUri != null) OffGridOnBackground else OffGridText
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text  = "الصور والفيديو تُعالج محلياً على جهازك فقط",
            style = MaterialTheme.typography.bodySmall,
            color = OffGridText
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onPickImage,
                shape   = RoundedCornerShape(12.dp),
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = OffGridPrimary),
                border  = androidx.compose.foundation.BorderStroke(1.dp, OffGridPrimary),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (mediaUri != null && !isVideo) "تغيير الصورة" else "صورة", style = MaterialTheme.typography.labelMedium.copy(color = OffGridPrimary))
            }
            OutlinedButton(
                onClick = onPickVideo,
                shape   = RoundedCornerShape(12.dp),
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = OffGridPrimary),
                border  = androidx.compose.foundation.BorderStroke(1.dp, OffGridPrimary),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (mediaUri != null && isVideo) "تغيير الفيديو" else "فيديو", style = MaterialTheme.typography.labelMedium.copy(color = OffGridPrimary))
            }
        }
    }
}
