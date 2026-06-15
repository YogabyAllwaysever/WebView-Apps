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
import android.os.Message
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.material.icons.filled.Laptop

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

// Translate raw input/query into fully qualified search or loading URL
fun resolveInputUrl(input: String): String {
    val clean = input.trim()
    if (clean.isEmpty()) return "https://google.com"
    
    // Check if it's a search query (no dots, or contains spaces)
    if (!clean.contains(".") || clean.contains(" ")) {
        val query = Uri.encode(clean)
        return "https://www.google.com/search?q=$query"
    }
    
    if (clean.startsWith("http://") || clean.startsWith("https://")) {
        return clean
    }
    return "https://$clean"
}

// Determine if a URL belongs to common OAuth, login, sign-in, or authentication pages that should be rendered inline.
fun isAuthOrIdentityUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("accounts.google.com") ||
           lower.contains("appleid.apple.com") ||
           lower.contains("facebook.com/dialog/oauth") ||
           lower.contains("facebook.com/v") ||
           lower.contains("api.twitter.com/oauth") ||
           lower.contains("github.com/login") ||
           lower.contains("github.com/join") ||
           lower.contains("github.com/oauth") ||
           lower.contains("auth0.com") ||
           lower.contains("okta.com") ||
           lower.contains("microsoftonline.com") ||
           lower.contains("firebaseapp.com/__/auth") ||
           lower.contains("supabase.co") ||
           lower.contains("supabase.com") ||
           lower.contains("cognito") ||
           lower.contains("keycloak") ||
           lower.contains("clerk.com") ||
           lower.contains("auth.") ||
           lower.contains("login.") ||
           lower.contains("/oauth") ||
           lower.contains("/auth/") ||
           lower.contains("/login") ||
           lower.contains("/signin") ||
           lower.contains("/signup") ||
           lower.contains("/api/auth")
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
    var isBrowserMode by remember {
        mutableStateOf(sharedPref.getBoolean("is_browser_mode", true))
    }
    var isDesktopMode by remember {
        mutableStateOf(sharedPref.getBoolean("is_desktop_mode", false))
    }

    // If an incoming intent brought a specific URL, priority load it directly
    val activeUrlToLoad = remember(initialUrlIntent, savedUrl) {
        if (!initialUrlIntent.isNullOrEmpty()) {
            initialUrlIntent
        } else {
            savedUrl
        }
    }

    var addressBarInput by remember { mutableStateOf(activeUrlToLoad) }

    // Webster Runtime states
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentLoadedUrl by remember { mutableStateOf(activeUrlToLoad) }

    // Sync input box when loaded url loads successfully or changes
    LaunchedEffect(currentLoadedUrl) {
        addressBarInput = currentLoadedUrl
    }

    // Dynamic desktop site configuration handler
    LaunchedEffect(isDesktopMode) {
        webViewInstance?.let { wv ->
            wv.settings.userAgentString = if (isDesktopMode) {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            } else {
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }
            wv.reload()
        }
    }
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Mode Browser Lite", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Tampilkan bilah alamat & kontrol navigasi Chrome Lite", fontSize = 11.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = isBrowserMode,
                                onCheckedChange = {
                                    isBrowserMode = it
                                    sharedPref.edit().putBoolean("is_browser_mode", it).apply()
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Mode Situs Desktop", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Muat situs web versi komputer/desktop", fontSize = 11.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = isDesktopMode,
                                onCheckedChange = {
                                    isDesktopMode = it
                                    sharedPref.edit().putBoolean("is_desktop_mode", it).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFBB86FC),
                                    checkedTrackColor = Color(0xFFBB86FC).copy(alpha = 0.5f)
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

                        HorizontalDivider(color = Color(0xFF2C2C2C))

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
        ) {
            if (isBrowserMode) {
                BrowserTopBar(
                    currentUrl = currentLoadedUrl,
                    addressInput = addressBarInput,
                    onAddressInputChange = { newValue: String -> addressBarInput = newValue },
                    onNavigate = { inputUrl: String ->
                        val targetUrl = resolveInputUrl(inputUrl)
                        webViewInstance?.loadUrl(targetUrl)
                    },
                    canGoBack = canGoBack,
                    onBack = {
                        webViewInstance?.let { if (it.canGoBack()) it.goBack() }
                    },
                    canGoForward = canGoForward,
                    onForward = {
                        webViewInstance?.let { if (it.canGoForward()) it.goForward() }
                    },
                    isLoading = isLoading,
                    onRefreshOrStop = {
                        webViewInstance?.let {
                            if (isLoading) it.stopLoading() else it.reload()
                        }
                    },
                    isDesktopMode = isDesktopMode,
                    onToggleDesktop = {
                        isDesktopMode = !isDesktopMode
                        sharedPref.edit().putBoolean("is_desktop_mode", isDesktopMode).apply()
                    },
                    onOpenMenu = {
                        scope.launch { drawerState.open() }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
                                setSupportMultipleWindows(true)
                                setJavaScriptCanOpenWindowsAutomatically(true)

                                userAgentString = if (isDesktopMode) {
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                } else {
                                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                }

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

                                // Block external redirections leaking scope or redirect to actual browser
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val destination = request?.url?.toString() ?: return false
                                    val targetDomain = request.url?.host ?: ""
                                    val baseDomain = try { Uri.parse(savedUrl).host } catch (e: Exception) { null }

                                    // In Chrome Lite Browser mode, allow user to browse any page inline in Android!
                                    if (isBrowserMode) {
                                        if (destination.startsWith("mailto:") || destination.startsWith("tel:")) {
                                            try {
                                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destination)))
                                                return true
                                            } catch (e: Exception) {
                                                // Ignore
                                            }
                                        }
                                        return false
                                    }

                                    // Standalone mode intercepts external domains unless they are common OAuth/auth flows
                                    val isAuth = isAuthOrIdentityUrl(destination)
                                    if (!isAuth && baseDomain != null && targetDomain.isNotEmpty() && !targetDomain.contains(baseDomain) &&
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
                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message?
                                ): Boolean {
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                                    if (transport != null) {
                                        // Force opening target="_blank" popups inside the current/main WebView
                                        transport.webView = view
                                        resultMsg.sendToTarget()
                                        return true
                                    }
                                    return false
                                }

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
                            .align(Alignment.TopCenter)
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
            if (isOfflineState) {
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
}

@Composable
fun BrowserTopBar(
    currentUrl: String,
    addressInput: String,
    onAddressInputChange: (String) -> Unit,
    onNavigate: (String) -> Unit,
    canGoBack: Boolean,
    onBack: () -> Unit,
    canGoForward: Boolean,
    onForward: () -> Unit,
    isLoading: Boolean,
    onRefreshOrStop: () -> Unit,
    isDesktopMode: Boolean,
    onToggleDesktop: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        color = Color(0xFF1E1E1E),
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Back Button
                IconButton(
                    onClick = onBack,
                    enabled = canGoBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = if (canGoBack) Color.White else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Forward Button
                IconButton(
                    onClick = onForward,
                    enabled = canGoForward,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Maju",
                        tint = if (canGoForward) Color.White else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Address Omnibox with Lock Icon & Input Field
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF121212))
                        .border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isHttps = currentUrl.startsWith("https://")
                    Icon(
                        imageVector = if (isHttps) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isHttps) "Koneksi Aman" else "Koneksi Tidak Aman",
                        tint = if (isHttps) Color(0xFF03DAC6) else Color(0xFFCF6679),
                        modifier = Modifier
                            .size(14.dp)
                            .clickable {
                                Toast
                                    .makeText(
                                        context,
                                        if (isHttps) "Situs terenkripsi dengan aman (HTTPS)" else "Lalu lintas data tidak dienkripsi (HTTP)",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            }
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    androidx.compose.foundation.text.BasicTextField(
                        value = addressInput,
                        onValueChange = onAddressInputChange,
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = {
                                onNavigate(addressInput)
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF03DAC6)),
                        decorationBox = { innerTextField ->
                            if (addressInput.isEmpty()) {
                                Text(
                                    text = "Telusuri atau ketik URL...",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (addressInput.isNotEmpty()) {
                        IconButton(
                            onClick = { onAddressInputChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Bersihkan",
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Copy active link
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("URL", currentUrl)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Link berhasil disalin ke papan klip!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Salin Url",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Desktop site toggle indicator
                IconButton(
                    onClick = onToggleDesktop,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Laptop,
                        contentDescription = "Desktop Site Mode",
                        tint = if (isDesktopMode) Color(0xFFBB86FC) else Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Refresh / Stop Action
                IconButton(
                    onClick = onRefreshOrStop,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = if (isLoading) "Batal" else "Muat Ulang",
                        tint = if (isLoading) Color(0xFFCF6679) else Color(0xFF03DAC6),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Open menu / drawer
                IconButton(
                    onClick = onOpenMenu,
                    modifier = Modifier.size(31.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu Pengaturan",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            HorizontalDivider(color = Color(0xFF2C2C2C), thickness = 1.dp)
        }
    }
}
