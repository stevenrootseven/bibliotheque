package com.stevenroot.mabibliotheque.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stevenroot.mabibliotheque.R

private enum class DragHandle { MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * Lets the user tighten a freshly captured/picked cover photo down to just the book/disc — a
 * phone photo is rarely perfectly framed, so cropping in-app beats asking for a retake. Manual
 * only, no auto edge-detection: the ML Kit document scanner that would add that has a known
 * 16KB-page-size native-library incompatibility that can block Play Store submission — not worth
 * the risk for a crop box the user can position in two drags.
 */
@Composable
fun CropImageDialog(
    bitmap: Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Ajustez le cadrage",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )

                val density = LocalDensity.current
                val configuration = LocalConfiguration.current
                val availableWidthPx = with(density) { (configuration.screenWidthDp.dp - 32.dp).toPx() }
                val availableHeightPx = with(density) { (configuration.screenHeightDp.dp * 0.62f).toPx() }
                val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                val boxAspect = availableWidthPx / availableHeightPx
                val renderedWidthPx = if (bitmapAspect > boxAspect) availableWidthPx else availableHeightPx * bitmapAspect
                val renderedHeightPx = if (bitmapAspect > boxAspect) availableWidthPx / bitmapAspect else availableHeightPx
                val handleRadiusPx = with(density) { 24.dp.toPx() }
                val minCropSizePx = with(density) { 48.dp.toPx() }

                // Starts at the full image: the photo is framed correctly almost every time, so
                // defaulting to a pre-shrunk box just makes the user drag it back out each time.
                var cropRect by remember(bitmap) {
                    mutableStateOf(
                        Rect(
                            left = 0f,
                            top = 0f,
                            right = renderedWidthPx,
                            bottom = renderedHeightPx,
                        ),
                    )
                }

                Box(
                    modifier = with(density) { Modifier.size(renderedWidthPx.toDp(), renderedHeightPx.toDp()) },
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(bitmap) {
                                var activeHandle: DragHandle? = null
                                detectDragGestures(
                                    onDragStart = { start ->
                                        activeHandle = nearestHandle(cropRect, start, handleRadiusPx)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val handle = activeHandle ?: return@detectDragGestures
                                        cropRect = applyDrag(
                                            cropRect,
                                            handle,
                                            dragAmount,
                                            renderedWidthPx,
                                            renderedHeightPx,
                                            minCropSizePx,
                                        )
                                    },
                                    onDragEnd = { activeHandle = null },
                                )
                            },
                    ) {
                        val dim = Color.Black.copy(alpha = 0.6f)
                        drawRect(color = dim, size = Size(size.width, cropRect.top))
                        drawRect(
                            color = dim,
                            topLeft = Offset(0f, cropRect.bottom),
                            size = Size(size.width, size.height - cropRect.bottom),
                        )
                        drawRect(
                            color = dim,
                            topLeft = Offset(0f, cropRect.top),
                            size = Size(cropRect.left, cropRect.height),
                        )
                        drawRect(
                            color = dim,
                            topLeft = Offset(cropRect.right, cropRect.top),
                            size = Size(size.width - cropRect.right, cropRect.height),
                        )
                        drawRect(
                            color = Color.White,
                            topLeft = cropRect.topLeft,
                            size = cropRect.size,
                            style = Stroke(width = 3.dp.toPx()),
                        )
                        listOf(cropRect.topLeft, cropRect.topRight, cropRect.bottomLeft, cropRect.bottomRight).forEach { corner ->
                            drawCircle(color = Color.White, radius = 10.dp.toPx(), center = corner)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel), color = Color.White) }
                    TextButton(onClick = {
                        val scaleX = bitmap.width / renderedWidthPx
                        val scaleY = bitmap.height / renderedHeightPx
                        val x = (cropRect.left * scaleX).toInt().coerceIn(0, bitmap.width - 1)
                        val y = (cropRect.top * scaleY).toInt().coerceIn(0, bitmap.height - 1)
                        val w = (cropRect.width * scaleX).toInt().coerceIn(1, bitmap.width - x)
                        val h = (cropRect.height * scaleY).toInt().coerceIn(1, bitmap.height - y)
                        onConfirm(Bitmap.createBitmap(bitmap, x, y, w, h))
                    }) { Text("Valider") }
                }
            }
        }
    }
}

private fun nearestHandle(rect: Rect, point: Offset, radius: Float): DragHandle? {
    val corners = listOf(
        DragHandle.TOP_LEFT to rect.topLeft,
        DragHandle.TOP_RIGHT to rect.topRight,
        DragHandle.BOTTOM_LEFT to rect.bottomLeft,
        DragHandle.BOTTOM_RIGHT to rect.bottomRight,
    )
    corners.forEach { (handle, corner) -> if ((corner - point).getDistance() <= radius) return handle }
    return if (rect.contains(point)) DragHandle.MOVE else null
}

private fun applyDrag(
    rect: Rect,
    handle: DragHandle,
    delta: Offset,
    maxWidth: Float,
    maxHeight: Float,
    minSize: Float,
): Rect = when (handle) {
    DragHandle.MOVE -> {
        val dx = delta.x.coerceIn(-rect.left, maxWidth - rect.right)
        val dy = delta.y.coerceIn(-rect.top, maxHeight - rect.bottom)
        rect.translate(dx, dy)
    }
    DragHandle.TOP_LEFT -> Rect(
        left = (rect.left + delta.x).coerceIn(0f, rect.right - minSize),
        top = (rect.top + delta.y).coerceIn(0f, rect.bottom - minSize),
        right = rect.right,
        bottom = rect.bottom,
    )
    DragHandle.TOP_RIGHT -> Rect(
        left = rect.left,
        top = (rect.top + delta.y).coerceIn(0f, rect.bottom - minSize),
        right = (rect.right + delta.x).coerceIn(rect.left + minSize, maxWidth),
        bottom = rect.bottom,
    )
    DragHandle.BOTTOM_LEFT -> Rect(
        left = (rect.left + delta.x).coerceIn(0f, rect.right - minSize),
        top = rect.top,
        right = rect.right,
        bottom = (rect.bottom + delta.y).coerceIn(rect.top + minSize, maxHeight),
    )
    DragHandle.BOTTOM_RIGHT -> Rect(
        left = rect.left,
        top = rect.top,
        right = (rect.right + delta.x).coerceIn(rect.left + minSize, maxWidth),
        bottom = (rect.bottom + delta.y).coerceIn(rect.top + minSize, maxHeight),
    )
}
