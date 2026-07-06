package com.eventmanager.app.ui.components

import android.util.Base64
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.eventmanager.app.R
import kotlin.math.min

private val PixelFont = FontFamily.Monospace

private val PixelCabinetOuter = Color(0xFF6B4C9A)
private val PixelCabinetMid = Color(0xFF3D2B6E)
private val PixelCabinetInner = Color(0xFF1A1035)
private val PixelScreenBg = Color(0xFF0A0820)
private val PixelYellow = Color(0xFFFFE566)
private val PixelOrange = Color(0xFFFF6B35)
private val PixelCyan = Color(0xFF00E5FF)
private val PixelMagenta = Color(0xFFFF2D95)
private val PixelGreen = Color(0xFF39FF88)
private val PixelSkyBlue = Color(0xFF57B8FF)
private val PixelBevelLight = Color(0xFFCCCCEE)
private val PixelBevelDark = Color(0xFF1A1A44)
private val PixelStarColors = listOf(PixelYellow, PixelCyan, PixelMagenta, PixelGreen, Color.White)

private val PixelStarOffsets = listOf(
    0.08f to 0.12f, 0.22f to 0.28f, 0.38f to 0.08f, 0.55f to 0.18f,
    0.72f to 0.10f, 0.88f to 0.25f, 0.15f to 0.55f, 0.45f to 0.62f,
    0.68f to 0.48f, 0.92f to 0.58f, 0.30f to 0.82f, 0.60f to 0.88f,
)

@Composable
fun RetroSynthwaveGameDialog(
    onDismiss: () -> Unit,
    onHextrisSelected: () -> Unit,
    onPizzaUndeliverySelected: () -> Unit,
    onScrollSelected: () -> Unit,
    onWendolVillageSelected: () -> Unit,
    onCatculusSelected: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val maxDialogHeight = min(680, (configuration.screenHeightDp * 0.92f).toInt()).dp
    val scrollState = rememberScrollState()
    val blinkTransition = rememberInfiniteTransition(label = "arcade_blink")
    val subtitleAlpha by blinkTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "subtitle_blink",
    )
    val titleHue by blinkTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Restart),
        label = "title_hue",
    )
    val titleColor = when ((titleHue * 4).toInt() % 4) {
        0 -> PixelYellow
        1 -> PixelCyan
        2 -> PixelMagenta
        else -> PixelGreen
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f)
                .heightIn(max = maxDialogHeight),
        ) {
            PixelStarfieldBackground(Modifier.matchParentSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .border(6.dp, PixelCabinetOuter)
                    .padding(6.dp)
                    .border(4.dp, PixelBevelLight)
                    .padding(4.dp)
                    .border(4.dp, PixelCabinetMid)
                    .padding(4.dp)
                    .background(PixelCabinetInner)
                    .padding(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    PixelStarColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(color)
                                .border(1.dp, Color.Black),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = context.getString(R.string.arcade_title),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = PixelFont,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp,
                    ),
                    color = titleColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = context.getString(R.string.arcade_subtitle),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = PixelFont,
                        letterSpacing = 2.sp,
                    ),
                    color = PixelYellow.copy(alpha = subtitleAlpha),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "═══════════════════════",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = PixelFont),
                    color = PixelCyan.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = context.getString(R.string.arcade_pick_game),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = PixelFont,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                    color = PixelMagenta,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PixelGameButton(
                        emoji = "⬡",
                        label = context.getString(R.string.easter_egg_hextris),
                        accentColor = PixelCyan,
                        onClick = onHextrisSelected,
                    )
                    PixelGameButton(
                        emoji = "🍕",
                        label = context.getString(R.string.easter_egg_pizza_undelivery),
                        accentColor = PixelOrange,
                        onClick = onPizzaUndeliverySelected,
                    )
                    PixelGameButton(
                        emoji = "👆",
                        label = context.getString(R.string.easter_egg_scroll),
                        accentColor = PixelMagenta,
                        onClick = onScrollSelected,
                    )
                    PixelGameButton(
                        emoji = "🏰",
                        label = context.getString(R.string.easter_egg_wendol_village),
                        accentColor = PixelGreen,
                        onClick = onWendolVillageSelected,
                    )
                    PixelGameButton(
                        emoji = "🐱",
                        label = context.getString(R.string.easter_egg_catculus),
                        accentColor = PixelSkyBlue,
                        onClick = onCatculusSelected,
                    )

                    Spacer(Modifier.height(4.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(3.dp, PixelBevelDark)
                            .background(PixelScreenBg)
                            .border(2.dp, PixelCyan.copy(alpha = 0.4f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = context.getString(R.string.arcade_credits_title),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = PixelFont,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            ),
                            color = PixelYellow,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PixelCreditLine(context.getString(R.string.arcade_credit_hextris))
                        PixelCreditLine(context.getString(R.string.arcade_credit_pizza_undelivery))
                        PixelCreditLine(context.getString(R.string.arcade_credit_scroll))
                        PixelCreditLine(context.getString(R.string.arcade_credit_wendol_village))
                        PixelCreditLine(context.getString(R.string.arcade_credit_catculus))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable(onClick = onDismiss)
                        .border(3.dp, PixelBevelLight)
                        .background(PixelBevelDark)
                        .border(2.dp, Color.Black)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = context.getString(R.string.arcade_exit),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = PixelFont,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = PixelYellow,
                    )
                }
            }
        }
    }
}

@Composable
private fun PixelStarfieldBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF12082E),
                    Color(0xFF1E0F4A),
                    Color(0xFF2A1560),
                ),
            ),
        ),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            PixelStarOffsets.forEachIndexed { index, (xRatio, yRatio) ->
                val color = PixelStarColors[index % PixelStarColors.size]
                val size = if (index % 3 == 0) 6f else 4f
                drawRect(
                    color = color.copy(alpha = 0.7f),
                    topLeft = Offset(xRatio * this.size.width, yRatio * this.size.height),
                    size = androidx.compose.ui.geometry.Size(size, size),
                )
            }
        }
    }
}

@Composable
private fun PixelGameButton(
    emoji: String,
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(3.dp, PixelBevelLight)
            .padding(bottom = 3.dp, end = 3.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(accentColor.copy(alpha = 0.18f))
                .border(3.dp, PixelBevelDark)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = emoji,
                fontSize = 28.sp,
                modifier = Modifier
                    .border(2.dp, accentColor)
                    .background(PixelScreenBg)
                    .padding(6.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = PixelFont,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "▶",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = PixelFont),
                color = accentColor,
            )
        }
    }
}

@Composable
private fun PixelCreditLine(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "■",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = PixelFont),
            color = PixelCyan.copy(alpha = 0.6f),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = PixelFont,
                lineHeight = 15.sp,
            ),
            color = Color(0xFF88FFAA).copy(alpha = 0.85f),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun HextrisGameDialog(onDismiss: () -> Unit) {
    ArcadeAssetGameDialog(
        onDismiss = onDismiss,
        assetPath = "arcade/hextris/index.html",
        backgroundColor = Color(0xFF2C3E50),
    )
}

@Composable
fun PizzaUndeliveryGameDialog(onDismiss: () -> Unit) {
    ArcadeAssetGameDialog(
        onDismiss = onDismiss,
        assetPath = "arcade/pizza-undelivery/index.html",
        backgroundColor = Color.Black,
    )
}

@Composable
fun WendolVillageGameDialog(onDismiss: () -> Unit) {
    ArcadeAssetGameDialog(
        onDismiss = onDismiss,
        assetPath = "arcade/wendol-village/index.html",
        injectViewportSize = true,
        injectTextureDataUrl = true,
        backgroundColor = Color(0xFF5B6EE1),
    )
}

@Composable
fun CatculusGameDialog(onDismiss: () -> Unit) {
    ArcadeAssetGameDialog(
        onDismiss = onDismiss,
        assetPath = "arcade/catculus/index.html",
        backgroundColor = Color(0xFF0D2E45),
    )
}

@Composable
fun ScrollGameDialog(onDismiss: () -> Unit) {
    ArcadeAssetGameDialog(
        onDismiss = onDismiss,
        assetPath = "arcade/scroll/index.html",
        backgroundColor = Color.Black,
    )
}

@Composable
private fun ArcadeAssetGameDialog(
    onDismiss: () -> Unit,
    assetPath: String,
    backgroundColor: Color,
    wideViewPort: Boolean = true,
    injectViewportSize: Boolean = false,
    injectTextureDataUrl: Boolean = false,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val textureInjectionScript = remember(injectTextureDataUrl, assetPath) {
        if (!injectTextureDataUrl) {
            null
        } else {
            val texturePath = "${assetPath.substringBeforeLast('/')}/t.png"
            runCatching {
                val textureBase64 = context.assets.open(texturePath).use { stream ->
                    Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
                }
                "window.__WENDOL_TEXTURE_DATA_URL='data:image/png;base64,$textureBase64';"
            }.getOrNull()
        }
    }
    val gameUrl = remember(assetPath) {
        "https://appassets.androidplatform.net/assets/$assetPath"
    }
    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(backgroundColor),
        ) {
            key(configuration.orientation) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                loadWithOverviewMode = wideViewPort
                                useWideViewPort = wideViewPort
                                builtInZoomControls = false
                                displayZoomControls = false
                                setSupportZoom(false)
                                mediaPlaybackRequiresUserGesture = false
                                allowFileAccess = true
                                allowContentAccess = true
                            }
                            webViewClient = object : WebViewClientCompat() {
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: android.webkit.WebResourceRequest,
                                ) = assetLoader.shouldInterceptRequest(request.url)

                                override fun onPageFinished(view: WebView, url: String?) {
                                    scheduleArcadeWebViewLayout(view, injectViewportSize, textureInjectionScript)
                                }
                            }
                            webChromeClient = WebChromeClient()
                            loadUrl(gameUrl)
                            webViewRef = this
                        }
                    },
                    update = { webView ->
                        scheduleArcadeWebViewLayout(webView, injectViewportSize, textureInjectionScript)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(3.dp, PixelBevelLight),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = context.getString(R.string.close),
                    tint = PixelYellow,
                )
            }
        }
    }
}

private fun scheduleArcadeWebViewLayout(
    webView: WebView,
    overrideWindowSize: Boolean,
    textureInjectionScript: String?,
) {
    val delaysMs = longArrayOf(0L, 50L, 150L, 400L)
    delaysMs.forEach { delayMs ->
        webView.postDelayed({
            notifyArcadeWebViewLayout(
                webView = webView,
                syncViewport = true,
                textureInjectionScript = textureInjectionScript,
                overrideWindowSize = overrideWindowSize,
            )
        }, delayMs)
    }
}

private fun notifyArcadeWebViewLayout(
    webView: WebView,
    syncViewport: Boolean,
    textureInjectionScript: String?,
    overrideWindowSize: Boolean,
) {
    if (webView.width <= 0 || webView.height <= 0) {
        return
    }

    val density = webView.resources.displayMetrics.density
    val cssWidth = (webView.width / density).toInt().coerceAtLeast(1)
    val cssHeight = (webView.height / density).toInt().coerceAtLeast(1)

    val script = buildString {
        if (textureInjectionScript != null) {
            append(textureInjectionScript)
        }
        if (syncViewport) {
            append("window.__arcadeViewportW=").append(cssWidth).append(';')
            append("window.__arcadeViewportH=").append(cssHeight).append(';')
            append("window.__wendolViewportW=").append(cssWidth).append(';')
            append("window.__wendolViewportH=").append(cssHeight).append(';')
        }
        if (overrideWindowSize) {
            append("(function(w,h){")
            append("try{")
            append("Object.defineProperty(window,'innerWidth',{configurable:true,get:function(){return w;}});")
            append("Object.defineProperty(window,'innerHeight',{configurable:true,get:function(){return h;}});")
            append("}catch(e){}")
            append("})(").append(cssWidth).append(',').append(cssHeight).append(");")
        }
        append("if(window.__wendolTryStart)window.__wendolTryStart();")
        append("if(window.__wendolResizeCanvas)window.__wendolResizeCanvas();")
        append("window.dispatchEvent(new Event('resize'));")
    }
    webView.evaluateJavascript(script, null)
}
