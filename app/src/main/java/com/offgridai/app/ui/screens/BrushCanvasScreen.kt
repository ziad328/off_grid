package com.offgridai.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.offgridai.app.ui.components.OffGridPrimaryButton
import com.offgridai.app.ui.theme.*
import com.offgridai.app.utils.ImageUtils
import androidx.compose.foundation.gestures.detectTransformGestures

import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════
//  OffGrid AI — Interactive Touch Brush Selection Screen
//  Allows user to draw over any person/object with finger
//  Generates a high-precision binary mask for AI Inpainting.
// ══════════════════════════════════════════════════════

data class DrawnLine(
    val path: Path,
    val strokeWidth: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrushCanvasScreen(
    imageUri: String,
    onConfirmMask: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var brushSize by remember { mutableStateOf(40f) }
    val lines = remember { mutableStateListOf<DrawnLine>() }
    var currentLine by remember { mutableStateOf<DrawnLine?>(null) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var recomposeTrigger by remember { mutableIntStateOf(0) }
    
    // Zoom and Pan states
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var isZoomMode by remember { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        loadedBitmap = ImageUtils.loadBitmapFromUri(context, Uri.parse(imageUri))
    }

    Scaffold(
        containerColor = OffGridBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "حدد بالفرشاة الشخص أو العنصر",
                        style = MaterialTheme.typography.titleMedium.copy(color = OffGridOnBackground)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "رجوع", tint = OffGridText)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isZoomMode = !isZoomMode },
                        modifier = Modifier
                            .background(
                                color = if (isZoomMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isZoomMode) Icons.Outlined.PanTool else Icons.Outlined.Brush,
                            contentDescription = "تبديل وضع الفرشاة/التحريك",
                            tint = if (isZoomMode) MaterialTheme.colorScheme.primary else OffGridText
                        )
                    }
                    if (lines.isNotEmpty()) {
                        IconButton(onClick = { 
                            if (lines.isNotEmpty()) lines.removeLast() 
                        }) {
                            Icon(Icons.Outlined.Undo, contentDescription = "تراجع", tint = OffGridText)
                        }
                        IconButton(onClick = { lines.clear() }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "مسح الكل", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OffGridBackground)
            )
        },
        bottomBar = {
            Surface(
                color = OffGridSurface,
                tonalElevation = 6.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Brush Size Slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "حجم الفرشاة",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Slider(
                            value = brushSize,
                            onValueChange = { brushSize = it },
                            valueRange = 15f..90f,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Confirm Action Button
                    OffGridPrimaryButton(
                        text = "مسح العنصر المحدد بالذكاء الاصطناعي",
                        enabled = lines.isNotEmpty() && loadedBitmap != null,
                        onClick = {
                            loadedBitmap?.let { bmp ->
                                scope.launch {
                                    val maskBmp = generateMaskBitmap(bmp, lines, canvasSize.width.toFloat(), canvasSize.height.toFloat())
                                    val file = java.io.File(context.cacheDir, "current_mask.png")
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        file.outputStream().use { out ->
                                            maskBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                                        }
                                    }
                                    onConfirmMask()
                                }
                            }
                        },
                        icon = Icons.Outlined.AutoFixHigh
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            if (imageUri.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .onSizeChanged { canvasSize = it }
                        .clip(RoundedCornerShape(20.dp))
                        .background(OffGridSurface)
                        .border(1.dp, OffGridSubtle, RoundedCornerShape(20.dp))
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                        .pointerInput(isZoomMode) {
                            if (isZoomMode) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    val maxOffsetX = (canvasSize.width * (scale - 1)) / 2
                                    val maxOffsetY = (canvasSize.height * (scale - 1)) / 2
                                    offsetX = (offsetX + pan.x * scale).coerceIn(-maxOffsetX, maxOffsetX)
                                    offsetY = (offsetY + pan.y * scale).coerceIn(-maxOffsetY, maxOffsetY)
                                }
                            } else {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val newPath = Path().apply { moveTo(offset.x, offset.y) }
                                        currentLine = DrawnLine(newPath, brushSize / scale)
                                        recomposeTrigger++
                                    },
                                    onDrag = { change, _ ->
                                        currentLine?.path?.lineTo(change.position.x, change.position.y)
                                        recomposeTrigger++
                                    },
                                    onDragEnd = {
                                        currentLine?.let { lines.add(it) }
                                        currentLine = null
                                    }
                                )
                            }
                        }
                ) {
                    // Base Image
                    AsyncImage(
                        model = Uri.parse(imageUri),
                        contentDescription = "صورة الأصل",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Touch Brush Canvas Overlay
                    ComposeCanvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val trigger = recomposeTrigger // Read state to force recomposition
                        lines.forEach { line ->
                            drawPath(
                                path = line.path,
                                color = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.55f),
                                style = Stroke(width = line.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                        currentLine?.let { line ->
                            drawPath(
                                path = line.path,
                                color = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.55f),
                                style = Stroke(width = line.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders user drawn lines onto a black-and-white binary mask bitmap matching image dimensions.
 * White = Area to inpaint / remove. Black = Keep untouched.
 */
private fun generateMaskBitmap(srcBitmap: Bitmap, lines: List<DrawnLine>, canvasWidth: Float, canvasHeight: Float): Bitmap {
    val w = srcBitmap.width
    val h = srcBitmap.height
    val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(mask)
    canvas.drawColor(Color.BLACK)

    val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    if (canvasWidth > 0 && canvasHeight > 0) {
        val imageAspect = w.toFloat() / h.toFloat()
        val canvasAspect = canvasWidth / canvasHeight

        var scale = 1f
        var dx = 0f
        var dy = 0f

        if (imageAspect > canvasAspect) {
            scale = w.toFloat() / canvasWidth
            val scaledHeight = canvasWidth / imageAspect
            dy = (canvasHeight - scaledHeight) / 2f
        } else {
            scale = h.toFloat() / canvasHeight
            val scaledWidth = canvasHeight * imageAspect
            dx = (canvasWidth - scaledWidth) / 2f
        }

        lines.forEach { line ->
            val androidPath = line.path.asAndroidPath()
            val scaledPath = android.graphics.Path()
            val matrix = android.graphics.Matrix()
            matrix.postTranslate(-dx, -dy)
            matrix.postScale(scale, scale)
            androidPath.transform(matrix, scaledPath)

            paint.strokeWidth = line.strokeWidth * scale
            canvas.drawPath(scaledPath, paint)
        }
    } else {
        paint.strokeWidth = (w.toFloat() / 400f) * 45f
        canvas.drawRect(w * 0.20f, h * 0.10f, w * 0.80f, h * 0.90f, paint)
    }

    return mask
}
