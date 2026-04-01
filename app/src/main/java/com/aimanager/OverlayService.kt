package com.aimanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class OverlayService : Service(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    // ─── Lifecycle / SavedState для ComposeView ───────────────────────────────
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val viewModelStore = ViewModelStore()

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    // ─── WindowManager ────────────────────────────────────────────────────────
    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        showBubble()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> hideBubble()
            ACTION_SHOW -> showBubble()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        hideBubble()
        viewModelStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── Показать пузырь ──────────────────────────────────────────────────────
    private fun showBubble() {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 200
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
        }

        // Drag + tap логика
        var lastX = 0f
        var lastY = 0f
        var isDragging = false
        var downRawX = 0f
        var downRawY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    downRawX = event.rawX
                    downRawY = event.rawY
                    isDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY
                    if (!isDragging && (Math.abs(event.rawX - downRawX) > 8 || Math.abs(event.rawY - downRawY) > 8)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = (params.x - dx).toInt()
                        params.y = (params.y + dy).toInt()
                        windowManager.updateViewLayout(view, params)
                        lastX = event.rawX
                        lastY = event.rawY
                    }
                    isDragging
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        launchAsFloatingWindow()
                    }
                    isDragging = false
                    false
                }
                else -> false
            }
        }

        view.setContent {
            BubbleButton()
        }

        overlayView = view
        windowManager.addView(view, params)
    }

    // ─── Запуск MainActivity как плавающего окна (Xiaomi/HyperOS Freeform) ────
    private fun launchAsFloatingWindow() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // ── Диагностика: перебираем известные Xiaomi freeform классы и методы ──
        val candidates = listOf(
            "miui.app.MiuiFreeFormManager",
            "com.miui.freeform.MiuiFreeFormManager",
            "android.app.MiuiFreeFormManager",
            "miui.app.ActivityManagerEx",
            "com.android.server.wm.MiuiFreeFormManagerService"
        )
        for (className in candidates) {
            try {
                val cls = Class.forName(className)
                android.util.Log.d("AIMANAGER_FF", "FOUND class: $className")
                cls.methods.forEach { m ->
                    android.util.Log.d("AIMANAGER_FF", "  method: ${m.name}(${m.parameterTypes.joinToString { it.simpleName }})")
                }
            } catch (e: Exception) {
                android.util.Log.d("AIMANAGER_FF", "NOT FOUND: $className")
            }
        }

        // ── Пробуем запуск через ActivityOptions (стандартный freeform Android) ──
        var launched = false
        try {
            val options = android.app.ActivityOptions.makeBasic()
            val setWindowingMode = options.javaClass.getMethod("setLaunchWindowingMode", Int::class.java)
            setWindowingMode.invoke(options, 5) // WINDOWING_MODE_FREEFORM = 5
            startActivity(intent, options.toBundle())
            android.util.Log.d("AIMANAGER_FF", "Launched via ActivityOptions freeform=5")
            launched = true
        } catch (e: Exception) {
            android.util.Log.d("AIMANAGER_FF", "ActivityOptions freeform failed: ${e.message}")
        }

        // ── Fallback ──
        if (!launched) {
            startActivity(intent)
            android.util.Log.d("AIMANAGER_FF", "Launched via fallback")
        }
    }

    private fun hideBubble() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    // ─── Foreground notification (обязательно для Android 8+) ─────────────────
    private fun startForegroundNotification() {
        val channelId = "overlay_service"
        val channel = NotificationChannel(
            channelId,
            "AI Manager Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AI Manager")
            .setContentText("Плавающая кнопка активна")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(1, notification)
    }

    companion object {
        const val ACTION_HIDE = "com.aimanager.HIDE_BUBBLE"
        const val ACTION_SHOW = "com.aimanager.SHOW_BUBBLE"
    }
}

// ─── Кнопка-пузырь ────────────────────────────────────────────────────────────
@Composable
fun BubbleButton() {
    Box(
        modifier = Modifier
            .size(52.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFF2A2A35)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "AI Manager",
            tint = Color(0xFFE0E0E0),
            modifier = Modifier.size(24.dp)
        )
    }
}