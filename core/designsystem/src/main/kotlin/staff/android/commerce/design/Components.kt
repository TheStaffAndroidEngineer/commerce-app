package staff.android.commerce.design

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

fun formatPrice(amountMinor: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).apply {
    minimumFractionDigits = if (amountMinor % 100L == 0L) 0 else 2
    maximumFractionDigits = 2
}.format(java.math.BigDecimal.valueOf(amountMinor, 2))

// Immutable public image keys are versioned by the fixture. Coil owns both bounded caches.
@Composable fun ProductPhoto(imageKey: String, modifier: Modifier = Modifier, contentDescription: String? = null, contentScale: ContentScale = ContentScale.Crop) {
    val context=LocalContext.current
    var retry by remember(imageKey){mutableIntStateOf(0)}
    var failed by remember(imageKey,retry){mutableStateOf(false)}
    var loading by remember(imageKey,retry){mutableStateOf(true)}
    val data=if(imageKey.endsWith("-v1") || imageKey.endsWith("-v2")) "http://10.0.2.2:8788/images/$imageKey.png" else "file:///android_asset/$imageKey.png"
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=androidx.compose.ui.Alignment.Center) {
        key(imageKey,retry) {
            coil3.compose.AsyncImage(model=coil3.request.ImageRequest.Builder(context).data(data).build(),
                contentDescription=contentDescription,modifier=Modifier.fillMaxSize(),contentScale=contentScale,
                onLoading={loading=true;failed=false},onSuccess={loading=false;failed=false},onError={loading=false;failed=true})
        }
        if(loading) CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)
        if(failed) TextButton(onClick={retry++},modifier=Modifier.semantics{this.contentDescription="Retry image for ${contentDescription ?: "product"}"}) {Text("Retry image",style=MaterialTheme.typography.labelSmall)}
    }
}

enum class ShopSymbol { Search, Heart, HeartFilled, Bag, ArrowBack, ArrowRight, Close, Plus, Minus, Check, Trash, Home, Info, Refresh }
@Composable fun ShopIcon(kind: ShopSymbol, modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val factor = size.minDimension / 24f
        scale(factor, factor, pivot = Offset.Zero) {
            val stroke = Stroke(1.65f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1, y1), Offset(x2, y2), 1.65f, StrokeCap.Round)
            fun path(block: Path.() -> Unit) = drawPath(Path().apply(block), tint, style = stroke)
            when (kind) {
                ShopSymbol.Search -> { drawCircle(tint, 6.8f, Offset(10.5f, 10.5f), style=stroke); line(15.8f,15.8f,21f,21f) }
                ShopSymbol.Heart, ShopSymbol.HeartFilled -> {
                    val heart = Path().apply {
                        moveTo(12f,21f); cubicTo(9f,18.2f,2.5f,13.5f,2.5f,8f)
                        cubicTo(2.5f,2f,9.7f,1.4f,12f,6.3f)
                        cubicTo(14.3f,1.4f,21.5f,2f,21.5f,8f)
                        cubicTo(21.5f,13.5f,15f,18.2f,12f,21f); close()
                    }
                    if (kind == ShopSymbol.HeartFilled) drawPath(heart, tint)
                    else drawPath(heart, tint, style = stroke)
                }
                ShopSymbol.Bag -> { path { moveTo(4.5f,7.5f); lineTo(19.5f,7.5f); lineTo(21f,21f); lineTo(3f,21f); close() }; path { moveTo(8f,9.5f); lineTo(8f,6f); cubicTo(8f,0.5f,16f,0.5f,16f,6f); lineTo(16f,9.5f) } }
                ShopSymbol.Home -> { path { moveTo(2.5f,10.5f); lineTo(12f,2.7f); lineTo(21.5f,10.5f) }; path { moveTo(5f,9f); lineTo(5f,21f); lineTo(10f,21f); lineTo(10f,14f); lineTo(14f,14f); lineTo(14f,21f); lineTo(19f,21f); lineTo(19f,9f) } }
                ShopSymbol.ArrowBack -> { line(20f,12f,4f,12f); line(4f,12f,10f,6f); line(4f,12f,10f,18f) }
                ShopSymbol.ArrowRight -> { line(4f,12f,20f,12f); line(20f,12f,14f,6f); line(20f,12f,14f,18f) }
                ShopSymbol.Close -> { line(6f,6f,18f,18f); line(18f,6f,6f,18f) }
                ShopSymbol.Plus -> { line(5f,12f,19f,12f); line(12f,5f,12f,19f) }
                ShopSymbol.Minus -> line(5f,12f,19f,12f)
                ShopSymbol.Check -> path { moveTo(4f,12f); lineTo(9.5f,17.5f); lineTo(20f,6f) }
                ShopSymbol.Trash -> { line(4f,6f,20f,6f); path { moveTo(8f,6f); lineTo(8f,3f); lineTo(16f,3f); lineTo(16f,6f) }; path { moveTo(6f,6f); lineTo(7f,21f); lineTo(17f,21f); lineTo(18f,6f) }; line(10f,10f,10f,17f); line(14f,10f,14f,17f) }
                ShopSymbol.Info -> { drawCircle(tint,9f,Offset(12f,12f),style=stroke); drawCircle(tint,1f,Offset(12f,7f)); line(12f,11f,12f,17f) }
                ShopSymbol.Refresh -> { path { moveTo(20f,9f); cubicTo(18f,1f,6f,1f,4f,10f); cubicTo(1f,20f,16f,25f,21f,15f) }; path { moveTo(15.5f,9f); lineTo(20.5f,9f); lineTo(20.5f,4f) } }
            }
        }
    }
}

@Composable fun IconAction(kind: ShopSymbol, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    IconButton(onClick, modifier.semantics { contentDescription = label }) {
        if (selected) Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).padding(8.dp)) {
            ShopIcon(if (kind == ShopSymbol.Heart) ShopSymbol.HeartFilled else kind, Modifier.fillMaxSize(), MaterialTheme.colorScheme.primary)
        } else ShopIcon(kind)
    }
}
