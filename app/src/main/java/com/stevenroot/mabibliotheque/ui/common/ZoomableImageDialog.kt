package com.stevenroot.mabibliotheque.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage

/**
 * Full-screen pinch-to-zoom/pan cover viewer — a poster thumbnail is too small to read the
 * disc's audio/subtitle language icons, which is the whole point of keeping a back-cover photo.
 * When [back] is available, a "Devant"/"Dos" chip switches between the two without leaving the
 * zoomed view — flipping a physical case doesn't require putting it down first either.
 */
@Composable
fun ZoomableImageDialog(
    front: Any,
    back: Any? = null,
    initialShowBack: Boolean = false,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var showingBack by remember { mutableStateOf(initialShowBack && back != null) }
        var scale by remember(showingBack) { mutableStateOf(1f) }
        var offset by remember(showingBack) { mutableStateOf(Offset.Zero) }
        val model = if (showingBack) back ?: front else front

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(showingBack) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 6f)
                        scale = newScale
                        offset = if (newScale <= 1f) Offset.Zero else offset + pan
                    }
                },
        ) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
            }
            if (back != null) {
                AssistChip(
                    onClick = { showingBack = !showingBack },
                    label = { Text(if (showingBack) "Devant" else "Dos") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        labelColor = Color.White,
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                )
            }
        }
    }
}
