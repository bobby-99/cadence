package com.bobby.cadence.widget

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobby.cadence.CadenceApp
import com.bobby.cadence.MainActivity
import com.bobby.cadence.data.Actions
import com.bobby.cadence.ui.CadenceTheme
import com.bobby.cadence.ui.QuickAddSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The widget's "+" button opens this: just the add sheet floating over your home screen.
 * Type, hit add, and you are back where you were. Smart parsing works exactly like in the app.
 */
class WidgetAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val app = application as CadenceApp
        setContent {
            val settings by app.prefs.state.collectAsStateWithLifecycle()
            CadenceTheme(settings.font) {
                val is24 = remember { DateFormat.is24HourFormat(this@WidgetAddActivity) }
                QuickAddSheet(
                    defaultDate = null,
                    settings = settings,
                    is24 = is24,
                    today = LocalDate.now(),
                    onDismiss = { finish() },
                    onAdd = { d ->
                        val task = d.toTask()
                        CoroutineScope(Dispatchers.Main).launch {
                            app.repo.add(task)
                            Toast.makeText(applicationContext, "Added: " + task.title, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    },
                    onMore = { d ->
                        val task = d.toTask()
                        CoroutineScope(Dispatchers.Main).launch {
                            val id = app.repo.add(task)
                            startActivity(
                                Intent(this@WidgetAddActivity, MainActivity::class.java)
                                    .setAction(Intent.ACTION_VIEW)
                                    .putExtra(Actions.EXTRA_TASK_ID, id)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                            )
                            finish()
                        }
                    },
                )
            }
        }
    }
}
