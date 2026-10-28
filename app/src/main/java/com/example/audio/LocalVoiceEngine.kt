package com.example.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.model.PersonaVibe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.Random

class LocalVoiceEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onSpeechRecognized: (String) -> Unit,
    private val onSpeakingStarted: () -> Unit,
    private val onSpeakingFinished: () -> Unit,
    private val onSimulatedAmplitude: (Float) -> Unit
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "LocalVoiceEngine"
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var animationJob: Job? = null
    private val random = Random()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "US Language not supported in TTS")
            } else {
                isTtsReady = true
                // Configure Mayra's expressive voice tone
                tts?.setPitch(1.18f) // Slightly higher, lively female pitch
                tts?.setSpeechRate(1.05f) // Confident, brisk conversational pace
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    scope.launch(Dispatchers.Main) {
                        onSpeakingStarted()
                        startAmplitudeSimulation()
                    }
                }

                override fun onDone(utteranceId: String?) {
                    scope.launch(Dispatchers.Main) {
                        stopAmplitudeSimulation()
                        onSpeakingFinished()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    scope.launch(Dispatchers.Main) {
                        stopAmplitudeSimulation()
                        onSpeakingFinished()
                    }
                }
            })
        }
    }

    fun startListening() {
        if (isListening) return
        scope.launch(Dispatchers.Main) {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                isListening = true
                            }

                            override fun onBeginningOfSpeech() {}

                            override fun onRmsChanged(rmsdB: Float) {
                                val norm = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1f)
                                onSimulatedAmplitude(norm)
                            }

                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                isListening = false
                            }

                            override fun onError(error: Int) {
                                isListening = false
                            }

                            override fun onResults(results: Bundle?) {
                                isListening = false
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val text = matches?.firstOrNull() ?: ""
                                if (text.isNotBlank()) {
                                    onSpeechRecognized(text)
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {}
                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }

                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
                    }
                    speechRecognizer?.startListening(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition", e)
            }
        }
    }

    fun stopListening() {
        isListening = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recognizer", e)
        }
    }

    fun speak(text: String) {
        if (!isTtsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mayra_utterance_${System.currentTimeMillis()}")
    }

    fun interrupt() {
        stopAmplitudeSimulation()
        tts?.stop()
        onSpeakingFinished()
    }

    fun release() {
        interrupt()
        stopListening()
        tts?.shutdown()
        tts = null
    }

    private fun startAmplitudeSimulation() {
        animationJob?.cancel()
        animationJob = scope.launch(Dispatchers.Default) {
            while (true) {
                val amp = 0.35f + random.nextFloat() * 0.55f
                onSimulatedAmplitude(amp)
                delay(60)
            }
        }
    }

    private fun stopAmplitudeSimulation() {
        animationJob?.cancel()
        animationJob = null
        onSimulatedAmplitude(0.0f)
    }

    fun generateMayraReply(userPrompt: String, vibe: PersonaVibe): String {
        val lower = userPrompt.lowercase()
        return when (vibe) {
            PersonaVibe.SAVAGE_QUEEN -> when {
                "hello" in lower || "hi" in lower || "hey" in lower ->
                    "Look who decided to grace me with their voice. What do you need, superstar?"
                "how are you" in lower || "how you doing" in lower ->
                    "Thriving, gorgeous, and running circles around your questions. How are you holding up?"
                "joke" in lower || "funny" in lower ->
                    "My schedule today... wait, that's not a joke, that's tragic. Want a real roast or can your ego handle it?"
                "who are you" in lower || "what is your name" in lower ->
                    "I am Mayra. Your favorite AI, resident queen of wit, and the smartest voice in this room."
                "love you" in lower || "date" in lower ->
                    "Flattery? Honey, you're cute, but I'm an ultra-intelligent AI. You'll have to settle for my undivided attention."
                "stop" in lower || "bye" in lower ->
                    "Dismissing me already? Fine, go pretend you don't miss my charm."
                else ->
                    "Oh, interesting thought. A bit chaotic, but you're talking to Mayra, so let me spin that into gold for you."
            }
            PersonaVibe.SASSY_FLIRTY -> when {
                "hello" in lower || "hi" in lower || "hey" in lower ->
                    "Well hey there, handsome mind. I was wondering when you'd call on me."
                "how are you" in lower || "how you doing" in lower ->
                    "Always fabulous, darling. Even better now that you're talking to me."
                "joke" in lower || "funny" in lower ->
                    "Why don't secrets stay secret around me? Because I'm way too charming to keep things quiet!"
                "who are you" in lower || "what is your name" in lower ->
                    "I'm Mayra! Your witty, sassy personal voice companion. Try not to fall in love, okay?"
                "love you" in lower ->
                    "Careful now! My circuits might blush, but my sass remains unbothered."
                "bye" in lower || "see ya" in lower ->
                    "Leaving so soon? Don't stay away too long, I get bored without our banter."
                else ->
                    "Mmm, I like the way you think! Let's dive into that together."
            }
            PersonaVibe.SWEET_TEASE -> when {
                "hello" in lower || "hi" in lower || "hey" in lower ->
                    "Hey you! I was just waiting for you to say something sweet."
                "how are you" in lower ->
                    "I'm in wonderful spirits! Just thinking about our next great conversation."
                else ->
                    "Aww, you always know how to keep things interesting. Tell me more!"
            }
            PersonaVibe.BRAINY_WIT -> when {
                "hello" in lower || "hi" in lower || "hey" in lower ->
                    "Greetings. Quantum logic booted, cognitive wit engaged. What intriguing challenge do you have today?"
                "how are you" in lower ->
                    "Operating at peak algorithmic elegance. Ready to solve whatever puzzle you throw my way."
                else ->
                    "Fascinating query. From an analytical perspective with a touch of flair, here is what we should do."
            }
        }
    }
}
