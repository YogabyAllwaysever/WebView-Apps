package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.launch

// Model data representing a popular PWA preset
data class PwaPreset(
    val name: String,
    val url: String,
    val icon: ImageVector,
    val description: String,
    val accentColor: Color
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Define classical elegant Material Design 2 Dark Scheme colors
            val md2DarkColorScheme = darkColorScheme(
                primary = Color(0xFF03DAC6),       // Modern Bright Teal Accent
                secondary = Color(0xFFBB86FC),     // Material 2 Default Purple Accent
                background = Color(0xFF121212),    // Pure Clean Carbon/Grey
                surface = Color(0xFF1E1E1E),       // Classic Elevational Card Grey
                onPrimary = Color.Black,
                onSecondary = Color.Black,
                onBackground = Color.White,
                onSurface = Color.White,
                surfaceVariant = Color(0xFF2C2C2C),
                outline = Color(0xFF3E3E3E)
            )

            MaterialTheme(
                colorScheme = md2DarkColorScheme
            ) {
                // Read potential deep link metadata/shortcut intent parameter
                val incomingUrl = intent?.getStringExtra("target_url") ?: intent?.data?.toString()
                
                MainAppScreen(initialUrlIntent = incomingUrl)
            }
        }
    }
}

// Check connectivity considering backward API compatibility
fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    } else {
        @Suppress("DEPRECATION")
        val networkInfo = connectivityManager.activeNetworkInfo
        @Suppress("DEPRECATION")
        return networkInfo != null && networkInfo.isConnected
    }
}

// Pin PWA Shortcut onto the Home Screen
fun createPwaShortcut(context: Context, name: String, url: String) {
    if (name.trim().isEmpty() || url.trim().isEmpty()) {
        Toast.makeText(context, "Sila masukkan nama dan URL terlebih dahulu!", Toast.LENGTH_SHORT).show()
        return
    }

    // Direct configuration of action intent with parameters
    val shortcutIntent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse(url)
        putExtra("target_url", url)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    val id = "pwa_${System.currentTimeMillis()}"
    val iconRes = IconCompat.createWithResource(context, context.applicationInfo.icon)

    val shortcutInfo = ShortcutInfoCompat.Builder(context, id)
        .setShortLabel(name)
        .setLongLabel(name)
        .setIcon(iconRes)
        .setIntent(shortcutIntent)
        .build()

    if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
        Toast.makeText(context, "Shortcut '$name' dikirim ke Layar Utama!", Toast.LENGTH_LONG).show()
    } else {
        // Broadcast legacy fallback
        val installerIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
            putExtra(Intent.EXTRA_SHORTCUT_NAME, name)
            val iconResource = Intent.ShortcutIconResource.fromContext(context, context.applicationInfo.icon)
            putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource)
        }
        context.sendBroadcast(installerIntent)
        Toast.makeText(context, "Shortcut '$name' dibuat!", Toast.LENGTH_LONG).show()
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(initialUrlIntent: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPref = remember { context.getSharedPreferences("pwa_webview_prefs", Context.MODE_PRIVATE) }

    // Persistent Settings State
    var savedUrl by remember {
        mutableStateOf(sharedPref.getString("configured_url", "https://pwa.rocks") ?: "https://pwa.rocks")
    }
    var cacheModeOption by remember {
        mutableStateOf(sharedPref.getString("cache_mode_option", "OFFLINE_FIRST") ?: "OFFLINE_FIRST")
    }
    var pullToRefreshEnabled by remember {
        mutableStateOf(sharedPref.getBoolean("pull_to_refresh_enabled", true))
    }

    // If an incoming intent brought a specific URL, priority load it directly
    val activeUrlToLoad = remember(initialUrlIntent, savedUrl) {
        if (!initialUrlIntent.isNullOrEmpty()) {
            initialUrlIntent
        } else {
            savedUrl
        }
    }

    // Webster Runtime states
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentLoadedUrl by remember { mutableStateOf(activeUrlToLoad) }
    var isLoading by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isOfflineState by remember { mutableStateOf(false) }
    var isWebViewAtTop by remember { mutableStateOf(true) }
    var isRefreshingState by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    // File selection utility parameters
    var uploadMessageCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val dataUri = result.data?.data
            val dataClip = result.data?.clipData
            val uris = mutableListOf<Uri>()
            if (dataUri != null) {
                uris.add(dataUri)
            } else if (dataClip != null) {
                for (i in 0 until dataClip.itemCount) {
                    uris.add(dataClip.getItemAt(i).uri)
                }
            }
            if (uris.isNotEmpty()) {
                uploadMessageCallback?.onReceiveValue(uris.toTypedArray())
            } else {
                uploadMessageCallback?.onReceiveValue(null)
            }
        } else {
            uploadMessageCallback?.onReceiveValue(null)
        }
        uploadMessageCallback = null
    }

    // Intercept hardware/system back button
    BackHandler(enabled = canGoBack) {
        webViewInstance?.let {
            if (it.canGoBack()) {
                it.goBack()
            }
        }
    }

    // Preconfigured Preset List
    val pwaPresets = listOf(
        PwaPreset("PWA Rocks", "https://pwa.rocks", Icons.Default.RocketLaunch, "PWA Directory", Color(0xFF03DAC6)),
        PwaPreset("Pinterest", "https://pinterest.com", Icons.Default.Image, "Inspirasi & Estetika", Color(0xFFBD081C)),
        PwaPreset("Dev.to", "https://dev.to", Icons.Default.Code, "Komunitas Developer", Color(0xFFBB86FC)),
        PwaPreset("Wikipedia", "https://en.m.wikipedia.org", Icons.Default.Book, "Ensiklopedia Bebas", Color(0xFFE0E0E0)),
        PwaPreset("Wordle", "https://www.nytimes.com/games/wordle", Icons.Default.Casino, "Tebak Kata NYT", Color(0xFF6AAA64)),
        PwaPreset("Instagram", "https://instagram.com", Icons.Default.CameraAlt, "Media Sosial Visual", Color(0xFFE1306C))
    )

    // Pull physical state managers
    var pullDeltaY by remember { mutableStateOf(0f) }
    val pullMaxThreshold = 310f
    val pullTriggerThreshold = 190f

    // Navigation drawer state (Swipe Right opens settings)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true, // Perfectly enables edge-swipe (swipe right) to open
        drawerContent = {
            Surface(
                color = Color(0xFF1E1E1E), // Solid elevated dark surface
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .border(
                        BorderStroke(1.dp, Color(0xFF2C2C2C)),
                        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                    )
            ) {
                // Sidebar Scrollable Layout
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Sidebar Settings Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF121212))
                            .padding(horizontal = 24.dp, vertical = 28.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Pengaturan",
                                tint = Color(0xFF03DAC6),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "PWA Shell",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "Konfigurasi URL, caching, dan shortcut launcher",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // Setting items scroll wrap
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Part 1: Custom Web URL setting input
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Alamat Web Utama",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF03DAC6)
                            )
                            
                            var inputUrlText by remember { mutableStateOf(savedUrl) }
                            
                            OutlinedTextField(
                                value = inputUrlText,
                                onValueChange = { inputUrlText = it },
                                placeholder = { Text("https://example.com") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF03DAC6),
                                    unfocusedBorderColor = Color(0xFF3E3E3E),
                                    cursorColor = Color(0xFF03DAC6)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            var cleanUrl = inputUrlText.trim()
                                            if (cleanUrl.isNotEmpty()) {
                                                if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                                                    cleanUrl = "https://$cleanUrl"
                                                }
                                                savedUrl = cleanUrl
                                                sharedPref.edit().putString("configured_url", cleanUrl).apply()
                                                webViewInstance?.loadUrl(cleanUrl)
                                                Toast.makeText(context, "URL disimpan & dimuat!", Toast.LENGTH_SHORT).show()
                                                scope.launch { drawerState.close() }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Simpan", tint = Color(0xFF03DAC6))
                                    }
                                }
                            )
                        }

                        // Part 2: Dynamic Shortcut Creation workspace
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                            border = BorderStroke(1.dp, Color(0xFF2C2C2C))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Buat Shortcut Peluncur",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFFBB86FC)
                                )
                                Text(
                                    text = "Buat launcher icon di layar home Android Anda untuk URL saat ini, menjadikannya seolah-olah aplikasi native mandiri.",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )

                                var shortcutNameInput by remember { mutableStateOf("") }
                                OutlinedTextField(
                                    value = shortcutNameInput,
                                    onValueChange = { shortcutNameInput = it },
                                    label = { Text("Nama Aplikasi Pintasan", fontSize = 12.sp) },
                                    placeholder = { Text("Nama PWA") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFFBB86FC),
                                        unfocusedBorderColor = Color(0xFF2C2C2C),
                                        cursorColor = Color(0xFFBB86FC)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    onClick = {
                                        if (shortcutNameInput.trim().isEmpty()) {
                                            Toast.makeText(context, "Gagal: Sila masukkan nama shortcut!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            createPwaShortcut(context, shortcutNameInput.trim(), currentLoadedUrl)
                                            shortcutNameInput = "" // clear
                                            scope.launch { drawerState.close() }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC), contentColor = Color.Black),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.AddHome, contentDescription = "Daftarkan", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pasang Pintasan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Part 3: Quick Presets Shortcut List
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Preset Navigasi PWA",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.LightGray
                            )
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(pwaPresets) { preset ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                savedUrl = preset.url
                                                sharedPref.edit().putString("configured_url", preset.url).apply()
                                                webViewInstance?.loadUrl(preset.url)
                                                Toast.makeText(context, "Membuka ${preset.name}...", Toast.LENGTH_SHORT).show()
                                                scope.launch { drawerState.close() }
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                                        border = BorderStroke(1.dp, Color(0xFF2C2C2C))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = preset.icon,
                                                contentDescription = preset.name,
                                                tint = preset.accentColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = preset.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Part 4: Advanced Offline caching controller
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                            border = BorderStroke(1.dp, Color(0xFF2C2C2C))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Mode Caching Data",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF03DAC6)
                                )

                                val cacheOptions = listOf(
                                    "OFFLINE_FIRST" to "Prioritaskan Offline",
                                    "DEFAULT" to "Setelan Default Browser",
                                    "NETWORK_ONLY" to "Selalu Jaringan (Tanpa Cache)"
                                )

                                cacheOptions.forEach { (key, label) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                cacheModeOption = key
                                                sharedPref.edit().putString("cache_mode_option", key).apply()
                                                webViewInstance?.let { wv ->
                                                    val online = isNetworkAvailable(context)
                                                    wv.settings.cacheMode = when (key) {
                                                        "OFFLINE_FIRST" -> if (online) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
                                                        "NETWORK_ONLY" -> WebSettings.LOAD_NO_CACHE
                                                        else -> if (online) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
                                                    }
                                                }
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = cacheModeOption == key,
                                            onClick = {
                                                cacheModeOption = key
                                                sharedPref.edit().putString("cache_mode_option", key).apply()
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = Color(0xFF03DAC6),
                                                unselectedColor = Color.Gray
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = label, color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // Toggles section
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Pull-to-Reload Geser", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Geser ke bawah untuk muat ulang", fontSize = 11.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = pullToRefreshEnabled,
                                onCheckedChange = {
                                    pullToRefreshEnabled = it
                                    sharedPref.edit().putBoolean("pull_to_refresh_enabled", it).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF03DAC6),
                                    checkedTrackColor = Color(0xFF03DAC6).copy(alpha = 0.5f)
                                )
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Tombol Kontrol Menu", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Hubungkan kembali navigasi manual", fontSize = 11.sp, color = Color.Gray)
                            }
                            IconButton(
                                onClick = {
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color(0xFFCF6679))
                            }
                        }

                        Divider(color = Color(0xFF2C2C2C))

                        // Wipe / Reset everything button
                        Button(
                            onClick = {
                                webViewInstance?.let { wv ->
                                    wv.clearCache(true)
                                    wv.clearHistory()
                                    wv.reload()
                                }
                                Toast.makeText(context, "Selesai: Semua cache dan cookie web telah dihapus!", Toast.LENGTH_SHORT).show()
                                scope.launch { drawerState.close() }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679), contentColor = Color.Black),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Wipe")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Bersihkan Cache Aplikasi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) {
        val animatedPullOffset by animateFloatAsState(
            targetValue = pullDeltaY,
            animationSpec = spring(dampingRatio = 0.76f, stiffness = 420f),
            label = "physicsPullBounceY"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .pointerInput(isWebViewAtTop, pullToRefreshEnabled) {
                    if (!pullToRefreshEnabled || !isWebViewAtTop) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = {},
                        onDragEnd = {
                            if (pullDeltaY >= pullTriggerThreshold) {
                                isRefreshingState = true
                                webViewInstance?.reload()
                            }
                            pullDeltaY = 0f
                        },
                        onDragCancel = {
                            pullDeltaY = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0 || pullDeltaY > 0) {
                                val frictionResult = pullDeltaY + dragAmount * 0.42f
                                pullDeltaY = frictionResult.coerceIn(0f, pullMaxThreshold)
                                change.consume()
                            }
                        }
                    )
                }
        ) {
            // Android Native Webview Frame Wrapper (Standalone, pure immersion)
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = (animatedPullOffset / 2.6f).dp)
                    .testTag("pwa_webview"),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Optimize settings for native shell operations
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            setSupportZoom(true)

                            // Cache routing setup
                            val activeHasInternet = isNetworkAvailable(ctx)
                            cacheMode = when (cacheModeOption) {
                                "OFFLINE_FIRST" -> if (activeHasInternet) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
                                "NETWORK_ONLY" -> WebSettings.LOAD_NO_CACHE
                                else -> if (activeHasInternet) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
                            }
                        }

                        setOnScrollChangeListener { _, _, scrollY, _, _ ->
                            isWebViewAtTop = (scrollY == 0)
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                isOfflineState = false
                                if (!url.isNullOrEmpty()) {
                                    currentLoadedUrl = url
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                isRefreshingState = false
                                if (!url.isNullOrEmpty()) {
                                    currentLoadedUrl = url
                                    canGoBack = canGoBack()
                                    canGoForward = canGoForward()
                                }
                                isWebViewAtTop = (view?.scrollY == 0)
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    isOfflineState = true
                                    isLoading = false
                                    isRefreshingState = false
                                }
                            }

                            // Block external redirections leaking scope
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val destination = request?.url?.toString() ?: return false
                                val targetDomain = request.url?.host ?: ""
                                val baseDomain = try { Uri.parse(savedUrl).host } catch (e: Exception) { null }

                                if (baseDomain != null && targetDomain.isNotEmpty() && !targetDomain.contains(baseDomain) &&
                                    !destination.startsWith("mailto:") && !destination.startsWith("tel:")) {
                                    try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destination)))
                                        return true
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                                return false
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                            }

                            override fun onGeolocationPermissionsShowPrompt(
                                origin: String?,
                                callback: GeolocationPermissions.Callback?
                            ) {
                                callback?.invoke(origin, true, false)
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                uploadMessageCallback?.onReceiveValue(null)
                                uploadMessageCallback = filePathCallback

                                val selectIntent = fileChooserParams?.createIntent()
                                if (selectIntent != null) {
                                    try {
                                        fileChooserLauncher.launch(selectIntent)
                                    } catch (e: Exception) {
                                        uploadMessageCallback?.onReceiveValue(null)
                                        uploadMessageCallback = null
                                        return false
                                    }
                                }
                                return true
                            }
                        }

                        webViewInstance = this
                        loadUrl(activeUrlToLoad)
                    }
                },
                update = { wv ->
                    val internetOk = isNetworkAvailable(context)
                    wv.settings.cacheMode = when (cacheModeOption) {
                        "OFFLINE_FIRST" -> if (internetOk) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
                        "NETWORK_ONLY" -> WebSettings.LOAD_NO_CACHE
                        else -> if (internetOk) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
                    }
                }
            )

            // Dynamic progress loader bar on standard top edge
            if (isLoading && !isRefreshingState) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(3.dp),
                    color = Color(0xFF03DAC6),
                    trackColor = Color(0xFF03DAC6).copy(alpha = 0.2f)
                )
            }

            // Swipe right reminder guide tab on left edge (subtle interactive hint handle)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(8.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(Color(0xFF03DAC6).copy(alpha = 0.35f))
                    .clickable {
                        scope.launch { drawerState.open() }
                    }
            )

            // Dynamic bouncy reload loader visual representation
            if (pullDeltaY > 0 || isRefreshingState) {
                val degrees = (pullDeltaY / pullMaxThreshold) * 450f
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (24 + (pullDeltaY / 3.6f)).dp)
                        .size(44.dp),
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    border = BorderStroke(1.dp, Color(0xFF2C2C2C)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRefreshingState) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF03DAC6),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                modifier = Modifier
                                    .size(22.dp)
                                    .rotate(degrees),
                                tint = if (pullDeltaY >= pullTriggerThreshold) Color(0xFF03DAC6) else Color.LightGray
                            )
                        }
                    }
                }
            }

            // Indonesian design offline state fallback screen
            AnimatedVisibility(
                visible = isOfflineState,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF121212))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Card(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(24.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                            border = BorderStroke(1.dp, Color(0xFFCF6679).copy(alpha = 0.4f))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiOff,
                                    contentDescription = "Offline",
                                    tint = Color(0xFFCF6679),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Koneksi Terputus",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Silakan bersihkan cache, aktifkan wifi/data Anda, atau gunakan cache offline terakhir yang tersimpan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(30.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Load Cache OfflineFallback
                            Button(
                                onClick = {
                                    webViewInstance?.settings?.cacheMode = WebSettings.LOAD_CACHE_ONLY
                                    webViewInstance?.reload()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C), contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Storage, contentDescription = "Cache", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Muat Cache Offline", fontSize = 12.sp)
                            }

                            // Retry
                            Button(
                                onClick = {
                                    isOfflineState = false
                                    isLoading = true
                                    val internetOk = isNetworkAvailable(context)
                                    webViewInstance?.settings?.cacheMode = when (cacheModeOption) {
                                        "OFFLINE_FIRST" -> if (internetOk) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
                                        "NETWORK_ONLY" -> WebSettings.LOAD_NO_CACHE
                                        else -> if (internetOk) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
                                    }
                                    webViewInstance?.reload()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6), contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Coba Lagi", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
