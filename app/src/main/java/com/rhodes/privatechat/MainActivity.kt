package com.rhodes.privatechat

import android.graphics.Color
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.core.content.FileProvider
import com.rhodes.privatechat.navigation.AppNavigation
import com.rhodes.privatechat.notification.RhodesAppVisibility
import com.rhodes.privatechat.notification.RhodesNotificationCenter
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.theme.isDarkMode
import com.rhodes.privatechat.ui.theme.AppearanceSkin
import com.rhodes.privatechat.ui.theme.currentAppearanceSkin
import com.rhodes.privatechat.ui.theme.罗德岛终端Theme
import java.io.File
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.koin.java.KoinJavaComponent.inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {
    private var showStartup by androidx.compose.runtime.mutableStateOf(false)
    private var startupSound = false
    private var imageResultHandler: ((String) -> Unit)? = null
    private var pendingCameraUri: Uri? = null
    private var pendingTakePhotoHandler: ((String) -> Unit)? = null
    private var pendingMicrophoneHandler: ((Boolean) -> Unit)? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val handler = imageResultHandler
        imageResultHandler = null
        if (uri != null && handler != null) lifecycleScope.launch {
            val copied = withContext(Dispatchers.IO) { copyChatImageToFiles(uri) }
            handler(copied.toString())
        }
    }
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        val handler = pendingTakePhotoHandler
        pendingCameraUri = null
        pendingTakePhotoHandler = null
        if (ok && uri != null && handler != null) lifecycleScope.launch {
            val copied = withContext(Dispatchers.IO) { copyChatImageToFiles(uri) }
            handler(copied.toString())
        }
    }
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val handler = pendingTakePhotoHandler ?: return@registerForActivityResult
        if (granted) launchTakePhoto(handler) else pendingTakePhotoHandler = null
    }
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val microphonePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingMicrophoneHandler?.invoke(granted)
        pendingMicrophoneHandler = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val shouldDropSavedState = consumeDropSavedStateFlag()
        super.onCreate(if (shouldDropSavedState) null else savedInstanceState)
        val settings: SettingsRepository by inject(SettingsRepository::class.java)
        val opening = com.rhodes.privatechat.ui.startup.StartupPreferences(this)
        startupSound = opening.sound
        showStartup = opening.claim(savedInstanceState != null, isDirectEntry(intent))
        current = this
        RhodesNotificationCenter.ensureChannels(this)
        currentAppearanceSkin = AppearanceSkin.fromStorage(settings.appearanceSkin, settings.darkMode)
        isDarkMode = currentAppearanceSkin.isDark
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        enableEdgeToEdge(
            statusBarStyle = if (isDarkMode) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (isDarkMode) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            罗德岛终端Theme {
                val app = application as RhodesApplication
                val databaseReady by app.databaseReady.collectAsState()
                val databaseError by app.databaseError.collectAsState()
                androidx.compose.runtime.LaunchedEffect(showStartup, databaseReady) {
                    if (!showStartup && databaseReady && android.os.Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    if (databaseReady) AppNavigation() else {
                        androidx.compose.foundation.layout.Column(
                            androidx.compose.ui.Modifier.fillMaxSize().background(com.rhodes.privatechat.ui.theme.BG),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                        ) {
                            if (databaseError) {
                                androidx.compose.material3.Text("本地数据暂时未能打开，请重试。", color = com.rhodes.privatechat.ui.theme.TextPrimary)
                                androidx.compose.material3.TextButton(onClick = { app.prepareDatabase() }) { androidx.compose.material3.Text("重试") }
                            } else {
                                androidx.compose.material3.CircularProgressIndicator(color = com.rhodes.privatechat.ui.theme.Primary)
                                androidx.compose.material3.Text("正在准备通讯端…", color = com.rhodes.privatechat.ui.theme.TextSecondary)
                            }
                        }
                    }
                    if (showStartup) com.rhodes.privatechat.ui.startup.TerminalStartup(startupSound) { showStartup = false }
                }
            }
        }
        publishNavigationIntent(intent)
    }
    override fun onStart() {
        super.onStart()
        RhodesAppVisibility.isForeground = true
        current = this
    }

    override fun onStop() {
        RhodesAppVisibility.isForeground = false
        super.onStop()
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    fun pickChatImage(handler: (String) -> Unit) {
        imageResultHandler = handler
        pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun takeChatPhoto(handler: (String) -> Unit) {
        pendingTakePhotoHandler = handler
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        } else {
            launchTakePhoto(handler)
        }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isDirectEntry(intent)) showStartup = false
        publishNavigationIntent(intent)
    }

    fun ensureMicrophonePermission(handler: (Boolean) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            handler(true)
        } else {
            pendingMicrophoneHandler = handler
            microphonePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun launchTakePhoto(handler: (String) -> Unit) {
        pendingTakePhotoHandler = handler
        val dir = File(cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun copyChatImageToFiles(uri: Uri): Uri {
        val dir = File(filesDir, "chat_images").apply { mkdirs() }
        val file = File(dir, "image_${System.currentTimeMillis()}.jpg")
        runCatching {
            val bitmap = decodeSampledBitmap(uri, 1920) ?: error("无法解码图片")
            run {
                val maxDim = 1920
                val scaled = if (maxOf(bitmap.width, bitmap.height) > maxDim) {
                    val ratio = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
                } else bitmap
                file.outputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 80, out) }
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
            }
        }.onFailure { file.delete() }
        return Uri.fromFile(file)
    }

    fun prepareImageForModel(uriText: String): String? {
        if (uriText.startsWith("http://") || uriText.startsWith("https://") || uriText.startsWith("data:")) return uriText
        val uri = Uri.parse(uriText)
        val bytes = runCatching {
            val bitmap = decodeSampledBitmap(uri, 1600) ?: return@runCatching null
            val scaled = if (maxOf(bitmap.width, bitmap.height) > 1600) {
                val ratio = 1600f / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else bitmap
            java.io.ByteArrayOutputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
                output.toByteArray()
            }
        }.getOrNull() ?: return null
        if (bytes.size > 4 * 1024 * 1024) return null
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decodeSampledBitmap(uri: Uri, maxDimension: Int): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDimension * 2 || bounds.outHeight / sample > maxDimension * 2) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun consumeDropSavedStateFlag(): Boolean {
        val prefs = getSharedPreferences("rhodes_runtime", Context.MODE_PRIVATE)
        val shouldDrop = prefs.getBoolean("drop_saved_state_once", false)
        if (shouldDrop) prefs.edit().putBoolean("drop_saved_state_once", false).apply()
        return shouldDrop
    }

    companion object {
        private fun isDirectEntry(intent: android.content.Intent?): Boolean =
            !intent?.getStringExtra("nav_session_id").isNullOrBlank() || intent?.data != null ||
                intent?.action == android.content.Intent.ACTION_SEND || intent?.action == android.content.Intent.ACTION_SEND_MULTIPLE

        data class NavigationRequest(val sessionId: String, val isGroup: Boolean, val nonce: Long)
        private val _navigationRequest = MutableStateFlow<NavigationRequest?>(null)
        val navigationRequest = _navigationRequest.asStateFlow()
        @Volatile private var current: MainActivity? = null
        fun pickImage(handler: (String) -> Unit) { current?.pickChatImage(handler) }
        fun takePhoto(handler: (String) -> Unit) { current?.takeChatPhoto(handler) }
        fun requestMicrophonePermission(handler: (Boolean) -> Unit) { current?.ensureMicrophonePermission(handler) ?: handler(false) }
        fun imageForModel(uriText: String): String? = current?.prepareImageForModel(uriText)

        private fun publishNavigationIntent(intent: android.content.Intent?) {
            val sessionId = intent?.getStringExtra("nav_session_id")?.takeIf { it.isNotBlank() } ?: return
            _navigationRequest.value = NavigationRequest(sessionId, intent.getBooleanExtra("nav_is_group", false), System.currentTimeMillis())
        }

        fun consumeNavigationRequest(nonce: Long) {
            if (_navigationRequest.value?.nonce == nonce) _navigationRequest.value = null
        }
    }
}
