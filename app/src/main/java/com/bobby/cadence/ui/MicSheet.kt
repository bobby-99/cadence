package com.bobby.cadence.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bobby.cadence.Draft
import com.bobby.cadence.data.AppSettings
import com.bobby.cadence.data.Fmt
import com.bobby.cadence.data.describe
import com.bobby.cadence.nlp.ParsedTask
import com.bobby.cadence.nlp.TaskParser
import com.bobby.cadence.nlp.TokenKind
import java.time.LocalDate

/** "4:30 p.m." becomes "4:30 pm" so the parser understands what the recogniser wrote. */
private fun normalizeSpeech(raw: String): String =
    raw.replace(Regex("""\b([ap])\.\s?m\.?""", RegexOption.IGNORE_CASE)) { it.groupValues[1].lowercase() + "m" }.trim()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MicSheet(
    settings: AppSettings,
    is24: Boolean,
    today: LocalDate,
    onDismiss: () -> Unit,
    onAdd: (Draft) -> Unit,
    onEdit: (Draft) -> Unit,
) {
    val ctx = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val parser = remember { TaskParser() }

    var text by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var rawLevel by remember { mutableFloatStateOf(0f) }

    val available = remember { SpeechRecognizer.isRecognitionAvailable(ctx) }
    val recognizer = remember { if (available) SpeechRecognizer.createSpeechRecognizer(ctx) else null }
    DisposableEffect(Unit) { onDispose { try { recognizer?.destroy() } catch (e: Exception) { } } }

    fun start() {
        val r = recognizer
        if (r == null) {
            message = "Speech recognition is not available on this phone."
            return
        }
        message = null
        text = ""
        listening = true
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { rawLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false; rawLevel = 0f }
            override fun onError(error: Int) {
                listening = false
                rawLevel = 0f
                message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        if (text.isBlank()) "Did not catch that. Tap the mic and try again." else null
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Still busy. Tap the mic again in a moment."
                    else -> "Speech recognition is not available right now."
                }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                rawLevel = 0f
                val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!best.isNullOrBlank()) text = normalizeSpeech(best)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val part = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!part.isNullOrBlank()) text = normalizeSpeech(part)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        try {
            r.startListening(i)
        } catch (e: Exception) {
            listening = false
            message = "Could not start the microphone."
        }
    }

    fun stop() {
        try { recognizer?.stopListening() } catch (e: Exception) { }
        listening = false
        rawLevel = 0f
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) start() else message = "Microphone permission is needed. Allow it in the phone's app settings."
    }
    fun startWithPermission() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) { startWithPermission() }

    val parsed: ParsedTask? = remember(text, settings.smartParsing) {
        if (!settings.smartParsing || text.isBlank()) null else parser.parse(text, null)
    }
    val draft: Draft = remember(parsed, text, settings) {
        if (parsed != null) draftFromParsed(parsed, settings)
        else draftFromParsed(ParsedTask(text.trim(), null, null, null, null, null, null, emptyList()), settings)
    }
    val canAdd = draft.title.isNotBlank()

    val level by animateFloatAsState(rawLevel, tween(120), label = "micLevel")

    ModalBottomSheet(
        onDismissRequest = { stop(); onDismiss() },
        sheetState = sheet,
        containerColor = C.Surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(C.SurfaceTop))
        },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp).padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (listening) C.Accent else C.Faint))
                Text(
                    if (listening) "LISTENING" else if (text.isBlank()) "TAP THE MIC" else "PAUSED",
                    color = if (listening) C.Accent else C.Dim,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Box(Modifier.fillMaxWidth().heightIn(min = 112.dp).padding(top = 18.dp), contentAlignment = Alignment.Center) {
                if (text.isBlank()) {
                    Text(
                        "Say it the way you would write it, like “dentist next friday at 4:30”",
                        color = C.Faint,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        highlighted(text, parsed),
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 33.sp, fontWeight = FontWeight.SemiBold),
                        color = C.Text,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            val chips = ArrayList<Pair<String, Color>>()
            draft.date?.let { chips += Fmt.date(it, today) to C.Accent }
            draft.time?.let { chips += Fmt.time(it, is24) to C.Accent }
            draft.rule?.let { chips += "↻ " + it.describe() to C.Done }
            if (draft.priority > 0) chips += priorityLabel(draft.priority) + " priority" to priorityColor(draft.priority)
            if (chips.isNotEmpty() && text.isNotBlank()) {
                FlowRow(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { chips.forEach { (t, c) -> Tag(t, c) } }
            }

            if (message != null) {
                Text(message!!, color = C.Danger, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
            }

            Spacer(Modifier.height(24.dp))

            // mic button with a ring that follows your voice
            Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size((84 + 56 * level).dp).clip(CircleShape).background(C.Accent.copy(alpha = 0.12f)))
                Box(Modifier.size((84 + 24 * level).dp).clip(CircleShape).background(C.Accent.copy(alpha = 0.2f)))
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(C.Accent).tap { if (listening) stop() else startWithPermission() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MicIcon, if (listening) "Stop listening" else "Start listening", tint = C.AccentOn, modifier = Modifier.size(32.dp))
                }
            }
            Text(if (listening) "Tap to pause" else "Tap to speak again", color = C.Dim, style = MaterialTheme.typography.bodySmall)

            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(18.dp))
                        .border(1.dp, C.Stroke, RoundedCornerShape(18.dp)).tap(enabled = text.isNotBlank()) { stop(); onEdit(draft) },
                    contentAlignment = Alignment.Center,
                ) { Text("Edit", color = if (text.isNotBlank()) C.Text else C.Faint, style = MaterialTheme.typography.titleSmall) }
                Box(
                    Modifier.weight(2f).height(54.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (canAdd) C.Accent else C.SurfaceTop).tap(enabled = canAdd) { stop(); onAdd(draft) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Add task", color = if (canAdd) C.AccentOn else C.Faint, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
                }
            }
        }
    }
}

private fun highlighted(text: String, parsed: ParsedTask?): AnnotatedString {
    val b = AnnotatedString.Builder(text)
    parsed?.tokens?.forEach { t ->
        if (t.start < 0 || t.end > text.length || t.start >= t.end) return@forEach
        val color = when (t.kind) {
            TokenKind.DATE, TokenKind.TIME -> C.Accent
            TokenKind.REPEAT -> C.Done
            TokenKind.PRIORITY -> C.High
            TokenKind.REMINDER -> C.Low
            TokenKind.NOTE -> C.Faint
        }
        b.addStyle(SpanStyle(color = color), t.start, t.end)
    }
    return b.toAnnotatedString()
}
