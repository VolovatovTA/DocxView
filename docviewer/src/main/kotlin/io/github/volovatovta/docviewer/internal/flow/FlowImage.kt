package io.github.volovatovta.docviewer.internal.flow

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.R
import io.github.volovatovta.docviewer.core.model.ImageRun
import io.github.volovatovta.docviewer.core.model.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object ImageSpec {
    val VerticalPadding = 6.dp
    val CornerRadius = 4.dp
    val PlaceholderPadding = 12.dp
    val PlaceholderTextSize = 13.sp

    // Sizes up to a point are treated as missing: Word writes 0 or 1 for "unknown".
    const val MIN_KNOWN_SIZE_PT = 1f
    const val FALLBACK_ASPECT_RATIO = 1.6f
    const val MIN_ASPECT_RATIO = 0.1f
    const val MAX_ASPECT_RATIO = 10f

    // Images look slightly small at their point size next to body text scaled up for phones.
    const val DISPLAY_SCALE = 1.25f
    const val MIN_DECODE_WIDTH_PX = 480
    const val MAX_DECODE_WIDTH_PX = 2048
}

private sealed interface ImageState {
    data object Loading : ImageState

    data object Unsupported : ImageState

    class Ready(val bitmap: ImageBitmap) : ImageState
}

@Composable
internal fun FlowImage(
    run: ImageRun,
    context: FlowContext,
    alignment: TextAlign,
    modifier: Modifier = Modifier,
) {
    val image by rememberDecodedImage(run.imageId, context)
    var isFullscreen by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = ImageSpec.VerticalPadding),
        contentAlignment = alignment.toBoxAlignment(),
    ) {
        val frame = Modifier
            .widthIn(max = run.displayMaxWidth(context.scale))
            .fillMaxWidth()
            .aspectRatio(image.aspectRatio(run))
            .clip(RoundedCornerShape(ImageSpec.CornerRadius))
        when (val current = image) {
            is ImageState.Ready -> Image(
                bitmap = current.bitmap,
                contentDescription = run.description,
                modifier = frame.clickable { isFullscreen = true },
                contentScale = ContentScale.Fit,
            )
            ImageState.Loading -> Box(frame.background(context.colors.stripe))
            ImageState.Unsupported -> UnsupportedImage(run.description, context.colors, frame)
        }
        val ready = image as? ImageState.Ready
        if (isFullscreen && ready != null) {
            ZoomableImageDialog(ready.bitmap, run.description, onDismiss = { isFullscreen = false })
        }
    }
}

@Composable
private fun UnsupportedImage(description: String?, colors: DocumentViewerColors, modifier: Modifier = Modifier) {
    Box(modifier.background(colors.stripe), contentAlignment = Alignment.Center) {
        BasicText(
            text = description ?: stringResource(R.string.dv_image_unsupported),
            modifier = Modifier.padding(ImageSpec.PlaceholderPadding),
            style = TextStyle(
                color = colors.secondaryText,
                fontSize = ImageSpec.PlaceholderTextSize,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun rememberDecodedImage(imageId: String, context: FlowContext): State<ImageState> {
    val screenWidth = LocalResources.current.displayMetrics.widthPixels
    val targetWidth = screenWidth.coerceIn(ImageSpec.MIN_DECODE_WIDTH_PX, ImageSpec.MAX_DECODE_WIDTH_PX)
    val cached = context.images.cache.get(imageId)?.let(ImageState::Ready)
    return produceState(cached ?: ImageState.Loading, imageId) {
        if (value is ImageState.Ready) return@produceState
        val decoded = withContext(Dispatchers.IO) { decodeImage(context.images.store, imageId, targetWidth) }
        if (decoded is ImageState.Ready) context.images.cache.put(imageId, decoded.bitmap)
        value = decoded
    }
}

private fun decodeImage(store: ImageStore, imageId: String, targetWidth: Int): ImageState {
    val bytes = runCatching { store.open(imageId) }.getOrNull() ?: return ImageState.Unsupported
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0) return ImageState.Unsupported
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, targetWidth) }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    return bitmap?.let { ImageState.Ready(it.asImageBitmap()) } ?: ImageState.Unsupported
}

// Power-of-two downsampling is cheap and keeps the bitmap at least as wide as the screen.
private fun sampleSize(width: Int, targetWidth: Int): Int {
    var sample = 1
    while (width / (sample * 2) >= targetWidth) sample *= 2
    return sample
}

private fun ImageState.aspectRatio(run: ImageRun): Float {
    val ratio = if (this is ImageState.Ready) {
        bitmap.width.toFloat() / bitmap.height
    } else {
        run.declaredAspectRatio() ?: ImageSpec.FALLBACK_ASPECT_RATIO
    }
    return ratio.coerceIn(ImageSpec.MIN_ASPECT_RATIO, ImageSpec.MAX_ASPECT_RATIO)
}

private fun ImageRun.declaredAspectRatio(): Float? {
    val width = widthPt?.takeIf { it > ImageSpec.MIN_KNOWN_SIZE_PT } ?: return null
    val height = heightPt?.takeIf { it > ImageSpec.MIN_KNOWN_SIZE_PT } ?: return null
    return width / height
}

private fun ImageRun.displayMaxWidth(scale: Float): Dp =
    widthPt?.takeIf { it > ImageSpec.MIN_KNOWN_SIZE_PT }?.let { (it * ImageSpec.DISPLAY_SCALE * scale).dp }
        ?: Dp.Unspecified

private fun TextAlign.toBoxAlignment(): Alignment = when (this) {
    TextAlign.Center -> Alignment.Center
    TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
    else -> Alignment.CenterStart
}
