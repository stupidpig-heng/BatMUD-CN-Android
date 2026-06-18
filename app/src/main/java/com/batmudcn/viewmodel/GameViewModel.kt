package com.batmudcn.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.batmudcn.data.AppDatabase
import com.batmudcn.data.AppSettings
import com.batmudcn.data.UserPreferences
import com.batmudcn.engine.DebugLine
import com.batmudcn.engine.GameEngine
import com.batmudcn.engine.GameEngine.ConnectionState
import com.batmudcn.engine.OutputLine
import com.batmudcn.translate.Translator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "GameViewModel"
    }

    private val preferences = UserPreferences(application)
    private val database = AppDatabase.getInstance(application)

    // Current settings
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // Game engine (created after config is loaded)
    private var gameEngine: GameEngine? = null

    // Stable fallback state flows — reused across all accesses
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _outputLines = MutableStateFlow<List<OutputLine>>(emptyList())
    val outputLines: StateFlow<List<OutputLine>> = _outputLines.asStateFlow()

    private val _debugLines = MutableStateFlow<List<DebugLine>>(emptyList())
    val debugLines: StateFlow<List<DebugLine>> = _debugLines.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _statusLine = MutableStateFlow("")
    val statusLine: StateFlow<String> = _statusLine.asStateFlow()

    // Translator (recreated when settings change)
    private var translator: Translator? = null

    init {
        // Load settings
        viewModelScope.launch {
            preferences.settings.collect { s ->
                _settings.value = s
            }
        }

        // Watch settings changes and re-init engine
        viewModelScope.launch {
            preferences.settings
                .drop(1) // skip initial
                .collect { s ->
                    Log.d(TAG, "Settings updated, reinitializing...")
                    initializeEngine(s)
                }
        }

        // Initial engine setup — auto-connect on start (translation is optional)
        viewModelScope.launch {
            preferences.settings.first().let { s ->
                initializeEngine(s)
                if (gameEngine?.connectionState?.value != ConnectionState.CONNECTED) {
                    gameEngine?.connect()
                }
            }
        }
    }

    private fun initializeEngine(settings: AppSettings) {
        // Create translator if translation is enabled and configured
        translator = if (settings.translationEnabled && settings.isConfigured) {
            Translator(
                appId = settings.appId,
                secretKey = settings.secretKey,
                modelType = settings.modelType,
                cacheSize = settings.cacheSize,
                database = database,
            )
        } else {
            null
        }

        // Create or update game engine
        if (gameEngine == null) {
            gameEngine = GameEngine(translator, viewModelScope)
            // Forward engine states to our stable flows
            forwardEngineStates()
        } else {
            gameEngine?.configure(settings.serverHost, settings.serverPort, settings.minChars)
        }
    }

    /**
     * Forward StateFlows from the GameEngine into our stable ViewModel flows
     * so that UI collections don't break across recompositions.
     */
    private fun forwardEngineStates() {
        val engine = gameEngine ?: return
        viewModelScope.launch {
            engine.connectionState.collect { _connectionState.value = it }
        }
        viewModelScope.launch {
            engine.outputLines.collect { _outputLines.value = it }
        }
        viewModelScope.launch {
            engine.debugLines.collect { _debugLines.value = it }
        }
        viewModelScope.launch {
            engine.statusMessage.collect { _statusMessage.value = it }
        }
        viewModelScope.launch {
            engine.statusLine.collect { _statusLine.value = it }
        }
    }

    // ---- User Actions ----

    fun connect() {
        viewModelScope.launch {
            gameEngine?.connect()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            gameEngine?.disconnect()
        }
    }

    fun sendCommand(cmd: String) {
        viewModelScope.launch {
            gameEngine?.sendCommand(cmd)
        }
    }

    fun clearOutput() {
        gameEngine?.clearOutput()
    }

    fun clearDebug() {
        gameEngine?.clearDebug()
    }

    fun toggleConnection() {
        viewModelScope.launch {
            val current = gameEngine?.connectionState?.value
            if (current == ConnectionState.CONNECTED || current == ConnectionState.CONNECTING) {
                gameEngine?.disconnect()
            } else {
                gameEngine?.connect()
            }
        }
    }

    // ---- Settings ----

    fun updateServer(host: String, port: Int) {
        viewModelScope.launch { preferences.setServer(host, port) }
    }

    fun updateBaiduCredentials(appId: String, secretKey: String) {
        viewModelScope.launch { preferences.setBaiduCredentials(appId, secretKey) }
    }

    fun updateTranslationSettings(enabled: Boolean, modelType: String, cacheSize: Int, minChars: Int) {
        viewModelScope.launch { preferences.setTranslationSettings(enabled, modelType, cacheSize, minChars) }
    }

    fun completeFirstRun() {
        viewModelScope.launch { preferences.completeFirstRun() }
    }

    // ---- Stats ----

    fun getCacheStats(): String {
        val stats = translator?.cacheStats ?: return "翻译未启用"
        return "内存: ${stats.memorySize} | API调用: ${stats.apiCalls} | " +
                "内存命中: ${stats.memoryHits} | 磁盘命中: ${stats.diskHits}"
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            gameEngine?.disconnect()
        }
    }
}
