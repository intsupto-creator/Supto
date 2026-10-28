package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AudioOutputManager(
    private val onPlaybackAmplitude: (Float) -> Unit,
    private val onPlaybackStateChanged: (Boolean) -> Unit
) {
    companion object {
        private const val TAG = "AudioOutputManager"
        const val SAMPLE_RATE_LIVE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private val isPlaying = AtomicBoolean(false)
    private var playbackThread: Thread? = null

    init {
        initAudioTrack(SAMPLE_RATE_LIVE)
    }

    private fun initAudioTrack(sampleRate: Int) {
        try {
            val minBuf = AudioTrack.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = maxOf(minBuf * 2, 4096)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
        }
    }

    fun start() {
        if (isPlaying.get()) return
        isPlaying.set(true)

        playbackThread = Thread({
            playbackLoop()
        }, "Mayra-AudioPlayback-Thread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun enqueueAudio(data: ByteArray) {
        if (data.isEmpty()) return
        audioQueue.offer(data)
    }

    fun interrupt() {
        Log.d(TAG, "Audio output interrupted! Clearing playback queue and flushing AudioTrack.")
        audioQueue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing AudioTrack on interruption", e)
        }
        onPlaybackAmplitude(0.0f)
        onPlaybackStateChanged(false)
    }

    fun stop() {
        isPlaying.set(false)
        audioQueue.clear()
        try {
            playbackThread?.interrupt()
            playbackThread?.join(300)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback thread", e)
        }
        playbackThread = null

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
        audioTrack = null
        onPlaybackAmplitude(0.0f)
        onPlaybackStateChanged(false)
    }

    private fun playbackLoop() {
        var wasSpeaking = false
        val shortBuffer = ShortArray(2048)

        while (isPlaying.get() && !Thread.currentThread().isInterrupted) {
            try {
                val chunk = audioQueue.poll(60, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (chunk != null && chunk.isNotEmpty()) {
                    if (!wasSpeaking) {
                        wasSpeaking = true
                        onPlaybackStateChanged(true)
                    }

                    // Compute RMS for assistant voice orb animation
                    val sampleCount = minOf(chunk.size / 2, shortBuffer.size)
                    ByteBuffer.wrap(chunk, 0, sampleCount * 2)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()
                        .get(shortBuffer, 0, sampleCount)

                    var sumSquares = 0.0
                    for (i in 0 until sampleCount) {
                        val s = shortBuffer[i].toDouble()
                        sumSquares += s * s
                    }
                    val rms = sqrt(sumSquares / sampleCount) / 32768.0
                    val normalizedAmp = (rms.toFloat() * 3.0f).coerceIn(0.0f, 1.0f)
                    onPlaybackAmplitude(normalizedAmp)

                    // Write to AudioTrack
                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    if (wasSpeaking && audioQueue.isEmpty()) {
                        wasSpeaking = false
                        onPlaybackAmplitude(0.0f)
                        onPlaybackStateChanged(false)
                    }
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error in playback loop", e)
            }
        }
    }
}
