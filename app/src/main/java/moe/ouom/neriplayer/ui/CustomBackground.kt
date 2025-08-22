package moe.ouom.neriplayer.ui

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import androidx.core.graphics.createBitmap

@Composable
fun CustomBackground(
    imageUri: String?,
    blur: Float,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    if (imageUri != null) {
        val context = LocalContext.current

        val imageRequest = ImageRequest.Builder(context)
            .data(imageUri.toUri())
            .crossfade(true)
            .transformations(
                if (blur > 0f) {
                    listOf(BlurTransformation(context, radius = blur))
                } else {
                    emptyList()
                }
            )
            .build()

        AsyncImage(
            model = imageRequest,
            contentDescription = "App Background",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxSize()
                .alpha(alpha)
        )
    }
}

class BlurTransformation(
    private val context: Context,
    private val radius: Float,
) : Transformation {

    override val cacheKey: String = "${this::class.java.name}-$radius"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        var rs: RenderScript? = null
        try {
            rs = RenderScript.create(context)

            val inputAllocation = Allocation.createFromBitmap(rs, input)
            val outputAllocation = Allocation.createTyped(rs, inputAllocation.type)

            val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

            script.setRadius(radius.coerceIn(0.1f, 25.0f))
            script.setInput(inputAllocation)
            script.forEach(outputAllocation)

            val output = input.config?.let { createBitmap(input.width, input.height, it) }
            outputAllocation.copyTo(output)

            return output!!
        } finally {
            rs?.destroy()
        }
    }
}