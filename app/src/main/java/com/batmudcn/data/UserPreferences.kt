package com.batmudcn.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.batmudcn.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "batmud_settings")

/**
 * User preferences backed by Jetpack DataStore.
 * Mirrors Python config.yaml.
 */
class UserPreferences(private val context: Context) {

    companion object {
        private val KEY_SERVER_HOST = stringPreferencesKey("server_host")
        private val KEY_SERVER_PORT = intPreferencesKey("server_port")
        private val KEY_APP_ID = stringPreferencesKey("app_id")
        private val KEY_SECRET_KEY = stringPreferencesKey("secret_key")
        private val KEY_MODEL_TYPE = stringPreferencesKey("model_type")
        private val KEY_TRANSLATION_ENABLED = booleanPreferencesKey("translation_enabled")
        private val KEY_CACHE_SIZE = intPreferencesKey("cache_size")
        private val KEY_MIN_CHARS = intPreferencesKey("min_chars")
        private val KEY_FIRST_RUN = booleanPreferencesKey("first_run")
    }

    /** All settings as a Flow */
    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            serverHost = prefs[KEY_SERVER_HOST] ?: Constants.DEFAULT_HOST,
            serverPort = prefs[KEY_SERVER_PORT] ?: Constants.DEFAULT_PORT,
            appId = prefs[KEY_APP_ID] ?: "",
            secretKey = prefs[KEY_SECRET_KEY] ?: "",
            modelType = prefs[KEY_MODEL_TYPE] ?: Constants.DEFAULT_MODEL_TYPE,
            translationEnabled = prefs[KEY_TRANSLATION_ENABLED] ?: Constants.DEFAULT_ENABLED,
            cacheSize = prefs[KEY_CACHE_SIZE] ?: Constants.DEFAULT_CACHE_SIZE,
            minChars = prefs[KEY_MIN_CHARS] ?: Constants.DEFAULT_MIN_CHARS,
            isFirstRun = prefs[KEY_FIRST_RUN] ?: true,
        )
    }

    /** Read current settings once */
    suspend fun getSettings(): AppSettings = settings.first()

    /** Update server config */
    suspend fun setServer(host: String, port: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_HOST] = host
            prefs[KEY_SERVER_PORT] = port
        }
    }

    /** Update Baidu API credentials */
    suspend fun setBaiduCredentials(appId: String, secretKey: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_ID] = appId
            prefs[KEY_SECRET_KEY] = secretKey
        }
    }

    /** Update translation settings */
    suspend fun setTranslationSettings(enabled: Boolean, modelType: String, cacheSize: Int, minChars: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TRANSLATION_ENABLED] = enabled
            prefs[KEY_MODEL_TYPE] = modelType
            prefs[KEY_CACHE_SIZE] = cacheSize
            prefs[KEY_MIN_CHARS] = minChars
        }
    }

    /** Mark first run complete */
    suspend fun completeFirstRun() {
        context.dataStore.edit { prefs ->
            prefs[KEY_FIRST_RUN] = false
        }
    }
}

data class AppSettings(
    val serverHost: String = Constants.DEFAULT_HOST,
    val serverPort: Int = Constants.DEFAULT_PORT,
    val appId: String = "",
    val secretKey: String = "",
    val modelType: String = Constants.DEFAULT_MODEL_TYPE,
    val translationEnabled: Boolean = Constants.DEFAULT_ENABLED,
    val cacheSize: Int = Constants.DEFAULT_CACHE_SIZE,
    val minChars: Int = Constants.DEFAULT_MIN_CHARS,
    val isFirstRun: Boolean = true,
) {
    val isConfigured: Boolean
        get() = appId.isNotBlank() && secretKey.isNotBlank()
}
