package dev.vclient.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.vclient.MainActivity
import dev.vclient.R
import dev.vclient.core.VClientCore
import dev.vclient.modules.FullbrightModule
import dev.vclient.modules.MotionBlurModule
import dev.vclient.modules.ZoomModule
import dev.vclient.core.runtime.ClientHooks
import android.view.WindowManager
import dev.vclient.overlay.vmenu.VMenuWindow

/**
 * The overlay session service. Owns every window V Client draws above the
 * game and the effect controllers:
 *
 *   HUD surface (full-screen, not touchable) + floating bubble + V Menu +
 *   fullbright filter / motion-blur windows + zoom button.
 *
 * The service is started before Minecraft launches and keeps running while
 * the game is in the foreground (the HUD hides itself when the game loses
 * focus if the accessibility service is connected).
 */
class OverlayService : Service() {

    private val core: VClientCore get() = VClientCore.core
    private lateinit var windowManager: WindowManager

    private var hudView: HudSurfaceView? = null
    private var bubbleView: FloatingBubble? = null
    private var vmenu: VMenuWindow? = null
    private lateinit var effectWindows: EffectWindows
    private lateinit var zoomController: ZoomController

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        effectWindows = EffectWindows(this, windowManager, core.logger)
        zoomController = ZoomController(this, windowManager, core.logger)

        startInForeground()
        addHudWindow()
        addBubble()
        wireHooks()
        applyEnabledEffectModules()
        core.session.start()
        core.logger.i(TAG, "Overlay session started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_OPEN_MENU -> openVMenu()
            ACTION_TOGGLE_EDITOR -> toggleEditor()
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    // --- windows -------------------------------------------------------------

    private fun addHudWindow() {
        val view = HudSurfaceView(this, core)
        view.onEditorActiveChanged = { active ->
            setHudTouchable(active)
            if (active) closeVMenu()
        }
        hudView = view
        runCatching { windowManager.addView(view, hudParams(touchable = false)) }
            .onFailure {
                core.logger.e(TAG, "Failed to attach HUD window (overlay permission?)", it)
                stopSelf()
            }
    }

    private fun hudParams(touchable: Boolean): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )

    private fun setHudTouchable(touchable: Boolean) {
        val view = hudView ?: return
        runCatching { windowManager.updateViewLayout(view, hudParams(touchable)) }
            .onFailure { core.logger.e(TAG, "updateViewLayout failed", it) }
    }

    private fun addBubble() {
        val bubble = FloatingBubble(
            context = this,
            onOpenMenu = { openVMenu() },
            onToggleEditor = { toggleEditor() },
            onPositionChanged = { x, y ->
                core.settings.bubbleX = x
                core.settings.bubbleY = y
            },
        )
        bubble.windowManagerUpdate = { params ->
            runCatching { windowManager.updateViewLayout(bubble, params) }
        }
        bubbleView = bubble
        runCatching {
            windowManager.addView(bubble, FloatingBubble.layoutParams(this, core.settings.bubbleX, core.settings.bubbleY))
        }.onFailure { core.logger.e(TAG, "Failed to attach bubble", it) }
    }

    // --- V Menu / editor --------------------------------------------------------

    fun openVMenu() {
        val menu = vmenu ?: VMenuWindow(this).also { vmenu = it }
        menu.show(onClose = { closeVMenu() }, onOpenEditor = { toggleEditor() })
    }

    fun closeVMenu() {
        vmenu?.hide()
    }

    fun toggleEditor() {
        hudView?.editor?.toggle()
    }

    val editorActive: Boolean get() = hudView?.editor?.active == true

    // --- module hooks ---------------------------------------------------------------

    private fun wireHooks() {
        ClientHooks.openVMenu = { openVMenu() }
        ClientHooks.toggleEditor = { toggleEditor() }
        ClientHooks.zoomRequest = { enabled -> zoomController.setEnabled(enabled) }
        ClientHooks.filterRequest = { enabled ->
            (core.modules.byId(FullbrightModule.ID) as? FullbrightModule)?.let {
                effectWindows.setFullbright(enabled, it)
            }
        }
        ClientHooks.blurRequest = { enabled ->
            (core.modules.byId(MotionBlurModule.ID) as? MotionBlurModule)?.let {
                effectWindows.setMotionBlur(enabled, it)
            }
        }
    }

    /** Re-apply effect modules that the profile enabled while we weren't running. */
    private fun applyEnabledEffectModules() {
        (core.modules.byId(FullbrightModule.ID) as? FullbrightModule)?.takeIf { it.enabled }
            ?.let { effectWindows.setFullbright(true, it) }
        (core.modules.byId(MotionBlurModule.ID) as? MotionBlurModule)?.takeIf { it.enabled }
            ?.let { effectWindows.setMotionBlur(true, it) }
        (core.modules.byId(ZoomModule.ID) as? ZoomModule)?.takeIf { it.enabled }
            ?.let { zoomController.setEnabled(true) }
    }

    // --- foreground notification ------------------------------------------------------

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_overlay),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_overlay_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)

        fun servicePending(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
            this, requestCode,
            Intent(this, OverlayService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.action_open_vmenu), servicePending(ACTION_OPEN_MENU, 1))
            .addAction(0, getString(R.string.action_hud_editor), servicePending(ACTION_TOGGLE_EDITOR, 2))
            .addAction(0, getString(R.string.action_stop), servicePending(ACTION_STOP, 3))
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // --- teardown -------------------------------------------------------------------------

    override fun onDestroy() {
        ClientHooks.openVMenu = null
        ClientHooks.toggleEditor = null
        ClientHooks.zoomRequest = null
        ClientHooks.filterRequest = null
        ClientHooks.blurRequest = null
        effectWindows.destroy()
        zoomController.destroy()
        hudView?.let { runCatching { windowManager.removeView(it) } }
        hudView = null
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        vmenu?.hide()
        vmenu = null
        core.profiles.flush()
        core.session.stop()
        instance = null
        core.logger.i(TAG, "Overlay session stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val TAG = "OverlayService"
        const val CHANNEL_ID = "vclient_overlay"
        const val NOTIFICATION_ID = 42

        const val ACTION_OPEN_MENU = "dev.vclient.action.OPEN_MENU"
        const val ACTION_TOGGLE_EDITOR = "dev.vclient.action.TOGGLE_EDITOR"
        const val ACTION_STOP = "dev.vclient.action.STOP"

        @Volatile var instance: OverlayService? = null
            private set

        val isRunning: Boolean get() = instance != null

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            instance?.stopSelf()
                ?: runCatching { context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_STOP)) }
        }

        fun openMenu(context: Context) {
            instance?.openVMenu()
                ?: runCatching { context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_OPEN_MENU)) }
        }
    }
}
