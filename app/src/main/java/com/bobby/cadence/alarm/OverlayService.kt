package com.bobby.cadence.alarm

import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import com.bobby.cadence.data.Task

/**
 * Draws the priority card over whatever app is on screen.
 * Only used while the phone is on and unlocked. It never wakes the screen and never keeps it on.
 * Needs the "Display over other apps" permission; without it the notification still works.
 *
 * The window itself is owned by [OverlayHost]. The service only keeps the process alive while the
 * card is up; if the system refuses to start it, the card is drawn directly instead.
 */
class OverlayService : Service() {

    companion object {
        fun canDraw(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

        /** The card is only for a phone that is on and unlocked. Screen off or locked: notification only. */
        fun phoneInUse(ctx: Context): Boolean {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            return pm.isInteractive && !km.isKeyguardLocked
        }

        /** Returns false when the card could not be shown. */
        fun show(ctx: Context, task: Task): Boolean {
            if (!canDraw(ctx) || !phoneInUse(ctx)) return false
            val app = ctx.applicationContext
            return try {
                app.startService(Intent(app, OverlayService::class.java).putTask(task))
                true
            } catch (e: Exception) {
                // background start refused: draw the card without the service
                OverlayHost.show(app, task)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val task = intent?.getTask()
        if (task == null || !canDraw(this) || !OverlayHost.show(applicationContext, task) { stopSelf() }) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        OverlayHost.close(applicationContext)
        super.onDestroy()
    }
}

/** Owns the one card window. Always touches the window manager on the main thread. */
internal object OverlayHost {
    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null

    fun show(ctx: Context, task: Task, onClosed: () -> Unit = {}): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return display(ctx, task, onClosed)
        main.post { display(ctx, task, onClosed) }
        return true
    }

    fun close(ctx: Context) {
        if (Looper.myLooper() == Looper.getMainLooper()) remove(ctx) else main.post { remove(ctx) }
    }

    private fun display(ctx: Context, task: Task, onClosed: () -> Unit): Boolean = try {
        remove(ctx)
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val v = PriorityCard.build(ctx, task, PriorityCard.handlers(ctx, task) { remove(ctx); onClosed() })
        view = v
        wm.addView(v, lp)
        v.requestFocus()
        true
    } catch (e: Exception) {
        view = null
        false
    }

    private fun remove(ctx: Context) {
        val v = view ?: return
        view = null
        try {
            (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (e: Exception) {
        }
    }
}
