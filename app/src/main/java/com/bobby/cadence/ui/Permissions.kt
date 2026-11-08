package com.bobby.cadence.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bobby.cadence.alarm.Notifier
import com.bobby.cadence.data.CalendarBridge

/** Live state of every permission Cadence uses, plus the actions that ask for them. Re-reads on resume. */
class Perms(
    val notifications: Boolean,
    /** the "Task reminders" channel still pops up (not switched off or turned down) */
    val channel: Boolean,
    val battery: Boolean,
    val overlay: Boolean,
    val calendar: Boolean,
    val askNotifications: () -> Unit,
    val askBattery: () -> Unit,
    val askOverlay: () -> Unit,
    val askCalendar: () -> Unit,
    val openNotificationSettings: () -> Unit,
    val openAppSettings: () -> Unit,
) {
    /** How many of the settings that affect reminders still need attention (calendar is optional). */
    val needAttention: Int
        get() = listOf(notifications && channel, battery, overlay).count { !it }
}

@Composable
fun rememberPerms(): Perms {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val calLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    val notif = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val channel = remember(tick) { Notifier.channelOk(ctx) }
    val battery = remember(tick) { (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName) }
    val overlay = remember(tick) { Settings.canDrawOverlays(ctx) }
    val calendar = remember(tick) { CalendarBridge.canWrite(ctx) }

    fun openNotificationSettings() = launchOrToast(
        ctx, "Open system settings and allow notifications for Cadence",
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
    )

    return Perms(
        notifications = notif,
        channel = channel,
        battery = battery,
        overlay = overlay,
        calendar = calendar,
        askNotifications = {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else openNotificationSettings()
        },
        askBattery = {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: Exception) {
                launchOrToast(ctx, "Open battery settings and set Cadence to unrestricted", Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        },
        askOverlay = {
            launchOrToast(
                ctx, "Open Settings, Apps, Cadence, Display over other apps",
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")),
            )
        },
        askCalendar = { calLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) },
        openNotificationSettings = ::openNotificationSettings,
        openAppSettings = {
            launchOrToast(
                ctx, "Open Settings, Apps, Cadence",
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
            )
        },
    )
}

internal fun launchOrToast(ctx: Context, fallback: String, intent: Intent) {
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, fallback, Toast.LENGTH_LONG).show()
    }
}
