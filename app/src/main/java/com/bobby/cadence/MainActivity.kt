package com.bobby.cadence

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobby.cadence.data.Actions
import com.bobby.cadence.ui.CadenceTheme
import com.bobby.cadence.ui.HomeScreen
import com.bobby.cadence.ui.LaunchCommand

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var command by mutableStateOf<LaunchCommand?>(null)
    private var serial = 0L

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) handle(intent)

        // first run: the tutorial asks for permissions with an explanation, so the system prompt waits for it
        if ((application as CadenceApp).prefs.state.value.onboarded && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            CadenceTheme(settings.font) {
                HomeScreen(vm, command)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // pick up changes made on another device while the app was closed
        (application as CadenceApp).syncNow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i == null) return
        val action = i.action
        // text shared from another app, or selected text sent via the "Cadence" menu entry
        val shared = when (action) {
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (!shared.isNullOrBlank()) {
            command = LaunchCommand(Actions.QUICK_ADD, -1L, ++serial, shared.trim().take(280))
            i.action = Intent.ACTION_MAIN
            return
        }
        val taskId = i.getLongExtra(Actions.EXTRA_TASK_ID, -1L)
        if (action == Actions.QUICK_ADD || action == Actions.OPEN_TASKS || action == Actions.OPEN_FOCUS || taskId > 0) {
            command = LaunchCommand(action, taskId, ++serial)
            // consume so a rotation or recreation does not replay it
            i.action = Intent.ACTION_MAIN
            i.removeExtra(Actions.EXTRA_TASK_ID)
        }
    }
}
