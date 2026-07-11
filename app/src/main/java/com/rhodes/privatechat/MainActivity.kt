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
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import com.rhodes.privatechat.navigation.AppNavigation
import com.rhodes.privatechat.notification.RhodesAppVisibility
import com.rhodes.privatechat.notification.RhodesNotificationCenter
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.theme.isDarkMode
import com.rhodes.privatechat.ui.theme.罗德岛终端Theme
import java.io.File
import org.koin.java.KoinJavaComponent.inject

class MainActivity : ComponentActivity() {
    private var imageResultHandler: ((String) -> Unit)? = null
    private var pendingCameraUri: Uri? = null
    private var pendingTakePhotoHandler: ((String) -> Unit)? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { imageResultHandler?.invoke(copyChatImageToFiles(it).toString()) }
        imageResultHandler = null
    }
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        if (ok && uri != null) pendingTakePhotoHandler?.invoke(copyChatImageToFiles(uri).toString())
        pendingCameraUri = null
        pendingTakePhotoHandler = null
    }
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val handler = pendingTakePhotoHandler ?: return@registerForActivityResult
        if (granted) launchTakePhoto(handler) else pendingTakePhotoHandler = null
    }
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        val shouldDropSavedState = consumeDropSavedStateFlag()
        super.onCreate(if (shouldDropSavedState) null else savedInstanceState)
        val settings: SettingsRepository by inject(SettingsRepository::class.java)
        current = this
        RhodesNotificationCenter.ensureChannels(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        isDarkMode = settings.darkMode
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        enableEdgeToEdge(
            statusBarStyle = if (isDarkMode) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (isDarkMode) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            罗德岛终端Theme {
                AppNavigation()
            }
        }
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
        contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        return Uri.fromFile(file)
    }

    fun prepareImageForModel(uriText: String): String? {
        if (uriText.startsWith("http://") || uriText.startsWith("https://") || uriText.startsWith("data:")) return uriText
        val uri = Uri.parse(uriText)
        val bytes = runCatching {
            when (uri.scheme) {
                "file" -> File(uri.path.orEmpty()).readBytes()
                else -> contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
        }.getOrNull() ?: return null
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun consumeDropSavedStateFlag(): Boolean {
        val prefs = getSharedPreferences("rhodes_runtime", Context.MODE_PRIVATE)
        val shouldDrop = prefs.getBoolean("drop_saved_state_once", false)
        if (shouldDrop) prefs.edit().putBoolean("drop_saved_state_once", false).apply()
        return shouldDrop
    }

    companion object {
        @Volatile private var current: MainActivity? = null
        fun pickImage(handler: (String) -> Unit) { current?.pickChatImage(handler) }
        fun takePhoto(handler: (String) -> Unit) { current?.takeChatPhoto(handler) }
        fun imageForModel(uriText: String): String? = current?.prepareImageForModel(uriText)
    }
}
