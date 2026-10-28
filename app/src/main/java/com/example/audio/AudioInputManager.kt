package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AudioInputManager(
    private val onPcmChunk: (ByteArray) -> Unit,
    private val onAmplitude: (Float) -> Unit,
    private val onUserInterruptionDetected: () -> Unit
) {
    companion object {
        private const val TAG = "AudioInputManager"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // Chunks of ~100ms: 1600 samples = 3200 bytes
        private const val CHUNK_SAMPLES = 1600
        private const val CHUNK_BYTES = CHUNK_SAMPLES * 2
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(false)

    // Interruption detection
    @Volatile
    var isAssistantSpeaking: Boolean = false
    @Volatile
    var interruptionThreshold: Float = 0.08f

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (isRecording.get()) return true

        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufSize * 2, CHUNK_BYTES * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isRecording.set(true)

            recordingThread = Thread({
                recordLoop()
            }, "Mayra-AudioRecord-Thread").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting AudioRecord", e)
            return false
        }
    }

    fun stop() {
        isRecording.set(false)
        try {
            recordingThread?.interrupt()
            recordingThread?.join(500)
        } catch (e: Exception) {
            Log.e(TAG, "Error joining record thread", e)
        }
        recordingThread = null

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null
    }

    fun setMuted(muted: Boolean) {
        isMuted.set(muted)
    }

    private fun recordLoop() {
        val buffer = ByteArray(CHUNK_BYTES)
        val shortBuffer = ShortArray(CHUNK_SAMPLES)

        while (isRecording.get() && !Thread.currentThread().isInterrupted) {
            val record = audioRecord ?: break
            val readBytes = record.read(buffer, 0, buffer.size)

            if (readBytes > 0) {
                if (isMuted.get()) {
                    onAmplitude(0.0f)
                    continue
                }

                // Calculate RMS energy
                ByteBuffer.wrap(buffer, 0, readBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortBuffer, 0, readBytes / 2)
                var sumSquares = 0.0
                val samplesCount = readBytes / 2
                for (i in 0 until samplesCount) {
                    val sample = shortBuffer[i].toDouble()
                    sumSquares += sample * sample
                }
                val rms = sqrt(sumSquares / samplesCount) / 32768.0
                val normalizedAmp = (rms.toFloat() * 3.5f).coerceIn(0.0f, 1.0f)
                onAmplitude(normalizedAmp)

                // Interruption check: user started speaking while Mayra is speaking
                if (isAssistantSpeaking && normalizedAmp > interruptionThreshold) {
                    Log.d(TAG, "User speech detected (amp=$normalizedAmp > $interruptionThreshold). Triggering interruption!")
                    onUserInterruptionDetected()
                }

                // Send PCM to Gemini Live
                val chunkToSend = if (readBytes == buffer.size) buffer.copyOf() else buffer.copyOfRange(0, readBytes)
                onPcmChunk(chunkToSend)
            }
        }
    }
}
