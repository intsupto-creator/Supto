package com.example.gemini

import android.util.Base64
import android.util.Log
import com.example.model.PersonaVibe
import com.example.model.VoiceSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GeminiLiveClient(
    private val scope: CoroutineScope,
    private val onAudioReceived: (ByteArray) -> Unit,
    private val onInterrupted: () -> Unit,
    private val onTurnComplete: () -> Unit,
    private val onConnectionStateChanged: (ConnectionState) -> Unit
) {
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val BASE_WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private var isSetupComplete = false
    private var currentSettings: VoiceSettings = VoiceSettings()
    private var reconnectJob: Job? = null

    fun connect(apiKey: String, settings: VoiceSettings) {
        currentSettings = settings
        if (apiKey.isBlank()) {
            Log.w(TAG, "Empty API key provided. Not connecting to live WebSocket.")
            onConnectionStateChanged(ConnectionState.Error("API Key is missing. Configure in settings."))
            return
        }

        disconnect()
        onConnectionStateChanged(ConnectionState.Connecting)

        val cleanKey = apiKey.trim()
        val url = "$BASE_WS_URL?key=$cleanKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully. Sending setup message.")
                isConnected.set(true)
                sendSetupMessage(webSocket, settings)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                isConnected.set(false)
                isSetupComplete = false
                onConnectionStateChanged(ConnectionState.Disconnected)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                isConnected.set(false)
                isSetupComplete = false
                onConnectionStateChanged(ConnectionState.Disconnected)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                isConnected.set(false)
                isSetupComplete = false
                val errorMsg = t.message ?: "Failed to connect to Gemini Live"
                onConnectionStateChanged(ConnectionState.Error(errorMsg))
            }
        })
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        isConnected.set(false)
        isSetupComplete = false
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing websocket", e)
        }
        webSocket = null
        onConnectionStateChanged(ConnectionState.Disconnected)
    }

    fun isLive(): Boolean = isConnected.get() && isSetupComplete

    fun sendAudioChunk(pcmData: ByteArray) {
        if (!isLive() || webSocket == null) return

        try {
            val base64Data = Base64.encodeToString(pcmData, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Data)
                        })
                    })
                })
            }
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk", e)
        }
    }

    fun sendInterruptionSignal() {
        if (!isLive() || webSocket == null) return
        try {
            // Signal interruption to Gemini Live session
            val json = JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put("turns", JSONArray())
                    put("turnComplete", false)
                })
            }
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending client interruption", e)
        }
    }

    private fun sendSetupMessage(ws: WebSocket, settings: VoiceSettings) {
        try {
            val modelId = if (settings.model.startsWith("models/")) settings.model else "models/${settings.model}"
            val setupPayload = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", modelId)
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply {
                            put("AUDIO")
                        })
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", settings.voiceName)
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", settings.vibe.systemInstruction)
                            })
                        })
                    })
                })
            }
            Log.d(TAG, "Sending setup payload for voice ${settings.voiceName} model $modelId")
            ws.send(setupPayload.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send setup message", e)
        }
    }

    private fun handleServerMessage(text: String) {
        try {
            val root = JSONObject(text)

            if (root.has("setupComplete")) {
                Log.d(TAG, "Setup complete confirmed by Gemini Live server.")
                isSetupComplete = true
                scope.launch(Dispatchers.Main) {
                    onConnectionStateChanged(ConnectionState.Connected)
                }
                return
            }

            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Server flagged interruption event")
                    scope.launch(Dispatchers.Main) {
                        onInterrupted()
                    }
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val dataBase64 = inlineData.optString("data")
                                if (dataBase64.isNotEmpty()) {
                                    val audioBytes = Base64.decode(dataBase64, Base64.NO_WRAP)
                                    onAudioReceived(audioBytes)
                                }
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    Log.d(TAG, "Model turn complete")
                    scope.launch(Dispatchers.Main) {
                        onTurnComplete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message", e)
        }
    }
}
