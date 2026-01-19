package com.example.flutter_application_1

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TTSAction(context: Context) : IWarningAction, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var lastSpeakTime = 0L
    private val COOLDOWN_MS = 5000 // Don't spam voice every frame

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.CHINESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                println("TTS: Language not supported")
            } else {
                isReady = true
                println("TTS: Initialized successfully")
            }
        } else {
            println("TTS: Initialization failed")
        }
    }

    override fun trigger(level: Int) {
        if (!isReady) return

        val now = System.currentTimeMillis()
        if (now - lastSpeakTime < COOLDOWN_MS) return

        val text = if (level >= 2) "危险！检测到疲劳驾驶，请立即休息！" else "注意！请保持清醒。"
        
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fatigue_warning")
        lastSpeakTime = now
    }

    override fun cancel() {
        // Even if isSpeaking is false, stop() ensures any buffered speech is cleared
        try {
            tts?.stop()
        } catch (e: Exception) {
            println("TTS: Error stopping speech: ${e.message}")
        }
    }

    fun shutdown() {
        tts?.shutdown()
    }
}