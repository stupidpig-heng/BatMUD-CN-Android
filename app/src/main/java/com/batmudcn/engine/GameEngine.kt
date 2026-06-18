package com.batmudcn.engine

import android.util.Log
import com.batmudcn.translate.Translator
import com.batmudcn.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central game engine — manages Telnet connection, line processing,
 * and translation pipeline. Exposes game state via StateFlow for UI.
 */
class GameEngine(
    private val translator: Translator?,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    companion object {
        private const val TAG = "GameEngine"
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR,
    }

    // Connection state exposed to UI
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Output lines streamed to UI
    private val _outputLines = MutableStateFlow<List<OutputLine>>(emptyList())
    val outputLines: StateFlow<List<OutputLine>> = _outputLines.asStateFlow()

    // Debug info stream
    private val _debugLines = MutableStateFlow<List<DebugLine>>(emptyList())
    val debugLines: StateFlow<List<DebugLine>> = _debugLines.asStateFlow()

    // Status message
    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // Character status line (HP/SP/EP/Exp prompt) — displayed in fixed HUD
    private val _statusLine = MutableStateFlow("")
    val statusLine: StateFlow<String> = _statusLine.asStateFlow()

    private var telnetClient: TelnetClient? = null
    private var lineProcessor: LineProcessor? = null
    private var configJob: Job? = null

    private var host: String = Constants.DEFAULT_HOST
    private var port: Int = Constants.DEFAULT_PORT
    private var minChars: Int = Constants.DEFAULT_MIN_CHARS

    /**
     * Update configuration (called when settings change).
     */
    fun configure(host: String, port: Int, minChars: Int) {
        this.host = host
        this.port = port
        this.minChars = minChars
    }

    /**
     * Connect to the game server.
     */
    suspend fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING) {
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        _statusMessage.value = "正在连接 $host:$port..."

        lineProcessor = LineProcessor(translator, minChars)

        telnetClient = TelnetClient(
            onData = { data -> handleIncomingData(data) },
            onDisconnect = { handleDisconnect() },
            scope = scope,
        )

        try {
            telnetClient?.connect(host, port)
            _connectionState.value = ConnectionState.CONNECTED
            _statusMessage.value = "已连接到 $host:$port"
            Log.i(TAG, "Game engine connected")
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}", e)
            _connectionState.value = ConnectionState.ERROR
            _statusMessage.value = "连接失败: ${e.message}"
        }
    }

    /**
     * Disconnect from the game server.
     */
    suspend fun disconnect() {
        telnetClient?.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
        _statusMessage.value = "已断开连接"
    }

    /**
     * Send a command to the game server.
     */
    suspend fun sendCommand(cmd: String) {
        // Always echo locally so user can see what they typed
        val echoLine = OutputLine(
            text = "> $cmd",
            isEcho = true,
        )
        addOutputLine(echoLine)

        if (_connectionState.value != ConnectionState.CONNECTED) {
            _statusMessage.value = "未连接 — 无法发送命令，请先连接服务器"
            return
        }

        // Clear any pending cross-chunk buffered line — it's stale after a command
        lineProcessor?.clearPending()

        // Send to server with \r\n
        telnetClient?.send("$cmd\r\n")
    }

    /**
     * Handle raw data from the Telnet connection.
     */
    private suspend fun handleIncomingData(data: ByteArray) {
        try {
            val processor = lineProcessor ?: return

            // Streaming: each line is emitted via callback as soon as translated,
            // so UI updates incrementally instead of waiting for the whole batch
            processor.feed(data) { line ->
                if (line.isNewline) {
                    addOutputLine(OutputLine(text = ""))
                    return@feed  // continue to next line
                }

                // Route prompt lines to status HUD, not terminal output
                if (line.isPrompt) {
                    _statusLine.value = line.text
                    return@feed  // skip terminal output
                }

                val outputLine = OutputLine(
                    text = line.text,
                    html = line.html,
                    rawBytes = line.rawBytes,
                    isPrompt = line.isPrompt,
                    debug = line.debug.takeIf { it.isNotEmpty() }?.let { debug ->
                        OutputDebugInfo(
                            mode = debug["mode"] as? String ?: "",
                            ansiCount = (debug["ansi_count"] as? Int) ?: 0,
                            segmentCount = (debug["segments"] as? Int) ?: 0,
                            translatedSegs = (debug["translated_segs"] as? Int) ?: 0,
                            rawHex = debug["raw_hex"] as? String ?: "",
                            text = debug["text"] as? String ?: "",
                            html = debug["html"] as? String ?: "",
                            skip = if (debug["skip"] == true) "skip" else "",
                            transMap = (debug["trans_map"] as? Map<String, String>) ?: emptyMap(),
                        )
                    },
                )

                addOutputLine(outputLine)

                // Add debug info
                if (outputLine.debug != null) {
                    addDebugLine(DebugLine(
                        mode = outputLine.debug.mode,
                        ansiCount = outputLine.debug.ansiCount,
                        segmentCount = outputLine.debug.segmentCount,
                        translatedSegs = outputLine.debug.translatedSegs,
                        textPreview = outputLine.debug.text.take(40),
                        htmlPreview = outputLine.debug.html.take(60),
                        rawHex = outputLine.debug.rawHex.take(80),
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing incoming data: ${e.message}", e)
        }
    }

    /**
     * Handle disconnection from the game server.
     */
    private suspend fun handleDisconnect() {
        _connectionState.value = ConnectionState.DISCONNECTED
        _statusMessage.value = "与服务器断开连接 — 点击状态栏重连"
        _statusLine.value = ""  // clear old status on disconnect
    }

    private fun addOutputLine(line: OutputLine) {
        val current = _outputLines.value.toMutableList()
        // Dedup: skip if identical to the most recent line (prevents GMCP duplication)
        val last = current.lastOrNull()
        if (last != null && last.text == line.text && last.html == line.html) return
        current.add(line)
        // Trim buffer if too large
        while (current.size > Constants.MAX_OUTPUT_LINES) {
            current.removeAt(0)
        }
        _outputLines.value = current
    }

    private fun addDebugLine(line: DebugLine) {
        val current = _debugLines.value.toMutableList()
        current.add(line)
        // Keep last 200 debug entries
        while (current.size > 200) {
            current.removeAt(0)
        }
        _debugLines.value = current
    }

    fun clearOutput() {
        _outputLines.value = emptyList()
    }

    fun clearDebug() {
        _debugLines.value = emptyList()
    }
}

/**
 * UI-facing output line.
 */
data class OutputLine(
    val text: String,
    val html: String = text,
    val isEcho: Boolean = false,
    val debug: OutputDebugInfo? = null,
    val rawBytes: ByteArray? = null,  // ANSI+translated bytes for AnsiRenderer
    val isPrompt: Boolean = false,    // true if this is a status prompt (HP/SP/EP)
)

data class OutputDebugInfo(
    val mode: String,
    val ansiCount: Int,
    val segmentCount: Int,
    val translatedSegs: Int,
    val rawHex: String,
    val text: String,
    val html: String,
    val skip: String,
    val transMap: Map<String, String>,
)

data class DebugLine(
    val mode: String,
    val ansiCount: Int,
    val segmentCount: Int,
    val translatedSegs: Int,
    val textPreview: String,
    val htmlPreview: String,
    val rawHex: String,
)
