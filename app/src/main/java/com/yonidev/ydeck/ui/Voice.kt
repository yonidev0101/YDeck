package com.yonidev.ydeck.ui

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/*
 * Talking with YDeck: the phone's own speech, no key and no cost. Android hears the question (its speech recognizer,
 * in Hebrew) and reads the answer aloud (its text to speech). The question still goes to the computer as words, and
 * Claude Code answers there, as for a typed one.
 */
object Voice {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var queued: String? = null

    private val _speaking = MutableStateFlow<String?>(null)
    /** The id of what is being read aloud now, or null. */
    val speaking: StateFlow<String?> = _speaking

    /** What the speech recognizer is asked for: Hebrew, one question, in the phone's own dialog. */
    fun listenIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
        .putExtra(RecognizerIntent.EXTRA_PROMPT, "מה תרצה לשאול?")

    /** What was heard, from the recognizer's result. */
    fun heard(data: Intent?): String = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty().trim()

    /** Reads [text] aloud as [id]; what was being read stops. Calls [onMissing] when the phone has no Hebrew voice. */
    fun say(context: Context, id: String, text: String, onMissing: () -> Unit = {}) {
        val words = plain(text)
        if (words.isBlank()) return
        val t = tts
        if (t == null) {
            queued = words
            _speaking.value = id
            tts = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                val engine = tts ?: return@TextToSpeech
                if (!ready) { _speaking.value = null; onMissing(); return@TextToSpeech }
                val ok = engine.setLanguage(Locale.forLanguageTag("he-IL"))
                if (ok == TextToSpeech.LANG_MISSING_DATA || ok == TextToSpeech.LANG_NOT_SUPPORTED) onMissing()
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { if (_speaking.value == utteranceId) _speaking.value = null }
                    @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { if (_speaking.value == utteranceId) _speaking.value = null }
                })
                queued?.let { engine.speak(it, TextToSpeech.QUEUE_FLUSH, null, _speaking.value ?: id) }
                queued = null
            }
            return
        }
        _speaking.value = id
        if (ready) t.speak(words, TextToSpeech.QUEUE_FLUSH, null, id) else queued = words
    }

    fun stop() {
        tts?.stop()
        queued = null
        _speaking.value = null
    }

    /** Markdown as it would be said: no marks, links by their words, lines as pauses. */
    fun plain(md: String): String = md
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("https?://\\S+"), "")
        .replace(Regex("[*_`#>|]+"), "")
        .replace(Regex("(?m)^\\s*[-•]\\s*"), "")
        .replace(Regex("\\n{2,}"), ".\n")
        .trim()
}
