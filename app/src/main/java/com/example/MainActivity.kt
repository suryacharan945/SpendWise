package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.MainViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(applicationContext)
    }

    private var filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>? = null

    val requestAudioPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] ?: false
        val cameraGranted = permissions[android.Manifest.permission.CAMERA] ?: false
        android.util.Log.d("SpendWiseApp", "Permissions granted - Audio: $audioGranted, Camera: $cameraGranted")
    }

    private val fileChooserLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        val clipData = result.data?.clipData
        val results: Array<android.net.Uri>? = when {
            uri != null -> arrayOf(uri)
            clipData != null -> Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
            else -> null
        }
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.ui.theme.NotificationHelper.registerNotificationChannel(this)

        // Ensure runtime permissions for microphone audio and camera are requested
        val requiredPerms = arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CAMERA
        )
        val missingPerms = requiredPerms.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missingPerms.isNotEmpty()) {
            requestAudioPermissionLauncher.launch(missingPerms.toTypedArray())
        }

        setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color(0xFF0E, 0x0F, 0x0C))
                        .systemBarsPadding()
                ) {
                    SmartBudgetCockpitView()
                }
            }
        }
    }

    class SpendWiseNativeInterface(private val activity: MainActivity) {
        @android.webkit.JavascriptInterface
        fun saveFile(base64Data: String, mimeType: String, filename: String): Boolean {
            return try {
                val cleanBase64 = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
                val decodedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                    val resolver = activity.contentResolver
                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(decodedBytes)
                            outputStream.flush()
                        }
                        activity.runOnUiThread {
                            android.widget.Toast.makeText(activity, "Saved to Downloads: $filename", android.widget.Toast.LENGTH_LONG).show()
                        }
                        true
                    } else false
                } else {
                    @Suppress("DEPRECATION")
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    val file = java.io.File(downloadsDir, filename)
                    java.io.FileOutputStream(file).use { it.write(decodedBytes) }
                    activity.runOnUiThread {
                        android.widget.Toast.makeText(activity, "Saved to Downloads: $filename", android.widget.Toast.LENGTH_LONG).show()
                    }
                    true
                }
            } catch (e: Exception) {
                android.util.Log.e("SpendWiseNative", "Error saving file: ${e.message}", e)
                activity.runOnUiThread {
                    android.widget.Toast.makeText(activity, "Failed to save file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
                false
            }
        }

        @android.webkit.JavascriptInterface
        fun requestAudioPermission() {
            activity.runOnUiThread {
                activity.requestAudioPermissionLauncher.launch(
                    arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.CAMERA)
                )
            }
        }
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    @Composable
    fun SmartBudgetCockpitView() {
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    clearCache(true)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        allowFileAccessFromFileURLs = true
                        allowUniversalAccessFromFileURLs = true
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        javaScriptCanOpenWindowsAutomatically = true
                        mediaPlaybackRequiresUserGesture = false
                        cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }
                    isVerticalScrollBarEnabled = true
                    isHorizontalScrollBarEnabled = false
                    addJavascriptInterface(SpendWiseNativeInterface(this@MainActivity), "SpendWiseNative")
                    setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                        try {
                            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
                                setMimeType(mimetype)
                                val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
                                addRequestHeader("cookie", cookies)
                                addRequestHeader("User-Agent", userAgent)
                                setDescription("Downloading SpendWise file...")
                                setTitle(android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype))
                                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                setDestinationInExternalPublicDir(
                                    android.os.Environment.DIRECTORY_DOWNLOADS,
                                    android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
                                )
                            }
                            val dm = ctx.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                            dm.enqueue(request)
                            android.widget.Toast.makeText(ctx, "Download started...", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.util.Log.e("SpendWiseApp", "Download listener error: ${e.message}")
                        }
                    }
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            android.util.Log.d("SpendWiseApp", "WebView loaded successfully: $url")
                        }
                        override fun onReceivedError(
                            view: android.webkit.WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            android.util.Log.e("SpendWiseApp", "WebView resource error: ${error?.description}")
                        }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                            consoleMessage?.let {
                                android.util.Log.d("SpendWiseJS", "[${it.messageLevel()}] ${it.message()} (line ${it.lineNumber()} of ${it.sourceId()})")
                            }
                            return true
                        }
                        override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                            request?.grant(request.resources)
                        }
                        override fun onShowFileChooser(
                            webView: android.webkit.WebView?,
                            filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            this@MainActivity.filePathCallback?.onReceiveValue(null)
                            this@MainActivity.filePathCallback = filePathCallback
                            val intent = fileChooserParams?.createIntent() ?: android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                                type = "image/*"
                                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                            }
                            return try {
                                fileChooserLauncher.launch(intent)
                                true
                            } catch (e: Exception) {
                                this@MainActivity.filePathCallback = null
                                false
                            }
                        }
                    }
                    loadUrl("file:///android_asset/index.html")
                }
            }
        )
    }

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainAppScaffold() {
val currentTabState by viewModel.currentTab.collectAsStateWithLifecycle()
val currentLanguageState by viewModel.currentLanguage.collectAsStateWithLifecycle()

Box(
modifier = Modifier
.fillMaxSize()
.background(MaterialTheme.colorScheme.background),
contentAlignment = Alignment.TopCenter
) {
BoxWithConstraints(
modifier = Modifier.fillMaxSize()
) {
val maxWidthDp = maxWidth
val isWide = maxWidthDp > 720.dp

if (isWide) {
Row(modifier = Modifier.fillMaxSize()) {
// Navigation Rail for large screens (Laptop/Tablet)
NavigationRail(
modifier = Modifier.fillMaxHeight()
) {
val tabs = listOf(
Triple("dashboard", com.example.ui.theme.Localization.getString("dashboard", currentLanguageState), Icons.Default.Home),
Triple("expenses", com.example.ui.theme.Localization.getString("expenses", currentLanguageState), Icons.Default.List),
Triple("goals", com.example.ui.theme.Localization.getString("goals", currentLanguageState), Icons.Default.Star),
Triple("analytics", com.example.ui.theme.Localization.getString("analytics", currentLanguageState), Icons.Default.Info),
Triple("profile", com.example.ui.theme.Localization.getString("profile", currentLanguageState), Icons.Default.Person)
)

Spacer(modifier = Modifier.height(24.dp))
Text(
text = "Smart\nBudget",
fontSize = 16.sp,
fontWeight = FontWeight.Black,
color = MaterialTheme.colorScheme.primary,
textAlign = TextAlign.Center,
modifier = Modifier.padding(bottom = 24.dp)
)

tabs.forEach { (route, label, icon) ->
val isSelected = currentTabState == route || (route == "dashboard" && currentTabState == "coach")
NavigationRailItem(
selected = isSelected,
onClick = { viewModel.navigateTo(route) },
icon = { Icon(icon, contentDescription = label) },
label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
)
}
}

// Wide layout content frame
Box(
modifier = Modifier
.weight(1f)
.fillMaxHeight()
.background(MaterialTheme.colorScheme.background),
contentAlignment = Alignment.TopCenter
) {
Box(
modifier = Modifier
.widthIn(max = 1000.dp)
.fillMaxHeight()
.padding(horizontal = 24.dp)
) {
ScreenContent(currentRoute = currentTabState)
}
}
}
} else {
// Mobile Bottom Navigation Bar Layout with Floating Add Button
Scaffold(
modifier = Modifier.fillMaxSize(),
floatingActionButton = {
if (currentTabState != "coach" && currentTabState != "expenses" && currentTabState != "goals") {
com.example.ui.components.FloatingAddButton(
onAddExpense = { viewModel.navigateTo("expenses") },
onDepositToGoal = { viewModel.navigateTo("goals") },
onQuickNote = { viewModel.navigateTo("expenses") }
)
}
},
bottomBar = {
NavigationBar(
modifier = Modifier.navigationBarsPadding(),
containerColor = MaterialTheme.colorScheme.surface,
tonalElevation = 8.dp
) {
val tabs = listOf(
Triple("dashboard", com.example.ui.theme.Localization.getString("dashboard", currentLanguageState), Icons.Default.Home),
Triple("expenses", com.example.ui.theme.Localization.getString("expenses", currentLanguageState), Icons.Default.List),
Triple("goals", com.example.ui.theme.Localization.getString("goals", currentLanguageState), Icons.Default.Star),
Triple("analytics", com.example.ui.theme.Localization.getString("analytics", currentLanguageState), Icons.Default.Info),
Triple("profile", com.example.ui.theme.Localization.getString("profile", currentLanguageState), Icons.Default.Person)
)

tabs.forEach { (route, label, icon) ->
val isSelected = currentTabState == route || (route == "dashboard" && currentTabState == "coach")
NavigationBarItem(
selected = isSelected,
onClick = { viewModel.navigateTo(route) },
icon = { Icon(icon, contentDescription = label) },
label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp) },
colors = NavigationBarItemDefaults.colors(
selectedIconColor = MaterialTheme.colorScheme.onPrimary,
selectedTextColor = MaterialTheme.colorScheme.primary,
indicatorColor = MaterialTheme.colorScheme.primary,
unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)
)
}
}
}
) { innerPadding ->
Box(
modifier = Modifier
.fillMaxSize()
.padding(innerPadding)
) {
ScreenContent(currentRoute = currentTabState)
}
}
}
}
}
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ScreenContent(currentRoute: String) {
AnimatedContent(
targetState = currentRoute,
transitionSpec = {
// Shared-axis X slide 300ms FastOutSlowIn
slideInHorizontally(
initialOffsetX = { fullWidth -> (fullWidth * 0.4f).toInt() },
animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
) + fadeIn(
animationSpec = androidx.compose.animation.core.tween(300)
) with slideOutHorizontally(
targetOffsetX = { fullWidth -> (-fullWidth * 0.4f).toInt() },
animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
) + fadeOut(
animationSpec = androidx.compose.animation.core.tween(200)
)
},
label = "ScreenTransition"
) { route ->
when (route) {
"dashboard" -> DashboardScreen(
viewModel = viewModel,
onNavigateToAiCoach = { viewModel.navigateTo("coach") },
onNavigateToNetWorth = { viewModel.navigateTo("networth") }
)
"expenses" -> ExpensesScreen(viewModel = viewModel)
"goals" -> GoalsScreen(viewModel = viewModel)
"analytics" -> AnalyticsScreen(viewModel = viewModel)
"profile" -> ProfileScreen(
viewModel = viewModel,
onNavigateToNetWorth = { viewModel.navigateTo("networth") },
onNavigateToCoach = { viewModel.navigateTo("coach") }
)
"coach" -> AiCoachScreen(viewModel = viewModel)
"networth" -> NetWorthScreen(
viewModel = viewModel,
onBack = { viewModel.navigateTo("dashboard") }
)
}
}
}
}
