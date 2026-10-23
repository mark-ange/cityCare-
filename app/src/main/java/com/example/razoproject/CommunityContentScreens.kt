package com.example.razoproject

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CommunityContentScreen(
    contentType: String,
    onBack: () -> Unit,
    onRegisterWebViewBackHandler: (((() -> Boolean)?) -> Unit) = {}
) {
    var content by remember(contentType) { mutableStateOf<List<CityCareCommunityContent>>(emptyList()) }
    var isLoading by remember(contentType) { mutableStateOf(true) }
    var loadError by remember(contentType) { mutableStateOf<String?>(null) }
    var reloadKey by remember(contentType) { mutableIntStateOf(0) }
    var webViewUrl by remember { mutableStateOf<String?>(null) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    val title = when (contentType) {
        "recycle" -> "Recycle"
        "event" -> "Events"
        "tip" -> "Tips"
        "blog" -> "Blog"
        else -> "Community"
    }

    LaunchedEffect(contentType, reloadKey) {
        isLoading = true
        loadError = null
        try {
            content = CityCareApiService.fetchCommunityContent(contentType)
        } catch (error: Exception) {
            content = emptyList()
            loadError = error.message ?: "Could not load $title content. Check your internet connection and try again."
        } finally {
            isLoading = false
        }
    }

    fun handleWebViewBack(): Boolean {
        val webView = activeWebView
        if (webView?.canGoBack() == true) {
            webView.goBack()
        } else {
            webViewUrl = null
        }
        return true
    }

    DisposableEffect(webViewUrl) {
        if (webViewUrl != null) {
            onRegisterWebViewBackHandler { handleWebViewBack() }
        } else {
            onRegisterWebViewBackHandler(null)
        }
        onDispose { onRegisterWebViewBackHandler(null) }
    }

    BackHandler(enabled = webViewUrl != null) {
        handleWebViewBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (webViewUrl != null) handleWebViewBack() else onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = EcoTextDark)
                }
                Text(
                    text = if (webViewUrl == null) title else "More information",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = EcoTextDark
                )
            }
        }

        if (webViewUrl != null) {
            DisposableEffect(webViewUrl) {
                onDispose {
                    activeWebView?.stopLoading()
                    activeWebView?.destroy()
                    activeWebView = null
                }
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { androidContext ->
                    WebView(androidContext).apply {
                        activeWebView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.setSupportMultipleWindows(false)
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                return request.url.scheme != "https" || request.url.host.isNullOrBlank()
                            }

                            override fun onReceivedSslError(
                                view: WebView,
                                handler: SslErrorHandler,
                                error: android.net.http.SslError
                            ) {
                                handler.cancel()
                            }
                        }
                        loadUrl(webViewUrl!!)
                    }
                },
                update = { view ->
                    if (view.url != webViewUrl) view.loadUrl(webViewUrl!!)
                }
            )
        } else {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EcoPrimaryGreen)
                }
                loadError != null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(loadError.orEmpty(), color = EcoTextMuted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { reloadKey += 1 },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = EcoPrimaryGreen)
                    ) { Text("Retry") }
                }
                content.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No published $title content yet.",
                        color = EcoTextMuted,
                        textAlign = TextAlign.Center
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(content, key = { item -> item.id }) { item ->
                        CommunityContentCard(
                            item = item,
                            onImageClick = { selectedImage = it },
                            onSourceClick = { source ->
                                val uri = Uri.parse(source)
                                if (uri.scheme == "https" && !uri.host.isNullOrBlank()) {
                                    webViewUrl = uri.toString()
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "This source link is not a valid HTTPS address.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    selectedImage?.let { bitmap ->
        Dialog(
            onDismissRequest = { selectedImage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { selectedImage = null },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Expanded community image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun CommunityContentCard(
    item: CityCareCommunityContent,
    onImageClick: (Bitmap) -> Unit,
    onSourceClick: (String) -> Unit
) {
    val sourceUrl = item.sourceUrl
        .takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
        ?: findSourceUrl(item.body)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(item.title, color = EcoTextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (item.summary.isNotBlank()) {
                Text(item.summary, color = EcoTextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            if (item.type == "event") {
                Text(
                    text = buildString {
                        append(formatCommunityDate(item.eventStartAt))
                        if (item.eventEndAt.isNotBlank()) append(" – ${formatCommunityDate(item.eventEndAt)}")
                        if (item.location.isNotBlank()) append("\n${item.location}")
                    },
                    color = EcoPrimaryGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(item.body, color = EcoTextDark, fontSize = 13.sp, lineHeight = 19.sp)
            if (item.imageUrl.isNotBlank() || item.images.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    if (item.imageUrl.isNotBlank()) {
                        CommunityContentImageView(
                            image = CityCareContentImage(
                                id = "image-url",
                                url = item.imageUrl,
                                name = "Content image",
                                mimeType = "image/*"
                            ),
                            onClick = onImageClick
                        )
                    }
                    item.images.forEach { image ->
                        CommunityContentImageView(
                            image = image,
                            onClick = onImageClick
                        )
                    }
                }
            }
            if (sourceUrl.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSourceClick(sourceUrl) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            tint = EcoPrimaryGreen,
                            modifier = Modifier.size(21.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "OPEN LINKED PAGE",
                                color = EcoPrimaryGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                sourceUrl,
                                color = EcoTextDark,
                                fontSize = 12.sp,
                                maxLines = 2,
                                lineHeight = 16.sp
                            )
                        }
                        Text(
                            "Open",
                            color = EcoPrimaryGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun findSourceUrl(text: String): String {
    val candidate = Regex("""https://[^\s<>"']+""", RegexOption.IGNORE_CASE)
        .find(text)
        ?.value
        ?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']')
        .orEmpty()
    val uri = Uri.parse(candidate)
    return candidate.takeIf { uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() }
        .orEmpty()
}

@Composable
private fun CommunityContentImageView(
    image: CityCareContentImage,
    onClick: (Bitmap) -> Unit
) {
    val bitmapResult by produceState<Result<Bitmap>?>(initialValue = null, key1 = image.url) {
        value = runCatching {
            CityCareApiService.fetchCommunityImage(image.url)
        }
    }

    Box(
        modifier = Modifier
            .width(220.dp)
            .height(150.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFE2E8F0)),
        contentAlignment = Alignment.Center
    ) {
        val result = bitmapResult
        when {
            result == null -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = EcoPrimaryGreen,
                strokeWidth = 2.dp
            )
            result.isSuccess -> {
                val bitmap = result.getOrThrow()
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = image.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onClick(bitmap) },
                    contentScale = ContentScale.Crop
                )
            }
            else -> Text(
                text = "Image unavailable",
                color = EcoTextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

private fun formatCommunityDate(value: String): String {
    return runCatching {
        OffsetDateTime.parse(value)
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.getDefault()))
    }.getOrDefault(value)
}
