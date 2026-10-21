package com.docscan.app.ui.write

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Splits [text] into pieces the speech engine will accept, breaking at sentence ends or spaces where it can. */
fun speechChunks(text: String, max: Int): List<String> {
    val out = ArrayList<String>()
    var rest = text.replace(Regex("\\s+"), " ").trim()
    val limit = max.coerceAtLeast(200)
    while (rest.isNotEmpty()) {
        if (rest.length <= limit) { out += rest; break }
        var cut = rest.lastIndexOfAny(charArrayOf('.', '!', '?', ';'), limit - 1)
        if (cut < limit / 3) cut = rest.lastIndexOf(' ', limit - 1)
        if (cut < limit / 3) cut = limit - 1
        out += rest.substring(0, cut + 1).trim()
        rest = rest.substring(cut + 1).trim()
    }
    return out.filter { it.isNotEmpty() }
}

/** Reads text out loud with the phone's own speech voice (nothing leaves the phone). */
class ReadAloud(context: Context) {
    /** True while it is speaking, so the button can say Stop. */
    var speaking by mutableStateOf(false)
        private set
    var onProblem: ((String) -> Unit)? = null
    private var ready = false
    private var pending: String? = null
    private var left = 0
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            pending?.let { pending = null; start(it) }
        } else { pending = null; speaking = false; onProblem?.invoke("This phone has no speech voice to read with. Install one in Android settings, Text-to-speech.") }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (--left <= 0) speaking = false }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { speaking = false }
        })
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        speaking = true
        if (ready) start(text) else pending = text
    }

    private fun start(text: String) {
        val chunks = speechChunks(text, TextToSpeech.getMaxSpeechInputLength() - 100)
        left = chunks.size
        chunks.forEachIndexed { i, c ->
            tts.speak(c, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "chunk$i")
        }
    }

    fun stop() { pending = null; if (ready) tts.stop(); speaking = false }

    fun shutdown() { pending = null; runCatching { tts.stop(); tts.shutdown() }; speaking = false }
}
